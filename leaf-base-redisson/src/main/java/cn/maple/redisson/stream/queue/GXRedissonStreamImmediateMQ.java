package cn.maple.redisson.stream.queue;

import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;
import cn.maple.redisson.stream.handler.GXRedissonStreamMessageHandler;
import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.PendingEntry;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Immediate queue backed by Redis Streams consumer groups.
 */
@Slf4j
public class GXRedissonStreamImmediateMQ {

    private static final String FIELD_MESSAGE = "msg";

    private final RedissonClient redissonMQClient;

    private final GXRedissonStreamMQProperties props;

    private final ObjectMapper objectMapper;
    private final String consumerInstanceId;

    private final Map<String, GXRedissonStreamMessageHandler> handlers = new ConcurrentHashMap<>();
    private final Set<String> activeConsumerTopics = ConcurrentHashMap.newKeySet();

    private ExecutorService consumerExecutor;

    private volatile boolean running = false;

    public GXRedissonStreamImmediateMQ(RedissonClient redissonMQClient, GXRedissonStreamMQProperties props) {
        this(redissonMQClient, props, new ObjectMapper());
    }

    public GXRedissonStreamImmediateMQ(RedissonClient redissonMQClient,
                                       GXRedissonStreamMQProperties props,
                                       ObjectMapper objectMapper) {
        if (redissonMQClient == null) {
            throw new IllegalArgumentException("redissonMQClient must not be null");
        }
        if (props == null) {
            throw new IllegalArgumentException("props must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.redissonMQClient = redissonMQClient;
        this.props = props;
        this.objectMapper = objectMapper;
        this.consumerInstanceId = resolveConsumerInstanceId(props);
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        consumerExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("stream-immediate-consumer-", 1).factory());
        for (String topic : handlers.keySet()) {
            startConsumers(topic);
        }
        log.info("Stream immediate queue started, topics={}, consumerThreads={}", handlers.keySet(), props.getConsumerThreads());
    }

    public synchronized void stop() {
        running = false;
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
            try {
                if (!consumerExecutor.awaitTermination(props.getBlockingTimeoutMillis() + 1000, TimeUnit.MILLISECONDS)) {
                    log.warn("Stream immediate queue consumers did not stop before timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            consumerExecutor = null;
        }
        activeConsumerTopics.clear();
        log.info("Stream immediate queue stopped");
    }

    public synchronized void registerHandler(String topic, GXRedissonStreamMessageHandler handler) {
        validateTopic(topic);
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        GXRedissonStreamMessageHandler previous = handlers.putIfAbsent(topic, handler);
        if (previous != null) {
            throw new IllegalStateException("Stream handler already registered for topic [" + topic + "]");
        }
        if (running) {
            startConsumers(topic);
        }
        log.info("Registered stream handler, topic={}", topic);
    }

    public String publish(GXRedissonStreamMessageDto message) {
        validateMessage(message);
        message.setDeliveryTime(System.currentTimeMillis());
        String streamKey = streamKey(message.getTopic());
        RStream<String, String> stream = redissonMQClient.getStream(streamKey);

        String msgJson = objectMapper.writeValueAsString(message);
        StreamMessageId id = stream.add(
                StreamAddArgs.entry(FIELD_MESSAGE, msgJson)
                        .trimNonStrict()
                        .maxLen(props.streamMaxLenAsInt())
                        .noLimit()
        );
        log.debug("Published stream message, topic={}, msgId={}, streamId={}", message.getTopic(), message.getMessageId(), id);
        return id.toString();
    }

    private void consumeLoop(String topic, int threadIdx) {
        String consumerName = consumerName(threadIdx);
        String groupName = props.getConsumerGroup();
        String streamKey = streamKey(topic);
        RStream<String, String> stream = redissonMQClient.getStream(streamKey);

        log.info("Stream consumer started, topic={}, consumer={}", topic, consumerName);

        while (running) {
            try {
                Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(
                        groupName,
                        consumerName,
                        StreamReadGroupArgs.neverDelivered()
                                .count(props.getBatchSize())
                                .timeout(Duration.ofMillis(props.getBlockingTimeoutMillis()))
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    StreamMessageId streamId = entry.getKey();
                    String msgJson = entry.getValue().get(FIELD_MESSAGE);
                    if (msgJson == null) {
                        stream.ack(groupName, streamId);
                        continue;
                    }
                    processMessage(stream, streamKey, topic, groupName, streamId, msgJson);
                }

            } catch (Exception e) {
                if (running) {
                    log.error("Stream consume loop failed, topic={}", topic, e);
                    sleepQuietly(1_000);
                }
            }
        }
        log.info("Stream consumer stopped, topic={}, consumer={}", topic, consumerName);
    }

    void processMessage(RStream<String, String> stream,
                        String streamKey,
                        String topic,
                        String groupName,
                        StreamMessageId streamId,
                        String msgJson) {
        GXRedissonStreamMessageDto message;
        try {
            message = objectMapper.readValue(msgJson, GXRedissonStreamMessageDto.class);
            message.setStreamMessageId(streamId.toString());
        } catch (Exception e) {
            try {
                sendToDlq(topic, msgJson);
            } catch (Exception dlqException) {
                log.error("Failed to move malformed stream message to DLQ, streamId={}", streamId, dlqException);
                return;
            }
            stream.ack(groupName, streamId);
            log.error("Malformed stream message sent to DLQ, streamId={}", streamId, e);
            return;
        }

        GXRedissonStreamMessageHandler handler = handlers.get(topic);
        if (handler == null) {
            log.warn("No stream handler found, ack skipped entry, topic={}", topic);
            stream.ack(groupName, streamId);
            return;
        }

        if (isProcessed(topic, message.getMessageId())) {
            stream.ack(groupName, streamId);
            log.debug("Skipped already processed stream message, msgId={}, streamId={}", message.getMessageId(), streamId);
            return;
        }

        if (isDispatched(topic, streamId.toString())) {
            stream.ack(groupName, streamId);
            log.debug("Skipped already dispatched stream message, msgId={}, streamId={}", message.getMessageId(), streamId);
            return;
        }

        try {
            handler.handle(message);
        } catch (Exception e) {
            log.warn("Stream message handling failed, msgId={}, retry={}/{}",
                    message.getMessageId(), message.getRetryCount(), message.getMaxRetry(), e);

            handleFailedMessage(stream, topic, groupName, streamId, message);
            return;
        }

        try {
            markProcessed(topic, message);
        } catch (Exception e) {
            log.warn("Failed to mark stream message as processed, msgId={}", message.getMessageId(), e);
        }
        stream.ack(groupName, streamId);
        log.debug("Stream message handled, msgId={}, streamId={}", message.getMessageId(), streamId);
    }

    private void handleFailedMessage(RStream<String, String> stream,
                                     String topic,
                                     String groupName,
                                     StreamMessageId streamId,
                                     GXRedissonStreamMessageDto message) {
        try {
            if (message.isExhausted()) {
                sendToDlq(message);
            } else {
                publish(message.nextRetry());
            }
        } catch (Exception ex) {
            log.error("Failed to publish failed stream message result, msgId={}, streamId={}",
                    message.getMessageId(), streamId, ex);
            return;
        }

        try {
            markDispatched(topic, streamId.toString());
        } catch (Exception ex) {
            log.warn("Failed to mark failed stream message as dispatched, msgId={}, streamId={}",
                    message.getMessageId(), streamId, ex);
        }
        stream.ack(groupName, streamId);
    }

    public void recoverPendingMessages(String topic) {
        String streamKey = streamKey(topic);
        String groupName = props.getConsumerGroup();
        RStream<String, String> stream = redissonMQClient.getStream(streamKey);

        try {
            List<PendingEntry> pendingList = stream.listPending(
                    groupName,
                    StreamMessageId.MIN,
                    StreamMessageId.MAX,
                    (int) Math.min(props.getBatchSize() * 2L, 100)
            );

            if (pendingList == null || pendingList.isEmpty()) return;

            long now = System.currentTimeMillis();
            for (PendingEntry pe : pendingList) {
                long idleMs = pe.getIdleTime();
                if (idleMs < props.getMaxPendingMillis()) continue;

                String myConsumer = consumerName("recovery");
                Map<StreamMessageId, Map<String, String>> claimed = stream.claim(
                        groupName,
                        myConsumer,
                        props.getMaxPendingMillis(),
                        TimeUnit.MILLISECONDS,
                        pe.getId()
                );

                if (claimed == null || claimed.isEmpty()) continue;

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : claimed.entrySet()) {
                    String msgJson = entry.getValue().get(FIELD_MESSAGE);
                    if (msgJson == null) {
                        stream.ack(groupName, entry.getKey());
                        continue;
                    }
                    log.info("Recovering pending stream message, streamId={}, idleMillis={}", entry.getKey(), idleMs);
                    processMessage(stream, streamKey, topic, groupName, entry.getKey(), msgJson);
                }
            }
        } catch (Exception e) {
            log.error("Failed to recover pending stream messages, topic={}", topic, e);
        }
    }

    private void sendToDlq(GXRedissonStreamMessageDto message) {
        sendToDlq(message.getTopic(), objectMapper.writeValueAsString(message));
        log.warn("Stream message sent to DLQ, topic={}, msgId={}", message.getTopic(), message.getMessageId());
    }

    private void sendToDlq(String topic, String msgJson) {
        String dlqKey = props.getDlqPrefix() + topic;
        RStream<String, String> dlq = redissonMQClient.getStream(dlqKey);
        dlq.add(StreamAddArgs.entry(FIELD_MESSAGE, msgJson)
                .trimNonStrict()
                .maxLen(props.streamMaxLenAsInt())
                .noLimit());
    }

    private boolean isProcessed(String topic, String messageId) {
        RMapCache<String, String> processed = redissonMQClient.getMapCache(processedKey(topic));
        return processed.containsKey(messageId);
    }

    private boolean isDispatched(String topic, String streamId) {
        RMapCache<String, String> dispatched = redissonMQClient.getMapCache(dispatchedKey(topic));
        return dispatched.containsKey(streamId);
    }

    private void markProcessed(String topic, GXRedissonStreamMessageDto message) {
        RMapCache<String, String> processed = redissonMQClient.getMapCache(processedKey(topic));
        processed.fastPut(message.getMessageId(), Long.toString(System.currentTimeMillis()),
                props.getProcessedMessageTtlMillis(), TimeUnit.MILLISECONDS);
    }

    private void markDispatched(String topic, String streamId) {
        RMapCache<String, String> dispatched = redissonMQClient.getMapCache(dispatchedKey(topic));
        dispatched.fastPut(streamId, Long.toString(System.currentTimeMillis()),
                props.getProcessedMessageTtlMillis(), TimeUnit.MILLISECONDS);
    }

    private String processedKey(String topic) {
        return props.getProcessedMessagePrefix() + topic;
    }

    private String dispatchedKey(String topic) {
        return props.getProcessedMessagePrefix() + topic + ":dispatched";
    }

    public void ensureGroupExists(String topic) {
        String streamKey = streamKey(topic);
        RStream<String, String> stream = redissonMQClient.getStream(streamKey);
        try {
            stream.createGroup(StreamCreateGroupArgs.name(props.getConsumerGroup()).id(StreamMessageId.ALL).makeStream());
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || !message.contains("BUSYGROUP")) {
                log.warn("Failed to create stream group, topic={}, error={}", topic, message);
            }
        }
    }

    public Set<String> registeredTopics() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    private String streamKey(String topic) {
        return props.getStreamPrefix() + topic;
    }

    private String consumerName(int threadIdx) {
        return consumerName(Integer.toString(threadIdx));
    }

    private String consumerName(String suffix) {
        if (props.isAppendInstanceIdToConsumerName()) {
            return props.getConsumerName() + "-" + consumerInstanceId + "-" + suffix;
        }
        return props.getConsumerName() + "-" + suffix;
    }

    private String resolveConsumerInstanceId(GXRedissonStreamMQProperties properties) {
        String configured = properties.getConsumerInstanceId();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return UUID.randomUUID().toString();
    }

    private void startConsumers(String topic) {
        if (!running || consumerExecutor == null) {
            return;
        }
        if (!activeConsumerTopics.add(topic)) {
            return;
        }
        ensureGroupExists(topic);
        for (int i = 0; i < props.getConsumerThreads(); i++) {
            final int idx = i;
            consumerExecutor.submit(() -> consumeLoop(topic, idx));
        }
    }

    private void validateMessage(GXRedissonStreamMessageDto message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        validateTopic(message.getTopic());
        if (message.getPayload() == null) {
            throw new IllegalArgumentException("message payload must not be null");
        }
    }

    private void validateTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
