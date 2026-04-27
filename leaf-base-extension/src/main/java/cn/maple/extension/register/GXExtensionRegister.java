package cn.maple.extension.register;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXLoggerUtils;
import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionCoordinate;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.GXExtensions;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Registers extension point implementations into {@link GXExtensionRepository}.
 */
@Component
@Slf4j
public class GXExtensionRegister {
    public static final String EXTENSION_EXT_PT_NAMING = "ExtPoint";

    @Resource
    private GXExtensionRepository extensionRepository;

    public void doRegistration(GXExtensionPoint extensionObject) {
        Objects.requireNonNull(extensionObject, "Extension object cannot be null");

        Class<?> extensionClz = getExtensionClass(extensionObject);
        GXExtension extensionAnn = AnnotationUtils.findAnnotation(extensionClz, GXExtension.class);
        if (Objects.nonNull(extensionAnn)) {
            GXBizScenario bizScenario = GXBizScenario.valueOf(extensionAnn.bizId(), extensionAnn.useCase(), extensionAnn.scenario());
            registerExtension(extensionClz, bizScenario, extensionObject);
        }
    }

    public void doRegistrationExtensions(GXExtensionPoint extensionObject) {
        Objects.requireNonNull(extensionObject, "Extension object cannot be null");

        Class<?> extensionClz = getExtensionClass(extensionObject);
        GXExtensions extensionsAnnotation = AnnotationUtils.findAnnotation(extensionClz, GXExtensions.class);
        Objects.requireNonNull(extensionsAnnotation, "GXExtensions annotation not found on " + extensionClz.getName());

        GXExtension[] extensions = extensionsAnnotation.value();
        if (extensions.length > 0) {
            for (GXExtension extensionAnn : extensions) {
                GXBizScenario bizScenario = GXBizScenario.valueOf(extensionAnn.bizId(), extensionAnn.useCase(), extensionAnn.scenario());
                registerExtension(extensionClz, bizScenario, extensionObject);
            }
            return;
        }

        for (String bizId : extensionsAnnotation.bizId()) {
            for (String useCase : extensionsAnnotation.useCase()) {
                for (String scenario : extensionsAnnotation.scenario()) {
                    GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
                    registerExtension(extensionClz, bizScenario, extensionObject);
                }
            }
        }
    }

    private Class<?> getExtensionClass(GXExtensionPoint extensionObject) {
        if (AopUtils.isAopProxy(extensionObject)) {
            return AopUtils.getTargetClass(extensionObject);
        }
        return extensionObject.getClass();
    }

    private void registerExtension(Class<?> extensionClz, GXBizScenario bizScenario, GXExtensionPoint extensionObject) {
        GXExtensionCoordinate extensionCoordinate = new GXExtensionCoordinate(calculateExtensionPoint(extensionClz), bizScenario.getUniqueIdentity());
        if (extensionRepository.registerExtensionIfAbsent(extensionCoordinate, extensionObject).isPresent()) {
            GXLoggerUtils.logWarn(log, CharSequenceUtil.format("Duplicate registration is not allowed for : {}", extensionCoordinate));
        }
    }

    private String calculateExtensionPoint(Class<?> targetClz) {
        Objects.requireNonNull(targetClz, "Target class cannot be null");

        Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(targetClz);
        if (CollUtil.isEmpty(Arrays.asList(interfaces))) {
            throw new GXBusinessException("Please assign a extension point interface for " + targetClz);
        }
        for (Class<?> clazz : interfaces) {
            String extensionPoint = clazz.getSimpleName();
            if (extensionPoint.contains(EXTENSION_EXT_PT_NAMING)) {
                return clazz.getName();
            }
        }
        throw new GXBusinessException("Your name of ExtensionPoint for " + targetClz + " is not valid, must contain '" + EXTENSION_EXT_PT_NAMING + "'");
    }
}
