package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelSaveEntityEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXMyBatisListenerConcurrencyTest {
    private static final int CONCURRENCY = 200;

    @Test
    void concurrentWritesDeliverEachPayloadExactlyOnce() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        GXMyBatisListenerEventPublisher publisher =
                new GXMyBatisListenerEventPublisher(resolver, properties, new StandardEnvironment());
        Method method = DeltaMapper.class.getMethod("insert", Object.class);
        CollectingListener collector = new CollectingListener();
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
                int id = index;
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        publisher.publish(new DeltaMapperImpl(), method,
                                GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                                Dict.create().set("id", id));
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
            pool.shutdownNow();

            assertNull(failure.get(), () -> "Concurrent delivery failed: " + failure.get());
            assertEquals(CONCURRENCY, collector.deliveredIds().size());
        } finally {
            GXApplicationContextSingleton.INSTANCE.clearApplicationContext();
        }
    }

    static class CollectingListener implements ApplicationListener<ApplicationEvent> {
        private final Set<Integer> deliveredIds = ConcurrentHashMap.newKeySet();

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            if (event instanceof GXMyBatisModelSaveEntityEvent<?> saveEvent
                    && saveEvent.getSource() instanceof Dict source) {
                deliveredIds.add((Integer) source.get("id"));
            }
        }

        Set<Integer> deliveredIds() {
            return deliveredIds;
        }
    }

    @GXMyBatisListener(listenerClazz = DeltaListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface DeltaMapper {
        Object insert(Object entity);
    }

    private static class DeltaMapperImpl implements DeltaMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class DeltaListener implements GXMybatisListenerService<Object> {
    }
}
