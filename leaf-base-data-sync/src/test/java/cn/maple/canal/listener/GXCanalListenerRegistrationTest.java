package cn.maple.canal.listener;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.GXCanalMessageParseService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the listener registration behaviour that the {@code @Lazy} annotation on a listener bean breaks.
 *
 * <p>No broker is required: the container factory is created with {@code autoStartup=false}, so
 * containers are registered but never open a connection.</p>
 */
class GXCanalListenerRegistrationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RabbitInfrastructure.class, CanalBeansStub.class);

    @Test
    void shouldRegisterEagerListener() {
        contextRunner.withUserConfiguration(EagerListenerConfig.class).run(context -> {
            RabbitListenerEndpointRegistry registry = context.getBean(RabbitListenerEndpointRegistry.class);

            assertFalse(registry.getListenerContainers().isEmpty(),
                    "an eager listener bean must be registered");
        });
    }

    @Test
    void shouldNotRegisterLazyListener() {
        contextRunner.withUserConfiguration(LazyListenerConfig.class).run(context -> {
            RabbitListenerEndpointRegistry registry = context.getBean(RabbitListenerEndpointRegistry.class);

            assertTrue(registry.getListenerContainers().isEmpty(),
                    "a lazy listener bean is not instantiated, so its endpoint is never registered");
        });
    }

    @Test
    void shouldNotMarkProductionListenerAsLazy() {
        assertFalse(GXCanalRabbitMQListener.class.isAnnotationPresent(Lazy.class),
                "GXCanalRabbitMQListener must stay eager, otherwise the Canal endpoint may never register");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRabbit
    static class RabbitInfrastructure {
        @Bean
        ConnectionFactory connectionFactory() {
            return new CachingConnectionFactory("localhost", 5672);
        }

        @Bean
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
            SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
            factory.setConnectionFactory(connectionFactory);
            factory.setAutoStartup(false);
            return factory;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CanalBeansStub {
        @Bean
        GXCanalProperties canalProperties() {
            return new GXCanalProperties();
        }

        @Bean
        GXCanalMessageParseService canalMessageParseService() {
            return new GXCanalMessageParseService() {
                @Override
                public java.util.List<cn.maple.canal.dto.inner.GXCanalParseResultInnerDto> parse(String rawPayload) {
                    return java.util.List.of();
                }

                @Override
                public Dict parseMessage(String message) {
                    return Dict.create();
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EagerListenerConfig {
        @Bean
        EagerListener eagerListener() {
            return new EagerListener();
        }
    }

    static class EagerListener {
        @RabbitListener(queues = "canal.registration.eager")
        public void onMessage(String message) {
            // Registration probe only.
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LazyListenerConfig {
        @Bean
        @Lazy
        LazyListener lazyListener() {
            return new LazyListener();
        }
    }

    static class LazyListener {
        @RabbitListener(queues = "canal.registration.lazy")
        public void onMessage(String message) {
            // Documents the pitfall: a lazy listener never registers.
        }
    }
}
