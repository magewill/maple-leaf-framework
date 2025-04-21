package cn.maple.core.framework.listener;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXPermission;
import cn.maple.core.framework.annotation.GXPermissionCtl;
import cn.maple.core.framework.dto.inner.permission.GXBasePermissionInnerDto;
import cn.maple.core.framework.event.GXPermissionEvent;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 应用启动后权限收集监听器
 * <p>
 * 该监听器在应用启动完成后触发，用于收集系统中所有标记了{@link GXPermissionCtl}注解的类
 * 以及其中标记了{@link GXPermission}注解的方法，并将这些权限信息通过事件机制发布出去。
 * 仅在开发、本地和测试环境中启用。
 * </p>
 *
 * @author maple
 * @since 1.0.0
 */
@Component
@Profile({"dev", "local", "test"})
@Slf4j
public class GXApplicationStartedListener implements ApplicationListener<ApplicationStartedEvent> {
    /**
     * 应用启动完成事件处理方法
     * <p>
     * 该方法会在Spring应用启动完成后被调用，用于收集系统中的权限信息并发布权限事件。
     * 具体流程：
     * 1. 获取所有标记了{@link GXPermissionCtl}注解的Bean
     * 2. 遍历这些Bean，收集其中标记了{@link GXPermission}注解的方法信息
     * 3. 将收集到的权限信息通过{@link GXPermissionEvent}事件发布出去
     * </p>
     *
     * @param applicationStartedEvent 应用启动完成事件
     */
    @Override
    public void onApplicationEvent(ApplicationStartedEvent applicationStartedEvent) {
        try {
            log.info("开始收集系统权限信息...");
            // 使用ConcurrentHashMap保证线程安全
            ConcurrentHashMap<String, List<GXBasePermissionInnerDto>> permissionMap = new ConcurrentHashMap<>();
            // 获取所有标记了GXPermissionCtl注解的Bean
            Map<String, Object> beansWithAnnotation = applicationStartedEvent.getApplicationContext()
                    .getBeanFactory()
                    .getBeansWithAnnotation(GXPermissionCtl.class);
            
            // 遍历所有标记了GXPermissionCtl注解的Bean
            beansWithAnnotation.forEach((beanName, bean) -> {
                try {
                    // 获取目标类（处理可能的代理类情况）
                    Class<?> targetClass = AopUtils.getTargetClass(bean);
                    // 获取类上的GXPermissionCtl注解
                    GXPermissionCtl permissionCtl = targetClass.getAnnotation(GXPermissionCtl.class);
                    if (permissionCtl == null) {
                        log.warn("Bean [{}] 类型为 [{}] 未找到GXPermissionCtl注解", beanName, targetClass.getName());
                        return;
                    }
                    
                    // 获取模块信息
                    String moduleCode = permissionCtl.moduleCode();
                    String moduleName = permissionCtl.moduleName();
                    
                    // 收集方法上的权限信息
                    List<GXBasePermissionInnerDto> permissionDtoList = collectMethodPermissions(targetClass, moduleCode, moduleName);
                    
                    // 将收集到的权限信息放入Map中
                    if (!permissionDtoList.isEmpty()) {
                        permissionMap.putIfAbsent(beanName, permissionDtoList);
                    }
                } catch (Exception e) {
                    log.error("处理Bean [{}] 的权限信息时发生异常: {}", beanName, e.getMessage(), e);
                }
            });
            
            // 发布权限事件
            if (!permissionMap.isEmpty()) {
                log.info("收集到 {} 个Bean的权限信息，准备发布权限事件", permissionMap.size());
                GXPermissionEvent permissionEvent = new GXPermissionEvent(permissionMap, Dict.create());
                GXEventPublisherUtils.publishEvent(permissionEvent);
                log.info("权限事件发布完成");
            } else {
                log.info("未收集到任何权限信息");
            }
        } catch (Exception e) {
            log.error("收集系统权限信息时发生异常: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 收集类中方法的权限信息
     *
     * @param targetClass 目标类
     * @param moduleCode  模块编码
     * @param moduleName  模块名称
     * @return 权限信息列表
     */
    private List<GXBasePermissionInnerDto> collectMethodPermissions(Class<?> targetClass, String moduleCode, String moduleName) {
        List<GXBasePermissionInnerDto> permissionDtoList = new ArrayList<>();
        Method[] declaredMethods = targetClass.getDeclaredMethods();
        
        for (Method declaredMethod : declaredMethods) {
            // 获取方法上的GXPermission注解
            GXPermission permissionAction = declaredMethod.getAnnotation(GXPermission.class);
            if (Objects.nonNull(permissionAction)) {
                // 获取权限信息
                String permissionModuleCode = permissionAction.moduleCode();
                String permissionModuleName = permissionAction.moduleName();
                
                // 如果方法上未指定模块信息，则使用类上的模块信息
                if (CharSequenceUtil.isEmpty(permissionModuleCode)) {
                    permissionModuleCode = moduleCode;
                }
                if (CharSequenceUtil.isEmpty(permissionModuleName)) {
                    permissionModuleName = moduleName;
                }
                
                // 构建权限DTO对象
                GXBasePermissionInnerDto permissionDto = GXBasePermissionInnerDto.builder()
                        .permissionCode(permissionAction.permissionCode())
                        .permissionName(permissionAction.permissionName())
                        .moduleName(permissionModuleName)
                        .moduleCode(permissionModuleCode)
                        .build();
                        
                permissionDtoList.add(permissionDto);
            }
        }
        
        return permissionDtoList;
    }
}
