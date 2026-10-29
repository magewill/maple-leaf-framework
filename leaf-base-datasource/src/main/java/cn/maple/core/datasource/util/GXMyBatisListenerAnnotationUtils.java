package cn.maple.core.datasource.util;

import cn.hutool.core.annotation.AnnotationUtil;
import cn.maple.core.datasource.annotation.GXMyBatisListener;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class GXMyBatisListenerAnnotationUtils {
    private GXMyBatisListenerAnnotationUtils() {
    }

    /**
     * Resolves a type-level declaration together with the type that actually carries it.
     *
     * <p>The lookup walks parent interfaces and superclasses, so the declaring type is not
     * necessarily the candidate type the caller passed in. Returning it keeps diagnostics (the
     * {@code declaredFrom} field of a resolved listener) pointing at the real declaration site.</p>
     */
    public static @Nullable ResolvedDeclaration findTypeDeclaration(@Nullable Class<?> targetType) {
        return findTypeDeclaration(targetType, new HashSet<>());
    }

    /**
     * Resolves a method-level declaration together with the type that actually carries it.
     */
    public static @Nullable ResolvedDeclaration findMethodDeclaration(@Nullable Class<?> targetType,
                                                                      @Nullable Method method) {
        if (Objects.isNull(method)) {
            return null;
        }
        GXMyBatisListener annotation = AnnotationUtil.getAnnotation(method, GXMyBatisListener.class);
        if (Objects.nonNull(annotation)) {
            return new ResolvedDeclaration(annotation, method.getDeclaringClass());
        }
        return findMethodDeclaration(targetType, method, new HashSet<>());
    }

    private static @Nullable ResolvedDeclaration findTypeDeclaration(@Nullable Class<?> targetType,
                                                                     Set<Class<?>> visited) {
        if (Objects.isNull(targetType) || !visited.add(targetType)) {
            return null;
        }
        GXMyBatisListener annotation = AnnotationUtil.getAnnotation(targetType, GXMyBatisListener.class);
        if (Objects.nonNull(annotation)) {
            return new ResolvedDeclaration(annotation, targetType);
        }
        for (Class<?> interfaceType : targetType.getInterfaces()) {
            ResolvedDeclaration declaration = findTypeDeclaration(interfaceType, visited);
            if (Objects.nonNull(declaration)) {
                return declaration;
            }
        }
        return findTypeDeclaration(targetType.getSuperclass(), visited);
    }

    private static @Nullable ResolvedDeclaration findMethodDeclaration(@Nullable Class<?> targetType, Method method,
                                                                       Set<Class<?>> visited) {
        if (Objects.isNull(targetType) || !visited.add(targetType)) {
            return null;
        }
        for (Class<?> interfaceType : targetType.getInterfaces()) {
            try {
                Method interfaceMethod = interfaceType.getMethod(method.getName(), method.getParameterTypes());
                GXMyBatisListener annotation = AnnotationUtil.getAnnotation(interfaceMethod, GXMyBatisListener.class);
                if (Objects.nonNull(annotation)) {
                    return new ResolvedDeclaration(annotation, interfaceMethod.getDeclaringClass());
                }
            } catch (NoSuchMethodException ignored) {
                // Continue searching parent interfaces and superclasses.
            }
            ResolvedDeclaration declaration = findMethodDeclaration(interfaceType, method, visited);
            if (Objects.nonNull(declaration)) {
                return declaration;
            }
        }
        return findMethodDeclaration(targetType.getSuperclass(), method, visited);
    }

    /**
     * A declaration together with the type that actually carries the annotation.
     *
     * @param annotation    resolved annotation instance
     * @param declaringType the interface or class that declares the annotation, not the candidate
     *                      type that merely inherited it
     */
    public record ResolvedDeclaration(GXMyBatisListener annotation, Class<?> declaringType) {
    }
}
