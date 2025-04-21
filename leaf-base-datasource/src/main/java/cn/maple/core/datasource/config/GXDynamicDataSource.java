package cn.maple.core.datasource.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 多数据源路由实现
 * <p>
 * 该类继承自Spring的AbstractRoutingDataSource，实现了动态数据源的路由功能。
 * 通过重写determineCurrentLookupKey方法，根据当前线程上下文中的数据源标识，
 * 动态确定要使用的实际数据源。
 * </p>
 * <p>
 * 工作原理：
 * 1. 在应用启动时，GXDynamicDataSourceConfig会创建并配置多个数据源
 * 2. 将这些数据源注册到GXDynamicDataSource的targetDataSources映射中
 * 3. 在运行时，通过GXDynamicContextHolder设置当前线程的数据源标识
 * 4. 当需要获取连接时，determineCurrentLookupKey方法返回当前线程的数据源标识
 * 5. AbstractRoutingDataSource根据该标识从映射中查找并返回对应的数据源
 * </p>
 * <p>
 * 线程安全说明：
 * - 该类本身是线程安全的，因为它不维护任何可变状态
 * - 数据源的选择完全依赖于GXDynamicContextHolder提供的线程隔离机制
 * - 每个线程独立维护自己的数据源标识，不会相互干扰
 * </p>
 */
public class GXDynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return GXDynamicContextHolder.peek();
    }
}
