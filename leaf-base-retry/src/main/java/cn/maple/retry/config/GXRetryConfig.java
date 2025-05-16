package cn.maple.retry.config;

import cn.maple.retry.listener.GXRetryListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import javax.annotation.Resource;

/**
 * Spring Retry框架的配置类
 * <p>
 * 该配置类提供了RetryTemplate的默认配置，包括：
 * - 默认的重试策略（最大重试次数为3）
 * - 默认的退避策略（指数退避，初始延迟1秒，最大延迟10秒，乘数为2.0）
 * - 注册自定义的重试监听器
 * <p>
 * 应用可以通过自定义RetryTemplate Bean来覆盖默认配置
 *
 * @author maple
 * @since 1.0.0
 */
@Configuration
public class GXRetryConfig {
    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * 默认初始延迟时间（毫秒）
     */
    private static final long DEFAULT_INITIAL_DELAY = 1000L;

    /**
     * 默认退避乘数
     */
    private static final double DEFAULT_MULTIPLIER = 2.0;

    /**
     * 默认最大延迟时间（毫秒）
     */
    private static final long DEFAULT_MAX_DELAY = 10000L;

    /**
     * 重试监听器
     */
    @Resource
    private GXRetryListener retryListener;

    /**
     * 配置并返回一个带有默认配置的RetryTemplate实例
     * <p>
     * 默认配置：
     * - 最大重试次数：3次
     * - 初始延迟：1000毫秒
     * - 退避乘数：2.0
     * - 最大延迟：10000毫秒
     * <p>
     * 该Bean仅在应用上下文中没有其他RetryTemplate Bean时才会被创建
     *
     * @return RetryTemplate实例，用于执行带有重试机制的操作
     */
    @Bean
    @ConditionalOnMissingBean(RetryTemplate.class)
    public RetryTemplate retryTemplate() {
        // 创建RetryTemplate实例
        RetryTemplate retryTemplate = new RetryTemplate();

        // 配置重试策略
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(DEFAULT_MAX_ATTEMPTS);

        // 配置退避策略
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(DEFAULT_INITIAL_DELAY);
        backOffPolicy.setMultiplier(DEFAULT_MULTIPLIER);
        backOffPolicy.setMaxInterval(DEFAULT_MAX_DELAY);

        // 应用策略到RetryTemplate
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // 注册重试监听器
        retryTemplate.registerListener(retryListener);

        return retryTemplate;
    }
}