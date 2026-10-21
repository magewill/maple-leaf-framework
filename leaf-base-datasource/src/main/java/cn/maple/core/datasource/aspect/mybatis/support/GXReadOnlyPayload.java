package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.lang.Dict;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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
public final class GXReadOnlyPayload {
    private GXReadOnlyPayload() {
    }

    public static Dict wrap(Dict payload) {
        Map<String, Object> values = new LinkedHashMap<>();
        payload.forEach((key, value) -> values.put(key, wrapValue(value)));
        return new ReadOnlyDict(values);
    }

    private static @Nullable Object wrapValue(@Nullable Object value) {
        if (value instanceof Dict dict) {
            Map<String, Object> values = new LinkedHashMap<>();
            dict.forEach((key, nested) -> values.put(key, wrapValue(nested)));
            return new ReadOnlyDict(values);
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> values = new LinkedHashMap<>();
            map.forEach((key, nested) -> values.put(key, wrapValue(nested)));
            return Collections.unmodifiableMap(values);
        }
        if (value instanceof Set<?> set) {
            Set<Object> values = new LinkedHashSet<>();
            set.forEach(nested -> values.add(wrapValue(nested)));
            return Collections.unmodifiableSet(values);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> values = new ArrayList<>(collection.size());
            collection.forEach(nested -> values.add(wrapValue(nested)));
            return Collections.unmodifiableList(values);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(wrapValue(Array.get(value, index)));
            }
            return values;
        }
        return value;
    }

    /**
     * Read-only {@link Dict}; every mutator throws so a listener cannot affect other listeners.
     */
    private static final class ReadOnlyDict extends Dict {
        private ReadOnlyDict(Map<String, Object> source) {
            source.forEach(super::put);
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
         */
        @Override
        public Set<Map.Entry<String, Object>> entrySet() {
            Set<Map.Entry<String, Object>> entries = new LinkedHashSet<>();
            super.entrySet().forEach(entry -> entries.add(Map.entry(entry.getKey(), entry.getValue())));
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
