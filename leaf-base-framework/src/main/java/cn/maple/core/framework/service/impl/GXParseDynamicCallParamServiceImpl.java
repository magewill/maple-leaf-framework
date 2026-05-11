package cn.maple.core.framework.service.impl;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXTokenConstant;
import cn.maple.core.framework.dto.req.GXDynamicCallParamAttributeReqDto;
import cn.maple.core.framework.dto.req.GXDynamicCallParamReqDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXParseDynamicCallParamService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXCurrentRequestContextUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GXParseDynamicCallParamServiceImpl implements GXParseDynamicCallParamService {
    private static final String ALLOWED_CLASS_PREFIXES_KEY = "maple.framework.dynamic-call.allowed-class-prefixes";

    private static final String DEFAULT_ALLOWED_CLASS_PREFIXES = "cn.maple.";

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$");

    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");

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
                validateAllowedClassName(javaType, "dynamic parameter target type");
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
            validateAllowedClassName(callBackClassName, "dynamic parameter callback class");
            validateCallbackMethodName(callBackMethodName);
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
            if (!Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
                log.error("Callback method must be public and parameterless: className={}, methodName={}", callBackClassName, callBackMethodName);
                return null;
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
            return GXCurrentRequestContextUtils.getLoginFieldFromToken(
                    GXTokenConstant.TOKEN_NAME, callParamDto.getSourceFieldName(), Object.class, GXTokenConstant.USER_TOKEN_SECRET_KEY);
        } catch (Exception e) {
            log.error("Failed to get token field value: fieldName={}, error={}",
                    callParamDto.getSourceFieldName(), e.getMessage(), e);
            return null;
        }
    }

    private void validateAllowedClassName(String className, String source) {
        if (CharSequenceUtil.isBlank(className) || !CLASS_NAME_PATTERN.matcher(className).matches()) {
            throw new GXBusinessException(source + " is invalid: " + className);
        }
        boolean allowed = allowedClassPrefixes().stream().anyMatch(className::startsWith);
        if (!allowed) {
            throw new GXBusinessException(source + " is not allowed: " + className);
        }
    }

    private void validateCallbackMethodName(String methodName) {
        if (CharSequenceUtil.isBlank(methodName) || !METHOD_NAME_PATTERN.matcher(methodName).matches()) {
            throw new GXBusinessException("Dynamic parameter callback method is invalid: " + methodName);
        }
        if ("getClass".equals(methodName) || "wait".equals(methodName) || "notify".equals(methodName)
                || "notifyAll".equals(methodName)) {
            throw new GXBusinessException("Dynamic parameter callback method is not allowed: " + methodName);
        }
    }

    private List<String> allowedClassPrefixes() {
        String configured = GXCommonUtils.getEnvironmentValue(
                ALLOWED_CLASS_PREFIXES_KEY, String.class, DEFAULT_ALLOWED_CLASS_PREFIXES);
        return Arrays.stream(configured.split(","))
                .map(CharSequenceUtil::trim)
                .filter(CharSequenceUtil::isNotBlank)
                .toList();
    }
}
