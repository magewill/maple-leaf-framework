package cn.maple.extension.register;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensions;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.TreeSet;

/**
 * Registers Spring beans annotated with {@link GXExtension} or {@link GXExtensions} at startup.
 * <p>
 * Both annotation types are collected into a single bean name set, so a bean mixing a single
 * annotation with a container is registered exactly once, with all of its declared coordinates.
 */
@Component
public class GXExtensionBootstrap implements SmartInitializingSingleton {
    @Resource
    private GXExtensionRegister extensionRegister;

    @Resource
    private ApplicationContext applicationContext;

    @Override
    public void afterSingletonsInstantiated() {
        init();
    }

    /**
     * Scans the Spring context and registers extension point beans.
     */
    public void init() {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring application context is not initialized yet");
        }

        Set<String> beanNames = new TreeSet<>();
        beanNames.addAll(Set.of(applicationContext.getBeanNamesForAnnotation(GXExtension.class)));
        beanNames.addAll(Set.of(applicationContext.getBeanNamesForAnnotation(GXExtensions.class)));

        for (String beanName : beanNames) {
            Object extension = applicationContext.getBean(beanName);
            if (extension instanceof GXExtensionPoint extensionPoint) {
                extensionRegister.doRegistration(extensionPoint);
            } else {
                throw new IllegalStateException(
                        "Bean with GXExtension/GXExtensions annotation must implement GXExtensionPoint interface: "
                                + extension.getClass().getName());
            }
        }
    }
}
