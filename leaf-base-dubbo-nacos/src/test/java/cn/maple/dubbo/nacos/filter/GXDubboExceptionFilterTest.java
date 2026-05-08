package cn.maple.dubbo.nacos.filter;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSentinelFlowException;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.GenericService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.FatalBeanException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXDubboExceptionFilterTest {
    private final GXDubboExceptionFilter filter = new GXDubboExceptionFilter();

    @Test
    void convertsSentinelFlowExceptionByMessage() {
        AppResponse response = new AppResponse();
        response.setException(new RuntimeException("SentinelBlockException: FlowException"));
        Invocation invocation = invocation("call");
        Invoker<?> invoker = invoker(TestService.class);

        filter.onResponse(response, invoker, invocation);

        assertInstanceOf(GXSentinelFlowException.class, response.getException());
    }

    @Test
    void keepsBusinessExceptionUnchanged() {
        GXBusinessException exception = new GXBusinessException("business");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
    }

    @Test
    void skipsGenericServiceCalls() {
        RuntimeException exception = new RuntimeException("generic");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(GenericService.class), invocation("$invoke"));

        assertSame(exception, response.getException());
    }

    @Test
    void keepsCheckedExceptionUnchanged() {
        Exception exception = new Exception("checked");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
    }

    @Test
    void keepsDeclaredExceptionUnchanged() {
        DeclaredException exception = new DeclaredException();
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("declared"));

        assertSame(exception, response.getException());
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
        RpcException exception = new RpcException("rpc");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
    }

    @Test
    void keepsJdkExceptionUnchanged() {
        IllegalArgumentException exception = new IllegalArgumentException("illegal");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertSame(exception, response.getException());
    }

    @Test
    void wrapsUndeclaredRuntimeExceptionFromDifferentCodebase() {
        FatalBeanException exception = new FatalBeanException("spring");
        AppResponse response = new AppResponse();
        response.setException(exception);

        filter.onResponse(response, invoker(TestService.class), invocation("call"));

        assertInstanceOf(GXBusinessException.class, response.getException());
    }

    interface TestService {
        void call();

        void declared() throws DeclaredException;
    }

    static class DeclaredException extends RuntimeException {
    }

    private Invocation invocation(String methodName) {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn(methodName);
        when(invocation.getParameterTypes()).thenReturn(new Class<?>[0]);
        when(invocation.getArguments()).thenReturn(new Object[0]);
        return invocation;
    }

    private Invoker<?> invoker(Class<?> serviceInterface) {
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getInterface()).thenReturn((Class) serviceInterface);
        return invoker;
    }
}
