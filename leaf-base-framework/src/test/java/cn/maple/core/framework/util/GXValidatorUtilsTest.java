package cn.maple.core.framework.util;

import cn.maple.core.framework.exception.GXBeanValidateException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GXValidatorUtilsTest {
    @Test
    void validateEntityRejectsNullTarget() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> GXValidatorUtils.validateEntity(null));

        assertEquals("Validation target must not be null", exception.getMessage());
    }

    @Test
    void validateEntityUsesDefaultValidatorWhenSpringValidatorUnavailable() {
        try (MockedStatic<GXSpringContextUtils> springContextUtils = mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(() -> GXSpringContextUtils.getBean(Validator.class)).thenReturn(null);

            GXBeanValidateException exception = assertThrows(GXBeanValidateException.class,
                    () -> GXValidatorUtils.validateEntity(new SampleBean("")));

            assertEquals("Data validation failed", exception.getMsg());
            assertEquals("must not be blank , value = ", exception.getData().getStr("name"));
        }
    }

    @Test
    void validateEntityWithJsonNameFormatsFieldNamePlaceholder() {
        try (MockedStatic<GXSpringContextUtils> springContextUtils = mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(() -> GXSpringContextUtils.getBean(Validator.class)).thenReturn(null);

            GXBeanValidateException exception = assertThrows(GXBeanValidateException.class,
                    () -> GXValidatorUtils.validateEntity(new PlaceholderBean(""), "payload"));

            assertEquals("Data validation failed", exception.getMsg());
            assertEquals("payload.name is required", exception.getData().getStr("payload.name"));
        }
    }

    @Test
    void validateEntityPrefersSpringValidatorWhenAvailable() {
        Validator springValidator = mock(Validator.class);
        when(springValidator.validate(any(), any(Class[].class))).thenReturn(Collections.emptySet());

        try (MockedStatic<GXSpringContextUtils> springContextUtils = mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(() -> GXSpringContextUtils.getBean(Validator.class)).thenReturn(springValidator);

            assertDoesNotThrow(() -> GXValidatorUtils.validateEntity(new SampleBean("")));
        }

        verify(springValidator).validate(any(), any(Class[].class));
    }

    static class SampleBean {
        @NotBlank(message = "must not be blank")
        private final String name;

        SampleBean(String name) {
            this.name = name;
        }
    }

    static class PlaceholderBean {
        @NotBlank(message = "{fieldName} is required")
        private final String name;

        PlaceholderBean(String name) {
            this.name = name;
        }
    }
}
