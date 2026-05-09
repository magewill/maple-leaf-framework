package cn.maple.dubbo.nacos.filter;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import cn.maple.core.framework.util.GXTraceIdContextUtils;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.FatalBeanException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXDubboExceptionFilterTest {
    private final GXDubboExceptionFilter filter = new GXDubboExceptionFilter();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void convertsSentinelFlowExceptionByMessageWithoutLeakingArguments() {
        GXTraceIdContextUtils.putTraceId("trace-flow");
        AppResponse response = new AppResponse();
        response.setException(new RuntimeException("SentinelBlockException: FlowException"));
        Invocation invocation = invocation("call", new Class<?>[]{String.class}, new Object[]{"secret"});
        Invoker<?> invoker = invoker(TestService.class);

        filter.onResponse(response, invoker, invocation);

        GXSentinelFlowException exception = assertInstanceOf(GXSentinelFlowException.class, response.getException());
        assertEquals("trace-flow", exception.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("sentinelFlow", exception.getData().getStr("errorType"));
        assertEquals(TestService.class.getName(), exception.getData().getStr("interfaceName"));
        assertEquals("call", exception.getData().getStr("methodName"));
        assertEquals(1, exception.getData().getInt("argumentCount"));
        assertFalse(exception.getData().containsKey("arguments"));
    }

    @Test
    void keepsBusinessExceptionUnchanged() {
        GXTraceIdContextUtils.putTraceId("trace-business");
        GXBusinessException exception = new GXBusinessException("business", 500, Dict.create());
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
        assertEquals("trace-business", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("trace-business", exception.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void wrapsBusinessExceptionWhenDataIsNullToExposeTraceId() {
        GXTraceIdContextUtils.putTraceId("trace-business-null-data");
        GXBusinessException exception = new GXBusinessException("business");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        GXBusinessException wrapped = assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals("business", wrapped.getMsg());
        assertEquals(500, wrapped.getCode());
        assertSame(exception, wrapped.getCause());
        assertEquals("trace-business-null-data", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("trace-business-null-data", wrapped.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void skipsGenericServiceExceptionWrappingButStillAttachesTraceId() {
        GXTraceIdContextUtils.putTraceId("trace-generic");
        RuntimeException exception = new RuntimeException("generic");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(GenericService.class), invocation("$invoke"));

        assertSame(exception, response.getException());
        assertEquals("trace-generic", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsCheckedExceptionUnchanged() {
        GXTraceIdContextUtils.putTraceId("trace-checked");
        Exception exception = new Exception("checked");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
        assertEquals("trace-checked", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsDeclaredExceptionUnchanged() {
        GXTraceIdContextUtils.putTraceId("trace-declared");
        DeclaredException exception = new DeclaredException();
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("declared"));

        assertSame(exception, response.getException());
        assertEquals("trace-declared", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsMyBatisSystemExceptionUnchanged() {
        RuntimeException exception = new org.mybatis.spring.MyBatisSystemException("mybatis");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
    }

    @Test
    void keepsRpcExceptionUnchanged() {
        GXTraceIdContextUtils.putTraceId("trace-rpc");
        RpcException exception = new RpcException("rpc");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
        assertEquals("trace-rpc", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void keepsJdkExceptionUnchanged() {
        GXTraceIdContextUtils.putTraceId("trace-jdk");
        IllegalArgumentException exception = new IllegalArgumentException("illegal");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
        assertEquals("trace-jdk", response.getAttachment(GXTraceIdContextUtils.TRACE_ID_KEY));
    }

    @Test
    void wrapsUndeclaredRuntimeExceptionFromDifferentCodebase() {
        GXTraceIdContextUtils.putTraceId("trace-provider");
        FatalBeanException exception = new FatalBeanException("spring");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        GXBusinessException wrapped = assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals("trace-provider", wrapped.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("providerRuntime", wrapped.getData().getStr("errorType"));
        assertEquals(TestService.class.getName(), wrapped.getData().getStr("interfaceName"));
    }

    @Test
    void wrapsUndeclaredRuntimeExceptionFromSameCodebase() {
        GXTraceIdContextUtils.putTraceId("trace-same-codebase");
        SameCodebaseRuntimeException exception = new SameCodebaseRuntimeException();
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        GXBusinessException wrapped = assertInstanceOf(GXBusinessException.class, response.getException());
        assertEquals("trace-same-codebase", wrapped.getData().getStr(GXTraceIdContextUtils.TRACE_ID_KEY));
        assertEquals("providerRuntime", wrapped.getData().getStr("errorType"));
    }

    interface TestService {
        void call();

        void declared() throws DeclaredException;
    }

    static class DeclaredException extends RuntimeException {
    }

    static class SameCodebaseRuntimeException extends RuntimeException {
    }

    private Invocation invocation(String methodName) {
        return invocation(methodName, new Class<?>[0], new Object[0]);
    }

    private Invocation invocation(String methodName, Class<?>[] parameterTypes, Object[] arguments) {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn(methodName);
        when(invocation.getParameterTypes()).thenReturn(parameterTypes);
        when(invocation.getArguments()).thenReturn(arguments);
        return invocation;
    }

    private Invoker<?> invoker(Class<?> serviceInterface) {
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getInterface()).thenReturn((Class) serviceInterface);
        return invoker;
    }
}
