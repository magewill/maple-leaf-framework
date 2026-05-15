package cn.maple.rocketmq.listener;

import cn.maple.rocketmq.dto.inner.GXCdcEvent;
import cn.maple.rocketmq.dispatcher.GXCdcMessageDispatcher;

/**
 * Business listener for normalized CDC events consumed from RocketMQ.
 *
 * <p>A RocketMQ consumer should delegate raw message handling to
 * {@link GXCdcMessageDispatcher}. Implementations keep only table or business
 * synchronization logic.</p>
 */
public interface GXCdcEventListener {
    default boolean supports(GXCdcEvent event) {
        return true;
    }

    default boolean shouldIgnore(GXCdcEvent event) {
        return false;
    }

    default void onCreate(GXCdcEvent event) {
    }

    default void onUpdate(GXCdcEvent event) {
    }

    default void onDelete(GXCdcEvent event) {
    }

    default void onRead(GXCdcEvent event) {
    }
}
