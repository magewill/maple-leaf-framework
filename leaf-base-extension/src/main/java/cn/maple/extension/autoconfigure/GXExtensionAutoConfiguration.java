package cn.maple.extension.autoconfigure;

import cn.maple.extension.GXExtensionExecutor;
import cn.maple.extension.GXExtensionRepository;
import cn.maple.extension.register.GXExtensionBootstrap;
import cn.maple.extension.register.GXExtensionRegister;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the extension framework.
 * <p>
 * Default beans are guarded by {@link ConditionalOnMissingBean}; extension registration is
 * triggered by {@link GXExtensionBootstrap}.
 */
@AutoConfiguration
public class GXExtensionAutoConfiguration {
    /**
     * Creates the startup registrar.
     */
    @Bean(value = "extensionBootstrap")
    @ConditionalOnMissingBean(GXExtensionBootstrap.class)
    public GXExtensionBootstrap extPointBootstrap() {
        return new GXExtensionBootstrap();
    }

    /**
     * Creates the extension repository.
     */
    @Bean("extensionRepository")
    @ConditionalOnMissingBean(GXExtensionRepository.class)
    public GXExtensionRepository extPointRepository() {
        return new GXExtensionRepository();
    }

    /**
     * Creates the extension executor.
     */
    @Bean(value = "extensionExecutor")
    @ConditionalOnMissingBean(GXExtensionExecutor.class)
    public GXExtensionExecutor extensionExecutor() {
        return new GXExtensionExecutor();
    }

    /**
     * Creates the extension registrar.
     */
    @Bean(value = "extensionRegister")
    @ConditionalOnMissingBean(GXExtensionRegister.class)
    public GXExtensionRegister extensionRegister() {
        return new GXExtensionRegister();
    }
}
