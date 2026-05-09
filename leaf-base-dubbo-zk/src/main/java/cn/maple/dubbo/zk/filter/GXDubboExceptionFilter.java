package cn.maple.dubbo.zk.filter;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.apache.dubbo.rpc.service.GenericService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * Provider-side exception filter that preserves declared exceptions and hides undeclared runtime details.
 */
@Activate(group = CommonConstants.PROVIDER)
public class GXDubboExceptionFilter extends ExceptionFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXDubboExceptionFilter.class);

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        if (appResponse.hasException()) {
            try {
                attachTraceId(appResponse);
                if (GenericService.class == invoker.getInterface()) {
                    return;
                }
                Throwable exception = appResponse.getException();

                if (Objects.isNull(exception)) {
                    return;
                }

                if (isSentinelFlowException(exception)) {
                    Dict data = buildErrorData(invoker, invocation, "sentinelFlow");
                    exception = new GXSentinelFlowException("Service busy, please retry later.", HttpStatus.HTTP_NOT_ACCEPTABLE, data, exception);
                    appResponse.setException(exception);
                    return;
                }

                if (!(exception instanceof RuntimeException) && (exception instanceof Exception)) {
                    return;
                }

                if (CharSequenceUtil.equalsIgnoreCase(exception.getClass().getCanonicalName(), "org.mybatis.spring.MyBatisSystemException")) {
                    return;
                }

                if (exception instanceof GXBusinessException) {
                    appResponse.setException(attachTraceId((GXBusinessException) exception));
                    return;
                }

                try {
                    Method method = invoker.getInterface().getMethod(invocation.getMethodName(), invocation.getParameterTypes());
                    Class<?>[] exceptionClasses = method.getExceptionTypes();
                    for (Class<?> exceptionClass : exceptionClasses) {
                        if (exceptionClass.isAssignableFrom(exception.getClass())) {
                            attachTraceId(appResponse);
                            return;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    return;
                }

                LOGGER.error("Got unchecked and undeclared exception called by {}. service: {}, method: {}, exception: {}: {}",
                        RpcContext.getServiceContext().getRemoteHost(), invoker.getInterface().getName(),
                        invocation.getMethodName(), exception.getClass().getName(), exception.getMessage(), exception);

                String className = exception.getClass().getName();
                if (className.startsWith("java.") || className.startsWith("jakarta.")) {
                    return;
                }

                if (exception instanceof RpcException) {
                    return;
                }

                if (exception instanceof RuntimeException) {
                    Dict data = buildErrorData(invoker, invocation, "providerRuntime");
                    exception = new GXBusinessException("Provider error, please contact provider.", HttpStatus.HTTP_INTERNAL_ERROR, data, exception);
                }
                appResponse.setException(exception);
            } catch (Throwable e) {
                LOGGER.warn("Failed to run Dubbo exception filter called by {}. service: {}, method: {}, exception: {}: {}",
                        RpcContext.getServiceContext().getRemoteHost(), invoker.getInterface().getName(),
                        invocation.getMethodName(), e.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    private boolean isSentinelFlowException(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (CharSequenceUtil.equals(className, "com.alibaba.csp.sentinel.slots.block.flow.FlowException")
                    || CharSequenceUtil.equals(className, "com.alibaba.csp.sentinel.slots.block.BlockException")
                    || CharSequenceUtil.contains(message, "SentinelBlockException: FlowException")
                    || CharSequenceUtil.contains(message, "FlowException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Dict buildErrorData(Invoker<?> invoker, Invocation invocation, String errorType) {
        Class<?>[] parameterTypes = invocation.getParameterTypes();
        Object[] arguments = invocation.getArguments();
        return Dict.create()
                .set(GXTraceIdContextUtils.TRACE_ID_KEY, GXTraceIdContextUtils.getTraceId())
                .set("errorType", errorType)
                .set("interfaceName", invoker.getInterface().getName())
                .set("methodName", invocation.getMethodName())
                .set("argumentCount", arguments == null ? 0 : arguments.length)
                .set("parameterTypes", Arrays.stream(parameterTypes == null ? new Class<?>[0] : parameterTypes)
                        .map(Class::getName)
                        .toList());
    }

    private void attachTraceId(Result result) {
        String traceId = GXTraceIdContextUtils.getTraceId();
        if (CharSequenceUtil.isNotBlank(traceId)) {
            result.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
        }
    }

    private GXBusinessException attachTraceId(GXBusinessException exception) {
        String traceId = GXTraceIdContextUtils.getTraceId();
        if (CharSequenceUtil.isBlank(traceId)) {
            return exception;
        }
        Dict data = exception.getData();
        if (Objects.nonNull(data)) {
            data.set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
            return exception;
        }
        data = Dict.create().set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
        return new GXBusinessException(exception.getMsg(), exception.getCode(), data, exception);
    }
}
