package cn.maple.core.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.framework.util.GXCommonUtils;
import com.alibaba.druid.pool.DruidDataSource;

import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Druid datasource factory.
 */
public class GXDynamicDataSourceFactory {
    private GXDynamicDataSourceFactory() {
    }

    public static DruidDataSource buildDruidDataSource(GXDataSourceProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("Datasource properties must not be null");
        }

        String url = GXCommonUtils.decodeConnectStr(properties.getUrl(), String.class);
        if (CharSequenceUtil.isBlank(url)) {
            throw new IllegalArgumentException("Datasource URL must not be blank");
        }

        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setDbType(properties.getDbType());
        druidDataSource.setDriverClassName(properties.getDriverClassName());
        druidDataSource.setUrl(url);
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
        druidDataSource.setTestWhileIdle(properties.isTestWhileIdle());
        druidDataSource.setPoolPreparedStatements(properties.isPoolPreparedStatements());
        druidDataSource.setMaxOpenPreparedStatements(properties.getMaxOpenPreparedStatements());
        druidDataSource.setSharePreparedStatements(properties.isSharePreparedStatements());

        try {
            druidDataSource.setFilters(resolveFilters(properties.getFilters()));
            druidDataSource.init();
            return druidDataSource;
        } catch (SQLException | RuntimeException e) {
            druidDataSource.close();
            throw new IllegalStateException("Failed to initialize datasource: " + e.getMessage(), e);
        }
    }

    private static String resolveFilters(String filters) {
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
        return String.join(",", filterSet);
    }
}
