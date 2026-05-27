package cn.maple.core.framework.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.reference.WeakKeyConcurrentMap;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.TypeUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.*;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GXCGLibDataConvert implements Converter {
    private static final Logger LOG = LoggerFactory.getLogger(GXCGLibDataConvert.class);

    private static final Map<Class<?>, GXCGLibDataConvert> CONVERTER_CACHE = new WeakKeyConcurrentMap<>(new ConcurrentHashMap<>(1024));
    private static final Map<Class<?>, ClassMetadata> CLASS_METADATA_CACHE = new WeakKeyConcurrentMap<>(new ConcurrentHashMap<>(1024));
    private static final Map<BeanCopierCacheKey, BeanCopier> BEAN_COPIER_CACHE = new WeakKeyConcurrentMap<>(new ConcurrentHashMap<>(2048));
    private static final Map<String, String> SETTER_PROPERTY_CACHE = new ConcurrentHashMap<>(256);

    private final ClassMetadata classMetadata;

    private GXCGLibDataConvert(Class<?> targetClass) {
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass must not be null");
        }
        this.classMetadata = CLASS_METADATA_CACHE.computeIfAbsent(targetClass, GXCGLibDataConvert::buildClassMetadata);
    }

    public static GXCGLibDataConvert getConverter(Class<?> targetClass) {
        if (targetClass == null) {
            throw new GXBusinessException("targetClass must not be null");
        }
        return CONVERTER_CACHE.computeIfAbsent(targetClass, GXCGLibDataConvert::new);
    }

    private static BeanCopier getBeanCopier(Class<?> sourceClass, Class<?> targetClass) {
        return BEAN_COPIER_CACHE.computeIfAbsent(new BeanCopierCacheKey(sourceClass, targetClass),
                k -> BeanCopier.create(sourceClass, targetClass, true));
    }

    private static ClassMetadata buildClassMetadata(Class<?> clazz) {
        if (clazz == null || shouldSkipMetadata(clazz)) {
            return ClassMetadata.EMPTY;
        }
        try {
            Field[] fields = ReflectUtil.getFields(clazz);
            Map<String, Field> fieldCache = new HashMap<>(Math.max(16, fields.length));
            Map<String, Type> genericTypeCache = new HashMap<>(Math.max(16, fields.length));
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                fieldCache.putIfAbsent(field.getName(), field);
                genericTypeCache.putIfAbsent(field.getName(), field.getGenericType());
            }
            return new ClassMetadata(Collections.unmodifiableMap(fieldCache), Collections.unmodifiableMap(genericTypeCache));
        } catch (Exception e) {
            LOG.warn("Build field metadata for {} failed: {}", clazz.getName(), e.getMessage());
            return ClassMetadata.EMPTY;
        }
    }

    private static boolean shouldSkipMetadata(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.isArray()
                || clazz.isEnum()
                || clazz.isInterface()
                || Map.class.isAssignableFrom(clazz)
                || Collection.class.isAssignableFrom(clazz)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Character.class == clazz
                || CharSequence.class.isAssignableFrom(clazz)
                || isDateTimeType(clazz);
    }

    private static boolean isDateTimeType(Class<?> clazz) {
        return Date.class.isAssignableFrom(clazz)
                || Calendar.class.isAssignableFrom(clazz)
                || Temporal.class.isAssignableFrom(clazz);
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

    private static boolean canReturnDirectly(Class<?> targetClass, Object value, @Nullable String propertyName) {
        if (propertyName != null && targetClass == Optional.class && value instanceof Optional<?>) {
            return false;
        }
        if (propertyName != null && (value instanceof Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray())) {
            return false;
        }
        return !Collection.class.isAssignableFrom(targetClass) && !Map.class.isAssignableFrom(targetClass) && !targetClass.isArray();
    }

    private static Class<?> primitiveToWrapper(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return clazz;
        }
        if (clazz == int.class) return Integer.class;
        if (clazz == long.class) return Long.class;
        if (clazz == boolean.class) return Boolean.class;
        if (clazz == double.class) return Double.class;
        if (clazz == float.class) return Float.class;
        if (clazz == short.class) return Short.class;
        if (clazz == byte.class) return Byte.class;
        if (clazz == char.class) return Character.class;
        if (clazz == void.class) return Void.class;
        return clazz;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public @Nullable Object convert(@Nullable Object sourceValue, Class targetClass, @Nullable Object context) {
        if (targetClass == null) {
            return null;
        }
        if (sourceValue == null) {
            if (targetClass == Optional.class) {
                return Optional.empty();
            }
            return targetClass.isPrimitive() ? GXCommonUtils.getClassDefaultValue(targetClass) : null;
        }

        String propertyName = context instanceof String ? getPropertyName((String) context) : null;
        Class<?> sourceClass = sourceValue.getClass();
        if (targetClass == Object.class || (isAssignableValue(targetClass, sourceValue) && canReturnDirectly(targetClass, sourceValue, propertyName))) {
            return sourceValue;
        }

        try {
            if (targetClass.isEnum()) {
                return handleEnumConversion(targetClass, sourceValue);
            }
            if (isDateTimeType(targetClass)) {
                return Convert.convert(targetClass, sourceValue);
            }
            if (targetClass == Optional.class) {
                return handleOptionalConversion(propertyName, sourceValue);
            }
            if (sourceValue instanceof CharSequence charSequence) {
                return handleStringSourceConversion(targetClass, propertyName, charSequence.toString());
            }
            if (sourceValue instanceof Collection<?> collection) {
                return handleCollectionSourceConversion(targetClass, propertyName, collection);
            }
            if (sourceClass.isArray()) {
                return handleArraySourceConversion(targetClass, propertyName, sourceValue);
            }
            if (sourceValue instanceof Map<?, ?> map) {
                return handleMapSourceConversion(targetClass, propertyName, map);
            }
            if (isComplexBean(sourceClass) && (isComplexBean(targetClass) || Map.class.isAssignableFrom(targetClass))) {
                if (Map.class.isAssignableFrom(targetClass)) {
                    return BeanUtil.beanToMap(sourceValue);
                }
                Object targetInstance = ReflectUtil.newInstanceIfPossible(targetClass);
                if (targetInstance == null) {
                    LOG.warn("Cannot instantiate target bean {}", targetClass.getName());
                    return null;
                }
                getBeanCopier(sourceClass, targetClass).copy(sourceValue, targetInstance, getConverter(targetClass));
                return targetInstance;
            }

            Object convertedValue = Convert.convertWithCheck(targetClass, sourceValue, null, false);
            if (convertedValue != null && (targetClass.isInstance(convertedValue) || isAssignableValue(targetClass, convertedValue))) {
                return convertedValue;
            }
            return null;
        } catch (Exception e) {
            LOG.warn("Convert property [{}] from {} to {} failed: {} - {}",
                    propertyName != null ? propertyName : "N/A",
                    sourceClass.getName(),
                    targetClass.getName(),
                    e.getClass().getName(),
                    e.getMessage());
            return null;
        }
    }

    private @Nullable String getPropertyName(@Nullable String setterName) {
        if (setterName == null) {
            return null;
        }
        return SETTER_PROPERTY_CACHE.computeIfAbsent(setterName, key -> {
            if (key.startsWith("set") && key.length() > 3) {
                String propertyNamePart = key.substring(3);
                if (propertyNamePart.length() == 1) {
                    return propertyNamePart.toLowerCase();
                }
                if (Character.isUpperCase(propertyNamePart.charAt(0)) && Character.isLowerCase(propertyNamePart.charAt(1))) {
                    return Character.toLowerCase(propertyNamePart.charAt(0)) + propertyNamePart.substring(1);
                }
                return propertyNamePart;
            }
            return key;
        });
    }

    private boolean isComplexBean(Class<?> clazz) {
        return clazz != null
                && !clazz.isPrimitive()
                && !clazz.isArray()
                && !clazz.isEnum()
                && !clazz.isInterface()
                && !Number.class.isAssignableFrom(clazz)
                && !Boolean.class.isAssignableFrom(clazz)
                && !Character.class.isAssignableFrom(clazz)
                && !CharSequence.class.isAssignableFrom(clazz)
                && !isDateTimeType(clazz)
                && !Map.class.isAssignableFrom(clazz)
                && !Collection.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Object handleEnumConversion(Class<?> targetEnumClass, Object sourceValue) {
        if (targetEnumClass.isInstance(sourceValue)) {
            return sourceValue;
        }
        Object[] enumConstants = targetEnumClass.getEnumConstants();
        if (enumConstants == null || enumConstants.length == 0) {
            return null;
        }
        if (sourceValue instanceof CharSequence charSequence) {
            String enumName = charSequence.toString().trim();
            if (enumName.isEmpty()) {
                return null;
            }
            try {
                return Enum.valueOf((Class<Enum>) targetEnumClass, enumName);
            } catch (IllegalArgumentException ignored) {
                for (Object enumConstant : enumConstants) {
                    if (((Enum<?>) enumConstant).name().equalsIgnoreCase(enumName)) {
                        return enumConstant;
                    }
                }
            }
        } else if (sourceValue instanceof Number number) {
            int ordinal = number.intValue();
            if (ordinal >= 0 && ordinal < enumConstants.length) {
                return enumConstants[ordinal];
            }
        } else if (sourceValue.getClass().isEnum()) {
            try {
                return Enum.valueOf((Class<Enum>) targetEnumClass, ((Enum<?>) sourceValue).name());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private @Nullable Object handleOptionalConversion(@Nullable String propertyName, Object sourceValue) {
        Type valueType = getGenericTypeArgumentForTarget(propertyName, Optional.class, 0);
        Class<?> valueClass = resolveClass(valueType, Object.class);
        if (sourceValue instanceof Optional<?> optional) {
            return optional.map(item -> valueClass == Object.class ? item : convert(item, valueClass, null));
        }
        Object convertedValue = valueClass == Object.class ? sourceValue : convert(sourceValue, valueClass, null);
        return Optional.ofNullable(convertedValue);
    }

    private @Nullable Object handleStringSourceConversion(Class<?> targetClass, @Nullable String propertyName, String sourceValueStr) {
        if (String.class.equals(targetClass)) {
            return sourceValueStr;
        }
        if (JSONUtil.isTypeJSONObject(sourceValueStr)) {
            return handleJsonObjectStringConversion(targetClass, sourceValueStr, propertyName);
        }
        if (JSONUtil.isTypeJSONArray(sourceValueStr)) {
            return handleJsonArrayStringConversion(targetClass, sourceValueStr, propertyName);
        }
        return Convert.convertWithCheck(targetClass, sourceValueStr, null, false);
    }

    private @Nullable Object handleJsonObjectStringConversion(Class<?> targetClass, String jsonObjectStr, @Nullable String propertyName) {
        try {
            if (Map.class.isAssignableFrom(targetClass)) {
                Map<?, ?> intermediateMap = JSONUtil.toBean(jsonObjectStr, Map.class);
                return handleMapSourceConversion(targetClass, propertyName, intermediateMap);
            }
            if (isComplexBean(targetClass)) {
                return JSONUtil.toBean(jsonObjectStr, targetClass);
            }
            return Convert.convertWithCheck(targetClass, jsonObjectStr, null, false);
        } catch (Exception e) {
            LOG.warn("Convert JSON object string to {} failed: {}", targetClass.getName(), e.getMessage());
            return null;
        }
    }

    private @Nullable Object handleJsonArrayStringConversion(Class<?> targetClass, String jsonArrayStr, @Nullable String propertyName) {
        if (!Collection.class.isAssignableFrom(targetClass) && !targetClass.isArray()) {
            return null;
        }

        Type targetComponentType = targetClass.isArray()
                ? targetClass.getComponentType()
                : getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
        Class<?> targetComponentClass = resolveClass(targetComponentType, Object.class);

        try {
            List<?> parsedList = JSONUtil.parseArray(jsonArrayStr);
            return convertElementsToTarget(targetClass, targetComponentClass, parsedList);
        } catch (Exception e) {
            LOG.warn("Convert JSON array string to {} failed: {}", targetClass.getName(), e.getMessage());
            return null;
        }
    }

    private @Nullable Object handleCollectionSourceConversion(Class<?> targetClass, @Nullable String propertyName, Collection<?> sourceCollection) {
        if (!Collection.class.isAssignableFrom(targetClass) && !targetClass.isArray()) {
            return Convert.convertWithCheck(targetClass, sourceCollection, null, false);
        }
        Type targetComponentType = targetClass.isArray()
                ? targetClass.getComponentType()
                : getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
        Class<?> targetComponentClass = resolveClass(targetComponentType, Object.class);
        return convertElementsToTarget(targetClass, targetComponentClass, sourceCollection);
    }

    private @Nullable Object handleArraySourceConversion(Class<?> targetClass, @Nullable String propertyName, Object sourceArray) {
        int length = Array.getLength(sourceArray);
        List<Object> sourceList = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            sourceList.add(Array.get(sourceArray, i));
        }
        return handleCollectionSourceConversion(targetClass, propertyName, sourceList);
    }

    private @Nullable Object convertElementsToTarget(Class<?> targetClass, Class<?> targetComponentClass, Collection<?> sourceCollection) {
        if (targetClass.isArray()) {
            Object resultArray = Array.newInstance(targetComponentClass, sourceCollection.size());
            int i = 0;
            for (Object sourceItem : sourceCollection) {
                Object convertedItem = sourceItem;
                if (sourceItem == null) {
                    convertedItem = targetComponentClass.isPrimitive() ? GXCommonUtils.getClassDefaultValue(targetComponentClass) : null;
                } else if (targetComponentClass != Object.class && !isAssignableValue(targetComponentClass, sourceItem)) {
                    convertedItem = convert(sourceItem, targetComponentClass, null);
                    if (convertedItem == null && targetComponentClass.isPrimitive()) {
                        convertedItem = GXCommonUtils.getClassDefaultValue(targetComponentClass);
                    }
                }
                Array.set(resultArray, i++, convertedItem);
            }
            return resultArray;
        }

        Collection<Object> resultCollection = newTargetCollection(targetClass, sourceCollection.size());
        for (Object sourceItem : sourceCollection) {
            if (sourceItem == null || targetComponentClass == Object.class || isAssignableValue(targetComponentClass, sourceItem)) {
                resultCollection.add(sourceItem);
            } else {
                resultCollection.add(convert(sourceItem, targetComponentClass, null));
            }
        }
        return resultCollection;
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> newTargetCollection(Class<?> targetClass, int size) {
        if (targetClass.equals(List.class) || targetClass.equals(Collection.class) || targetClass.equals(ArrayList.class)) {
            return new ArrayList<>(size);
        }
        if (targetClass.equals(Set.class) || targetClass.equals(HashSet.class)) {
            return new HashSet<>(size);
        }
        if (targetClass.equals(LinkedHashSet.class)) {
            return new LinkedHashSet<>(size);
        }
        Collection<Object> result = (Collection<Object>) ReflectUtil.newInstanceIfPossible(targetClass);
        return result != null ? result : new ArrayList<>(size);
    }

    @SuppressWarnings("unchecked")
    private @Nullable Object handleMapSourceConversion(Class<?> targetClass, @Nullable String propertyName, Map<?, ?> sourceMap) {
        if (isComplexBean(targetClass)) {
            return GXHutoolDataConvert.staticConvert(targetClass, sourceMap);
        }
        if (!Map.class.isAssignableFrom(targetClass)) {
            return Convert.convertWithCheck(targetClass, sourceMap, null, false);
        }

        Type targetKeyType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
        Type targetValueType = getGenericTypeArgumentForTarget(propertyName, targetClass, 1);
        Class<?> targetKeyClass = resolveClass(targetKeyType, Object.class);
        Class<?> targetValueClass = resolveClass(targetValueType, Object.class);
        Map<Object, Object> resultMap = newTargetMap(targetClass, sourceMap.size());

        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            Object sourceKey = entry.getKey();
            Object sourceValue = entry.getValue();
            if (rejectsNullEntries(resultMap, sourceKey, sourceValue)) {
                continue;
            }

            Object convertedKey = (sourceKey == null || targetKeyClass == Object.class || isAssignableValue(targetKeyClass, sourceKey))
                    ? sourceKey
                    : convert(sourceKey, targetKeyClass, null);
            Object convertedValue = (sourceValue == null || targetValueClass == Object.class || isAssignableValue(targetValueClass, sourceValue))
                    ? sourceValue
                    : convert(sourceValue, targetValueClass, null);

            if (resultMap instanceof TreeMap && convertedKey != null && !(convertedKey instanceof Comparable<?>)) {
                convertedKey = convertedKey.toString();
            }
            if (rejectsNullEntries(resultMap, convertedKey, convertedValue)) {
                continue;
            }
            resultMap.put(convertedKey, convertedValue);
        }
        return resultMap;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> newTargetMap(Class<?> targetClass, int size) {
        if (targetClass.equals(Map.class) || targetClass.equals(HashMap.class)) {
            return new HashMap<>(size);
        }
        if (targetClass.equals(LinkedHashMap.class)) {
            return new LinkedHashMap<>(size);
        }
        if (targetClass.equals(TreeMap.class)) {
            return new TreeMap<>();
        }
        if (targetClass.equals(ConcurrentHashMap.class)) {
            return new ConcurrentHashMap<>(size);
        }
        Map<Object, Object> result = (Map<Object, Object>) ReflectUtil.newInstanceIfPossible(targetClass);
        return result != null ? result : new HashMap<>(size);
    }

    private boolean rejectsNullEntries(Map<?, ?> map, @Nullable Object key, @Nullable Object value) {
        if (map instanceof ConcurrentHashMap<?, ?> || map instanceof Hashtable<?, ?>) {
            return key == null || value == null;
        }
        if (map instanceof TreeMap<?, ?>) {
            return key == null;
        }
        return false;
    }

    private @Nullable Type getGenericTypeArgumentForTarget(@Nullable String propertyName, Class<?> targetType, int index) {
        if (propertyName == null) {
            return null;
        }
        Type genericFieldType = classMetadata.genericTypeCache().get(propertyName);
        Type cachedType = resolveParameterizedType(genericFieldType, targetType, index);
        if (cachedType != null) {
            return cachedType;
        }

        Field field = classMetadata.fieldCache().get(propertyName);
        return field == null ? null : resolveParameterizedType(field.getGenericType(), targetType, index);
    }

    private @Nullable Type resolveParameterizedType(@Nullable Type genericType, Class<?> targetType, int index) {
        if (!(genericType instanceof ParameterizedType pType)) {
            return null;
        }
        Type rawType = pType.getRawType();
        if (!(rawType instanceof Class<?> rawClass) || !targetType.isAssignableFrom(rawClass)) {
            return null;
        }
        Type[] typeArguments = pType.getActualTypeArguments();
        return index < typeArguments.length ? typeArguments[index] : null;
    }

    private Class<?> resolveClass(@Nullable Type type, Class<?> defaultClass) {
        Class<?> resolvedClass = type == null ? null : TypeUtil.getClass(type);
        return Objects.requireNonNullElse(resolvedClass, defaultClass);
    }

    private record ClassMetadata(Map<String, Field> fieldCache, Map<String, Type> genericTypeCache) {
        private static final ClassMetadata EMPTY = new ClassMetadata(Collections.emptyMap(), Collections.emptyMap());
    }

    private record BeanCopierCacheKey(Class<?> sourceClass, Class<?> targetClass) {
    }
}
