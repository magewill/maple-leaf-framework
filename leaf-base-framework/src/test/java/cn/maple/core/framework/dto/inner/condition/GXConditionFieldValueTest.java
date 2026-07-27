package cn.maple.core.framework.dto.inner.condition;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXConditionFieldValueTest {
    @Test
    void returnsEffectiveValuesForStringAndLikeConditions() {
        assertEquals("enabled", new GXConditionStrEQ("", "status", "enabled").getFieldValue());
        assertEquals("disabled", new GXConditionStrNE("", "status", "disabled").getFieldValue());
        assertEquals("%ops", new GXConditionLikeLeft("", "name", "ops").getFieldValue());
        assertEquals("ops%", new GXConditionLikeRight("", "name", "ops").getFieldValue());
        assertEquals("%ops%", new GXConditionLikeFull("", "name", "ops").getFieldValue());
    }

    @Test
    void collectionConditionsReturnImmutableSnapshots() {
        Set<Number> numericValues = new LinkedHashSet<>(List.of(3, 7));
        Set<String> stringValues = new LinkedHashSet<>(List.of("admin", "user"));
        GXConditionIn inCondition = new GXConditionIn("", "id", numericValues);
        GXConditionNotIn notInCondition = new GXConditionNotIn("", "id", numericValues);
        GXConditionStrIn stringInCondition = new GXConditionStrIn("", "role", stringValues);
        GXConditionStrNotIn stringNotInCondition = new GXConditionStrNotIn("", "role", stringValues);

        numericValues.add(9);
        stringValues.add("guest");

        assertImmutableSnapshot(inCondition.getFieldValue(), Set.of(3, 7));
        assertImmutableSnapshot(notInCondition.getFieldValue(), Set.of(3, 7));
        assertImmutableSnapshot(stringInCondition.getFieldValue(), Set.of("admin", "user"));
        assertImmutableSnapshot(stringNotInCondition.getFieldValue(), Set.of("admin", "user"));
    }

    private static void assertImmutableSnapshot(Object actual, Set<?> expected) {
        Set<?> snapshot = assertInstanceOf(Set.class, actual);
        assertEquals(expected, snapshot);
        assertThrows(UnsupportedOperationException.class, () -> addValue(snapshot));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addValue(Set<?> values) {
        ((Set) values).add(new Object());
    }
}
