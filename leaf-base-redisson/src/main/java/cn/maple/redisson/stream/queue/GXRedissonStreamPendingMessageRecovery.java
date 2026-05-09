package cn.maple.redisson.stream.queue;

import cn.maple.redisson.properties.GXRedissonStreamMQProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Recovers stale Redis Stream pending messages.
 */
@Slf4j
public class GXRedissonStreamPendingMessageRecovery {

    private static final String RECOVERY_LOCK_KEY = "mq:pending:recovery:lock";

    private final RedissonClient redissonMQClient;
    private final GXRedissonStreamMQProperties props;
    private final GXRedissonStreamImmediateMQ immediateQueue;

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    public GXRedissonStreamPendingMessageRecovery(RedissonClient redissonMQClient,
                                                  GXRedissonStreamMQProperties props,
                                                  GXRedissonStreamImmediateMQ immediateQueue) {
        if (redissonMQClient == null) {
            throw new IllegalArgumentException("redissonMQClient must not be null");
        }
        if (props == null) {
            throw new IllegalArgumentException("props must not be null");
        }
        if (immediateQueue == null) {
            throw new IllegalArgumentException("immediateQueue must not be null");
        }
        this.redissonMQClient = redissonMQClient;
        this.props = props;
        this.immediateQueue = immediateQueue;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("stream-pending-recovery-", 1).factory());
        scheduler.scheduleWithFixedDelay(
                this::recover,
                props.getPendingRecoveryInterval(),
                props.getPendingRecoveryInterval(),
                TimeUnit.MILLISECONDS
        );
        log.info("Stream pending recovery started, scanIntervalMillis={}, maxPendingMillis={}",
                props.getPendingRecoveryInterval(), props.getMaxPendingMillis());
    }

    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                if (!scheduler.awaitTermination(props.getBlockingTimeoutMillis() + 1000, TimeUnit.MILLISECONDS)) {
                    log.warn("Stream pending recovery scheduler did not stop before timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        log.info("Stream pending recovery stopped");
    }

    private void recover() {
        if (!running) return;

        RLock lock = redissonMQClient.getLock(RECOVERY_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.MILLISECONDS);
            if (!locked) {
                log.debug("Stream pending recovery lock is held by another instance");
                return;
            }
            for (String topic : immediateQueue.registeredTopics()) {
                try {
                    immediateQueue.recoverPendingMessages(topic);
                } catch (Exception e) {
                    log.error("Failed to recover stream topic, topic={}", topic, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
