package cn.maple.extension;

import cn.hutool.http.HttpStatus;
import cn.maple.extension.exception.GXExtensionException;
import cn.maple.extension.register.GXAbstractComponentExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Executes extension point implementations by business scenario.
 * <p>
 * Lookup order: full scenario, default scenario, default use case, then global default. Both entry
 * points (extension point type and extension point name) share the same order.
 */
@Component
@Slf4j
public class GXExtensionExecutor extends GXAbstractComponentExecutor {
    private static final String DEFAULT_BIZ_SCENARIO_IDENTITY = GXBizScenario.newDefault().getUniqueIdentity();

    @Resource
    private GXExtensionRepository extensionRepository;

    @Override
    protected <C> C locateComponent(Class<C> targetClz, GXBizScenario bizScenario) {
        Objects.requireNonNull(targetClz, "targetClz cannot be null");
        C extension = locateExtension(targetClz, bizScenario);
        logLocated(extension);
        return extension;
    }

    @Override
    protected <C> C locateComponent(String extensionPointName, String bizScenarioUniqueIdentity) {
        Objects.requireNonNull(extensionPointName, "extensionPointName cannot be null");
        Objects.requireNonNull(bizScenarioUniqueIdentity, "bizScenarioUniqueIdentity cannot be null");
        C extension = locateExtensionByName(extensionPointName,
                GXBizScenario.fromUniqueIdentity(bizScenarioUniqueIdentity));
        logLocated(extension);
        return extension;
    }

    /**
     * Locates an extension by applying the scenario fallback order.
     *
     * @param targetClz   extension point interface
     * @param bizScenario requested business scenario
     * @return matching implementation, never {@code null}
     * @throws GXExtensionException when no coordinate matches
     */
    protected <E> E locateExtension(Class<E> targetClz, GXBizScenario bizScenario) {
        Objects.requireNonNull(targetClz, "targetClz cannot be null");
        Objects.requireNonNull(bizScenario, "bizScenario cannot be null");
        return locateExtensionByName(targetClz.getName(), bizScenario);
    }

    private <E> E locateExtensionByName(String extensionPointName, GXBizScenario bizScenario) {
        if (extensionPointName == null || extensionPointName.isBlank()) {
            throw new IllegalArgumentException("Extension point name cannot be blank");
        }

        E extension = locate(extensionPointName, bizScenario.getUniqueIdentity());
        if (extension == null) {
            extension = locate(extensionPointName, bizScenario.getIdentityWithDefaultScenario());
        }
        if (extension == null) {
            extension = locate(extensionPointName, bizScenario.getIdentityWithDefaultUseCase());
        }
        if (extension == null) {
            extension = locate(extensionPointName, DEFAULT_BIZ_SCENARIO_IDENTITY);
        }
        if (extension == null) {
            throw new GXExtensionException("Can not find extension with ExtensionPoint: " + extensionPointName
                    + " BizScenario:" + bizScenario.getUniqueIdentity(), HttpStatus.HTTP_NOT_FOUND);
        }
        return extension;
    }

    private <E> E locate(String extensionPointName, String uniqueIdentity) {
        return cast(extensionRepository.findExtension(extensionPointName, uniqueIdentity).orElse(null));
    }

    @SuppressWarnings("unchecked")
    private <E> E cast(GXExtensionPoint extension) {
        return (E) extension;
    }

    private void logLocated(Object extension) {
        if (log.isDebugEnabled()) {
            log.debug("[Located Extension]: {}", extension.getClass().getSimpleName());
        }
    }
}
