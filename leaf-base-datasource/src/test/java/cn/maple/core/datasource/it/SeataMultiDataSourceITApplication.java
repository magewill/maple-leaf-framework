package cn.maple.core.datasource.it;

import cn.maple.core.datasource.aspect.GXDataSourceAspect;
import cn.maple.core.datasource.config.GXDynamicDataSourceConfig;
import cn.maple.core.datasource.config.GXMyBatisAsyncListenerExecutorConfig;
import cn.maple.core.datasource.config.GXMyBatisAsyncListenerProperties;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.config.GXMyBatisPlusConfig;
import cn.maple.core.datasource.it.service.MultiDataSourceTestService;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

/**
 * Context for the Seata global transaction test.
 *
 * <p>Unlike the other integration contexts, Seata auto-configuration is <b>not</b> excluded, so the
 * {@code GlobalTransactionScanner} is active and {@code @GlobalTransactional} takes effect. The
 * automatic datasource proxy is disabled because the framework already wraps each target datasource
 * with a Seata {@code DataSourceProxy}.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackages = "cn.maple.core.datasource.it.mapper")
@PropertySource(value = "classpath:/dev/datasource-multi.yml", factory = GXYamlPropertySourceFactory.class)
@Import({
        GXDynamicDataSourceConfig.class,
        GXMyBatisPlusConfig.class,
        GXMyBatisAsyncListenerExecutorConfig.class,
        GXMyBatisListenerProperties.class,
        GXMyBatisAsyncListenerProperties.class,
        GXDataSourceAspect.class,
        MultiDataSourceTestService.class
})
public class SeataMultiDataSourceITApplication {
    @Bean
    public GXDynamicDataSourceProperties dynamicDataSourceProperties() {
        return new TestDynamicDataSourceProperties();
    }
}
