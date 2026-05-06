package cn.maple.core.framework.config;

import cn.maple.core.framework.handler.GXAsyncExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXAsyncConfigTest {
    @Test
    void getAsyncExecutorUsesVirtualThreads() throws Exception {
        GXAsyncConfig config = new GXAsyncConfig(mock(ObjectProvider.class));
        Executor executor = config.getAsyncExecutor();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        executor.execute(() -> future.complete(Thread.currentThread().isVirtual()));

        assertTrue(future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void asyncExceptionHandlerDelegatesToCustomHandlerWhenAvailable() throws Exception {
        GXAsyncExceptionHandler customHandler = mock(GXAsyncExceptionHandler.class);
        ObjectProvider<GXAsyncExceptionHandler> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(customHandler);
        GXAsyncConfig config = new GXAsyncConfig(provider);
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();
        RuntimeException exception = new RuntimeException("async");
        Method method = GXAsyncConfigTest.class.getDeclaredMethod("sampleMethod", String.class);

        handler.handleUncaughtException(exception, method, "value");

        verify(customHandler).handleUncaughtException(exception, method, "value");
    }

    @SuppressWarnings("unused")
    private void sampleMethod(String value) {
    }
}
