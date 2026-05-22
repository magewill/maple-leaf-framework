package cn.maple.debezium.services;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.util.GXSpringContextUtils;
import cn.maple.debezium.config.GXDebeziumEngineLockConfig;

/**
 * Business extension point for Debezium CDC events.
 */
public interface GXDebeziumService {
    /**
     * Redis lock name format.
     */
    String LOCK_NAME_FORMAT = "initial-engine-lock:{}:{}";

    /**
     * Handles a Debezium payload. The payload keeps Debezium field names such as
     * {@code op}, {@code before}, {@code after}, {@code source}, and {@code ts_ms}.
     *
     * @param data Debezium payload data
     */
    void processCaptureDataChange(Dict data);

    /**
     * Tries to claim the distributed engine owner slot.
     *
     * @param lockKey lock key
     * @return true if this instance owns the slot
     * @deprecated use {@link GXDebeziumEngineLockConfig#tryLock(String)} instead
     */
    @Deprecated
    default boolean tryInitialEngineLock(String lockKey) {
        return getRequiredEngineLockConfig().tryLock(lockKey);
    }

    /**
     * @deprecated use {@link GXDebeziumEngineLockConfig#lock(String)} instead
     */
    @Deprecated
    default void initialEngineLock(String lockKey) {
        getRequiredEngineLockConfig().lock(lockKey);
    }

    /**
     * Renews the distributed engine owner slot while the engine is running.
     *
     * @param lockKey lock key
     * @return true if the slot still exists and was renewed
     * @deprecated use {@link GXDebeziumEngineLockConfig#renew(String)} instead
     */
    @Deprecated
    default boolean renewInitialEngineLock(String lockKey) {
        return getRequiredEngineLockConfig().renew(lockKey);
    }

    /**
     * Releases the distributed engine owner slot.
     *
     * @param lockKey lock key
     * @deprecated use {@link GXDebeziumEngineLockConfig#unlock(String)} instead
     */
    @Deprecated
    default void initialEngineUnLock(String lockKey) {
        getRequiredEngineLockConfig().unlock(lockKey);
    }

    /**
     * Checks whether an engine owner slot exists.
     *
     * @param lockKey lock key
     * @return true if the slot exists
     * @deprecated use {@link GXDebeziumEngineLockConfig#isLocked(String)} instead
     */
    @Deprecated
    default boolean isEngineInitialized(String lockKey) {
        return getRequiredEngineLockConfig().isLocked(lockKey);
    }

    /**
     * Returns the owner token used by this service instance for Debezium engine lock operations.
     *
     * @deprecated engine lock ownership is managed by {@link GXDebeziumEngineLockConfig}
     */
    @Deprecated
    default String getEngineLockValue() {
        return getRequiredEngineLockConfig().getOwnerToken();
    }

    private static GXDebeziumEngineLockConfig getRequiredEngineLockConfig() {
        GXDebeziumEngineLockConfig engineLockConfig = GXSpringContextUtils.getBean(GXDebeziumEngineLockConfig.class);
        if (engineLockConfig == null) {
            throw new IllegalStateException("GXDebeziumEngineLockConfig bean is required for Debezium engine lock");
        }
        return engineLockConfig;
    }
}
