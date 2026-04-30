package cn.maple.core.framework.ddd.publisher;

import cn.maple.core.framework.exception.GXBusinessException;

public interface GXDomainEventPublisher {
    default void publish(Object event) {
        throw new GXBusinessException("请实现publish方法");
    }
}
