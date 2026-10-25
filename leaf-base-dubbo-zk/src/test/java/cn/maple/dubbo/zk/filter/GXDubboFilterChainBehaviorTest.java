package cn.maple.dubbo.zk.filter;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static cn.maple.dubbo.zk.support.GXDubboTestSupport.appResponse;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invocation;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invoker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Simulates the provider filter chain to prove the concrete impact of F-01: with the built-in
 * {@code ExceptionFilter} active, {@code GXBusinessException} loses its {@code code/msg/data}.
 */
class GXDubboFilterChainBehaviorTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.removeContext();
    }

    @Test
    void builtInExceptionFilterActiveLosesBusinessCode() {
        GXTraceIdContextUtils.putTraceId("chain-active");
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create().set("orderId", "A-1"));
        AppResponse response = appResponse(business);

        new ExceptionFilter().onResponse(response, invoker(TestService.class), invocation("call"));
        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation("call"));

        assertFalse(response.getException() instanceof GXBusinessException,
                "built-in filter wraps the cross-jar business exception, dropping code/msg/data");
        assertInstanceOf(RuntimeException.class, response.getException());
    }

    @Test
    void builtInExceptionFilterExcludedKeepsBusinessCode() {
        GXTraceIdContextUtils.putTraceId("chain-excluded");
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create().set("orderId", "A-1"));
        AppResponse response = appResponse(business);

        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation("call"));

        GXBusinessException preserved = assertInstanceOf(GXBusinessException.class, response.getException());
        assertSame(business, preserved);
        assertEquals(4001, preserved.getCode());
        assertEquals("A-1", preserved.getData().getStr("orderId"));
        assertEquals("chain-excluded", preserved.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void consumerSideWrapsUnclassifiedRuntimeException() {
        GXTraceIdContextUtils.putTraceId("chain-consumer");
        AppResponse response = appResponse(new UndeclaredException("wrapped-by-builtin"));

        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation("call"));

        GXBusinessException wrapped = assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals("providerRuntime", wrapped.getData().getStr("errorType"));
        assertEquals("chain-consumer", wrapped.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    interface TestService {
        void call();
    }

    static final class UndeclaredException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        UndeclaredException(String message) {
            super(message);
        }
    }
}
