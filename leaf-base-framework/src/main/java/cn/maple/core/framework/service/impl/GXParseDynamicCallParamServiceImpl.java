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
import java.util.Objects;

@Slf4j
@Service
public class GXParseDynamicCallParamServiceImpl implements GXParseDynamicCallParamService {
    private Object getValueFromCallback(GXDynamicCallParamAttributeReqDto callParamDto) {
        if (callParamDto == null || CharSequenceUtil.isBlank(callParamDto.getCallBackClassName())
                || CharSequenceUtil.isBlank(callParamDto.getCallBackMethodName())) {
            log.error("回调参数配置不完整，无法执行回调");
            return null;
        }

        try {
            final String callBackClassName = callParamDto.getCallBackClassName();
            final Class<?> aClass = Class.forName(callBackClassName);
            final String callBackMethodName = callParamDto.getCallBackMethodName();
            final Object bean = GXSpringContextUtils.getBean(aClass);
            if (Objects.isNull(bean)) {
                log.error("callBackClassName = {}的bean不存在", callBackClassName);
                return null;
            }
            final Method method = ReflectUtil.getMethodByName(aClass, callBackMethodName);
            if (Objects.isNull(method)) {
                log.error("callBackMethodName = {}在bean中不存在", aClass);
                return null;
            }
            return method.invoke(bean);
        } catch (Exception e) {
            log.error("反射调用获取参数值失败 {}", JSONUtil.toJsonStr(e));
        }
        return null;
    }

    private Object getValueFromAssign(GXDynamicCallParamAttributeReqDto callParamDto) {
        return callParamDto != null ? callParamDto.getFixedAssignedValue() : null;
    }

    @Override
    public Object getDynamicCallMethodParamValue(String jsonStr) {
        if (JSONUtil.isNull(jsonStr) || !JSONUtil.isTypeJSON(jsonStr)) {
            log.error("参数必须是有效的JSON格式");
            return null;
        }

        try {
            final GXDynamicCallParamReqDto callParamDto = JSONUtil.toBean(jsonStr, GXDynamicCallParamReqDto.class);
            if (callParamDto == null) {
                log.error("JSON解析为GXDynamicCallParamReqDto失败");
                return null;
            }

            final String javaType = callParamDto.getJavaType();
            final List<GXDynamicCallParamAttributeReqDto> attributes = callParamDto.getAttributes();

            if (attributes == null || attributes.isEmpty()) {
                log.warn("参数属性列表为空");
                return null;
            }

            if (CharSequenceUtil.isBlank(javaType)) {
                return getParamValueList(attributes);
            }

            final Dict paramValueObject = getParamValueObject(attributes);
            Class<?> aClass;
            try {
                aClass = Class.forName(javaType);
                return JSONUtil.toBean(JSONUtil.toJsonStr(paramValueObject), aClass);
            } catch (Exception e) {
                log.error("将参数值对象{}转换为{}类型失败: {}",
                        JSONUtil.toJsonStr(paramValueObject), javaType, e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("解析动态调用参数失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private List<Object> getParamValueList(List<GXDynamicCallParamAttributeReqDto> callParamAttributes) {
        final ArrayList<Object> objects = new ArrayList<>(callParamAttributes.size());

        for (GXDynamicCallParamAttributeReqDto attribute : callParamAttributes) {
            if (attribute == null) {
                objects.add(null);
                continue;
            }

            final String dataSource = attribute.getDataSource();
            Object value = null;

            if (CharSequenceUtil.isBlank(dataSource)) {
                log.warn("参数数据源类型为空");
                objects.add(null);
                continue;
            }

            if (CharSequenceUtil.equalsIgnoreCase(dataSource, "token")) {
                value = getValueFromToken(attribute);
            } else if (CharSequenceUtil.equalsIgnoreCase(dataSource, "assign")) {
                value = getValueFromAssign(attribute);
            } else if (CharSequenceUtil.equalsIgnoreCase(dataSource, "callback")) {
                value = getValueFromCallback(attribute);
            } else {
                log.warn("未知的参数数据源类型: {}", dataSource);
            }

            objects.add(value);
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
                log.warn("字段名为空，无法设置参数对象属性");
                continue;
            }

            final String dataSource = attribute.getDataSource();
            if (CharSequenceUtil.isBlank(dataSource)) {
                log.warn("字段[{}]的数据源类型为空", fieldName);
                dict.set(fieldName, null);
                continue;
            }

            Object value = null;
            if (CharSequenceUtil.equalsIgnoreCase(dataSource, "token")) {
                value = getValueFromToken(attribute);
            } else if (CharSequenceUtil.equalsIgnoreCase(dataSource, "assign")) {
                value = getValueFromAssign(attribute);
            } else if (CharSequenceUtil.equalsIgnoreCase(dataSource, "callback")) {
                value = getValueFromCallback(attribute);
            } else {
                log.warn("未知的数据源类型: {}, 字段: {}", dataSource, fieldName);
            }

            dict.set(fieldName, value);
        }

        return dict;
    }

    private Object getValueFromToken(GXDynamicCallParamAttributeReqDto callParamDto) {
        if (callParamDto == null || CharSequenceUtil.isBlank(callParamDto.getSourceFieldName())) {
            log.warn("Token参数配置不完整，无法获取Token字段值");
            return null;
        }

        try {
            // TODO: 此处应该集成实际的认证框架，从请求上下文中获取Token数据
            final Dict tokenData = Dict.create();

            final String sourceFieldName = callParamDto.getSourceFieldName();
            return tokenData.getObj(sourceFieldName);
        } catch (Exception e) {
            log.error("从Token获取字段[{}]值失败: {}",
                    callParamDto.getSourceFieldName(), e.getMessage(), e);
            return null;
        }
    }
}
