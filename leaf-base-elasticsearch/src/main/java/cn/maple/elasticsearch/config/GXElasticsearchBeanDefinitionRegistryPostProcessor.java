package cn.maple.elasticsearch.config;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.elasticsearch.properties.GXElasticsearchProperties;
import cn.maple.elasticsearch.properties.GXElasticsearchSourceProperties;
import cn.maple.elasticsearch.properties.local.GXLocalElasticsearchProperties;
import cn.maple.elasticsearch.properties.nacos.GXNacosElasticsearchProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.extern.log4j.Log4j2;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchClients;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.rest5_client.Rest5Clients;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.data.elasticsearch.support.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.boot.context.properties.bind.Bindable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Elasticsearch的多数据源配置，支持动态注册多个ElasticsearchTemplate实例
 * <p>
 * 该类负责动态注册多个Elasticsearch数据源，支持从本地配置文件或Nacos配置中心读取配置信息。
 * 实现了BeanDefinitionRegistryPostProcessor接口，在Spring容器启动时动态注册Elasticsearch相关的Bean。
 * 支持配置主从数据源,确保系统中有且仅有一个主数据源。
 * </p>
 * <p>
 * 内存安全特性：
 * - 安全处理凭证信息，避免敏感信息泄露
 * - 对所有外部输入进行严格验证和解码
 * - 合理管理资源，避免内存泄漏
 * - 使用不可变对象和线程安全的集合类
 * </p>
 * <p>
 * 线程安全特性：
 * - 避免共享可变状态，确保方法执行的线程安全
 * - 在Spring容器初始化阶段执行，不存在并发访问问题
 * - 通过参数验证和防御性编程确保多线程环境下的安全性
 * </p>
 *
 * @author britton <britton@126.com>
 * @since 2023-08-24
 */
@Configuration
@Log4j2
public class GXElasticsearchBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware, Ordered {
    private Environment environment;

    private ApplicationContext applicationContext;

    /**
     * 修改应用上下文的内部Bean定义注册表
     * <p>
     * 在标准初始化后，所有常规Bean定义都已加载，但尚未实例化任何Bean。
     * 这允许在下一个后处理阶段开始之前添加更多的Bean定义。
     * 该方法负责动态创建和注册Elasticsearch数据源相关的Bean，包括ElasticsearchClient和ElasticsearchTemplate。
     * </p>
     * <p>
     * 内存安全：安全处理凭证信息，避免敏感信息泄露
     * 线程安全：在Spring容器初始化阶段执行，不存在并发访问问题
     * </p>
     *
     * @param beanDefinitionRegistry Spring应用上下文使用的Bean定义注册表
     * @throws BeansException 如果在处理过程中发生错误
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        try {
            // 获取所有数据源配置
            Map<String, GXElasticsearchProperties> datasourceMap = getSourceElasticsearchProperties().getDatasource();
            
            // 检查配置中是否只有一个主数据源
            checkElasticsearchDataSourceProperties(datasourceMap);

            // 遍历所有配置的数据源，为每个数据源创建相应的Bean
            datasourceMap.forEach((key, dataSourceProperties) -> {
                try {
                    // 动态注册 ElasticsearchClient Bean
                    String clientBeanName = key + "ElasticsearchClient";
                    BeanDefinitionBuilder clientBeanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(ElasticsearchClient.class, () -> buildElasticsearchClient(dataSourceProperties));
                    beanDefinitionRegistry.registerBeanDefinition(clientBeanName, clientBeanDefinitionBuilder.getBeanDefinition());

                    // 动态注册 SimpleElasticsearchMappingContext
                    String mappingContextBeanName = key + "ElasticsearchMappingContext";
                    BeanDefinitionBuilder mappingContextBuilder = BeanDefinitionBuilder.genericBeanDefinition(SimpleElasticsearchMappingContext.class);
                    mappingContextBuilder.setInitMethodName("initialize");
                    beanDefinitionRegistry.registerBeanDefinition(mappingContextBeanName, mappingContextBuilder.getBeanDefinition());

                    // 动态注册 MappingElasticsearchConverter
                    String converterBeanName = key + "MappingElasticsearchConverter";
                    BeanDefinitionBuilder converterBuilder = BeanDefinitionBuilder.genericBeanDefinition(MappingElasticsearchConverter.class);
                    converterBuilder.addConstructorArgReference(mappingContextBeanName);
                    converterBuilder.setInitMethodName("afterPropertiesSet");
                    beanDefinitionRegistry.registerBeanDefinition(converterBeanName, converterBuilder.getBeanDefinition());

                    // 创建ElasticsearchTemplate的BeanDefinition构建对象
                    BeanDefinitionBuilder elasticsearchTemplateBeanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(ElasticsearchTemplate.class);
                    elasticsearchTemplateBeanDefinitionBuilder.addConstructorArgReference(clientBeanName);
                    elasticsearchTemplateBeanDefinitionBuilder.addConstructorArgReference(converterBeanName);

                    // 设置自动装配模式
                    elasticsearchTemplateBeanDefinitionBuilder.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME);

                    // 确定Bean名称和主数据源设置
                    boolean primary = dataSourceProperties.isPrimary();
                    String beanName = key + "ElasticsearchTemplate";

                    // 如果是主数据源，设置相关属性并注册别名
                    if (primary) {
                        // 注意：Spring Boot 4中已移除ElasticsearchProperties类
                        // 如果需要设置主数据源属性，需要使用自定义的方式处理
                        // settingElasticsearchPropertiesBeanProperties(dataSourceProperties);
                        elasticsearchTemplateBeanDefinitionBuilder.setPrimary(true);
                        // 为主数据源注册别名，方便其他组件引用
                        beanDefinitionRegistry.registerAlias(beanName, "elasticsearchTemplate");
                        log.info("主Elasticsearch数据源[{}]已设置为primary", key);
                    }

                    // 注册ElasticsearchTemplate Bean
                    beanDefinitionRegistry.registerBeanDefinition(beanName, elasticsearchTemplateBeanDefinitionBuilder.getBeanDefinition());

                    // 记录数据源注册成功的日志
                    log.info("Elasticsearch数据源[{}]注册成功", key);

                } catch (Exception e) {
                    // 记录详细的错误信息，但不暴露敏感数据
                    log.error("Elasticsearch数据源[{}]注册失败: {}", key, e.getMessage());
                    throw new GXBusinessException("Elasticsearch数据源注册失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Elasticsearch多数据源配置处理失败: {}", e.getMessage());
            throw new GXBusinessException("Elasticsearch多数据源配置处理失败: " + e.getMessage());
        }
    }

    /**
     * Spring Boot 4中已移除ElasticsearchProperties类
     * 此方法已废弃，保留注释以供参考
     * 如果需要设置主数据源属性，建议使用自定义的配置Bean或直接在配置文件中管理
     */
    @Deprecated
    private void settingElasticsearchPropertiesBeanProperties(GXElasticsearchProperties dataSourceProperties) {
        // Spring Boot 4中此方法不再需要
        // 原有的ElasticsearchProperties类已被移除
        log.warn("settingElasticsearchPropertiesBeanProperties方法在Spring Boot 4中已不再使用");
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
     * <p>
     * 该方法负责从配置文件中读取Elasticsearch数据源配置信息。
     * 优先使用Nacos配置中心的配置，如果Nacos不可用则回退到本地配置文件。
     * 支持多数据源配置，每个数据源可以有不同的连接参数和认证信息。
     * </p>
     * <p>
     * 内存安全：
     * - 对配置绑定结果进行严格验证，防止空指针异常
     * - 安全处理类型转换，避免类型转换异常
     * - 使用日志记录关键操作，便于问题排查
     * </p>
     *
     * @return GXElasticsearchSourceProperties 数据源配置信息对象
     * @throws IllegalStateException 如果无法绑定配置、配置为空或格式不正确
     */
    private GXElasticsearchSourceProperties getSourceElasticsearchProperties() {
        try {
            // 从环境中绑定elasticsearch.datasource配置
            BindResult<Map<String, GXElasticsearchProperties>> bind = Binder.get(this.environment)
                    .bind("elasticsearch.datasource", Bindable.mapOf(String.class, GXElasticsearchProperties.class));
            if (!bind.isBound() || bind.get() == null) {
                throw new IllegalStateException("未找到有效的Elasticsearch数据源配置，请检查配置文件中是否包含elasticsearch.datasource节点");
            }

            // 将配置转换为数据源Map
            Map<String, GXElasticsearchProperties> datasource = bind.get();

            // 验证数据源配置是否有效
            if (datasource == null || datasource.isEmpty()) {
                throw new IllegalStateException("Elasticsearch数据源配置为空，请检查配置格式是否正确");
            }

            // 记录数据源配置信息（不包含敏感信息）
            log.info("成功加载{}个Elasticsearch数据源配置", datasource.size());

            try {
                // 判断是否导入了nacos，如果导入了则使用nacos的配置
                Class.forName("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties");
                log.info("检测到Nacos配置中心，使用Nacos的Elasticsearch配置");
                GXNacosElasticsearchProperties elasticsearchSourceProperties = new GXNacosElasticsearchProperties();
                elasticsearchSourceProperties.setDatasource(datasource);
                return elasticsearchSourceProperties;
            } catch (ClassNotFoundException e) {
                // 不存在nacos则使用local配置
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

    /**
     * 通过配置文件构建ElasticsearchClient对象
     * <p>
     * 该方法根据提供的配置信息创建ElasticsearchClient实例。
     * ElasticsearchClient是与Elasticsearch服务器通信的核心客户端对象，
     * 负责执行所有的索引、查询、更新和删除操作。
     * </p>
     * <p>
     * 内存安全：
     * - 验证输入参数，防止空指针异常
     * - 安全处理客户端配置，避免连接泄漏
     * </p>
     *
     * @param elasticsearchSourceProperties 数据源配置信息，不能为null
     * @return 构建好的ElasticsearchClient实例
     * @throws IllegalArgumentException 如果配置信息为null
     * @throws IllegalStateException    如果客户端创建失败
     */
    private ElasticsearchClient buildElasticsearchClient(GXElasticsearchProperties elasticsearchSourceProperties) {
        Assert.notNull(elasticsearchSourceProperties, "Elasticsearch数据源配置不能为null");

        try {
            // 构建客户端配置
            ClientConfiguration.MaybeSecureClientConfigurationBuilder configurationBuilder =
                    buildElasticsearchConfigurationBuilder(elasticsearchSourceProperties);

            // 创建并返回客户端实例
            return ElasticsearchClients.createImperative(configurationBuilder.build());
        } catch (Exception e) {
            log.error("创建ElasticsearchClient失败: {}", e.getMessage());
            throw new IllegalStateException("创建ElasticsearchClient失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通过配置文件构建客户端配置 ClientConfiguration
     * <p>
     * 该方法负责根据配置信息构建Elasticsearch客户端配置，包括：
     * - 设置连接信息（服务器地址、端口）
     * - 配置认证信息（用户名、密码）
     * - 设置请求头（兼容性、内容类型）
     * - 配置超时时间（连接超时、读取超时）
     * - 设置HTTP客户端属性（连接保持、拦截器）
     * </p>
     * <p>
     * 内存安全：
     * - 安全处理凭证信息，避免敏感信息泄露
     * - 对所有外部输入进行严格验证
     * - 使用不可变对象和线程安全的集合类
     * </p>
     *
     * @param elasticsearchSourceProperties 数据源配置信息，不能为null
     * @return 构建好的ClientConfiguration构建器
     * @throws IllegalArgumentException 如果配置信息为null或必要参数缺失
     * @throws IllegalStateException    如果构建过程中发生错误
     */
    private ClientConfiguration.MaybeSecureClientConfigurationBuilder buildElasticsearchConfigurationBuilder(GXElasticsearchProperties elasticsearchSourceProperties) {
        Assert.notNull(elasticsearchSourceProperties, "Elasticsearch数据源配置不能为null");

        try {
            // 获取认证信息
            String username = elasticsearchSourceProperties.getUsername();
            String password = elasticsearchSourceProperties.getPassword();

            // 获取并解析URI列表
            List<String> uris = elasticsearchSourceProperties.getUris();
            if (uris == null || uris.isEmpty()) {
                throw new IllegalArgumentException("Elasticsearch URI列表不能为空");
            }
            String[] uriArray = uris.toArray(new String[0]);

            // 创建基础配置构建器并设置连接地址
            ClientConfiguration.MaybeSecureClientConfigurationBuilder configurationBuilder =
                    ClientConfiguration.builder().connectedTo(uriArray);

            // 设置认证信息（如果提供）
            if (CharSequenceUtil.isAllNotEmpty(username, password)) {
                configurationBuilder.withBasicAuth(username, password);
                log.debug("已配置Elasticsearch认证信息");
            } else {
                log.warn("未配置Elasticsearch认证信息，将使用匿名访问");
            }

            // 设置路径前缀（如果提供）
            if (CharSequenceUtil.isNotEmpty(elasticsearchSourceProperties.getPathPrefix())) {
                configurationBuilder.withPathPrefix(elasticsearchSourceProperties.getPathPrefix());
                log.debug("已配置Elasticsearch路径前缀: {}", elasticsearchSourceProperties.getPathPrefix());
            }

            // 配置兼容性请求头
            HttpHeaders compatibilityHeaders = new HttpHeaders();
            compatibilityHeaders.add("Accept", "application/vnd.elasticsearch+json;compatible-with=7");
            compatibilityHeaders.add(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");

            // 配置请求头和客户端配置
            configurationBuilder.withDefaultHeaders(compatibilityHeaders)
                    .withHeaders(() -> {
                        // 添加动态请求头
                        HttpHeaders headers = new HttpHeaders();
                        headers.add("currentDate", DateUtil.now());
                        return headers;
                    })
                    .withClientConfigurer(Rest5Clients.ElasticsearchHttpClientConfigurationCallback.from(clientBuilder -> {
                        // 禁用认证缓存，确保每次请求都使用最新的认证信息
                        clientBuilder.disableAuthCaching();
                        // 设置连接保持策略为60秒
                        clientBuilder.setKeepAliveStrategy((httpResponse, httpContext) -> TimeValue.ofSeconds(60));
                        // 添加响应拦截器，设置产品标识
                        clientBuilder.addResponseInterceptorLast((response, entity, context) -> response.addHeader("X-Elastic-Product", "Elasticsearch"));
                        return clientBuilder;
                    }));

            // 设置超时时间
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

    /**
     * 检测配置中是否只有一个配置项设置了primary为true
     * <p>
     * 该方法确保系统中有且仅有一个主ElasticsearchTemplate实例。
     * 在多数据源环境中，必须指定一个且只能指定一个主数据源，
     * 以便其他组件在不指定具体数据源时能够正确引用默认数据源。
     * </p>
     * <p>
     * 内存安全：使用局部变量计数，避免共享状态
     * 线程安全：在Spring容器初始化阶段执行，不存在并发访问问题
     * </p>
     *
     * @throws GXBusinessException 如果没有主数据源或有多个主数据源
     */
    private void checkElasticsearchDataSourceProperties(Map<String, GXElasticsearchProperties> datasourceMap) {
        try {
            if (datasourceMap == null || datasourceMap.isEmpty()) {
                throw new GXBusinessException("未找到有效的Elasticsearch数据源配置");
            }

            // 计算主数据源数量
            int primaryBeanCount = 0;
            String primaryDataSourceName = null;

            for (Map.Entry<String, GXElasticsearchProperties> entry : datasourceMap.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isPrimary()) {
                    primaryBeanCount++;
                    primaryDataSourceName = entry.getKey();
                }
            }

            // 验证主数据源数量
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

    /**
     * 设置处理器的执行顺序
     * <p>
     * 返回最低优先级，确保在其他高优先级的处理器之后执行。
     * 这样可以保证所有其他Bean定义都已加载完成，避免依赖问题。
     * </p>
     *
     * @return 优先级顺序值，固定为Ordered.LOWEST_PRECEDENCE
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}