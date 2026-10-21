package cn.maple.seata.config;

import cn.maple.core.datasource.config.GXDynamicDataSource;
import cn.maple.core.datasource.config.GXDynamicDataSourceConfig;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.seata.properties.GXLocalSeataProperties;
import cn.maple.seata.properties.GXNacosSeataProperties;
import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.apache.seata.rm.datasource.SeataDataSourceProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Integrates Seata dynamic datasource with MyBatis-Plus.
 * <p>
 * Seata support is applied at the datasource layer: {@link GXDynamicDataSourceConfig}
 * creates each physical datasource and wraps it with Seata's DataSourceProxy when
 * Seata is available. This class ensures MyBatis-Plus uses the dynamic routing
 * datasource and verifies at startup that every target datasource is proxied.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(
        value = {DruidDataSource.class, GXDynamicDataSourceConfig.class},
        name = GXSeataDynamicDataSourceConfig.SEATA_DATASOURCE_PROXY_INTERFACE
)
public class GXSeataDynamicDataSourceConfig {
    /**
     * Seata 2.6.0 新命名空间下的代理接口。装配判定与实例化必须使用同一命名空间，禁止回退旧兼容类。
     */
    static final String SEATA_DATASOURCE_PROXY_INTERFACE = "org.apache.seata.rm.datasource.SeataDataSourceProxy";

    static final String STRICT_SWITCH_KEY = "maple.framework.enable.seata-strict";

    private static final Logger log = LoggerFactory.getLogger(GXSeataDynamicDataSourceConfig.class);

    @Value("${maple.framework.enable.seata-strict:false}")
    private boolean strictProxyRequired;

    /**
     * Injects the dynamic datasource into MyBatis-Plus.
     *
     * <p>不要在这里再次把 {@link GXDynamicDataSource} 包装成 Seata 的
     * {@code DataSourceProxy}。{@link GXDynamicDataSourceConfig} 已经在创建物理数据源时完成代理，
     * 这里重复代理路由数据源会导致双层代理和资源注册异常风险。</p>
     */
    @Bean
    public SqlSessionFactoryBeanCustomizer seataDynamicDataSourceCustomizer(
            ObjectProvider<GXDynamicDataSource> dynamicDataSourceProvider) {
        return factoryBean -> {
            GXDynamicDataSource dynamicDataSource = dynamicDataSourceProvider.getIfAvailable();
            if (dynamicDataSource == null) {
                log.warn("[Seata] GXDynamicDataSource is absent, skip MyBatis-Plus datasource customization");
                return;
            }
            log.debug("[Seata] Inject GXDynamicDataSource into MyBatis-Plus SqlSessionFactoryBean");
            factoryBean.setDataSource(dynamicDataSource);
        };
    }

    /**
     * 启动期校验每个物理数据源是否已被 Seata 代理。未启用 Seata 时本类不装配，因此这里只处理
     * "已装配但目标源未代理"的情况：默认 WARN，严格模式下启动失败。
     */
    @Bean
    public SmartInitializingSingleton seataDataSourceProxyVerifier(
            ObjectProvider<GXDynamicDataSource> dynamicDataSourceProvider,
            ObjectProvider<GXLocalSeataProperties> localPropertiesProvider,
            ObjectProvider<GXNacosSeataProperties> nacosPropertiesProvider) {
        return () -> verifyDataSourceProxies(dynamicDataSourceProvider, localPropertiesProvider, nacosPropertiesProvider);
    }

    private void verifyDataSourceProxies(ObjectProvider<GXDynamicDataSource> dynamicDataSourceProvider,
                                         ObjectProvider<GXLocalSeataProperties> localPropertiesProvider,
                                         ObjectProvider<GXNacosSeataProperties> nacosPropertiesProvider) {
        GXDynamicDataSource dynamicDataSource = dynamicDataSourceProvider.getIfAvailable();
        if (dynamicDataSource == null) {
            log.warn("[Seata] GXDynamicDataSource is absent, skip datasource proxy verification");
            return;
        }

        String configSource = resolveConfigSource(localPropertiesProvider, nacosPropertiesProvider);
        Map<String, DataSource> resolvedDataSources = new LinkedHashMap<>();
        dynamicDataSource.getResolvedDataSources().forEach((key, dataSource) -> resolvedDataSources.put(String.valueOf(key), dataSource));

        List<String> proxied = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        resolvedDataSources.forEach((name, dataSource) -> {
            if (dataSource instanceof SeataDataSourceProxy) {
                proxied.add(name);
            } else {
                missing.add(name);
            }
        });

        log.info("[Seata] enabled=true, config-source={}, datasources={}, proxied={}",
                configSource, resolvedDataSources.keySet(), proxied);
        missing.forEach(name -> log.warn(
                "[Seata] datasource '{}' is NOT wrapped by Seata; branches on it will not join global transactions", name));

        if (!missing.isEmpty() && strictProxyRequired) {
            throw new GXBusinessException("Seata datasource proxy is required but missing for " + missing
                    + "; set " + STRICT_SWITCH_KEY + "=false to degrade to warning, missing contract: "
                    + SEATA_DATASOURCE_PROXY_INTERFACE);
        }
    }

    private String resolveConfigSource(ObjectProvider<GXLocalSeataProperties> localPropertiesProvider,
                                       ObjectProvider<GXNacosSeataProperties> nacosPropertiesProvider) {
        boolean nacosActive = nacosPropertiesProvider.getIfAvailable() != null;
        boolean localActive = localPropertiesProvider.getIfAvailable() != null;
        if (nacosActive && localActive) {
            log.warn("[Seata] both local and nacos seata configuration are active, using nacos");
            return "nacos";
        }
        if (nacosActive) {
            return "nacos";
        }
        if (localActive) {
            return "local";
        }
        return "none";
    }
}
