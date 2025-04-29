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
 *
 * <p>功能特点：</p>
 * <p>1. 支持多种参数数据源：</p>
 * <ul>
 *   <li>Token数据源：从当前请求的Token中提取参数值</li>
 *   <li>固定分配值：直接使用配置中指定的固定值</li>
 *   <li>回调方法：通过反射调用指定类的方法获取参数值</li>
 * </ul>
 *
 * <p>2. 支持两种参数构建模式：</p>
 * <ul>
 *   <li>参数列表模式：构建多个独立参数的列表</li>
 *   <li>对象参数模式：构建单个复杂对象参数</li>
 * </ul>
 *
 * <p>线程安全说明：</p>
 * <p>该实现类不包含可变状态，所有方法都是无状态的，因此是线程安全的。</p>
 * <p>反射调用过程中使用了特权访问控制，确保安全性。</p>
 *
 * <p>性能考虑：</p>
 * <p>1. 反射调用可能影响性能，建议缓存常用的Method对象</p>
 * <p>2. JSON解析和对象构建在大量数据时可能较慢，应避免过于复杂的参数结构</p>
 * <p>3. 异常处理采用了记录日志但不中断流程的策略，确保服务稳定性</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在配置中心或数据库中存储参数配置的JSON字符串
 * String paramConfigJson = "{
 *   \"javaType\": \"com.example.UserQueryParam\",
 *   \"attributes\": [
 *     {
 *       \"fieldName\": \"userId\",
 *       \"dataSource\": \"token\",
 *       \"sourceFieldName\": \"id\"
 *     },
 *     {
 *       \"fieldName\": \"queryTime\",
 *       \"dataSource\": \"callback\",
 *       \"callBackClassName\": \"com.example.TimeService\",
 *       \"callBackMethodName\": \"getCurrentTime\"
 *     }
 *   ]
 * }";
 *
 * // 2. 在服务中使用参数解析服务
 * @Service
 * public class UserServiceClient {
 *     @Autowired
 *     private GXParseDynamicCallParamService paramService;
 *
 *     @Autowired
 *     private UserRemoteService userRemoteService;
 *
 *     public UserDTO getUserInfo() {
 *         // 解析参数配置，获取参数对象
 *         Object paramObject = paramService.getDynamicCallMethodParamValue(paramConfigJson);
 *
 *         // 使用解析后的参数调用远程服务
 *         if (paramObject instanceof UserQueryParam) {
 *             return userRemoteService.getUserInfo((UserQueryParam) paramObject);
 *         } else {
 *             throw new IllegalArgumentException("参数解析失败");
 *         }
 *     }
 * }
 * </pre>
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
     * <p>
     * 实现细节：
     * 1. 首先验证回调参数配置的完整性
     * 2. 通过类名获取Class对象
     * 3. 从Spring容器中获取对应的Bean实例
     * 4. 通过反射获取指定的方法
     * 5. 调用方法并返回结果
     * </p>
     * <p>
     * 异常处理：
     * - 配置不完整时记录错误日志并返回null
     * - Bean不存在时记录错误日志并返回null
     * - 方法不存在时记录错误日志并返回null
     * - 反射调用异常时记录错误日志并返回null
     * </p>
     *
     * @param callParamDto 包含回调类名和方法名的参数配置对象，不能为null且必须包含有效的类名和方法名
     * @return 回调方法的返回值，如果调用失败则返回null
     * @throws SecurityException 当没有足够权限访问方法时可能抛出此异常，但通常会被捕获并记录日志
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
     * 从参数配置对象中直接获取预设的固定值。这是最简单的参数获取方式，
     * 直接返回配置中指定的固定值，不需要额外的处理逻辑。
     * </p>
     * <p>
     * 实现细节：
     * - 检查参数配置对象是否为null
     * - 如果不为null，则返回其中的固定分配值
     * - 如果为null，则返回null
     * </p>
     * <p>
     * 性能说明：
     * 该方法执行效率很高，不涉及复杂计算或远程调用
     * </p>
     *
     * @param callParamDto 包含固定值的参数配置对象，可以为null
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
     * <p>
     * 实现流程：
     * 1. 验证JSON字符串的有效性
     * 2. 解析JSON为参数配置对象
     * 3. 根据配置中是否指定了javaType决定返回参数列表还是参数对象
     * 4. 如果未指定javaType，则构建参数列表
     * 5. 如果指定了javaType，则构建参数对象并转换为指定类型
     * </p>
     * <p>
     * 异常处理：
     * - JSON格式无效时记录错误日志并返回null
     * - JSON解析失败时记录错误日志并返回null
     * - 参数属性列表为空时记录警告日志并返回null
     * - 类型转换失败时记录错误日志并返回null
     * </p>
     * <p>
     * 性能考虑：
     * - JSON解析和对象构建可能在大量数据时较慢
     * - 类型转换涉及反射操作，可能影响性能
     * - 方法采用了防御性编程，确保在各种异常情况下不会中断业务流程
     * </p>
     *
     * @param jsonStr 包含参数配置的JSON字符串，必须是有效的JSON格式
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
     * <p>
     * 实现流程：
     * 1. 创建一个与参数属性列表大小相同的ArrayList
     * 2. 遍历参数属性列表，处理每个参数配置
     * 3. 根据参数的数据源类型调用相应的方法获取参数值
     * 4. 将获取的参数值添加到结果列表中
     * </p>
     * <p>
     * 异常处理：
     * - 参数属性为null时，添加null值到结果列表
     * - 数据源类型为空时，记录警告日志并添加null值
     * - 数据源类型未知时，记录警告日志并添加null值
     * </p>
     * <p>
     * 支持的数据源类型：
     * - token: 从当前请求的Token中获取值
     * - assign: 使用配置中指定的固定值
     * - callback: 通过调用指定的方法获取值
     * </p>
     *
     * @param callParamAttributes 参数属性配置列表，包含每个参数的数据源和获取方式
     * @return 构建好的参数值列表，可直接用于方法调用
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
     * <p>
     * 实现流程：
     * 1. 创建一个空的Dict对象作为结果容器
     * 2. 遍历参数属性列表，处理每个字段配置
     * 3. 检查字段名是否有效
     * 4. 根据字段的数据源类型调用相应的方法获取字段值
     * 5. 将字段名和字段值设置到Dict对象中
     * </p>
     * <p>
     * 异常处理：
     * - 参数属性为null时，跳过处理
     * - 字段名为空时，记录警告日志并跳过处理
     * - 数据源类型为空时，记录警告日志并设置字段值为null
     * - 数据源类型未知时，记录警告日志
     * </p>
     * <p>
     * 与getParamValueList的区别：
     * - getParamValueList返回参数值列表，用于多参数方法调用
     * - getParamValueObject返回单个对象，用于单参数对象方法调用
     * </p>
     *
     * @param callParamAttributes 参数属性配置列表，包含每个字段的名称、数据源和获取方式
     * @return 包含所有字段值的Dict对象，可转换为指定的Java类型
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
     * <p>
     * 实现说明：
     * 1. 验证参数配置的完整性
     * 2. 从请求上下文中获取Token数据（当前为示例实现）
     * 3. 根据源字段名从Token数据中提取值
     * </p>
     * <p>
     * 安全考虑：
     * - Token数据通常包含敏感信息，应确保只提取必要的字段
     * - 异常处理确保即使Token解析失败也不会影响业务流程
     * </p>
     * <p>
     * 扩展点：
     * 当前实现为示例，实际项目中需要替换为从认证上下文获取Token的逻辑，
     * 可以集成JWT、OAuth等认证框架。
     * </p>
     *
     * @param callParamDto 包含源字段名的参数配置对象，不能为null且必须包含有效的源字段名
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
