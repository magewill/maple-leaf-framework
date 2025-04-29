package cn.maple.core.framework.lang;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 扩展Dict类，提供更多类型安全的获取方法和默认值处理
 * 该类主要用于处理键值对数据，支持各种基本类型的安全获取
 * 所有方法都进行了空值检查，确保不会在运行时因空值而抛出异常
 */
@SuppressWarnings("unused")
public class GXDict extends Dict {
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
     * @param attr         字段名
     * @param defaultValue 默认值
     * @return 字段值
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
     * 安全地获取字段值，确保即使字段不存在也不会抛出异常
     *
     * @param attr 字段名
     * @param type 期望的返回类型Class
     * @return 转换后的值，如果转换失败则返回null
     */
    public <T> T getSafe(String attr, Class<T> type) {
        try {
            Object value = getObj(attr);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            // 尝试进行类型转换
            return convert(value, type);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 尝试将对象转换为指定类型
     *
     * @param value 需要转换的值
     * @param type  目标类型
     * @return 转换后的值
     */
    @SuppressWarnings("unchecked")
    private <T> T convert(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }

        if (type == String.class) {
            return (T) value.toString();
        } else if (type == Integer.class || type == int.class) {
            if (value instanceof Number) {
                return (T) Integer.valueOf(((Number) value).intValue());
            } else {
                try {
                    return (T) Integer.valueOf(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        } else if (type == Long.class || type == long.class) {
            if (value instanceof Number) {
                return (T) Long.valueOf(((Number) value).longValue());
            } else {
                try {
                    return (T) Long.valueOf(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        } else if (type == Double.class || type == double.class) {
            if (value instanceof Number) {
                return (T) Double.valueOf(((Number) value).doubleValue());
            } else {
                try {
                    return (T) Double.valueOf(value.toString());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        } else if (type == Boolean.class || type == boolean.class) {
            if (value instanceof Boolean) {
                return (T) value;
            } else {
                String strValue = value.toString().toLowerCase();
                return (T) Boolean.valueOf(strValue.equals("true") || strValue.equals("1") || strValue.equals("yes"));
            }
        }

        return null;
    }
}
