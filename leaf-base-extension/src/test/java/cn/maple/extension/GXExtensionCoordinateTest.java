package cn.maple.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void rejectsStringCoordinateWithInvalidIdentityFormat() {
        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf("ext", "biz.useCase"));
        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf("ext", "biz.useCase.scenario.extra"));
        assertThrows(IllegalArgumentException.class, () -> GXExtensionCoordinate.valueOf("ext", "biz..scenario"));
    }

    @Test
    void invalidIdentityDiagnosticsMatchScenarioFactory() {
        IllegalArgumentException fromScenario = assertThrows(IllegalArgumentException.class,
                () -> GXBizScenario.fromUniqueIdentity("biz..scenario"));
        IllegalArgumentException fromCoordinate = assertThrows(IllegalArgumentException.class,
                () -> GXExtensionCoordinate.valueOf("ext", "biz..scenario"));

        assertEquals(fromScenario.getMessage(), fromCoordinate.getMessage());

        IllegalArgumentException wrongSegments = assertThrows(IllegalArgumentException.class,
                () -> GXExtensionCoordinate.valueOf("ext", "biz.useCase"));
        assertTrue(wrongSegments.getMessage().contains("actual=2"));
    }

    @Test
    void stringCoordinateExposesNullableTypeAndScenario() {
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf("ext", "biz.useCase.scenario");

        assertNull(coordinate.getExtensionPointClass());
        assertNull(coordinate.getBizScenario());
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
