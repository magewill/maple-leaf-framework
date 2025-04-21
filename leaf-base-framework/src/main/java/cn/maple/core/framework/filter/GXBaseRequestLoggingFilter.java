package cn.maple.core.framework.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

/**
 * 基础请求日志过滤器，用于处理HTTP请求的跟踪ID（TraceId）。
 * <p>
 * 该过滤器继承自Spring的AbstractRequestLoggingFilter，主要职责是：
 * 1. 在请求处理前（beforeRequest）设置或获取TraceId，确保请求链路可追踪
 * 2. 在请求处理后（afterRequest）清理TraceId，防止内存泄漏
 * </p>
 * <p>
 * <b>线程安全说明</b>：
 * - 本过滤器基于Spring的过滤器链机制，每个请求由独立的线程处理，不存在线程安全问题
 * - TraceId的存储基于ThreadLocal（通过MDC实现），确保了在高并发环境下的线程隔离
 * - 在请求结束时清理ThreadLocal资源，防止可能的内存泄漏
 * </p>
 * <p>
 * <b>使用场景</b>：
 * - 微服务架构中的请求链路追踪
 * - 分布式系统中的日志聚合与分析
 * - 多线程环境下的请求上下文传递
 * </p>
 * 
 * @author gapleaf@163.com
 */
public class GXBaseRequestLoggingFilter extends AbstractRequestLoggingFilter {
    /**
     * 在请求处理前执行，负责设置或获取TraceId。
     * <p>
     * 处理逻辑：
     * 1. 优先从请求头中获取TraceId，支持跨服务调用时的链路追踪
     * 2. 如果请求头中不存在，则尝试从当前线程上下文获取
     * 3. 如果上下文中也不存在，则生成新的TraceId
     * 4. 将TraceId设置到MDC和请求属性中，供后续处理使用
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 方法内部使用ThreadLocal存储TraceId，确保线程隔离
     * - 使用Optional避免空指针异常，提高代码健壮性
     * </p>
     *
     * @param request HTTP请求对象，用于获取请求头和设置属性
     * @param message 日志消息，由父类AbstractRequestLoggingFilter提供
     */
    @Override
    protected void beforeRequest(HttpServletRequest request, @NotNull String message) {
        // 优先从请求头获取TraceId，支持分布式追踪
        String requestId = Optional.ofNullable(request.getHeader(GXTraceIdContextUtils.TRACE_ID_KEY))
                .orElse(GXTraceIdContextUtils.getTraceId());
        
        // 如果没有获取到有效的TraceId，则生成新的
        if (CharSequenceUtil.isEmpty(requestId)) {
            requestId = GXTraceIdContextUtils.generateTraceId();
        }
        
        // 设置TraceId到MDC，用于日志输出
        GXTraceIdContextUtils.setTraceId(requestId);
        // 设置TraceId到请求属性，方便在请求处理过程中获取
        request.setAttribute(GXTraceIdContextUtils.TRACE_ID_KEY, requestId);
    }

    /**
     * 在请求处理后执行，负责清理TraceId。
     * <p>
     * 处理逻辑：
     * 1. 从MDC中移除TraceId，释放ThreadLocal资源
     * 2. 这一步对于防止内存泄漏至关重要，特别是在使用线程池的环境中
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 方法内部只清理当前线程的ThreadLocal资源，不影响其他线程
     * - 即使在高并发环境下，也能确保资源正确释放
     * </p>
     *
     * @param request HTTP请求对象
     * @param message 日志消息，由父类AbstractRequestLoggingFilter提供
     */
    @Override
    protected void afterRequest(@NotNull HttpServletRequest request, @NotNull String message) {
        // 清理当前线程的TraceId，防止内存泄漏
        GXTraceIdContextUtils.removeTraceId();
    }
}
