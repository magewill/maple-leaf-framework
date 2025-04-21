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

/**
 * 异步事件总线中心
 * <p>
 * 提供基于Guava EventBus的异步事件发布-订阅功能
 * 使用自定义线程池处理事件，提高系统吞吐量
 * 该类是线程安全的，可在多线程环境下使用
 * </p>
 *
 * @author maple
 */
@SuppressWarnings("unused")
@Slf4j
public class AsyncEventBusCenter {
    /**
     * 应用名称，用于标识线程池和事件总线
     */
    private static final String APPLICATION_NAME = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);

    /**
     * CPU核心数，用于配置线程池大小
     */
    private static final Integer CPU_CORE_NUMBER = Runtime.getRuntime().availableProcessors();

    /**
     * 线程工厂，用于创建线程池中的线程
     * 设置有意义的线程名前缀，便于问题排查
     */
    private static final ThreadFactory THREAD_FACTORY = ThreadFactoryBuilder.create()
            .setNamePrefix(CharSequenceUtil.format("{}-{}-{}", "async", APPLICATION_NAME, "guava-event-pool"))
            .setDaemon(true) // 设置为守护线程，不阻止JVM退出
            .setUncaughtExceptionHandler((t, e) -> {
                // 处理线程中未捕获的异常，防止线程静默失败
                log.error(CharSequenceUtil.format("Uncaught exception in thread {} : {}", t.getName(), e.getMessage()), e);
            })
            .build();

    /**
     * 线程池执行器，用于异步处理事件
     * 参数配置说明：
     * - 核心线程数：CPU核心数，保持适当的并发度
     * - 最大线程数：CPU核心数的2倍，允许一定程度的线程数扩展
     * - 工作队列容量：CPU核心数 * 2048，避免OOM风险的同时提供足够的缓冲空间
     * - 拒绝策略：CallerRunsPolicy，在线程池满载时，由调用者线程执行任务，避免任务丢失
     */
    private static final ThreadPoolExecutor THREAD_POOL_EXECUTOR = ExecutorBuilder.create()
            .setCorePoolSize(CPU_CORE_NUMBER)
            .setMaxPoolSize(CPU_CORE_NUMBER * 2)
            .setWorkQueue(new LinkedBlockingDeque<>(CPU_CORE_NUMBER * 2048))
            .setThreadFactory(THREAD_FACTORY)
            .setKeepAliveTime(60, TimeUnit.SECONDS) // 空闲线程存活时间
            .setHandler(new ThreadPoolExecutor.CallerRunsPolicy()) // 拒绝策略：调用者运行
            .build();

    /**
     * 异步事件总线实例
     * 使用自定义线程池处理事件，提高系统吞吐量
     * 静态初始化确保线程安全
     */
    private static final AsyncEventBus ASYNC_EVENT_BUS = new AsyncEventBus("async-" + APPLICATION_NAME, THREAD_POOL_EXECUTOR);

    /**
     * 私有构造函数，防止实例化
     * 该类采用静态工厂方法模式提供功能
     */
    private AsyncEventBusCenter() {
        // 防止通过反射方式实例化
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 获取异步事件总线实例
     *
     * @return AsyncEventBus实例
     */
    public static AsyncEventBus getInstance() {
        return ASYNC_EVENT_BUS;
    }

    /**
     * 注册事件订阅者
     * 将包含@Subscribe注解方法的对象注册到事件总线
     *
     * @param obj 事件订阅者对象
     * @throws NullPointerException 如果obj为null
     */
    public static void register(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        ASYNC_EVENT_BUS.register(obj);
    }

    /**
     * 注销事件订阅者
     * 将对象从事件总线中移除，不再接收事件
     *
     * @param obj 事件订阅者对象
     * @throws NullPointerException 如果obj为null
     */
    public static void unregister(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event subscriber cannot be null");
        }
        ASYNC_EVENT_BUS.unregister(obj);
    }

    /**
     * 发布事件
     * 将事件发送给所有注册的订阅者
     * 事件处理在线程池中异步执行
     *
     * @param obj 事件对象
     * @throws NullPointerException 如果obj为null
     */
    public static void post(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Event cannot be null");
        }
        ASYNC_EVENT_BUS.post(obj);
    }
}
