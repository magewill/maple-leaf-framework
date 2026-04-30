package cn.maple.core.datasource.properties;

import lombok.Data;

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

    private boolean sharePreparedStatements = false;

    private String filters = "stat,wall,slf4j";

    private String dbType = "mysql";
}