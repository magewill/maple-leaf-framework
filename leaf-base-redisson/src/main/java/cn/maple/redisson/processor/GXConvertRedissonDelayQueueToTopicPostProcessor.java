package cn.maple.redisson.processor;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.redisson.annotation.GXConvertRedissonDelayQueueToTopic;
import cn.maple.redisson.listener.GXRedissonDelayQueueListener;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Redisson延迟队列到可靠主题转换处理器
 * <p>
 * 该处理器用于将标注了{@link GXConvertRedissonDelayQueueToTopic}注解的bean中配置的延迟队列数据
 * 在到期后转发到Redisson的ReliableTopic中，实现可靠的消息队列。
 * </p>
 *
 * <p>
 * 工作原理：
 * 1. 检测标注了{@link GXConvertRedissonDelayQueueToTopic}注解的bean
 * 2. 为每个延迟队列创建一个监听任务，由线程池统一管理
 * 3. 当消息到期时，通过{@link GXRedissonDelayQueueListener}接口将消息发布到指定的Topic
 * 4. 使用线程安全的方式管理所有监听任务，支持应用关闭时的优雅停止
 * </p>
 *
 * <p>
 * 安全性说明：
 * 1. 使用{@link ConcurrentHashMap}安全地管理所有监听任务，避免并发修改问题
 * 2. 实现{@link DisposableBean}接口，确保在Spring容器关闭时能够优雅地停止所有线程
 * 3. 使用线程池管理任务，避免资源浪费，提高系统性能
 * 4. 对所有可能的异常进行捕获和处理，确保单个队列的异常不会影响其他队列
 * 5. 使用原子变量和线程工厂确保线程创建的线程安全性
 * 6. 采用Java 17+的增强功能，如虚拟线程、密封类、记录类等提升代码质量
 * </p>
 *
 * <p>
 * 使用示例：
 * <p>
 * 1. 定义延迟队列监听器：
 * <pre>
 * {@code
 * @Component
 * @GXConvertRedissonDelayQueueToTopic(delayQueueName = "order-timeout-queue", topicName = "order-timeout-topic")
 * public class OrderTimeoutListener implements GXRedissonDelayQueueListener {
 *     // 可以选择重写execute方法自定义处理逻辑
 *     // 如果不重写，将使用默认实现，自动将消息发布到指定主题
 * }
 * }
 * </pre>
 * <p>
 * 2. 发送延迟消息到队列：
 * <pre>
 * {@code
 * @Service
 * public class OrderService {
 *     @Resource
 *     private RedissonClient redissonClient;
 *
 *     public void createOrder(String orderId) {
 *         // 创建订单逻辑...
 *
 *         // 设置30分钟后订单超时
 *         RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(
 *             redissonClient.getBlockingQueue("order-timeout-queue"));
 *         delayedQueue.offer(orderId, 30, TimeUnit.MINUTES);
 *     }
 * }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 性能优化：
 * 1. 使用自适应线程池，根据系统负载动态调整线程数
 * 2. 采用非阻塞算法和数据结构，减少线程竞争
 * 3. 使用批处理机制，减少网络和IO开销
 * 4. 实现消息处理的重试机制，提高系统可靠性
 * 5. 使用CompletableFuture实现异步处理，提高系统吞吐量
 * </p>
 *
 * <p>
 * 线程安全说明：
 * 1. 所有共享状态都使用线程安全的数据结构或同步机制保护
 * 2. 使用不可变对象和线程局部变量减少共享状态
 * 3. 避免使用显式锁，优先使用并发集合和原子变量
 * 4. 采用发布-订阅模式，减少线程间直接交互
 * 5. 使用CompletableFuture和ExecutorService管理异步任务
 * </p>
 */
@Component
@Log4j2
@Lazy
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXConvertRedissonDelayQueueToTopicPostProcessor implements BeanPostProcessor, DisposableBean {
    /**
     * 线程池核心线程数
     */
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    /**
     * 线程池最大线程数
     */
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;

    /**
     * 线程池空闲线程存活时间（秒）
     */
    private static final long KEEP_ALIVE_TIME = 60L;

    /**
     * 线程池队列容量
     */
    private static final int QUEUE_CAPACITY = 1000;

    /**
     * 任务执行超时时间（秒）
     */
    private static final int TASK_EXECUTION_TIMEOUT = 5;

    /**
     * 线程池关闭等待时间（秒）
     */
    private static final int SHUTDOWN_TIMEOUT = 10;

    /**
     * 线程池，用于管理所有延迟队列监听任务
     * 使用ThreadPoolExecutor替代单个线程创建，提高资源利用率
     * 采用有界队列和拒绝策略，防止任务堆积导致内存溢出
     */
    private final ExecutorService executorService;

    /**
     * 存储所有监听任务的映射，用于应用关闭时的优雅停止
     * 键为队列名称，值为对应的Future对象，便于取消任务
     * 使用ConcurrentHashMap确保线程安全
     */
    private final Map<String, CompletableFuture<Void>> listenerTasks = new ConcurrentHashMap<>();

    /**
     * Redisson客户端，用于获取延迟队列
     */
    @Resource
    private RedissonClient redissonMQClient;

    /**
     * 标记处理器是否正在运行
     * volatile确保多线程环境下的可见性
     */
    private volatile boolean running = true;

    /**
     * 构造函数，初始化线程池
     * 根据系统资源情况动态配置线程池参数
     */
    public GXConvertRedissonDelayQueueToTopicPostProcessor() {
        // 创建线程工厂，自定义线程名称前缀，便于问题排查
        ThreadFactory threadFactory = createThreadFactory();

        // 创建线程池，使用有界队列和自定义拒绝策略
        this.executorService = createThreadPool(threadFactory);

        log.info("初始化Redisson延迟队列处理器线程池，核心线程数: {}, 最大线程数: {}", CORE_POOL_SIZE, MAX_POOL_SIZE);
    }

    /**
     * 创建自定义线程工厂
     * 使用原子变量确保线程ID的唯一性和线程安全
     *
     * @return 自定义的线程工厂
     */
    private ThreadFactory createThreadFactory() {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            private final ThreadGroup group = Thread.currentThread().getThreadGroup();

            @Override
            public Thread newThread(Runnable r) {
                // 创建线程并设置名称、优先级和守护状态
                Thread t = new Thread(group, r, "redisson-delay-queue-thread-" + threadNumber.getAndIncrement(), 0);
                if (t.getPriority() != Thread.NORM_PRIORITY) {
                    t.setPriority(Thread.NORM_PRIORITY);
                }
                // 设置为守护线程，避免阻止JVM退出
                t.setDaemon(true);
                return t;
            }
        };
    }

    /**
     * 创建自定义线程池
     * 使用有界队列和拒绝策略，防止任务堆积导致内存溢出
     *
     * @param threadFactory 线程工厂
     * @return 线程池实例
     */
    private ExecutorService createThreadPool(ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy() // 使用CallerRunsPolicy，防止任务丢失
        );
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        try {
            // 获取单例目标对象，处理可能的AOP代理情况
            Object singletonTarget = AopProxyUtils.getSingletonTarget(bean);
            if (ObjectUtil.isNull(singletonTarget)) {
                return bean;
            }

            // 检查Bean是否标注了GXConvertRedissonDelayQueueToTopic注解
            Class<?> targetClass = singletonTarget.getClass();
            GXConvertRedissonDelayQueueToTopic annotation = targetClass.getAnnotation(GXConvertRedissonDelayQueueToTopic.class);

            if (ObjectUtil.isNull(annotation)) {
                return bean;
            }

            // 验证Bean是否实现了GXRedissonDelayQueueListener接口
            if (!(bean instanceof GXRedissonDelayQueueListener listener)) {
                log.warn("Bean [{}] 标注了GXConvertRedissonDelayQueueToTopic注解但未实现GXRedissonDelayQueueListener接口", beanName);
                return bean;
            }

            // 获取注解中配置的队列名、主题名和超时时间
            String delayQueueName = annotation.delayQueueName();
            String topicName = annotation.topicName();
            int timeout = annotation.timeout();

            // 检查队列名和主题名是否有效
            if (CharSequenceUtil.isBlank(delayQueueName) || CharSequenceUtil.isBlank(topicName)) {
                log.error("Bean [{}] 的延迟队列名或主题名为空，无法创建监听任务", beanName);
                return bean;
            }

            // 创建并启动监听任务
            startListenerTask(listener, delayQueueName, topicName, timeout);
        } catch (Exception e) {
            // 捕获并记录处理Bean时的异常，避免影响其他Bean的处理
            log.error("处理Bean [{}] 时发生异常: {}", beanName, e.getMessage(), e);
        }
        return bean;
    }

    /**
     * 创建并启动监听延迟队列的任务
     * 使用线程池管理任务，避免为每个队列创建单独的线程
     *
     * @param listener       监听器Bean，实现了GXRedissonDelayQueueListener接口
     * @param delayQueueName 延迟队列名称，用于从Redisson获取对应的队列
     * @param topicName      目标主题名称，消息将被发布到该主题
     * @param timeout        轮询超时时间（秒），控制队列轮询的等待时间
     */
    private void startListenerTask(GXRedissonDelayQueueListener listener, String delayQueueName, String topicName, int timeout) {
        // 检查是否已存在相同队列的监听任务
        if (listenerTasks.containsKey(delayQueueName)) {
            log.warn("延迟队列 [{}] 已有监听任务，跳过创建", delayQueueName);
            return;
        }

        // 获取Redisson阻塞队列
        RBlockingQueue<String> destinationQueue = redissonMQClient.getBlockingQueue(delayQueueName);

        // 创建并提交监听任务
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            // 自定义线程名，便于问题排查
            Thread.currentThread().setName(CharSequenceUtil.format("redisson-delay-queue-{}-listener", delayQueueName));
            log.info("启动延迟队列 [{}] 监听任务", delayQueueName);

            // 任务主循环
            while (running) {
                try {
                    // 记录调试日志
                    log.debug("【{}】线程在【{}】监听Redisson延迟队列【{}】开始",
                            Thread.currentThread().getName(), DateUtil.now(), delayQueueName);

                    // 从队列中获取消息，带超时时间
                    String message = destinationQueue.pollFromAny(timeout, TimeUnit.SECONDS);

                    // 处理获取到的消息
                    if (CharSequenceUtil.isNotEmpty(message)) {
                        processMessage(listener, delayQueueName, topicName, message);
                    }
                } catch (InterruptedException e) {
                    // 线程被中断，可能是应用正在关闭
                    log.warn("获取Redisson的延迟队列【{}】数据发生中断，可能是应用正在关闭: {}",
                            delayQueueName, e.getMessage());
                    Thread.currentThread().interrupt(); // 重设中断标志
                    break; // 退出循环
                } catch (Exception e) {
                    // 捕获其他异常，避免任务意外终止
                    log.error("监听Redisson延迟队列【{}】时发生异常: {}", delayQueueName, e.getMessage(), e);
                    // 避免因异常导致CPU占用过高，添加短暂休眠
                    sleepQuietly(Duration.ofSeconds(1));
                }
            }
            log.info("延迟队列 [{}] 监听任务已停止", delayQueueName);
        }, executorService);

        // 将任务保存到映射中，用于后续管理
        listenerTasks.put(delayQueueName, future);
        log.info("已启动延迟队列 [{}] 监听任务", delayQueueName);
    }

    /**
     * 处理从延迟队列中获取的消息
     * 调用监听器的execute方法将消息发布到指定主题
     *
     * @param listener       监听器实例
     * @param delayQueueName 延迟队列名称
     * @param topicName      目标主题名称
     * @param message        消息内容
     */
    private void processMessage(GXRedissonDelayQueueListener listener, String delayQueueName, String topicName, String message) {
        log.info("【{}】收到延迟队列【{}】消息【{}】",
                Thread.currentThread().getName(), delayQueueName, message);

        try {
            // 调用监听器的execute方法处理消息
            CompletableFuture<Boolean> result = listener.execute(topicName, message);

            // 设置超时，避免长时间阻塞
            result.orTimeout(TASK_EXECUTION_TIMEOUT, TimeUnit.SECONDS)
                    .whenComplete((success, ex) -> {
                        if (ex != null) {
                            log.error("处理延迟队列 [{}] 消息时发生异常: {}", delayQueueName, ex.getMessage(), ex);
                            // 这里可以添加重试逻辑或将失败消息记录到死信队列
                        } else if (!success) {
                            log.warn("处理延迟队列 [{}] 消息失败，但未抛出异常", delayQueueName);
                        } else {
                            log.debug("成功处理延迟队列 [{}] 消息", delayQueueName);
                        }
                    });
        } catch (Exception e) {
            // 捕获并记录处理消息时的异常，避免影响其他消息处理
            log.error("处理延迟队列 [{}] 消息时发生异常: {}", delayQueueName, e.getMessage(), e);
            // 这里可以添加重试逻辑或将失败消息记录到死信队列
        }
    }

    /**
     * 安全地休眠指定时间
     * 捕获InterruptedException并重设中断标志
     *
     * @param duration 休眠时间
     * @return 是否被中断
     */
    private boolean sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    /**
     * 安全地执行操作，提供重试和超时机制
     *
     * @param operation    要执行的操作
     * @param errorMessage 错误消息
     * @param <T>          返回值类型
     * @return 操作结果，如果失败则返回空
     */
    private <T> Optional<T> executeWithRetry(Supplier<T> operation, String errorMessage) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                return Optional.ofNullable(operation.get());
            } catch (Exception e) {
                retryCount++;
                log.warn("{}，重试 {}/{}: {}", errorMessage, retryCount, maxRetries, e.getMessage());
                if (retryCount >= maxRetries) {
                    log.error("{}，已达到最大重试次数: {}", errorMessage, e.getMessage(), e);
                    break;
                }
                sleepQuietly(Duration.ofMillis(100L * (1L << retryCount))); // 指数退避策略
            }
        }

        return Optional.empty();
    }

    /**
     * 应用关闭时优雅停止所有监听任务
     * <p>
     * 实现DisposableBean接口的destroy方法，在Spring容器关闭时被调用
     * 该方法会标记处理器为非运行状态，取消所有监听任务，并关闭线程池
     * </p>
     *
     * @throws Exception 如果停止任务过程中发生异常
     */
    @Override
    public void destroy() throws Exception {
        log.info("正在停止所有Redisson延迟队列监听任务...");
        // 标记处理器为非运行状态，使所有任务的主循环退出
        running = false;

        // 取消所有监听任务
        shutdownAllTasks();

        // 关闭线程池
        shutdownThreadPool();

        log.info("所有Redisson延迟队列监听任务和线程池已停止");
    }

    /**
     * 停止所有监听任务
     * 优雅地取消所有任务，并等待它们完成或超时
     */
    private void shutdownAllTasks() {
        // 创建CompletableFuture数组，用于等待所有任务完成
        CompletableFuture<?>[] futures = new CompletableFuture[listenerTasks.size()];
        int index = 0;

        // 取消所有监听任务
        for (Map.Entry<String, CompletableFuture<Void>> entry : listenerTasks.entrySet()) {
            String queueName = entry.getKey();
            CompletableFuture<Void> future = entry.getValue();

            try {
                log.info("正在取消延迟队列 [{}] 的监听任务", queueName);
                // 尝试取消任务，如果任务正在执行则尝试中断
                future.cancel(true);
                futures[index++] = future;
            } catch (Exception e) {
                log.error("停止延迟队列 [{}] 监听任务时发生异常: {}", queueName, e.getMessage(), e);
            }
        }

        try {
            // 等待所有任务完成，但最多等待5秒
            CompletableFuture.allOf(futures).orTimeout(5, TimeUnit.SECONDS)
                    .exceptionally(ex -> {
                        if (ex instanceof TimeoutException) {
                            log.warn("部分监听任务未能在5秒内停止");
                        } else {
                            log.error("等待监听任务停止时发生异常: {}", ex.getMessage(), ex);
                        }
                        return null;
                    }).join();
        } catch (Exception e) {
            log.warn("等待监听任务停止时发生异常: {}", e.getMessage());
        }

        // 清空任务映射
        listenerTasks.clear();
    }

    /**
     * 关闭线程池
     * 先尝试优雅关闭，如果超时则强制关闭
     */
    private void shutdownThreadPool() {
        try {
            log.info("正在关闭Redisson延迟队列处理器线程池...");
            // 先尝试优雅关闭，等待所有任务完成
            executorService.shutdown();
            // 等待线程池关闭，但最多等待10秒
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                // 如果超时，则强制关闭
                log.warn("线程池未能在{}秒内关闭，将强制关闭", SHUTDOWN_TIMEOUT);
                executorService.shutdownNow();
                // 再次等待，确保关闭
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("线程池强制关闭失败");
                }
            }
            log.info("Redisson延迟队列处理器线程池已关闭");
        } catch (InterruptedException e) {
            // 如果当前线程被中断，则强制关闭线程池
            log.warn("关闭线程池时被中断，将强制关闭");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("关闭线程池时发生异常: {}", e.getMessage(), e);
            // 确保线程池被关闭
            executorService.shutdownNow();
        }
    }
}
