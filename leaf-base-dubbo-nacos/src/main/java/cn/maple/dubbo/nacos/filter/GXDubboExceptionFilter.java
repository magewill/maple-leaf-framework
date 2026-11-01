package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.apache.dubbo.rpc.service.GenericService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provider-side exception filter that keeps the business {@code code/msg/data} of
 * {@code GXBusinessException}, converts Sentinel blocks and hides undeclared runtime details.
 *
 * <p>Decision order: Sentinel block (class prefix, {@code BlockException} type or message prefix) is
 * converted to {@code GXSentinelFlowException(406)}; checked / declared exceptions,
 * {@code MyBatisSystemException}, {@code GXBusinessException}, {@code java.*} / {@code jakarta.*} and
 * {@code RpcException} pass through; every other undeclared runtime exception is wrapped as
 * {@code GXBusinessException(500, errorType=providerRuntime)}. Error data only carries the TraceId,
 * {@code errorType}, service/method names and the parameter types - never argument values.
 *
 * <p>An {@code Exception} raised by the filter itself is counted in {@link #filterFailureCount()} and
 * logged at WARN so the RPC still completes (FR-009). JVM {@code Error}s are deliberately not caught:
 * fatal conditions must not be masked as business responses.
 */
@Activate(group = CommonConstants.PROVIDER)
public class GXDubboExceptionFilter extends ExceptionFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXDubboExceptionFilter.class);
    private static final String SENTINEL_BLOCK_PACKAGE = "com.alibaba.csp.sentinel.slots.block.";
    private static final String SENTINEL_BLOCK_MESSAGE_PREFIX = "SentinelBlockException";
    private static final @Nullable Class<?> SENTINEL_BLOCK_EXCEPTION = resolveBlockExceptionClass();
    private static final AtomicLong FILTER_FAILURE_COUNT = new AtomicLong();

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

                Class<?>[] parameterTypes = invocation.getParameterTypes() == null ? new Class<?>[0] : invocation.getParameterTypes();
                try {
                    Method method = invoker.getInterface().getMethod(invocation.getMethodName(), parameterTypes);
                    Class<?>[] exceptionClasses = method.getExceptionTypes();
                    for (Class<?> exceptionClass : exceptionClasses) {
                        if (exceptionClass.isAssignableFrom(exception.getClass())) {
                            attachTraceId(appResponse);
                            return;
                        }
                    }
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("Method signature not resolvable, fall through to undeclared-exception handling. service: {}, method: {}",
                            invoker.getInterface().getName(), invocation.getMethodName());
                }

                LOGGER.error("Got unchecked and undeclared exception called by {}. service: {}, method: {}, exception: {}: {}",
                        RpcContext.getServiceContext().getRemoteHost(), serviceName(invoker),
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
            } catch (Exception e) {
                FILTER_FAILURE_COUNT.incrementAndGet();
                LOGGER.warn("Failed to run Dubbo exception filter. service: {}, method: {}, exception: {}: {}",
                        serviceName(invoker), invocation.getMethodName(), e.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    private boolean isSentinelFlowException(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            String message = current.getMessage();
            if (current.getClass().getName().startsWith(SENTINEL_BLOCK_PACKAGE)
                    || isAssignableToBlockException(current)
                    || startsWithSentinelBlockMessage(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isAssignableToBlockException(Throwable exception) {
        return Objects.nonNull(SENTINEL_BLOCK_EXCEPTION) && SENTINEL_BLOCK_EXCEPTION.isAssignableFrom(exception.getClass());
    }

    /**
     * Last-resort fallback for deployments that wrap Sentinel blocks in a plain exception: only a
     * message that starts with the Sentinel marker counts, so a business message merely mentioning
     * it is not treated as a rate limit.
     */
    private boolean startsWithSentinelBlockMessage(String message) {
        return Objects.nonNull(message) && message.strip().startsWith(SENTINEL_BLOCK_MESSAGE_PREFIX);
    }

    private static Class<?> resolveBlockExceptionClass() {
        try {
            return Class.forName("com.alibaba.csp.sentinel.slots.block.BlockException", false, GXDubboExceptionFilter.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    static long filterFailureCount() {
        return FILTER_FAILURE_COUNT.get();
    }

    /**
     * Diagnostic helper that must never throw: the invoker may be exactly the broken collaborator that
     * caused the failure, and FR-009 still requires the counter plus a log line.
     */
    private String serviceName(Invoker<?> invoker) {
        try {
            Class<?> serviceInterface = invoker.getInterface();
            return Objects.isNull(serviceInterface) ? "unknown" : serviceInterface.getName();
        } catch (RuntimeException e) {
            return "unknown";
        }
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
