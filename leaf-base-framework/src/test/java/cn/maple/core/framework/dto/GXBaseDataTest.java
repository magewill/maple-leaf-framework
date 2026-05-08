package cn.maple.core.framework.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBaseDataTest {
    private final TestBaseData baseData = new TestBaseData();

    @Test
    void convertJsonObjectToTargetUsesFrameworkConversion() {
        Target target = baseData.toTarget("{\"name\":\"alpha\",\"age\":\"18\"}");

        assertNotNull(target);
        assertEquals("alpha", target.name);
        assertEquals(18, target.age);
    }

    @Test
    void convertJsonObjectToTargetReturnsDefaultForInvalidJson() {
        Target target = baseData.toTarget("not-json");

        assertNotNull(target);
        assertEquals(0, target.age);
    }

    @Test
    void convertJsonArrayToTargetKeepsElementType() {
        List<Target> targets = baseData.toTargetList("[{\"name\":\"alpha\",\"age\":\"18\"},{\"name\":\"beta\",\"age\":20}]");

        assertEquals(2, targets.size());
        assertEquals("alpha", targets.get(0).name);
        assertEquals(18, targets.get(0).age);
        assertEquals(Target.class, targets.get(0).getClass());
        assertEquals(20, targets.get(1).age);
    }

    @Test
    void convertJsonArrayToTargetConvertsSimpleElementType() {
        List<Integer> values = baseData.toIntegerList("[\"1\",2]");

        assertEquals(List.of(1, 2), values);
    }

    @Test
    void convertJsonArrayToTargetHandlesPrimitiveTargetType() {
        List<Integer> values = baseData.toPrimitiveIntList("[\"1\",2]");

        assertEquals(List.of(1, 2), values);
    }

    @Test
    void convertJsonArrayToTargetReturnsEmptyListForInvalidJson() {
        assertTrue(baseData.toTargetList("{}").isEmpty());
        assertTrue(baseData.toTargetList(null).isEmpty());
    }

    @Test
    void convertJsonMethodsHandleNullTargetType() {
        assertNull(baseData.toNullTarget("{\"name\":\"alpha\"}"));
        assertTrue(baseData.toNullTargetList("[{\"name\":\"alpha\"}]").isEmpty());
    }

    static class TestBaseData extends GXBaseData {
        Target toTarget(String json) {
            return convertJsonObjectToTarget(json, Target.class);
        }

        List<Target> toTargetList(String json) {
            return convertJsonArrayToTarget(json, Target.class);
        }

        List<Integer> toIntegerList(String json) {
            return convertJsonArrayToTarget(json, Integer.class);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Integer> toPrimitiveIntList(String json) {
            return convertJsonArrayToTarget(json, (Class) int.class);
        }

        Object toNullTarget(String json) {
            return convertJsonObjectToTarget(json, null);
        }

        List<Object> toNullTargetList(String json) {
            return convertJsonArrayToTarget(json, null);
        }
    }

    public static class Target {
        public String name;
        public int age;
    }
}
