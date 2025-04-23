package cn.maple.core.datasource.service;

/**
 * MyBatis Plus的自动填充数据获取服务接口
 * <p>
 * 该接口用于在实体对象被持久化到数据库时，自动填充创建者和更新者信息。
 * 通常与MyBatis Plus的自动填充功能配合使用，在实体类的字段上添加
 * {@code @TableField(fill = FieldFill.INSERT)} 或 {@code @TableField(fill = FieldFill.INSERT_UPDATE)} 注解。
 * </p>
 * 
 * <p>
 * 内存安全说明：
 * - 接口默认实现不创建任何对象，避免内存泄漏
 * - 实现类应当避免在方法中创建大量临时对象
 * - 建议使用线程安全的缓存机制存储频繁访问的用户信息，减少重复获取
 * - 返回的对象应当是不可变的或者是防御性复制的，避免外部修改
 * </p>
 * 
 * <p>
 * 线程安全说明：
 * - 该接口的实现类应确保在多线程环境下是安全的，特别是在获取当前用户信息时
 * - 避免使用静态变量存储用户状态，除非有适当的同步机制
 * - 如果使用ThreadLocal存储用户信息，必须确保在请求结束时正确清理，防止内存泄漏
 * - 实现类中的所有方法都应当是线程安全的，不应依赖实例状态
 * </p>
 * 
 * <p>
 * 安全建议：
 * - 实现类应当对获取的用户信息进行验证，防止伪造身份
 * - 避免在日志中输出完整的用户敏感信息
 * - 考虑对租户ID进行加密或混淆处理，避免直接暴露真实租户标识
 * - 实现类应当防范SQL注入风险，特别是在动态构建租户条件时
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 实现接口
 * @Service
 * public class MyAutoFillMetaObjectServiceImpl implements GXMyBatisAutoFillMetaObjectService {
 *     @Autowired
 *     private UserContextHolder userContextHolder;
 *
 *     @Override
 *     public String getCreatedBy() {
 *         // 从上下文中获取当前用户信息
 *         UserInfo currentUser = userContextHolder.getCurrentUser();
 *         return currentUser != null ? currentUser.getUsername() : "system";
 *     }
 *
 *     @Override
 *     public String getUpdatedBy() {
 *         // 从上下文中获取当前用户信息
 *         UserInfo currentUser = userContextHolder.getCurrentUser();
 *         return currentUser != null ? currentUser.getUsername() : "system";
 *     }
 *     
 *     @Override
 *     public Object getTenantId() {
 *         // 从上下文中获取当前租户ID
 *         Long tenantId = userContextHolder.getTenantId();
 *         // 确保返回值不为null，提供默认值
 *         return tenantId != null ? tenantId : 0L;
 *     }
 * }
 *
 * // 2. 在实体类中使用
 * @TableName("t_user")
 * public class UserEntity extends GXBaseModel {
 *     // 其他字段...
 *
 *     @TableField(fill = FieldFill.INSERT)
 *     private String createdBy;
 *
 *     @TableField(fill = FieldFill.INSERT_UPDATE)
 *     private String updatedBy;
 *     
 *     @TableField(fill = FieldFill.INSERT)
 *     private Long tenantId;
 *
 *     // getter和setter方法...
 * }
 *
 * // 3. 在MyBatis Plus的元对象处理器中使用
 * @Component
 * public class MyMetaObjectHandler implements MetaObjectHandler {
 *     @Autowired
 *     private GXMyBatisAutoFillMetaObjectService autoFillService;
 *
 *     @Override
 *     public void insertFill(MetaObject metaObject) {
 *         this.strictInsertFill(metaObject, "createdBy", String.class, autoFillService.getCreatedBy());
 *         this.strictInsertFill(metaObject, "updatedBy", String.class, autoFillService.getUpdatedBy());
 *         // 多租户场景下填充租户ID
 *         this.strictInsertFill(metaObject, "tenantId", Long.class, autoFillService.getTenantId());
 *     }
 *
 *     @Override
 *     public void updateFill(MetaObject metaObject) {
 *         this.strictUpdateFill(metaObject, "updatedBy", String.class, autoFillService.getUpdatedBy());
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author 塵渊  britton@126.com
 */
public interface GXMyBatisAutoFillMetaObjectService {
    /**
     * 获取创建者信息
     * <p>
     * 该方法用于获取当前操作的创建者信息，通常是当前登录用户的标识。
     * 在实体被首次插入数据库时调用。
     * </p>
     * <p>
     * 默认实现返回"unknown"，实现类应根据实际业务需求重写此方法。
     * </p>
     *
     * @return 创建者标识，通常是用户名或用户ID
     */
    default String getCreatedBy() {
        return "unknown";
    }

    /**
     * 获取更新者信息
     * <p>
     * 该方法用于获取当前操作的更新者信息，通常是当前登录用户的标识。
     * 在实体被更新时调用。
     * </p>
     * <p>
     * 默认实现返回"unknown"，实现类应根据实际业务需求重写此方法。
     * </p>
     *
     * @return 更新者标识，通常是用户名或用户ID
     */
    default String getUpdatedBy() {
        return "unknown";
    }

    /**
     * 获取当前租户ID
     * <p>
     * 返回一个租户ID，用于在新建数据时插入数据库指定字段，方便后期SQL查询中添加租户过滤条件。
     * 默认实现返回固定值NULL，表示不开启租户模式。
     * 实际应用中应该根据系统上下文动态获取租户ID。
     * </p>
     *
     * <p>
     * 实现此方法时应注意：
     * - 返回值不应为null，至少提供一个默认值
     * - 考虑租户ID获取失败的情况，提供合理的错误处理
     * - 可以从请求头、线程上下文、安全上下文等获取租户ID
     * </p>
     *
     * @return 表示租户ID的SQL表达式对象
     */
    default Object getTenantId() {
        return null;
    }
}
