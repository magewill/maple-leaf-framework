package cn.maple.redisson.adapter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.Getter;
import org.redisson.api.RFuture;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter for Debezium Redis Stream topics backed by Redisson reliable topics.
 *
 * @param <T> message type
 */
public class GXRedissonDebeziumReliableTopic<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXRedissonDebeziumReliableTopic.class);

    private final RReliableTopic reliableTopic;

    @Getter
    private final String topicName;

    public GXRedissonDebeziumReliableTopic(String name) {
        this(getRedissonMQClient(), StringCodec.INSTANCE, name);
    }

    public GXRedissonDebeziumReliableTopic(Codec codec, String name) {
        this(getRedissonMQClient(), codec, name);
    }

    public GXRedissonDebeziumReliableTopic(RedissonClient redissonClient, Codec codec, String name) {
        if (redissonClient == null) {
            throw new IllegalArgumentException("redissonClient must not be null");
        }
        if (codec == null) {
            throw new IllegalArgumentException("codec must not be null");
        }
        if (CharSequenceUtil.isBlank(name)) {
            throw new IllegalArgumentException("topicName must not be blank");
        }
        this.topicName = name;
        this.reliableTopic = redissonClient.getReliableTopic(name, codec);
    }

    public static <R> CompletableFuture<R> toCompletableFuture(RFuture<R> future) {
        if (future == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("future must not be null"));
        }
        return future.toCompletableFuture();
    }

    public long publish(T message) {
        validateMessage(message);
        try {
            return reliableTopic.publish(message);
        } catch (Exception e) {
            LOGGER.error("Failed to publish message to topic [{}]", topicName, e);
            throw new GXBusinessException("Failed to publish message: " + e.getMessage(), e);
        }
    }

    public RFuture<Long> publishAsync(T message) {
        validateMessage(message);
        return reliableTopic.publishAsync(message);
    }

    public <M> String addListener(Class<M> type, MessageListener<M> listener) {
        validateListener(type, listener);
        try {
            return reliableTopic.addListener(type, listener);
        } catch (Exception e) {
            LOGGER.error("Failed to add listener to topic [{}]", topicName, e);
            throw new GXBusinessException("Failed to add listener: " + e.getMessage(), e);
        }
    }

    public <M> RFuture<String> addListenerAsync(Class<M> type, MessageListener<M> listener) {
        validateListener(type, listener);
        return reliableTopic.addListenerAsync(type, listener);
    }

    public void removeListener(String... listenerIds) {
        if (listenerIds == null || listenerIds.length == 0) {
            return;
        }
        try {
            reliableTopic.removeListener(listenerIds);
        } catch (Exception e) {
            LOGGER.error("Failed to remove listeners from topic [{}]", topicName, e);
            throw new GXBusinessException("Failed to remove listeners: " + e.getMessage(), e);
        }
    }

    public RFuture<Void> removeListenerAsync(String... listenerIds) {
        if (listenerIds == null) {
            throw new IllegalArgumentException("listenerIds must not be null");
        }
        return reliableTopic.removeListenerAsync(listenerIds);
    }

    public void removeAllListeners() {
        reliableTopic.removeAllListeners();
    }

    public RFuture<Void> removeAllListenersAsync() {
        return reliableTopic.removeAllListenersAsync();
    }

    public long size() {
        return reliableTopic.size();
    }

    public RFuture<Long> sizeAsync() {
        return reliableTopic.sizeAsync();
    }

    public int countListeners() {
        return reliableTopic.countListeners();
    }

    public int countSubscribers() {
        return reliableTopic.countSubscribers();
    }

    public RFuture<Integer> countSubscribersAsync() {
        return reliableTopic.countSubscribersAsync();
    }

    public boolean delete() {
        return reliableTopic.delete();
    }

    public RFuture<Boolean> deleteAsync() {
        return reliableTopic.deleteAsync();
    }

    public boolean expire(Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        return reliableTopic.expire(duration);
    }

    public RFuture<Boolean> expireAsync(Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException("duration must not be null");
        }
        return reliableTopic.expireAsync(duration);
    }

    public boolean expireAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        return reliableTopic.expire(instant);
    }

    public boolean clearExpire() {
        return reliableTopic.clearExpire();
    }

    public RFuture<Boolean> clearExpireAsync() {
        return reliableTopic.clearExpireAsync();
    }

    public long sizeInMemory() {
        return reliableTopic.sizeInMemory();
    }

    public RFuture<Long> sizeInMemoryAsync() {
        return reliableTopic.sizeInMemoryAsync();
    }

    private static RedissonClient getRedissonMQClient() {
        RedissonClient redissonClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
        if (redissonClient == null) {
            throw new GXBusinessException("Unable to get redissonMQClient bean");
        }
        return redissonClient;
    }

    private void validateMessage(T message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
    }

    private static <M> void validateListener(Class<M> type, MessageListener<M> listener) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
    }
}
