package cn.maple.core.framework.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.req.GXDynamicCallParamAttributeReqDto;
import cn.maple.core.framework.dto.req.GXDynamicCallParamReqDto;
import cn.maple.core.framework.service.GXParseDynamicCallParamService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GXParseDynamicCallParamServiceImpl implements GXParseDynamicCallParamService {
    private static final Map<String, Class<?>> CALLBACK_CLASS_CACHE = new ConcurrentHashMap<>(64);

    private static final Map<String, Method> CALLBACK_METHOD_CACHE = new ConcurrentHashMap<>(128);

    @Override
    public Object getDynamicCallMethodParamValue(String jsonStr) {
        try {
            final GXDynamicCallParamReqDto callParamDto = parseDynamicCallParam(jsonStr);
            if (callParamDto == null) {
                return null;
            }

            final String javaType = callParamDto.getJavaType();
            final List<GXDynamicCallParamAttributeReqDto> attributes = callParamDto.getAttributes();

            if (attributes == null || attributes.isEmpty()) {
                log.warn("Dynamic call parameter attribute list is empty");
                return null;
            }

            if (CharSequenceUtil.isBlank(javaType)) {
                return getParamValueList(attributes);
            }

            final Dict paramValueObject = getParamValueObject(attributes);
            try {
                Class<?> targetClass = Class.forName(javaType);
                return JSONUtil.toBean(JSONUtil.toJsonStr(paramValueObject), targetClass);
            } catch (Exception e) {
                log.error("Failed to convert dynamic parameter object: targetType={}, error={}",
                        javaType, e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Failed to parse dynamic call parameter: error={}", e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Map<String, Object> getDynamicCallMethodParamMap(String jsonStr) {
        GXDynamicCallParamReqDto callParamDto = parseDynamicCallParam(jsonStr);
        if (callParamDto == null || callParamDto.getAttributes() == null || callParamDto.getAttributes().isEmpty()) {
            return Dict.create();
        }
        return getParamValueObject(callParamDto.getAttributes());
    }

    @Override
    public <T> T getDynamicCallMethodParamValue(String jsonStr, Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        Object value = getDynamicCallMethodParamValue(jsonStr);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        return JSONUtil.toBean(JSONUtil.toJsonStr(value), clazz);
    }

    private GXDynamicCallParamReqDto parseDynamicCallParam(String jsonStr) {
        if (CharSequenceUtil.isBlank(jsonStr) || !JSONUtil.isTypeJSON(jsonStr)) {
            log.error("Dynamic call parameter must be valid JSON");
            return null;
        }
        try {
            GXDynamicCallParamReqDto callParamDto = JSONUtil.toBean(jsonStr, GXDynamicCallParamReqDto.class);
            if (callParamDto == null) {
                log.error("Failed to parse dynamic call parameter JSON");
            }
            return callParamDto;
        } catch (Exception e) {
            log.error("Failed to parse dynamic call parameter JSON: error={}", e.getMessage(), e);
            return null;
        }
    }

    private List<Object> getParamValueList(List<GXDynamicCallParamAttributeReqDto> callParamAttributes) {
        final ArrayList<Object> objects = new ArrayList<>(callParamAttributes.size());

        for (GXDynamicCallParamAttributeReqDto attribute : callParamAttributes) {
            if (attribute == null) {
                objects.add(null);
                continue;
            }

            final String dataSource = attribute.getDataSource();
            if (CharSequenceUtil.isBlank(dataSource)) {
                log.warn("Dynamic call parameter data source is empty");
                objects.add(null);
                continue;
            }

            objects.add(getValueByDataSource(attribute, dataSource, null));
        }

        return objects;
    }

    private Dict getParamValueObject(List<GXDynamicCallParamAttributeReqDto> callParamAttributes) {
        final Dict dict = Dict.create();

        for (GXDynamicCallParamAttributeReqDto attribute : callParamAttributes) {
            if (attribute == null) {
                continue;
            }

            final String fieldName = attribute.getFieldName();
            if (CharSequenceUtil.isBlank(fieldName)) {
                log.warn("Dynamic call parameter field name is empty");
                continue;
            }

            final String dataSource = attribute.getDataSource();
            if (CharSequenceUtil.isBlank(dataSource)) {
                log.warn("Dynamic call parameter data source is empty: fieldName={}", fieldName);
                dict.set(fieldName, null);
                continue;
            }

            dict.set(fieldName, getValueByDataSource(attribute, dataSource, fieldName));
        }

        return dict;
    }

    private Object getValueByDataSource(GXDynamicCallParamAttributeReqDto attribute, String dataSource, String fieldName) {
        if (CharSequenceUtil.equalsIgnoreCase(dataSource, "token")) {
            return getValueFromToken(attribute);
        }
        if (CharSequenceUtil.equalsIgnoreCase(dataSource, "assign")) {
            return getValueFromAssign(attribute);
        }
        if (CharSequenceUtil.equalsIgnoreCase(dataSource, "callback")) {
            return getValueFromCallback(attribute);
        }
        if (fieldName == null) {
            log.warn("Unknown dynamic call parameter data source: {}", dataSource);
        } else {
            log.warn("Unknown dynamic call parameter data source: dataSource={}, fieldName={}", dataSource, fieldName);
        }
        return null;
    }

    private Object getValueFromCallback(GXDynamicCallParamAttributeReqDto callParamDto) {
        if (callParamDto == null || CharSequenceUtil.isBlank(callParamDto.getCallBackClassName())
                || CharSequenceUtil.isBlank(callParamDto.getCallBackMethodName())) {
            log.error("Callback parameter config is incomplete");
            return null;
        }

        try {
            final String callBackClassName = callParamDto.getCallBackClassName();
            final String callBackMethodName = callParamDto.getCallBackMethodName();
            final Class<?> targetClass = CALLBACK_CLASS_CACHE.computeIfAbsent(callBackClassName, this::loadCallbackClass);
            final Object bean = GXSpringContextUtils.getBean(targetClass);
            if (Objects.isNull(bean)) {
                log.error("Callback bean not found: className={}", callBackClassName);
                return null;
            }

            final Method method = CALLBACK_METHOD_CACHE.computeIfAbsent(callBackClassName + "#" + callBackMethodName,
                    key -> ReflectUtil.getMethodByName(targetClass, callBackMethodName));
            if (Objects.isNull(method)) {
                log.error("Callback method not found: className={}, methodName={}", callBackClassName, callBackMethodName);
                return null;
            }
            if (!method.canAccess(bean)) {
                method.setAccessible(true);
            }
            return method.invoke(bean);
        } catch (Exception e) {
            log.error("Failed to invoke callback for dynamic parameter: error={}", e.getMessage(), e);
            return null;
        }
    }

    private Class<?> loadCallbackClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Callback class not found: " + className, e);
        }
    }

    private Object getValueFromAssign(GXDynamicCallParamAttributeReqDto callParamDto) {
        return callParamDto != null ? callParamDto.getFixedAssignedValue() : null;
    }

    private Object getValueFromToken(GXDynamicCallParamAttributeReqDto callParamDto) {
        if (callParamDto == null || CharSequenceUtil.isBlank(callParamDto.getSourceFieldName())) {
            log.warn("Token parameter config is incomplete");
            return null;
        }

        try {
            final Dict tokenData = Dict.create();
            return tokenData.getObj(callParamDto.getSourceFieldName());
        } catch (Exception e) {
            log.error("Failed to get token field value: fieldName={}, error={}",
                    callParamDto.getSourceFieldName(), e.getMessage(), e);
            return null;
        }
    }
}
