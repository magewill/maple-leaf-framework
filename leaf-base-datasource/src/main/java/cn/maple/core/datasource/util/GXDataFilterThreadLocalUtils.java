package cn.maple.core.datasource.util;

import cn.maple.core.datasource.dto.GXDataFilterContext;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;

import java.util.Objects;
import java.util.concurrent.Callable;

public final class GXDataFilterThreadLocalUtils {
    private static final ThreadLocal<GXDataFilterContext> DATA_FILTER_CONTEXT = new ThreadLocal<>();

    private GXDataFilterThreadLocalUtils() {
    }

    public static GXDataFilterContext getDataFilterContext() {
        return copy(DATA_FILTER_CONTEXT.get());
    }

    public static void setDataFilterContext(GXDataFilterContext context) {
        GXDataFilterContext snapshot = copy(context);
        if (snapshot == null) {
            DATA_FILTER_CONTEXT.remove();
            return;
        }
        DATA_FILTER_CONTEXT.set(snapshot);
    }

    public static void cleanDataFilterContext() {
        DATA_FILTER_CONTEXT.remove();
    }

    public static GXDataFilterInnerDto getDataFilterInnerDto() {
        GXDataFilterContext context = getDataFilterContext();
        if (context == null) {
            return null;
        }
        return new GXDataFilterInnerDto(context);
    }

    public static void setDataFilterInnerDto(GXDataFilterInnerDto dto) {
        setDataFilterContext(dto);
    }

    public static void cleanDataFilterInnerDto() {
        cleanDataFilterContext();
    }

    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        GXDataFilterContext capturedContext = snapshot();
        return () -> {
            GXDataFilterContext previousContext = snapshot();
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
        GXDataFilterContext capturedContext = snapshot();
        return () -> {
            GXDataFilterContext previousContext = snapshot();
            restore(capturedContext);
            try {
                return task.call();
            } finally {
                restore(previousContext);
            }
        };
    }

    private static GXDataFilterContext snapshot() {
        return copy(DATA_FILTER_CONTEXT.get());
    }

    private static void restore(GXDataFilterContext context) {
        if (context == null) {
            DATA_FILTER_CONTEXT.remove();
            return;
        }
        DATA_FILTER_CONTEXT.set(copy(context));
    }

    private static GXDataFilterContext copy(GXDataFilterContext context) {
        if (context == null) {
            return null;
        }
        return new GXDataFilterContext(context);
    }
}
