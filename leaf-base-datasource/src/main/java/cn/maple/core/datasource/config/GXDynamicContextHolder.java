package cn.maple.core.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Thread-local dynamic datasource context.
 */
public class GXDynamicContextHolder {
    private static final ThreadLocal<Deque<String>> CONTEXT_HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    private GXDynamicContextHolder() {
    }

    public static String peek() {
        Deque<String> deque = CONTEXT_HOLDER.get();
        String dataSourceName = deque.peek();
        if (deque.isEmpty()) {
            CONTEXT_HOLDER.remove();
        }
        return dataSourceName;
    }

    public static void push(String dataSourceName) {
        if (CharSequenceUtil.isBlank(dataSourceName)) {
            throw new IllegalArgumentException("Datasource name must not be blank");
        }
        CONTEXT_HOLDER.get().push(dataSourceName);
    }

    public static void poll() {
        Deque<String> deque = CONTEXT_HOLDER.get();
        if (!deque.isEmpty()) {
            deque.poll();
        }
        if (deque.isEmpty()) {
            CONTEXT_HOLDER.remove();
        }
    }

    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    public static AutoCloseableDataSource withDataSource(String dataSourceName) {
        push(dataSourceName);
        return new AutoCloseableDataSource();
    }

    public static <T> T executeOn(String dataSourceName, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "Operation must not be null");
        push(dataSourceName);
        try {
            return supplier.get();
        } finally {
            poll();
        }
    }

    public static void executeOnVoid(String dataSourceName, Runnable runnable) {
        Objects.requireNonNull(runnable, "Operation must not be null");
        push(dataSourceName);
        try {
            runnable.run();
        } finally {
            poll();
        }
    }

    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        Deque<String> capturedContext = snapshot();
        return () -> {
            Deque<String> previousContext = snapshot();
            restore(capturedContext);
            try {
                task.run();
            } finally {
                restore(previousContext);
            }
        };
    }

    public static <V> Callable<V> wrap(Callable<V> task) {
        Objects.requireNonNull(task, "Task must not be null");
        Deque<String> capturedContext = snapshot();
        return () -> {
            Deque<String> previousContext = snapshot();
            restore(capturedContext);
            try {
                return task.call();
            } finally {
                restore(previousContext);
            }
        };
    }

    private static Deque<String> snapshot() {
        Deque<String> deque = CONTEXT_HOLDER.get();
        Deque<String> snapshot = new ArrayDeque<>(deque);
        if (deque.isEmpty()) {
            CONTEXT_HOLDER.remove();
        }
        return snapshot;
    }

    private static void restore(Deque<String> context) {
        CONTEXT_HOLDER.remove();
        if (context.isEmpty()) {
            return;
        }
        Deque<String> deque = CONTEXT_HOLDER.get();
        Iterator<String> iterator = context.descendingIterator();
        while (iterator.hasNext()) {
            deque.push(iterator.next());
        }
    }

    public static class AutoCloseableDataSource implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (!closed) {
                poll();
                closed = true;
            }
        }
    }
}
