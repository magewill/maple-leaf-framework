package cn.maple.dubbo.nacos.selector;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.nacos.filter.GXDubboClientTraceIdFilter;
import cn.maple.dubbo.nacos.filter.GXDubboServerTraceIdFilter;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.PenetrateAttachmentSelector;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcContextAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Dubbo附件选择器，负责在RPC调用链中传递TraceId等关键信息
 * <p>
 * 本选择器通过Dubbo的SPI机制自动加载，同时在服务提供方和消费方都会激活。
 * 主要职责是在RPC调用过程中选择需要传递给下一跳的附件信息，确保TraceId等上下文信息在分布式系统中正确传递。
 * </p>
 * <p>
 * 调用流程说明：
 * 1. 客户端调用时：先调用{@link #select}方法，再调用{@link GXDubboClientTraceIdFilter#invoke}方法
 * 2. 服务端响应时：先调用{@link GXDubboServerTraceIdFilter#invoke}方法，再调用{@link #selectReverse}方法
 * </p>
 * <p>
 * 线程安全说明：
 * - 本选择器基于Dubbo的SPI机制，每个请求由独立线程处理，不存在线程安全问题
 * - TraceId的存储基于ThreadLocal实现，各线程之间互不干扰
 * - 在获取和设置TraceId时，遵循严格的优先级策略，确保链路追踪的连续性
 * </p>
 * 
 * @author gapleaf@163.com
 */
@Activate(group = {CommonConstants.PROVIDER, CommonConstants.CONSUMER})
public class GXPenetrateAttachmentSelector implements PenetrateAttachmentSelector {
    /**
     * 日志对象，用于记录TraceId传递过程中的关键信息
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXPenetrateAttachmentSelector.class);

    /**
     * 选择需要传递给下一跳的附件信息
     * <p>
     * 该方法在服务作为客户端（消费者）发起调用时被执行，负责选择需要传递给下游服务的附件信息。
     * 主要职责是确保TraceId在分布式调用链中的连续传递，遵循以下优先级获取TraceId：
     * 1. 从当前线程上下文获取（通过{@link GXTraceIdContextUtils#getTraceId()}）
     * 2. 从服务端上下文获取（通过{@link RpcContext#getServerAttachment()}）
     * 3. 如果以上来源都没有TraceId，则生成新的TraceId
     * </p>
     * <p>
     * 调用流程：
     * 该方法被调用后，会调用{@link GXDubboClientTraceIdFilter#invoke}方法，
     * 该过滤器会将本方法返回的附件信息写入Invocation对象，相当于调用Invocation.addObjectAttachments方法
     * </p>
     * <p>
     * 线程安全说明：
     * - 每个请求由独立线程处理，TraceId基于ThreadLocal存储，线程间互不干扰
     * - 在获取TraceId时，遵循严格的优先级策略，确保链路追踪的连续性
     * </p>
     *
     * @param invocation 调用信息，包含方法名、参数等
     * @param clientAttachment 客户端上下文附件
     * @param serverAttachment 服务端上下文附件
     * @return 需要传递给下一跳的附件信息，包含TraceId等关键数据
     */
    @Override
    public Map<String, Object> select(Invocation invocation, RpcContextAttachment clientAttachment, RpcContextAttachment serverAttachment) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp");
        LOG.info("【{} --->> Dubbo Client Selector】进入select方法", appName);
        
        String traceId = resolveTraceId(invocation, clientAttachment, serverAttachment);
        
        LOG.info("【{} --->> Dubbo Client Selector】选择传递TraceId: {}", appName, traceId);
        return Dict.create()
                .set("author", "塵子曦")
                .set("SPC", "客户端")
                .set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
    }

    /**
     * 选择需要从服务端传回客户端的附件信息
     * <p>
     * 该方法在服务作为服务端（提供者）处理完请求后被执行，负责选择需要传递回客户端的附件信息。
     * 主要职责是确保TraceId在响应过程中的传递，特别是在服务作为中间服务时，需要将TraceId传递回上游服务。
     * 遵循以下优先级获取TraceId：
     * 1. 从当前线程上下文获取（通过{@link GXTraceIdContextUtils#getTraceId()}）
     * 2. 从服务端上下文获取（通过{@link RpcContext#getServerAttachment()}）
     * 3. 如果以上来源都没有TraceId，则生成新的TraceId
     * </p>
     * <p>
     * 调用流程：
     * 该方法在{@link GXDubboServerTraceIdFilter#invoke}方法执行后被调用，
     * 用于将服务处理过程中的TraceId传递回客户端，确保分布式链路追踪的完整性
     * </p>
     * <p>
     * 线程安全说明：
     * - 每个请求由独立线程处理，TraceId基于ThreadLocal存储，线程间互不干扰
     * - 在获取TraceId时，遵循严格的优先级策略，确保链路追踪的连续性
     * - 即使在服务作为中间节点的复杂调用链中，也能确保TraceId的正确传递
     * </p>
     *
     * @param invocation 调用信息，包含方法名、参数等
     * @param clientResponseContext 客户端的响应上下文
     * @param serverResponseContext 服务端的响应上下文
     * @return 需要传递回客户端的附件信息，包含TraceId等关键数据
     */
    @Override
    public Map<String, Object> selectReverse(Invocation invocation, RpcContextAttachment clientResponseContext, RpcContextAttachment serverResponseContext) {
        String appName = GXCommonUtils.getEnvironmentValue("spring.application.name", String.class, "UnknownApp");
        LOG.info("【{} --->> Dubbo Server Selector】进入selectReverse方法", appName);
        
        String traceId = resolveTraceId(invocation, clientResponseContext, serverResponseContext);
        
        // 如果服务作为中间服务，确保将TraceId传递回上游服务
        LOG.info("【{} --->> Dubbo Server Selector】选择传递TraceId: {}", appName, traceId);
        return Dict.create()
                .set("author", "塵子曦")
                .set("SPC", "服务端")
                .set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
    }

    private String resolveTraceId(Invocation invocation, RpcContextAttachment clientAttachment, RpcContextAttachment serverAttachment) {
        String traceId = GXTraceIdContextUtils.getTraceId();
        if (CharSequenceUtil.isNotEmpty(traceId)) {
            return traceId;
        }
        traceId = getInvocationAttachment(invocation);
        if (CharSequenceUtil.isNotEmpty(traceId)) {
            return traceId;
        }
        traceId = getAttachment(serverAttachment);
        if (CharSequenceUtil.isNotEmpty(traceId)) {
            return traceId;
        }
        traceId = getAttachment(clientAttachment);
        if (CharSequenceUtil.isNotEmpty(traceId)) {
            return traceId;
        }
        traceId = GXTraceIdContextUtils.generateTraceId();
        LOG.debug("在Dubbo附件选择器中生成新的TraceId: {}", traceId);
        return traceId;
    }

    private String getAttachment(RpcContextAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        Object traceId = attachment.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            traceId = attachment.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        }
        return Objects.toString(traceId, null);
    }

    private String getInvocationAttachment(Invocation invocation) {
        Object traceId = invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            traceId = invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        }
        return Objects.toString(traceId, null);
    }
}
