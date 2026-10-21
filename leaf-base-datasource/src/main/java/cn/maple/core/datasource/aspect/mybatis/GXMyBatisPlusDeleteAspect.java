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
public class GXMyBatisPlusDeleteAspect {
    private final GXMyBatisListenerEventPublisher eventPublisher;

    public GXMyBatisPlusDeleteAspect(GXMyBatisListenerEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Intercepts only GXBaseMapper.deleteCondition, whose provider emits SQL DELETE.
     * BaseMapper delete methods can be configured as logical deletes and must not be
     * reported as physical deletion events.
     */
    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* deleteCondition(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        if (!isSuccessfulResult(result)) {
            return result;
        }
        publishEvent(point);
        return result;
    }

    private boolean isSuccessfulResult(Object result) {
        if (ObjectUtil.isNull(result)) {
            return false;
        }
        if (result instanceof Boolean booleanResult) {
            return booleanResult;
        }
        if (result instanceof Number numberResult) {
            return numberResult.longValue() > 0L;
        }
        return true;
    }

    private Dict handlePointArgs(ProceedingJoinPoint point) {
        Object[] args = point.getArgs();
        if (ObjectUtil.isEmpty(args) || ObjectUtil.isNull(args[0])) {
            return Dict.create();
        }
        String operation = ((MethodSignature) point.getSignature()).getName();
        Dict source = Dict.create().set("operation", operation);
        if (CharSequenceUtil.equals("deleteCondition", operation)) {
            GXBaseQueryParamInnerDto queryParam = Convert.convert(new TypeReference<>() {
            }, args[0]);
            if (ObjectUtil.isNull(queryParam) || ObjectUtil.isEmpty(queryParam.getCondition())) {
                return Dict.create();
            }
            List<String> rawConditions = new ArrayList<>();
            Dict conditionFieldData = toConditionFieldData(queryParam.getCondition(), rawConditions);
            return source
                    .set("conditionFieldData", conditionFieldData)
                    .set("conditionLossless", Boolean.TRUE)
                    .set("degradeReasons", List.of())
                    .set("rawConditionSql", String.join(" AND ", rawConditions));
        }
        if (CharSequenceUtil.equals("deleteByMap", operation)) {
            return source
                    .set("conditionFieldData", Convert.convert(Dict.class, args[0]))
                    .set("conditionLossless", Boolean.TRUE)
                    .set("degradeReasons", List.of())
                    .set("rawConditionSql", "");
        }
        if (CharSequenceUtil.equals("deleteByIds", operation)) {
            return source.set("ids", args[0]);
        }
        return source.set("id", args[0]);
    }

    private Dict toConditionFieldData(List<GXCondition<?>> conditions, List<String> rawConditions) {
        Dict conditionFieldData = Dict.create();
        conditions.forEach(condition -> {
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
        return conditionFieldData;
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
                GXModelEventNamingEnums.SYNC_DELETE, GXModelEventNamingEnums.ASYNC_DELETE, source);
    }
}
