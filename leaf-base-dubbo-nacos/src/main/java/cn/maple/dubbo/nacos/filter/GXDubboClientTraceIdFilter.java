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

/**
 * Dubbo客户端TraceId过滤器，负责在RPC调用链路中传递TraceId
 * <p>
 * 该过滤器通过SPI机制自动加载，在Dubbo消费者发起远程调用前被调用。
 * 主要职责是从多个来源获取TraceId，并确保将其传递给下游服务，
 * 便于分布式系统中的请求追踪和日志关联。
 * </p>
 * <p>
 * 调用流程：
 * 1. 先调用{@link GXPenetrateAttachmentSelector#select(Invocation invocation, RpcContextAttachment clientAttachment, RpcContextAttachment serverAttachment)}
 * 2. 然后调用{@link GXDubboClientTraceIdFilter#invoke(Invoker<?> invoker, Invocation invocation)}方法
 * </p>
 * <p>
 * 线程安全说明：
 * - 本过滤器基于Dubbo的Filter机制，每个请求由独立线程处理，不存在线程安全问题
 * - TraceId的存储基于ThreadLocal实现，各线程之间互不干扰
 * </p>
 * 
 * @author gapleaf@163.com
 */
@Activate(group = {CommonConstants.CONSUMER}, order = Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class GXDubboClientTraceIdFilter implements Filter {
    /**
     * 处理Dubbo客户端的请求，确保TraceId在整个调用链路中的传递
     * <p>
     * 该方法的主要职责：
     * 1. 尝试从多个来源获取TraceId：当前线程、客户端上下文、服务端上下文
     * 2. 将TraceId设置到当前线程、调用参数和RPC上下文中，确保下游服务能够接收到TraceId
     * 3. 记录日志，便于问题排查和链路追踪
     * </p>
     * <p>
     * 线程安全说明：
     * - 每个请求由独立线程处理，TraceId基于ThreadLocal存储，线程间互不干扰
     * </p>
     *
     * @param invoker    服务调用器
     * @param invocation 调用信息，包含方法名、参数等
     * @return 调用结果
     * @throws RpcException 当RPC调用发生异常时抛出
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        try {
            // 1. 获取TraceId，优先级：当前线程 > 客户端上下文 > 服务端上下文
            // 通过GXPenetrateAttachmentSelector设置的值可以通过以下方式获取
            // RpcContext.getCurrentServiceContext().getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)
            // 在GXBaseRequestLoggingFilter中会设置该值
            String traceId = GXTraceIdContextUtils.getTraceId();
            if (CharSequenceUtil.isEmpty(traceId)) {
                // 当该服务既是服务方又是消费方时，会在GXDubboServerTraceIdFilter设置该值
                traceId = RpcContext.getClientAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
                if (CharSequenceUtil.isEmpty(traceId)) {
                    traceId = RpcContext.getServerAttachment().getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
                }
                // 注意：与GXDubboServerTraceIdFilter不同，客户端过滤器不负责生成新的TraceId
                // 如果此时traceId仍为空，表示链路起点未设置TraceId，将传递空值
            }

            // 2. 记录应用名称和TraceId，便于日志追踪
            String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp");
            log.info("【{} --->> Dubbo Client】传递 TraceId: {}", appName, traceId);

            // 3. 设置TraceId到各个上下文中，确保在整个调用链路中传递
            // a) 设置回当前线程上下文
            GXTraceIdContextUtils.setTraceId(traceId);
            // b) 设置到Invocation的Attachment，这是Dubbo跨进程传递信息的标准方式
            invocation.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            // c) 设置到RpcContext的Server和Client Attachment
            RpcContext.getServerAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            RpcContext.getClientAttachment().setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);

            // 4. 执行实际的服务调用
            return invoker.invoke(invocation);
        } catch (Exception e) {
            // 记录异常信息，但仍然抛出，由Dubbo框架处理
            log.error("Dubbo客户端处理请求时发生异常", e);
            throw e;
        }
        // 注意：与GXDubboServerTraceIdFilter不同，客户端过滤器不清理ThreadLocal
        // 因为客户端过滤器在调用完成后，可能还需要处理响应，此时仍需要TraceId
        // ThreadLocal的清理应由请求入口处（如Web过滤器）负责
    }
}
