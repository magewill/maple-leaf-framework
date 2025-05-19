package cn.maple.core.framework.dto.inner.permission;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限内部DTO基类
 * <p>
 * 该类用于封装系统权限相关的基本信息，包括权限名称、权限码、所属模块等
 * 主要用于权限管理、权限验证、菜单权限控制等场景
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个权限对象
 * GXBasePermissionInnerDto permission = GXBasePermissionInnerDto.builder()
 *     .permissionName("用户管理")
 *     .permissionCode("user:manage")
 *     .moduleName("系统管理")
 *     .moduleCode("system")
 *     .build();
 *
 * // 在权限验证中使用
 * boolean hasPermission = permissionService.checkPermission(userId, permission.getPermissionCode());
 *
 * // 构建权限树
 * List<GXBasePermissionInnerDto> permissions = permissionService.getUserPermissions(userId);
 * Map<String, List<GXBasePermissionInnerDto>> modulePermissions = permissions.stream()
 *     .collect(Collectors.groupingBy(GXBasePermissionInnerDto::getModuleCode));
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明：
 * 1. 权限码应当遵循一定的命名规范，如模块:操作的格式
 * 2. 权限验证应当在服务端执行，不能仅依赖前端验证
 * 3. 敏感操作应当配置更严格的权限控制
 * 4. 权限分配应当遵循最小权限原则
 * </p>
 *
 * <p>
 * 性能优化说明：
 * 1. 权限数据可以考虑缓存，减少频繁查询数据库
 * 2. 对于复杂的权限树结构，可以预先计算并缓存
 * 3. 权限验证逻辑应当高效，避免在频繁调用的接口中执行复杂的权限计算
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
public class GXBasePermissionInnerDto extends GXBaseDto {
    /**
     * 权限名字
     */
    private String permissionName;

    /**
     * 权限码
     */
    private String permissionCode;

    /**
     * 权限所属模块名字
     */
    private String moduleName;

    /**
     * 权限所属模块code
     */
    private String moduleCode;
}
