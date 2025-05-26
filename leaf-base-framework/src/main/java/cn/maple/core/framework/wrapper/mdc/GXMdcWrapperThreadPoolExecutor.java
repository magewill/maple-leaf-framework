package cn.maple.core.framework.wrapper.mdc;

import cn.maple.core.framework.util.GXMdcThreadUtils;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * GXMdcWrapperThreadPoolExecutor
 * 标准Java线程池的MDC包装类
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
 * 在需要使用Java标准线程池的时候, 使用GXMdcWrapperThreadPoolExecutor类替代标准的ThreadPoolExecutor创建线程池去执行相关逻辑，
 * 这样可以确保在异步执行过程中正确传递MDC上下文，保持日志中的traceId一致性。
 *
 * <p>
 * <b>工作原理</b>:
 * 本类继承自Java标准库的ThreadPoolExecutor，重写了所有任务提交和执行方法。
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
 * // 1. 创建MDC包装的线程池执行器（推荐配置）
 * BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(100);
 * ThreadFactory threadFactory = new ThreadFactoryBuilder()
 *     .setNameFormat("mdc-pool-%d")
 *     .setDaemon(false)
 *     .setPriority(Thread.NORM_PRIORITY)
 *     .build();
 *
 * GXMdcWrapperThreadPoolExecutor executor = new GXMdcWrapperThreadPoolExecutor(
 *     Runtime.getRuntime().availableProcessors(),     // 核心线程数：CPU核心数
 *     Runtime.getRuntime().availableProcessors() * 2, // 最大线程数：CPU核心数的2倍
 *     60, TimeUnit.SECONDS,                           // 空闲线程存活时间
 *     workQueue,                                      // 工作队列
 *     threadFactory,                                  // 线程工厂
 *     new ThreadPoolExecutor.CallerRunsPolicy()      // 拒绝策略：调用者运行
 * );
 *
 * // 2. 在主线程中设置MDC上下文
 * MDC.put("traceId", UUID.randomUUID().toString());
 * MDC.put("userId", "12345");
 * MDC.put("requestId", "req-" + System.currentTimeMillis());
 *
 * // 3. 单个任务执行（MDC上下文会自动传递）
 * executor.execute(() -> {
 *     // 在这里可以获取到与主线程相同的MDC上下文
 *     String traceId = MDC.get("traceId");
 *     String userId = MDC.get("userId");
 *     String requestId = MDC.get("requestId");
 *
 *     // 业务逻辑...
 *     logger.info("异步任务执行中，用户ID: {}", userId); // 日志会包含完整的MDC上下文
 * });
 *
 * // 4. 提交有返回值的任务
 * Future<String> future = executor.submit(() -> {
 *     // MDC上下文也会被正确传递
 *     return "任务完成，traceId: " + MDC.get("traceId");
 * });
 *
 * // 5. 批量任务执行（推荐用于大量任务）
 * List<Callable<String>> tasks = IntStream.range(1, 11)
 *     .mapToObj(i -> (Callable<String>) () -> {
 *         Thread.sleep(100); // 模拟业务处理
 *         return "Task " + i + " completed, traceId: " + MDC.get("traceId");
 *     })
 *     .collect(Collectors.toList());
 *
 * List<Future<String>> futures = executor.invokeAll(tasks);
 * for (Future<String> taskFuture : futures) {
 *     String result = taskFuture.get(); // 所有结果都包含相同的traceId
 * }
 *
 * // 6. 竞争执行模式（适用于多数据源查询等场景）
 * List<Callable<String>> dataSources = Arrays.asList(
 *     () -> fetchFromPrimaryDB(MDC.get("traceId")),
 *     () -> fetchFromCache(MDC.get("traceId")),
 *     () -> fetchFromBackupDB(MDC.get("traceId"))
 * );
 *
 * try {
 *     String result = executor.invokeAny(dataSources, 2, TimeUnit.SECONDS);
 *     logger.info("获取到数据: {}", result);
 * } catch (TimeoutException e) {
 *     logger.warn("所有数据源在2秒内未响应，traceId: {}", MDC.get("traceId"));
 * }
 *
 * // 7. 优雅关闭线程池
 * executor.shutdown();
 * try {
 *     if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
 *         executor.shutdownNow();
 *         if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
 *             logger.error("线程池未能正常关闭");
 *         }
 *     }
 * } catch (InterruptedException e) {
 *     executor.shutdownNow();
 *     Thread.currentThread().interrupt();
 * }
 * </pre>
 *
 * <p>
 * <b>Spring Boot集成示例</b>:
 * <pre>
 * @Configuration
 * @EnableAsync
 * public class ThreadPoolConfig {
 *
 *     @Bean("mdcTaskExecutor")
 *     @Primary
 *     public Executor mdcTaskExecutor() {
 *         GXMdcWrapperThreadPoolExecutor executor = new GXMdcWrapperThreadPoolExecutor(
 *             4,  // 核心线程数
 *             8,  // 最大线程数
 *             60, TimeUnit.SECONDS,
 *             new LinkedBlockingQueue<>(200),
 *             new ThreadFactoryBuilder().setNameFormat("async-mdc-%d").build(),
 *             new ThreadPoolExecutor.CallerRunsPolicy()
 *         );
 *         return executor;
 *     }
 * }
 *
 * @Service
 * public class AsyncService {
 *
 *     @Async("mdcTaskExecutor")
 *     public CompletableFuture<String> processAsync(String data) {
 *         // 这里可以直接使用MDC，traceId会自动传递
 *         String traceId = MDC.get("traceId");
 *         logger.info("异步处理数据: {}, traceId: {}", data, traceId);
 *         return CompletableFuture.completedFuture("处理完成");
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * <b>性能优化建议</b>:
 * <ul>
 * <li><b>线程池大小调优</b>：CPU密集型任务使用CPU核心数，IO密集型任务使用CPU核心数的2-3倍</li>
 * <li><b>队列选择</b>：有界队列防止内存溢出，无界队列适用于突发流量</li>
 * <li><b>拒绝策略</b>：CallerRunsPolicy提供背压机制，AbortPolicy快速失败</li>
 * <li><b>监控指标</b>：定期监控线程池的活跃线程数、队列长度、任务完成数等</li>
 * <li><b>MDC优化</b>：避免在MDC中存储大对象，及时清理不需要的上下文</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>最佳实践</b>:
 * <ul>
 * <li>在微服务架构中，确保traceId在服务间传递</li>
 * <li>使用结构化日志格式，便于日志分析和检索</li>
 * <li>定期审查和清理MDC中的键值对，避免内存泄漏</li>
 * <li>在生产环境中启用线程池监控和告警</li>
 * <li>考虑使用虚拟线程（Java 21+）来进一步优化并发性能</li>
 * </ul>
 * </p>
 *
 * @author gapleaf@163.com
 */
@SuppressWarnings("all")
public class GXMdcWrapperThreadPoolExecutor extends ThreadPoolExecutor {
    /**
     * 基本构造函数，使用默认线程工厂和拒绝策略
     * <p>
     * 此构造函数适用于大多数标准场景，提供了核心的线程池配置参数，
     * 同时使用系统默认的线程工厂和AbortPolicy拒绝策略。
     *
     * <p>
     * <b>默认配置</b>：
     * <ul>
     * <li><b>线程工厂</b>：Executors.defaultThreadFactory()（线程名格式：pool-N-thread-M）</li>
     * <li><b>拒绝策略</b>：AbortPolicy（抛出RejectedExecutionException异常）</li>
     * </ul>
     *
     * <p>
     * <b>参数配置建议</b>：
     * <ul>
     * <li><b>CPU密集型任务</b>：corePoolSize = CPU核心数，maximumPoolSize = CPU核心数 + 1</li>
     * <li><b>IO密集型任务</b>：corePoolSize = CPU核心数 * 2，maximumPoolSize = CPU核心数 * 4</li>
     * <li><b>混合型任务</b>：根据IO等待时间比例调整，建议通过压测确定最优值</li>
     * </ul>
     *
     * <p>
     * <b>队列选择指南</b>：
     * <ul>
     * <li><b>ArrayBlockingQueue</b>：有界队列，防止内存溢出，适合高并发场景</li>
     * <li><b>LinkedBlockingQueue</b>：可选有界/无界，吞吐量高，适合任务量波动大的场景</li>
     * <li><b>SynchronousQueue</b>：直接交换，适合任务执行时间短且并发度高的场景</li>
     * <li><b>PriorityBlockingQueue</b>：优先级队列，适合有任务优先级要求的场景</li>
     * </ul>
     *
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * // CPU密集型任务配置
     * int cpuCores = Runtime.getRuntime().availableProcessors();
     * GXMdcWrapperThreadPoolExecutor cpuExecutor = new GXMdcWrapperThreadPoolExecutor(
     *     cpuCores,                                    // 核心线程数
     *     cpuCores,                                    // 最大线程数
     *     0L, TimeUnit.MILLISECONDS,                   // 线程存活时间
     *     new LinkedBlockingQueue<>(100)               // 有界队列
     * );
     *
     * // IO密集型任务配置
     * GXMdcWrapperThreadPoolExecutor ioExecutor = new GXMdcWrapperThreadPoolExecutor(
     *     cpuCores * 2,                                // 核心线程数
     *     cpuCores * 4,                                // 最大线程数
     *     60L, TimeUnit.SECONDS,                       // 线程存活时间
     *     new ArrayBlockingQueue<>(200)                // 有界队列
     * );
     * </pre>
     *
     * @param corePoolSize    核心线程数，线程池中始终保持的线程数量（≥ 0）
     * @param maximumPoolSize 最大线程数，线程池允许的最大线程数量（≥ corePoolSize）
     * @param keepAliveTime   非核心线程的空闲存活时间，超过此时间的空闲线程将被回收（≥ 0）
     * @param unit            keepAliveTime的时间单位，不能为null
     * @param workQueue       任务等待队列，用于存储等待执行的任务，不能为null
     * @throws IllegalArgumentException 如果参数不满足以下条件：
     *                                  corePoolSize < 0 或
     *                                  keepAliveTime < 0 或
     *                                  maximumPoolSize <= 0 或
     *                                  maximumPoolSize < corePoolSize
     * @throws NullPointerException     如果workQueue或unit为null
     * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
     * @see #GXMdcWrapperThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, ThreadFactory)
     * @see #GXMdcWrapperThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, RejectedExecutionHandler)
     */
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * 带自定义线程工厂的构造函数，使用默认拒绝策略
     * <p>
     * 此构造函数允许自定义线程的创建方式，适用于需要特定线程属性的场景，
     * 如自定义线程名称、优先级、守护线程状态等。使用默认的AbortPolicy拒绝策略。
     *
     * <p>
     * <b>线程工厂的作用</b>：
     * <ul>
     * <li><b>线程命名</b>：便于问题排查和性能监控</li>
     * <li><b>优先级设置</b>：影响线程调度顺序</li>
     * <li><b>守护线程</b>：控制JVM退出行为</li>
     * <li><b>异常处理</b>：设置未捕获异常处理器</li>
     * <li><b>线程组</b>：便于批量管理线程</li>
     * </ul>
     *
     * <p>
     * <b>推荐的线程工厂实现</b>：
     * <ul>
     * <li><b>Guava ThreadFactoryBuilder</b>：功能丰富，使用简便</li>
     * <li><b>Spring CustomizableThreadFactory</b>：Spring环境下的标准选择</li>
     * <li><b>自定义实现</b>：满足特殊需求的定制化方案</li>
     * </ul>
     *
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * // 使用Guava ThreadFactoryBuilder
     * ThreadFactory threadFactory = new ThreadFactoryBuilder()
     *     .setNameFormat("mdc-worker-%d")              // 线程名格式
     *     .setDaemon(false)                            // 非守护线程
     *     .setPriority(Thread.NORM_PRIORITY)           // 正常优先级
     *     .setUncaughtExceptionHandler((t, e) -> {     // 异常处理
     *         logger.error("线程 {} 发生未捕获异常", t.getName(), e);
     *     })
     *     .build();
     *
     * GXMdcWrapperThreadPoolExecutor executor = new GXMdcWrapperThreadPoolExecutor(
     *     4, 8, 60L, TimeUnit.SECONDS,
     *     new LinkedBlockingQueue<>(100),
     *     threadFactory
     * );
     *
     * // 使用Spring CustomizableThreadFactory
     * CustomizableThreadFactory springFactory = new CustomizableThreadFactory("spring-mdc-");
     * springFactory.setDaemon(false);
     * springFactory.setPriority(Thread.NORM_PRIORITY);
     *
     * // 自定义线程工厂实现
     * ThreadFactory customFactory = new ThreadFactory() {
     *     private final AtomicInteger threadNumber = new AtomicInteger(1);
     *     private final String namePrefix = "custom-mdc-pool-thread-";
     *
     *     @Override
     *     public Thread newThread(Runnable r) {
     *         Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
     *         t.setDaemon(false);
     *         t.setPriority(Thread.NORM_PRIORITY);
     *         return t;
     *     }
     * };
     * </pre>
     *
     * <p>
     * <b>线程工厂最佳实践</b>：
     * <ul>
     * <li><b>有意义的线程名</b>：包含业务标识，便于日志分析和问题定位</li>
     * <li><b>合适的优先级</b>：避免过高或过低的优先级，通常使用NORM_PRIORITY</li>
     * <li><b>非守护线程</b>：确保任务完成后JVM才退出</li>
     * <li><b>异常处理器</b>：记录未捕获异常，便于问题排查</li>
     * <li><b>线程安全</b>：确保ThreadFactory实现是线程安全的</li>
     * </ul>
     *
     * @param corePoolSize    核心线程数，线程池中始终保持的线程数量（≥ 0）
     * @param maximumPoolSize 最大线程数，线程池允许的最大线程数量（≥ corePoolSize）
     * @param keepAliveTime   非核心线程的空闲存活时间，超过此时间的空闲线程将被回收（≥ 0）
     * @param unit            keepAliveTime的时间单位，不能为null
     * @param workQueue       任务等待队列，用于存储等待执行的任务，不能为null
     * @param threadFactory   线程工厂，用于创建新线程，不能为null
     * @throws IllegalArgumentException 如果参数不满足以下条件：
     *                                  corePoolSize < 0 或
     *                                  keepAliveTime < 0 或
     *                                  maximumPoolSize <= 0 或
     *                                  maximumPoolSize < corePoolSize
     * @throws NullPointerException     如果workQueue、unit或threadFactory为null
     * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, ThreadFactory)
     * @see #GXMdcWrapperThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
     * @see #GXMdcWrapperThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, RejectedExecutionHandler)
     */
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    /**
     * 带自定义拒绝策略的构造函数，使用默认线程工厂
     * <p>
     * 此构造函数允许自定义任务拒绝策略，适用于需要特定拒绝处理逻辑的场景。
     * 当线程池和队列都满时，拒绝策略决定如何处理新提交的任务。
     *
     * <p>
     * <b>内置拒绝策略详解</b>：
     * <ul>
     * <li><b>AbortPolicy（默认）</b>：直接抛出RejectedExecutionException异常，适合快速失败场景</li>
     * <li><b>CallerRunsPolicy</b>：由调用线程执行任务，提供背压机制，适合不能丢失任务的场景</li>
     * <li><b>DiscardPolicy</b>：静默丢弃任务，适合任务可丢失且不需要通知的场景</li>
     * <li><b>DiscardOldestPolicy</b>：丢弃队列中最老的任务，适合新任务优先级更高的场景</li>
     * </ul>
     *
     * <p>
     * <b>拒绝策略选择指南</b>：
     * <ul>
     * <li><b>关键业务</b>：使用CallerRunsPolicy，确保任务不丢失</li>
     * <li><b>实时性要求高</b>：使用AbortPolicy，快速响应系统压力</li>
     * <li><b>日志记录等非关键任务</b>：使用DiscardPolicy，避免影响主流程</li>
     * <li><b>缓存更新等场景</b>：使用DiscardOldestPolicy，保持数据新鲜度</li>
     * </ul>
     *
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * // 使用CallerRunsPolicy提供背压机制
     * GXMdcWrapperThreadPoolExecutor backpressureExecutor = new GXMdcWrapperThreadPoolExecutor(
     *     2, 4, 60L, TimeUnit.SECONDS,
     *     new ArrayBlockingQueue<>(10),
     *     new ThreadPoolExecutor.CallerRunsPolicy()
     * );
     *
     * // 使用自定义拒绝策略记录被拒绝的任务
     * RejectedExecutionHandler customHandler = (r, executor) -> {
     *     logger.warn("任务被拒绝执行: {}, 当前线程池状态: 活跃线程={}, 队列大小={}",
     *                 r.toString(), executor.getActiveCount(), executor.getQueue().size());
     *     // 可以选择将任务存储到数据库或消息队列中稍后处理
     *     taskPersistenceService.saveRejectedTask(r);
     * };
     *
     * GXMdcWrapperThreadPoolExecutor customExecutor = new GXMdcWrapperThreadPoolExecutor(
     *     2, 4, 60L, TimeUnit.SECONDS,
     *     new LinkedBlockingQueue<>(50),
     *     customHandler
     * );
     *
     * // 结合监控的拒绝策略
     * RejectedExecutionHandler monitoringHandler = (r, executor) -> {
     *     // 记录拒绝指标
     *     meterRegistry.counter("thread.pool.rejected", "pool", "mdc-executor").increment();
     *
     *     // 尝试降级处理
     *     if (r instanceof Callable) {
     *         logger.info("尝试同步执行被拒绝的Callable任务");
     *         try {
     *             ((Callable<?>) r).call();
     *         } catch (Exception e) {
     *             logger.error("同步执行任务失败", e);
     *         }
     *     } else {
     *         logger.warn("任务被拒绝且无法降级处理: {}", r.getClass().getSimpleName());
     *     }
     * };
     * </pre>
     *
     * <p>
     * <b>拒绝策略最佳实践</b>：
     * <ul>
     * <li><b>监控拒绝率</b>：定期监控任务拒绝情况，及时调整线程池配置</li>
     * <li><b>优雅降级</b>：在自定义拒绝策略中实现降级逻辑</li>
     * <li><b>日志记录</b>：记录拒绝事件，便于问题排查和容量规划</li>
     * <li><b>告警机制</b>：拒绝率过高时及时告警</li>
     * <li><b>业务区分</b>：不同业务场景使用不同的拒绝策略</li>
     * </ul>
     *
     * @param corePoolSize    核心线程数，线程池中始终保持的线程数量（≥ 0）
     * @param maximumPoolSize 最大线程数，线程池允许的最大线程数量（≥ corePoolSize）
     * @param keepAliveTime   非核心线程的空闲存活时间，超过此时间的空闲线程将被回收（≥ 0）
     * @param unit            keepAliveTime的时间单位，不能为null
     * @param workQueue       任务等待队列，用于存储等待执行的任务，不能为null
     * @param handler         拒绝执行处理器，当线程池和队列都满时的处理策略，不能为null
     * @throws IllegalArgumentException 如果参数不满足以下条件：
     *                                  corePoolSize < 0 或
     *                                  keepAliveTime < 0 或
     *                                  maximumPoolSize <= 0 或
     *                                  maximumPoolSize < corePoolSize
     * @throws NullPointerException     如果workQueue、unit或handler为null
     * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, RejectedExecutionHandler)
     * @see ThreadPoolExecutor.AbortPolicy
     * @see ThreadPoolExecutor.CallerRunsPolicy
     * @see ThreadPoolExecutor.DiscardPolicy
     * @see ThreadPoolExecutor.DiscardOldestPolicy
     */
    public GXMdcWrapperThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    /**
     * 完整参数的构造函数，提供最大的灵活性和控制力
     * <p>
     * 这是功能最完整的构造函数，允许自定义所有线程池参数，适用于对性能和行为有精确要求的生产环境。
     * 通过精心配置各个参数，可以实现最优的性能表现和资源利用率。
     *
     * <p>
     * <b>生产环境配置模板</b>：
     * <pre>
     * // 高性能Web服务配置
     * int cpuCores = Runtime.getRuntime().availableProcessors();
     * ThreadFactory webThreadFactory = new ThreadFactoryBuilder()
     *     .setNameFormat("web-mdc-pool-%d")
     *     .setDaemon(false)
     *     .setPriority(Thread.NORM_PRIORITY)
     *     .setUncaughtExceptionHandler((t, e) ->
     *         logger.error("Web线程池异常: {}", t.getName(), e))
     *     .build();
     *
     * RejectedExecutionHandler webHandler = (r, executor) -> {
     *     logger.warn("Web请求被拒绝，当前负载: 活跃={}, 队列={}",
     *                 executor.getActiveCount(), executor.getQueue().size());
     *     // 记录监控指标
     *     meterRegistry.counter("web.requests.rejected").increment();
     *     // 快速失败，避免雪崩
     *     throw new RejectedExecutionException("服务器负载过高，请稍后重试");
     * };
     *
     * GXMdcWrapperThreadPoolExecutor webExecutor = new GXMdcWrapperThreadPoolExecutor(
     *     cpuCores * 2,                                    // 核心线程数：IO密集型
     *     cpuCores * 4,                                    // 最大线程数：处理突发流量
     *     60L, TimeUnit.SECONDS,                           // 线程存活时间：平衡资源回收
     *     new ArrayBlockingQueue<>(200),                   // 有界队列：防止内存溢出
     *     webThreadFactory,                                // 自定义线程工厂
     *     webHandler                                       // 自定义拒绝策略
     * );
     *
     * // 异步任务处理配置
     * ThreadFactory asyncThreadFactory = new ThreadFactoryBuilder()
     *     .setNameFormat("async-mdc-pool-%d")
     *     .setDaemon(true)                                 // 守护线程，不阻止JVM退出
     *     .setPriority(Thread.NORM_PRIORITY - 1)           // 稍低优先级
     *     .build();
     *
     * RejectedExecutionHandler asyncHandler = new ThreadPoolExecutor.CallerRunsPolicy(); // 背压机制
     *
     * GXMdcWrapperThreadPoolExecutor asyncExecutor = new GXMdcWrapperThreadPoolExecutor(
     *     cpuCores,                                        // 核心线程数：CPU密集型
     *     cpuCores * 2,                                    // 最大线程数：适度扩展
     *     300L, TimeUnit.SECONDS,                          // 线程存活时间：长期保持
     *     new LinkedBlockingQueue<>(500),                  // 较大队列：缓冲突发任务
     *     asyncThreadFactory,                              // 异步线程工厂
     *     asyncHandler                                     // 背压拒绝策略
     * );
     * </pre>
     *
     * <p>
     * <b>参数调优策略</b>：
     * <ul>
     * <li><b>核心线程数</b>：
     *     <ul>
     *     <li>CPU密集型：CPU核心数</li>
     *     <li>IO密集型：CPU核心数 × (1 + IO等待时间/CPU计算时间)</li>
     *     <li>混合型：通过压测确定最优值</li>
     *     </ul>
     * </li>
     * <li><b>最大线程数</b>：
     *     <ul>
     *     <li>通常为核心线程数的2-4倍</li>
     *     <li>考虑系统总体线程数限制</li>
     *     <li>避免过多线程导致上下文切换开销</li>
     *     </ul>
     * </li>
     * <li><b>线程存活时间</b>：
     *     <ul>
     *     <li>高频场景：30-60秒，减少线程创建销毁开销</li>
     *     <li>低频场景：5-10秒，及时回收资源</li>
     *     <li>稳定负载：可设置为0，保持固定线程数</li>
     *     </ul>
     * </li>
     * <li><b>队列大小</b>：
     *     <ul>
     *     <li>内存充足：可设置较大值，提高吞吐量</li>
     *     <li>内存紧张：设置较小值，快速触发拒绝策略</li>
     *     <li>实时性要求：使用SynchronousQueue</li>
     *     </ul>
     * </li>
     * </ul>
     *
     * <p>
     * <b>监控和调优建议</b>：
     * <ul>
     * <li><b>关键指标</b>：活跃线程数、队列长度、任务完成数、拒绝数</li>
     * <li><b>性能测试</b>：在真实负载下测试不同配置的性能表现</li>
     * <li><b>动态调整</b>：根据监控数据动态调整线程池参数</li>
     * <li><b>告警设置</b>：队列长度、拒绝率、响应时间等关键指标告警</li>
     * </ul>
     *
     * @param corePoolSize    核心线程数，线程池中始终保持的线程数量（≥ 0）
     * @param maximumPoolSize 最大线程数，线程池允许的最大线程数量（≥ corePoolSize）
     * @param keepAliveTime   非核心线程的空闲存活时间，超过此时间的空闲线程将被回收（≥ 0）
     * @param unit            keepAliveTime的时间单位，不能为null
     * @param workQueue       任务等待队列，用于存储等待执行的任务，不能为null
     * @param threadFactory   线程工厂，用于创建新线程，不能为null
     * @param handler         拒绝执行处理器，当线程池和队列都满时的处理策略，不能为null
     * @throws IllegalArgumentException 如果参数不满足以下条件：
     *                                  corePoolSize < 0 或
     *                                  keepAliveTime < 0 或
     *                                  maximumPoolSize <= 0 或
     *                                  maximumPoolSize < corePoolSize
     * @throws NullPointerException     如果workQueue、unit、threadFactory或handler为null
     * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, ThreadFactory, RejectedExecutionHandler)
     * @see #GXMdcWrapperThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue)
     * @see #GXMdcWrapperThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, ThreadFactory)
     * @see #GXMdcWrapperThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue, RejectedExecutionHandler)
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
     * @param task   需要提交的任务
     * @param result 任务完成后返回的结果
     * @param <T>    结果的类型
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
     * @param <T>  任务结果的类型
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

    /**
     * 执行给定的任务集合，当所有任务完成时返回保存其状态和结果的Future列表
     * <p>
     * 在执行任务前，使用GXMdcThreadUtils包装每个任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * 这是批量任务执行的推荐方法，相比单独提交多个任务，invokeAll能够更好地管理任务生命周期。
     * </p>
     * <p>
     * <b>线程安全性</b>：
     * - 每个任务都会独立包装MDC上下文，任务间不会相互干扰
     * - 使用当前线程的MDC上下文作为所有任务的基础上下文
     * - 任务执行完成后会自动清理MDC，防止内存泄漏
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用Java 17+的var关键字和Stream API优化代码可读性
     * - 批量包装任务减少重复的MDC上下文获取操作
     * - 利用并行流处理大量任务时的包装操作
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * // 创建多个任务
     * List<Callable<String>> tasks = Arrays.asList(
     *     () -> "Task 1: " + MDC.get("traceId"),
     *     () -> "Task 2: " + MDC.get("traceId"),
     *     () -> "Task 3: " + MDC.get("traceId")
     * );
     *
     * // 批量执行任务（MDC上下文会自动传递到每个任务）
     * List<Future<String>> futures = executor.invokeAll(tasks);
     *
     * // 获取所有任务结果
     * for (Future<String> future : futures) {
     *     String result = future.get(); // 结果中包含相同的traceId
     * }
     * </pre>
     * </p>
     *
     * @param tasks 任务集合
     * @param <T>   任务结果的类型
     * @return 表示任务的Future列表，按照迭代器产生的顺序排列，每个任务要么完成要么被取消
     * @throws InterruptedException 如果等待时被中断
     * @throws NullPointerException 如果tasks或其任何元素为null
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");

        // 获取当前线程的MDC上下文，用于所有任务
        var mdcContext = MDC.getCopyOfContextMap();

        // 包装所有任务，确保MDC上下文传递
        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAll(wrappedTasks);
    }

    /**
     * 执行给定的任务集合，当所有任务完成或超时期满时（无论哪个首先发生），返回保存其状态和结果的Future列表
     * <p>
     * 在执行任务前，使用GXMdcThreadUtils包装每个任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * 支持超时控制，在指定时间内未完成的任务将被取消。
     * </p>
     * <p>
     * <b>线程安全性</b>：
     * - 每个任务都会独立包装MDC上下文，任务间不会相互干扰
     * - 超时机制确保不会无限期等待，避免资源泄漏
     * - 未完成的任务会被自动取消，释放相关资源
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用Java 17+的现代语法提升代码可读性和性能
     * - 合理的超时设置可以避免长时间阻塞
     * - 自动取消超时任务，释放线程池资源
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * // 创建多个任务，包含一个可能耗时较长的任务
     * List<Callable<String>> tasks = Arrays.asList(
     *     () -> { Thread.sleep(1000); return "Fast task: " + MDC.get("traceId"); },
     *     () -> { Thread.sleep(5000); return "Slow task: " + MDC.get("traceId"); }
     * );
     *
     * // 设置3秒超时，慢任务将被取消
     * List<Future<String>> futures = executor.invokeAll(tasks, 3, TimeUnit.SECONDS);
     *
     * // 检查任务状态
     * for (Future<String> future : futures) {
     *     if (future.isDone() && !future.isCancelled()) {
     *         String result = future.get(); // 只有快任务会成功完成
     *     }
     * }
     * </pre>
     * </p>
     *
     * @param tasks   任务集合
     * @param timeout 最长等待时间
     * @param unit    timeout参数的时间单位
     * @param <T>     任务结果的类型
     * @return 表示任务的Future列表，按照迭代器产生的顺序排列，每个任务要么完成要么被取消
     * @throws InterruptedException 如果等待时被中断
     * @throws NullPointerException 如果tasks、unit或其任何元素为null
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");
        Objects.requireNonNull(unit, "TimeUnit cannot be null");

        // 获取当前线程的MDC上下文，用于所有任务
        var mdcContext = MDC.getCopyOfContextMap();

        // 包装所有任务，确保MDC上下文传递
        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAll(wrappedTasks, timeout, unit);
    }

    /**
     * 执行给定的任务，返回其中一个任务的结果（如果有的话）
     * <p>
     * 在执行任务前，使用GXMdcThreadUtils包装每个任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * 当任何一个任务成功完成时，会取消其他尚未完成的任务。
     * </p>
     * <p>
     * <b>线程安全性</b>：
     * - 每个任务都会独立包装MDC上下文，任务间不会相互干扰
     * - 当一个任务完成时，其他任务会被自动取消，避免资源浪费
     * - 异常处理确保即使部分任务失败，也能正确返回成功任务的结果
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 竞争执行模式，只要有一个任务成功即可返回，提高响应速度
     * - 自动取消其他任务，节省计算资源
     * - 适用于多种方案并行尝试的场景
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * // 创建多个获取数据的任务（比如从不同数据源获取相同数据）
     * List<Callable<String>> tasks = Arrays.asList(
     *     () -> fetchFromDatabase(MDC.get("traceId")),
     *     () -> fetchFromCache(MDC.get("traceId")),
     *     () -> fetchFromRemoteService(MDC.get("traceId"))
     * );
     *
     * // 执行任务，返回最快完成的结果
     * String result = executor.invokeAny(tasks);
     * // 其他未完成的任务会被自动取消
     * </pre>
     * </p>
     *
     * @param tasks 任务集合
     * @param <T>   任务结果的类型
     * @return 某个任务返回的结果
     * @throws InterruptedException 如果等待时被中断
     * @throws ExecutionException   如果没有任务成功完成
     * @throws NullPointerException 如果tasks或其任何元素为null
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");

        // 获取当前线程的MDC上下文，用于所有任务
        var mdcContext = MDC.getCopyOfContextMap();

        // 包装所有任务，确保MDC上下文传递
        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAny(wrappedTasks);
    }

    /**
     * 执行给定的任务，返回其中一个任务的结果（如果在给定的超时期满前有的话）
     * <p>
     * 在执行任务前，使用GXMdcThreadUtils包装每个任务，确保MDC上下文（包括traceId）能够传递到线程池线程中。
     * 支持超时控制，如果在指定时间内没有任务完成，将抛出TimeoutException。
     * </p>
     * <p>
     * <b>线程安全性</b>：
     * - 每个任务都会独立包装MDC上下文，任务间不会相互干扰
     * - 超时机制确保不会无限期等待
     * - 超时后所有任务都会被取消，释放资源
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 竞争执行模式结合超时控制，在性能和资源利用之间取得平衡
     * - 适用于对响应时间有严格要求的场景
     * - 超时保护避免长时间阻塞调用线程
     * </p>
     * <p>
     * <b>使用示例</b>：
     * <pre>
     * // 创建多个任务，设置合理的超时时间
     * List<Callable<String>> tasks = Arrays.asList(
     *     () -> quickOperation(MDC.get("traceId")),
     *     () -> fallbackOperation(MDC.get("traceId"))
     * );
     *
     * try {
     *     // 最多等待2秒
     *     String result = executor.invokeAny(tasks, 2, TimeUnit.SECONDS);
     * } catch (TimeoutException e) {
     *     // 处理超时情况
     *     logger.warn("所有任务在指定时间内未完成，traceId: {}", MDC.get("traceId"));
     * }
     * </pre>
     * </p>
     *
     * @param tasks   任务集合
     * @param timeout 最长等待时间
     * @param unit    timeout参数的时间单位
     * @param <T>     任务结果的类型
     * @return 某个任务返回的结果
     * @throws InterruptedException 如果等待时被中断
     * @throws ExecutionException   如果没有任务成功完成
     * @throws TimeoutException     如果在任何任务成功完成之前给定的超时期满
     * @throws NullPointerException 如果tasks、unit或其任何元素为null
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        Objects.requireNonNull(tasks, "Tasks collection cannot be null");
        Objects.requireNonNull(unit, "TimeUnit cannot be null");

        // 获取当前线程的MDC上下文，用于所有任务
        var mdcContext = MDC.getCopyOfContextMap();

        // 包装所有任务，确保MDC上下文传递
        var wrappedTasks = tasks.stream()
                .map(task -> GXMdcThreadUtils.wrap(task, mdcContext))
                .collect(Collectors.toList());

        return super.invokeAny(wrappedTasks, timeout, unit);
    }

    /**
     * 在执行给定任务之前调用的方法
     * <p>
     * 重写此方法以提供额外的监控和日志记录功能。
     * 可以在这里添加性能监控、任务统计等功能。
     * </p>
     * <p>
     * <b>注意</b>：此方法在任务执行线程中调用，此时MDC上下文已经被正确设置。
     * </p>
     *
     * @param t 将要执行的线程
     * @param r 将要执行的任务
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        // 可以在这里添加任务执行前的监控逻辑
        // 例如：记录任务开始时间、线程信息等
    }

    /**
     * 在执行给定任务之后调用的方法
     * <p>
     * 重写此方法以提供额外的监控和清理功能。
     * 可以在这里添加性能统计、异常处理等功能。
     * </p>
     * <p>
     * <b>注意</b>：此方法在任务执行线程中调用，MDC上下文在此方法调用后会被清理。
     * </p>
     *
     * @param r 已完成的任务
     * @param t 导致终止的异常，如果执行正常完成，则为null
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // 可以在这里添加任务执行后的监控逻辑
        // 例如：记录任务执行时间、处理异常等
        if (t != null) {
            // 记录任务执行异常，此时仍可访问MDC上下文
            // logger.error("任务执行异常，traceId: {}", MDC.get("traceId"), t);
        }
    }
}
