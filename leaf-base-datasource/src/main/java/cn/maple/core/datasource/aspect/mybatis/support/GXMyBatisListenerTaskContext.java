package cn.maple.core.datasource.aspect.mybatis.support;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Carries the write-operation identifier from the publishing thread into asynchronous delivery
 * tasks, so a rejected task can still be correlated with the write that produced it.
 *
 * <p>It also tracks how deep the current callback chain is. A listener that writes through an
 * annotated mapper would otherwise re-enter the publisher without bound (spec edge case
 * "递归触发"), so the depth is incremented around every callback and carried into asynchronous
 * tasks; {@link GXMyBatisListenerEventPublisher} refuses to deliver beyond the configured limit.</p>
 */
public final class GXMyBatisListenerTaskContext {
    private static final String UNKNOWN_OPERATION_ID = "unknown";

    private static final ThreadLocal<String> OPERATION_ID = new ThreadLocal<>();

    private static final ThreadLocal<Integer> DELIVERY_DEPTH = new ThreadLocal<>();

    private GXMyBatisListenerTaskContext() {
    }

    /**
     * Current callback nesting depth of this thread; {@code 0} outside any callback.
     */
    public static int currentDeliveryDepth() {
        Integer depth = DELIVERY_DEPTH.get();
        return Objects.isNull(depth) ? 0 : depth;
    }

    public static void setDeliveryDepth(int depth) {
        if (depth <= 0) {
            DELIVERY_DEPTH.remove();
            return;
        }
        DELIVERY_DEPTH.set(depth);
    }

    /**
     * Marks the start of one callback; must be paired with {@link #exitDelivery()}.
     */
    public static void enterDelivery() {
        setDeliveryDepth(currentDeliveryDepth() + 1);
    }

    public static void exitDelivery() {
        setDeliveryDepth(currentDeliveryDepth() - 1);
    }

    /**
     * Runs a task with the depth of the write that submitted it, so an asynchronous callback chain
     * keeps counting and cannot grow without bound either. The previous depth is restored afterwards
     * because executor threads are reused.
     */
    public static Runnable withDeliveryDepth(int depth, Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        return () -> {
            int previousDepth = currentDeliveryDepth();
            setDeliveryDepth(depth);
            try {
                task.run();
            } finally {
                setDeliveryDepth(previousDepth);
            }
        };
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
