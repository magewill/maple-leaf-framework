package cn.maple.core.datasource.config;

import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.framework.util.GXCommonUtils;
import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

/**
 * Druid数据源工厂类
 * <p>
 * 该工厂类负责根据配置属性创建和初始化Druid数据源。
 * 提供了一个静态方法用于构建配置完善的DruidDataSource实例，
 * 支持数据库连接信息的解密功能，增强了安全性。
 * </p>
 * <p>
 * 安全特性：
 * - 使用GXCommonUtils.decodeConnectStr方法解密敏感连接信息
 * - 配置了SQL防火墙，防止SQL注入攻击
 * - 支持连接池参数的精细化配置，避免资源耗尽
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 创建数据源属性对象
 * GXDataSourceProperties properties = new GXDataSourceProperties();
 * properties.setUrl("jdbc:mysql://localhost:3306/test");
 * properties.setUsername("root");
 * properties.setPassword("encrypted_password"); // 加密的密码
 * properties.setInitialSize(5);
 * properties.setMaxActive(20);
 * 
 * // 使用工厂方法创建数据源
 * DruidDataSource dataSource = GXDynamicDataSourceFactory.buildDruidDataSource(properties);
 * 
 * // 使用数据源...
 * </pre>
 * </p>
 */
@Slf4j
public class GXDynamicDataSourceFactory {
    private GXDynamicDataSourceFactory() {
    }

    /**
     * 构建Druid数据源
     * <p>
     * 根据提供的属性配置创建并初始化一个DruidDataSource实例。
     * 该方法会解密连接信息，设置连接池参数，并初始化数据源。
     * </p>
     * <p>
     * 安全特性：
     * - 使用GXCommonUtils.decodeConnectStr解密敏感连接信息
     * - 添加wall过滤器防止SQL注入
     * - 设置合理的连接池参数，防止资源耗尽
     * </p>
     * <p>
     * 线程安全性：
     * - 该方法是线程安全的，不维护任何共享可变状态
     * - 返回的DruidDataSource实例本身是线程安全的
     * </p>
     *
     * @param properties 数据源配置属性
     * @return 配置完成的DruidDataSource实例
     */
    public static DruidDataSource buildDruidDataSource(GXDataSourceProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("数据源配置属性不能为空");
        }
        
        DruidDataSource druidDataSource = new DruidDataSource();
        // 设置数据库类型
        druidDataSource.setDbType(properties.getDbType());
        // 设置JDBC驱动
        druidDataSource.setDriverClassName(properties.getDriverClassName());
        // 解密并设置数据库连接URL
        druidDataSource.setUrl(GXCommonUtils.decodeConnectStr(properties.getUrl(), String.class));
        // 解密并设置用户名
        druidDataSource.setUsername(GXCommonUtils.decodeConnectStr(properties.getUsername(), String.class));
        // 解密并设置密码
        druidDataSource.setPassword(GXCommonUtils.decodeConnectStr(properties.getPassword(), String.class));
        
        // 设置连接池初始大小
        druidDataSource.setInitialSize(properties.getInitialSize());
        // 设置连接池最大活跃连接数
        druidDataSource.setMaxActive(properties.getMaxActive());
        // 设置连接池最小空闲连接数
        druidDataSource.setMinIdle(properties.getMinIdle());
        // 设置获取连接最大等待时间
        druidDataSource.setMaxWait(properties.getMaxWait());
        // 设置空闲连接检查间隔时间
        druidDataSource.setTimeBetweenEvictionRunsMillis(properties.getTimeBetweenEvictionRunsMillis());
        // 设置连接最小空闲时间
        druidDataSource.setMinEvictableIdleTimeMillis(properties.getMinEvictableIdleTimeMillis());
        // 设置连接最大空闲时间
        druidDataSource.setMaxEvictableIdleTimeMillis(properties.getMaxEvictableIdleTimeMillis());
        // 设置连接有效性检查SQL
        druidDataSource.setValidationQuery(properties.getValidationQuery());
        // 设置连接有效性检查超时时间
        druidDataSource.setValidationQueryTimeout(properties.getValidationQueryTimeout());
        // 设置是否在获取连接时检查有效性
        druidDataSource.setTestOnBorrow(properties.isTestOnBorrow());
        // 设置是否在归还连接时检查有效性
        druidDataSource.setTestOnReturn(properties.isTestOnReturn());
        // 设置是否在空闲时检查连接有效性
        druidDataSource.setTestWhileIdle(properties.isTestWhileIdle());
        // 设置是否缓存PreparedStatement
        druidDataSource.setPoolPreparedStatements(properties.isPoolPreparedStatements());
        // 设置缓存PreparedStatement的最大数量
        druidDataSource.setMaxOpenPreparedStatements(properties.getMaxOpenPreparedStatements());
        // 设置是否共享PreparedStatement
        druidDataSource.setSharePreparedStatements(properties.isSharePreparedStatements());
        
        try {
            // 设置过滤器，添加wall过滤器防止SQL注入
            druidDataSource.setFilters(properties.getFilters() + ",wall");
            // 初始化数据源
            druidDataSource.init();
        } catch (SQLException e) {
            log.error("初始化数据源失败: {}", e.getMessage(), e);
        }
        return druidDataSource;
    }
}