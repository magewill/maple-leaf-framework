package cn.maple.core.framework.util;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXCommonUtilsTest {
    @Test
    void shouldParseJsonPropertyForComplexTypeWhenDefaultValueProvided() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("demo.names", List.class)).thenReturn(null);
        when(environment.getProperty("demo.names", String.class)).thenReturn("[\"alpha\",\"beta\"]");

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());

            List<?> names = GXCommonUtils.getEnvironmentValue("demo.names", List.class, List.of("fallback"));

            assertEquals(List.of("alpha", "beta"), names);
        }
    }

    @Test
    void shouldParseJsonPropertyForComplexTypeWithoutDefaultValue() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("demo.names", List.class)).thenReturn(null);
        when(environment.getProperty("demo.names", String.class)).thenReturn("[\"alpha\",\"beta\"]");

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());

            List<?> names = GXCommonUtils.getEnvironmentValue("demo.names", List.class);

            assertEquals(List.of("alpha", "beta"), names);
        }
    }

    @Test
    void shouldReturnDefaultValueWhenEnvironmentUnavailable() {
        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(null);

            String value = GXCommonUtils.getEnvironmentValue("demo.name", String.class, "fallback");

            assertEquals("fallback", value);
        }
    }

    @Test
    void shouldReturnDefaultProfileWhenEnvironmentUnavailable() {
        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(null);

            assertEquals("default", GXCommonUtils.getActiveProfile());
        }
    }

    @Test
    void shouldConvertSimpleSourceToSimpleTargetType() {
        Integer value = GXCommonUtils.convertSourceToTarget("123", Integer.class, null, null);

        assertEquals(123, value);
    }

    @Test
    void shouldCheckMethodExistsWhenArgumentIsNull() {
        assertTrue(GXCommonUtils.checkMethodExists(NullArgumentTarget.class, "setName", (Object) null));
        assertFalse(GXCommonUtils.checkMethodExists(NullArgumentTarget.class, "setAge", (Object) null));
    }

    static class NullArgumentTarget {
        @SuppressWarnings("unused")
        public void setName(String name) {
        }

        @SuppressWarnings("unused")
        public void setAge(int age) {
        }
    }
}
