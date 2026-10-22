package cn.maple.core.framework.audit;

import cn.maple.core.framework.event.center.AsyncEventBusCenter;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes the event dispatch model: one executor submission per subscriber and a
 * dedicated static executor that is never shut down.</p>
 */
class GXEventExecutionAuditTest {
    @Test
    void asyncDispatchSubmitsOncePerSubscriber() {
        AtomicInteger submissions = new AtomicInteger();
        Executor countingExecutor = command -> {
            submissions.incrementAndGet();
            Thread thread = new Thread(command, "audit-event-worker");
            thread.setDaemon(true);
            thread.start();
        };
        AsyncEventBus bus = new AsyncEventBus("audit-async-bus", countingExecutor);
        bus.register(new AuditSubscriber());
        bus.register(new AuditSubscriber());

        bus.post("payload");

        assertEquals(2, submissions.get());
    }

    @Test
    void syncDispatchRunsOnCallerThread() {
        AtomicInteger submissions = new AtomicInteger();
        EventBus bus = new EventBus("audit-sync-bus");
        bus.register(new AuditSubscriber());

        bus.post("payload");

        assertEquals(0, submissions.get());
    }

    @Test
    void staticAsyncCenterOwnsUnmanagedExecutorWithCallerRunsPolicy() throws Exception {
        ThreadPoolExecutor executor = staticExecutor();

        assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class, executor.getRejectedExecutionHandler());
        assertTrue(executor.getQueue().remainingCapacity() > 0);
        assertEquals(false, executor.isShutdown());
    }

    private ThreadPoolExecutor staticExecutor() throws Exception {
        Field field = AsyncEventBusCenter.class.getDeclaredField("THREAD_POOL_EXECUTOR");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(null);
    }

    static class AuditSubscriber {
        @Subscribe
        public void onEvent(String event) {
            // no-op: only dispatch accounting matters here
        }
    }
}
