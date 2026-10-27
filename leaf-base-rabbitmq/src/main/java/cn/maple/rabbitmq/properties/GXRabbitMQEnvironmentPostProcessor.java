package cn.maple.rabbitmq.properties;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * 在配置绑定前从 Nacos 拉取 {@code rabbit.yml}，弥补 {@code @NacosConfigurationProperties}
 * 在缺少注解处理器时完全不生效的问题。
 *
 * <p>行为约束：</p>
 * <ol>
 *   <li>仅当 {@code maple.framework.rabbitmq.config-source=nacos} 且配置了 server-addr 时才发起网络调用；</li>
 *   <li>拉取超时 2 秒，任何失败只记录 warn，不阻断启动；</li>
 *   <li>属性源以最低优先级加入，{@code application.yml} 与命令行仍可覆盖；</li>
 *   <li>启动期一次性加载，不支持动态刷新（已在模块文档登记为能力边界）；</li>
 *   <li>日志不含地址全量、账号与口令。</li>
 * </ol>
 */
@Slf4j
public class GXRabbitMQEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    static final String CONFIG_SOURCE_PROPERTY = "maple.framework.rabbitmq.config-source";

    static final String DATA_ID = "rabbit.yml";

    static final String DEFAULT_GROUP = "DEFAULT_GROUP";

    private static final long TIMEOUT_MILLIS = 2000L;

    private static final String PROPERTY_SOURCE_NAME = "nacos-rabbit.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configSource = environment.getProperty(CONFIG_SOURCE_PROPERTY, "nacos");
        if (!"nacos".equalsIgnoreCase(configSource)) {
            return;
        }

        String serverAddr = firstNonBlank(environment.getProperty("spring.cloud.nacos.config.server-addr"),
                environment.getProperty("nacos.config.server-addr"));
        if (!StringUtils.hasText(serverAddr)) {
            log.info("RabbitMQ config source: nacos, dataId={}, loaded=false, reason=no-server-addr", DATA_ID);
            return;
        }

        String group = firstNonBlank(environment.getProperty("spring.cloud.nacos.config.group"),
                environment.getProperty("nacos.config.group"));
        if (!StringUtils.hasText(group)) {
            group = DEFAULT_GROUP;
        }

        Properties properties = new Properties();
        properties.setProperty("serverAddr", serverAddr);
        putIfPresent(properties, "namespace", firstNonBlank(
                environment.getProperty("spring.cloud.nacos.config.namespace"),
                environment.getProperty("nacos.config.namespace")));
        putIfPresent(properties, "username", firstNonBlank(
                environment.getProperty("spring.cloud.nacos.username"),
                environment.getProperty("nacos.config.username")));
        putIfPresent(properties, "password", firstNonBlank(
                environment.getProperty("spring.cloud.nacos.password"),
                environment.getProperty("nacos.config.password")));

        ConfigService configService = null;
        try {
            configService = createConfigService(properties);
            String content = configService.getConfig(DATA_ID, group, TIMEOUT_MILLIS);
            if (!StringUtils.hasText(content)) {
                log.warn("RabbitMQ config source: nacos, dataId={}, group={}, loaded=false, reason=empty-content",
                        DATA_ID, group);
                return;
            }
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load(PROPERTY_SOURCE_NAME, new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
            if (sources.isEmpty()) {
                log.warn("RabbitMQ config source: nacos, dataId={}, group={}, loaded=false, reason=no-property",
                        DATA_ID, group);
                return;
            }
            int keyCount = 0;
            for (PropertySource<?> source : sources) {
                environment.getPropertySources().addLast(source);
                if (source instanceof EnumerablePropertySource<?> enumerable) {
                    keyCount += enumerable.getPropertyNames().length;
                }
            }
            log.info("RabbitMQ config source: nacos, dataId={}, group={}, loaded=true, keys={}",
                    DATA_ID, group, keyCount);
        } catch (Exception e) {
            log.warn("RabbitMQ config source: nacos, dataId={}, group={}, loaded=false, error={}",
                    DATA_ID, group, e.getClass().getName());
        } finally {
            shutdownQuietly(configService);
        }
    }

    /**
     * 便于测试替换的客户端工厂。
     */
    protected ConfigService createConfigService(Properties properties) throws NacosException {
        return NacosFactory.createConfigService(properties);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private static void putIfPresent(Properties properties, String key, String value) {
        if (StringUtils.hasText(value)) {
            properties.setProperty(key, value);
        }
    }

    private static void shutdownQuietly(ConfigService configService) {
        if (configService == null) {
            return;
        }
        try {
            configService.shutDown();
        } catch (Exception e) {
            log.debug("RabbitMQ nacos config client shutdown failed: {}", e.getClass().getName());
        }
    }
}
