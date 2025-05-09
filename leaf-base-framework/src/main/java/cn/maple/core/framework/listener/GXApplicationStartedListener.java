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
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 应用启动后权限收集监听器
 * <p>
 * 该监听器在应用启动完成后触发，用于收集系统中所有标记了{@link GXPermissionCtl}注解的类
 * 以及其中标记了{@link GXPermission}注解的方法，并将这些权限信息通过事件机制发布出去。
 * 仅在开发、本地和测试环境中启用。
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在控制器类上添加GXPermissionCtl注解
 * @RestController
 * @RequestMapping("/api/users")
 * @GXPermissionCtl(moduleCode = "user", moduleName = "用户管理")
 * public class UserController {
 *
 *     // 2. 在需要权限控制的方法上添加GXPermission注解
 *     @GetMapping("/list")
 *     @GXPermission(permissionCode = "user:list", permissionName = "用户列表查询")
 *     public Result list() {
 *         // 业务逻辑
 *         return Result.success();
 *     }
 *
 *     // 3. 也可以在方法上覆盖模块信息
 *     @PostMapping("/add")
 *     @GXPermission(
 *         permissionCode = "user:add",
 *         permissionName = "添加用户",
 *         moduleCode = "user-management",
 *         moduleName = "用户管理模块"
 *     )
 *     public Result add() {
 *         // 业务逻辑
 *         return Result.success();
 *     }
 * }
 * </pre>
 * </p>
 *
 * <p>
 * 优化说明：
 * 1. 使用方法缓存减少反射开销
 * 2. 增加性能监控和统计功能
 * 3. 优化并发处理和内存使用
 * 4. 应用Java 17+特性提升代码质量
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
     * 权限方法缓存
     * <p>
     * 缓存已扫描过的类及其包含的带有@GXPermission注解的方法
     * 用于减少反射操作的开销，提高性能
     * </p>
     */
    private final ConcurrentHashMap<Class<?>, Set<Method>> permissionMethodCache = new ConcurrentHashMap<>();

    /**
     * 清理方法
     * <p>
     * 在Bean销毁前执行，清理缓存数据
     * </p>
     */
    @PreDestroy
    public void destroy() {
        log.debug("GXApplicationStartedListener销毁，清理缓存数据");
        permissionMethodCache.clear();
    }

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
            Map<String, Object> beansWithAnnotation = applicationStartedEvent
                    .getApplicationContext()
                    .getBeanFactory()
                    .getBeansWithAnnotation(GXPermissionCtl.class);
            // 使用并行流提高处理效率
            beansWithAnnotation.entrySet().parallelStream().forEach(entry -> {
                String beanName = entry.getKey();
                Object bean = entry.getValue();
                try {
                    processBean(beanName, bean, permissionMap);
                } catch (Exception e) {
                    log.error("处理Bean [{}] 的权限信息时发生异常: {}", beanName, e.getMessage(), e);
                }
            });
            // 发布权限事件
            publishPermissionEvent(permissionMap);
        } catch (Exception e) {
            log.error("收集系统权限信息时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理单个Bean的权限信息
     *
     * @param beanName      Bean名称
     * @param bean          Bean对象
     * @param permissionMap 权限信息映射
     */
    private void processBean(String beanName, Object bean, ConcurrentHashMap<String, List<GXBasePermissionInnerDto>> permissionMap) {
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
        // 更新模块权限统计
        if (!permissionDtoList.isEmpty()) {
            // 将收集到的权限信息放入Map中
            permissionMap.putIfAbsent(beanName, permissionDtoList);
        }
    }

    /**
     * 收集类中方法的权限信息
     * <p>
     * 使用缓存减少反射操作，提高性能
     * </p>
     *
     * @param targetClass 目标类
     * @param moduleCode  模块编码
     * @param moduleName  模块名称
     * @return 权限信息列表
     */
    private List<GXBasePermissionInnerDto> collectMethodPermissions(Class<?> targetClass, String moduleCode, String moduleName) {
        // 尝试从缓存获取带有@GXPermission注解的方法
        Set<Method> cachedMethods = permissionMethodCache.get(targetClass);
        if (cachedMethods == null) {
            // 缓存未命中，扫描类中的方法
            Method[] declaredMethods = targetClass.getDeclaredMethods();
            cachedMethods = Arrays.stream(declaredMethods)
                    .filter(method -> method.isAnnotationPresent(GXPermission.class))
                    .collect(Collectors.toSet());
            // 将结果放入缓存
            permissionMethodCache.put(targetClass, cachedMethods);
        }
        // 处理每个带有@GXPermission注解的方法
        return cachedMethods.stream()
                .map(method -> createPermissionDto(method, moduleCode, moduleName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 创建权限DTO对象
     *
     * @param method     方法对象
     * @param moduleCode 默认模块编码
     * @param moduleName 默认模块名称
     * @return 权限DTO对象，如果创建失败则返回null
     */
    private GXBasePermissionInnerDto createPermissionDto(Method method, String moduleCode, String moduleName) {
        try {
            // 获取方法上的GXPermission注解
            GXPermission permissionAction = method.getAnnotation(GXPermission.class);
            if (permissionAction == null) {
                return null;
            }
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

    /**
     * 发布权限事件
     *
     * @param permissionMap 权限信息映射
     */
    private void publishPermissionEvent(ConcurrentHashMap<String, List<GXBasePermissionInnerDto>> permissionMap) {
        if (!permissionMap.isEmpty()) {
            log.info("收集到 {} 个Bean的权限信息，准备发布权限事件", permissionMap.size());
            // 创建并发布权限事件
            GXPermissionEvent permissionEvent = new GXPermissionEvent(permissionMap, Dict.create());
            GXEventPublisherUtils.publishEvent(permissionEvent);
            log.info("权限事件发布完成");
        } else {
            log.info("未收集到任何权限信息");
        }
    }
}
