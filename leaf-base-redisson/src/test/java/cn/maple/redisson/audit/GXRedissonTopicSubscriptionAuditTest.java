package cn.maple.redisson.audit;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.util.GXRedissonMQUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-01, F-09, F-10: reliable-topic subscription cache, cross-context reuse and
 * exception swallowing.
 */
class GXRedissonTopicSubscriptionAuditTest {

    @AfterEach
    void clearCaches() {
        GXRedissonMQUtils.clearLocalCache();
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": subscribe reports success after unsubscribe failed")
    @SuppressWarnings("unchecked")
    void subscribeReturnsStaleListenerIdWhenUnsubscribeFails() {
        RedissonClient client = mock(RedissonClient.class);
        RReliableTopic topic = mock(RReliableTopic.class);
        when(client.getReliableTopic("audit-topic")).thenReturn(topic);
        when(topic.addListener(any(Class.class), any(MessageListener.class))).thenReturn("listener-1");
        doThrow(new IllegalStateException("redis unavailable")).when(topic).removeListener(any(String[].class));

        try (MockedStatic<GXSpringContextUtils> context = Mockito.mockStatic(GXSpringContextUtils.class)) {
            context.when(() -> GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class))
                    .thenReturn(client);

            assertEquals("listener-1", GXRedissonMQUtils.subscribe("audit-topic", String.class, (channel, msg) -> {
            }));

            assertThrows(GXBusinessException.class,
                    () -> GXRedissonMQUtils.unsubscribe("audit-topic", "listener-1"));

            // CURRENT BEHAVIOR: the cache entry survived the failed removal, so the next
            // subscribe returns the stale id without touching Redisson again.
            assertEquals("listener-1",
                    GXRedissonMQUtils.subscribe("audit-topic", String.class, (channel, msg) -> {
                    }));
            verify(topic, times(1)).addListener(any(Class.class), any(MessageListener.class));
        }
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": topic cache keeps the first RedissonClient forever")
    void topicCacheKeepsFirstClientInstance() {
        RedissonClient firstClient = mock(RedissonClient.class);
        RedissonClient secondClient = mock(RedissonClient.class);
        RReliableTopic firstTopic = mock(RReliableTopic.class);
        when(firstClient.getReliableTopic("audit-shared-topic")).thenReturn(firstTopic);

        try (MockedStatic<GXSpringContextUtils> context = Mockito.mockStatic(GXSpringContextUtils.class)) {
            context.when(() -> GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class))
                    .thenReturn(firstClient);
            RReliableTopic cached = GXRedissonMQUtils.getReliableTopic("audit-shared-topic");

            context.when(() -> GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class))
                    .thenReturn(secondClient);
            RReliableTopic secondRead = GXRedissonMQUtils.getReliableTopic("audit-shared-topic");

            // CURRENT BEHAVIOR: the static cache is bound to the first client instance.
            assertSame(cached, secondRead);
            verify(secondClient, never()).getReliableTopic(anyString());
        }
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": business listener exceptions are swallowed")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void businessListenerExceptionIsSwallowed() {
        RedissonClient client = mock(RedissonClient.class);
        RReliableTopic topic = mock(RReliableTopic.class);
        when(client.getReliableTopic("audit-swallow")).thenReturn(topic);
        when(topic.addListener(any(Class.class), any(MessageListener.class))).thenReturn("listener-2");

        try (MockedStatic<GXSpringContextUtils> context = Mockito.mockStatic(GXSpringContextUtils.class)) {
            context.when(() -> GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class))
                    .thenReturn(client);
            GXRedissonMQUtils.subscribe("audit-swallow", String.class, (channel, msg) -> {
                throw new IllegalStateException("business failure");
            });

            ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
            verify(topic).addListener(any(Class.class), captor.capture());
            MessageListener wrapped = captor.getValue();

            // CURRENT BEHAVIOR: the wrapper logs and returns, so Redisson treats the
            // message as delivered and never redelivers it.
            assertDoesNotThrow(() -> wrapped.onMessage("audit-swallow", "payload"));
        }
    }
}
