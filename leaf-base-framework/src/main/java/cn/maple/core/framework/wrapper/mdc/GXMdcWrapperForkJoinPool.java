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
 * 解决场景:
 * 在生产环境中，查询发现错误日志时，需要定位跟踪到最终问题，但是项目中用到了很多的异步线程池，导致定位问题时根据traceId，只能找到一部分日志内容，无法最终定位到问题所在；
 *
 * <p>
 * 原因:
 * 这是由于MDC的实现是通过ThreadLocal实现的，然后线程池中的线程和用户线程不是同一个线程，所以在异步调用的时候，
 * 用户线程的内容没有同步到线程池线程中，导致最终没有打印出关联的traceId，从而导致最终无法关联相关日志，无法定位到具体问题；
 *
 * <p>
 * 使用方法:
 * 在需要使用ForkJoinPool线程池的时候, 使用GXMdcWrapperForkJoinPool类创建线程池去执行相关逻辑，
 * 这样可以确保在并行计算过程中正确传递MDC上下文，保持日志中的traceId一致性。
 *
 * <p>
 * 工作原理:
 * 本类继承自Java标准库的ForkJoinPool，重写了所有任务提交和执行方法。
 * 在每个方法中，通过GXMdcThreadUtils工具类包装原始任务，确保在任务执行前复制当前线程的MDC上下文到执行线程，
 * 并在任务执行后清理MDC，防止内存泄漏。这样，无论任务在哪个线程执行，都能保持日志中traceId的一致性。
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
     * @param factory 创建工作线程的工厂
     * @param handler 未捕获异常的处理器
     * @param asyncMode 是否使用异步模式（FIFO而非LIFO）
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
     * @param task 需要提交的任务
     * @param result 任务完成后返回的结果
     * @param <T> 结果的类型
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
     * @param <T> 任务结果的类型
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