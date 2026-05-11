package cn.maple.core.datasource.config;

import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.framework.util.GXCommonUtils;
import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.wall.WallFilter;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDynamicDataSourceFactoryTest {

    @Test
    void dynamicDatasourcePropertiesOverrideSpringDruidDefaults() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        properties.setInitialSize(2);
        properties.setTestOnBorrow(false);
        properties.setValidationQuery("select 1");

        DruidDataSource dataSource = new DruidDataSource();
        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class, Mockito.CALLS_REAL_METHODS)) {
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.initial-size", Integer.class, 5);
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.max-active", Integer.class, 20);
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.test-on-borrow", Boolean.class, true);
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.test-on-return", Boolean.class, true);
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.async-init", Boolean.class, true);
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.validation-query", String.class, "SELECT 1 FROM DUAL");
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.max-pool-prepared-statement-per-connection-size", Integer.class, 20);

            configureDruidDataSource(dataSource, properties, "jdbc:h2:mem:test");
        }

        assertEquals(2, dataSource.getInitialSize());
        assertEquals(20, dataSource.getMaxActive());
        assertFalse(dataSource.isTestOnBorrow());
        assertTrue(dataSource.isTestOnReturn());
        assertTrue(dataSource.isAsyncInit());
        assertEquals("select 1", dataSource.getValidationQuery());
        assertEquals(20, dataSource.getMaxPoolPreparedStatementPerConnectionSize());
    }

    @Test
    void dynamicConnectionPropertiesCanDisableSpringDruidInheritance() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        properties.setConnectionProperties("");
        DruidDataSource dataSource = new DruidDataSource();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class, Mockito.CALLS_REAL_METHODS)) {
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.connection-properties", String.class, "druid.stat.mergeSql=true");

            configureDruidDataSource(dataSource, properties, "jdbc:h2:mem:test");
        }

        assertTrue(dataSource.getConnectProperties().isEmpty());
    }

    @Test
    void buildsConfiguredDruidFilters() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        properties.setDbType("mysql");
        properties.setFilters("stat,wall,slf4j");

        GXDataSourceProperties.StatProperties stat = properties.getFilter().getStat();
        stat.setDbType("mysql");
        stat.setConnectionStackTraceEnable(true);
        stat.setLogSlowSql(true);
        stat.setSlowSqlMillis(500L);
        stat.setMergeSql(false);

        GXDataSourceProperties.WallConfigProperties wallConfig = properties.getFilter().getWall().getConfig();
        wallConfig.setSelectAllColumnAllow(false);
        wallConfig.setCommentAllow(false);
        wallConfig.setUpdateWhereNoneCheck(true);
        wallConfig.setSelectLimit(100);
        wallConfig.setMultiStatementAllow(true);
        wallConfig.setTruncateAllow(false);
        wallConfig.setDropTableAllow(false);

        GXDataSourceProperties.Slf4jProperties slf4j = properties.getFilter().getSlf4j();
        slf4j.setEnable(true);
        slf4j.setStatementLoggerName("custom.sql.Statement");
        slf4j.setStatementCreateAfterLogEnabled(false);
        slf4j.setStatementCloseAfterLogEnabled(false);
        slf4j.setStatementSqlPrettyFormat(true);
        slf4j.setResultSetOpenAfterLogEnabled(false);
        slf4j.setResultSetCloseAfterLogEnabled(false);

        List<Filter> filters = buildFilters(properties);

        assertEquals(3, filters.size());
        StatFilter statFilter = assertInstanceOf(StatFilter.class, filters.get(0));
        WallFilter wallFilter = assertInstanceOf(WallFilter.class, filters.get(1));
        Slf4jLogFilter slf4jLogFilter = assertInstanceOf(Slf4jLogFilter.class, filters.get(2));

        assertTrue(statFilter.isLogSlowSql());
        assertTrue(statFilter.isConnectionStackTraceEnable());
        assertEquals(500L, statFilter.getSlowSqlMillis());
        assertFalse(statFilter.isMergeSql());
        assertFalse(wallFilter.getConfig().isSelectAllColumnAllow());
        assertFalse(wallFilter.getConfig().isCommentAllow());
        assertTrue(wallFilter.getConfig().isUpdateWhereNoneCheck());
        assertEquals(100, wallFilter.getConfig().getSelectLimit());
        assertTrue(wallFilter.getConfig().isMultiStatementAllow());
        assertFalse(wallFilter.getConfig().isTruncateAllow());
        assertFalse(wallFilter.getConfig().isDropTableAllow());
        assertFalse(slf4jLogFilter.isStatementCreateAfterLogEnabled());
        assertFalse(slf4jLogFilter.isStatementCloseAfterLogEnabled());
        assertEquals("custom.sql.Statement", slf4jLogFilter.getStatementLoggerName());
        assertTrue(slf4jLogFilter.isStatementSqlPrettyFormat());
        assertFalse(slf4jLogFilter.isResultSetOpenAfterLogEnabled());
        assertFalse(slf4jLogFilter.isResultSetCloseAfterLogEnabled());
    }

    @Test
    void statFilterSettingsKeepPriorityAfterDruidConnectionPropertiesAreApplied() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        GXDataSourceProperties.StatProperties stat = properties.getFilter().getStat();
        stat.setConnectionStackTraceEnable(true);
        stat.setLogSlowSql(true);
        stat.setSlowSqlMillis(500L);
        stat.setMergeSql(false);

        StatFilter statFilter = assertInstanceOf(StatFilter.class, buildFilters(properties).get(0));
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("druid.stat.logSlowSql", "false");
        connectionProperties.setProperty("druid.stat.slowSqlMillis", "9999");
        connectionProperties.setProperty("druid.stat.mergeSql", "true");
        connectionProperties.setProperty("druid.stat.connectionStackTraceEnable", "false");
        DataSourceProxy dataSourceProxy = Mockito.mock(DataSourceProxy.class);
        Mockito.when(dataSourceProxy.getConnectProperties()).thenReturn(connectionProperties);

        statFilter.init(dataSourceProxy);

        assertTrue(statFilter.isLogSlowSql());
        assertTrue(statFilter.isConnectionStackTraceEnable());
        assertEquals(500L, statFilter.getSlowSqlMillis());
        assertFalse(statFilter.isMergeSql());
    }

    @Test
    void skipsSlf4jFilterWhenDisabled() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        properties.getFilter().getSlf4j().setEnable(false);

        List<Filter> filters = buildFilters(properties);

        assertEquals(2, filters.size());
        assertInstanceOf(StatFilter.class, filters.get(0));
        assertInstanceOf(WallFilter.class, filters.get(1));
    }

    @Test
    void supportsSpringDruidSlf4jEnabledAlias() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class, Mockito.CALLS_REAL_METHODS)) {
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.filter.slf4j.enabled", Boolean.class, false);

            List<Filter> filters = buildFilters(properties);

            assertEquals(2, filters.size());
            assertInstanceOf(StatFilter.class, filters.get(0));
            assertInstanceOf(WallFilter.class, filters.get(1));
        }
    }

    @Test
    void statementLogEnabledTurnsOnDruidStatementLogging() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        properties.getFilter().getSlf4j().setStatementLogEnabled(true);

        Slf4jLogFilter slf4jLogFilter = assertInstanceOf(Slf4jLogFilter.class, buildFilters(properties).get(2));
        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);
        slf4jLogFilter.setStatementLogger(logger);

        assertTrue(slf4jLogFilter.isStatementLogEnabled());
    }

    @Test
    void dynamicSlf4jStatementLogEnabledOverridesSpringDruidDefault() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        properties.getFilter().getSlf4j().setStatementLogEnabled(true);

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class, Mockito.CALLS_REAL_METHODS)) {
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.filter.slf4j.statement-log-enabled", Boolean.class, false);

            Slf4jLogFilter slf4jLogFilter = assertInstanceOf(Slf4jLogFilter.class, buildFilters(properties).get(2));
            Logger logger = Mockito.mock(Logger.class);
            Mockito.when(logger.isDebugEnabled()).thenReturn(true);
            slf4jLogFilter.setStatementLogger(logger);

            assertTrue(slf4jLogFilter.isStatementLogEnabled());
        }
    }

    @Test
    void dynamicWallConfigOverridesSpringDruidDefault() throws Exception {
        GXDataSourceProperties properties = new GXDataSourceProperties();
        properties.getFilter().getWall().getConfig().setMultiStatementAllow(true);

        try (MockedStatic<GXCommonUtils> commonUtils = Mockito.mockStatic(GXCommonUtils.class, Mockito.CALLS_REAL_METHODS)) {
            mockSpringDruidValue(commonUtils, "spring.datasource.druid.filter.wall.config.multi-statement-allow", Boolean.class, false);

            WallFilter wallFilter = assertInstanceOf(WallFilter.class, buildFilters(properties).get(1));

            assertTrue(wallFilter.getConfig().isMultiStatementAllow());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Filter> buildFilters(GXDataSourceProperties properties) throws Exception {
        Method method = GXDynamicDataSourceFactory.class.getDeclaredMethod("buildFilters", GXDataSourceProperties.class);
        method.setAccessible(true);
        return (List<Filter>) method.invoke(null, properties);
    }

    private void configureDruidDataSource(DruidDataSource dataSource, GXDataSourceProperties properties, String url) throws Exception {
        Method method = GXDynamicDataSourceFactory.class.getDeclaredMethod("configureDruidDataSource", DruidDataSource.class, GXDataSourceProperties.class, String.class);
        method.setAccessible(true);
        method.invoke(null, dataSource, properties, url);
    }

    private <T> void mockSpringDruidValue(MockedStatic<GXCommonUtils> commonUtils, String key, Class<T> type, T value) {
        commonUtils.when(() -> GXCommonUtils.getEnvironmentValue(Mockito.eq(key), Mockito.eq(type), Mockito.any())).thenReturn(value);
    }
}
