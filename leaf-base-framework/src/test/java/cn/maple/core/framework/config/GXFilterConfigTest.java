package cn.maple.core.framework.config;

import cn.maple.core.framework.filter.GXXssFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.filter.ServletRequestPathFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXFilterConfigTest {
    @Test
    void xssFilterRegistrationUsesEarlyAsyncSupportedRegistration() {
        GXFilterConfig config = new GXFilterConfig();

        FilterRegistrationBean<GXXssFilter> registration = config.xssFilterRegistration();

        assertEquals("xssFilter", registration.getFilterName());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 10, registration.getOrder());
        assertTrue(registration.isAsyncSupported());
        assertEquals("/*", registration.getUrlPatterns().iterator().next());
        assertInstanceOf(GXXssFilter.class, registration.getFilter());
    }

    @Test
    void requestContextFilterUsesConfiguredThreadContextInheritance() {
        GXFilterConfig config = new GXFilterConfig();
        ReflectionTestUtils.setField(config, "threadContextInheritable", false);

        RequestContextFilter filter = config.requestContextFilter();

        assertEquals(false, ReflectionTestUtils.getField(filter, "threadContextInheritable"));
    }

    @Test
    void optionalFiltersCanBeCreated() {
        GXFilterConfig config = new GXFilterConfig();

        assertInstanceOf(ServletRequestPathFilter.class, config.servletRequestPathFilter());
        assertInstanceOf(CommonsRequestLoggingFilter.class, config.commonsRequestLoggingFilter());
    }
}
