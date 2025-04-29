package cn.maple.core.framework.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.TypeUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.util.GXCommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用CGLIB转换器实现，支持所有Java类型的深度转换，包括集合和映射中的嵌套复杂对象。
 * <p>
 * 该转换器处理以下类型：
 * 1. 基本类型和包装类型（如int、Integer、double、Double等）
 * 2. 字符串转换（包括JSON解析）
 * 3. 集合类型（List、Set、数组）及其泛型类型的深度支持
 * 4. Map转换及其值的泛型类型深度支持
 * 5. JavaBean的深度复制
 * 6. 枚举类型
 * 7. 日期和时间类型
 * 8. 自定义对象和嵌套结构
 * </p>
 * <p>
 * 性能优化：
 * 1. 缓存字段元数据和泛型类型，最小化反射操作
 * 2. 缓存BeanCopier实例，提高嵌套复制效率
 * 3. 复用Hutool的高效类型转换工具
 * 4. 使用ConcurrentHashMap确保线程安全
 * 5. 跳过不必要的兼容类型转换
 * 6. 智能处理集合和数组的转换，减少内存分配
 * </p>
 * <p>
 * 内存安全特性：
 * 1. 所有方法都进行了参数验证，防止空指针异常
 * 2. 使用安全的集合操作，避免并发修改异常
 * 3. 合理管理资源，避免内存泄漏
 * 4. 安全处理异常，确保异常情况下资源能够正确释放
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 基本使用方式
 * // 创建源对象
 * UserEntity source = new UserEntity();
 * source.setId(1L);
 * source.setUsername("张三");
 * source.setRoles(Arrays.asList("admin", "user"));
 * source.setAttributes(Map.of("department", "技术部", "level", 3));
 *
 * // 创建目标对象
 * UserDTO target = new UserDTO();
 *
 * // 创建BeanCopier并使用GXDataCglibConvert进行属性转换
 * BeanCopier copier = BeanCopier.create(UserEntity.class, UserDTO.class, true);
 * copier.copy(source, target, new GXDataCglibConvert(UserDTO.class, target));
 *
 * // 2. 处理复杂嵌套对象
 * // 源对象包含嵌套的集合和对象
 * OrderEntity order = new OrderEntity();
 * order.setOrderId("ORD20230101");
 * order.setItems(List.of(
 *     new OrderItemEntity("item1", 2, new BigDecimal("100.00")),
 *     new OrderItemEntity("item2", 1, new BigDecimal("50.50"))
 * ));
 * order.setCustomer(new CustomerEntity("customer123", "李四", "13800138000"));
 *
 * // 目标DTO对象
 * OrderDTO orderDTO = new OrderDTO();
 *
 * // 使用GXDataCglibConvert进行深度转换
 * BeanCopier orderCopier = BeanCopier.create(OrderEntity.class, OrderDTO.class, true);
 * orderCopier.copy(order, orderDTO, new GXDataCglibConvert(OrderDTO.class, orderDTO));
 *
 * // 3. 处理JSON字符串转换
 * String jsonData = "{\"name\":\"王五\",\"age\":30,\"skills\":[\"Java\",\"Spring\",\"MySQL\"]}";
 * UserProfile profile = new UserProfile();
 *
 * // 设置JSON字符串到对象属性（会自动解析）
 * BeanUtil.setProperty(profile, "data", jsonData, CopyOptions.create().setConverter(new GXDataCglibConvert(UserProfile.class, profile)));
 *
 * // 4. 与GXCglibUtils结合使用
 * List<ProductEntity> products = getProductList(); // 假设这是获取产品列表的方法
 * List<ProductDTO> productDTOs = products.stream()
 *     .map(p -> {
 *         ProductDTO dto = new ProductDTO();
 *         BeanCopier copier = BeanCopier.create(ProductEntity.class, ProductDTO.class, true);
 *         copier.copy(p, dto, new GXDataCglibConvert(ProductDTO.class, dto));
 *         return dto;
 *     })
 *     .collect(Collectors.toList());
 * </pre>
 * </p>
 */
public class GXCGLibDataConvert implements Converter {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXCGLibDataConvert.class);

    /**
     * 字段缓存
     * <p>
     * 缓存目标类的字段信息，减少反射开销
     * 使用ConcurrentHashMap确保线程安全
     * </p>
     */
    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();

    /**
     * 泛型类型缓存
     * <p>
     * 缓存字段的泛型类型信息，用于集合和Map的元素类型推断
     * 使用ConcurrentHashMap确保线程安全
     * </p>
     */
    private final Map<String, Type> genericTypeCache = new ConcurrentHashMap<>();

    /**
     * BeanCopier缓存
     * <p>
     * 缓存不同类型组合的BeanCopier实例，显著提升性能
     * 使用两层ConcurrentHashMap确保线程安全和高效查找
     * </p>
     */
    private final Map<Class<?>, Map<Class<?>, BeanCopier>> beanCopierCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * <p>
     * 创建转换器实例并预缓存目标类的字段信息
     * </p>
     *
     * @param targetClass 目标类型，不能为null
     */
    public GXCGLibDataConvert(Class<?> targetClass) {
        preCacheFields(targetClass);
    }

    /**
     * 获取或创建CGLIB BeanCopier实例
     * <p>
     * 该方法首先尝试从缓存中获取指定源类和目标类的BeanCopier实例，
     * 如果不存在则创建新实例并放入缓存。这种缓存机制显著提高了性能，
     * 特别是在需要重复进行相同类型转换的场景下。
     * </p>
     * <p>
     * 使用双层ConcurrentHashMap实现高效的线程安全缓存，避免了频繁创建
     * BeanCopier实例的开销，同时保证了在高并发环境下的安全性。
     * </p>
     *
     * @param sourceClass  源类型，不能为null
     * @param targetClass  目标类型，不能为null
     * @param useConverter 是否使用转换器，true表示在复制过程中使用转换器
     * @return 缓存的或新创建的BeanCopier实例
     */
    private BeanCopier getBeanCopier(Class<?> sourceClass, Class<?> targetClass, boolean useConverter) {
        return beanCopierCache
                .computeIfAbsent(sourceClass, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(targetClass, k -> BeanCopier.create(sourceClass, targetClass, useConverter));
    }

    /**
     * 执行类型转换
     * <p>
     * 这是CGLIB转换器的核心方法，负责将源值转换为目标类型。该方法实现了一个分层的转换策略，
     * 根据源值类型和目标类型选择最合适的转换路径。转换过程中优先处理特殊类型（如枚举、日期时间等），
     * 然后是集合类型、Map类型和复杂对象类型，最后才使用通用转换作为兜底方案。
     * </p>
     * <p>
     * 转换流程：
     * 1. 空值检查 - 如果源值为null，直接返回null
     * 2. 枚举类型转换 - 处理字符串或数字到枚举的转换
     * 3. 日期时间类型转换 - 处理各种日期时间格式
     * 4. 字符串源值转换 - 包括JSON字符串的智能解析
     * 5. 集合类型转换 - 处理List、Set和数组之间的转换
     * 6. Map类型转换 - 处理Map到Bean或其他Map的转换
     * 7. 复杂对象转换 - 处理JavaBean之间的深度复制
     * 8. 通用转换 - 使用Hutool的Convert工具作为兜底方案
     * </p>
     * <p>
     * 异常处理：
     * 所有转换过程中的异常都会被捕获并记录，确保转换过程不会因异常而中断。
     * 在转换失败时，方法会返回null而不是抛出异常，保证调用方的稳定性。
     * </p>
     *
     * @param sourceValue 源值，可以是任意类型的对象
     * @param targetClass 目标类型，指定转换的目标类型
     * @param context     上下文信息，通常是setter方法名（如"setSysUserList"）
     * @return 转换后的目标类型对象，如果转换失败则返回null
     */
    @Override
    public Object convert(Object sourceValue, Class targetClass, Object context) {
        // 空值安全处理
        if (sourceValue == null) {
            LOG.trace("源值为null，返回目标类型{}的null值", targetClass.getName());
            return null;
        }

        // 获取源值的类型
        Class<?> sourceClass = sourceValue.getClass();

        // 从上下文中提取属性名（通常是setter方法名）
        String propertyName = (context instanceof String) ? getPropertyName((String) context) : null;
        LOG.trace("尝试转换: 源类型 [{}], 目标类型 [{}], 属性 [{}]",
                sourceClass.getName(), targetClass.getName(), propertyName != null ? propertyName : "N/A (上下文: " + context + ")");

        try {
            // 1. 处理枚举类型转换
            if (targetClass.isEnum()) {
                return handleEnumConversion(targetClass, sourceValue);
            }

            // 2. 处理日期/时间类型转换（使用Hutool）
            if (Date.class.isAssignableFrom(targetClass) || Calendar.class.isAssignableFrom(targetClass)
                    || java.time.temporal.Temporal.class.isAssignableFrom(targetClass)) {
                LOG.trace("处理日期/时间转换到{}", targetClass.getName());
                return Convert.convert(targetClass, sourceValue);
            }

            // 3. 处理字符串源值转换
            if (sourceValue instanceof CharSequence) {
                return handleStringSourceConversion(targetClass, propertyName, sourceValue.toString(), context);
            }

            // 4. 处理集合源值转换（List, Set）-> 目标（List, Set, Array）
            if (sourceValue instanceof Collection<?>) {
                return handleCollectionSourceConversion(targetClass, propertyName, (Collection<?>) sourceValue, context);
            }

            // 5. 处理数组源值转换 -> 目标（List, Set, Array）
            if (sourceClass.isArray()) {
                // 区分处理对象数组和基本类型数组
                if (sourceValue instanceof Object[]) {
                    return handleArraySourceConversion(targetClass, propertyName, (Object[]) sourceValue, context);
                } else {
                    // 对于基本类型数组，使用Hutool Convert处理
                    LOG.trace("基本类型数组源值，使用Hutool Convert转换到目标类型 {}", targetClass.getName());
                    return Convert.convertWithCheck(targetClass, sourceValue, null, false); // 转换失败时返回null而不抛出异常
                }
            }

            // 6. 处理Map源值转换 -> 目标（Map, Bean）
            if (sourceValue instanceof Map<?, ?>) {
                return handleMapSourceConversion(targetClass, propertyName, (Map<?, ?>) sourceValue, context);
            }

            // 7. 处理复杂对象（JavaBean）源值转换 -> 目标（Map, Bean）
            // 检查源和目标是否可能是JavaBean（非基本类型、非包装类型、非字符串、非集合、非Map、非数组、非日期、非枚举等）
            if (isComplexBean(sourceClass) && (isComplexBean(targetClass) || Map.class.isAssignableFrom(targetClass))) {
                LOG.trace("处理复杂Bean到Bean/Map的转换: {} -> {}", sourceClass.getName(), targetClass.getName());
                // 使用CGLIB BeanCopier进行Bean之间的深度复制
                if (Map.class.isAssignableFrom(targetClass)) {
                    // Bean转Map
                    LOG.trace("将Bean {}转换为Map", sourceClass.getName());
                    return BeanUtil.beanToMap(sourceValue);
                } else {
                    // Bean转Bean
                    LOG.trace("深度复制Bean {} 到 {}", sourceClass.getName(), targetClass.getName());
                    Object targetInstance = ReflectUtil.newInstanceIfPossible(targetClass);
                    if (targetInstance != null) {
                        // 递归使用CGLIB BeanCopier，传递*this*转换器
                        org.springframework.cglib.beans.BeanCopier copier = getBeanCopier(sourceClass, targetClass, true);
                        copier.copy(sourceValue, targetInstance, this); // 使用'this'作为转换器
                        return targetInstance;
                    } else {
                        LOG.warn("无法实例化目标Bean类: {}", targetClass.getName());
                        // 失败后使用Hutool转换作为最后的尝试
                    }
                }
            }

            // 8. 使用Hutool的通用转换作为兜底方案
            LOG.trace("使用Hutool Convert作为兜底方案: {} -> {}", sourceClass.getName(), targetClass.getName());
            // 使用convertWithCheck进行更好的错误处理，但不立即抛出异常
            Object convertedValue = Convert.convertWithCheck(targetClass, sourceValue, null, false); // `false` = 失败时返回null
            if (convertedValue != null) {
                // 检查Hutool是否真正进行了转换，还是因类型不兼容而返回了原始对象
                if (!targetClass.isInstance(convertedValue) && sourceValue == convertedValue) {
                    LOG.debug("Hutool Convert对不兼容类型返回了原始值: {} -> {}. 返回null.",
                            sourceClass.getName(), targetClass.getName());
                    return null; // 更明确地表示转换失败
                }
                LOG.trace("Hutool Convert成功转换: {} -> {}", sourceClass.getName(), targetClass.getName());
                return convertedValue;
            }

            LOG.warn("无法将类型 [{}] 转换为 [{}] (属性 [{}])，返回null",
                    sourceClass.getName(), targetClass.getName(), propertyName != null ? propertyName : "N/A");
            return null; // 所有尝试都失败后返回null

        } catch (Exception e) {
            LOG.warn("转换属性 [{}] 从 {} 到 {} 时出错: {} - {}",
                    propertyName != null ? propertyName : "N/A",
                    sourceClass.getName(),
                    targetClass.getName(),
                    e.getClass().getName(), e.getMessage());
            // 可选的调试日志: LOG.debug("异常堆栈:", e);
            // 异常情况下返回null
            return null;
        }
    }

    /**
     * 预缓存字段元数据，加速泛型类型解析
     * <p>
     * 该方法在转换器初始化时调用，预先缓存目标类的所有字段信息和泛型类型信息，
     * 显著减少后续转换过程中的反射开销。对于复杂对象和集合类型的转换尤其有效。
     * </p>
     * <p>
     * 性能优化：
     * 1. 使用ConcurrentHashMap存储缓存，确保线程安全
     * 2. 只缓存有意义的类型，跳过基本类型、数组、枚举等
     * 3. 利用Hutool的ReflectUtil获取包括继承字段在内的所有字段
     * </p>
     * <p>
     * 异常处理：
     * 捕获并记录所有反射过程中的异常，确保初始化过程不会因异常而中断
     * </p>
     *
     * @param clazz 要缓存字段的类，不能为null
     */
    private void preCacheFields(Class<?> clazz) {
        // 跳过不需要缓存的类型
        if (clazz == null || clazz.isPrimitive() || clazz.isArray() || clazz.isEnum() ||
                clazz.isInterface() || Map.class.isAssignableFrom(clazz) ||
                Collection.class.isAssignableFrom(clazz)) {
            return;
        }
        try {
            // 使用ReflectUtil获取包括继承字段在内的所有字段
            for (Field field : ReflectUtil.getFields(clazz)) {
                if (!fieldCache.containsKey(field.getName())) {
                    // 缓存字段对象
                    fieldCache.put(field.getName(), field);
                    // 缓存字段的泛型类型信息
                    genericTypeCache.put(field.getName(), field.getGenericType());
                }
            }
            // ReflectUtil.getFields已经处理了继承字段，不需要递归处理父类
            // preCacheFields(clazz.getSuperclass());
        } catch (Exception e) {
            LOG.warn("为类{}预缓存字段时失败: {}", clazz.getName(), e.getMessage());
        }
    }

    /**
     * 从setter方法名中提取属性名
     * <p>
     * 该方法根据JavaBean规范，从setter方法名（如setUserName）中提取实际的属性名（如userName）。
     * 处理了各种特殊情况，包括单字母属性名和特殊大写缩写（如URL）。
     * </p>
     * <p>
     * 处理规则：
     * 1. 标准setter方法（setXxx）：移除前缀"set"，并将首字母小写（除非后续字母也是大写）
     * 2. 单字母属性（setA）：转换为小写（a）
     * 3. 大写缩写（setURL）：保持大写（URL）
     * 4. 非标准名称：原样返回
     * </p>
     *
     * @param setterName setter方法名，如"setUserName"、"setURL"等
     * @return 提取的属性名，如"userName"、"URL"等
     */
    private String getPropertyName(String setterName) {
        if (setterName.startsWith("set") && setterName.length() > 3) {
            // 处理可能的单字母属性名
            if (setterName.length() == 4 || Character.isLowerCase(setterName.charAt(4))) {
                // 例如：setA -> a 或 setUserName -> userName
                return setterName.substring(3, 4).toLowerCase() + setterName.substring(4);
            } else {
                // 避免改变第二个字符为大写的情况（例如：setURL -> URL）
                return setterName.substring(3);
            }
        }
        // 如果上下文不是标准的setter名称，则原样返回
        return setterName;
    }

    /**
     * 判断一个类是否为需要深度复制的复杂JavaBean
     * <p>
     * 该方法通过排除法确定一个类是否为复杂JavaBean。复杂JavaBean是指需要进行深度复制的对象，
     * 而不是可以直接赋值或简单转换的基本类型、包装类型、字符串、集合等。
     * </p>
     * <p>
     * 排除的类型包括：
     * 1. 基本类型（int、long、boolean等）
     * 2. 数组类型
     * 3. 枚举类型
     * 4. 接口类型
     * 5. 数字类型（Integer、Long、Double等）
     * 6. 布尔类型（Boolean）
     * 7. 字符类型（Character）
     * 8. 字符序列类型（String、StringBuilder等）
     * 9. 日期时间类型（Date、Calendar、LocalDateTime等）
     * 10. 集合类型（List、Set等）
     * 11. 映射类型（Map等）
     * </p>
     * <p>
     * 性能优化：
     * 该方法使用短路逻辑，一旦确定类型不符合条件就立即返回false，避免不必要的判断
     * </p>
     *
     * @param clazz 要判断的类，可以为null
     * @return 如果是复杂JavaBean返回true，否则返回false
     */
    private boolean isComplexBean(Class<?> clazz) {
        return clazz != null &&
                !clazz.isPrimitive() &&
                !clazz.isArray() &&
                !clazz.isEnum() &&
                !clazz.isInterface() &&
                !Number.class.isAssignableFrom(clazz) &&
                !Boolean.class.isAssignableFrom(clazz) &&
                !Character.class.isAssignableFrom(clazz) &&
                !CharSequence.class.isAssignableFrom(clazz) && // 包括String
                !Date.class.isAssignableFrom(clazz) &&
                !Calendar.class.isAssignableFrom(clazz) &&
                !java.time.temporal.Temporal.class.isAssignableFrom(clazz) &&
                !Map.class.isAssignableFrom(clazz) &&
                !Collection.class.isAssignableFrom(clazz);
        // 如有必要，可以添加其他简单类型的判断
    }

    /**
     * 处理枚举类型的转换
     * <p>
     * 该方法将各种类型的源值转换为指定的枚举类型。主要处理两种情况：
     * 1. 字符串到枚举的转换：通过枚举名称精确匹配
     * 2. 其他类型到枚举的转换：通过Hutool的Convert工具处理（如整数序号到枚举的转换）
     * </p>
     * <p>
     * 异常处理：
     * 对于字符串转换，如果找不到匹配的枚举值，会捕获异常并返回null
     * 对于其他类型，依赖Hutool的Convert工具进行安全转换
     * </p>
     *
     * @param targetEnumClass 目标枚举类型，不能为null
     * @param sourceValue     源值，可以是字符串、数字等
     * @return 转换后的枚举值，如果转换失败则返回null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object handleEnumConversion(Class<?> targetEnumClass, Object sourceValue) {
        LOG.trace("处理枚举转换到 {}", targetEnumClass.getName());
        if (sourceValue instanceof String) {
            try {
                // 通过枚举名称精确匹配
                return Enum.valueOf((Class<Enum>) targetEnumClass, (String) sourceValue);
            } catch (IllegalArgumentException e) {
                LOG.warn("无法将字符串 \"{}\" 转换为枚举 {}: {}", sourceValue, targetEnumClass.getName(), e.getMessage());
                return null;
            }
        }
        // 使用Hutool处理其他类型（如Integer序号）到枚举的转换
        return Convert.convert(targetEnumClass, sourceValue);
    }

    /**
     * 处理字符串源值的转换
     * <p>
     * 该方法负责将字符串类型的源值转换为目标类型。它能够智能识别并处理以下几种情况：
     * 1. JSON对象字符串 -> 目标对象/Map
     * 2. JSON数组字符串 -> 目标集合/数组
     * 3. 普通字符串 -> 目标类型
     * </p>
     * <p>
     * 智能识别：
     * - 通过检查字符串的开始和结束字符，以及使用JSONUtil验证，判断字符串是否为JSON格式
     * - 对于JSON对象字符串，委托给handleJsonObjectStringConversion方法处理
     * - 对于JSON数组字符串，委托给handleJsonArrayStringConversion方法处理
     * - 对于普通字符串，使用Hutool的Convert工具进行基本转换
     * </p>
     * <p>
     * 性能优化：
     * - 使用快速的前缀/后缀检查作为初步筛选，减少不必要的JSON解析尝试
     * - 对于非JSON字符串，直接使用高效的基本类型转换
     * </p>
     *
     * @param targetClass    目标类型，不能为null
     * @param propertyName   属性名，可以为null
     * @param sourceValueStr 源字符串值，不能为null
     * @param context        上下文信息，可以为null
     * @return 转换后的目标类型对象，如果转换失败则返回null
     */
    private Object handleStringSourceConversion(Class<?> targetClass, String propertyName, String sourceValueStr, Object context) {
        LOG.trace("处理字符串源值转换到{}，属性名：{}", targetClass.getName(), propertyName);
        // 检查是否为JSON对象字符串 -> 目标对象/集合/Map
        if (sourceValueStr.startsWith("{") && sourceValueStr.endsWith("}") && JSONUtil.isTypeJSONObject(sourceValueStr)) {
            LOG.trace("字符串源值看起来是JSON对象");
            return handleJsonObjectStringConversion(targetClass, sourceValueStr, propertyName, context);
        }
        if (sourceValueStr.startsWith("[") && sourceValueStr.endsWith("]") && JSONUtil.isTypeJSONArray(sourceValueStr)) {
            LOG.trace("字符串源值看起来是JSON数组");
            return handleJsonArrayStringConversion(targetClass, sourceValueStr, propertyName, context);
        }
        // 基本字符串 -> 目标类型转换（使用Hutool）
        LOG.trace("执行基本字符串到{}的转换", targetClass.getName());
        return Convert.convert(targetClass, sourceValueStr);
    }

    /**
     * 处理JSON对象字符串的转换
     * <p>
     * 该方法负责将JSON对象字符串转换为目标类型。主要处理以下几种情况：
     * 1. JSON对象字符串 -> Map类型（支持泛型类型推断）
     * 2. JSON对象字符串 -> JavaBean（复杂对象）
     * 3. JSON对象字符串 -> 其他简单类型（不太常见）
     * </p>
     * <p>
     * 转换策略：
     * - 对于Map目标类型：尝试推断Map的键值泛型类型，并进行相应转换
     * - 对于JavaBean目标类型：使用JSONUtil直接解析为目标Bean
     * - 对于其他类型：尝试使用Hutool的Convert工具进行转换
     * </p>
     * <p>
     * 异常处理：
     * 所有解析和转换过程中的异常都会被捕获并记录，确保转换过程不会因异常而中断。
     * 在转换失败时，方法会返回null而不是抛出异常。
     * </p>
     * <p>
     * 性能优化：
     * - 对于简单Map<String, Object>类型，直接返回解析结果，避免不必要的二次转换
     * - 对于需要特定Map实现或不同泛型类型的情况，使用BeanUtil进行精确转换
     * </p>
     *
     * @param targetClass   目标类型，不能为null
     * @param jsonObjectStr JSON对象字符串，不能为null
     * @param propertyName  属性名，可以为null
     * @param context       上下文信息，可以为null
     * @return 转换后的目标类型对象，如果转换失败则返回null
     */
    private Object handleJsonObjectStringConversion(Class<?> targetClass, String jsonObjectStr, String propertyName, Object context) {
        LOG.trace("将JSON对象字符串转换为{}", targetClass.getName());
        if (Map.class.isAssignableFrom(targetClass)) {
            // 尝试确定Map的泛型类型（如果可能）
            Type keyType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            Type valueType = getGenericTypeArgumentForTarget(propertyName, targetClass, 1);
            LOG.trace("目标Map泛型类型: 键={}, 值={}", keyType, valueType);
            // Hutool的JSONUtil.toBean可能不直接支持泛型Map，需要手动解析或使用更强大的JSON库
            try {
                // 常见做法是先解析为Map<String, Object>
                Map<String, Object> intermediateMap = JSONUtil.toBean(jsonObjectStr, Map.class);
                // 然后根据需要进行转换（或调用handleMapSourceConversion处理）
                if (targetClass.isAssignableFrom(Map.class) && (keyType == null || keyType == String.class) && (valueType == null || valueType == Object.class)) {
                    return intermediateMap; // 简单的Map<String, Object>已经足够
                } else {
                    // 如果目标是特定的Map实现或有不同的泛型类型，需要进一步转换
                    LOG.trace("将中间Map进一步转换为目标Map类型 {}", targetClass.getName());
                    // 这可能会委托回handleMapSourceConversion（如果结构合适），
                    // 或者在这里需要自定义逻辑进行键/值类型转换。
                    // 为简单起见，回退到基本的bean转换。
                    return BeanUtil.toBean(intermediateMap, targetClass, CopyOptions.create().setConverter(GXHutoolDataConvert::staticConvert));
                    // 递归调用需谨慎，防止栈溢出
                    // return convert(intermediateMap, targetClass, context);
                }
            } catch (Exception e) {
                LOG.warn("无法将JSON对象字符串转换为Map/Bean {}: {}", targetClass.getName(), e.getMessage());
                return null;
            }
        } else if (isComplexBean(targetClass)) {
            // JSON字符串 -> Bean
            LOG.trace("将JSON对象字符串转换为bean {}", targetClass.getName());
            try {
                // 使用Hutool的JSON -> Bean功能，它能处理嵌套结构
                // 如果JSON结构与bean匹配，可能不需要自定义转换器
                return JSONUtil.toBean(jsonObjectStr, targetClass);
            } catch (Exception e) {
                LOG.warn("无法将JSON对象字符串转换为bean {}: {}", targetClass.getName(), e.getMessage());
                return null;
            }
        } else {
            // JSON字符串 -> 其他简单类型？不太可能，但尝试使用Convert
            LOG.trace("尝试使用Hutool Convert将JSON对象字符串转换为简单类型 {}", targetClass.getName());
            return Convert.convert(targetClass, jsonObjectStr);
        }
    }

    /**
     * 处理JSON数组字符串的转换
     * <p>
     * 该方法负责将JSON数组字符串转换为目标集合或数组类型。主要处理以下几种情况：
     * 1. JSON数组字符串 -> 集合类型（List、Set等，支持泛型元素类型）
     * 2. JSON数组字符串 -> 数组类型
     * </p>
     * <p>
     * 转换策略：
     * 1. 首先确定目标类型的元素类型（集合的泛型类型或数组的组件类型）
     * 2. 使用JSONUtil解析JSON数组为List
     * 3. 对每个元素进行递归转换，确保类型匹配
     * 4. 根据目标类型创建合适的集合或数组并填充转换后的元素
     * </p>
     * <p>
     * 性能优化：
     * 1. 预先分配集合容量，减少动态扩容开销
     * 2. 对于有序集合（如LinkedHashSet），保留原始顺序
     * 3. 使用TypeUtil高效获取类型信息
     * </p>
     * <p>
     * 异常处理：
     * 捕获并记录所有解析和转换过程中的异常，确保转换过程不会因异常而中断
     * </p>
     *
     * @param targetClass  目标类型，不能为null
     * @param jsonArrayStr JSON数组字符串，不能为null
     * @param propertyName 属性名，可以为null
     * @param context      上下文信息，可以为null
     * @return 转换后的集合或数组，如果转换失败则返回null
     */
    private Object handleJsonArrayStringConversion(Class<?> targetClass, String jsonArrayStr, String propertyName, Object context) {
        LOG.trace("将JSON数组字符串转换为{}", targetClass.getName());
        Type targetComponentType = null;

        // 确定目标元素类型
        if (Collection.class.isAssignableFrom(targetClass)) {
            // 对于集合类型，获取泛型参数类型
            targetComponentType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            if (targetComponentType == null) targetComponentType = Object.class; // 如果没有泛型信息，默认为Object
            LOG.trace("目标集合元素类型: {}", targetComponentType);
        } else if (targetClass.isArray()) {
            // 对于数组类型，获取数组组件类型
            targetComponentType = targetClass.getComponentType();
            LOG.trace("目标数组元素类型: {}", targetComponentType);
        } else {
            // 不支持转换为非集合/非数组类型
            LOG.warn("无法将JSON数组字符串转换为非集合/非数组类型: {}", targetClass.getName());
            return null;
        }

        try {
            // 使用Hutool JSONUtil将JSON数组解析为List<元素类型>
            List<?> parsedList = JSONUtil.toList(jsonArrayStr, TypeUtil.getClass(targetComponentType));

            // 确保列表元素类型正确（JSONUtil可能对嵌套结构返回JSONObject/JSONArray）
            // 并将解析后的列表转换为最终目标类型（List、Set、Array）

            // 根据目标类型创建合适的集合
            Collection<Object> resultCollection = null;
            if (List.class.isAssignableFrom(targetClass) || targetClass.equals(Collection.class)) {
                resultCollection = new ArrayList<>(parsedList.size());
            } else if (Set.class.isAssignableFrom(targetClass)) {
                resultCollection = new LinkedHashSet<>(parsedList.size()); // 可能需要保持顺序
            } else if (targetClass.isArray()) {
                // 数组类型在元素转换后处理
            } else {
                LOG.warn("不支持的目标集合类型，无法进行JSON数组转换: {}", targetClass.getName());
                return null;
            }

            // 转换每个元素
            List<Object> convertedList = new ArrayList<>(parsedList.size());
            for (Object item : parsedList) {
                // 递归转换每个元素，从解析类型到目标组件类型
                Object convertedItem = convert(item, TypeUtil.getClass(targetComponentType), null); // 数组元素没有特定上下文
                convertedList.add(convertedItem);
                if (resultCollection != null) {
                    resultCollection.add(convertedItem);
                }
            }

            // 根据目标类型返回合适的结果
            if (targetClass.isArray()) {
                LOG.trace("将转换后的列表转换为类型{}的数组", targetComponentType);
                // 创建目标类型的数组并填充元素
                Object resultArray = Array.newInstance(TypeUtil.getClass(targetComponentType), convertedList.size());
                for (int i = 0; i < convertedList.size(); ++i) {
                    Array.set(resultArray, i, convertedList.get(i));
                }
                return resultArray;
            } else {
                // 返回集合类型
                return resultCollection;
            }

        } catch (Exception e) {
            LOG.warn("无法将JSON数组字符串转换为{}: {}", targetClass.getName(), e.getMessage());
            return null;
        }
    }


    /**
     * 处理集合类型源值的转换
     * <p>
     * 该方法负责将Collection类型的源值（如List、Set等）转换为目标类型，支持以下转换场景：
     * 1. Collection -> Collection：支持不同集合类型之间的转换（如List到Set、ArrayList到LinkedList等）
     * 2. Collection -> Array：将集合转换为数组类型
     * 3. Collection -> 其他类型：尝试使用Hutool的通用转换（如集合转字符串等）
     * </p>
     * <p>
     * 泛型处理：
     * 1. 自动推断目标集合的泛型类型，确保元素类型正确转换
     * 2. 对于无法确定泛型类型的情况，默认使用Object类型作为安全回退
     * 3. 递归处理集合中的每个元素，支持深层嵌套集合的转换
     * </p>
     * <p>
     * 安全特性：
     * 1. 预分配合适大小的集合，减少动态扩容开销
     * 2. 安全处理目标集合实例化失败的情况，提供合理的回退策略
     * 3. 处理原始类型数组中的null值转换，避免NullPointerException
     * 4. 捕获并记录类型不匹配异常，确保转换过程不会中断
     * </p>
     *
     * @param targetClass      目标类型，可以是集合类型、数组类型或其他类型
     * @param propertyName     属性名，用于获取泛型类型信息
     * @param sourceCollection 源集合对象
     * @param context          上下文信息，通常是setter方法名
     * @return 转换后的目标类型对象，如果转换失败则可能返回null
     */
    private Object handleCollectionSourceConversion(Class<?> targetClass, String propertyName, Collection<?> sourceCollection, Object context) {
        LOG.trace("Handling Collection source (size {}) conversion to {} for property {}", sourceCollection.size(), targetClass.getName(), propertyName);
        Type targetComponentType = null;

        // 确定目标组件类型 (List<TargetElement>, Set<TargetElement>, TargetElement[])
        if (Collection.class.isAssignableFrom(targetClass)) {
            targetComponentType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            if (targetComponentType == null) targetComponentType = Object.class; // 如果无法获取泛型信息，默认使用Object类型
            LOG.trace("目标集合组件类型: {}", targetComponentType);
        } else if (targetClass.isArray()) {
            targetComponentType = targetClass.getComponentType();
            LOG.trace("目标数组组件类型: {}", targetComponentType);
        } else {
            // 可能是将集合转换为单个字符串（例如，逗号分隔）或其他类型，使用Hutool进行转换
            LOG.trace("目标既不是集合也不是数组，回退到Hutool Convert处理集合源值");
            return Convert.convertWithCheck(targetClass, sourceCollection, null, false);
        }

        Class<?> targetComponentClass = TypeUtil.getClass(targetComponentType);
        if (targetComponentClass == null) {
            LOG.warn("无法确定属性{}的目标组件类类型", propertyName);
            targetComponentClass = Object.class; // 回退到Object类型
        }


        Collection<Object> resultCollection = null;
        if (targetClass.equals(List.class) || targetClass.equals(Collection.class) || targetClass.equals(ArrayList.class)) {
            resultCollection = new ArrayList<>(sourceCollection.size());
        } else if (targetClass.equals(Set.class) || targetClass.equals(HashSet.class)) {
            resultCollection = new HashSet<>(sourceCollection.size());
        } else if (targetClass.equals(LinkedHashSet.class)) {
            resultCollection = new LinkedHashSet<>(sourceCollection.size());
        } else if (!targetClass.isArray()) {
            // 尝试实例化其他集合类型
            try {
                resultCollection = (Collection<Object>) ReflectUtil.newInstanceIfPossible(targetClass);
                if (resultCollection == null) {
                    LOG.warn("无法实例化目标集合类型 {}，回退到ArrayList", targetClass.getName());
                    resultCollection = new ArrayList<>(sourceCollection.size());
                }
            } catch (Exception e) {
                LOG.warn("实例化目标集合类型 {} 时出错，回退到ArrayList。错误: {}", targetClass.getName(), e.getMessage());
                resultCollection = new ArrayList<>(sourceCollection.size());
            }
        }

        List<Object> tempListForArray = targetClass.isArray() ? new ArrayList<>(sourceCollection.size()) : null;

        // 遍历源集合并转换每个元素
        int index = 0;
        for (Object sourceItem : sourceCollection) {
            // 递归调用convert方法转换每个元素
            // 提供null上下文，因为setter上下文不适用于元素
            LOG.trace("转换集合元素 #{} 从 {} 到 {}", index, sourceItem != null ? sourceItem.getClass().getName() : "null", targetComponentClass.getName());
            Object convertedItem = convert(sourceItem, targetComponentClass, null);

            if (resultCollection != null) {
                resultCollection.add(convertedItem);
            }
            if (tempListForArray != null) {
                tempListForArray.add(convertedItem);
            }
            index++;
        }

        // 如果目标是数组，创建并填充它
        if (targetClass.isArray()) {
            LOG.trace("将临时列表转换为类型为 {} 的数组", targetComponentClass.getName());
            Object resultArray = Array.newInstance(targetComponentClass, tempListForArray.size());
            for (int i = 0; i < tempListForArray.size(); i++) {
                // 我们已经转换了项目，只需要处理如果Array.set需要它的潜在null值
                Object itemToSet = tempListForArray.get(i);
                if (itemToSet == null && targetComponentClass.isPrimitive()) {
                    // 处理原始类型数组元素的null转换（例如，int为0，boolean为false）
                    LOG.trace("将null设置为原始类型数组 {} 的默认值", targetComponentClass.getName());
                    itemToSet = GXCommonUtils.getClassDefaultValue(targetComponentClass);
                }
                try {
                    Array.set(resultArray, i, itemToSet);
                } catch (IllegalArgumentException e) {
                    LOG.warn("设置数组元素 {} 在索引 {} 时类型不匹配: 期望 {}, 得到 {}. 值: {}",
                            targetComponentClass.getName(), i, targetComponentClass,
                            itemToSet != null ? itemToSet.getClass().getName() : "null", itemToSet);
                    // 根据数组类型设置默认值或null
                    Array.set(resultArray, i, GXCommonUtils.getClassDefaultValue(targetComponentClass)); // 设置 0/false/null
                }
            }
            return resultArray;
        } else {
            return resultCollection;
        }
    }

    /**
     * 处理数组类型源值的转换
     * <p>
     * 该方法负责将数组类型的源值转换为目标类型，通过将数组包装为List后委托给集合转换方法处理。
     * 支持以下转换场景：
     * 1. Array -> Collection：将数组转换为各种集合类型
     * 2. Array -> Array：支持不同类型数组之间的转换
     * 3. Array -> 其他类型：通过集合转换逻辑处理特殊情况
     * </p>
     * <p>
     * 优化策略：
     * 1. 复用集合转换逻辑，避免代码重复
     * 2. 使用Arrays.asList高效包装数组，无需创建新集合
     * 3. 保持与集合转换相同的类型安全和异常处理机制
     * </p>
     * <p>
     * 注意事项：
     * 1. 该方法仅处理对象数组，基本类型数组在convert方法中直接使用Hutool处理
     * 2. Arrays.asList返回的List是固定大小的，但这不影响转换过程
     * </p>
     *
     * @param targetClass  目标类型，可以是集合类型、数组类型或其他类型
     * @param propertyName 属性名，用于获取泛型类型信息
     * @param sourceArray  源数组对象
     * @param context      上下文信息，通常是setter方法名
     * @return 转换后的目标类型对象，如果转换失败则可能返回null
     */
    private Object handleArraySourceConversion(Class<?> targetClass, String propertyName, Object[] sourceArray, Object context) {
        // 本质上是通过将数组包装为列表来委托给集合处理逻辑
        LOG.trace("处理数组源值(长度 {})转换到 {} 属性 {}", sourceArray.length, targetClass.getName(), propertyName);
        return handleCollectionSourceConversion(targetClass, propertyName, Arrays.asList(sourceArray), context);
    }

    /**
     * 处理Map类型源值的转换
     * <p>
     * 该方法负责将Map类型的源值转换为目标类型，支持以下转换场景：
     * 1. Map -> Bean：将Map转换为JavaBean对象，自动映射字段
     * 2. Map -> Map：支持不同Map类型之间的转换，包括键值类型的转换
     * 3. Map -> 其他类型：尝试使用Hutool的通用转换（较少见的情况）
     * </p>
     * <p>
     * 泛型处理：
     * 1. 自动推断目标Map的键值泛型类型，确保类型安全转换
     * 2. 对于无法确定泛型类型的情况，默认使用Object类型作为安全回退
     * 3. 递归处理Map中的每个键值对，支持深层嵌套Map的转换
     * </p>
     * <p>
     * 安全特性：
     * 1. 预分配合适大小的Map，减少动态扩容开销
     * 2. 安全处理目标Map实例化失败的情况，提供合理的回退策略
     * 3. 特殊处理TreeMap的Comparable键要求，避免运行时异常
     * 4. 捕获并记录类型不匹配异常，确保转换过程不会中断
     * </p>
     * <p>
     * 性能优化：
     * 1. 对常见Map类型（HashMap、LinkedHashMap等）进行特殊处理，避免反射开销
     * 2. 使用CopyOptions配置转换器，确保嵌套属性的正确转换
     * 3. 仅在必要时进行类型转换，避免不必要的对象创建
     * </p>
     *
     * @param targetClass  目标类型，可以是Bean类型、Map类型或其他类型
     * @param propertyName 属性名，用于获取泛型类型信息
     * @param sourceMap    源Map对象
     * @param context      上下文信息，通常是setter方法名
     * @return 转换后的目标类型对象，如果转换失败则可能返回null
     */
    private Object handleMapSourceConversion(Class<?> targetClass, String propertyName, Map<?, ?> sourceMap, Object context) {
        LOG.trace("处理Map源值(大小 {})转换到 {} 属性 {}", sourceMap.size(), targetClass.getName(), propertyName);

        // 情况1: Map -> Bean
        if (isComplexBean(targetClass)) {
            LOG.trace("将Map转换为Bean: {}", targetClass.getName());
            // 使用Hutool BeanUtil进行Map -> Bean转换，可能使用此转换器处理嵌套值
            CopyOptions options = CopyOptions.create().setConverter(GXHutoolDataConvert::staticConvert);
            return BeanUtil.toBean(sourceMap, targetClass, options);
        }

        // 情况2: Map -> Map
        if (Map.class.isAssignableFrom(targetClass)) {
            // 确定目标Map的键和值类型
            Type targetKeyType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            Type targetValueType = getGenericTypeArgumentForTarget(propertyName, targetClass, 1);
            Class<?> targetKeyClass = TypeUtil.getClass(targetKeyType);
            Class<?> targetValueClass = TypeUtil.getClass(targetValueType);

            if (targetKeyClass == null) targetKeyClass = Object.class;
            if (targetValueClass == null) targetValueClass = Object.class;

            LOG.trace("目标Map类型: 键={}, 值={}", targetKeyClass.getName(), targetValueClass.getName());

            Map<Object, Object> resultMap;
            // 实例化目标Map类型
            if (targetClass.equals(Map.class) || targetClass.equals(HashMap.class)) {
                resultMap = new HashMap<>(sourceMap.size());
            } else if (targetClass.equals(LinkedHashMap.class)) {
                resultMap = new LinkedHashMap<>(sourceMap.size());
            } else if (targetClass.equals(TreeMap.class)) {
                resultMap = new TreeMap<>(); // 要求键实现Comparable接口或提供Comparator
            } else if (targetClass.equals(ConcurrentHashMap.class)) {
                resultMap = new ConcurrentHashMap<>(sourceMap.size());
            } else {
                try {
                    resultMap = (Map<Object, Object>) ReflectUtil.newInstanceIfPossible(targetClass);
                    if (resultMap == null) {
                        LOG.warn("无法实例化目标Map类型 {}，回退到HashMap", targetClass.getName());
                        resultMap = new HashMap<>(sourceMap.size());
                    }
                } catch (Exception e) {
                    LOG.warn("实例化目标Map类型 {} 时出错，回退到HashMap。错误: {}", targetClass.getName(), e.getMessage());
                    resultMap = new HashMap<>(sourceMap.size());
                }
            }


            // 遍历源Map并转换键/值
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                Object sourceKey = entry.getKey();
                Object sourceValue = entry.getValue();

                // 转换键
                LOG.trace("转换Map键从 {} 到 {}", sourceKey != null ? sourceKey.getClass().getName() : "null", targetKeyClass.getName());
                Object convertedKey = convert(sourceKey, targetKeyClass, null); // 键没有特定上下文

                // 转换值
                LOG.trace("转换Map值从 {} 到 {}", sourceValue != null ? sourceValue.getClass().getName() : "null", targetValueClass.getName());
                Object convertedValue = convert(sourceValue, targetValueClass, null); // 值没有特定上下文

                // 处理TreeMap要求Comparable键但转换结果不是Comparable的潜在问题
                if (resultMap instanceof TreeMap && !(convertedKey instanceof Comparable)) {
                    LOG.warn("转换后的键类型 {} 不是Comparable，这可能导致TreeMap出现问题，键: {}",
                            convertedKey != null ? convertedKey.getClass().getName() : "null", convertedKey);
                    // 可选择跳过、抛出异常或使用toString表示
                }

                resultMap.put(convertedKey, convertedValue);
            }
            return resultMap;
        }

        // 情况3: Map -> 其他类型？不太可能，回退到Hutool Convert
        LOG.trace("目标既不是Bean也不是Map，回退到Hutool Convert处理Map源值");
        return Convert.convertWithCheck(targetClass, sourceMap, null, false);
    }


    /**
     * 获取目标属性的泛型组件类型
     * <p>
     * 该方法用于获取目标属性的泛型参数类型，例如List<T>或Set<T>中的T，或Map<K,V>中的K和V。
     * 方法基于属性名和目标类结构，通过多种策略尝试解析泛型类型信息。
     * </p>
     * <p>
     * 解析策略：
     * 1. 首先尝试从缓存中获取泛型类型信息
     * 2. 如果缓存命中且类型匹配，直接返回对应的泛型参数
     * 3. 如果缓存未命中或类型不匹配，尝试从字段缓存中直接查找字段并获取泛型信息
     * 4. 如果所有尝试都失败，返回null表示无法解析
     * </p>
     * <p>
     * 性能优化：
     * 1. 使用缓存减少反射操作，显著提高性能
     * 2. 使用多级查找策略，优先使用最高效的方法
     * 3. 详细的日志记录，便于调试和性能分析
     * </p>
     *
     * @param propertyName 要设置的属性名
     * @param targetType   目标*集合*或*映射*本身的类（例如List.class, Map.class）
     * @param index        泛型参数的索引（对于List/Set元素为0，对于Map键为0，对于Map值为1）
     * @return 泛型类型，如果未找到或不适用则返回null
     */
    private Type getGenericTypeArgumentForTarget(String propertyName, Class<?> targetType, int index) {
        if (propertyName == null) {
            LOG.trace("无法在没有属性名的情况下解析目标{}的泛型类型", targetType.getName());
            return null; // 没有属性上下文无法解析
        }

        // 1. 首先尝试缓存（基于初始目标类的字段）
        Type genericFieldType = genericTypeCache.get(propertyName);
        LOG.trace("属性'{}'的泛型类型缓存查找结果: {}", propertyName, genericFieldType);

        // 2. 如果缓存未命中或不匹配目标类型结构，尝试对特定目标类型进行反射
        // （如果targetType只是List.class而没有上下文，这可能不太准确）
        // 然而，CGLIB通常通过`convert`中的主要'targetClass'参数提供特定的目标字段/setter类型。
        // 如果缓存的类型似乎适合目标结构，我们可以使用它进行优化。

        if (genericFieldType instanceof ParameterizedType pType) {
            // 检查缓存字段的原始类型是否匹配预期的集合/映射类型
            if (targetType.isAssignableFrom((Class<?>) pType.getRawType())) {
                Type[] typeArguments = pType.getActualTypeArguments();
                if (index < typeArguments.length) {
                    LOG.trace("从缓存中解析属性'{}'在索引{}处的泛型类型参数: {}", propertyName, index, typeArguments[index]);
                    return typeArguments[index];
                } else {
                    LOG.trace("属性'{}'的泛型类型参数索引{}超出范围", propertyName, index);
                }
            } else {
                LOG.trace("缓存的泛型类型{}原始类型{}与属性'{}'的目标类型{}不匹配", genericFieldType, pType.getRawType(), propertyName, targetType.getName());
            }
        } else {
            LOG.trace("属性'{}'的缓存类型不是参数化类型: {}", propertyName, genericFieldType);
        }

        // 3. 备选方案：尝试再次直接在'initialTargetClass'上查找字段？
        // 这假设propertyName属于顶级bean，这通常是正确的。
        Field field = fieldCache.get(propertyName);
        if (field != null) {
            Type directGenericType = field.getGenericType();
            if (directGenericType instanceof ParameterizedType pType) {
                if (targetType.isAssignableFrom((Class<?>) pType.getRawType())) {
                    Type[] typeArguments = pType.getActualTypeArguments();
                    if (index < typeArguments.length) {
                        LOG.trace("通过直接字段查找解析属性'{}'在索引{}处的泛型类型参数: {}", propertyName, index, typeArguments[index]);
                        return typeArguments[index];
                    }
                }
            }
        }

        LOG.warn("无法解析目标类型{}上属性'{}'在索引{}处的泛型类型参数。返回null。", targetType.getName(), propertyName, index);
        return null; // 表示解析失败
    }

    // Keep original methods for reference if needed, but they are superseded by getGenericTypeArgumentForTarget
    /*
    private Class<?> getGenericComponentType(String propertyName) {
        Type genericType = getGenericTypeArgumentForTarget(propertyName, Collection.class, 0); // Assuming target is collection
        return TypeUtil.getClass(genericType);
    }

    private Type getGenericTypeArgument(String propertyName, int index) {
        return getGenericTypeArgumentForTarget(propertyName, Map.class, index); // Assuming target is map
    }
    */
}