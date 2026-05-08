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

    @Test
    void rejectsInvalidClassCoordinateTypes() {
        GXBizScenario bizScenario = GXBizScenario.valueOf("biz");

        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf(String.class, bizScenario));
        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf(NotExtensionPoint.class, bizScenario));
        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf(GXExtensionPoint.class, bizScenario));
    }

    @Test
    void hashCodeIsStableAndMatchesStringCoordinate() {
        GXBizScenario bizScenario = GXBizScenario.valueOf("biz", "useCase", "scenario");
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(CoordinateTestExtPoint.class, bizScenario);
        GXExtensionCoordinate sameCoordinate = GXExtensionCoordinate.valueOf(
                CoordinateTestExtPoint.class.getName(), bizScenario.getUniqueIdentity());

        assertEquals(coordinate.hashCode(), coordinate.hashCode());
        assertEquals(coordinate.hashCode(), sameCoordinate.hashCode());
    }

    private interface CoordinateTestExtPoint extends GXExtensionPoint {
    }

    private interface NotExtensionPoint {
    }
}
