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
import cn.maple.core.datasource.event.GXMyBatisModelUpdateFieldEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import lombok.extern.slf4j.Slf4j;
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
public class GXMyBatisPlusUpdateFieldAspect {
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
            String fieldName = field.getFieldName();
            if (CharSequenceUtil.isNotEmpty(fieldName)) {
                updateFieldData.set(CharSequenceUtil.toCamelCase(fieldName), field.getFieldValue());
            }
        });

        List<GXCondition<?>> conditionList = baseQueryParam.getCondition();
        Dict conditionFieldData = Dict.create();
        conditionList.forEach(condition -> {
            if (condition instanceof GXConditionRaw) {
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
                .set("conditionFieldData", conditionFieldData);
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
            String eventType = GXModelEventNamingEnums.SYNC_UPDATE_FIELD.getEventType();
            String eventName = GXModelEventNamingEnums.SYNC_UPDATE_FIELD.getEventName();
            if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                eventType = GXModelEventNamingEnums.ASYNC_UPDATE_FIELD.getEventType();
                eventName = GXModelEventNamingEnums.ASYNC_UPDATE_FIELD.getEventName();
            }

            Dict eventParam = Dict.create()
                    .set("listenerClazzName", listenerClass.getSimpleName())
                    .set("listenerClazz", listenerClass);
            GXMyBatisModelUpdateFieldEvent<Dict> event = new GXMyBatisModelUpdateFieldEvent<>(source, eventType, eventParam, eventName);
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
        return AnnotationUtil.getAnnotation(mapperClass, GXMyBatisListener.class);
    }

    private Method findMethod(Class<?> mapperClass, Method invokedMethod) {
        try {
            return mapperClass.getMethod(invokedMethod.getName(), invokedMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Class<?> convertTypeToClass(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }
}
