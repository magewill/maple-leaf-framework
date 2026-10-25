package cn.maple.dubbo.zk.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.handler.GXExceptionHandler;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Dubbo-specific MVC advice.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} ensures {@link RpcException} / {@link GXSentinelFlowException}
 * are matched by the dedicated handlers below instead of the catch-all
 * {@code @ExceptionHandler(Exception.class)} inherited from {@link GXExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GXDubboCallExceptionHandler extends GXExceptionHandler {
    private static final String PROVIDER_ERROR_MESSAGE = "Dubbo provider error, please contact operations.";

    @ExceptionHandler(RpcException.class)
    public GXResultUtils<Dict> handleRpcException(RpcException e) {
        log.error("Dubbo RPC exception", e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, PROVIDER_ERROR_MESSAGE, buildRpcErrorData(e));
    }

    @ExceptionHandler(GXSentinelFlowException.class)
    public GXResultUtils<Dict> handleSentinelFlowException(GXSentinelFlowException e) {
        log.error("Dubbo Sentinel flow exception", e);
        return GXResultUtils.error(e.getCode(), e.getMsg(), enrichTraceId(e.getData()));
    }

    private Dict buildRpcErrorData(RpcException e) {
        String errorType = resolveRpcErrorType(e);
        return Dict.create()
                .set(GXTraceIdContextUtils.TRACE_ID_KEY, GXTraceIdContextUtils.getTraceId())
                .set("errorType", errorType)
                .set("rpcCode", e.getCode())
                .set("exceptionClass", e.getClass().getName())
                .set("rpcMessage", resolveRpcMessage(errorType));
    }

    private String resolveRpcErrorType(RpcException e) {
        if (e.isTimeout()) {
            return "timeout";
        }
        if (e.isNetwork()) {
            return "network";
        }
        if (e.isSerialization()) {
            return "serialization";
        }
        if (e.isForbidden()) {
            return "forbidden";
        }
        if (e.isAuthorization()) {
            return "authorization";
        }
        if (e.isLimitExceed()) {
            return "limitExceed";
        }
        if (e.isNoInvokerAvailableAfterFilter()) {
            return "noInvoker";
        }
        if (e.isValidation()) {
            return "validation";
        }
        if (e.isBiz()) {
            return "business";
        }
        return "unknown";
    }

    private String resolveRpcMessage(String errorType) {
        return switch (errorType) {
            case "timeout" -> "RPC timeout";
            case "network" -> "RPC network error";
            case "serialization" -> "RPC serialization error";
            case "forbidden" -> "RPC forbidden";
            case "authorization" -> "RPC authorization error";
            case "limitExceed" -> "RPC limit exceeded";
            case "noInvoker" -> "No available RPC provider";
            case "validation" -> "RPC validation error";
            case "business" -> "RPC business error";
            default -> "RPC failed";
        };
    }

    /**
     * Returns a copy of the exception data with the current TraceId, never mutating the exception
     * itself: a shared exception instance must stay thread-safe from the advice's point of view.
     */
    private Dict enrichTraceId(@Nullable Dict data) {
        Dict result = Dict.create();
        if (data != null) {
            result.putAll(data);
        }
        result.set(GXTraceIdContextUtils.TRACE_ID_KEY, GXTraceIdContextUtils.getTraceId());
        return result;
    }
}
