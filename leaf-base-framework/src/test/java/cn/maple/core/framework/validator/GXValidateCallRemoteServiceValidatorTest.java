package cn.maple.core.framework.validator;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.annotation.GXValidateCRS;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXCallRemoteValidateService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXValidateCallRemoteServiceValidatorTest {
    @GXValidateCRS(service = TestRemoteValidateService.class)
    private String annotatedField;

    @Test
    void initializeLoadsRemoteValidateServiceAndValidatesValue() throws Exception {
        GXValidateCallRemoteServiceValidator validator = new GXValidateCallRemoteServiceValidator();
        TestRemoteValidateService service = new TestRemoteValidateService();
        GXValidateCRS annotation = getAnnotation();
        ConstraintValidatorContext context = Mockito.mock(ConstraintValidatorContext.class);

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(TestRemoteValidateService.class)).thenReturn(service);

            validator.initialize(annotation);

            assertTrue(validator.isValid("ok", context));
            assertFalse(validator.isValid("bad", context));
        }
    }

    @Test
    void nullValueIsValidAndDoesNotCallRemoteService() throws Exception {
        GXValidateCallRemoteServiceValidator validator = new GXValidateCallRemoteServiceValidator();
        TestRemoteValidateService service = Mockito.spy(new TestRemoteValidateService());
        GXValidateCRS annotation = getAnnotation();

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(TestRemoteValidateService.class)).thenReturn(service);
            validator.initialize(annotation);

            assertTrue(validator.isValid(null, Mockito.mock(ConstraintValidatorContext.class)));
            Mockito.verify(service, Mockito.never())
                    .callRemoteValidateService(Mockito.any(), Mockito.any(), Mockito.any());
        }
    }

    @Test
    void missingServiceFailsFast() throws Exception {
        GXValidateCallRemoteServiceValidator validator = new GXValidateCallRemoteServiceValidator();
        GXValidateCRS annotation = getAnnotation();

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(TestRemoteValidateService.class)).thenReturn(null);

            assertThrows(GXBusinessException.class, () -> validator.initialize(annotation));
        }
    }

    @Test
    void remoteServiceExceptionIsWrapped() throws Exception {
        GXValidateCallRemoteServiceValidator validator = new GXValidateCallRemoteServiceValidator();
        GXValidateCRS annotation = getAnnotation();
        TestRemoteValidateService service = new TestRemoteValidateService();
        service.throwException = true;

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(TestRemoteValidateService.class)).thenReturn(service);
            validator.initialize(annotation);

            assertThrows(GXBusinessException.class,
                    () -> validator.isValid("ok", Mockito.mock(ConstraintValidatorContext.class)));
        }
    }

    @Test
    void annotationMessageIsAscii() throws Exception {
        assertDoesNotThrow(this::getAnnotation);
        assertTrue(getAnnotation().message().chars().allMatch(ch -> ch <= 127));
    }

    private GXValidateCRS getAnnotation() throws NoSuchFieldException {
        Field field = GXValidateCallRemoteServiceValidatorTest.class.getDeclaredField("annotatedField");
        return field.getAnnotation(GXValidateCRS.class);
    }

    public static class TestRemoteValidateService implements GXCallRemoteValidateService {
        private boolean throwException;

        @Override
        public boolean callRemoteValidateService(Object value, ConstraintValidatorContext constraintValidatorContext, Dict param) {
            if (throwException) {
                throw new IllegalStateException("remote failed");
            }
            return "ok".equals(value);
        }
    }
}
