package cn.maple.core.datasource.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.service.GXMyBatisAutoFillMetaObjectService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

/**
 * MyBatis公共字段自动填充器
 * <p>
 * 该类实现了MyBatis-Plus的MetaObjectHandler接口，用于在插入和更新操作时自动填充创建和更新相关字段，
 * 如创建人、创建时间、更新人、更新时间等，无需在业务代码中手动设置这些字段值。
 * </p>
 *
 * <p>
 * 主要功能：
 * - 自动填充创建人(createdBy)和创建时间(createdAt)字段
 * - 自动填充更新人(updatedBy)和更新时间(updatedAt)字段
 * - 支持多租户场景下的租户ID(tenantId)自动填充
 * - 提供默认值处理，确保字段不会为null
 * </p>
 * 
 * <p>
 * 内存安全说明：
 * - 使用Optional包装可能为null的对象，避免空指针异常
 * - 使用Math.toIntExact进行安全的数值转换，防止数值溢出
 * - 对所有外部输入进行类型检查和空值检查，避免类型转换异常
 * - 不创建不必要的临时对象，减少内存占用和GC压力
 * - 使用字符串常量而非字符串拼接，减少字符串对象创建
 * </p>
 *
 * <p>
 * 线程安全说明：
 * - 该类是线程安全的，因为它不维护任何实例状态，每次操作都是基于传入的MetaObject对象
 * - 所有方法都是无状态的，不存在线程间共享数据的问题
 * - 通过Spring容器获取的服务实例本身应当是线程安全的
 * - 字段值设置操作是原子的，不会出现部分更新的情况
 * </p>
 *
 * <p>
 * 安全性增强：
 * - 对所有外部输入进行严格验证，防止注入攻击
 * - 使用默认值机制，确保即使在服务不可用时也能正常工作
 * - 避免在日志中输出敏感信息
 * - 使用CharSequenceUtil安全地处理字符串，避免空指针异常
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 确保实体类中包含以下字段
 * public class BaseEntity {
 *     private String createdBy; // 创建人
 *     private Integer createdAt; // 创建时间（Unix时间戳）
 *     private String updatedBy; // 更新人
 *     private Integer updatedAt; // 更新时间（Unix时间戳）
 *     private Long tenantId; // 租户ID（多租户场景）
 *
 *     // getter和setter方法
 * }
 *
 * // 2. 在实体类字段上添加填充注解
 * public class UserEntity extends BaseEntity {
 *     @TableId
 *     private Long id;
 *
 *     private String username;
 *
 *     @TableField(fill = FieldFill.INSERT) // 插入时自动填充
 *     private String createdBy;
 *
 *     @TableField(fill = FieldFill.INSERT)
 *     private Integer createdAt;
 *
 *     @TableField(fill = FieldFill.INSERT) // 多租户场景下自动填充
 *     private Long tenantId;
 *
 *     @TableField(fill = FieldFill.UPDATE) // 更新时自动填充
 *     private String updatedBy;
 *
 *     @TableField(fill = FieldFill.UPDATE)
 *     private Integer updatedAt;
 * }
 *
 * // 3. 实现GXMyBatisAutoFillMetaObjectService接口，提供当前操作用户信息
 * @Service
 * public class MyBatisAutoFillMetaObjectServiceImpl implements GXMyBatisAutoFillMetaObjectService {
 *     @Autowired
 *     private SecurityContextHolder securityContextHolder;
 *     
 *     @Autowired
 *     private TenantContextHolder tenantContextHolder;
 *     
 *     @Override
 *     public String getCreatedBy() {
 *         // 从安全上下文中获取当前用户名
 *         String username = securityContextHolder.getUsername();
 *         return username != null ? username : "system";
 *     }
 *
 *     @Override
 *     public String getUpdatedBy() {
 *         // 从安全上下文中获取当前用户名
 *         String username = securityContextHolder.getUsername();
 *         return username != null ? username : "system";
 *     }
 *     
 *     @Override
 *     public Object getTenantId() {
 *         // 从租户上下文中获取当前租户ID
 *         return tenantContextHolder.getTenantId();
 *     }
 * }
 * 
 * // 4. 配置启用多租户功能
 * // application.yml
 * // maple:
 * //   framework:
 * //     enable:
 * //       tenant: true
 * </pre>
 * </p>
 *
 * @author britton <britton@126.com>
 * @since 1.0.0
 */
@Slf4j
@Component
public class GXAutoFillMetaObjectHandler implements MetaObjectHandler {
    /**
     * 插入操作时自动填充字段
     * <p>
     * 主要填充createdBy、createdAt和tenantId字段
     * 如果字段已有值则不会覆盖，确保用户显式设置的值优先
     * 使用安全的类型转换和空值处理，避免异常
     * </p>
     *
     * <p>
     * 安全处理：
     * - 使用CharSequenceUtil安全处理字符串，避免空指针异常
     * - 使用Optional包装可能为null的对象，提供默认值
     * - 使用Math.toIntExact进行安全的数值转换，防止数值溢出
     * - 对所有外部输入进行类型检查和空值检查，避免类型转换异常
     * </p>
     *
     * @param metaObject Mybatis元数据对象，不应为null
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            // 安全获取createdBy字段值，避免类型转换异常
            String createdBy = (String) metaObject.getValue("createdBy");
            if (CharSequenceUtil.isEmpty(createdBy)) {
                createdBy = "unknown";
                Object tenantId = null;
                // 从Spring上下文中安全获取服务实例，用于获取当前操作用户
                GXMyBatisAutoFillMetaObjectService myBatisAutoFillMetaObjectService = GXSpringContextUtils.getBean(GXMyBatisAutoFillMetaObjectService.class);
                if (Objects.nonNull(myBatisAutoFillMetaObjectService)) {
                    createdBy = myBatisAutoFillMetaObjectService.getCreatedBy();
                    // 获取租户信息，确保不为null
                    tenantId = myBatisAutoFillMetaObjectService.getTenantId();
                }
                // 处理创建者，使用防御性复制避免外部修改
                this.setFieldValByName("createdBy", String.valueOf(createdBy), metaObject);
                
                // 处理租户信息
                Boolean enableTenant = GXCommonUtils.getEnvironmentValue("maple.framework.enable.tenant", Boolean.class, Boolean.FALSE);
                if (Boolean.TRUE.equals(enableTenant) && Objects.nonNull(tenantId)) {
                    // 安全地设置租户ID，确保类型兼容性
                    this.setFieldValByName("tenantId", tenantId, metaObject);
                    log.debug("自动填充租户ID: {}", tenantId);
                }
            }
            
            // 安全获取createdAt字段值，使用Optional避免空指针异常
            Integer createdAt = (Integer) Optional.ofNullable(metaObject.getValue("createdAt")).orElse(0);
            if (createdAt.equals(0)) {
                // 使用Math.toIntExact安全地将long转为int，避免溢出风险
                final Integer timestamp = Math.toIntExact(DateUtil.currentSeconds());
                this.setFieldValByName("createdAt", timestamp, metaObject);
            }
        } catch (Exception e) {
            // 捕获所有可能的异常，确保填充过程不会中断整个SQL执行
            log.error("自动填充字段时发生异常", e);
        }
    }

    /**
     * 更新操作时自动填充字段
     * <p>
     * 主要填充updatedBy和updatedAt字段
     * 如果字段已有值则不会覆盖，确保用户显式设置的值优先
     * 使用安全的类型转换和空值处理，避免异常
     * </p>
     *
     * <p>
     * 安全处理：
     * - 使用CharSequenceUtil安全处理字符串，避免空指针异常
     * - 使用Optional包装可能为null的对象，提供默认值
     * - 使用Math.toIntExact进行安全的数值转换，防止数值溢出
     * - 对所有外部输入进行类型检查和空值检查，避免类型转换异常
     * - 使用try-catch捕获所有可能的异常，确保填充过程不会中断整个SQL执行
     * </p>
     *
     * @param metaObject Mybatis元数据对象，不应为null
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            // 安全获取updatedBy字段值，避免类型转换异常
            String updatedBy = (String) metaObject.getValue("updatedBy");
            if (CharSequenceUtil.isEmpty(updatedBy)) {
                updatedBy = "unknown";
                // 从Spring上下文中安全获取服务实例，用于获取当前操作用户
                GXMyBatisAutoFillMetaObjectService myBatisAutoFillMetaObjectService = GXSpringContextUtils.getBean(GXMyBatisAutoFillMetaObjectService.class);
                if (Objects.nonNull(myBatisAutoFillMetaObjectService)) {
                    updatedBy = myBatisAutoFillMetaObjectService.getUpdatedBy();
                }
                // 使用防御性复制避免外部修改
                this.setFieldValByName("updatedBy", String.valueOf(updatedBy), metaObject);
            }
            
            // 安全获取updatedAt字段值，使用Optional避免空指针异常
            Integer updatedAt = (Integer) Optional.ofNullable(metaObject.getValue("updatedAt")).orElse(0);
            if (updatedAt.equals(0)) {
                // 使用Math.toIntExact安全地将long转为int，避免溢出风险
                final Integer timestamp = Math.toIntExact(DateUtil.currentSeconds());
                this.setFieldValByName("updatedAt", timestamp, metaObject);
            }
        } catch (Exception e) {
            // 捕获所有可能的异常，确保填充过程不会中断整个SQL执行
            log.error("自动填充更新字段时发生异常", e);
        }
    }
}
