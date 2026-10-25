package cn.maple.dubbo.zk.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDubboCallExceptionHandlerTest {
    private final GXDubboCallExceptionHandler handler = new GXDubboCallExceptionHandler();

    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.removeContext();
    }

    @ParameterizedTest(name = "rpcCode={0} -> errorType={1}")
    @MethodSource("rpcExceptionCases")
    void rpcExceptionSubtypesKeepStableCode(int rpcCode, String expectedErrorType, String expectedMessage) {
        GXTraceIdContextUtils.putTraceId("rpc-trace");

        GXResultUtils<Dict> result = handler.handleRpcException(new RpcException(rpcCode));

        assertEquals(500, result.getCode());
        assertEquals("Dubbo provider error, please contact operations.", result.getMsg());
        Dict data = result.getData();
        assertNotNull(data);
        assertEquals(expectedErrorType, data.getStr("errorType"));
        assertEquals(expectedMessage, data.getStr("rpcMessage"));
        assertEquals(rpcCode, data.get("rpcCode"));
        assertEquals("rpc-trace", data.getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals(RpcException.class.getName(), data.getStr("exceptionClass"));
    }

    @Test
    void sentinelFlowKeepsCodeAndCopiesData() {
        GXTraceIdContextUtils.putTraceId("flow-advice-trace");
        Dict original = Dict.create().set("methodName", "call");
        GXSentinelFlowException flow = new GXSentinelFlowException("busy", 406, original);

        GXResultUtils<Dict> result = handler.handleSentinelFlowException(flow);

        assertEquals(406, result.getCode());
        assertEquals("busy", result.getMsg());
        assertEquals("call", result.getData().getStr("methodName"));
        assertEquals("flow-advice-trace", result.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertFalse(original.containsKey(GXTraceIdContextUtils.TRACE_ID_KEY),
                "handler must copy the exception data instead of mutating it");
    }

    @Test
    void adviceDeclaresHighestPrecedence() {
        Order order = GXDubboCallExceptionHandler.class.getAnnotation(Order.class);

        assertNotNull(order, "Dubbo advice must declare @Order to win over the catch-all base advice");
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());
    }

    @Test
    void sentinelFlowWithNullDataStillReturnsTraceId() {
        GXTraceIdContextUtils.putTraceId("flow-null-trace");
        GXSentinelFlowException flow = new GXSentinelFlowException("busy", 406);

        GXResultUtils<Dict> result = handler.handleSentinelFlowException(flow);

        assertNotNull(result.getData());
        assertEquals("flow-null-trace", result.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertTrue(result.getData().containsKey(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    private static Stream<Arguments> rpcExceptionCases() {
        return Stream.of(
                Arguments.of(RpcException.TIMEOUT_EXCEPTION, "timeout", "RPC timeout"),
                Arguments.of(RpcException.NETWORK_EXCEPTION, "network", "RPC network error"),
                Arguments.of(RpcException.SERIALIZATION_EXCEPTION, "serialization", "RPC serialization error"),
                Arguments.of(RpcException.FORBIDDEN_EXCEPTION, "forbidden", "RPC forbidden"),
                Arguments.of(RpcException.AUTHORIZATION_EXCEPTION, "authorization", "RPC authorization error"),
                Arguments.of(RpcException.LIMIT_EXCEEDED_EXCEPTION, "limitExceed", "RPC limit exceeded"),
                Arguments.of(RpcException.NO_INVOKER_AVAILABLE_AFTER_FILTER, "noInvoker", "No available RPC provider"),
                Arguments.of(RpcException.VALIDATION_EXCEPTION, "validation", "RPC validation error"),
                Arguments.of(RpcException.BIZ_EXCEPTION, "business", "RPC business error"),
                Arguments.of(RpcException.UNKNOWN_EXCEPTION, "unknown", "RPC failed")
        );
    }
}
