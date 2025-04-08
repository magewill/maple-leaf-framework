package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;

import java.util.concurrent.*;

/**
 * GXMdcWrapperThreadPoolExecutor
 * 标准Java线程池的MDC包装类
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
 * 在需要使用Java标准线程池的时候, 使用GXMdcWrapperThreadPoolExecutor类创建线程池去执行相关逻辑，
 * 这样可以确保在异步执行过程中正确传递MDC上下文，保持日志中的traceId一致性。
 *
 * @author gapleaf@163.com
 */
@SuppressWarnings("all")
public class GXMdcWrapperThreadPoolExecutor extends ThreadPoolExecutor {
    /**
     * 基本构造函数
     * <p>
     * 创建一个具有指定核心线程数、最大线程数、保持活动时间和工作队列的线程池，并支持MDC上下文传递。
     * </p>
     *
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 线程空闲时的保持活动时间
     * @param unit 保持活动时间的时间单位
     * @param workQueue 用于保存任务的阻塞队列
     */
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * 带线程工厂的构造函数
     * <p>
     * 创建一个具有指定参数和线程工厂的线程池，并支持MDC上下文传递。
     * 线程工厂用于创建新线程，可以自定义线程的命名、优先级等属性。
     * </p>
     *
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 线程空闲时的保持活动时间
     * @param unit 保持活动时间的时间单位
     * @param workQueue 用于保存任务的阻塞队列
     * @param threadFactory 创建新线程的工厂
     */
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    /**
     * 带拒绝处理器的构造函数
     * <p>
     * 创建一个具有指定参数和拒绝处理器的线程池，并支持MDC上下文传递。
     * 拒绝处理器用于处理线程池无法接受新任务的情况。
     * </p>
     *
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 线程空闲时的保持活动时间
     * @param unit 保持活动时间的时间单位
     * @param workQueue 用于保存任务的阻塞队列
     * @param handler 拒绝执行处理器
     */
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    /**
     * 完整参数的构造函数
     * <p>
     * 创建一个具有所有可配置参数的线程池，并支持MDC上下文传递。
     * </p>
     *
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 线程空闲时的保持活动时间
     * @param unit 保持活动时间的时间单位
     * @param workQueue 用于保存任务的阻塞队列
     * @param threadFactory 创建新线程的工厂
     * @param handler 拒绝执行处理器
     */
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * 执行Runnable任务
     * <p>
     * 在执行任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * 这样在异步执行的任务中打印的日志也会包含相同的traceId，便于追踪完整调用链路。
     * </p>
     *
     * @param task 需要执行的任务
     */
    @Override
    public void execute(Runnable task) {
        super.execute(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    /**
     * 提交一个Runnable任务用于执行，并返回指定结果的Future
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * </p>
     *
     * @param task 需要提交的任务
     * @param result 任务完成后返回的结果
     * @param <T> 结果的类型
     * @return 表示任务的Future
     */
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()), result);
    }

    /**
     * 提交一个Callable任务用于执行，返回表示任务的Future
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * </p>
     *
     * @param task 需要提交的任务
     * @param <T> 任务结果的类型
     * @return 表示任务的Future
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    /**
     * 提交一个Runnable任务用于执行，返回表示任务的Future
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * </p>
     *
     * @param task 需要提交的任务
     * @return 表示任务的Future
     */
    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }
}
