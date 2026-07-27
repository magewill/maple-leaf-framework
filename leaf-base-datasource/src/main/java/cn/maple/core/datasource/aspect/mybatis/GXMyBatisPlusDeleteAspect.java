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
import cn.maple.core.datasource.event.GXMyBatisModelDeleteEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.framework.dto.inner.GXBaseQueryParamInnerDto;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.condition.GXConditionRaw;
import cn.maple.core.framework.util.GXEventPublisherUtils;
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
@SuppressWarnings("all")
public class GXMyBatisPlusDeleteAspect {
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
            return source.set("conditionFieldData", toConditionFieldData(queryParam.getCondition()));
        }
        if (CharSequenceUtil.equals("deleteByMap", operation)) {
            return source.set("conditionFieldData", Convert.convert(Dict.class, args[0]));
        }
        if (CharSequenceUtil.equals("deleteByIds", operation)) {
            return source.set("ids", args[0]);
        }
        return source.set("id", args[0]);
    }

    private Dict toConditionFieldData(List<GXCondition<?>> conditions) {
        Dict conditionFieldData = Dict.create();
        conditions.forEach(condition -> {
            if (condition instanceof GXConditionRaw) {
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
            Class<?> mapperClass = Convert.convert(new TypeReference<>() {
            }, type);
            if (ObjectUtil.isNull(mapperClass)) {
                continue;
            }
            GXMyBatisListener listenerConfig = resolveListenerConfig(mapperClass, invokedMethod);
            if (ObjectUtil.isNull(listenerConfig)) {
                continue;
            }
            Class<? extends GXMybatisListenerService> listenerClass = listenerConfig.listenerClazz();
            String eventType = GXModelEventNamingEnums.SYNC_DELETE.getEventType();
            String eventName = GXModelEventNamingEnums.SYNC_DELETE.getEventName();
            if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                eventType = GXModelEventNamingEnums.ASYNC_DELETE.getEventType();
                eventName = GXModelEventNamingEnums.ASYNC_DELETE.getEventName();
            }
            Dict eventParam = Dict.create()
                    .set("listenerClazzName", listenerClass.getSimpleName())
                    .set("listenerClazz", listenerClass);
            GXMyBatisModelDeleteEvent<Dict> event = new GXMyBatisModelDeleteEvent<>(source, eventType, eventParam, eventName);
            if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
                GXEventPublisherUtils.publishEventAfterCommit(event);
            } else {
                GXEventPublisherUtils.publishEvent(event);
            }
        }
    }

    private GXMyBatisListener resolveListenerConfig(Class<?> mapperClass, Method invokedMethod) {
        try {
            Method mapperMethod = mapperClass.getMethod(invokedMethod.getName(), invokedMethod.getParameterTypes());
            GXMyBatisListener methodAnnotation = AnnotationUtil.getAnnotation(mapperMethod, GXMyBatisListener.class);
            if (ObjectUtil.isNotNull(methodAnnotation)) {
                return methodAnnotation;
            }
        } catch (NoSuchMethodException ignored) {
        }
        return AnnotationUtil.getAnnotation(mapperClass, GXMyBatisListener.class);
    }
}
