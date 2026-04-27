package cn.maple.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GXExtensionCoordinateTest {
    @Test
    void classAndStringCoordinatesAreEqualWhenNamesMatch() {
        GXBizScenario bizScenario = GXBizScenario.valueOf("biz", "useCase", "scenario");

        GXExtensionCoordinate classCoordinate = GXExtensionCoordinate.valueOf(CoordinateTestExtPoint.class, bizScenario);
        GXExtensionCoordinate stringCoordinate = GXExtensionCoordinate.valueOf(
                CoordinateTestExtPoint.class.getName(), bizScenario.getUniqueIdentity());

        assertEquals(classCoordinate, stringCoordinate);
        assertEquals(classCoordinate.hashCode(), stringCoordinate.hashCode());
    }

    @Test
    void rejectsBlankCoordinateParts() {
        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf("", "biz.use.scenario"));
        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf("  ", "biz.use.scenario"));
        assertThrows(NullPointerException.class, () -> GXExtensionCoordinate.valueOf(CoordinateTestExtPoint.class, null));
    }

    private interface CoordinateTestExtPoint extends GXExtensionPoint {
    }
}
