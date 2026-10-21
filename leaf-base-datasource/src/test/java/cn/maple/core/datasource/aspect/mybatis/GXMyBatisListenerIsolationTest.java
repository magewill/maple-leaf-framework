package cn.maple.core.datasource.aspect.mybatis;

import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

class GXMyBatisListenerIsolationTest {
    @Test
    void failingListenerDoesNotStopTheNextListener() throws Throwable {
        GXMyBatisPlusSaveEntityAspect aspect = aspectWith(new GXMyBatisListenerProperties());
        ProceedingJoinPoint point = savePoint();
        AtomicInteger deliveries = new AtomicInteger();

        try (MockedStatic<GXEventPublisherUtils> publisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                if (deliveries.incrementAndGet() == 1) {
                    throw new IllegalStateException("listener boom");
                }
                return null;
            });

            assertDoesNotThrow(() -> aspect.around(point));
            assertEquals(2, deliveries.get());
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

    @GXMyBatisListener(listenerClazz = AlphaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface AlphaMapper {
        Object insert(Object entity);
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
    }

    private static class BetaListener implements GXMybatisListenerService<Object> {
    }
}
