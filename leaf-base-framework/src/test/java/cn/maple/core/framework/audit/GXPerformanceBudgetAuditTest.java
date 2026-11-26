package cn.maple.core.framework.audit;

import cn.maple.core.framework.filter.GXXssHttpServletRequestWrapper;
import cn.maple.core.framework.util.cglib.GXBeanCopierCache;
import org.junit.jupiter.api.Test;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 性能预算审计。
 *
 * <p>F-16 的 BeanCopier 部分已修复（弱引用 String 键改为 {@code ClassValue} + 强引用键），
 * 原先"每次查找都重建键"的特征化断言已翻转为"同一类型对复用同一实例、重复查找不再增长缓存"。
 * XSS 包装器在构造期读取全量请求体仍为当前行为。</p>
 */
class GXPerformanceBudgetAuditTest {
    @Test
    void beanCopierIsReusedForSameTypePair() {
        BeanCopier first = GXBeanCopierCache.INSTANCE.get(CopierSource.class, CopierTarget.class, false);
        BeanCopier second = GXBeanCopierCache.INSTANCE.get(CopierSource.class, CopierTarget.class, false);

        assertSame(first, second);
    }

    @Test
    void beanCopierSeparatesConverterAndNonConverterInstances() {
        BeanCopier plain = GXBeanCopierCache.INSTANCE.get(CopierSource.class, CopierTarget.class, false);
        BeanCopier withConverter = GXBeanCopierCache.INSTANCE.get(CopierSource.class, CopierTarget.class, true);

        assertNotSame(plain, withConverter);
    }

    @Test
    void beanCopierCacheDoesNotGrowOnRepeatedLookups() {
        GXBeanCopierCache.INSTANCE.get(CopierSource.class, CopierTarget.class, false);
        int before = GXBeanCopierCache.INSTANCE.size(CopierSource.class);

        for (int i = 0; i < 100; i++) {
            GXBeanCopierCache.INSTANCE.get(CopierSource.class, CopierTarget.class, false);
        }

        assertEquals(before, GXBeanCopierCache.INSTANCE.size(CopierSource.class));
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

    static class CopierSource {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class CopierTarget {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
