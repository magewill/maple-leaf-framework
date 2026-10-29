package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerAspectSupport;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
@Slf4j
public class GXMyBatisPlusUpdateFieldAspect {
    private final GXMyBatisListenerEventPublisher eventPublisher;

    public GXMyBatisPlusUpdateFieldAspect(GXMyBatisListenerEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* updateFieldByCondition(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object proceed = point.proceed();
        if (!GXMyBatisListenerAspectSupport.isSuccessfulResult(proceed)) {
            return proceed;
        }
        GXMyBatisListenerAspectSupport.publishIfDeclared(eventPublisher, point,
                GXModelEventNamingEnums.SYNC_UPDATE_FIELD, GXModelEventNamingEnums.ASYNC_UPDATE_FIELD,
                () -> handlePointArgs(point));
        return proceed;
    }

    private Dict handlePointArgs(ProceedingJoinPoint point) {
        Object[] args = point.getArgs();
        if (ObjectUtil.isEmpty(args) || args.length < 2 || ObjectUtil.isNull(args[0]) || ObjectUtil.isNull(args[1])) {
            return Dict.create();
        }

        GXBaseQueryParamInnerDto baseQueryParam = Convert.convert(new TypeReference<>() {
        }, args[0]);
        List<GXUpdateField<?>> updateFieldList = Convert.convert(new TypeReference<>() {
        }, args[1]);
        if (ObjectUtil.isNull(baseQueryParam) || ObjectUtil.isNull(baseQueryParam.getCondition()) || ObjectUtil.isNull(updateFieldList)) {
            return Dict.create();
        }

        List<String> rawConditions = new ArrayList<>();
        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons = new ArrayList<>();
        Dict conditionFieldData = buildConditionFieldData(baseQueryParam.getCondition(), rawConditions, degradeReasons);

        return Dict.create()
                .set("updateFieldData", buildUpdateFieldData(updateFieldList))
                .set("conditionFieldData", conditionFieldData)
                .set("conditionLossless", degradeReasons.isEmpty())
                .set("degradeReasons", List.copyOf(degradeReasons))
                .set("rawConditionSql", String.join(" AND ", rawConditions));
    }

    private Dict buildUpdateFieldData(List<GXUpdateField<?>> updateFieldList) {
        Dict updateFieldData = Dict.create();
        updateFieldList.forEach(field -> {
            if (field == null) {
                return;
            }
            String fieldName = field.getFieldName();
            if (CharSequenceUtil.isNotEmpty(fieldName)) {
                updateFieldData.set(CharSequenceUtil.toCamelCase(fieldName), field.getFieldValue());
            }
        });
        return updateFieldData;
    }

    /**
     * Collects the structured condition fields and the raw SQL pieces. A raw condition that cannot be
     * rendered is recorded as a {@code PARSE_ERROR} degrade reason instead of being dropped silently
     * (FR-009/FR-010).
     */
    private Dict buildConditionFieldData(List<GXCondition<?>> conditionList, List<String> rawConditions,
                                         List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
        Dict conditionFieldData = Dict.create();
        conditionList.forEach(condition -> {
            if (condition == null) {
                return;
            }
            if (condition instanceof GXConditionRaw rawCondition) {
                try {
                    rawConditions.add(rawCondition.whereString());
                } catch (RuntimeException exception) {
                    log.warn("Raw condition skipped from listener payload: {}", exception.getMessage());
                    degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR);
                }
                return;
            }
            String fieldExpression = condition.getFieldExpression();
            if (CharSequenceUtil.isBlank(fieldExpression)) {
                return;
            }
            conditionFieldData.set(CharSequenceUtil.toCamelCase(fieldExpression), condition.getFieldValue());
        });
        return conditionFieldData;
    }
}
