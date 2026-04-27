package cn.maple.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
