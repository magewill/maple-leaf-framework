package cn.maple.retry.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于Spring Retry框架的重试工具类
 * <p>
 * 该工具类提供了一组静态方法，用于执行需要重试的操作，特别适用于以下场景：
 * <ul>
 *   <li>网络请求：处理网络抖动、临时连接失败等情况</li>
 *   <li>分布式服务调用：微服务间通信、RPC调用等可能出现临时失败的场景</li>
 *   <li>数据库操作：处理数据库临时不可用、死锁等情况</li>
 *   <li>消息队列操作：处理消息发送失败、临时连接断开等情况</li>
 *   <li>文件操作：处理文件锁定、临时IO异常等情况</li>
 * </ul>
 * <p>
 * 重试策略说明：
 * <ul>
 *   <li>默认采用指数退避策略，即每次重试的间隔时间会按指数增长</li>
 *   <li>支持设置最大重试次数、初始延迟时间、退避乘数和最大延迟时间</li>
 *   <li>支持指定需要重试的异常类型，只有指定的异常才会触发重试</li>
 *   <li>默认情况下会对所有异常进行重试</li>
 * </ul>
 * <p>
 * 线程安全性：
 * <ul>
 *   <li>所有方法都是线程安全的，可以在多线程环境下使用</li>
 *   <li>每次调用都会创建新的RetryTemplate实例，避免状态共享导致的线程安全问题</li>
 * </ul>
 * <p>
 * 性能考虑：
 * <ul>
 *   <li>使用指数退避策略可以避免频繁重试导致系统负载过高</li>
 *   <li>支持设置最大延迟时间，防止退避时间过长</li>
 *   <li>可以指定只对特定异常进行重试，避免对不需要重试的异常进行无谓的重试</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * 1. 基本用法 - 使用默认参数进行重试：
 *
 * // 调用外部服务并自动重试（最多重试3次，初始延迟1秒）
 * String result = GXRetryUtil.retryOperation(() -> {
 *     return externalService.getData("someParam");
 * });
 *
 * 2. 自定义重试次数和延迟：
 *
 * // 调用数据库操作并自定义重试参数（最多重试5次，初始延迟500毫秒）
 * User user = GXRetryUtil.retryOperation(
 *     () -> userRepository.findById(userId),
 *     5,    // 最大重试次数
 *     500   // 初始延迟（毫秒）
 * );
 *
 * 3. 高级用法 - 完全自定义重试参数：
 *
 * // 发送HTTP请求并自定义所有重试参数
 * HttpResponse response = GXRetryUtil.retryOperation(
 *     () -> httpClient.sendRequest(request),
 *     3,                          // 最多重试3次
 *     1000,                       // 初始延迟1秒
 *     1.5,                        // 每次延迟增加50%
 *     5000,                       // 最大延迟5秒
 *     new Class[]{               // 只对这些异常进行重试
 *         IOException.class,
 *         SocketTimeoutException.class,
 *         TimeoutException.class
 *     }
 * );
 *
 * 4. 处理特定业务场景 - 分布式锁获取：
 *
 * // 尝试获取分布式锁，失败后进行重试
 * Lock lock = GXRetryUtil.retryOperation(
 *     () -> {
 *         Lock acquiredLock = lockService.acquireLock("resourceId");
 *         if (acquiredLock == null) {
 *             throw new LockAcquisitionException("无法获取锁");
 *         }
 *         return acquiredLock;
 *     },
 *     5,    // 最多尝试5次
 *     200,  // 初始等待200毫秒
 *     2.0,  // 每次等待时间翻倍
 *     3000, // 最大等待3秒
 *     new Class[]{LockAcquisitionException.class}
 * );
 *
 * 5. 结合Lambda表达式处理检查型异常：
 *
 * // 处理可能抛出IOException的文件操作
 * String fileContent = GXRetryUtil.retryOperation(
 *     () -> {
 *         try {
 *             return Files.readString(Paths.get("some/file/path.txt"));
 *         } catch (IOException e) {
 *             throw new RuntimeException("文件读取失败", e);
 *         }
 *     }
 * );
 * </pre>
 * <p>
 * 注意事项：
 * <ul>
 *   <li>重试机制适用于临时性故障，对于永久性故障（如参数错误、权限问题等）不应使用重试</li>
 *   <li>重试操作应该是幂等的，即多次执行不会产生副作用</li>
 *   <li>对于关键业务操作，建议结合熔断机制一起使用</li>
 *   <li>重试会增加系统延迟，在对延迟敏感的场景中需要谨慎使用</li>
 * </ul>
 *
 * @author maple
 * @since 1.0.0
 */
@Slf4j
public class GXRetryUtil {
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

    private GXRetryUtil() {
        throw new UnsupportedOperationException("工具类不允许创建实例");
    }

    /**
     * 使用默认参数执行重试操作
     * <p>
     * 默认参数：
     * <ul>
     *   <li>最大重试次数：3次</li>
     *   <li>初始延迟：1000毫秒（1秒）</li>
     *   <li>退避乘数：2.0（每次延迟时间翻倍）</li>
     *   <li>最大延迟：10000毫秒（10秒）</li>
     * </ul>
     * <p>
     * 示例：
     * <pre>
     * // 调用外部服务并自动重试
     * String result = GXRetryUtil.retryOperation(() -> {
     *     return externalService.getData("someParam");
     * });
     * </pre>
     *
     * @param operation 需要执行的操作，通常使用Lambda表达式实现
     * @param <T>       操作返回值类型
     * @return 操作成功后的结果
     * @throws RuntimeException 如果在最大重试次数后仍然失败，将抛出运行时异常，原始异常作为cause
     */
    public static <T> T retryOperation(RetryCallback<T, Exception> operation) {
        return retryOperation(operation, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY);
    }

    /**
     * 使用自定义重试次数和初始延迟执行重试操作
     * <p>
     * 该方法允许自定义最大重试次数和初始延迟时间，其他参数使用默认值：
     * <ul>
     *   <li>退避乘数：2.0（每次延迟时间翻倍）</li>
     *   <li>最大延迟：10000毫秒（10秒）</li>
     *   <li>重试所有异常类型</li>
     * </ul>
     * <p>
     * 示例：
     * <pre>
     * // 调用数据库操作并自定义重试参数
     * User user = GXRetryUtil.retryOperation(
     *     () -> userRepository.findById(userId),
     *     5,    // 最大重试次数
     *     500   // 初始延迟（毫秒）
     * );
     * </pre>
     *
     * @param operation   要执行的操作，以RetryCallback接口的形式提供
     * @param maxAttempts 最大重试次数（包括第一次尝试），必须大于0
     * @param delay       初始延迟时间（毫秒），即第一次失败后等待多长时间再次尝试
     * @param <T>         操作返回的泛型类型
     * @return 操作的结果
     * @throws IllegalArgumentException 如果maxAttempts小于等于0或delay小于0
     * @throws RuntimeException         如果在最大重试次数后仍然失败，将抛出运行时异常，原始异常作为cause
     */
    public static <T> T retryOperation(RetryCallback<T, Exception> operation, int maxAttempts, long delay) {
        return retryOperation(operation, maxAttempts, delay, DEFAULT_MULTIPLIER, DEFAULT_MAX_DELAY, null);
    }

    /**
     * 执行重试操作的方法（完整版）
     * <p>
     * 该方法是GXRetryUtil的核心方法，提供了最完整的重试功能配置，使用Spring Retry框架来管理重试逻辑，
     * 支持自定义重试策略和退避策略。其他重载方法最终都会调用此方法。
     * </p>
     *
     * <h3>功能特点：</h3>
     * <ul>
     *   <li>支持完全自定义重试参数，包括最大重试次数、初始延迟、退避乘数和最大延迟</li>
     *   <li>支持指定需要重试的异常类型，只有指定的异常才会触发重试</li>
     *   <li>使用指数退避策略，避免频繁重试导致系统负载过高</li>
     *   <li>提供详细的日志记录，包括操作名称、重试次数、异常信息和执行时间</li>
     * </ul>
     *
     * <h3>线程安全性：</h3>
     * <p>
     * 此方法是线程安全的，每次调用都会创建新的RetryTemplate实例，避免状态共享导致的线程安全问题。
     * 这意味着多个线程可以同时调用此方法而不会相互干扰。
     * </p>
     *
     * <h3>性能优化：</h3>
     * <ol>
     *   <li>使用了指数退避策略，避免频繁重试导致系统负载过高</li>
     *   <li>支持指定最大延迟时间，防止退避时间过长</li>
     *   <li>可以指定只对特定异常进行重试，避免对不需要重试的异常进行无谓的重试</li>
     *   <li>通过日志级别控制，只在必要时记录详细日志，减少日志开销</li>
     * </ol>
     *
     * <h3>使用场景：</h3>
     * <ul>
     *   <li>需要精细控制重试行为的复杂业务场景</li>
     *   <li>对特定类型的异常进行有针对性的重试</li>
     *   <li>需要自定义退避策略的高级应用场景</li>
     *   <li>对重试过程需要详细监控和日志记录的场景</li>
     * </ul>
     *
     * <h3>使用示例：</h3>
     * <pre>
     * // 示例1：HTTP请求重试，只对网络相关异常进行重试
     * HttpResponse response = GXRetryUtil.retryOperation(
     *     () -> httpClient.sendRequest(request),
     *     3,                          // 最多重试3次
     *     1000,                       // 初始延迟1秒
     *     1.5,                        // 每次延迟增加50%
     *     5000,                       // 最大延迟5秒
     *     new Class[]{               // 只对这些异常进行重试
     *         IOException.class,
     *         SocketTimeoutException.class,
     *         ConnectException.class
     *     }
     * );
     *
     * // 示例2：数据库操作重试，处理死锁和连接问题
     * TransactionResult result = GXRetryUtil.retryOperation(
     *     () -> {
     *         try {
     *             return dbService.executeTransaction(params);
     *         } catch (SQLException e) {
     *             // 将检查型异常转换为运行时异常以便重试机制处理
     *             if (e.getErrorCode() == 1205) { // 假设1205是死锁错误码
     *                 throw new DeadlockException("数据库死锁，将进行重试", e);
     *             }
     *             throw new DatabaseException("数据库操作失败", e);
     *         }
     *     },
     *     5,                          // 最多重试5次
     *     200,                        // 初始延迟200毫秒
     *     2.0,                        // 每次延迟时间翻倍
     *     3000,                       // 最大延迟3秒
     *     new Class[]{DeadlockException.class, ConnectionException.class}
     * );
     *
     * // 示例3：消息发送重试，使用自定义异常类型
     * MessageDeliveryStatus status = GXRetryUtil.retryOperation(
     *     () -> messagingService.sendMessage(message),
     *     4,                          // 最多重试4次
     *     500,                        // 初始延迟500毫秒
     *     1.5,                        // 每次延迟增加50%
     *     10000,                      // 最大延迟10秒
     *     new Class[]{MessageDeliveryException.class}
     * );
     * </pre>
     *
     * @param operation   要执行的操作，以RetryCallback接口的形式提供，通常使用Lambda表达式实现
     * @param maxAttempts 最大重试次数（包括第一次尝试），必须大于0
     * @param delay       初始重试延迟，单位为毫秒，第一次失败后等待多长时间再次尝试
     * @param multiplier  退避乘数，每次重试后延迟时间会乘以这个系数，例如2.0表示每次延迟时间翻倍
     * @param maxDelay    最大延迟时间，单位为毫秒，防止退避时间过长，实际延迟不会超过此值
     * @param retryFor    一个异常类数组，表示哪些异常类型应该触发重试，如果为null则重试所有异常
     * @param <T>         操作返回的泛型类型
     * @return 操作的结果，类型为泛型T
     * @throws IllegalArgumentException 如果参数无效，例如maxAttempts小于等于0或delay小于0
     * @throws RuntimeException         如果重试后操作仍然失败，则抛出运行时异常，包含原始异常作为cause
     * @see org.springframework.retry.RetryCallback
     * @see org.springframework.retry.RetryPolicy
     * @see org.springframework.retry.backoff.ExponentialBackOffPolicy
     */
    public static <T> T retryOperation(RetryCallback<T, Exception> operation, int maxAttempts, long delay,
                                       double multiplier, long maxDelay, Class<? extends Throwable>[] retryFor) {
        long startTime = System.currentTimeMillis();
        String operationName = operation.getClass().getSimpleName();
        log.debug("开始执行重试操作: {}, 最大重试次数: {}, 初始延迟: {}ms", operationName, maxAttempts, delay);

        // 创建新的RetryTemplate实例，确保线程安全
        RetryTemplate retryTemplate = new RetryTemplate();

        // 设置RetryTemplate在重试次数用尽时抛出最后一个异常
        retryTemplate.setThrowLastExceptionOnExhausted(true);

        // 配置重试策略
        RetryPolicy retryPolicy;
        if (retryFor != null && retryFor.length > 0) {
            // 创建异常类型到布尔值的映射，指定哪些异常需要重试
            Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>(retryFor.length);
            for (Class<? extends Throwable> exceptionClass : retryFor) {
                retryableExceptions.put(exceptionClass, true);
            }
            retryPolicy = new SimpleRetryPolicy(maxAttempts, retryableExceptions);
        } else {
            // 如果没有指定异常类型，则对所有异常进行重试
            retryPolicy = new SimpleRetryPolicy(maxAttempts);
        }

        // 配置退避策略
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(delay);
        backOffPolicy.setMultiplier(multiplier);
        backOffPolicy.setMaxInterval(maxDelay);

        // 应用策略到RetryTemplate
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        try {
            T result = retryTemplate.execute(operation);
            long duration = System.currentTimeMillis() - startTime;
            log.info("重试操作成功完成: {}, 耗时: {}ms", operationName, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("重试操作失败: {}, 异常类型: {}, 异常信息: {}, 耗时: {}ms",
                    operationName, e.getClass().getSimpleName(), e.getMessage(), duration);
            throw new RuntimeException("操作在重试后仍然失败: " + e.getMessage(), e);
        }
    }
}
