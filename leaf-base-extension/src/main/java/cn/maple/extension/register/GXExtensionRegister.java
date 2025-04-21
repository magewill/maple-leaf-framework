package cn.maple.extension.register;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXLoggerUtils;
import cn.maple.extension.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.Objects;

/**
 * GXExtensionRegister 扩展注册器
 * <p>
 * 该类负责将标记了{@link GXExtension}或{@link GXExtensions}注解的扩展点实现类注册到扩展仓库中。
 * 注册过程会处理AOP代理对象，确保能够正确获取原始类的注解信息。
 * <p>
 * 线程安全性：该类本身不存储状态，主要依赖{@link GXExtensionRepository}的线程安全性，
 * 而GXExtensionRepository使用ConcurrentHashMap保证了线程安全。
 * <p>
 * 使用示例：
 * <pre>
 * // 通常不需要直接使用该类，而是通过Spring自动注入和GXExtensionBootstrap自动注册
 * // 但如果需要手动注册，可以这样使用：
 * 
 * @Component
 * public class MyService {
 *     @Resource
 *     private GXExtensionRegister extensionRegister;
 *     
 *     public void registerExtension(GXExtensionPoint extension) {
 *         extensionRegister.doRegistration(extension);
 *     }
 * }
 * </pre>
 *
 * @author britton
 * @see GXExtension 扩展点注解
 * @see GXExtensions 扩展点集合注解
 * @see GXExtensionRepository 扩展仓库
 * @see GXExtensionBootstrap 扩展启动类
 */
@Component
@Slf4j
public class GXExtensionRegister {
    /**
     * 扩展点的名字
     */
    public static final String EXTENSION_EXT_PT_NAMING = "ExtPoint";

    @Resource
    private GXExtensionRepository extensionRepository;

    /**
     * 注册单个扩展点实现类
     * <p>
     * 该方法处理标记了{@link GXExtension}注解的扩展点实现类，将其注册到扩展仓库中。
     * 如果扩展点已经注册，会发出警告日志但不会影响后续使用。
     *
     * @param extensionObject 扩展点实现对象，不能为null
     * @throws NullPointerException 如果extensionObject为null
     */
    public void doRegistration(GXExtensionPoint extensionObject) {
        Objects.requireNonNull(extensionObject, "Extension object cannot be null");
        
        Class<?> extensionClz = extensionObject.getClass();
        if (AopUtils.isAopProxy(extensionObject)) {
            extensionClz = ClassUtils.getUserClass(extensionObject);
        }
        GXExtension extensionAnn = AnnotationUtils.findAnnotation(extensionClz, GXExtension.class);
        if (Objects.nonNull(extensionAnn)) {
            GXBizScenario bizScenario = GXBizScenario.valueOf(extensionAnn.bizId(), extensionAnn.useCase(), extensionAnn.scenario());
            GXExtensionCoordinate extensionCoordinate = new GXExtensionCoordinate(calculateExtensionPoint(extensionClz), bizScenario.getUniqueIdentity());
            GXExtensionPoint preVal = extensionRepository.getExtensionRepo().put(extensionCoordinate, extensionObject);
            if (preVal != null) {
                // 如果已经注册 , 则发出警告信息 , 不影响后面使用
                GXLoggerUtils.logWarn(log, CharSequenceUtil.format("Duplicate registration is not allowed for : {}", extensionCoordinate));
            }
        }
    }

    /**
     * 注册多个扩展点实现类
     * <p>
     * 该方法处理标记了{@link GXExtensions}注解的扩展点实现类，将其注册到扩展仓库中。
     * 支持两种方式的注册：
     * 1. 通过{@link GXExtensions#value()}指定的多个{@link GXExtension}注解
     * 2. 通过{@link GXExtensions#bizId()}, {@link GXExtensions#useCase()}, {@link GXExtensions#scenario()}指定的笛卡尔积组合
     * <p>
     * 如果扩展点已经注册，会发出警告日志但不会影响后续使用。
     *
     * @param extensionObject 扩展点实现对象，不能为null
     * @throws NullPointerException 如果extensionObject为null或extensionsAnnotation为null
     */
    public void doRegistrationExtensions(GXExtensionPoint extensionObject) {
        Objects.requireNonNull(extensionObject, "Extension object cannot be null");
        
        Class<?> extensionClz = extensionObject.getClass();
        if (AopUtils.isAopProxy(extensionObject)) {
            extensionClz = ClassUtils.getUserClass(extensionObject);
        }

        GXExtensions extensionsAnnotation = AnnotationUtils.findAnnotation(extensionClz, GXExtensions.class);
        Objects.requireNonNull(extensionsAnnotation, "GXExtensions annotation not found on " + extensionClz.getName());
        
        // 处理value()中的GXExtension数组
        GXExtension[] extensions = extensionsAnnotation.value();
        if (!ObjectUtils.isEmpty(extensions)) {
            for (GXExtension extensionAnn : extensions) {
                GXBizScenario bizScenario = GXBizScenario.valueOf(extensionAnn.bizId(), extensionAnn.useCase(), extensionAnn.scenario());
                GXExtensionCoordinate extensionCoordinate = new GXExtensionCoordinate(calculateExtensionPoint(extensionClz), bizScenario.getUniqueIdentity());
                GXExtensionPoint preVal = extensionRepository.getExtensionRepo().put(extensionCoordinate, extensionObject);
                if (preVal != null) {
                    // 如果已经注册 , 则发出警告信息 , 不影响后面使用
                    GXLoggerUtils.logWarn(log, CharSequenceUtil.format("Duplicate registration is not allowed for : {}", extensionCoordinate));
                }
            }
        }

        // 处理bizId、useCase、scenario的笛卡尔积组合
        String[] bizIds = extensionsAnnotation.bizId();
        String[] useCases = extensionsAnnotation.useCase();
        String[] scenarios = extensionsAnnotation.scenario();
        for (String bizId : bizIds) {
            for (String useCase : useCases) {
                for (String scenario : scenarios) {
                    GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
                    GXExtensionCoordinate extensionCoordinate = new GXExtensionCoordinate(calculateExtensionPoint(extensionClz), bizScenario.getUniqueIdentity());
                    GXExtensionPoint preVal = extensionRepository.getExtensionRepo().put(extensionCoordinate, extensionObject);
                    if (preVal != null) {
                        GXLoggerUtils.logWarn(log, CharSequenceUtil.format("Duplicate registration is not allowed for : {}", extensionCoordinate));
                    }
                }
            }
        }
    }

    /**
     * 计算扩展点的全限定类名
     * <p>
     * 该方法通过查找目标类实现的接口，找到符合命名规范的扩展点接口。
     * 扩展点接口的简单名称必须包含{@link #EXTENSION_EXT_PT_NAMING}字符串。
     * <p>
     * 线程安全性：该方法是纯函数，不依赖实例状态，线程安全。
     *
     * @param targetClz 目标类型，不能为null
     * @return 扩展点接口的全限定类名
     * @throws GXBusinessException 如果目标类没有实现任何接口，或者没有找到符合命名规范的扩展点接口
     * @throws NullPointerException 如果targetClz为null
     */
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
