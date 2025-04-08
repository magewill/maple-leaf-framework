package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;

/**
 * TraceId上下文工具类
 * <p>
 * 该工具类用于在分布式系统中管理TraceId，实现请求链路追踪。
 * TraceId会被记录在日志中，方便在排查问题时追踪完整的调用链路。
 * 注意：由于MDC基于ThreadLocal实现，在多线程环境下需要特别处理，
 * 请结合GXMdcThreadUtils使用以确保子线程能够正确继承父线程的TraceId。
 * </p>
 *
 * @author gapleaf@163.com
 */
public class GXTraceIdContextUtils {
    /**
     * TraceId的字段名字
     * 使用X-B3-TraceId是为了兼容Spring Cloud Sleuth等分布式追踪框架
     */
    public static final String TRACE_ID_KEY = "X-B3-TraceId";

    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXTraceIdContextUtils.class);

    /**
     * 私有化构造函数，防止实例化
     */
    private GXTraceIdContextUtils() {
    }

    /**
     * 获取当前线程的TraceId
     * <p>
     * 从当前线程的MDC中获取TraceId，如果不存在则返回空字符串。
     * 注意：在多线程环境下，子线程需要通过GXMdcThreadUtils工具类
     * 包装任务才能继承父线程的TraceId。
     * </p>
     *
     * @return String 当前线程的TraceId，不存在时返回空字符串
     */
    public static String getTraceId() {
        String threadName = Thread.currentThread().getName();
        String traceId = MDC.get(TRACE_ID_KEY);
        LOG.debug("线程 {} 获取的TraceId : {}", threadName, traceId);
        return Objects.isNull(traceId) ? "" : traceId;
    }

    /**
     * 设置TraceId到当前线程的MDC中
     * <p>
     * 只有当传入的traceId不为空且当前MDC中不存在TraceId时才会设置。
     * 这种设计可以防止已有的TraceId被覆盖，保证链路追踪的连续性。
     * </p>
     *
     * @param traceId 需要设置的TraceId，不能为空
     */
    public static void setTraceId(String traceId) {
        if (CharSequenceUtil.isNotEmpty(traceId) && CharSequenceUtil.isEmpty(MDC.get(TRACE_ID_KEY))) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 移除当前线程MDC中的TraceId
     * <p>
     * 在请求处理完成后调用此方法清理资源，防止内存泄漏。
     * 通常在Filter或Interceptor的afterCompletion方法中调用。
     * </p>
     */
    public static void removeTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            return;
        }
        String threadName = Thread.currentThread().getName();
        LOG.debug("线程 {} 销毁的TraceId : {}", threadName, traceId);
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 清空当前线程的MDC
     * <p>
     * 与removeTraceId不同，此方法会清空MDC中的所有内容，而不仅仅是TraceId。
     * 谨慎使用，因为可能会清除MDC中的其他重要信息。
     * </p>
     */
    public static void clearTraceId() {
        MDC.clear();
    }

    /**
     * 生成新的TraceId
     * <p>
     * 生成的TraceId格式为：应用名称:UUID。
     * 包含应用名称可以在微服务架构中快速定位请求来源。
     * 此方法仅生成TraceId，不会自动设置到MDC中，如需设置请调用setTraceId方法。
     * </p>
     *
     * @return String 新生成的TraceId
     */
    public static String generateTraceId() {
        String threadName = Thread.currentThread().getName();
        String traceId = GXTraceIdGenerator.generateTraceId();
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        traceId = CharSequenceUtil.format("{}:{}", appName, traceId);
        LOG.debug("线程 {} 生成的TraceId : {}", threadName, traceId);
        return traceId;
    }

    /**
     * TraceId生成器
     * <p>
     * 内部静态类，负责生成唯一的TraceId标识符。
     * 使用内部类可以实现延迟加载，提高性能。
     * </p>
     */
    private static class GXTraceIdGenerator {
        /**
         * 私有构造函数，防止实例化
         */
        private GXTraceIdGenerator() {
        }

        /**
         * 生成traceId
         * <p>
         * 使用hutool工具类的fastSimpleUUID方法生成不含连字符的UUID。
         * 相比标准UUID，fastSimpleUUID性能更好，生成的字符串更短。
         * </p>
         *
         * @return TraceId 基于UUID的唯一标识符
         */
        public static String generateTraceId() {
            return IdUtil.fastSimpleUUID();
        }
    }
}
