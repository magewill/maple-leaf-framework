package cn.maple.sso.web.interceptor;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class GXHandlerMethodAnnotationUtils {
    private static final int CACHE_LIMIT = 4096;

    private static final ConcurrentMap<CacheKey, Optional<Annotation>> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    private GXHandlerMethodAnnotationUtils() {
    }

    static <A extends Annotation> A findMergedAnnotation(HandlerMethod handlerMethod, Class<A> annotationType) {
        CacheKey cacheKey = new CacheKey(handlerMethod.getMethod(), handlerMethod.getBeanType(), annotationType);
        Optional<Annotation> cached = ANNOTATION_CACHE.get(cacheKey);
        if (cached == null) {
            cached = Optional.ofNullable(findMergedAnnotationUncached(handlerMethod, annotationType));
            if (ANNOTATION_CACHE.size() >= CACHE_LIMIT) {
                ANNOTATION_CACHE.clear();
            }
            Optional<Annotation> previous = ANNOTATION_CACHE.putIfAbsent(cacheKey, cached);
            if (previous != null) {
                cached = previous;
            }
        }
        return cached.map(annotationType::cast).orElse(null);
    }

    static boolean hasMergedAnnotation(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        return findMergedAnnotation(handlerMethod, annotationType) != null;
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

    private record CacheKey(Method method, Class<?> beanType, Class<? extends Annotation> annotationType) {
        private CacheKey {
            Objects.requireNonNull(method, "method must not be null");
            Objects.requireNonNull(beanType, "beanType must not be null");
            Objects.requireNonNull(annotationType, "annotationType must not be null");
        }
    }
}
