package cn.maple.dubbo.zk.filter;

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
 * Provider-side exception filter that preserves declared exceptions and hides undeclared runtime details.
 *
 * <p>The order of the branches below is part of the published contract, see
 * {@code specs/015-dubbo-zk-module-audit/contracts/dubbo-exception-contract.md}:
 * <ol>
 *     <li>{@link GXSentinelFlowException} passes through untouched (already classified).</li>
 *     <li>Sentinel block exceptions are converted into {@link GXSentinelFlowException}.</li>
 *     <li>Declared / checked / MyBatis / {@link GXBusinessException} exceptions pass through.</li>
 *     <li>Every other runtime exception is wrapped into {@link GXBusinessException} (fail-closed).</li>
 * </ol>
 *
 * <p>Requires {@code dubbo.provider.filter} to exclude the built-in {@code exception} filter,
 * otherwise the built-in one re-wraps {@link GXBusinessException} and drops {@code code/msg/data}.
 */
@Activate(group = CommonConstants.PROVIDER)
public class GXDubboExceptionFilter extends ExceptionFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GXDubboExceptionFilter.class);

    private static final String SENTINEL_BLOCK_PACKAGE = "com.alibaba.csp.sentinel.slots.block.";
    private static final String SENTINEL_FLOW_MESSAGE = "SentinelBlockException: FlowException";
    private static final Class<?> SENTINEL_BLOCK_EXCEPTION = resolveBlockExceptionClass();
    private static final AtomicLong FILTER_FAILURE_COUNT = new AtomicLong();

    @Override
    public void onResponse(Result appResponse, Invoker<?> invoker, Invocation invocation) {
        if (!appResponse.hasException()) {
            return;
        }
        try {
            String traceId = resolveTraceId(invocation);
            attachTraceId(appResponse, traceId);
            if (GenericService.class == invoker.getInterface()) {
                return;
            }
            Throwable exception = appResponse.getException();

            if (Objects.isNull(exception)) {
                return;
            }

            if (exception instanceof GXSentinelFlowException sentinelFlowException) {
                appResponse.setException(attachTraceId(sentinelFlowException, traceId));
                return;
            }

            if (isSentinelFlowException(exception)) {
                Dict data = buildErrorData(invoker, invocation, "sentinelFlow", traceId);
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
                appResponse.setException(attachTraceId((GXBusinessException) exception, traceId));
                return;
            }

            if (isDeclaredException(invoker, invocation, exception)) {
                return;
            }

            LOGGER.error("Got unchecked and undeclared exception called by {}. service: {}, method: {}, exception: {}: {}",
                    remoteHost(), serviceName(invoker), invocation.getMethodName(),
                    exception.getClass().getName(), exception.getMessage(), exception);

            String className = exception.getClass().getName();
            if (className.startsWith("java.") || className.startsWith("jakarta.")) {
                return;
            }

            if (exception instanceof RpcException) {
                return;
            }

            if (exception instanceof RuntimeException) {
                Dict data = buildErrorData(invoker, invocation, "providerRuntime", traceId);
                exception = new GXBusinessException("Provider error, please contact provider.", HttpStatus.HTTP_INTERNAL_ERROR, data, exception);
            }
            appResponse.setException(exception);
        } catch (Throwable e) {
            FILTER_FAILURE_COUNT.incrementAndGet();
            LOGGER.warn("Failed to run Dubbo exception filter called by {}. service: {}, method: {}, exception: {}: {}",
                    remoteHost(), serviceName(invoker), methodName(invocation), e.getClass().getName(), e.getMessage(), e);
        }
    }

    /**
     * Resolves the TraceId for the current response.
     *
     * <p>{@link org.apache.dubbo.rpc.Filter.Listener#onResponse} runs after the filter chain returned,
     * so the TraceId filters already restored (and possibly cleared) the MDC. The invocation attachment
     * written by the provider TraceId filter is therefore the primary source and MDC is only a fallback.
     */
    private @Nullable String resolveTraceId(Invocation invocation) {
        String traceId = getInvocationTraceId(invocation);
        if (CharSequenceUtil.isNotBlank(traceId)) {
            return traceId;
        }
        String mdcTraceId = GXTraceIdContextUtils.getTraceId();
        return CharSequenceUtil.isBlank(mdcTraceId) ? null : mdcTraceId;
    }

    private @Nullable String getInvocationTraceId(Invocation invocation) {
        if (Objects.isNull(invocation)) {
            return null;
        }
        Object traceId = invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        if (Objects.isNull(traceId)) {
            traceId = invocation.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY);
        }
        return Objects.toString(traceId, null);
    }

    /**
     * Returns whether the thrown exception is declared by the invoked method signature.
     *
     * <p>An unresolvable signature must NOT short-circuit the classification: it falls through to the
     * undeclared-runtime path so that unexpected errors stay wrapped (fail-closed).
     */
    private boolean isDeclaredException(Invoker<?> invoker, Invocation invocation, Throwable exception) {
        Class<?>[] parameterTypes = invocation.getParameterTypes();
        if (Objects.isNull(parameterTypes)) {
            parameterTypes = new Class<?>[0];
        }
        try {
            Method method = invoker.getInterface().getMethod(invocation.getMethodName(), parameterTypes);
            for (Class<?> exceptionClass : method.getExceptionTypes()) {
                if (exceptionClass.isAssignableFrom(exception.getClass())) {
                    return true;
                }
            }
        } catch (NoSuchMethodException e) {
            LOGGER.debug("Method signature not resolvable, fall through to undeclared-exception handling. service: {}, method: {}",
                    serviceName(invoker), methodName(invocation));
        }
        return false;
    }

    /**
     * Recognizes Sentinel block exceptions without a Sentinel dependency.
     *
     * <p>Matches the official package prefix, assignability to {@code BlockException} when present on
     * the class path, or the wrapper message produced by older Sentinel adapters. A bare
     * {@code "FlowException"} substring match is intentionally not used: it misclassifies business
     * errors that merely mention the word.
     */
    private boolean isSentinelFlowException(Throwable exception) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            String message = current.getMessage();
            if (current.getClass().getName().startsWith(SENTINEL_BLOCK_PACKAGE)
                    || isAssignableToBlockException(current)
                    || CharSequenceUtil.contains(message, SENTINEL_FLOW_MESSAGE)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isAssignableToBlockException(Throwable exception) {
        return Objects.nonNull(SENTINEL_BLOCK_EXCEPTION) && SENTINEL_BLOCK_EXCEPTION.isAssignableFrom(exception.getClass());
    }

    private static @Nullable Class<?> resolveBlockExceptionClass() {
        try {
            return Class.forName("com.alibaba.csp.sentinel.slots.block.BlockException", false, GXDubboExceptionFilter.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Cumulative number of exceptions thrown by the filter itself. Package-private on purpose: it is
     * an observability hook used by tests, not a public API.
     */
    static long filterFailureCount() {
        return FILTER_FAILURE_COUNT.get();
    }

    /**
     * Defensive accessor used on the failure path: it must never throw, otherwise a broken invoker
     * would escape {@link #onResponse} and hide the original exception.
     */
    private String serviceName(Invoker<?> invoker) {
        try {
            Class<?> serviceInterface = invoker.getInterface();
            return Objects.isNull(serviceInterface) ? "unknown" : serviceInterface.getName();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private String methodName(Invocation invocation) {
        try {
            return Objects.isNull(invocation) ? "unknown" : invocation.getMethodName();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private @Nullable String remoteHost() {
        return RpcContext.getServiceContext().getRemoteHost();
    }

    private Dict buildErrorData(Invoker<?> invoker, Invocation invocation, String errorType, @Nullable String traceId) {
        Class<?>[] parameterTypes = invocation.getParameterTypes();
        Object[] arguments = invocation.getArguments();
        return Dict.create()
                .set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId == null ? "" : traceId)
                .set("errorType", errorType)
                .set("interfaceName", serviceName(invoker))
                .set("methodName", methodName(invocation))
                .set("argumentCount", arguments == null ? 0 : arguments.length)
                .set("parameterTypes", Arrays.stream(parameterTypes == null ? new Class<?>[0] : parameterTypes)
                        .map(Class::getName)
                        .toList());
    }

    private void attachTraceId(Result result, @Nullable String traceId) {
        if (CharSequenceUtil.isBlank(traceId)) {
            return;
        }
        result.setAttachment(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
    }

    private GXSentinelFlowException attachTraceId(GXSentinelFlowException exception, @Nullable String traceId) {
        attachTraceIdToData(exception.getData(), traceId);
        return exception;
    }

    /**
     * Injects the resolved TraceId into the exception data map.
     *
     * <p>Only the existing map is mutated (both {@code GXBusinessException} and
     * {@code GXSentinelFlowException} keep {@code data} in a final field), so callers must not share
     * a single exception instance across threads.
     */
    private void attachTraceIdToData(@Nullable Dict data, @Nullable String traceId) {
        if (Objects.isNull(data) || CharSequenceUtil.isBlank(traceId)) {
            return;
        }
        data.set(GXTraceIdContextUtils.TRACE_ID_KEY, traceId);
    }

    private GXBusinessException attachTraceId(GXBusinessException exception, @Nullable String traceId) {
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
