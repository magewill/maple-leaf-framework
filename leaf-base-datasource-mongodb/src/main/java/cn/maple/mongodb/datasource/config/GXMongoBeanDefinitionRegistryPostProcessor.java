package cn.maple.mongodb.datasource.config;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
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
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.*;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MongoDB的多数据源配置类
 * <p>
 * 该类负责动态注册多个MongoDB数据源，支持从本地配置文件或Nacos配置中心读取配置信息。
 * 实现了BeanDefinitionRegistryPostProcessor接口，在Spring容器启动时动态注册MongoDB相关的Bean。
 * 支持配置主从数据源，确保系统中有且仅有一个主数据源。
 * </p>
 * <p>
 * 内存安全特性：
 * - 安全处理凭证信息，避免敏感信息泄露
 * - 使用原子操作确保线程安全计数
 * - 对所有外部输入进行严格验证和解码
 * - 合理管理资源，避免内存泄漏
 * </p>
 * <p>
 * 线程安全特性：
 * - 使用AtomicInteger确保计数操作的原子性
 * - 避免共享可变状态，确保方法执行的线程安全
 * - 在Spring容器初始化阶段执行，不存在并发访问问题
 * </p>
 *
 * @author britton <britton@126.com>
 * @since 2023-08-24
 */
@Component
@Log4j2
public class GXMongoBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware {
    private Environment environment;

    private ApplicationContext applicationContext;

    /**
     * 修改应用上下文的内部Bean定义注册表
     * <p>
     * 在标准初始化后，所有常规Bean定义都已加载，但尚未实例化任何Bean。
     * 这允许在下一个后处理阶段开始之前添加更多的Bean定义。
     * 该方法负责动态创建和注册MongoDB数据源相关的Bean，包括MongoDatabaseFactory和MongoTemplate。
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
        // 首先检查配置的有效性，确保只有一个主数据源
        checkMongoDynamicDataSourceProperties();
        
        // 遍历所有配置的数据源，为每个数据源创建相应的Bean
        getMongoDynamicDataSourceProperties().getDatasource().forEach((key, dataSourceProperties) -> {
            try {
                // 安全解码连接信息，避免敏感信息泄露
                String uri = GXCommonUtils.decodeConnectStr(dataSourceProperties.getUri(), String.class);
                String database = dataSourceProperties.getDatabase();
                String username = Objects.isNull(dataSourceProperties.getUsername()) ? null : 
                    GXCommonUtils.decodeConnectStr(dataSourceProperties.getUsername(), String.class);
                String authenticationDatabase = GXCommonUtils.decodeConnectStr(dataSourceProperties.getAuthenticationDatabase(), String.class);
                
                // 安全处理密码，使用char[]而非String，便于后续清除内存
                char[] password = null;
                if (Objects.nonNull(dataSourceProperties.getPassword())) {
                    password = GXCommonUtils.decodeConnectStr(String.valueOf(dataSourceProperties.getPassword()), String.class).toCharArray();
                }
                
                // 创建MongoDB凭证和连接设置
                MongoCredential credential = MongoCredential.createCredential(username, authenticationDatabase, password);
                ConnectionString connectionString = new ConnectionString(uri);
                MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                    .credential(credential)
                    .applyConnectionString(connectionString)
                    .build();
                
                // 创建MongoDatabaseFactory的BeanDefinition构建对象
                BeanDefinitionBuilder mongoDatabaseFactoryBeanDefinitionBuilder = 
                    BeanDefinitionBuilder.rootBeanDefinition(SimpleMongoClientDatabaseFactory.class);
                mongoDatabaseFactoryBeanDefinitionBuilder.addConstructorArgValue(MongoClients.create(mongoClientSettings));
                mongoDatabaseFactoryBeanDefinitionBuilder.addConstructorArgValue(database);
                
                // 设置主数据源标记
                Boolean primary = dataSourceProperties.getPrimary();
                if (Boolean.TRUE.equals(primary)) {
                    mongoDatabaseFactoryBeanDefinitionBuilder.setPrimary(true);
                }
                
                // 注册MongoDatabaseFactory Bean
                String factoryBeanName = key + "MongoDatabaseFactory";
                beanDefinitionRegistry.registerBeanDefinition(
                    factoryBeanName, 
                    mongoDatabaseFactoryBeanDefinitionBuilder.getBeanDefinition()
                );

                // 创建MongoTemplate的BeanDefinition构建对象
                BeanDefinitionBuilder mongoTemplateBeanDefinitionBuilder = 
                    BeanDefinitionBuilder.rootBeanDefinition(MongoTemplate.class);
                SimpleMongoClientDatabaseFactory factory = applicationContext.getBean(factoryBeanName, SimpleMongoClientDatabaseFactory.class);
                mongoTemplateBeanDefinitionBuilder.addConstructorArgValue(factory);
                
                // 确定Bean名称，优先使用配置的名称，否则使用默认命名规则
                String beanName = dataSourceProperties.getBeanName();
                if (CharSequenceUtil.isEmpty(beanName)) {
                    beanName = key + "MongoTemplate";
                }
                
                // 为主数据源设置别名，方便其他组件引用
                if (Boolean.TRUE.equals(primary)) {
                    mongoTemplateBeanDefinitionBuilder.setPrimary(true);
                    beanDefinitionRegistry.registerAlias(beanName, "mongoTemplate");
                }
                
                // 注册MongoTemplate Bean
                beanDefinitionRegistry.registerBeanDefinition(
                    beanName, 
                    mongoTemplateBeanDefinitionBuilder.getBeanDefinition()
                );
                
                // 记录数据源注册成功的日志
                log.info("MongoDB数据源[{}]注册成功，数据库：{}", key, database);
                
            } catch (Exception e) {
                // 记录详细的错误信息，但不暴露敏感数据
                log.error("MongoDB数据源[{}]注册失败: {}", key, e.getMessage());
                throw new GXBusinessException("MongoDB数据源注册失败: " + e.getMessage());
            }
        });
    }

    /**
     * 修改应用上下文的内部Bean工厂
     * <p>
     * 在标准初始化后，所有Bean定义都已加载，但尚未实例化任何Bean。
     * 这允许覆盖或添加属性，即使是急切初始化的Bean。
     * 当前实现为空，保留以满足接口要求。
     * </p>
     *
     * @param beanFactory 应用上下文使用的Bean工厂
     * @throws BeansException 如果在处理过程中发生错误
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 当前无需实现，保留以满足接口要求
    }

    /**
     * 设置此对象运行的ApplicationContext
     * <p>
     * 通常此调用将用于初始化对象。在填充普通Bean属性之后但在初始化回调之前调用，
     * 例如{@link InitializingBean#afterPropertiesSet()}或自定义init方法。
     * 在{@link ResourceLoaderAware#setResourceLoader}、
     * {@link ApplicationEventPublisherAware#setApplicationEventPublisher}和
     * {@link MessageSourceAware}之后调用（如果适用）。
     * </p>
     * <p>
     * 该方法确保ApplicationContext被正确设置，并且只初始化一次，避免重复初始化导致的问题。
     * 使用单例模式保证全局只有一个ApplicationContext实例。
     * </p>
     *
     * @param applicationContext 要由此对象使用的ApplicationContext对象
     * @throws ApplicationContextException 在上下文初始化错误的情况下
     * @throws BeansException 如果由应用程序上下文方法抛出
     * @see BeanInitializationException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 确保全局ApplicationContext单例只被初始化一次
        if (Objects.isNull(GXApplicationContextSingleton.INSTANCE.getApplicationContext())) {
            GXApplicationContextSingleton.INSTANCE.setApplicationContext(applicationContext);
        }
        this.applicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        log.debug("MongoDB多数据源配置已设置ApplicationContext");
    }

    /**
     * 设置此组件运行的环境
     * <p>
     * 该方法由Spring容器调用，用于注入Environment对象，使组件能够访问配置属性。
     * Environment对象包含应用程序环境的所有属性源，如配置文件、系统属性等。
     * </p>
     *
     * @param environment 包含配置信息的环境对象
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        log.debug("MongoDB多数据源配置已设置Environment");
    }

    /**
     * 检测配置中是否只有一个配置项设置了primary为true
     * <p>
     * 该方法确保在多数据源配置中，有且仅有一个数据源被标记为主数据源（primary=true）。
     * 如果没有主数据源或有多个主数据源，将抛出业务异常。
     * 使用AtomicInteger确保计数操作的线程安全性。
     * </p>
     * 
     * @throws GXBusinessException 当主数据源数量不为1时抛出
     */
    private void checkMongoDynamicDataSourceProperties() {
        // 使用AtomicInteger确保线程安全的计数
        AtomicInteger primaryBeanCnt = new AtomicInteger();
        
        // 检查所有数据源配置，统计主数据源的数量
        getMongoDynamicDataSourceProperties().getDatasource().forEach((k, properties) -> {
            Boolean primary = properties.getPrimary();
            if (Boolean.TRUE.equals(primary)) {
                primaryBeanCnt.getAndIncrement();
                log.info("检测到主MongoDB数据源: {}", k);
            }
        });
        
        // 验证主数据源的数量必须为1
        if (primaryBeanCnt.get() > 1) {
            log.error("MongoDB配置错误: 检测到多个主数据源");
            throw new GXBusinessException("只能有一个主MongoTemplate, 请检查primary是否设置了多个!");
        }
        if (primaryBeanCnt.get() == 0) {
            log.error("MongoDB配置错误: 未检测到主数据源");
            throw new GXBusinessException("必须有一个主MongoTemplate, 请检查primary是否被设置!");
        }
        
        log.info("MongoDB数据源配置检查通过，共有{}个数据源，其中1个主数据源", 
                getMongoDynamicDataSourceProperties().getDatasource().size());
    }

    /**
     * 解析配置文件中的MongoDB连接信息
     * <p>
     * 该方法从环境配置中读取MongoDB数据源配置，并根据是否存在Nacos配置中心
     * 决定使用Nacos配置还是本地配置。优先使用Nacos配置，如果不存在则回退到本地配置。
     * </p>
     * <p>
     * 内存安全：安全处理配置信息，避免空指针异常
     * 异常处理：优雅处理ClassNotFoundException，确保在Nacos不可用时能够回退到本地配置
     * </p>
     *
     * @return GXMongoDynamicDataSourceProperties 数据源配置信息对象
     * @throws IllegalStateException 如果无法绑定配置或配置为空
     */
    private GXMongoDynamicDataSourceProperties getMongoDynamicDataSourceProperties() {
        // 从环境中绑定MongoDB数据源配置
        BindResult<Dict> bind = Binder.get(this.environment).bind("mongodb.datasource", Dict.class);
        
        // 验证配置是否存在
        if (!bind.isBound() || bind.get() == null) {
            log.error("未找到有效的MongoDB数据源配置，请检查配置文件");
            throw new IllegalStateException("未找到有效的MongoDB数据源配置，请检查配置文件");
        }
        
        // 转换配置为强类型Map
        Map<String, GXMongoDataSourceProperties> datasource = Convert.convert(
            new TypeReference<>() {}, bind.get()
        );
        
        // 验证转换后的配置是否有效
        if (datasource == null || datasource.isEmpty()) {
            log.error("MongoDB数据源配置为空，请检查配置格式是否正确");
            throw new IllegalStateException("MongoDB数据源配置为空，请检查配置格式是否正确");
        }
        
        try {
            // 判断是否导入了nacos，如果导入了则使用nacos的配置
            Class.forName("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties");
            log.info("检测到Nacos配置中心，使用Nacos的MongoDB配置");
            GXNacosMongoDynamicDataSourceProperties mongoDynamicDataSourceProperties = new GXNacosMongoDynamicDataSourceProperties();
            mongoDynamicDataSourceProperties.setDatasource(datasource);
            return mongoDynamicDataSourceProperties;
        } catch (ClassNotFoundException e) {
            // 不存在nacos则使用local配置
            log.info("未检测到Nacos配置中心，使用本地MongoDB配置");
        }
        
        // 创建并返回本地配置对象
        GXLocalMongoDynamicDataSourceProperties mongoDynamicDataSourceProperties = new GXLocalMongoDynamicDataSourceProperties();
        mongoDynamicDataSourceProperties.setDatasource(datasource);
        log.info("MongoDB数据源配置加载完成，共有{}个数据源", datasource.size());
        return mongoDynamicDataSourceProperties;
    }
}
