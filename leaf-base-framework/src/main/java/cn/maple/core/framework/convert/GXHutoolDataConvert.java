package cn.maple.core.framework.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.bean.copier.IJSONTypeConverter;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.TypeUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.GXBaseData;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.time.temporal.Temporal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class GXHutoolDataConvert {
    private static final Logger LOG = LoggerFactory.getLogger(GXHutoolDataConvert.class);
    private static final GXHutoolDataConvert INSTANCE = new GXHutoolDataConvert();

    private GXHutoolDataConvert() {
    }

    public static GXHutoolDataConvert getInstance() {
        return INSTANCE;
    }

    public static Object staticConvert(Type type, Object value) {
        return getInstance().convert(type, value);
    }

    public Object convert(Type type, Object value) {
        Class<?> targetClass = resolveClass(type);
        if (value == null) {
            if (targetClass == Optional.class) {
                return Optional.empty();
            }
            return targetClass != null && targetClass.isPrimitive() ? primitiveDefaultValue(targetClass) : null;
        }

        if (targetClass == null) {
            return normalizeJsonValue(value);
        }
        if (targetClass == Object.class) {
            return value;
        }
        if (isAssignableValue(targetClass, value) && !(value instanceof Collection<?>) && !(value instanceof Map<?, ?>) && !value.getClass().isArray()) {
            return value;
        }

        try {
            if (targetClass.isEnum()) {
                return handleEnumConversion(targetClass, value);
            }
            if (isDateTimeType(targetClass)) {
                return handleDateTimeConversion(targetClass, value);
            }
            if (targetClass == Optional.class) {
                return handleOptionalConversion(type, value);
            }
            if (value instanceof CharSequence charSequence) {
                return handleStringConversion(type, targetClass, charSequence.toString());
            }
            if (value instanceof IJSONTypeConverter jsonTypeConverter) {
                return jsonTypeConverter.toBean(Objects.requireNonNullElse(type, Object.class));
            }
            if (value instanceof Collection<?> collection) {
                return handleCollectionConversion(type, targetClass, collection);
            }
            if (value.getClass().isArray()) {
                return handleArrayConversion(type, targetClass, value);
            }
            if (value instanceof Map<?, ?> map) {
                return handleMapConversion(type, targetClass, map);
            }
            if (value instanceof JSON json) {
                return json.toBean(type);
            }

            Object convertedValue = Convert.convertWithCheck(type, value, null, false);
            return convertedValue != null ? convertedValue : value;
        } catch (Exception e) {
            LOG.warn("Type convert failed: {} -> {}, {}",
                    value.getClass().getName(),
                    type != null ? type.getTypeName() : "null",
                    e.getMessage());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Type convert failed", e);
            }
            return value;
        }
    }

    private Object handleOptionalConversion(Type type, Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.map(item -> convertOptionalValue(type, item));
        }
        return Optional.ofNullable(convertOptionalValue(type, value));
    }

    private Object convertOptionalValue(Type optionalType, Object value) {
        Type valueType = TypeUtil.getTypeArgument(optionalType, 0);
        if (valueType == null || valueType == Object.class) {
            return value;
        }
        return convert(valueType, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object handleEnumConversion(Class<?> targetClass, Object value) {
        if (targetClass.isInstance(value)) {
            return value;
        }
        Object[] enumConstants = targetClass.getEnumConstants();
        if (enumConstants == null || enumConstants.length == 0) {
            return null;
        }
        if (value instanceof CharSequence charSequence) {
            String enumName = charSequence.toString().trim();
            if (enumName.isEmpty()) {
                return null;
            }
            try {
                return Enum.valueOf((Class<Enum>) targetClass, enumName);
            } catch (IllegalArgumentException ignored) {
                for (Object enumConstant : enumConstants) {
                    if (((Enum<?>) enumConstant).name().equalsIgnoreCase(enumName) || enumConstant.toString().equals(enumName)) {
                        return enumConstant;
                    }
                }
                return null;
            }
        }
        if (value instanceof Number number) {
            int index = number.intValue();
            return index >= 0 && index < enumConstants.length ? enumConstants[index] : null;
        }
        if (value.getClass().isEnum()) {
            try {
                return Enum.valueOf((Class<Enum>) targetClass, ((Enum<?>) value).name());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object handleDateTimeConversion(Class<?> targetClass, Object value) {
        try {
            if (value instanceof Date dateValue) {
                if (targetClass == Date.class) {
                    return dateValue;
                }
                if (targetClass == Calendar.class) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(dateValue);
                    return calendar;
                }
            }
            if (value instanceof Calendar calendarValue) {
                if (targetClass == Calendar.class) {
                    return calendarValue;
                }
                if (targetClass == Date.class) {
                    return calendarValue.getTime();
                }
            }
            if (value instanceof CharSequence charSequence) {
                String text = charSequence.toString().trim();
                if (text.isEmpty()) {
                    return null;
                }
                Date date = DateUtil.parse(text);
                if (targetClass == Date.class) {
                    return date;
                }
                if (targetClass == Calendar.class) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    return calendar;
                }
            }
            if (value instanceof Number number) {
                long timestamp = number.longValue();
                if (timestamp < 100000000000L) {
                    timestamp *= 1000;
                }
                Date date = new Date(timestamp);
                if (targetClass == Date.class) {
                    return date;
                }
                if (targetClass == Calendar.class) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    return calendar;
                }
            }
            return Convert.convertWithCheck(targetClass, value, null, false);
        } catch (Exception e) {
            LOG.warn("Date/time convert failed: {} -> {}, {}", value.getClass().getName(), targetClass.getName(), e.getMessage());
            return null;
        }
    }

    private Object handleStringConversion(Type type, Class<?> targetClass, String value) {
        if (CharSequenceUtil.isBlank(value)) {
            return targetClass.isPrimitive() ? primitiveDefaultValue(targetClass) : null;
        }
        if (targetClass == String.class) {
            return value;
        }
        if (JSONUtil.isTypeJSONObject(value)) {
            return handleJsonObjectConversion(type, targetClass, value);
        }
        if (JSONUtil.isTypeJSONArray(value)) {
            return handleJsonArrayConversion(type, targetClass, value);
        }

        Object convertedValue = Convert.convertWithCheck(type, value, null, false);
        return convertedValue != null ? convertedValue : value;
    }

    private Object handleJsonObjectConversion(Type type, Class<?> targetClass, String value) {
        try {
            if (isGXBaseDataType(targetClass)) {
                return JSONUtil.toBean(value, targetClass);
            }
            if (Dict.class.isAssignableFrom(targetClass)) {
                return JSONUtil.toBean(value, Dict.class);
            }
            if (JSONObject.class.isAssignableFrom(targetClass)) {
                return JSONUtil.parseObj(value);
            }
            if (Map.class.isAssignableFrom(targetClass)) {
                return handleMapConversion(type, targetClass, JSONUtil.toBean(value, Map.class));
            }
            return convertToJavaBean(targetClass, value);
        } catch (Exception e) {
            LOG.warn("JSON object convert failed: {} -> {}, {}", value, targetClass.getName(), e.getMessage());
            return null;
        }
    }

    private Object convertToJavaBean(Class<?> targetClass, String value) {
        try {
            return JSONUtil.toBean(value, targetClass);
        } catch (Exception e) {
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw e;
            }
            try {
                return objectMapper.readValue(value, targetClass);
            } catch (Exception jacksonException) {
                throw e;
            }
        }
    }

    private Object handleJsonArrayConversion(Type type, Class<?> targetClass, String value) {
        if (!Collection.class.isAssignableFrom(targetClass) && !targetClass.isArray()) {
            return null;
        }
        List<?> parsedList = JSONUtil.parseArray(value);
        Type componentType = targetClass.isArray() ? targetClass.getComponentType() : getTypeArgument(type, 0);
        if (componentType == null) {
            componentType = Dict.class;
        }
        return convertCollectionToTarget(targetClass, parsedList, componentType);
    }

    private Object handleCollectionConversion(Type type, Class<?> targetClass, Collection<?> sourceCollection) {
        if (!Collection.class.isAssignableFrom(targetClass) && !targetClass.isArray()) {
            return null;
        }
        Type componentType = targetClass.isArray() ? targetClass.getComponentType() : getTypeArgument(type, 0);
        if (componentType == null) {
            componentType = Object.class;
        }
        return convertCollectionToTarget(targetClass, sourceCollection, componentType);
    }

    private Object handleArrayConversion(Type type, Class<?> targetClass, Object sourceArray) {
        if (!Collection.class.isAssignableFrom(targetClass) && !targetClass.isArray()) {
            return null;
        }
        int length = Array.getLength(sourceArray);
        List<Object> sourceList = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            sourceList.add(Array.get(sourceArray, i));
        }
        return handleCollectionConversion(type, targetClass, sourceList);
    }

    private Object convertCollectionToTarget(Class<?> targetClass, Collection<?> sourceCollection, Type componentType) {
        Class<?> componentClass = resolveClass(componentType);
        if (componentClass == null) {
            componentClass = Object.class;
        }
        if (targetClass.isArray()) {
            Object resultArray = Array.newInstance(componentClass, sourceCollection.size());
            int index = 0;
            for (Object item : sourceCollection) {
                Object convertedItem = convert(componentType, item);
                if (convertedItem == null && componentClass.isPrimitive()) {
                    convertedItem = primitiveDefaultValue(componentClass);
                }
                Array.set(resultArray, index++, convertedItem);
            }
            return resultArray;
        }

        Collection<Object> targetCollection = newTargetCollection(targetClass, sourceCollection.size());
        for (Object item : sourceCollection) {
            Object convertedItem = componentClass == Object.class ? normalizeJsonValue(item) : convert(componentType, item);
            targetCollection.add(convertedItem);
        }
        return targetCollection;
    }

    private Collection<Object> newTargetCollection(Class<?> targetClass, int size) {
        if (targetClass == List.class || targetClass == Collection.class || targetClass == ArrayList.class) {
            return new ArrayList<>(size);
        }
        if (targetClass == Set.class || targetClass == HashSet.class) {
            return new HashSet<>(size);
        }
        if (targetClass == LinkedHashSet.class) {
            return new LinkedHashSet<>(size);
        }
        if (targetClass == ArrayDeque.class) {
            return new ArrayDeque<>(size);
        }
        try {
            @SuppressWarnings("unchecked")
            Collection<Object> collection = (Collection<Object>) targetClass.getDeclaredConstructor().newInstance();
            return collection;
        } catch (Exception e) {
            return new ArrayList<>(size);
        }
    }

    public <T> T convert(Class<T> targetClass, Dict value) {
        if (value == null) {
            @SuppressWarnings("unchecked")
            T defaultValue = targetClass != null && targetClass.isPrimitive() ? (T) primitiveDefaultValue(targetClass) : null;
            return defaultValue;
        }
        try {
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper != null) {
                return objectMapper.convertValue(value, targetClass);
            }
            return BeanUtil.toBean(value, targetClass, CopyOptions.create().setIgnoreError(true));
        } catch (Exception e) {
            LOG.warn("Dict convert to {} failed: {}", targetClass.getName(), e.getMessage());
            return null;
        }
    }

    private Object handleMapConversion(Type type, Class<?> targetClass, Map<?, ?> sourceMap) {
        if (Dict.class.isAssignableFrom(targetClass)) {
            Dict dict = Dict.create();
            sourceMap.forEach((k, v) -> {
                if (k != null) {
                    dict.set(k.toString(), normalizeJsonValue(v));
                }
            });
            return dict;
        }
        if (JSONObject.class.isAssignableFrom(targetClass)) {
            return JSONUtil.parseObj(sourceMap);
        }
        if (!targetClass.isInterface() && !Map.class.isAssignableFrom(targetClass)) {
            return BeanUtil.toBean(sourceMap, targetClass, CopyOptions.create().setIgnoreError(true).setConverter(GXHutoolDataConvert::staticConvert));
        }
        if (!Map.class.isAssignableFrom(targetClass)) {
            return null;
        }

        Type keyType = TypeUtil.getTypeArgument(type, 0);
        Type valueType = TypeUtil.getTypeArgument(type, 1);
        Class<?> keyClass = keyType == null ? Object.class : resolveClass(keyType);
        Class<?> valueClass = valueType == null ? Object.class : resolveClass(valueType);
        Map<Object, Object> resultMap = newTargetMap(targetClass, sourceMap.size());

        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            Object convertedKey = keyClass == Object.class ? entry.getKey() : convert(keyType, entry.getKey());
            Object convertedValue = valueClass == Object.class ? normalizeJsonValue(entry.getValue()) : convert(valueType, entry.getValue());
            if (resultMap instanceof TreeMap<?, ?> && convertedKey != null && !(convertedKey instanceof Comparable<?>)) {
                convertedKey = convertedKey.toString();
            }
            if (rejectsNullEntries(resultMap) && (convertedKey == null || convertedValue == null)) {
                continue;
            }
            resultMap.put(convertedKey, convertedValue);
        }
        return resultMap;
    }

    private Map<Object, Object> newTargetMap(Class<?> targetClass, int size) {
        if (targetClass == Map.class || targetClass == HashMap.class) {
            return new HashMap<>(size);
        }
        if (targetClass == LinkedHashMap.class) {
            return new LinkedHashMap<>(size);
        }
        if (targetClass == ConcurrentHashMap.class) {
            return new ConcurrentHashMap<>(size);
        }
        if (targetClass == TreeMap.class) {
            return new TreeMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) targetClass.getDeclaredConstructor().newInstance();
            return map;
        } catch (Exception e) {
            return new HashMap<>(size);
        }
    }

    private boolean rejectsNullEntries(Map<?, ?> map) {
        return map instanceof ConcurrentHashMap<?, ?> || map instanceof Hashtable<?, ?>;
    }

    private boolean isGXBaseDataType(Class<?> targetClass) {
        return TypeToken.of(targetClass).isSubtypeOf(GXBaseData.class);
    }

    private static boolean isDateTimeType(Class<?> targetClass) {
        return Date.class.isAssignableFrom(targetClass)
                || Calendar.class.isAssignableFrom(targetClass)
                || Temporal.class.isAssignableFrom(targetClass);
    }

    private static boolean isAssignableValue(Class<?> targetClass, Object value) {
        if (value == null) {
            return !targetClass.isPrimitive();
        }
        if (targetClass.isInstance(value)) {
            return true;
        }
        Class<?> wrapperClass = primitiveToWrapper(targetClass);
        return wrapperClass != targetClass && wrapperClass.isInstance(value);
    }

    private static Class<?> primitiveToWrapper(Class<?> targetClass) {
        if (!targetClass.isPrimitive()) {
            return targetClass;
        }
        if (targetClass == int.class) return Integer.class;
        if (targetClass == long.class) return Long.class;
        if (targetClass == boolean.class) return Boolean.class;
        if (targetClass == double.class) return Double.class;
        if (targetClass == float.class) return Float.class;
        if (targetClass == short.class) return Short.class;
        if (targetClass == byte.class) return Byte.class;
        if (targetClass == char.class) return Character.class;
        if (targetClass == void.class) return Void.class;
        return targetClass;
    }

    private static Object primitiveDefaultValue(Class<?> primitiveClass) {
        if (primitiveClass == boolean.class) return false;
        if (primitiveClass == char.class) return '\0';
        if (primitiveClass == byte.class) return (byte) 0;
        if (primitiveClass == short.class) return (short) 0;
        if (primitiveClass == int.class) return 0;
        if (primitiveClass == long.class) return 0L;
        if (primitiveClass == float.class) return 0F;
        if (primitiveClass == double.class) return 0D;
        return null;
    }

    private static Class<?> resolveClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        return type == null ? null : TypeUtil.getClass(type);
    }

    private static Type getTypeArgument(Type type, int index) {
        return TypeUtil.getTypeArgument(type, index);
    }

    private Object normalizeJsonValue(Object value) {
        if (value instanceof JSONObject jsonObject) {
            Dict dict = Dict.create();
            jsonObject.forEach((k, v) -> dict.set(k, normalizeJsonValue(v)));
            return dict;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object item : collection) {
                list.add(normalizeJsonValue(item));
            }
            return list;
        }
        if (value instanceof Map<?, ?> map && !(value instanceof Dict)) {
            Map<Object, Object> normalized = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> normalized.put(k, normalizeJsonValue(v)));
            return normalized;
        }
        return value;
    }
}
