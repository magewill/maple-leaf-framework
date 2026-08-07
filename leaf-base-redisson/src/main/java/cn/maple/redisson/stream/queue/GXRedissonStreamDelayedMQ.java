package cn.maple.redisson.stream.queue;

import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;
import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.protocol.ScoredEntry;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Delayed queue backed by a sorted set and Redis Streams.
 */
@Slf4j
public class GXRedissonStreamDelayedMQ {
    private static final String DELAY_LOCK_KEY = "mq:delay:lock";

    private static final int MAX_TRANSFER_PER_ROUND = 200;

    private final RedissonClient redissonMQClient;

    private final GXRedissonStreamMQProperties props;
    private final GXRedissonStreamImmediateMQ immediateQueue;
    private final ObjectMapper objectMapper;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public GXRedissonStreamDelayedMQ(RedissonClient redissonMQClient,
                                     GXRedissonStreamMQProperties props,
                                     GXRedissonStreamImmediateMQ immediateQueue) {
        this(redissonMQClient, props, immediateQueue, new ObjectMapper());
    }

    public GXRedissonStreamDelayedMQ(RedissonClient redissonMQClient,
                                     GXRedissonStreamMQProperties props,
                                     GXRedissonStreamImmediateMQ immediateQueue,
                                     ObjectMapper objectMapper) {
        if (redissonMQClient == null) {
            throw new IllegalArgumentException("redissonMQClient must not be null");
        }
        if (props == null) {
            throw new IllegalArgumentException("props must not be null");
        }
        if (immediateQueue == null) {
            throw new IllegalArgumentException("immediateQueue must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        this.redissonMQClient = redissonMQClient;
        this.props = props;
        this.immediateQueue = immediateQueue;
        this.objectMapper = objectMapper;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("stream-delay-scheduler-", 1).factory());
        scheduler.scheduleWithFixedDelay(
                this::transferDueMessages,
                0,
                props.getDelayScanInterval(),
                TimeUnit.MILLISECONDS
        );
        log.info("Stream delayed queue started, scanIntervalMillis={}", props.getDelayScanInterval());
    }

    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(props.getBlockingTimeoutMillis() + 1000, TimeUnit.MILLISECONDS)) {
                    log.warn("Stream delayed queue scheduler did not stop before timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        log.info("Stream delayed queue stopped");
    }

    public void publish(GXRedissonStreamMessageDto message) {
        validateMessage(message);
        if (message.getDeliveryTime() <= System.currentTimeMillis()) {
            immediateQueue.publish(message);
            return;
        }
        String zsetKey = zsetKey(message.getTopic());
        RScoredSortedSet<String> zset = redissonMQClient.getScoredSortedSet(zsetKey);

        String msgJson = objectMapper.writeValueAsString(message);
        String hashKey = delayHashKey(message.getTopic());
        RMap<String, String> hash = redissonMQClient.getMap(hashKey);
        RScoredSortedSet<String> transferSet = redissonMQClient.getScoredSortedSet(transferKey(message.getTopic()));

        try {
            hash.put(message.getMessageId(), msgJson);
            zset.add(message.getDeliveryTime(), message.getMessageId());
            delayedTopics().add(message.getTopic());
        } catch (Exception e) {
            zset.remove(message.getMessageId());
            hash.remove(message.getMessageId());
            cleanupTopicIfEmpty(message.getTopic(), zset, transferSet, hash);
            throw e;
        }

        log.debug("Published delayed stream message, topic={}, msgId={}, deliveryAt={}",
                message.getTopic(), message.getMessageId(), message.getDeliveryTime());
    }

    public boolean cancel(String topic, String messageId) {
        validateTopic(topic);
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId must not be blank");
        }
        String zsetKey = zsetKey(topic);
        String hashKey = delayHashKey(topic);
        String transferKey = transferKey(topic);
        RScoredSortedSet<String> zset = redissonMQClient.getScoredSortedSet(zsetKey);
        RScoredSortedSet<String> transferSet = redissonMQClient.getScoredSortedSet(transferKey);
        RMap<String, String> hash = redissonMQClient.getMap(hashKey);

        boolean removed = zset.remove(messageId);
        transferSet.remove(messageId);
        hash.remove(messageId);
        if (removed) {
            log.info("Canceled delayed stream message, topic={}, msgId={}", topic, messageId);
        }
        cleanupTopicIfEmpty(topic, zset, transferSet, hash);
        return removed;
    }

    private void transferDueMessages() {
        if (!running) return;

        RLock lock = redissonMQClient.getLock(DELAY_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.MILLISECONDS);
            if (!locked) return;

            for (String topic : scanTopics()) {
                transferTopicDueMessages(topic);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to transfer due delayed stream messages", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void transferTopicDueMessages(String topic) {
        String zsetKey = zsetKey(topic);
        String hashKey = delayHashKey(topic);
        String transferKey = transferKey(topic);
        RScoredSortedSet<String> zset = redissonMQClient.getScoredSortedSet(zsetKey);
        RMap<String, String> hash = redissonMQClient.getMap(hashKey);
        RScoredSortedSet<String> transferSet = redissonMQClient.getScoredSortedSet(transferKey);

        double now = System.currentTimeMillis();
        recoverAbandonedTransfers(topic, transferSet, hash, now);

        Collection<ScoredEntry<String>> dueEntries =
                zset.entryRange(Double.NEGATIVE_INFINITY, true, now, true, 0, MAX_TRANSFER_PER_ROUND);

        if (dueEntries == null || dueEntries.isEmpty()) return;

        log.debug("Found due delayed stream messages, count={}, topic={}", dueEntries.size(), topic);

        for (ScoredEntry<String> entry : dueEntries) {
            String messageId = entry.getValue();
            String msgJson = hash.get(messageId);

            if (msgJson == null) {
                zset.remove(messageId);
                continue;
            }

            try {
                GXRedissonStreamMessageDto message = objectMapper.readValue(msgJson, GXRedissonStreamMessageDto.class);
                transferSet.add(now, messageId);
                if (!zset.remove(messageId)) {
                    transferSet.remove(messageId);
                    continue;
                }
                immediateQueue.publish(message);
                hash.remove(messageId);
                transferSet.remove(messageId);
                log.debug("Transferred delayed stream message, msgId={}", messageId);
            } catch (Exception e) {
                log.error("Failed to transfer delayed stream message, msgId={}", messageId, e);
            }
        }
        cleanupTopicIfEmpty(topic, zset, transferSet, hash);
    }

    private void recoverAbandonedTransfers(String topic,
                                           RScoredSortedSet<String> transferSet,
                                           RMap<String, String> hash,
                                           double now) {
        double expiredBefore = now - props.getDelayTransferRecoveryMillis();
        Collection<ScoredEntry<String>> entries =
                transferSet.entryRange(Double.NEGATIVE_INFINITY, true, expiredBefore, true, 0, MAX_TRANSFER_PER_ROUND);

        if (entries == null || entries.isEmpty()) return;

        for (ScoredEntry<String> entry : entries) {
            String messageId = entry.getValue();
            String msgJson = hash.get(messageId);
            if (msgJson == null) {
                transferSet.remove(messageId);
                continue;
            }
            try {
                GXRedissonStreamMessageDto message = objectMapper.readValue(msgJson, GXRedissonStreamMessageDto.class);
                immediateQueue.publish(message);
                hash.remove(messageId);
                transferSet.remove(messageId);
                log.warn("Recovered delayed stream transfer, topic={}, msgId={}", topic, messageId);
            } catch (Exception e) {
                log.error("Failed to recover delayed stream transfer, topic={}, msgId={}", topic, messageId, e);
            }
        }
    }

    private Set<String> scanTopics() {
        Set<String> topics = new HashSet<>(immediateQueue.registeredTopics());
        topics.addAll(delayedTopics().readAll());
        return topics;
    }

    private RSet<String> delayedTopics() {
        return redissonMQClient.getSet(props.getDelayTransferPrefix() + "topics");
    }

    private void cleanupTopicIfEmpty(String topic,
                                     RScoredSortedSet<String> zset,
                                     RScoredSortedSet<String> transferSet,
                                     RMap<String, String> hash) {
        if (zset.isEmpty() && transferSet.isEmpty() && hash.isEmpty()) {
            delayedTopics().remove(topic);
        }
    }

    private String zsetKey(String topic) {
        return props.getDelayZsetPrefix() + topic;
    }

    private String delayHashKey(String topic) {
        return props.getDelayZsetPrefix() + topic + ":data";
    }

    private String transferKey(String topic) {
        return props.getDelayTransferPrefix() + topic;
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
}
