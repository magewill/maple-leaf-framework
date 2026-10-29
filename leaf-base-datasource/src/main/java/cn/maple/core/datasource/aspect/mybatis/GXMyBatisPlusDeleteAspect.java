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
        if (!GXMyBatisListenerAspectSupport.isSuccessfulResult(result)) {
            return result;
        }
        GXMyBatisListenerAspectSupport.publishIfDeclared(eventPublisher, point,
                GXModelEventNamingEnums.SYNC_DELETE, GXModelEventNamingEnums.ASYNC_DELETE,
                () -> handlePointArgs(point));
        return result;
    }

    /**
     * Builds the payload for the only intercepted operation. Branches for {@code deleteByMap},
     * {@code deleteByIds} and primary-key deletes were unreachable: this aspect matches
     * {@code deleteCondition} only, and primary-key deletion deliberately produces no event
     * (FR-012).
     */
    private Dict handlePointArgs(ProceedingJoinPoint point) {
        Object[] args = point.getArgs();
        if (ObjectUtil.isEmpty(args) || ObjectUtil.isNull(args[0])) {
            return Dict.create();
        }
        GXBaseQueryParamInnerDto queryParam = Convert.convert(new TypeReference<>() {
        }, args[0]);
        if (ObjectUtil.isNull(queryParam) || ObjectUtil.isEmpty(queryParam.getCondition())) {
            return Dict.create();
        }
        List<String> rawConditions = new ArrayList<>();
        List<GXMyBatisListenerDegradeReasonEnums> degradeReasons = new ArrayList<>();
        Dict conditionFieldData = toConditionFieldData(queryParam.getCondition(), rawConditions, degradeReasons);
        return Dict.create()
                .set("operation", GXMyBatisListenerAspectSupport.invokedMethod(point).getName())
                .set("conditionFieldData", conditionFieldData)
                .set("conditionLossless", degradeReasons.isEmpty())
                .set("degradeReasons", List.copyOf(degradeReasons))
                .set("rawConditionSql", String.join(" AND ", rawConditions));
    }

    private Dict toConditionFieldData(List<GXCondition<?>> conditions, List<String> rawConditions,
                                      List<GXMyBatisListenerDegradeReasonEnums> degradeReasons) {
        Dict conditionFieldData = Dict.create();
        conditions.forEach(condition -> {
            if (condition == null) {
                return;
            }
            if (condition instanceof GXConditionRaw rawCondition) {
                try {
                    rawConditions.add(rawCondition.whereString());
                } catch (RuntimeException exception) {
                    // A skipped raw condition means the payload is no longer lossless; never claim
                    // otherwise just because the structured fields look complete (FR-009/FR-010).
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
