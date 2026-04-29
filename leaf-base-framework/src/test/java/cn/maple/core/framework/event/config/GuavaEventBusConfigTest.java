package cn.maple.core.framework.event.config;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Guava事件总线配置测试")
public class GuavaEventBusConfigTest {
    private GuavaEventBusConfig config;

    @Mock
    private ApplicationContext mockContext;

    private MockedStatic<cn.maple.core.framework.util.GXCommonUtils> mockedCommonUtils;

    @BeforeEach
    public void setUp() {
        config = new GuavaEventBusConfig();

        mockedCommonUtils = Mockito.mockStatic(cn.maple.core.framework.util.GXCommonUtils.class);
        mockedCommonUtils.when(() ->
                cn.maple.core.framework.util.GXCommonUtils.getEnvironmentValue(
                        eq("spring.application.name"),
                        eq(String.class),
                        anyString())
        ).thenReturn("test-app");
        mockedCommonUtils.when(() ->
                cn.maple.core.framework.util.GXCommonUtils.getEnvironmentValue(
                        eq("maple.event.guava.virtual-threads.enabled"),
                        eq(Boolean.class),
                        anyBoolean())
        ).thenReturn(true);
    }

    @AfterEach
    public void tearDown() {
        config.destroy();
        mockedCommonUtils.close();
    }

    @Test
    @DisplayName("测试同步事件总线创建")
    public void testEventBusCreation() {
        EventBus eventBus = config.eventBus();

        assertNotNull(eventBus, "同步事件总线不应为空");

        TestEvent testEvent = new TestEvent("test-data");
        TestSubscriber subscriber = new TestSubscriber();

        eventBus.register(subscriber);

        eventBus.post(testEvent);

        assertEquals("test-data", subscriber.getLastEventData(), "事件数据应正确传递");
        assertEquals(1, subscriber.getEventCount(), "应处理一个事件");

        eventBus.unregister(subscriber);

        eventBus.post(new TestEvent("ignored-data"));

        assertEquals(1, subscriber.getEventCount(), "注销后不应再处理事件");
    }

    @Test
    @DisplayName("测试异步事件总线创建")
    public void testAsyncEventBusCreation() throws Exception {
        AsyncEventBus asyncEventBus = config.asyncEventBus();

        assertNotNull(asyncEventBus, "异步事件总线不应为空");

        TestEvent testEvent = new TestEvent("async-test-data");
        AsyncTestSubscriber subscriber = new AsyncTestSubscriber();
        CountDownLatch latch = new CountDownLatch(1);
        subscriber.setLatch(latch);

        asyncEventBus.register(subscriber);

        asyncEventBus.post(testEvent);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "异步事件处理应在5秒内完成");

        assertEquals("async-test-data", subscriber.getLastEventData(), "异步事件数据应正确传递");
        assertEquals(1, subscriber.getEventCount(), "应处理一个事件");
    }

    @Test
    @DisplayName("测试异步事件执行器配置")
    public void testAsyncExecutorConfiguration() {
        AsyncEventBus asyncEventBus = config.asyncEventBus();

        Executor executor = config.getAsyncEventBusExecutor();

        assertNotNull(asyncEventBus, "异步事件总线不应为空");
        assertNotNull(executor, "异步事件执行器不应为空");
        assertInstanceOf(SimpleAsyncTaskExecutor.class, executor, "默认应使用支持虚拟线程的SimpleAsyncTaskExecutor");
        assertTrue(((SimpleAsyncTaskExecutor) executor).isActive(), "异步事件执行器初始化后应处于活动状态");
    }

    @Test
    @DisplayName("测试平台线程池配置")
    public void testPlatformThreadPoolTaskExecutorConfiguration() {
        mockedCommonUtils.when(() ->
                cn.maple.core.framework.util.GXCommonUtils.getEnvironmentValue(
                        eq("maple.event.guava.virtual-threads.enabled"),
                        eq(Boolean.class),
                        anyBoolean())
        ).thenReturn(false);

        config.asyncEventBus();

        Executor executor = config.getAsyncEventBusExecutor();

        assertInstanceOf(ThreadPoolTaskExecutor.class, executor, "关闭虚拟线程时应使用ThreadPoolTaskExecutor");
        ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) executor;
        assertEquals(Runtime.getRuntime().availableProcessors(), threadPoolTaskExecutor.getCorePoolSize(), "核心线程数应等于CPU核心数");
        assertTrue(threadPoolTaskExecutor.getMaxPoolSize() >= threadPoolTaskExecutor.getCorePoolSize(), "最大线程数应大于等于核心线程数");
        assertTrue(threadPoolTaskExecutor.getKeepAliveSeconds() > 0, "空闲线程存活时间应大于0");
        assertTrue(threadPoolTaskExecutor.getQueueCapacity() > 0, "工作队列容量应大于0");
    }

    @Test
    @DisplayName("测试平台线程池拒绝策略")
    public void testPlatformThreadPoolRejectionHandler() {
        mockedCommonUtils.when(() ->
                cn.maple.core.framework.util.GXCommonUtils.getEnvironmentValue(
                        eq("maple.event.guava.virtual-threads.enabled"),
                        eq(Boolean.class),
                        anyBoolean())
        ).thenReturn(false);

        config.asyncEventBus();

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) config.getAsyncEventBusExecutor();
        LongAdder rejectedTaskCount = (LongAdder) ReflectionTestUtils.getField(config, "rejectedTaskCount");
        assertNotNull(rejectedTaskCount, "拒绝任务计数器不应为空");
        long initialCount = rejectedTaskCount.sum();
        AtomicBoolean executedInCaller = new AtomicBoolean(false);

        RejectedExecutionHandler handler = taskExecutor.getThreadPoolExecutor().getRejectedExecutionHandler();
        handler.rejectedExecution(() -> executedInCaller.set(true), taskExecutor.getThreadPoolExecutor());

        assertTrue(executedInCaller.get(), "拒绝策略应在调用者线程执行任务");
        assertEquals(initialCount + 1, rejectedTaskCount.sum(), "拒绝任务计数应增加");
    }

    @Test
    @DisplayName("测试JDK21虚拟线程执行异步事件")
    public void testAsyncEventRunsOnVirtualThread() throws Exception {
        AsyncEventBus asyncEventBus = config.asyncEventBus();

        VirtualThreadSubscriber subscriber = new VirtualThreadSubscriber();
        CountDownLatch latch = new CountDownLatch(1);
        subscriber.setLatch(latch);

        asyncEventBus.register(subscriber);
        asyncEventBus.post(new TestEvent("virtual-thread-test"));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "异步事件处理应在5秒内完成");

        assertTrue(subscriber.isVirtualThread(), "JDK21环境下异步事件应由虚拟线程执行");
        assertTrue(subscriber.getThreadName().startsWith("async-test-app-event-vt-"), "虚拟线程名称应包含事件总线标识");
    }

    @Test
    @DisplayName("测试异常处理")
    public void testExceptionHandling() throws Exception {
        AsyncEventBus asyncEventBus = config.asyncEventBus();

        LongAdder exceptionCount = (LongAdder) ReflectionTestUtils.getField(config, "exceptionCount");
        assertNotNull(exceptionCount, "异常计数器不应为空");

        long initialCount = exceptionCount.sum();

        ExceptionThrowingSubscriber subscriber = new ExceptionThrowingSubscriber();
        CountDownLatch latch = new CountDownLatch(1);
        subscriber.setLatch(latch);

        asyncEventBus.register(subscriber);

        asyncEventBus.post(new TestEvent("exception-test"));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "异步事件处理应在5秒内完成");

        asyncEventBus.unregister(subscriber);

        assertTrue(subscriber.isExceptionThrown(), "订阅者应抛出异常");
        waitUntilAsserted(() -> assertEquals(initialCount + 1, exceptionCount.sum(), "异常计数应增加"));
    }

    @Test
    @DisplayName("测试高并发场景")
    public void testHighConcurrency() throws Exception {
        AsyncEventBus asyncEventBus = config.asyncEventBus();

        ConcurrentTestSubscriber subscriber = new ConcurrentTestSubscriber();
        asyncEventBus.register(subscriber);

        int eventCount = 1000;
        CountDownLatch latch = new CountDownLatch(eventCount);
        subscriber.setLatch(latch);

        for (int i = 0; i < eventCount; i++) {
            asyncEventBus.post(new TestEvent("concurrent-" + i));
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "所有事件应在30秒内处理完成");

        assertEquals(eventCount, subscriber.getEventCount(), "应处理所有事件");

        asyncEventBus.unregister(subscriber);
    }

    @Test
    @DisplayName("测试应用上下文设置")
    public void testSetApplicationContext() {
        ApplicationContext originalContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        try {
            ReflectionTestUtils.setField(GXApplicationContextSingleton.INSTANCE, "applicationContext", null);

            config.setApplicationContext(mockContext);

            assertSame(mockContext, GXApplicationContextSingleton.INSTANCE.getApplicationContext());
        } finally {
            ReflectionTestUtils.setField(GXApplicationContextSingleton.INSTANCE, "applicationContext", originalContext);
        }
    }

    @Test
    @DisplayName("测试异步线程池销毁")
    public void testDestroyShutsDownAsyncExecutor() {
        config.asyncEventBus();

        Executor executor = config.getAsyncEventBusExecutor();
        assertNotNull(executor, "异步事件执行器不应为空");

        config.destroy();

        assertInstanceOf(SimpleAsyncTaskExecutor.class, executor, "默认应使用SimpleAsyncTaskExecutor");
        assertFalse(((SimpleAsyncTaskExecutor) executor).isActive(), "销毁配置时应关闭异步事件执行器");
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        }), "异步事件执行器关闭后应拒绝新任务");
    }

    @Test
    @DisplayName("测试平台线程池销毁")
    public void testDestroyShutsDownPlatformExecutor() {
        mockedCommonUtils.when(() ->
                cn.maple.core.framework.util.GXCommonUtils.getEnvironmentValue(
                        eq("maple.event.guava.virtual-threads.enabled"),
                        eq(Boolean.class),
                        anyBoolean())
        ).thenReturn(false);

        config.asyncEventBus();

        Executor executor = config.getAsyncEventBusExecutor();
        assertInstanceOf(ThreadPoolTaskExecutor.class, executor, "关闭虚拟线程时应使用ThreadPoolTaskExecutor");

        config.destroy();

        assertTrue(((ThreadPoolTaskExecutor) executor).getThreadPoolExecutor().isShutdown(), "销毁配置时应关闭平台线程池");
    }

    private void waitUntilAsserted(Runnable assertion) throws InterruptedException {
        AssertionError lastError = null;
        for (int i = 0; i < 50; i++) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                lastError = e;
                TimeUnit.MILLISECONDS.sleep(100);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    record TestEvent(String data) {
    }

    static class TestSubscriber {
        private final AtomicInteger eventCount = new AtomicInteger(0);
        private String lastEventData;

        @Subscribe
        public void handleEvent(TestEvent event) {
            this.lastEventData = event.data();
            this.eventCount.incrementAndGet();
        }

        public String getLastEventData() {
            return lastEventData;
        }

        public int getEventCount() {
            return eventCount.get();
        }
    }

    static class AsyncTestSubscriber {
        private final AtomicInteger eventCount = new AtomicInteger(0);
        private String lastEventData;
        private CountDownLatch latch;

        @Subscribe
        public void handleEvent(TestEvent event) {
            this.lastEventData = event.data();
            this.eventCount.incrementAndGet();
            if (latch != null) {
                latch.countDown();
            }
        }

        public void setLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        public String getLastEventData() {
            return lastEventData;
        }

        public int getEventCount() {
            return eventCount.get();
        }
    }

    static class ExceptionThrowingSubscriber {
        private boolean exceptionThrown = false;
        private CountDownLatch latch;

        @Subscribe
        public void handleEvent(TestEvent event) {
            try {
                throw new RuntimeException("测试异常");
            } finally {
                exceptionThrown = true;
                if (latch != null) {
                    latch.countDown();
                }
            }
        }

        public void setLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        public boolean isExceptionThrown() {
            return exceptionThrown;
        }
    }

    static class ConcurrentTestSubscriber {
        private final AtomicInteger eventCount = new AtomicInteger(0);
        private CountDownLatch latch;

        @Subscribe
        public void handleEvent(TestEvent event) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            this.eventCount.incrementAndGet();
            if (latch != null) {
                latch.countDown();
            }
        }

        public void setLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        public int getEventCount() {
            return eventCount.get();
        }
    }

    static class VirtualThreadSubscriber {
        private volatile boolean virtualThread;
        private volatile String threadName;
        private CountDownLatch latch;

        @Subscribe
        public void handleEvent(TestEvent event) {
            Thread currentThread = Thread.currentThread();
            this.virtualThread = currentThread.isVirtual();
            this.threadName = currentThread.getName();
            if (latch != null) {
                latch.countDown();
            }
        }

        public void setLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        public boolean isVirtualThread() {
            return virtualThread;
        }

        public String getThreadName() {
            return threadName;
        }
    }
}
