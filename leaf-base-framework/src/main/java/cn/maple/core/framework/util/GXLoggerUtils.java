package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/**
 * 日志工具类
 * 封装了SLF4J的日志功能，统一业务代码的日志记录方式
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 获取日志对象
 * private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);
 * 
 * // 记录info级别日志
 * GXLoggerUtils.logInfo(LOG, "用户登录成功", userId, userName);
 * 
 * // 记录error级别日志
 * GXLoggerUtils.logError(LOG, "用户登录失败", userId, errorMessage);
 * 
 * // 记录异常信息
 * GXLoggerUtils.logError(LOG, exception);
 * }
 * </pre>
 * </p>
 *
 * @author britton
 * @since 2021-10-19 17:40
 */
public class GXLoggerUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXLoggerUtils.class);

    /**
     * 私有构造函数，防止实例化
     */
    private GXLoggerUtils() {
    }

    /**
     * 记录INFO级别日志
     * <p>
     * 日志格式：traceId : [traceId] --> 线程 : [threadName] --> [desc] --> 日志详细 : [data]
     * </p>
     *
     * @param logger 日志对象
     * @param desc   日志描述
     * @param data   日志数据
     */
    public static void logInfo(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.info(format, data);
    }

    /**
     * 记录DEBUG级别日志
     * <p>
     * 日志格式：traceId : [traceId] --> 线程 : [threadName] --> [desc] --> 日志详细 : [data]
     * </p>
     *
     * @param logger 日志对象
     * @param desc   日志描述
     * @param data   日志数据
     */
    public static void logDebug(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.debug(format, data);
    }

    /**
     * 记录ERROR级别日志
     * <p>
     * 日志格式：traceId : [traceId] --> 线程 : [threadName] --> [desc] --> 日志详细 : [data]
     * </p>
     *
     * @param logger 日志对象
     * @param desc   日志描述
     * @param data   日志数据
     */
    public static void logError(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.error(format, data);
    }

    /**
     * 记录WARN级别日志
     * <p>
     * 日志格式：traceId : [traceId] --> 线程 : [threadName] --> [desc] --> 日志详细 : [data]
     * </p>
     *
     * @param logger 日志对象
     * @param desc   日志描述
     * @param data   日志数据
     */
    public static void logWarn(Logger logger, String desc, Object... data) {
        if (data.length == 0) {
            return;
        }
        String format = generateFormat(desc, data);
        logger.warn(format, data);
    }

    /**
     * 记录ERROR级别日志（带异常信息）
     * <p>
     * 日志格式：线程 : [threadName]
     * </p>
     *
     * @param logger 日志对象
     * @param t      异常信息
     */
    public static void logError(Logger logger, Throwable t) {
        String threadName = Thread.currentThread().getName();
        String format = CharSequenceUtil.format("线程 : {}", threadName);
        logger.error(format, t);
    }

    /**
     * 生成日志的格式化字符串
     * <p>
     * 格式：traceId : [traceId] --> 线程 : [threadName] --> [desc] --> 日志详细 : [data]
     * </p>
     *
     * @param desc 日志描述信息
     * @param data 需要记录的数据
     * @return 日志格式化字符串
     */
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
