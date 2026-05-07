package cn.maple.redisson.processor;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.annotation.GXRedissonDelayMQToTopic;
import cn.maple.redisson.listener.GXRedissonDelayMQListener;
import cn.maple.redisson.util.GXRedissonDelayMQUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bean post processor that forwards expired Redisson delayed-queue messages to
 * Redisson reliable topics.
 */
@Component
@Log4j2
@ConditionalOnExpression("${maple.framework.mq.redisson.enable:false}")
public class GXRedissonDelayMQPostProcessor implements BeanPostProcessor, DisposableBean, PriorityOrdered {
    private static final int WORKER_CORE_POOL_SIZE = Math.max(4, Runtime.getRuntime().availableProcessors());
    private static final int WORKER_MAX_POOL_SIZE = WORKER_CORE_POOL_SIZE * 2;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final int LOCAL_QUEUE_CAPACITY = 5000;
    private static final int WORKER_QUEUE_CAPACITY = 10000;
    private static final int TASK_EXECUTION_TIMEOUT = 30;
    private static final int SHUTDOWN_TIMEOUT = 30;
    private static final int BATCH_POLL_SIZE = 20;
    private static final int DEFAULT_POLL_TIMEOUT_SECONDS = 30;
    private static final long HEARTBEAT_INTERVAL = 300000L;
    private static final int MAX_RETRY_TIMES = 3;
    private static final long RETRY_DELAY = 1000L;

    private final ExecutorService fetchExecutor;
    private final ExecutorService workerExecutor;
    private final ScheduledExecutorService retryScheduler;
    private final Map<String, QueueListenerConfig> listenerConfigs = new ConcurrentHashMap<>();

    private volatile boolean running = true;
    private volatile boolean shuttingDown = false;
    private volatile boolean redissonShutdown = false;

    public GXRedissonDelayMQPostProcessor() {
        this.fetchExecutor = Executors.newCachedThreadPool(createThreadFactory("redisson-delay-fetch"));
        this.workerExecutor = createWorkerThreadPool();
        this.retryScheduler = Executors.newScheduledThreadPool(
                2,
                new ThreadFactoryBuilder()
                        .setNameFormat("redisson-delay-retry-%d")
                        .setDaemon(true)
                        .build()
        );
        log.info("Redisson delayed MQ post processor initialized, workerPool={}/{}",
                WORKER_CORE_POOL_SIZE, WORKER_MAX_POOL_SIZE);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        GXRedissonDelayMQToTopic annotation = AnnotationUtils.findAnnotation(targetClass, GXRedissonDelayMQToTopic.class);
        if (annotation == null) {
            return bean;
        }

        if (!(bean instanceof GXRedissonDelayMQListener listener)) {
            log.error("Bean [{}] is annotated with @GXRedissonDelayMQToTopic but does not implement GXRedissonDelayMQListener", beanName);
            return bean;
        }

        String queueName = annotation.delayQueueName();
        String topicName = annotation.topicName();
        if (CharSequenceUtil.isBlank(queueName) || CharSequenceUtil.isBlank(topicName)) {
            log.error("Bean [{}] has blank delayQueueName or topicName", beanName);
            return bean;
        }

        int timeoutSeconds = annotation.timeout() > 0 ? annotation.timeout() : DEFAULT_POLL_TIMEOUT_SECONDS;
        startListenerTask(listener, queueName, topicName, timeoutSeconds);
        return bean;
    }

    @SuppressWarnings("deprecation")
    private void startListenerTask(GXRedissonDelayMQListener listener, String queueName, String topicName, int timeoutSeconds) {
        QueueListenerConfig existingConfig = listenerConfigs.get(queueName);
        if (existingConfig != null) {
            log.warn("Delayed queue [{}] already has a listener, skip duplicate registration", queueName);
            return;
        }

        try {
            RedissonClient client = getRedissonClient();
            RBlockingQueue<String> blockingQueue = client.getBlockingQueue(queueName);
            RDelayedQueue<String> delayedQueue = client.getDelayedQueue(blockingQueue);
            QueueListenerConfig config = new QueueListenerConfig(
                    queueName,
                    topicName,
                    timeoutSeconds,
                    listener,
                    blockingQueue,
                    delayedQueue,
                    new LinkedBlockingQueue<>(LOCAL_QUEUE_CAPACITY)
            );

            QueueListenerConfig previous = listenerConfigs.putIfAbsent(queueName, config);
            if (previous != null) {
                log.warn("Delayed queue [{}] already has a listener, skip duplicate registration", queueName);
                return;
            }

            startAsyncFetchTask(config);
            startLocalQueueConsumer(config);
            log.info("Started delayed queue listener, queue={}, topic={}, pollTimeoutSeconds={}",
                    queueName, topicName, timeoutSeconds);
        } catch (Exception e) {
            log.error("Failed to start delayed queue listener, queue={}", queueName, e);
        }
    }

    private void startAsyncFetchTask(QueueListenerConfig config) {
        fetchExecutor.submit(() -> {
            String threadName = "fetch-" + config.queueName();
            Thread.currentThread().setName(threadName);
            long lastHeartbeat = System.currentTimeMillis();

            while (running && !shuttingDown && !redissonShutdown) {
                try {
                    List<String> messages = pollBatch(config);
                    if (messages.isEmpty()) {
                        long now = System.currentTimeMillis();
                        if (now - lastHeartbeat > HEARTBEAT_INTERVAL) {
                            log.info("[{}] waiting for expired delayed messages", threadName);
                            lastHeartbeat = now;
                        }
                        continue;
                    }

                    for (int i = 0; i < messages.size(); i++) {
                        String message = messages.get(i);
                        if (shuttingDown || redissonShutdown) {
                            requeueToBlockingQueue(config, messages.subList(i, messages.size()));
                            break;
                        }
                        if (!config.localQueue().offer(message, 5, TimeUnit.SECONDS)) {
                            log.warn("[{}] local queue is full, message will be returned to Redis queue", threadName);
                            requeueToBlockingQueue(config, message);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (isRedissonShutdownException(e)) {
                        redissonShutdown = true;
                        break;
                    }
                    if (!shuttingDown) {
                        log.error("[{}] failed to fetch delayed messages", threadName, e);
                    }
                    sleepQuietly(Duration.ofSeconds(1));
                }
            }
            log.info("[{}] delayed message fetch stopped", threadName);
        });
    }

    private List<String> pollBatch(QueueListenerConfig config) {
        List<String> messages = new ArrayList<>(BATCH_POLL_SIZE);
        if (shuttingDown || redissonShutdown) {
            return messages;
        }

        try {
            config.blockingQueue().drainTo(messages, BATCH_POLL_SIZE);
            if (messages.isEmpty() && !shuttingDown && !redissonShutdown) {
                String message = config.blockingQueue().poll(config.timeoutSeconds(), TimeUnit.SECONDS);
                if (message != null) {
                    messages.add(message);
                    config.blockingQueue().drainTo(messages, BATCH_POLL_SIZE - 1);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (isRedissonShutdownException(e)) {
                redissonShutdown = true;
            } else if (!shuttingDown) {
                log.error("Failed to poll delayed queue [{}]", config.queueName(), e);
            }
        }
        return messages;
    }

    private void startLocalQueueConsumer(QueueListenerConfig config) {
        int consumerCount = getConsumerCountForQueue();
        for (int i = 0; i < consumerCount; i++) {
            final int consumerIndex = i;
            workerExecutor.submit(() -> consumeMessages(config, consumerIndex));
        }
    }

    private int getConsumerCountForQueue() {
        int totalQueues = Math.max(1, listenerConfigs.size());
        int perQueueConsumers = Math.max(1, WORKER_CORE_POOL_SIZE / totalQueues);
        return Math.min(4, perQueueConsumers);
    }

    private void consumeMessages(QueueListenerConfig config, int consumerIndex) {
        String threadName = "consumer-" + config.queueName() + "-" + consumerIndex;
        Thread.currentThread().setName(threadName);

        while (running && !shuttingDown) {
            try {
                String message = config.localQueue().poll(1, TimeUnit.SECONDS);
                if (message == null) {
                    continue;
                }
                if (shuttingDown) {
                    requeueToBlockingQueue(config, message);
                    break;
                }
                processMessageAsync(config, message, threadName, 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!shuttingDown) {
                    log.error("[{}] failed to consume delayed message", threadName, e);
                }
                sleepQuietly(Duration.ofSeconds(1));
            }
        }
        log.info("[{}] delayed message consumer stopped", threadName);
    }

    private void processMessageAsync(QueueListenerConfig config, String message, String threadName, int attempt) {
        if (shuttingDown) {
            requeueToBlockingQueue(config, message);
            return;
        }
        if (attempt > MAX_RETRY_TIMES) {
            requeueToDelayedQueue(config, message, threadName);
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            CompletableFuture<Boolean> result = config.listener().execute(config.topicName(), message);
            if (result == null) {
                scheduleRetry(config, message, threadName, attempt);
                return;
            }

            result.orTimeout(TASK_EXECUTION_TIMEOUT, TimeUnit.SECONDS)
                    .whenComplete((success, ex) -> {
                        if (shuttingDown) {
                            requeueToBlockingQueue(config, message);
                            return;
                        }
                        if (ex != null) {
                            log.error("[{}] failed to forward delayed message, attempt={}", threadName, attempt, ex);
                            scheduleRetry(config, message, threadName, attempt);
                        } else if (Boolean.TRUE.equals(success)) {
                            log.debug("[{}] forwarded delayed message, queue={}, topic={}, cost={}ms",
                                    threadName, config.queueName(), config.topicName(), System.currentTimeMillis() - startTime);
                        } else {
                            log.warn("[{}] delayed message forwarding returned false, attempt={}", threadName, attempt);
                            scheduleRetry(config, message, threadName, attempt);
                        }
                    });
        } catch (Exception e) {
            if (!shuttingDown) {
                log.error("[{}] failed to submit delayed message forwarding, attempt={}", threadName, attempt, e);
            }
            scheduleRetry(config, message, threadName, attempt);
        }
    }

    private void scheduleRetry(QueueListenerConfig config, String message, String threadName, int attempt) {
        if (shuttingDown) {
            requeueToBlockingQueue(config, message);
            return;
        }
        if (attempt >= MAX_RETRY_TIMES) {
            requeueToDelayedQueue(config, message, threadName);
            return;
        }

        int nextAttempt = attempt + 1;
        long delay = RETRY_DELAY * attempt;
        try {
            retryScheduler.schedule(
                    () -> processMessageAsync(config, message, threadName, nextAttempt),
                    delay,
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            log.error("[{}] failed to schedule retry, message will be returned to Redis queue", threadName, e);
            requeueToBlockingQueue(config, message);
        }
    }

    private void requeueToBlockingQueue(QueueListenerConfig config, String message) {
        try {
            config.blockingQueue().offer(message);
        } catch (Exception e) {
            if (!isRedissonShutdownException(e)) {
                log.error("Failed to return message to blocking queue [{}]", config.queueName(), e);
            }
        }
    }

    private void requeueToBlockingQueue(QueueListenerConfig config, List<String> messages) {
        for (String message : messages) {
            requeueToBlockingQueue(config, message);
        }
    }

    private void requeueToDelayedQueue(QueueListenerConfig config, String message, String threadName) {
        if (shuttingDown || redissonShutdown) {
            requeueToBlockingQueue(config, message);
            return;
        }
        try {
            config.delayedQueue().offer(message, RETRY_DELAY * MAX_RETRY_TIMES, TimeUnit.MILLISECONDS);
            log.error("[{}] delayed message forwarding failed after {} attempts, message requeued with delay, queue={}",
                    threadName, MAX_RETRY_TIMES, config.queueName());
        } catch (Exception e) {
            log.error("[{}] failed to requeue delayed message, trying blocking queue, queue={}", threadName, config.queueName(), e);
            requeueToBlockingQueue(config, message);
        }
    }

    @Override
    public void destroy() {
        log.info("Stopping Redisson delayed MQ post processor, listenerCount={}", listenerConfigs.size());
        shuttingDown = true;
        running = false;

        for (QueueListenerConfig config : listenerConfigs.values()) {
            String message;
            while ((message = config.localQueue().poll()) != null) {
                requeueToBlockingQueue(config, message);
            }
        }

        shutdownThreadPools();
        listenerConfigs.values().forEach(this::destroyDelayedQueue);
        listenerConfigs.clear();
        GXRedissonDelayMQUtils.clearDelayedQueueCache();
        log.info("Redisson delayed MQ post processor stopped");
    }

    private void destroyDelayedQueue(QueueListenerConfig config) {
        try {
            config.delayedQueue().destroy();
        } catch (Exception e) {
            if (!isRedissonShutdownException(e)) {
                log.warn("Failed to destroy delayed queue [{}]", config.queueName(), e);
            }
        }
    }

    private void shutdownThreadPools() {
        shutdownExecutor(fetchExecutor, "fetchExecutor");
        shutdownExecutor(workerExecutor, "workerExecutor");
        shutdownExecutor(retryScheduler, "retryScheduler");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        try {
            executor.shutdown();
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                List<Runnable> pendingTasks = executor.shutdownNow();
                log.warn("{} did not stop in {} seconds, cancelled {} tasks", name, SHUTDOWN_TIMEOUT, pendingTasks.size());
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("{} did not stop after shutdownNow", name);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to shutdown {}", name, e);
        }
    }

    private ExecutorService createWorkerThreadPool() {
        return new ThreadPoolExecutor(
                WORKER_CORE_POOL_SIZE,
                WORKER_MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(WORKER_QUEUE_CAPACITY),
                createThreadFactory("redisson-delay-worker"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    private ThreadFactory createThreadFactory(String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            private final ThreadGroup group = Thread.currentThread().getThreadGroup();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(group, r, prefix + "-" + threadNumber.getAndIncrement());
                thread.setDaemon(false);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isRedissonShutdownException(Throwable e) {
        if (e == null) {
            return false;
        }
        if ("org.redisson.RedissonShutdownException".equals(e.getClass().getName())) {
            return true;
        }
        String message = e.getMessage();
        if (message != null && message.contains("Redisson is shutdown")) {
            return true;
        }
        Throwable cause = e.getCause();
        return cause != null && cause != e && isRedissonShutdownException(cause);
    }

    private RedissonClient getRedissonClient() {
        RedissonClient redissonMQClient = GXSpringContextUtils.getBean("redissonMQClient", RedissonClient.class);
        if (redissonMQClient == null) {
            throw new GXBusinessException("Unable to get redissonMQClient bean");
        }
        return redissonMQClient;
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE - 1000;
    }

    private record QueueListenerConfig(
            String queueName,
            String topicName,
            int timeoutSeconds,
            GXRedissonDelayMQListener listener,
            RBlockingQueue<String> blockingQueue,
            RDelayedQueue<String> delayedQueue,
            BlockingQueue<String> localQueue) {
    }
}
