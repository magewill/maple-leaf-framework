package cn.maple.dubbo.nacos.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GXDubboCallExceptionHandlerTest {
    private final GXDubboCallExceptionHandler handler = new GXDubboCallExceptionHandler();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void handlesRpcExceptionWithStableErrorCode() {
        GXTraceIdContextUtils.putTraceId("trace-rpc");

        GXResultUtils<Dict> result = handler.handleRpcException(new RpcException(RpcException.TIMEOUT_EXCEPTION, "timeout"));

        assertEquals(500, result.getCode());
        assertEquals("Dubbo provider error, please contact operations.", result.getMsg());
        assertEquals("trace-rpc", result.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("timeout", result.getData().getStr("errorType"));
        assertEquals(RpcException.TIMEOUT_EXCEPTION, result.getData().getInt("rpcCode"));
        assertEquals("RPC timeout", result.getData().getStr("rpcMessage"));
    }

    @Test
    void handlesSentinelFlowExceptionWithOriginalCodeMessageAndData() {
        GXTraceIdContextUtils.putTraceId("trace-flow");
        Dict data = Dict.create().set("methodName", "call");
        GXSentinelFlowException exception = new GXSentinelFlowException("busy", 406, data);

        GXResultUtils<Dict> result = handler.handleSentinelFlowException(exception);

        assertEquals(406, result.getCode());
        assertEquals("busy", result.getMsg());
        assertEquals(data, result.getData());
        assertEquals("trace-flow", result.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void rpcExceptionSubtypesKeepStableCode() {
        Map<Integer, String> expectedErrorTypes = new LinkedHashMap<>();
        expectedErrorTypes.put(RpcException.TIMEOUT_EXCEPTION, "timeout");
        expectedErrorTypes.put(RpcException.NETWORK_EXCEPTION, "network");
        expectedErrorTypes.put(RpcException.SERIALIZATION_EXCEPTION, "serialization");
        expectedErrorTypes.put(RpcException.FORBIDDEN_EXCEPTION, "forbidden");
        expectedErrorTypes.put(RpcException.AUTHORIZATION_EXCEPTION, "authorization");
        expectedErrorTypes.put(RpcException.LIMIT_EXCEEDED_EXCEPTION, "limitExceed");
        expectedErrorTypes.put(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER, "noInvoker");
        expectedErrorTypes.put(RpcException.VALIDATION_EXCEPTION, "validation");
        expectedErrorTypes.put(RpcException.BIZ_EXCEPTION, "business");

        expectedErrorTypes.forEach((rpcCode, errorType) -> {
            GXResultUtils<Dict> result = handler.handleRpcException(new RpcException(rpcCode, "subtype-" + rpcCode));

            assertEquals(500, result.getCode());
            assertEquals(errorType, result.getData().getStr("errorType"));
            assertEquals(rpcCode, result.getData().getInt("rpcCode"));
        });
    }
}
