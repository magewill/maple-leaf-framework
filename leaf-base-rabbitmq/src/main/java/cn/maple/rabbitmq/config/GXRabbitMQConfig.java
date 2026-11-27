package cn.maple.rabbitmq.config;

import cn.maple.rabbitmq.listener.impl.GXAmqpListenerErrorHandler;
import cn.maple.rabbitmq.properties.GXRabbitMQProperties;
import cn.maple.rabbitmq.support.GXAmqpManagementClient;
import cn.maple.rabbitmq.support.GXAmqpMessagePublisher;
import cn.maple.rabbitmq.support.GXRabbitMQJsonSupport;
import org.apache.qpid.protonj2.client.ConnectionOptions;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.client.AmqpConnectionFactory;
import org.springframework.amqp.client.config.AmqpDefaultConfiguration;
import org.springframework.amqp.client.config.EnableAmqp;
import org.springframework.amqp.client.config.MethodAmqpMessageListenerContainerFactory;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.AmqpConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.AmqpConnectionDetails.Address;
import org.springframework.boot.amqp.autoconfigure.AmqpMessageListenerContainerFactoryConfigurer;
import org.springframework.boot.amqp.autoconfigure.ConnectionOptionsCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 核心装配（AMQP 1.0）。
 *
 * <p>连接工厂、{@code AmqpClient} 与监听容器工厂由 Spring Boot 4.2 的
 * {@code AmqpAutoConfiguration} 提供，本类只补齐 Maple Leaf 的约定：</p>
 * <ul>
 *   <li>{@link AmqpConnectionDetails}：优先 {@code spring.amqp.*}，回退到历史前缀
 *       {@code spring.rabbitmq.*}，使既有 {@code rabbit.yml} 继续生效；</li>
 *   <li>{@link ConnectionOptionsCustomizer}：补上 Boot 未暴露的 vhost（AMQP 1.0 通过
 *       open 帧 hostname 传递）与重连参数；</li>
 *   <li>{@link GXAmqpMessagePublisher}：带结算结果的消息发布器；</li>
 *   <li>{@link GXAmqpManagementClient}：拓扑管理（Management HTTP API）；</li>
 *   <li>{@link MethodAmqpMessageListenerContainerFactory}：关闭自动 accept，
 *       由监听器显式 accept/reject/requeue。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableAmqp
@ConditionalOnClass(name = {"org.springframework.amqp.client.AmqpConnectionFactory",
        "org.springframework.amqp.client.config.EnableAmqp"})
public class GXRabbitMQConfig {
    private static final long RECONNECT_DELAY_MILLIS = 1000L;

    private static final long MAX_RECONNECT_DELAY_MILLIS = 30_000L;

    private static final int DEFAULT_AMQP_PORT = 5672;

    private static final String MANAGEMENT_URL_PROPERTY = "maple.framework.rabbitmq.management.url";

    private static final String MANAGEMENT_USERNAME_PROPERTY = "maple.framework.rabbitmq.management.username";

    private static final String MANAGEMENT_PASSWORD_PROPERTY = "maple.framework.rabbitmq.management.password";

    private static final String MANAGEMENT_VHOST_PROPERTY = "maple.framework.rabbitmq.management.virtual-host";

    /**
     * 连接细节：{@code spring.amqp.*} 显式配置优先，否则使用 {@code spring.rabbitmq.*}。
     */
    @Bean
    @ConditionalOnMissingBean
    public AmqpConnectionDetails gxAmqpConnectionDetails(GXRabbitMQProperties properties, Environment environment) {
        Address address = resolveAddress(properties, environment);
        String username = firstText(environment.getProperty("spring.amqp.username"), properties.getUsername(), null);
        String password = firstText(environment.getProperty("spring.amqp.password"), properties.getPassword(), null);
        return new GXAmqpConnectionDetails(address, username, password);
    }

    /**
     * 解析连接端点：{@code spring.amqp.host/port} → {@code spring.rabbitmq.addresses} 的首个地址 →
     * {@code spring.rabbitmq.host/port}。
     *
     * <p>历史 {@code rabbit.yml} 使用 {@code addresses: host1:5672,host2:5672}，迁移后必须继续生效，
     * 否则会静默回落到 {@code localhost}。</p>
     */
    private static Address resolveAddress(GXRabbitMQProperties properties, Environment environment) {
        String amqpHost = environment.getProperty("spring.amqp.host");
        if (StringUtils.hasText(amqpHost)) {
            return new Address(amqpHost, environment.getProperty("spring.amqp.port", Integer.class, properties.getPort()));
        }
        Address firstFromAddresses = parseFirstAddress(properties.getAddresses());
        if (firstFromAddresses != null) {
            return firstFromAddresses;
        }
        return new Address(properties.getHost(), properties.getPort());
    }

    private static @Nullable Address parseFirstAddress(@Nullable String addresses) {
        if (!StringUtils.hasText(addresses)) {
            return null;
        }
        String first = addresses.split(",")[0].trim();
        int separator = first.lastIndexOf(':');
        if (separator <= 0) {
            return new Address(first, DEFAULT_AMQP_PORT);
        }
        try {
            return new Address(first.substring(0, separator), Integer.parseInt(first.substring(separator + 1)));
        } catch (NumberFormatException e) {
            return new Address(first, DEFAULT_AMQP_PORT);
        }
    }

    /**
     * 补齐 vhost 与重连参数。Boot 的 {@code AmqpProperties} 不包含虚拟主机，
     * 而 RabbitMQ 会用 open 帧的 hostname 字段选择 vhost，缺失时连接会落到错误的虚拟主机。
     */
    @Bean
    public ConnectionOptionsCustomizer gxAmqpConnectionOptionsCustomizer(GXRabbitMQProperties properties,
                                                                        Environment environment) {
        String virtualHost = firstText(environment.getProperty("spring.amqp.virtual-host"),
                environment.getProperty("spring.rabbitmq.virtual-host"), properties.getVirtualHost(), "/");
        return connectionOptions -> configureConnectionOptions(connectionOptions, virtualHost);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("cn.hutool.core.lang", "cn.hutool.core", "cn.maple");
        JacksonJsonMessageConverter converter =
                new JacksonJsonMessageConverter(GXRabbitMQJsonSupport.jsonMapper());
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public GXAmqpMessagePublisher gxAmqpMessagePublisher(AmqpConnectionFactory connectionFactory) {
        return new GXAmqpMessagePublisher(connectionFactory);
    }

    /**
     * 拓扑管理客户端。关闭 {@code maple.framework.rabbitmq.management.enabled} 时不存在该 Bean，
     * 拓扑方法会以明确的异常失败，发送与消费不受影响。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "maple.framework.rabbitmq.management", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public GXAmqpManagementClient gxAmqpManagementClient(GXRabbitMQProperties properties, Environment environment) {
        Address address = resolveAddress(properties, environment);
        String url = firstText(environment.getProperty(MANAGEMENT_URL_PROPERTY),
                "http://" + address.host() + ":15672");
        String username = firstText(environment.getProperty(MANAGEMENT_USERNAME_PROPERTY),
                environment.getProperty("spring.amqp.username"), properties.getUsername(), null);
        String password = firstText(environment.getProperty(MANAGEMENT_PASSWORD_PROPERTY),
                environment.getProperty("spring.amqp.password"), properties.getPassword(), null);
        String virtualHost = firstText(environment.getProperty(MANAGEMENT_VHOST_PROPERTY),
                environment.getProperty("spring.amqp.virtual-host"),
                environment.getProperty("spring.rabbitmq.virtual-host"), properties.getVirtualHost(), "/");
        return new GXAmqpManagementClient(url, virtualHost, username, password,
                GXRabbitMQJsonSupport.jsonMapper());
    }

    /**
     * 覆盖 Boot 默认的监听容器工厂：关闭 autoAccept，让监听器显式决定 accept/reject/requeue，
     * 避免"先 ack 后处理"导致失败消息丢失。
     */
    @Bean(name = AmqpDefaultConfiguration.DEFAULT_AMQP_LISTENER_CONTAINER_FACTORY_BEAN_NAME)
    @ConditionalOnMissingBean(name = AmqpDefaultConfiguration.DEFAULT_AMQP_LISTENER_CONTAINER_FACTORY_BEAN_NAME)
    public MethodAmqpMessageListenerContainerFactory amqpListenerContainerFactory(
            AmqpConnectionFactory connectionFactory,
            @Qualifier("amqpMessageListenerContainerFactoryConfigurer")
            ObjectProvider<AmqpMessageListenerContainerFactoryConfigurer> configurerProvider) {
        MethodAmqpMessageListenerContainerFactory factory =
                new MethodAmqpMessageListenerContainerFactory(connectionFactory);
        configurerProvider.ifAvailable(configurer -> configurer.configure(factory));
        factory.setAutoAccept(false);
        return factory;
    }

    /**
     * 毒消息处理器：消息体无法转换时直接 reject，避免监听器方法还没执行就被无限重投。
     * Boot 的容器工厂配置器会自动应用唯一的一个 {@code AmqpListenerErrorHandler} Bean。
     */
    @Bean
    @ConditionalOnMissingBean
    public GXAmqpListenerErrorHandler gxAmqpListenerErrorHandler() {
        return new GXAmqpListenerErrorHandler();
    }

    private static void configureConnectionOptions(ConnectionOptions connectionOptions, String virtualHost) {
        connectionOptions.virtualHost(virtualHost);
        connectionOptions.reconnectOptions()
                .reconnectEnabled(true)
                .reconnectDelay((int) RECONNECT_DELAY_MILLIS)
                .maxReconnectDelay((int) MAX_RECONNECT_DELAY_MILLIS)
                .useReconnectBackOff(true)
                .reconnectBackOffMultiplier(2.0d);
        connectionOptions.requestTimeout(TimeUnit.SECONDS.toMillis(10));
    }

    private static @Nullable String firstText(@Nullable String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private record GXAmqpConnectionDetails(Address address, @Nullable String username,
                                           @Nullable String password) implements AmqpConnectionDetails {

        @Override
        public Address getAddress() {
            return address;
        }

        @Override
        public @Nullable String getUsername() {
            return username;
        }

        @Override
        public @Nullable String getPassword() {
            return password;
        }
    }
}
