package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.framework.util.GXCommonUtils;
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
    private final GXMyBatisListenerEventPublisher eventPublisher;

    public GXMyBatisPlusSaveBatchEntityAspect(GXMyBatisListenerEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

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
        if (ObjectUtil.isEmpty(entities)) {
            return Dict.create();
        }
        Collection<Dict> entityData = Convert.convert(new TypeReference<List<Dict>>() {
        }, entities);
        List<Dict> nonNullEntities = entityData.stream().filter(ObjectUtil::isNotNull).toList();
        if (ObjectUtil.isEmpty(nonNullEntities)) {
            return Dict.create();
        }
        return Dict.create()
                .set("operation", ((MethodSignature) point.getSignature()).getName())
                .set("entityData", nonNullEntities)
                .set("batchEntities", nonNullEntities);
    }

    private void publishEvent(ProceedingJoinPoint point) {
        Class<?> targetClass = AopUtils.getTargetClass(point.getTarget());
        Type mapperType = GXCommonUtils.getGenericClassType(targetClass, 0);
        Class<?> mapperClass = ObjectUtil.isNull(mapperType) ? null : convertTypeToClass(mapperType);

        Method invokedMethod = ((MethodSignature) point.getSignature()).getMethod();
        if (!eventPublisher.hasListener(point.getTarget(), mapperClass, invokedMethod)) {
            return;
        }
        Dict source = handlePointArgs(point);
        if (ObjectUtil.isEmpty(source)) {
            return;
        }
        boolean batchChange = CharSequenceUtil.equals("saveOrUpdateBatch", source.getStr("operation"));
        GXModelEventNamingEnums syncEvent = batchChange
                ? GXModelEventNamingEnums.SYNC_BATCH_CHANGE
                : GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY;
        GXModelEventNamingEnums asyncEvent = batchChange
                ? GXModelEventNamingEnums.ASYNC_BATCH_CHANGE
                : GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY;
        eventPublisher.publish(point.getTarget(), mapperClass, invokedMethod, syncEvent, asyncEvent, source);
    }

    private Class<?> convertTypeToClass(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }
}
