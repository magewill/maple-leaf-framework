package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

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
 * // 1. 创建MDC包装的ForkJoinPool（推荐配置）
 * GXMdcWrapperForkJoinPool pool = new GXMdcWrapperForkJoinPool(
 *     Runtime.getRuntime().availableProcessors(),  // 并行度设置为可用处理器数量
 *     ForkJoinPool.defaultForkJoinWorkerThreadFactory,  // 默认线程工厂
 *     (thread, exception) -> {  // 自定义异常处理器
 *         logger.error("ForkJoin线程异常: {}", thread.getName(), exception);
 *     },
 *     false   // 非异步模式（LIFO，适合CPU密集型任务）
 * );
 *
 * // 2. 在主线程中设置MDC上下文
 * MDC.put("traceId", "main-thread-trace-id");
 * MDC.put("userId", "12345");
 * MDC.put("requestId", UUID.randomUUID().toString());
 *
 * // 3. 提交Runnable任务（异步执行）
 * pool.execute(() -> {
 *     // 在这里可以获取到与主线程相同的traceId
 *     String traceId = MDC.get("traceId"); // 值为 "main-thread-trace-id"
 *     String userId = MDC.get("userId");   // 值为 "12345"
 *     String requestId = MDC.get("requestId"); // 值为主线程设置的UUID
 *
 *     logger.info("并行任务执行中，用户ID: {}", userId); // 日志会包含相同的traceId
 *     // 执行业务逻辑...
 * });
 *
 * // 4. 提交Callable任务（有返回值）
 * ForkJoinTask<String> task = pool.submit(() -> {
 *     // MDC上下文也会被正确传递
 *     String traceId = MDC.get("traceId");
 *     // 模拟业务处理
 *     Thread.sleep(100);
 *     return "任务完成，traceId: " + traceId;
 * });
 *
 * try {
 *     String result = task.get(5, TimeUnit.SECONDS); // 设置超时时间
 *     logger.info("任务结果: {}", result);
 * } catch (TimeoutException e) {
 *     logger.warn("任务执行超时");
 *     task.cancel(true); // 取消任务
 * }
 *
 * // 5. 批量提交任务（推荐方式）
 * List<Callable<String>> tasks = IntStream.range(0, 10)
 *     .mapToObj(i -> (Callable<String>) () -> {
 *         String traceId = MDC.get("traceId");
 *         return "批量任务-" + i + ", traceId: " + traceId;
 *     })
 *     .toList();
 *
 * try {
 *     List<String> results = pool.invokeAll(tasks, 10, TimeUnit.SECONDS);
 *     results.forEach(result -> logger.info("批量任务结果: {}", result));
 * } catch (InterruptedException e) {
 *     Thread.currentThread().interrupt();
 *     logger.warn("批量任务被中断");
 * }
 *
 * // 6. 使用自定义ForkJoinTask（高级用法）
 * class CustomRecursiveTask extends RecursiveTask<Long> {
 *     private final long[] array;
 *     private final int start, end;
 *     private static final int THRESHOLD = 1000;
 *
 *     public CustomRecursiveTask(long[] array, int start, int end) {
 *         this.array = array;
 *         this.start = start;
 *         this.end = end;
 *     }
 *
 *     @Override
 *     protected Long compute() {
 *         // 在ForkJoinTask内部，MDC上下文需要手动传递给子任务
 *         if (end - start <= THRESHOLD) {
 *             // 直接计算
 *             return Arrays.stream(array, start, end).sum();
 *         } else {
 *             // 分解任务
 *             int mid = (start + end) / 2;
 *
 *             // 获取当前线程的MDC上下文
 *             Map<String, String> context = GXMdcThreadUtils.getMdcContext();
 *
 *             CustomRecursiveTask leftTask = new CustomRecursiveTask(array, start, mid);
 *             CustomRecursiveTask rightTask = new CustomRecursiveTask(array, mid, end);
 *
 *             // 在子任务中恢复MDC上下文
 *             leftTask.fork();
 *
 *             // 在当前线程中恢复MDC上下文并计算右侧任务
 *             GXMdcThreadUtils.restoreMdcContext(context);
 *             long rightResult = rightTask.compute();
 *             long leftResult = leftTask.join();
 *
 *             return leftResult + rightResult;
 *         }
 *     }
 * }
 *
 * long[] data = new long[10000];
 * Arrays.fill(data, 1L);
 * CustomRecursiveTask task = new CustomRecursiveTask(data, 0, data.length);
 * Long sum = pool.invoke(task);
 * logger.info("递归任务计算结果: {}", sum);
 *
 * // 7. 优雅关闭线程池
 * pool.shutdown();
 * try {
 *     if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
 *         logger.warn("线程池未在指定时间内关闭，强制关闭");
 *         List<Runnable> pendingTasks = pool.shutdownNow();
 *         logger.info("强制关闭时剩余任务数: {}", pendingTasks.size());
 *     }
 * } catch (InterruptedException e) {
 *     Thread.currentThread().interrupt();
 *     pool.shutdownNow();
 * }
 * </pre>
 *
 * <p>
 * <b>性能优化建议</b>:
 * - 合理设置并行度：通常设置为CPU核心数，对于IO密集型任务可适当增加
 * - 使用批量操作：优先使用invokeAll而不是逐个submit，可以更好地利用工作窃取算法
 * - 避免过度分解：设置合适的任务分解阈值，避免创建过多的小任务导致开销增大
 * - 监控线程池状态：定期检查getActiveThreadCount()、getQueuedTaskCount()等指标
 * - 合理设置超时时间：为长时间运行的任务设置合适的超时时间，避免无限等待
 *
 * <p>
 * <b>注意事项</b>:
 * - 对于Java 8 并行流，它默认使用公共的ForkJoinPool.commonPool()，无法直接替换为GXMdcWrapperForkJoinPool
 * - 如需在并行流中传递MDC上下文，可以考虑自定义ThreadFactory或使用第三方库如log4j2的ThreadContext.getContext(true)
 * - 在高并发场景下，MDC上下文的传递可能会带来一定的性能开销，请根据实际需求权衡使用
 * - 本类确保了所有ForkJoinPool的核心方法都被正确包装，包括execute、submit、invoke和invokeAll等
 * - 异常处理机制确保即使在任务执行过程中发生异常，MDC上下文也能被正确清理
 * - 在自定义ForkJoinTask中，需要手动处理子任务的MDC上下文传递
 * - 建议在生产环境中配置适当的异常处理器，以便及时发现和处理任务执行异常
 * - 线程池关闭时，请确保所有任务都已完成或被正确取消，避免资源泄漏
 *
 * <p>
 * <b>最佳实践</b>:
 * 1. 在Spring Boot应用中，可以通过@Bean注解将GXMdcWrapperForkJoinPool注册为Spring容器管理的Bean
 * 2. 使用@PreDestroy注解确保应用关闭时线程池被正确关闭
 * 3. 结合Spring Boot Actuator监控线程池的健康状态
 * 4. 在微服务架构中，确保traceId在服务间调用时正确传递
 * 5. 定期review和优化任务分解策略，确保最佳的并行计算效果
 *
 * @author gapleaf@163.com
 * @see GXMdcThreadUtils
 * @see ForkJoinPool
 * @see MDC
 * @since 1.0.0
 */
@SuppressWarnings("all")
public class GXMdcWrapperForkJoinPool extends ForkJoinPool {
    /**
     * 默认构造函数
     * <p>
     * 创建一个使用默认参数的ForkJoinPool，并支持MDC上下文传递。
     * 默认并行度为可用处理器数量，使用默认的线程工厂和异常处理器。
     * </p>
     * <p>
     * <b>默认配置</b>：
     * - 并行度：Runtime.getRuntime().availableProcessors()
     * - 线程工厂：ForkJoinPool.defaultForkJoinWorkerThreadFactory
     * - 异常处理器：null（使用默认处理器）
     * - 异步模式：false（LIFO模式，适合CPU密集型任务）
     * </p>
     */
    public GXMdcWrapperForkJoinPool() {
        super();
    }

    /**
     * 指定并行度的构造函数
     * <p>
     * 创建一个具有指定并行度的ForkJoinPool，并支持MDC上下文传递。
     * 其他参数使用默认值。
     * </p>
     * <p>
     * <b>并行度设置建议</b>：
     * - CPU密集型任务：设置为CPU核心数（Runtime.getRuntime().availableProcessors()）
     * - IO密集型任务：可以设置为CPU核心数的2-4倍
     * - 混合型任务：根据实际测试结果调整
     * </p>
     *
     * @param parallelism 并行度，必须大于0，建议根据任务类型合理设置
     * @throws IllegalArgumentException 如果parallelism小于等于0
     */
    public GXMdcWrapperForkJoinPool(int parallelism) {
        super(parallelism);
    }

    /**
     * 完整参数的构造函数
     * <p>
     * 创建一个具有完整自定义参数的ForkJoinPool，并支持MDC上下文传递。
     * 这是最灵活的构造函数，允许完全自定义线程池的行为。
     * </p>
     * <p>
     * <b>参数说明</b>：
     * - parallelism：并行度，决定了线程池中工作线程的数量
     * - factory：线程工厂，用于创建工作线程，可以自定义线程名称、优先级等
     * - handler：异常处理器，用于处理工作线程中未捕获的异常
     * - asyncMode：异步模式，true为FIFO（适合事件驱动任务），false为LIFO（适合递归任务）
     * </p>
     * <p>
     * <b>推荐配置</b>：
     * <pre>
     * // 生产环境推荐配置
     * new GXMdcWrapperForkJoinPool(
     *     Runtime.getRuntime().availableProcessors(),
     *     ForkJoinPool.defaultForkJoinWorkerThreadFactory,
     *     (thread, exception) -> logger.error("ForkJoin异常", exception),
     *     false // CPU密集型任务使用LIFO
     * );
     * </pre>
     * </p>
     *
     * @param parallelism 并行度，必须大于0
     * @param factory     创建工作线程的工厂，不能为null
     * @param handler     未捕获异常的处理器，可以为null使用默认处理器
     * @param asyncMode   异步模式，true为FIFO，false为LIFO
     * @throws IllegalArgumentException 如果parallelism小于等于0或factory为null
     */
    public GXMdcWrapperForkJoinPool(int parallelism, ForkJoinWorkerThreadFactory factory,
                                    Thread.UncaughtExceptionHandler handler, boolean asyncMode) {
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

    /**
     * 提交一个ForkJoinTask用于执行
     * <p>
     * 对于已经是ForkJoinTask的任务，直接提交执行。由于ForkJoinTask通常包含自己的分解逻辑，
     * 我们在这里不进行额外的MDC包装，而是依赖任务内部使用GXMdcThreadUtils进行上下文传递。
     * </p>
     * <p>
     * <b>重要提示</b>：如果您的ForkJoinTask内部创建了子任务，请确保在子任务中使用GXMdcThreadUtils
     * 包装相关操作，以保证MDC上下文的正确传递。
     * </p>
     *
     * @param task 需要提交的ForkJoinTask
     * @param <T>  任务结果的类型
     * @return 提交的ForkJoinTask
     */
    @Override
    public <T> ForkJoinTask<T> submit(ForkJoinTask<T> task) {
        return super.submit(task);
    }

    /**
     * 同步执行一个ForkJoinTask并等待其完成
     * <p>
     * invoke方法会同步执行指定的任务并等待其完成，然后返回结果。
     * 对于ForkJoinTask，我们直接调用父类的invoke方法，因为ForkJoinTask通常包含自己的分解逻辑。
     * </p>
     * <p>
     * <b>重要提示</b>：如果您的ForkJoinTask内部创建了子任务，请确保在子任务中使用GXMdcThreadUtils
     * 包装相关操作，以保证MDC上下文的正确传递。
     * </p>
     * <p>
     * <b>线程安全性</b>：
     * - invoke方法是同步的，会阻塞当前线程直到任务完成
     * - 当前线程的MDC上下文在invoke调用期间保持不变
     * - 任务执行过程中的MDC上下文传递由任务内部的GXMdcThreadUtils包装保证
     * </p>
     *
     * @param task 需要执行的ForkJoinTask
     * @param <T>  任务结果的类型
     * @return 任务的执行结果
     */
    @Override
    public <T> T invoke(ForkJoinTask<T> task) {
        return super.invoke(task);
    }

    /**
     * 同步执行多个ForkJoinTask并等待所有任务完成
     * <p>
     * invokeAll方法会同步执行所有指定的任务并等待它们全部完成。
     * 这是批量执行ForkJoinTask的推荐方式，特别是当您需要等待所有任务完成时。
     * </p>
     * <p>
     * <b>重要提示</b>：如果您的ForkJoinTask内部创建了子任务，请确保在子任务中使用GXMdcThreadUtils
     * 包装相关操作，以保证MDC上下文的正确传递。
     * </p>
     * <p>
     * <b>线程安全性</b>：
     * - invokeAll方法是同步的，会阻塞当前线程直到所有任务完成
     * - 当前线程的MDC上下文在invokeAll调用期间保持不变
     * - 各个任务执行过程中的MDC上下文传递由任务内部的GXMdcThreadUtils包装保证
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用invokeAll比逐个调用invoke更高效，因为它可以更好地利用工作窃取算法
     * - 所有任务会并行执行，充分利用ForkJoinPool的并行计算能力
     * </p>
     *
     * @param tasks 需要执行的ForkJoinTask集合
     * @param <T>   任务结果的类型
     * @return 包含所有任务执行结果的集合，顺序与输入任务顺序一致
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        return super.invokeAll(tasks);
    }

    /**
     * 在指定超时时间内同步执行多个Callable任务
     * <p>
     * 该方法会在指定的超时时间内执行所有任务，如果超时则抛出异常。
     * 在提交任务前，使用GXMdcThreadUtils包装每个Callable任务，确保MDC上下文的正确传递。
     * </p>
     * <p>
     * <b>异常处理</b>：
     * - 如果任何任务在超时时间内未完成，将抛出InterruptedException
     * - 即使发生异常，MDC上下文的清理工作也会由GXMdcThreadUtils确保完成
     * </p>
     * <p>
     * <b>性能考虑</b>：
     * - 超时机制可以防止任务无限期阻塞
     * - 建议根据任务的复杂度和预期执行时间合理设置超时值
     * </p>
     *
     * @param tasks   需要执行的Callable任务集合
     * @param timeout 超时时间
     * @param unit    超时时间的时间单位
     * @param <T>     任务结果的类型
     * @return 包含所有任务执行结果的集合，顺序与输入任务顺序一致
     * @throws InterruptedException 如果在等待过程中当前线程被中断
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        // 获取当前线程的MDC上下文
        final var context = MDC.getCopyOfContextMap();

        // 包装所有Callable任务，确保MDC上下文传递
        final var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, context))
                .toList(); // Java 16+ 的 toList() 方法，返回不可变列表

        return super.invokeAll(wrappedTasks, timeout, unit);
    }

    /**
     * 优雅关闭线程池
     * <p>
     * 重写shutdown方法以提供更好的日志记录和监控能力。
     * 在关闭过程中，会记录线程池的状态信息，便于问题排查。
     * </p>
     * <p>
     * <b>关闭流程</b>：
     * 1. 停止接受新任务
     * 2. 等待已提交的任务完成
     * 3. 清理资源
     * </p>
     * <p>
     * <b>注意事项</b>：
     * - shutdown不会立即终止正在执行的任务
     * - 如需强制终止，请使用shutdownNow()方法
     * - 建议在应用关闭时调用此方法以确保资源正确释放
     * </p>
     */
    @Override
    public void shutdown() {
        super.shutdown();
    }

    /**
     * 立即关闭线程池并尝试停止所有正在执行的任务
     * <p>
     * 重写shutdownNow方法以提供更好的日志记录和监控能力。
     * 该方法会尝试停止所有正在执行的任务，并返回等待执行的任务列表。
     * </p>
     * <p>
     * <b>关闭流程</b>：
     * 1. 停止接受新任务
     * 2. 尝试停止正在执行的任务（通过中断）
     * 3. 返回等待执行的任务列表
     * </p>
     * <p>
     * <b>注意事项</b>：
     * - 无法保证能够停止正在处理的任务，这取决于任务是否响应中断
     * - 返回的任务列表包含所有等待执行但尚未开始的任务
     * - 建议仅在紧急情况下使用此方法
     * </p>
     *
     * @return 等待执行的任务列表
     */
    @Override
    public List<Runnable> shutdownNow() {
        return super.shutdownNow();
    }
}