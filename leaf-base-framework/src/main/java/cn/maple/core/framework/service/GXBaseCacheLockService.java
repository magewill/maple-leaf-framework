package cn.maple.core.framework.service;

import java.util.concurrent.TimeUnit;

/**
 * 基础缓存锁服务接口
 * <p>
 * 该接口提供了基于内存的锁机制，用于在多线程环境下保护共享资源。
 * 支持按照锁名称获取不同的锁实例，实现更细粒度的并发控制。
 * 主要用于缓存操作、数据同步等需要并发控制的场景。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：基本锁获取和释放
 * GXBaseCacheLockService lockService = new GXBaseCacheLockServiceImpl();
 * String lockName = "userCache";
 *
 * // 使用tryLock和releaseLock方法
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
 * </pre>
 *
 * @author britton
 */
public interface GXBaseCacheLockService {
    /**
     * 安全地获取锁，如果获取不到则等待指定时间
     * <p>
     * 尝试在指定的超时时间内获取锁，如果成功获取则记录锁的持有信息。
     * 该方法提供了超时机制，避免长时间等待导致的系统性能问题。
     * </p>
     *
     * @param lockName 锁的名称
     * @param timeout  超时时间
     * @param unit     时间单位
     * @return 是否成功获取到锁
     * @throws InterruptedException 如果当前线程在等待锁的过程中被中断
     */
    boolean tryLock(String lockName, long timeout, TimeUnit unit);

    /**
     * 安全地获取锁，如果获取不到则立即返回
     * <p>
     * 尝试立即获取锁，不等待。如果锁被其他线程持有，则立即返回false。
     * 适用于非阻塞场景，可以快速失败并执行备选逻辑。
     * </p>
     *
     * @param lockName 锁的名称
     * @return 是否成功获取到锁
     */
    boolean tryLock(String lockName);

    /**
     * 安全地释放锁
     * <p>
     * 释放指定名称的锁，并清理相关的线程持有记录。
     * 该方法会检查当前线程是否持有该锁，避免错误释放其他线程的锁。
     * </p>
     *
     * @param lockName 锁的名称
     * @return 是否成功释放锁
     */
    boolean releaseLock(String lockName);

    /**
     * 检查当前线程是否持有指定的锁
     * <p>
     * 用于在执行操作前验证线程是否已获取锁，避免在未加锁的情况下执行关键操作。
     * </p>
     *
     * @param lockName 锁的名称
     * @return 当前线程是否持有该锁
     */
    boolean isHeldByCurrentThread(String lockName);

    /**
     * 获取指定锁的等待线程数量
     * <p>
     * 用于监控锁的竞争情况，帮助识别潜在的性能瓶颈。
     * </p>
     *
     * @param lockName 锁的名称
     * @return 等待获取该锁的线程数量，如果锁不存在则返回0
     */
    int getQueueLength(String lockName);

    /**
     * 强制释放所有锁
     * <p>
     * 用于系统关闭或重置时清理所有锁资源。
     * 警告：此操作可能导致数据不一致，仅应在确保没有活动线程使用锁时调用。
     * </p>
     */
    void releaseAllLocks();
}
