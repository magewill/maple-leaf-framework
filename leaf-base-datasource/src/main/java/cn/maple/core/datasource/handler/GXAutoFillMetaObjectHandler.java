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

@Slf4j
@Component
public class GXAutoFillMetaObjectHandler implements MetaObjectHandler {
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
            log.error("自动填充字段时发生异常", e);
        }
    }

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
