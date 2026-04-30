package cn.maple.core.framework.event.center;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.maple.core.framework.util.GXCommonUtils;
import com.google.common.eventbus.AsyncEventBus;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@Slf4j
public class AsyncEventBusCenter {
    private static final String APPLICATION_NAME = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);

    private static final Integer CPU_CORE_NUMBER = Runtime.getRuntime().availableProcessors();

    private static final ThreadFactory THREAD_FACTORY = ThreadFactoryBuilder.create()
            .setNamePrefix(CharSequenceUtil.format("{}-{}-{}", "async", APPLICATION_NAME, "guava-event-pool"))
            .setDaemon(true)
            .setUncaughtExceptionHandler((t, e) -> {
                log.error(CharSequenceUtil.format("Uncaught exception in thread {} : {}", t.getName(), e.getMessage()), e);
            })
            .build();

    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = ExecutorBuilder.create()
            .setCorePoolSize(CPU_CORE_NUMBER)
            .setMaxPoolSize(CPU_CORE_NUMBER * 2)
            .setWorkQueue(new LinkedBlockingDeque<>(CPU_CORE_NUMBER * 2048))
            .setThreadFactory(THREAD_FACTORY)
            .setKeepAliveTime(60, TimeUnit.SECONDS)
            .setHandler(new ThreadPoolExecutor.CallerRunsPolicy())
            .build();

    private static final AsyncEventBus ASYNC_EVENT_BUS = new AsyncEventBus("async-" + APPLICATION_NAME, THREAD_POOL_EXECUTOR);

    private AsyncEventBusCenter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static AsyncEventBus getInstance() {
        return ASYNC_EVENT_BUS;
    }

    public static void register(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        ASYNC_EVENT_BUS.register(obj);
    }

    public static void unregister(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        ASYNC_EVENT_BUS.unregister(obj);
    }

    public static void post(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event cannot be null");
        }
        ASYNC_EVENT_BUS.post(obj);
    }
}
