package cn.maple.core.framework.util;

import org.slf4j.Logger;

import java.util.Arrays;
import java.util.StringJoiner;

public class GXLoggerUtils {
    private GXLoggerUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    public static void logInfo(Logger logger, String desc, Object... data) {
        log(logger, LogLevel.INFO, desc, null, data);
    }

    public static void logDebug(Logger logger, String desc, Object... data) {
        log(logger, LogLevel.DEBUG, desc, null, data);
    }

    public static void logError(Logger logger, String desc, Object... data) {
        Throwable throwable = getLastThrowable(data);
        log(logger, LogLevel.ERROR, desc, throwable, removeLastThrowable(data));
    }

    public static void logErrorWithThrowable(Logger logger, String desc, Throwable throwable, Object... data) {
        log(logger, LogLevel.ERROR, desc, throwable, data);
    }

    public static void logWarn(Logger logger, String desc, Object... data) {
        log(logger, LogLevel.WARN, desc, null, data);
    }

    public static void logError(Logger logger, Throwable t) {
        log(logger, LogLevel.ERROR, "exception", t);
    }

    private static void log(Logger logger, LogLevel level, String desc, Throwable throwable, Object... data) {
        if (logger == null || !isEnabled(logger, level)) {
            return;
        }

        String format = generateFormat(desc, data);
        if (throwable == null) {
            writeLog(logger, level, format, data);
        } else {
            writeLog(logger, level, format, appendThrowable(data, throwable));
        }
    }

    private static String generateFormat(String desc, Object... data) {
        String threadName = Thread.currentThread().getName();
        String traceId = GXTraceIdContextUtils.getNullableTraceId();
        StringBuilder format = new StringBuilder(128)
                .append(GXTraceIdContextUtils.TRACE_ID_KEY)
                .append(" : ")
                .append(traceId == null ? "" : traceId)
                .append(" --> thread : ")
                .append(threadName)
                .append(" --> desc : ")
                .append(desc == null ? "" : desc);
        if (!isEmpty(data)) {
            format.append(" --> detail : ").append(generatePlaceholders(data.length));
        }
        return format.toString();
    }

    private static boolean isEnabled(Logger logger, LogLevel level) {
        return switch (level) {
            case INFO -> logger.isInfoEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case WARN -> logger.isWarnEnabled();
            case ERROR -> logger.isErrorEnabled();
        };
    }

    private static void writeLog(Logger logger, LogLevel level, String format, Object... data) {
        if (isEmpty(data)) {
            writeLog(logger, level, format);
            return;
        }

        switch (level) {
            case INFO -> logger.info(format, data);
            case DEBUG -> logger.debug(format, data);
            case WARN -> logger.warn(format, data);
            case ERROR -> logger.error(format, data);
        }
    }

    private static void writeLog(Logger logger, LogLevel level, String format) {
        switch (level) {
            case INFO -> logger.info(format);
            case DEBUG -> logger.debug(format);
            case WARN -> logger.warn(format);
            case ERROR -> logger.error(format);
        }
    }

    private static String generatePlaceholders(int count) {
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < count; i++) {
            joiner.add("{}");
        }
        return joiner.toString();
    }

    private static boolean isEmpty(Object[] data) {
        return data == null || data.length == 0;
    }

    private static Throwable getLastThrowable(Object[] data) {
        if (isEmpty(data)) {
            return null;
        }
        Object last = data[data.length - 1];
        return last instanceof Throwable throwable ? throwable : null;
    }

    private static Object[] removeLastThrowable(Object[] data) {
        return getLastThrowable(data) == null ? data : Arrays.copyOf(data, data.length - 1);
    }

    private static Object[] appendThrowable(Object[] data, Throwable throwable) {
        if (throwable == null) {
            return data;
        }
        if (isEmpty(data)) {
            return new Object[]{throwable};
        }
        Object[] arguments = Arrays.copyOf(data, data.length + 1);
        arguments[arguments.length - 1] = throwable;
        return arguments;
    }

    private enum LogLevel {
        INFO,
        DEBUG,
        WARN,
        ERROR
    }
}
