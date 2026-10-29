package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.lang.Dict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXReadOnlyPayloadTest {
    @Test
    void sharedEntityListIsWrappedOnce() {
        List<Dict> entities = List.of(Dict.create().set("id", 1L), Dict.create().set("id", 2L));
        Dict payload = Dict.create()
                .set("operation", "saveBatch")
                .set("entityData", entities)
                .set("batchEntities", entities);

        Dict wrapped = GXReadOnlyPayload.wrap(payload);

        assertSame(wrapped.get("entityData"), wrapped.get("batchEntities"),
                "The batch aspect publishes one entity list under two keys; it must be copied once");
        assertEquals(2, ((List<?>) wrapped.get("batchEntities")).size());
        assertThrows(UnsupportedOperationException.class, () -> wrapped.set("operation", "hacked"));
    }

    @Test
    void sharedNestedDictIsWrappedOnce() {
        Dict row = Dict.create().set("id", 1L);
        Dict payload = Dict.create().set("first", row).set("second", row);

        Dict wrapped = GXReadOnlyPayload.wrap(payload);

        assertSame(wrapped.get("first"), wrapped.get("second"));
        assertThrows(UnsupportedOperationException.class, () -> ((Dict) wrapped.get("first")).set("id", 2L));
    }

    @Test
    void entrySetToleratesNullValues() {
        // Contract payload fields use empty defaults, but a null value must still not break the view.
        Dict wrapped = GXReadOnlyPayload.wrap(
                Dict.create().set("operation", "saveBatch").set("optionalValue", null));

        Map.Entry<String, Object> nullValueEntry = wrapped.entrySet().stream()
                .filter(entry -> "optionalValue".equals(entry.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The null-valued entry must be visible"));

        assertNull(nullValueEntry.getValue());
        assertThrows(UnsupportedOperationException.class, () -> nullValueEntry.setValue("hacked"));
    }

    @Test
    void wrappedArraysAreReadOnly() {
        Dict wrapped = GXReadOnlyPayload.wrap(
                Dict.create().set("operation", "saveBatch").set("ids", new String[]{"alpha", "beta"}));

        Object wrappedValues = wrapped.get("ids");
        assertInstanceOf(List.class, wrappedValues);
        @SuppressWarnings("unchecked")
        List<Object> ids = (List<Object>) wrappedValues;

        assertEquals(2, ids.size());
        assertThrows(UnsupportedOperationException.class, () -> ids.add("gamma"));
    }

    @Test
    void wrappingKeepsTheOriginalPayloadWritable() {
        List<Dict> entities = List.of(Dict.create().set("id", 1L));
        Dict payload = Dict.create().set("operation", "saveBatch").set("batchEntities", entities);

        GXReadOnlyPayload.wrap(payload);

        payload.set("operation", "still-writable");
        assertEquals("still-writable", payload.getStr("operation"));
        assertEquals(1L, entities.getFirst().getLong("id").longValue());
    }
}
