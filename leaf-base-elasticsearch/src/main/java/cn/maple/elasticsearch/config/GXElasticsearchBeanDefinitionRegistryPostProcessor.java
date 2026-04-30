package cn.maple.elasticsearch.config;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.elasticsearch.properties.GXElasticsearchProperties;
import cn.maple.elasticsearch.properties.GXElasticsearchSourceProperties;
import cn.maple.elasticsearch.properties.local.GXLocalElasticsearchProperties;
import cn.maple.elasticsearch.properties.nacos.GXNacosElasticsearchProperties;
import cn.maple.elasticsearch.support.GXDynamicElasticsearchOperations;
import cn.maple.elasticsearch.support.GXElasticsearchRepositoryFactoryBeanPostProcessor;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.log4j.Log4j2;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.rest5_client.Rest5Clients;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.support.HttpHeaders;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
@Log4j2
public class GXElasticsearchBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware, Ordered {
    private static final String ELASTICSEARCH_CLIENT_BEAN_NAME = "elasticsearchClient";

    private static final String ELASTICSEARCH_MAPPING_CONTEXT_BEAN_NAME = "elasticsearchMappingContext";

    private static final String ELASTICSEARCH_ENTITY_MAPPER_BEAN_NAME = "elasticsearchEntityMapper";

    private static final String ELASTICSEARCH_TEMPLATE_BEAN_NAME = "elasticsearchTemplate";

    private static final String ELASTICSEARCH_OPERATIONS_BEAN_NAME = "elasticsearchOperations";

    private static final String PRIMARY_ELASTICSEARCH_TEMPLATE_BEAN_NAME = "primaryElasticsearchTemplate";

    private static final String ELASTICSEARCH_REPOSITORY_FACTORY_BEAN_POST_PROCESSOR_BEAN_NAME = "elasticsearchRepositoryFactoryBeanPostProcessor";

    private Environment environment;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        try {
            Map<String, GXElasticsearchProperties> datasourceMap = getSourceElasticsearchProperties().getDatasource();

            checkElasticsearchDataSourceProperties(datasourceMap);

            datasourceMap.forEach((key, dataSourceProperties) -> {
                try {
                    String clientBeanName = key + "ElasticsearchClient";
                    BeanDefinitionBuilder clientBeanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(ElasticsearchClient.class, () -> buildElasticsearchClient(dataSourceProperties));
                    clientBeanDefinitionBuilder.setPrimary(dataSourceProperties.isPrimary());
                    beanDefinitionRegistry.registerBeanDefinition(clientBeanName, clientBeanDefinitionBuilder.getBeanDefinition());

                    String mappingContextBeanName = key + "ElasticsearchMappingContext";
                    BeanDefinitionBuilder mappingContextBuilder = BeanDefinitionBuilder.genericBeanDefinition(SimpleElasticsearchMappingContext.class);
                    mappingContextBuilder.setInitMethodName("initialize");
                    mappingContextBuilder.setPrimary(dataSourceProperties.isPrimary());
                    beanDefinitionRegistry.registerBeanDefinition(mappingContextBeanName, mappingContextBuilder.getBeanDefinition());

                    String converterBeanName = key + "MappingElasticsearchConverter";
                    BeanDefinitionBuilder converterBuilder = BeanDefinitionBuilder.genericBeanDefinition(MappingElasticsearchConverter.class);
                    converterBuilder.addConstructorArgReference(mappingContextBeanName);
                    converterBuilder.setInitMethodName("afterPropertiesSet");
                    converterBuilder.setPrimary(dataSourceProperties.isPrimary());
                    beanDefinitionRegistry.registerBeanDefinition(converterBeanName, converterBuilder.getBeanDefinition());

                    BeanDefinitionBuilder elasticsearchTemplateBeanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(ElasticsearchTemplate.class);
                    elasticsearchTemplateBeanDefinitionBuilder.addConstructorArgReference(clientBeanName);
                    elasticsearchTemplateBeanDefinitionBuilder.addConstructorArgReference(converterBeanName);

                    elasticsearchTemplateBeanDefinitionBuilder.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME);

                    boolean primary = dataSourceProperties.isPrimary();
                    String beanName = key + "ElasticsearchTemplate";

                    if (primary) {
                        // settingElasticsearchPropertiesBeanProperties(dataSourceProperties);
                        elasticsearchTemplateBeanDefinitionBuilder.setPrimary(true);
                        log.info("主Elasticsearch数据源[{}]已设置为primary", key);
                    }

                    beanDefinitionRegistry.registerBeanDefinition(beanName, elasticsearchTemplateBeanDefinitionBuilder.getBeanDefinition());

                    if (primary) {
                        registerPrimaryElasticsearchAliases(beanDefinitionRegistry, clientBeanName, mappingContextBeanName, converterBeanName, beanName);
                    }

                    log.info("Elasticsearch数据源[{}]注册成功", key);

                } catch (Exception e) {
                    log.error("Elasticsearch数据源[{}]注册失败: {}", key, e.getMessage());
                    throw new GXBusinessException("Elasticsearch数据源注册失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Elasticsearch多数据源配置处理失败: {}", e.getMessage());
            throw new GXBusinessException("Elasticsearch多数据源配置处理失败: " + e.getMessage());
        }
    }

    private void registerPrimaryElasticsearchAliases(BeanDefinitionRegistry beanDefinitionRegistry,
                                                     String clientBeanName,
                                                     String mappingContextBeanName,
                                                     String converterBeanName,
                                                     String templateBeanName) {
        registerAliasReplacingExistingBeanDefinition(beanDefinitionRegistry, clientBeanName, ELASTICSEARCH_CLIENT_BEAN_NAME);
        registerAliasReplacingExistingBeanDefinition(beanDefinitionRegistry, mappingContextBeanName, ELASTICSEARCH_MAPPING_CONTEXT_BEAN_NAME);
        registerAliasReplacingExistingBeanDefinition(beanDefinitionRegistry, converterBeanName, ELASTICSEARCH_ENTITY_MAPPER_BEAN_NAME);
        registerAliasReplacingExistingBeanDefinition(beanDefinitionRegistry, templateBeanName, ELASTICSEARCH_TEMPLATE_BEAN_NAME);
        registerAliasReplacingExistingBeanDefinition(beanDefinitionRegistry, templateBeanName, PRIMARY_ELASTICSEARCH_TEMPLATE_BEAN_NAME);
        registerDynamicElasticsearchOperations(beanDefinitionRegistry, templateBeanName);
        registerRepositoryFactoryBeanPostProcessor(beanDefinitionRegistry, templateBeanName);
    }

    private void registerDynamicElasticsearchOperations(BeanDefinitionRegistry beanDefinitionRegistry, String defaultTemplateBeanName) {
        removeBeanNameIfPresent(beanDefinitionRegistry, ELASTICSEARCH_OPERATIONS_BEAN_NAME);
        BeanDefinitionBuilder operationsBuilder = BeanDefinitionBuilder.genericBeanDefinition(
                ElasticsearchOperations.class,
                () -> GXDynamicElasticsearchOperations.create(defaultTemplateBeanName)
        );
        beanDefinitionRegistry.registerBeanDefinition(ELASTICSEARCH_OPERATIONS_BEAN_NAME, operationsBuilder.getBeanDefinition());
    }

    private void registerRepositoryFactoryBeanPostProcessor(BeanDefinitionRegistry beanDefinitionRegistry, String defaultTemplateBeanName) {
        removeBeanNameIfPresent(beanDefinitionRegistry, ELASTICSEARCH_REPOSITORY_FACTORY_BEAN_POST_PROCESSOR_BEAN_NAME);
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(GXElasticsearchRepositoryFactoryBeanPostProcessor.class);
        builder.addConstructorArgValue(defaultTemplateBeanName);
        beanDefinitionRegistry.registerBeanDefinition(ELASTICSEARCH_REPOSITORY_FACTORY_BEAN_POST_PROCESSOR_BEAN_NAME, builder.getBeanDefinition());
    }

    private void registerAliasReplacingExistingBeanDefinition(BeanDefinitionRegistry beanDefinitionRegistry,
                                                              String beanName,
                                                              String alias) {
        if (CharSequenceUtil.equals(beanName, alias)) {
            return;
        }
        removeBeanNameIfPresent(beanDefinitionRegistry, alias);
        beanDefinitionRegistry.registerAlias(beanName, alias);
    }

    private void removeBeanNameIfPresent(BeanDefinitionRegistry beanDefinitionRegistry, String beanName) {
        if (beanDefinitionRegistry.containsBeanDefinition(beanName)) {
            beanDefinitionRegistry.removeBeanDefinition(beanName);
            log.info("已移除Spring Boot自动装配的Elasticsearch Bean定义[{}]，使用框架自定义主数据源", beanName);
        }
        if (beanDefinitionRegistry.isAlias(beanName)) {
            beanDefinitionRegistry.removeAlias(beanName);
        }
    }

    @Deprecated
    private void settingElasticsearchPropertiesBeanProperties(GXElasticsearchProperties dataSourceProperties) {
        log.warn("settingElasticsearchPropertiesBeanProperties方法在Spring Boot 4中已不再使用");
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

    private GXElasticsearchSourceProperties getSourceElasticsearchProperties() {
        try {
            BindResult<Map<String, GXElasticsearchProperties>> bind = Binder.get(this.environment)
                    .bind("elasticsearch.datasource", Bindable.mapOf(String.class, GXElasticsearchProperties.class));
            if (!bind.isBound() || bind.get() == null) {
                throw new IllegalStateException("未找到有效的Elasticsearch数据源配置，请检查配置文件中是否包含elasticsearch.datasource节点");
            }

            Map<String, GXElasticsearchProperties> datasource = bind.get();

            if (datasource == null || datasource.isEmpty()) {
                throw new IllegalStateException("Elasticsearch数据源配置为空，请检查配置格式是否正确");
            }

            log.info("成功加载{}个Elasticsearch数据源配置", datasource.size());

            try {
                Class.forName("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties");
                if (!isNacosConfigEnabled()) {
                    throw new ClassNotFoundException("Nacos config is not enabled");
                }
                log.info("检测到Nacos配置中心，使用Nacos的Elasticsearch配置");
                GXNacosElasticsearchProperties elasticsearchSourceProperties = new GXNacosElasticsearchProperties();
                elasticsearchSourceProperties.setDatasource(datasource);
                return elasticsearchSourceProperties;
            } catch (ClassNotFoundException e) {
                log.info("未检测到Nacos配置中心，使用本地Elasticsearch配置");
                GXLocalElasticsearchProperties elasticsearchSourceProperties = new GXLocalElasticsearchProperties();
                elasticsearchSourceProperties.setDatasource(datasource);
                return elasticsearchSourceProperties;
            }
        } catch (Exception e) {
            log.error("解析Elasticsearch数据源配置失败: {}", e.getMessage());
            throw new IllegalStateException("解析Elasticsearch数据源配置失败: " + e.getMessage(), e);
        }
    }

    private boolean isNacosConfigEnabled() {
        String serverAddr = environment.getProperty("spring.cloud.nacos.config.server-addr");
        if (CharSequenceUtil.isBlank(serverAddr)) {
            serverAddr = environment.getProperty("nacos.config.server-addr");
        }
        return CharSequenceUtil.isNotBlank(serverAddr);
    }

    private ElasticsearchClient buildElasticsearchClient(GXElasticsearchProperties elasticsearchSourceProperties) {
        Assert.notNull(elasticsearchSourceProperties, "Elasticsearch数据源配置不能为null");

        try {
            ClientConfiguration.TerminalClientConfigurationBuilder configurationBuilder =
                    buildElasticsearchConfigurationBuilder(elasticsearchSourceProperties);

            return ElasticsearchClients.createImperative(configurationBuilder.build());
        } catch (Exception e) {
            log.error("创建ElasticsearchClient失败: {}", e.getMessage());
            throw new IllegalStateException("创建ElasticsearchClient失败: " + e.getMessage(), e);
        }
    }

    private ClientConfiguration.TerminalClientConfigurationBuilder buildElasticsearchConfigurationBuilder(GXElasticsearchProperties elasticsearchSourceProperties) {
        Assert.notNull(elasticsearchSourceProperties, "Elasticsearch数据源配置不能为null");

        try {
            String username = elasticsearchSourceProperties.getUsername();
            String password = elasticsearchSourceProperties.getPassword();

            List<String> uris = elasticsearchSourceProperties.getUris();
            if (uris == null || uris.isEmpty()) {
                throw new IllegalArgumentException("Elasticsearch URI列表不能为空");
            }
            boolean useSsl = uris.stream().anyMatch(uri -> CharSequenceUtil.startWithIgnoreCase(uri, "https://"));
            String[] uriArray = uris.stream()
                    .filter(CharSequenceUtil::isNotBlank)
                    .map(uri -> CharSequenceUtil.removePrefixIgnoreCase(uri, "http://"))
                    .map(uri -> CharSequenceUtil.removePrefixIgnoreCase(uri, "https://"))
                    .map(uri -> CharSequenceUtil.subBefore(uri, "/", false))
                    .toArray(String[]::new);
            if (uriArray.length == 0) {
                throw new IllegalArgumentException("Elasticsearch URI列表不能为空");
            }

            ClientConfiguration.MaybeSecureClientConfigurationBuilder connectedBuilder =
                    ClientConfiguration.builder().connectedTo(uriArray);
            ClientConfiguration.TerminalClientConfigurationBuilder configurationBuilder = useSsl ? connectedBuilder.usingSsl() : connectedBuilder;

            if (CharSequenceUtil.isAllNotEmpty(username, password)) {
                configurationBuilder.withBasicAuth(username, password);
                log.debug("已配置Elasticsearch认证信息");
            } else {
                log.warn("未配置Elasticsearch认证信息，将使用匿名访问");
            }

            if (CharSequenceUtil.isNotEmpty(elasticsearchSourceProperties.getPathPrefix())) {
                configurationBuilder.withPathPrefix(elasticsearchSourceProperties.getPathPrefix());
                log.debug("已配置Elasticsearch路径前缀: {}", elasticsearchSourceProperties.getPathPrefix());
            }

            HttpHeaders compatibilityHeaders = new HttpHeaders();
            compatibilityHeaders.add("Accept", "application/vnd.elasticsearch+json;compatible-with=7");
            compatibilityHeaders.add(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");

            configurationBuilder.withDefaultHeaders(compatibilityHeaders)
                    .withHeaders(() -> {
                        HttpHeaders headers = new HttpHeaders();
                        headers.add("currentDate", DateUtil.now());
                        return headers;
                    })
                    .withClientConfigurer(Rest5Clients.ElasticsearchHttpClientConfigurationCallback.from(clientBuilder -> {
                        clientBuilder.disableAuthCaching();
                        clientBuilder.setKeepAliveStrategy((httpResponse, httpContext) -> TimeValue.ofSeconds(60));
                        clientBuilder.addResponseInterceptorLast((response, entity, context) -> response.addHeader("X-Elastic-Product", "Elasticsearch"));
                        return clientBuilder;
                    }));

            Duration connectionTimeout = elasticsearchSourceProperties.getConnectionTimeout();
            Duration socketTimeout = elasticsearchSourceProperties.getSocketTimeout();
            configurationBuilder.withConnectTimeout(connectionTimeout).withSocketTimeout(socketTimeout);

            log.debug("Elasticsearch客户端配置构建成功");
            return configurationBuilder;
        } catch (Exception e) {
            log.error("构建Elasticsearch客户端配置失败: {}", e.getMessage());
            throw new IllegalStateException("构建Elasticsearch客户端配置失败: " + e.getMessage(), e);
        }
    }

    private void checkElasticsearchDataSourceProperties(Map<String, GXElasticsearchProperties> datasourceMap) {
        try {
            if (datasourceMap == null || datasourceMap.isEmpty()) {
                throw new GXBusinessException("未找到有效的Elasticsearch数据源配置");
            }

            int primaryBeanCount = 0;
            String primaryDataSourceName = null;

            for (Map.Entry<String, GXElasticsearchProperties> entry : datasourceMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isPrimary()) {
                    primaryBeanCount++;
                    primaryDataSourceName = entry.getKey();
                }
            }

            if (primaryBeanCount > 1) {
                throw new GXBusinessException("只能有一个主ElasticsearchTemplate，请检查primary是否设置了多个!");
            }
            if (primaryBeanCount == 0) {
                throw new GXBusinessException("必须有一个主ElasticsearchTemplate，请检查primary是否被设置!");
            }

            log.info("已确认主Elasticsearch数据源: {}", primaryDataSourceName);
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("检查Elasticsearch数据源配置失败: {}", e.getMessage());
            throw new GXBusinessException("检查Elasticsearch数据源配置失败: " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
