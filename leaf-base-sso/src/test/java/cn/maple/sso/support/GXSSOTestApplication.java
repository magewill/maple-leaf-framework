package cn.maple.sso.support;

import cn.maple.core.framework.config.aware.GXApplicationContextAware;
import cn.maple.core.framework.service.GXBaseCacheService;
import cn.maple.core.framework.web.config.GXWebMvcConfigurer;
import cn.maple.sso.cache.GXSSOCache;
import cn.maple.sso.oauth.GXSSOAuthorization;
import cn.maple.sso.service.GXTokenConfigService;
import cn.maple.sso.service.GXUUserService;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot application for integration tests.
 *
 * <p>Only the SSO package is scanned and the framework context holder plus the MVC
 * configurer are imported explicitly, so the interceptor chain is exercised for real
 * without pulling the whole framework auto configuration.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "cn.maple.sso")
@Import({GXApplicationContextAware.class, GXWebMvcConfigurer.class})
public class GXSSOTestApplication {
    /**
     * Shared doubles, reset by each test class in {@code @BeforeEach}.
     */
    public static final GXSSOTestDoubles.InMemoryCacheService CACHE_SERVICE =
            new GXSSOTestDoubles.InMemoryCacheService();

    public static final GXSSOTestDoubles.CountingSsoCache SSO_CACHE =
            new GXSSOTestDoubles.CountingSsoCache();

    public static final GXSSOTestDoubles.FixedTokenConfigService TOKEN_CONFIG =
            new GXSSOTestDoubles.FixedTokenConfigService();

    public static final GXSSOTestDoubles.RecordingAuthorization AUTHORIZATION =
            new GXSSOTestDoubles.RecordingAuthorization(true);

    public static final GXSSOTestDoubles.RecordingPlugin PLUGIN =
            new GXSSOTestDoubles.RecordingPlugin();

    public static final GXSSOTestDoubles.StubUserService USER_SERVICE =
            new GXSSOTestDoubles.StubUserService();

    @Bean
    public GXBaseCacheService gxBaseCacheService() {
        return CACHE_SERVICE;
    }

    @Bean
    public GXSSOCache gxSSOCache() {
        return SSO_CACHE;
    }

    @Bean
    public GXTokenConfigService gxTokenConfigService() {
        return TOKEN_CONFIG;
    }

    @Bean
    public GXSSOAuthorization gxSSOAuthorization() {
        return AUTHORIZATION;
    }

    @Bean
    public GXSSOPluginTestBean gxSSOPlugin() {
        return new GXSSOPluginTestBean();
    }

    @Bean
    public GXUUserService gxUUserService() {
        return USER_SERVICE;
    }

    /**
     * Plugin wrapper contributing the shared recording plugin to the SSO plugin list.
     */
    public static class GXSSOPluginTestBean implements cn.maple.sso.plugins.GXSSOPlugin {
        @Override
        public boolean login(jakarta.servlet.http.HttpServletRequest request,
                             jakarta.servlet.http.HttpServletResponse response) {
            return PLUGIN.login(request, response);
        }

        @Override
        public boolean validateToken(cn.hutool.core.lang.Dict ssoToken) {
            return PLUGIN.validateToken(ssoToken);
        }

        @Override
        public boolean logout(jakarta.servlet.http.HttpServletRequest request,
                              jakarta.servlet.http.HttpServletResponse response) {
            return PLUGIN.logout(request, response);
        }
    }
}
