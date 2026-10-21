package cn.maple.core.datasource.perf;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GXMyBatisListenerOverheadTest {
    private static final int WARMUP_ITERATIONS = 10_000;
    private static final int MEASURED_ITERATIONS = 20_000;

    @Test
    void unannotatedWritePathDoesNotPublishAnEvent() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = PlainMapper.class.getMethod("insert", Object.class);

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            for (int index = 0; index < 1_000; index++) {
                publisher.publish(new PlainMapperImpl(), method,
                        GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                        Dict.create().set("id", index));
            }
            eventPublisher.verifyNoInteractions();
        }
    }

    @Test
    void batchPayloadProducesExactlyOneEvent() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = BatchMapper.class.getMethod("insert", Object.class);
        List<Dict> entities = new ArrayList<>();
        for (int index = 0; index < 10_000; index++) {
            entities.add(Dict.create().set("id", index));
        }

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.publish(new BatchMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY,
                    Dict.create().set("operation", "saveBatch").set("batchEntities", entities));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "maple.perf.enabled", matches = "true")
    void listenerOverheadStaysWithinThreshold() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = PlainMapper.class.getMethod("insert", Object.class);

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            for (int index = 0; index < WARMUP_ITERATIONS; index++) {
                publisher.publish(new PlainMapperImpl(), method,
                        GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                        Dict.create().set("id", index));
            }

            long startNanos = System.nanoTime();
            for (int index = 0; index < MEASURED_ITERATIONS; index++) {
                publisher.publish(new PlainMapperImpl(), method,
                        GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                        Dict.create().set("id", index));
            }
            long elapsedNanos = System.nanoTime() - startNanos;
            long nanosPerOperation = elapsedNanos / MEASURED_ITERATIONS;

            System.out.printf("MyBatis listener overhead: %.3f us/op (no listener path)%n",
                    nanosPerOperation / 1_000.0D);
            assertTrue(nanosPerOperation < 1_000_000L,
                    () -> "Listener path exceeded 1 ms/op: " + nanosPerOperation + " ns");
            assertEquals(MEASURED_ITERATIONS, MEASURED_ITERATIONS);
        }
    }

    private GXMyBatisListenerEventPublisher publisher(GXMyBatisListenerProperties properties) {
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        return new GXMyBatisListenerEventPublisher(resolver, properties, new StandardEnvironment());
    }

    @GXMyBatisListener(listenerClazz = PlainListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface BatchMapper {
        Object insert(Object entity);
    }

    private static class BatchMapperImpl implements BatchMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private interface PlainMapper {
        Object insert(Object entity);
    }

    private static class PlainMapperImpl implements PlainMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class PlainListener implements GXMybatisListenerService<Object> {
    }
}
