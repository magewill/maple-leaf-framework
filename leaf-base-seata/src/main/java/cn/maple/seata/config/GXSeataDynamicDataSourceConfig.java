package cn.maple.seata.config;

import cn.maple.core.datasource.config.GXDynamicDataSource;
import cn.maple.core.datasource.config.GXDynamicDataSourceConfig;
import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Integrates Seata dynamic datasource with MyBatis-Plus.
 * <p>
 * Seata support is applied at the datasource layer: {@link GXDynamicDataSourceConfig}
 * creates each physical datasource and wraps it with Seata's DataSourceProxy when
 * Seata is available. This class only ensures MyBatis-Plus uses the dynamic
 * routing datasource that already contains the proxied physical datasources.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(
        value = {DruidDataSource.class, GXDynamicDataSourceConfig.class},
        name = "org.apache.seata.rm.datasource.SeataDataSourceProxy"
)
public class GXSeataDynamicDataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(GXSeataDynamicDataSourceConfig.class);

    /**
     * 通过 {@link SqlSessionFactoryBeanCustomizer} 将动态数据源注入 MyBatis-Plus。
     *
     * <p>此方式是官方推荐的非侵入式定制手段：仅替换数据源，
     * 不干预 MyBatis-Plus 的分页插件、自动填充、乐观锁等其他自动配置逻辑，
     * 降低配置耦合度，也无需重新声明完整的 {@code SqlSessionFactory} Bean。</p>
     *
     * <p>Customizer 由 {@link MybatisPlusAutoConfiguration} 在创建
     * {@code SqlSessionFactory} 时统一收集并按序应用，
     * 本类在配置类加载时注册该 Bean，确保 Factory 初始化时可以应用。</p>
     *
     * <p>不要在这里再次把 {@link GXDynamicDataSource} 包装成 Seata 的
     * {@code DataSourceProxy}。{@link GXDynamicDataSourceConfig} 已经在创建物理数据源时完成代理，
     * 这里重复代理路由数据源会导致双层代理和资源注册异常风险。</p>
     *
     * @param dynamicDataSource 动态数据源
     * @return MyBatis-Plus SqlSessionFactory 定制器
     */
    @Bean
    public SqlSessionFactoryBeanCustomizer seataDynamicDataSourceCustomizer(GXDynamicDataSource dynamicDataSource) {
        return factoryBean -> {
            log.debug("[Seata] SqlSessionFactoryBeanCustomizer 正在将 GXDynamicDataSource 注入 MyBatis-Plus");
            factoryBean.setDataSource(dynamicDataSource);
        };
    }
}
