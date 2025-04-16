package cn.maple.core.datasource.config;

import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.framework.util.GXCommonUtils;
import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

/**
 * DruidDataSource
 */
@Slf4j
public class GXDynamicDataSourceFactory {
    private GXDynamicDataSourceFactory() {
    }

    public static DruidDataSource buildDruidDataSource(GXDataSourceProperties properties) {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setDbType(properties.getDbType());
        druidDataSource.setDriverClassName(properties.getDriverClassName());
        druidDataSource.setUrl(GXCommonUtils.decodeConnectStr(properties.getUrl(), String.class));
        druidDataSource.setUsername(GXCommonUtils.decodeConnectStr(properties.getUsername(), String.class));
        druidDataSource.setPassword(GXCommonUtils.decodeConnectStr(properties.getPassword(), String.class));
        druidDataSource.setInitialSize(properties.getInitialSize());
        druidDataSource.setMaxActive(properties.getMaxActive());
        druidDataSource.setMinIdle(properties.getMinIdle());
        druidDataSource.setMaxWait(properties.getMaxWait());
        druidDataSource.setTimeBetweenEvictionRunsMillis(properties.getTimeBetweenEvictionRunsMillis());
        druidDataSource.setMinEvictableIdleTimeMillis(properties.getMinEvictableIdleTimeMillis());
        druidDataSource.setMaxEvictableIdleTimeMillis(properties.getMaxEvictableIdleTimeMillis());
        druidDataSource.setValidationQuery(properties.getValidationQuery());
        druidDataSource.setValidationQueryTimeout(properties.getValidationQueryTimeout());
        druidDataSource.setTestOnBorrow(properties.isTestOnBorrow());
        druidDataSource.setTestOnReturn(properties.isTestOnReturn());
        druidDataSource.setPoolPreparedStatements(properties.isPoolPreparedStatements());
        druidDataSource.setMaxOpenPreparedStatements(properties.getMaxOpenPreparedStatements());
        druidDataSource.setSharePreparedStatements(properties.isSharePreparedStatements());
        try {
            druidDataSource.setFilters(properties.getFilters() + ",wall");
            druidDataSource.init();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return druidDataSource;
    }
}