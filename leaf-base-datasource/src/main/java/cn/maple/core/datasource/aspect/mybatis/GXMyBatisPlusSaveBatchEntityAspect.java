package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.datasource.event.GXMyBatisModelSaveBatchEntityEvent;
import cn.maple.core.datasource.service.GXMybatisListenerService;
import cn.maple.core.datasource.util.GXMyBatisListenerAnnotationUtils;
import cn.maple.core.framework.util.GXCommonUtils;
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
import java.util.Collection;
import java.util.List;

@Aspect
@Component
@Slf4j
@SuppressWarnings("all")
public class GXMyBatisPlusSaveBatchEntityAspect {
    @Around("target(com.baomidou.mybatisplus.spring.service.impl.ServiceImpl) && (execution(* saveBatch(..)) || execution(* saveOrUpdateBatch(..)))")
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
        if (ObjectUtil.isEmpty(args) || ObjectUtil.isEmpty(args[0])) {
            return Dict.create();
        }
        Collection<Object> entities = Convert.convert(new TypeReference<>() {
        }, args[0]);
        Collection<Dict> entityData = Convert.convert(new TypeReference<List<Dict>>() {
        }, entities);
        return Dict.create()
                .set("operation", ((MethodSignature) point.getSignature()).getName())
                .set("entityData", entityData);
    }

    private void publishEvent(ProceedingJoinPoint point) {
        if (ObjectUtil.isNull(point) || ObjectUtil.isNull(point.getTarget())) {
            return;
        }

        Type mapperType = GXCommonUtils.getGenericClassType(AopUtils.getTargetClass(point.getTarget()), 0);
        if (ObjectUtil.isNull(mapperType)) {
            return;
        }

        Class<?> mapperClass = convertTypeToClass(mapperType);
        if (ObjectUtil.isNull(mapperClass)) {
            return;
        }

        Method invokedMethod = ((MethodSignature) point.getSignature()).getMethod();
        Class<?> targetClass = AopUtils.getTargetClass(point.getTarget());
        GXMyBatisListener listenerConfig = resolveListenerConfig(targetClass, invokedMethod, mapperClass);
        if (ObjectUtil.isNull(listenerConfig)) {
            return;
        }

        Dict source = handlePointArgs(point);
        if (ObjectUtil.isEmpty(source)) {
            return;
        }

        Class<? extends GXMybatisListenerService> listenerClass = listenerConfig.listenerClazz();
        boolean batchChange = CharSequenceUtil.equals("saveOrUpdateBatch", source.getStr("operation"));
        String eventType = batchChange
                ? GXModelEventNamingEnums.SYNC_BATCH_CHANGE.getEventType()
                : GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY.getEventType();
        String eventName = batchChange
                ? GXModelEventNamingEnums.SYNC_BATCH_CHANGE.getEventName()
                : GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY.getEventName();
        if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
            eventType = batchChange
                    ? GXModelEventNamingEnums.ASYNC_BATCH_CHANGE.getEventType()
                    : GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY.getEventType();
            eventName = batchChange
                    ? GXModelEventNamingEnums.ASYNC_BATCH_CHANGE.getEventName()
                    : GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY.getEventName();
        }

        Dict eventParam = Dict.create()
                .set("listenerClazzName", listenerClass.getSimpleName())
                .set("listenerClazz", listenerClass);
        GXMyBatisModelSaveBatchEntityEvent<Dict> event = new GXMyBatisModelSaveBatchEntityEvent<>(source, eventType, eventParam, eventName);
        if (CharSequenceUtil.equals(listenerConfig.runType(), GXMyBatisEventConstant.MYBATIS_ASYNC_EVENT)) {
            GXEventPublisherUtils.publishEventAfterCommit(event);
        } else {
            GXEventPublisherUtils.publishEvent(event);
        }
    }

    private Class<?> convertTypeToClass(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }

    private GXMyBatisListener resolveListenerConfig(Class<?> targetClass, Method invokedMethod, Class<?> mapperClass) {
        GXMyBatisListener annotation = GXMyBatisListenerAnnotationUtils.findMethodAnnotation(targetClass, invokedMethod);
        if (annotation != null) {
            return annotation;
        }
        annotation = GXMyBatisListenerAnnotationUtils.findTypeAnnotation(targetClass);
        return annotation != null ? annotation : GXMyBatisListenerAnnotationUtils.findTypeAnnotation(mapperClass);
    }
}

