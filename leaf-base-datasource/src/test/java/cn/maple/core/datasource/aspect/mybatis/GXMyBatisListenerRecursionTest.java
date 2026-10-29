package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerTaskContext;
import cn.maple.core.datasource.config.GXLogCapture;
import cn.maple.core.datasource.config.GXMyBatisAsyncListenerExecutorConfig;
import cn.maple.core.datasource.config.GXMyBatisAsyncListenerProperties;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.apache.logging.log4j.Level;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the spec edge case "递归触发": a listener that writes through an annotated mapper must not
 * recurse without bound, in either the synchronous or the asynchronous delivery mode.
 */
class GXMyBatisListenerRecursionTest {
    private static final int MAX_DELIVERY_DEPTH = GXMyBatisListenerEventPublisher.MAX_DELIVERY_DEPTH;

    private final GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
    private final GXMyBatisListenerEventPublisher publisher = new GXMyBatisListenerEventPublisher(
            new GXMyBatisListenerConfigResolver(properties), properties, new StandardEnvironment());
    private final GXMyBatisPlusSaveEntityAspect saveEntityAspect = new GXMyBatisPlusSaveEntityAspect(publisher);
    private final GXMyBatisSyncListener syncListener = new GXMyBatisSyncListener();

    @Test
    void synchronousRecursiveWriteStopsAtTheDepthLimit() throws Throwable {
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        RecursiveListener listener = new RecursiveListener(saveEntityAspect);
        GXLogCapture capture = GXLogCapture.attach(GXMyBatisListenerEventPublisher.class);
        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class);
             MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            stubListenerBean(springContext, listener);
            stubSynchronousDispatch(eventPublisher);

            Object result = saveEntityAspect.around(listener.joinPoint());

            assertEquals(1, result, "A successful write must keep its result even when a listener recurses");
            assertEquals(MAX_DELIVERY_DEPTH, listener.deliveries(), "The chain must stop at the depth limit");
            assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                    "The suppressed delivery must be counted once and stay distinguishable from failures");
            assertTrue(capture.hasEvent(Level.WARN, "recursion suppressed"),
                    "The suppressed delivery must be logged");
            assertTrue(capture.hasEvent(Level.WARN, "reason=recursion-limit"),
                    "The suppression log must carry a reason");
            assertEquals(0, GXMyBatisListenerTaskContext.currentDeliveryDepth(),
                    "The callback depth must be restored after the chain");
        } finally {
            capture.detach();
        }
    }

    @Test
    void asynchronousCallbackChainKeepsCountingDepthAndStopsAtTheLimit() throws Exception {
        GXMyBatisAsyncListenerExecutorConfig config = executorConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.myBatisEventAsyncTaskExecutor();
        int startDepth = MAX_DELIVERY_DEPTH - 2;
        Method method = RecursiveMapper.class.getMethod("insert", Object.class);
        RecursiveListener listener = new RecursiveListener(saveEntityAspect);
        AtomicInteger observedDepth = new AtomicInteger(-1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();

        GXMyBatisListenerTaskContext.setDeliveryDepth(startDepth);
        try {
            executor.submit(() -> {
                observedDepth.set(GXMyBatisListenerTaskContext.currentDeliveryDepth());
                try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class);
                     MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
                    stubListenerBean(springContext, listener);
                    stubSynchronousDispatch(eventPublisher);
                    publisher.publish(new RecursiveMapperImpl(), method,
                            GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                            Dict.create().set("id", 1));
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            }).get(5, TimeUnit.SECONDS);

            // Asserted before the cleanup below so it can actually fail: the executor thread runs
            // with the submitting depth and restores its own, the submitting thread keeps its depth.
            assertEquals(startDepth, GXMyBatisListenerTaskContext.currentDeliveryDepth(),
                    "The submitting thread must keep its own delivery depth");
        } finally {
            GXMyBatisListenerTaskContext.setDeliveryDepth(0);
            executor.shutdown();
        }

        assertNull(failure.get(), () -> "The asynchronous chain must not fail: " + failure.get());
        assertEquals(startDepth, observedDepth.get(),
                "The task must run at the delivery depth of the write that submitted it");
        assertEquals(MAX_DELIVERY_DEPTH - startDepth, listener.deliveries(),
                "Only the remaining depth budget may be delivered inside the task");
        assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed());
    }

    @SuppressWarnings("unchecked")
    private void stubListenerBean(MockedStatic<GXSpringContextUtils> springContext, RecursiveListener listener) {
        springContext.when(() -> GXSpringContextUtils.getBean(Mockito.anyString(), Mockito.any(Class.class)))
                .thenReturn(listener);
        springContext.when(() -> GXSpringContextUtils.getBean(Mockito.anyString())).thenReturn(listener);
        springContext.when(() -> GXSpringContextUtils.getBean(Mockito.any(Class.class))).thenReturn(listener);
    }

    private void stubSynchronousDispatch(MockedStatic<GXEventPublisherUtils> eventPublisher) {
        eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(Mockito.any())).thenAnswer(invocation -> {
            dispatch(invocation.getArgument(0));
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private void dispatch(Object event) {
        syncListener.listenerSaveEntity((GXMyBatisModelSaveEntityEvent<Dict>) event);
    }

    private GXMyBatisAsyncListenerExecutorConfig executorConfig() {
        GXMyBatisAsyncListenerProperties asyncProperties = new GXMyBatisAsyncListenerProperties();
        asyncProperties.setCorePoolSizeFactor(0.001D);
        asyncProperties.setMaxPoolSizeFactor(0.001D);
        asyncProperties.setQueueCapacity(4);
        asyncProperties.setKeepAliveSeconds(0);
        asyncProperties.setLongTaskThresholdMs(0L);
        asyncProperties.setAwaitTerminationSeconds(1);
        asyncProperties.getVirtual().setEnabled(false);
        return new GXMyBatisAsyncListenerExecutorConfig(asyncProperties, new StandardEnvironment());
    }

    /**
     * Listener that writes again through the annotated mapper, which is exactly the "递归触发" edge
     * case: the nested write is matched by the same declaration.
     */
    private static final class RecursiveListener implements GXMybatisListenerService<Object> {
        private final GXMyBatisPlusSaveEntityAspect aspect;
        private final ProceedingJoinPoint point;
        private final AtomicInteger deliveries = new AtomicInteger();

        private RecursiveListener(GXMyBatisPlusSaveEntityAspect aspect) {
            this.aspect = aspect;
            this.point = nestedWrite();
        }

        private ProceedingJoinPoint joinPoint() {
            return point;
        }

        private int deliveries() {
            return deliveries.get();
        }

        @Override
        public void saveEntityListener(Object data) {
            deliveries.incrementAndGet();
            try {
                aspect.around(point);
            } catch (RuntimeException | Error throwable) {
                throw throwable;
            } catch (Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        }

        private static ProceedingJoinPoint nestedWrite() {
            try {
                Method method = RecursiveMapper.class.getMethod("insert", Object.class);
                ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
                MethodSignature signature = Mockito.mock(MethodSignature.class);
                Mockito.when(point.getTarget()).thenReturn(new RecursiveMapperImpl());
                Mockito.when(point.getSignature()).thenReturn(signature);
                Mockito.when(signature.getMethod()).thenReturn(method);
                Mockito.when(signature.getName()).thenReturn(method.getName());
                Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});
                Mockito.when(point.proceed()).thenReturn(1);
                return point;
            } catch (NoSuchMethodException | RuntimeException exception) {
                throw new IllegalStateException(exception);
            } catch (Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        }
    }

    @GXMyBatisListener(listenerClazz = RecursiveListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface RecursiveMapper {
        Object insert(Object entity);
    }

    private static final class RecursiveMapperImpl implements RecursiveMapper {
        @Override
        public Object insert(Object entity) {
            return 1;
        }
    }
}
