package cn.maple.seata.config;

import cn.maple.core.datasource.config.GXDynamicDataSource;
import cn.maple.core.datasource.config.GXDynamicDataSourceConfig;
import cn.maple.core.datasource.handler.GXAutoFillMetaObjectHandler;
import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import jakarta.annotation.Resource;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seata分布式事务动态数据源配置类
 * <p>
 * 本配置类用于整合Seata分布式事务与动态数据源，实现在分布式事务环境下的多数据源切换功能。
 * 通过条件注解确保只有在存在Seata和动态数据源相关类时才会启用此配置。
 * </p>
 *
 * <p>
 * 使用示例：
 * 1. 确保项目中引入了Seata和动态数据源相关依赖
 * 2. 在应用配置中添加Seata和数据源相关配置
 * 3. 使用@GlobalTransactional注解标记需要分布式事务的方法
 * </p>
 *
 * <p>
 * 配置示例：
 * <pre>
 * # Seata配置
 * seata:
 *   enabled: true
 *   application-id: ${spring.application.name}
 *   tx-service-group: my_tx_group
 *   registry:
 *     type: nacos
 *     nacos:
 *       server-addr: 127.0.0.1:8848
 *       namespace: seata
 *       group: SEATA_GROUP
 *
 * # 数据源配置
 * spring:
 *   datasource:
 *     dynamic:
 *       primary: master
 *       datasource:
 *         master:
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           url: jdbc:mysql://localhost:3306/master_db
 *           username: root
 *           password: root
 *         slave:
 *           driver-class-name: com.mysql.cj.jdbc.Driver
 *           url: jdbc:mysql://localhost:3306/slave_db
 *           username: root
 *           password: root
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * 1. 使用Seata时，所有参与分布式事务的数据源都会被Seata代理
 * 2. 动态数据源切换功能依然可用，但所有操作都会被纳入到Seata的事务管理中
 * 3. 确保所有参与事务的数据库都已注册到Seata服务端
 * </p>
 *
 * @author maple
 * @see io.seata.rm.datasource.DataSourceProxy Seata数据源代理类
 * @see cn.maple.core.datasource.config.GXDynamicDataSource 动态数据源实现
 * @see cn.maple.core.datasource.config.GXDynamicDataSourceConfig 动态数据源配置
 */
@Configuration
@ConditionalOnClass(value = {DruidDataSource.class, GXDynamicDataSourceConfig.class}, name = {"io.seata.rm.datasource.DataSourceProxy"})
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
public class GXSeataDynamicDataSourceConfig {
    @Resource
    private GXAutoFillMetaObjectHandler autoFillMetaObjectHandler;

    @Resource
    private MybatisPlusProperties mybatisPlusProperties;

    @Resource
    private MybatisPlusInterceptor mybatisPlusInterceptor;

    /**
     * 创建与配置MyBatis-Plus的SqlSessionFactory
     * <p>
     * 该方法创建一个支持Seata分布式事务的SqlSessionFactory，使用MybatisSqlSessionFactoryBean替代标准的
     * SqlSessionFactoryBean以确保MyBatis-Plus的功能正常工作。同时，它配置了事务管理器、插件和全局配置。
     * </p>
     *
     * <p>
     * 主要功能：
     * 1. 设置动态数据源，支持多数据源切换
     * 2. 配置Spring管理的事务工厂，确保事务正常工作
     * 3. 应用MyBatis-Plus的配置属性和插件
     * 4. 配置Mapper XML文件的位置
     * 5. 设置字段自动填充处理器，用于创建时间、更新时间等字段的自动处理
     * </p>
     *
     * <p>
     * 注意：在Seata环境中，所有数据源操作都会被Seata代理，以支持分布式事务
     * </p>
     *
     * @param dynamicDataSource 动态数据源，支持在运行时切换数据源
     * @return 配置好的MybatisSqlSessionFactoryBean实例
     */
    @Bean
    public MybatisSqlSessionFactoryBean sqlSessionFactoryBean(GXDynamicDataSource dynamicDataSource) {
        // 这里用MybatisSqlSessionFactoryBean代替了SqlSessionFactoryBean, 否则MyBatisPlus不会生效
        MybatisSqlSessionFactoryBean mybatisSqlSessionFactoryBean = new MybatisSqlSessionFactoryBean();
        mybatisSqlSessionFactoryBean.setDataSource(dynamicDataSource);
        mybatisSqlSessionFactoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
        mybatisSqlSessionFactoryBean.setConfigurationProperties(mybatisPlusProperties.getConfigurationProperties());
        // MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 向代理数据源添加分页拦截器
        //interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        mybatisSqlSessionFactoryBean.setPlugins(mybatisPlusInterceptor);
        // 设置mapper文件的位置
        mybatisSqlSessionFactoryBean.setMapperLocations(mybatisPlusProperties.resolveMapperLocations());
        GlobalConfig globalConfig = mybatisPlusProperties.getGlobalConfig();
        // 设置字段自动填充
        globalConfig.setMetaObjectHandler(autoFillMetaObjectHandler);
        // 代理数据源添加id生成器
        /*globalConfig.setIdentifierGenerator(new DefaultIdentifierGenerator());*/
        mybatisSqlSessionFactoryBean.setGlobalConfig(globalConfig);
        return mybatisSqlSessionFactoryBean;
    }
}