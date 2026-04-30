package cn.maple.core.datasource.util;

import cn.maple.core.datasource.dto.GXDataFilterInnerDto;

import java.util.Objects;
import java.util.concurrent.Callable;

public class GXDataFilterThreadLocalUtils {
    private static final ThreadLocal<GXDataFilterInnerDto> DATA_FILTER_INNER_DTO = new ThreadLocal<>();

    private GXDataFilterThreadLocalUtils() {
    }

    public static GXDataFilterInnerDto getDataFilterInnerDto() {
        return copy(DATA_FILTER_INNER_DTO.get());
    }

    public static void setDataFilterInnerDto(GXDataFilterInnerDto dto) {
        GXDataFilterInnerDto snapshot = copy(dto);
        if (snapshot == null) {
            DATA_FILTER_INNER_DTO.remove();
            return;
        }
        DATA_FILTER_INNER_DTO.set(snapshot);
    }

    public static void cleanDataFilterInnerDto() {
        DATA_FILTER_INNER_DTO.remove();
    }

    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "Task must not be null");
        GXDataFilterInnerDto capturedContext = snapshot();
        return () -> {
            GXDataFilterInnerDto previousContext = snapshot();
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
        GXDataFilterInnerDto capturedContext = snapshot();
        return () -> {
            GXDataFilterInnerDto previousContext = snapshot();
            restore(capturedContext);
            try {
                return task.call();
            } finally {
                restore(previousContext);
            }
        };
    }

    private static GXDataFilterInnerDto snapshot() {
        return copy(DATA_FILTER_INNER_DTO.get());
    }

    private static void restore(GXDataFilterInnerDto context) {
        if (context == null) {
            DATA_FILTER_INNER_DTO.remove();
            return;
        }
        DATA_FILTER_INNER_DTO.set(copy(context));
    }

    private static GXDataFilterInnerDto copy(GXDataFilterInnerDto dto) {
        if (dto == null) {
            return null;
        }
        return new GXDataFilterInnerDto(dto.getSqlFilter());
    }
}
