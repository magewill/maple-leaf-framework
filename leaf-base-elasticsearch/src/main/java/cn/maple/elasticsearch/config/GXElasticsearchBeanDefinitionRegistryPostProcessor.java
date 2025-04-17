package cn.maple.elasticsearch.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.elasticsearch.properties.GXElasticsearchProperties;
import cn.maple.elasticsearch.properties.GXElasticsearchSourceProperties;
import cn.maple.elasticsearch.properties.local.GXLocalElasticsearchProperties;
import cn.maple.elasticsearch.properties.nacos.GXNacosElasticsearchProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchProperties;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.support.HttpHeaders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Elasticsearch的多数据源配置，支持动态注册多个ElasticsearchTemplate实例
 * 实现了BeanDefinitionRegistryPostProcessor接口，用于在Spring容器启动时动态注册Bean
 * 线程安全说明：该类在Spring容器初始化阶段执行，不存在并发访问问题
 *
 * @author britton <britton@126.com>
 * @since 2023-08-24
 */
@Configuration
@Log4j2
public class GXElasticsearchBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware, PriorityOrdered {
    private Environment environment;

    private ApplicationContext applicationContext;

    /**
     * Modify the application context's internal bean definition registry after its
     * standard initialization. All regular bean definitions will have been loaded,
     * but no beans will have been instantiated yet. This allows for adding further
     * bean definitions before the next post-processing phase kicks in.
     *
     * @param beanDefinitionRegistry the bean definition registry used by the application context
     * @throws BeansException in case of errors
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        // 检查配置中是否只有一个主数据源
        checkElasticsearchDataSourceProperties();
        
        getSourceElasticsearchProperties().getDatasource().forEach((key, dataSourceProperties) -> {
            ElasticsearchClient elasticsearchClient = buildElasticsearchClient(dataSourceProperties);
            // 创建ElasticsearchTemplate的BeanDefinition构建对象
            BeanDefinitionBuilder elasticsearchTemplateBeanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(ElasticsearchTemplate.class);
            elasticsearchTemplateBeanDefinitionBuilder.addConstructorArgValue(elasticsearchClient);
            MappingElasticsearchConverter mappingElasticsearchConverter = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
            mappingElasticsearchConverter.afterPropertiesSet();
            elasticsearchTemplateBeanDefinitionBuilder.addConstructorArgValue(mappingElasticsearchConverter);
            elasticsearchTemplateBeanDefinitionBuilder.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME);
            boolean primary = dataSourceProperties.isPrimary();
            String beanName = key + "ElasticsearchTemplate";
            if (Boolean.TRUE.equals(primary)) {
                settingElasticsearchPropertiesBeanProperties(dataSourceProperties);
                elasticsearchTemplateBeanDefinitionBuilder.setPrimary(true);
                // 为主数据源注册别名，方便其他组件引用
                beanDefinitionRegistry.registerAlias(beanName, "elasticsearchTemplate");
            }
            beanDefinitionRegistry.registerBeanDefinition(beanName, elasticsearchTemplateBeanDefinitionBuilder.getBeanDefinition());
        });
    }

    /**
     * 重新将IOC中的ElasticsearchProperties的bean对象填充属性
     * 用于设置主数据源的属性，确保与Spring Boot自动配置兼容
     *
     * @param dataSourceProperties 自定义配置的属性
     * @throws AssertionError 如果无法获取ElasticsearchProperties bean
     */
    private void settingElasticsearchPropertiesBeanProperties(GXElasticsearchProperties dataSourceProperties) {
        ElasticsearchProperties elasticsearchProperties = GXSpringContextUtils.getBean(ElasticsearchProperties.class);
        assert elasticsearchProperties != null;
        elasticsearchProperties.setUsername(dataSourceProperties.getUsername());
        elasticsearchProperties.setPassword(dataSourceProperties.getPassword());
        elasticsearchProperties.setUris(stringToLst(dataSourceProperties.getUris().get(0)));
        if (CharSequenceUtil.isNotEmpty(dataSourceProperties.getPathPrefix())) {
            elasticsearchProperties.setPathPrefix(dataSourceProperties.getPathPrefix());
        }
        Duration socketTimeout = dataSourceProperties.getSocketTimeout();
        Duration connectionTimeout = dataSourceProperties.getConnectionTimeout();
        elasticsearchProperties.setConnectionTimeout(connectionTimeout);
        elasticsearchProperties.setSocketTimeout(socketTimeout);
    }

    /**
     * Modify the application context's internal bean factory after its standard
     * initialization. All bean definitions will have been loaded, but no beans
     * will have been instantiated yet. This allows for overriding or adding
     * properties even to eager-initializing beans.
     *
     * @param beanFactory the bean factory used by the application context
     * @throws BeansException in case of errors
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    /**
     * Set the ApplicationContext that this object runs in.
     * Normally this call will be used to initialize the object.
     * <p>Invoked after population of normal bean properties but before an init callback such
     * as {@link InitializingBean#afterPropertiesSet()}
     * or a custom init-method. Invoked after {@link ResourceLoaderAware#setResourceLoader},
     * {@link ApplicationEventPublisherAware#setApplicationEventPublisher} and
     * {@link MessageSourceAware}, if applicable.
     *
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws ApplicationContextException in case of context initialization errors
     * @throws BeansException              if thrown by application context methods
     * @see BeanInitializationException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (Objects.isNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext())) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
        this.applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
    }

    /**
     * Set the {@code Environment} that this component runs in.
     *
     * @param environment
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 解析配置文件中的连接信息
     * 优先使用nacos配置，如果不存在则使用本地配置
     *
     * @return GXElasticsearchSourceProperties 数据源配置信息
     * @throws IllegalStateException 如果无法绑定配置或配置为空
     */
    private GXElasticsearchSourceProperties getSourceElasticsearchProperties() {
        BindResult<Dict> bind = Binder.get(this.environment).bind("elasticsearch.datasource", Dict.class);
        if (!bind.isBound() || bind.get() == null) {
            throw new IllegalStateException("未找到有效的Elasticsearch数据源配置，请检查配置文件");
        }
        
        Map<String, GXElasticsearchProperties> datasource = Convert.convert(new TypeReference<>() {
        }, bind.get());
        
        if (datasource == null || datasource.isEmpty()) {
            throw new IllegalStateException("Elasticsearch数据源配置为空，请检查配置格式是否正确");
        }
        
        try {
            // 判断是否导入了nacos 导入了nacos 则使用nacos的配置
            Class.forName("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties");
            log.info("检测到Nacos配置中心，使用Nacos的Elasticsearch配置");
            GXNacosElasticsearchProperties elasticsearchSourceProperties = new GXNacosElasticsearchProperties();
            elasticsearchSourceProperties.setDatasource(datasource);
            return elasticsearchSourceProperties;
        } catch (ClassNotFoundException e) {
            // 不存在nacos 则使用local配置
            log.info("未检测到Nacos配置中心，使用本地Elasticsearch配置");
        }
        GXLocalElasticsearchProperties elasticsearchSourceProperties = new GXLocalElasticsearchProperties();
        elasticsearchSourceProperties.setDatasource(datasource);
        return elasticsearchSourceProperties;
    }

    /**
     * 通过配置文件构建ElasticsearchClient对象
     *
     * @param elasticsearchSourceProperties 配置信息
     * @return 构建好的ElasticsearchClient实例
     */
    private ElasticsearchClient buildElasticsearchClient(GXElasticsearchProperties elasticsearchSourceProperties) {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder configurationBuilder = buildElasticsearchConfigurationBuilder(elasticsearchSourceProperties);
        return ElasticsearchClients.createImperative(configurationBuilder.build());
    }

    /**
     * 通过配置文件构建客户端配置 ClientConfiguration
     * 设置连接信息、认证信息、请求头、超时时间等
     *
     * @param elasticsearchSourceProperties 配置信息
     * @return 构建好的ClientConfiguration构建器
     */
    private ClientConfiguration.MaybeSecureClientConfigurationBuilder buildElasticsearchConfigurationBuilder(GXElasticsearchProperties elasticsearchSourceProperties) {
        String username = elasticsearchSourceProperties.getUsername();
        String password = elasticsearchSourceProperties.getPassword();
        String uriStr = elasticsearchSourceProperties.getUris().get(0);
        String[] uris = stringToLst(uriStr).toArray(new String[0]);
        ClientConfiguration.MaybeSecureClientConfigurationBuilder configurationBuilder = ClientConfiguration.builder().connectedTo(uris);
        if (CharSequenceUtil.isAllNotEmpty(username, password)) {
            configurationBuilder.withBasicAuth(username, password);
        }
        if (CharSequenceUtil.isNotEmpty(elasticsearchSourceProperties.getPathPrefix())) {
            configurationBuilder.withPathPrefix(elasticsearchSourceProperties.getPathPrefix());
        }
        HttpHeaders compatibilityHeaders = new HttpHeaders();
        compatibilityHeaders.add("Accept", "application/vnd.elasticsearch+json;compatible-with=7");
        //compatibilityHeaders.add(HttpHeaders.CONTENT_TYPE, "application/vnd.elasticsearch+json;compatible-with=7");
        compatibilityHeaders.add(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        // 创建认证提供者并设置认证信息
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        
        // 配置请求头和客户端配置
        configurationBuilder.withDefaultHeaders(compatibilityHeaders).withHeaders(() -> {
            HttpHeaders headers = new HttpHeaders();
            headers.add("currentDate", DateUtil.now());
            return headers;
        }).withClientConfigurer(ElasticsearchClients.ElasticsearchHttpClientConfigurationCallback.from(clientBuilder -> {
            clientBuilder.disableAuthCaching();
            // 设置连接保持策略为60秒
            clientBuilder.setKeepAliveStrategy((httpResponse, httpContext) -> 1000 * 60);
            clientBuilder.addInterceptorLast((HttpResponseInterceptor) (response, context) -> response.addHeader("X-Elastic-Product", "Elasticsearch"));
            // 使用已创建的认证提供者
            return clientBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }));
        Duration connectionTimeout = elasticsearchSourceProperties.getConnectionTimeout();
        Duration socketTimeout = elasticsearchSourceProperties.getSocketTimeout();
        configurationBuilder.withConnectTimeout(connectionTimeout).withSocketTimeout(socketTimeout);
        return configurationBuilder;
    }

    /**
     * 将字符串转换为字符串列表
     * 将形如"{0=192.168.7.213:9200, 1=192.168.7.213:9200}"的字符串转换为URI列表
     *
     * @param uriStr 待转换的字符串 eg "{0=192.168.7.213:9200, 1=192.168.7.213:9200}"
     * @return 转换后的URI字符串列表
     * @throws NullPointerException 如果输入字符串为null或无法转换为Dict对象
     * @throws IllegalArgumentException 如果转换后的列表为空
     */
    private List<String> stringToLst(String uriStr) {
        if (CharSequenceUtil.isEmpty(uriStr)) {
            throw new NullPointerException("URI字符串不能为空");
        }
        
        List<String> lstUris = new ArrayList<>();
        Dict uriDict = GXCommonUtils.convertStrToTarget(uriStr, Dict.class);
        
        if (uriDict == null) {
            throw new NullPointerException("无法将URI字符串转换为Dict对象: " + uriStr);
        }
        
        uriDict.forEach((k, value) -> {
            if (value != null) {
                lstUris.add(value.toString());
            }
        });
        
        if (lstUris.isEmpty()) {
            throw new IllegalArgumentException("转换后的URI列表为空，请检查URI字符串格式: " + uriStr);
        }
        
        return lstUris;
    }

    /**
     * 设置处理器的执行顺序
     * 返回最低优先级，确保在其他高优先级的处理器之后执行
     *
     * @return 优先级顺序值
     */
    /**
     * 检测配置中是否只有一个配置项设置了primary为true
     * 确保系统中只有一个主ElasticsearchTemplate
     * 
     * @throws cn.maple.core.framework.exception.GXBusinessException 如果没有主数据源或有多个主数据源
     */
    private void checkElasticsearchDataSourceProperties() {
        int primaryBeanCount = 0;
        for (GXElasticsearchProperties properties : getSourceElasticsearchProperties().getDatasource().values()) {
            if (properties.isPrimary()) {
                primaryBeanCount++;
            }
        }
        
        if (primaryBeanCount > 1) {
            throw new cn.maple.core.framework.exception.GXBusinessException("只能有一个主ElasticsearchTemplate，请检查primary是否设置了多个!");
        }
        if (primaryBeanCount == 0) {
            throw new cn.maple.core.framework.exception.GXBusinessException("必须有一个主ElasticsearchTemplate，请检查primary是否被设置!");
        }
    }
    
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;  // within PriorityOrdered
    }
}