package cn.maple.retry.listener;

import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryState;
import org.springframework.core.retry.Retryable;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GXRetryListenerTest {

    @Test
    void getRetryableTypeDescription_DoesNotCacheInstanceNameByClass() throws Exception {
        GXRetryListener.clearCache();
        GXRetryListener listener = new GXRetryListener();
        Method method = GXRetryListener.class.getDeclaredMethod("getRetryableTypeDescription", Retryable.class);
        method.setAccessible(true);

        assertEquals("firstName", method.invoke(listener, new NamedRetryable("firstName")));
        assertEquals("secondName", method.invoke(listener, new NamedRetryable("secondName")));
    }

    @Test
    void onRetryPolicyExhaustion_LogsRetryException() {
        GXRetryListener listener = new GXRetryListener();
        RetryException exception = new RetryException("retry failed", new IllegalStateException("failed"));

        assertDoesNotThrow(() -> listener.onRetryPolicyExhaustion(null, new NamedRetryable("retryable"), exception));
    }

    @Test
    void onRetryableExecution_AllowsNullLastException() {
        GXRetryListener listener = new GXRetryListener();
        RetryState retryState = new RetryState() {
            @Override
            public int getRetryCount() {
                return 1;
            }

            @Override
            public List<Throwable> getExceptions() {
                return List.of();
            }

            @Override
            public Throwable getLastException() {
                return null;
            }

            @Override
            public boolean isSuccessful() {
                return false;
            }
        };

        assertDoesNotThrow(() -> listener.onRetryableExecution(null, new NamedRetryable("retryable"), retryState));
    }

    private record NamedRetryable(String name) implements Retryable<Object> {

        @Override
        public Object execute() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
