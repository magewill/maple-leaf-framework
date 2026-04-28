package cn.maple.mongodb.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.mongodb.datasource.properties.GXMongoDataSourceProperties;
import cn.maple.mongodb.datasource.properties.GXMongoDynamicDataSourceProperties;
import cn.maple.mongodb.datasource.properties.local.GXLocalMongoDynamicDataSourceProperties;
import cn.maple.mongodb.datasource.properties.nacos.GXNacosMongoDynamicDataSourceProperties;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClients;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Log4j2
public class GXMongoBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware {
    private static final String MONGO_CLIENT_BEAN_NAME = "mongoClient";

    private static final String MONGO_DATABASE_FACTORY_BEAN_NAME = "mongoDatabaseFactory";

    private static final String MONGO_TEMPLATE_BEAN_NAME = "mongoTemplate";

    private static final String MONGO_OPERATIONS_BEAN_NAME = "mongoOperations";

    private Environment environment;

    private GXMongoDynamicDataSourceProperties cachedProperties;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        GXMongoDynamicDataSourceProperties properties = getMongoDynamicDataSourceProperties();
        checkMongoDynamicDataSourceProperties(properties);
        PrimaryMongoBeanNames primaryMongoBeanNames = new PrimaryMongoBeanNames();

        properties.getDatasource().forEach((key, dataSourceProperties) -> {
            try {
                validateDataSource(key, dataSourceProperties);

                MongoClientSettings mongoClientSettings = buildMongoClientSettings(dataSourceProperties);
                String database = resolveDatabase(dataSourceProperties);
                Boolean primary = dataSourceProperties.getPrimary();

                String clientBeanName = key + "MongoClient";
                BeanDefinitionBuilder mongoClientBeanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(MongoClients.class);
                mongoClientBeanDefinitionBuilder.setFactoryMethod("create");
                mongoClientBeanDefinitionBuilder.addConstructorArgValue(mongoClientSettings);
                mongoClientBeanDefinitionBuilder.setDestroyMethodName("close");
                registerBeanDefinition(beanDefinitionRegistry, clientBeanName, mongoClientBeanDefinitionBuilder);

                String factoryBeanName = key + "MongoDatabaseFactory";
                BeanDefinitionBuilder mongoDatabaseFactoryBeanDefinitionBuilder =
                        BeanDefinitionBuilder.rootBeanDefinition(SimpleMongoClientDatabaseFactory.class);
                mongoDatabaseFactoryBeanDefinitionBuilder.addConstructorArgValue(new RuntimeBeanReference(clientBeanName));
                mongoDatabaseFactoryBeanDefinitionBuilder.addConstructorArgValue(database);
                mongoDatabaseFactoryBeanDefinitionBuilder.setPrimary(Boolean.TRUE.equals(primary));
                registerBeanDefinition(beanDefinitionRegistry, factoryBeanName, mongoDatabaseFactoryBeanDefinitionBuilder);

                String templateBeanName = CharSequenceUtil.isBlank(dataSourceProperties.getBeanName())
                        ? key + "MongoTemplate"
                        : dataSourceProperties.getBeanName();
                BeanDefinitionBuilder mongoTemplateBeanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(MongoTemplate.class);
                mongoTemplateBeanDefinitionBuilder.addConstructorArgValue(new RuntimeBeanReference(factoryBeanName));
                mongoTemplateBeanDefinitionBuilder.setPrimary(Boolean.TRUE.equals(primary));
                registerBeanDefinition(beanDefinitionRegistry, templateBeanName, mongoTemplateBeanDefinitionBuilder);

                if (Boolean.TRUE.equals(primary)) {
                    primaryMongoBeanNames.set(clientBeanName, factoryBeanName, templateBeanName);
                }

                log.info("MongoDB datasource [{}] registered, database: {}", key, database);
            } catch (Exception e) {
                log.error("MongoDB datasource [{}] registration failed: {}", key, e.getMessage(), e);
                throw new GXBusinessException("MongoDB datasource registration failed: " + key + ", " + e.getMessage());
            }
        });

        registerPrimaryMongoAliases(
                beanDefinitionRegistry,
                primaryMongoBeanNames.clientBeanName,
                primaryMongoBeanNames.factoryBeanName,
                primaryMongoBeanNames.templateBeanName
        );
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (Objects.isNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext())) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    private MongoClientSettings buildMongoClientSettings(GXMongoDataSourceProperties dataSourceProperties) {
        String uri = decode(dataSourceProperties.getUri());
        ConnectionString connectionString = new ConnectionString(uri);
        MongoClientSettings.Builder builder = MongoClientSettings.builder().applyConnectionString(connectionString);

        String username = decode(dataSourceProperties.getUsername());
        char[] password = decodePassword(dataSourceProperties.getPassword());
        if (CharSequenceUtil.isNotBlank(username) && password != null) {
            String authenticationDatabase = decode(dataSourceProperties.getAuthenticationDatabase());
            if (CharSequenceUtil.isBlank(authenticationDatabase)) {
                String database = resolveDatabase(dataSourceProperties);
                authenticationDatabase = CharSequenceUtil.isBlank(database)
                        ? "admin"
                        : database;
            }
            builder.credential(MongoCredential.createCredential(username, authenticationDatabase, password));
        }

        return builder.build();
    }

    private void checkMongoDynamicDataSourceProperties(GXMongoDynamicDataSourceProperties properties) {
        AtomicInteger primaryBeanCnt = new AtomicInteger();
        properties.getDatasource().forEach((key, dataSourceProperties) -> {
            if (Boolean.TRUE.equals(dataSourceProperties.getPrimary())) {
                primaryBeanCnt.incrementAndGet();
                log.info("Detected primary MongoDB datasource: {}", key);
            }
        });

        if (primaryBeanCnt.get() > 1) {
            throw new GXBusinessException("Only one primary MongoTemplate can be configured");
        }
        if (primaryBeanCnt.get() == 0) {
            throw new GXBusinessException("One primary MongoTemplate must be configured");
        }
        log.info("MongoDB datasource configuration checked, datasource count: {}", properties.getDatasource().size());
    }

    private GXMongoDynamicDataSourceProperties getMongoDynamicDataSourceProperties() {
        if (cachedProperties != null) {
            return cachedProperties;
        }
        BindResult<Map<String, GXMongoDataSourceProperties>> bind = Binder.get(this.environment)
                .bind("mongodb.datasource", Bindable.mapOf(String.class, GXMongoDataSourceProperties.class));
        Map<String, GXMongoDataSourceProperties> datasource = bind.orElseGet(LinkedHashMap::new);
        if (datasource.isEmpty()) {
            throw new IllegalStateException("MongoDB datasource configuration is empty, please check mongodb.datasource");
        }

        GXMongoDynamicDataSourceProperties mongoDynamicDataSourceProperties;
        if (isNacosAvailable()) {
            mongoDynamicDataSourceProperties = new GXNacosMongoDynamicDataSourceProperties();
            log.info("Detected Nacos configuration center, using Nacos MongoDB datasource properties");
        } else {
            mongoDynamicDataSourceProperties = new GXLocalMongoDynamicDataSourceProperties();
            log.info("Nacos configuration center not detected, using local MongoDB datasource properties");
        }
        mongoDynamicDataSourceProperties.setDatasource(datasource);
        cachedProperties = mongoDynamicDataSourceProperties;
        return cachedProperties;
    }

    private boolean isNacosAvailable() {
        try {
            Class.forName("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void validateDataSource(String key, GXMongoDataSourceProperties dataSourceProperties) {
        if (dataSourceProperties == null) {
            throw new GXBusinessException("MongoDB datasource [" + key + "] configuration must not be null");
        }
        if (CharSequenceUtil.isBlank(dataSourceProperties.getUri())) {
            throw new GXBusinessException("MongoDB datasource [" + key + "] uri must not be blank");
        }
        if (CharSequenceUtil.isBlank(resolveDatabase(dataSourceProperties))) {
            throw new GXBusinessException("MongoDB datasource [" + key + "] database must not be blank");
        }
    }

    private String resolveDatabase(GXMongoDataSourceProperties dataSourceProperties) {
        String database = dataSourceProperties.getDatabase();
        if (CharSequenceUtil.isNotBlank(database)) {
            return database;
        }
        String uri = decode(dataSourceProperties.getUri());
        if (CharSequenceUtil.isBlank(uri)) {
            return null;
        }
        return new ConnectionString(uri).getDatabase();
    }

    private String decode(String value) {
        if (CharSequenceUtil.isBlank(value)) {
            return value;
        }
        return GXCommonUtils.decodeConnectStr(value, String.class);
    }

    private char[] decodePassword(char[] password) {
        if (password == null || password.length == 0) {
            return null;
        }
        return decode(String.valueOf(password)).toCharArray();
    }

    private void registerBeanDefinition(BeanDefinitionRegistry registry, String beanName, BeanDefinitionBuilder builder) {
        if (registry.containsBeanDefinition(beanName)) {
            registry.removeBeanDefinition(beanName);
            log.info("MongoDB bean [{}] already exists, remove it and use GX definition", beanName);
        }
        if (registry.isAlias(beanName)) {
            registry.removeAlias(beanName);
        }
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    private void registerPrimaryMongoAliases(BeanDefinitionRegistry registry,
                                             String clientBeanName,
                                             String factoryBeanName,
                                             String templateBeanName) {
        registerAliasReplacingExistingBeanDefinition(registry, clientBeanName, MONGO_CLIENT_BEAN_NAME);
        registerAliasReplacingExistingBeanDefinition(registry, factoryBeanName, MONGO_DATABASE_FACTORY_BEAN_NAME);
        registerAliasReplacingExistingBeanDefinition(registry, templateBeanName, MONGO_TEMPLATE_BEAN_NAME);
        registerAliasReplacingExistingBeanDefinition(registry, templateBeanName, MONGO_OPERATIONS_BEAN_NAME);
    }

    private void registerAliasReplacingExistingBeanDefinition(BeanDefinitionRegistry registry, String beanName, String alias) {
        if (Objects.equals(beanName, alias)) {
            return;
        }
        removeBeanNameIfPresent(registry, alias);
        registry.registerAlias(beanName, alias);
    }

    private void removeBeanNameIfPresent(BeanDefinitionRegistry registry, String beanName) {
        if (registry.containsBeanDefinition(beanName)) {
            registry.removeBeanDefinition(beanName);
            log.info("Removed Spring Boot MongoDB bean definition [{}], using GX primary datasource", beanName);
        }
        if (registry.isAlias(beanName)) {
            registry.removeAlias(beanName);
        }
    }

    private static class PrimaryMongoBeanNames {
        private String clientBeanName;

        private String factoryBeanName;

        private String templateBeanName;

        private void set(String clientBeanName, String factoryBeanName, String templateBeanName) {
            this.clientBeanName = clientBeanName;
            this.factoryBeanName = factoryBeanName;
            this.templateBeanName = templateBeanName;
        }
    }
}
