package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.nacos.selector.GXPenetrateAttachmentSelector;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.springframework.core.Ordered;

import java.util.Objects;

/**
 * Dubbo服务提供方的TraceId过滤器，负责在RPC调用链路中传递和管理TraceId
 * <p>
 * 该过滤器通过SPI机制自动加载，在Dubbo服务提供方处理请求时被调用。
 * 主要职责是从RPC上下文中提取TraceId，并确保在整个服务调用过程中TraceId的一致性，
 * 便于分布式系统中的请求追踪和日志关联。
 * </p>
 * <p>
 * 调用流程：
 * 1. 先调用{@link GXDubboServerTraceIdFilter#invoke(Invoker<?> invoker, Invocation invocation)}方法
 * 2. 然后才会调用{@link GXPenetrateAttachmentSelector#electReverse(Invocation invocation, RpcContextAttachment clientResponseContext, RpcContextAttachment serverResponseContext)}方法
 * </p>
 * <p>
 * 线程安全说明：
 * - 本过滤器基于Dubbo的Filter机制，每个请求由独立线程处理，不存在线程安全问题
 * - TraceId的存储基于ThreadLocal实现，各线程之间互不干扰
 * - 在finally块中确保清理ThreadLocal，防止内存泄漏
 * </p>
 * 
 * @author gapleaf@163.com
 */
@Activate(group = {CommonConstants.PROVIDER}, order = Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GXDubboServerTraceIdFilter implements Filter {
    /**
     * 处理Dubbo服务提供方的请求，确保TraceId在整个调用链路中的传递
     * <p>
     * 该方法的主要职责：
     * 1. 尝试从多个来源获取TraceId：当前线程、服务端上下文、客户端上下文
     * 2. 如果所有来源都没有TraceId，则自动生成一个新的TraceId
     * 3. 将TraceId设置到当前线程、调用参数和RPC上下文中
     * 4. 确保在方法执行完毕后清理ThreadLocal，防止内存泄漏
     * </p>
     * <p>
     * 线程安全说明：
     * - 每个请求由独立线程处理，TraceId基于ThreadLocal存储，线程间互不干扰
     * - 在finally块中确保清理ThreadLocal，即使发生异常也能正确释放资源
     * </p>
     *
     * @param invoker    服务调用器
     * @param invocation 调用信息，包含方法名、参数等
     * @return 调用结果
     * @throws RpcException 当RPC调用发生异常时抛出
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String originalTraceId = GXTraceIdContextUtils.getNullableTraceId();
        try {
            // 1. 获取TraceId，优先级：当前线程 > 服务端上下文 > 客户端上下文 > 新生成
            String traceId = resolveTraceId(invocation);
            
            // 2. 记录应用名称和TraceId，便于日志追踪
            String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class);
            log.info("【{} --->> Dubbo Service】获取TraceId : {}", appName, traceId);
            
            // 3. 设置TraceId到各个上下文中，确保在整个调用链路中传递
            GXTraceIdContextUtils.putTraceId(traceId);
            invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            RpcContext.getClientAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            RpcContext.getServerAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            
            // 4. 执行实际的服务调用
            return invoker.invoke(invocation);
        } catch (Exception e) {
            // 记录异常信息，但仍然抛出，由Dubbo框架处理
            log.error("Dubbo服务端处理请求时发生异常", e);
            throw e;
        } finally {
            // 5. 清理当前线程的TraceId，防止内存泄漏
            // 注意：这里只清理ThreadLocal中的TraceId，不影响RPC上下文中的TraceId传递
            GXTraceIdContextUtils.restoreTraceId(originalTraceId);
        }
    }

    private String resolveTraceId(Invocation invocation) {
        String traceId = getInvocationAttachment(invocation);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = RpcContext.getServerAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = RpcContext.getClientAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = GXTraceIdContextUtils.getTraceId();
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        traceId = GXTraceIdContextUtils.generateTraceId();
        log.debug("在Dubbo服务端生成新的TraceId: {}", traceId);
        return traceId;
    }

    private String getInvocationAttachment(Invocation invocation) {
        Object traceId = invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            traceId = invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        }
        return Objects.toString(traceId, null);
    }
}
