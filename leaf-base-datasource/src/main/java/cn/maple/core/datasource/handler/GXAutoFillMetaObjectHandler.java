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
            GXMyBatisAutoFillMetaObjectService autoFillService = getAutoFillService();
            String createdBy = asString(metaObject.getValue("createdBy"));
            if (CharSequenceUtil.isEmpty(createdBy)) {
                String fillCreatedBy = Objects.nonNull(autoFillService) ? autoFillService.getCreatedBy() : null;
                this.setFieldValByName("createdBy", defaultIfBlank(fillCreatedBy, "unknown"), metaObject);
            }

            fillTenantId(metaObject, autoFillService);
            fillTimestampIfAbsent(metaObject, "createdAt");
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
            GXMyBatisAutoFillMetaObjectService autoFillService = getAutoFillService();
            String updatedBy = asString(metaObject.getValue("updatedBy"));
            if (CharSequenceUtil.isEmpty(updatedBy)) {
                String fillUpdatedBy = Objects.nonNull(autoFillService) ? autoFillService.getUpdatedBy() : null;
                this.setFieldValByName("updatedBy", defaultIfBlank(fillUpdatedBy, "unknown"), metaObject);
            }
            fillTimestampIfAbsent(metaObject, "updatedAt");
        } catch (Exception e) {
            // 捕获所有可能的异常，确保填充过程不会中断整个SQL执行
            log.error("自动填充更新字段时发生异常", e);
        }
    }

    private GXMyBatisAutoFillMetaObjectService getAutoFillService() {
        return GXSpringContextUtils.getBean(GXMyBatisAutoFillMetaObjectService.class);
    }

    private void fillTenantId(MetaObject metaObject, GXMyBatisAutoFillMetaObjectService autoFillService) {
        Boolean enableTenant = GXCommonUtils.getEnvironmentValue("maple.framework.enable.tenant", Boolean.class, Boolean.FALSE);
        if (!Boolean.TRUE.equals(enableTenant) || Objects.isNull(autoFillService)) {
            return;
        }

        Object currentTenantId = metaObject.getValue("tenantId");
        if (Objects.nonNull(currentTenantId)) {
            return;
        }

        Object tenantId = autoFillService.getTenantId();
        if (Objects.nonNull(tenantId)) {
            this.setFieldValByName("tenantId", tenantId, metaObject);
            log.debug("自动填充租户ID: {}", tenantId);
        }
    }

    private void fillTimestampIfAbsent(MetaObject metaObject, String fieldName) {
        Object fieldValue = metaObject.getValue(fieldName);
        if (isNullOrZero(fieldValue)) {
            this.setFieldValByName(fieldName, Math.toIntExact(DateUtil.currentSeconds()), metaObject);
        }
    }

    private String asString(Object value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private boolean isNullOrZero(Object value) {
        if (Objects.isNull(value)) {
            return true;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue() == 0L;
        }
        if (value instanceof CharSequence) {
            return CharSequenceUtil.isBlank((CharSequence) value) || "0".contentEquals((CharSequence) value);
        }
        return false;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return CharSequenceUtil.isBlank(value) ? defaultValue : value;
    }
}
