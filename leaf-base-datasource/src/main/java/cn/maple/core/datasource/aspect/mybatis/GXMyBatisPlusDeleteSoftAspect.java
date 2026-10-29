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
public class GXMyBatisPlusDeleteSoftAspect {
    private final GXMyBatisListenerEventPublisher eventPublisher;

    public GXMyBatisPlusDeleteSoftAspect(GXMyBatisListenerEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* deleteSoftCondition(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object proceed = point.proceed();
        if (!GXMyBatisListenerAspectSupport.isSuccessfulResult(proceed)) {
            return proceed;
        }
        GXMyBatisListenerAspectSupport.publishIfDeclared(eventPublisher, point,
                GXModelEventNamingEnums.SYNC_DELETE_SOFT, GXModelEventNamingEnums.ASYNC_DELETE_SOFT,
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
        Dict conditionFieldData = Dict.create();
        List<GXCondition<?>> conditionList = baseQueryParam.getCondition();
        conditionList.forEach(condition -> {
            if (condition == null) {
                return;
            }
            if (condition instanceof GXConditionRaw rawCondition) {
                addRawCondition(rawConditions, degradeReasons, rawCondition);
                return;
            }
            String fieldExpression = condition.getFieldExpression();
            if (CharSequenceUtil.isBlank(fieldExpression)) {
                return;
            }
            conditionFieldData.set(CharSequenceUtil.toCamelCase(fieldExpression), condition.getFieldValue());
        });

        Dict updateFieldData = Dict.create();
        updateFieldList.forEach(updateField -> {
                    if (updateField == null) {
                        return;
                    }
                    String fieldName = updateField.getFieldName();
                    // Same guard as the update-field payload: an entry without a column name must not
                    // inject a null key into the payload listeners read (contracts/event-payload.md).
                    if (CharSequenceUtil.isNotEmpty(fieldName)) {
                        updateFieldData.set(CharSequenceUtil.toCamelCase(fieldName), updateField.getFieldValue());
                    }
                }
        );

        return Dict.create()
                .set("conditionFieldData", conditionFieldData)
                .set("updateFieldData", updateFieldData)
                .set("conditionLossless", degradeReasons.isEmpty())
                .set("degradeReasons", List.copyOf(degradeReasons))
                .set("rawConditionSql", String.join(" AND ", rawConditions));
    }

    private void addRawCondition(List<String> rawConditions,
                                 List<GXMyBatisListenerDegradeReasonEnums> degradeReasons,
                                 GXConditionRaw rawCondition) {
        try {
            rawConditions.add(rawCondition.whereString());
        } catch (RuntimeException exception) {
            // A skipped raw condition means the payload is no longer lossless; never claim
            // otherwise just because the structured fields look complete (FR-009/FR-010).
            log.warn("Raw condition skipped from listener payload: {}", exception.getMessage());
            degradeReasons.add(GXMyBatisListenerDegradeReasonEnums.PARSE_ERROR);
        }
    }

}
