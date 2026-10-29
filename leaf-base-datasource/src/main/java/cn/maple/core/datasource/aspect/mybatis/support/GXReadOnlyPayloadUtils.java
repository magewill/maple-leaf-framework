package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.lang.Dict;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a single read-only snapshot that can be shared by several listeners.
 *
 * <p>Batch payloads are produced once by the aspect and then delivered to every declared listener.
 * Copying the payload per listener keeps listeners isolated but doubles memory for large batches
 * ({@code SC-005}). This utility copies the payload once into read-only containers, so all
 * listeners share the same snapshot while any mutation attempt fails instead of leaking into
 * another listener's payload ({@code FR-008}).</p>
 */
public final class GXReadOnlyPayloadUtils {
    private GXReadOnlyPayloadUtils() {
    }

    public static Dict wrap(Dict payload) {
        return wrapDict(payload, new IdentityHashMap<>());
    }

    private static Dict wrapDict(Dict payload, Map<Object, Object> wrapped) {
        ReadOnlyDict copy = new ReadOnlyDict();
        // Register the copy before recursing: the batch aspect writes one entity list under both
        // entityData and batchEntities, so the same container is reached twice and must be copied
        // only once to stay within the SC-005 2x budget.
        wrapped.put(payload, copy);
        payload.forEach((key, value) -> copy.seed(key, wrapValue(value, wrapped)));
        return copy;
    }

    private static @Nullable Object wrapValue(@Nullable Object value, Map<Object, Object> wrapped) {
        if (value == null) {
            return null;
        }
        Object existing = wrapped.get(value);
        if (existing != null) {
            return existing;
        }
        if (value instanceof Dict dict) {
            return wrapDict(dict, wrapped);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> values = new HashMap<>(map.size() * 2);
            Map<Object, Object> copy = Collections.unmodifiableMap(values);
            wrapped.put(value, copy);
            map.forEach((key, nested) -> values.put(key, wrapValue(nested, wrapped)));
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> values = new LinkedHashSet<>();
            Set<Object> copy = Collections.unmodifiableSet(values);
            wrapped.put(value, copy);
            set.forEach(nested -> values.add(wrapValue(nested, wrapped)));
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> values = new ArrayList<>(collection.size());
            List<Object> copy = Collections.unmodifiableList(values);
            wrapped.put(value, copy);
            collection.forEach(nested -> values.add(wrapValue(nested, wrapped)));
            return copy;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            List<Object> copy = Collections.unmodifiableList(values);
            wrapped.put(value, copy);
            for (int index = 0; index < length; index++) {
                values.add(wrapValue(Array.get(value, index), wrapped));
            }
            return copy;
        }
        return value;
    }

    /**
     * Read-only {@link Dict}; every mutator throws so a listener cannot affect other listeners.
     */
    private static final class ReadOnlyDict extends Dict {
        private ReadOnlyDict() {
        }

        /**
         * Fills the view while it is being built; the shared snapshot is handed to listeners only
         * after the whole payload has been wrapped.
         */
        private void seed(String key, @Nullable Object value) {
            super.put(key, value);
        }

        @Override
        public Object put(String key, Object value) {
            throw readOnly();
        }

        @Override
        public void putAll(Map<? extends String, ?> map) {
            throw readOnly();
        }

        @Override
        public Object remove(Object key) {
            throw readOnly();
        }

        @Override
        public boolean remove(Object key, Object value) {
            throw readOnly();
        }

        @Override
        public void clear() {
            throw readOnly();
        }

        @Override
        public Object putIfAbsent(String key, Object value) {
            throw readOnly();
        }

        @Override
        public boolean replace(String key, Object oldValue, Object newValue) {
            throw readOnly();
        }

        @Override
        public Object replace(String key, Object value) {
            throw readOnly();
        }

        @Override
        public Object computeIfAbsent(String key, java.util.function.Function<? super String, ?> mappingFunction) {
            throw readOnly();
        }

        @Override
        public Object computeIfPresent(String key,
                                       java.util.function.BiFunction<? super String, ? super Object, ?> remappingFunction) {
            throw readOnly();
        }

        @Override
        public Object compute(String key,
                              java.util.function.BiFunction<? super String, ? super Object, ?> remappingFunction) {
            throw readOnly();
        }

        @Override
        public Object merge(String key, Object value,
                            java.util.function.BiFunction<? super Object, ? super Object, ?> remappingFunction) {
            throw readOnly();
        }

        @Override
        public void replaceAll(java.util.function.BiFunction<? super String, ? super Object, ? extends Object> function) {
            throw readOnly();
        }

        /**
         * Returns immutable entry copies, so {@code entry.setValue(...)} cannot bypass the read-only view.
         *
         * <p>{@link AbstractMap.SimpleImmutableEntry} rather than {@link Map#entry}: payload values may
         * legitimately be {@code null} (is-null conditions, entity fields without a value) and
         * {@code Map.entry} rejects them with a {@link NullPointerException}.</p>
         */
        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            Set<Map.Entry<String, Object>> entries = new LinkedHashSet<>();
            super.entrySet().forEach(entry -> entries.add(
                    new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue())));
            return Collections.unmodifiableSet(entries);
        }

        @Override
        public Set<String> keySet() {
            return Collections.unmodifiableSet(super.keySet());
        }

        @Override
        public Collection<Object> values() {
            return Collections.unmodifiableCollection(super.values());
        }

        private UnsupportedOperationException readOnly() {
            return new UnsupportedOperationException(
                    "MyBatis listener payload is a shared read-only snapshot; copy it before modifying");
        }
    }
}
