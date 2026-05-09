package cn.maple.core.framework.web.advice;

import cn.maple.core.framework.service.GXResponseBodyAdviceService;
import cn.maple.core.framework.util.GXResultUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXResponseBodyAdviceTest {

    @Test
    void supportsUsesResultTypeAssignableDirection() throws Exception {
        GXResponseBodyAdvice advice = new GXResponseBodyAdvice(provider(null));

        assertTrue(advice.supports(returnType("result"), null));
        assertTrue(advice.supports(returnType("specialResult"), null));
        assertFalse(advice.supports(returnType("object"), null));
    }

    private static MethodParameter returnType(String methodName) throws NoSuchMethodException {
        Method method = Controller.class.getDeclaredMethod(methodName);
        return new MethodParameter(method, -1);
    }

    private static ObjectProvider<GXResponseBodyAdviceService> provider(GXResponseBodyAdviceService service) {
        @SuppressWarnings("unchecked")
        ObjectProvider<GXResponseBodyAdviceService> provider = mock(ObjectProvider.class);
        when(provider.getIfUnique()).thenReturn(service);
        return provider;
    }

    static class Controller {
        GXResultUtils<String> result() {
            return GXResultUtils.ok();
        }

        SpecialResult specialResult() {
            return new SpecialResult();
        }

        Object object() {
            return new Object();
        }
    }

    static class SpecialResult extends GXResultUtils<String> {
    }
}
