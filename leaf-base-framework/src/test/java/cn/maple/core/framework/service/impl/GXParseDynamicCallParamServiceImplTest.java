package cn.maple.core.framework.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.req.GXDynamicCallParamAttributeReqDto;
import cn.maple.core.framework.dto.req.GXDynamicCallParamReqDto;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class GXParseDynamicCallParamServiceImplTest {
    private final GXParseDynamicCallParamServiceImpl service = new GXParseDynamicCallParamServiceImpl();

    @Test
    void getDynamicCallMethodParamValueReturnsListWhenJavaTypeIsBlank() {
        GXDynamicCallParamReqDto req = new GXDynamicCallParamReqDto();
        req.setAttributes(List.of(assignAttr("foo", 1), assignAttr("bar", "two")));

        Object result = service.getDynamicCallMethodParamValue(JSONUtil.toJsonStr(req));

        assertInstanceOf(List.class, result);
        assertEquals(List.of(1, "two"), result);
    }

    @Test
    void getDynamicCallMethodParamValueBuildsTypedObjectAndSupportsCallback() {
        GXDynamicCallParamReqDto req = new GXDynamicCallParamReqDto();
        req.setJavaType(TargetParam.class.getName());

        GXDynamicCallParamAttributeReqDto nameAttr = assignAttr("name", "maple");
        GXDynamicCallParamAttributeReqDto callbackAttr = new GXDynamicCallParamAttributeReqDto();
        callbackAttr.setFieldName("code");
        callbackAttr.setDataSource("callback");
        callbackAttr.setCallBackClassName(CallbackBean.class.getName());
        callbackAttr.setCallBackMethodName("value");
        req.setAttributes(List.of(nameAttr, callbackAttr));

        try (MockedStatic<GXSpringContextUtils> springContext = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContext.when(() -> GXSpringContextUtils.getBean(CallbackBean.class)).thenReturn(new CallbackBean());

            TargetParam result = service.getDynamicCallMethodParamValue(JSONUtil.toJsonStr(req), TargetParam.class);
            assertEquals("maple", result.getName());
            assertEquals("callback-value", result.getCode());
        }
    }

    @Test
    void getDynamicCallMethodParamMapReturnsMapViewOfAttributes() {
        GXDynamicCallParamReqDto req = new GXDynamicCallParamReqDto();
        req.setAttributes(List.of(assignAttr("foo", "bar")));

        Map<String, Object> result = service.getDynamicCallMethodParamMap(JSONUtil.toJsonStr(req));

        assertEquals(Dict.create().set("foo", "bar"), result);
    }

    @Test
    void getDynamicCallMethodParamValueReturnsNullForInvalidJson() {
        assertNull(service.getDynamicCallMethodParamValue("not-json"));
    }

    @Test
    void getDynamicCallMethodParamValueRejectsDisallowedTargetType() {
        GXDynamicCallParamReqDto req = new GXDynamicCallParamReqDto();
        req.setJavaType("java.lang.Runtime");
        req.setAttributes(List.of(assignAttr("name", "maple")));

        assertNull(service.getDynamicCallMethodParamValue(JSONUtil.toJsonStr(req)));
    }

    @Test
    void callbackRejectsObjectMethods() {
        GXDynamicCallParamReqDto req = new GXDynamicCallParamReqDto();
        GXDynamicCallParamAttributeReqDto callbackAttr = new GXDynamicCallParamAttributeReqDto();
        callbackAttr.setDataSource("callback");
        callbackAttr.setCallBackClassName(CallbackBean.class.getName());
        callbackAttr.setCallBackMethodName("getClass");
        req.setAttributes(List.of(callbackAttr));

        Object result = service.getDynamicCallMethodParamValue(JSONUtil.toJsonStr(req));

        assertInstanceOf(List.class, result);
        List<?> values = (List<?>) result;
        assertEquals(1, values.size());
        assertNull(values.getFirst());
    }

    private GXDynamicCallParamAttributeReqDto assignAttr(String fieldName, Object value) {
        GXDynamicCallParamAttributeReqDto attr = new GXDynamicCallParamAttributeReqDto();
        attr.setFieldName(fieldName);
        attr.setDataSource("assign");
        attr.setFixedAssignedValue(value);
        return attr;
    }

    public static class TargetParam {
        private String name;
        private String code;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class CallbackBean {
        public String value() {
            return "callback-value";
        }
    }
}
