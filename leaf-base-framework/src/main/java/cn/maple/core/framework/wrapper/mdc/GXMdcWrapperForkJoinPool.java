package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

@SuppressWarnings("all")
public class GXMdcWrapperForkJoinPool extends ForkJoinPool {
    public GXMdcWrapperForkJoinPool() {
        super();
    }

    public GXMdcWrapperForkJoinPool(int parallelism) {
        super(parallelism);
    }

    public GXMdcWrapperForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory,
                                    Thread.UncaughtExceptionHandler handler, boolean asyncMode) {
        super(parallelism, factory, handler, asyncMode);
    }

    @Override
    public void execute(Runnable task) {
        super.execute(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public <T> ForkJoinTask<T> submit(Runnable task, T result) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()), result);
    }

    @Override
    public <T> ForkJoinTask<T> submit(Callable<T> task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public ForkJoinTask<?> submit(Runnable task) {
        return super.submit(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        return super.submit(task);
    }

    @Override
    public <T> T invoke(ForkJoinTask<T> task) {
        return super.invoke(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        final var context = MDC.getCopyOfContextMap();
        final var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, context))
                .toList();

        return super.invokeAll(wrappedTasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        final var context = MDC.getCopyOfContextMap();

        final var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, context))
                .toList();

        return super.invokeAll(wrappedTasks, timeout, unit);
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return super.shutdownNow();
    }

}
