package cn.maple.core.datasource.it;

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
import cn.maple.core.datasource.it.listener.AsyncUserChangeRecorder;
import cn.maple.core.datasource.it.listener.OrderEventRecorder;
import cn.maple.core.datasource.it.listener.OrderItemEventRecorder;
import cn.maple.core.datasource.it.listener.SyncOrderEventRecorder;
import cn.maple.core.datasource.it.listener.SyncOrderItemEventRecorder;
import cn.maple.core.datasource.it.listener.UserChangeRecorder;
import cn.maple.core.datasource.listener.GXMyBatisAsyncListener;
import cn.maple.core.datasource.listener.GXMyBatisSyncListener;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import cn.maple.core.framework.factory.GXYamlPropertySourceFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Minimal application context for the MyBatis listener integration test.
 *
 * <p>Components are imported explicitly so that test classes under the same package hierarchy and
 * unrelated middleware modules on the test classpath are never component-scanned.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableAsync
@MapperScan(basePackages = "cn.maple.core.datasource.it.mapper")
@PropertySource(value = "classpath:/dev/datasource.yml", factory = GXYamlPropertySourceFactory.class)
@Import({
        GXDynamicDataSourceConfig.class,
        GXMyBatisPlusConfig.class,
        GXMyBatisAsyncListenerExecutorConfig.class,
        GXMyBatisListenerProperties.class,
        GXMyBatisAsyncListenerProperties.class,
        GXMyBatisListenerConfigResolver.class,
        GXMyBatisListenerEventPublisher.class,
        GXMyBatisPlusSaveEntityAspect.class,
        GXMyBatisPlusUpdateEntityAspect.class,
        GXMyBatisPlusUpdateFieldAspect.class,
        GXMyBatisPlusDeleteAspect.class,
        GXMyBatisPlusDeleteSoftAspect.class,
        GXMyBatisPlusSaveBatchEntityAspect.class,
        GXMyBatisSyncListener.class,
        GXMyBatisAsyncListener.class,
        UserChangeRecorder.class,
        AsyncUserChangeRecorder.class,
        OrderEventRecorder.class,
        OrderItemEventRecorder.class,
        SyncOrderEventRecorder.class,
        SyncOrderItemEventRecorder.class
})
public class MyBatisListenerITApplication {
    @Bean
    public GXDynamicDataSourceProperties dynamicDataSourceProperties() {
        return new TestDynamicDataSourceProperties();
    }
}
