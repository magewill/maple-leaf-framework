package cn.maple.core.framework.service;

import java.util.concurrent.TimeUnit;

public interface GXBaseCacheLockService {
    boolean tryLock(String lockName, long timeout, TimeUnit unit);

    boolean tryLock(String lockName);

    boolean releaseLock(String lockName);

    boolean isHeldByCurrentThread(String lockName);

    int getQueueLength(String lockName);

    void releaseAllLocks();
}
