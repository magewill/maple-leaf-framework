package cn.maple.retry.util;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.retry.listener.GXRetryListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * GX 重试工具类，提供基于 Spring Retry 的重试操作封装。
 * <p>
 * 该工具类旨在简化重试逻辑的实现，支持自定义重试次数、退避策略、可重试异常等。
 * 主要特点：
 * <ul>
 *   <li><b>线程安全</b>: 每次重试操作都会创建一个新的 {@link RetryTemplate} 实例，避免了共享状态，确保线程安全。</li>
 *   <li><b>灵活配置</b>: 支持通过参数配置最大重试次数、初始退避时间、退避乘数、最大退避时间等。</li>
 *   <li><b>特定异常重试</b>: 可以指定只对某些特定类型的异常进行重试。</li>
 *   <li><b>恢复操作</b>: 支持在所有重试尝试失败后执行恢复操作 ({@link RecoveryCallback})。</li>
 *   <li><b>指数退避</b>: 默认采用指数退避策略，有助于避免在短时间内对下游服务造成过大压力。</li>
 *   <li><b>日志记录</b>: 配合 {@link GXRetryListener} 可以详细记录重试过程中的事件，便于问题排查。</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 示例1: 基本重试操作，默认配置
 * String result = GXRetryUtil.retryOperation(() -> {
 *     // 可能抛出异常的操作，例如网络请求
 *     System.out.println("Executing operation...");
 *     if (Math.random() < 0.7) { // 模拟70%概率失败
 *         throw new RuntimeException("Operation failed");
 *     }
 *     return "Operation successful";
 * });
 * System.out.println("Result: " + result);
 *
 * // 示例2: 自定义重试次数和退避策略
 * String customResult = GXRetryUtil.retryOperation(() -> {
 *     System.out.println("Executing custom operation...");
 *     if (Math.random() < 0.8) { // 模拟80%概率失败
 *         throw new RuntimeException("Custom operation failed");
 *     }
 *     return "Custom operation successful";
 * }, 5, 2000, 2.0, 30000); // 最多重试5次，初始延迟2秒，乘数2.0，最大延迟30秒
 * System.out.println("Custom Result: " + customResult);
 *
 * // 示例3: 指定特定异常进行重试，并提供恢复操作
 * String specificExceptionResult = GXRetryUtil.retryOperation(() -> {
 *     System.out.println("Executing operation with specific exception handling...");
 *     double random = Math.random();
 *     if (random < 0.5) {
 *         throw new java.io.IOException("Simulated IOException");
 *     } else if (random < 0.8) {
 *         throw new IllegalArgumentException("Simulated IllegalArgumentException");
 *     }
 *     return "Operation successful with specific exception";
 * }, context -> {
 *     System.err.println("All retries failed. Executing recovery callback. Last exception: " + context.getLastThrowable().getMessage());
 *     return "Recovered value after all retries failed";
 * }, Collections.singletonMap(java.io.IOException.class, true)); // 只重试IOException
 * System.out.println("Specific Exception Result: " + specificExceptionResult);
 * }</pre>
 *
 * <h3>最佳实践</h3>
 * <ul>
 *   <li><b>幂等性</b>: 确保被重试的操作是幂等的，即多次执行产生相同的结果或影响。</li>
 *   <li><b>避免无限重试</b>: 合理设置最大重试次数，避免因永久性错误导致无限重试。</li>
 *   <li><b>退避策略</b>: 根据实际场景选择合适的退避策略，指数退避通常是一个不错的选择。</li>
 *   <li><b>异常分类</b>: 仔细考虑哪些异常应该触发重试（通常是瞬时性、可恢复的错误），哪些不应该（例如，参数校验错误、权限问题等）。</li>
 *   <li><b>异步操作</b>: 对于耗时较长的操作，考虑使用异步重试机制 (例如 Spring Retry 的 {@code @Async} 支持或结合 {@link java.util.concurrent.CompletableFuture})，以避免阻塞调用线程。
 *       虽然本工具类本身不直接提供异步执行，但其执行的 {@link RetryCallback} 可以在异步任务中调用。</li>
 * </ul>
 *
 * @author Maple
 * @see RetryTemplate
 * @see RetryPolicy
 * @see ExponentialBackOffPolicy
 * @see GXRetryListener
 */
@Slf4j
public final class GXRetryUtil {
    // 默认重试次数
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    // 默认初始退避时间（毫秒）
    private static final long DEFAULT_INITIAL_INTERVAL = 1000L;

    // 默认退避乘数
    private static final double DEFAULT_MULTIPLIER = 2.0;

    // 默认最大退避时间（毫秒）
    private static final long DEFAULT_MAX_INTERVAL = 10000L;

    // 参数校验常量 定义重试机制的参数范围

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
     * 私有构造函数，防止实例化工具类。
     */
    private GXRetryUtil() {
        throw new UnsupportedOperationException("GXRetryUtil is a utility class and cannot be instantiated.");
    }

    /**
     * 执行带重试逻辑的操作，使用默认的重试策略。
     *
     * @param retryCallback 要执行的业务逻辑，封装在 {@link RetryCallback} 中。
     *                      它定义了实际的操作，并在失败时抛出异常以触发重试。
     * @param <T>           操作的返回类型。
     * @param <E>           操作可能抛出的异常类型，必须是 {@link Throwable} 的子类。
     * @return 操作成功时的返回结果。
     * @throws E 如果所有重试尝试都失败，则抛出最后一次尝试的异常。
     */
    public static <T, E extends Throwable> T retryOperation(RetryCallback<T, E> retryCallback) throws E {
        return retryOperation(retryCallback, null, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_INTERVAL, DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, Collections.emptyMap());
    }

    /**
     * 执行带重试逻辑的操作，使用默认的重试策略。
     *
     * @param retryCallback 要执行的业务逻辑，封装在 {@link RetryCallback} 中。
     *                      它定义了实际的操作，并在失败时抛出异常以触发重试。
     * @param maxAttempts   最大尝试次数 (包括首次尝试)。
     * @param <T>           操作的返回类型。
     * @param <E>           操作可能抛出的异常类型，必须是 {@link Throwable} 的子类。
     * @return 操作成功时的返回结果。
     * @throws E 如果所有重试尝试都失败，则抛出最后一次尝试的异常。
     */
    public static <T, E extends Throwable> T retryOperation(RetryCallback<T, E> retryCallback, int maxAttempts) throws E {
        return retryOperation(retryCallback, null, maxAttempts, DEFAULT_INITIAL_INTERVAL);
    }

    /**
     * 执行带重试逻辑的操作，使用默认的重试策略。
     *
     * @param retryCallback   要执行的业务逻辑，封装在 {@link RetryCallback} 中。
     *                        它定义了实际的操作，并在失败时抛出异常以触发重试。
     * @param maxAttempts     最大尝试次数 (包括首次尝试)。
     * @param initialInterval 初始退避时间 (毫秒)。
     * @param <T>             操作的返回类型。
     * @param <E>             操作可能抛出的异常类型，必须是 {@link Throwable} 的子类。
     * @return 操作成功时的返回结果。
     * @throws E 如果所有重试尝试都失败，则抛出最后一次尝试的异常。
     */
    public static <T, E extends Throwable> T retryOperation(RetryCallback<T, E> retryCallback, int maxAttempts, long initialInterval) throws E {
        return retryOperation(retryCallback, null, maxAttempts, initialInterval);
    }

    /**
     * 执行带重试逻辑的操作，使用默认的重试策略。
     *
     * @param retryCallback    要执行的业务逻辑，封装在 {@link RetryCallback} 中。
     *                         它定义了实际的操作，并在失败时抛出异常以触发重试。
     * @param recoveryCallback 当所有重试尝试都失败后执行的恢复逻辑，封装在 {@link RecoveryCallback} 中。
     *                         它可以用于提供一个默认值或执行清理操作。
     * @param maxAttempts      最大尝试次数 (包括首次尝试)。
     * @param <T>              操作的返回类型。
     * @param <E>              操作可能抛出的异常类型，必须是 {@link Throwable} 的子类。
     * @return 操作成功时的返回结果。
     * @throws E 如果所有重试尝试都失败，则抛出最后一次尝试的异常。
     */
    public static <T, E extends Throwable> T retryOperation(RetryCallback<T, E> retryCallback, RecoveryCallback<T> recoveryCallback, int maxAttempts) throws E {
        return retryOperation(retryCallback, recoveryCallback, maxAttempts, DEFAULT_INITIAL_INTERVAL);
    }

    /**
     * 执行带重试逻辑的操作，使用默认的重试策略。
     *
     * @param retryCallback    要执行的业务逻辑，封装在 {@link RetryCallback} 中。
     *                         它定义了实际的操作，并在失败时抛出异常以触发重试。
     * @param recoveryCallback 当所有重试尝试都失败后执行的恢复逻辑，封装在 {@link RecoveryCallback} 中。
     *                         它可以用于提供一个默认值或执行清理操作。
     * @param maxAttempts      最大尝试次数 (包括首次尝试)。
     * @param initialInterval  初始退避时间 (毫秒)。
     * @param <T>              操作的返回类型。
     * @param <E>              操作可能抛出的异常类型，必须是 {@link Throwable} 的子类。
     * @return 操作成功时的返回结果。
     * @throws E 如果所有重试尝试都失败，则抛出最后一次尝试的异常。
     */
    public static <T, E extends Throwable> T retryOperation(RetryCallback<T, E> retryCallback, RecoveryCallback<T> recoveryCallback, int maxAttempts, long initialInterval) throws E {
        return retryOperation(retryCallback, recoveryCallback, maxAttempts, initialInterval, DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, Collections.emptyMap());
    }

    /**
     * 执行带重试逻辑的操作，使用默认的重试策略，并指定可重试的异常类型。
     *
     * @param retryCallback       要执行的业务逻辑。
     * @param retryableExceptions 一个 Map，键为异常类，值为布尔值 (true 表示可重试，false 表示不可重试)。
     *                            如果为 null 或空，则所有异常都可重试 (除非被全局策略排除)。
     * @param <T>                 操作的返回类型。
     * @param <E>                 操作可能抛出的异常类型。
     * @return 操作成功时的返回结果。
     * @throws E 如果所有重试尝试都失败，则抛出最后一次尝试的异常。
     */
    public static <T, E extends Throwable> T retryOperation(
            RetryCallback<T, E> retryCallback,
            Map<Class<? extends Throwable>, Boolean> retryableExceptions) throws E {
        return retryOperation(retryCallback, null, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_INTERVAL, DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, retryableExceptions);
    }

    /**
     * 执行带重试逻辑的操作，使用默认的重试策略，并提供一个恢复回调函数。
     *
     * @param retryCallback    要执行的业务逻辑。
     * @param recoveryCallback 当所有重试尝试都失败后执行的恢复逻辑，封装在 {@link RecoveryCallback} 中。
     *                         它可以用于提供一个默认值或执行清理操作。
     * @param <T>              操作和恢复回调的返回类型。
     * @param <E>              操作可能抛出的异常类型。
     * @return 如果操作成功，则返回操作结果；如果所有重试都失败，则返回恢复回调的结果。
     * @throws E 如果恢复回调也抛出异常，或者没有提供恢复回调且所有重试都失败。
     */
    public static <T, E extends Throwable> T retryOperation(
            RetryCallback<T, E> retryCallback,
            RecoveryCallback<T> recoveryCallback) throws E {
        return retryOperation(retryCallback, recoveryCallback, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_INTERVAL, DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL, Collections.emptyMap());
    }

    /**
     * 执行带重试逻辑的操作，允许自定义重试次数和退避策略参数，并指定可重试的异常类型。
     *
     * @param retryCallback       要执行的业务逻辑。
     * @param maxAttempts         最大尝试次数 (包括首次尝试)。
     * @param initialInterval     初始退避时间 (毫秒)。
     * @param multiplier          退避乘数，用于指数增长退避时间。
     * @param maxInterval         最大退避时间 (毫秒)，防止退避时间无限增长。
     * @param retryableExceptions 一个 Map，键为异常类，值为布尔值 (true 表示可重试)。
     * @param <T>                 操作的返回类型。
     * @param <E>                 操作可能抛出的异常类型。
     * @return 操作成功时的返回结果。
     * @throws E 如果所有重试尝试都失败，则抛出最后一次尝试的异常。
     */
    public static <T, E extends Throwable> T retryOperation(
            RetryCallback<T, E> retryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryableExceptions) throws E {
        return retryOperation(retryCallback, null, maxAttempts, initialInterval, multiplier, maxInterval, retryableExceptions);
    }

    /**
     * 执行带重试逻辑的操作，允许自定义重试次数和退避策略参数，并提供一个恢复回调函数。
     *
     * @param retryCallback    要执行的业务逻辑。
     * @param recoveryCallback 当所有重试尝试都失败后执行的恢复逻辑。
     * @param maxAttempts      最大尝试次数。
     * @param initialInterval  初始退避时间 (毫秒)。
     * @param multiplier       退避乘数。
     * @param maxInterval      最大退避时间 (毫秒)。
     * @param <T>              操作和恢复回调的返回类型。
     * @param <E>              操作可能抛出的异常类型。
     * @return 如果操作成功，则返回操作结果；如果所有重试都失败，则返回恢复回调的结果。
     * @throws E 如果恢复回调也抛出异常，或者没有提供恢复回调且所有重试都失败。
     */
    public static <T, E extends Throwable> T retryOperation(
            RetryCallback<T, E> retryCallback,
            RecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) throws E {
        return retryOperation(retryCallback, recoveryCallback, maxAttempts, initialInterval, multiplier, maxInterval, Collections.emptyMap());
    }

    /**
     * 执行带重试逻辑的操作，这是所有公共重载方法的核心实现。
     *
     * @param retryCallback    要执行的业务逻辑。
     * @param recoveryCallback 当所有重试尝试都失败后执行的恢复逻辑 (可以为 null)。
     * @param maxAttempts      最大尝试次数。
     * @param initialInterval  初始退避时间 (毫秒)。
     * @param multiplier       退避乘数。
     * @param maxInterval      最大退避时间 (毫秒)。
     * @param retryExceptions  一个 Map，键为异常类，值为布尔值 (true 表示可重试)。
     *                         如果为 null 或空，则默认重试 {@link Exception} 及其子类。
     * @param <T>              操作和恢复回调的返回类型。
     * @param <E>              操作可能抛出的异常类型。
     * @return 如果操作成功，则返回操作结果；如果所有重试都失败且提供了恢复回调，则返回恢复回调的结果。
     * @throws E 如果所有重试都失败且没有提供恢复回调，或者恢复回调也抛出异常。
     */
    public static <T, E extends Throwable> T retryOperation(
            RetryCallback<T, E> retryCallback,
            RecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) throws E {
        // 增强参数校验
        validateParameters(retryCallback, maxAttempts, initialInterval, multiplier, maxInterval);

        // 使用工厂方法创建配置好的RetryTemplate
        var retryTemplate = createRetryTemplate(maxAttempts, initialInterval, multiplier, maxInterval, retryExceptions);

        // 执行重试操作
        // 如果提供了 recoveryCallback，则使用 execute(RetryCallback, RecoveryCallback)
        // 否则，使用 execute(RetryCallback)
        return recoveryCallback != null
                ? retryTemplate.execute(retryCallback, recoveryCallback)
                : retryTemplate.execute(retryCallback);
    }

    /**
     * 异步执行带重试逻辑的操作，使用默认的重试策略。
     * <p>
     * 此方法将重试操作提交到指定的执行器中异步执行，适用于不希望阻塞当前线程的场景。
     * 返回的 {@link CompletableFuture} 可以用于获取结果或处理异常。
     *
     * @param retryCallback 要执行的业务逻辑，封装在 {@link RetryCallback} 中
     * @param executor      用于异步执行的执行器
     * @param <T>           操作的返回类型
     * @param <E>           操作可能抛出的异常类型
     * @return {@link CompletableFuture} 包装的操作结果
     * @since 1.1.0
     */
    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            RetryCallback<T, E> retryCallback, Executor executor) {
        Objects.requireNonNull(retryCallback, "retryCallback 不能为空");
        Objects.requireNonNull(executor, "executor 不能为空");

        return CompletableFuture.supplyAsync(() -> {
            try {
                return retryOperation(retryCallback);
            } catch (Throwable e) {
                throw new GXBusinessException(e.getMessage(), e);
            }
        }, executor);
    }

    /**
     * 异步执行带重试逻辑的操作，支持自定义重试参数。
     *
     * @param retryCallback    要执行的业务逻辑
     * @param recoveryCallback 恢复回调函数
     * @param maxAttempts      最大尝试次数
     * @param initialInterval  初始退避时间
     * @param multiplier       退避乘数
     * @param maxInterval      最大退避时间
     * @param retryExceptions  可重试的异常映射
     * @param executor         用于异步执行的执行器
     * @param <T>              操作的返回类型
     * @param <E>              操作可能抛出的异常类型
     * @return {@link CompletableFuture} 包装的操作结果
     * @since 1.1.0
     */
    public static <T, E extends Throwable> CompletableFuture<T> retryOperationAsync(
            RetryCallback<T, E> retryCallback,
            RecoveryCallback<T> recoveryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions,
            Executor executor) {
        Objects.requireNonNull(retryCallback, "retryCallback 不能为空");
        Objects.requireNonNull(executor, "executor 不能为空");

        return CompletableFuture.supplyAsync(() -> {
            try {
                return retryOperation(retryCallback, recoveryCallback, maxAttempts,
                        initialInterval, multiplier, maxInterval, retryExceptions);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    /**
     * 使用 Supplier 接口执行带重试逻辑的操作，提供更简洁的API。
     * <p>
     * 此方法适用于不需要抛出受检异常的场景，使用 {@link Supplier} 接口
     * 提供更简洁的lambda表达式支持。
     *
     * @param supplier 要执行的业务逻辑，封装在 {@link Supplier} 中
     * @param <T>      操作的返回类型
     * @return 操作成功时的返回结果
     * @throws RuntimeException 如果所有重试尝试都失败
     * @since 1.1.0
     */
    public static <T> T retrySupplier(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier 不能为空");

        return retryOperation(context -> supplier.get());
    }

    /**
     * 使用 Supplier 接口执行带重试逻辑的操作，支持自定义重试参数。
     *
     * @param supplier        要执行的业务逻辑
     * @param maxAttempts     最大尝试次数
     * @param initialInterval 初始退避时间
     * @param <T>             操作的返回类型
     * @return 操作成功时的返回结果
     * @throws RuntimeException 如果所有重试尝试都失败
     * @since 1.1.0
     */
    public static <T> T retrySupplier(Supplier<T> supplier, int maxAttempts, long initialInterval) {
        Objects.requireNonNull(supplier, "supplier 不能为空");

        return retryOperation(context -> supplier.get(), maxAttempts, initialInterval);
    }

    /**
     * 增强的参数校验方法。
     * <p>
     * 对所有输入参数进行全面校验，确保参数的合理性和安全性。
     * 使用现代Java的Objects.requireNonNull进行空值检查。
     *
     * @param retryCallback   重试回调函数
     * @param maxAttempts     最大尝试次数
     * @param initialInterval 初始退避时间
     * @param multiplier      退避乘数
     * @param maxInterval     最大退避时间
     * @param <T>             操作的返回类型
     * @param <E>             操作可能抛出的异常类型
     * @throws IllegalArgumentException 如果参数不合法
     * @since 1.1.0
     */
    private static <T, E extends Throwable> void validateParameters(
            RetryCallback<T, E> retryCallback,
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval) {

        // 使用Objects.requireNonNull进行空值检查
        Objects.requireNonNull(retryCallback, "retryCallback 不能为空");

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

    /**
     * 获取重试异常映射。
     * <p>
     * 此方法用于处理传入的重试异常映射，如果传入的映射为空或null，则返回一个默认的重试异常映射。
     * 默认的重试异常映射包含所有Exception类型，并设置为可重试。
     * <p>
     * 使用现代Java的Map.of()方法创建不可变映射，提高性能和线程安全性。
     *
     * @param retryExceptions 用户定义的重试异常映射，包含一系列需要重试的异常类型及其是否可重试的标志
     * @return 如果传入的映射为空或null，返回默认的重试异常映射；否则返回用户定义的重试异常映射
     */
    private static Map<Class<? extends Throwable>, Boolean> getRetryableExceptionMap(
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {
        // 检查传入的重试异常映射是否为空或null
        if (retryExceptions == null || retryExceptions.isEmpty()) {
            // 使用Java 9+的Map.of()创建不可变映射，性能更好
            return Map.of(Exception.class, true);
        }
        // 如果不为空，直接返回用户定义的重试异常映射
        return retryExceptions;
    }

    /**
     * 创建并配置RetryTemplate的工厂方法。
     * <p>
     * 将RetryTemplate的创建和配置逻辑提取为独立方法，提高代码的可读性和可测试性。
     * 每次调用都创建新的实例，确保线程安全。
     *
     * @param maxAttempts     最大尝试次数
     * @param initialInterval 初始退避时间
     * @param multiplier      退避乘数
     * @param maxInterval     最大退避时间
     * @param retryExceptions 可重试的异常映射
     * @return 配置好的RetryTemplate实例
     * @since 1.1.0
     */
    private static RetryTemplate createRetryTemplate(
            int maxAttempts,
            long initialInterval,
            double multiplier,
            long maxInterval,
            Map<Class<? extends Throwable>, Boolean> retryExceptions) {

        RetryTemplate retryTemplate = new RetryTemplate();

        // 配置退避策略 (ExponentialBackOffPolicy)
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval > 0 ? initialInterval : DEFAULT_INITIAL_INTERVAL);
        backOffPolicy.setMultiplier(multiplier > 0 ? multiplier : DEFAULT_MULTIPLIER);
        backOffPolicy.setMaxInterval(maxInterval > 0 ? maxInterval : DEFAULT_MAX_INTERVAL);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // 配置重试策略 (SimpleRetryPolicy)
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS,
                getRetryableExceptionMap(retryExceptions),
                true, // true 表示遍历异常的父类进行匹配
                false // false 表示对于未在map中指定的异常，不进行重试 (如果map不为空)
        );
        retryTemplate.setRetryPolicy(retryPolicy);

        // 注册自定义的 RetryListener，用于日志记录等
        // 每次调用都创建一个新的 Listener 实例，以确保线程安全和上下文隔离
        retryTemplate.setListeners(new GXRetryListener[]{new GXRetryListener()});

        return retryTemplate;
    }

}
