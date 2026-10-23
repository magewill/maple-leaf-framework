package cn.maple.sso.web.interceptor;

import cn.maple.sso.annotation.GXPermissionAnnotation;
import cn.maple.sso.enums.GXAction;
import cn.maple.sso.properties.GXSSOProperties;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Cached handler method annotation lookup.
 *
 * <p>The cache stores <b>decision results</b> (a boolean or a tiny immutable record), never
 * {@link Annotation} instances and never {@link Method}/{@link Class} references: holding
 * them from a static map would pin the application class loader and an unbounded map grows
 * without limit. Capacity comes from {@code sso.config.capacity} and eviction is FIFO, so a
 * burst of new handlers never clears the whole cache.</p>
 */
final class GXHandlerMethodAnnotationUtils {
    private static final int DEFAULT_CAPACITY = 4096;

    private static final ConcurrentMap<String, CachedDecision> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    private static final Queue<String> INSERTION_ORDER = new ConcurrentLinkedQueue<>();

    private GXHandlerMethodAnnotationUtils() {
    }

    static boolean hasMergedAnnotation(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        return hasMergedAnnotationUncached(handlerMethod, annotationType);
    }

    /**
     * Cached lookup of the ignore-login annotation.
     *
     * @param handlerMethod handler under inspection
     * @return {@code true} when the handler or its declaring type carries the annotation
     */
    static boolean hasIgnoreAnnotation(HandlerMethod handlerMethod) {
        CachedDecision decision = decide(handlerMethod, "@ignore", GXHandlerMethodAnnotationUtils::hasIgnoreAnnotationUncached);
        return decision instanceof BooleanDecision booleanDecision && booleanDecision.value();
    }

    /**
     * Cached lookup of {@code GXHttpInvokerAuthToken}.
     *
     * @param handlerMethod handler under inspection
     * @return {@code true} when the handler carries the annotation
     */
    static boolean hasHttpInvokerTokenAnnotation(HandlerMethod handlerMethod) {
        CachedDecision decision = decide(handlerMethod, "@invoker-token",
                handler -> new BooleanDecision(hasMergedAnnotationUncached(handler,
                        cn.maple.core.framework.annotation.GXHttpInvokerAuthToken.class)));
        return decision instanceof BooleanDecision booleanDecision && booleanDecision.value();
    }

    /**
     * Cached, instance free view of {@link GXPermissionAnnotation}.
     *
     * @param handlerMethod handler under inspection
     * @return permission descriptor, never {@code null}
     */
    static PermissionInfo permissionInfo(HandlerMethod handlerMethod) {
        CachedDecision decision = decide(handlerMethod, "@permission", GXHandlerMethodAnnotationUtils::permissionInfoUncached);
        if (decision instanceof PermissionDecision permissionDecision) {
            return permissionDecision.info();
        }
        return PermissionInfo.ABSENT;
    }

    static int cacheSize() {
        return ANNOTATION_CACHE.size();
    }

    private static CachedDecision decide(HandlerMethod handlerMethod, String tag, Decider decider) {
        String key = cacheKey(handlerMethod, tag);
        CachedDecision cached = ANNOTATION_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        CachedDecision computed = decider.decide(handlerMethod);
        CachedDecision previous = ANNOTATION_CACHE.putIfAbsent(key, computed);
        if (previous != null) {
            return previous;
        }
        INSERTION_ORDER.offer(key);
        evictIfNecessary();
        return computed;
    }

    private static void evictIfNecessary() {
        int capacity = resolveCapacity();
        while (ANNOTATION_CACHE.size() > capacity) {
            String oldest = INSERTION_ORDER.poll();
            if (oldest == null) {
                return;
            }
            ANNOTATION_CACHE.remove(oldest);
        }
    }

    private static int resolveCapacity() {
        try {
            GXSSOProperties properties = GXSSOProperties.getInstance();
            int capacity = properties == null ? 0 : properties.getCapacity();
            return capacity > 0 ? capacity : DEFAULT_CAPACITY;
        } catch (Exception e) {
            return DEFAULT_CAPACITY;
        }
    }

    private static String cacheKey(HandlerMethod handlerMethod, String tag) {
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();
        return beanType.getName() + "#" + method.toGenericString() + "#" + tag;
    }

    private static CachedDecision hasIgnoreAnnotationUncached(HandlerMethod handlerMethod) {
        return new BooleanDecision(findMergedAnnotationUncached(handlerMethod,
                cn.maple.core.framework.annotation.GXIgnoreLoginIntercept.class) != null);
    }

    private static CachedDecision permissionInfoUncached(HandlerMethod handlerMethod) {
        GXPermissionAnnotation annotation = findMergedAnnotationUncached(handlerMethod, GXPermissionAnnotation.class);
        if (annotation == null) {
            return new PermissionDecision(PermissionInfo.ABSENT);
        }
        return new PermissionDecision(new PermissionInfo(true, annotation.action(), annotation.value()));
    }

    private static boolean hasMergedAnnotationUncached(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        return findMergedAnnotationUncached(handlerMethod, annotationType) != null;
    }

    private static <A extends Annotation> A findMergedAnnotationUncached(HandlerMethod handlerMethod, Class<A> annotationType) {
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();

        A annotation = findOnMethod(method, annotationType);
        if (annotation != null) {
            return annotation;
        }

        Method specificMethod = ClassUtils.getMostSpecificMethod(method, beanType);
        annotation = findOnMethod(specificMethod, annotationType);
        if (annotation != null) {
            return annotation;
        }

        annotation = findOnInterfaces(beanType, method, annotationType);
        if (annotation != null) {
            return annotation;
        }

        return findOnType(beanType, annotationType);
    }

    private static <A extends Annotation> A findOnMethod(Method method, Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
        if (annotation != null) {
            return annotation;
        }

        Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
        if (!bridgedMethod.equals(method)) {
            return AnnotatedElementUtils.findMergedAnnotation(bridgedMethod, annotationType);
        }
        return null;
    }

    private static <A extends Annotation> A findOnType(Class<?> type, Class<A> annotationType) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            A annotation = AnnotatedElementUtils.findMergedAnnotation(current, annotationType);
            if (annotation != null) {
                return annotation;
            }

            annotation = findOnInterfaceTypes(current, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private static <A extends Annotation> A findOnInterfaces(Class<?> type, Method method, Class<A> annotationType) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            A annotation = findOnInterfaceMethods(current, method, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private static <A extends Annotation> A findOnInterfaceMethods(Class<?> type, Method method, Class<A> annotationType) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            Method interfaceMethod = ReflectionUtils.findMethod(interfaceType, method.getName(), method.getParameterTypes());
            if (interfaceMethod != null) {
                A annotation = findOnMethod(interfaceMethod, annotationType);
                if (annotation != null) {
                    return annotation;
                }
            }

            A annotation = findOnInterfaceMethods(interfaceType, method, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private static <A extends Annotation> A findOnInterfaceTypes(Class<?> type, Class<A> annotationType) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            A annotation = AnnotatedElementUtils.findMergedAnnotation(interfaceType, annotationType);
            if (annotation != null) {
                return annotation;
            }

            annotation = findOnInterfaceTypes(interfaceType, annotationType);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Decider {
        CachedDecision decide(HandlerMethod handlerMethod);
    }

    private sealed interface CachedDecision permits BooleanDecision, PermissionDecision {
    }

    private record BooleanDecision(boolean value) implements CachedDecision {
    }

    private record PermissionDecision(PermissionInfo info) implements CachedDecision {
    }

    /**
     * Immutable permission descriptor, free of any application class reference.
     *
     * @param present whether the annotation is declared
     * @param action annotation action
     * @param value annotation value
     */
    record PermissionInfo(boolean present, GXAction action, String value) {
        static final PermissionInfo ABSENT = new PermissionInfo(false, GXAction.Normal, "");

        PermissionInfo {
            Objects.requireNonNull(action, "action must not be null");
            value = value == null ? "" : value;
        }
    }
}
