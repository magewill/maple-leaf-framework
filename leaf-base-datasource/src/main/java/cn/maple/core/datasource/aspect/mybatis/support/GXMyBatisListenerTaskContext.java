package cn.maple.core.datasource.aspect.mybatis.support;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Carries the write-operation identifier from the publishing thread into asynchronous delivery
 * tasks, so a rejected task can still be correlated with the write that produced it.
 */
public final class GXMyBatisListenerTaskContext {
    private static final String UNKNOWN_OPERATION_ID = "unknown";

    private static final ThreadLocal<String> OPERATION_ID = new ThreadLocal<>();

    private GXMyBatisListenerTaskContext() {
    }

    public static void setOperationId(@Nullable String operationId) {
        if (Objects.isNull(operationId) || operationId.isBlank()) {
            OPERATION_ID.remove();
            return;
        }
        OPERATION_ID.set(operationId);
    }

    public static @Nullable String currentOperationId() {
        return OPERATION_ID.get();
    }

    public static void clear() {
        OPERATION_ID.remove();
    }

    /**
     * Labels a task with the operation that produced it. The label survives the executor's task
     * decoration, which is what the rejection handler inspects.
     */
    public static Runnable label(@Nullable String operationId, Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        if (Objects.isNull(operationId) || operationId.isBlank()) {
            return task;
        }
        return new LabeledTask(operationId, task);
    }

    public static String operationIdOf(@Nullable Runnable task) {
        return task instanceof LabeledTask labeled ? labeled.operationId() : UNKNOWN_OPERATION_ID;
    }

    private record LabeledTask(String operationId, Runnable delegate) implements Runnable {
        @Override
        public void run() {
            delegate.run();
        }
    }
}
