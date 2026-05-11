package cn.maple.core.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.framework.util.GXCommonUtils;
import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.filter.FilterManager;
import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.WallFilter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class GXDynamicDataSourceFactory {
    private static final GXDataSourceProperties DEFAULT_PROPERTIES = new GXDataSourceProperties();

    private GXDynamicDataSourceFactory() {
    }

    public static DruidDataSource buildDruidDataSource(GXDataSourceProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("Datasource properties must not be null");
        }

        String url = GXCommonUtils.decodeConnectStr(resolveString(properties.getUrl(), "spring.datasource.druid.url"), String.class);
        if (CharSequenceUtil.isBlank(url)) {
            throw new IllegalArgumentException("Datasource URL must not be blank");
        }

        DruidDataSource druidDataSource = new DruidDataSource();
        configureDruidDataSource(druidDataSource, properties, url);

        try {
            druidDataSource.setProxyFilters(buildFilters(properties));
            druidDataSource.init();
            return druidDataSource;
        } catch (SQLException | RuntimeException e) {
            druidDataSource.close();
            throw new IllegalStateException("Failed to initialize datasource: " + e.getMessage(), e);
        }
    }

    private static void configureDruidDataSource(DruidDataSource druidDataSource, GXDataSourceProperties properties, String url) {
        druidDataSource.setDbType(resolveString(properties.getDbType(), properties.isDbTypeConfigured(), DEFAULT_PROPERTIES.getDbType(), "spring.datasource.druid.db-type"));
        druidDataSource.setDriverClassName(resolveString(properties.getDriverClassName(), properties.isDriverClassNameConfigured(), DEFAULT_PROPERTIES.getDriverClassName(), "spring.datasource.druid.driver-class-name"));
        druidDataSource.setUrl(url);
        druidDataSource.setUsername(GXCommonUtils.decodeConnectStr(resolveString(properties.getUsername(), "spring.datasource.druid.username"), String.class));
        druidDataSource.setPassword(GXCommonUtils.decodeConnectStr(resolveString(properties.getPassword(), "spring.datasource.druid.password"), String.class));

        druidDataSource.setInitialSize(resolveInt(properties.getInitialSize(), properties.isInitialSizeConfigured(), DEFAULT_PROPERTIES.getInitialSize(), "spring.datasource.druid.initial-size"));
        druidDataSource.setMaxActive(resolveInt(properties.getMaxActive(), properties.isMaxActiveConfigured(), DEFAULT_PROPERTIES.getMaxActive(), "spring.datasource.druid.max-active"));
        druidDataSource.setMinIdle(resolveInt(properties.getMinIdle(), properties.isMinIdleConfigured(), DEFAULT_PROPERTIES.getMinIdle(), "spring.datasource.druid.min-idle"));
        druidDataSource.setMaxWait(resolveLong(properties.getMaxWait(), properties.isMaxWaitConfigured(), DEFAULT_PROPERTIES.getMaxWait(), "spring.datasource.druid.max-wait"));
        druidDataSource.setTimeBetweenEvictionRunsMillis(resolveLong(properties.getTimeBetweenEvictionRunsMillis(), properties.isTimeBetweenEvictionRunsMillisConfigured(), DEFAULT_PROPERTIES.getTimeBetweenEvictionRunsMillis(), "spring.datasource.druid.time-between-eviction-runs-millis"));
        druidDataSource.setMinEvictableIdleTimeMillis(resolveLong(properties.getMinEvictableIdleTimeMillis(), properties.isMinEvictableIdleTimeMillisConfigured(), DEFAULT_PROPERTIES.getMinEvictableIdleTimeMillis(), "spring.datasource.druid.min-evictable-idle-time-millis"));
        druidDataSource.setMaxEvictableIdleTimeMillis(resolveLong(properties.getMaxEvictableIdleTimeMillis(), properties.isMaxEvictableIdleTimeMillisConfigured(), DEFAULT_PROPERTIES.getMaxEvictableIdleTimeMillis(), "spring.datasource.druid.max-evictable-idle-time-millis"));
        druidDataSource.setValidationQuery(resolveString(properties.getValidationQuery(), properties.isValidationQueryConfigured(), DEFAULT_PROPERTIES.getValidationQuery(), "spring.datasource.druid.validation-query"));
        druidDataSource.setValidationQueryTimeout(resolveInt(properties.getValidationQueryTimeout(), properties.isValidationQueryTimeoutConfigured(), DEFAULT_PROPERTIES.getValidationQueryTimeout(), "spring.datasource.druid.validation-query-timeout"));
        druidDataSource.setTestOnBorrow(resolveBoolean(properties.isTestOnBorrow(), properties.isTestOnBorrowConfigured(), DEFAULT_PROPERTIES.isTestOnBorrow(), "spring.datasource.druid.test-on-borrow"));
        druidDataSource.setTestOnReturn(resolveBoolean(properties.isTestOnReturn(), properties.isTestOnReturnConfigured(), DEFAULT_PROPERTIES.isTestOnReturn(), "spring.datasource.druid.test-on-return"));
        druidDataSource.setTestWhileIdle(resolveBoolean(properties.isTestWhileIdle(), properties.isTestWhileIdleConfigured(), DEFAULT_PROPERTIES.isTestWhileIdle(), "spring.datasource.druid.test-while-idle"));
        druidDataSource.setPoolPreparedStatements(resolveBoolean(properties.isPoolPreparedStatements(), properties.isPoolPreparedStatementsConfigured(), DEFAULT_PROPERTIES.isPoolPreparedStatements(), "spring.datasource.druid.pool-prepared-statements"));
        druidDataSource.setMaxOpenPreparedStatements(resolveInt(properties.getMaxOpenPreparedStatements(), properties.isMaxOpenPreparedStatementsConfigured(), DEFAULT_PROPERTIES.getMaxOpenPreparedStatements(), "spring.datasource.druid.max-open-prepared-statements"));
        int maxPoolPreparedStatementPerConnectionSize = resolveInt(properties.getMaxPoolPreparedStatementPerConnectionSize(), properties.isMaxPoolPreparedStatementPerConnectionSizeConfigured(), DEFAULT_PROPERTIES.getMaxPoolPreparedStatementPerConnectionSize(), "spring.datasource.druid.max-pool-prepared-statement-per-connection-size");
        if (maxPoolPreparedStatementPerConnectionSize > 0) {
            druidDataSource.setMaxPoolPreparedStatementPerConnectionSize(maxPoolPreparedStatementPerConnectionSize);
        }
        druidDataSource.setSharePreparedStatements(resolveBoolean(properties.isSharePreparedStatements(), properties.isSharePreparedStatementsConfigured(), DEFAULT_PROPERTIES.isSharePreparedStatements(), "spring.datasource.druid.share-prepared-statements"));
        druidDataSource.setAsyncInit(resolveBoolean(properties.isAsyncInit(), properties.isAsyncInitConfigured(), DEFAULT_PROPERTIES.isAsyncInit(), "spring.datasource.druid.async-init"));
        String connectionProperties = resolveString(properties.getConnectionProperties(), properties.isConnectionPropertiesConfigured(), null, "spring.datasource.druid.connection-properties");
        if (CharSequenceUtil.isNotBlank(connectionProperties)) {
            druidDataSource.setConnectionProperties(connectionProperties);
        }
    }

    private static List<Filter> buildFilters(GXDataSourceProperties properties) throws SQLException {
        List<Filter> filters = new ArrayList<>();
        for (String filterName : resolveFilters(resolveString(properties.getFilters(), properties.isFiltersConfigured(), DEFAULT_PROPERTIES.getFilters(), "spring.datasource.druid.filters"))) {
            switch (filterName) {
                case "stat" -> filters.add(buildStatFilter(properties));
                case "wall" -> filters.add(buildWallFilter(properties));
                case "slf4j" -> {
                    if (resolveSlf4jEnabled(resolveSlf4jProperties(properties))) {
                        filters.add(buildSlf4jLogFilter(properties));
                    }
                }
                default -> FilterManager.loadFilter(filters, filterName);
            }
        }
        return filters;
    }

    private static boolean resolveSlf4jEnabled(GXDataSourceProperties.Slf4jProperties slf4jProperties) {
        if (slf4jProperties.getEnable() != null) {
            return slf4jProperties.getEnable();
        }
        if (slf4jProperties.getEnabled() != null) {
            return slf4jProperties.getEnabled();
        }
        Boolean enabled = GXCommonUtils.getEnvironmentValue("spring.datasource.druid.filter.slf4j.enable", Boolean.class, null);
        if (enabled != null) {
            return enabled;
        }
        return GXCommonUtils.getEnvironmentValue("spring.datasource.druid.filter.slf4j.enabled", Boolean.class, true);
    }

    private static StatFilter buildStatFilter(GXDataSourceProperties properties) {
        GXDataSourceProperties.StatProperties statProperties = resolveStatProperties(properties);
        StatFilter statFilter = new ReapplyingStatFilter();
        String dbType = resolveString(properties.getDbType(), properties.isDbTypeConfigured(), DEFAULT_PROPERTIES.getDbType(), "spring.datasource.druid.db-type");
        statFilter.setDbType(resolveString(statProperties.getDbType(), "spring.datasource.druid.filter.stat.db-type", dbType));
        applyIfNotNull(resolveBoolean(statProperties.getConnectionStackTraceEnable(), "spring.datasource.druid.filter.stat.connection-stack-trace-enable"), statFilter::setConnectionStackTraceEnable);
        applyIfNotNull(resolveBoolean(statProperties.getLogSlowSql(), "spring.datasource.druid.filter.stat.log-slow-sql"), statFilter::setLogSlowSql);
        applyIfNotNull(resolveLong(statProperties.getSlowSqlMillis(), "spring.datasource.druid.filter.stat.slow-sql-millis"), statFilter::setSlowSqlMillis);
        applyIfNotNull(resolveBoolean(statProperties.getMergeSql(), "spring.datasource.druid.filter.stat.merge-sql"), statFilter::setMergeSql);
        applyIfNotBlank(resolveString(statProperties.getSlowSqlLogLevel(), "spring.datasource.druid.filter.stat.slow-sql-log-level"), statFilter::setSlowSqlLogLevel);
        return statFilter;
    }

    private static WallFilter buildWallFilter(GXDataSourceProperties properties) {
        GXDataSourceProperties.WallProperties wallProperties = resolveWallProperties(properties);
        GXDataSourceProperties.WallConfigProperties configProperties = Objects.requireNonNullElseGet(
                wallProperties.getConfig(), GXDataSourceProperties.WallConfigProperties::new);
        WallConfig wallConfig = new WallConfig();
        applyIfNotBlank(resolveString(configProperties.getDir(), "spring.datasource.druid.filter.wall.config.dir"), wallConfig::setDir);
        applyIfNotBlank(resolveString(configProperties.getTenantTablePattern(), "spring.datasource.druid.filter.wall.config.tenant-table-pattern"), wallConfig::setTenantTablePattern);
        applyIfNotBlank(resolveString(configProperties.getTenantColumn(), "spring.datasource.druid.filter.wall.config.tenant-column"), wallConfig::setTenantColumn);
        applyIfNotNull(resolveBoolean(configProperties.getNoneBaseStatementAllow(), "spring.datasource.druid.filter.wall.config.none-base-statement-allow"), wallConfig::setNoneBaseStatementAllow);
        applyIfNotNull(resolveBoolean(configProperties.getCallAllow(), "spring.datasource.druid.filter.wall.config.call-allow"), wallConfig::setCallAllow);
        applyIfNotNull(resolveBoolean(configProperties.getSelectAllow(), "spring.datasource.druid.filter.wall.config.select-allow"), wallConfig::setSelectAllow);
        applyIfNotNull(resolveBoolean(configProperties.getSelectAllColumnAllow(), "spring.datasource.druid.filter.wall.config.select-all-column-allow"), wallConfig::setSelectAllColumnAllow);
        applyIfNotNull(resolveBoolean(configProperties.getSelectIntoAllow(), "spring.datasource.druid.filter.wall.config.select-into-allow"), wallConfig::setSelectIntoAllow);
        applyIfNotNull(resolveBoolean(configProperties.getSelectIntoOutfileAllow(), "spring.datasource.druid.filter.wall.config.select-into-outfile-allow"), wallConfig::setSelectIntoOutfileAllow);
        applyIfNotNull(resolveBoolean(configProperties.getSelectUnionCheck(), "spring.datasource.druid.filter.wall.config.select-union-check"), wallConfig::setSelectUnionCheck);
        applyIfNotNull(resolveBoolean(configProperties.getSelectMinusCheck(), "spring.datasource.druid.filter.wall.config.select-minus-check"), wallConfig::setSelectMinusCheck);
        applyIfNotNull(resolveBoolean(configProperties.getSelectExceptCheck(), "spring.datasource.druid.filter.wall.config.select-except-check"), wallConfig::setSelectExceptCheck);
        applyIfNotNull(resolveBoolean(configProperties.getSelectIntersectCheck(), "spring.datasource.druid.filter.wall.config.select-intersect-check"), wallConfig::setSelectIntersectCheck);
        applyIfNotNull(resolveBoolean(configProperties.getSelectWhereAlwayTrueCheck(), "spring.datasource.druid.filter.wall.config.select-where-alway-true-check"), wallConfig::setSelectWhereAlwayTrueCheck);
        applyIfNotNull(resolveBoolean(configProperties.getSelectHavingAlwayTrueCheck(), "spring.datasource.druid.filter.wall.config.select-having-alway-true-check"), wallConfig::setSelectHavingAlwayTrueCheck);
        applyIfNotNull(resolveInteger(configProperties.getSelectLimit(), "spring.datasource.druid.filter.wall.config.select-limit"), wallConfig::setSelectLimit);
        applyIfNotNull(resolveBoolean(configProperties.getInsertAllow(), "spring.datasource.druid.filter.wall.config.insert-allow"), wallConfig::setInsertAllow);
        applyIfNotNull(resolveBoolean(configProperties.getReplaceAllow(), "spring.datasource.druid.filter.wall.config.replace-allow"), wallConfig::setReplaceAllow);
        applyIfNotNull(resolveBoolean(configProperties.getCompleteInsertValuesCheck(), "spring.datasource.druid.filter.wall.config.complete-insert-values-check"), wallConfig::setCompleteInsertValuesCheck);
        applyIfNotNull(resolveInteger(configProperties.getInsertValuesCheckSize(), "spring.datasource.druid.filter.wall.config.insert-values-check-size"), wallConfig::setInsertValuesCheckSize);
        applyIfNotNull(resolveBoolean(configProperties.getUpdateAllow(), "spring.datasource.druid.filter.wall.config.update-allow"), wallConfig::setUpdateAllow);
        applyIfNotNull(resolveBoolean(configProperties.getUpdateWhereNoneCheck(), "spring.datasource.druid.filter.wall.config.update-where-none-check"), wallConfig::setUpdateWhereNoneCheck);
        applyIfNotNull(resolveBoolean(configProperties.getUpdateWhereAlwayTrueCheck(), "spring.datasource.druid.filter.wall.config.update-where-alway-true-check"), wallConfig::setUpdateWhereAlwayTrueCheck);
        applyIfNotNull(resolveBoolean(configProperties.getDeleteAllow(), "spring.datasource.druid.filter.wall.config.delete-allow"), wallConfig::setDeleteAllow);
        applyIfNotNull(resolveBoolean(configProperties.getDeleteWhereNoneCheck(), "spring.datasource.druid.filter.wall.config.delete-where-none-check"), wallConfig::setDeleteWhereNoneCheck);
        applyIfNotNull(resolveBoolean(configProperties.getDeleteWhereAlwayTrueCheck(), "spring.datasource.druid.filter.wall.config.delete-where-alway-true-check"), wallConfig::setDeleteWhereAlwayTrueCheck);
        applyIfNotNull(resolveBoolean(configProperties.getCreateTableAllow(), "spring.datasource.druid.filter.wall.config.create-table-allow"), wallConfig::setCreateTableAllow);
        applyIfNotNull(resolveBoolean(configProperties.getAlterTableAllow(), "spring.datasource.druid.filter.wall.config.alter-table-allow"), wallConfig::setAlterTableAllow);
        applyIfNotNull(resolveBoolean(configProperties.getRenameTableAllow(), "spring.datasource.druid.filter.wall.config.rename-table-allow"), wallConfig::setRenameTableAllow);
        applyIfNotNull(resolveBoolean(configProperties.getMultiStatementAllow(), "spring.datasource.druid.filter.wall.config.multi-statement-allow"), wallConfig::setMultiStatementAllow);
        applyIfNotNull(resolveBoolean(configProperties.getTruncateAllow(), "spring.datasource.druid.filter.wall.config.truncate-allow"), wallConfig::setTruncateAllow);
        applyIfNotNull(resolveBoolean(configProperties.getDropTableAllow(), "spring.datasource.druid.filter.wall.config.drop-table-allow"), wallConfig::setDropTableAllow);
        applyIfNotNull(resolveBoolean(configProperties.getCommentAllow(), "spring.datasource.druid.filter.wall.config.comment-allow"), wallConfig::setCommentAllow);
        applyIfNotNull(resolveBoolean(configProperties.getStrictSyntaxCheck(), "spring.datasource.druid.filter.wall.config.strict-syntax-check"), wallConfig::setStrictSyntaxCheck);
        applyIfNotNull(resolveBoolean(configProperties.getSchemaCheck(), "spring.datasource.druid.filter.wall.config.schema-check"), wallConfig::setSchemaCheck);
        applyIfNotNull(resolveBoolean(configProperties.getTableCheck(), "spring.datasource.druid.filter.wall.config.table-check"), wallConfig::setTableCheck);
        applyIfNotNull(resolveBoolean(configProperties.getFunctionCheck(), "spring.datasource.druid.filter.wall.config.function-check"), wallConfig::setFunctionCheck);
        applyIfNotNull(resolveBoolean(configProperties.getVariantCheck(), "spring.datasource.druid.filter.wall.config.variant-check"), wallConfig::setVariantCheck);
        applyIfNotNull(resolveBoolean(configProperties.getObjectCheck(), "spring.datasource.druid.filter.wall.config.object-check"), wallConfig::setObjectCheck);
        applyIfNotNull(resolveBoolean(configProperties.getMustParameterized(), "spring.datasource.druid.filter.wall.config.must-parameterized"), wallConfig::setMustParameterized);
        applyIfNotNull(resolveBoolean(configProperties.getMetadataAllow(), "spring.datasource.druid.filter.wall.config.metadata-allow"), wallConfig::setMetadataAllow);
        applyIfNotNull(resolveBoolean(configProperties.getShowAllow(), "spring.datasource.druid.filter.wall.config.show-allow"), wallConfig::setShowAllow);
        applyIfNotNull(resolveBoolean(configProperties.getDescribeAllow(), "spring.datasource.druid.filter.wall.config.describe-allow"), wallConfig::setDescribeAllow);
        applyIfNotNull(resolveBoolean(configProperties.getSetAllow(), "spring.datasource.druid.filter.wall.config.set-allow"), wallConfig::setSetAllow);
        applyIfNotNull(resolveBoolean(configProperties.getUseAllow(), "spring.datasource.druid.filter.wall.config.use-allow"), wallConfig::setUseAllow);
        applyIfNotNull(resolveBoolean(configProperties.getCommitAllow(), "spring.datasource.druid.filter.wall.config.commit-allow"), wallConfig::setCommitAllow);
        applyIfNotNull(resolveBoolean(configProperties.getRollbackAllow(), "spring.datasource.druid.filter.wall.config.rollback-allow"), wallConfig::setRollbackAllow);
        applyIfNotNull(resolveBoolean(configProperties.getMergeAllow(), "spring.datasource.druid.filter.wall.config.merge-allow"), wallConfig::setMergeAllow);
        applyIfNotNull(resolveBoolean(configProperties.getHintAllow(), "spring.datasource.druid.filter.wall.config.hint-allow"), wallConfig::setHintAllow);
        applyIfNotNull(resolveBoolean(configProperties.getLockTableAllow(), "spring.datasource.druid.filter.wall.config.lock-table-allow"), wallConfig::setLockTableAllow);
        applyIfNotNull(resolveBoolean(configProperties.getStartTransactionAllow(), "spring.datasource.druid.filter.wall.config.start-transaction-allow"), wallConfig::setStartTransactionAllow);
        applyIfNotNull(resolveBoolean(configProperties.getBlockAllow(), "spring.datasource.druid.filter.wall.config.block-allow"), wallConfig::setBlockAllow);
        applyIfNotNull(resolveBoolean(configProperties.getWrapAllow(), "spring.datasource.druid.filter.wall.config.wrap-allow"), wallConfig::setWrapAllow);
        applyIfNotNull(resolveBoolean(configProperties.getDoPrivilegedAllow(), "spring.datasource.druid.filter.wall.config.do-privileged-allow"), wallConfig::setDoPrivilegedAllow);
        applyIfNotNull(resolveBoolean(configProperties.getCaseConditionConstAllow(), "spring.datasource.druid.filter.wall.config.case-condition-const-allow"), wallConfig::setCaseConditionConstAllow);
        applyIfNotNull(resolveBoolean(configProperties.getConditionDoubleConstAllow(), "spring.datasource.druid.filter.wall.config.condition-double-const-allow"), wallConfig::setConditionDoubleConstAllow);
        applyIfNotNull(resolveBoolean(configProperties.getConditionLikeTrueAllow(), "spring.datasource.druid.filter.wall.config.condition-like-true-allow"), wallConfig::setConditionLikeTrueAllow);
        applyIfNotNull(resolveBoolean(configProperties.getConditionAndAlwayTrueAllow(), "spring.datasource.druid.filter.wall.config.condition-and-alway-true-allow"), wallConfig::setConditionAndAlwayTrueAllow);
        applyIfNotNull(resolveBoolean(configProperties.getConditionAndAlwayFalseAllow(), "spring.datasource.druid.filter.wall.config.condition-and-alway-false-allow"), wallConfig::setConditionAndAlwayFalseAllow);
        applyIfNotNull(resolveBoolean(configProperties.getConditionOpXorAllow(), "spring.datasource.druid.filter.wall.config.condition-op-xor-allow"), wallConfig::setConditionOpXorAllow);
        applyIfNotNull(resolveBoolean(configProperties.getConditionOpBitwiseAllow(), "spring.datasource.druid.filter.wall.config.condition-op-bitwise-allow"), wallConfig::setConditionOpBitwiseAllow);
        applyIfNotNull(resolveBoolean(configProperties.getConstArithmeticAllow(), "spring.datasource.druid.filter.wall.config.const-arithmetic-allow"), wallConfig::setConstArithmeticAllow);
        applyIfNotNull(resolveBoolean(configProperties.getLimitZeroAllow(), "spring.datasource.druid.filter.wall.config.limit-zero-allow"), wallConfig::setLimitZeroAllow);
        applyIfNotNull(resolveBoolean(configProperties.getIntersectAllow(), "spring.datasource.druid.filter.wall.config.intersect-allow"), wallConfig::setIntersectAllow);
        applyIfNotNull(resolveBoolean(configProperties.getMinusAllow(), "spring.datasource.druid.filter.wall.config.minus-allow"), wallConfig::setMinusAllow);

        WallFilter wallFilter = new WallFilter();
        wallFilter.setDbType(resolveString(properties.getDbType(), properties.isDbTypeConfigured(), DEFAULT_PROPERTIES.getDbType(), "spring.datasource.druid.db-type"));
        wallFilter.setConfig(wallConfig);
        applyIfNotBlank(resolveString(wallProperties.getTenantColumn(), "spring.datasource.druid.filter.wall.tenant-column"), wallFilter::setTenantColumn);
        applyIfNotNull(resolveBoolean(wallProperties.getLogViolation(), "spring.datasource.druid.filter.wall.log-violation"), wallFilter::setLogViolation);
        applyIfNotNull(resolveBoolean(wallProperties.getThrowException(), "spring.datasource.druid.filter.wall.throw-exception"), wallFilter::setThrowException);
        return wallFilter;
    }

    private static Slf4jLogFilter buildSlf4jLogFilter(GXDataSourceProperties properties) {
        GXDataSourceProperties.Slf4jProperties slf4jProperties = resolveSlf4jProperties(properties);
        Slf4jLogFilter slf4jLogFilter = new Slf4jLogFilter();
        applyIfNotBlank(resolveString(slf4jProperties.getDataSourceLoggerName(), "spring.datasource.druid.filter.slf4j.data-source-logger-name"), slf4jLogFilter::setDataSourceLoggerName);
        applyIfNotBlank(resolveString(slf4jProperties.getConnectionLoggerName(), "spring.datasource.druid.filter.slf4j.connection-logger-name"), slf4jLogFilter::setConnectionLoggerName);
        applyIfNotBlank(resolveString(slf4jProperties.getStatementLoggerName(), "spring.datasource.druid.filter.slf4j.statement-logger-name"), slf4jLogFilter::setStatementLoggerName);
        applyIfNotBlank(resolveString(slf4jProperties.getResultSetLoggerName(), "spring.datasource.druid.filter.slf4j.result-set-logger-name"), slf4jLogFilter::setResultSetLoggerName);
        applyIfNotNull(resolveBoolean(slf4jProperties.getDataSourceLogEnabled(), "spring.datasource.druid.filter.slf4j.data-source-log-enabled"), slf4jLogFilter::setDataSourceLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getConnectionLogEnabled(), "spring.datasource.druid.filter.slf4j.connection-log-enabled"), slf4jLogFilter::setConnectionLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getConnectionLogErrorEnabled(), "spring.datasource.druid.filter.slf4j.connection-log-error-enabled"), slf4jLogFilter::setConnectionLogErrorEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getConnectionConnectBeforeLogEnabled(), "spring.datasource.druid.filter.slf4j.connection-connect-before-log-enabled"), slf4jLogFilter::setConnectionConnectBeforeLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getConnectionConnectAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.connection-connect-after-log-enabled"), slf4jLogFilter::setConnectionConnectAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getConnectionCloseAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.connection-close-after-log-enabled"), slf4jLogFilter::setConnectionCloseAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getConnectionCommitAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.connection-commit-after-log-enabled"), slf4jLogFilter::setConnectionCommitAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getConnectionRollbackAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.connection-rollback-after-log-enabled"), slf4jLogFilter::setConnectionRollbackAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-log-enabled"), slf4jLogFilter::setStatementLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementLogErrorEnabled(), "spring.datasource.druid.filter.slf4j.statement-log-error-enabled"), slf4jLogFilter::setStatementLogErrorEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementCreateAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-create-after-log-enabled"), slf4jLogFilter::setStatementCreateAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementPrepareAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-prepare-after-log-enabled"), slf4jLogFilter::setStatementPrepareAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementPrepareCallAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-prepare-call-after-log-enabled"), slf4jLogFilter::setStatementPrepareCallAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementExecuteAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-execute-after-log-enabled"), slf4jLogFilter::setStatementExecuteAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementExecuteQueryAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-execute-query-after-log-enabled"), slf4jLogFilter::setStatementExecuteQueryAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementExecuteUpdateAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-execute-update-after-log-enabled"), slf4jLogFilter::setStatementExecuteUpdateAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementExecuteBatchAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-execute-batch-after-log-enabled"), slf4jLogFilter::setStatementExecuteBatchAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementExecutableSqlLogEnable(), "spring.datasource.druid.filter.slf4j.statement-executable-sql-log-enable"), slf4jLogFilter::setStatementExecutableSqlLogEnable);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementCloseAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-close-after-log-enabled"), slf4jLogFilter::setStatementCloseAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementParameterSetLogEnabled(), "spring.datasource.druid.filter.slf4j.statement-parameter-set-log-enabled"), slf4jLogFilter::setStatementParameterSetLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementParameterClearLogEnable(), "spring.datasource.druid.filter.slf4j.statement-parameter-clear-log-enable"), slf4jLogFilter::setStatementParameterClearLogEnable);
        applyIfNotNull(resolveBoolean(slf4jProperties.getStatementSqlPrettyFormat(), "spring.datasource.druid.filter.slf4j.statement-sql-pretty-format"), slf4jLogFilter::setStatementSqlPrettyFormat);
        applyIfNotNull(resolveBoolean(slf4jProperties.getResultSetLogEnabled(), "spring.datasource.druid.filter.slf4j.result-set-log-enabled"), slf4jLogFilter::setResultSetLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getResultSetLogErrorEnabled(), "spring.datasource.druid.filter.slf4j.result-set-log-error-enabled"), slf4jLogFilter::setResultSetLogErrorEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getResultSetOpenAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.result-set-open-after-log-enabled"), slf4jLogFilter::setResultSetOpenAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getResultSetNextAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.result-set-next-after-log-enabled"), slf4jLogFilter::setResultSetNextAfterLogEnabled);
        applyIfNotNull(resolveBoolean(slf4jProperties.getResultSetCloseAfterLogEnabled(), "spring.datasource.druid.filter.slf4j.result-set-close-after-log-enabled"), slf4jLogFilter::setResultSetCloseAfterLogEnabled);
        return slf4jLogFilter;
    }

    private static GXDataSourceProperties.StatProperties resolveStatProperties(GXDataSourceProperties properties) {
        GXDataSourceProperties.FilterProperties filterProperties = properties.getFilter();
        if (filterProperties == null || filterProperties.getStat() == null) {
            return new GXDataSourceProperties.StatProperties();
        }
        return filterProperties.getStat();
    }

    private static GXDataSourceProperties.WallProperties resolveWallProperties(GXDataSourceProperties properties) {
        GXDataSourceProperties.FilterProperties filterProperties = properties.getFilter();
        if (filterProperties == null || filterProperties.getWall() == null) {
            return new GXDataSourceProperties.WallProperties();
        }
        return filterProperties.getWall();
    }

    private static GXDataSourceProperties.Slf4jProperties resolveSlf4jProperties(GXDataSourceProperties properties) {
        GXDataSourceProperties.FilterProperties filterProperties = properties.getFilter();
        if (filterProperties == null || filterProperties.getSlf4j() == null) {
            return new GXDataSourceProperties.Slf4jProperties();
        }
        return filterProperties.getSlf4j();
    }

    private static Set<String> resolveFilters(String filters) {
        Set<String> filterSet = new LinkedHashSet<>();
        if (CharSequenceUtil.isNotBlank(filters)) {
            for (String filter : filters.split(",")) {
                String trimmedFilter = filter.trim();
                if (CharSequenceUtil.isNotBlank(trimmedFilter)) {
                    filterSet.add(trimmedFilter);
                }
            }
        }
        filterSet.add("stat");
        filterSet.add("wall");
        filterSet.add("slf4j");
        return filterSet;
    }

    private static String resolveString(String value, boolean configured, String defaultValue, String key) {
        if (configured) {
            return value;
        }
        return GXCommonUtils.getEnvironmentValue(key, String.class, defaultValue);
    }

    private static String resolveString(String value, String key) {
        return resolveString(value, key, null);
    }

    private static String resolveString(String value, String key, String defaultValue) {
        if (CharSequenceUtil.isNotBlank(value)) {
            return value;
        }
        return GXCommonUtils.getEnvironmentValue(key, String.class, defaultValue);
    }

    private static Boolean resolveBoolean(Boolean value, String key) {
        if (value != null) {
            return value;
        }
        return GXCommonUtils.getEnvironmentValue(key, Boolean.class, null);
    }

    private static Boolean resolveBoolean(Boolean value, String key, Boolean defaultValue) {
        if (value != null) {
            return value;
        }
        return GXCommonUtils.getEnvironmentValue(key, Boolean.class, defaultValue);
    }

    private static boolean resolveBoolean(boolean value, boolean configured, boolean defaultValue, String key) {
        if (configured) {
            return value;
        }
        Boolean configuredValue = GXCommonUtils.getEnvironmentValue(key, Boolean.class, null);
        return configuredValue == null ? value : configuredValue;
    }

    private static Long resolveLong(Long value, String key) {
        if (value != null) {
            return value;
        }
        return GXCommonUtils.getEnvironmentValue(key, Long.class, null);
    }

    private static Integer resolveInteger(Integer value, String key) {
        if (value != null) {
            return value;
        }
        return GXCommonUtils.getEnvironmentValue(key, Integer.class, null);
    }

    private static int resolveInt(int value, boolean configured, int defaultValue, String key) {
        if (configured) {
            return value;
        }
        Integer configuredValue = GXCommonUtils.getEnvironmentValue(key, Integer.class, null);
        return configuredValue == null ? value : configuredValue;
    }

    private static long resolveLong(long value, boolean configured, long defaultValue, String key) {
        if (configured) {
            return value;
        }
        Long configuredValue = GXCommonUtils.getEnvironmentValue(key, Long.class, null);
        return configuredValue == null ? value : configuredValue;
    }

    private static <T> void applyIfNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static void applyIfNotBlank(String value, Consumer<String> setter) {
        if (CharSequenceUtil.isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private static class ReapplyingStatFilter extends StatFilter {
        private String configuredDbType;

        private Boolean configuredLogSlowSql;

        private Long configuredSlowSqlMillis;

        private Boolean configuredMergeSql;

        private String configuredSlowSqlLogLevel;

        private Boolean configuredConnectionStackTraceEnable;

        @Override
        public void init(DataSourceProxy dataSource) {
            super.init(dataSource);
            reapplyConfiguredValues();
        }

        @Override
        public void setDbType(String dbType) {
            super.setDbType(dbType);
            this.configuredDbType = dbType;
        }

        @Override
        public void setLogSlowSql(boolean logSlowSql) {
            super.setLogSlowSql(logSlowSql);
            this.configuredLogSlowSql = logSlowSql;
        }

        @Override
        public void setSlowSqlMillis(long slowSqlMillis) {
            super.setSlowSqlMillis(slowSqlMillis);
            this.configuredSlowSqlMillis = slowSqlMillis;
        }

        @Override
        public void setMergeSql(boolean mergeSql) {
            super.setMergeSql(mergeSql);
            this.configuredMergeSql = mergeSql;
        }

        @Override
        public void setSlowSqlLogLevel(String slowSqlLogLevel) {
            super.setSlowSqlLogLevel(slowSqlLogLevel);
            this.configuredSlowSqlLogLevel = slowSqlLogLevel;
        }

        @Override
        public void setConnectionStackTraceEnable(boolean connectionStackTraceEnable) {
            super.setConnectionStackTraceEnable(connectionStackTraceEnable);
            this.configuredConnectionStackTraceEnable = connectionStackTraceEnable;
        }

        private void reapplyConfiguredValues() {
            applyIfNotBlank(configuredDbType, super::setDbType);
            applyIfNotNull(configuredConnectionStackTraceEnable, super::setConnectionStackTraceEnable);
            applyIfNotNull(configuredLogSlowSql, super::setLogSlowSql);
            applyIfNotNull(configuredSlowSqlMillis, super::setSlowSqlMillis);
            applyIfNotNull(configuredMergeSql, super::setMergeSql);
            applyIfNotBlank(configuredSlowSqlLogLevel, super::setSlowSqlLogLevel);
        }
    }
}
