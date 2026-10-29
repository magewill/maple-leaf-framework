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
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
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

        Dict payload = Dict.create()
                .set(GXMyBatisEventConstant.UPDATE_FIELD_DATA,
                        GXMyBatisListenerPayloadUtils.updateFieldPayload(updateFieldList));
        payload.putAll(GXMyBatisListenerPayloadUtils.conditionPayload(baseQueryParam.getCondition()));
        return payload;
    }
}
