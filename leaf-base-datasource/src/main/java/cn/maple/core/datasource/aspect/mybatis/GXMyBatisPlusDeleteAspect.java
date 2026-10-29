package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerAspectSupport;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerPayloadUtils;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
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
        Dict payload = Dict.create()
                .set(GXMyBatisEventConstant.OPERATION, GXMyBatisListenerAspectSupport.invokedMethod(point).getName());
        payload.putAll(GXMyBatisListenerPayloadUtils.conditionPayload(queryParam.getCondition()));
        return payload;
    }
}
