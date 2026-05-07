package cn.maple.retry.context;

import org.springframework.core.retry.RetryState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Retry context exposed by the maple retry callbacks.
 */
public class GXRetryContext {

    public static final String NAME = "context.name";

    private static final AttributeKey NULL_ATTRIBUTE_KEY = new AttributeKey(null);

    private static final Object NULL_ATTRIBUTE_VALUE = new Object();

    private final int retryCount;

    private final Throwable lastThrowable;

    private final Map<AttributeKey, Object> attributes = new ConcurrentHashMap<>();

    private final Queue<AttributeKey> attributeOrder = new ConcurrentLinkedQueue<>();

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
        AttributeKey attributeKey = toAttributeKey(name);
        Object attributeValue = maskNullValue(value);
        Object previous = attributes.putIfAbsent(attributeKey, attributeValue);
        if (previous == null) {
            attributeOrder.add(attributeKey);
        } else {
            attributes.put(attributeKey, attributeValue);
        }
    }

    public Object getAttribute(String name) {
        return unmaskNullValue(attributes.get(toAttributeKey(name)));
    }

    public String[] attributeNames() {
        return attributeOrder.stream()
                .filter(attributes::containsKey)
                .map(AttributeKey::name)
                .toArray(String[]::new);
    }

    public Map<String, Object> getAttributes() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        attributeOrder.stream()
                .filter(attributes::containsKey)
                .forEach(attributeKey -> snapshot.put(attributeKey.name(),
                        unmaskNullValue(attributes.get(attributeKey))));
        return Collections.unmodifiableMap(snapshot);
    }

    private AttributeKey toAttributeKey(String name) {
        return name == null ? NULL_ATTRIBUTE_KEY : new AttributeKey(name);
    }

    private Object maskNullValue(Object value) {
        return value == null ? NULL_ATTRIBUTE_VALUE : value;
    }

    private Object unmaskNullValue(Object value) {
        return value == NULL_ATTRIBUTE_VALUE ? null : value;
    }

    private record AttributeKey(String name) {
    }
}
