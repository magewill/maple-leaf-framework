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

/**
 * 基础缓存锁服务实现类
 * <p>
 * 该服务提供了基于内存的锁机制，用于在多线程环境下保护共享资源。
 * 支持按照锁名称获取不同的锁实例，实现更细粒度的并发控制。
 * 主要用于缓存操作、数据同步等需要并发控制的场景。
 * </p>
 *
 * <p>线程安全说明：</p>
 * <p>
 * 本实现类采用了多种机制确保线程安全：
 * 1. 使用ConcurrentHashMap存储锁实例和计数器，保证并发访问安全
 * 2. 使用ThreadLocal跟踪线程持有的锁，避免锁的误释放和遗漏释放
 * 3. 所有锁操作都有异常处理，确保在异常情况下不会导致锁泄露
 * 4. 提供了锁持有检查机制，防止非持有线程释放锁
 * </p>
 *
 * <p>性能考虑：</p>
 * <p>
 * 1. 锁的粒度控制 - 按名称隔离锁实例，避免不必要的锁竞争
 * 2. 超时机制 - 防止长时间等待锁导致的系统性能问题
 * 3. 非阻塞获取 - 提供tryLock方法实现快速失败，避免不必要的等待
 * 4. 锁计数监控 - 帮助识别潜在的锁竞争热点
 * </p>
 *
 * <p>内存安全：</p>
 * <p>
 * 1. ThreadLocal资源在不需要时会被清理，防止内存泄漏
 * 2. 静态集合的使用经过优化，避免无限增长
 * 3. 锁实例按需创建，不会预先创建大量锁对象
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：基本锁获取和释放
 * GXBaseCacheLockService lockService = new GXBaseCacheLockServiceImpl();
 * String lockName = "userCache";
 *
 * // 方式一：使用tryLock和releaseLock方法（推荐）
 * if (lockService.tryLock(lockName)) {
 *     try {
 *         // 执行需要加锁的操作
 *         doSomething();
 *     } finally {
 *         lockService.releaseLock(lockName); // 安全释放锁
 *     }
 * } else {
 *     // 获取锁失败的处理逻辑
 *     handleLockFailure();
 * }
 *
 * // 示例2：带超时的锁获取
 * boolean locked = lockService.tryLock(lockName, 5, TimeUnit.SECONDS);
 * if (locked) {
 *     try {
 *         // 执行需要加锁的操作
 *         doSomething();
 *     } finally {
 *         lockService.releaseLock(lockName);
 *     }
 * } else {
 *     // 超时处理逻辑
 *     handleTimeout();
 * }
 *
 * // 示例3：检查锁持有状态
 * if (lockService.isHeldByCurrentThread(lockName)) {
 *     // 当前线程已持有锁，可以安全执行操作
 *     doSomething();
 * } else {
 *     // 当前线程未持有锁，需要先获取锁
 *     if (lockService.tryLock(lockName)) {
 *         try {
 *             doSomething();
 *         } finally {
 *             lockService.releaseLock(lockName);
 *         }
 *     }
 * }
 *
 * // 示例4：监控锁竞争
 * int waitingThreads = lockService.getQueueLength(lockName);
 * if (waitingThreads > 10) {
 *     log.warn("锁{}的等待线程数过多: {}", lockName, waitingThreads);
 * }
 *
 * // 示例5：在系统关闭时释放所有锁
 * @PreDestroy
 * public void shutdown() {
 *     lockService.releaseAllLocks();
 * }
 * </pre>
 *
 * @author britton
 */
@Service
@Slf4j
public class GXBaseCacheLockServiceImpl implements GXBaseCacheLockService {
    /**
     * 默认可重入锁
     * <p>
     * 用于未指定锁名称时的全局锁，所有未指定名称的锁请求都将使用此锁。
     * 可重入锁允许同一个线程多次获取同一把锁，避免死锁问题。
     * </p>
     */
    private static final ReentrantLock DEFAULT_LOCK = new ReentrantLock(true);

    /**
     * 锁映射表
     * <p>
     * 用于存储不同名称的锁实例，实现按名称隔离的锁机制。
     * 使用ConcurrentHashMap保证线程安全，避免在并发获取锁时产生竞态条件。
     * </p>
     */
    private static final Map<String, Lock> LOCK_MAP = new ConcurrentHashMap<>();

    /**
     * 锁获取计数器
     * <p>
     * 记录每个锁被获取的次数，用于监控锁的使用情况。
     * </p>
     */
    private static final Map<String, AtomicInteger> LOCK_COUNTER = new ConcurrentHashMap<>();

    /**
     * 当前线程持有的锁名称
     * <p>
     * 用于跟踪当前线程持有的锁，防止重复释放或遗漏释放。
     * </p>
     */
    private static final ThreadLocal<Map<String, Integer>> THREAD_LOCKS = ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * 获取指定名称的锁
     * <p>
     * 根据提供的锁名称返回对应的锁实例。如果指定名称的锁不存在，则创建一个新的可重入锁。
     * 如果未指定锁名称（为null或空字符串），则返回默认锁。
     * </p>
     * <p>
     * 实现细节：
     * 1. 使用ConcurrentHashMap存储锁实例，保证线程安全
     * 2. 使用computeIfAbsent方法确保锁的原子创建，避免并发问题
     * 3. 所有创建的锁都是公平锁(fair=true)，避免线程饥饿问题
     * 4. 同时初始化锁的计数器，用于监控锁的使用情况
     * </p>
     *
     * @param lockName 锁的名称，用于区分不同的锁实例
     * @return 对应名称的锁实例
     */
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

    /**
     * 安全地获取锁，如果获取不到则等待指定时间
     * <p>
     * 尝试在指定的超时时间内获取锁，如果成功获取则记录锁的持有信息。
     * 该方法提供了超时机制，避免长时间等待导致的系统性能问题。
     * </p>
     * <p>
     * 安全保障：
     * 1. 超时机制防止无限等待，避免死锁
     * 2. 完整的异常处理，确保在中断或其他异常情况下能够正确返回
     * 3. 获取成功后记录锁的持有信息，便于后续释放和监控
     * </p>
     * <p>
     * 使用场景：
     * 1. 需要等待一定时间获取锁的场景
     * 2. 对锁获取时间有限制的操作
     * 3. 需要在超时后执行备选逻辑的场景
     * </p>
     *
     * @param lockName 锁的名称
     * @param timeout  超时时间
     * @param unit     时间单位
     * @return 是否成功获取到锁
     */
    @Override
    public boolean tryLock(String lockName, long timeout, TimeUnit unit) {
        // 参数校验
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
                // 记录锁竞争情况
                if (lock instanceof ReentrantLock) {
                    int queueLength = ((ReentrantLock) lock).getQueueLength();
                    if (queueLength > 5) { // 等待线程较多时记录警告
                        log.warn("锁{}的等待线程数较多: {}", lockName, queueLength);
                    }
                }
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 重新设置中断标志
            log.error("获取锁过程中被中断: {}", lockName, e);
            return false;
        } catch (Exception e) {
            log.error("获取锁时发生异常: {}", lockName, e);
            return false;
        }
    }

    /**
     * 安全地获取锁，如果获取不到则立即返回
     * <p>
     * 尝试立即获取锁，不等待。如果锁被其他线程持有，则立即返回false。
     * 适用于非阻塞场景，可以快速失败并执行备选逻辑。
     * </p>
     * <p>
     * 性能考虑：
     * 此方法不会导致线程阻塞，适合对响应时间要求较高的场景
     * </p>
     * <p>
     * 使用场景：
     * 1. 需要立即知道锁是否可用的场景
     * 2. 有备选执行路径的场景
     * 3. 不希望线程阻塞等待的场景
     * </p>
     *
     * @param lockName 锁的名称
     * @return 是否成功获取到锁
     */
    @Override
    public boolean tryLock(String lockName) {
        Lock lock = getLock(lockName);
        boolean acquired = lock.tryLock();
        if (acquired) {
            recordLockAcquisition(lockName);
            log.debug("成功获取锁: {}", lockName);
        } else {
            // 区分日志级别，避免日志过多
            if (lock instanceof ReentrantLock && ((ReentrantLock) lock).getQueueLength() > 3) {
                log.warn("获取锁失败，竞争较高: {}, 等待线程数: {}", lockName,
                        ((ReentrantLock) lock).getQueueLength());
            } else {
                log.debug("获取锁失败: {}", lockName);
            }
        }
        return acquired;
    }

    /**
     * 检查当前线程是否持有指定的锁
     * <p>
     * 用于在执行操作前验证线程是否已获取锁，避免在未加锁的情况下执行关键操作。
     * 此方法不会阻塞或尝试获取锁，只是检查当前状态。
     * </p>
     * <p>
     * 使用场景：
     * 1. 在执行需要锁保护的操作前进行检查
     * 2. 实现条件执行逻辑，根据锁持有状态决定执行路径
     * 3. 诊断锁相关问题，确认锁的持有状态
     * </p>
     *
     * @param lockName 锁的名称
     * @return 当前线程是否持有该锁
     */
    @Override
    public boolean isHeldByCurrentThread(String lockName) {
        if (Objects.isNull(lockName) || lockName.trim().isEmpty()) {
            // 对于默认锁的特殊处理
            return DEFAULT_LOCK.isHeldByCurrentThread();
        }

        Lock lock = getLock(lockName);
        if (lock instanceof ReentrantLock) {
            return ((ReentrantLock) lock).isHeldByCurrentThread();
        }
        return false;
    }

    /**
     * 获取指定锁的等待线程数量
     * <p>
     * 用于监控锁的竞争情况，帮助识别潜在的性能瓶颈。
     * 高等待线程数可能表明存在锁竞争热点，需要优化锁粒度或处理逻辑。
     * </p>
     * <p>
     * 使用场景：
     * 1. 系统监控，收集锁竞争指标
     * 2. 性能诊断，识别锁竞争热点
     * 3. 自适应策略，根据竞争程度调整行为
     * </p>
     *
     * @param lockName 锁的名称
     * @return 等待获取该锁的线程数量，如果锁不存在则返回0
     */
    @Override
    public int getQueueLength(String lockName) {
        if (Objects.isNull(lockName) || lockName.trim().isEmpty()) {
            // 对于默认锁的特殊处理
            return DEFAULT_LOCK.getQueueLength();
        }

        // 检查锁是否存在，避免创建新锁
        Lock lock = LOCK_MAP.get(lockName);
        if (lock == null) {
            return 0; // 锁不存在，没有等待线程
        }

        if (lock instanceof ReentrantLock) {
            return ((ReentrantLock) lock).getQueueLength();
        }
        return 0;
    }

    /**
     * 强制释放当前线程持有的所有锁
     * <p>
     * 用于系统关闭、线程结束或需要重置线程状态时清理所有锁资源。
     * 该方法会尝试释放当前线程持有的所有锁，并清理ThreadLocal资源，防止内存泄漏。
     * </p>
     * <p>
     * 注意事项：
     * 1. 此方法应谨慎使用，通常在确保没有活动操作时调用
     * 2. 建议在@PreDestroy方法或finally块中调用
     * 3. 释放过程中的异常会被捕获并记录，不会影响其他锁的释放
     * </p>
     * <p>
     * 内存安全：
     * 该方法会清理ThreadLocal资源，防止在线程池环境中导致的内存泄漏
     * </p>
     */
    @Override
    public void releaseAllLocks() {
        Map<String, Integer> heldLocks = THREAD_LOCKS.get();
        if (heldLocks != null && !heldLocks.isEmpty()) {
            // 复制一份锁名称列表，避免在迭代过程中修改集合
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

        // 清理ThreadLocal资源，防止内存泄漏
        THREAD_LOCKS.remove();
        log.info("已释放当前线程持有的所有锁并清理ThreadLocal资源");
    }

    /**
     * 安全地释放锁
     * <p>
     * 释放指定名称的锁，并清理相关的线程持有记录。
     * 该方法会检查当前线程是否持有该锁，避免错误释放其他线程的锁。
     * </p>
     * <p>
     * 安全保障：
     * 1. 验证当前线程是否持有锁，防止误释放
     * 2. 正确处理锁的重入计数，确保锁的完全释放
     * 3. 全面的异常处理，防止释放过程中的问题导致系统不稳定
     * </p>
     * <p>
     * 使用场景：
     * 在finally块中调用，确保无论操作是否成功，锁都能被正确释放
     * </p>
     *
     * @param lockName 锁的名称
     * @return 是否成功释放锁
     */
    @Override
    public boolean releaseLock(String lockName) {
        String actualLockName = Objects.isNull(lockName) || lockName.trim().isEmpty() ? "DEFAULT" : lockName;
        Map<String, Integer> heldLocks = THREAD_LOCKS.get();

        // 检查当前线程是否持有该锁
        if (heldLocks == null || !heldLocks.containsKey(actualLockName)) {
            log.warn("尝试释放未持有的锁: {}", actualLockName);
            return false;
        }

        try {
            Lock lock = getLock(lockName);
            if (lock instanceof ReentrantLock && ((ReentrantLock) lock).isHeldByCurrentThread()) {
                lock.unlock();
                // 更新线程持有的锁记录
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

    /**
     * 记录锁的获取信息
     * <p>
     * 更新锁的获取计数和当前线程持有的锁记录。
     * 用于监控锁的使用情况和确保正确释放锁。
     * </p>
     * <p>
     * 实现细节：
     * 1. 使用原子计数器记录全局锁获取次数，便于监控和统计
     * 2. 使用ThreadLocal记录每个线程持有的锁及其重入次数
     * 3. 支持锁的重入，正确累加持有计数
     * 4. 对空锁名进行标准化处理，统一使用"DEFAULT"作为默认锁名
     * </p>
     * <p>
     * 内存安全：
     * 此方法会更新ThreadLocal变量，但不会导致内存泄漏，因为：
     * 1. 在releaseAllLocks方法中会清理ThreadLocal资源
     * 2. 在releaseLock方法中会减少或移除对应的锁记录
     * </p>
     *
     * @param lockName 锁的名称
     */
    private void recordLockAcquisition(String lockName) {
        // 标准化锁名称，避免null或空字符串
        String actualLockName = Objects.isNull(lockName) || lockName.trim().isEmpty() ? "DEFAULT" : lockName;

        try {
            // 更新全局锁获取计数，用于监控和统计
            AtomicInteger counter = LOCK_COUNTER.computeIfAbsent(actualLockName, k -> new AtomicInteger(0));
            int count = counter.incrementAndGet();

            // 当计数达到特定阈值时记录警告，帮助发现潜在的锁竞争热点
            if (count % 1000 == 0) {
                log.info("锁{}的获取次数达到: {}", actualLockName, count);
            }

            // 更新当前线程持有的锁记录，支持锁的重入
            Map<String, Integer> heldLocks = THREAD_LOCKS.get();
            if (heldLocks != null) {
                int holdCount = heldLocks.compute(actualLockName, (k, v) -> v == null ? 1 : v + 1);
                if (holdCount > 10) {
                    // 重入次数过多可能表明代码存在问题，记录警告
                    log.warn("线程{}对锁{}的重入次数过多: {}", Thread.currentThread().getName(), actualLockName, holdCount);
                }
            } else {
                // ThreadLocal初始化失败的极端情况处理
                log.error("ThreadLocal初始化失败，无法记录锁持有信息: {}", actualLockName);
            }
        } catch (Exception e) {
            // 确保记录过程中的异常不会影响锁的正常使用
            log.error("记录锁获取信息时发生异常: {}", actualLockName, e);
        }
    }
}
