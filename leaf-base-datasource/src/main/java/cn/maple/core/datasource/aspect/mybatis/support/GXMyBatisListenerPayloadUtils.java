package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Builders for the payload fragments shared by the delete, soft-delete and update-field aspects.
 *
 * <p>Those writes render conditions and update fields the same way. Keeping the rendering here stops
 * the degrade rules ({@code conditionLossless}, {@code degradeReasons}, {@code rawConditionSql}) from
 * drifting between aspects (FR-009/FR-010). The value probes are shared with the strict-snapshot scan
 * of {@link GXMyBatisListenerEventPublisher}, so "unsnapshotable" means the same in both.</p>
 */
@Slf4j
public final class GXMyBatisListenerPayloadUtils {
    private GXMyBatisListenerPayloadUtils() {
    }

    /**
     * Renders a condition list into the structured condition fields plus the raw SQL fallback.
     *
     * <p>A raw condition that cannot be rendered is recorded as a {@code PARSE_ERROR} degrade reason
     * instead of being dropped silently, because a skipped condition makes the payload lossy
     * (FR-009/FR-010).</p>
     */
    public static Dict conditionPayload(@Nullable List<GXCondition<?>> conditions) {
        Dict conditionFieldData = Dict.create();
        List<String> rawConditions = new ArrayList<>();
        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons = new ArrayList<>();
        for (GXCondition<?> condition : CollUtil.emptyIfNull(conditions)) {
            if (ObjectUtil.isNull(condition)) {
                continue;
            }
            if (condition instanceof GXConditionRaw rawCondition) {
                appendRawCondition(rawCondition, rawConditions, degradeReasons);
                continue;
            }
            String fieldExpression = condition.getFieldExpression();
            if (CharSequenceUtil.isNotBlank(fieldExpression)) {
                conditionFieldData.set(CharSequenceUtil.toCamelCase(fieldExpression), condition.getFieldValue());
            }
        }
        return Dict.create()
                .set(GXMyBatisEventConstant.CONDITION_FIELD_DATA, conditionFieldData)
                .set(GXMyBatisEventConstant.CONDITION_LOSSLESS, degradeReasons.isEmpty())
                .set(GXMyBatisEventConstant.DEGRADE_REASONS, List.copyOf(degradeReasons))
                .set(GXMyBatisEventConstant.RAW_CONDITION_SQL, String.join(" AND ", rawConditions));
    }

    /**
     * Renders update fields keyed by their camel-cased column name.
     *
     * <p>An entry without a column name must not inject a {@code null} key into the payload listeners
     * read (contracts/event-payload.md).</p>
     */
    public static Dict updateFieldPayload(@Nullable List<GXUpdateField<?>> updateFields) {
        Dict updateFieldData = Dict.create();
        for (GXUpdateField<?> updateField : CollUtil.emptyIfNull(updateFields)) {
            if (ObjectUtil.isNull(updateField)) {
                continue;
            }
            String fieldName = updateField.getFieldName();
            if (CharSequenceUtil.isNotEmpty(fieldName)) {
                updateFieldData.set(CharSequenceUtil.toCamelCase(fieldName), updateField.getFieldValue());
            }
        }
        return updateFieldData;
    }

    /**
     * Builds the payload shared by the update-field and soft-delete aspects, which carry the same two
     * arguments ({@code GXBaseQueryParamInnerDto} and the update-field list).
     *
     * <p>Keeping the argument handling here stops the two aspects from drifting apart in what they
     * publish for the same write shape (FR-009/FR-010).</p>
     */
    public static Dict updateFieldConditionPayload(@Nullable Object queryParamArg, @Nullable Object updateFieldArg) {
        if (ObjectUtil.isNull(queryParamArg) || ObjectUtil.isNull(updateFieldArg)) {
            return Dict.create();
        }
        GXBaseQueryParamInnerDto queryParam = Convert.convert(new TypeReference<>() {
        }, queryParamArg);
        List<GXUpdateField<?>> updateFields = Convert.convert(new TypeReference<>() {
        }, updateFieldArg);
        if (ObjectUtil.isNull(queryParam) || ObjectUtil.isNull(queryParam.getCondition()) || ObjectUtil.isNull(updateFields)) {
            return Dict.create();
        }
        Dict payload = Dict.create()
                .set(GXMyBatisEventConstant.UPDATE_FIELD_DATA, updateFieldPayload(updateFields));
        payload.putAll(conditionPayload(queryParam.getCondition()));
        return payload;
    }

    /**
     * Whether a payload value cannot be defensively copied, so keeping it retains a reference to
     * caller-owned mutable state. Immutable leaves are never reported.
     */
    public static boolean isUnsnapshotable(@Nullable Object value) {
        if (ObjectUtil.isNull(value)) {
            return false;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof UUID
                || value instanceof Temporal || value instanceof Date || value instanceof Calendar) {
            return false;
        }
        try {
            return ObjectUtil.cloneIfPossible(value) == value;
        } catch (RuntimeException exception) {
            log.debug("Snapshot probe failed for value type {}; treating it as unsnapshotable",
                    value.getClass().getName(), exception);
            return true;
        }
    }

    private static void appendRawCondition(GXConditionRaw rawCondition, List<String> rawConditions,
                                          List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
        try {
            rawConditions.add(rawCondition.whereString());
        } catch (RuntimeException exception) {
            log.warn("Raw condition skipped from listener payload: {}", exception.getMessage());
            degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR);
        }
    }
}
