package cn.maple.retry.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * GX 自定义 Spring Retry 监听器实现。
 * <p>
 * 该监听器用于在 Spring Retry 执行重试操作的各个阶段记录日志，
 * 帮助开发者追踪重试过程、诊断问题以及监控重试行为。
 * <p>
 * 主要功能：
 * <ul>
 *   <li><b>开启重试日志</b>: 在每次重试操作开始时 ({@link #open}) 记录一条信息，包含重试上下文信息。</li>
 *   <li><b>错误日志</b>: 在每次重试尝试失败并抛出异常时 ({@link #onError}) 记录错误信息，包括尝试次数和异常详情。</li>
 *   <li><b>关闭重试日志</b>: 在重试操作完成时 ({@link #close}) 记录日志，
 *       区分是成功结束还是所有尝试均失败后结束。</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * Spring Retry 的 {@link RetryListener} 的方法通常是在执行重试的同一个线程中被调用的。
 * {@link RetryContext} 对象是与单次 {@code RetryTemplate.execute()} 调用相关联的，因此在监听器方法内部访问它是线程安全的。
 * 本监听器实现本身是无状态的 (stateless)，不持有任何跨重试操作的共享可变数据，因此它是线程安全的，
 * 可以被多个 {@link org.springframework.retry.support.RetryTemplate} 实例安全地共享和使用（尽管通常建议为每个模板或操作创建新的监听器实例，以隔离日志上下文）。
 *
 * <h3>使用方法</h3>
 * 此监听器可以注册到 {@link org.springframework.retry.support.RetryTemplate} 中：
 * <pre>{@code
 * RetryTemplate retryTemplate = new RetryTemplate();
 * // ... 配置 RetryTemplate 的其他策略 ...
 * retryTemplate.setListeners(new RetryListener[]{new GXRetryListener()});
 *
 * // 然后使用 retryTemplate 执行操作
 * retryTemplate.execute(context -> {
 *     // 你的业务逻辑
 *     System.out.println("Attempting operation...");
 *     if (context.getRetryCount() < 2) { // 模拟前两次失败
 *         throw new RuntimeException("Simulated failure on attempt " + context.getRetryCount());
 *     }
 *     return "Operation successful";
 * });
 * }</pre>
 * 或者通过 {@link cn.maple.retry.util.GXRetryUtil} 间接使用，该工具类会自动注册此监听器。
 *
 * @author Maple
 * @see RetryListener
 * @see RetryContext
 * @see org.springframework.retry.support.RetryTemplate
 * @see cn.maple.retry.util.GXRetryUtil
 */
@Slf4j
@Component
public class GXRetryListener implements RetryListener {
    /**
     * 回调类型缓存，用于存储回调类的类型描述信息
     * 使用 ConcurrentHashMap 确保线程安全，同时避免在高并发场景下重复计算类型描述
     */
    private static final Map<Class<?>, String> CALLBACK_TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * 回调类型解析器，用于从回调对象获取更有意义的类型描述
     * 默认实现返回类的简单名称，可以通过继承类覆盖此方法提供更详细的描述
     */
    private static final Function<Class<?>, String> DEFAULT_TYPE_RESOLVER = Class::getSimpleName;

    /**
     * 在重试操作开始时调用。
     * <p>
     * 对于有状态的重试 (stateful retry)，如果父级上下文 (parent context) 存在，
     * 则此方法可能在每次 {@code RetryOperations.execute()} 调用时被多次调用。
     * <p>
     * 此处用于记录重试操作的开始。
     *
     * @param context  当前重试的上下文，包含重试次数等信息。
     * @param callback 即将被执行的 {@link RetryCallback}。
     * @param <T>      回调返回类型。
     * @param <E>      回调可能抛出的异常类型。
     * @return 通常返回 true，表示继续执行回调。如果返回 false，则操作将不会被执行。
     * (注意: 返回 false 的行为取决于具体的 RetryOperations 实现，对于 RetryTemplate，它会中止操作)。
     */
    @Override
    public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
        // 记录重试操作开始，包含上下文ID和尝试次数（通常为0）
        // 使用 context.getAttribute("context.name") 可以获取到 RetryTemplate 设置的名称，如果设置了的话
        // 这里我们简单记录，更复杂的场景可以传递更多上下文信息
        // 仅在首次尝试时记录 "开始"，避免有状态重试时重复记录
        if (context.getRetryCount() == 0) {
            String contextId = getContextId(context);
            int retryCount = context.getRetryCount();
            // 使用完整类名以提供更详细的信息
            String callbackType = getCallbackTypeDescription(callback);

            log.info("GXRetryListener: 开始执行重试操作. 上下文ID: {}, 初始尝试次数: {}, 回调类型: {}",
                    contextId, retryCount, callbackType);
        }
        // 返回 true 表示继续执行操作
        return true;
    }

    /**
     * 在重试操作完成时调用，无论成功还是失败。
     * <p>
     * 此方法在 {@link RetryCallback} 成功返回或在所有重试尝试都失败后抛出异常时被调用。
     *
     * @param context   当前重试的上下文。
     * @param callback  已执行的 {@link RetryCallback}。
     * @param throwable 如果操作最终失败，则为最后一次尝试抛出的异常；如果操作成功，则为 null。
     * @param <T>       回调返回类型。
     * @param <E>       回调可能抛出的异常类型。
     */
    @Override
    public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        String contextId = getContextId(context);
        int totalAttempts = context.getRetryCount() + (throwable == null ? 1 : 0);
        // 使用完整类名以提供更详细的信息
        String callbackType = getCallbackTypeDescription(callback);

        if (throwable == null) {
            // 操作成功完成
            log.info("GXRetryListener: 重试操作成功结束. 上下文ID: {}, 总尝试次数: {}, 回调类型: {}",
                    contextId, totalAttempts, callbackType);
        } else {
            // 所有重试尝试均失败
            log.warn("GXRetryListener: 重试操作最终失败. 上下文ID: {}, 总尝试次数: {}, 回调类型: {}, 最后一次异常: {}: {}",
                    contextId,
                    totalAttempts,
                    callbackType,
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage());

            // 在DEBUG级别记录完整堆栈信息，便于问题排查
            if (log.isDebugEnabled()) {
                log.debug("GXRetryListener: 重试操作失败详细堆栈. 上下文ID: {}", contextId, throwable);
            }
        }
    }

    /**
     * 在 {@link RetryCallback} 抛出异常时调用。
     * <p>
     * 此方法在每次重试尝试失败后被调用。
     *
     * @param context   当前重试的上下文。
     * @param callback  抛出异常的 {@link RetryCallback}。
     * @param throwable 抛出的异常。
     * @param <T>       回调返回类型。
     * @param <E>       回调可能抛出的异常类型。
     */
    @Override
    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        String contextId = getContextId(context);
        // RetryCount 是已失败的次数，当前尝试是其+1
        int currentAttempt = context.getRetryCount() + 1;
        // 使用完整类名以提供更详细的信息
        String callbackType = getCallbackTypeDescription(callback);
        String exceptionClass = throwable.getClass().getSimpleName();
        String exceptionMessage = throwable.getMessage();

        // 记录每次重试失败的日志
        log.warn("GXRetryListener: 重试操作中发生错误. 上下文ID: {}, 当前尝试次数: {}, 回调类型: {}, 异常: {}: {}",
                contextId,
                currentAttempt,
                callbackType,
                exceptionClass,
                exceptionMessage);

        // 在DEBUG级别记录完整堆栈信息，便于问题排查
        if (log.isDebugEnabled()) {
            log.debug("GXRetryListener: 重试尝试失败详细堆栈. 上下文ID: {}, 尝试次数: {}", contextId, currentAttempt, throwable);
        }
    }

    /**
     * 获取回调对象的类型描述
     * <p>
     * 此方法从回调对象获取类型描述，优先使用缓存以提高性能。
     * 对于频繁使用的回调类型，避免重复计算类名，减少内存和CPU开销。
     *
     * @param callback 回调对象
     * @param <T>      回调返回类型
     * @param <E>      回调可能抛出的异常类型
     * @return 回调类型的描述字符串
     */
    private <T, E extends Throwable> String getCallbackTypeDescription(RetryCallback<T, E> callback) {
        if (callback == null) {
            return "<null>";
        }

        // 使用回调对象的类作为缓存键
        Class<?> callbackClass = callback.getClass();

        // 从缓存中获取类型描述，如果不存在则计算并存入缓存
        return CALLBACK_TYPE_CACHE.computeIfAbsent(callbackClass, clazz -> {
            // 使用 Java 17 的 switch 表达式处理不同类型的回调
            // 这里可以根据实际需求扩展，识别特定的回调类型并提供更有意义的描述
            return switch (clazz.getName()) {
                // 处理匿名内部类，提取外部类信息
                case String s when s.contains("$") -> {
                    String[] parts = s.split("\\$");
                    String outerClass = parts[0];
                    try {
                        // 尝试获取外部类的简单名称
                        Class<?> outer = Class.forName(outerClass);
                        yield outer.getSimpleName() + "$Anonymous";
                    } catch (ClassNotFoundException e) {
                        // 如果无法加载外部类，则使用包名+类名的最后部分
                        int lastDot = outerClass.lastIndexOf('.');
                        yield lastDot > 0 ? outerClass.substring(lastDot + 1) + "$Anonymous" : s;
                    }
                }
                // 处理 lambda 表达式
                case String s when s.contains("$$Lambda") ->
                        "Lambda@" + Integer.toHexString(System.identityHashCode(callback));
                // 默认情况下使用类的简单名称
                default -> DEFAULT_TYPE_RESOLVER.apply(clazz);
            };
        });
    }

    /**
     * 获取重试上下文的唯一标识符。
     * <p>
     * 此方法尝试从上下文中提取有意义的标识信息，按以下优先级：
     * 1. 首先尝试获取上下文名称（如果RetryTemplate设置了名称）
     * 2. 然后使用对象的identityHashCode作为唯一标识
     * 3. 如果存在父上下文，添加父上下文信息
     * <p>
     * 这样可以在日志中更容易地追踪和关联相关的重试操作。
     *
     * @param context 重试上下文
     * @return 上下文的标识符字符串
     */
    private String getContextId(RetryContext context) {
        // 尝试从上下文中获取名称，如果 RetryTemplate 设置了名称
        var name = context.getAttribute(RetryContext.NAME);
        if (name instanceof String nameStr && !nameStr.isEmpty()) {
            return nameStr;
        }

        // 使用对象的 identityHashCode 作为一种唯一标识
        var idStr = "ID@" + Integer.toHexString(System.identityHashCode(context));

        // 尝试获取父上下文信息，如果存在
        var parent = context.getParent();
        if (parent != null) {
            var parentId = Integer.toHexString(System.identityHashCode(parent));
            return idStr + "(父上下文:" + parentId + ")";
        }

        return idStr;
    }
}
