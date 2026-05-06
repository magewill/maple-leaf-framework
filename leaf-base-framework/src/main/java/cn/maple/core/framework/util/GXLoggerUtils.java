package cn.maple.core.framework.util;

import org.slf4j.Logger;

import java.util.StringJoiner;

public class GXLoggerUtils {
    private GXLoggerUtils() {
    }

    public static void logInfo(Logger logger, String desc, Object... data) {
        if (logger == null || !logger.isInfoEnabled()) {
            return;
        }
        String format = generateFormat(desc, data);
        if (isEmpty(data)) {
            logger.info(format);
        } else {
            logger.info(format, data);
        }
    }

    public static void logDebug(Logger logger, String desc, Object... data) {
        if (logger == null || !logger.isDebugEnabled()) {
            return;
        }
        String format = generateFormat(desc, data);
        if (isEmpty(data)) {
            logger.debug(format);
        } else {
            logger.debug(format, data);
        }
    }

    public static void logError(Logger logger, String desc, Object... data) {
        if (logger == null || !logger.isErrorEnabled()) {
            return;
        }
        String format = generateFormat(desc, data);
        if (isEmpty(data)) {
            logger.error(format);
        } else {
            logger.error(format, data);
        }
    }

    public static void logWarn(Logger logger, String desc, Object... data) {
        if (logger == null || !logger.isWarnEnabled()) {
            return;
        }
        String format = generateFormat(desc, data);
        if (isEmpty(data)) {
            logger.warn(format);
        } else {
            logger.warn(format, data);
        }
    }

    public static void logError(Logger logger, Throwable t) {
        if (logger == null || !logger.isErrorEnabled()) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        String format = "thread : " + threadName;
        if (t == null) {
            logger.error(format);
        } else {
            logger.error(format, t);
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
}
