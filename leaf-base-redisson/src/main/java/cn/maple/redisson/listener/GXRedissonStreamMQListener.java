package cn.maple.redisson.listener;

import org.springframework.beans.factory.DisposableBean;

/**
 * Contract for beans that register Redis Streams message listeners.
 */
public interface GXRedissonStreamMQListener extends DisposableBean {
    /**
     * Register Redis Streams listeners for this bean.
     */
    void registerRedissonStreamListener();

    /**
     * Stream listener lifecycle is managed by the stream message queue manager.
     */
    @Override
    default void destroy() {
        // no-op
    }
}
