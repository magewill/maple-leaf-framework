package cn.maple.dubbo.zk.filter;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.block.system.SystemBlockException;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mybatis.spring.MyBatisSystemException;
import org.slf4j.MDC;
import org.springframework.beans.FatalBeanException;

import java.util.stream.Stream;

import static cn.maple.dubbo.zk.support.GXDubboTestSupport.appResponse;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invocation;
import static cn.maple.dubbo.zk.support.GXDubboTestSupport.invoker;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXDubboExceptionFilterTest {
    private final GXDubboExceptionFilter filter = new GXDubboExceptionFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
        RpcContext.removeContext();
    }

    @Test
    void keepsBusinessExceptionUnchangedAndInjectsTraceId() {
        GXTraceIdContextUtils.putTraceId("biz-trace");
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create().set("orderId", "A-1"));

        AppResponse response = handle(business, invocation("call"));

        assertSame(business, response.getException());
        assertEquals(4001, business.getCode());
        assertEquals("biz-trace", business.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void wrapsBusinessExceptionWhenDataIsNullToExposeTraceId() {
        GXTraceIdContextUtils.putTraceId("biz-null-trace");
        GXBusinessException business = new GXBusinessException("business-error", 4001);

        AppResponse response = handle(business, invocation("call"));

        GXBusinessException wrapped = assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals(4001, wrapped.getCode());
        assertEquals("biz-null-trace", wrapped.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsSentinelFlowExceptionUnchanged() {
        GXTraceIdContextUtils.putTraceId("flow-trace");
        Dict data = Dict.create().set("methodName", "call");
        GXSentinelFlowException flow = new GXSentinelFlowException("busy", 406, data);

        AppResponse response = handle(flow, invocation("call"));

        GXSentinelFlowException preserved = assertInstanceOf(GXSentinelFlowException.class, response.getException());
        assertSame(flow, preserved);
        assertEquals(406, preserved.getCode());
        assertEquals("call", preserved.getData().getStr("methodName"));
        assertEquals("flow-trace", preserved.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsDeclaredExceptionUnchanged() {
        AppResponse response = handle(new DeclaredException(), invocation("declared"));

        assertInstanceOf(DeclaredException.class, response.getException());
    }

    @Test
    void keepsCheckedExceptionUnchanged() {
        AppResponse response = handle(new java.io.IOException("io"), invocation("call"));

        assertInstanceOf(java.io.IOException.class, response.getException());
    }

    @Test
    void keepsRpcExceptionUnchanged() {
        AppResponse response = handle(new RpcException("rpc"), invocation("call"));

        assertInstanceOf(RpcException.class, response.getException());
    }

    @Test
    void keepsJdkRuntimeExceptionUnchanged() {
        AppResponse response = handle(new IllegalArgumentException("bad-arg"), invocation("call"));

        assertInstanceOf(IllegalArgumentException.class, response.getException());
    }

    @Test
    void keepsMyBatisSystemExceptionUnchanged() {
        AppResponse response = handle(new MyBatisSystemException("mybatis"), invocation("call"));

        assertInstanceOf(MyBatisSystemException.class, response.getException());
    }

    @Test
    void skipsGenericServiceExceptionButStillAttachesTraceId() {
        GXTraceIdContextUtils.putTraceId("generic-trace");
        AppResponse response = appResponse(new IllegalStateException("generic"));

        filter.onResponse(response, invoker(GenericService.class), invocation("call"));

        assertInstanceOf(IllegalStateException.class, response.getException());
        assertEquals("generic-trace", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void convertsDegradeAndAuthorityExceptions() {
        GXTraceIdContextUtils.putTraceId("block-trace");

        AppResponse degrade = handle(new DegradeException("degrade"), invocation("call"));
        AppResponse authority = handle(new AuthorityException("authority"), invocation("call"));

        assertInstanceOf(GXSentinelFlowException.class, degrade.getException());
        assertInstanceOf(GXSentinelFlowException.class, authority.getException());
        assertEquals(406, ((GXSentinelFlowException) degrade.getException()).getCode());
    }

    @Test
    void convertsFlowByOfficialMessageWithoutLeakingArguments() {
        GXTraceIdContextUtils.putTraceId("flow-message-trace");
        Invocation invocation = invocation("call", new Class<?>[]{String.class}, new Object[]{"secret-value"});

        AppResponse response = handle(new RuntimeException("SentinelBlockException: FlowException"), invocation);

        GXSentinelFlowException flow = assertInstanceOf(GXSentinelFlowException.class, response.getException());
        Dict data = flow.getData();
        assertNotNull(data);
        assertEquals("call", data.getStr("methodName"));
        assertEquals("flow-message-trace", data.getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertFalse(data.containsKey("arguments"), "argument values must never be exposed");
    }

    @Test
    void doesNotMisclassifyBusinessMessageContainingFlowException() {
        GXBusinessException business = new GXBusinessException("downstream said FlowException but it is business", 4100);

        AppResponse response = handle(business, invocation("call"));

        assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals(4100, ((GXBusinessException) response.getException()).getCode());
    }

    @Test
    void wrapsUndeclaredRuntimeWhenMethodSignatureUnresolvable() {
        GXTraceIdContextUtils.putTraceId("signature-trace");
        Invocation mismatched = invocation("call", new Class<?>[]{String.class}, new Object[]{"x"});

        AppResponse response = handle(new FatalBeanException("boom"), mismatched);

        GXBusinessException wrapped = assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals(500, wrapped.getCode());
        assertEquals("providerRuntime", wrapped.getData().getStr("errorType"));
        assertEquals("signature-trace", wrapped.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void attachesTraceIdFromInvocationWhenMdcCleared() {
        Invocation invocation = invocation("call");
        when(invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn("trace-from-invocation");
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create());
        MDC.clear();

        AppResponse response = handle(business, invocation);

        assertSame(business, response.getException());
        assertEquals("trace-from-invocation", business.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("trace-from-invocation", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void recordsFilterInternalFailure() {
        long before = GXDubboExceptionFilter.filterFailureCount();
        Invoker<?> brokenInvoker = mock(Invoker.class);
        when(brokenInvoker.getInterface()).thenThrow(new IllegalStateException("broken-invoker"));

        AppResponse response = appResponse(new IllegalStateException("original"));

        filter.onResponse(response, brokenInvoker, invocation("call"));

        assertEquals(before + 1, GXDubboExceptionFilter.filterFailureCount());
        assertInstanceOf(IllegalStateException.class, response.getException());
        assertEquals("original", response.getException().getMessage());
    }

    @Test
    void convertsBusinessExceptionWhoseCauseIsBlockException() {
        GXTraceIdContextUtils.putTraceId("biz-block-trace");
        GXBusinessException business = new GXBusinessException("business-error", 4100, Dict.create().set("orderId", "A-1"),
                new DegradeException("degrade"));

        AppResponse response = handle(business, invocation("call"));

        GXSentinelFlowException converted = assertInstanceOf(GXSentinelFlowException.class, response.getException());
        assertEquals(406, converted.getCode());
        assertEquals("sentinelFlow", converted.getData().getStr("errorType"));
    }

    @Test
    void ignoresBlankInvocationTraceIdAndUsesMdcFallback() {
        GXTraceIdContextUtils.putTraceId("mdc-trace");
        Invocation invocation = invocation("call");
        when(invocation.getObjectAttachment(GXTraceIdContextUtils.TRACE_ID_KEY)).thenReturn("   ");
        GXBusinessException business = new GXBusinessException("business-error", 4001, Dict.create());

        AppResponse response = handle(business, invocation);

        assertSame(business, response.getException());
        assertEquals("mdc-trace", business.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsJakartaPrefixedExceptionUnchanged() {
        jakarta.validation.ValidationException jakartaException = new jakarta.validation.ValidationException("invalid");

        AppResponse response = handle(jakartaException, invocation("call"));

        assertInstanceOf(jakarta.validation.ValidationException.class, response.getException());
    }

    @ParameterizedTest(name = "{0} -> 406")
    @MethodSource("sentinelBlockExceptions")
    void convertsAllSentinelBlockSubtypes(Throwable blockException) {
        GXTraceIdContextUtils.putTraceId("block-" + blockException.getClass().getSimpleName());

        AppResponse response = handle(blockException, invocation("call"));

        GXSentinelFlowException converted = assertInstanceOf(GXSentinelFlowException.class, response.getException());
        assertEquals(406, converted.getCode());
        assertEquals("sentinelFlow", converted.getData().getStr("errorType"));
        assertEquals("block-" + blockException.getClass().getSimpleName(),
                converted.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    private static Stream<Arguments> sentinelBlockExceptions() {
        return Stream.of(
                Arguments.of(new FlowException("flow")),
                Arguments.of(new DegradeException("degrade")),
                Arguments.of(new AuthorityException("authority")),
                Arguments.of(new SystemBlockException("system")),
                Arguments.of(new ParamFlowException("param"))
        );
    }

    private AppResponse handle(Throwable throwable, Invocation invocation) {
        AppResponse response = appResponse(throwable);
        filter.onResponse(response, invoker(TestService.class), invocation);
        return response;
    }

    interface TestService {
        void call();

        void declared() throws DeclaredException;
    }

    static final class DeclaredException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
