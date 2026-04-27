package cn.maple.sso.web.interceptor;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

final class GXHandlerMethodAnnotationUtils {
    private GXHandlerMethodAnnotationUtils() {
    }

    static <A extends Annotation> A findMergedAnnotation(HandlerMethod handlerMethod, Class<A> annotationType) {
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

    static boolean hasMergedAnnotation(HandlerMethod handlerMethod, Class<? extends Annotation> annotationType) {
        return findMergedAnnotation(handlerMethod, annotationType) != null;
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
}
