package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * GXMdcWrapperThreadPoolTaskExecutor
 * Spring线程池任务执行器的MDC包装类
 *
 * <p>
 * <b>解决场景</b>:
 * 在生产环境中，查询发现错误日志时，需要定位跟踪到最终问题，但是项目中用到了很多的异步线程池，导致定位问题时根据traceId，只能找到一部分日志内容，无法最终定位到问题所在。
 *
 * <p>
 * <b>原因分析</b>:
 * 这是由于MDC的实现是通过ThreadLocal实现的，而ThreadLocal的特性决定了每个线程都有独立的变量副本。在异步调用时，
 * 线程池中的线程和用户线程不是同一个线程，用户线程的MDC上下文（包括traceId）没有同步到线程池线程中，
 * 导致最终没有打印出关联的traceId，从而导致最终无法关联相关日志，无法定位到具体问题。
 *
 * <p>
 * <b>使用方法</b>:
 * 在需要使用Spring线程池的时候, 使用GXMdcWrapperThreadPoolTaskExecutor类替代标准的ThreadPoolTaskExecutor创建线程池去执行相关逻辑，
 * 这样可以确保在异步执行过程中正确传递MDC上下文，保持日志中的traceId一致性。
 *
 * <p>
 * <b>工作原理</b>:
 * 本类继承自Spring框架的ThreadPoolTaskExecutor，重写了所有任务提交和执行方法。
 * 在每个方法中，通过GXMdcThreadUtils工具类包装原始任务，确保在任务执行前复制当前线程的MDC上下文到执行线程，
 * 并在任务执行后清理MDC，防止内存泄漏。这样，无论任务在哪个线程执行，都能保持日志中traceId的一致性。
 *
 * <p>
 * <b>线程安全性</b>:
 * - 本类通过GXMdcThreadUtils工具类确保MDC上下文的安全传递，不会出现上下文混淆或泄漏问题
 * - 在任务执行完成后，会清理线程的MDC上下文，防止内存泄漏
 * - 所有操作都是线程安全的，不会影响其他线程的MDC上下文
 *
 * <p>
 * <b>使用示例</b>:
 * <pre>
 * // 1. 创建MDC包装的线程池执行器
 * GXMdcWrapperThreadPoolTaskExecutor executor = new GXMdcWrapperThreadPoolTaskExecutor();
 * executor.setCorePoolSize(5);
 * executor.setMaxPoolSize(10);
 * executor.setQueueCapacity(100);
 * executor.setThreadNamePrefix("mdc-async-");
 * executor.initialize();
 *
 * // 2. 在主线程中设置MDC上下文
 * MDC.put("traceId", "main-thread-trace-id");
 * MDC.put("userId", "12345");
 *
 * // 3. 提交任务到线程池（MDC上下文会自动传递）
 * executor.execute(() -> {
 *     // 在这里可以获取到与主线程相同的traceId
 *     String traceId = MDC.get("traceId"); // 值为 "main-thread-trace-id"
 *     String userId = MDC.get("userId");   // 值为 "12345"
 *
 *     // 业务逻辑...
 *     logger.info("异步任务执行中..."); // 日志会包含相同的traceId
 * });
 *
 * // 4. 提交有返回值的任务
 * Future<String> future = executor.submit(() -> {
 *     // MDC上下文也会被正确传递
 *     return "任务完成，traceId: " + MDC.get("traceId");
 * });
 *
 * // 5. 在Spring配置中使用
 * // @Bean
 * // public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
 * //     GXMdcWrapperThreadPoolTaskExecutor executor = new GXMdcWrapperThreadPoolTaskExecutor();
 * //     // 配置线程池参数...
 * //     executor.initialize();
 * //     return executor;
 * // }
 * </pre>
 *
 * @author gapleaf@163.com
 */
@SuppressWarnings("all")
public class GXMdcWrapperThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
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
     * 在指定的启动超时时间内执行Runnable任务
     * <p>
     * 在执行任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * </p>
     *
     * @param task         需要执行的任务
     * @param startTimeout 启动超时时间（毫秒）
     */
    @Override
    public void execute(Runnable task, long startTimeout) {
        super.execute(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()), startTimeout);
    }

    /**
     * 提交一个Callable任务用于执行，返回表示任务的未来结果的Future
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * </p>
     *
     * @param task 需要提交的任务
     * @param <T>  任务结果的类型
     * @return 表示任务的未来结果的Future
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

    /**
     * 提交一个Runnable任务用于执行，返回可监听的Future
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * ListenableFuture是Spring的扩展接口，允许添加完成回调。
     * </p>
     *
     * @param task 需要提交的任务
     * @return 表示任务的可监听Future
     */
    @Override
    public CompletableFuture<Void> submitCompletable(Runnable task) {
        return super.submitCompletable(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }

    /**
     * 提交一个Callable任务用于执行，返回可监听的Future
     * <p>
     * 在提交任务前，使用GXMdcThreadUtils包装任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * ListenableFuture是Spring的扩展接口，允许添加完成回调。
     * </p>
     *
     * @param task 需要提交的任务
     * @param <T>  任务结果的类型
     * @return 表示任务的可监听Future
     */
    @Override
    public <T> CompletableFuture<T> submitCompletable(Callable<T> task) {
        return super.submitCompletable(GXMdcThreadUtils.wrap(task, MDC.getCopyOfContextMap()));
    }
}
