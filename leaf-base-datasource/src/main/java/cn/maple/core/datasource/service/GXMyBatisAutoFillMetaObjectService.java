package cn.maple.core.datasource.service;

public interface GXMyBatisAutoFillMetaObjectService {
    default String getCreatedBy() {
        return "unknown";
    }

    default String getUpdatedBy() {
        return "unknown";
    }
    
    default Object getTenantId() {
        return null;
    }
}
