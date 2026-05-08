package cn.maple.extension;

import cn.hutool.http.HttpStatus;
import cn.maple.extension.exception.GXExtensionException;
import cn.maple.extension.register.GXAbstractComponentExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Executes extension point implementations by business scenario.
 * <p>
 * Lookup order: full scenario, default scenario, default use case, then global default.
 */
@Component
@Slf4j
public class GXExtensionExecutor extends GXAbstractComponentExecutor {
    private static final String DEFAULT_BIZ_SCENARIO_IDENTITY = GXBizScenario.newDefault().getUniqueIdentity();

    @Resource
    private GXExtensionRepository extensionRepository;

    @Override
    protected <C> C locateComponent(Class<C> targetClz, GXBizScenario bizScenario) {
        if (targetClz == null) {
            throw new NullPointerException("Target class cannot be null");
        }
        C extension = locateExtension(targetClz, bizScenario);
        log.debug("[Located Extension]: {}", extension.getClass().getSimpleName());
        return extension;
    }

    @Override
    protected <C> C locateComponent(String extensionPointName, String bizScenarioUniqueIdentity) {
        C extension = locate(extensionPointName, bizScenarioUniqueIdentity);
        if (extension == null) {
            String errMessage = "Can not find extension with ExtensionPoint: " + extensionPointName
                    + " BizScenario:" + bizScenarioUniqueIdentity;
            throw new GXExtensionException(errMessage, HttpStatus.HTTP_NOT_FOUND);
        }
        log.debug("[Located Extension]: {}", extension.getClass().getSimpleName());
        return extension;
    }

    /**
     * Locates an extension by applying the scenario fallback order.
     */
    protected <E> E locateExtension(Class<E> targetClz, GXBizScenario bizScenario) {
        checkNull(bizScenario);

        E extension;
        String extensionPointName = targetClz.getName();

        log.debug("BizScenario in locateExtension is : {}", bizScenario.getUniqueIdentity());

        // 1、 first try with full namespace
        extension = firstTry(extensionPointName, bizScenario);
        if (extension != null) {
            return extension;
        }

        // 2、 second try with default scenario
        extension = secondTry(extensionPointName, bizScenario);
        if (extension != null) {
            return extension;
        }

        // 3、 third try with default use case + default scenario
        extension = defaultUseCaseTry(extensionPointName, bizScenario);
        if (extension != null) {
            return extension;
        }

        // 4. fourth try with default biz id + default use case + default scenario
        extension = defaultBizIdTry(extensionPointName);
        if (extension != null) {
            return extension;
        }

        String errMessage = "Can not find extension with ExtensionPoint: " + targetClz + " BizScenario:" + bizScenario.getUniqueIdentity();
        throw new GXExtensionException(errMessage, HttpStatus.HTTP_NOT_FOUND);
    }

    private <E> E firstTry(String extensionPointName, GXBizScenario bizScenario) {
        log.debug("First trying with {}", bizScenario.getUniqueIdentity());
        return locate(extensionPointName, bizScenario.getUniqueIdentity());
    }

    private <E> E secondTry(String extensionPointName, GXBizScenario bizScenario) {
        log.debug("Second trying with {}", bizScenario.getIdentityWithDefaultScenario());
        return locate(extensionPointName, bizScenario.getIdentityWithDefaultScenario());
    }

    private <E> E defaultUseCaseTry(String extensionPointName, GXBizScenario bizScenario) {
        log.debug("Third trying with {}", bizScenario.getIdentityWithDefaultUseCase());
        return locate(extensionPointName, bizScenario.getIdentityWithDefaultUseCase());
    }

    private <E> E defaultBizIdTry(String extensionPointName) {
        log.debug("Fourth trying with {}", DEFAULT_BIZ_SCENARIO_IDENTITY);
        return locate(extensionPointName, DEFAULT_BIZ_SCENARIO_IDENTITY);
    }

    @SuppressWarnings("all")
    private <E> E locate(String name, String uniqueIdentity) {
        return (E) extensionRepository.findExtension(name, uniqueIdentity).orElse(null);
    }

    private void checkNull(GXBizScenario bizScenario) {
        if (bizScenario == null) {
            throw new IllegalArgumentException("BizScenario cannot be null for extension");
        }
    }
}
