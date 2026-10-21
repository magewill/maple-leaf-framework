package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXConditionEQ;
import cn.maple.core.framework.dto.inner.field.GXUpdateStrField;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 200 concurrent write operations spread over the 7 covered write operations, each going through the
 * real aspect chain, asserting every payload is delivered exactly once to the right operation.
 */
class GXMyBatisListenerConcurrencyTest {
    private static final int CONCURRENCY = 200;
    private static final int OPERATION_KINDS = 7;

    @Test
    void concurrentWritesDeliverEachOperationExactlyOnce() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        GXMyBatisListenerEventPublisher publisher =
                new GXMyBatisListenerEventPublisher(resolver, properties, new StandardEnvironment());
        GXMyBatisPlusSaveEntityAspect saveAspect = new GXMyBatisPlusSaveEntityAspect(publisher);
        GXMyBatisPlusUpdateEntityAspect updateAspect = new GXMyBatisPlusUpdateEntityAspect(publisher);
        GXMyBatisPlusUpdateFieldAspect updateFieldAspect = new GXMyBatisPlusUpdateFieldAspect(publisher);
        GXMyBatisPlusDeleteSoftAspect deleteSoftAspect = new GXMyBatisPlusDeleteSoftAspect(publisher);
        GXMyBatisPlusDeleteAspect deleteAspect = new GXMyBatisPlusDeleteAspect(publisher);
        GXMyBatisPlusSaveBatchEntityAspect saveBatchAspect = new GXMyBatisPlusSaveBatchEntityAspect(publisher);

        CollectingListener collector = new CollectingListener();
        AtomicIntegerArray expectedPerOperation = new AtomicIntegerArray(OPERATION_KINDS);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(CollectingListener.class, () -> collector);
            context.refresh();
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(context);

            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
            CountDownLatch ready = new CountDownLatch(CONCURRENCY);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(CONCURRENCY);
            for (int index = 0; index < CONCURRENCY; index++) {
                final int marker = index;
                final int operation = index % OPERATION_KINDS;
                expectedPerOperation.incrementAndGet(operation);
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        invokeOperation(operation, marker, saveAspect, updateAspect, updateFieldAspect,
                                deleteSoftAspect, deleteAspect, saveBatchAspect);
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS));
            pool.shutdownNow();

            assertNull(failure.get(), () -> "Concurrent delivery failed: " + failure.get());
            assertEquals(CONCURRENCY, collector.deliveredMarkers().size());
            assertEquals(CONCURRENCY, collector.deliveredOnce().size());
            for (int operation = 0; operation < OPERATION_KINDS; operation++) {
                final int currentOperation = operation;
                int expected = expectedPerOperation.get(operation);
                long actual = collector.deliveredMarkers().stream()
                        .filter(marker -> marker % OPERATION_KINDS == currentOperation)
                        .count();
                assertEquals(expected, actual, "Operation " + operation + " delivered a wrong number of events");
            }
        } finally {
            GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        }
    }

    private void invokeOperation(int operation, int marker,
                                 GXMyBatisPlusSaveEntityAspect saveAspect,
                                 GXMyBatisPlusUpdateEntityAspect updateAspect,
                                 GXMyBatisPlusUpdateFieldAspect updateFieldAspect,
                                 GXMyBatisPlusDeleteSoftAspect deleteSoftAspect,
                                 GXMyBatisPlusDeleteAspect deleteAspect,
                                 GXMyBatisPlusSaveBatchEntityAspect saveBatchAspect) throws Throwable {
        switch (operation) {
            case 0 -> saveAspect.around(joinPoint(new SaveMapperImpl(),
                    SaveMapper.class.getMethod("insert", Object.class), new Object[]{Map.of("marker", marker)}));
            case 1 -> updateAspect.around(joinPoint(new UpdateMapperImpl(),
                    UpdateMapper.class.getMethod("update", Object.class, Wrapper.class),
                    new Object[]{null, new UpdateWrapper<Object>().eq("marker", marker)}));
            case 2 -> updateAspect.around(joinPoint(new UpdateMapperImpl(),
                    UpdateMapper.class.getMethod("updateById", Object.class),
                    new Object[]{Map.of("marker", marker)}));
            case 3 -> updateFieldAspect.around(joinPoint(new UpdateFieldMapperImpl(),
                    UpdateFieldMapper.class.getMethod("updateFieldByCondition", Object.class, Object.class),
                    new Object[]{conditionById(marker), List.of(new GXUpdateStrField(null, "status", "updated"))}));
            case 4 -> deleteSoftAspect.around(joinPoint(new DeleteSoftMapperImpl(),
                    DeleteSoftMapper.class.getMethod("deleteSoftCondition", Object.class, Object.class),
                    new Object[]{conditionById(marker), List.of(new GXUpdateStrField(null, "status", "deleted"))}));
            case 5 -> deleteAspect.around(joinPoint(new DeleteMapperImpl(),
                    DeleteMapper.class.getMethod("deleteCondition", Object.class),
                    new Object[]{conditionById(marker)}));
            default -> saveBatchAspect.around(joinPoint(new BatchService(),
                    BatchService.class.getMethod("saveBatch", Collection.class),
                    new Object[]{List.of(Map.of("marker", marker))}));
        }
    }

    private GXBaseQueryParamInnerDto conditionById(int marker) {
        return GXBaseQueryParamInnerDto.builder()
                .tableName("t_listener_concurrency")
                .condition(List.of(new GXConditionEQ(null, "marker", marker)))
                .build();
    }

    private ProceedingJoinPoint joinPoint(Object target, Method method, Object[] args) {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getTarget()).thenReturn(target);
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod()).thenReturn(method);
        Mockito.when(signature.getName()).thenReturn(method.getName());
        Mockito.when(point.getArgs()).thenReturn(args);
        try {
            Mockito.when(point.proceed()).thenReturn(1);
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
        return point;
    }

    static class CollectingListener implements ApplicationListener<ApplicationEvent> {
        private final Set<Integer> deliveredOnce = ConcurrentHashMap.newKeySet();
        private final List<Integer> deliveredMarkers = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (!(event instanceof cn.maple.core.framework.event.GXBaseEvent<?> baseEvent)
                    || !(baseEvent.getSource() instanceof Dict source)) {
                return;
            }
            Integer marker = extractMarker(source);
            if (marker == null) {
                return;
            }
            deliveredMarkers.add(marker);
            deliveredOnce.add(marker);
        }

        List<Integer> deliveredMarkers() {
            return deliveredMarkers;
        }

        Set<Integer> deliveredOnce() {
            return deliveredOnce;
        }

        private Integer extractMarker(Dict source) {
            Integer direct = asMarker(source.get("marker"));
            if (direct != null) {
                return direct;
            }
            for (String key : List.of("entityData", "keyValuePairs", "conditionFieldData", "updateFieldData")) {
                if (source.get(key) instanceof Map<?, ?> nested) {
                    Integer marker = asMarker(nested.get("marker"));
                    if (marker != null) {
                        return marker;
                    }
                }
            }
            if (source.get("batchEntities") instanceof List<?> batch && !batch.isEmpty()
                    && batch.getFirst() instanceof Map<?, ?> first) {
                return asMarker(first.get("marker"));
            }
            return null;
        }

        private Integer asMarker(Object value) {
            return value instanceof Number number ? number.intValue() : null;
        }
    }

    @GXMyBatisListener(listenerClazz = ConcurrencyListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface SaveMapper {
        Object insert(Object entity);
    }

    @GXMyBatisListener(listenerClazz = ConcurrencyListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface UpdateMapper {
        Object update(Object entity, Wrapper<Object> wrapper);

        Object updateById(Object entity);
    }

    @GXMyBatisListener(listenerClazz = ConcurrencyListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface UpdateFieldMapper {
        Object updateFieldByCondition(Object query, Object updateFields);
    }

    @GXMyBatisListener(listenerClazz = ConcurrencyListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface DeleteSoftMapper {
        Object deleteSoftCondition(Object query, Object updateFields);
    }

    @GXMyBatisListener(listenerClazz = ConcurrencyListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface DeleteMapper {
        Object deleteCondition(Object query);
    }

    private static class SaveMapperImpl implements SaveMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class UpdateMapperImpl implements UpdateMapper {
        @Override
        public Object update(Object entity, Wrapper<Object> wrapper) {
            return 1;
        }

        @Override
        public Object updateById(Object entity) {
            return 1;
        }
    }

    private static class UpdateFieldMapperImpl implements UpdateFieldMapper {
        @Override
        public Object updateFieldByCondition(Object query, Object updateFields) {
            return 1;
        }
    }

    private static class DeleteSoftMapperImpl implements DeleteSoftMapper {
        @Override
        public Object deleteSoftCondition(Object query, Object updateFields) {
            return 1;
        }
    }

    private static class DeleteMapperImpl implements DeleteMapper {
        @Override
        public Object deleteCondition(Object query) {
            return 1;
        }
    }

    private static class BatchService {
        @GXMyBatisListener(listenerClazz = ConcurrencyListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
        public Object saveBatch(Collection<?> entities) {
            return entities;
        }
    }

    private static class ConcurrencyListener implements GXMybatisListenerService<Object> {
    }
}
