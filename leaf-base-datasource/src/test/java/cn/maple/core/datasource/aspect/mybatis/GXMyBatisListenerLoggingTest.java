package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.lang.Dict;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerMetrics;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerTaskContext;
import cn.maple.core.datasource.config.GXLogCapture;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Asserts the structured log contract required by US4/AC1: delivery failures, degrade events and slow
 * callbacks must all carry {@code operationId}, {@code eventType}, {@code mode} and a reason.
 */
class GXMyBatisListenerLoggingTest {
    private final List<String> messages = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;
    private Logger publisherLogger;
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        appender = new AbstractAppender("listener-log-capture", null,
                PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        publisherLogger = (Logger) LogManager.getLogger(GXMyBatisListenerEventPublisher.class);
        previousLevel = publisherLogger.getLevel();
        Configurator.setLevel(GXMyBatisListenerEventPublisher.class.getName(), Level.WARN);
        publisherLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        publisherLogger.removeAppender(appender);
        // A null level restores inheritance, so the test never leaves a pinned level behind.
        Configurator.setLevel(GXMyBatisListenerEventPublisher.class.getName(), previousLevel);
        appender.stop();
        messages.clear();
    }

    @Test
    void deliveryFailureLogCarriesOperationIdTypeModeAndReason() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = LogMapper.class.getMethod("insert", Object.class);

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenThrow(new IllegalStateException("listener boom"));
            publisher.publish(new LogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
        }

        String failureLog = firstMessageContaining("delivery failed");
        assertTrue(failureLog.contains("operationId="), failureLog);
        assertTrue(failureLog.contains("eventType=sync_save_entity"), failureLog);
        assertTrue(failureLog.contains("mode=sync"), failureLog);
        assertTrue(failureLog.contains("reason=listener boom"), failureLog);
    }

    @Test
    void degradeLogCarriesOperationIdTypeModeAndReasons() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = LogMapper.class.getMethod("insert", Object.class);
        Dict source = Dict.create()
                .set("id", 1)
                .set("conditionLossless", Boolean.FALSE)
                .set("degradeReasons", List.of(GXMyBatisListenerDegradeReasonEnums.OR_EXPRESSION));

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.publish(new LogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY, source);
        }

        String degradeLog = firstMessageContaining("payload degraded");
        assertTrue(degradeLog.contains("operationId="), degradeLog);
        assertTrue(degradeLog.contains("eventType=sync_save_entity"), degradeLog);
        assertTrue(degradeLog.contains("mode=sync"), degradeLog);
        assertTrue(degradeLog.contains("reasons="), degradeLog);
        assertTrue(degradeLog.contains("OR_EXPRESSION"), degradeLog);
    }

    @Test
    void slowCallbackLogCarriesOperationIdTypeModeAndReason() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setSlowCallbackThresholdMs(1L);
        GXMyBatisListenerEventPublisher publisher = publisher(properties);
        Method method = LogMapper.class.getMethod("insert", Object.class);

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                Thread.sleep(10L);
                return null;
            });
            publisher.publish(new LogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
        }

        String slowLog = firstMessageContaining("Slow MyBatis listener callback");
        assertTrue(slowLog.contains("operationId="), slowLog);
        assertTrue(slowLog.contains("eventType=sync_save_entity"), slowLog);
        assertTrue(slowLog.contains("mode=sync"), slowLog);
        assertTrue(slowLog.contains("reason=slow-callback"), slowLog);
    }

    @Test
    void asyncDeliveryDoesNotEmitSlowCallbackWarning() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setSlowCallbackThresholdMs(1L);
        GXMyBatisListenerEventPublisher publisher = publisher(properties);
        Method method = AsyncLogMapper.class.getMethod("insert", Object.class);

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any())).thenAnswer(invocation -> {
                Thread.sleep(10L);
                return null;
            });
            publisher.publish(new AsyncLogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
        }

        assertTrue(messages.stream().noneMatch(message -> message.contains("Slow MyBatis listener callback")),
                "Async submissions must not emit the synchronous slow-callback warning: " + messages);
    }

    @Test
    void deliveryFailureIsCountedAndLoggedWhenPropagating() throws Exception {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        properties.setPropagateException(true);
        GXMyBatisListenerEventPublisher publisher = publisher(properties);
        Method method = LogMapper.class.getMethod("insert", Object.class);
        long failedBefore = GXMyBatisListenerMetrics.snapshot().failed();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any()))
                    .thenThrow(new IllegalStateException("delivery boom"));
            assertThrows(IllegalStateException.class, () -> publisher.publish(new LogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1)));
        }

        assertEquals(failedBefore + 1, GXMyBatisListenerMetrics.snapshot().failed(),
                "A propagating delivery failure must still be counted exactly once");
        String failureLog = firstMessageContaining("delivery failed");
        assertTrue(failureLog.contains("reason=delivery boom"), failureLog);
    }

    @Test
    void skippedEventsAreCountedEvenWhenDebugLogIsDisabled() throws Exception {
        Configurator.setLevel(GXMyBatisListenerEventPublisher.class.getName(), Level.INFO);
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = NoListenerMapper.class.getMethod("insert", Object.class);
        long skippedBefore = GXMyBatisListenerMetrics.snapshot().skipped();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            publisher.publish(new NoListenerMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
        }

        assertEquals(skippedBefore + 1, GXMyBatisListenerMetrics.snapshot().skipped(),
                "Skipped events must be counted even when debug logging is disabled");
    }

    @Test
    void asyncDeliveryLabelsTaskContextWithOperationId() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = AsyncLogMapper.class.getMethod("insert", Object.class);
        List<String> captured = new CopyOnWriteArrayList<>();

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any())).thenAnswer(invocation -> {
                String operationId = GXMyBatisListenerTaskContext.currentOperationId();
                captured.add(operationId == null ? "null" : operationId);
                return null;
            });
            publisher.publish(new AsyncLogMapperImpl(), method,
                    GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                    Dict.create().set("id", 1));
        }

        assertEquals(1, captured.size());
        assertTrue(captured.getFirst().startsWith("insert#"), captured.getFirst());
        assertNull(GXMyBatisListenerTaskContext.currentOperationId(),
                "Operation id must be cleared after the immediate async submission");
    }

    @Test
    void nestedWriteRestoresTheEnclosingOperationId() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = AsyncLogMapper.class.getMethod("insert", Object.class);
        List<String> captured = new CopyOnWriteArrayList<>();
        GXMyBatisListenerTaskContext.setOperationId("outer-write#1");
        try {
            try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
                eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any())).thenAnswer(invocation -> {
                    captured.add(GXMyBatisListenerTaskContext.currentOperationId());
                    return null;
                });
                publisher.publish(new AsyncLogMapperImpl(), method,
                        GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                        Dict.create().set("id", 1));
            }

            assertEquals(1, captured.size());
            assertTrue(captured.getFirst().startsWith("insert#"));
            assertEquals("outer-write#1", GXMyBatisListenerTaskContext.currentOperationId(),
                    "A write issued from a listener callback must restore the enclosing operation id");
        } finally {
            GXMyBatisListenerTaskContext.clear();
        }
    }

    @Test
    void asyncDeliveryKeepsOperationIdDuringOpenTransaction() throws Exception {
        // Covers submit-time visibility only; after-completion visibility is covered by
        // transactionTemplateAfterCommitDeliveryCarriesOperationIdUntilCompletion below.
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = AsyncLogMapper.class.getMethod("insert", Object.class);
        List<String> captured = new CopyOnWriteArrayList<>();
        TransactionSynchronizationManager.initSynchronization();
        try {
            try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
                eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any())).thenAnswer(invocation -> {
                    captured.add(GXMyBatisListenerTaskContext.currentOperationId());
                    return null;
                });
                publisher.publish(new AsyncLogMapperImpl(), method,
                        GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                        Dict.create().set("id", 1));
            }

            assertEquals(1, captured.size());
            assertNotNull(captured.getFirst());
            assertTrue(captured.getFirst().startsWith("insert#"), captured.getFirst());
            assertNotNull(GXMyBatisListenerTaskContext.currentOperationId(),
                    "Operation id must stay available for the after-commit submission");

            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

            assertNull(GXMyBatisListenerTaskContext.currentOperationId(),
                    "Operation id must be cleared when the transaction completes");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            GXMyBatisListenerTaskContext.clear();
        }
    }

    @Test
    void transactionTemplateAfterCommitDeliveryCarriesOperationIdUntilCompletion() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = AsyncLogMapper.class.getMethod("insert", Object.class);
        List<String> deliveredAtAfterCommit = new CopyOnWriteArrayList<>();
        TransactionTemplate template = new TransactionTemplate(new StubTransactionManager());

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any())).thenAnswer(invocation -> {
                // Mirrors the real utility: the delivery happens at real after-commit time.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_COMMITTED) {
                            String operationId = GXMyBatisListenerTaskContext.currentOperationId();
                            deliveredAtAfterCommit.add(operationId == null ? "null" : operationId);
                        }
                    }
                });
                return null;
            });

            template.execute(status -> {
                publisher.publish(new AsyncLogMapperImpl(), method,
                        GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                        Dict.create().set("id", 1));
                assertNotNull(GXMyBatisListenerTaskContext.currentOperationId(),
                        "Operation id must stay available while the transaction is open");
                return null;
            });
        }

        assertEquals(1, deliveredAtAfterCommit.size());
        assertTrue(deliveredAtAfterCommit.getFirst().startsWith("insert#"), deliveredAtAfterCommit.getFirst());
        assertNull(GXMyBatisListenerTaskContext.currentOperationId(),
                "Operation id must be cleared once the transaction completed");
    }

    @Test
    void everyAsyncListenerOfOneWriteKeepsItsOperationIdUntilAfterCommit() throws Exception {
        GXMyBatisListenerEventPublisher publisher = publisher(new GXMyBatisListenerProperties());
        Method method = DualAsyncLogMapperOne.class.getMethod("insert", Object.class);
        List<String> deliveredAtAfterCommit = new CopyOnWriteArrayList<>();
        TransactionTemplate template = new TransactionTemplate(new StubTransactionManager());

        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class)) {
            eventPublisher.when(() -> GXEventPublisherUtils.publishEventAfterCommit(any())).thenAnswer(invocation -> {
                // Mirrors the real utility: every listener's delivery happens at after-commit time.
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_COMMITTED) {
                            String operationId = GXMyBatisListenerTaskContext.currentOperationId();
                            deliveredAtAfterCommit.add(operationId == null ? "null" : operationId);
                        }
                    }
                });
                return null;
            });

            template.execute(status -> {
                publisher.publish(new DualAsyncLogMapperImpl(), method,
                        GXModelEventNamingEnums.SYNC_SAVE_ENTITY, GXModelEventNamingEnums.ASYNC_SAVE_ENTITY,
                        Dict.create().set("id", 1));
                return null;
            });
        }

        assertEquals(2, deliveredAtAfterCommit.size());
        assertTrue(deliveredAtAfterCommit.stream().allMatch(operationId -> operationId.startsWith("insert#")),
                () -> "Every after-commit delivery must see the operation id: " + deliveredAtAfterCommit);
        assertNull(GXMyBatisListenerTaskContext.currentOperationId(),
                "Operation id must be cleared once the transaction completed");
    }

    @Test
    void callbackFailureLogCarriesModeAndOperationId() throws Throwable {
        GXMyBatisListenerProperties properties = new GXMyBatisListenerProperties();
        GXMyBatisPlusSaveEntityAspect aspect = new GXMyBatisPlusSaveEntityAspect(publisher(properties));
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getTarget()).thenReturn(new FailingLogMapperImpl());
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod())
                .thenReturn(FailingLogMapper.class.getMethod("insert", Object.class));
        Mockito.when(point.getArgs()).thenReturn(new Object[]{Map.of("id", 9)});
        Mockito.when(point.proceed()).thenReturn(1);

        GXLogCapture capture = GXLogCapture.attach("cn.maple.core.datasource.listener.GXMyBatisBaseListener");
        try (MockedStatic<GXEventPublisherUtils> eventPublisher = Mockito.mockStatic(GXEventPublisherUtils.class);
             MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(anyString(), Mockito.any(Class.class)))
                    .thenReturn(new FailingLogListener());
            GXMyBatisSyncListener syncListener = new GXMyBatisSyncListener();
            eventPublisher.when(() -> GXEventPublisherUtils.publishEvent(any())).thenAnswer(invocation -> {
                syncListener.listenerSaveEntity(invocation.getArgument(0));
                return null;
            });

            assertEquals(1, assertDoesNotThrow(() -> aspect.around(point)));
        } finally {
            capture.detach();
        }

        // US4/AC1: the callback failure log must identify the write it belongs to and its mode, so
        // executor-thread failures stay correlatable with the originating write.
        assertTrue(capture.hasEvent(Level.ERROR, "mode=sync"),
                "Callback failure log must carry the delivery mode");
        assertTrue(capture.hasEvent(Level.ERROR, "operationId=insert#"),
                "Callback failure log must carry the operation id");
    }

    private String firstMessageContaining(String token) {
        return messages.stream()
                .filter(message -> message.contains(token))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log message containing '" + token + "': " + messages));
    }

    private GXMyBatisListenerEventPublisher publisher(GXMyBatisListenerProperties properties) {
        GXMyBatisListenerConfigResolver resolver = new GXMyBatisListenerConfigResolver(properties);
        return new GXMyBatisListenerEventPublisher(resolver, properties, new StandardEnvironment());
    }

    @GXMyBatisListener(listenerClazz = LogListener.class, runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface LogMapper {
        Object insert(Object entity);
    }

    private static class LogMapperImpl implements LogMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class LogListener implements GXMybatisListenerService<Object> {
    }

    @GXMyBatisListener(listenerClazz = FailingLogListener.class,
            runType = GXMyBatisEventConstant.MYBATIS_SYNC_EVENT)
    private interface FailingLogMapper {
        Object insert(Object entity);
    }

    private static class FailingLogMapperImpl implements FailingLogMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class FailingLogListener implements GXMybatisListenerService<Object> {
        @Override
        public void saveEntityListener(Object entity) {
            throw new IllegalStateException("log boom");
        }
    }

    @GXMyBatisListener(listenerClazz = LogListener.class, runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
    private interface AsyncLogMapper {
        Object insert(Object entity);
    }

    private static class AsyncLogMapperImpl implements AsyncLogMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private interface NoListenerMapper {
        Object insert(Object entity);
    }

    @GXMyBatisListener(listenerClazz = FirstAsyncLogListener.class,
            runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
    private interface DualAsyncLogMapperOne {
        Object insert(Object entity);
    }

    @GXMyBatisListener(listenerClazz = SecondAsyncLogListener.class,
            runType = GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)
    private interface DualAsyncLogMapperTwo {
        Object insert(Object entity);
    }

    private static class DualAsyncLogMapperImpl implements DualAsyncLogMapperOne, DualAsyncLogMapperTwo {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    private static class FirstAsyncLogListener implements GXMybatisListenerService<Object> {
    }

    private static class SecondAsyncLogListener implements GXMybatisListenerService<Object> {
    }

    private static class NoListenerMapperImpl implements NoListenerMapper {
        @Override
        public Object insert(Object entity) {
            return entity;
        }
    }

    /**
     * Minimal transaction manager so {@link TransactionTemplate} drives a real synchronization
     * lifecycle (init, afterCommit, afterCompletion, cleanup) without a database.
     */
    private static final class StubTransactionManager extends AbstractPlatformTransactionManager {
        private static final long serialVersionUID = 1L;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
