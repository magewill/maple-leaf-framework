package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * MDC线程工具类
 * <p>
 * 该工具类用于解决在多线程环境下MDC上下文（特别是TraceId）无法正确传递的问题。
 * 由于MDC基于ThreadLocal实现，在线程池或异步调用场景中，子线程无法自动继承父线程的MDC上下文，
 * 导致日志中的TraceId不连续，影响问题排查和链路追踪。
 * </p>
 * <p>
 * 使用场景：
 * 1. 在使用线程池执行异步任务时
 * 2. 在使用CompletableFuture等异步编程模型时
 * 3. 在使用并行流或ForkJoinPool时
 * </p>
 * <p>
 * 本工具类提供了对Runnable和Callable任务的包装方法，确保在任务执行前复制父线程的MDC上下文到子线程，
 * 并在任务执行完成后清理MDC，防止内存泄漏。
 * </p>
 * 
 * @author gapleaf@163.com
 */
@SuppressWarnings("unused")
public class GXMdcThreadUtils {
    /**
     * 私有构造函数，防止实例化
     */
    private GXMdcThreadUtils() {

    }

    /**
     * 设置TraceId（如果不存在）
     * <p>
     * 检查当前线程的MDC中是否已存在TraceId，如果不存在则生成并设置一个新的TraceId。
     * 这确保了每个线程都有一个有效的TraceId，即使是新创建的线程。
     * </p>
     */
    public static void setTraceIdIfAbsent() {
        if (CharSequenceUtil.isBlank(GXTraceIdContextUtils.getTraceId())) {
            GXTraceIdContextUtils.setTraceId(GXTraceIdContextUtils.generateTraceId());
        }
    }

    /**
     * 包装Callable任务，确保MDC上下文传递
     * <p>
     * 将父线程的MDC上下文复制到子线程，并在任务执行完成后清理MDC。
     * 如果传入的上下文为null，则会清空当前线程的MDC。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 在主线程中获取MDC上下文
     * Map<String, String> context = MDC.getCopyOfContextMap();
     * // 提交任务到线程池时包装Callable
     * Future<Result> future = executor.submit(GXMdcThreadUtils.wrap(myCallable, context));
     * </pre>
     * </p>
     *
     * @param callable 需要包装的Callable任务
     * @param context 父线程的MDC上下文映射
     * @param <T> Callable返回值的类型
     * @return 包装后的Callable任务
     */
    public static <T> Callable<T> wrap(final Callable<T> callable, final Map<String, String> context) {
        return () -> {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            setTraceIdIfAbsent();
            try {
                return callable.call();
            } finally {
                MDC.clear();
            }
        };
    }

    /**
     * 包装Runnable任务，确保MDC上下文传递
     * <p>
     * 将父线程的MDC上下文复制到子线程，并在任务执行完成后清理MDC。
     * 如果传入的上下文为null，则会清空当前线程的MDC。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 在主线程中获取MDC上下文
     * Map<String, String> context = MDC.getCopyOfContextMap();
     * // 提交任务到线程池时包装Runnable
     * executor.execute(GXMdcThreadUtils.wrap(myRunnable, context));
     * </pre>
     * </p>
     *
     * @param runnable 需要包装的Runnable任务
     * @param context 父线程的MDC上下文映射
     * @return 包装后的Runnable任务
     */
    public static Runnable wrap(final Runnable runnable, final Map<String, String> context) {
        return () -> {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            setTraceIdIfAbsent();
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
