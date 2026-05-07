package cn.maple.redisson.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Utility methods for Redisson reliable topics.
 */
public final class GXRedissonMQUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonMQUtils.class);
    private static final String CACHE_KEY_SEPARATOR = "\u001F";
    private static final AtomicLong FORCE_SUBSCRIBE_SEQUENCE = new AtomicLong(0);

    private static final ConcurrentHashMap<String, RReliableTopic> TOPIC_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ListenerRegistration> LISTENER_REGISTRATION_CACHE = new ConcurrentHashMap<>();

    private GXRedissonMQUtils() {
        throw new UnsupportedOperationException("Utility class must not be instantiated");
    }

    public static long publish(String topicName, Object message) {
        validatePublishParameters(topicName, message);
        try {
            long subscriberCount = getReliableTopic(topicName).publish(message);
            LOGGER.debug("Published message to topic [{}], subscriberCount={}", topicName, subscriberCount);
            return subscriberCount;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to publish message to topic [{}]", topicName, e);
            throw new GXBusinessException("Failed to publish message: " + e.getMessage(), e);
        }
    }

    public static CompletableFuture<Long> publishAsync(String topicName, Object message) {
        try {
            validatePublishParameters(topicName, message);
            return getReliableTopic(topicName)
                    .publishAsync(message)
                    .toCompletableFuture()
                    .whenComplete((count, ex) -> {
                        if (ex != null) {
                            LOGGER.error("Failed to publish message asynchronously to topic [{}]", topicName, ex);
                        } else {
                            LOGGER.debug("Published message asynchronously to topic [{}], subscriberCount={}", topicName, count);
                        }
                    });
        } catch (GXBusinessException | IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            LOGGER.error("Failed to publish message asynchronously to topic [{}]", topicName, e);
            return CompletableFuture.failedFuture(new GXBusinessException("Failed to publish message: " + e.getMessage(), e));
        }
    }

    /**
     * Subscribe once per topic and message class for the current application instance.
     */
    public static <T> String subscribe(String topicName, Class<T> messageClass, MessageListener<T> listener) {
        validateSubscribeParameters(topicName, messageClass, listener);

        String cacheKey = generateCacheKey(topicName, messageClass);
        ListenerRegistration registration = LISTENER_REGISTRATION_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                RReliableTopic reliableTopic = getReliableTopic(topicName);
                String listenerId = reliableTopic.addListener(messageClass, wrapListener(topicName, listener));
                LOGGER.info("Subscribed topic [{}], messageClass={}, listenerId={}",
                        topicName, messageClass.getSimpleName(), listenerId);
                return new ListenerRegistration(cacheKey, topicName, messageClass.getName(), listenerId, false);
            } catch (Exception e) {
                LOGGER.error("Failed to subscribe topic [{}]", topicName, e);
                throw new GXBusinessException("Failed to subscribe topic: " + e.getMessage(), e);
            }
        });
        return registration.listenerId();
    }

    /**
     * Force a new subscription even if the same topic and message class already
     * have a listener. Prefer {@link #subscribe(String, Class, MessageListener)}
     * unless duplicate delivery is intentional.
     */
    public static <T> String forceSubscribe(String topicName, Class<T> messageClass, MessageListener<T> listener) {
        validateSubscribeParameters(topicName, messageClass, listener);

        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            String listenerId = reliableTopic.addListener(messageClass, wrapListener(topicName, listener));
            String cacheKey = generateForceCacheKey(topicName, messageClass);
            LISTENER_REGISTRATION_CACHE.put(
                    cacheKey,
                    new ListenerRegistration(cacheKey, topicName, messageClass.getName(), listenerId, true)
            );
            LOGGER.warn("Force subscribed topic [{}], messageClass={}, listenerId={}. Duplicate delivery may occur.",
                    topicName, messageClass.getSimpleName(), listenerId);
            return listenerId;
        } catch (Exception e) {
            LOGGER.error("Failed to force subscribe topic [{}]", topicName, e);
            throw new GXBusinessException("Failed to force subscribe topic: " + e.getMessage(), e);
        }
    }

    public static void unsubscribe(String topicName, String... listenerIds) {
        validateTopicName(topicName);
        if (listenerIds == null || listenerIds.length == 0) {
            LOGGER.warn("listenerIds is empty, skip unsubscribe");
            return;
        }

        List<String> idList = Arrays.stream(listenerIds)
                .filter(CharSequenceUtil::isNotBlank)
                .toList();
        if (idList.isEmpty()) {
            LOGGER.warn("listenerIds is empty, skip unsubscribe");
            return;
        }

        try {
            getReliableTopic(topicName).removeListener(idList.toArray(String[]::new));
            LISTENER_REGISTRATION_CACHE.entrySet().removeIf(entry ->
                    topicName.equals(entry.getValue().topicName()) && idList.contains(entry.getValue().listenerId()));
            LOGGER.info("Unsubscribed topic [{}], listenerIds={}", topicName, idList);
        } catch (Exception e) {
            LOGGER.error("Failed to unsubscribe topic [{}], listenerIds={}", topicName, idList, e);
            throw new GXBusinessException("Failed to unsubscribe topic: " + e.getMessage(), e);
        }
    }

    /**
     * Unsubscribe by the cache key returned by {@link #getAllLocalListeners()}.
     */
    public static void unsubscribeByCacheKey(String cacheKey) {
        if (CharSequenceUtil.isBlank(cacheKey)) {
            throw new IllegalArgumentException("cacheKey must not be blank");
        }
        ListenerRegistration registration = LISTENER_REGISTRATION_CACHE.get(cacheKey);
        if (registration == null) {
            LOGGER.warn("Listener cache key [{}] was not found", cacheKey);
            return;
        }
        unsubscribe(registration.topicName(), registration.listenerId());
    }

    /**
     * Redisson 4.3.1 exposes listener removal here. This method is kept as a
     * compatibility alias for callers that used the old name.
     */
    public static void removeSubscriber(String topicName, String... listenerIds) {
        unsubscribe(topicName, listenerIds);
    }

    public static int publishBatch(String topicName, List<Object> messages) {
        validateTopicName(topicName);
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }

        int successCount = 0;
        RReliableTopic reliableTopic = getReliableTopic(topicName);
        for (Object message : messages) {
            if (message == null) {
                LOGGER.warn("Skip null message while publishing batch to topic [{}]", topicName);
                continue;
            }
            try {
                reliableTopic.publish(message);
                successCount++;
            } catch (Exception e) {
                LOGGER.error("Failed to publish one batch message to topic [{}]", topicName, e);
            }
        }
        LOGGER.info("Published batch to topic [{}], success={}, total={}", topicName, successCount, messages.size());
        return successCount;
    }

    public static int countSubscribers(String topicName) {
        validateTopicName(topicName);
        try {
            return getReliableTopic(topicName).countSubscribers();
        } catch (Exception e) {
            LOGGER.error("Failed to count subscribers for topic [{}]", topicName, e);
            throw new GXBusinessException("Failed to count subscribers: " + e.getMessage(), e);
        }
    }

    public static Map<String, String> getAllLocalListeners() {
        Map<String, String> result = new HashMap<>();
        LISTENER_REGISTRATION_CACHE.forEach((key, registration) -> result.put(key, registration.listenerId()));
        return result;
    }

    public static Map<String, String> getLocalListenersByTopic(String topicName) {
        validateTopicName(topicName);
        Map<String, String> result = new HashMap<>();
        LISTENER_REGISTRATION_CACHE.forEach((key, registration) -> {
            if (topicName.equals(registration.topicName())) {
                result.put(registration.messageClassName(), registration.listenerId());
            }
        });
        return result;
    }

    public static boolean hasListener(String topicName, Class<?> messageClass) {
        validateTopicName(topicName);
        if (messageClass == null) {
            return false;
        }
        return LISTENER_REGISTRATION_CACHE.containsKey(generateCacheKey(topicName, messageClass));
    }

    public static List<String> getAllSubscribedTopics() {
        return LISTENER_REGISTRATION_CACHE.values().stream()
                .map(ListenerRegistration::topicName)
                .distinct()
                .collect(Collectors.toList());
    }

    public static String getCachedListenerId(String topicName, Class<?> messageClass) {
        validateTopicName(topicName);
        if (messageClass == null) {
            return null;
        }
        ListenerRegistration registration = LISTENER_REGISTRATION_CACHE.get(generateCacheKey(topicName, messageClass));
        return registration == null ? null : registration.listenerId();
    }

    public static String getTopicNameByCacheKey(String cacheKey) {
        ListenerRegistration registration = LISTENER_REGISTRATION_CACHE.get(cacheKey);
        return registration == null ? null : registration.topicName();
    }

    public static boolean isSubscribed(String topicName, Class<?> messageClass) {
        return getCachedListenerId(topicName, messageClass) != null;
    }

    public static RReliableTopic getReliableTopic(String topicName) {
        validateTopicName(topicName);
        return TOPIC_CACHE.computeIfAbsent(topicName, name -> getRedissonMQClient().getReliableTopic(name));
    }

    public static void clearTopicCache(String topicName) {
        if (CharSequenceUtil.isBlank(topicName)) {
            LISTENER_REGISTRATION_CACHE.values().forEach(registration -> {
                try {
                    getReliableTopic(registration.topicName()).removeListener(registration.listenerId());
                } catch (Exception e) {
                    LOGGER.error("Failed to remove listener [{}] from topic [{}]",
                            registration.listenerId(), registration.topicName(), e);
                }
            });
            LISTENER_REGISTRATION_CACHE.clear();
            TOPIC_CACHE.clear();
            LOGGER.info("Cleared all local topic caches and listeners");
            return;
        }

        List<String> listenerIds = LISTENER_REGISTRATION_CACHE.values().stream()
                .filter(registration -> topicName.equals(registration.topicName()))
                .map(ListenerRegistration::listenerId)
                .toList();
        listenerIds.forEach(listenerId -> unsubscribe(topicName, listenerId));
        TOPIC_CACHE.remove(topicName);
        LOGGER.info("Cleared local topic cache [{}]", topicName);
    }

    public static void clearLocalCache() {
        LISTENER_REGISTRATION_CACHE.clear();
        TOPIC_CACHE.clear();
        LOGGER.info("Cleared local reliable topic cache");
    }

    public static String getInstanceId() {
        return InstanceIdHolder.get();
    }

    private static <T> MessageListener<T> wrapListener(String topicName, MessageListener<T> listener) {
        return (channel, msg) -> {
            try {
                listener.onMessage(channel, msg);
            } catch (Exception e) {
                LOGGER.error("Business listener failed while handling topic [{}] message", topicName, e);
            }
        };
    }

    private static void validatePublishParameters(String topicName, Object message) {
        validateTopicName(topicName);
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
    }

    private static void validateSubscribeParameters(String topicName, Class<?> messageClass, MessageListener<?> listener) {
        validateTopicName(topicName);
        if (messageClass == null) {
            throw new IllegalArgumentException("messageClass must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
    }

    private static void validateTopicName(String topicName) {
        if (CharSequenceUtil.isBlank(topicName)) {
            throw new IllegalArgumentException("topicName must not be blank");
        }
    }

    private static RedissonClient getRedissonMQClient() {
        RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
        if (redissonMQClient == null) {
            throw new GXBusinessException("Unable to get redissonMQClient bean");
        }
        return redissonMQClient;
    }

    private static String generateCacheKey(String topicName, Class<?> messageClass) {
        return getInstanceId() + CACHE_KEY_SEPARATOR + topicName + CACHE_KEY_SEPARATOR + messageClass.getName();
    }

    private static String generateForceCacheKey(String topicName, Class<?> messageClass) {
        return generateCacheKey(topicName, messageClass)
                + CACHE_KEY_SEPARATOR
                + "force"
                + CACHE_KEY_SEPARATOR
                + FORCE_SUBSCRIBE_SEQUENCE.incrementAndGet();
    }

    private record ListenerRegistration(
            String cacheKey,
            String topicName,
            String messageClassName,
            String listenerId,
            boolean forced) {

        private ListenerRegistration {
            Objects.requireNonNull(cacheKey, "cacheKey");
            Objects.requireNonNull(topicName, "topicName");
            Objects.requireNonNull(messageClassName, "messageClassName");
            Objects.requireNonNull(listenerId, "listenerId");
        }
    }

    private static class InstanceIdHolder {
        private static final String INSTANCE_ID = generateInstanceId();

        private static String generateInstanceId() {
            String hostname = getHostname();
            String randomId = UUID.randomUUID().toString().substring(0, 8);
            return System.currentTimeMillis() + "-" + hostname + "-" + randomId;
        }

        private static String getHostname() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                return "unknown";
            }
        }

        static String get() {
            return INSTANCE_ID;
        }
    }
}
