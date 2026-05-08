package cn.maple.debezium.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDebeziumEngineConfigTest {
    @Test
    void validateConfigurationRequiresTopicPrefix() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        Map<String, String> debeziumConfig = validConfig();
        debeziumConfig.remove("topic.prefix");

        assertFalse(invokeValidateConfiguration(config, debeziumConfig));
    }

    @Test
    void validateConfigurationAcceptsRequiredMysqlConfig() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();

        assertTrue(invokeValidateConfiguration(config, validConfig()));
    }

    @Test
    void getExecutorServiceRejectsCreationAfterDestroy() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        config.destroy();

        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod("getExecutorService");
        method.setAccessible(true);
        InvocationTargetException exception = org.junit.jupiter.api.Assertions.assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(config));

        assertInstanceOf(RejectedExecutionException.class, exception.getCause());
    }

    private boolean invokeValidateConfiguration(GXDebeziumEngineConfig config, Map<String, String> debeziumConfig) throws Exception {
        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod("validateConfiguration", Map.class);
        method.setAccessible(true);
        return (boolean) method.invoke(config, debeziumConfig);
    }

    private Map<String, String> validConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("name", "test-connector");
        config.put("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        config.put("database.hostname", "localhost");
        config.put("database.port", "3306");
        config.put("database.user", "debezium");
        config.put("database.password", "password");
        config.put("database.server.id", "184054");
        config.put("topic.prefix", "test-topic");
        return config;
    }
}
