package cn.maple.dubbo.nacos.handler;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.handler.GXExceptionHandler;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GXDubboCallExceptionHandler extends GXExceptionHandler {
    @ExceptionHandler(RpcException.class)
    public GXResultUtils<Dict> handleRpcException(RpcException e) {
        log.error("Dubbo RPC exception", e);
        return GXResultUtils.error(HttpStatus.HTTP_INTERNAL_ERROR, "Dubbo provider error, please contact operations.", buildRpcErrorData(e));
    }

    @ExceptionHandler(GXSentinelFlowException.class)
    public GXResultUtils<Dict> handleSentinelFlowException(GXSentinelFlowException e) {
        log.error("Dubbo Sentinel flow exception", e);
        return GXResultUtils.error(e.getCode(), e.getMsg(), enrichTraceId(e.getData()));
    }

    private Dict buildRpcErrorData(RpcException e) {
        return Dict.create()
                .set(GXTraceIdContextUtils.TRACE_ID_KEY, GXTraceIdContextUtils.getTraceId())
                .set("errorType", resolveRpcErrorType(e))
                .set("rpcCode", e.getCode())
                .set("exceptionClass", e.getClass().getName())
                .set("rpcMessage", resolveRpcMessage(e));
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

    private String resolveRpcMessage(RpcException e) {
        return switch (resolveRpcErrorType(e)) {
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

    private Dict enrichTraceId(Dict data) {
        Dict result = data == null ? Dict.create() : data;
        result.set(GXTraceIdContextUtils.TRACE_ID_KEY, GXTraceIdContextUtils.getTraceId());
        return result;
    }
}
