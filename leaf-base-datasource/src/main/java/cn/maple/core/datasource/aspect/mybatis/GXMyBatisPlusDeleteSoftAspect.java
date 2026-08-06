package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelDeleteSoftEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.datasource.util.GXMyBatisListenerAnnotationUtils;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusDeleteSoftAspect {
    @Around("target(cn.maple.core.datasource.mapper.GXBaseMapper) && execution(* deleteSoftCondition(..))")
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

        Dict conditionFieldData = Dict.create();
        List<GXCondition<?>> conditionList = baseQueryParam.getCondition();
        conditionList.forEach(condition -> {
            if (condition == null || condition instanceof GXConditionRaw) {
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
                    updateFieldData.set(CharSequenceUtil.toCamelCase(updateField.getFieldName()), updateField.getFieldValue());
                }
        );

        return Dict.create()
                .set("conditionFieldData", conditionFieldData)
                .set("updateFieldData", updateFieldData);
    }

    private void publishEvent(ProceedingJoinPoint point) {
        if (ObjectUtil.isNull(point) || ObjectUtil.isNull(point.getTarget())) {
            return;
        }

        Type[] mapperTypes = AopUtils.getTargetClass(point.getTarget()).getInterfaces();
        Method invokedMethod = ((MethodSignature) point.getSignature()).getMethod();
        if (ObjectUtil.isEmpty(mapperTypes)) {
            return;
        }

        Dict source = handlePointArgs(point);
        if (ObjectUtil.isEmpty(source)) {
            return;
        }

        for (Type type : mapperTypes) {
            Class<?> mapperClass = convertTypeToClass(type);
            if (ObjectUtil.isNull(mapperClass)) {
                continue;
            }

            GXMyBatisListener listenerConfig = resolveListenerConfig(mapperClass, invokedMethod);
            if (ObjectUtil.isNull(listenerConfig)) {
                continue;
            }

            Class<? extends GXMybatisListenerService> listenerClass = listenerConfig.listenerClazz();
            String eventType = GXModelEventNamingEnums.SYNC_DELETE_SOFT.getEventType();
            String eventName = GXModelEventNamingEnums.SYNC_DELETE_SOFT.getEventName();
            if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                eventType = GXModelEventNamingEnums.ASYNC_DELETE_SOFT.getEventType();
                eventName = GXModelEventNamingEnums.ASYNC_DELETE_SOFT.getEventName();
            }

            Dict eventParam = Dict.create()
                    .set("listenerClazzName", listenerClass.getSimpleName())
                    .set("listenerClazz", listenerClass);
            GXMyBatisModelDeleteSoftEvent<Dict> event = new GXMyBatisModelDeleteSoftEvent<>(source, eventType, eventParam, eventName);
            if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                GXEventPublisherUtils.publishEventAfterCommit(event);
            } else {
                GXEventPublisherUtils.publishEvent(event);
            }
        }
    }

    private GXMyBatisListener resolveListenerConfig(Class<?> mapperClass, Method invokedMethod) {
        Method mapperMethod = findMethod(mapperClass, invokedMethod);
        if (ObjectUtil.isNotNull(mapperMethod)) {
            GXMyBatisListener methodAnno = AnnotationUtil.getAnnotation(mapperMethod, GXMyBatisListener.class);
            if (ObjectUtil.isNotNull(methodAnno)) {
                return methodAnno;
            }
        }
        return GXMyBatisListenerAnnotationUtils.findTypeAnnotation(mapperClass);
    }

    private Method findMethod(Class<?> mapperClass, Method invokedMethod) {
        try {
            return mapperClass.getMethod(invokedMethod.getName(), invokedMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Class<Mapper> convertTypeToClass(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }
}
