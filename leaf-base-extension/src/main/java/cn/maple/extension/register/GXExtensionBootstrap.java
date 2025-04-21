package cn.maple.extension.register;

import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensionPoint;
import cn.maple.extension.GXExtensions;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Map;

/**
 * GXExtensionBootstrap 扩展启动类
 * <p>
 * 该类负责在应用启动时自动扫描并注册所有标记了{@link GXExtension}或{@link GXExtensions}注解的扩展点实现类。
 * 通过Spring的自动注入和PostConstruct机制，确保在应用启动时完成所有扩展点的注册。
 * <p>
 * 线程安全性：该类在应用启动时执行一次初始化，之后不再修改状态，因此是线程安全的。
 * <p>
 * 使用示例：
 * <pre>
 * // 通常不需要直接使用该类，Spring会自动创建并初始化它
 * // 但如果需要在特定时机重新注册扩展点，可以这样使用：
 * 
 * @Component
 * public class MyService {
 *     @Resource
 *     private GXExtensionBootstrap extensionBootstrap;
 *     
 *     public void reloadExtensions() {
 *         extensionBootstrap.init();
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXExtension 扩展点注解
 * @see GXExtensions 扩展点集合注解
 * @see GXExtensionRegister 扩展注册器
 */
@Component
public class GXExtensionBootstrap {
    @Resource
    private GXExtensionRegister extensionRegister;

    /**
     * 应用启动时自动注册所有扩展点对象
     * <p>
     * 该方法会扫描Spring容器中所有标记了{@link GXExtension}或{@link GXExtensions}注解的Bean，
     * 并将它们注册到扩展仓库中。注册过程分为两步：
     * 1. 注册所有标记了{@link GXExtension}注解的Bean
     * 2. 注册所有标记了{@link GXExtensions}注解的Bean
     * <p>
     * 注意：如果一个Bean同时标记了这两种注解，会被重复注册，但扩展仓库会处理重复注册的情况。
     */
    @PostConstruct
    public void init() {
        // 获取Spring容器
        if (GXSpringContextUtils.getApplicationContext() == null) {
            throw new IllegalStateException("Spring application context is not initialized yet");
        }
        
        // 注册所有标记了GXExtension注解的Bean
        Map<String, Object> extensionBeans = GXSpringContextUtils.getApplicationContext().getBeansWithAnnotation(GXExtension.class);
        extensionBeans.values().forEach(extension -> {
            if (extension instanceof GXExtensionPoint) {
                extensionRegister.doRegistration((GXExtensionPoint) extension);
            } else {
                throw new IllegalStateException("Bean with GXExtension annotation must implement GXExtensionPoint interface: " + extension.getClass().getName());
            }
        });

        // 注册所有标记了GXExtensions注解的Bean
        Map<String, Object> extensionsBeans = GXSpringContextUtils.getApplicationContext().getBeansWithAnnotation(GXExtensions.class);
        extensionsBeans.values().forEach(extension -> {
            if (extension instanceof GXExtensionPoint) {
                extensionRegister.doRegistrationExtensions((GXExtensionPoint) extension);
            } else {
                throw new IllegalStateException("Bean with GXExtensions annotation must implement GXExtensionPoint interface: " + extension.getClass().getName());
            }
        });
    }
}
