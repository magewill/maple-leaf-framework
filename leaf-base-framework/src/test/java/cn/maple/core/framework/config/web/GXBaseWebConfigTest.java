package cn.maple.core.framework.config.web;

import cn.maple.core.framework.filter.GXBaseRequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXBaseWebConfigTest {
    @Test
    void requestLoggingFilterUsesEarlyAsyncSupportedRegistration() {
        GXBaseWebConfig config = new GXBaseWebConfig();
        GXBaseRequestLoggingFilter filter = config.requestLoggingFilter();

        FilterRegistrationBean<GXBaseRequestLoggingFilter> registration = config.requestLoggingFilterRegistration(filter);

        assertEquals("requestLoggingFilter", registration.getFilterName());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 5, registration.getOrder());
        assertTrue(registration.isAsyncSupported());
        assertEquals("/*", registration.getUrlPatterns().iterator().next());
        assertInstanceOf(GXBaseRequestLoggingFilter.class, filter);
        assertEquals(filter, registration.getFilter());
    }
}
