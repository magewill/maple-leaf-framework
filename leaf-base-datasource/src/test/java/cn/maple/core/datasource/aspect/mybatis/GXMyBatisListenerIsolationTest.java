package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
import cn.maple.core.datasource.listener.GXMyBatisAsyncListener;
import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.task.TaskRejectedException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class GXMyBatisListenerIsolationTest {
    @Test
    void deliveryFailureDoesNotStopTheNextListener() throws Throwable {
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(new GXMyBatisListenerProperties());
        ProceedingJoinPoint point = savePoint();
        AtomicInteger deliveries = new AtomicInteger();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();

        try (MockedStatic<GXEventPublisherUtils> publisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                if (deliveries.incrementAndGet() == 1) {
                    throw new IllegalStateException("listener boom");
                }
                return null;
            });

            Object result = assertDoesNotThrow(() -> aspect.around(point));
            assertEquals(2, deliveries.get());
            assertEquals(1, result, "Business write result must be unchanged by listener failures");
            assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                    "The failing listener delivery must be counted as failed exactly once");
        }
    }

    @Test
    void propagateSwitchRestoresLegacyExceptionBehaviour() {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setPropagateException(true);
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(properties);
        ProceedingJoinPoint point = savePoint();

        try (MockedStatic<GXEventPublisherUtils> publisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenThrow(new IllegalStateException("listener boom"));

            assertThrows(IllegalStateException.class, () -> aspect.around(point));
        }
    }

    @Test
    void propagateModeDeliveryFailureIsCountedAndRethrown() {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setPropagateException(true);
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(properties);
        ProceedingJoinPoint point = savePoint();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();

        try (MockedStatic<GXEventPublisherUtils> publisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenThrow(new IllegalStateException("delivery boom"));

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, () -> aspect.around(point));
            assertEquals("delivery boom", failure.getMessage(),
                    "A delivery-layer failure must surface as-is when propagating");
        }
        assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                "A delivery-layer failure must be counted exactly once even when propagating");
    }

    @Test
    void realCallbackFailureIsIsolatedCountedOnceAndKeepsNextListener() throws Throwable {
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(new GXMyBatisListenerProperties());
        ProceedingJoinPoint point = savePoint();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        long deliveredBefore = GXMyBatisListenerMetrics.snapshot().delivered();
        BetaListener beta = new BetaListener();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class);
             MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            stubListenerBeans(springContext, beta);
            GXMyBatisSyncListener syncListener = new GXMyBatisSyncListener();
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                syncListener.listenerSaveEntity(invocation.getArgument(0));
                return null;
            });

            Object result = assertDoesNotThrow(() -> aspect.around(point));
            assertEquals(1, result, "Business write result must be unchanged by callback failures");
            assertEquals(1, beta.calls.get(), "The next listener must still be invoked");
            assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                    "The failing callback must be counted exactly once");
            // An isolated callback failure still counts as a delivered event; the counters are not
            // mutually exclusive (see GXMyBatisListenerMetrics). Both declared listeners are
            // delivered: the failing Alpha and the succeeding Beta.
            assertEquals(deliveredBefore + 2, GXMyBatisListenerMetrics.snapshot().delivered(),
                    "An isolated callback failure must still be counted as delivered");
        }
    }

    @Test
    void realCallbackFailurePropagatesAsIsAndStopsNextListener() throws Throwable {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setPropagateException(true);
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(properties);
        ProceedingJoinPoint point = savePoint();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        long deliveredBefore = GXMyBatisListenerMetrics.snapshot().delivered();
        BetaListener beta = new BetaListener();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class);
             MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            stubListenerBeans(springContext, beta);
            GXMyBatisSyncListener syncListener = new GXMyBatisSyncListener();
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                syncListener.listenerSaveEntity(invocation.getArgument(0));
                return null;
            });

            IllegalStateException failure =
                    assertThrows(IllegalStateException.class, () -> aspect.around(point));
            assertEquals("listener boom", failure.getMessage(),
                    "The original callback exception must surface as-is");
            assertEquals(0, beta.calls.get(), "Subsequent listeners must not run when propagating");
            assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                    "The propagating callback failure must still be counted exactly once");
            // The delivery call never returned normally, so it must not be counted as delivered.
            assertEquals(deliveredBefore, GXMyBatisListenerMetrics.snapshot().delivered(),
                    "A propagating callback failure must not be counted as delivered");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asyncRejectionDoesNotFailBusinessWrite(boolean propagate) throws Throwable {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setPropagateException(propagate);
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(properties);
        ProceedingJoinPoint point = asyncSavePoint();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        long rejectedBefore = GXMyBatisListenerMetrics.snapshot().rejected();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class);
             MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            stubListenerBeans(springContext, new BetaListener());
            eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any()))
                    .thenAnswer(invocation -> {
                        // The executor's rejection handler counts and logs before the exception
                        // travels back through the delivery layer.
                        GXMyBatisListenerMetrics.recordRejected();
                        throw new TaskRejectedException("queue full");
                    });

            // FR-016: a rejected asynchronous submission must keep the committed write successful in
            // both switch modes, while staying observable through the counters and logs.
            assertEquals(1, assertDoesNotThrow(() -> aspect.around(point)),
                    "Rejected async submissions must not fail the business write when propagate=" + propagate);
            assertEquals(rejectedBefore + 1, GXMyBatisListenerMetrics.snapshot().rejected(),
                    "The rejection must stay visible in the rejected counter");
            // FR-020: rejected and failed are distinct categories; a rejection already counted by the
            // executor must not inflate failed as well.
            assertEquals(failedBefore, GXMyBatisListenerMetrics.snapshot().failed(),
                    "A counted rejection must not be counted as failed again");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asyncCallbackFailureIsIsolatedInBothSwitchModes(boolean propagate) throws Throwable {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setPropagateException(propagate);
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(properties);
        ProceedingJoinPoint point = asyncSavePoint();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        long deliveredBefore = GXMyBatisListenerMetrics.snapshot().delivered();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class);
             MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            stubListenerBeans(springContext, new BetaListener());
            GXMyBatisAsyncListener asyncListener = new GXMyBatisAsyncListener();
            eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any()))
                    .thenAnswer(invocation -> {
                        asyncListener.listenerSaveEntity(invocation.getArgument(0));
                        return null;
                    });

            // Contract (listener-callback.md): asynchronous callback failures must never reach the
            // business caller or the executor thread, regardless of the propagate-exception switch.
            assertEquals(1, assertDoesNotThrow(() -> aspect.around(point)),
                    "Async callback failures must stay isolated when propagate=" + propagate);
            assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                    "Async callback failures must be counted in both switch modes");
            assertEquals(deliveredBefore + 1, GXMyBatisListenerMetrics.snapshot().delivered(),
                    "An isolated async callback failure must still be counted as delivered");
        }
    }

    @Test
    void syncAfterCommitCallbackFailureIsIsolatedEvenWhenPropagating() throws Throwable {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setSyncAfterCommit(true);
        properties.setPropagateException(true);
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(properties);
        ProceedingJoinPoint point = savePoint();
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();
        BetaListener beta = new BetaListener();
        List<GXMyBatisModelSaveEntityEvent<Dict>> deferredEvents = new CopyOnWriteArrayList<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class);
             MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            stubListenerBeans(springContext, beta);
            eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any()))
                    .thenAnswer(invocation -> {
                        deferredEvents.add(invocation.getArgument(0));
                        return null;
                    });

            // The business caller is gone once the deferred callback runs, so the write must stay
            // successful even with propagate-exception=true (contracts/listener-callback.md).
            assertEquals(1, assertDoesNotThrow(() -> aspect.around(point)));
            assertEquals(2, deferredEvents.size(), "Both synchronous listeners must be deferred");

            GXMyBatisSyncListener syncListener = new GXMyBatisSyncListener();
            assertDoesNotThrow(() -> deferredEvents.forEach(syncListener::listenerSaveEntity),
                    "A deferred callback failure must never escape to the executor thread");
        }

        assertEquals(1, beta.calls.get(), "A failing deferred listener must not stop the next one");
        assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                "The deferred callback failure must be counted exactly once");
        assertNotNull(deferredEvents.getFirst().getParam(), "Deferred events keep their param map");
        assertNull(deferredEvents.getFirst().getParam().get(GXMyBatisEventConstant.PENDING_CALLBACK_FAILURE),
                "A deferred callback must not stash a pending failure nobody can consume");
    }

    @SuppressWarnings("unchecked")
    private void stubListenerBeans(MockedStatic<GXSpringContextUtils> springContext, BetaListener beta) {
        AlphaListener alpha = new AlphaListener();
        springContext.when(() -> GXSpringContextUtils.getBean(anyString(), Mockito.any(Class.class)))
                .thenAnswer(invocation -> {
                    Class<?> required = invocation.getArgument(1);
                    return required == BetaListener.class ? beta : alpha;
                });
    }

    private GXMyBatisPlusSaveEntityAspect aspectWith(GXMyBatisListenerProperties properties) {
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        GXMyBatisListenerEventPublisher eventPublisher =
                new GXMyBatisListenerEventPublisher(resolver, properties, new StandardEnvironment());
        return new GXMyBatisPlusSaveEntityAspect(eventPublisher);
    }

    private ProceedingJoinPoint savePoint() {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Method method;
        try {
            method = AlphaMapper.class.getMethod("insert", Object.class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
        Mockito.when(point.getTarget()).thenReturn(new DualListenerMapperImpl());
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod()).thenReturn(method);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 1)});
        try {
            Mockito.when(point.proceed()).thenReturn(1);
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
        return point;
    }

    private ProceedingJoinPoint asyncSavePoint() {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Method method;
        try {
            method = AsyncSaveMapper.class.getMethod("insert", Object.class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
        Mockito.when(point.getTarget()).thenReturn(new AsyncSaveMapperImpl());
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod()).thenReturn(method);
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 2)});
        try {
            Mockito.when(point.proceed()).thenReturn(1);
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
        return point;
    }

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface AlphaMapper {
        Object insert(Object entity);
    }

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
    private interface AsyncSaveMapper {
        Object insert(Object entity);
    }

    private static class AsyncSaveMapperImpl implements AsyncSaveMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    @GXMyBatisListener(listenerClazz = BetaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface BetaMapper {
        Object insert(Object entity);
    }

    private static class DualListenerMapperImpl implements AlphaMapper, BetaMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class AlphaListener implements GXMybatisListenerService<Object> {
        @Override
        public void saveEntityListener(Object entity) {
            throw new IllegalStateException("listener boom");
        }
    }

    private static class BetaListener implements GXMybatisListenerService<Object> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public void saveEntityListener(Object entity) {
            calls.incrementAndGet();
        }
    }
}
