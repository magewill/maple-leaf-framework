package cn.maple.core.framework.web.interceptor;

import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;

/**
 * 请求链路追踪ID拦截器
 * <p>
 * 该拦截器用于在请求处理过程中管理TraceId，实现分布式系统中的请求链路追踪功能。
 * TraceId是一个贯穿整个请求生命周期的唯一标识符，可用于关联同一请求在不同服务、
 * 不同线程中产生的日志，便于问题排查和性能分析。
 * </p>
 * <p>
 * 工作原理：
 * 1. 在请求处理前，从请求属性中获取TraceId，如果不存在则使用GXTraceIdContextUtils生成新的TraceId
 * 2. 将TraceId设置到响应头中，便于客户端或其他服务获取
 * 3. 在请求处理完成后，清理ThreadLocal中的TraceId，防止内存泄漏
 * </p>
 * 
 * @author 塵子曦
 * @see GXTraceIdContextUtils 提供TraceId的生成、存储和清理功能
 * @see GXAuthorizationInterceptor 基础授权拦截器
 */
@Component
public class GXTraceIdInterceptor extends GXAuthorizationInterceptor {
    /**
     * 请求预处理方法，在Controller处理请求前执行
     * <p>
     * 该方法负责从请求属性中获取TraceId，如果不存在则使用GXTraceIdContextUtils获取当前线程的TraceId。
     * 然后将TraceId设置到响应头中，便于客户端或下游服务获取并传递，保持请求链路的完整性。
     * </p>
     *
     * @param request  HTTP请求对象，不能为null
     * @param response HTTP响应对象，不能为null
     * @param handler  处理请求的方法对象，不能为null
     * @return 是否继续执行后续拦截器和Controller，始终返回true
     */
    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        Object requestId = Optional.ofNullable(request.getAttribute(GXTraceIdContextUtils.TRACE_ID_KEY)).orElse(GXTraceIdContextUtils.getTraceId());
        response.setHeader(GXTraceIdContextUtils.TRACE_ID_KEY, requestId.toString());
        return true;
    }

    /**
     * 请求完成后的处理方法，在视图渲染完成后执行
     * <p>
     * 该方法负责清理当前线程中的TraceId，防止在线程复用时造成TraceId混淆，
     * 同时避免ThreadLocal长期持有对象导致的内存泄漏问题。
     * </p>
     *
     * @param request  HTTP请求对象，不能为null
     * @param response HTTP响应对象，不能为null
     * @param handler  处理请求的方法对象，不能为null
     * @param ex       处理过程中发生的异常，可能为null
     * @throws Exception 清理过程中可能抛出的异常
     */
    @Override
    public void afterCompletion(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler, Exception ex) throws Exception {
        GXTraceIdContextUtils.removeTraceId();
    }
}
