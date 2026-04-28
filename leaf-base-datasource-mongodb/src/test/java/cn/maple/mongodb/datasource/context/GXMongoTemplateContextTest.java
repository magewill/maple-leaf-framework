package cn.maple.mongodb.datasource.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
