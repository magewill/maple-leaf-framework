package cn.maple.core.framework.convert;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.reference.WeakKeyConcurrentMap;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.TypeUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.beans.BeanCopier;
import org.springframework.cglib.core.Converter;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class GXCGLibDataConvert implements Converter {
    private static final Logger LOG = LoggerFactory.getLogger(GXCGLibDataConvert.class);


    private static final Map<Class<?>, Boolean> PROCESSED_CLASSES_CACHE = new WeakKeyConcurrentMap<>(new ConcurrentHashMap<>(1024));


    private static final Map<Class<?>, GXCGLibDataConvert> CONVERTER_CACHE = new WeakKeyConcurrentMap<>(new ConcurrentHashMap<>(1024));


    private final Map<String, Field> fieldCache = new ConcurrentHashMap<>();


    private final Map<String, Type> genericTypeCache = new ConcurrentHashMap<>();


    private final Map<Class<?>, Map<Class<?>, BeanCopier>> beanCopierCache = new ConcurrentHashMap<>();


    private GXCGLibDataConvert(Class<?> targetClass) {
        if (targetClass == null) {
            throw new IllegalArgumentException("目标类型不能为null");
        }
        preCacheFields(targetClass);
    }

    public static GXCGLibDataConvert getConverter(Class<?> targetClass) {
        if (targetClass == null) {
            throw new GXBusinessException("目标类型不能为null");
        }
        return CONVERTER_CACHE.computeIfAbsent(targetClass, param -> new GXCGLibDataConvert(targetClass));
    }

    private BeanCopier getBeanCopier(Class<?> sourceClass, Class<?> targetClass) {
        return beanCopierCache
                .computeIfAbsent(sourceClass, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(targetClass, k -> BeanCopier.create(sourceClass, targetClass, true));
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Object convert(Object sourceValue, Class targetClass, Object context) {
        if (sourceValue == null) {
            return null;
        }

        Class<?> sourceClass = sourceValue.getClass();

        if (ClassUtil.isBasicType(targetClass) && targetClass.isInstance(sourceValue)) {
            return sourceValue;
        }

        String propertyName = (context instanceof String) ? getPropertyName((String) context) : null;

        try {
            if (targetClass.isEnum()) {
                return handleEnumConversion(targetClass, sourceValue);
            }

            if (Date.class.isAssignableFrom(targetClass) || Calendar.class.isAssignableFrom(targetClass)
                    || Temporal.class.isAssignableFrom(targetClass)) {
                return Convert.convert(targetClass, sourceValue);
            }

            if (sourceValue instanceof CharSequence) {
                return handleStringSourceConversion(targetClass, propertyName, sourceValue.toString(), context);
            }

            if (sourceValue instanceof Collection<?>) {
                return handleCollectionSourceConversion(targetClass, propertyName, (Collection<?>) sourceValue, context);
            }

            if (sourceClass.isArray()) {
                if (sourceValue instanceof Object[]) {
                    return handleArraySourceConversion(targetClass, propertyName, (Object[]) sourceValue, context);
                } else {
                    return Convert.convertWithCheck(targetClass, sourceValue, null, false);
                }
            }

            if (sourceValue instanceof Map<?, ?>) {
                return handleMapSourceConversion(targetClass, propertyName, (Map<?, ?>) sourceValue, context);
            }

            if (isComplexBean(sourceClass) && (isComplexBean(targetClass) || Map.class.isAssignableFrom(targetClass))) {
                if (Map.class.isAssignableFrom(targetClass)) {
                    return BeanUtil.toBean(sourceValue, targetClass);
                } else {
                    Object targetInstance = ReflectUtil.newInstanceIfPossible(targetClass);
                    if (targetInstance != null) {
                        BeanCopier copier = getBeanCopier(sourceClass, targetClass);
                        copier.copy(sourceValue, targetInstance, this);
                        return targetInstance;
                    } else {
                        LOG.warn("无法实例化目标Bean类: {}", targetClass.getName());
                    }
                }
            }

            Object convertedValue = Convert.convertWithCheck(targetClass, sourceValue, null, false);
            if (convertedValue != null) {
                if (!targetClass.isInstance(convertedValue) && sourceValue == convertedValue) {
                    LOG.info("Hutool Convert 未能将源类型 {} 转换为目标类型 {} (属性: {}), 返回了原始对象。",
                            sourceClass.getName(), targetClass.getName(), propertyName != null ? propertyName : "N/A");
                    return null;
                }
                return convertedValue;
            }
            return null;
        } catch (Exception e) {
            LOG.warn("转换属性 [{}] 从 {} 到 {} 时出错: {} - {}",
                    propertyName != null ? propertyName : "N/A",
                    sourceClass.getName(),
                    targetClass.getName(),
                    e.getClass().getName(), e.getMessage());
            return null;
        }
    }


    private void preCacheFields(Class<?> clazz) {
        if (clazz == null) {
            return;
        }

        if (PROCESSED_CLASSES_CACHE.containsKey(clazz)) {
            LOG.trace("类 {} 已被处理，跳过字段缓存", clazz.getName());
            return;
        }

        if (clazz.isPrimitive() ||
                clazz.isArray() ||
                clazz.isEnum() ||
                clazz.isInterface() ||
                Map.class.isAssignableFrom(clazz) ||
                Collection.class.isAssignableFrom(clazz) ||
                Number.class.isAssignableFrom(clazz) ||
                Boolean.class == clazz ||
                Character.class == clazz ||
                String.class == clazz ||
                Date.class.isAssignableFrom(clazz) ||
                Calendar.class.isAssignableFrom(clazz) ||
                Temporal.class.isAssignableFrom(clazz)) {
            PROCESSED_CLASSES_CACHE.put(clazz, Boolean.TRUE);
            return;
        }

        try {
            Field[] fields = ReflectUtil.getFields(clazz);

            int initialCapacity = Math.max(16, fields.length);
            Map<String, Field> tempFieldCache = new HashMap<>(initialCapacity);
            Map<String, Type> tempGenericCache = new HashMap<>(initialCapacity);

            for (Field field : fields) {
                String fieldName = field.getName();
                if (!fieldCache.containsKey(fieldName)) {
                    tempFieldCache.put(fieldName, field);
                    tempGenericCache.put(fieldName, field.getGenericType());
                }
            }

            if (!tempFieldCache.isEmpty()) {
                fieldCache.putAll(tempFieldCache);
                genericTypeCache.putAll(tempGenericCache);
            }

            PROCESSED_CLASSES_CACHE.put(clazz, Boolean.TRUE);
        } catch (Exception e) {
            LOG.warn("为类 {} 预缓存字段时失败: {}", clazz.getName(), e.getMessage());
            PROCESSED_CLASSES_CACHE.put(clazz, Boolean.TRUE);
        }
    }

    private String getPropertyName(String setterName) {
        if (setterName == null) {
            return "";
        }

        if (setterName.startsWith("set") && setterName.length() > 3) {
            String propertyNamePart = setterName.substring(3);

            if (propertyNamePart.length() == 1) {
                return propertyNamePart.toLowerCase();
            }

            if (Character.isUpperCase(propertyNamePart.charAt(0)) && Character.isLowerCase(propertyNamePart.charAt(1))) {
                return Character.toLowerCase(propertyNamePart.charAt(0)) + propertyNamePart.substring(1);
            }
            return propertyNamePart;
        }
        return setterName;
    }


    private boolean isComplexBean(Class<?> clazz) {
        if (clazz == null ||
                clazz.isPrimitive() ||
                clazz.isArray() ||
                clazz.isEnum() ||
                clazz.isInterface()) {
            return false;
        }

        if (Number.class.isAssignableFrom(clazz) ||
                Boolean.class.isAssignableFrom(clazz) ||
                Character.class.isAssignableFrom(clazz)) {
            return false;
        }

        if (CharSequence.class.isAssignableFrom(clazz) ||
                Date.class.isAssignableFrom(clazz) ||
                Calendar.class.isAssignableFrom(clazz) ||
                java.time.temporal.Temporal.class.isAssignableFrom(clazz)) {
            return false;
        }

        return !Map.class.isAssignableFrom(clazz) && !Collection.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object handleEnumConversion(Class<?> targetEnumClass, Object sourceValue) {
        if (sourceValue == null) {
            return null;
        }

        if (targetEnumClass.isInstance(sourceValue)) {
            return sourceValue;
        }

        try {
            Object[] enumConstants = targetEnumClass.getEnumConstants();
            if (enumConstants == null || enumConstants.length == 0) {
                return null;
            }

            if (sourceValue instanceof String) {
                String enumName = ((String) sourceValue).trim();
                if (enumName.isEmpty()) {
                    return null;
                }

                try {
                    return Enum.valueOf((Class<Enum>) targetEnumClass, enumName);
                } catch (IllegalArgumentException e) {
                    for (Object enumConstant : enumConstants) {
                        if (((Enum<?>) enumConstant).name().equalsIgnoreCase(enumName)) {
                            return enumConstant;
                        }
                    }
                }
            } else if (sourceValue instanceof Number) {
                int ordinal = ((Number) sourceValue).intValue();
                if (ordinal >= 0 && ordinal < enumConstants.length) {
                    return enumConstants[ordinal];
                }
            } else if (sourceValue.getClass().isEnum()) {
                String enumName = ((Enum<?>) sourceValue).name();
                try {
                    return Enum.valueOf((Class<Enum>) targetEnumClass, enumName);
                } catch (IllegalArgumentException ignored) {
                }
            }

            return Convert.convertWithCheck(targetEnumClass, sourceValue, null, false);
        } catch (Exception e) {
            return null;
        }
    }

    private Object handleStringSourceConversion(Class<?> targetClass, String propertyName, String sourceValueStr, Object context) {
        LOG.trace("处理字符串源值转换到{}，属性名：{}", targetClass.getName(), propertyName);
        if (String.class.equals(targetClass)) {
            LOG.debug("目标类型是String，直接返回字符串");
            return sourceValueStr;
        }
        if (JSONUtil.isTypeJSONObject(sourceValueStr)) {
            LOG.trace("字符串源值看起来是JSON对象");
            return handleJsonObjectStringConversion(targetClass, sourceValueStr, propertyName, context);
        }
        if (JSONUtil.isTypeJSONArray(sourceValueStr)) {
            LOG.trace("字符串源值看起来是JSON数组");
            return handleJsonArrayStringConversion(targetClass, sourceValueStr, propertyName, context);
        }
        LOG.trace("执行基本字符串到{}的转换", targetClass.getName());
        return Convert.convert(targetClass, sourceValueStr);
    }

    private Object handleJsonObjectStringConversion(Class<?> targetClass, String jsonObjectStr, String propertyName, Object context) {
        LOG.trace("将JSON对象字符串转换为{}", targetClass.getName());
        if (Map.class.isAssignableFrom(targetClass)) {
            Type keyType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            Type valueType = getGenericTypeArgumentForTarget(propertyName, targetClass, 1);
            LOG.trace("目标Map泛型类型: 键={}, 值={}", keyType, valueType);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> intermediateMap = JSONUtil.toBean(jsonObjectStr, Map.class);
                if (targetClass.isAssignableFrom(Map.class) && (keyType == null || keyType == String.class) && (valueType == null || valueType == Object.class)) {
                    return intermediateMap;
                } else {
                    LOG.trace("将中间Map进一步转换为目标Map类型 {}", targetClass.getName());
                    // return BeanUtil.toBean(intermediateMap, targetClass, CopyOptions.create().setConverter(GXHutoolDataConvert::staticConvert));
                    return GXHutoolDataConvert.staticConvert(targetClass, intermediateMap);
                    // return convert(intermediateMap, targetClass, context);
                }
            } catch (Exception e) {
                LOG.warn("无法将JSON对象字符串转换为Map/Bean {}: {}", targetClass.getName(), e.getMessage());
                return null;
            }
        } else if (isComplexBean(targetClass)) {
            LOG.trace("将JSON对象字符串转换为bean {}", targetClass.getName());
            try {
                return JSONUtil.toBean(jsonObjectStr, targetClass);
            } catch (Exception e) {
                LOG.warn("无法将JSON对象字符串转换为bean {}: {}", targetClass.getName(), e.getMessage());
                return null;
            }
        } else {
            LOG.trace("尝试使用Hutool Convert将JSON对象字符串转换为简单类型 {}", targetClass.getName());
            return Convert.convert(targetClass, jsonObjectStr);
        }
    }

    private Object handleJsonArrayStringConversion(Class<?> targetClass, String jsonArrayStr, String propertyName, Object context) {
        LOG.trace("将JSON数组字符串转换为{}", targetClass.getName());
        Type targetComponentType;
        if (Collection.class.isAssignableFrom(targetClass)) {
            targetComponentType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            if (targetComponentType == null) targetComponentType = Object.class;
            LOG.trace("目标集合元素类型: {}", targetComponentType);
        } else if (targetClass.isArray()) {
            targetComponentType = targetClass.getComponentType();
            LOG.trace("目标数组元素类型: {}", targetComponentType);
        } else {
            LOG.warn("无法将JSON数组字符串转换为非集合/非数组类型: {}", targetClass.getName());
            return null;
        }
        try {
            List<?> parsedList = JSONUtil.toList(jsonArrayStr, TypeUtil.getClass(targetComponentType));
            Collection<Object> resultCollection = null;
            if (List.class.isAssignableFrom(targetClass) || targetClass.equals(Collection.class)) {
                resultCollection = new ArrayList<>(parsedList.size());
            } else if (Set.class.isAssignableFrom(targetClass)) {
                resultCollection = new LinkedHashSet<>(parsedList.size()); // 可能需要保持顺序
            } else if (targetClass.isArray()) {
            } else {
                LOG.warn("不支持的目标集合类型，无法进行JSON数组转换: {}", targetClass.getName());
                return null;
            }
            List<Object> convertedList = new ArrayList<>(parsedList.size());
            for (Object item : parsedList) {
                Object convertedItem = convert(item, TypeUtil.getClass(targetComponentType), null); // 数组元素没有特定上下文
                convertedList.add(convertedItem);
                if (resultCollection != null) {
                    resultCollection.add(convertedItem);
                }
            }
            if (targetClass.isArray()) {
                LOG.trace("将转换后的列表转换为类型{}的数组", targetComponentType);
                Object resultArray = Array.newInstance(TypeUtil.getClass(targetComponentType), convertedList.size());
                for (int i = 0; i < convertedList.size(); ++i) {
                    Array.set(resultArray, i, convertedList.get(i));
                }
                return resultArray;
            } else {
                return resultCollection;
            }
        } catch (Exception e) {
            LOG.warn("无法将JSON数组字符串转换为{}: {}", targetClass.getName(), e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object handleCollectionSourceConversion(Class<?> targetClass, String propertyName, Collection<?> sourceCollection, Object context) {
        LOG.trace("Handling Collection source (size {}) conversion to {} for property {}", sourceCollection.size(), targetClass.getName(), propertyName);
        Type targetComponentType;
        if (Collection.class.isAssignableFrom(targetClass)) {
            targetComponentType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            if (targetComponentType == null) targetComponentType = Object.class;
            LOG.trace("目标集合组件类型: {}", targetComponentType);
        } else if (targetClass.isArray()) {
            targetComponentType = targetClass.getComponentType();
            LOG.trace("目标数组组件类型: {}", targetComponentType);
        } else {
            LOG.trace("目标既不是集合也不是数组，回退到Hutool Convert处理集合源值");
            return Convert.convertWithCheck(targetClass, sourceCollection, null, false);
        }
        Class<?> targetComponentClass = TypeUtil.getClass(targetComponentType);
        if (targetComponentClass == null) {
            LOG.warn("无法确定属性{}的目标组件类类型", propertyName);
            targetComponentClass = Object.class;
        }
        Collection<Object> resultCollection = null;
        if (targetClass.equals(List.class) || targetClass.equals(Collection.class) || targetClass.equals(ArrayList.class)) {
            resultCollection = new ArrayList<>(sourceCollection.size());
        } else if (targetClass.equals(Set.class) || targetClass.equals(HashSet.class)) {
            resultCollection = new HashSet<>(sourceCollection.size());
        } else if (targetClass.equals(LinkedHashSet.class)) {
            resultCollection = new LinkedHashSet<>(sourceCollection.size());
        } else if (!targetClass.isArray()) {
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
        int index = 0;
        for (Object sourceItem : sourceCollection) {
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
        if (targetClass.isArray()) {
            LOG.trace("将临时列表转换为类型为 {} 的数组", targetComponentClass.getName());
            assert tempListForArray != null;
            Object resultArray = Array.newInstance(targetComponentClass, tempListForArray.size());
            for (int i = 0; i < tempListForArray.size(); i++) {
                Object itemToSet = tempListForArray.get(i);
                if (itemToSet == null && targetComponentClass.isPrimitive()) {
                    LOG.trace("将null设置为原始类型数组 {} 的默认值", targetComponentClass.getName());
                    itemToSet = GXCommonUtils.getClassDefaultValue(targetComponentClass);
                }
                try {
                    Array.set(resultArray, i, itemToSet);
                } catch (IllegalArgumentException e) {
                    LOG.warn("设置数组元素 {} 在索引 {} 时类型不匹配: 期望 {}, 得到 {}. 值: {}",
                            targetComponentClass.getName(), i, targetComponentClass,
                            itemToSet != null ? itemToSet.getClass().getName() : "null", itemToSet);
                    Array.set(resultArray, i, GXCommonUtils.getClassDefaultValue(targetComponentClass)); // 设置 0/false/null
                }
            }
            return resultArray;
        } else {
            return resultCollection;
        }
    }


    private Object handleArraySourceConversion(Class<?> targetClass, String propertyName, Object[] sourceArray, Object context) {
        LOG.trace("处理数组源值(长度 {})转换到 {} 属性 {}", sourceArray.length, targetClass.getName(), propertyName);
        return handleCollectionSourceConversion(targetClass, propertyName, Arrays.asList(sourceArray), context);
    }

    @SuppressWarnings("unchecked")
    private Object handleMapSourceConversion(Class<?> targetClass, String propertyName, Map<?, ?> sourceMap, Object context) {
        LOG.trace("处理Map源值(大小 {})转换到 {} 属性 {}", sourceMap.size(), targetClass.getName(), propertyName);
        if (isComplexBean(targetClass)) {
            LOG.trace("将Map转换为Bean: {}", targetClass.getName());
            return GXHutoolDataConvert.staticConvert(targetClass, sourceMap);
        }
        if (Map.class.isAssignableFrom(targetClass)) {
            Type targetKeyType = getGenericTypeArgumentForTarget(propertyName, targetClass, 0);
            Type targetValueType = getGenericTypeArgumentForTarget(propertyName, targetClass, 1);
            Class<?> targetKeyClass = TypeUtil.getClass(targetKeyType);
            Class<?> targetValueClass = TypeUtil.getClass(targetValueType);
            if (targetKeyClass == null) targetKeyClass = Object.class;
            if (targetValueClass == null) targetValueClass = Object.class;
            LOG.trace("目标Map类型: 键={}, 值={}", targetKeyClass.getName(), targetValueClass.getName());
            Map<Object, Object> resultMap;
            if (targetClass.equals(Map.class) || targetClass.equals(HashMap.class)) {
                resultMap = new HashMap<>(sourceMap.size());
            } else if (targetClass.equals(LinkedHashMap.class)) {
                resultMap = new LinkedHashMap<>(sourceMap.size());
            } else if (targetClass.equals(TreeMap.class)) {
                resultMap = new TreeMap<>();
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
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                Object sourceKey = entry.getKey();
                Object sourceValue = entry.getValue();
                LOG.trace("转换Map键从 {} 到 {}", sourceKey != null ? sourceKey.getClass().getName() : "null", targetKeyClass.getName());
                Object convertedKey = convert(sourceKey, targetKeyClass, null);
                LOG.trace("转换Map值从 {} 到 {}", sourceValue != null ? sourceValue.getClass().getName() : "null", targetValueClass.getName());
                Object convertedValue = convert(sourceValue, targetValueClass, null);
                if (resultMap instanceof TreeMap && !(convertedKey instanceof Comparable)) {
                    LOG.warn("转换后的键类型 {} 不是Comparable，这可能导致TreeMap出现问题，键: {}",
                            convertedKey != null ? convertedKey.getClass().getName() : "null", convertedKey);
                }
                resultMap.put(convertedKey, convertedValue);
            }
            return resultMap;
        }
        LOG.trace("目标既不是Bean也不是Map，回退到Hutool Convert处理Map源值");
        return Convert.convertWithCheck(targetClass, sourceMap, null, false);
    }

    private Type getGenericTypeArgumentForTarget(String propertyName, Class<?> targetType, int index) {
        if (propertyName == null) {
            LOG.trace("无法在没有属性名的情况下解析目标{}的泛型类型", targetType.getName());
            return null;
        }
        Type genericFieldType = genericTypeCache.get(propertyName);
        LOG.trace("属性'{}'的泛型类型缓存查找结果: {}", propertyName, genericFieldType);
        if (genericFieldType instanceof ParameterizedType pType) {
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
        LOG.debug("无法解析目标类型{}上属性'{}'在索引{}处的泛型类型参数。返回null。", targetType.getName(), propertyName, index);
        return null;
    }
}