package cn.maple.sse.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.impl.GXBusinessServiceImpl;
import cn.maple.sse.dto.GXSseMessageInnerReqDto;
import cn.maple.sse.service.GXSseEmitterService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Server-Sent Events service implementation.
 */
@Slf4j
@Service
public class GXSseEmitterServiceImpl extends GXBusinessServiceImpl implements GXSseEmitterService {
    private static final long DEFAULT_MIN_TIMEOUT_SECONDS = 60L;
    private static final String DEFAULT_EVENT_NAME = "Push Msg";
    private static final int MAX_RETRIES = 3;

    private final Map<String, SseEmitter> sseClientCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("gx-sse-scheduler-", 0).factory());

    public GXSseEmitterServiceImpl() {
        scheduler.scheduleAtFixedRate(this::cleanupConnections, 1, 1, TimeUnit.MINUTES);
    }

    private static void sendNow(SseEmitter sseEmitter, SseEmitter.SseEventBuilder sseEventBuilder) throws IOException {
        sseEmitter.send(sseEventBuilder);
    }

    private void cleanupConnections() {
        sseClientCache.forEach((clientId, emitter) -> {
            if (!isCurrentEmitter(clientId, emitter)) {
                return;
            }
            try {
                sendNow(emitter, SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                log.debug("Remove invalid SSE connection, clientId: {}, reason: {}", clientId, e.getMessage());
                removeEmitter(clientId, emitter);
            }
        });
    }

    @Override
    public SseEmitter getSseEmitterByClientId(String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return null;
        }
        return sseClientCache.get(clientId);
    }

    @Override
    public SseEmitter createSseConnect(String clientId, long timeout) {
        long effectiveTimeout = Math.max(timeout, DEFAULT_MIN_TIMEOUT_SECONDS);
        SseEmitter sseEmitter = new SseEmitter(TimeUnit.SECONDS.toMillis(effectiveTimeout));
        String finalClientId = CharSequenceUtil.isBlank(clientId) ? IdUtil.fastSimpleUUID() : clientId;

        sseEmitter.onCompletion(onCompletionCallBack(finalClientId, sseEmitter));
        sseEmitter.onTimeout(onTimeoutCallBack(finalClientId, sseEmitter));
        sseEmitter.onError(onErrorCallBack(finalClientId, sseEmitter));

        SseEmitter oldEmitter = sseClientCache.put(finalClientId, sseEmitter);
        if (oldEmitter != null) {
            oldEmitter.complete();
        }
        log.info("Created SSE connection, clientId: {}, activeCount: {}", finalClientId, sseClientCache.size());

        try {
            SseEmitter.SseEventBuilder sseEventBuilder = SseEmitter.event()
                    .id(String.valueOf(HttpStatus.HTTP_CREATED))
                    .data(finalClientId, MediaType.APPLICATION_JSON);
            sendNow(sseEmitter, sseEventBuilder);
            return sseEmitter;
        } catch (IOException | IllegalStateException ex) {
            removeEmitter(finalClientId, sseEmitter);
            throw new GXBusinessException("Create SSE connection failed", ex);
        }
    }

    @Override
    public void sendMessageToAllClient(String msg) {
        if (sseClientCache.isEmpty()) {
            log.debug("No active SSE connection, broadcast skipped");
            return;
        }

        if (CharSequenceUtil.isBlank(msg)) {
            log.warn("Skip broadcasting blank SSE message");
            return;
        }

        sseClientCache.forEach((clientId, emitter) -> {
            GXSseMessageInnerReqDto messageDto = buildMessageDto(clientId, msg);
            sendMsgToClientByClientId(clientId, messageDto, emitter);
        });
    }

    @Override
    public void sendMessageToOneClient(String clientId, String msg, boolean splitMsg) {
        if (CharSequenceUtil.isBlank(clientId) || CharSequenceUtil.isBlank(msg)) {
            log.warn("Send SSE message failed: clientId or message is blank");
            return;
        }

        SseEmitter emitter = sseClientCache.get(clientId);
        if (emitter == null) {
            log.warn("Send SSE message failed: no connection found for clientId: {}", clientId);
            return;
        }

        List<String> msgList = splitMsg ? splitMessage(msg, RandomUtil.randomInt(1, 8)) : List.of(msg);
        for (String message : msgList) {
            GXSseMessageInnerReqDto messageDto = buildMessageDto(clientId, message);
            sendMsgToClientByClientId(clientId, messageDto, emitter);
        }
    }

    @Override
    public void closeConnect(String clientId) {
        if (CharSequenceUtil.isBlank(clientId)) {
            return;
        }

        SseEmitter sseEmitter = sseClientCache.remove(clientId);
        if (sseEmitter == null) {
            return;
        }

        try {
            SseEmitter.SseEventBuilder closeEvent = SseEmitter.event()
                    .name("close")
                    .data("Connection closed by server", MediaType.TEXT_PLAIN);
            sendNow(sseEmitter, closeEvent);
        } catch (IOException | IllegalStateException e) {
            log.debug("Failed to send SSE close event, clientId: {}, reason: {}", clientId, e.getMessage());
        } finally {
            sseEmitter.complete();
            log.info("Closed SSE connection, clientId: {}, activeCount: {}", clientId, sseClientCache.size());
        }
    }

    private void sendMsgToClientByClientId(String clientId, GXSseMessageInnerReqDto messageDto, SseEmitter sseEmitter) {
        if (sseEmitter == null) {
            log.error("Send SSE message failed: no connection found for clientId: {}, message: {}", clientId, messageDto);
            return;
        }
        if (!isCurrentEmitter(clientId, sseEmitter)) {
            log.debug("Skip sending SSE message to inactive connection, clientId: {}", clientId);
            return;
        }

        send(clientId, sseEmitter, messageDto, 1);
    }

    private Runnable onCompletionCallBack(String clientId, SseEmitter sseEmitter) {
        return () -> {
            log.info("SSE connection completed, clientId: {}", clientId);
            removeEmitter(clientId, sseEmitter);
        };
    }

    private Runnable onTimeoutCallBack(String clientId, SseEmitter sseEmitter) {
        return () -> {
            log.info("SSE connection timed out, clientId: {}", clientId);
            removeEmitter(clientId, sseEmitter);
        };
    }

    private Consumer<Throwable> onErrorCallBack(String clientId, SseEmitter sseEmitter) {
        return throwable -> {
            log.error("SSE connection error, clientId: {}, reason: {}", clientId, throwable.getMessage());
            removeEmitter(clientId, sseEmitter);
        };
    }

    private void removeEmitter(String clientId, SseEmitter sseEmitter) {
        if (sseClientCache.remove(clientId, sseEmitter)) {
            log.info("Removed SSE connection, clientId: {}, activeCount: {}", clientId, sseClientCache.size());
        }
    }

    private void send(String clientId, SseEmitter sseEmitter, GXSseMessageInnerReqDto messageDto, int retryCount) {
        if (!isCurrentEmitter(clientId, sseEmitter)) {
            return;
        }

        try {
            sendNow(sseEmitter, buildEvent(clientId, messageDto));
        } catch (IOException | IllegalStateException e) {
            log.error("SSE message send failed, clientId: {}, reason: {}", clientId, e.getMessage());
            retryWithBackoff(clientId, sseEmitter, messageDto, retryCount);
        }
    }

    private void retryWithBackoff(String clientId, SseEmitter sseEmitter,
                                  GXSseMessageInnerReqDto messageDto, int retryCount) {
        if (retryCount > MAX_RETRIES) {
            log.warn("SSE message send failed after retries, clientId: {}", clientId);
            removeEmitter(clientId, sseEmitter);
            return;
        }

        long delayMs = (long) Math.pow(2, retryCount - 1) * 100;
        try {
            scheduler.schedule(() -> {
                if (!isCurrentEmitter(clientId, sseEmitter)) {
                    return;
                }

                try {
                    log.debug("Retry SSE message send, clientId: {}, retryCount: {}", clientId, retryCount);
                    sendNow(sseEmitter, buildEvent(clientId, messageDto));
                } catch (IOException | IllegalStateException e) {
                    log.error("Retry SSE message send failed, clientId: {}, retryCount: {}, reason: {}",
                            clientId, retryCount, e.getMessage());
                    retryWithBackoff(clientId, sseEmitter, messageDto, retryCount + 1);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            if (!scheduler.isShutdown()) {
                log.error("Schedule SSE retry failed, clientId: {}, reason: {}", clientId, e.getMessage());
                removeEmitter(clientId, sseEmitter);
            }
        }
    }

    private SseEmitter.SseEventBuilder buildEvent(String clientId, GXSseMessageInnerReqDto messageDto) {
        return SseEmitter.event()
                .data(Objects.requireNonNull(messageDto.getData(), "SSE message data must not be null"),
                        MediaType.APPLICATION_JSON)
                .id(messageDto.getMsgId())
                .comment(messageDto.getComment() + ":" + clientId)
                .reconnectTime(messageDto.getReconnectTimeMillis())
                .name(messageDto.getEventName());
    }

    private boolean isCurrentEmitter(String clientId, SseEmitter sseEmitter) {
        return sseClientCache.get(clientId) == sseEmitter;
    }

    @Override
    public List<String> splitMessage(String msg, int length) {
        if (CharSequenceUtil.isEmpty(msg)) {
            return Collections.emptyList();
        }

        int maxChunkLength = Math.max(1, Math.min(length, 100));
        List<String> msgList = new ArrayList<>();

        int currentIndex = 0;
        while (currentIndex < msg.length()) {
            int chunkLength = RandomUtil.randomInt(1, maxChunkLength + 1);
            int endIndex = Math.min(currentIndex + chunkLength, msg.length());
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

    private GXSseMessageInnerReqDto buildMessageDto(String clientId, String message) {
        return GXSseMessageInnerReqDto.builder()
                .clientId(clientId)
                .msgId(IdUtil.fastUUID())
                .eventName(DEFAULT_EVENT_NAME)
                .data(Dict.create().set("message", message))
                .build();
    }

    public boolean sendHeartbeat(String clientId) {
        SseEmitter emitter = sseClientCache.get(clientId);
        if (emitter == null || !isCurrentEmitter(clientId, emitter)) {
            return false;
        }

        try {
            sendNow(emitter, SseEmitter.event().comment("heartbeat"));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Heartbeat send failed, clientId: {}, reason: {}", clientId, e.getMessage());
            removeEmitter(clientId, emitter);
            return false;
        }
    }

    public int getActiveConnectionCount() {
        return sseClientCache.size();
    }

    public int closeConnections(Function<String, Boolean> predicate) {
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

    @PreDestroy
    public void destroy() {
        sseClientCache.keySet().forEach(this::closeConnect);
        scheduler.shutdown();
    }
}
