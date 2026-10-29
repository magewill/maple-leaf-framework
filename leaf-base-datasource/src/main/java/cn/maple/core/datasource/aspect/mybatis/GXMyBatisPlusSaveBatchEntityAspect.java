package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerAspectSupport;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.framework.util.GXCommonUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.Optional;

@Aspect
@Component
public class GXMyBatisPlusSaveBatchEntityAspect {
    /**
     * Resolving the mapper type walks the generic hierarchy of the proxied service, which is too
     * expensive to repeat on every batch write (FR-018/SC-003). The cache is bounded and keyed by
     * the target class whose generic signature is immutable at runtime.
     */
    private static final Cache<Class<?>, Optional<Class<?>>> MAPPER_CLASS_CACHE =
            Caffeine.newBuilder().maximumSize(2_048L).build();

    private final GXMyBatisListenerEventPublisher eventPublisher;

    public GXMyBatisPlusSaveBatchEntityAspect(GXMyBatisListenerEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("target(com.baomidou.mybatisplus.spring.service.impl.ServiceImpl) && (execution(* saveBatch(..)) || execution(* saveOrUpdateBatch(..)))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object proceed = point.proceed();
        if (!GXMyBatisListenerAspectSupport.isSuccessfulResult(proceed)) {
            return proceed;
        }
        publishEvent(point);
        return proceed;
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
        Class<?> mapperClass = MAPPER_CLASS_CACHE
                .get(targetClass, type -> Optional.ofNullable(resolveMapperClass(type)))
                .orElse(null);

        Method invokedMethod = GXMyBatisListenerAspectSupport.invokedMethod(point);
        boolean batchChange = CharSequenceUtil.equals("saveOrUpdateBatch", invokedMethod.getName());
        GXModelEventNamingEnums syncEvent = batchChange
                ? GXModelEventNamingEnums.SYNC_BATCH_CHANGE
                : GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY;
        GXModelEventNamingEnums asyncEvent = batchChange
                ? GXModelEventNamingEnums.ASYNC_BATCH_CHANGE
                : GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY;
        // The mapper type is the extra declaration scope of a batch write; the shared support call
        // keeps the short-circuit, counting and payload rules identical to the other aspects (FR-018).
        GXMyBatisListenerAspectSupport.publishIfDeclared(eventPublisher, point, mapperClass,
                syncEvent, asyncEvent, () -> handlePointArgs(point));
    }

    private Class<?> resolveMapperClass(Class<?> targetClass) {
        Type mapperType = GXCommonUtils.getGenericClassType(targetClass, 0);
        return ObjectUtil.isNull(mapperType) ? null : convertTypeToClass(mapperType);
    }

    private Class<?> convertTypeToClass(Type type) {
        return Convert.convert(new TypeReference<>() {
        }, type);
    }
}
