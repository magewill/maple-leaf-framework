package cn.maple.retry.context;

import org.jspecify.annotations.Nullable;
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
 *
 * <p>A fresh instance is created for every attempt. Attributes written by a previous attempt are
 * copied into the next attempt's context, and {@link #getParent()} points to the previous attempt,
 * so {@code setAttribute} in attempt 1 is visible in attempt 2.
 *
 * <p>{@link #getRetryCount()} and {@link #getAttemptIndex()} both return the 0-based attempt index,
 * which is numerically equal to the number of retries already performed.
 */
public class GXRetryContext {
    public static final String NAME = "context.name";

    private static final AttributeKey NULL_ATTRIBUTE_KEY = new AttributeKey(null);

    private static final Object NULL_ATTRIBUTE_VALUE = new Object();

    private final int retryCount;

    private final @Nullable Throwable lastThrowable;

    private final Map<AttributeKey, Object> attributes = new ConcurrentHashMap<>();

    private final Queue<AttributeKey> attributeOrder = new ConcurrentLinkedQueue<>();

    private final @Nullable GXRetryContext parent;

    public GXRetryContext(int retryCount, @Nullable Throwable lastThrowable) {
        this(retryCount, lastThrowable, null);
    }

    public GXRetryContext(int retryCount, @Nullable Throwable lastThrowable, @Nullable GXRetryContext parent) {
        this.retryCount = retryCount;
        this.lastThrowable = lastThrowable;
        this.parent = parent;
        if (parent != null) {
            parent.getAttributes().forEach(this::setAttribute);
        }
    }

    public static GXRetryContext from(RetryState retryState) {
        return from(retryState, null);
    }

    public static GXRetryContext from(RetryState retryState, @Nullable GXRetryContext parent) {
        Throwable lastException = null;
        if (!retryState.getExceptions().isEmpty()) {
            lastException = retryState.getLastException();
        }
        return new GXRetryContext(retryState.getRetryCount(), lastException, parent);
    }

    /**
     * Number of retries already performed; {@code 0} for the initial attempt.
     */
    public int getRetryCount() {
        return retryCount;
    }

    /**
     * 0-based index of the current attempt, numerically equal to {@link #getRetryCount()}.
     */
    public int getAttemptIndex() {
        return retryCount;
    }

    public @Nullable Throwable getLastThrowable() {
        return lastThrowable;
    }

    public Optional<Throwable> getLastThrowableOptional() {
        return Optional.ofNullable(lastThrowable);
    }

    public @Nullable GXRetryContext getParent() {
        return parent;
    }

    public void setAttribute(@Nullable String name, @Nullable Object value) {
        AttributeKey attributeKey = toAttributeKey(name);
        Object attributeValue = maskNullValue(value);
        Object previous = attributes.putIfAbsent(attributeKey, attributeValue);
        if (previous == null) {
            attributeOrder.add(attributeKey);
        } else {
            attributes.put(attributeKey, attributeValue);
        }
    }

    public @Nullable Object getAttribute(@Nullable String name) {
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

    private AttributeKey toAttributeKey(@Nullable String name) {
        return name == null ? NULL_ATTRIBUTE_KEY : new AttributeKey(name);
    }

    private Object maskNullValue(@Nullable Object value) {
        return value == null ? NULL_ATTRIBUTE_VALUE : value;
    }

    private @Nullable Object unmaskNullValue(@Nullable Object value) {
        return value == NULL_ATTRIBUTE_VALUE ? null : value;
    }

    private record AttributeKey(@Nullable String name) {
    }
}
