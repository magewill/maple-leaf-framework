package cn.maple.core.framework.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.lang.GXDict;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.IdentityHashMap;

/**
 * Event payload snapshotting supports maps, collections, arrays, {@link Date}, and {@link Calendar}.
 * Callers must convert custom mutable objects to immutable values or DTO maps before publishing; the
 * event system deliberately does not use Java serialization or reflective cloning for arbitrary objects.
 */
@Getter
public class GXBaseEvent<T> extends ApplicationEvent implements ResolvableTypeProvider {
    private static final int MAX_SNAPSHOT_DEPTH = 64;
    private static final int MAX_SNAPSHOT_NODES = 100_000;
    private final Dict param;

    private final String eventName;

    private final String eventType;

    public GXBaseEvent(T source) {
        this(source, "", Dict.create(), "");
    }

    public GXBaseEvent(T source, String eventType) {
        this(source, eventType, Dict.create(), "");
    }

    public GXBaseEvent(T source, String eventType, String eventName) {
        this(source, eventType, Dict.create(), eventName);
    }

    public GXBaseEvent(T source, String eventType, Dict param) {
        this(source, eventType, param, "");
    }

    public GXBaseEvent(T source, String eventType, Dict param, String eventName) {
        super(snapshotSource(source));
        this.param = copyParam(param);
        this.eventName = eventName;
        this.eventType = eventType;
    }

    public Object getEventName() {
        return eventName;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClass(getClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getSource() {
        return (T) super.getSource();
    }

    private static Dict copyParam(Dict param) {
        return (Dict) snapshot(param == null ? Dict.create() : param, new SnapshotContext(), 0);
    }

    @SuppressWarnings("unchecked")
    private static <T> T snapshotSource(T source) {
        return (T) snapshot(source, new SnapshotContext(), 0);
    }

    private static Object snapshot(Object source, SnapshotContext context, int depth) {
        if (source == null) {
            return null;
        }
        Object existingSnapshot = context.snapshots.get(source);
        if (existingSnapshot != null) {
            return existingSnapshot;
        }
        context.record(depth);
        if (source instanceof Date date) {
            Object copy = date.clone();
            context.snapshots.put(source, copy);
            return copy;
        }
        if (source instanceof Calendar calendar) {
            Object copy = calendar.clone();
            context.snapshots.put(source, copy);
            return copy;
        }
        if (source instanceof GXDict dict) {
            GXDict copy = GXDict.create();
            context.snapshots.put(source, copy);
            dict.forEach((key, value) -> copy.set(key, snapshot(value, context, depth + 1)));
            return copy;
        }
        if (source instanceof Dict dict) {
            Dict copy = Dict.create();
            context.snapshots.put(source, copy);
            dict.forEach((key, value) -> copy.set(key, snapshot(value, context, depth + 1)));
            return copy;
        }
        if (source instanceof Map<?, ?> map) {
            Map<Object, Object> copy = createMapSnapshot(map);
            context.snapshots.put(source, copy);
            map.forEach((key, value) -> copy.put(snapshot(key, context, depth + 1), snapshot(value, context, depth + 1)));
            return copy;
        }
        if (source instanceof Set<?> set) {
            Set<Object> copy = createSetSnapshot(set);
            context.snapshots.put(source, copy);
            set.forEach(value -> copy.add(snapshot(value, context, depth + 1)));
            return copy;
        }
        if (source instanceof Collection<?> collection) {
            Collection<Object> copy = createCollectionSnapshot(collection);
            context.snapshots.put(source, copy);
            collection.forEach(value -> copy.add(snapshot(value, context, depth + 1)));
            return copy;
        }
        if (source.getClass().isArray()) {
            int length = Array.getLength(source);
            Object copy = Array.newInstance(source.getClass().getComponentType(), length);
            context.snapshots.put(source, copy);
            for (int index = 0; index < length; index++) {
                Array.set(copy, index, snapshot(Array.get(source, index), context, depth + 1));
            }
            return copy;
        }
        return source;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> createMapSnapshot(Map<?, ?> source) {
        if (source instanceof EnumMap<?, ?> enumMap) {
            EnumMap<?, ?> copy = new EnumMap<>(enumMap);
            copy.clear();
            return (Map<Object, Object>) copy;
        }
        if (source instanceof TreeMap<?, ?> treeMap) {
            return new TreeMap<>((java.util.Comparator<Object>) treeMap.comparator());
        }
        if (source instanceof ConcurrentSkipListMap<?, ?> concurrentSkipListMap) {
            return new ConcurrentSkipListMap<>((java.util.Comparator<Object>) concurrentSkipListMap.comparator());
        }
        if (source instanceof ConcurrentHashMap<?, ?>) {
            return new ConcurrentHashMap<>();
        }
        if (source instanceof LinkedHashMap<?, ?>) {
            return new LinkedHashMap<>();
        }
        if (source instanceof HashMap<?, ?>) {
            return new HashMap<>();
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> createSetSnapshot(Set<?> source) {
        if (source instanceof TreeSet<?> treeSet) {
            return new TreeSet<>((java.util.Comparator<Object>) treeSet.comparator());
        }
        if (source instanceof CopyOnWriteArraySet<?>) {
            return new CopyOnWriteArraySet<>();
        }
        if (source instanceof LinkedHashSet<?>) {
            return new LinkedHashSet<>();
        }
        if (source instanceof HashSet<?>) {
            return new HashSet<>();
        }
        return new LinkedHashSet<>();
    }

    private static Collection<Object> createCollectionSnapshot(Collection<?> source) {
        if (source instanceof ArrayDeque<?>) {
            return new ArrayDeque<>(source.size());
        }
        if (source instanceof LinkedList<?>) {
            return new LinkedList<>();
        }
        if (source instanceof CopyOnWriteArrayList<?>) {
            return new CopyOnWriteArrayList<>();
        }
        return new ArrayList<>(source.size());
    }

    private static final class SnapshotContext {
        private final IdentityHashMap<Object, Object> snapshots = new IdentityHashMap<>();
        private int nodes;

        private void record(int depth) {
            if (depth > MAX_SNAPSHOT_DEPTH) {
                throw new IllegalArgumentException("Event snapshot exceeds maximum nesting depth");
            }
            if (++nodes > MAX_SNAPSHOT_NODES) {
                throw new IllegalArgumentException("Event snapshot exceeds maximum node count");
            }
        }
    }
}
