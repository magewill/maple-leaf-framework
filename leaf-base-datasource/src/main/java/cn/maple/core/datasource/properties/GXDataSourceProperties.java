package cn.maple.core.datasource.properties;

import lombok.Data;
import lombok.AccessLevel;
import lombok.Setter;

@Data
public class GXDataSourceProperties {
    private String driverClassName = "com.mysql.cj.jdbc.Driver"; //"com.p6spy.engine.spy.P6SpyDriver";

    private String url;

    private String username;

    private String password;

    private int initialSize = 2;

    private int maxActive = 10;

    private int minIdle = 2;

    private long maxWait = 60 * 1000L;

    private long timeBetweenEvictionRunsMillis = 60 * 1000L;

    private long minEvictableIdleTimeMillis = 1000L * 60L * 30L;

    private long maxEvictableIdleTimeMillis = 1000L * 60L * 60L * 7;

    private String validationQuery = "select 1";

    private int validationQueryTimeout = -1;

    private boolean testOnBorrow = false;

    private boolean testOnReturn = false;

    private boolean testWhileIdle = true;

    private boolean poolPreparedStatements = false;

    private int maxOpenPreparedStatements = -1;

    private int maxPoolPreparedStatementPerConnectionSize = -1;

    private boolean sharePreparedStatements = false;

    private boolean asyncInit = false;

    private String connectionProperties;

    private String filters = "stat,wall,slf4j";

    private FilterProperties filter = new FilterProperties();

    private String dbType = "mysql";

    @Setter(AccessLevel.NONE)
    private boolean driverClassNameConfigured;

    @Setter(AccessLevel.NONE)
    private boolean initialSizeConfigured;

    @Setter(AccessLevel.NONE)
    private boolean maxActiveConfigured;

    @Setter(AccessLevel.NONE)
    private boolean minIdleConfigured;

    @Setter(AccessLevel.NONE)
    private boolean maxWaitConfigured;

    @Setter(AccessLevel.NONE)
    private boolean timeBetweenEvictionRunsMillisConfigured;

    @Setter(AccessLevel.NONE)
    private boolean minEvictableIdleTimeMillisConfigured;

    @Setter(AccessLevel.NONE)
    private boolean maxEvictableIdleTimeMillisConfigured;

    @Setter(AccessLevel.NONE)
    private boolean validationQueryConfigured;

    @Setter(AccessLevel.NONE)
    private boolean validationQueryTimeoutConfigured;

    @Setter(AccessLevel.NONE)
    private boolean testOnBorrowConfigured;

    @Setter(AccessLevel.NONE)
    private boolean testOnReturnConfigured;

    @Setter(AccessLevel.NONE)
    private boolean testWhileIdleConfigured;

    @Setter(AccessLevel.NONE)
    private boolean poolPreparedStatementsConfigured;

    @Setter(AccessLevel.NONE)
    private boolean maxOpenPreparedStatementsConfigured;

    @Setter(AccessLevel.NONE)
    private boolean maxPoolPreparedStatementPerConnectionSizeConfigured;

    @Setter(AccessLevel.NONE)
    private boolean sharePreparedStatementsConfigured;

    @Setter(AccessLevel.NONE)
    private boolean asyncInitConfigured;

    @Setter(AccessLevel.NONE)
    private boolean connectionPropertiesConfigured;

    @Setter(AccessLevel.NONE)
    private boolean filtersConfigured;

    @Setter(AccessLevel.NONE)
    private boolean dbTypeConfigured;

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
        this.driverClassNameConfigured = true;
    }

    public void setInitialSize(int initialSize) {
        this.initialSize = initialSize;
        this.initialSizeConfigured = true;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
        this.maxActiveConfigured = true;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
        this.minIdleConfigured = true;
    }

    public void setMaxWait(long maxWait) {
        this.maxWait = maxWait;
        this.maxWaitConfigured = true;
    }

    public void setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
        this.timeBetweenEvictionRunsMillisConfigured = true;
    }

    public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
        this.minEvictableIdleTimeMillisConfigured = true;
    }

    public void setMaxEvictableIdleTimeMillis(long maxEvictableIdleTimeMillis) {
        this.maxEvictableIdleTimeMillis = maxEvictableIdleTimeMillis;
        this.maxEvictableIdleTimeMillisConfigured = true;
    }

    public void setValidationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
        this.validationQueryConfigured = true;
    }

    public void setValidationQueryTimeout(int validationQueryTimeout) {
        this.validationQueryTimeout = validationQueryTimeout;
        this.validationQueryTimeoutConfigured = true;
    }

    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        this.testOnBorrowConfigured = true;
    }

    public void setTestOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        this.testOnReturnConfigured = true;
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
        this.testWhileIdleConfigured = true;
    }

    public void setPoolPreparedStatements(boolean poolPreparedStatements) {
        this.poolPreparedStatements = poolPreparedStatements;
        this.poolPreparedStatementsConfigured = true;
    }

    public void setMaxOpenPreparedStatements(int maxOpenPreparedStatements) {
        this.maxOpenPreparedStatements = maxOpenPreparedStatements;
        this.maxOpenPreparedStatementsConfigured = true;
    }

    public void setMaxPoolPreparedStatementPerConnectionSize(int maxPoolPreparedStatementPerConnectionSize) {
        this.maxPoolPreparedStatementPerConnectionSize = maxPoolPreparedStatementPerConnectionSize;
        this.maxPoolPreparedStatementPerConnectionSizeConfigured = true;
    }

    public void setSharePreparedStatements(boolean sharePreparedStatements) {
        this.sharePreparedStatements = sharePreparedStatements;
        this.sharePreparedStatementsConfigured = true;
    }

    public void setAsyncInit(boolean asyncInit) {
        this.asyncInit = asyncInit;
        this.asyncInitConfigured = true;
    }

    public void setConnectionProperties(String connectionProperties) {
        this.connectionProperties = connectionProperties;
        this.connectionPropertiesConfigured = true;
    }

    public void setFilters(String filters) {
        this.filters = filters;
        this.filtersConfigured = true;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
        this.dbTypeConfigured = true;
    }

    @Data
    public static class FilterProperties {
        private StatProperties stat = new StatProperties();

        private WallProperties wall = new WallProperties();

        private Slf4jProperties slf4j = new Slf4jProperties();
    }

    @Data
    public static class StatProperties {
        private String dbType;

        private Boolean connectionStackTraceEnable;

        private Boolean logSlowSql;

        private Long slowSqlMillis;

        private Boolean mergeSql;

        private String slowSqlLogLevel;
    }

    @Data
    public static class WallProperties {
        private Boolean logViolation;

        private Boolean throwException;

        private String tenantColumn;

        private WallConfigProperties config = new WallConfigProperties();
    }

    @Data
    public static class WallConfigProperties {
        private String dir;

        private String tenantTablePattern;

        private String tenantColumn;

        private Boolean noneBaseStatementAllow;

        private Boolean callAllow;

        private Boolean selectAllow;

        private Boolean selectAllColumnAllow;

        private Boolean selectIntoAllow;

        private Boolean selectIntoOutfileAllow;

        private Boolean selectUnionCheck;

        private Boolean selectMinusCheck;

        private Boolean selectExceptCheck;

        private Boolean selectIntersectCheck;

        private Boolean selectWhereAlwayTrueCheck;

        private Boolean selectHavingAlwayTrueCheck;

        private Integer selectLimit;

        private Boolean insertAllow;

        private Boolean replaceAllow;

        private Boolean completeInsertValuesCheck;

        private Integer insertValuesCheckSize;

        private Boolean updateAllow;

        private Boolean updateWhereNoneCheck;

        private Boolean updateWhereAlwayTrueCheck;

        private Boolean deleteAllow;

        private Boolean deleteWhereNoneCheck;

        private Boolean deleteWhereAlwayTrueCheck;

        private Boolean createTableAllow;

        private Boolean alterTableAllow;

        private Boolean renameTableAllow;

        private Boolean multiStatementAllow;

        private Boolean truncateAllow;

        private Boolean dropTableAllow;

        private Boolean commentAllow;

        private Boolean strictSyntaxCheck;

        private Boolean schemaCheck;

        private Boolean tableCheck;

        private Boolean functionCheck;

        private Boolean variantCheck;

        private Boolean objectCheck;

        private Boolean mustParameterized;

        private Boolean metadataAllow;

        private Boolean showAllow;

        private Boolean describeAllow;

        private Boolean setAllow;

        private Boolean useAllow;

        private Boolean commitAllow;

        private Boolean rollbackAllow;

        private Boolean mergeAllow;

        private Boolean hintAllow;

        private Boolean lockTableAllow;

        private Boolean startTransactionAllow;

        private Boolean blockAllow;

        private Boolean wrapAllow;

        private Boolean doPrivilegedAllow;

        private Boolean caseConditionConstAllow;

        private Boolean conditionDoubleConstAllow;

        private Boolean conditionLikeTrueAllow;

        private Boolean conditionAndAlwayTrueAllow;

        private Boolean conditionAndAlwayFalseAllow;

        private Boolean conditionOpXorAllow;

        private Boolean conditionOpBitwiseAllow;

        private Boolean constArithmeticAllow;

        private Boolean limitZeroAllow;

        private Boolean intersectAllow;

        private Boolean minusAllow;
    }

    @Data
    public static class Slf4jProperties {
        private Boolean enable;

        private Boolean enabled;

        private String dataSourceLoggerName;

        private String connectionLoggerName;

        private String statementLoggerName;

        private String resultSetLoggerName;

        private Boolean dataSourceLogEnabled;

        private Boolean connectionLogEnabled;

        private Boolean connectionLogErrorEnabled;

        private Boolean connectionConnectBeforeLogEnabled;

        private Boolean connectionConnectAfterLogEnabled;

        private Boolean connectionCloseAfterLogEnabled;

        private Boolean connectionCommitAfterLogEnabled;

        private Boolean connectionRollbackAfterLogEnabled;

        private Boolean statementLogEnabled;

        private Boolean statementLogErrorEnabled;

        private Boolean statementCreateAfterLogEnabled;

        private Boolean statementPrepareAfterLogEnabled;

        private Boolean statementPrepareCallAfterLogEnabled;

        private Boolean statementExecuteAfterLogEnabled;

        private Boolean statementExecuteQueryAfterLogEnabled;

        private Boolean statementExecuteUpdateAfterLogEnabled;

        private Boolean statementExecuteBatchAfterLogEnabled;

        private Boolean statementExecutableSqlLogEnable;

        private Boolean statementCloseAfterLogEnabled;

        private Boolean statementParameterSetLogEnabled;

        private Boolean statementParameterClearLogEnable;

        private Boolean statementSqlPrettyFormat;

        private Boolean resultSetLogEnabled;

        private Boolean resultSetLogErrorEnabled;

        private Boolean resultSetOpenAfterLogEnabled;

        private Boolean resultSetNextAfterLogEnabled;

        private Boolean resultSetCloseAfterLogEnabled;
    }
}
