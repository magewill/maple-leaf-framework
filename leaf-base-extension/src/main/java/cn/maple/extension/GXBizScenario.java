package cn.maple.extension;

import lombok.Getter;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;
import java.util.Objects;

/**
 * Business scenario identifier: bizId + useCase + scenario.
 */
@Getter
public final class GXBizScenario implements Serializable {
    public static final String DEFAULT_BIZ_ID = "#defaultBizId#";
    public static final String DEFAULT_USE_CASE = "#defaultUseCase#";
    public static final String DEFAULT_SCENARIO = "#defaultScenario#";

    private static final String DOT_SEPARATOR = ".";
    private static final char DOT = '.';

    private final String bizId;
    private final String useCase;
    private final String scenario;
    private final String uniqueIdentity;
    private final String identityWithDefaultScenario;
    private final String identityWithDefaultUseCase;

    private GXBizScenario(String bizId, String useCase, String scenario) {
        this.bizId = requireText(bizId, "bizId");
        this.useCase = requireText(useCase, "useCase");
        this.scenario = requireText(scenario, "scenario");
        this.uniqueIdentity = this.bizId + DOT_SEPARATOR + this.useCase + DOT_SEPARATOR + this.scenario;
        this.identityWithDefaultScenario = this.bizId + DOT_SEPARATOR + this.useCase + DOT_SEPARATOR + DEFAULT_SCENARIO;
        this.identityWithDefaultUseCase = this.bizId + DOT_SEPARATOR + DEFAULT_USE_CASE + DOT_SEPARATOR + DEFAULT_SCENARIO;
    }

    public static GXBizScenario valueOf(String bizId, String useCase, String scenario) {
        return new GXBizScenario(bizId, useCase, scenario);
    }

    public static GXBizScenario valueOf(String bizId, String useCase) {
        return GXBizScenario.valueOf(bizId, useCase, DEFAULT_SCENARIO);
    }

    public static GXBizScenario valueOf(String bizId) {
        return GXBizScenario.valueOf(bizId, DEFAULT_USE_CASE, DEFAULT_SCENARIO);
    }

    public static GXBizScenario newDefault() {
        return GXBizScenario.valueOf(DEFAULT_BIZ_ID, DEFAULT_USE_CASE, DEFAULT_SCENARIO);
    }

    /**
     * Restores a scenario from its {@link #getUniqueIdentity()} form.
     *
     * @param uniqueIdentity three dot separated segments: bizId.useCase.scenario
     * @return equivalent scenario instance
     * @throws IllegalArgumentException when the identity is not exactly three non-blank segments
     */
    @NullMarked
    public static GXBizScenario fromUniqueIdentity(String uniqueIdentity) {
        Objects.requireNonNull(uniqueIdentity, "uniqueIdentity cannot be null");
        int firstDot = uniqueIdentity.indexOf(DOT);
        int secondDot = firstDot < 0 ? -1 : uniqueIdentity.indexOf(DOT, firstDot + 1);
        validateSegments(uniqueIdentity, firstDot, secondDot);
        return GXBizScenario.valueOf(uniqueIdentity.substring(0, firstDot), uniqueIdentity.substring(firstDot + 1, secondDot),
                uniqueIdentity.substring(secondDot + 1));
    }

    /**
     * Validates an identity without building a scenario instance; used by coordinate construction.
     * <p>
     * Produces the same failure messages as {@link #fromUniqueIdentity(String)}.
     *
     * @param uniqueIdentity three dot separated segments: bizId.useCase.scenario
     * @throws IllegalArgumentException when the identity is not exactly three non-blank segments
     */
    @NullMarked
    static void validateUniqueIdentity(String uniqueIdentity) {
        Objects.requireNonNull(uniqueIdentity, "uniqueIdentity cannot be null");
        int firstDot = uniqueIdentity.indexOf(DOT);
        int secondDot = firstDot < 0 ? -1 : uniqueIdentity.indexOf(DOT, firstDot + 1);
        validateSegments(uniqueIdentity, firstDot, secondDot);
    }

    private static void validateSegments(String uniqueIdentity, int firstDot, int secondDot) {
        if (firstDot < 0 || secondDot < 0 || uniqueIdentity.indexOf(DOT, secondDot + 1) >= 0) {
            int segmentCount = 1;
            for (int index = 0; index < uniqueIdentity.length(); index++) {
                if (uniqueIdentity.charAt(index) == DOT) {
                    segmentCount++;
                }
            }
            throw new IllegalArgumentException("Biz scenario unique identity must contain 3 segments separated by '.', actual="
                    + segmentCount + ", value=" + uniqueIdentity);
        }
        if (firstDot == 0) {
            throw new IllegalArgumentException("Biz scenario segment[1] cannot be blank, value=" + uniqueIdentity);
        }
        if (secondDot == firstDot + 1) {
            throw new IllegalArgumentException("Biz scenario segment[2] cannot be blank, value=" + uniqueIdentity);
        }
        if (secondDot == uniqueIdentity.length() - 1) {
            throw new IllegalArgumentException("Biz scenario segment[3] cannot be blank, value=" + uniqueIdentity);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        if (value.contains(DOT_SEPARATOR)) {
            throw new IllegalArgumentException(fieldName + " cannot contain '.'");
        }
        return value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bizId, useCase, scenario);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof GXBizScenario other)) {
            return false;
        }
        return Objects.equals(bizId, other.bizId)
                && Objects.equals(useCase, other.useCase)
                && Objects.equals(scenario, other.scenario);
    }

    @Override
    public String toString() {
        return getUniqueIdentity();
    }
}
