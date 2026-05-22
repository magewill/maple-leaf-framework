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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    void handleChangeEventPropagatesProcessingFailure() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        GXDebeziumService service = data -> {
            throw new IllegalStateException("processing failed");
        };

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeHandleChangeEvent(config, service, "{\"payload\":{\"op\":\"c\"}}"));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void handleChangeEventFailsWhenEngineStopWasRequested() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        getAtomicBoolean(config, "engineStopRequested").set(true);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeHandleChangeEvent(config, data -> {
                }, "{\"payload\":{\"op\":\"c\"}}"));

        assertInstanceOf(IllegalStateException.class, exception.getCause());
    }

    @Test
    void lockRenewalWatchdogClosesEngineWhenLastRenewalIsStale() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        DebeziumEngine<ChangeEvent<String, String>> engine = mock(DebeziumEngine.class);

        getAtomicBoolean(config, "engineLockAcquired").set(true);
        getAtomicReference(config, "debeziumEngineRef").set(engine);
        getAtomicLong(config, "lastLockRenewalSuccessTimeMillis")
                .set(System.currentTimeMillis() - getStaticLong("LOCK_RENEW_STALE_TIMEOUT_MILLIS") - 1L);

        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod("checkLockRenewalFreshness");
        method.setAccessible(true);
        method.invoke(config);

        verify(engine).close();
    }

    @Test
    void releaseEngineLockDoesNotRestoreAcquiredStateWhenRedisReleaseFails() throws Exception {
        GXDebeziumEngineConfig config = new GXDebeziumEngineConfig();
        GXDebeziumEngineLockConfig engineLockConfig = mock(GXDebeziumEngineLockConfig.class);
        doThrow(new IllegalStateException("redis unavailable")).when(engineLockConfig).unlock("lock-key");

        AtomicBoolean engineLockAcquired = getAtomicBoolean(config, "engineLockAcquired");
        engineLockAcquired.set(true);

        Method method = GXDebeziumEngineConfig.class.getDeclaredMethod(
                "releaseEngineLock", GXDebeziumEngineLockConfig.class, String.class);
        method.setAccessible(true);
        method.invoke(config, engineLockConfig, "lock-key");

        assertFalse(engineLockAcquired.get());
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
