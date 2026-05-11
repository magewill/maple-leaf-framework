package cn.maple.redisson;

import cn.maple.redisson.services.impl.GXRedissonCacheServiceImpl;
import cn.maple.redisson.annotation.GXRedissonDelayMQToTopic;
import cn.maple.redisson.config.GXRedissonMQConfig;
import cn.maple.redisson.config.GXRedissonSpringDataConfig;
import cn.maple.redisson.listener.GXRedissonDelayMQListener;
import cn.maple.redisson.listener.GXRedissonMQListener;
import cn.maple.redisson.listener.GXRedissonStreamMQListener;
import cn.maple.redisson.processor.GXRedissonDelayMQPostProcessor;
import cn.maple.redisson.processor.GXRedissonMQPostProcessor;
import cn.maple.redisson.processor.GXRedissonStreamMQPostProcessor;
import cn.maple.redisson.properties.GXRedissonConnectProperties;
import cn.maple.redisson.properties.local.GXLocalRedissonMQProperties;
import cn.maple.redisson.properties.local.GXLocalRedissonProperties;
import cn.maple.redisson.stream.GXRedissonStreamMQManager;
import cn.maple.redisson.stream.dto.req.GXRedissonStreamMessageDto;
import cn.maple.redisson.stream.queue.GXRedissonStreamImmediateMQ;
import cn.maple.redisson.properties.local.GXLocalRedissonStreamMQProperties;
import cn.maple.redisson.properties.nacos.GXNacosRedissonStreamMQProperties;
import cn.maple.redisson.util.GXRedissonStreamMQUtils;
import cn.maple.redisson.util.GXRedissonDelayMQUtils;
import cn.maple.redisson.util.GXRedissonMQUtils;
import cn.maple.redisson.util.GXRedissonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RMapCache;
import org.redisson.api.RReliableTopic;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.config.Config;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonUtilityRegressionTest {

    @Test
    void updateCacheExpiredTimeRefreshesTtlWithoutRewritingValue() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.remainTimeToLive("key")).thenReturn(TimeUnit.SECONDS.toMillis(1));
        when(mapCache.expireEntry("key", Duration.ofSeconds(30), Duration.ZERO)).thenReturn(true);

        assertTrue(service.updateCacheExpiredTime("bucket", "key", 30, 10));
        verify(mapCache).expireEntry("key", Duration.ofSeconds(30), Duration.ZERO);
        verify(mapCache, never()).get("key");
        verify(mapCache, never()).fastPut(eq("key"), any(), eq(30L), eq(TimeUnit.SECONDS));
    }

    @Test
    void updateCacheExpiredTimeReturnsFalseWhenEntryIsMissing() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.remainTimeToLive("key")).thenReturn(-2L);

        assertEquals(false, service.updateCacheExpiredTime("bucket", "key", 30, 10));
        verify(mapCache, never()).expireEntry(any(), any(), any());
    }

    @Test
    void updateCacheExpiredTimeKeepsExistingNoExpiryEntry() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.remainTimeToLive("key")).thenReturn(-1L);

        assertTrue(service.updateCacheExpiredTime("bucket", "key", 30, 10));
        verify(mapCache, never()).expireEntry(any(), any(), any());
    }

    @Test
    void getBucketAllDataUsesCountAsScanBatchSizeWithoutTruncatingResults() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RMapCache<Object, Object> mapCache = mock(RMapCache.class);
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        Set<Object> scannedKeys = new LinkedHashSet<>();
        scannedKeys.add("k1");
        scannedKeys.add("k2");
        scannedKeys.add("k3");
        Map<Object, Object> expected = new LinkedHashMap<>();
        expected.put("k1", "v1");
        expected.put("k2", "v2");
        expected.put("k3", "v3");

        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        when(redissonClient.getMapCache("bucket")).thenReturn(mapCache);
        when(mapCache.keySet(2)).thenReturn(scannedKeys);
        when(mapCache.getAll(scannedKeys)).thenReturn(expected);

        Map<Object, Object> actual = service.getBucketAllData("bucket", 2);

        assertEquals(expected, actual);
        verify(mapCache).getAll(scannedKeys);
    }

    @Test
    void clearDelayedQueueCacheDestroysCachedQueues() throws Exception {
        ConcurrentHashMap<String, RDelayedQueue<String>> cache = delayedQueueCache();
        cache.clear();
        RDelayedQueue<String> delayedQueue = mock(RDelayedQueue.class);
        cache.put("queue", delayedQueue);

        GXRedissonDelayMQUtils.clearDelayedQueueCache();

        verify(delayedQueue).destroy();
        assertTrue(cache.isEmpty());
    }

    @Test
    void clearReliableTopicLocalCacheRemovesCachedState() throws Exception {
        ConcurrentHashMap<String, RReliableTopic> topicCache = topicCache();
        ConcurrentHashMap<String, Object> listenerCache = listenerRegistrationCache();
        topicCache.clear();
        listenerCache.clear();
        topicCache.put("topic", mock(RReliableTopic.class));
        listenerCache.put("listener", new Object());

        GXRedissonMQUtils.clearLocalCache();

        assertTrue(topicCache.isEmpty());
        assertTrue(listenerCache.isEmpty());
    }

    @Test
    void counterTtlMustNotRoundDownToZeroMilliseconds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GXRedissonUtils.incrementAndGet("counter", 1, TimeUnit.MICROSECONDS)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GXRedissonUtils.set("key", "value", 1, TimeUnit.MICROSECONDS)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> GXRedissonUtils.setCounterValue("counter", 1, 1, TimeUnit.MICROSECONDS)
        );
    }

    @Test
    void cacheEntryTtlMustNotRoundDownToZeroMilliseconds() {
        GXRedissonCacheServiceImpl service = new GXRedissonCacheServiceImpl();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.setCache("bucket", "key", "value", 1, TimeUnit.MICROSECONDS)
        );
    }

    @Test
    void streamQueuePropertiesExposeLocalAndNacosDefaults() {
        GXLocalRedissonStreamMQProperties local = new GXLocalRedissonStreamMQProperties();
        GXNacosRedissonStreamMQProperties nacos = new GXNacosRedissonStreamMQProperties();

        assertEquals("mq:stream:", local.getStreamPrefix());
        assertEquals("mq:delay:", nacos.getDelayZsetPrefix());
        assertEquals("mq:stream:processed:", local.getProcessedMessagePrefix());
        assertEquals("mq:delay:transfer:", nacos.getDelayTransferPrefix());
        assertTrue(local.isAppendInstanceIdToConsumerName());
        assertEquals(10_000L, local.getStreamMaxLen());
        assertEquals(2, nacos.getConsumerThreads());
    }

    @Test
    void springDataRedissonConfigStoresAuthOnRootConfig() {
        GXLocalRedissonProperties properties = new GXLocalRedissonProperties();
        properties.setConfig(Map.of("single", connectionProperties("redis://127.0.0.1:6379")));
        GXRedissonSpringDataConfig springDataConfig = new GXRedissonSpringDataConfig();
        ReflectionTestUtils.setField(springDataConfig, "redissonConfig", properties);

        Config config = springDataConfig.config();

        assertEquals("secret", config.getPassword());
        assertEquals("default", config.getUsername());
    }

    @Test
    void mqRedissonConfigStoresAuthOnRootConfig() {
        GXLocalRedissonMQProperties properties = new GXLocalRedissonMQProperties();
        properties.setConfig(Map.of("cluster", connectionProperties("redis://127.0.0.1:6379,redis://127.0.0.1:6380")));
        GXRedissonMQConfig mqConfig = new GXRedissonMQConfig();
        ReflectionTestUtils.setField(mqConfig, "redissonMQConfig", properties);

        Config config = mqConfig.mqConfig();

        assertEquals("secret", config.getPassword());
        assertEquals("default", config.getUsername());
    }

    @Test
    void streamManagerValidatesPropertyPrefixes() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        GXLocalRedissonStreamMQProperties props = new GXLocalRedissonStreamMQProperties();
        props.setProcessedMessagePrefix(" ");

        assertThrows(
                IllegalArgumentException.class,
                () -> new GXRedissonStreamMQManager(redissonClient, props)
        );
    }

    @Test
    void streamManagerRejectsBlankTopicAndNullPayload() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        GXRedissonStreamMQManager manager = new GXRedissonStreamMQManager(redissonClient, new GXLocalRedissonStreamMQProperties());

        assertThrows(IllegalArgumentException.class, () -> manager.sendImmediate(" ", "payload"));
        assertThrows(IllegalArgumentException.class, () -> manager.sendDelayed("topic", null, 1, TimeUnit.SECONDS));
    }

    @Test
    void streamManagerUsesSpringLifecycleContract() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        GXRedissonStreamMQManager manager = new GXRedissonStreamMQManager(redissonClient, new GXLocalRedissonStreamMQProperties());
        AtomicBoolean callbackInvoked = new AtomicBoolean(false);

        assertTrue(manager.isAutoStartup());
        manager.stop(() -> callbackInvoked.set(true));

        assertTrue(callbackInvoked.get());
    }

    @Test
    void streamMessageDefaultsAndValidationBehavePredictably() {
        GXRedissonStreamMessageDto message = GXRedissonStreamMessageDto.immediate("topic", "payload", String.class.getName());
        assertTrue(message.getMessageId() != null && !message.getMessageId().isBlank());
        assertThrows(IllegalArgumentException.class, () -> GXRedissonStreamMessageDto.delayed("topic", "payload", String.class.getName(), 0));
    }

    @Test
    void streamManagerValidatesDirectMessagePublishing() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        GXRedissonStreamMQManager manager = new GXRedissonStreamMQManager(redissonClient, new GXLocalRedissonStreamMQProperties());

        assertThrows(IllegalArgumentException.class, () -> manager.sendImmediate(GXRedissonStreamMessageDto.builder().topic("topic").build()));
    }

    @Test
    void streamMqUtilsDelegatesToSpringManagedManager() {
        GXRedissonStreamMQManager manager = mock(GXRedissonStreamMQManager.class);
        when(manager.sendImmediate("topic", "payload")).thenReturn("stream-id");

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean("redissonStreamMessageQueueManager", GXRedissonStreamMQManager.class))
                    .thenReturn(manager);

            assertEquals("stream-id", GXRedissonStreamMQUtils.sendImmediate("topic", "payload"));
            verify(manager).sendImmediate("topic", "payload");
        }
    }

    @Test
    void streamImmediateQueueTracksActiveConsumerTopics() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        GXRedissonStreamImmediateMQ queue = new GXRedissonStreamImmediateMQ(redissonClient, new GXLocalRedissonStreamMQProperties());

        @SuppressWarnings("unchecked")
        Set<String> activeConsumerTopics = (Set<String>) ReflectionTestUtils.getField(queue, "activeConsumerTopics");

        assertTrue(activeConsumerTopics.isEmpty());
    }

    @Test
    void streamFailedMessageDispatchIsMarkedByOriginalStreamId() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RStream<String, String> stream = mock(RStream.class);
        RMapCache<String, String> cache = mock(RMapCache.class);
        GXLocalRedissonStreamMQProperties props = new GXLocalRedissonStreamMQProperties();
        GXRedissonStreamImmediateMQ queue = new GXRedissonStreamImmediateMQ(redissonClient, props);
        GXRedissonStreamMessageDto message = GXRedissonStreamMessageDto.immediate("topic", "payload", String.class.getName());
        String msgJson = new tools.jackson.databind.ObjectMapper().writeValueAsString(message);
        StreamMessageId originalStreamId = new StreamMessageId(1, 1);

        when(redissonClient.<String, String>getMapCache(any(String.class))).thenReturn(cache);
        when(redissonClient.<String, String>getStream("mq:stream:topic")).thenReturn(stream);
        when(cache.containsKey(any())).thenReturn(false);
        when(stream.add(any())).thenReturn(new StreamMessageId(2, 1));

        queue.registerHandler("topic", ignored -> {
            throw new IllegalStateException("boom");
        });

        ReflectionTestUtils.invokeMethod(
                queue,
                "processMessage",
                stream,
                "mq:stream:topic",
                "topic",
                props.getConsumerGroup(),
                originalStreamId,
                msgJson
        );

        verify(cache).fastPut(eq("1-1"), any(), eq(props.getProcessedMessageTtlMillis()), eq(TimeUnit.MILLISECONDS));
        verify(stream).ack(props.getConsumerGroup(), originalStreamId);
    }

    @Test
    void mqPostProcessorFailsFastWhenListenerRegistrationFails() {
        GXRedissonMQPostProcessor processor = new GXRedissonMQPostProcessor();
        GXRedissonMQListener listener = () -> {
            throw new IllegalStateException("boom");
        };

        assertThrows(
                IllegalStateException.class,
                () -> processor.postProcessAfterInitialization(listener, "brokenListener")
        );
    }

    @Test
    void streamMqPostProcessorRegistersStreamListenerBeans() {
        GXRedissonStreamMQPostProcessor processor = new GXRedissonStreamMQPostProcessor();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("redissonStreamMessageQueueManager", GXRedissonStreamMQManager.class))
                .thenReturn(mock(GXRedissonStreamMQManager.class));
        processor.setApplicationContext(applicationContext);
        AtomicBoolean registered = new AtomicBoolean(false);
        GXRedissonStreamMQListener listener = () -> registered.set(true);

        processor.postProcessAfterInitialization(listener, "streamListener");

        assertTrue(registered.get());
    }

    @Test
    void streamMqPostProcessorFailsFastWhenListenerRegistrationFails() {
        GXRedissonStreamMQPostProcessor processor = new GXRedissonStreamMQPostProcessor();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("redissonStreamMessageQueueManager", GXRedissonStreamMQManager.class))
                .thenReturn(mock(GXRedissonStreamMQManager.class));
        processor.setApplicationContext(applicationContext);
        GXRedissonStreamMQListener listener = () -> {
            throw new IllegalStateException("boom");
        };

        assertThrows(
                IllegalStateException.class,
                () -> processor.postProcessAfterInitialization(listener, "brokenStreamListener")
        );
    }

    @Test
    void delayPostProcessorFailsFastWhenAnnotatedBeanDoesNotImplementListener() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        GXRedissonDelayMQPostProcessor processor = delayPostProcessor(redissonClient);

        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> processor.postProcessAfterInitialization(new InvalidDelayListenerBean(), "invalidDelayListener")
            );
        } finally {
            processor.destroy();
        }
    }

    @Test
    void delayPostProcessorRequeuesInFlightMessagesOnDestroy() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RBlockingQueue<String> blockingQueue = mock(RBlockingQueue.class);
        RDelayedQueue<String> delayedQueue = mock(RDelayedQueue.class);
        when(redissonClient.<String>getBlockingQueue("delay-queue")).thenReturn(blockingQueue);
        when(redissonClient.getDelayedQueue(blockingQueue)).thenReturn(delayedQueue);

        GXRedissonDelayMQPostProcessor processor = delayPostProcessor(redissonClient);
        processor.postProcessAfterInitialization(new ValidDelayListenerBean(), "validDelayListener");

        Map<String, Object> configs = delayListenerConfigs(processor);
        Object config = configs.get("delay-queue");
        @SuppressWarnings("unchecked")
        Map<String, String> inFlightMessages = (Map<String, String>) ReflectionTestUtils.getField(config, "inFlightMessages");
        inFlightMessages.put("message-id", "payload");

        processor.destroy();

        verify(blockingQueue).offer("payload");
        verify(delayedQueue).destroy();
        assertTrue(inFlightMessages.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, RDelayedQueue<String>> delayedQueueCache() throws Exception {
        Field field = GXRedissonDelayMQUtils.class.getDeclaredField("DELAYED_QUEUE_CACHE");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, RDelayedQueue<String>>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, RReliableTopic> topicCache() throws Exception {
        Field field = GXRedissonMQUtils.class.getDeclaredField("TOPIC_CACHE");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, RReliableTopic>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<String, Object> listenerRegistrationCache() throws Exception {
        Field field = GXRedissonMQUtils.class.getDeclaredField("LISTENER_REGISTRATION_CACHE");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> delayListenerConfigs(GXRedissonDelayMQPostProcessor processor) {
        return (Map<String, Object>) ReflectionTestUtils.getField(processor, "listenerConfigs");
    }

    private static GXRedissonConnectProperties connectionProperties(String address) {
        GXRedissonConnectProperties properties = new GXRedissonConnectProperties();
        properties.setAddress(address);
        properties.setUsername("default");
        properties.setPassword("secret");
        return properties;
    }

    private static GXRedissonDelayMQPostProcessor delayPostProcessor(RedissonClient redissonClient) {
        GXRedissonDelayMQPostProcessor processor = new GXRedissonDelayMQPostProcessor();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean("redissonMQClient", RedissonClient.class)).thenReturn(redissonClient);
        processor.setApplicationContext(applicationContext);
        return processor;
    }

    @GXRedissonDelayMQToTopic(delayQueueName = "delay-queue", topicName = "topic", timeout = 1)
    private static final class InvalidDelayListenerBean {
    }

    @GXRedissonDelayMQToTopic(delayQueueName = "delay-queue", topicName = "topic", timeout = 1)
    private static final class ValidDelayListenerBean implements GXRedissonDelayMQListener {
    }
}
