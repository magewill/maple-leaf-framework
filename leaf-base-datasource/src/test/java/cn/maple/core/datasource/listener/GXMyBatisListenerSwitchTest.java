package cn.maple.core.datasource.listener;

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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class GXMyBatisListenerSwitchTest {
    @Test
    void globalSwitchDisablesResolutionAndPublishing() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(GXMyBatisListenerEventPublisher.ENABLED_KEY, "false");
        GXMyBatisListenerEventPublisher publisher = publisher(environment);
        Method method = SwitchMapper.class.getMethod("insert", Object.class);

        assertFalse(publisher.isListenerEnabled());
        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.publish(new SwitchMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
            eventPublisher.verifyNoInteractions();
        }
    }

    @Test
    void asyncSwitchFallsBackToSynchronousDelivery() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(GXMyBatisListenerEventPublisher.ASYNC_ENABLED_KEY, "false");
        GXMyBatisListenerEventPublisher publisher = publisher(environment);
        Method method = AsyncSwitchMapper.class.getMethod("insert", Object.class);

        assertTrue(publisher.isListenerEnabled());
        assertFalse(publisher.isAsyncModeEnabled());
        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.publish(new AsyncSwitchMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEvent(any()), Mockito.times(1));
            eventPublisher.verify(() -> GXEventPublisherUtils.publishEventAfterCommit(any()), Mockito.never());
        }
    }

    private GXMyBatisListenerEventPublisher publisher(MockEnvironment environment) {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        return new GXMyBatisListenerEventPublisher(resolver, properties, environment);
    }

    @GXMyBatisListener(listenerClazz = SwitchListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface SwitchMapper {
        Object insert(Object entity);
    }

    private static class SwitchMapperImpl implements SwitchMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    @GXMyBatisListener(listenerClazz = SwitchListener.class, runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
    private interface AsyncSwitchMapper {
        Object insert(Object entity);
    }

    private static class AsyncSwitchMapperImpl implements AsyncSwitchMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class SwitchListener implements GXMybatisListenerService<Object> {
    }
}
