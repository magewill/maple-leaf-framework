package cn.maple.core.framework.util;

import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GXSpringContextUtilsTest {
    private ApplicationContext originalApplicationContext;

    @BeforeEach
    void setUp() {
        originalApplicationContext = GXApplicationContextSingleton.INSTANCE.getApplicationContext();
        replaceApplicationContext(null);
    }

    @AfterEach
    void tearDown() {
        replaceApplicationContext(originalApplicationContext);
    }

    @Test
    void returnsNullOrEmptyWhenContextUnavailable() {
        assertNull(GXSpringContextUtils.getApplicationContext());
        assertNull(GXSpringContextUtils.getBean("missing"));
        assertNull(GXSpringContextUtils.getBean(String.class));
        assertNull(GXSpringContextUtils.getBean("missing", String.class));
        assertFalse(GXSpringContextUtils.containsBean("missing"));
        assertFalse(GXSpringContextUtils.isSingleton("missing"));
        assertNull(GXSpringContextUtils.getType("missing"));
        assertEquals(Map.of(), GXSpringContextUtils.getBeans(String.class));
        assertNull(GXSpringContextUtils.getEnvironment());
    }

    @Test
    void registerSingletonRejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> GXSpringContextUtils.registerSingleton("", new Object()));
        assertThrows(IllegalArgumentException.class, () -> GXSpringContextUtils.registerSingleton("bean", null));
    }

    @Test
    void registerSingletonNoOpsWithoutApplicationContext() {
        assertDoesNotThrow(() -> GXSpringContextUtils.registerSingleton("bean", new Object()));
    }

    @Test
    void getBeanAndMetadataWorkWithApplicationContext() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("demoString", String.class, () -> "hello");
        context.refresh();
        replaceApplicationContext(context);

        try {
            ApplicationContext appContext = GXSpringContextUtils.getApplicationContext();
            assertNotNull(appContext);
            assertEquals("hello", GXSpringContextUtils.getBean("demoString"));
            assertEquals("hello", GXSpringContextUtils.getBean("demoString", String.class));
            assertTrue(GXSpringContextUtils.containsBean("demoString"));
            assertTrue(GXSpringContextUtils.isSingleton("demoString"));
            assertEquals(String.class, GXSpringContextUtils.getType("demoString"));
            assertEquals("hello", GXSpringContextUtils.getBeans(String.class).get("demoString"));
            assertNotNull(GXSpringContextUtils.getEnvironment());
        } finally {
            context.close();
        }
    }

    private static void replaceApplicationContext(ApplicationContext applicationContext) {
        ReflectionTestUtils.setField(GXApplicationContextSingleton.INSTANCE, "applicationContext", applicationContext);
    }
}
