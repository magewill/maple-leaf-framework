package cn.maple.redisson.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.stream.GXRedissonStreamMQManager;
import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;
import cn.maple.redisson.stream.handler.GXRedissonStreamMessageHandler;
import cn.maple.redisson.properties.GXRedissonStreamMQProperties;

import java.util.concurrent.TimeUnit;

/**
 * Utility methods for Redis Streams based MQ.
 */
public final class GXRedissonStreamMQUtils {
    private static final String MANAGER_BEAN_NAME = "redissonStreamMessageQueueManager";

    private GXRedissonStreamMQUtils() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    @Deprecated(since = "4.3.0", forRemoval = false)
    public static void start() {
        getMessageQueueManager().start();
    }

    @Deprecated(since = "4.3.0", forRemoval = false)
    public static void stop() {
        getMessageQueueManager().stop();
    }

    public static boolean isStarted() {
        return getMessageQueueManager().isStarted();
    }

    public static void subscribe(String topic, GXRedissonStreamMessageHandler handler) {
        validateTopic(topic);
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }
        getMessageQueueManager().subscribe(topic, handler);
    }

    public static <T> String sendImmediate(String topic, T payload) {
        validatePayload(topic, payload);
        return getMessageQueueManager().sendImmediate(topic, payload);
    }

    public static <T> String sendImmediate(String topic, T payload, int maxRetry) {
        validatePayload(topic, payload);
        validateMaxRetry(maxRetry);
        return getMessageQueueManager().sendImmediate(topic, payload, maxRetry);
    }

    public static String sendImmediate(GXRedissonStreamMessageDto message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return getMessageQueueManager().sendImmediate(message);
    }

    public static <T> String sendDelayed(String topic, T payload, long delayTime, TimeUnit timeUnit) {
        validateDelayPayload(topic, payload, delayTime, timeUnit);
        return getMessageQueueManager().sendDelayed(topic, payload, delayTime, timeUnit);
    }

    public static <T> String sendDelayed(String topic, T payload, long delayTime, TimeUnit timeUnit, int maxRetry) {
        validateDelayPayload(topic, payload, delayTime, timeUnit);
        validateMaxRetry(maxRetry);
        return getMessageQueueManager().sendDelayed(topic, payload, delayTime, timeUnit, maxRetry);
    }

    public static String sendDelayed(GXRedissonStreamMessageDto message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        return getMessageQueueManager().sendDelayed(message);
    }

    public static boolean cancelDelay(String topic, String messageId) {
        validateTopic(topic);
        if (CharSequenceUtil.isBlank(messageId)) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        return getMessageQueueManager().cancelDelay(topic, messageId);
    }

    public static GXRedissonStreamMQProperties getProperties() {
        return getMessageQueueManager().getProps();
    }

    public static GXRedissonStreamMQManager getMessageQueueManager() {
        GXRedissonStreamMQManager manager = GXSpringContextUtils.getBean(MANAGER_BEAN_NAME, GXRedissonStreamMQManager.class);
        if (manager == null) {
            manager = GXSpringContextUtils.getBean(GXRedissonStreamMQManager.class);
        }
        if (manager == null) {
            throw new GXBusinessException("Unable to get redissonStreamMessageQueueManager bean");
        }
        return manager;
    }

    private static void validateDelayPayload(String topic, Object payload, long delayTime, TimeUnit timeUnit) {
        validatePayload(topic, payload);
        if (delayTime <= 0) {
            throw new IllegalArgumentException("delayTime must be greater than 0");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null");
        }
    }

    private static void validatePayload(String topic, Object payload) {
        validateTopic(topic);
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
    }

    private static void validateTopic(String topic) {
        if (CharSequenceUtil.isBlank(topic)) {
            throw new IllegalArgumentException("topic must not be blank");
        }
    }

    private static void validateMaxRetry(int maxRetry) {
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry must not be negative");
        }
    }
}
