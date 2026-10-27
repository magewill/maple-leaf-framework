package cn.maple.canal.integration;

import cn.maple.canal.service.GXProcessCanalDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline smoke coverage for the two environment-gated broker tests.
 *
 * <p>Those classes are skipped without a broker, so a broken configuration would only surface in an
 * environment that has one. This test validates their structure instead: the guard annotation is
 * present and every bean the scenario depends on still exists with the expected type.</p>
 */
class GXCanalRealBrokerBootstrapTest {
    @Test
    void shouldKeepRabbitBrokerConfigComplete() {
        Class<?> configType = GXCanalRabbitRealBrokerTest.BrokerConfig.class;

        assertConfiguration(configType);
        assertTrue(configType.isAnnotationPresent(EnableRabbit.class),
                "the RabbitMQ scenario needs @EnableRabbit to register the listener endpoint");
        assertGuardPresent(GXCanalRabbitRealBrokerTest.class, "RABBITMQ_HOST");

        Map<String, Class<?>> expectedBeans = new LinkedHashMap<>();
        expectedBeans.put("connectionFactory", ConnectionFactory.class);
        expectedBeans.put("rabbitTemplate", RabbitTemplate.class);
        expectedBeans.put("rabbitAdmin", RabbitAdmin.class);
        expectedBeans.put("rabbitListenerContainerFactory", SimpleRabbitListenerContainerFactory.class);
        expectedBeans.put("demoSysUserService", GXProcessCanalDataService.class);
        assertBeanMethods(configType, expectedBeans);
    }

    @Test
    void shouldKeepRocketBrokerConfigComplete() {
        Class<?> configType = GXCanalRocketRealBrokerTest.BrokerConfig.class;

        assertConfiguration(configType);
        assertGuardPresent(GXCanalRocketRealBrokerTest.class, "ROCKETMQ_NAME_SERVER");

        Map<String, Class<?>> expectedBeans = new LinkedHashMap<>();
        expectedBeans.put("demoSysUserService", GXProcessCanalDataService.class);
        assertBeanMethods(configType, expectedBeans);
    }

    private void assertConfiguration(Class<?> configType) {
        assertTrue(configType.isAnnotationPresent(Configuration.class),
                configType.getName() + " must stay a @Configuration");
    }

    private void assertGuardPresent(Class<?> testType, String variable) {
        EnabledIfEnvironmentVariable guard = testType.getAnnotation(EnabledIfEnvironmentVariable.class);
        assertNotNull(guard, testType.getSimpleName() + " must stay gated by an environment variable");
        assertTrue(variable.equals(guard.named()), testType.getSimpleName() + " must be gated by " + variable);
    }

    private void assertBeanMethods(Class<?> configType, Map<String, Class<?>> expectedBeans) {
        for (Map.Entry<String, Class<?>> entry : expectedBeans.entrySet()) {
            Method method = beanMethod(configType, entry.getKey(), entry.getValue());
            assertNotNull(method, configType.getSimpleName() + " must keep the bean method " + entry.getKey());
        }
    }

    private Method beanMethod(Class<?> configType, String methodName, Class<?> expectedType) {
        for (Method method : configType.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || !method.isAnnotationPresent(Bean.class)) {
                continue;
            }
            if (expectedType.isAssignableFrom(method.getReturnType())) {
                return method;
            }
        }
        return null;
    }
}
