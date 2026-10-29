package cn.maple.core.datasource.aspect.mybatis;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerAspectSupport;
import cn.maple.core.datasource.aspect.mybatis.support.GXMyBatisListenerEventPublisher;
import cn.maple.core.datasource.constant.GXMyBatisEventConstant;
import cn.maple.core.datasource.enums.GXMyBatisListenerDegradeReasonEnums;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import cn.maple.core.framework.util.GXCommonUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Bound on the payload detachment walk. Values nested deeper keep the entity reference, so a
     * truncated walk marks the payload as degraded instead of looking like a clean snapshot
     * (AGENTS.md 14). The strict-snapshot scan uses its own, much smaller budget, so it cannot make
     * up for the references kept here.
     */
    private static final int MAX_DETACH_DEPTH = 32;

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
        // The batch event hands this payload to listeners without a second defensive snapshot
        // (SC-005), so the values still shared with the business entities - dates, calendars and
        // array/collection/map fields such as `ext` - are copied once into the payload rows here.
        // A listener that mutates a payload value can therefore no longer reach the entity, while the
        // payload itself is never duplicated (FR-008, SC-005).
        // `Convert` returns the caller's own dictionaries when Dict/Map rows are passed in, so those
        // rows are copied instead of rewritten; a converted entity yields a private row that can be
        // rewritten in place. The decision is taken before the walk, hence the constructor argument.
        DetachContext detachContext = new DetachContext(
                entities.stream().anyMatch(entity -> entity instanceof Map<?, ?>));
        List<Dict> payloadRows = new ArrayList<>(nonNullEntities.size());
        nonNullEntities.forEach(row -> payloadRows.add(detachMutableValues(row, detachContext, 0)));
        Dict payload = Dict.create()
                .set(GXMyBatisEventConstant.OPERATION, ((MethodSignature) point.getSignature()).getName())
                .set(GXMyBatisEventConstant.ENTITY_DATA, payloadRows)
                .set(GXMyBatisEventConstant.BATCH_ENTITIES, payloadRows);
        if (detachContext.truncated) {
            markDetachTruncated(payload);
        }
        return payload;
    }

    /**
     * A bounded walk that stopped early must never look like a verified snapshot: the values it did not
     * copy still reference the business entities, so the payload is marked and the shared degrade
     * counter/log picks it up (AGENTS.md 14, FR-020).
     */
    private static void markDetachTruncated(Dict payload) {
        payload.set(GXMyBatisEventConstant.SNAPSHOT_DEGRADED, Boolean.TRUE)
                .set(GXMyBatisEventConstant.DEGRADE_REASONS, List.of(GXMyBatisListenerDegradeReasonEnums.UNSNAPSHOTABLE_VALUE));
    }

    /**
     * Replaces entity-owned mutable values of one payload row with private copies and returns the row
     * to publish: the row itself when it was created by the conversion, a new row when it is owned by
     * the caller.
     */
    private static Dict detachMutableValues(Dict row, DetachContext context, int depth) {
        Dict target = context.copyRows ? Dict.create() : row;
        context.copies.put(row, target);
        row.forEach((key, value) -> target.set(key, detachMutableValue(value, context, depth + 1)));
        return target;
    }

    private static @Nullable Object detachMutableValue(@Nullable Object value, DetachContext context, int depth) {
        if (ObjectUtil.isNull(value)) {
            return value;
        }
        // Leaves are cheap to copy, so they are cloned even below the depth budget.
        if (value instanceof Date date) {
            return date.clone();
        }
        if (value instanceof Calendar calendar) {
            return calendar.clone();
        }
        Object copied = context.copies.get(value);
        if (ObjectUtil.isNotNull(copied)) {
            return copied;
        }
        if (depth > MAX_DETACH_DEPTH) {
            // Only a skipped container leaks an entity reference; a scalar at this depth is harmless.
            if (value instanceof Map<?, ?> || value instanceof Collection<?> || value.getClass().isArray()) {
                context.truncated = true;
            }
            return value;
        }
        if (value instanceof Dict dict) {
            Dict copy = Dict.create();
            context.copies.put(value, copy);
            dict.forEach((key, nested) -> copy.set(key, detachMutableValue(nested, context, depth + 1)));
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>(Math.max(16, map.size() * 2));
            context.copies.put(value, copy);
            map.forEach((key, nested) -> copy.put(key, detachMutableValue(nested, context, depth + 1)));
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            context.copies.put(value, copy);
            set.forEach(nested -> copy.add(detachMutableValue(nested, context, depth + 1)));
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            context.copies.put(value, copy);
            collection.forEach(nested -> copy.add(detachMutableValue(nested, context, depth + 1)));
            return copy;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object copy = Array.newInstance(value.getClass().getComponentType(), length);
            context.copies.put(value, copy);
            for (int index = 0; index < length; index++) {
                Array.set(copy, index, detachMutableValue(Array.get(value, index), context, depth + 1));
            }
            return copy;
        }
        return value;
    }

    /**
     * Tracks one copy per source instance, so containers referenced from several places (the same
     * entity list is published under both {@code entityData} and {@code batchEntities}) are copied
     * once and a self-referencing payload cannot recurse without bound.
     *
     * <p>Whether the rows belong to the caller is fixed when the context is created, because it is a
     * property of the input, not of the walk. {@code truncated} is the walk's outcome.</p>
     */
    private static final class DetachContext {
        private final Map<Object, Object> copies = new IdentityHashMap<>();
        private final boolean copyRows;
        private boolean truncated;

        private DetachContext(boolean copyRows) {
            this.copyRows = copyRows;
        }
    }

    private void publishEvent(ProceedingJoinPoint point) {
        Class<?> targetClass = AopUtils.getTargetClass(point.getTarget());
        Class<?> mapperClass = MAPPER_CLASS_CACHE
                .get(targetClass, type -> Optional.ofNullable(resolveMapperClass(type)))
                .orElse(null);

        Method invokedMethod = GXMyBatisListenerAspectSupport.invokedMethod(point);
        GXModelEventNamingEnums syncEvent;
        GXModelEventNamingEnums asyncEvent;
        if ("saveOrUpdateBatch".equals(invokedMethod.getName())) {
            syncEvent = GXModelEventNamingEnums.SYNC_BATCH_CHANGE;
            asyncEvent = GXModelEventNamingEnums.ASYNC_BATCH_CHANGE;
        } else {
            syncEvent = GXModelEventNamingEnums.SYNC_SAVE_BATCH_ENTITY;
            asyncEvent = GXModelEventNamingEnums.ASYNC_SAVE_BATCH_ENTITY;
        }
        // The mapper type is the extra declaration scope of a batch write; the shared support call
        // keeps the short-circuit, counting and payload rules identical to the other aspects (FR-018).
        GXMyBatisListenerAspectSupport.publishIfDeclared(eventPublisher, point, mapperClass,
                syncEvent, asyncEvent, () -> handlePointArgs(point));
    }

    private static @Nullable Class<?> resolveMapperClass(Class<?> targetClass) {
        Type mapperType = GXCommonUtils.getGenericClassType(targetClass, 0);
        if (ObjectUtil.isNull(mapperType)) {
            return null;
        }
        return Convert.convert(new TypeReference<>() {
        }, mapperType);
    }
}
