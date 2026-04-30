package cn.maple.mongodb.datasource.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXMongoTemplateContextTest {
    @AfterEach
    void tearDown() {
        GXMongoTemplateContext.clear();
    }

    @Test
    void shouldRestoreNestedContext() {
        GXMongoTemplateContext.push("primaryMongoTemplate");
        assertEquals("primaryMongoTemplate", GXMongoTemplateContext.peek());

        GXMongoTemplateContext.push("secondaryMongoTemplate");
        assertEquals("secondaryMongoTemplate", GXMongoTemplateContext.peek());

        GXMongoTemplateContext.pop();
        assertEquals("primaryMongoTemplate", GXMongoTemplateContext.peek());

        GXMongoTemplateContext.pop();
        assertNull(GXMongoTemplateContext.peek());
    }

    @Test
    void shouldUseSnapshotCopyWithoutSharingMutableStack() {
        GXMongoTemplateContext.push("primaryMongoTemplate");
        GXMongoTemplateContext.Snapshot snapshot = GXMongoTemplateContext.capture();
        GXMongoTemplateContext.push("secondaryMongoTemplate");

        GXMongoTemplateContext.Snapshot previous = GXMongoTemplateContext.replaceWith(snapshot);
        try {
            assertEquals("primaryMongoTemplate", GXMongoTemplateContext.peek());
        } finally {
            GXMongoTemplateContext.replaceWith(previous);
        }

        assertEquals("secondaryMongoTemplate", GXMongoTemplateContext.peek());
    }

    @Test
    void shouldPropagateContextToVirtualThreadBySnapshot() throws Exception {
        GXMongoTemplateContext.push("secondaryMongoTemplate");
        GXMongoTemplateContext.Snapshot snapshot = GXMongoTemplateContext.capture();
        FutureTask<String> task = new FutureTask<>(() -> {
            GXMongoTemplateContext.Snapshot previous = GXMongoTemplateContext.replaceWith(snapshot);
            try {
                return GXMongoTemplateContext.peek();
            } finally {
                GXMongoTemplateContext.replaceWith(previous);
            }
        });

        Thread.ofVirtual().start(task).join();

        assertEquals("secondaryMongoTemplate", task.get());
        assertEquals("secondaryMongoTemplate", GXMongoTemplateContext.peek());
    }

    @Test
    void shouldNotInheritContextToNewThreadImplicitly() throws Exception {
        GXMongoTemplateContext.push("primaryMongoTemplate");
        FutureTask<String> task = new FutureTask<>(GXMongoTemplateContext::peek);

        Thread.ofVirtual().start(task).join();

        assertNull(task.get());
        assertEquals("primaryMongoTemplate", GXMongoTemplateContext.peek());
    }

    @Test
    void shouldNotLeakContextBetweenPooledThreadTasksWhenSnapshotIsRestored() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            assertNull(executorService.submit(GXMongoTemplateContext::peek).get());

            GXMongoTemplateContext.push("reportMongoTemplate");
            GXMongoTemplateContext.Snapshot snapshot = GXMongoTemplateContext.capture();

            String value = executorService.submit(wrapWithSnapshot(snapshot, GXMongoTemplateContext::peek)).get();

            assertEquals("reportMongoTemplate", value);
            assertNull(executorService.submit(GXMongoTemplateContext::peek).get());
            assertEquals("reportMongoTemplate", GXMongoTemplateContext.peek());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldRestorePreviousContextWhenSnapshotWrappedTaskThrows() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            GXMongoTemplateContext.push("archiveMongoTemplate");
            GXMongoTemplateContext.Snapshot snapshot = GXMongoTemplateContext.capture();

            ExecutionException exception = assertThrows(ExecutionException.class, () ->
                    executorService.submit(wrapWithSnapshot(snapshot, () -> {
                        assertEquals("archiveMongoTemplate", GXMongoTemplateContext.peek());
                        throw new IllegalStateException("boom");
                    })).get());

            assertEquals("boom", exception.getCause().getMessage());
            assertNull(executorService.submit(GXMongoTemplateContext::peek).get());
            assertEquals("archiveMongoTemplate", GXMongoTemplateContext.peek());
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldClearContextWhenReplacingWithNullOrEmptySnapshot() {
        GXMongoTemplateContext.push("primaryMongoTemplate");

        GXMongoTemplateContext.Snapshot previous = GXMongoTemplateContext.replaceWith(null);

        assertNull(GXMongoTemplateContext.peek());
        GXMongoTemplateContext.replaceWith(previous);
        assertEquals("primaryMongoTemplate", GXMongoTemplateContext.peek());
    }

    private <T> Callable<T> wrapWithSnapshot(GXMongoTemplateContext.Snapshot snapshot, Callable<T> callable) {
        return () -> {
            GXMongoTemplateContext.Snapshot previous = GXMongoTemplateContext.replaceWith(snapshot);
            try {
                return callable.call();
            } finally {
                GXMongoTemplateContext.replaceWith(previous);
            }
        };
    }
}
