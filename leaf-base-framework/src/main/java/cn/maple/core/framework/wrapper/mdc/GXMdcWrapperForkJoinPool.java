package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;

import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * GXMdcWrapperForkJoinPool
 * ForkJoinPool的MDC包装类
 *
 * <p>
 * <b>解决场景</b>:
 * 在生产环境中，查询发现错误日志时，需要定位跟踪到最终问题，但是项目中用到了很多的异步线程池，导致定位问题时根据traceId，只能找到一部分日志内容，无法最终定位到问题所在。
 * 特别是在使用ForkJoinPool进行并行计算时，由于任务会被分解为多个子任务并行执行，更容易导致日志上下文丢失。
 *
 * <p>
 * <b>原因分析</b>:
 * 这是由于MDC的实现是通过ThreadLocal实现的，而ThreadLocal的特性决定了每个线程都有独立的变量副本。在并行计算时，
 * ForkJoinPool中的工作线程和用户线程不是同一个线程，用户线程的MDC上下文（包括traceId）没有同步到工作线程中，
 * 导致最终没有打印出关联的traceId，从而导致最终无法关联相关日志，无法定位到具体问题。
 *
 * <p>
 * <b>使用方法</b>:
 * 在需要使用ForkJoinPool线程池的时候, 使用GXMdcWrapperForkJoinPool类替代标准的ForkJoinPool创建线程池去执行相关逻辑，
 * 这样可以确保在并行计算过程中正确传递MDC上下文，保持日志中的traceId一致性。
 *
 * <p>
 * <b>工作原理</b>:
 * 本类继承自Java标准库的ForkJoinPool，重写了所有任务提交和执行方法。
 * 在每个方法中，通过GXMdcThreadUtils工具类包装原始任务，确保在任务执行前复制当前线程的MDC上下文到执行线程，
 * 并在任务执行后清理MDC，防止内存泄漏。这样，无论任务在哪个线程执行，都能保持日志中traceId的一致性。
 *
 * <p>
 * <b>线程安全性</b>:
 * - 本类通过GXMdcThreadUtils工具类确保MDC上下文的安全传递，不会出现上下文混淆或泄漏问题
 * - 在任务执行完成后，会清理线程的MDC上下文，防止内存泄漏
 * - 所有操作都是线程安全的，不会影响其他线程的MDC上下文
 * - 即使在任务分解和合并过程中，也能保持MDC上下文的一致性
 *
 * <p>
 * <b>使用示例</b>:
 * <pre>
 * // 1. 创建MDC包装的ForkJoinPool
 * GXMdcWrapperForkJoinPool pool = new GXMdcWrapperForkJoinPool(
 *     Runtime.getRuntime().availableProcessors(),  // 并行度设置为可用处理器数量
 *     ForkJoinPool.defaultForkJoinWorkerThreadFactory,  // 默认线程工厂
 *     null,  // 默认异常处理器
 *     false   // 非异步模式
 * );
 *
 * // 2. 在主线程中设置MDC上下文
 * MDC.put("traceId", "main-thread-trace-id");
 * MDC.put("userId", "12345");
 *
 * // 3. 提交Runnable任务
 * pool.execute(() -> {
 *     // 在这里可以获取到与主线程相同的traceId
 *     String traceId = MDC.get("traceId"); // 值为 "main-thread-trace-id"
 *     String userId = MDC.get("userId");   // 值为 "12345"
 *
 *     logger.info("并行任务执行中..."); // 日志会包含相同的traceId
 * });
 *
 * // 4. 提交Callable任务
 * ForkJoinTask<String> task = pool.submit(() -> {
 *     // MDC上下文也会被正确传递
 *     return "任务完成，traceId: " + MDC.get("traceId");
 * });
 *
 * // 5. 使用Java 8 并行流时的配置（全局设置）
 * // System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", "自定义的MDC感知线程工厂");
 * // List<String> result = list.parallelStream()
 * //     .map(item -> {
 * //         // 并行流中的操作也会继承MDC上下文
 * //         logger.info("处理项: {}", item);
 * //         return item.toUpperCase();
 * //     })
 * //     .collect(Collectors.toList());
 *
 * // 6. 关闭线程池
 * pool.shutdown();
 * </pre>
 *
 * <p>
 * <b>注意事项</b>:
 * - 对于Java 8 并行流，它默认使用公共的ForkJoinPool.commonPool()，无法直接替换为GXMdcWrapperForkJoinPool
 * - 如需在并行流中传递MDC上下文，可以考虑自定义ThreadFactory或使用第三方库如log4j2的ThreadContext.getContext(true)
 * - 在高并发场景下，MDC上下文的传递可能会带来一定的性能开销，请根据实际需求权衡使用
 *
 * @author gapleaf@163.com
 */
@SuppressWarnings("all")
public class GXMdcWrapperForkJoinPool extends ForkJoinPool {
    /**
     * 默认构造函数
     * <p>
     * 创建一个使用默认参数的ForkJoinPool，并支持MDC上下文传递。
     * </p>
     */
    public GXMdcWrapperForkJoinPool() {
        super();
    }

    /**
     * 指定并行度的构造函数
     * <p>
     * 创建一个具有指定并行度的ForkJoinPool，并支持MDC上下文传递。
     * </p>
     *
     * @param parallelism 并行度，通常设置为可用处理器数量
     */
    public GXMdcWrapperForkJoinPool(int parallelism) {
        super(parallelism);
    }

    /**
     * 完整参数的构造函数
     * <p>
     * 创建一个具有完整自定义参数的ForkJoinPool，并支持MDC上下文传递。
     * </p>
     *
     * @param parallelism 并行度，通常设置为可用处理器数量
     * @param factory     创建工作线程的工厂
     * @param handler     未捕获异常的处理器
     * @param asyncMode   是否使用异步模式（FIFO而非LIFO）
     */
    public GXMdcWrapperForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory, Thread.UncaughtExceptionHandler handler, boolean asyncMode) {
        super(parallelism, factory, handler, asyncMode);
    }

    /**
     * 执行Runnable任务
     * <p>
     * 在执行任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到ForkJoin线程中。
     * 这样在并行执行的任务中打印的日志也会包含相同的traceId，便于追踪完整调用链路。
     * </p>
     *
     * @param task 需要执行的任务
     */
    @Override
    public void execute(Runnable task) {
        super.execute(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    /**
     * 提交一个Runnable任务用于执行，并返回指定结果的ForkJoinTask
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到ForkJoin线程中。
     * </p>
     *
     * @param task   需要提交的任务
     * @param result 任务完成后返回的结果
     * @param <T>    结果的类型
     * @return 表示任务的ForkJoinTask
     */
    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()), result);
    }

    /**
     * 提交一个Callable任务用于执行，返回表示任务的ForkJoinTask
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到ForkJoin线程中。
     * </p>
     *
     * @param task 需要提交的任务
     * @param <T>  任务结果的类型
     * @return 表示任务的ForkJoinTask
     */
    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    /**
     * 提交一个Runnable任务用于执行，返回表示任务的ForkJoinTask
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到ForkJoin线程中。
     * 这个方法是对ForkJoinPool.submit(Runnable)的包装，确保在并行执行时MDC上下文的正确传递。
     * </p>
     *
     * @param task 需要提交的任务
     * @return 表示任务的ForkJoinTask
     */
    @Override
    public ForkJoinTask<?> submit(Runnable task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }
}