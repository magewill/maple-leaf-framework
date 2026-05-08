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
     * Injects the dynamic datasource into MyBatis-Plus.
     *
     * <p>不要在这里再次把 {@link GXDynamicDataSource} 包装成 Seata 的
     * {@code DataSourceProxy}。{@link GXDynamicDataSourceConfig} 已经在创建物理数据源时完成代理，
     * 这里重复代理路由数据源会导致双层代理和资源注册异常风险。</p>
     */
    @Bean
    public SqlSessionFactoryBeanCustomizer seataDynamicDataSourceCustomizer(GXDynamicDataSource dynamicDataSource) {
        return factoryBean -> {
            log.debug("[Seata] Inject GXDynamicDataSource into MyBatis-Plus SqlSessionFactoryBean");
            factoryBean.setDataSource(dynamicDataSource);
        };
    }
}
