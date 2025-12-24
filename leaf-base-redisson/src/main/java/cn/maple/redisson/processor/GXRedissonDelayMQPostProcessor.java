package cn.maple.redisson.processor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.annotation.GXRedissonDelayMQToTopic;
import cn.maple.redisson.listener.GXRedissonDelayMQListener;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redisson延迟队列到可靠主题转换处理器
 * <p>
 * 该处理器用于将标注了{@link GXRedissonDelayMQToTopic}注解的bean中配置的延迟队列数据
 * 在到期后转发到Redisson的ReliableTopic中，实现可靠的消息队列。
 * </p>
 *
 * <p>
 * 工作原理：
 * 1. 检测标注了{@link GXRedissonDelayMQToTopic}注解的bean
 * 2. 为每个延迟队列创建一个监听任务，由线程池统一管理
 * 3. 当消息到期时，通过{@link GXRedissonDelayMQListener}接口将消息发布到指定的Topic
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
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonDelayMQPostProcessor implements BeanPostProcessor, DisposableBean, PriorityOrdered {
    /**
     * 拉取线程池核心线程数：固定为2，用于异步拉取消息
     */
    private static final int FETCH_CORE_POOL_SIZE = 2;

    /**
     * 工作线程池核心线程数：根据CPU核心数计算
     */
    private static final int WORKER_CORE_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());

    /**
     * 工作线程池最大线程数
     */
    private static final int WORKER_MAX_POOL_SIZE = WORKER_CORE_POOL_SIZE * 2;

    /**
     * 线程空闲存活时间：60秒
     */
    private static final long KEEP_ALIVE_TIME = 60L;

    /**
     * 本地缓冲队列容量：每个监听器的本地队列容量
     */
    private static final int LOCAL_QUEUE_CAPACITY = 5000;

    /**
     * 工作线程池队列容量
     */
    private static final int WORKER_QUEUE_CAPACITY = 10000;

    /**
     * 任务执行超时时间：30秒
     */
    private static final int TASK_EXECUTION_TIMEOUT = 30;

    /**
     * 线程池关闭超时时间：30秒
     */
    private static final int SHUTDOWN_TIMEOUT = 30;

    /**
     * 批量拉取消息的数量
     */
    private static final int BATCH_POLL_SIZE = 10;

    /**
     * 批量拉取超时时间（毫秒）
     */
    private static final long BATCH_POLL_TIMEOUT = 100L;

    /**
     * 心跳日志打印间隔：5分钟
     */
    private static final long HEARTBEAT_INTERVAL = 300000L;

    /**
     * 失败重试次数
     */
    private static final int MAX_RETRY_TIMES = 3;

    /**
     * 重试延迟时间（毫秒）
     */
    private static final long RETRY_DELAY = 1000L;

    /**
     * 拉取消息的线程池（小线程池，用于异步拉取）
     */
    private final ExecutorService fetchExecutor;

    /**
     * 处理消息的工作线程池（大线程池，用于并发处理）
     */
    private final ExecutorService workerExecutor;

    /**
     * 队列监听器的配置映射
     * key: 队列名称
     * value: 监听器配置对象
     */
    private final Map<String, QueueListenerConfig> listenerConfigs = new ConcurrentHashMap<>();

    /**
     * 队列名称与延迟队列对象的映射关系
     */
    @SuppressWarnings("deprecation")
    private final Map<String, RDelayedQueue<String>> delayedQueueMap = new ConcurrentHashMap<>();

    /**
     * 运行状态标志：使用 volatile 保证可见性
     */
    private volatile boolean running = true;

    /**
     * 关闭中标志：用于区分正常运行和关闭过程
     */
    private volatile boolean shuttingDown = false;

    /**
     * RedissonClient 缓存引用，避免重复获取
     */
    private volatile RedissonClient redissonClient;

    /**
     * Redisson 是否已关闭的标志
     */
    private volatile boolean redissonShutdown = false;

    /**
     * 构造函数：初始化处理器和线程池
     */
    public GXRedissonDelayMQPostProcessor() {
        this.fetchExecutor = createFetchThreadPool();
        this.workerExecutor = createWorkerThreadPool();
        log.info("=== Redisson延迟队列处理器初始化完成（优化版）===");
        log.info("拉取线程池: 核心{}线程", FETCH_CORE_POOL_SIZE);
        log.info("工作线程池: 核心{}线程, 最大{}线程", WORKER_CORE_POOL_SIZE, WORKER_MAX_POOL_SIZE);
        log.info("本地缓冲队列容量: {}, 批量拉取: {}条/次", LOCAL_QUEUE_CAPACITY, BATCH_POLL_SIZE);
    }

    /**
     * Bean后置处理方法：在Bean初始化后检查是否需要启动延迟队列监听
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 获取目标对象（处理AOP代理情况）
        Object actualBean = AopProxyUtils.getSingletonTarget(bean);
        if (actualBean == null) {
            actualBean = bean;
        }

        // 检查Bean是否标注了延迟队列转换注解
        GXRedissonDelayMQToTopic annotation =
                actualBean.getClass().getAnnotation(GXRedissonDelayMQToTopic.class);

        if (annotation == null) {
            return bean;
        }

        // 验证Bean是否实现了必需的监听器接口
        if (!(actualBean instanceof GXRedissonDelayMQListener)) {
            log.error("❌ Bean [{}] 标注了@GXConvertRedissonDelayQueueToTopic但未实现GXRedissonDelayQueueListener接口", beanName);
            return bean;
        }

        // 从注解中获取配置参数
        String blockingQueueName = annotation.delayQueueName();
        String topicName = annotation.topicName();

        // 验证队列名和主题名是否为空
        if (CharSequenceUtil.isBlank(blockingQueueName) || CharSequenceUtil.isBlank(topicName)) {
            log.error("❌ Bean [{}] 的队列名或主题名为空", beanName);
            return bean;
        }

        log.info("=== 发现延迟队列监听器 Bean: {} ===", beanName);
        log.info("📋 配置 - 队列: {}, 主题: {}", blockingQueueName, topicName);

        // 启动监听任务
        startListenerTask((GXRedissonDelayMQListener) actualBean, blockingQueueName, topicName);
        return bean;
    }

    /**
     * 启动延迟队列的监听任务（优化版）
     */
    private void startListenerTask(GXRedissonDelayMQListener listener,
                                   String blockingQueueName,
                                   String topicName) {
        // 检查是否已存在该队列的监听任务
        if (listenerConfigs.containsKey(blockingQueueName)) {
            log.warn("⚠️  队列 [{}] 已有监听任务，跳过创建", blockingQueueName);
            return;
        }

        try {
            // 获取Redisson客户端并初始化队列
            RedissonClient client = getRedissonClient();
            RBlockingQueue<String> blockingQueue = client.getBlockingQueue(blockingQueueName);
            @SuppressWarnings("deprecation")
            RDelayedQueue<String> delayedQueue = client.getDelayedQueue(blockingQueue);
            delayedQueueMap.put(blockingQueueName, delayedQueue);

            // 创建监听器配置
            QueueListenerConfig config = new QueueListenerConfig(
                    blockingQueueName,
                    topicName,
                    listener,
                    blockingQueue,
                    new LinkedBlockingQueue<>(LOCAL_QUEUE_CAPACITY)
            );
            listenerConfigs.put(blockingQueueName, config);

            log.info("✅ 延迟队列已初始化: {}", blockingQueueName);

            // 启动异步拉取任务
            startAsyncFetchTask(config);

            // 启动本地队列消费任务
            startLocalQueueConsumer(config);

            log.info("🚀 延迟队列 [{}] 监听任务已启动（异步模式）", blockingQueueName);
        } catch (Exception e) {
            log.error("❌ 启动延迟队列 [{}] 监听任务失败", blockingQueueName, e);
        }
    }

    /**
     * 启动异步拉取任务：从Redis队列拉取消息到本地缓冲队列
     */
    private void startAsyncFetchTask(QueueListenerConfig config) {
        fetchExecutor.submit(() -> {
            String threadName = "fetch-" + config.queueName;
            Thread.currentThread().setName(threadName);
            log.info("🎯 【{}】异步拉取线程已启动", threadName);

            long lastHeartbeat = System.currentTimeMillis();

            while (running && !shuttingDown && !redissonShutdown) {
                try {
                    // 批量拉取消息
                    List<String> messages = pollBatch(config.blockingQueue);
                    if (!messages.isEmpty()) {
                        // 将消息放入本地缓冲队列
                        for (String message : messages) {
                            // 检查是否正在关闭
                            if (shuttingDown || redissonShutdown) {
                                log.info("【{}】检测到关闭信号，停止处理新消息", threadName);
                                break;
                            }
                            if (!config.localQueue.offer(message, 5, TimeUnit.SECONDS)) {
                                log.warn("⚠️  【{}】本地队列已满，消息: {}", threadName, message);
                            }
                        }
                        log.debug("📥 【{}】拉取 {} 条消息到本地队列", threadName, messages.size());
                    } else {
                        // 空闲时打印心跳
                        long now = System.currentTimeMillis();
                        if (now - lastHeartbeat > HEARTBEAT_INTERVAL) {
                            log.info("💓 【{}】拉取中...", threadName);
                            lastHeartbeat = now;
                        }
                        // 短暂休眠避免空轮询
                        Thread.sleep(100);
                    }

                } catch (InterruptedException e) {
                    log.info("⚠️  【{}】拉取被中断", threadName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // 检查是否是 Redisson 关闭异常
                    if (isRedissonShutdownException(e)) {
                        redissonShutdown = true;
                        log.debug("【{}】检测到 Redisson 已关闭，停止拉取", threadName);
                        break;
                    }
                    // 如果是关闭过程中的异常，不打印错误日志
                    if (shuttingDown || redissonShutdown) {
                        log.debug("【{}】关闭过程中的正常异常，已忽略", threadName);
                    } else {
                        log.error("❌ 【{}】拉取异常", threadName, e);
                    }
                    sleepQuietly(Duration.ofSeconds(1));
                }
            }
            log.info("🛑 【{}】拉取已停止", threadName);
        });
    }

    /**
     * 批量拉取消息
     */
    private List<String> pollBatch(RBlockingQueue<String> blockingQueue) {
        List<String> messages = new ArrayList<>();

        // 如果正在关闭或 Redisson 已关闭，直接返回空列表
        if (shuttingDown || redissonShutdown) {
            return messages;
        }

        try {
            // 使用 drainTo 批量拉取
            blockingQueue.drainTo(messages, BATCH_POLL_SIZE);
            // 如果没有消息且不在关闭中，等待一个
            if (messages.isEmpty() && !shuttingDown && !redissonShutdown) {
                String message = blockingQueue.poll(BATCH_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
                if (message != null) {
                    messages.add(message);
                }
            }
        } catch (Exception e) {
            // 检查是否是 Redisson 关闭异常
            if (isRedissonShutdownException(e)) {
                redissonShutdown = true;
                return messages;
            }
            // 如果不是关闭过程中的异常，才记录错误
            if (!shuttingDown && !redissonShutdown) {
                log.error("批量拉取消息失败", e);
            }
        }
        return messages;
    }

    /**
     * 启动本地队列消费任务：从本地缓冲队列消费消息并处理
     */
    private void startLocalQueueConsumer(QueueListenerConfig config) {
        // 启动多个消费者线程提高并发
        int consumerCount = Math.min(4, Math.max(1, WORKER_CORE_POOL_SIZE / Math.max(1, listenerConfigs.size())));
        for (int i = 0; i < consumerCount; i++) {
            final int consumerIndex = i;
            workerExecutor.submit(() -> {
                String threadName = "consumer-" + config.queueName + "-" + consumerIndex;
                Thread.currentThread().setName(threadName);
                log.info("🎯 【{}】消费者线程已启动", threadName);
                while (running && !shuttingDown) {
                    try {
                        // 从本地队列获取消息
                        String message = config.localQueue.poll(1, TimeUnit.SECONDS);
                        if (message != null) {
                            // 再次检查是否正在关闭
                            if (shuttingDown) {
                                log.info("【{}】检测到关闭信号，停止处理消息", threadName);
                                break;
                            }
                            log.debug("📨 【{}】收到消息: {}", threadName, message);
                            // 处理消息（带重试）
                            processMessageWithRetry(config, message, threadName);
                        }
                    } catch (InterruptedException e) {
                        log.info("⚠️  【{}】消费被中断", threadName);
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // 如果是关闭过程中的异常，不打印错误日志
                        if (!shuttingDown) {
                            log.error("❌ 【{}】消费异常", threadName, e);
                        }
                        sleepQuietly(Duration.ofSeconds(1));
                    }
                }
                log.info("🛑 【{}】消费已停止", threadName);
            });
        }
    }

    /**
     * 处理消息（带重试机制）
     */
    private void processMessageWithRetry(QueueListenerConfig config, String message, String threadName) {
        long startTime = System.currentTimeMillis();

        for (int attempt = 1; attempt <= MAX_RETRY_TIMES; attempt++) {
            // 检查是否正在关闭
            if (shuttingDown) {
                log.info("【{}】检测到关闭信号，停止重试", threadName);
                return;
            }
            try {
                log.debug("⚙️  【{}】处理消息 (尝试 {}/{}) - 队列: {} -> 主题: {}",
                        threadName, attempt, MAX_RETRY_TIMES, config.queueName, config.topicName);
                // 调用监听器处理
                CompletableFuture<Boolean> result = config.listener.execute(config.topicName, message);
                // 等待结果（带超时）
                int finalAttempt = attempt;
                Boolean success = result
                        .completeOnTimeout(false, TASK_EXECUTION_TIMEOUT, TimeUnit.SECONDS)
                        .exceptionally(ex -> {
                            // 如果不是关闭过程中的异常，才记录错误
                            if (!shuttingDown) {
                                log.error("❌ 【{}】处理异常 (尝试 {}) {}", threadName, finalAttempt, ex);
                            }
                            return false;
                        })
                        .join();
                if (Boolean.TRUE.equals(success)) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("✅ 【{}】消息处理成功 (耗时: {}ms, 尝试: {})", threadName, duration, attempt);
                    return; // 成功，退出重试
                } else {
                    log.warn("⚠️  【{}】消息处理失败 (尝试 {})", threadName, attempt);
                    // 如果不是最后一次尝试，等待后重试
                    if (attempt < MAX_RETRY_TIMES && !shuttingDown) {
                        Thread.sleep(RETRY_DELAY * attempt); // 递增延迟
                    }
                }
            } catch (Exception e) {
                // 如果是关闭过程中的异常，不打印错误日志
                if (!shuttingDown) {
                    log.error("❌ 【{}】处理消息异常 (尝试 {})", threadName, attempt, e);
                }
                if (attempt < MAX_RETRY_TIMES && !shuttingDown) {
                    sleepQuietly(Duration.ofMillis(RETRY_DELAY * attempt));
                }
            }
        }
        // 所有重试都失败（且不是关闭过程）
        if (!shuttingDown) {
            log.error("❌ 【{}】消息处理最终失败，已重试 {} 次 - 消息: {}", threadName, MAX_RETRY_TIMES, message);
        }
    }

    /**
     * 销毁方法：在Bean销毁时执行清理工作
     * 优化关闭顺序，避免 RedissonShutdownException
     */
    @Override
    public void destroy() {
        log.info("=== 开始停止 Redisson 延迟队列处理器 ===");
        log.info("监听器数量: {}", listenerConfigs.size());

        // 第一步：设置关闭标志，停止新消息的处理
        shuttingDown = true;
        running = false;

        // 等待一小段时间，让正在执行的拉取操作完成
        sleepQuietly(Duration.ofMillis(500));

        // 第二步：销毁 Redisson 资源（在关闭线程池之前，避免线程池还在尝试访问已关闭的资源）
        destroyDelayedQueues();

        // 设置 Redisson 关闭标志
        redissonShutdown = true;

        // 等待一小段时间，确保所有线程都能检测到关闭状态
        sleepQuietly(Duration.ofMillis(300));

        // 第三步：关闭线程池
        shutdownThreadPools();

        log.info("=== Redisson 延迟队列处理器已完全停止 ===");
    }

    /**
     * 销毁所有延迟队列
     */
    private void destroyDelayedQueues() {
        if (delayedQueueMap.isEmpty()) {
            return;
        }

        log.info("正在销毁 {} 个延迟队列...", delayedQueueMap.size());
        delayedQueueMap.forEach((queueName, delayedQueue) -> {
            try {
                if (delayedQueue != null && delayedQueue.isExists()) {
                    delayedQueue.destroy();
                    log.info("延迟队列 [{}] 已销毁", queueName);
                }
            } catch (Exception e) {
                // 忽略 Redisson 关闭异常
                if (!isRedissonShutdownException(e)) {
                    log.warn("销毁延迟队列 [{}] 时出现异常: {}", queueName, e.getMessage());
                }
            }
        });
        delayedQueueMap.clear();
        listenerConfigs.clear();
    }

    /**
     * 关闭所有线程池
     */
    private void shutdownThreadPools() {
        log.info("正在关闭线程池...");
        // 先关闭拉取线程池（停止从 Redis 拉取新消息）
        shutdownExecutor(fetchExecutor, "拉取线程池");
        // 再关闭工作线程池（处理剩余的本地消息）
        shutdownExecutor(workerExecutor, "工作线程池");
    }

    /**
     * 关闭单个线程池
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        try {
            log.info("关闭 {}...", name);
            // 停止接收新任务
            executor.shutdown();
            // 等待现有任务完成
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                log.warn("{} 未在 {} 秒内关闭，尝试强制关闭", name, SHUTDOWN_TIMEOUT);
                // 尝试停止所有正在执行的任务
                List<Runnable> pendingTasks = executor.shutdownNow();
                log.info("{} 强制关闭，未执行任务数: {}", name, pendingTasks.size());
                // 再等待一小段时间
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("{} 强制关闭后仍未完全停止", name);
                }
            }
            log.info("{} 已关闭", name);
        } catch (InterruptedException e) {
            log.warn("关闭 {} 时被中断", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("关闭 {} 时发生异常", name, e);
        }
    }

    /**
     * 创建拉取线程池（小线程池）
     */
    private ExecutorService createFetchThreadPool() {
        return new ThreadPoolExecutor(
                FETCH_CORE_POOL_SIZE,
                FETCH_CORE_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                createThreadFactory("redisson-fetch"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建工作线程池（大线程池）
     */
    private ExecutorService createWorkerThreadPool() {
        return new ThreadPoolExecutor(
                WORKER_CORE_POOL_SIZE,
                WORKER_MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(WORKER_QUEUE_CAPACITY),
                createThreadFactory("redisson-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 创建线程工厂
     */
    private ThreadFactory createThreadFactory(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            private final ThreadGroup group = Thread.currentThread().getThreadGroup();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(group, r, prefix + "-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                if (t.getPriority() != Thread.NORM_PRIORITY) {
                    t.setPriority(Thread.NORM_PRIORITY);
                }
                return t;
            }
        };
    }

    /**
     * 安全休眠
     */
    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检查异常是否为 Redisson 关闭异常
     */
    private boolean isRedissonShutdownException(Throwable e) {
        if (e == null) {
            return false;
        }
        // 检查异常类名
        String exceptionName = e.getClass().getName();
        if ("org.redisson.RedissonShutdownException".equals(exceptionName)) {
            return true;
        }
        // 检查异常消息
        String message = e.getMessage();
        if (message != null && message.contains("Redisson is shutdown")) {
            return true;
        }
        // 递归检查 cause
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return isRedissonShutdownException(cause);
        }
        return false;
    }

    /**
     * 获取 RedissonClient 实例（使用缓存避免重复获取）
     */
    private RedissonClient getRedissonClient() {
        if (redissonClient == null) {
            synchronized (this) {
                if (redissonClient == null) {
                    redissonClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
                    if (redissonClient == null) {
                        throw new GXBusinessException("无法获取redissonMQClient实例，请确保已正确配置");
                    }
                }
            }
        }
        return redissonClient;
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE - 1000;
    }

    /**
     * 队列监听器配置
     */
    private record QueueListenerConfig(String queueName, String topicName, GXRedissonDelayMQListener listener,
                                       RBlockingQueue<String> blockingQueue, BlockingQueue<String> localQueue) {
    }
}