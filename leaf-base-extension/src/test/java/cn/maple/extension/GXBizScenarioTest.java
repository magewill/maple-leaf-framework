package cn.maple.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBizScenarioTest {
    @Test
    void buildsExpectedIdentities() {
        GXBizScenario scenario = GXBizScenario.valueOf("biz", "useCase", "scenario");

        assertEquals("biz.useCase.scenario", scenario.getUniqueIdentity());
        assertEquals("biz.useCase.#defaultScenario#", scenario.getIdentityWithDefaultScenario());
        assertEquals("biz.#defaultUseCase#.#defaultScenario#", scenario.getIdentityWithDefaultUseCase());
        assertEquals("#defaultBizId#.#defaultUseCase#.#defaultScenario#", GXBizScenario.newDefault().getUniqueIdentity());
    }

    @Test
    void isAStableValueObject() {
        GXBizScenario first = GXBizScenario.valueOf("biz", "useCase", "scenario");
        GXBizScenario second = GXBizScenario.valueOf("biz", "useCase", "scenario");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals("biz.useCase.scenario", first.toString());
    }

    @Test
    void rejectsBlankParts() {
        assertThrows(IllegalArgumentException.class, () -> GXBizScenario.valueOf(null, "useCase", "scenario"));
        assertThrows(IllegalArgumentException.class, () -> GXBizScenario.valueOf(" ", "useCase", "scenario"));
        assertThrows(IllegalArgumentException.class, () -> GXBizScenario.valueOf("biz", "", "scenario"));
        assertThrows(IllegalArgumentException.class, () -> GXBizScenario.valueOf("biz", "useCase", "  "));
    }

    @Test
    void rejectsPartsContainingIdentitySeparator() {
        assertThrows(IllegalArgumentException.class, () -> GXBizScenario.valueOf("biz.id", "useCase", "scenario"));
        assertThrows(IllegalArgumentException.class, () -> GXBizScenario.valueOf("biz", "use.case", "scenario"));
        assertThrows(IllegalArgumentException.class, () -> GXBizScenario.valueOf("biz", "useCase", "sce.nario"));
    }

    @Test
    void fromUniqueIdentityRestoresEquivalentScenario() {
        GXBizScenario scenario = GXBizScenario.fromUniqueIdentity("biz.useCase.scenario");

        assertEquals(GXBizScenario.valueOf("biz", "useCase", "scenario"), scenario);
        assertEquals(scenario, GXBizScenario.fromUniqueIdentity(scenario.getUniqueIdentity()));
        assertEquals(GXBizScenario.newDefault(), GXBizScenario.fromUniqueIdentity(GXBizScenario.newDefault().getUniqueIdentity()));
    }

    @Test
    void fromUniqueIdentityRejectsWrongSegmentCount() {
        IllegalArgumentException twoSegments = assertThrows(IllegalArgumentException.class,
                () -> GXBizScenario.fromUniqueIdentity("biz.useCase"));
        IllegalArgumentException fourSegments = assertThrows(IllegalArgumentException.class,
                () -> GXBizScenario.fromUniqueIdentity("biz.useCase.scenario.extra"));
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> GXBizScenario.fromUniqueIdentity(""));

        assertTrue(twoSegments.getMessage().contains("actual=2"));
        assertTrue(fourSegments.getMessage().contains("actual=4"));
        assertTrue(empty.getMessage().contains("actual=1"));
    }

    @Test
    void fromUniqueIdentityRejectsBlankSegment() {
        IllegalArgumentException blankMiddle = assertThrows(IllegalArgumentException.class,
                () -> GXBizScenario.fromUniqueIdentity("biz..scenario"));
        IllegalArgumentException blankFirst = assertThrows(IllegalArgumentException.class,
                () -> GXBizScenario.fromUniqueIdentity(".useCase.scenario"));

        assertTrue(blankMiddle.getMessage().contains("segment[2]"));
        assertTrue(blankFirst.getMessage().contains("segment[1]"));
    }
}
