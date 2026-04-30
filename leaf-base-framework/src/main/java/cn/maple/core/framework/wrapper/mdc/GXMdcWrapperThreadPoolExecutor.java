package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@SuppressWarnings("all")
public class GXMdcWrapperThreadPoolExecutor extends ThreadPoolExecutor {
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    public void execute(Runnable task) {
        super.execute(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");

        var mdcContext = MDC.getCopyOfContextMap();

        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAll(wrappedTasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");
        Objects.requireNonNull(unit, "TimeUnit cannot be null");

        var mdcContext = MDC.getCopyOfContextMap();

        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAll(wrappedTasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");

        var mdcContext = MDC.getCopyOfContextMap();

        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAny(wrappedTasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");
        Objects.requireNonNull(unit, "TimeUnit cannot be null");

        var mdcContext = MDC.getCopyOfContextMap();

        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAny(wrappedTasks, timeout, unit);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t != null) {
            // logger.error("任务执行异常，traceId: {}", MDC.get("traceId"), t);
        }
    }
}
