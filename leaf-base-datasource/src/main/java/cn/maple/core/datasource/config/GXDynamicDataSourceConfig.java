package cn.maple.core.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.properties.GXDataSourceProperties;
import cn.maple.core.datasource.properties.GXDynamicDataSourceProperties;
import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.alibaba.druid.pool.DruidDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Dynamic datasource configuration.
 */
@Configuration
@Slf4j
public class GXDynamicDataSourceConfig extends GXApplicationContextAware {
    private static final String DEFAULT_DATA_SOURCE_NAME = "framework";

    @Resource
    private GXDynamicDataSourceProperties dynamicDataSourceProperties;

    @Bean
    public GXDataSourceProperties dataSourceProperties() {
        Map<String, GXDataSourceProperties> dataSourcePropertiesMap = getConfiguredDataSources();
        String defaultDataSourceName = getDefaultDataSourceName();
        GXDataSourceProperties properties = dataSourcePropertiesMap.get(defaultDataSourceName);
        if (ObjectUtil.isNull(properties)) {
            throw new GXBusinessException("Default datasource not found: " + defaultDataSourceName);
        }
        return properties;
    }

    @Bean
    public GXDynamicDataSource dynamicDataSource() {
        DynamicDataSourceBuildResult buildResult = buildDynamicDataSources();
        Map<Object, Object> dynamicDataSources = buildResult.targetDataSources();
        String defaultDataSourceName = getDefaultDataSourceName();
        Object defaultDataSource = dynamicDataSources.get(defaultDataSourceName);
        if (!(defaultDataSource instanceof DataSource dataSource)) {
            closeCreatedDataSources(buildResult.managedDataSources());
            throw new GXBusinessException("Default datasource not found: " + defaultDataSourceName);
        }

        GXDynamicDataSource dynamicDataSource = new GXDynamicDataSource();
        dynamicDataSource.setTargetDataSources(dynamicDataSources);
        dynamicDataSource.setDefaultTargetDataSource(dataSource);
        dynamicDataSource.setManagedDataSources(buildResult.managedDataSources());
        return dynamicDataSource;
    }

    private String getDefaultDataSourceName() {
        ApplicationContext applicationContext = GXSpringContextUtils.getApplicationContext();
        if (Objects.isNull(applicationContext)) {
            return DEFAULT_DATA_SOURCE_NAME;
        }

        String[] defaultDataSourceName = new String[]{DEFAULT_DATA_SOURCE_NAME};
        applicationContext.getBeansWithAnnotation(SpringBootApplication.class).forEach((name, bean) -> {
            GXDataSource annotation = AnnotationUtils.findAnnotation(AopUtils.getTargetClass(bean), GXDataSource.class);
            if (Objects.nonNull(annotation) && CharSequenceUtil.isNotBlank(annotation.value())) {
                defaultDataSourceName[0] = annotation.value();
            }
        });
        return defaultDataSourceName[0];
    }

    private DataSource wrapSeataDataSource(DataSource dataSource) {
        try {
            Class<?> proxyClass = Class.forName("io.seata.rm.datasource.DataSourceProxy");
            Constructor<?> constructor = proxyClass.getConstructor(DataSource.class);
            Object proxy = constructor.newInstance(dataSource);
            log.info("Datasource wrapped with Seata DataSourceProxy");
            return (DataSource) proxy;
        } catch (ClassNotFoundException e) {
            log.debug("Seata DataSourceProxy not found, using original DataSource");
        } catch (ClassCastException | ReflectiveOperationException e) {
            throw new GXBusinessException("Failed to wrap Seata datasource", e);
        }
        return dataSource;
    }

    protected Map<Object, Object> getDynamicDataSources() {
        return buildDynamicDataSources().targetDataSources();
    }

    private DynamicDataSourceBuildResult buildDynamicDataSources() {
        Map<String, GXDataSourceProperties> dataSourcePropertiesMap = getConfiguredDataSources();
        Map<Object, Object> targetDataSources = new LinkedHashMap<>(dataSourcePropertiesMap.size());
        List<DruidDataSource> createdDruidDataSources = new ArrayList<>(dataSourcePropertiesMap.size());

        try {
            for (Map.Entry<String, GXDataSourceProperties> entry : dataSourcePropertiesMap.entrySet()) {
                String dataSourceName = entry.getKey();
                DruidDataSource druidDataSource = GXDynamicDataSourceFactory.buildDruidDataSource(entry.getValue());
                createdDruidDataSources.add(druidDataSource);
                try {
                    DataSource dataSource = wrapSeataDataSource(druidDataSource);
                    targetDataSources.put(dataSourceName, dataSource);
                    log.debug("Datasource created: {}", dataSourceName);
                } catch (RuntimeException e) {
                    throw new GXBusinessException("Failed to create datasource: " + dataSourceName, e);
                }
            }
            return new DynamicDataSourceBuildResult(targetDataSources, List.copyOf(createdDruidDataSources));
        } catch (RuntimeException e) {
            closeCreatedDataSources(createdDruidDataSources);
            if (e instanceof GXBusinessException businessException) {
                throw businessException;
            }
            throw new GXBusinessException("Failed to initialize dynamic datasources", e);
        }
    }

    private Map<String, GXDataSourceProperties> getConfiguredDataSources() {
        if (ObjectUtil.isNull(dynamicDataSourceProperties)) {
            throw new GXBusinessException("Dynamic datasource properties are missing");
        }
        Map<String, GXDataSourceProperties> dataSourcePropertiesMap = dynamicDataSourceProperties.getDatasource();
        if (ObjectUtil.isEmpty(dataSourcePropertiesMap)) {
            throw new GXBusinessException("At least one datasource must be configured");
        }
        dataSourcePropertiesMap.forEach((dataSourceName, properties) -> {
            if (CharSequenceUtil.isBlank(dataSourceName)) {
                throw new GXBusinessException("Datasource name must not be blank");
            }
            if (ObjectUtil.isNull(properties)) {
                throw new GXBusinessException("Datasource properties must not be null: " + dataSourceName);
            }
        });
        return dataSourcePropertiesMap;
    }

    private void closeCreatedDataSources(List<DruidDataSource> druidDataSources) {
        druidDataSources.forEach(this::closeDataSourceQuietly);
    }

    private void closeDataSourceQuietly(DruidDataSource dataSource) {
        try {
            dataSource.close();
        } catch (RuntimeException e) {
            log.warn("Failed to close datasource during initialization cleanup", e);
        }
    }

    private record DynamicDataSourceBuildResult(Map<Object, Object> targetDataSources, List<DruidDataSource> managedDataSources) {
    }
}
