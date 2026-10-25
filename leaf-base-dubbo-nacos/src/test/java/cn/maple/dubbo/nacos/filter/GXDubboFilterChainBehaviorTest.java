package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.filter.ExceptionFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static cn.maple.dubbo.nacos.support.GXDubboTestSupport.appResponse;
import static cn.maple.dubbo.nacos.support.GXDubboTestSupport.invocation;
import static cn.maple.dubbo.nacos.support.GXDubboTestSupport.invoker;
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
    }

    @Test
    void businessExceptionLosesCodeWhenBuiltInFilterActive() {
        GXTraceIdContextUtils.putTraceId("chain-active");
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create().set("orderId", "A-1"));
        AppResponse response = appResponse(business);
        Invocation invocation = invocation("call");

        new ExceptionFilter().onResponse(response, invoker(TestService.class), invocation);
        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation);

        assertFalse(response.getException() instanceof GXBusinessException);
        assertInstanceOf(RuntimeException.class, response.getException());
    }

    @Test
    void businessExceptionPreservedWhenBuiltInFilterExcluded() {
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

    interface TestService {
        void call();
    }
}
