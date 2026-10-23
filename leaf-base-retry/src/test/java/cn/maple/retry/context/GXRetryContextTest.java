package cn.maple.retry.context;

import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXRetryContextTest {

    @Test
    void getAttributes_ReturnsStableUnmodifiableSnapshot() {
        GXRetryContext context = new GXRetryContext(0, null);
        context.setAttribute("first", "one");

        Map<String, Object> snapshot = context.getAttributes();
        context.setAttribute("second", "two");

        assertEquals(1, snapshot.size());
        assertEquals("one", snapshot.get("first"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("third", "three"));
    }

    @Test
    void attributeNames_ReturnsSnapshot() {
        GXRetryContext context = new GXRetryContext(0, null);
        context.setAttribute("first", "one");

        String[] names = context.attributeNames();
        context.setAttribute("second", "two");

        assertEquals(1, names.length);
        assertEquals("first", names[0]);
    }

    @Test
    void attributes_AllowNullNameAndNullValue() {
        GXRetryContext context = new GXRetryContext(0, null);

        context.setAttribute(null, null);

        assertNull(context.getAttribute(null));
        assertEquals(1, context.attributeNames().length);
        assertNull(context.attributeNames()[0]);
        assertNull(context.getAttributes().get(null));
    }

    @Test
    void attributeNames_PreserveInsertionOrder() {
        GXRetryContext context = new GXRetryContext(0, null);
        context.setAttribute("first", "one");
        context.setAttribute("second", "two");
        context.setAttribute("first", "updated");

        String[] names = context.attributeNames();
        assertEquals("first", names[0]);
        assertEquals("second", names[1]);
        assertEquals("updated", context.getAttributes().get("first"));
    }

    @Test
    void attributes_AreCopiedFromParentContext() {
        GXRetryContext parent = new GXRetryContext(0, null);
        parent.setAttribute("endpoint", "node-b");
        parent.setAttribute("attempts-hint", 1);

        GXRetryContext child = new GXRetryContext(1, new IllegalStateException("boom"), parent);

        assertSame(parent, child.getParent());
        assertEquals("node-b", child.getAttribute("endpoint"));
        assertEquals(1, child.getAttribute("attempts-hint"));
        assertEquals(1, child.getRetryCount());
        assertEquals(1, child.getAttemptIndex());
    }

    @Test
    void attributes_WrittenByChildDoNotLeakIntoParent() {
        GXRetryContext parent = new GXRetryContext(0, null);
        GXRetryContext child = new GXRetryContext(1, null, parent);

        child.setAttribute("only-child", true);

        assertNull(parent.getAttribute("only-child"));
        assertEquals(true, child.getAttribute("only-child"));
    }

    @Test
    void from_RetryStateUsesRetryCountAndOptionalParent() {
        GXRetryContext parent = new GXRetryContext(0, null);
        parent.setAttribute("endpoint", "node-c");
        RetryState retryState = new RetryState() {
            @Override
            public int getRetryCount() {
                return 2;
            }

            @Override
            public List<Throwable> getExceptions() {
                return List.of(new IllegalStateException("first"), new IllegalStateException("last"));
            }
        };

        GXRetryContext withoutParent = GXRetryContext.from(retryState);
        GXRetryContext withParent = GXRetryContext.from(retryState, parent);

        assertEquals(2, withoutParent.getRetryCount());
        assertEquals("last", withoutParent.getLastThrowable().getMessage());
        assertNull(withoutParent.getParent());
        assertSame(parent, withParent.getParent());
        assertEquals("node-c", withParent.getAttribute("endpoint"));
    }

    @Test
    void attributes_AreInheritedAcrossThreeAttempts() {
        GXRetryContext first = new GXRetryContext(0, null);
        first.setAttribute("attempt-0", "a");
        GXRetryContext second = new GXRetryContext(1, null, first);
        second.setAttribute("attempt-1", "b");
        GXRetryContext third = new GXRetryContext(2, null, second);

        assertEquals("a", third.getAttribute("attempt-0"));
        assertEquals("b", third.getAttribute("attempt-1"));
        assertSame(second, third.getParent());
        assertSame(first, third.getParent().getParent());
        assertNull(third.getParent().getParent().getParent());
    }

    @Test
    void getLastThrowableOptional_ReflectsPresence() {
        GXRetryContext withoutFailure = new GXRetryContext(0, null);
        GXRetryContext withFailure = new GXRetryContext(1, new IllegalStateException("boom"));

        assertTrue(withoutFailure.getLastThrowableOptional().isEmpty());
        assertEquals("boom", withFailure.getLastThrowableOptional().orElseThrow().getMessage());
    }

    @Test
    void setAttribute_IsSafeUnderConcurrentWrites() throws Exception {
        GXRetryContext context = new GXRetryContext(0, null);
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
                final int owner = threadIndex;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int index = 0; index < perThread; index++) {
                        context.setAttribute("k-" + owner + "-" + index, index);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(threads * perThread, context.getAttributes().size());
        assertEquals(threads * perThread, context.attributeNames().length);
    }

    @Test
    void getAttributes_IsReadableFromAnotherThread() throws InterruptedException {
        GXRetryContext context = new GXRetryContext(0, null);
        context.setAttribute("shared", "value");
        AtomicReference<Map<String, Object>> read = new AtomicReference<>();

        Thread thread = new Thread(() -> read.set(context.getAttributes()));
        thread.start();
        thread.join();

        assertEquals("value", read.get().get("shared"));
    }
}
