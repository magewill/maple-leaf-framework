package cn.maple.core.framework.event;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.lang.GXDict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.util.Calendar;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXBaseEventTest {
    @Test
    void snapshotsNestedMapAndCollectionSources() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "created");
        List<String> tags = new ArrayList<>(List.of("initial"));
        Dict source = Dict.create().set("details", details).set("tags", tags);

        GXBaseEvent<Dict> event = new GXBaseEvent<>(source);
        details.put("status", "changed");
        tags.add("later");
        source.set("newField", "newValue");

        Map<?, ?> eventDetails = (Map<?, ?>) event.getSource().get("details");
        List<?> eventTags = (List<?>) event.getSource().get("tags");
        assertEquals("created", eventDetails.get("status"));
        assertEquals(List.of("initial"), eventTags);
        assertEquals(null, event.getSource().get("newField"));
    }

    @Test
    void preservesArraySourceTypeWhileSnapshottingItsContents() {
        byte[] source = {1, 2};

        GXBaseEvent<byte[]> event = new GXBaseEvent<>(source);
        source[0] = 9;

        assertArrayEquals(new byte[]{1, 2}, event.getSource());
    }

    @Test
    void preservesConcreteMapSourceTypeWhileSnapshottingItsContents() {
        HashMap<String, Object> source = new HashMap<>();
        source.put("status", "created");

        GXBaseEvent<HashMap<String, Object>> event = new GXBaseEvent<>(source);
        source.put("status", "changed");

        HashMap<String, Object> eventSource = assertInstanceOf(HashMap.class, event.getSource());
        assertEquals("created", eventSource.get("status"));
    }

    @Test
    void preservesGXDictSourceTypeWhileSnapshottingItsContents() {
        GXDict source = GXDict.create().set("status", "created");

        GXBaseEvent<GXDict> event = new GXBaseEvent<>(source);
        source.set("status", "changed");

        GXDict eventSource = assertInstanceOf(GXDict.class, event.getSource());
        assertEquals("created", eventSource.get("status"));
    }

    @Test
    void preservesArrayDequeSourceTypeWhileSnapshottingItsContents() {
        ArrayDeque<String> source = new ArrayDeque<>(List.of("created"));

        GXBaseEvent<ArrayDeque<String>> event = new GXBaseEvent<>(source);
        source.add("changed");

        ArrayDeque<String> eventSource = assertInstanceOf(ArrayDeque.class, event.getSource());
        assertEquals(List.of("created"), new ArrayList<>(eventSource));
    }

    @Test
    void snapshotsSelfReferentialMapWithoutRecursingIndefinitely() {
        Map<String, Object> source = new HashMap<>();
        source.put("self", source);

        GXBaseEvent<Map<String, Object>> event = new GXBaseEvent<>(source);

        assertSame(event.getSource(), event.getSource().get("self"));
    }

    @Test
    void rejectsSnapshotsThatExceedTheSupportedNestingDepth() {
        Map<String, Object> source = new HashMap<>();
        Map<String, Object> nested = source;
        for (int level = 0; level < 65; level++) {
            Map<String, Object> child = new HashMap<>();
            nested.put("child", child);
            nested = child;
        }

        assertThrows(IllegalArgumentException.class, () -> new GXBaseEvent<>(source));
    }

    @Test
    void snapshotsMutableDateLeafValues() {
        Date createdAt = new Date(1_000L);
        Dict source = Dict.create().set("createdAt", createdAt);

        GXBaseEvent<Dict> event = new GXBaseEvent<>(source);
        createdAt.setTime(2_000L);

        assertEquals(new Date(1_000L), event.getSource().get("createdAt"));
    }

    @Test
    void preservesEnumMapSourceTypeWhileSnapshottingItsContents() {
        EnumMap<TestState, String> source = new EnumMap<>(TestState.class);
        source.put(TestState.CREATED, "created");

        GXBaseEvent<EnumMap<TestState, String>> event = new GXBaseEvent<>(source);
        source.put(TestState.CREATED, "changed");

        EnumMap<TestState, String> eventSource = assertInstanceOf(EnumMap.class, event.getSource());
        assertEquals("created", eventSource.get(TestState.CREATED));
    }

    @Test
    void snapshotsMutableCalendarLeafValues() {
        Calendar createdAt = Calendar.getInstance();
        createdAt.setTimeInMillis(1_000L);
        Dict source = Dict.create().set("createdAt", createdAt);

        GXBaseEvent<Dict> event = new GXBaseEvent<>(source);
        createdAt.setTimeInMillis(2_000L);

        assertEquals(1_000L, ((Calendar) event.getSource().get("createdAt")).getTimeInMillis());
    }

    private enum TestState {
        CREATED
    }
}
