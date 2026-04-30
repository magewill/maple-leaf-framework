package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.*;

@SuppressWarnings("unused")
public class GXMdcThreadUtils {
    private GXMdcThreadUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    public static void setTraceIdIfAbsent() {
        if (CharSequenceUtil.isBlank(GXTraceIdContextUtils.getTraceId())) {
            GXTraceIdContextUtils.setTraceId(GXTraceIdContextUtils.generateTraceId());
        }
    }

    public static Map<String, String> getMdcContext() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        if (context == null || context.isEmpty()) {
            return null;
        }
        return Map.copyOf(context);
    }

    public static <T> Callable<T> wrap(final Callable<T> callable, final Map<String, String> context) {
        Objects.requireNonNull(callable, "Callable cannot be null");
        return () -> {
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                setTraceIdIfAbsent();
                return callable.call();
            } finally {
                MDC.clear();
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }

    public static Runnable wrap(final Runnable runnable, final Map<String, String> context) {
        Objects.requireNonNull(runnable, "Runnable cannot be null");
        return () -> {
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                setTraceIdIfAbsent();
                runnable.run();
            } finally {
                MDC.clear();
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }

    public static void restoreMdcContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    public static <T> Supplier<T> wrapSupplier(final Supplier<T> supplier, final Map<String, String> context) {
        Objects.requireNonNull(supplier, "Supplier cannot be null");
        return () -> {
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                setTraceIdIfAbsent();
                return supplier.get();
            } finally {
                MDC.clear();
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.supplyAsync(wrapSupplier(supplier, context));
    }

    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.supplyAsync(wrapSupplier(supplier, context), executor);
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.runAsync(wrap(runnable, context));
    }

    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        Map<String, String> context = getMdcContext();
        return CompletableFuture.runAsync(wrap(runnable, context), executor);
    }

    public static <T, R> Function<T, R> contextWrapper(Function<T, R> function) {
        Objects.requireNonNull(function, "Function cannot be null");
        Map<String, String> context = getMdcContext();
        return contextWrapper(function, context);
    }

    public static <T, R> Function<T, R> contextWrapper(Function<T, R> function, Map<String, String> context) {
        Objects.requireNonNull(function, "Function cannot be null");
        return input -> {
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                if (context == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(context);
                }
                setTraceIdIfAbsent();
                return function.apply(input);
            } finally {
                MDC.clear();
                if (originalContext != null) {
                    MDC.setContextMap(originalContext);
                }
            }
        };
    }

    public static <T> Consumer<T> consumerWrapper(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "Consumer cannot be null");
        Map<String, String> context = getMdcContext();
        return consumerWrapper(consumer, context);
    }

    public static <T> Consumer<T> consumerWrapper(Consumer<T> consumer, Map<String, String> context) {
        Objects.requireNonNull(consumer, "Consumer cannot be null");
        return input -> contextWrapper((T value) -> {
            consumer.accept(value);
            return null;
        }, context).apply(input);
    }

    public static <T, U, R> BiFunction<T, U, R> biFunctionWrapper(BiFunction<T, U, R> function, Map<String, String> context) {
        Objects.requireNonNull(function, "BiFunction cannot be null");
        return (left, right) -> contextWrapper((T value) -> function.apply(value, right), context).apply(left);
    }

    public static <T, U> BiConsumer<T, U> biConsumerWrapper(BiConsumer<T, U> consumer, Map<String, String> context) {
        Objects.requireNonNull(consumer, "BiConsumer cannot be null");
        return (left, right) -> biFunctionWrapper((T value, U throwable) -> {
            consumer.accept(value, throwable);
            return null;
        }, context).apply(left, right);
    }
}
