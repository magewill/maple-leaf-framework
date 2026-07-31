package cn.maple.core.datasource.aspect;

import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.config.GXDynamicDataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import cn.maple.core.datasource.context.GXSeataRootContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class GXDataSourceAspectTest {
    private final GXDataSourceAspect aspect = new GXDataSourceAspect();

    @AfterEach
    void clearContext() {
        GXDynamicContextHolder.clear();
    }

    @Test
    void switchesDatasourceFromInterfaceMethodAnnotationAndRestoresPreviousContext() throws Throwable {
        GXDynamicContextHolder.push("outer");
        InterfaceAnnotatedService target = new InterfaceAnnotatedServiceImpl();
        Method method = InterfaceAnnotatedService.class.getMethod("query");
        ProceedingJoinPoint point = mockJoinPoint(target, method);

        Mockito.when(point.proceed()).thenAnswer(invocation -> {
            assertEquals("interface_ds", GXDynamicContextHolder.peek());
            return "ok";
        });

        Object result = aspect.around(point);

        assertEquals("ok", result);
        assertEquals("outer", GXDynamicContextHolder.peek());
    }

    @Test
    void switchesDatasourceFromClassAnnotationAndClearsContextAfterExecution() throws Throwable {
        ClassAnnotatedService target = new ClassAnnotatedService();
        Method method = ClassAnnotatedService.class.getMethod("query");
        ProceedingJoinPoint point = mockJoinPoint(target, method);

        Mockito.when(point.proceed()).thenAnswer(invocation -> {
            assertEquals("class_ds", GXDynamicContextHolder.peek());
            return "ok";
        });

        Object result = aspect.around(point);

        assertEquals("ok", result);
        assertNull(GXDynamicContextHolder.peek());
    }

    @Test
    void nestedDifferentDatasourceUsesIndependentTransactionAndRestoresOuterDatasource() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TransactionFixture fixture = context.getBean(TransactionFixture.class);

            assertThrows(OuterTransactionFailure.class, outerService::run);

            assertEquals(List.of("ds1", "ds2", "ds1"), fixture.usedDataSources());
            assertTrue(fixture.ds1Connection().rolledBack());
            assertTrue(fixture.ds2Connection().committed());
            assertTrue(fixture.ds2Connection().readOnlyRequested());
        }
    }

    @Test
    void nestedDatasourceHonorsNoRollbackForAndRestoresOuterDatasource() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TransactionFixture fixture = context.getBean(TransactionFixture.class);

            outerService.runAfterInnerNoRollbackFailure();

            assertEquals(List.of("ds1", "ds2", "ds1"), fixture.usedDataSources());
            assertTrue(fixture.ds1Connection().committed());
            assertTrue(fixture.ds2Connection().committed());
            assertFalse(fixture.ds2Connection().rolledBack());
        }
    }

    @Test
    void defaultDatasourceDoesNotCreateIndependentTransaction() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TransactionFixture fixture = context.getBean(TransactionFixture.class);

            assertThrows(OuterTransactionFailure.class, outerService::runOnDefaultDatasource);

            assertEquals(List.of("ds1", "ds1", "ds1"), fixture.usedDataSources());
            assertTrue(fixture.ds1Connection().rolledBack());
            assertFalse(fixture.ds1Connection().committed());
        }
    }

    @Test
    void nestedDatasourceUsesTransactionManagerQualifiedOnInnerMethod() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TrackingTransactionManager defaultManager = context.getBean("transactionManager", TrackingTransactionManager.class);
            TrackingTransactionManager qualifiedManager = context.getBean("qualifiedTxManager", TrackingTransactionManager.class);

            outerService.runUsingQualifiedTransactionManager();

            assertEquals(1, defaultManager.commitCount());
            assertEquals(1, qualifiedManager.commitCount());
        }
    }

    @Test
    void nestedDatasourceUsesConfigurerSelectedDefaultTransactionManager() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ConfigurerTransactionTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TrackingTransactionManager primaryManager = context.getBean("primaryTxManager", TrackingTransactionManager.class);
            TrackingTransactionManager configuredManager = context.getBean("configuredTxManager", TrackingTransactionManager.class);

            assertThrows(OuterTransactionFailure.class, outerService::run);

            assertEquals(0, primaryManager.commitCount());
            assertEquals(0, primaryManager.rollbackCount());
            assertEquals(1, configuredManager.commitCount());
            assertEquals(1, configuredManager.rollbackCount());
        }
    }

    @Test
    void nestedDatasourceUsesClassQualifiedTransactionManager() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TrackingTransactionManager defaultManager = context.getBean("transactionManager", TrackingTransactionManager.class);
            TrackingTransactionManager qualifiedManager = context.getBean("qualifiedTxManager", TrackingTransactionManager.class);

            outerService.runUsingClassQualifiedTransactionManager();

            assertEquals(1, defaultManager.commitCount());
            assertEquals(1, qualifiedManager.commitCount());
        }
    }

    @Test
    void nestedDatasourceSuspendsSeataGlobalTransactionAndRestoresOuterContext() {
        GXSeataRootContext.bind("xid-1");
        GXSeataRootContext.bindGlobalLockFlag();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TransactionFixture fixture = context.getBean(TransactionFixture.class);

            assertThrows(OuterTransactionFailure.class, outerService::runWithSeataGlobalTransaction);

            assertEquals(Arrays.asList("xid-1", null, "xid-1"), fixture.seataXids());
            assertEquals(List.of(true, false, true), fixture.seataGlobalLockFlags());
            assertTrue(fixture.ds1Connection().rolledBack());
            assertTrue(fixture.ds2Connection().committed());
        } finally {
            GXSeataRootContext.unbind();
            GXSeataRootContext.unbindGlobalLockFlag();
        }
    }

    @Test
    void requiresNewDatasourceSuspendsSeataGlobalTransactionAndRestoresOuterContext() {
        GXSeataRootContext.bind("xid-2");
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TransactionFixture fixture = context.getBean(TransactionFixture.class);

            assertThrows(OuterTransactionFailure.class, outerService::runWithSeataGlobalTransactionAndRequiresNew);

            assertEquals(Arrays.asList("xid-2", null, "xid-2"), fixture.seataXids());
            assertTrue(fixture.ds1Connection().rolledBack());
            assertTrue(fixture.ds2Connection().committed());
        } finally {
            GXSeataRootContext.unbind();
        }
    }

    @Test
    void datasourceWithOnlySeataGlobalContextSuspendsAndRestoresIt() {
        GXSeataRootContext.bind("xid-3");
        GXSeataRootContext.bindGlobalLockFlag();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TransactionalTestConfig.class)) {
            OuterService outerService = context.getBean(OuterService.class);
            TransactionFixture fixture = context.getBean(TransactionFixture.class);

            outerService.runWithSeataGlobalContextOnly();

            assertEquals(Arrays.asList("xid-3", null, "xid-3"), fixture.seataXids());
            assertEquals(List.of(true, false, true), fixture.seataGlobalLockFlags());
            assertTrue(fixture.ds2Connection().committed());
        } finally {
            GXSeataRootContext.unbind();
            GXSeataRootContext.unbindGlobalLockFlag();
        }
    }

    private ProceedingJoinPoint mockJoinPoint(Object target, Method method) {
        ProceedingJoinPoint point = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);
        Mockito.when(point.getTarget()).thenReturn(target);
        Mockito.when(point.getSignature()).thenReturn(signature);
        Mockito.when(signature.getMethod()).thenReturn(method);
        return point;
    }

    private interface InterfaceAnnotatedService {
        @GXDataSource("interface_ds")
        String query();
    }

    private static class InterfaceAnnotatedServiceImpl implements InterfaceAnnotatedService {
        @Override
        public String query() {
            return "ok";
        }
    }

    @GXDataSource("class_ds")
    private static class ClassAnnotatedService {
        public String query() {
            return "ok";
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement
    static class TransactionalTestConfig {
        @Bean
        GXDataSourceAspect gxDataSourceAspect() {
            return new GXDataSourceAspect();
        }

        @Bean
        TransactionFixture transactionFixture() {
            return new TransactionFixture();
        }

        @Bean({"routingDataSource", "dynamicDataSource"})
        GXDynamicDataSource routingDataSource(TransactionFixture fixture) {
            GXDynamicDataSource dataSource = new GXDynamicDataSource();
            dataSource.setTargetDataSources(Map.of("ds1", fixture.ds1(), "ds2", fixture.ds2(),
                    "framework", fixture.ds1()));
            dataSource.setDefaultTargetDataSource(fixture.ds1());
            dataSource.afterPropertiesSet();
            return dataSource;
        }

        @Bean
        @Primary
        TrackingTransactionManager transactionManager(@Qualifier("routingDataSource") DataSource dataSource) {
            return new TrackingTransactionManager(dataSource);
        }

        @Bean("qualifiedTxManager")
        TrackingTransactionManager qualifiedTxManager(@Qualifier("routingDataSource") DataSource dataSource) {
            return new TrackingTransactionManager(dataSource);
        }

        @Bean
        TransactionRecorder transactionRecorder(@Qualifier("routingDataSource") DataSource dataSource,
                                                TransactionFixture fixture) {
            return new TransactionRecorder(dataSource, fixture);
        }

        @Bean
        InnerService innerService(TransactionRecorder transactionRecorder) {
            return new InnerService(transactionRecorder);
        }

        @Bean
        OuterService outerService(InnerService innerService, ClassQualifiedInnerService classQualifiedInnerService,
                                  TransactionRecorder transactionRecorder) {
            return new OuterService(innerService, classQualifiedInnerService, transactionRecorder);
        }

        @Bean
        ClassQualifiedInnerService classQualifiedInnerService(TransactionRecorder transactionRecorder) {
            return new ClassQualifiedInnerService(transactionRecorder);
        }
    }

    static class OuterService {
        private final InnerService innerService;
        private final ClassQualifiedInnerService classQualifiedInnerService;
        private final TransactionRecorder transactionRecorder;

        OuterService(InnerService innerService, ClassQualifiedInnerService classQualifiedInnerService,
                     TransactionRecorder transactionRecorder) {
            this.innerService = innerService;
            this.classQualifiedInnerService = classQualifiedInnerService;
            this.transactionRecorder = transactionRecorder;
        }

        @GXDataSource("ds1")
        @Transactional
        public void run() {
            transactionRecorder.recordCurrentDataSource();
            innerService.getList();
            transactionRecorder.recordCurrentDataSource();
            throw new OuterTransactionFailure();
        }

        @GXDataSource("ds1")
        @Transactional
        public void runAfterInnerNoRollbackFailure() {
            transactionRecorder.recordCurrentDataSource();
            try {
                innerService.getListAndFail();
            } catch (InnerTransactionFailure ignored) {
                // The inner transaction is configured not to roll back for this exception.
            }
            transactionRecorder.recordCurrentDataSource();
        }

        @Transactional
        public void runOnDefaultDatasource() {
            transactionRecorder.recordCurrentDataSource();
            innerService.getFromDefaultDatasource();
            transactionRecorder.recordCurrentDataSource();
            throw new OuterTransactionFailure();
        }

        @GXDataSource("ds1")
        @Transactional
        public void runUsingQualifiedTransactionManager() {
            innerService.getListWithQualifiedTransactionManager();
        }

        @GXDataSource("ds1")
        @Transactional
        public void runUsingClassQualifiedTransactionManager() {
            classQualifiedInnerService.getList();
        }

        @GXDataSource("ds1")
        @Transactional
        public void runWithSeataGlobalTransaction() {
            transactionRecorder.recordSeataXid();
            innerService.getListAndRecordSeataXid();
            transactionRecorder.recordSeataXid();
            throw new OuterTransactionFailure();
        }

        @GXDataSource("ds1")
        @Transactional
        public void runWithSeataGlobalTransactionAndRequiresNew() {
            transactionRecorder.recordSeataXid();
            innerService.getListInNewTransactionAndRecordSeataXid();
            transactionRecorder.recordSeataXid();
            throw new OuterTransactionFailure();
        }

        @GXDataSource("ds1")
        public void runWithSeataGlobalContextOnly() {
            transactionRecorder.recordSeataXid();
            innerService.getListAndRecordSeataXid();
            transactionRecorder.recordSeataXid();
        }
    }

    static class InnerService {
        private final TransactionRecorder transactionRecorder;

        InnerService(TransactionRecorder transactionRecorder) {
            this.transactionRecorder = transactionRecorder;
        }

        @GXDataSource("ds2")
        @Transactional(readOnly = true)
        public void getList() {
            transactionRecorder.recordCurrentDataSource();
        }

        @GXDataSource("ds2")
        @Transactional(noRollbackFor = InnerTransactionFailure.class)
        public void getListAndFail() {
            transactionRecorder.recordCurrentDataSource();
            throw new InnerTransactionFailure();
        }

        @GXDataSource("framework")
        public void getFromDefaultDatasource() {
            transactionRecorder.recordCurrentDataSource();
        }

        @GXDataSource("ds2")
        @Transactional(transactionManager = "qualifiedTxManager")
        public void getListWithQualifiedTransactionManager() {
            transactionRecorder.recordCurrentDataSource();
        }

        @GXDataSource("ds2")
        @Transactional
        public void getListAndRecordSeataXid() {
            transactionRecorder.recordCurrentDataSource();
            transactionRecorder.recordSeataXid();
        }

        @GXDataSource("ds2")
        @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
        public void getListInNewTransactionAndRecordSeataXid() {
            transactionRecorder.recordCurrentDataSource();
            transactionRecorder.recordSeataXid();
        }
    }

    private record TransactionRecorder(DataSource dataSource, TransactionFixture fixture) {

        private void recordCurrentDataSource() {
                try {
                    fixture.record(DataSourceUtils.getConnection(dataSource));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }

        private void recordSeataXid() {
            fixture.recordSeataXid(GXSeataRootContext.getXID());
            fixture.recordSeataGlobalLockFlag(GXSeataRootContext.requireGlobalLock());
        }
        }

    private static class TransactionFixture {
        private final RecordingDataSource ds1 = new RecordingDataSource();
        private final RecordingDataSource ds2 = new RecordingDataSource();
        private final List<String> usedDataSources = new ArrayList<>();
        private final List<String> seataXids = new ArrayList<>();
        private final List<Boolean> seataGlobalLockFlags = new ArrayList<>();

        private DataSource ds1() {
            return ds1;
        }

        private DataSource ds2() {
            return ds2;
        }

        private void record(Connection connection) {
            if (connection == ds1.connection()) {
                usedDataSources.add("ds1");
            } else if (connection == ds2.connection()) {
                usedDataSources.add("ds2");
            } else {
                throw new IllegalStateException("Unexpected datasource connection");
            }
        }

        private List<String> usedDataSources() {
            return List.copyOf(usedDataSources);
        }

        private void recordSeataXid(String xid) {
            seataXids.add(xid);
        }

        private List<String> seataXids() {
            return new ArrayList<>(seataXids);
        }

        private void recordSeataGlobalLockFlag(boolean required) {
            seataGlobalLockFlags.add(required);
        }

        private List<Boolean> seataGlobalLockFlags() {
            return List.copyOf(seataGlobalLockFlags);
        }

        private RecordingConnection ds1Connection() {
            return ds1.recordingConnection();
        }

        private RecordingConnection ds2Connection() {
            return ds2.recordingConnection();
        }
    }

    private static class RecordingDataSource implements DataSource {
        private final RecordingConnection recordingConnection = new RecordingConnection();

        @Override
        public Connection getConnection() {
            return recordingConnection.connection();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        private Connection connection() {
            return recordingConnection.connection();
        }

        private RecordingConnection recordingConnection() {
            return recordingConnection;
        }
    }

    private static class RecordingConnection implements java.lang.reflect.InvocationHandler {
        private final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class[]{Connection.class}, this);
        private boolean autoCommit = true;
        private boolean committed;
        private boolean rolledBack;
        private boolean readOnlyRequested;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getAutoCommit" -> autoCommit;
                case "setAutoCommit" -> {
                    autoCommit = (boolean) args[0];
                    yield null;
                }
                case "setReadOnly" -> {
                    readOnlyRequested = (boolean) args[0] || readOnlyRequested;
                    yield null;
                }
                case "commit" -> {
                    committed = true;
                    yield null;
                }
                case "rollback" -> {
                    rolledBack = true;
                    yield null;
                }
                case "isClosed", "isReadOnly", "isWrapperFor" -> false;
                case "getTransactionIsolation" -> Connection.TRANSACTION_READ_COMMITTED;
                case "toString" -> "RecordingConnection";
                case "unwrap" -> throw new IllegalStateException("Not a wrapper");
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }

        private Connection connection() {
            return connection;
        }

        private boolean committed() {
            return committed;
        }

        private boolean rolledBack() {
            return rolledBack;
        }

        private boolean readOnlyRequested() {
            return readOnlyRequested;
        }
    }

    @Qualifier("qualifiedTxManager")
    static class ClassQualifiedInnerService {
        private final TransactionRecorder transactionRecorder;

        ClassQualifiedInnerService(TransactionRecorder transactionRecorder) {
            this.transactionRecorder = transactionRecorder;
        }

        @GXDataSource("ds2")
        @Transactional
        public void getList() {
            transactionRecorder.recordCurrentDataSource();
        }
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableTransactionManagement
    static class ConfigurerTransactionTestConfig implements TransactionManagementConfigurer {
        @Bean
        GXDataSourceAspect gxDataSourceAspect() {
            return new GXDataSourceAspect();
        }

        @Bean
        TransactionFixture transactionFixture() {
            return new TransactionFixture();
        }

        @Bean({"routingDataSource", "dynamicDataSource"})
        GXDynamicDataSource routingDataSource(TransactionFixture fixture) {
            GXDynamicDataSource dataSource = new GXDynamicDataSource();
            dataSource.setTargetDataSources(Map.of("ds1", fixture.ds1(), "ds2", fixture.ds2(),
                    "framework", fixture.ds1()));
            dataSource.setDefaultTargetDataSource(fixture.ds1());
            dataSource.setDefaultDataSourceName("framework");
            dataSource.afterPropertiesSet();
            return dataSource;
        }

        @Bean("primaryTxManager")
        @Primary
        TrackingTransactionManager primaryTxManager(@Qualifier("routingDataSource") DataSource dataSource) {
            return new TrackingTransactionManager(dataSource);
        }

        @Bean("configuredTxManager")
        TrackingTransactionManager configuredTxManager(@Qualifier("routingDataSource") DataSource dataSource) {
            return new TrackingTransactionManager(dataSource);
        }

        @Override
        public TransactionManager annotationDrivenTransactionManager() {
            return configuredTxManager(routingDataSource(transactionFixture()));
        }

        @Bean
        TransactionRecorder transactionRecorder(@Qualifier("routingDataSource") DataSource dataSource,
                                                TransactionFixture fixture) {
            return new TransactionRecorder(dataSource, fixture);
        }

        @Bean
        InnerService innerService(TransactionRecorder transactionRecorder) {
            return new InnerService(transactionRecorder);
        }

        @Bean
        ClassQualifiedInnerService classQualifiedInnerService(TransactionRecorder transactionRecorder) {
            return new ClassQualifiedInnerService(transactionRecorder);
        }

        @Bean
        OuterService outerService(InnerService innerService, ClassQualifiedInnerService classQualifiedInnerService,
                                  TransactionRecorder transactionRecorder) {
            return new OuterService(innerService, classQualifiedInnerService, transactionRecorder);
        }
    }

    private static class TrackingTransactionManager extends DataSourceTransactionManager {
        private int commitCount;
        private int rollbackCount;

        private TrackingTransactionManager(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
            super.doCommit(status);
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
            super.doRollback(status);
        }

        private int commitCount() {
            return commitCount;
        }

        private int rollbackCount() {
            return rollbackCount;
        }
    }

    private static class OuterTransactionFailure extends RuntimeException {
    }

    private static class InnerTransactionFailure extends RuntimeException {
    }
}
