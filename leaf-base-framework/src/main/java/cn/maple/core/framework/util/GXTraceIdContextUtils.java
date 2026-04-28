package cn.maple.core.framework.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;

/**
 * TraceId 上下文工具类，用于在分布式系统中管理 TraceId，实现请求链路追踪。
 * <p>
 * <b>实现原理</b>：
 * TraceId 是分布式系统中用于追踪请求链路的唯一标识符，通常记录在日志中，便于问题排查。
 * 本工具类通过 SLF4J 的 MDC（Mapped Diagnostic Context）存储 TraceId。MDC 基于 ThreadLocal 实现，
 * 每个线程都有独立的 MDC 上下文副本，因此在多线程场景（如线程池、异步任务）中，
 * 子线程无法直接继承父线程的 TraceId，导致日志中的链路追踪信息不连续。
 * </p>
 * <p>
 * 本工具类通过以下方式解决该问题：
 * 1. 在主线程中生成并设置 TraceId（通过 setTraceId 方法）。
 * 2. 在多线程环境下，结合 GXMdcThreadUtils（需单独实现）将父线程的 MDC 上下文传递给子线程。
 * 3. 在请求处理完成后，清理 MDC（通过 removeTraceId 或 clearTraceId 方法），防止上下文泄漏。
 * </p>
 * <p>
 * <b>线程安全机制</b>：
 * - MDC 基于 ThreadLocal 实现，天然线程安全，每个线程的 MDC 上下文互不干扰。
 * - 在设置和移除 TraceId 时，仅操作当前线程的 MDC，不会影响其他线程。
 * - 提供 removeTraceId 和 clearTraceId 方法，确保在请求处理完成后清理 MDC，防止内存泄漏。
 * - 在多线程环境下，需结合 GXMdcThreadUtils 包装任务，确保子线程继承父线程的 TraceId。
 * </p>
 * <p>
 * <b>使用场景</b>：
 * 1. 在 Web 应用中，通过 Filter 或 Interceptor 在请求开始时设置 TraceId，请求结束时清理。
 * 2. 在微服务架构中，通过日志追踪跨服务的请求链路。
 * 3. 在多线程环境下（如线程池、CompletableFuture），结合 GXMdcThreadUtils 传递 TraceId。
 * </p>
 * <p>
 * <b>使用示例</b>：
 * <pre>
 * // 在 Filter 中设置 TraceId
 * public class TraceIdFilter implements Filter {
 *     @Override
 *     public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
 *         try {
 *             String traceId = GXTraceIdContextUtils.generateTraceId();
 *             GXTraceIdContextUtils.setTraceId(traceId);
 *             chain.doFilter(request, response);
 *         } finally {
 *             GXTraceIdContextUtils.removeTraceId();
 *         }
 *     }
 * }
 *
 * // 在多线程环境下传递 TraceId
 * ExecutorService executor = Executors.newFixedThreadPool(2);
 * Map<String, String> context = GXMdcThreadUtils.getMdcContext();
 * executor.execute(GXMdcThreadUtils.wrap(() -> {
 *     System.out.println("Sub-thread TraceId: " + GXTraceIdContextUtils.getTraceId());
 * }, context));
 * </pre>
 * </p>
 * <p>
 * <b>注意事项</b>：
 * 1. 在多线程环境下，必须结合 GXMdcThreadUtils 包装任务，否则子线程无法继承 TraceId。
 * 2. 确保在请求处理完成后调用 removeTraceId 或 clearTraceId，避免 MDC 上下文泄漏。
 * 3. 如果 MDC 中已存在 TraceId，setTraceId 方法不会覆盖现有值，以保证链路追踪的连续性。
 * </p>
 *
 * @author gapleaf@163.com
 */
public class GXTraceIdContextUtils {
    /**
     * TraceId 的字段名称，使用 X-B3-TraceId 兼容 Spring Cloud Sleuth 等分布式追踪框架。
     */
    public static final String TRACE_ID_KEY = "X-B3-TraceId";

    /**
     * 日志对象，用于记录 TraceId 的设置、获取和清理操作。
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXTraceIdContextUtils.class);

    /**
     * 私有构造函数，防止实例化。
     * <p>
     * 本类为工具类，所有方法均为静态方法，不需要实例化。
     * </p>
     */
    private GXTraceIdContextUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    /**
     * 获取当前线程的 TraceId。
     * <p>
     * 从当前线程的 MDC 中获取 TraceId，如果不存在则返回空字符串。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，获取操作仅影响当前线程，天然线程安全。
     * - 在多线程环境下，子线程需通过 GXMdcThreadUtils 包装任务以继承父线程的 TraceId。
     * </p>
     *
     * @return 当前线程的 TraceId，不存在时返回空字符串
     */
    public static String getTraceId() {
        String threadName = Thread.currentThread().getName();
        String traceId = MDC.get(TRACE_ID_KEY);
        LOG.debug("线程 {} 获取的 TraceId: {}", threadName, traceId);
        return Objects.isNull(traceId) ? "" : traceId;
    }

    public static String getNullableTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 设置 TraceId 到当前线程的 MDC 中。
     * <p>
     * 只有当传入的 traceId 不为空且当前 MDC 中不存在 TraceId 时才会设置。
     * 这种设计防止已有的 TraceId 被覆盖，保证链路追踪的连续性。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，设置操作仅影响当前线程，天然线程安全。
     * - 在多线程环境下，子线程需通过 GXMdcThreadUtils 包装任务以继承父线程的 TraceId。
     * </p>
     *
     * @param traceId 需要设置的 TraceId，不能为空
     */
    public static void setTraceId(String traceId) {
        if (CharSequenceUtil.isNotEmpty(traceId) && CharSequenceUtil.isEmpty(MDC.get(TRACE_ID_KEY))) {
            MDC.put(TRACE_ID_KEY, traceId);
            LOG.debug("线程 {} 设置的 TraceId: {}", Thread.currentThread().getName(), traceId);
        } else {
            LOG.debug("线程 {} 未设置 TraceId: 当前已存在 TraceId 或传入的 TraceId 为空", Thread.currentThread().getName());
        }
    }

    public static void putTraceId(String traceId) {
        if (CharSequenceUtil.isBlank(traceId)) {
            removeTraceId();
            return;
        }
        MDC.put(TRACE_ID_KEY, traceId);
        LOG.debug("线程 {} 写入 TraceId: {}", Thread.currentThread().getName(), traceId);
    }

    public static void restoreTraceId(String traceId) {
        if (CharSequenceUtil.isBlank(traceId)) {
            removeTraceId();
        } else {
            putTraceId(traceId);
        }
    }

    /**
     * 移除当前线程 MDC 中的 TraceId。
     * <p>
     * 在请求处理完成后调用此方法清理资源，防止内存泄漏。
     * 通常在 Filter 或 Interceptor 的 afterCompletion 方法中调用。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，移除操作仅影响当前线程，天然线程安全。
     * - 如果当前线程的 MDC 中不存在 TraceId，则直接返回，无需额外操作。
     * </p>
     */
    public static void removeTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            LOG.debug("线程 {} 的 MDC 中不存在 TraceId，无需移除", Thread.currentThread().getName());
            return;
        }
        String threadName = Thread.currentThread().getName();
        LOG.debug("线程 {} 移除的 TraceId: {}", threadName, traceId);
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 清空当前线程的 MDC。
     * <p>
     * 与 removeTraceId 不同，此方法会清空 MDC 中的所有内容，而不仅仅是 TraceId。
     * 谨慎使用，因为可能会清除 MDC 中的其他重要信息（如用户 ID、请求 ID 等）。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，清空操作仅影响当前线程，天然线程安全。
     * </p>
     */
    public static void clearTraceId() {
        MDC.clear();
        LOG.debug("线程 {} 清空了 MDC", Thread.currentThread().getName());
    }

    /**
     * 生成新的 TraceId。
     * <p>
     * 生成的 TraceId 格式为：应用名称:UUID。
     * - 应用名称通过环境变量 spring.application.name 获取，便于在微服务架构中定位请求来源。
     * - UUID 使用 Hutool 的 fastSimpleUUID 方法生成，性能高且长度较短。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - fastSimpleUUID 方法是线程安全的，基于 UUID.randomUUID() 实现。
     * - 环境变量获取操作（GXCommonUtils.getEnvironmentValue）需确保线程安全（假设已实现）。
     * </p>
     *
     * @return 新生成的 TraceId
     */
    public static String generateTraceId() {
        String threadName = Thread.currentThread().getName();
        String traceId = GXTraceIdGenerator.generateTraceId();
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
        traceId = CharSequenceUtil.format("{}:{}", appName, traceId);
        LOG.debug("线程 {} 生成的 TraceId: {}", threadName, traceId);
        return traceId;
    }

    /**
     * 如果当前线程的 MDC 中不存在 TraceId，则生成并设置一个新的 TraceId。
     * <p>
     * 此方法不会覆盖现有的 TraceId，保证链路追踪的连续性。
     * 如果 MDC 中没有 TraceId，则调用 {@link #generateTraceId()} 生成新的 TraceId，
     * 并通过 {@link #setTraceId(String)} 设置到当前线程的 MDC 中。
     * </p>
     * <p>
     * <b>使用场景</b>：
     * - 在请求处理开始时，确保线程具有 TraceId，例如在 Filter 或 Interceptor 中调用。
     * - 在异步任务启动时，确保子线程具有 TraceId（需结合 GXMdcThreadUtils 传递上下文）。
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - MDC 基于 ThreadLocal，操作仅影响当前线程，天然线程安全。
     * - {@link #generateTraceId()} 方法是线程安全的，基于 UUID.randomUUID() 实现。
     * - 判断和设置操作是原子性的，因为 MDC 的 get 和 put 操作针对当前线程的独立上下文。
     * </p>
     * <p>
     * <b>注意事项</b>：
     * - 如果当前线程已有 TraceId，则不会生成或设置新的 TraceId。
     * - 在多线程环境下，子线程需通过 GXMdcThreadUtils 包装任务以继承父线程的 TraceId。
     * - 调用方无需显式传递 TraceId，方法内部会自动生成。
     * </p>
     */
    public static void setTraceIdIfAbsent() {
        String currentTraceId = getTraceId();
        if (CharSequenceUtil.isEmpty(currentTraceId)) {
            String newTraceId = generateTraceId();
            setTraceId(newTraceId);
            LOG.debug("线程 {} 自动设置了新的 TraceId: {}", Thread.currentThread().getName(), newTraceId);
        } else {
            LOG.debug("线程 {} 已存在 TraceId: {}，无需设置", Thread.currentThread().getName(), currentTraceId);
        }
    }

    /**
     * TraceId 生成器，内部静态类，负责生成唯一的 TraceId 标识符。
     * <p>
     * 使用内部类实现延迟加载，仅在首次调用 generateTraceId 方法时加载，提高性能。
     * </p>
     */
    private static class GXTraceIdGenerator {
        /**
         * 私有构造函数，防止实例化。
         */
        private GXTraceIdGenerator() {
            throw new AssertionError("Utility class, cannot be instantiated");
        }

        /**
         * 生成 TraceId。
         * <p>
         * 使用 Hutool 的 fastSimpleUUID 方法生成不含连字符的 UUID。
         * 相比标准 UUID，fastSimpleUUID 性能更好，生成的字符串更短。
         * </p>
         * <p>
         * <b>线程安全</b>：
         * - fastSimpleUUID 方法基于 UUID.randomUUID() 实现，天然线程安全。
         * </p>
         *
         * @return 基于 UUID 的唯一 TraceId
         */
        public static String generateTraceId() {
            return IdUtil.fastSimpleUUID();
        }
    }
}
