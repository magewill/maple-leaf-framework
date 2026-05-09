package cn.maple.redisson.stream;

import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;
import cn.maple.redisson.stream.handler.GXRedissonStreamMessageHandler;
import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import cn.maple.redisson.stream.queue.GXRedissonStreamDelayedMQ;
import cn.maple.redisson.stream.queue.GXRedissonStreamImmediateMQ;
import cn.maple.redisson.stream.queue.GXRedissonStreamPendingMessageRecovery;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.SmartLifecycle;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/**
 * Facade for Redis Streams based message queues.
 */
@Slf4j
public class GXRedissonStreamMQManager implements AutoCloseable, SmartLifecycle {

    private final RedissonClient redissonMQClient;
    @Getter
    private final GXRedissonStreamMQProperties props;
    @Getter
    private final GXRedissonStreamImmediateMQ immediateQueue;
    @Getter
    private final GXRedissonStreamDelayedMQ delayedQueue;
    private final GXRedissonStreamPendingMessageRecovery pendingRecovery;
    private final ObjectMapper objectMapper;
    @Getter
    private volatile boolean started = false;

    public GXRedissonStreamMQManager(RedissonClient redissonMQClient,
                                     GXRedissonStreamMQProperties props) {
        this(redissonMQClient, props, new ObjectMapper());
    }

    public GXRedissonStreamMQManager(RedissonClient redissonMQClient,
                                     GXRedissonStreamMQProperties props,
                                     ObjectMapper objectMapper) {
        this.redissonMQClient = redissonMQClient;
        this.props = props;
        this.objectMapper = objectMapper;
        validateProperties(props);
        this.immediateQueue = new GXRedissonStreamImmediateMQ(redissonMQClient, props, objectMapper);
        this.delayedQueue = new GXRedissonStreamDelayedMQ(redissonMQClient, props, immediateQueue, objectMapper);
        this.pendingRecovery = new GXRedissonStreamPendingMessageRecovery(redissonMQClient, props, immediateQueue);
    }

    public synchronized void start() {
        if (started) return;
        immediateQueue.start();
        delayedQueue.start();
        pendingRecovery.start();
        started = true;
        log.info("Redisson stream message queue manager started");
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        pendingRecovery.stop();
        delayedQueue.stop();
        immediateQueue.stop();
        started = false;
        log.info("Redisson stream message queue manager stopped");
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return started;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public void close() {
        stop();
    }

    public void subscribe(String topic, GXRedissonStreamMessageHandler handler) {
        immediateQueue.registerHandler(topic, handler);
    }

    public <T> String sendImmediate(String topic, T payload) {
        return sendImmediate(topic, payload, 3);
    }

    public <T> String sendImmediate(String topic, T payload, int maxRetry) {
        validateSendArgs(topic, payload, maxRetry);
        GXRedissonStreamMessageDto message = GXRedissonStreamMessageDto.builder()
                .topic(topic)
                .payload(objectMapper.writeValueAsString(payload))
                .payloadClass(payload.getClass().getName())
                .maxRetry(maxRetry)
                .build();
        return immediateQueue.publish(message);
    }

    public String sendImmediate(GXRedissonStreamMessageDto message) {
        validateMessage(message);
        message.normalizeDefaults();
        return immediateQueue.publish(message);
    }

    public <T> String sendDelayed(String topic, T payload, long delay, TimeUnit unit) {
        return sendDelayed(topic, payload, delay, unit, 3);
    }

    public <T> String sendDelayed(String topic, T payload, long delay, TimeUnit unit, int maxRetry) {
        validateSendArgs(topic, payload, maxRetry);
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }
        long delayMillis = unit.toMillis(delay);
        if (delayMillis <= 0) {
            throw new IllegalArgumentException("delay must be greater than 0 milliseconds");
        }
        GXRedissonStreamMessageDto message = GXRedissonStreamMessageDto.delayed(
                topic,
                objectMapper.writeValueAsString(payload),
                payload.getClass().getName(),
                delayMillis
        );
        message.setMaxRetry(maxRetry);
        delayedQueue.publish(message);
        return message.getMessageId();
    }

    public String sendDelayed(GXRedissonStreamMessageDto message) {
        validateMessage(message);
        message.normalizeDefaults();
        delayedQueue.publish(message);
        return message.getMessageId();
    }

    public boolean cancelDelay(String topic, String messageId) {
        return delayedQueue.cancel(topic, messageId);
    }

    private void validateSendArgs(String topic, Object payload, int maxRetry) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry must not be negative");
        }
    }

    private void validateMessage(GXRedissonStreamMessageDto message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        validateSendArgs(message.getTopic(), message.getPayload(), message.getMaxRetry());
        if (message.getPayloadClass() == null || message.getPayloadClass().isBlank()) {
            throw new IllegalArgumentException("message payloadClass must not be blank");
        }
    }

    private void validateProperties(GXRedissonStreamMQProperties properties) {
        if (redissonMQClient == null) {
            throw new IllegalArgumentException("redissonMQClient must not be null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("props must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        properties.validate();
    }
}
