package cn.maple.core.framework.listener;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.annotation.GXPermission;
import cn.maple.core.framework.annotation.GXPermissionCtl;
import cn.maple.core.framework.dto.inner.permission.GXBasePermissionInnerDto;
import cn.maple.core.framework.event.GXPermissionEvent;
import cn.maple.core.framework.util.GXEventPublisherUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Profile({"dev", "local", "test"})
@Slf4j
public class GXApplicationStartedListener implements ApplicationListener<ApplicationStartedEvent> {
    private final ConcurrentHashMap<Class<?>, List<Method>> permissionMethodCache = new ConcurrentHashMap<>();

    @PreDestroy
    public void destroy() {
        log.debug("GXApplicationStartedListener销毁，清理缓存数据");
        permissionMethodCache.clear();
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent applicationStartedEvent) {
        try {
            log.info("开始收集系统权限信息...");
            Map<String, List<GXBasePermissionInnerDto>> permissionMap = new LinkedHashMap<>();
            Map<String, Object> beansWithAnnotation = applicationStartedEvent
                    .getApplicationContext()
                    .getBeanFactory()
                    .getBeansWithAnnotation(GXPermissionCtl.class);
            beansWithAnnotation.forEach((beanName, bean) -> {
                try {
                    processBean(beanName, bean, permissionMap);
                } catch (Exception e) {
                    log.error("处理Bean [{}] 的权限信息时发生异常: {}", beanName, e.getMessage(), e);
                }
            });
            publishPermissionEvent(permissionMap);
        } catch (Exception e) {
            log.error("收集系统权限信息时发生异常: {}", e.getMessage(), e);
        }
    }

    private void processBean(String beanName, Object bean, Map<String, List<GXBasePermissionInnerDto>> permissionMap) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        GXPermissionCtl permissionCtl = AnnotatedElementUtils.findMergedAnnotation(targetClass, GXPermissionCtl.class);
        if (permissionCtl == null) {
            log.warn("Bean [{}] 类型为 [{}] 未找到GXPermissionCtl注解", beanName, targetClass.getName());
            return;
        }
        String moduleCode = permissionCtl.moduleCode();
        String moduleName = permissionCtl.moduleName();
        List<GXBasePermissionInnerDto> permissionDtoList = collectMethodPermissions(targetClass, moduleCode, moduleName);
        if (!permissionDtoList.isEmpty()) {
            permissionMap.putIfAbsent(beanName, Collections.unmodifiableList(permissionDtoList));
        }
    }

    private List<GXBasePermissionInnerDto> collectMethodPermissions(Class<?> targetClass, String moduleCode, String moduleName) {
        List<Method> cachedMethods = permissionMethodCache.computeIfAbsent(targetClass, this::findPermissionMethods);
        return cachedMethods.stream()
                .map(method -> createPermissionDto(method, moduleCode, moduleName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Method> findPermissionMethods(Class<?> targetClass) {
        Map<String, Method> permissionMethods = new LinkedHashMap<>();
        Set<String> scannedSignatures = new HashSet<>();
        Class<?> currentClass = targetClass;
        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                String signature = buildMethodSignature(method);
                if (scannedSignatures.add(signature) && method.isAnnotationPresent(GXPermission.class)) {
                    permissionMethods.put(signature, method);
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        collectClassInterfacePermissionMethods(targetClass, permissionMethods);
        return Collections.unmodifiableList(new ArrayList<>(permissionMethods.values()));
    }

    private void collectClassInterfacePermissionMethods(Class<?> targetClass, Map<String, Method> permissionMethods) {
        for (Class<?> interfaceClass : targetClass.getInterfaces()) {
            collectInterfacePermissionMethods(interfaceClass, permissionMethods);
        }
        Class<?> superclass = targetClass.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            collectClassInterfacePermissionMethods(superclass, permissionMethods);
        }
    }

    private void collectInterfacePermissionMethods(Class<?> interfaceClass, Map<String, Method> permissionMethods) {
        for (Method method : interfaceClass.getMethods()) {
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            if (method.isAnnotationPresent(GXPermission.class)) {
                permissionMethods.putIfAbsent(buildMethodSignature(method), method);
            }
        }
        for (Class<?> parentInterface : interfaceClass.getInterfaces()) {
            collectInterfacePermissionMethods(parentInterface, permissionMethods);
        }
    }

    private String buildMethodSignature(Method method) {
        return method.getName() + Arrays.toString(method.getParameterTypes());
    }

    private GXBasePermissionInnerDto createPermissionDto(Method method, String moduleCode, String moduleName) {
        try {
            GXPermission permissionAction = method.getAnnotation(GXPermission.class);
            if (permissionAction == null) {
                return null;
            }
            String permissionModuleCode = permissionAction.moduleCode();
            String permissionModuleName = permissionAction.moduleName();
            if (CharSequenceUtil.isEmpty(permissionModuleCode)) {
                permissionModuleCode = moduleCode;
            }
            if (CharSequenceUtil.isEmpty(permissionModuleName)) {
                permissionModuleName = moduleName;
            }
            return GXBasePermissionInnerDto.builder()
                    .permissionCode(permissionAction.permissionCode())
                    .permissionName(permissionAction.permissionName())
                    .moduleName(permissionModuleName)
                    .moduleCode(permissionModuleCode).build();
        } catch (Exception e) {
            log.error("创建权限DTO对象时发生异常: {} - {}", method.getName(), e.getMessage());
            return null;
        }
    }

    private void publishPermissionEvent(Map<String, List<GXBasePermissionInnerDto>> permissionMap) {
        if (!permissionMap.isEmpty()) {
            log.info("收集到 {} 个Bean的权限信息，准备发布权限事件", permissionMap.size());
            GXPermissionEvent permissionEvent = new GXPermissionEvent(Collections.unmodifiableMap(permissionMap), Dict.create());
            GXEventPublisherUtils.publishEvent(permissionEvent);
            log.info("权限事件发布完成");
        } else {
            log.info("未收集到任何权限信息");
        }
    }
}
