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

/**
 * 动态调用参数解析服务实现类
 * <p>
 * 该服务用于解析和处理动态调用参数，支持从不同数据源（Token、固定分配值、回调方法）获取参数值。
 * 主要用于构建微服务间调用的参数，支持两种调用方式：
 * 1. 单个参数列表方式：如 service.method(param1, param2)
 * 2. 对象参数方式：如 service.method(objectParam)
 * </p>
 * <p>
 * 线程安全说明：
 * - 该实现类不包含可变状态，所有方法都是无状态的，因此是线程安全的
 * - 反射调用过程中使用了特权访问控制，确保安全性
 * </p>
 *
 * @author maple
 */
@Slf4j
@Service
public class GXParseDynamicCallParamServiceImpl implements GXParseDynamicCallParamService {
    /**
     * 从回调方法获取参数值
     * <p>
     * 通过反射调用指定类的指定方法获取参数值。该方法使用特权访问控制确保安全性，
     * 并对可能出现的各种异常进行了详细处理。
     * </p>
     *
     * @param callParamDto 包含回调类名和方法名的参数配置对象
     * @return 回调方法的返回值，如果调用失败则返回null
     * @throws SecurityException 当没有足够权限访问方法时
     */
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

    /**
     * 获取固定分配的参数值
     * <p>
     * 从参数配置对象中直接获取预设的固定值
     * </p>
     *
     * @param callParamDto 包含固定值的参数配置对象
     * @return 配置的固定值，如果callParamDto为null则返回null
     */
    private Object getValueFromAssign(GXDynamicCallParamAttributeReqDto callParamDto) {
        return callParamDto != null ? callParamDto.getFixedAssignedValue() : null;
    }

    /**
     * 获取动态调用的方法的参数实参
     * <p>
     * 根据JSON配置字符串解析出参数值。支持两种模式：
     * 1. 参数列表模式：返回参数值列表，用于service.method(param1, param2)形式的调用
     * 2. 对象参数模式：返回参数对象，用于service.method(objectParam)形式的调用
     * </p>
     *
     * @param jsonStr 包含参数配置的JSON字符串
     * @return 解析后的参数值(列表或对象)，解析失败时返回null
     */
    @Override
    public Object getDynamicCallMethodParamValue(String jsonStr) {
        // 验证JSON字符串的有效性
        if (JSONUtil.isNull(jsonStr) || !JSONUtil.isTypeJSON(jsonStr)) {
            log.error("参数必须是有效的JSON格式");
            return null;
        }

        try {
            // 解析JSON为参数配置对象
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

            // 根据javaType决定返回参数列表还是参数对象
            if (CharSequenceUtil.isBlank(javaType)) {
                return getParamValueList(attributes);
            }

            // 构建参数对象并转换为指定类型
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

    /**
     * 构建参数列表用于服务方法调用
     * <p>
     * 用于构建形如 XXXService.xxMethod(String name, Integer age) 的参数列表。
     * 根据每个参数的数据源类型(token/assign/callback)获取对应的参数值。
     * </p>
     *
     * @param callParamAttributes 参数属性配置列表
     * @return 参数值列表
     */
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

            // 根据数据源类型获取参数值
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

    /**
     * 构建参数对象用于服务方法调用
     * <p>
     * 用于构建形如 XXXService.xxMethod(TestReqDto testReqDto) 的参数对象。
     * 根据每个字段的数据源类型(token/assign/callback)获取对应的字段值，
     * 并组装成一个Dict对象，后续可转换为指定Java类型。
     * </p>
     *
     * @param callParamAttributes 参数属性配置列表
     * @return 包含所有字段值的Dict对象
     */
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

            // 根据数据源类型获取字段值
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

    /**
     * 从Token中获取参数值
     * <p>
     * 从当前请求的Token中提取指定字段的值。
     * 注意：当前实现需要集成认证框架来获取实际的Token数据。
     * </p>
     *
     * @param callParamDto 包含源字段名的参数配置对象
     * @return Token中指定字段的值，如果字段不存在或Token无效则返回null
     */
    private Object getValueFromToken(GXDynamicCallParamAttributeReqDto callParamDto) {
        if (callParamDto == null || CharSequenceUtil.isBlank(callParamDto.getSourceFieldName())) {
            log.warn("Token参数配置不完整，无法获取Token字段值");
            return null;
        }

        try {
            // TODO: 此处应该集成实际的认证框架，从请求上下文中获取Token数据
            // 当前为示例实现，实际项目中需要替换为从认证上下文获取Token的逻辑
            final Dict tokenData = Dict.create();
            // tokenData = 实际获取Token数据的逻辑

            final String sourceFieldName = callParamDto.getSourceFieldName();
            return tokenData.getObj(sourceFieldName);
        } catch (Exception e) {
            log.error("从Token获取字段[{}]值失败: {}",
                    callParamDto.getSourceFieldName(), e.getMessage(), e);
            return null;
        }
    }
}
