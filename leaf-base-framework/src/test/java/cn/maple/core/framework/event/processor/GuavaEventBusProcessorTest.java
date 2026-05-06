package cn.maple.core.framework.event.processor;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Log4j2
public class GuavaEventBusProcessorTest {
    @Mock
    private EventBus syncEventBus;

    @Mock
    private AsyncEventBus asyncEventBus;

    private GuavaSyncEventBusBeanPostProcessor syncProcessor;
    private GuavaASyncEventBusBeanPostProcessor asyncProcessor;

    private AutoCloseable closeable;

    @BeforeEach
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        syncProcessor = new GuavaSyncEventBusBeanPostProcessor();
        ReflectionTestUtils.setField(syncProcessor, "eventBus", syncEventBus);

        asyncProcessor = new GuavaASyncEventBusBeanPostProcessor();
        ReflectionTestUtils.setField(asyncProcessor, "asyncEventBus", asyncEventBus);
    }

    @AfterEach
    public void tearDown() throws Exception {
        syncProcessor.destroy();
        asyncProcessor.destroy();
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    @DisplayName("测试同步事件总线处理器的Bean注册功能")
    public void testSyncProcessorBeanRegistration() {
        TestEventListener listener = new TestEventListener();

        syncProcessor.postProcessAfterInitialization(listener, "testListener");

        verify(syncEventBus, times(1)).register(listener);
        assertTrue(syncProcessor.isBeanRegistered("testListener"));
        assertEquals(1, syncProcessor.getRegisteredBeanCount());
    }

    @Test
    @DisplayName("测试异步事件总线处理器的Bean注册功能")
    public void testAsyncProcessorBeanRegistration() {
        TestEventListener listener = new TestEventListener();
        asyncProcessor.postProcessAfterInitialization(listener, "testListener");
        verify(asyncEventBus, times(1)).register(listener);
        assertTrue(asyncProcessor.isBeanRegistered("testListener"));
    }

    @Test
    @DisplayName("测试无订阅方法的Bean不会注册")
    public void testBeanWithoutSubscribeMethodIsIgnored() {
        NoSubscribeListener listener = new NoSubscribeListener();

        Object result = syncProcessor.postProcessAfterInitialization(listener, "noSubscribeListener");

        assertSame(listener, result);
        verify(syncEventBus, never()).register(any());
        assertFalse(syncProcessor.isBeanRegistered("noSubscribeListener"));
    }

    @Test
    @DisplayName("测试处理器的方法缓存功能")
    public void testMethodCaching() {
        TestEventListener listener = new TestEventListener();
        syncProcessor.postProcessAfterInitialization(listener, "testListener");

        ConcurrentHashMap<Class<?>, Set<Method>> cache =
                (ConcurrentHashMap<Class<?>, Set<Method>>) ReflectionTestUtils.getField(
                        syncProcessor, "subscribedMethodsCache");

        assertNotNull(cache);
        assertTrue(cache.containsKey(TestEventListener.class));
        assertEquals(2, cache.get(TestEventListener.class).size());
        assertThrows(UnsupportedOperationException.class, () -> cache.get(TestEventListener.class).clear());
    }

    @Test
    @DisplayName("测试处理器的重复注册防护功能")
    public void testDuplicateRegistrationPrevention() {
        TestEventListener listener = new TestEventListener();

        syncProcessor.postProcessAfterInitialization(listener, "testListener");

        syncProcessor.postProcessAfterInitialization(listener, "testListener");

        verify(syncEventBus, times(1)).register(listener);
        assertEquals(1, syncProcessor.getRegisteredBeanCount());
    }

    @Test
    @DisplayName("测试同名Bean并发注册只执行一次")
    public void testConcurrentDuplicateRegistrationPrevention() throws InterruptedException {
        TestEventListener listener = new TestEventListener();
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    syncProcessor.postProcessAfterInitialization(listener, "sameBeanName");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        verify(syncEventBus, times(1)).register(listener);
        assertEquals(1, syncProcessor.getRegisteredBeanCount());
    }

    @Test
    @DisplayName("测试同名Bean并发注册失败后后续线程可重试")
    public void testConcurrentRegistrationRetriesAfterFailure() throws InterruptedException {
        TestEventListener listener = new TestEventListener();
        CountDownLatch firstAttemptEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstAttempt = new CountDownLatch(1);
        AtomicInteger registerAttempts = new AtomicInteger(0);

        doAnswer(invocation -> {
            if (registerAttempts.incrementAndGet() == 1) {
                firstAttemptEntered.countDown();
                releaseFirstAttempt.await(5, TimeUnit.SECONDS);
                throw new RuntimeException("首次注册失败");
            }
            return null;
        }).when(syncEventBus).register(listener);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        executor.submit(() -> {
            try {
                syncProcessor.postProcessAfterInitialization(listener, "sameBeanName");
            } finally {
                doneLatch.countDown();
            }
        });
        assertTrue(firstAttemptEntered.await(5, TimeUnit.SECONDS));
        executor.submit(() -> {
            try {
                syncProcessor.postProcessAfterInitialization(listener, "sameBeanName");
            } finally {
                doneLatch.countDown();
            }
        });

        releaseFirstAttempt.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        verify(syncEventBus, times(2)).register(listener);
        assertTrue(syncProcessor.isBeanRegistered("sameBeanName"));
        assertEquals(1, syncProcessor.getRegisteredBeanCount());
    }


    @Test
    @DisplayName("测试处理器的空Bean处理功能")
    public void testNullBeanHandling() {
        Object result = syncProcessor.postProcessAfterInitialization(null, "nullBean");

        assertNull(result);
        verify(syncEventBus, never()).register(any());
    }

    @Test
    @DisplayName("测试处理器的异常处理功能")
    public void testExceptionHandling() {
        TestEventListener listener = new TestEventListener();

        doThrow(new RuntimeException("测试异常")).when(syncEventBus).register(listener);

        Object result = syncProcessor.postProcessAfterInitialization(listener, "testListener");

        assertNotNull(result);
        assertEquals(0, syncProcessor.getRegisteredBeanCount());

        AnotherTestEventListener anotherListener = new AnotherTestEventListener();
        syncProcessor.postProcessAfterInitialization(anotherListener, "anotherListener");

        verify(syncEventBus, times(1)).register(anotherListener);
    }

    @Test
    @DisplayName("测试异步处理器注册失败后会回滚注册状态")
    public void testAsyncRegistrationFailureRollsBackState() {
        TestEventListener listener = new TestEventListener();
        doThrow(new RuntimeException("测试异常")).when(asyncEventBus).register(listener);

        asyncProcessor.postProcessAfterInitialization(listener, "testListener");

        assertFalse(asyncProcessor.isBeanRegistered("testListener"));
        assertEquals(0, asyncProcessor.getRegisteredBeanCount());
    }

    @Test
    @DisplayName("测试异步处理器同名Bean并发注册失败后后续线程可重试")
    public void testAsyncConcurrentRegistrationRetriesAfterFailure() throws InterruptedException {
        TestEventListener listener = new TestEventListener();
        CountDownLatch firstAttemptEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstAttempt = new CountDownLatch(1);
        AtomicInteger registerAttempts = new AtomicInteger(0);

        doAnswer(invocation -> {
            if (registerAttempts.incrementAndGet() == 1) {
                firstAttemptEntered.countDown();
                releaseFirstAttempt.await(5, TimeUnit.SECONDS);
                throw new RuntimeException("首次注册失败");
            }
            return null;
        }).when(asyncEventBus).register(listener);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch doneLatch = new CountDownLatch(2);
        executor.submit(() -> {
            try {
                asyncProcessor.postProcessAfterInitialization(listener, "sameBeanName");
            } finally {
                doneLatch.countDown();
            }
        });
        assertTrue(firstAttemptEntered.await(5, TimeUnit.SECONDS));
        executor.submit(() -> {
            try {
                asyncProcessor.postProcessAfterInitialization(listener, "sameBeanName");
            } finally {
                doneLatch.countDown();
            }
        });

        releaseFirstAttempt.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        verify(asyncEventBus, times(2)).register(listener);
        assertTrue(asyncProcessor.isBeanRegistered("sameBeanName"));
        assertEquals(1, asyncProcessor.getRegisteredBeanCount());
    }

    @Test
    @DisplayName("测试处理器在多线程环境下的线程安全性")
    public void testThreadSafety() throws InterruptedException {
        GuavaSyncEventBusBeanPostProcessor realProcessor = new GuavaSyncEventBusBeanPostProcessor();
        ReflectionTestUtils.setField(realProcessor, "eventBus", new EventBus());

        int threadCount = 10;
        int beanCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < beanCount; j++) {
                        String beanName = "testBean-" + threadIndex + "-" + j;
                        TestEventListener listener = new TestEventListener();
                        realProcessor.postProcessAfterInitialization(listener, beanName);
                        if (realProcessor.isBeanRegistered(beanName)) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount * beanCount, successCount.get());
        assertEquals(threadCount * beanCount, realProcessor.getRegisteredBeanCount());

        realProcessor.destroy();
    }

    @Test
    @DisplayName("测试处理器的性能")
    public void testPerformance() {
        GuavaSyncEventBusBeanPostProcessor realProcessor = new GuavaSyncEventBusBeanPostProcessor();
        ReflectionTestUtils.setField(realProcessor, "eventBus", new EventBus());

        int beanCount = 1000;
        TestEventListener[] listeners = new TestEventListener[beanCount];
        for (int i = 0; i < beanCount; i++) {
            listeners[i] = new TestEventListener();
        }

        Instant start = Instant.now();
        for (int i = 0; i < beanCount; i++) {
            realProcessor.postProcessAfterInitialization(listeners[i], "testBean-" + i);
        }
        Duration duration = Duration.between(start, Instant.now());

        log.info("处理{}个Bean耗时: {}ms，平均每个Bean: {}ms",
                beanCount, duration.toMillis(), (double) duration.toMillis() / beanCount);

        assertEquals(beanCount, realProcessor.getRegisteredBeanCount());

        realProcessor.destroy();
    }

    @Data
    static class TestEventListener {
        private int event1Count = 0;
        private int event2Count = 0;

        @Subscribe
        public void handleEvent1(String event) {
            event1Count++;
        }

        @Subscribe
        public void handleEvent2(Integer event) {
            event2Count++;
        }
    }

    static class AnotherTestEventListener {
        @Subscribe
        public void handleEvent(Object event) {
        }
    }

    static class NoSubscribeListener {
        public void handleEvent(Object event) {
        }
    }
}
