package cn.maple.dubbo.zk.support;

import cn.maple.dubbo.zk.selector.GXPenetrateAttachmentSelector;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test doubles and helpers shared by the dubbo-zk tests.
 *
 * <p>Scenarios are always built from injectable collaborators or mocks, never from reflection on
 * private fields.
 */
public final class GXDubboTestSupport {
    private GXDubboTestSupport() {
    }

    @SuppressWarnings("unchecked")
    public static Invoker<?> invoker(Class<?> serviceInterface) {
        Invoker<?> invoker = mock(Invoker.class);
        when(invoker.getInterface()).thenReturn((Class) serviceInterface);
        return invoker;
    }

    public static Invocation invocation(String methodName) {
        return invocation(methodName, new Class<?>[0], new Object[0]);
    }

    public static Invocation invocation(String methodName, Class<?>[] parameterTypes, Object[] arguments) {
        Invocation invocation = mock(Invocation.class);
        when(invocation.getMethodName()).thenReturn(methodName);
        when(invocation.getParameterTypes()).thenReturn(parameterTypes);
        when(invocation.getArguments()).thenReturn(arguments);
        return invocation;
    }

    public static AppResponse appResponse(Throwable throwable) {
        AppResponse response = new AppResponse();
        response.setException(throwable);
        return response;
    }

    public static void resetAppNameCache() {
        GXPenetrateAttachmentSelector.resetAppNameCache();
    }
}
