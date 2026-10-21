package cn.maple.core.datasource.it;

import cn.maple.core.datasource.aspect.GXDataSourceAspect;
import cn.maple.core.datasource.aspect.mybatis.GXMyBatisPlusDeleteAspect;
import cn.maple.core.datasource.aspect.mybatis.GXMyBatisPlusDeleteSoftAspect;
import cn.maple.core.datasource.aspect.mybatis.GXMyBatisPlusSaveBatchEntityAspect;
import cn.maple.core.datasource.aspect.mybatis.GXMyBatisPlusSaveEntityAspect;
import cn.maple.core.datasource.aspect.mybatis.GXMyBatisPlusUpdateEntityAspect;
import cn.maple.core.datasource.aspect.mybatis.GXMyBatisPlusUpdateFieldAspect;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerConfigResolver;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.config.GXDynamicDataSourceConfig;
import cn.maple.core.datasource.config.GXMyBatisAsyncListenerExecutorConfig;
import cn.maple.core.datasource.config.GXMyBatisAsyncListenerProperties;
import cn.maple.core.datasource.config.GXMyBatisListenerProperties;
import cn.maple.core.datasource.config.GXMyBatisPlusConfig;
import cn.maple.core.datasource.it.listener.OrderEventRecorder;
import cn.maple.core.datasource.it.listener.OrderItemEventRecorder;
import cn.maple.core.datasource.it.service.MultiDataSourceTestService;
import cn.maple.core.datasource.listener.GXMyBatisAsyncListener;
import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Minimal context for the multi-datasource integration test (framework + slave1).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableAsync
@MapperScan(basePackages = "cn.maple.core.datasource.it.mapper")
@PropertySource(value = "classpath:/dev/datasource-multi.yml", factory = GXYamlPropertySourceFactory.class)
@Import({
        GXDynamicDataSourceConfig.class,
        GXMyBatisPlusConfig.class,
        GXMyBatisAsyncListenerExecutorConfig.class,
        GXMyBatisListenerProperties.class,
        GXMyBatisAsyncListenerProperties.class,
        GXMyBatisListenerConfigResolver.class,
        GXMyBatisListenerEventPublisher.class,
        GXDataSourceAspect.class,
        GXMyBatisPlusSaveEntityAspect.class,
        GXMyBatisPlusUpdateEntityAspect.class,
        GXMyBatisPlusUpdateFieldAspect.class,
        GXMyBatisPlusDeleteAspect.class,
        GXMyBatisPlusDeleteSoftAspect.class,
        GXMyBatisPlusSaveBatchEntityAspect.class,
        GXMyBatisSyncListener.class,
        GXMyBatisAsyncListener.class,
        OrderEventRecorder.class,
        OrderItemEventRecorder.class,
        MultiDataSourceTestService.class
})
public class MultiDataSourceITApplication {
    @Bean
    public GXDynamicDataSourceProperties dynamicDataSourceProperties() {
        return new TestDynamicDataSourceProperties();
    }
}
