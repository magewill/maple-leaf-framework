package cn.maple.core.datasource.aspect.mybatis.support;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.maple.core.datasource.enums.GXModelEventNamingEnums;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Plumbing shared by the MyBatis write aspects.
 *
 * <p>An aspect still owns its write semantics: it decides when the write counts as effective
 * (through {@link #isSuccessfulResult(Object)}) and how to build its payload. The declaration
 * short-circuit, the operation identifier, the counters and the publish call itself stay in
 * {@link GXMyBatisListenerEventPublisher}. Keeping both pieces here stops every aspect from
 * re-implementing them and drifting from the delivery rules (FR-018, AGENTS.md 关键行为 9).</p>
 */
@Slf4j
public final class GXMyBatisListenerAspectSupport {
    private GXMyBatisListenerAspectSupport() {
    }

    /**
     * A write is effective when it reports at least one affected row.
     */
    public static boolean isSuccessfulResult(@Nullable Object result) {
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

    /**
     * Publishes the event declared on the mapper, building the payload only when a listener is
     * declared so an undeclared write constructs nothing (FR-018).
     */
    public static void publishIfDeclared(GXMyBatisListenerEventPublisher eventPublisher,
                                         ProceedingJoinPoint point,
                                         GXModelEventNamingEnums syncEvent,
                                         GXModelEventNamingEnums asyncEvent,
                                         Supplier<Dict> payloadFactory) {
        Method invokedMethod = invokedMethod(point);
        Object target = point.getTarget();
        if (!eventPublisher.hasListener(target, invokedMethod)) {
            return;
        }
        Dict source = buildPayload(invokedMethod, payloadFactory);
        if (ObjectUtil.isEmpty(source)) {
            return;
        }
        eventPublisher.publish(target, invokedMethod, syncEvent, asyncEvent, source);
    }

    /**
     * Batch variant: the service target is not the mapper, so declarations on the mapper type have
     * to be considered as well (FR-018).
     *
     * <p>This overload must not be collapsed into the single-target one by passing a {@code null}
     * extra type: the publisher then falls back to the service candidate set, which also includes the
     * target class itself and would widen the declaration scope.</p>
     */
    public static void publishIfDeclared(GXMyBatisListenerEventPublisher eventPublisher,
                                         ProceedingJoinPoint point,
                                         @Nullable Class<?> extraType,
                                         GXModelEventNamingEnums syncEvent,
                                         GXModelEventNamingEnums asyncEvent,
                                         Supplier<Dict> payloadFactory) {
        Method invokedMethod = invokedMethod(point);
        Object target = point.getTarget();
        if (!eventPublisher.hasListener(target, extraType, invokedMethod)) {
            return;
        }
        Dict source = buildPayload(invokedMethod, payloadFactory);
        if (ObjectUtil.isEmpty(source)) {
            return;
        }
        eventPublisher.publish(target, extraType, invokedMethod, syncEvent, asyncEvent, source);
    }

    /**
     * Builds the payload after the write already succeeded. Payload construction is a delivery
     * concern: a condition that cannot be read (for example an unsafe field expression rejected by
     * SQL validation) must be counted and logged instead of escaping to the business caller and
     * failing a write that already completed (FR-006).
     */
    private static @Nullable Dict buildPayload(Method invokedMethod, Supplier<Dict> payloadFactory) {
        try {
            return payloadFactory.get();
        } catch (RuntimeException exception) {
            GXMyBatisListenerMetrics.recordFailed();
            log.error("MyBatis listener payload construction failed: method={}, reason={}",
                    invokedMethod.getName(), exception.getMessage(), exception);
            return null;
        }
    }

    /**
     * Resolves the method that was intercepted, which is also the declaration lookup key.
     */
    public static Method invokedMethod(ProceedingJoinPoint point) {
        return ((MethodSignature) point.getSignature()).getMethod();
    }
}
