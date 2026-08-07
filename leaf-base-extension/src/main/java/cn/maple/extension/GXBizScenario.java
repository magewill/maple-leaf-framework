package cn.maple.extension;

import lombok.Getter;

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
