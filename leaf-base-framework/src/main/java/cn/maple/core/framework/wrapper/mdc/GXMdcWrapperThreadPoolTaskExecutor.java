package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@SuppressWarnings("all")
public class GXMdcWrapperThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
    @Override
    public void execute(Runnable task) {
        super.execute(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public void execute(Runnable task, long startTimeout) {
        super.execute(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()), startTimeout);
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
    public CompletableFuture<Void> submitCompletable(Runnable task) {
        return super.submitCompletable(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    @Override
    public <T> CompletableFuture<T> submitCompletable(Callable<T> task) {
        return super.submitCompletable(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }
}
