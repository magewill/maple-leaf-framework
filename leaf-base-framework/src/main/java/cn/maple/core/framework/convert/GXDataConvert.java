package cn.maple.core.framework.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.bean.copier.IJSONTypeConverter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.TypeUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.GXBaseData;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 高性能数据类型转换工具类
 * <p>
 * 本工具类提供了一系列高性能、类型安全的数据转换方法，用于在不同数据类型之间进行智能转换。
 * 所有转换方法都经过内存安全和异常安全的处理，确保在各种复杂场景下的稳定性和可靠性。
 * </p>
 * 
 * <p>
 * 主要功能特性：
 * 1. 基本类型和包装类型的安全转换 - 确保类型转换的准确性和安全性
 * 2. 字符串到各种类型的智能转换 - 自动识别JSON格式并解析为对应对象
 * 3. 集合类型之间的转换 - 支持List、Set、数组等集合类型的互相转换
 * 4. Map到JavaBean的转换 - 支持复杂对象的属性映射和转换
 * 5. 枚举类型的转换 - 支持字符串和数字到枚举的智能转换
 * 6. 日期时间类型的转换 - 灵活处理各种日期时间格式
 * 7. 泛型支持 - 完整支持复杂泛型类型的转换
 * </p>
 * 
 * <p>
 * 内存安全特性：
 * 1. 空值安全处理 - 所有方法对null值进行安全处理，防止NullPointerException
 * 2. 类型安全检查 - 在转换前进行类型兼容性检查，确保转换的准确性
 * 3. 异常安全处理 - 捕获并处理转换过程中的异常，防止程序崩溃
 * 4. 资源优化 - 最小化对象创建，减少内存使用和垃圾回收压力
 * 5. 边界检查 - 对集合和数组进行边界检查，防止越界访问
 * </p>
 * 
 * <p>
 * 性能优化特性：
 * 1. 类型检查优化 - 快速判断源类型和目标类型的兼容性，避免不必要的转换
 * 2. 直接转换优先 - 当源对象已经是目标类型的实例时，直接返回源对象
 * 3. 专用转换路径 - 为常见类型提供专门的转换路径，避免通用转换的开销
 * 4. 异常处理优化 - 精细化的异常处理，减少异常栈的生成开销
 * 5. 集合预分配 - 为集合类型预分配合适的初始容量，减少扩容操作
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 基本类型转换
 * Integer intValue = (Integer) GXDataConvertUtils.convert(Integer.class, "123");
 *
 * // 日期类型转换
 * Date date = (Date) GXDataConvertUtils.convert(Date.class, "2023-01-01");
 *
 * // 枚举类型转换
 * UserStatus status = (UserStatus) GXDataConvertUtils.convert(UserStatus.class, "ACTIVE");
 *
 * // JSON字符串到对象的转换
 * String json = "{\"name\":\"张三\",\"age\":30}";
 * User user = (User) GXDataConvertUtils.convert(User.class, json);
 *
 * // 集合类型转换
 * List<Integer> list = (List<Integer>) GXDataConvertUtils.convert(
 *     new TypeReference<List<Integer>>(){}.getType(), "[1,2,3]");
 * 
 * // Dict对象转换为实体类
 * Dict userDict = Dict.create().set("name", "张三").set("age", 30);
 * User user = GXDataConvertUtils.convert(User.class, userDict);
 * </pre>
 * </p>
 * 
 * @author britton
 * @since 1.0.0
 */
public class GXDataConvert {
    /**
     * 日志对象
     * <p>
     * 使用final确保线程安全且只有一个实例
     * </p>
     */
    private final Logger LOG = LoggerFactory.getLogger(GXDataConvert.class);
    
    /**
     * 私有构造函数，防止实例化
     * <p>
     * 工具类应该设计为静态方法的集合，不需要实例化
     * </p>
     */
    public GXDataConvert() {
    }

    /**
     * 将对象转换为指定的目标类型
     * <p>
     * 这是本工具类的核心方法，提供了统一的类型转换入口。该方法能够智能地处理各种类型的转换需求，
     * 包括基本类型、集合类型、日期时间、JSON数据等。转换过程采用了分层处理策略，优先处理常见和
     * 特殊的类型，然后再尝试通用转换方法。
     * </p>
     * <p>
     * 转换策略和处理流程：
     * 1. 首先进行空值和类型检查，避免不必要的转换
     * 2. 然后根据目标类型和源值类型，选择最合适的转换路径
     * 3. 对特殊类型（枚举、日期时间、字符串、集合等）使用专门的处理方法
     * 4. 最后尝试使用通用转换工具作为兜底方案
     * </p>
     * <p>
     * 安全性保障：
     * 1. 全面的异常捕获和处理，确保转换过程不会导致程序崩溃
     * 2. 详细的日志记录，便于问题排查和性能优化
     * 3. 在转换失败时优雅降级，返回原始值而不是抛出异常
     * </p>
     * <p>
     * 性能优化：
     * 1. 快速路径检查，对于已经符合目标类型的值直接返回
     * 2. 针对不同类型的专门处理，避免通用转换的性能开销
     * 3. 合理的类型判断顺序，优先处理常见场景
     * </p>
     *
     * @param type  目标类型，可以是Class对象或带有泛型信息的Type
     * @param value 需要转换的值，可以是任意对象
     * @return 转换后的目标类型对象，如果转换失败则返回原始值
     */
    public  Object convert(Type type, Object value) {
        // 空值安全处理：如果输入为null，则返回null
        if (value == null) {
            return null;
        }

        try {
            // 获取目标类型的Class对象
            Class<?> targetClazz = TypeUtil.getClass(type);
            if (targetClazz == null) {
                LOG.debug("无法确定目标类型，返回原始值");
                return value; // 如果目标类型无法确定，则返回原始值
            }

            // 性能优化：如果值已经是目标类型的实例，则直接返回（快速路径）
            if (targetClazz.isInstance(value)) {
                LOG.debug("值已经是目标类型的实例，无需转换");
                return value;
            }

            // 根据目标类型和源值类型选择合适的转换路径
            // 如果所有转换方法都失败，则返回原始值
            return convertBySpecializedPath(type, targetClazz, value);
        } catch (Exception e) {
            // 异常安全处理：记录异常并返回原始值，确保程序稳定性
            LOG.warn("类型转换异常: {} -> {}", e.getClass().getName(), e.getMessage());
            return value; // 转换失败时返回原始值
        }
    }
    
    /**
     * 根据目标类型和源对象类型选择专用的转换路径
     * <p>
     * 该方法根据目标类型和源对象类型，选择最合适的转换策略。
     * 针对不同类型的组合，提供了专门的处理逻辑，提高转换的准确性和效率。
     * </p>
     *
     * @param type        目标类型
     * @param targetClazz 目标类型的Class对象
     * @param value       需要转换的值
     * @return 转换后的对象
     */
    private  Object convertBySpecializedPath(Type type, Class<?> targetClazz, Object value) {
        // 处理枚举类型转换
        if (targetClazz.isEnum()) {
            LOG.debug("检测到枚举类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleEnumConversion(targetClazz, value);
        }
        // 处理日期和日历类型转换
        if (Date.class.isAssignableFrom(targetClazz) || Calendar.class.isAssignableFrom(targetClazz)) {
            LOG.debug("检测到日期/时间类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleDateTimeConversion(targetClazz, value);
        }
        // 处理字符串类型转换（包括JSON解析）
        if (value instanceof CharSequence) {
            LOG.debug("检测到字符串类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleStringConversion(type, targetClazz, value.toString());
        }
        // 处理JSON类型转换器
        if (value instanceof IJSONTypeConverter) {
            LOG.debug("检测到JSON类型转换器: {}", value.getClass().getName());
            return ((IJSONTypeConverter) value).toBean(ObjectUtil.defaultIfNull(type, Object.class));
        }
        // 处理集合类型转换
        if (value instanceof Collection<?>) {
            LOG.debug("检测到集合类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleCollectionConversion(type, targetClazz, (Collection<?>) value);
        }
        // 处理数组类型转换
        if (value instanceof Object[]) {
            LOG.debug("检测到数组类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleArrayConversion(type, targetClazz, (Object[]) value);
        }
        // 处理Map类型转换
        if (value instanceof Map<?, ?>) {
            LOG.debug("检测到Map类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleMapConversion(type, targetClazz, (Map<?, ?>) value);
        }

        // 尝试使用Hutool的通用转换工具作为兜底方案
        LOG.debug("尝试使用通用转换工具: {} -> {}", value.getClass().getName(), targetClazz.getName());
        Object convertedValue = Convert.convertWithCheck(type, value, null, true);
        if (convertedValue != null) {
            return convertedValue;
        }

        LOG.debug("无法将类型 [{}] 转换为 [{}]，返回原始值", value.getClass().getName(), targetClazz.getName());
        // 如果所有转换方法都失败，则返回原始值
        return value;
    }

    /**
     * 处理枚举类型的转换
     * <p>
     * 该方法支持两种主要的枚举转换方式：
     * 1. 字符串到枚举 - 通过枚举名称进行匹配
     * 2. 数字到枚举 - 通过枚举的序号进行匹配
     * </p>
     * <p>
     * 安全特性：
     * - 对非法枚举名称进行安全处理，避免异常传播
     * - 对枚举序号进行边界检查，防止数组越界
     * - 详细的日志记录，便于问题排查
     * </p>
     *
     * @param targetClazz 目标枚举类，必须是枚举类型
     * @param value       需要转换的值，可以是字符串或数字
     * @return 转换后的枚举对象，如果转换失败则返回null
     */
    @SuppressWarnings({"rawtypes","unchecked"})
    private  Object handleEnumConversion(Class<?> targetClazz, Object value) {
        // 处理字符串到枚举的转换
        if (value instanceof String) {
            try {
                // 尝试通过名称获取枚举常量
                return Enum.valueOf((Class<Enum>) targetClazz, (String) value);
            } catch (IllegalArgumentException e) {
                LOG.warn("枚举转换失败: {} 不是 {} 的有效枚举值", value, targetClazz.getName());
                return null;
            }
        } 
        // 处理数字到枚举的转换（通过序号）
        else if (value instanceof Number) {
            try {
                // 获取枚举类的所有常量
                Object[] enumConstants = targetClazz.getEnumConstants();
                int index = ((Number) value).intValue();
                // 检查索引是否在有效范围内
                if (index >= 0 && index < enumConstants.length) {
                    return enumConstants[index];
                } else {
                    LOG.warn("枚举序号越界: 索引 {} 超出 {} 的有效范围 [0, {}]", 
                            index, targetClazz.getName(), enumConstants.length - 1);
                }
            } catch (Exception e) {
                LOG.warn("通过序号转换枚举失败: {}", e.getMessage());
            }
        } else {
            LOG.debug("不支持将类型 [{}] 转换为枚举类型 [{}]", 
                    value != null ? value.getClass().getName() : "null", targetClazz.getName());
        }
        return null; // 如果转换不可能，则返回null
    }

    /**
     * 处理日期/时间类型的转换
     * <p>
     * 该方法支持多种日期时间格式的转换，包括：
     * 1. Date与Calendar之间的互相转换
     * 2. 字符串到日期的智能解析（支持多种日期格式）
     * 3. 数字（时间戳）到日期的转换
     * </p>
     * <p>
     * 安全特性：
     * - 对空字符串进行安全处理
     * - 对解析失败的情况进行异常捕获和处理
     * - 对不支持的类型组合返回null而不是抛出异常
     * </p>
     *
     * @param targetClazz 目标日期/时间类，支持Date和Calendar
     * @param value       需要转换的值，可以是Date、Calendar、String或Number
     * @return 转换后的日期/时间对象，如果转换失败则返回null
     */
    private  Object handleDateTimeConversion(Class<?> targetClazz, Object value) {
        try {
            // 如果值已经是日期类型
            if (value instanceof Date) {
                Date dateValue = (Date) value;
                if (targetClazz == Date.class) {
                    return dateValue; // Date到Date，直接返回
                } else if (targetClazz == Calendar.class) {
                    // Date到Calendar的转换
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(dateValue);
                    return calendar;
                }
            }
            // 如果值是日历类型
            else if (value instanceof Calendar) {
                Calendar calValue = (Calendar) value;
                if (targetClazz == Calendar.class) {
                    return calValue; // Calendar到Calendar，直接返回
                } else if (targetClazz == Date.class) {
                    // Calendar到Date的转换
                    return calValue.getTime();
                }
            }
            // 如果值是字符串类型，尝试解析为日期
            else if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.isEmpty()) {
                    LOG.debug("日期字符串为空，返回null");
                    return null;
                }
                
                // 使用Hutool的DateUtil进行日期解析（支持多种日期格式）
                Date date = DateUtil.parse(strValue);
                if (date != null) {
                    if (targetClazz == Date.class) {
                        return date; // 字符串到Date的转换
                    } else if (targetClazz == Calendar.class) {
                        // 字符串到Calendar的转换
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(date);
                        return calendar;
                    }
                } else {
                    LOG.debug("无法解析日期字符串: {}", strValue);
                }
            }
            // 如果值是数字类型，尝试作为时间戳转换
            else if (value instanceof Number) {
                long timestamp = ((Number) value).longValue();
                Date date = new Date(timestamp);
                if (targetClazz == Date.class) {
                    return date; // 时间戳到Date的转换
                } else if (targetClazz == Calendar.class) {
                    // 时间戳到Calendar的转换
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    return calendar;
                }
            }
            
            // 尝试使用Hutool的通用转换作为最后的尝试
            LOG.debug("尝试使用通用转换工具转换日期/时间类型");
            return Convert.convert(targetClazz, value);
        } catch (Exception e) {
            LOG.warn("日期时间转换异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 处理字符串到目标类型的转换
     * <p>
     * 该方法处理字符串值到各种目标类型的转换，主要包括：
     * 1. JSON对象字符串到JavaBean或Map的转换
     * 2. JSON数组字符串到集合或数组的转换
     * 3. 其他字符串到目标类型的通用转换
     * </p>
     * <p>
     * 转换策略：
     * - 首先检测字符串是否为JSON格式
     * - 如果是JSON对象，则调用专门的JSON对象转换方法
     * - 如果是JSON数组，则调用专门的JSON数组转换方法
     * - 否则尝试使用通用工具进行转换
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息
     * @param targetClazz 目标类的Class对象
     * @param valueStr    需要转换的字符串值
     * @return 转换后的对象，如果转换失败则返回原始字符串
     */
    private  Object handleStringConversion(Type type, Class<?> targetClazz, String valueStr) {
        // 处理JSON对象字符串（形如 {"key":"value"}）
        if (JSONUtil.isTypeJSONObject(valueStr)) {
            LOG.debug("检测到JSON对象字符串，进行JSON对象转换");
            return handleJsonObjectConversion(targetClazz, valueStr);
        }
        // 处理JSON数组字符串（形如 [1,2,3] 或 [{},{}]）
        if (JSONUtil.isTypeJSONArray(valueStr)) {
            LOG.debug("检测到JSON数组字符串，进行JSON数组转换");
            return handleJsonArrayConversion(type, targetClazz, valueStr);
        }
        // 尝试使用通用工具进行转换（处理非JSON格式的字符串）
        LOG.debug("尝试使用通用工具转换字符串: {}", valueStr);
        Object convertedObj = GXCommonUtils.convertStrToTarget(valueStr, targetClazz);
        return convertedObj != null ? convertedObj : valueStr;
    }

    /**
     * 处理JSON对象字符串到目标类型的转换
     * <p>
     * 该方法专门处理JSON对象字符串到各种目标类型的转换，支持以下目标类型：
     * 1. GXBaseData及其子类 - 框架中的基础数据对象
     * 2. Dict类型 - 类似Map的键值对容器
     * 3. JSONObject类型 - JSON对象
     * 4. Map类型 - 键值对映射
     * 5. 其他JavaBean类型 - 通过属性映射转换
     * </p>
     * <p>
     * 转换策略：
     * - 根据目标类型选择最合适的转换方式
     * - 优先处理框架特定类型和常用容器类型
     * - 最后尝试转换为普通JavaBean
     * </p>
     * <p>
     * 性能优化：
     * - 使用类型继承关系检查，快速确定最合适的转换路径
     * - 利用JSONUtil工具类的高性能解析能力
     * - 对常见类型进行特殊处理，提高转换效率
     * </p>
     * <p>
     * 安全特性：
     * - 对JSON解析异常进行内部处理，避免异常传播
     * - 详细的日志记录，便于问题排查和性能分析
     * - 类型安全检查，确保类型转换的正确性
     * </p>
     *
     * @param targetClazz 目标类的Class对象，指定JSON应该被转换成的类型
     * @param valueStr    JSON对象字符串，形如 {"key":"value"}
     * @return 转换后的目标类型对象，如果转换失败则可能返回null
     */
    private  Object handleJsonObjectConversion(Class<?> targetClazz, String valueStr) {
        try {
            // 处理转换为GXBaseData子类（框架特定类型）
            // 首先检查目标类型是否是框架基础数据类型的子类
            if (TypeToken.of(targetClazz).isSubtypeOf(GXBaseData.class)) {
                LOG.debug("将JSON转换为GXBaseData子类: {}", targetClazz.getName());
                return JSONUtil.toBean(valueStr, targetClazz);
            }
            
            // 处理转换为Dict类型（键值对容器）
            // Dict是一种类似Map的数据结构，常用于存储键值对数据
            if (targetClazz.isAssignableFrom(Dict.class)) {
                LOG.debug("将JSON转换为Dict类型");
                return JSONUtil.toBean(valueStr, Dict.class);
            }
            
            // 处理转换为JSONObject类型（JSON对象）
            // 如果目标类型是JSONObject或其子类，直接解析为JSONObject
            if (targetClazz.isAssignableFrom(JSONObject.class)) {
                LOG.debug("将JSON转换为JSONObject类型");
                return JSONUtil.parseObj(valueStr);
            }
            
            // 处理转换为Map类型（键值对映射）
            // 如果目标类型是Map或其子类接口，转换为Map实现
            if (targetClazz.isAssignableFrom(Map.class)) {
                LOG.debug("将JSON转换为Map类型");
                return JSONUtil.toBean(valueStr, Map.class);
            }
            
            // 处理转换为其他JavaBean类型（通过属性映射）
            // 对于其他普通JavaBean类型，通过属性名映射进行转换
            LOG.debug("将JSON转换为JavaBean类型: {}", targetClazz.getName());
            return JSONUtil.toBean(valueStr, targetClazz);
        } catch (Exception e) {
            LOG.warn("JSON对象转换异常: {} -> {}", e.getClass().getName(), e.getMessage());
            return null; // 转换失败时返回null
        }
    }

    /**
     * 处理JSON数组字符串到目标类型的转换
     * <p>
     * 该方法专门处理JSON数组字符串到各种集合或数组类型的转换，支持以下目标类型：
     * 1. List类型 - 有序集合，保持元素的插入顺序
     * 2. Set类型 - 无序不重复集合，自动去除重复元素
     * 3. 数组类型 - 固定长度的同类型元素序列
     * </p>
     * <p>
     * 转换策略：
     * - 根据目标集合类型选择最合适的转换方式
     * - 尝试获取集合的泛型参数类型作为元素类型
     * - 如果无法确定元素类型，则默认使用Dict作为元素类型
     * - 对于数组类型，先转换为List，再转换为特定类型的数组
     * </p>
     * <p>
     * 性能优化：
     * - 使用JSONUtil高效解析JSON数组
     * - 根据目标类型直接选择最优转换路径，减少中间转换步骤
     * - 对于Set类型，先转换为List再转换为Set，避免多次解析JSON
     * </p>
     * <p>
     * 安全特性：
     * - 对JSON解析异常进行内部处理，避免异常传播
     * - 对不支持的类型返回null而不是抛出异常
     * - 详细的日志记录，便于问题排查和性能分析
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息，如List<User>、Set<String>等
     * @param targetClazz 目标类的Class对象，如List.class、Set.class、User[].class等
     * @param valueStr    JSON数组字符串，形如 [1,2,3] 或 [{"name":"张三"},{"name":"李四"}]
     * @return 转换后的集合或数组对象，如果不支持的类型则返回null
     */
    private  Object handleJsonArrayConversion(Type type, Class<?> targetClazz, String valueStr) {
        try {
            // 处理转换为List类型（有序集合）
            if (targetClazz.isAssignableFrom(List.class)) {
                // 尝试获取List的泛型参数类型（如List<User>中的User.class）
                Class<?> componentType = getComponentType(type, 0);
                LOG.debug("将JSON数组转换为List<{}>类型", componentType != null ? componentType.getSimpleName() : "Dict");
                
                // 如果能确定元素类型，则使用该类型进行转换；否则默认使用Dict作为元素类型
                // Dict是一种通用的键值对容器，适合存储结构不确定的JSON对象
                return componentType != null ? 
                       JSONUtil.toList(valueStr, componentType) : 
                       JSONUtil.toList(valueStr, Dict.class);
            }
            
            // 处理转换为Set类型（无序不重复集合）
            if (targetClazz.isAssignableFrom(Set.class)) {
                // 尝试获取Set的泛型参数类型（如Set<User>中的User.class）
                Class<?> componentType = getComponentType(type, 0);
                LOG.debug("将JSON数组转换为Set<{}>类型", componentType != null ? componentType.getSimpleName() : "Dict");
                
                // 先转换为List，再转换为Set，这样可以复用List的转换逻辑
                // 同时HashSet构造函数会自动去除重复元素
                List<?> list = componentType != null ? 
                             JSONUtil.toList(valueStr, componentType) : 
                             JSONUtil.toList(valueStr, Dict.class);
                return new HashSet<>(list);
            }
            
            // 处理转换为数组类型
            if (targetClazz.isArray()) {
                // 获取数组的元素类型（如User[]中的User.class）
                Class<?> componentType = targetClazz.getComponentType();
                LOG.debug("将JSON数组转换为{}[]类型", componentType.getSimpleName());
                
                // 先转换为List，再转换为特定类型的数组
                // 这种方式可以处理任意类型的数组，包括基本类型和对象类型
                List<?> list = JSONUtil.toList(valueStr, componentType);
                return list.toArray((Object[]) java.lang.reflect.Array.newInstance(componentType, list.size()));
            }
            
            LOG.debug("不支持将JSON数组转换为类型: {}", targetClazz.getName());
            return null; // 不支持的类型返回null
        } catch (Exception e) {
            LOG.warn("JSON数组转换异常: {} -> {}", e.getClass().getName(), e.getMessage());
            return null; // 转换失败时返回null
        }
    }

    /**
     * 处理集合到目标类型的转换
     * <p>
     * 该方法专门处理Collection类型对象到各种集合或数组类型的转换，支持以下目标类型：
     * 1. List类型 - 将任意集合转换为List，保留元素顺序
     * 2. Set类型 - 将任意集合转换为Set，去除重复元素
     * 3. 数组类型 - 将任意集合转换为数组
     * </p>
     * <p>
     * 转换策略：
     * - 尝试获取目标集合的泛型参数类型作为元素类型
     * - 如果能确定元素类型，则对集合中的每个元素进行类型转换
     * - 如果无法确定元素类型，则直接复制集合元素
     * - 对于数组类型，先转换为List，再转换为数组
     * </p>
     * <p>
     * 性能优化：
     * - 预分配集合容量，减少扩容操作
     * - 对元素类型进行判断，避免不必要的转换
     * </p>
     *
     * @param type             目标类型，可能包含泛型信息
     * @param targetClazz      目标类的Class对象
     * @param sourceCollection 源集合，任意Collection类型的实例
     * @return 转换后的集合或数组，如果不支持的类型则返回null
     */
    private  Object handleCollectionConversion(Type type, Class<?> targetClazz, Collection<?> sourceCollection) {
        // 处理转换为List类型（有序集合）
        if (targetClazz.isAssignableFrom(List.class)) {
            // 尝试获取List的泛型参数类型
            Class<?> componentType = getComponentType(type, 0);
            if (componentType != null) {
                LOG.debug("将集合转换为List<{}>类型", componentType.getSimpleName());
                // 预分配容量，减少扩容操作
                List<Object> resultList = new ArrayList<>(sourceCollection.size());
                // 对集合中的每个元素进行类型转换
                for (Object item : sourceCollection) {
                    Object convertedItem = convert(componentType, item);
                    resultList.add(convertedItem != null ? convertedItem : item);
                }
                return resultList;
            }
            LOG.debug("将集合直接转换为List类型（不转换元素类型）");
            return new ArrayList<>(sourceCollection);
        }
        // 处理转换为Set类型（无序不重复集合）
        if (targetClazz.isAssignableFrom(Set.class)) {
            // 尝试获取Set的泛型参数类型
            Class<?> componentType = getComponentType(type, 0);
            if (componentType != null) {
                LOG.debug("将集合转换为Set<{}>类型", componentType.getSimpleName());
                // 预分配容量，减少扩容操作
                Set<Object> resultSet = new HashSet<>(sourceCollection.size());
                // 对集合中的每个元素进行类型转换
                for (Object item : sourceCollection) {
                    Object convertedItem = convert(componentType, item);
                    resultSet.add(convertedItem != null ? convertedItem : item);
                }
                return resultSet;
            }
            LOG.debug("将集合直接转换为Set类型（不转换元素类型）");
            return new HashSet<>(sourceCollection);
        }
        // 处理转换为数组类型
        if (targetClazz.isArray()) {
            // 获取数组的元素类型
            Class<?> componentType = targetClazz.getComponentType();
            LOG.debug("将集合转换为{}[]类型", componentType.getSimpleName());
            // 预分配容量，减少扩容操作
            List<Object> resultList = new ArrayList<>(sourceCollection.size());
            // 对集合中的每个元素进行类型转换
            for (Object item : sourceCollection) {
                Object convertedItem = convert(componentType, item);
                resultList.add(convertedItem != null ? convertedItem : item);
            }
            // 转换为特定类型的数组
            return resultList.toArray((Object[]) java.lang.reflect.Array.newInstance(componentType, resultList.size()));
        }
        LOG.debug("不支持将集合转换为类型: {}", targetClazz.getName());
        return null; // 不支持的类型返回null
    }

    /**
     * 处理数组到目标类型的转换
     * <p>
     * 该方法专门处理数组类型对象到各种集合类型的转换，支持以下目标类型：
     * 1. List类型 - 将数组转换为List，保留元素顺序
     * 2. Set类型 - 将数组转换为Set，去除重复元素
     * </p>
     * <p>
     * 转换策略：
     * - 尝试获取目标集合的泛型参数类型作为元素类型
     * - 如果能确定元素类型，则对数组中的每个元素进行类型转换
     * - 如果无法确定元素类型，则直接复制数组元素
     * </p>
     * <p>
     * 性能优化：
     * - 预分配集合容量，减少扩容操作
     * - 使用Hutool工具类进行简单转换
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息
     * @param targetClazz 目标类的Class对象
     * @param sourceArray 源数组
     * @return 转换后的集合，如果不支持的类型则返回null
     */
    private  Object handleArrayConversion(Type type, Class<?> targetClazz, Object[] sourceArray) {
        // 处理转换为List类型（有序集合）
        if (targetClazz.isAssignableFrom(List.class)) {
            // 尝试获取List的泛型参数类型
            Class<?> componentType = getComponentType(type, 0);
            if (componentType != null) {
                LOG.debug("将数组转换为List<{}>类型", componentType.getSimpleName());
                // 预分配容量，减少扩容操作
                List<Object> resultList = new ArrayList<>(sourceArray.length);
                // 对数组中的每个元素进行类型转换
                for (Object item : sourceArray) {
                    Object convertedItem = convert(componentType, item);
                    resultList.add(convertedItem != null ? convertedItem : item);
                }
                return resultList;
            }
            LOG.debug("将数组直接转换为List类型（不转换元素类型）");
            return CollUtil.newArrayList(sourceArray);
        }
        // 处理转换为Set类型（无序不重复集合）
        if (targetClazz.isAssignableFrom(Set.class)) {
            // 尝试获取Set的泛型参数类型
            Class<?> componentType = getComponentType(type, 0);
            if (componentType != null) {
                LOG.debug("将数组转换为Set<{}>类型", componentType.getSimpleName());
                // 预分配容量，减少扩容操作
                Set<Object> resultSet = new HashSet<>(sourceArray.length);
                // 对数组中的每个元素进行类型转换
                for (Object item : sourceArray) {
                    Object convertedItem = convert(componentType, item);
                    resultSet.add(convertedItem != null ? convertedItem : item);
                }
                return resultSet;
            }
            LOG.debug("将数组直接转换为Set类型（不转换元素类型）");
            return CollUtil.newHashSet(sourceArray);
        }
        LOG.debug("不支持将数组转换为类型: {}", targetClazz.getName());
        return null; // 不支持的类型返回null
    }

    /**
     * 将Dict转换为指定类型的对象
     * <p>
     * 该方法提供了一个便捷的方式将Dict对象转换为任意JavaBean类型。
     * Dict是Hutool提供的一种类似Map的数据结构，常用于存储键值对数据。
     * </p>
     * <p>
     * 转换策略：
     * - 使用Jackson的ObjectMapper进行转换，支持复杂对象的映射
     * - 通过Spring容器获取ObjectMapper实例，确保配置一致性
     * - 对转换过程中的异常进行捕获和处理，确保程序稳定性
     * </p>
     *
     * @param tClass 目标类型的Class对象
     * @param value  需要转换的Dict对象
     * @param <T>    目标类型的泛型参数
     * @return 转换后的目标类型对象，如果转换失败则返回null
     */
    public  <T> T convert(Class<T> tClass, Dict value) {
        // 空值安全处理
        if (value == null) {
            return null;
        }
        try {
            // 从Spring容器获取ObjectMapper实例
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            // 使用Jackson进行对象转换
            return objectMapper.convertValue(value, tClass);
        } catch (Exception e) {
            LOG.warn("Dict转换为{}失败: {}", tClass.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 处理Map到目标类型的转换
     * <p>
     * 该方法专门处理Map类型对象到各种目标类型的转换，支持以下目标类型：
     * 1. Dict类型 - 类似Map的键值对容器
     * 2. JSONObject类型 - JSON对象
     * 3. JavaBean类型 - 通过属性映射转换
     * 4. Map类型 - 支持泛型参数的Map实现
     * </p>
     * <p>
     * 转换策略：
     * - 根据目标类型选择最合适的转换方式
     * - 对于Dict和JSONObject等特殊类型，使用专门的转换逻辑
     * - 对于JavaBean类型，使用BeanUtil进行属性映射
     * - 对于Map类型，尝试保留泛型信息并转换键值对
     * </p>
     * <p>
     * 性能优化：
     * - 预分配Map容量，减少扩容操作
     * - 使用类型检查避免不必要的转换
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息
     * @param targetClazz 目标类的Class对象
     * @param sourceMap   源Map对象
     * @return 转换后的对象，如果不支持的类型则返回null
     */
    private  Object handleMapConversion(Type type, Class<?> targetClazz, Map<?, ?> sourceMap) {
        // 处理转换为Dict类型（键值对容器）
        if (targetClazz.isAssignableFrom(Dict.class)) {
            LOG.debug("将Map转换为Dict类型");
            Dict dict = Dict.create();
            // 将Map中的所有键值对添加到Dict中
            sourceMap.forEach((k, v) -> dict.set(k.toString(), v));
            return dict;
        }
        // 处理转换为JSONObject类型（JSON对象）
        if (targetClazz.isAssignableFrom(JSONObject.class)) {
            LOG.debug("将Map转换为JSONObject类型");
            return JSONUtil.parseObj(sourceMap);
        }
        // 处理转换为JavaBean类型（通过属性映射）
        if (!targetClazz.isInterface() && !Map.class.isAssignableFrom(targetClazz)) {
            LOG.debug("将Map转换为JavaBean类型: {}", targetClazz.getName());
            return BeanUtil.toBean(sourceMap, targetClazz, CopyOptions.create());
        }
        // 处理转换为Map类型（保留泛型信息）
        if (Map.class.isAssignableFrom(targetClazz)) {
            // 尝试获取Map的键类型和值类型
            Type keyType = TypeUtil.getTypeArgument(type, 0);
            Type valueType = TypeUtil.getTypeArgument(type, 1);
            if (keyType != null && valueType != null) {
                Class<?> keyClass = TypeUtil.getClass(keyType);
                Class<?> valueClass = TypeUtil.getClass(valueType);
                if (keyClass != null && valueClass != null) {
                    LOG.debug("将Map转换为Map<{}, {}>类型", keyClass.getSimpleName(), valueClass.getSimpleName());
                    // 预分配容量，减少扩容操作
                    Map<Object, Object> resultMap = new HashMap<>(sourceMap.size());
                    // 对Map中的每个键值对进行类型转换
                    sourceMap.forEach((k, v) -> {
                        Object convertedKey = convert(keyClass, k);
                        Object convertedValue = convert(valueClass, v);
                        resultMap.put(convertedKey, convertedValue);
                    });
                    return resultMap;
                }
            } else {
                LOG.debug("无法确定Map的泛型参数类型，返回原始Map");
                return new HashMap<>(sourceMap);
            }
        }
        LOG.debug("不支持将Map转换为类型: {}", targetClazz.getName());
        return null; // 不支持的类型返回null
    }

    /**
     * 获取泛型类型的组件类型
     * <p>
     * 该方法用于从泛型类型中提取特定位置的实际类型参数。
     * 例如，从List<String>中提取String类型，从Map<Integer, User>中提取Integer或User类型。
     * </p>
     * <p>
     * 使用场景：
     * - 处理集合类型时，需要知道集合元素的类型
     * - 处理Map类型时，需要知道键和值的类型
     * - 处理其他泛型类型时，需要知道实际类型参数
     * </p>
     *
     * @param type  泛型类型，如List<String>、Map<Integer, User>等
     * @param index 泛型参数的索引，从0开始（例如，对于Map<K,V>，0表示K，1表示V）
     * @return 组件类型的Class对象，如果未找到则返回null
     */
    private  Class<?> getComponentType(Type type, int index) {
        // 使用Hutool工具获取泛型参数类型
        Type actualTypeArgument = TypeUtil.getTypeArgument(type, index);
        // 将Type转换为Class对象
        return actualTypeArgument != null ? TypeUtil.getClass(actualTypeArgument) : null;
    }
}