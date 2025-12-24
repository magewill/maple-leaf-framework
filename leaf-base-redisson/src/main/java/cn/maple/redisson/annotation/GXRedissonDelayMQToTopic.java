package cn.maple.redisson.annotation;

import java.lang.annotation.*;

/**
 * 转换Redisson的延迟队列数据到Redisson的ReliableTopic
 * Redisson的ReliableTopic是基于redis的Stream数据结构实现的
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GXRedissonDelayMQToTopic {
    /**
     * 延迟队列的名字
     * 该队列只是使用来存储延迟的数据
     */
    String delayQueueName();

    /**
     * 投递消息的topic
     * 防止业务逻辑出错导致消息丢失
     */
    String topicName();

    /**
     * 从消息队列中取消息等待的超时时间
     */
    int timeout() default 1800;
}
