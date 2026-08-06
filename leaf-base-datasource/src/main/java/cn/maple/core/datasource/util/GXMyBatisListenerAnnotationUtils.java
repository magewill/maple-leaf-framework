package cn.maple.core.datasource.util;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class GXMyBatisListenerAnnotationUtils {
    private GXMyBatisListenerAnnotationUtils() {
    }

    public static GXMyBatisListener findTypeAnnotation(Class<?> targetType) {
        return findTypeAnnotation(targetType, new HashSet<>());
    }

    public static GXMyBatisListener findMethodAnnotation(Class<?> targetType, Method method) {
        if (method == null) {
            return null;
        }
        GXMyBatisListener annotation = AnnotationUtil.getAnnotation(method, GXMyBatisListener.class);
        if (annotation != null) {
            return annotation;
        }
        return findMethodAnnotation(targetType, method, new HashSet<>());
    }

    private static GXMyBatisListener findTypeAnnotation(Class<?> targetType, Set<Class<?>> visited) {
        if (targetType == null || !visited.add(targetType)) {
            return null;
        }
        GXMyBatisListener annotation = AnnotationUtil.getAnnotation(targetType, GXMyBatisListener.class);
        if (annotation != null) {
            return annotation;
        }
        for (Class<?> interfaceType : targetType.getInterfaces()) {
            annotation = findTypeAnnotation(interfaceType, visited);
            if (annotation != null) {
                return annotation;
            }
        }
        return findTypeAnnotation(targetType.getSuperclass(), visited);
    }

    private static GXMyBatisListener findMethodAnnotation(Class<?> targetType, Method method, Set<Class<?>> visited) {
        if (targetType == null || !visited.add(targetType)) {
            return null;
        }
        for (Class<?> interfaceType : targetType.getInterfaces()) {
            try {
                Method interfaceMethod = interfaceType.getMethod(method.getName(), method.getParameterTypes());
                GXMyBatisListener annotation = AnnotationUtil.getAnnotation(interfaceMethod, GXMyBatisListener.class);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchMethodException ignored) {
                // Continue searching parent interfaces and superclasses.
            }
            GXMyBatisListener annotation = findMethodAnnotation(interfaceType, method, visited);
            if (annotation != null) {
                return annotation;
            }
        }
        return findMethodAnnotation(targetType.getSuperclass(), method, visited);
    }
}
