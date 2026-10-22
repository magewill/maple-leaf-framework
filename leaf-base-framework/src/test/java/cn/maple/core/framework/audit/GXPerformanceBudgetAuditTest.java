package cn.maple.core.framework.audit;

import cn.maple.core.framework.filter.GXXssHttpServletRequestWrapper;
import cn.maple.core.framework.util.cglib.GXBeanCopierCache;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CURRENT BEHAVIOR - flip when fixed.
 *
 * <p>Characterizes two structural costs: the copier cache is defeated by identity based
 * keys on a weak map, and the XSS wrapper materializes the whole request body at
 * construction time.</p>
 */
class GXPerformanceBudgetAuditTest {
    @Test
    void beanCopierCacheKeyIsNewInstanceOnEveryLookup() throws Exception {
        Method genKey = GXBeanCopierCache.class.getDeclaredMethod(
                "genKey", Class.class, Class.class, boolean.class);
        genKey.setAccessible(true);

        String first = (String) genKey.invoke(GXBeanCopierCache.INSTANCE, String.class, StringBuilder.class, false);
        String second = (String) genKey.invoke(GXBeanCopierCache.INSTANCE, String.class, StringBuilder.class, false);

        assertNotSame(first, second);
    }

    @Test
    void beanCopierCacheIsBackedByWeakKeys() throws Exception {
        Field cacheField = GXBeanCopierCache.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        Object cache = cacheField.get(GXBeanCopierCache.INSTANCE);

        assertTrue(cache.getClass().getName().contains("WeakKey"));
    }

    @Test
    void xssWrapperConsumesWholeBodyAtConstruction() throws Exception {
        String json = "{\"note\":\"a&b<c\"}";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/audit/peak");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body);

        GXXssHttpServletRequestWrapper wrapper = new GXXssHttpServletRequestWrapper(request);

        assertTrue(wrapper.getContentLength() > 0);
        assertTrue(request.getInputStream().available() == 0);
    }
}
