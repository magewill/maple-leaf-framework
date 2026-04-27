package cn.maple.redisson.util;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for Redisson delayed queues.
 */
public final class GXRedissonDelayMQUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonDelayMQUtils.class);

    @SuppressWarnings("deprecation")
    private static final ConcurrentHashMap<String, RDelayedQueue<String>> DELAYED_QUEUE_CACHE = new ConcurrentHashMap<>();

    private GXRedissonDelayMQUtils() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    @SuppressWarnings("deprecation")
    public static void sendDelayMessage(String queueName, Object message, long delayTime, TimeUnit timeUnit) {
        validateParameters(queueName, message, delayTime, timeUnit);

        try {
            String msg = convertMessageToString(message);
            getDelayedQueue(queueName).offer(msg, delayTime, timeUnit);
            LOGGER.debug("Sent delayed message to queue [{}], delay={} {}", queueName, delayTime, timeUnit);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to send delayed message to queue [{}]", queueName, e);
            throw new GXBusinessException("Failed to send delayed message: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("deprecation")
    public static CompletableFuture<Void> sendAsyncDelayMessage(String queueName, Object message, long delayTime, TimeUnit timeUnit) {
        try {
            validateParameters(queueName, message, delayTime, timeUnit);
            String msg = convertMessageToString(message);
            return getDelayedQueue(queueName)
                    .offerAsync(msg, delayTime, timeUnit)
                    .toCompletableFuture();
        } catch (GXBusinessException | IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            LOGGER.error("Failed to send delayed message asynchronously to queue [{}]", queueName, e);
            return CompletableFuture.failedFuture(new GXBusinessException("Failed to send delayed message: " + e.getMessage(), e));
        }
    }

    @SuppressWarnings("deprecation")
    public static RDelayedQueue<String> getDelayedQueue(String queueName) {
        validateQueueName(queueName);
        return DELAYED_QUEUE_CACHE.computeIfAbsent(queueName, name -> {
            RedissonClient redissonMQClient = getRedissonMQClient();
            RBlockingQueue<String> destinationQueue = redissonMQClient.getBlockingQueue(name);
            return redissonMQClient.getDelayedQueue(destinationQueue);
        });
    }

    public static boolean removeDelayMessage(String queueName, Object message) {
        validateQueueName(queueName);
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }

        String msg = convertMessageToString(message);
        boolean removed = getDelayedQueue(queueName).remove(msg);
        if (removed) {
            LOGGER.info("Removed delayed message from queue [{}]", queueName);
        } else {
            LOGGER.warn("Delayed message was not found in queue [{}]", queueName);
        }
        return removed;
    }

    public static int removeDelayMessages(String queueName, List<Object> messages) {
        validateQueueName(queueName);
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }

        RDelayedQueue<String> delayedQueue = getDelayedQueue(queueName);
        int removedCount = 0;
        for (Object message : messages) {
            if (message != null && delayedQueue.remove(convertMessageToString(message))) {
                removedCount++;
            }
        }
        LOGGER.info("Removed {} delayed messages from queue [{}]", removedCount, queueName);
        return removedCount;
    }

    public static int clearDelayQueue(String queueName) {
        validateQueueName(queueName);
        RDelayedQueue<String> delayedQueue = getDelayedQueue(queueName);
        int size = delayedQueue.size();
        delayedQueue.clear();
        LOGGER.warn("Cleared delayed queue [{}], removed {} messages", queueName, size);
        return size;
    }

    public static int getDelayQueueSize(String queueName) {
        validateQueueName(queueName);
        return getDelayedQueue(queueName).size();
    }

    private static String convertMessageToString(Object message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (message instanceof String value) {
            return value;
        }
        try {
            if (!ClassUtil.isBasicType(message.getClass())) {
                return JSONUtil.toJsonStr(message);
            }
            return Convert.convert(String.class, message);
        } catch (Exception e) {
            LOGGER.error("Failed to convert delayed message to String", e);
            throw new GXBusinessException("Failed to convert delayed message: " + e.getMessage(), e);
        }
    }

    private static void validateParameters(String queueName, Object message, long delayTime, TimeUnit timeUnit) {
        validateQueueName(queueName);
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        if (delayTime <= 0) {
            throw new IllegalArgumentException("delayTime must be greater than 0");
        }
        if (timeUnit == null) {
            throw new IllegalArgumentException("timeUnit must not be null");
        }
    }

    private static void validateQueueName(String queueName) {
        if (CharSequenceUtil.isBlank(queueName)) {
            throw new IllegalArgumentException("queueName must not be blank");
        }
    }

    private static RedissonClient getRedissonMQClient() {
        RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
        if (redissonMQClient == null) {
            throw new GXBusinessException("Unable to get redissonMQClient bean");
        }
        return redissonMQClient;
    }
}
