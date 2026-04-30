package cn.maple.core.framework.service.impl;

import cn.maple.core.framework.service.GXBaseCacheLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class GXBaseCacheLockServiceImpl implements GXBaseCacheLockService {
    private static final ReentrantLock DEFAULT_LOCK = new ReentrantLock(true);

    private static final Map<String, Lock> LOCK_MAP = new ConcurrentHashMap<>();

    private static final Map<String, AtomicInteger> LOCK_COUNTER = new ConcurrentHashMap<>();

    private static final ThreadLocal<Map<String, Integer>> THREAD_LOCKS = ThreadLocal.withInitial(ConcurrentHashMap::new);

    private Lock getLock(String lockName) {
        if (Objects.isNull(lockName) || lockName.trim().isEmpty()) {
            log.debug("使用默认锁");
            return DEFAULT_LOCK;
        }

        // 如果锁不存在，则创建一个新的可重入锁并放入映射表
        return LOCK_MAP.computeIfAbsent(lockName, k -> {
            log.debug("为{}创建新的锁实例", lockName);
            // 同时初始化锁计数器
            LOCK_COUNTER.putIfAbsent(k, new AtomicInteger(0));
            // 创建公平锁，避免线程饥饿问题
            return new ReentrantLock(true);
        });
    }

    @Override
    public boolean tryLock(String lockName, long timeout, TimeUnit unit) {
        if (timeout < 0) {
            log.warn("锁超时时间不能为负数: {}", timeout);
            return false;
        }

        Lock lock = getLock(lockName);
        try {
            boolean acquired = lock.tryLock(timeout, unit);
            if (acquired) {
                recordLockAcquisition(lockName);
                log.debug("成功获取锁: {}, 超时时间: {} {}", lockName, timeout, unit);
            } else {
                log.warn("获取锁超时: {}, 超时时间: {} {}", lockName, timeout, unit);
                if (lock instanceof ReentrantLock) {
                    int queueLength = ((ReentrantLock) lock).getQueueLength();
                    if (queueLength > 5) {
                        log.warn("锁{}的等待线程数较多: {}", lockName, queueLength);
                    }
                }
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁过程中被中断: {}", lockName, e);
            return false;
        } catch (Exception e) {
            log.error("获取锁时发生异常: {}", lockName, e);
            return false;
        }
    }

    @Override
    public boolean tryLock(String lockName) {
        Lock lock = getLock(lockName);
        boolean acquired = lock.tryLock();
        if (acquired) {
            recordLockAcquisition(lockName);
            log.debug("成功获取锁: {}", lockName);
        } else {
            if (lock instanceof ReentrantLock && ((ReentrantLock) lock).getQueueLength() > 3) {
                log.warn("获取锁失败，竞争较高: {}, 等待线程数: {}", lockName,
                        ((ReentrantLock) lock).getQueueLength());
            } else {
                log.debug("获取锁失败: {}", lockName);
            }
        }
        return acquired;
    }

    @Override
    public boolean isHeldByCurrentThread(String lockName) {
        if (Objects.isNull(lockName) || lockName.trim().isEmpty()) {
            return DEFAULT_LOCK.isHeldByCurrentThread();
        }

        Lock lock = getLock(lockName);
        if (lock instanceof ReentrantLock) {
            return ((ReentrantLock) lock).isHeldByCurrentThread();
        }
        return false;
    }

    @Override
    public int getQueueLength(String lockName) {
        if (Objects.isNull(lockName) || lockName.trim().isEmpty()) {
            return DEFAULT_LOCK.getQueueLength();
        }

        Lock lock = LOCK_MAP.get(lockName);
        if (lock == null) {
            return 0;
        }

        if (lock instanceof ReentrantLock) {
            return ((ReentrantLock) lock).getQueueLength();
        }
        return 0;
    }

    @Override
    public void releaseAllLocks() {
        Map<String, Integer> heldLocks = THREAD_LOCKS.get();
        if (heldLocks != null && !heldLocks.isEmpty()) {
            List<String> lockNames = new ArrayList<>(heldLocks.keySet());
            int successCount = 0;
            int failCount = 0;

            for (String lockName : lockNames) {
                try {
                    if (releaseLock(lockName)) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("释放锁时发生异常: {}", lockName, e);
                }
            }

            log.info("锁释放统计 - 成功: {}, 失败: {}", successCount, failCount);
        }

        THREAD_LOCKS.remove();
        log.info("已释放当前线程持有的所有锁并清理ThreadLocal资源");
    }

    @Override
    public boolean releaseLock(String lockName) {
        String actualLockName = Objects.isNull(lockName) || lockName.trim().isEmpty() ? "DEFAULT" : lockName;
        Map<String, Integer> heldLocks = THREAD_LOCKS.get();

        if (heldLocks == null || !heldLocks.containsKey(actualLockName)) {
            log.warn("尝试释放未持有的锁: {}", actualLockName);
            return false;
        }

        try {
            Lock lock = getLock(lockName);
            if (lock instanceof ReentrantLock && ((ReentrantLock) lock).isHeldByCurrentThread()) {
                lock.unlock();
                int holdCount = heldLocks.get(actualLockName) - 1;
                if (holdCount <= 0) {
                    heldLocks.remove(actualLockName);
                    log.debug("完全释放锁: {}", actualLockName);
                } else {
                    heldLocks.put(actualLockName, holdCount);
                    log.debug("部分释放锁: {}, 剩余持有计数: {}", actualLockName, holdCount);
                }
                return true;
            } else {
                log.warn("当前线程未持有锁: {}", actualLockName);
                return false;
            }
        } catch (IllegalMonitorStateException e) {
            log.error("释放锁时状态异常: {}", actualLockName, e);
            return false;
        } catch (Exception e) {
            log.error("释放锁时发生异常: {}", actualLockName, e);
            return false;
        }
    }

    private void recordLockAcquisition(String lockName) {
        String actualLockName = Objects.isNull(lockName) || lockName.trim().isEmpty() ? "DEFAULT" : lockName;

        try {
            AtomicInteger counter = LOCK_COUNTER.computeIfAbsent(actualLockName, k -> new AtomicInteger(0));
            int count = counter.incrementAndGet();

            if (count % 1000 == 0) {
                log.info("锁{}的获取次数达到: {}", actualLockName, count);
            }

            Map<String, Integer> heldLocks = THREAD_LOCKS.get();
            if (heldLocks != null) {
                int holdCount = heldLocks.compute(actualLockName, (k, v) -> v == null ? 1 : v + 1);
                if (holdCount > 10) {
                    log.warn("线程{}对锁{}的重入次数过多: {}", Thread.currentThread().getName(), actualLockName, holdCount);
                }
            } else {
                log.error("ThreadLocal初始化失败，无法记录锁持有信息: {}", actualLockName);
            }
        } catch (Exception e) {
            log.error("记录锁获取信息时发生异常: {}", actualLockName, e);
        }
    }
}
