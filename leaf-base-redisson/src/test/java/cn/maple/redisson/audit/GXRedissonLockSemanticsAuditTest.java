package cn.maple.redisson.audit;

import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.redisson.util.GXRedissonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-11: lock helpers cannot distinguish "lock not acquired" from "operation
 * returned null", release costs extra round trips, and counter read failures are
 * reported as zero.
 */
class GXRedissonLockSemanticsAuditTest {

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": lock timeout is indistinguishable from a null result")
    void lockTimeoutReturnsNull() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lock:audit-lock")).thenReturn(lock);
        doReturn(false).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));

        try (MockedStatic<GXSpringContextUtils> context = Mockito.mockStatic(GXSpringContextUtils.class)) {
            context.when(() -> GXSpringContextUtils.getBean("redissonClient", RedissonClient.class)).thenReturn(client);

            // CURRENT BEHAVIOR: a timed-out acquisition returns null, exactly like an
            // operation that legitimately produced null.
            assertNull(GXRedissonUtils.executeWithLock("audit-lock", () -> null));
            assertNull(GXRedissonUtils.executeWithLock("audit-lock", () -> "never-executed"));
        }
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": release costs three round trips")
    void releaseCostsThreeRoundTrips() throws InterruptedException {
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("lock:audit-lock")).thenReturn(lock);
        doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        try (MockedStatic<GXSpringContextUtils> context = Mockito.mockStatic(GXSpringContextUtils.class)) {
            context.when(() -> GXSpringContextUtils.getBean("redissonClient", RedissonClient.class)).thenReturn(client);

            assertEquals("done", GXRedissonUtils.executeWithLock("audit-lock", () -> "done"));

            // CURRENT BEHAVIOR: unlock is preceded by isLocked() and
            // isHeldByCurrentThread(), i.e. three network round trips per release on
            // top of the acquisition.
            verify(lock, times(1)).isLocked();
            verify(lock, times(1)).isHeldByCurrentThread();
            verify(lock, times(1)).unlock();
        }
    }

    @Test
    @DisplayName(GXRedissonAuditTestSupport.CURRENT_BEHAVIOR + ": counter read failure is reported as zero")
    void counterReadFailureIsReportedAsZero() {
        RedissonClient client = mock(RedissonClient.class);
        RAtomicLong counter = mock(RAtomicLong.class);
        when(client.getAtomicLong("audit-counter")).thenReturn(counter);
        when(counter.get()).thenThrow(new IllegalStateException("redis unavailable"));

        try (MockedStatic<GXSpringContextUtils> context = Mockito.mockStatic(GXSpringContextUtils.class)) {
            context.when(() -> GXSpringContextUtils.getBean("redissonClient", RedissonClient.class)).thenReturn(client);

            // CURRENT BEHAVIOR: the failure is logged and swallowed into 0, which a
            // caller cannot distinguish from a real zero counter.
            assertEquals(0L, GXRedissonUtils.getCounterValue("audit-counter"));
        }
    }
}
