package cn.maple.retry.context;

import org.springframework.core.retry.RetryState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Retry context exposed by the maple retry callbacks.
 */
public class GXRetryContext {

    public static final String NAME = "context.name";

    private final int retryCount;

    private final Throwable lastThrowable;

    private final Map<String, Object> attributes = new LinkedHashMap<>();

    private final GXRetryContext parent;

    public GXRetryContext(int retryCount, Throwable lastThrowable) {
        this(retryCount, lastThrowable, null);
    }

    public GXRetryContext(int retryCount, Throwable lastThrowable, GXRetryContext parent) {
        this.retryCount = retryCount;
        this.lastThrowable = lastThrowable;
        this.parent = parent;
    }

    public static GXRetryContext from(RetryState retryState) {
        Throwable lastException = null;
        if (!retryState.getExceptions().isEmpty()) {
            lastException = retryState.getLastException();
        }
        return new GXRetryContext(retryState.getRetryCount(), lastException);
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Throwable getLastThrowable() {
        return lastThrowable;
    }

    public Optional<Throwable> getLastThrowableOptional() {
        return Optional.ofNullable(lastThrowable);
    }

    public GXRetryContext getParent() {
        return parent;
    }

    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public String[] attributeNames() {
        return attributes.keySet().toArray(String[]::new);
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
