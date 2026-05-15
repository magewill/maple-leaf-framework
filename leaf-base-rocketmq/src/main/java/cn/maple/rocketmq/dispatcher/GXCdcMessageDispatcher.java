package cn.maple.rocketmq.dispatcher;

import org.jspecify.annotations.Nullable;

/**
 * Dispatches raw CDC messages consumed from RocketMQ to CDC event listeners.
 */
public interface GXCdcMessageDispatcher {
    void dispatch(@Nullable String message, @Nullable String bizType);
}
