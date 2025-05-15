package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * MDC 线程工具类，用于在多线程环境下安全传递 MDC 上下文。
 * <p>
 * <b>实现原理</b>：
 * MDC（Mapped Diagnostic Context）是 SLF4J 提供的一种日志上下文工具，基于 ThreadLocal 实现。
 * ThreadLocal 的特性决定了每个线程都有独立的 MDC 上下文副本，因此在多线程场景（如线程池、异步任务）中，
 * 子线程无法直接继承父线程的 MDC 上下文，导致日志中的 TraceId 等信息不连续，影响链路追踪。
 * </p>
 * <p>
 * 本工具类通过以下步骤解决该问题：
 * 1. 在父线程中获取当前 MDC 上下文（通过 MDC.getCopyOfContextMap()），并将其传递给子线程。
 * 2. 在子线程执行任务前，将父线程的 MDC 上下文复制到子线程（通过 MDC.setContextMap()）。
 * 3. 如果父线程的上下文为空，则清空子线程的 MDC。
 * 4. 在子线程任务执行完成后，清理 MDC（通过 MDC.clear()），防止上下文泄漏。
 * </p>
 * <p>
 * <b>线程安全机制</b>：
 * - MDC 基于 ThreadLocal 实现，天然线程安全，每个线程的 MDC 上下文互不干扰。
 * - 在子线程中设置和清理 MDC 时，使用 try-finally 块确保 MDC 一定会被清理，即使任务抛出异常。
 * - 父线程的 MDC 上下文通过 Map 传递，复制时使用不可变 Map（Collections.unmodifiableMap）防止意外修改。
 * - 如果父线程的上下文为空，子线程会清空 MDC，避免复用其他任务的上下文。
 * </p>
 * <p>
 * <b>使用场景</b>：
 * 1. 线程池执行异步任务时（如 ExecutorService）。
 * 2. 使用 CompletableFuture 或其他异步编程模型时。
 * 3. 使用并行流（Parallel Stream）或 ForkJoinPool 时。
 * </p>
 * <p>
 * <b>使用示例</b>：
 * <pre>
 * // 主线程中设置 TraceId
 * MDC.put("traceId", "main-thread-trace-id");
 * ExecutorService executor = Executors.newFixedThreadPool(2);
 *
 * // 获取当前 MDC 上下文
 * Map<String, String> context = GXMdcThreadUtils.getMdcContext();
 *
 * // 提交 Runnable 任务
 * executor.execute(GXMdcThreadUtils.wrap(() -> {
 *     System.out.println("Runnable TraceId: " + MDC.get("traceId"));
 * }, context));
 *
 * // 提交 Callable 任务
 * Future<String> future = executor.submit(GXMdcThreadUtils.wrap(() -> {
 *     return "Callable TraceId: " + MDC.get("traceId");
 * }, context));
 *
 * executor.shutdown();
 * </pre>
 * </p>
 * <p>
 * <b>注意事项</b>：
 * 1. 必须在父线程中显式获取 MDC 上下文（通过 getMdcContext()），并传递给子线程。
 * 2. 不要直接修改传递的上下文 Map，否则可能导致线程安全问题。
 * 3. 确保线程池或异步框架支持自定义任务包装（如 ThreadPoolExecutor 的 beforeExecute/afterExecute）。
 * </p>
 *
 * @author gapleaf@163.com
 */
@SuppressWarnings("unused")
public class GXMdcThreadUtils {
    /**
     * 私有构造函数，防止实例化。
     * <p>
     * 本类为工具类，所有方法均为静态方法，不需要实例化。
     * </p>
     */
    private GXMdcThreadUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    /**
     * 设置 TraceId（如果不存在）。
     * <p>
     * 检查当前线程的 MDC 中是否已存在 TraceId，如果不存在则生成并设置一个新的 TraceId。
     * 这确保了每个线程都有一个有效的 TraceId，即使是新创建的线程。
     * </p>
     * <p>
     * <b>线程安全</b>：MDC 基于 ThreadLocal，设置操作仅影响当前线程，天然线程安全。
     * </p>
     */
    public static void setTraceIdIfAbsent() {
        if (CharSequenceUtil.isBlank(GXTraceIdContextUtils.getTraceId())) {
            GXTraceIdContextUtils.setTraceId(GXTraceIdContextUtils.generateTraceId());
        }
    }

    /**
     * 获取当前线程的 MDC 上下文。
     * <p>
     * 该方法返回当前线程的 MDC 上下文副本（不可变 Map），用于传递给子线程。
     * 如果当前线程的 MDC 为空，则返回 null。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC.getCopyOfContextMap() 返回当前线程的 MDC 上下文副本，不会影响其他线程。
     * - 返回的 Map 被包装为不可变 Map（通过 Map.copyOf），防止意外修改。
     * - Java 17+ 中的 Map.copyOf 比 Collections.unmodifiableMap 更高效，因为它创建了一个全新的不可变 Map。
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用 Map.copyOf 替代传统的 Collections.unmodifiableMap，减少内存占用。
     * - 对空 Map 进行快速检查，避免不必要的复制操作。
     * </p>
     *
     * @return 当前线程的 MDC 上下文副本（不可变 Map），如果为空则返回 null
     */
    public static Map<String, String> getMdcContext() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            return null;
        }
        // 返回不可变 Map，防止外部修改
        // Java 17+ 中的 Map.copyOf 比 Collections.unmodifiableMap 更高效
        return Map.copyOf(context);
    }

    /**
     * 包装 Callable 任务，确保 MDC 上下文在子线程中正确传递。
     * <p>
     * <b>实现原理</b>：
     * 1. 在子线程执行任务前，将父线程的 MDC 上下文（context 参数）复制到子线程的 MDC。
     * 2. 如果父线程的上下文为空，则清空子线程的 MDC，避免复用其他任务的上下文。
     * 3. 检查子线程的 TraceId，如果不存在则生成一个新的 TraceId。
     * 4. 在任务执行完成后，清理子线程的 MDC，防止上下文泄漏。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，设置和清理操作仅影响当前线程（子线程），不会干扰其他线程。
     * - 使用 try-finally 块确保 MDC 一定会被清理，即使任务抛出异常。
     * - 传入的 context 是不可变 Map（通过 getMdcContext 获取），避免被修改。
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用 Objects.requireNonNull 进行参数校验，提高代码的健壮性。
     * - 优化 MDC 上下文的设置和恢复逻辑，减少不必要的操作。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * ExecutorService executor = Executors.newFixedThreadPool(2);
     * Map<String, String> context = GXMdcThreadUtils.getMdcContext();
     * Future<String> future = executor.submit(GXMdcThreadUtils.wrap(() -> "Task Result", context));
     * </pre>
     * </p>
     *
     * @param callable 需要包装的 Callable 任务
     * @param context  父线程的 MDC 上下文映射（通过 getMdcContext 获取）
     * @param <T>      Callable 返回值的类型
     * @return 包装后的 Callable 任务
     * @throws NullPointerException 如果 callable 为 null
     */
    public static <T> Callable<T> wrap(final Callable<T> callable, final Map<String, String> context) {
        Objects.requireNonNull(callable, "Callable cannot be null");
        return () -> {
            // 保存子线程的原始 MDC 上下文（如果存在）
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                // 设置子线程的 MDC 上下文
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                // 确保子线程有 TraceId
                setTraceIdIfAbsent();
                // 执行任务
                return callable.call();
            } finally {
                // 清理子线程的 MDC
                MDC.clear();
                // 恢复子线程的原始 MDC 上下文（如果有）
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }

    /**
     * 包装 Runnable 任务，确保 MDC 上下文在子线程中正确传递。
     * <p>
     * <b>实现原理</b>：
     * 1. 在子线程执行任务前，将父线程的 MDC 上下文（context 参数）复制到子线程的 MDC。
     * 2. 如果父线程的上下文为空，则清空子线程的 MDC，避免复用其他任务的上下文。
     * 3. 检查子线程的 TraceId，如果不存在则生成一个新的 TraceId。
     * 4. 在任务执行完成后，清理子线程的 MDC，防止上下文泄漏。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，设置和清理操作仅影响当前线程（子线程），不会干扰其他线程。
     * - 使用 try-finally 块确保 MDC 一定会被清理，即使任务抛出异常。
     * - 传入的 context 是不可变 Map（通过 getMdcContext 获取），避免被修改。
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用 Objects.requireNonNull 进行参数校验，提高代码的健壮性。
     * - 优化 MDC 上下文的设置和恢复逻辑，减少不必要的操作。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * ExecutorService executor = Executors.newFixedThreadPool(2);
     * Map<String, String> context = GXMdcThreadUtils.getMdcContext();
     * executor.execute(GXMdcThreadUtils.wrap(() -> System.out.println("Task"), context));
     * </pre>
     * </p>
     *
     * @param runnable 需要包装的 Runnable 任务
     * @param context  父线程的 MDC 上下文映射（通过 getMdcContext 获取）
     * @return 包装后的 Runnable 任务
     * @throws NullPointerException 如果 runnable 为 null
     */
    public static Runnable wrap(final Runnable runnable, final Map<String, String> context) {
        Objects.requireNonNull(runnable, "Runnable cannot be null");
        return () -> {
            // 保存子线程的原始 MDC 上下文（如果存在）
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                // 设置子线程的 MDC 上下文
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                // 确保子线程有 TraceId
                setTraceIdIfAbsent();
                // 执行任务
                runnable.run();
            } finally {
                // 清理子线程的 MDC
                MDC.clear();
                // 恢复子线程的原始 MDC 上下文（如果有）
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }

    /**
     * 恢复当前线程的 MDC 上下文。
     * <p>
     * 将指定的 MDC 上下文（通常是之前通过 getMdcContext 获取的副本）恢复到当前线程。
     * 如果传入的上下文为 null，则清空当前线程的 MDC。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，设置和清理操作仅影响当前线程，不会干扰其他线程。
     * - 传入的 context 应为不可变 Map（通过 getMdcContext 获取），避免被修改。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * Map<String, String> context = GXMdcThreadUtils.getMdcContext();
     * // 执行一些操作（可能修改了 MDC）
     * GXMdcThreadUtils.restoreMdcContext(context);
     * </pre>
     * </p>
     *
     * @param context 需要恢复的 MDC 上下文（通过 getMdcContext 获取）
     */
    public static void restoreMdcContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    /**
     * 包装 CompletableFuture 的 Supplier，确保 MDC 上下文在异步执行时正确传递。
     * <p>
     * <b>实现原理</b>：
     * 1. 在创建 CompletableFuture 时，将当前线程的 MDC 上下文传递给异步执行的线程。
     * 2. 在异步线程执行任务前，设置 MDC 上下文。
     * 3. 在任务执行完成后，清理 MDC 上下文。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 与 wrap(Callable) 和 wrap(Runnable) 方法类似，使用 try-finally 块确保 MDC 上下文的清理。
     * - 传入的 context 是不可变 Map，避免被修改。
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用 Objects.requireNonNull 进行参数校验，提高代码的健壮性。
     * - 优化 MDC 上下文的设置和恢复逻辑，减少不必要的操作。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * Map<String, String> context = GXMdcThreadUtils.getMdcContext();
     * CompletableFuture<String> future = CompletableFuture.supplyAsync(
     *     GXMdcThreadUtils.wrapSupplier(() -> "Result", context)
     * );
     * </pre>
     * </p>
     *
     * @param supplier 需要包装的 Supplier
     * @param context  父线程的 MDC 上下文映射（通过 getMdcContext 获取）
     * @param <T>      Supplier 返回值的类型
     * @return 包装后的 Supplier
     * @throws NullPointerException 如果 supplier 为 null
     */
    public static <T> Supplier<T> wrapSupplier(final Supplier<T> supplier, final Map<String, String> context) {
        Objects.requireNonNull(supplier, "Supplier cannot be null");
        return () -> {
            // 保存子线程的原始 MDC 上下文（如果存在）
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                // 设置子线程的 MDC 上下文
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                // 确保子线程有 TraceId
                setTraceIdIfAbsent();
                // 执行任务
                return supplier.get();
            } finally {
                // 清理子线程的 MDC
                MDC.clear();
                // 恢复子线程的原始 MDC 上下文（如果有）
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }

    /**
     * 创建一个包含当前 MDC 上下文的 CompletableFuture。
     * <p>
     * 该方法是对 CompletableFuture.supplyAsync 的包装，确保异步任务能够继承当前线程的 MDC 上下文。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 通过 wrapSupplier 方法确保 MDC 上下文的安全传递和清理。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * CompletableFuture<String> future = GXMdcThreadUtils.supplyAsync(() -> {
     *     // 在这里可以安全地访问 MDC 中的 TraceId
     *     return "TraceId: " + GXTraceIdContextUtils.getTraceId();
     * });
     * </pre>
     * </p>
     *
     * @param supplier 异步执行的 Supplier
     * @param <T>      返回值类型
     * @return 包装后的 CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.supplyAsync(wrapSupplier(supplier, context));
    }

    /**
     * 使用指定的执行器创建一个包含当前 MDC 上下文的 CompletableFuture。
     * <p>
     * 该方法是对 CompletableFuture.supplyAsync 的包装，确保异步任务能够继承当前线程的 MDC 上下文，
     * 并使用指定的执行器执行任务。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 通过 wrapSupplier 方法确保 MDC 上下文的安全传递和清理。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * ExecutorService executor = Executors.newFixedThreadPool(2);
     * CompletableFuture<String> future = GXMdcThreadUtils.supplyAsync(() -> {
     *     return "TraceId: " + GXTraceIdContextUtils.getTraceId();
     * }, executor);
     * </pre>
     * </p>
     *
     * @param supplier 异步执行的 Supplier
     * @param executor 执行异步任务的执行器
     * @param <T>      返回值类型
     * @return 包装后的 CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.supplyAsync(wrapSupplier(supplier, context), executor);
    }

    /**
     * 包装 Runnable 任务，创建一个包含当前 MDC 上下文的 CompletableFuture。
     * <p>
     * 该方法是对 CompletableFuture.runAsync 的包装，确保异步任务能够继承当前线程的 MDC 上下文。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 通过 wrap(Runnable) 方法确保 MDC 上下文的安全传递和清理。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * CompletableFuture<Void> future = GXMdcThreadUtils.runAsync(() -> {
     *     // 在这里可以安全地访问 MDC 中的 TraceId
     *     System.out.println("TraceId: " + GXTraceIdContextUtils.getTraceId());
     * });
     * </pre>
     * </p>
     *
     * @param runnable 异步执行的 Runnable
     * @return 包装后的 CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.runAsync(wrap(runnable, context));
    }

    /**
     * 使用指定的执行器，包装 Runnable 任务，创建一个包含当前 MDC 上下文的 CompletableFuture。
     * <p>
     * 该方法是对 CompletableFuture.runAsync 的包装，确保异步任务能够继承当前线程的 MDC 上下文，
     * 并使用指定的执行器执行任务。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 通过 wrap(Runnable) 方法确保 MDC 上下文的安全传递和清理。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * ExecutorService executor = Executors.newFixedThreadPool(2);
     * CompletableFuture<Void> future = GXMdcThreadUtils.runAsync(() -> {
     *     System.out.println("TraceId: " + GXTraceIdContextUtils.getTraceId());
     * }, executor);
     * </pre>
     * </p>
     *
     * @param runnable 异步执行的 Runnable
     * @param executor 执行异步任务的执行器
     * @return 包装后的 CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.runAsync(wrap(runnable, context), executor);
    }

    /**
     * 包装 CompletableFuture 的 thenApply 方法，确保 MDC 上下文在链式调用中正确传递。
     * <p>
     * 该方法用于在 CompletableFuture 链式调用中传递 MDC 上下文，确保每个阶段都能访问到正确的 TraceId。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 通过包装 Function 确保 MDC 上下文的安全传递和清理。
     * - 使用 try-finally 块确保 MDC 上下文的清理，即使函数执行过程中抛出异常。
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 在方法调用时获取 MDC 上下文，而不是在每次函数执行时重新获取，减少上下文复制操作。
     * - 优化 MDC 上下文的设置和恢复逻辑，减少不必要的操作。
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * CompletableFuture<String> future = GXMdcThreadUtils.supplyAsync(() -> "result")
     *     .thenApply(GXMdcThreadUtils.contextWrapper(result -> {
     *         // 在这里可以安全地访问 MDC 中的 TraceId
     *         return result + " with TraceId: " + GXTraceIdContextUtils.getTraceId();
     *     }));
     * </pre>
     * </p>
     *
     * @param function 需要包装的函数
     * @param <T>      输入类型
     * @param <R>      输出类型
     * @return 包装后的函数，可以安全地访问 MDC 上下文
     * @throws NullPointerException 如果 function 为 null
     */
    public static <T, R> Function<T, R> contextWrapper(Function<T, R> function) {
        Objects.requireNonNull(function, "Function cannot be null");
        Map<String, String> context = getMdcContext();
        return input -> {
            // 保存当前线程的原始 MDC 上下文
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                // 设置 MDC 上下文
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                // 确保有 TraceId
                setTraceIdIfAbsent();
                // 执行函数
                return function.apply(input);
            } finally {
                // 清理 MDC
                MDC.clear();
                // 恢复原始 MDC 上下文
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }
}