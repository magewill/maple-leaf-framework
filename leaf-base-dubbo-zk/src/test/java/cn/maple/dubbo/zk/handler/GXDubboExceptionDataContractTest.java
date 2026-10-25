package cn.maple.dubbo.zk.handler;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import cn.maple.dubbo.zk.filter.GXDubboExceptionFilter;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.RpcContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static cn.maple.dubbo.zk.support.GXDubboTestSupport.appResponse;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invocation;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invoker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Documents who may mutate the exception data map.
 *
 * <p>Filter side: the existing map is mutated in place (accepted contract, because {@code data} is a
 * final field). Advice side: the exception map is never mutated, a copy is returned instead.
 */
class GXDubboExceptionDataContractTest {
    private final GXDubboCallExceptionHandler handler = new GXDubboCallExceptionHandler();

    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.removeContext();
    }

    @Test
    void filterInjectsTraceIdIntoExistingDataInPlace() {
        GXTraceIdContextUtils.putTraceId("in-place-trace");
        Dict data = Dict.create().set("orderId", "A-1");
        GXBusinessException business = new GXBusinessException("business-error", 4001, data);

        AppResponse response = appResponse(business);
        new GXDubboExceptionFilter().onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(business, response.getException());
        assertSame(data, business.getData());
        assertEquals("in-place-trace", data.getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void adviceNeverMutatesExceptionData() {
        GXTraceIdContextUtils.putTraceId("advice-trace");
        Dict data = Dict.create().set("methodName", "call");
        GXSentinelFlowException flow = new GXSentinelFlowException("busy", 406, data);

        GXResultUtils<Dict> result = handler.handleSentinelFlowException(flow);

        assertEquals("advice-trace", result.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertFalse(data.containsKey(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals(1, data.size());
    }

    interface TestService {
        void call();
    }
}
