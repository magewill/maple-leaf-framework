package cn.maple.retry.config;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.retry.listener.GXRetryListener;
import cn.maple.retry.util.GXRetryUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring Retry 配置类。
 * <p>
 * 这个配置类主要用于定义默认和自定义的 {@link RetryTemplate} Bean，
 * 以便在应用程序中通过依赖注入的方式使用 Spring Retry 功能。
 * 它配置了默认的重试策略、退避策略以及自定义的监听器。
 * <p>
 * <h3>配置详情:</h3>
 * <ul>
 *   <li><b>默认重试策略 ({@link SimpleRetryPolicy})</b>:
 *     <ul>
 *       <li>最大尝试次数: 3 次 (包括首次尝试)。</li>
 *       <li>可重试异常: 默认情况下，会重试所有 {@link GXBusinessException} 及其子类。</li>
 *     </ul>
 *   </li>
 *   <li><b>默认退避策略 ({@link ExponentialBackOffPolicy})</b>:
 *     <ul>
 *       <li>初始退避间隔: 1000 毫秒 (1 秒)。</li>
 *       <li>退避乘数: 2.0 (每次重试的间隔时间将是前一次的两倍)。</li>
 *       <li>最大退避间隔: 10000 毫秒 (10 秒)，防止退避时间无限增长。</li>
 *     </ul>
 *   </li>
 *   <li><b>自定义监听器 ({@link GXRetryListener})</b>:
 *     <ul>
 *       <li>注册了 {@link GXRetryListener}，用于在重试的各个阶段记录日志，
 *           方便追踪和调试重试过程。</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>线程安全考虑:</h3>
 * {@link RetryTemplate} 本身是线程安全的，可以被多个线程共享使用。
 * 此配置创建的 {@code RetryTemplate} Bean 是一个单例，可以在整个应用程序中安全地注入和使用。
 * 每次执行重试操作时，{@link RetryTemplate} 会创建新的 {@link RetryContext} 对象，确保线程间的隔离。
 * <p>
 * {@link GXRetryUtil} 工具类在每次调用时会创建新的 {@code RetryTemplate} 实例，以提供更灵活的即时配置，
 * 而此处的 Bean 主要用于需要固定默认配置并通过 Spring 管理其生命周期的场景。
 *
 * <h3>性能优化:</h3>
 * <ul>
 *   <li>使用 {@link ConcurrentHashMap} 存储可重试异常配置，提高并发环境下的性能。</li>
 *   <li>指数退避策略在高并发环境下可以有效减轻系统负载，防止雪崩效应。</li>
 *   <li>提供了自定义配置方法，可以根据不同场景优化重试参数。</li>
 * </ul>
 *
 * <h3>使用方法:</h3>
 * <h4>1. 使用默认配置的RetryTemplate</h4>
 * 可以直接在需要重试逻辑的 Spring Bean 中注入默认的 {@code RetryTemplate}:
 * <pre>{@code
 * @Service
 * public class MyService {
 *
 *     private final RetryTemplate retryTemplate;
 *
 *     @Autowired
 *     public MyService(RetryTemplate retryTemplate) {
 *         this.retryTemplate = retryTemplate;
 *     }
 *
 *     public String doSomethingWithRetry() throws Exception {
 *         return retryTemplate.execute(context -> {
 *             // ... 你的业务逻辑 ...
 *             System.out.println("尝试执行操作，尝试次数: " + context.getRetryCount());
 *             if (Math.random() < 0.7) { // 模拟失败
 *                 throw new RuntimeException("模拟错误");
 *             }
 *             return "操作成功";
 *         }, context -> {
 *             // ... 恢复回调逻辑 (可选) ...
 *             System.err.println("所有重试尝试均失败。最后异常: " + context.getLastThrowable().getMessage());
 *             return "降级返回值";
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h4>2. 使用自定义配置的RetryTemplate</h4>
 * 可以使用 {@code createCustomRetryTemplate} 方法创建自定义配置的 {@code RetryTemplate}:
 * <pre>{@code
 * @Service
 * public class CustomRetryService {
 *
 *     private final GXRetryConfig retryConfig;
 *
 *     @Autowired
 *     public CustomRetryService(GXRetryConfig retryConfig) {
 *         this.retryConfig = retryConfig;
 *     }
 *
 *     public void executeWithCustomRetry() {
 *         // 创建只重试特定异常的RetryTemplate
 *         Map<Class<? extends Throwable>, Boolean> retryExceptions = new HashMap<>();
 *         retryExceptions.put(IOException.class, true);
 *         retryExceptions.put(TimeoutException.class, true);
 *         retryExceptions.put(IllegalArgumentException.class, false); // 不重试参数异常
 *
 *         RetryTemplate customTemplate = retryConfig.createCustomRetryTemplate(
 *             5,                // 最大尝试5次
 *             2000L,            // 初始延迟2秒
 *             1.5,              // 退避乘数1.5
 *             30000L,           // 最大延迟30秒
 *             retryExceptions,
 *             "CustomIORetryTemplate"
 *         );
 *
 *         // 使用自定义模板执行重试操作
 *         customTemplate.execute(context -> {
 *             // ... 业务逻辑 ...
 *             return null;
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h4>3. 使用 {@link GXRetryUtil} 工具类</h4>
 * 如果希望使用更灵活的、基于 {@link cn.maple.retry.util.GXRetryUtil} 的编程方式，则不需要直接注入此 Bean，
 * 因为 {@code GXRetryUtil} 会在内部创建和配置其自己的 {@code RetryTemplate} 实例。
 * <pre>{@code
 * // 使用默认配置执行重试操作
 * String result = GXRetryUtil.retryOperation(() -> {
 *     // ... 业务逻辑 ...
 *     return "操作结果";
 * });
 *
 * // 使用自定义配置执行重试操作
 * String customResult = GXRetryUtil.retryOperation(
 *     () -> {
 *         // ... 业务逻辑 ...
 *         return "操作结果";
 *     },
 *     context -> {
 *         // ... 恢复逻辑 ...
 *         return "降级结果";
 *     },
 *     5,                // 最大尝试5次
 *     2000L,            // 初始延迟2秒
 *     1.5,              // 退避乘数1.5
 *     30000L            // 最大延迟30秒
 * );
 * }</pre>
 *
 * <h4>4. 使用 {@code @Retryable} 注解</h4>
 * 这个 Bean 主要为那些希望使用 Spring AOP (例如 {@code @Retryable} 注解) 或直接注入标准 {@code RetryTemplate} 的场景提供便利。
 * <pre>{@code
 * @Service
 * public class AnnotationRetryService {
 *
 *     @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
 *     public String serviceWithRetry(String param) throws ServiceException {
 *         // ... 业务逻辑，可能抛出异常 ...
 *         return "处理结果";
 *     }
 *
 *     @Recover
 *     public String recover(ServiceException e, String param) {
 *         // 所有重试尝试失败后的恢复逻辑
 *         return "降级结果: " + param;
 *     }
 * }
 * }</pre>
 *
 * @author Maple
 * @see RetryTemplate
 * @see SimpleRetryPolicy
 * @see ExponentialBackOffPolicy
 * @see GXRetryListener
 * @see cn.maple.retry.util.GXRetryUtil
 * @since 1.0.0
 */
@Configuration
@EnableRetry
public class GXRetryConfig {
    /**
     * 默认的最大尝试次数 (包括首次尝试)。
     */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * 默认的初始退避间隔时间 (毫秒)。
     */
    private static final long DEFAULT_INITIAL_INTERVAL = 1000L;

    /**
     * 默认的退避乘数。
     */
    private static final double DEFAULT_MULTIPLIER = 2.0;

    /**
     * 默认的最大退避间隔时间 (毫秒)。
     */
    private static final long DEFAULT_MAX_INTERVAL = 10000L;

    /**
     * 参数校验常量 - 定义重试机制的参数范围
     */
    // 最小和最大的重试次数
    private static final int MIN_MAX_ATTEMPTS = 1;
    private static final int MAX_MAX_ATTEMPTS = 100;

    // 最小和最大的初始间隔时间，单位为毫秒
    private static final long MIN_INITIAL_INTERVAL = 100L;
    private static final long MAX_INITIAL_INTERVAL = 300000L; // 5分钟

    // 最小和最大的时间倍增系数
    private static final double MIN_MULTIPLIER = 1.0;
    private static final double MAX_MULTIPLIER = 10.0;

    // 最小和最大的最大间隔时间，单位为毫秒
    private static final long MIN_MAX_INTERVAL = 1000L;
    private static final long MAX_MAX_INTERVAL = 3600000L; // 1小时

    /**
     * 创建并配置一个默认的 {@link RetryTemplate} Bean。
     * <p>
     * 这个 {@code RetryTemplate} 实例配置了标准的重试策略、指数退避策略，
     * 并注册了 {@link GXRetryListener} 用于日志记录。
     * <p>
     * 在多线程环境下，此Bean是线程安全的，可以被多个线程并发使用。
     * {@link RetryTemplate}内部不维护任何可变状态，每次执行操作时会创建新的{@link RetryContext}对象，
     * 确保线程间的隔离。
     *
     * @return 配置好的 {@link RetryTemplate} 实例。
     */
    @Bean
    @ConditionalOnMissingBean(RetryTemplate.class)
    public RetryTemplate retryTemplate() {
        // 使用自定义方法创建默认配置的RetryTemplate
        // 这样可以复用代码，确保默认实例和自定义实例使用相同的创建逻辑
        return createCustomRetryTemplate(
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_INITIAL_INTERVAL,
                DEFAULT_MULTIPLIER,
                DEFAULT_MAX_INTERVAL,
                createDefaultRetryExceptionMap());
    }

    /**
     * 创建默认的重试异常映射。
     * <p>
     * 默认情况下，配置为重试 {@link GXBusinessException} 及其子类异常。
     * 使用 {@link ConcurrentHashMap} 确保线程安全。
     *
     * @return 默认的重试异常映射
     */
    private Map<Class<? extends Throwable>, Boolean> createDefaultRetryExceptionMap() {
        Map<Class<? extends Throwable>, Boolean> retryExceptions = new ConcurrentHashMap<>();
        // 默认重试所有GXBusinessException类型异常
        retryExceptions.put(GXBusinessException.class, true);
        //retryExceptions.put(IOException.class, true);       // 示例：网络IO异常
        //retryExceptions.put(TimeoutException.class, true);  // 示例：超时异常
        return retryExceptions;
    }

    /**
     * 创建自定义配置的 {@link RetryTemplate} 实例。
     * <p>
     * 此方法允许根据特定需求创建定制的重试模板，可以指定最大尝试次数、
     * 退避策略参数以及需要重试的异常类型。
     * <p>
     * 每次调用此方法都会创建一个新的 {@link RetryTemplate} 实例，
     * 确保不同配置之间相互隔离，避免共享状态引起的问题。
     *
     * @param maxAttempts     最大尝试次数 (包括首次尝试)，必须大于等于1且小于等于100
     * @param initialInterval 初始退避间隔 (毫秒)，必须大于等于100ms且小于等于5分钟
     * @param multiplier      退避乘数，必须大于等于1.0且小于等于10.0
     * @param maxInterval     最大退避间隔 (毫秒)，必须大于等于1秒且小于等于1小时
     * @param retryExceptions 可重试的异常映射，键为异常类，值为布尔值 (true表示可重试)
     * @return 配置好的 {@link RetryTemplate} 实例
     * @throws IllegalArgumentException 如果任何参数不符合要求
     */
    public RetryTemplate createCustomRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        // 参数校验
        validateRetryParameters(maxAttempts, initialInterval, multiplier, maxInterval);

        // 确保异常映射不为null
        Map<Class<? extends Throwable>, Boolean> exceptionMap = retryExceptions != null ? retryExceptions : createDefaultRetryExceptionMap();

        // 创建RetryTemplate实例
        final RetryTemplate retryTemplate = new RetryTemplate();

        // 1. 配置重试策略 (SimpleRetryPolicy)
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(maxAttempts, exceptionMap, true);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 2. 配置退避策略 (ExponentialBackOffPolicy)
        //    - 设置初始退避间隔、退避乘数和最大退避间隔
        //    - 指数退避策略在高并发环境下可以有效减轻系统负载
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        // 初始延迟1秒
        backOffPolicy.setInitialInterval(initialInterval);
        // 乘数2.0，下次延迟为上次的2倍
        backOffPolicy.setMultiplier(multiplier);
        // 最大延迟10秒
        backOffPolicy.setMaxInterval(maxInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // 3. 注册自定义的 RetryListener
        // 确保 GXRetryListener 是无状态的，否则应每次新建一个实例
        //    - GXRetryListener 用于记录重试过程中的日志信息
        //    - 每次重试操作都会创建新的上下文，确保线程安全
        //    - 每次创建新的监听器实例，确保上下文隔离
        retryTemplate.setListeners(new GXRetryListener[]{new GXRetryListener()});

        // 注意：默认情况下，RetryTemplate在耗尽重试次数后会抛出最后一次异常
        // 如果需要修改此行为，可以设置：
        // 设置耗尽重试次数后抛出最后一次异常
        retryTemplate.setThrowLastExceptionOnExhausted(true);

        // 注意：默认情况下，RetryTemplate不会在多次调用之间共享上下文
        // 如果需要有状态重试（跨多个调用共享上下文），可以设置：
        //retryTemplate.setRetryContextCache(new MapRetryContextCache());

        return retryTemplate;
    }

    /**
     * 创建自定义配置的 {@link RetryTemplate} 实例，使用默认的异常映射。
     * <p>
     * 此方法是 {@link #createCustomRetryTemplate(int, long, double, long, Map)} 的简化版本，
     * 使用默认的异常映射 (重试 {@link GXBusinessException} 及其子类)。
     *
     * @param maxAttempts     最大尝试次数 (包括首次尝试)
     * @param initialInterval 初始退避间隔 (毫秒)
     * @param multiplier      退避乘数
     * @param maxInterval     最大退避间隔 (毫秒)
     * @return 配置好的 {@link RetryTemplate} 实例
     * @throws IllegalArgumentException 如果任何参数不符合要求
     */
    public RetryTemplate createCustomRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) {
        return createCustomRetryTemplate(
                maxAttempts,
                initialInterval,
                multiplier,
                maxInterval,
                createDefaultRetryExceptionMap());
    }

    /**
     * 验证重试参数的有效性。
     * <p>
     * 此方法检查所有重试参数是否在有效范围内，如果任何参数无效，则抛出异常。
     *
     * @param maxAttempts     最大尝试次数
     * @param initialInterval 初始退避间隔
     * @param multiplier      退避乘数
     * @param maxInterval     最大退避间隔
     * @throws IllegalArgumentException 如果任何参数不符合要求
     */
    private void validateRetryParameters(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) {
        // 校验最大尝试次数
        if (maxAttempts < MIN_MAX_ATTEMPTS || maxAttempts > MAX_MAX_ATTEMPTS) {
            throw new IllegalArgumentException(
                    String.format("maxAttempts 必须在 %d 到 %d 之间，当前值: %d",
                            MIN_MAX_ATTEMPTS, MAX_MAX_ATTEMPTS, maxAttempts));
        }

        // 校验初始退避时间
        if (initialInterval < MIN_INITIAL_INTERVAL || initialInterval > MAX_INITIAL_INTERVAL) {
            throw new IllegalArgumentException(
                    String.format("initialInterval 必须在 %d 到 %d 毫秒之间，当前值: %d",
                            MIN_INITIAL_INTERVAL, MAX_INITIAL_INTERVAL, initialInterval));
        }

        // 校验退避乘数
        if (multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException(
                    String.format("multiplier 必须在 %.1f 到 %.1f 之间，当前值: %.2f",
                            MIN_MULTIPLIER, MAX_MULTIPLIER, multiplier));
        }

        // 校验最大退避时间
        if (maxInterval < MIN_MAX_INTERVAL || maxInterval > MAX_MAX_INTERVAL) {
            throw new IllegalArgumentException(
                    String.format("maxInterval 必须在 %d 到 %d 毫秒之间，当前值: %d",
                            MIN_MAX_INTERVAL, MAX_MAX_INTERVAL, maxInterval));
        }

        // 校验退避时间的逻辑关系
        if (initialInterval > maxInterval) {
            throw new IllegalArgumentException(
                    String.format("initialInterval (%d) 不能大于 maxInterval (%d)",
                            initialInterval, maxInterval));
        }
    }
}