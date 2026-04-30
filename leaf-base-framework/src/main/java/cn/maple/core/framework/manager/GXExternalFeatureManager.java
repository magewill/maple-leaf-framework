package cn.maple.core.framework.manager;

public interface GXExternalFeatureManager {
    default boolean checkConnection() {
        return true;
    }
}
