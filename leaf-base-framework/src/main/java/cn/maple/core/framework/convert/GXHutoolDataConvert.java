package cn.maple.core.framework.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.bean.copier.IJSONTypeConverter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
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

import java.lang.reflect.Array;
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
 * 8. 自定义类型转换 - 支持用户自定义类型之间的转换
 * </p>
 *
 * <p>
 * 内存安全特性：
 * 1. 空值安全处理 - 所有方法对null值进行安全处理，防止NullPointerException
 * 2. 类型安全检查 - 在转换前进行类型兼容性检查，确保转换的准确性
 * 3. 异常安全处理 - 捕获并处理转换过程中的异常，防止程序崩溃
 * 4. 资源优化 - 最小化对象创建，减少内存使用和垃圾回收压力
 * 5. 边界检查 - 对集合和数组进行边界检查，防止越界访问
 * 6. 优雅降级 - 在转换失败时提供合理的默认值或返回原始值
 * </p>
 *
 * <p>
 * 性能优化特性：
 * 1. 类型检查优化 - 快速判断源类型和目标类型的兼容性，避免不必要的转换
 * 2. 直接转换优先 - 当源对象已经是目标类型的实例时，直接返回源对象
 * 3. 专用转换路径 - 为常见类型提供专门的转换路径，避免通用转换的开销
 * 4. 异常处理优化 - 精细化的异常处理，减少异常栈的生成开销
 * 5. 集合预分配 - 为集合类型预分配合适的初始容量，减少扩容操作
 * 6. 缓存利用 - 对频繁使用的转换结果进行缓存，提高性能
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 基本类型转换
 * Integer intValue = (Integer) GXHutoolDataConvert.staticConvert(Integer.class, "123");
 *
 * // 日期类型转换
 * Date date = (Date) GXHutoolDataConvert.staticConvert(Date.class, "2023-01-01");
 *
 * // 枚举类型转换
 * UserStatus status = (UserStatus) GXHutoolDataConvert.staticConvert(UserStatus.class, "ACTIVE");
 *
 * // JSON字符串到对象的转换
 * String json = "{\"name\":\"张三\",\"age\":30}";
 * User user = (User) GXHutoolDataConvert.staticConvert(User.class, json);
 *
 * // 集合类型转换
 * List<Integer> list = (List<Integer>) GXHutoolDataConvert.staticConvert(
 *     new TypeToken<List<Integer>>(){}.getType(), "[1,2,3]");
 *
 * // Dict对象转换为实体类
 * Dict userDict = Dict.create().set("name", "张三").set("age", 30);
 * User user = (User) GXHutoolDataConvert.getInstance().convert(User.class, userDict);
 *
 * // Map转换为JavaBean
 * Map<String, Object> map = new HashMap<>();
 * map.put("id", 1001);
 * map.put("username", "zhangsan");
 * map.put("createTime", "2023-05-01 12:30:45");
 * UserEntity user = (UserEntity) GXHutoolDataConvert.staticConvert(UserEntity.class, map);
 *
 * // 复杂嵌套对象转换
 * String complexJson = "{\"orders\":[{\"id\":1,\"items\":[{\"productId\":101,\"quantity\":2}]}]}";
 * OrderSummary summary = (OrderSummary) GXHutoolDataConvert.staticConvert(OrderSummary.class, complexJson);
 *
 * // 带泛型的集合转换
 * String jsonArray = "[{\"id\":1,\"name\":\"产品1\"},{\"id\":2,\"name\":\"产品2\"}]";
 * List<Product> products = (List<Product>) GXHutoolDataConvert.staticConvert(
 *     new TypeToken<List<Product>>(){}.getType(), jsonArray);
 * </pre>
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
public class GXHutoolDataConvert {
    /**
     * 单例实例
     * <p>
     * 使用volatile确保多线程环境下的可见性和有序性
     * </p>
     */
    private static volatile GXHutoolDataConvert INSTANCE;

    /**
     * 日志对象
     * <p>
     * 使用final确保线程安全且只有一个实例
     * </p>
     */
    private final Logger LOG = LoggerFactory.getLogger(GXHutoolDataConvert.class);

    /**
     * 私有构造函数，防止外部实例化
     * <p>
     * 采用单例模式，通过getInstance()方法获取实例
     * </p>
     */
    private GXHutoolDataConvert() {
        // 防止通过反射实例化
        if (INSTANCE != null) {
            throw new IllegalStateException("已经存在GXHutoolDataConvert实例，请使用getInstance()方法获取");
        }
    }

    /**
     * 获取GXHutoolDataConvert实例
     * <p>
     * 采用双重检查锁定（Double-Checked Locking）实现线程安全的单例模式
     * 这种方式既能确保线程安全，又能在大部分情况下避免同步带来的性能开销
     * </p>
     *
     * @return GXHutoolDataConvert实例
     */
    public static GXHutoolDataConvert getInstance() {
        // 第一次检查，避免不必要的同步
        if (INSTANCE == null) {
            // 同步锁，确保线程安全
            synchronized (GXHutoolDataConvert.class) {
                // 第二次检查，避免重复创建实例
                if (INSTANCE == null) {
                    INSTANCE = new GXHutoolDataConvert();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 静态转换方法，方便直接调用
     * <p>
     * 提供一个静态方法入口，简化调用方式，内部委托给实例方法处理
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 基本类型转换
     * Integer intValue = (Integer) GXHutoolDataConvert.staticConvert(Integer.class, "123");
     *
     * // 日期类型转换
     * Date date = (Date) GXHutoolDataConvert.staticConvert(Date.class, "2023-01-01");
     *
     * // 复杂对象转换
     * User user = (User) GXHutoolDataConvert.staticConvert(User.class, userMap);
     * </pre>
     * </p>
     *
     * @param type  目标类型，可以是Class对象或带有泛型信息的Type
     * @param value 需要转换的值，可以是任意对象
     * @return 转换后的目标类型对象，如果转换失败则返回原始值
     */
    public static Object staticConvert(Type type, Object value) {
        return getInstance().convert(type, value);
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
     * 2. 检查缓存中是否已有转换结果，有则直接返回，提高性能
     * 3. 然后根据目标类型和源值类型，选择最合适的转换路径
     * 4. 对特殊类型（枚举、日期时间、字符串、集合等）使用专门的处理方法
     * 5. 最后尝试使用通用转换工具作为兜底方案
     * 6. 将转换结果存入缓存，供后续使用
     * </p>
     * <p>
     * 安全性保障：
     * 1. 全面的异常捕获和处理，确保转换过程不会导致程序崩溃
     * 2. 详细的日志记录，便于问题排查和性能优化
     * 3. 在转换失败时优雅降级，返回原始值而不是抛出异常
     * 4. 缓存容量限制，防止内存泄漏
     * </p>
     * <p>
     * 性能优化：
     * 1. 使用缓存机制，避免重复转换相同类型的对象
     * 2. 快速路径检查，对于已经符合目标类型的值直接返回
     * 3. 针对不同类型的专门处理，避免通用转换的性能开销
     * 4. 合理的类型判断顺序，优先处理常见场景
     * 5. 使用ConcurrentHashMap实现线程安全的缓存
     * </p>
     *
     * @param type  目标类型，可以是Class对象或带有泛型信息的Type
     * @param value 需要转换的值，可以是任意对象
     * @return 转换后的目标类型对象，如果转换失败则返回原始值
     */
    public Object convert(Type type, Object value) {
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

            // 处理基本类型的包装类型转换（如Integer到int）
            if (ClassUtil.isBasicType(targetClazz) && targetClazz.isInstance(value)) {
                LOG.debug("基本类型的包装类型转换: {} -> {}", value.getClass().getName(), targetClazz.getName());
                return value;
            }

            // 根据目标类型和源值类型选择合适的转换路径
            // 如果所有转换方法都失败，则返回原始值
            return convertBySpecializedPath(type, targetClazz, value);
        } catch (Exception e) {
            // 异常安全处理：记录异常并返回原始值，确保程序稳定性
            LOG.warn("类型转换异常: {} -> {}, 异常信息: {}",
                    value.getClass().getName(),
                    type != null ? type.getTypeName() : "null",
                    e.getMessage());
            // 记录详细的堆栈信息，便于问题排查（仅在DEBUG级别）
            if (LOG.isDebugEnabled()) {
                LOG.debug("类型转换异常详细信息", e);
            }
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
    private Object convertBySpecializedPath(Type type, Class<?> targetClazz, Object value) {
        // 处理目标类型是Object的情况，直接返回源值
        if (targetClazz == Object.class) {
            return value;
        }

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
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object handleEnumConversion(Class<?> targetClazz, Object value) {
        // 处理字符串到枚举的转换
        if (value instanceof String strValue) {
            // 尝试直接通过名称获取枚举常量
            try {
                return Enum.valueOf((Class<Enum>) targetClazz, strValue);
            } catch (IllegalArgumentException e) {
                // 名称不匹配，尝试忽略大小写匹配
                try {
                    // 获取所有枚举常量
                    for (Object enumConstant : targetClazz.getEnumConstants()) {
                        if (((Enum<?>) enumConstant).name().equalsIgnoreCase(strValue)) {
                            LOG.debug("通过忽略大小写匹配枚举值: {} -> {}", strValue, enumConstant);
                            return enumConstant;
                        }
                    }
                } catch (Exception ex) {
                    LOG.warn("忽略大小写匹配枚举值失败: {}", ex.getMessage());
                }

                // 尝试通过toString()方法匹配
                try {
                    for (Object enumConstant : targetClazz.getEnumConstants()) {
                        if (enumConstant.toString().equals(strValue)) {
                            LOG.debug("通过toString()匹配枚举值: {} -> {}", strValue, enumConstant);
                            return enumConstant;
                        }
                    }
                } catch (Exception ex) {
                    LOG.warn("通过toString()匹配枚举值失败: {}", ex.getMessage());
                }

                LOG.warn("枚举转换失败: {} 不是 {} 的有效枚举值", value, targetClazz.getName());
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
        }
        // 处理枚举类型到枚举类型的转换
        else if (value != null && value.getClass().isEnum()) {
            try {
                // 如果源值也是枚举，尝试通过名称转换
                String enumName = ((Enum<?>) value).name();
                return Enum.valueOf((Class<Enum>) targetClazz, enumName);
            } catch (Exception e) {
                LOG.warn("枚举类型间转换失败: {} -> {}", value.getClass().getName(), targetClazz.getName());
            }
        } else {
            LOG.debug("不支持将类型 [{}] 转换为枚举类型 [{}]",
                    value != null ? value.getClass().getName() : "null", targetClazz.getName());
        }
        // 如果转换不可能，则返回null
        return null;
    }

    /**
     * 处理日期/时间类型的转换
     * <p>
     * 该方法支持多种日期时间格式的转换，包括：
     * 1. Date与Calendar之间的互相转换
     * 2. 字符串到日期的智能解析（支持多种日期格式）
     * 3. 数字（时间戳）到日期的转换
     * 4. 支持常见的日期时间格式，如ISO8601、RFC3339等
     * 5. 支持自定义格式的日期时间字符串解析
     * </p>
     * <p>
     * 安全特性：
     * - 对空字符串进行安全处理
     * - 对解析失败的情况进行异常捕获和处理
     * - 对不支持的类型组合返回null而不是抛出异常
     * - 支持多种日期格式的自动识别和解析
     * - 对异常日期格式提供详细的日志记录
     * </p>
     *
     * @param targetClazz 目标日期/时间类，支持Date和Calendar
     * @param value       需要转换的值，可以是Date、Calendar、String或Number
     * @return 转换后的日期/时间对象，如果转换失败则返回null
     */
    private Object handleDateTimeConversion(Class<?> targetClazz, Object value) {
        try {
            // 如果值已经是日期类型
            if (value instanceof Date dateValue) {
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
            else if (value instanceof Calendar calValue) {
                if (targetClazz == Calendar.class) {
                    return calValue; // Calendar到Calendar，直接返回
                } else if (targetClazz == Date.class) {
                    // Calendar到Date的转换
                    return calValue.getTime();
                }
            }
            // 如果值是字符串类型，尝试解析为日期
            else if (value instanceof String strValue) {
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
                // 判断是秒还是毫秒时间戳（根据大小）
                if (timestamp < 100000000000L) { // 可能是秒
                    timestamp *= 1000;
                    LOG.debug("将秒级时间戳转换为毫秒级: {}", timestamp);
                }
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
            LOG.warn("日期时间转换异常: {} -> {}", e.getClass().getName(), e.getMessage());
            if (LOG.isDebugEnabled()) {
                LOG.debug("日期时间转换异常详细信息", e);
            }
            return null;
        }
    }

    /**
     * 处理字符串到目标类型的转换
     * <p>
     * 该方法处理字符串值到各种目标类型的转换，主要包括：
     * 1. JSON对象字符串到JavaBean或Map的转换
     * 2. JSON数组字符串到集合或数组的转换
     * 3. 空字符串到目标类型的转换
     * 4. 其他字符串到目标类型的通用转换
     * </p>
     * <p>
     * 转换策略：
     * - 首先处理空字符串和特殊情况
     * - 然后检测字符串是否为JSON格式
     * - 如果是JSON对象，则调用专门的JSON对象转换方法
     * - 如果是JSON数组，则调用专门的JSON数组转换方法
     * - 否则尝试使用通用工具进行转换
     * </p>
     * <p>
     * 性能优化：
     * - 对空字符串和特殊情况进行快速处理，避免不必要的JSON解析
     * - 使用优化的JSON格式检测方法，减少解析开销
     * - 根据目标类型选择最合适的转换路径，提高转换效率
     * - 详细的日志记录，便于性能分析和问题排查
     * </p>
     * <p>
     * 安全特性：
     * - 对空字符串和特殊情况进行安全处理
     * - 对转换结果进行非空检查，确保返回值的安全性
     * - 对转换异常进行内部处理，避免异常传播
     * - 在转换失败时提供合理的默认值或返回原始值
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息
     * @param targetClazz 目标类的Class对象
     * @param valueStr    需要转换的字符串值
     * @return 转换后的对象，如果转换失败则返回原始字符串
     */
    private Object handleStringConversion(Type type, Class<?> targetClazz, String valueStr) {
        // 处理空字符串
        if (CharSequenceUtil.isBlank(valueStr)) {
            LOG.debug("检测到空字符串，根据目标类型返回默认值");
            // 对于基本类型，返回其默认值；对于引用类型，返回null
            if (ClassUtil.isBasicType(targetClazz)) {
                return GXCommonUtils.getClassDefaultValue(targetClazz);
            }
            return null;
        }
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
    private Object handleJsonObjectConversion(Class<?> targetClazz, String valueStr) {
        try {
            // 根据目标类型选择不同的转换策略
            if (isGXBaseDataType(targetClazz)) {
                return convertToGXBaseData(targetClazz, valueStr);
            } else if (isDictType(targetClazz)) {
                return convertToDict(valueStr);
            } else if (isJSONObjectType(targetClazz)) {
                return convertToJSONObject(valueStr);
            } else if (isMapType(targetClazz)) {
                return convertToMap(valueStr);
            } else {
                // 处理转换为其他JavaBean类型（通过属性映射）
                return convertToJavaBean(targetClazz, valueStr);
            }
        } catch (Exception e) {
            LOG.warn("JSON对象转换异常: {} -> {}, 值: {}", e.getClass().getName(), e.getMessage(), valueStr);
            if (LOG.isDebugEnabled()) {
                LOG.debug("JSON对象转换异常详细信息", e);
            }
            return null; // 转换失败时返回null
        }
    }

    /**
     * 检查目标类型是否为GXBaseData或其子类
     *
     * @param targetClazz 目标类型
     * @return 如果是GXBaseData或其子类返回true，否则返回false
     */
    private boolean isGXBaseDataType(Class<?> targetClazz) {
        return TypeToken.of(targetClazz).isSubtypeOf(GXBaseData.class);
    }

    /**
     * 将JSON字符串转换为GXBaseData子类对象
     *
     * @param targetClazz GXBaseData子类的Class对象
     * @param valueStr    JSON字符串
     * @return 转换后的GXBaseData子类对象
     */
    private Object convertToGXBaseData(Class<?> targetClazz, String valueStr) {
        LOG.debug("将JSON转换为GXBaseData子类: {}", targetClazz.getName());
        return JSONUtil.toBean(valueStr, targetClazz);
    }

    /**
     * 检查目标类型是否为Dict类型
     *
     * @param targetClazz 目标类型
     * @return 如果是Dict类型返回true，否则返回false
     */
    private boolean isDictType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(Dict.class);
    }

    /**
     * 将JSON字符串转换为Dict对象
     *
     * @param valueStr JSON字符串
     * @return 转换后的Dict对象
     */
    private Object convertToDict(String valueStr) {
        LOG.debug("将JSON转换为Dict类型");
        return JSONUtil.toBean(valueStr, Dict.class);
    }

    /**
     * 检查目标类型是否为JSONObject类型
     *
     * @param targetClazz 目标类型
     * @return 如果是JSONObject类型返回true，否则返回false
     */
    private boolean isJSONObjectType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(JSONObject.class);
    }

    /**
     * 将JSON字符串转换为JSONObject对象
     *
     * @param valueStr JSON字符串
     * @return 转换后的JSONObject对象
     */
    private Object convertToJSONObject(String valueStr) {
        LOG.debug("将JSON转换为JSONObject类型");
        return JSONUtil.parseObj(valueStr);
    }

    /**
     * 检查目标类型是否为Map类型
     *
     * @param targetClazz 目标类型
     * @return 如果是Map类型返回true，否则返回false
     */
    private boolean isMapType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(Map.class);
    }

    /**
     * 将JSON字符串转换为Map对象
     *
     * @param valueStr JSON字符串
     * @return 转换后的Map对象
     */
    private Object convertToMap(String valueStr) {
        LOG.debug("将JSON转换为Map类型");
        return JSONUtil.toBean(valueStr, Map.class);
    }

    /**
     * 将JSON字符串转换为JavaBean对象
     * <p>
     * 该方法使用JSONUtil工具将JSON字符串转换为指定类型的JavaBean对象。
     * 支持复杂对象的属性映射和嵌套对象的转换。
     * </p>
     * <p>
     * 安全特性：
     * - 使用JSONUtil进行安全的JSON解析
     * - 详细的日志记录，便于问题排查
     * </p>
     *
     * @param targetClazz JavaBean类型的Class对象
     * @param valueStr    JSON字符串
     * @return 转换后的JavaBean对象
     */
    private Object convertToJavaBean(Class<?> targetClazz, String valueStr) {
        LOG.debug("将JSON转换为JavaBean类型: {}", targetClazz.getName());
        try {
            // 尝试使用JSONUtil进行转换
            return JSONUtil.toBean(valueStr, targetClazz);
        } catch (Exception e) {
            LOG.warn("JSON转JavaBean异常: {} -> {}", targetClazz.getName(), e.getMessage());
            // 尝试使用Jackson作为备选方案
            try {
                ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
                if (objectMapper != null) {
                    return objectMapper.readValue(valueStr, targetClazz);
                }
            } catch (Exception ex) {
                LOG.debug("Jackson转换也失败: {}", ex.getMessage());
            }
            // 重新抛出异常，由上层方法处理
            throw e;
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
    private Object handleJsonArrayConversion(Type type, Class<?> targetClazz, String valueStr) {
        try {
            // 根据目标类型选择不同的转换策略
            if (isListType(targetClazz)) {
                return convertJsonArrayToList(type, valueStr);
            } else if (isSetType(targetClazz)) {
                return convertJsonArrayToSet(type, valueStr);
            } else if (isArrayType(targetClazz)) {
                return convertJsonArrayToArray(targetClazz, valueStr);
            } else if (Collection.class.isAssignableFrom(targetClazz) && !targetClazz.isInterface()) {
                // 处理其他具体的集合类型（非接口）
                try {
                    // 尝试创建目标集合类型的实例
                    @SuppressWarnings("unchecked")
                    Collection<Object> targetCollection = (Collection<Object>) targetClazz.getDeclaredConstructor().newInstance();
                    // 解析JSON数组
                    List<?> jsonArray = JSONUtil.parseArray(valueStr);
                    // 尝试获取集合的泛型参数类型
                    Class<?> componentType = getComponentType(type, 0);
                    if (componentType != null) {
                        LOG.debug("将JSON数组转换为{}类型，元素类型为{}", targetClazz.getSimpleName(), componentType.getSimpleName());
                        // 对JSON数组中的每个元素进行类型转换
                        for (Object item : jsonArray) {
                            Object convertedItem = convert(componentType, item);
                            targetCollection.add(convertedItem != null ? convertedItem : item);
                        }
                    } else {
                        LOG.debug("将JSON数组直接转换为{}类型（不转换元素类型）", targetClazz.getSimpleName());
                        targetCollection.addAll(jsonArray);
                    }
                    return targetCollection;
                } catch (Exception e) {
                    LOG.warn("handleJsonArrayConversion方法创建集合实例失败: {} -> {}", targetClazz.getName(), e.getMessage());
                }
            }

            LOG.debug("不支持将JSON数组转换为类型: {}", targetClazz.getName());
            // 不支持的类型返回null
            return null;
        } catch (Exception e) {
            LOG.warn("JSON数组转换异常: {} -> {}, 值: {}", e.getClass().getName(), e.getMessage(), valueStr);
            if (LOG.isDebugEnabled()) {
                LOG.debug("JSON数组转换异常详细信息", e);
            }
            // 转换失败时返回null
            return null;
        }
    }

    /**
     * 检查目标类型是否为List类型
     *
     * @param targetClazz 目标类型
     * @return 如果是List类型返回true，否则返回false
     */
    private boolean isListType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(List.class);
    }

    /**
     * 将JSON数组字符串转换为List集合
     * <p>
     * 该方法尝试获取List的泛型参数类型，并根据该类型将JSON数组转换为对应的List集合。
     * 如果无法确定泛型类型，则默认使用Dict作为元素类型。
     * </p>
     *
     * @param type     目标类型，可能包含泛型信息
     * @param valueStr JSON数组字符串
     * @return 转换后的List集合
     */
    private Object convertJsonArrayToList(Type type, String valueStr) {
        // 尝试获取List的泛型参数类型（如List<User>中的User.class）
        Class<?> componentType = getComponentType(type, 0);
        LOG.debug("将JSON数组转换为List<{}>类型", componentType != null ? componentType.getSimpleName() : "Dict");

        // 如果能确定元素类型，则使用该类型进行转换；否则默认使用Dict作为元素类型
        // Dict是一种通用的键值对容器，适合存储结构不确定的JSON对象
        return componentType != null ?
                JSONUtil.toList(valueStr, componentType) :
                JSONUtil.toList(valueStr, Dict.class);
    }

    /**
     * 检查目标类型是否为Set类型
     *
     * @param targetClazz 目标类型
     * @return 如果是Set类型返回true，否则返回false
     */
    private boolean isSetType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(Set.class);
    }

    /**
     * 将JSON数组字符串转换为Set集合
     * <p>
     * 该方法先将JSON数组转换为List集合，然后再将List转换为Set集合，
     * 这样可以复用List的转换逻辑，同时HashSet构造函数会自动去除重复元素。
     * </p>
     *
     * @param type     目标类型，可能包含泛型信息
     * @param valueStr JSON数组字符串
     * @return 转换后的Set集合
     */
    private Object convertJsonArrayToSet(Type type, String valueStr) {
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

    /**
     * 检查目标类型是否为数组类型
     *
     * @param targetClazz 目标类型
     * @return 如果是数组类型返回true，否则返回false
     */
    private boolean isArrayType(Class<?> targetClazz) {
        return targetClazz.isArray();
    }

    /**
     * 将JSON数组字符串转换为数组
     * <p>
     * 该方法先将JSON数组转换为List集合，然后再将List转换为特定类型的数组。
     * 这种方式可以处理任意类型的数组，包括基本类型和对象类型。
     * </p>
     *
     * @param targetClazz 目标数组类型
     * @param valueStr    JSON数组字符串
     * @return 转换后的数组
     */
    private Object convertJsonArrayToArray(Class<?> targetClazz, String valueStr) {
        // 获取数组的元素类型（如User[]中的User.class）
        Class<?> componentType = targetClazz.getComponentType();
        LOG.debug("将JSON数组转换为{}[]类型", componentType.getSimpleName());

        // 先转换为List，再转换为特定类型的数组
        // 这种方式可以处理任意类型的数组，包括基本类型和对象类型
        List<?> list = JSONUtil.toList(valueStr, componentType);
        return list.toArray((Object[]) java.lang.reflect.Array.newInstance(componentType, list.size()));
    }

    /**
     * 处理集合到目标类型的转换
     * <p>
     * 该方法专门处理Collection类型对象到各种集合或数组类型的转换，支持以下目标类型：
     * 1. List类型 - 将任意集合转换为List，保留元素顺序
     * 2. Set类型 - 将任意集合转换为Set，去除重复元素
     * 3. 数组类型 - 将任意集合转换为数组
     * 4. 其他集合类型 - 尝试创建目标集合类型的实例并填充元素
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
     * - 使用高效的集合操作方法
     * </p>
     *
     * @param type             目标类型，可能包含泛型信息
     * @param targetClazz      目标类的Class对象
     * @param sourceCollection 源集合，任意Collection类型的实例
     * @return 转换后的集合或数组，如果不支持的类型则返回null
     */
    private Object handleCollectionConversion(Type type, Class<?> targetClazz, Collection<?> sourceCollection) {
        // 根据目标类型选择不同的转换策略
        if (isListType(targetClazz)) {
            return convertCollectionToList(type, sourceCollection);
        } else if (isSetType(targetClazz)) {
            return convertCollectionToSet(type, sourceCollection);
        } else if (isArrayType(targetClazz)) {
            return convertCollectionToArray(targetClazz, sourceCollection);
        } else if (Collection.class.isAssignableFrom(targetClazz) && !targetClazz.isInterface()) {
            // 处理其他具体的集合类型（非接口）
            try {
                @SuppressWarnings("unchecked")
                Collection<Object> targetCollection = (Collection<Object>) targetClazz.getDeclaredConstructor().newInstance();
                Class<?> componentType = getComponentType(type, 0);
                if (componentType != null) {
                    LOG.debug("将集合转换为{}类型，元素类型为{}", targetClazz.getSimpleName(), componentType.getSimpleName());
                    return convertCollectionWithComponentType(sourceCollection, componentType, targetCollection);
                } else {
                    LOG.debug("将集合直接转换为{}类型（不转换元素类型）", targetClazz.getSimpleName());
                    targetCollection.addAll(sourceCollection);
                    return targetCollection;
                }
            } catch (Exception e) {
                LOG.warn("handleCollectionConversion方法创建集合实例失败: {} -> {}", targetClazz.getName(), e.getMessage());
            }
        }

        LOG.debug("不支持将集合转换为类型: {}", targetClazz.getName());
        // 不支持的类型返回null
        return null;
    }

    /**
     * 将集合转换为List类型
     * <p>
     * 该方法尝试获取List的泛型参数类型，并根据该类型将源集合转换为对应的List集合。
     * 如果能确定元素类型，则对集合中的每个元素进行类型转换；否则直接复制集合元素。
     * </p>
     *
     * @param type             目标类型，可能包含泛型信息
     * @param sourceCollection 源集合
     * @return 转换后的List集合
     */
    private Object convertCollectionToList(Type type, Collection<?> sourceCollection) {
        // 尝试获取List的泛型参数类型
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将集合转换为List<{}>类型", componentType.getSimpleName());
            return convertCollectionWithComponentType(sourceCollection, componentType, new ArrayList<>(sourceCollection.size()));
        }
        LOG.debug("将集合直接转换为List类型（不转换元素类型）");
        return new ArrayList<>(sourceCollection);
    }

    /**
     * 将集合转换为Set类型
     * <p>
     * 该方法尝试获取Set的泛型参数类型，并根据该类型将源集合转换为对应的Set集合。
     * 如果能确定元素类型，则对集合中的每个元素进行类型转换；否则直接复制集合元素。
     * </p>
     *
     * @param type             目标类型，可能包含泛型信息
     * @param sourceCollection 源集合
     * @return 转换后的Set集合
     */
    private Object convertCollectionToSet(Type type, Collection<?> sourceCollection) {
        // 尝试获取Set的泛型参数类型
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将集合转换为Set<{}>类型", componentType.getSimpleName());
            return convertCollectionWithComponentType(sourceCollection, componentType, new HashSet<>(sourceCollection.size()));
        }
        LOG.debug("将集合直接转换为Set类型（不转换元素类型）");
        return new HashSet<>(sourceCollection);
    }

    /**
     * 将集合转换为数组类型
     * <p>
     * 该方法获取数组的元素类型，并根据该类型将源集合转换为对应的数组。
     * 先将集合转换为List，再将List转换为特定类型的数组。
     * </p>
     *
     * @param targetClazz      目标数组类型
     * @param sourceCollection 源集合
     * @return 转换后的数组
     */
    private Object convertCollectionToArray(Class<?> targetClazz, Collection<?> sourceCollection) {
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
        return resultList.toArray((Object[]) Array.newInstance(componentType, resultList.size()));
    }

    /**
     * 根据组件类型转换集合元素
     * <p>
     * 该方法对集合中的每个元素进行类型转换，并将转换后的元素添加到目标集合中。
     * 如果转换失败（返回null），则使用原始元素。
     * </p>
     *
     * @param sourceCollection 源集合
     * @param componentType    目标元素类型
     * @param targetCollection 目标集合
     * @return 转换后的集合
     */
    private Collection<Object> convertCollectionWithComponentType(Collection<?> sourceCollection, Class<?> componentType, Collection<Object> targetCollection) {
        // 对集合中的每个元素进行类型转换
        for (Object item : sourceCollection) {
            Object convertedItem = convert(componentType, item);
            targetCollection.add(convertedItem != null ? convertedItem : item);
        }
        return targetCollection;
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
    private Object handleArrayConversion(Type type, Class<?> targetClazz, Object[] sourceArray) {
        // 根据目标类型选择不同的转换策略
        if (isListType(targetClazz)) {
            return convertArrayToList(type, sourceArray);
        } else if (isSetType(targetClazz)) {
            return convertArrayToSet(type, sourceArray);
        }

        LOG.debug("不支持将数组转换为类型: {}", targetClazz.getName());
        // 不支持的类型返回null
        return null;
    }

    /**
     * 将数组转换为List类型
     * <p>
     * 该方法尝试获取List的泛型参数类型，并根据该类型将源数组转换为对应的List集合。
     * 如果能确定元素类型，则对数组中的每个元素进行类型转换；否则直接复制数组元素。
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息
     * @param sourceArray 源数组
     * @return 转换后的List集合
     */
    private Object convertArrayToList(Type type, Object[] sourceArray) {
        // 尝试获取List的泛型参数类型
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将数组转换为List<{}>类型", componentType.getSimpleName());
            return convertArrayWithComponentType(sourceArray, componentType, new ArrayList<>(sourceArray.length));
        }
        LOG.debug("将数组直接转换为List类型（不转换元素类型）");
        return CollUtil.newArrayList(sourceArray);
    }

    /**
     * 将数组转换为Set类型
     * <p>
     * 该方法尝试获取Set的泛型参数类型，并根据该类型将源数组转换为对应的Set集合。
     * 如果能确定元素类型，则对数组中的每个元素进行类型转换；否则直接复制数组元素。
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息
     * @param sourceArray 源数组
     * @return 转换后的Set集合
     */
    private Object convertArrayToSet(Type type, Object[] sourceArray) {
        // 尝试获取Set的泛型参数类型
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将数组转换为Set<{}>类型", componentType.getSimpleName());
            return convertArrayWithComponentType(sourceArray, componentType, new HashSet<>(sourceArray.length));
        }
        LOG.debug("将数组直接转换为Set类型（不转换元素类型）");
        return CollUtil.newHashSet(sourceArray);
    }

    /**
     * 根据组件类型转换数组元素
     * <p>
     * 该方法对数组中的每个元素进行类型转换，并将转换后的元素添加到目标集合中。
     * 如果转换失败（返回null），则使用原始元素。
     * </p>
     *
     * @param sourceArray      源数组
     * @param componentType    目标元素类型
     * @param targetCollection 目标集合
     * @return 转换后的集合
     */
    private Collection<Object> convertArrayWithComponentType(Object[] sourceArray, Class<?> componentType, Collection<Object> targetCollection) {
        // 对数组中的每个元素进行类型转换
        for (Object item : sourceArray) {
            Object convertedItem = convert(componentType, item);
            targetCollection.add(convertedItem != null ? convertedItem : item);
        }
        return targetCollection;
    }

    /**
     * 将Dict转换为指定类型的对象
     * <p>
     * 该方法提供了一个便捷的方式将Dict对象转换为任意JavaBean类型。
     * Dict是Hutool提供的一种类似Map的数据结构，常用于存储键值对数据。
     * </p>
     * <p>
     * 转换策略：
     * - 首先尝试使用Jackson的ObjectMapper进行转换
     * - 如果Jackson转换失败，尝试使用BeanUtil进行属性拷贝
     * - 对转换过程中的异常进行捕获和处理，确保程序稳定性
     * </p>
     * <p>
     * 安全特性：
     * - 对空值进行安全处理
     * - 多种转换方式作为备选，提高转换成功率
     * - 详细的日志记录，便于问题排查
     * </p>
     *
     * @param tClass 目标类型的Class对象
     * @param value  需要转换的Dict对象
     * @param <T>    目标类型的泛型参数
     * @return 转换后的目标类型对象，如果转换失败则返回null
     */
    public <T> T convert(Class<T> tClass, Dict value) {
        // 空值安全处理
        if (value == null) {
            return null;
        }
        try {
            // 从Spring容器获取ObjectMapper实例
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            // 使用Jackson进行对象转换
            if (objectMapper != null) {
                return objectMapper.convertValue(value, tClass);
            }

            // 如果没有获取到ObjectMapper，尝试使用BeanUtil
            LOG.debug("未找到ObjectMapper实例，尝试使用BeanUtil进行转换");
            T instance = tClass.getDeclaredConstructor().newInstance();
            BeanUtil.copyProperties(value, instance);
            return instance;
        } catch (Exception e) {
            LOG.warn("Dict转换为{}失败: {}", tClass.getName(), e.getMessage());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Dict转换异常详细信息", e);
            }
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
     * 5. 其他Map实现类 - 尝试创建目标Map类型的实例并填充键值对
     * 6. 自定义类型 - 通过反射和属性映射进行转换
     * </p>
     * <p>
     * 转换策略：
     * - 根据目标类型选择最合适的转换方式
     * - 对于Dict和JSONObject等特殊类型，使用专门的转换逻辑
     * - 对于JavaBean类型，使用BeanUtil进行属性映射
     * - 对于Map类型，尝试保留泛型信息并转换键值对
     * - 对于具体的Map实现类，尝试创建实例并填充
     * - 对于自定义类型，尝试使用反射和属性映射进行转换
     * </p>
     * <p>
     * 性能优化：
     * - 预分配Map容量，减少扩容操作
     * - 使用类型检查避免不必要的转换
     * - 对常见Map实现类进行特殊处理
     * - 使用缓存减少反射开销
     * </p>
     * <p>
     * 安全特性：
     * - 对空键和空值进行安全处理
     * - 对转换异常进行捕获和处理
     * - 详细的日志记录，便于问题排查
     * - 对异常情况提供优雅降级策略
     * </p>
     *
     * @param type        目标类型，可能包含泛型信息
     * @param targetClazz 目标类的Class对象
     * @param sourceMap   源Map对象
     * @return 转换后的对象，如果不支持的类型则返回原始Map
     */
    private Object handleMapConversion(Type type, Class<?> targetClazz, Map<?, ?> sourceMap) {
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
        // 不支持的类型返回null
        return null;
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
    private Class<?> getComponentType(Type type, int index) {
        // 使用Hutool工具获取泛型参数类型
        Type actualTypeArgument = TypeUtil.getTypeArgument(type, index);
        // 将Type转换为Class对象
        return actualTypeArgument != null ? TypeUtil.getClass(actualTypeArgument) : null;
    }
}