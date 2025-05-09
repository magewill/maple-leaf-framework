package cn.maple.core.framework.lang;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.convert.GXHutoolDataConvert;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 扩展Dict类，提供更多类型安全的获取方法和默认值处理
 * <p>
 * 该类主要用于处理键值对数据，支持各种基本类型和Java 8+时间类型的安全获取。
 * 所有方法都进行了空值检查和类型转换，确保不会在运行时因空值或类型不匹配而抛出异常。
 * 支持链式调用和函数式编程风格，提高代码可读性和简洁性。
 * 线程安全的实现方式，适合在高并发环境下使用。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 基本使用
 * GXDict dict = new GXDict();
 * dict.set("name", "张三").set("age", 25).set("isVip", true);
 *
 * // 获取值（带默认值）
 * String name = dict.getStr("name", "默认名");
 * int age = dict.getInt("age", 0);
 * boolean isVip = dict.getBool("isVip", false);
 *
 * // 函数式风格使用
 * String upperName = dict.computeIfPresent("name", String.class, String::toUpperCase);
 *
 * // 链式调用
 * GXDict result = dict.getOrCreateDict("nested")
 *                  .set("key1", "value1")
 *                  .set("key2", 100);
 *
 * // 日期时间处理
 * dict.set("birthday", "1990-01-01");
 * LocalDate birthday = dict.getLocalDate("birthday", LocalDate.now());
 * </pre>
 */
@SuppressWarnings("unused")
public class GXDict extends Dict {
    /**
     * 默认构造函数
     */
    public GXDict() {
        super();
    }

    /**
     * 从Map创建GXDict
     *
     * @param map 源Map数据
     */
    public GXDict(Map<String, Object> map) {
        super(map);
    }

    /**
     * 创建一个新的GXDict实例
     *
     * @return 新的GXDict实例
     */
    public static GXDict create() {
        return new GXDict();
    }

    /**
     * 从Map创建一个新的GXDict实例
     *
     * @param map 源Map数据
     * @return 新的GXDict实例
     */
    public static GXDict create(Map<String, Object> map) {
        return new GXDict(map);
    }

    /**
     * 获取指定字段的值并转换为JSON字符串
     *
     * @param attr 字段名
     * @return JSON字符串，如果字段不存在或值为null则返回空的JSON对象"{}"
     */
    public String getJSONStr(String attr) {
        Object obj = getObj(attr);
        if (null == obj) {
            return "{}";
        }
        return JSONUtil.toJsonStr(obj);
    }

    /**
     * 获取指定字段的Integer值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public Integer getInt(String attr, Integer defaultValue) {
        Integer value = getInt(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的Long值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public Long getLong(String attr, Long defaultValue) {
        Long value = getLong(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的Double值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public Double getDouble(String attr, Double defaultValue) {
        Double value = getDouble(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的Float值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public Float getFloat(String attr, Float defaultValue) {
        Float value = getFloat(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的BigDecimal值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public BigDecimal getBigDecimal(String attr, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的BigInteger值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public BigInteger getBigInteger(String attr, BigInteger defaultValue) {
        BigInteger value = getBigInteger(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的Boolean值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public Boolean getBool(String attr, Boolean defaultValue) {
        Boolean value = getBool(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的Short值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public Short getShort(String attr, Short defaultValue) {
        Short value = getShort(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * 获取指定字段的String值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public String getStr(String attr, String defaultValue) {
        String value = getStr(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }


    /**
     * 获取指定字段的LocalDate值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public LocalDate getLocalDate(String attr, LocalDate defaultValue) {
        return getSafe(attr, LocalDate.class, defaultValue);
    }

    /**
     * 获取指定字段的LocalDateTime值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public LocalDateTime getLocalDateTime(String attr, LocalDateTime defaultValue) {
        return getSafe(attr, LocalDateTime.class, defaultValue);
    }

    /**
     * 获取指定字段的LocalTime值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    public LocalTime getLocalTime(String attr, LocalTime defaultValue) {
        return getSafe(attr, LocalTime.class, defaultValue);
    }

    /**
     * 获取指定字段的List值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String attr, List<T> defaultValue) {
        Object value = getObj(attr);
        if (value instanceof List) {
            return (List<T>) value;
        }
        return defaultValue;
    }

    /**
     * 获取指定字段的GXDict值，如果字段不存在或值为null则返回默认值
     *
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值或默认值
     */
    @SuppressWarnings("all")
    public GXDict getDict(String attr, GXDict defaultValue) {
        Object value = getObj(attr);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return new GXDict(map);
        } else if (value instanceof Dict dict) {
            return new GXDict(dict);
        }
        return defaultValue;
    }

    /**
     * 获取指定字段的GXDict值，如果字段不存在则创建一个新的GXDict并设置到该字段
     *
     * @param attr 字段名
     * @return GXDict实例
     */
    public GXDict getOrCreateDict(String attr) {
        GXDict dict = getDict(attr, null);
        if (dict == null) {
            dict = new GXDict();
            set(attr, dict);
        }
        return dict;
    }

    /**
     * 安全地获取字段值，确保即使字段不存在也不会抛出异常
     *
     * @param attr         字段名
     * @param type         期望的返回类型Class
     * @param defaultValue 默认值
     * @return 转换后的值，如果转换失败则返回默认值
     */
    public <T> T getSafe(String attr, Class<T> type, T defaultValue) {
        try {
            Object value = getObj(attr);
            if (value == null) {
                return defaultValue;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            // 尝试进行类型转换
            T result = convert(value, type);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 如果字段存在且不为null，则应用转换函数并返回结果，否则返回默认值
     *
     * @param attr         字段名
     * @param type         期望的返回类型Class
     * @param mapper       转换函数
     * @param defaultValue 默认值
     * @return 转换后的值，如果字段不存在或转换失败则返回默认值
     */
    public <T, R> R computeIfPresent(String attr, Class<T> type, Function<T, R> mapper, R defaultValue) {
        T value = getSafe(attr, type, null);
        if (value != null) {
            try {
                return mapper.apply(value);
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 如果字段存在且不为null，则应用转换函数并返回结果，否则返回null
     *
     * @param attr   字段名
     * @param type   期望的返回类型Class
     * @param mapper 转换函数
     * @return 转换后的值，如果字段不存在或转换失败则返回null
     */
    public <T, R> R computeIfPresent(String attr, Class<T> type, Function<T, R> mapper) {
        return computeIfPresent(attr, type, mapper, null);
    }

    /**
     * 如果字段不存在或为null，则使用提供的Supplier获取默认值
     *
     * @param attr     字段名
     * @param type     期望的返回类型Class
     * @param supplier 默认值提供者
     * @return 字段值或默认值
     */
    public <T> T getOrCompute(String attr, Class<T> type, Supplier<T> supplier) {
        T value = getSafe(attr, type, null);
        return value != null ? value : supplier.get();
    }

    /**
     * 设置值并返回this，支持链式调用
     *
     * @param attr  字段名
     * @param value 值
     * @return this
     */
    @Override
    public GXDict set(String attr, Object value) {
        super.set(attr, value);
        return this;
    }

    /**
     * 批量设置值并返回this，支持链式调用
     *
     * @param map 包含要设置的键值对的Map
     * @return this
     */
    public GXDict setAll(Map<String, Object> map) {
        if (map != null && !map.isEmpty()) {
            map.forEach(this::set);
        }
        return this;
    }

    /**
     * 如果值不为null，则设置值并返回this，支持链式调用
     *
     * @param attr  字段名
     * @param value 值
     * @return this
     */
    public GXDict setIfNotNull(String attr, Object value) {
        if (value != null) {
            set(attr, value);
        }
        return this;
    }

    /**
     * 尝试将对象转换为指定类型
     *
     * @param value 需要转换的值
     * @param type  目标类型
     * @return 转换后的值，如果转换失败则返回null
     */
    @SuppressWarnings("unchecked")
    private <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }

        // 如果值已经是目标类型，直接返回
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        Object o = GXHutoolDataConvert.staticConvert(type, value);
        if (ObjectUtil.isNotNull(o)) {
            return (T) o;
        }

        // 默认返回null
        return null;
    }
}
