package cn.maple.redisson.listener;

import org.springframework.beans.factory.DisposableBean;

/**
 * Contract for beans that register Redisson reliable-topic listeners.
 */
public interface GXRedissonMQListener extends DisposableBean {
    /**
     * Register Redisson listeners for this bean.
     */
    void registerRedissonListener();

    /**
     * Business listeners rarely need their own destroy hook. The post processor
     * unregisters subscriptions recorded through GXRedissonMQUtils.
     */
    @Override
    default void destroy() {
        // no-op
    }
}
