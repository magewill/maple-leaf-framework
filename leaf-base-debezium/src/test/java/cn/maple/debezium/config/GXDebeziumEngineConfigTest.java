package cn.maple.debezium.config;

import cn.maple.debezium.services.GXDebeziumService;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(config));

        assertInstanceOf(RejectedExecutionException.class, exception.getCause());
    }

    @Test
    void handleChangeEventProcessesPayloadBeforeReturning() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        AtomicBoolean processed = new AtomicBoolean(false);
        GXDebeziumService service = data -> processed.set(true);

        invokeHandleChangeEvent(config, service, "{\"payload\":{\"op\":\"c\"}}");

        assertTrue(processed.get());
    }

    @Test
    void handleChangeEventSkipsProcessingFailureWithoutPropagating() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        GXDebeziumService service = data -> {
            throw new IllegalStateException("processing failed");
        };

        assertDoesNotThrow(() -> invokeHandleChangeEvent(config, service, "{\"payload\":{\"op\":\"c\"}}"));
    }

    @Test
    void handleChangeEventSkipsDuplicateEventByFingerprint() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        AtomicInteger processedCount = new AtomicInteger(0);
        GXDebeziumService service = data -> processedCount.incrementAndGet();
        String value = "{\"payload\":{\"op\":\"u\",\"ts_ms\":123,\"source\":{\"file\":\"mysql-bin.000001\",\"pos\":100,\"row\":1}}}";

        invokeHandleChangeEvent(config, service, value);
        invokeHandleChangeEvent(config, service, value);

        assertEquals(1, processedCount.get());
    }

    @Test
    void handleChangeEventFailsWhenEngineStopWasRequested() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        getAtomicBoolean(config, "engineStopRequested").set(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeHandleChangeEvent(config, data -> {
                }, "{\"payload\":{\"op\":\"c\"}}"));

        assertInstanceOf(RejectedExecutionException.class, exception.getCause());
    }

    @Test
    void onLockRenewalTickStopsEngineAfterFailureThreshold() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        GXDebeziumEngineLockConfig engineLockConfig = mock(GXDebeziumEngineLockConfig.class);
        DebeziumEngine<ChangeEvent<String, String>> engine = mock(DebeziumEngine.class);

        when(engineLockConfig.renew("lock-key")).thenThrow(new IllegalStateException("redis timeout"));
        getAtomicBoolean(config, "engineLockAcquired").set(true);
        getAtomicReference(config, "debeziumEngineRef").set(engine);

        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod(
                "onLockRenewalTick", GXDebeziumEngineLockConfig.class, String.class);
        method.setAccessible(true);

        method.invoke(config, engineLockConfig, "lock-key");
        assertFalse(getAtomicBoolean(config, "engineStopRequested").get());

        method.invoke(config, engineLockConfig, "lock-key");
        assertFalse(getAtomicBoolean(config, "engineStopRequested").get());

        method.invoke(config, engineLockConfig, "lock-key");
        assertTrue(getAtomicBoolean(config, "engineStopRequested").get());
        verify(engine).close();
    }

    @Test
    void onLockRenewalTickDoesNothingAfterStopWasRequested() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        GXDebeziumEngineLockConfig engineLockConfig = mock(GXDebeziumEngineLockConfig.class);
        DebeziumEngine<ChangeEvent<String, String>> engine = mock(DebeziumEngine.class);

        getAtomicBoolean(config, "engineLockAcquired").set(true);
        getAtomicBoolean(config, "engineStopRequested").set(true);
        getAtomicReference(config, "debeziumEngineRef").set(engine);
        getAtomicInteger(config, "renewalFailureCount").set(2);

        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod(
                "onLockRenewalTick", GXDebeziumEngineLockConfig.class, String.class);
        method.setAccessible(true);
        method.invoke(config, engineLockConfig, "lock-key");

        assertEquals(2, getAtomicInteger(config, "renewalFailureCount").get());
    }

    @Test
    void releaseEngineLockResetsRenewalState() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        GXDebeziumEngineLockConfig engineLockConfig = mock(GXDebeziumEngineLockConfig.class);

        getAtomicBoolean(config, "engineLockAcquired").set(true);
        getAtomicBoolean(config, "engineStopRequested").set(true);
        getAtomicInteger(config, "renewalFailureCount").set(2);
        getAtomicLong(config, "lastLockRenewalSuccessTimeMillis").set(System.currentTimeMillis());

        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod(
                "releaseEngineLock", GXDebeziumEngineLockConfig.class, String.class);
        method.setAccessible(true);
        method.invoke(config, engineLockConfig, "lock-key");

        assertFalse(getAtomicBoolean(config, "engineLockAcquired").get());
        assertFalse(getAtomicBoolean(config, "engineStopRequested").get());
        assertEquals(0, getAtomicInteger(config, "renewalFailureCount").get());
        assertEquals(0L, getAtomicLong(config, "lastLockRenewalSuccessTimeMillis").get());
        verify(engineLockConfig).unlock("lock-key");
    }


    private boolean invokeValidateConfiguration(GXDebeziumEngineConfig config, Map<String, String> debeziumConfig) throws Exception {
        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod("validateConfiguration", Map.class);
        method.setAccessible(true);
        return (boolean) method.invoke(config, debeziumConfig);
    }

    private void invokeHandleChangeEvent(GXDebeziumEngineConfig config, GXDebeziumService service, String value) throws Exception {
        ChangeEvent<String, String> record = mock(ChangeEvent.class);
        when(record.value()).thenReturn(value);

        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod(
                "handleChangeEvent", ChangeEvent.class, GXDebeziumService.class);
        method.setAccessible(true);
        method.invoke(config, record, service);
    }

    private AtomicBoolean getAtomicBoolean(GXDebeziumEngineConfig config, String fieldName) throws Exception {
        Field field = GXDebeziumEngineConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicBoolean) field.get(config);
    }

    private AtomicInteger getAtomicInteger(GXDebeziumEngineConfig config, String fieldName) throws Exception {
        Field field = GXDebeziumEngineConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicInteger) field.get(config);
    }

    private AtomicLong getAtomicLong(GXDebeziumEngineConfig config, String fieldName) throws Exception {
        Field field = GXDebeziumEngineConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicLong) field.get(config);
    }

    @SuppressWarnings("unchecked")
    private AtomicReference<DebeziumEngine<ChangeEvent<String, String>>> getAtomicReference(
            GXDebeziumEngineConfig config, String fieldName) throws Exception {
        Field field = GXDebeziumEngineConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AtomicReference<DebeziumEngine<ChangeEvent<String, String>>>) field.get(config);
    }

    private long getStaticLong(String fieldName) throws Exception {
        Field field = GXDebeziumEngineConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(null);
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
