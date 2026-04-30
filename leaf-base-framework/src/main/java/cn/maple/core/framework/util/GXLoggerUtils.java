package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;

import java.util.ArrayList;

public class GXLoggerUtils {
    private GXLoggerUtils() {
    }

    public static void logInfo(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.info(format, data);
    }

    public static void logDebug(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.debug(format, data);
    }

    public static void logError(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.error(format, data);
    }

    public static void logWarn(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.warn(format, data);
    }

    public static void logError(Logger logger, Throwable t) {
        String threadName = Thread.currentThread().getName();
        String format = CharSequenceUtil.format("线程 : {}", threadName);
        logger.error(format, t);
    }

    private static String generateFormat(String desc, Object... data) {
        String threadName = Thread.currentThread().getName();
        String format = CharSequenceUtil.format(GXCommonConstant.SHORT_LOGGER_FORMAT, threadName, desc);
        int length = data.length;
        ArrayList<String> strings = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            strings.add("{}");
        }
        String appendFormat = Strings.join(strings, ',');
        return GXTraceIdContextUtils.TRACE_ID_KEY + " : " + GXTraceIdContextUtils.getTraceId() + " --> " + format + " --> 日志详细 : " + appendFormat;
    }
}
