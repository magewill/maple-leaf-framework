package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusUpdateFieldAspect {
    private final GXMyBatisListenerEventPublisher eventPublisher;

    public GXMyBatisPlusUpdateFieldAspect(GXMyBatisListenerEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* updateFieldByCondition(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object proceed = point.proceed();
        if (!isSuccessfulResult(proceed)) {
            return proceed;
        }
        publishEvent(point);
        return proceed;
    }

    private boolean isSuccessfulResult(Object result) {
        if (ObjectUtil.isNull(result)) {
            return false;
        }
        if (result instanceof Boolean boolResult) {
            return boolResult;
        }
        if (result instanceof Number numResult) {
            return numResult.longValue() > 0L;
        }
        return true;
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

        List<String> rawConditions = new ArrayList<>();
        Dict conditionFieldData = Dict.create();
        List<GXCondition<?>> conditionList = baseQueryParam.getCondition();
        conditionList.forEach(condition -> {
            if (condition == null) {
                return;
            }
            if (condition instanceof GXConditionRaw rawCondition) {
                try {
                    rawConditions.add(rawCondition.whereString());
                } catch (RuntimeException exception) {
                    log.warn("Raw condition skipped from listener payload: {}", exception.getMessage());
                }
                return;
            }
            String fieldExpression = condition.getFieldExpression();
            if (CharSequenceUtil.isBlank(fieldExpression)) {
                return;
            }
            conditionFieldData.set(CharSequenceUtil.toCamelCase(fieldExpression), condition.getFieldValue());
        });

        return Dict.create()
                .set("updateFieldData", updateFieldData)
                .set("conditionFieldData", conditionFieldData)
                .set("conditionLossless", Boolean.TRUE)
                .set("degradeReasons", List.of())
                .set("rawConditionSql", String.join(" AND ", rawConditions));
    }

    private void publishEvent(ProceedingJoinPoint point) {
        Method invokedMethod = ((MethodSignature) point.getSignature()).getMethod();
        if (!eventPublisher.hasListener(point.getTarget(), invokedMethod)) {
            return;
        }
        Dict source = handlePointArgs(point);
        if (ObjectUtil.isEmpty(source)) {
            return;
        }
        eventPublisher.publish(point.getTarget(), invokedMethod,
                GXModelEventNamingEnums.SYNC_UPDATE_FIELD, GXModelEventNamingEnums.ASYNC_UPDATE_FIELD, source);
    }
}
