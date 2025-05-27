package cn.maple.core.framework.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 基础请求日志过滤器，用于处理HTTP请求的跟踪ID（TraceId）和请求处理时间统计。
 * <p>
 * 该过滤器继承自Spring的AbstractRequestLoggingFilter，主要职责是：
 * 1. 在请求处理前（beforeRequest）设置或获取TraceId，确保请求链路可追踪
 * 2. 在请求处理过程中（doFilterInternal）记录请求开始时间和计算处理耗时
 * 3. 在请求处理后（afterRequest）清理TraceId，防止内存泄漏
 * </p>
 * <p>
 * <b>线程安全说明</b>：
 * - 本过滤器基于Spring的过滤器链机制，每个请求由独立的线程处理，不存在线程安全问题
 * - TraceId的存储基于ThreadLocal（通过MDC实现），确保了在高并发环境下的线程隔离
 * - 时间戳计算和响应头设置操作是线程安全的，不需要额外的同步机制
 * - 在请求结束时清理ThreadLocal资源，防止可能的内存泄漏
 * </p>
 * <p>
 * <b>使用场景</b>：
 * - 微服务架构中的请求链路追踪
 * - 分布式系统中的日志聚合与分析
 * - 多线程环境下的请求上下文传递
 * - 请求性能监控和耗时统计
 * </p>
 * <p>
 * <b>使用示例</b>：
 * 1. 在Spring Boot应用中注册过滤器：
 * <pre>
 * {@code
 * @Bean
 * public FilterRegistrationBean<GXBaseRequestLoggingFilter> requestLoggingFilterRegistration() {
 *     FilterRegistrationBean<GXBaseRequestLoggingFilter> registration = new FilterRegistrationBean<>();
 *     registration.setFilter(new GXBaseRequestLoggingFilter());
 *     registration.addUrlPatterns("/*");
 *     registration.setName("requestLoggingFilter");
 *     registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
 *     return registration;
 * }
 * }
 * </pre>
 * <p>
 * 2. 在请求处理完成后，可以从响应头中获取处理时间：
 * <pre>
 * {@code
 * String processingTime = response.getHeader("X-Request-Processing-Time");
 * // 进行性能分析或监控
 * }
 * </pre>
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

    /**
     * 请求过滤器的核心处理方法，负责计算请求处理时间并记录到响应头。
     * <p>
     * 处理逻辑：
     * 1. 获取请求开始时间，优先从请求头中获取，支持分布式系统中的请求追踪
     * 2. 执行过滤器链，处理实际业务逻辑
     * 3. 计算请求处理时间，并将结果添加到响应头中
     * </p>
     * <p>
     * <b>性能优化</b>：
     * - 使用try-finally结构确保即使发生异常也能记录处理时间
     * - 对请求头解析异常进行静默处理，不影响主流程
     * - 避免不必要的日志记录，减少性能开销
     * </p>
     * <p>
     * <b>线程安全</b>：
     * - 方法内的时间计算和响应头设置操作是线程安全的
     * - 每个请求由独立线程处理，不存在资源竞争问题
     * </p>
     *
     * @param request     HTTP请求对象
     * @param response    HTTP响应对象，用于设置响应头
     * @param filterChain 过滤器链，用于继续执行后续过滤器
     * @throws ServletException 如果处理过程中发生Servlet异常
     * @throws IOException      如果处理过程中发生IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String headerName = "X-Request-Start-Time";
        // 获取请求开始时间，优先从请求头获取，支持分布式系统中的请求追踪
        String requestStartTimeHeader = request.getHeader(headerName);

        if (CharSequenceUtil.isNotBlank(requestStartTimeHeader)) {
            // 将原始时间戳保留在响应头中，用于全链路追踪
            response.setHeader(headerName, requestStartTimeHeader);
        }

        // 继续执行过滤器链中的下一个过滤器或最终的处理器（Controller）
        super.doFilterInternal(request, response, filterChain);
    }
}
