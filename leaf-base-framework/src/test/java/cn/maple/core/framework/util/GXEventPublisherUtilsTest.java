package cn.maple.core.framework.util;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.event.GXBaseEvent;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXEventPublisherUtilsTest {
    private MockedStatic<GXSpringContextUtils> springContextUtils;

    @BeforeEach
    void setUp() {
        clearRegisterCache();
    }

    @AfterEach
    void tearDown() {
        if (springContextUtils != null) {
            springContextUtils.close();
        }
        clearRegisterCache();
    }

    @Test
    void syncAndAsyncPublishUseIndependentRegistrationCache() {
        EventBus syncEventBus = new EventBus();
        AsyncEventBus asyncEventBus = new AsyncEventBus(Runnable::run);
        TestListener listener = new TestListener();
        springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class);
        springContextUtils.when(() -> GXSpringContextUtils.getBean("eventBus", EventBus.class)).thenReturn(syncEventBus);
        springContextUtils.when(() -> GXSpringContextUtils.getBean("asyncEventBus", AsyncEventBus.class)).thenReturn(asyncEventBus);

        GXEventPublisherUtils.publishGuavaAsyncEvent(new GXBaseEvent<>("async"), listener);
        GXEventPublisherUtils.publishGuavaSyncEvent(new GXBaseEvent<>("sync"), listener);

        assertEquals(2, listener.getCount());
    }

    @Test
    void syncPublishRegistersListenerOnceWhenCalledConcurrently() throws Exception {
        SlowRegisterEventBus syncEventBus = new SlowRegisterEventBus();
        TestListener listener = new TestListener();
        ApplicationContext originalContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.registerBean("eventBus", EventBus.class, () -> syncEventBus);
        applicationContext.refresh();
        GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        Future<?> first = executor.submit(() -> {
            await(startGate);
            GXEventPublisherUtils.publishGuavaSyncEvent(new GXBaseEvent<>("first"), listener);
        });
        Future<?> second = executor.submit(() -> {
            await(startGate);
            GXEventPublisherUtils.publishGuavaSyncEvent(new GXBaseEvent<>("second"), listener);
        });

        startGate.countDown();

        try {
            assertDoesNotThrow(() -> first.get(5, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            if (originalContext == null) {
                GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
            } else {
                GXApplicationContextSingleton.INSTANCE.setApplicationContext(originalContext);
            }
            applicationContext.close();
        }

        assertEquals(2, listener.getCount());
        assertEquals(1, syncEventBus.getRegisterCount());
    }

    @Test
    void differentRegisterKeysDoNotContendOnSingleGlobalLock() throws Exception {
        CountDownLatch enteredRegister = new CountDownLatch(2);
        CountDownLatch releaseRegister = new CountDownLatch(1);
        BlockingRegisterEventBus firstBus = null;
        BlockingRegisterEventBus secondBus = null;
        TestListener firstListener = null;
        TestListener secondListener = null;
        for (int i = 0; i < 256; i++) {
            BlockingRegisterEventBus candidateFirstBus = new BlockingRegisterEventBus(enteredRegister, releaseRegister);
            BlockingRegisterEventBus candidateSecondBus = new BlockingRegisterEventBus(enteredRegister, releaseRegister);
            TestListener candidateFirstListener = new TestListener();
            TestListener candidateSecondListener = new TestListener();
            if (resolveRegisterStripe(candidateFirstBus, candidateFirstListener) != resolveRegisterStripe(candidateSecondBus, candidateSecondListener)) {
                firstBus = candidateFirstBus;
                secondBus = candidateSecondBus;
                firstListener = candidateFirstListener;
                secondListener = candidateSecondListener;
                break;
            }
        }
        if (firstBus == null || secondBus == null) {
            throw new IllegalStateException("Unable to find two distinct register stripes");
        }
        final BlockingRegisterEventBus selectedFirstBus = firstBus;
        final BlockingRegisterEventBus selectedSecondBus = secondBus;
        final TestListener selectedFirstListener = firstListener;
        final TestListener selectedSecondListener = secondListener;
        GXBaseEvent<String> firstEvent = new GXBaseEvent<>("first");
        GXBaseEvent<String> secondEvent = new GXBaseEvent<>("second");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() -> invokeRegisterAndPost(selectedFirstBus, selectedFirstListener, firstEvent));
        Future<?> second = executor.submit(() -> invokeRegisterAndPost(selectedSecondBus, selectedSecondListener, secondEvent));

        try {
            assertTrue(enteredRegister.await(1, TimeUnit.SECONDS));
            releaseRegister.countDown();
            assertDoesNotThrow(() -> first.get(5, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> second.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, selectedFirstBus.getRegisterCount());
        assertEquals(1, selectedSecondBus.getRegisterCount());
        assertEquals(1, selectedFirstListener.getCount());
        assertEquals(1, selectedSecondListener.getCount());
    }

    @SuppressWarnings("unchecked")
    private void clearRegisterCache() {
        ConcurrentHashMap<String, Boolean> cache = (ConcurrentHashMap<String, Boolean>) ReflectionTestUtils.getField(
                GXEventPublisherUtils.class,
                "EVENT_BUS_REGISTER_CACHE"
        );
        if (cache != null) {
            cache.clear();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void invokeRegisterAndPost(EventBus eventBus, TestListener listener, GXBaseEvent<String> event) {
        try {
            Method registerAndPostEvent = GXEventPublisherUtils.class.getDeclaredMethod(
                    "registerAndPostEvent",
                    EventBus.class,
                    Object.class,
                    GXBaseEvent.class
            );
            registerAndPostEvent.setAccessible(true);
            registerAndPostEvent.invoke(null, eventBus, listener, event);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private int resolveRegisterStripe(EventBus eventBus, Object listener) {
        String key = System.identityHashCode(eventBus) + ":" + System.identityHashCode(listener) + ":" + listener.getClass().getName();
        Integer stripeSize = (Integer) ReflectionTestUtils.getField(GXEventPublisherUtils.class, "REGISTER_LOCK_STRIPE_SIZE");
        if (stripeSize == null || stripeSize <= 0) {
            throw new IllegalStateException("Missing register stripe size");
        }
        return (key.hashCode() & Integer.MAX_VALUE) % stripeSize;
    }

    static class TestListener {
        private final AtomicInteger count = new AtomicInteger();

        @Subscribe
        public void onEvent(GXBaseEvent<String> event) {
            count.incrementAndGet();
        }

        int getCount() {
            return count.get();
        }
    }

    static class SlowRegisterEventBus extends EventBus {
        private final AtomicInteger registerCount = new AtomicInteger();

        @Override
        public void register(Object object) {
            registerCount.incrementAndGet();
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            super.register(object);
        }

        int getRegisterCount() {
            return registerCount.get();
        }
    }

    static class BlockingRegisterEventBus extends EventBus {
        private final AtomicInteger registerCount = new AtomicInteger();
        private final CountDownLatch enteredRegister;
        private final CountDownLatch releaseRegister;

        BlockingRegisterEventBus(CountDownLatch enteredRegister, CountDownLatch releaseRegister) {
            this.enteredRegister = enteredRegister;
            this.releaseRegister = releaseRegister;
        }

        @Override
        public void register(Object object) {
            registerCount.incrementAndGet();
            enteredRegister.countDown();
            try {
                assertTrue(releaseRegister.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            super.register(object);
        }

        int getRegisterCount() {
            return registerCount.get();
        }
    }
}
