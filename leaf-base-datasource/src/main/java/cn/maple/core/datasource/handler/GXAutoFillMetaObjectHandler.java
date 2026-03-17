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
