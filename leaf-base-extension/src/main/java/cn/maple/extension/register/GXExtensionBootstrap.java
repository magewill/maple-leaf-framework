package cn.maple.extension.register;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensions;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Registers Spring beans annotated with {@link GXExtension} or {@link GXExtensions} at startup.
 */
@Component
public class GXExtensionBootstrap {
    @Resource
    private GXExtensionRegister extensionRegister;

    @Resource
    private ApplicationContext applicationContext;

    /**
     * Scans the Spring context and registers extension point beans.
     */
    @PostConstruct
    public void init() {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring application context is not initialized yet");
        }
        
        Map<String, Object> extensionBeans = applicationContext.getBeansWithAnnotation(GXExtension.class);
        extensionBeans.values().forEach(extension -> {
            if (extension instanceof GXExtensionPoint) {
                if (AnnotationUtils.findAnnotation(AopUtils.getTargetClass(extension), GXExtensions.class) == null) {
                    extensionRegister.doRegistration((GXExtensionPoint) extension);
                }
            } else {
                throw new IllegalStateException("Bean with GXExtension annotation must implement GXExtensionPoint interface: " + extension.getClass().getName());
            }
        });

        Map<String, Object> extensionsBeans = applicationContext.getBeansWithAnnotation(GXExtensions.class);
        extensionsBeans.values().forEach(extension -> {
            if (extension instanceof GXExtensionPoint) {
                extensionRegister.doRegistrationExtensions((GXExtensionPoint) extension);
            } else {
                throw new IllegalStateException("Bean with GXExtensions annotation must implement GXExtensionPoint interface: " + extension.getClass().getName());
            }
        });
    }
}
