package cn.maple.sse.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.core.framework.util.GXMdcThreadUtils;
import cn.maple.sse.dto.GXSseMessageInnerReqDto;
import cn.maple.sse.service.GXSseEmitterService;
import cn.maple.sse.service.GXSseMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Server-Sent Events service implementation.
 * <p>
 * Connection lifetime is managed through a single {@code ConcurrentHashMap} cache and
 * must stay consistent in three ways: an emitter is closed whenever it is removed, only
 * the current emitter of a client id may be removed, and every delivery for one client
 * stays ordered. See {@code specs/011-sse-module-audit/contracts/} for the full contract.
 * </p>
 */
@Slf4j
@Service
public class GXSseEmitterServiceImpl extends GXBusinessServiceImpl implements GXSseEmitterService {
    private static final long DEFAULT_MIN_TIMEOUT_SECONDS = 60L;
    private static final String DEFAULT_EVENT_NAME = "Push Msg";
    private static final int MAX_RETRIES = 3;
    private static final int DEFAULT_SPLIT_LENGTH = 100;
    private static final int MAX_PENDING_MESSAGES = 256;
    private static final int HEARTBEAT_BATCH_SIZE = 256;
    private static final long RETRY_BASE_DELAY_MILLIS = 100L;
    private static final long DESTROY_AWAIT_SECONDS = 5L;
    private static final String DESTROYED_MESSAGE = "SSE service has been destroyed";

    private final Map<String, SseEmitter> sseClientCache = new ConcurrentHashMap<>();
    private final Map<String, GXSseClientSendPipeline> sendPipelines = new ConcurrentHashMap<>();
    private final LongAdder attempts = new LongAdder();
    private final LongAdder successes = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder retries = new LongAdder();
    private final LongAdder drops = new LongAdder();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private volatile ScheduledExecutorService scheduler;
    private volatile ExecutorService taskExecutor;
    private volatile boolean started;

    /**
     * Starts the heartbeat trigger and the task executor. Called by the container and
     * lazily by the first connection, so manually created instances keep working.
     */
    @PostConstruct
    public void init() {
        startIfNeeded();
    }

    /**
     * Creates the emitter used by a new SSE connection. This is the only place where an
     * emitter is instantiated so that subclasses and tests can replace the creation
     * strategy without reflection.
     *
     * @param timeoutMillis connection timeout in milliseconds
     * @return new emitter
     */
    protected SseEmitter createEmitter(long timeoutMillis) {
        return new SseEmitter(timeoutMillis);
    }

    private synchronized void startIfNeeded() {
        if (started || destroyed.get()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("gx-sse-scheduler-", 0).factory());
        taskExecutor = Executors.newVirtualThreadPerTaskExecutor();
        scheduler.scheduleAtFixedRate(this::triggerHeartbeat, 1L, 1L, TimeUnit.MINUTES);
        started = true;
    }

    /**
     * Submits one heartbeat round to the task executor, split into batches. Visible for tests;
     * the scheduled trigger calls it once per minute.
     */
    void triggerHeartbeat() {
        if (destroyed.get()) {
            return;
        }
        List<Map.Entry<String, SseEmitter>> snapshot = new ArrayList<>(sseClientCache.entrySet());
        for (int from = 0; from < snapshot.size(); from += HEARTBEAT_BATCH_SIZE) {
            int to = Math.min(from + HEARTBEAT_BATCH_SIZE, snapshot.size());
            List<Map.Entry<String, SseEmitter>> batch = List.copyOf(snapshot.subList(from, to));
            ExecutorService executor = taskExecutor;
            if (executor == null || executor.isShutdown()) {
                return;
            }
            Map<String, String> mdcContext = GXMdcThreadUtils.getMdcContext();
            try {
                executor.execute(GXMdcThreadUtils.wrap(() -> sendHeartbeatBatch(batch), mdcContext));
            } catch (RejectedExecutionException e) {
                if (log.isDebugEnabled()) {
                    log.debug("SSE heartbeat skipped, executor rejected task: {}", e.getMessage());
                }
                return;
            }
        }
    }

    /**
     * Sends one heartbeat to every registered connection and removes the ones that fail.
     * Visible for tests; the scheduler uses the batched {@link #triggerHeartbeat()} path.
     */
    void cleanupConnections() {
        sendHeartbeatBatch(new ArrayList<>(sseClientCache.entrySet()));
    }

    private void sendHeartbeatBatch(List<Map.Entry<String, SseEmitter>> batch) {
        int scanned = 0;
        int removed = 0;
        long startNanos = System.nanoTime();
        for (Map.Entry<String, SseEmitter> entry : batch) {
            String clientId = entry.getKey();
            SseEmitter sseEmitter = entry.getValue();
            if (!isCurrentEmitter(clientId, sseEmitter)) {
                continue;
            }
            scanned++;
            try {
                sendNow(sseEmitter, SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("Remove invalid SSE connection, clientId: {}, reason: {}", clientId, e.getMessage());
                }
                closeAndRemove(clientId, sseEmitter, "heartbeat-failed");
                removed++;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("SSE heartbeat batch finished, scanned: {}, removed: {}, elapsedMs: {}",
                    scanned, removed, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        }
    }

    @Override
    public @Nullable SseEmitter getSseEmitterByClientId(@Nullable String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return null;
        }
        return sseClientCache.get(clientId);
    }

    @Override
    public SseEmitter createSseConnect(@Nullable String clientId, long timeout) {
        if (destroyed.get()) {
            throw new GXBusinessException(DESTROYED_MESSAGE);
        }
        startIfNeeded();

        long effectiveTimeout = Math.max(timeout, DEFAULT_MIN_TIMEOUT_SECONDS);
        SseEmitter sseEmitter = createEmitter(TimeUnit.SECONDS.toMillis(effectiveTimeout));
        String finalClientId = CharSequenceUtil.isBlank(clientId) ? IdUtil.fastSimpleUUID() : clientId;

        sseEmitter.onCompletion(onCompletionCallBack(finalClientId, sseEmitter));
        sseEmitter.onTimeout(onTimeoutCallBack(finalClientId, sseEmitter));
        sseEmitter.onError(onErrorCallBack(finalClientId, sseEmitter));

        SseEmitter oldEmitter = sseClientCache.put(finalClientId, sseEmitter);
        if (oldEmitter != null && oldEmitter != sseEmitter) {
            closePipeline(finalClientId);
            completeQuietly(finalClientId, oldEmitter);
            if (log.isInfoEnabled()) {
                log.info("Replaced SSE connection, clientId: {}, activeCount: {}", finalClientId, sseClientCache.size());
            }
        }
        if (log.isInfoEnabled()) {
            log.info("Created SSE connection, clientId: {}, activeCount: {}", finalClientId, sseClientCache.size());
        }

        try {
            SseEmitter.SseEventBuilder sseEventBuilder = SseEmitter.event()
                    .id(String.valueOf(HttpStatus.HTTP_CREATED))
                    .data(finalClientId, MediaType.APPLICATION_JSON);
            sendNow(sseEmitter, sseEventBuilder);
            return sseEmitter;
        } catch (IllegalStateException ex) {
            if (!isCurrentEmitter(finalClientId, sseEmitter)) {
                if (log.isInfoEnabled()) {
                    log.info("SSE handshake of a replaced connection skipped, clientId: {}", finalClientId);
                }
                return sseEmitter;
            }
            closeAndRemove(finalClientId, sseEmitter, "handshake-failed");
            throw new GXBusinessException("Create SSE connection failed", ex);
        } catch (IOException ex) {
            closeAndRemove(finalClientId, sseEmitter, "handshake-failed");
            throw new GXBusinessException("Create SSE connection failed", ex);
        }
    }

    @Override
    public void sendMessageToAllClient(@Nullable String msg) {
        if (CharSequenceUtil.isBlank(msg)) {
            log.warn("Skip broadcasting blank SSE message");
            return;
        }
        if (destroyed.get()) {
            drops.increment();
            if (log.isDebugEnabled()) {
                log.debug("Skip broadcasting SSE message, service has been destroyed");
            }
            return;
        }
        if (sseClientCache.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("No active SSE connection, broadcast skipped");
            }
            return;
        }
        sseClientCache.forEach((clientId, emitter) -> {
            GXSseMessageInnerReqDto messageDto = buildMessageDto(clientId, msg, "", 1, 1);
            dispatchMessage(clientId, emitter, messageDto);
        });
    }

    @Override
    public void sendMessageToOneClient(@Nullable String clientId, @Nullable String msg, boolean splitMsg) {
        if (CharSequenceUtil.isBlank(clientId) || CharSequenceUtil.isBlank(msg)) {
            log.warn("Send SSE message failed: clientId or message is blank");
            return;
        }
        if (destroyed.get()) {
            drops.increment();
            if (log.isDebugEnabled()) {
                log.debug("Skip sending SSE message, service has been destroyed");
            }
            return;
        }
        SseEmitter emitter = sseClientCache.get(clientId);
        if (emitter == null) {
            log.warn("Send SSE message failed: no connection found for clientId: {}", clientId);
            return;
        }

        List<String> fragments = splitMsg ? splitMessage(msg, DEFAULT_SPLIT_LENGTH) : List.of(msg);
        if (fragments.isEmpty()) {
            log.warn("Send SSE message failed: message is blank after splitting, clientId: {}", clientId);
            return;
        }
        int total = fragments.size();
        String msgId = total > 1 ? IdUtil.fastUUID() : "";
        for (int index = 0; index < total; index++) {
            GXSseMessageInnerReqDto messageDto = buildMessageDto(clientId, fragments.get(index), msgId, index + 1, total);
            dispatchMessage(clientId, emitter, messageDto);
        }
    }

    @Override
    public void closeConnect(@Nullable String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return;
        }
        SseEmitter sseEmitter = sseClientCache.remove(clientId);
        closePipeline(clientId);
        if (sseEmitter == null) {
            return;
        }
        try {
            SseEmitter.SseEventBuilder closeEvent = SseEmitter.event()
                    .name("close")
                    .data("Connection closed by server", MediaType.TEXT_PLAIN);
            sendNow(sseEmitter, closeEvent);
        } catch (IOException | IllegalStateException e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to send SSE close event, clientId: {}, reason: {}", clientId, e.getMessage());
            }
        } finally {
            completeQuietly(clientId, sseEmitter);
            if (log.isInfoEnabled()) {
                log.info("Closed SSE connection, clientId: {}, activeCount: {}", clientId, sseClientCache.size());
            }
        }
    }

    void dispatchMessage(String clientId, SseEmitter sseEmitter, GXSseMessageInnerReqDto messageDto) {
        if (messageDto.getData() == null) {
            drops.increment();
            log.warn("Drop SSE message, payload is null, clientId: {}", clientId);
            return;
        }

        GXSseClientSendPipeline pipeline = sendPipelines.get(clientId);
        if (pipeline != null) {
            enqueue(pipeline, clientId, sseEmitter, new GXSseClientSendPipeline.PendingMessage(messageDto, 0));
            return;
        }

        SendOutcome outcome = trySend(clientId, sseEmitter, messageDto);
        if (outcome == SendOutcome.DEAD) {
            closeAndRemove(clientId, sseEmitter, "dead-connection");
            return;
        }
        if (outcome == SendOutcome.IO_FAILED) {
            GXSseClientSendPipeline created = sendPipelines.computeIfAbsent(
                    clientId, id -> new GXSseClientSendPipeline(id, MAX_PENDING_MESSAGES));
            enqueue(created, clientId, sseEmitter, new GXSseClientSendPipeline.PendingMessage(messageDto, 1));
        }
    }

    private void enqueue(GXSseClientSendPipeline pipeline, String clientId, SseEmitter sseEmitter,
                         GXSseClientSendPipeline.PendingMessage pending) {
        GXSseClientSendPipeline.OfferResult result = pipeline.offer(pending);
        if (result.dropped() > 0) {
            drops.add(result.dropped());
            log.warn("Drop SSE message, pending queue is full, clientId: {}, dropped: {}", clientId, result.dropped());
        }
        switch (result.outcome()) {
            case STARTED:
                submitDrain(clientId, sseEmitter, pipeline);
                break;
            case CLOSED:
                drops.increment();
                if (log.isDebugEnabled()) {
                    log.debug("Drop SSE message, send pipeline is closed, clientId: {}", clientId);
                }
                break;
            default:
                break;
        }
    }

    private void submitDrain(String clientId, SseEmitter sseEmitter, GXSseClientSendPipeline pipeline) {
        ExecutorService executor = taskExecutor;
        if (executor == null || executor.isShutdown()) {
            drops.add(pipeline.close());
            sendPipelines.remove(clientId, pipeline);
            return;
        }
        Map<String, String> mdcContext = GXMdcThreadUtils.getMdcContext();
        try {
            executor.execute(GXMdcThreadUtils.wrap(() -> drainPipeline(clientId, sseEmitter, pipeline), mdcContext));
        } catch (RejectedExecutionException e) {
            drops.add(pipeline.close());
            sendPipelines.remove(clientId, pipeline);
            if (log.isDebugEnabled()) {
                log.debug("Drop pending SSE messages, executor rejected task, clientId: {}", clientId);
            }
        }
    }

    private void drainPipeline(String clientId, SseEmitter sseEmitter, GXSseClientSendPipeline pipeline) {
        try {
            while (true) {
                GXSseClientSendPipeline.PendingMessage pending = pipeline.poll();
                if (pending == null) {
                    return;
                }
                if (!isCurrentEmitter(clientId, sseEmitter)) {
                    drops.add(pipeline.close());
                    if (log.isDebugEnabled()) {
                        log.debug("Drop pending SSE messages, connection is no longer active, clientId: {}", clientId);
                    }
                    return;
                }
                if (!deliverWithRetry(clientId, sseEmitter, pending)) {
                    failures.increment();
                    log.error("SSE message dropped after retries, clientId: {}, attempts: {}, msgId: {}",
                            clientId, MAX_RETRIES + 1, pending.message().getMsgId());
                    closeAndRemove(clientId, sseEmitter, "retry-exhausted");
                    return;
                }
            }
        } catch (RuntimeException e) {
            drops.add(pipeline.close());
            sendPipelines.remove(clientId, pipeline);
            log.warn("SSE send pipeline aborted, clientId: {}, type: {}", clientId, e.getClass().getSimpleName());
        }
    }

    private boolean deliverWithRetry(String clientId, SseEmitter sseEmitter, GXSseClientSendPipeline.PendingMessage pending) {
        GXSseMessageInnerReqDto message = pending.message();
        int attempt = pending.attempt();
        while (attempt <= MAX_RETRIES) {
            if (destroyed.get()) {
                drops.increment();
                return true;
            }
            if (attempt > 0) {
                retries.increment();
                if (log.isDebugEnabled()) {
                    log.debug("Retry SSE message send, clientId: {}, attempt: {}", clientId, attempt);
                }
                if (!sleepBeforeRetry(attempt)) {
                    drops.increment();
                    return true;
                }
                if (!isCurrentEmitter(clientId, sseEmitter)) {
                    drops.increment();
                    return true;
                }
            }
            SendOutcome outcome = trySend(clientId, sseEmitter, message);
            if (outcome == SendOutcome.SENT || outcome == SendOutcome.DEAD) {
                return true;
            }
            attempt++;
        }
        return false;
    }

    private static boolean sleepBeforeRetry(int attempt) {
        long delayMillis = RETRY_BASE_DELAY_MILLIS << Math.min(Math.max(attempt - 1, 0), 2);
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private SendOutcome trySend(String clientId, SseEmitter sseEmitter, GXSseMessageInnerReqDto messageDto) {
        if (!isCurrentEmitter(clientId, sseEmitter)) {
            drops.increment();
            return SendOutcome.DEAD;
        }
        attempts.increment();
        try {
            sendNow(sseEmitter, buildEvent(clientId, messageDto));
            successes.increment();
            return SendOutcome.SENT;
        } catch (IllegalStateException e) {
            drops.increment();
            if (log.isDebugEnabled()) {
                log.debug("Drop SSE message, emitter already completed, clientId: {}, msgId: {}", clientId, messageDto.getMsgId());
            }
            return SendOutcome.DEAD;
        } catch (IOException e) {
            log.warn("SSE message send failed, clientId: {}, msgId: {}, reason: {}", clientId, messageDto.getMsgId(), e.getMessage());
            return SendOutcome.IO_FAILED;
        } catch (RuntimeException e) {
            drops.increment();
            log.warn("Drop SSE message, unexpected error, clientId: {}, type: {}", clientId, e.getClass().getSimpleName());
            return SendOutcome.DEAD;
        }
    }

    private Runnable onCompletionCallBack(String clientId, SseEmitter sseEmitter) {
        return () -> {
            if (log.isInfoEnabled()) {
                log.info("SSE connection completed, clientId: {}", clientId);
            }
            removeEmitter(clientId, sseEmitter);
        };
    }

    private Runnable onTimeoutCallBack(String clientId, SseEmitter sseEmitter) {
        return () -> {
            if (log.isInfoEnabled()) {
                log.info("SSE connection timed out, clientId: {}", clientId);
            }
            removeEmitter(clientId, sseEmitter);
        };
    }

    private Consumer<Throwable> onErrorCallBack(String clientId, SseEmitter sseEmitter) {
        return throwable -> {
            log.warn("SSE connection error, clientId: {}, reason: {}", clientId, throwable.getMessage());
            removeEmitter(clientId, sseEmitter);
        };
    }

    private void removeEmitter(String clientId, SseEmitter sseEmitter) {
        if (sseClientCache.remove(clientId, sseEmitter)) {
            closePipeline(clientId);
            if (log.isDebugEnabled()) {
                log.debug("Removed SSE connection, clientId: {}, activeCount: {}", clientId, sseClientCache.size());
            }
        }
    }

    private void closeAndRemove(String clientId, SseEmitter sseEmitter, String reason) {
        if (sseEmitter == null) {
            return;
        }
        boolean removed = sseClientCache.remove(clientId, sseEmitter);
        if (!removed) {
            return;
        }
        closePipeline(clientId);
        completeQuietly(clientId, sseEmitter);
        if (log.isDebugEnabled()) {
            log.debug("Removed SSE connection, clientId: {}, reason: {}, activeCount: {}",
                    clientId, reason, sseClientCache.size());
        }
    }

    private void closePipeline(String clientId) {
        GXSseClientSendPipeline pipeline = sendPipelines.remove(clientId);
        if (pipeline != null) {
            drops.add(pipeline.close());
        }
    }

    private void completeQuietly(String clientId, SseEmitter sseEmitter) {
        try {
            sseEmitter.complete();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Complete SSE emitter failed, clientId: {}, reason: {}", clientId, e.getMessage());
            }
        }
    }

    private SseEmitter.SseEventBuilder buildEvent(String clientId, GXSseMessageInnerReqDto messageDto) {
        return SseEmitter.event()
                .data(Objects.requireNonNullElseGet(messageDto.getData(), Dict::create), MediaType.APPLICATION_JSON)
                .id(messageDto.getMsgId())
                .comment(messageDto.getComment() + ":" + clientId)
                .reconnectTime(messageDto.getReconnectTimeMillis())
                .name(messageDto.getEventName());
    }

    private boolean isCurrentEmitter(String clientId, SseEmitter sseEmitter) {
        return sseClientCache.get(clientId) == sseEmitter;
    }

    private static void sendNow(SseEmitter sseEmitter, SseEmitter.SseEventBuilder sseEventBuilder) throws IOException {
        sseEmitter.send(sseEventBuilder);
    }

    @Override
    public List<String> splitMessage(@Nullable String msg, int length) {
        if (CharSequenceUtil.isBlank(msg)) {
            return Collections.emptyList();
        }

        int maxChunkLength = Math.max(1, Math.min(length, DEFAULT_SPLIT_LENGTH));
        List<String> msgList = new ArrayList<>();

        int currentIndex = 0;
        int msgLength = msg.length();
        while (currentIndex < msgLength) {
            int endIndex = Math.min(currentIndex + RandomUtil.randomInt(1, maxChunkLength + 1), msgLength);
            if (endIndex < msgLength && Character.isHighSurrogate(msg.charAt(endIndex - 1))
                    && Character.isLowSurrogate(msg.charAt(endIndex))) {
                if (endIndex - currentIndex > 1) {
                    endIndex--;
                } else {
                    endIndex++;
                }
            }

            String subStr = msg.substring(currentIndex, endIndex);

            if (log.isDebugEnabled()) {
                log.debug("Split SSE message, currentIndex: {}, endIndex: {}, chunkLength: {}",
                        currentIndex, endIndex, subStr.length());
            }

            msgList.add(subStr);
            currentIndex = endIndex;
        }

        return msgList;
    }

    GXSseMessageInnerReqDto buildMessageDto(String clientId, String message, String msgId, int chunkIndex, int chunkTotal) {
        String comment = chunkTotal > 1 ? ":chunk=" + chunkIndex + "/" + chunkTotal : "";
        GXSseMessageInnerReqDto.GXSseMessageInnerReqDtoBuilder builder = GXSseMessageInnerReqDto.builder()
                .clientId(clientId)
                .eventName(DEFAULT_EVENT_NAME)
                .data(Dict.create().set("message", message))
                .comment(comment);
        if (chunkTotal > 1) {
            builder.msgId(msgId);
        }
        return builder.build();
    }

    /**
     * Sends a heartbeat to one client and removes the connection when it fails.
     *
     * @param clientId client id, may be blank
     * @return {@code true} when the heartbeat was delivered
     */
    public boolean sendHeartbeat(@Nullable String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return false;
        }

        SseEmitter emitter = sseClientCache.get(clientId);
        if (emitter == null || !isCurrentEmitter(clientId, emitter)) {
            return false;
        }

        try {
            sendNow(emitter, SseEmitter.event().comment("heartbeat"));
            return true;
        } catch (IOException | IllegalStateException e) {
            if (log.isDebugEnabled()) {
                log.debug("Heartbeat send failed, clientId: {}, reason: {}", clientId, e.getMessage());
            }
            closeAndRemove(clientId, emitter, "heartbeat-failed");
            return false;
        }
    }

    /**
     * @return number of currently registered connections
     */
    public int getActiveConnectionCount() {
        return sseClientCache.size();
    }

    /**
     * Closes every connection accepted by the predicate.
     * <p>
     * Batch close is not atomic: the predicate runs on the calling thread, its exception is
     * propagated to the caller, already closed connections stay closed, and connections
     * created concurrently may be missed by this pass.
     * </p>
     *
     * @param predicate client id filter; {@code null} closes nothing
     * @return number of closed connections
     * @throws RuntimeException whatever the predicate throws
     */
    public int closeConnections(@Nullable Function<String, Boolean> predicate) {
        if (predicate == null) {
            return 0;
        }

        int closedCount = 0;
        for (String clientId : sseClientCache.keySet()) {
            if (Boolean.TRUE.equals(predicate.apply(clientId))) {
                closeConnect(clientId);
                closedCount++;
            }
        }

        return closedCount;
    }

    @Override
    public GXSseMetrics getMetrics() {
        return new GXSseMetrics(attempts.sum(), successes.sum(), failures.sum(), retries.sum(),
                drops.sum(), sseClientCache.size());
    }

    /**
     * Releases every connection and both executors. Idempotent; after this call new
     * connections are rejected with a business exception.
     */
    @PreDestroy
    public void destroy() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }
        int closedConnections = 0;
        for (String clientId : new ArrayList<>(sseClientCache.keySet())) {
            if (sseClientCache.containsKey(clientId)) {
                closeConnect(clientId);
                closedConnections++;
            }
        }
        sendPipelines.values().forEach(pipeline -> drops.add(pipeline.close()));
        sendPipelines.clear();
        long awaitMillis = shutdownExecutors();
        if (log.isInfoEnabled()) {
            log.info("SSE service destroyed, closedConnections: {}, awaitMs: {}", closedConnections, awaitMillis);
        }
    }

    private long shutdownExecutors() {
        long startNanos = System.nanoTime();
        ScheduledExecutorService localScheduler = scheduler;
        ExecutorService localExecutor = taskExecutor;
        if (localScheduler == null && localExecutor == null) {
            return 0L;
        }
        if (localScheduler != null) {
            localScheduler.shutdownNow();
        }
        if (localExecutor != null) {
            localExecutor.shutdownNow();
        }
        boolean interrupted = false;
        try {
            if (localScheduler != null
                    && !localScheduler.awaitTermination(DESTROY_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("SSE scheduler did not terminate within {}s", DESTROY_AWAIT_SECONDS);
            }
            if (localExecutor != null
                    && !localExecutor.awaitTermination(DESTROY_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("SSE task executor did not terminate within {}s", DESTROY_AWAIT_SECONDS);
            }
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
        }
        if (interrupted && log.isDebugEnabled()) {
            log.debug("Interrupted while waiting for SSE executors to terminate");
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    /**
     * Result of a single write attempt.
     */
    private enum SendOutcome {
        /**
         * Written successfully.
         */
        SENT,
        /**
         * Write failed with an {@link IOException}; the connection may still be usable.
         */
        IO_FAILED,
        /**
         * The connection is gone or the emitter has already completed.
         */
        DEAD
    }
}
