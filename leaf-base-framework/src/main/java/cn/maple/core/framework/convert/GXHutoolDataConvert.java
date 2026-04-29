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
import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.*;

public class GXHutoolDataConvert {
    private static volatile GXHutoolDataConvert INSTANCE;

    private final Logger LOG = LoggerFactory.getLogger(GXHutoolDataConvert.class);


    private GXHutoolDataConvert() {
        if (INSTANCE != null) {
            throw new IllegalStateException("已经存在GXHutoolDataConvert实例，请使用getInstance()方法获取");
        }
    }


    public static GXHutoolDataConvert getInstance() {
        if (INSTANCE == null) {
            synchronized (GXHutoolDataConvert.class) {
                if (INSTANCE == null) {
                    INSTANCE = new GXHutoolDataConvert();
                }
            }
        }
        return INSTANCE;
    }


    public static Object staticConvert(Type type, Object value) {
        return getInstance().convert(type, value);
    }


    public Object convert(Type type, Object value) {
        if (value == null) {
            return null;
        }

        try {
            Class<?> targetClazz = TypeUtil.getClass(type);
            if (targetClazz == null) {
                LOG.debug("无法确定目标类型，返回原始值");
                return value;
            }
            if (ClassUtil.isBasicType(targetClazz) && targetClazz.isInstance(value)) {
                LOG.debug("基本类型的包装类型转换: {} -> {}", value.getClass().getName(), targetClazz.getName());
                return value;
            }
            return convertBySpecializedPath(type, targetClazz, value);
        } catch (Exception e) {
            LOG.warn("类型转换异常: {} -> {}, 异常信息: {}",
                    value.getClass().getName(),
                    type != null ? type.getTypeName() : "null",
                    e.getMessage());
            if (LOG.isDebugEnabled()) {
                LOG.debug("类型转换异常详细信息", e);
            }
            return value;
        }
    }


    private Object convertBySpecializedPath(Type type, Class<?> targetClazz, Object value) {
        if (targetClazz == Object.class) {
            return value;
        }

        if (targetClazz.isEnum()) {
            LOG.debug("检测到枚举类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleEnumConversion(targetClazz, value);
        }
        if (Date.class.isAssignableFrom(targetClazz) || Calendar.class.isAssignableFrom(targetClazz)) {
            LOG.debug("检测到日期/时间类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleDateTimeConversion(targetClazz, value);
        }
        if (value instanceof CharSequence) {
            LOG.debug("检测到字符串类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleStringConversion(type, targetClazz, value.toString());
        }
        if (value instanceof IJSONTypeConverter) {
            LOG.debug("检测到JSON类型转换器: {}", value.getClass().getName());
            return ((IJSONTypeConverter) value).toBean(ObjectUtil.defaultIfNull(type, Object.class));
        }
        if (value instanceof Collection<?>) {
            LOG.debug("检测到集合类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleCollectionConversion(type, targetClazz, (Collection<?>) value);
        }
        if (value instanceof Object[]) {
            LOG.debug("检测到数组类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleArrayConversion(type, targetClazz, (Object[]) value);
        }
        if (value instanceof Map<?, ?>) {
            LOG.debug("检测到Map类型转换需求: {} -> {}", value.getClass().getName(), targetClazz.getName());
            return handleMapConversion(type, targetClazz, (Map<?, ?>) value);
        }

        LOG.debug("尝试使用通用转换工具: {} -> {}", value.getClass().getName(), targetClazz.getName());
        Object convertedValue = Convert.convertWithCheck(type, value, null, true);
        if (convertedValue != null) {
            return convertedValue;
        }

        LOG.debug("无法将类型 [{}] 转换为 [{}]，返回原始值", value.getClass().getName(), targetClazz.getName());
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object handleEnumConversion(Class<?> targetClazz, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String strValue) {
            try {
                return Enum.valueOf((Class<Enum>) targetClazz, strValue);
            } catch (IllegalArgumentException e) {
                try {
                    for (Object enumConstant : targetClazz.getEnumConstants()) {
                        if (((Enum<?>) enumConstant).name().equalsIgnoreCase(strValue)) {
                            LOG.debug("通过忽略大小写匹配枚举值: {} -> {}", strValue, enumConstant);
                            return enumConstant;
                        }
                    }
                } catch (Exception ex) {
                    LOG.warn("忽略大小写匹配枚举值失败: {}", ex.getMessage());
                }
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
        } else if (value instanceof Number) {
            try {
                Object[] enumConstants = targetClazz.getEnumConstants();
                int index = ((Number) value).intValue();
                if (index >= 0 && index < enumConstants.length) {
                    return enumConstants[index];
                } else {
                    LOG.warn("枚举序号越界: 索引 {} 超出 {} 的有效范围 [0, {}]",
                            index, targetClazz.getName(), enumConstants.length - 1);
                }
            } catch (Exception e) {
                LOG.warn("通过序号转换枚举失败: {}", e.getMessage());
            }
        } else if (value.getClass().isEnum()) {
            try {
                String enumName = ((Enum<?>) value).name();
                return Enum.valueOf((Class<Enum>) targetClazz, enumName);
            } catch (Exception e) {
                LOG.warn("枚举类型间转换失败: {} -> {}", value.getClass().getName(), targetClazz.getName());
            }
        } else {
            LOG.debug("不支持将类型 [{}] 转换为枚举类型 [{}]",
                    value.getClass().getName(), targetClazz.getName());
        }
        return null;
    }

    private Object handleDateTimeConversion(Class<?> targetClazz, Object value) {
        if (value == null) {
            return null;
        }
        try {
            switch (value) {
                case Date dateValue -> {
                    if (targetClazz == Date.class) {
                        return dateValue;
                    } else if (targetClazz == Calendar.class) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(dateValue);
                        return calendar;
                    }
                }
                case Calendar calValue -> {
                    if (targetClazz == Calendar.class) {
                        return calValue;
                    } else if (targetClazz == Date.class) {
                        return calValue.getTime();
                    }
                }
                case String strValue -> {
                    if (strValue.isEmpty()) {
                        LOG.debug("日期字符串为空，返回null");
                        return null;
                    }

                    Date date = DateUtil.parse(strValue);
                    if (date != null) {
                        if (targetClazz == Date.class) {
                            return date;
                        } else if (targetClazz == Calendar.class) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTime(date);
                            return calendar;
                        }
                    } else {
                        LOG.debug("无法解析日期字符串: {}", strValue);
                    }
                }
                case Number number -> {
                    long timestamp = number.longValue();
                    if (timestamp < 100000000000L) {
                        timestamp *= 1000;
                        LOG.debug("将秒级时间戳转换为毫秒级: {}", timestamp);
                    }
                    Date date = new Date(timestamp);
                    if (targetClazz == Date.class) {
                        return date;
                    } else if (targetClazz == Calendar.class) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(date);
                        return calendar;
                    }
                }
                default -> {
                }
            }

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


    private Object handleStringConversion(Type type, Class<?> targetClazz, String valueStr) {
        if (valueStr == null) {
            return null;
        }
        if (CharSequenceUtil.isBlank(valueStr)) {
            LOG.debug("检测到空字符串，根据目标类型返回默认值");
            if (ClassUtil.isBasicType(targetClazz)) {
                return GXCommonUtils.getClassDefaultValue(targetClazz);
            }
            return null;
        }
        if (targetClazz == String.class) {
            LOG.debug("目标类型是String，直接返回字符串");
            return valueStr;
        }
        if (JSONUtil.isTypeJSONObject(valueStr)) {
            LOG.debug("检测到JSON对象字符串，进行JSON对象转换");
            return handleJsonObjectConversion(targetClazz, valueStr);
        }
        if (JSONUtil.isTypeJSONArray(valueStr)) {
            LOG.debug("检测到JSON数组字符串，进行JSON数组转换");
            return handleJsonArrayConversion(type, targetClazz, valueStr);
        }
        LOG.debug("尝试使用通用工具转换字符串: {}", valueStr);
        Object convertedObj = GXCommonUtils.convertStrToTarget(valueStr, targetClazz);
        return convertedObj != null ? convertedObj : valueStr;
    }


    private Object handleJsonObjectConversion(Class<?> targetClazz, String valueStr) {
        try {
            if (isGXBaseDataType(targetClazz)) {
                return convertToGXBaseData(targetClazz, valueStr);
            } else if (isDictType(targetClazz)) {
                return convertToDict(valueStr);
            } else if (isJSONObjectType(targetClazz)) {
                return convertToJSONObject(valueStr);
            } else if (isMapType(targetClazz)) {
                return convertToMap(valueStr);
            } else {
                return convertToJavaBean(targetClazz, valueStr);
            }
        } catch (Exception e) {
            LOG.warn("JSON对象转换异常: {} -> {}, 值: {}", e.getClass().getName(), e.getMessage(), valueStr);
            if (LOG.isDebugEnabled()) {
                LOG.debug("JSON对象转换异常详细信息", e);
            }
            return null;
        }
    }


    private boolean isGXBaseDataType(Class<?> targetClazz) {
        return TypeToken.of(targetClazz).isSubtypeOf(GXBaseData.class);
    }


    private Object convertToGXBaseData(Class<?> targetClazz, String valueStr) {
        LOG.debug("将JSON转换为GXBaseData子类: {}", targetClazz.getName());
        return JSONUtil.toBean(valueStr, targetClazz);
    }


    private boolean isDictType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(Dict.class);
    }


    private Object convertToDict(String valueStr) {
        LOG.debug("将JSON转换为Dict类型");
        return JSONUtil.toBean(valueStr, Dict.class);
    }


    private boolean isJSONObjectType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(JSONObject.class);
    }


    private Object convertToJSONObject(String valueStr) {
        LOG.debug("将JSON转换为JSONObject类型");
        return JSONUtil.parseObj(valueStr);
    }


    private boolean isMapType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(Map.class);
    }


    private Object convertToMap(String valueStr) {
        LOG.debug("将JSON转换为Map类型");
        return JSONUtil.toBean(valueStr, Map.class);
    }


    private Object convertToJavaBean(Class<?> targetClazz, String valueStr) {
        LOG.debug("将JSON转换为JavaBean类型: {}", targetClazz.getName());
        try {
            return JSONUtil.toBean(valueStr, targetClazz);
        } catch (Exception e) {
            LOG.warn("JSON转JavaBean异常: {} -> {}", targetClazz.getName(), e.getMessage());
            try {
                ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
                if (objectMapper != null) {
                    return objectMapper.readValue(valueStr, targetClazz);
                }
            } catch (Exception ex) {
                LOG.debug("Jackson转换也失败: {}", ex.getMessage());
            }
            throw e;
        }
    }


    private Object handleJsonArrayConversion(Type type, Class<?> targetClazz, String valueStr) {
        try {
            if (isListType(targetClazz)) {
                return convertJsonArrayToList(type, valueStr);
            } else if (isSetType(targetClazz)) {
                return convertJsonArrayToSet(type, valueStr);
            } else if (isArrayType(targetClazz)) {
                return convertJsonArrayToArray(targetClazz, valueStr);
            } else if (Collection.class.isAssignableFrom(targetClazz) && !targetClazz.isInterface()) {
                try {
                    @SuppressWarnings("unchecked")
                    Collection<Object> targetCollection = (Collection<Object>) targetClazz.getDeclaredConstructor().newInstance();
                    List<?> jsonArray = JSONUtil.parseArray(valueStr);
                    Class<?> componentType = getComponentType(type, 0);
                    if (componentType != null) {
                        LOG.debug("将JSON数组转换为{}类型，元素类型为{}", targetClazz.getSimpleName(), componentType.getSimpleName());
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
            return null;
        } catch (Exception e) {
            LOG.warn("JSON数组转换异常: {} -> {}, 值: {}", e.getClass().getName(), e.getMessage(), valueStr);
            if (LOG.isDebugEnabled()) {
                LOG.debug("JSON数组转换异常详细信息", e);
            }
            return null;
        }
    }


    private boolean isListType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(List.class);
    }


    private Object convertJsonArrayToList(Type type, String valueStr) {
        Class<?> componentType = getComponentType(type, 0);
        LOG.debug("将JSON数组转换为List<{}>类型", componentType != null ? componentType.getSimpleName() : "Dict");
        return componentType != null ?
                JSONUtil.toList(valueStr, componentType) :
                JSONUtil.toList(valueStr, Dict.class);
    }


    private boolean isSetType(Class<?> targetClazz) {
        return targetClazz.isAssignableFrom(Set.class);
    }


    private Object convertJsonArrayToSet(Type type, String valueStr) {
        Class<?> componentType = getComponentType(type, 0);
        LOG.debug("将JSON数组转换为Set<{}>类型", componentType != null ? componentType.getSimpleName() : "Dict");
        List<?> list = componentType != null ?
                JSONUtil.toList(valueStr, componentType) :
                JSONUtil.toList(valueStr, Dict.class);
        return new HashSet<>(list);
    }


    private boolean isArrayType(Class<?> targetClazz) {
        return targetClazz.isArray();
    }


    private Object convertJsonArrayToArray(Class<?> targetClazz, String valueStr) {
        // 获取数组的元素类型（如User[]中的User.class）
        Class<?> componentType = targetClazz.getComponentType();
        LOG.debug("将JSON数组转换为{}[]类型", componentType.getSimpleName());
        List<?> list = JSONUtil.toList(valueStr, componentType);
        return list.toArray((Object[]) java.lang.reflect.Array.newInstance(componentType, list.size()));
    }


    private Object handleCollectionConversion(Type type, Class<?> targetClazz, Collection<?> sourceCollection) {
        if (sourceCollection == null) {
            return null;
        }
        if (isListType(targetClazz)) {
            return convertCollectionToList(type, sourceCollection);
        } else if (isSetType(targetClazz)) {
            return convertCollectionToSet(type, sourceCollection);
        } else if (isArrayType(targetClazz)) {
            return convertCollectionToArray(targetClazz, sourceCollection);
        } else if (Collection.class.isAssignableFrom(targetClazz) && !targetClazz.isInterface()) {
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
        return null;
    }


    private Object convertCollectionToList(Type type, Collection<?> sourceCollection) {
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将集合转换为List<{}>类型", componentType.getSimpleName());
            return convertCollectionWithComponentType(sourceCollection, componentType, new ArrayList<>(sourceCollection.size()));
        }
        LOG.debug("将集合直接转换为List类型（不转换元素类型）");
        return new ArrayList<>(sourceCollection);
    }


    private Object convertCollectionToSet(Type type, Collection<?> sourceCollection) {
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将集合转换为Set<{}>类型", componentType.getSimpleName());
            return convertCollectionWithComponentType(sourceCollection, componentType, new HashSet<>(sourceCollection.size()));
        }
        LOG.debug("将集合直接转换为Set类型（不转换元素类型）");
        return new HashSet<>(sourceCollection);
    }


    private Object convertCollectionToArray(Class<?> targetClazz, Collection<?> sourceCollection) {
        Class<?> componentType = targetClazz.getComponentType();
        LOG.debug("将集合转换为{}[]类型", componentType.getSimpleName());
        List<Object> resultList = new ArrayList<>(sourceCollection.size());
        for (Object item : sourceCollection) {
            Object convertedItem = convert(componentType, item);
            resultList.add(convertedItem != null ? convertedItem : item);
        }
        // 转换为特定类型的数组
        return resultList.toArray((Object[]) Array.newInstance(componentType, resultList.size()));
    }


    private Collection<Object> convertCollectionWithComponentType(Collection<?> sourceCollection, Class<?> componentType, Collection<Object> targetCollection) {
        for (Object item : sourceCollection) {
            Object convertedItem = convert(componentType, item);
            targetCollection.add(convertedItem != null ? convertedItem : item);
        }
        return targetCollection;
    }


    private Object handleArrayConversion(Type type, Class<?> targetClazz, Object[] sourceArray) {
        if (sourceArray == null) {
            return null;
        }
        if (isListType(targetClazz)) {
            return convertArrayToList(type, sourceArray);
        } else if (isSetType(targetClazz)) {
            return convertArrayToSet(type, sourceArray);
        }

        LOG.debug("不支持将数组转换为类型: {}", targetClazz.getName());
        return null;
    }


    private Object convertArrayToList(Type type, Object[] sourceArray) {
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将数组转换为List<{}>类型", componentType.getSimpleName());
            return convertArrayWithComponentType(sourceArray, componentType, new ArrayList<>(sourceArray.length));
        }
        LOG.debug("将数组直接转换为List类型（不转换元素类型）");
        return CollUtil.newArrayList(sourceArray);
    }


    private Object convertArrayToSet(Type type, Object[] sourceArray) {
        Class<?> componentType = getComponentType(type, 0);
        if (componentType != null) {
            LOG.debug("将数组转换为Set<{}>类型", componentType.getSimpleName());
            return convertArrayWithComponentType(sourceArray, componentType, new HashSet<>(sourceArray.length));
        }
        LOG.debug("将数组直接转换为Set类型（不转换元素类型）");
        return CollUtil.newHashSet(sourceArray);
    }


    private Collection<Object> convertArrayWithComponentType(Object[] sourceArray, Class<?> componentType, Collection<Object> targetCollection) {
        for (Object item : sourceArray) {
            Object convertedItem = convert(componentType, item);
            targetCollection.add(convertedItem != null ? convertedItem : item);
        }
        return targetCollection;
    }


    public <T> T convert(Class<T> tClass, Dict value) {
        if (value == null) {
            return null;
        }
        try {
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper != null) {
                return objectMapper.convertValue(value, tClass);
            }
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


    private Object handleMapConversion(Type type, Class<?> targetClazz, Map<?, ?> sourceMap) {
        if (sourceMap == null) {
            return null;
        }
        if (targetClazz.isAssignableFrom(Dict.class)) {
            LOG.debug("将Map转换为Dict类型");
            Dict dict = Dict.create();
            sourceMap.forEach((k, v) -> dict.set(k.toString(), v));
            return dict;
        }
        if (targetClazz.isAssignableFrom(JSONObject.class)) {
            LOG.debug("将Map转换为JSONObject类型");
            return JSONUtil.parseObj(sourceMap);
        }
        if (!targetClazz.isInterface() && !Map.class.isAssignableFrom(targetClazz)) {
            LOG.debug("将Map转换为JavaBean类型: {}", targetClazz.getName());
            return BeanUtil.toBean(sourceMap, targetClazz, CopyOptions.create());
        }
        if (Map.class.isAssignableFrom(targetClazz)) {
            Type keyType = TypeUtil.getTypeArgument(type, 0);
            Type valueType = TypeUtil.getTypeArgument(type, 1);
            if (keyType != null && valueType != null) {
                Class<?> keyClass = TypeUtil.getClass(keyType);
                Class<?> valueClass = TypeUtil.getClass(valueType);
                if (keyClass != null && valueClass != null) {
                    LOG.debug("将Map转换为Map<{}, {}>类型", keyClass.getSimpleName(), valueClass.getSimpleName());
                    Map<Object, Object> resultMap = new HashMap<>(sourceMap.size());
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
        return null;
    }


    private Class<?> getComponentType(Type type, int index) {
        Type actualTypeArgument = TypeUtil.getTypeArgument(type, index);
        return actualTypeArgument != null ? TypeUtil.getClass(actualTypeArgument) : null;
    }
}