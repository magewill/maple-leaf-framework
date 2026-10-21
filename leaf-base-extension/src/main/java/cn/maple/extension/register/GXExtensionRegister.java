package cn.maple.extension.register;

import cn.hutool.http.HttpStatus;
import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionCoordinate;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.GXExtensions;
import cn.maple.extension.exception.GXExtensionException;
import jakarta.annotation.Resource;
import org.jspecify.annotations.NullMarked;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Registers extension point implementations into {@link GXExtensionRepository}.
 * <p>
 * Both entry points resolve coordinates through {@link #resolveBizScenarios(Class)}, so a single
 * implementation class always registers every coordinate it declares, whichever entry point is used.
 */
@Component
public class GXExtensionRegister {
    public static final String EXTENSION_EXT_PT_NAMING = "ExtPoint";

    @Resource
    private GXExtensionRepository extensionRepository;

    public void doRegistration(GXExtensionPoint extensionObject) {
        doRegistrations(extensionObject);
    }

    public void doRegistrationExtensions(GXExtensionPoint extensionObject) {
        doRegistrations(extensionObject);
    }

    private void doRegistrations(GXExtensionPoint extensionObject) {
        Objects.requireNonNull(extensionObject, "Extension object cannot be null");

        Class<?> extensionClz = getExtensionClass(extensionObject);
        List<GXBizScenario> bizScenarios = resolveBizScenarios(extensionClz);
        String extensionPointName = calculateExtensionPoint(extensionClz);

        for (GXBizScenario bizScenario : bizScenarios) {
            GXExtensionCoordinate extensionCoordinate =
                    GXExtensionCoordinate.valueOf(extensionPointName, bizScenario.getUniqueIdentity());
            extensionRepository.registerExtensionIfAbsent(extensionCoordinate, extensionObject)
                    .ifPresent(registeredExtension -> {
                        throw new GXExtensionException("Duplicate extension registration for " + extensionCoordinate
                                + ", existing=" + registeredExtension.getClass().getName()
                                + ", duplicate=" + extensionObject.getClass().getName(),
                                HttpStatus.HTTP_INTERNAL_ERROR);
                    });
        }
    }

    /**
     * Resolves every scenario coordinate declared by an implementation class.
     * <p>
     * Supports a single {@link GXExtension}, repeated {@link GXExtension} annotations, an explicit
     * {@link GXExtensions#value()} list, the cartesian product of {@link GXExtensions} arrays, and a
     * mix of a single annotation with a container. Returns at least one coordinate or fails.
     *
     * @param extensionClz implementation class, never {@code null}
     * @return declared coordinates in declaration order
     * @throws GXExtensionException when no coordinate can be resolved
     */
    @NullMarked
    protected List<GXBizScenario> resolveBizScenarios(Class<?> extensionClz) {
        Objects.requireNonNull(extensionClz, "Extension class cannot be null");

        Set<GXExtension> repeatable =
                AnnotatedElementUtils.findMergedRepeatableAnnotations(extensionClz, GXExtension.class);
        GXExtensions container = AnnotatedElementUtils.findMergedAnnotation(extensionClz, GXExtensions.class);

        List<GXBizScenario> bizScenarios = new ArrayList<>(repeatable.size() + 1);
        try {
            for (GXExtension extension : repeatable) {
                bizScenarios.add(GXBizScenario.valueOf(extension.bizId(), extension.useCase(), extension.scenario()));
            }

            if (container != null && container.value().length == 0) {
                for (String bizId : container.bizId()) {
                    for (String useCase : container.useCase()) {
                        for (String scenario : container.scenario()) {
                            bizScenarios.add(GXBizScenario.valueOf(bizId, useCase, scenario));
                        }
                    }
                }
            }
        } catch (IllegalArgumentException invalidAttribute) {
            throw new GXExtensionException("Invalid extension coordinate declaration for " + extensionClz.getName()
                    + ", declaration=" + describeDeclaration(extensionClz, container)
                    + ", detail=" + invalidAttribute.getMessage(),
                    HttpStatus.HTTP_INTERNAL_ERROR, invalidAttribute);
        }

        if (bizScenarios.isEmpty()) {
            throw new GXExtensionException("No extension coordinate resolved for " + extensionClz.getName()
                    + ", declaration=" + describeDeclaration(extensionClz, container), HttpStatus.HTTP_INTERNAL_ERROR);
        }
        return bizScenarios;
    }

    private String describeDeclaration(Class<?> extensionClz, GXExtensions container) {
        if (container != null) {
            return "GXExtensions(bizId=" + Arrays.toString(container.bizId())
                    + ", useCase=" + Arrays.toString(container.useCase())
                    + ", scenario=" + Arrays.toString(container.scenario()) + ")";
        }
        GXExtension single = AnnotatedElementUtils.findMergedAnnotation(extensionClz, GXExtension.class);
        if (single != null) {
            return "GXExtension(bizId=[" + single.bizId() + "], useCase=[" + single.useCase()
                    + "], scenario=[" + single.scenario() + "])";
        }
        return "GXExtension/GXExtensions(absent)";
    }

    private Class<?> getExtensionClass(GXExtensionPoint extensionObject) {
        if (AopUtils.isAopProxy(extensionObject)) {
            return AopUtils.getTargetClass(extensionObject);
        }
        return extensionObject.getClass();
    }

    private String calculateExtensionPoint(Class<?> targetClz) {
        Objects.requireNonNull(targetClz, "Target class cannot be null");

        Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(targetClz);
        if (interfaces.length == 0) {
            throw new GXExtensionException("Please assign a extension point interface for " + targetClz,
                    HttpStatus.HTTP_INTERNAL_ERROR);
        }
        List<Class<?>> extensionPointInterfaces = Arrays.stream(interfaces)
                .filter(clazz -> clazz != GXExtensionPoint.class)
                .filter(GXExtensionPoint.class::isAssignableFrom)
                .filter(clazz -> clazz.getSimpleName().contains(EXTENSION_EXT_PT_NAMING))
                .toList();
        if (extensionPointInterfaces.size() == 1) {
            return extensionPointInterfaces.getFirst().getName();
        }
        if (extensionPointInterfaces.size() > 1) {
            throw new GXExtensionException("Only one extension point interface is allowed for " + targetClz
                    + ", found " + extensionPointInterfaces, HttpStatus.HTTP_INTERNAL_ERROR);
        }
        throw new GXExtensionException("Your name of ExtensionPoint for " + targetClz
                + " is not valid, must contain '" + EXTENSION_EXT_PT_NAMING + "'", HttpStatus.HTTP_INTERNAL_ERROR);
    }
}
