package cn.maple.core.framework.lang;

import cn.hutool.core.lang.Dict;
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

@SuppressWarnings("unused")
public class GXDict extends Dict {
    public GXDict() {
        super();
    }

    public GXDict(Map<String, Object> map) {
        super();
        setAll(map);
    }

    public static GXDict create() {
        return new GXDict();
    }

    public static GXDict create(Map<String, Object> map) {
        return new GXDict(map);
    }

    private static Class<?> primitiveToWrapper(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == void.class) return Void.class;
        return type;
    }

    public String getJSONStr(String attr) {
        Object obj = getObj(attr);
        if (null == obj) {
            return "{}";
        }
        return JSONUtil.toJsonStr(obj);
    }

    public Integer getInt(String attr, Integer defaultValue) {
        Integer value = getInt(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public Long getLong(String attr, Long defaultValue) {
        Long value = getLong(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public Double getDouble(String attr, Double defaultValue) {
        Double value = getDouble(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public Float getFloat(String attr, Float defaultValue) {
        Float value = getFloat(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public BigDecimal getBigDecimal(String attr, BigDecimal defaultValue) {
        BigDecimal value = getBigDecimal(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public BigInteger getBigInteger(String attr, BigInteger defaultValue) {
        BigInteger value = getBigInteger(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public Boolean getBool(String attr, Boolean defaultValue) {
        Boolean value = getBool(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public Short getShort(String attr, Short defaultValue) {
        Short value = getShort(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public String getStr(String attr, String defaultValue) {
        String value = getStr(attr);
        if (null == value) {
            value = defaultValue;
        }
        return value;
    }

    public LocalDate getLocalDate(String attr, LocalDate defaultValue) {
        return getSafe(attr, LocalDate.class, defaultValue);
    }

    public LocalDateTime getLocalDateTime(String attr, LocalDateTime defaultValue) {
        return getSafe(attr, LocalDateTime.class, defaultValue);
    }

    public LocalTime getLocalTime(String attr, LocalTime defaultValue) {
        return getSafe(attr, LocalTime.class, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> getList(String attr, List<T> defaultValue) {
        Object value = getObj(attr);
        if (value instanceof List) {
            return (List<T>) value;
        }
        return defaultValue;
    }

    public GXDict getDict(String attr, GXDict defaultValue) {
        Object value = getObj(attr);
        if (value instanceof GXDict dict) {
            return dict;
        }
        if (value instanceof Dict dict) {
            return new GXDict(dict);
        }
        if (value instanceof Map<?, ?> map) {
            return toGXDict(map);
        }
        return defaultValue;
    }

    public GXDict getOrCreateDict(String attr) {
        Object value = getObj(attr);
        if (value instanceof GXDict dict) {
            return dict;
        }

        GXDict dict;
        if (value instanceof Dict hutoolDict) {
            dict = new GXDict(hutoolDict);
        } else if (value instanceof Map<?, ?> map) {
            dict = toGXDict(map);
        } else {
            dict = new GXDict();
        }
        set(attr, dict);
        return dict;
    }

    public <T> T getSafe(String attr, Class<T> type, T defaultValue) {
        try {
            if (type == null) {
                return defaultValue;
            }
            Object value = getObj(attr);
            if (value == null) {
                return defaultValue;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            T result = convert(value, type);
            return result != null ? result : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

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

    public <T, R> R computeIfPresent(String attr, Class<T> type, Function<T, R> mapper) {
        return computeIfPresent(attr, type, mapper, null);
    }

    public <T> T getOrCompute(String attr, Class<T> type, Supplier<T> supplier) {
        T value = getSafe(attr, type, null);
        return value != null ? value : supplier.get();
    }

    @Override
    public GXDict set(String attr, Object value) {
        super.set(attr, value);
        return this;
    }

    public GXDict setAll(Map<String, Object> map) {
        if (map != null && !map.isEmpty()) {
            map.forEach(this::set);
        }
        return this;
    }

    public GXDict setIfNotNull(String attr, Object value) {
        if (value != null) {
            set(attr, value);
        }
        return this;
    }

    private <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return type.cast(value);
        }

        Object o = GXHutoolDataConvert.staticConvert(type, value);
        if (o == null) {
            return null;
        }
        if (type.isInstance(o)) {
            return type.cast(o);
        }
        Class<?> wrapperType = primitiveToWrapper(type);
        if (wrapperType.isInstance(o)) {
            @SuppressWarnings("unchecked")
            T result = (T) o;
            return result;
        }

        return null;
    }

    private GXDict toGXDict(Map<?, ?> map) {
        GXDict dict = new GXDict();
        map.forEach((key, value) -> {
            if (key != null) {
                dict.set(key.toString(), value);
            }
        });
        return dict;
    }
}
