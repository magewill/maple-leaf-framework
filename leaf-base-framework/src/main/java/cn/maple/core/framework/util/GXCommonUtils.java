package cn.maple.core.framework.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.codec.Base64Encoder;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.exceptions.UtilException;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.*;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.api.dto.req.GXUpdateFieldRequest;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import cn.maple.core.framework.convert.GXCGLibDataConvert;
import cn.maple.core.framework.convert.GXHutoolDataConvert;
import cn.maple.core.framework.dto.GXBaseData;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.exception.GXBeanValidateException;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXConvertException;
import cn.maple.core.framework.util.cglib.GXCglibUtils;
import com.google.common.collect.Table;
import com.google.common.reflect.TypeToken;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GXCommonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXCommonUtils.class);

    private static final String MAP_STR_FORMAT_REGULAR = "\\{(.+?)=(.+?)(, (.+?)=(.+?))*\\}";

    private static final String BASE64_FORMAT_REGULAR = "^[A-Za-z0-9+/]*={0,2}$";

    @Getter
    private static final CopyOptions defaultCopyOptions = CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true).setConverter(GXHutoolDataConvert::staticConvert);

    @Getter
    private static final Map<GXMethodCacheKeyUtils.MethodCacheKey, Method> METHOD_CACHE = new ConcurrentHashMap<>(64);

    private static final ConcurrentHashMap<String, Class<?>> UPDATE_FIELD_CLASS_CACHE = new ConcurrentHashMap<>(8);

    private static final int MAX_METHOD_CACHE_SIZE = 4096;

    private static final AtomicBoolean METHOD_CACHE_CLEANING = new AtomicBoolean(false);

    private static final Class<?>[] EMPTY_PARAM_TYPES = new Class<?>[0];

    private static final Class<?> NULL_PARAM_TYPE = NullParam.class;

    private static final Method METHOD_NOT_FOUND = initMethodNotFound();

    private GXCommonUtils() {
        throw new AssertionError("GXCommonUtils must not be instantiated");
    }

    private static Method initMethodNotFound() {
        try {
            return GXCommonUtils.class.getDeclaredMethod("__methodNotFound");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private static void __methodNotFound() {
    }

    public static <R> R getEnvironmentValue(String key, Class<R> clazzType) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("Configuration key must not be blank");
        }
        if (clazzType == null) {
            throw new IllegalArgumentException("Return type must not be null");
        }

        try {
            Environment environment = GXSpringContextUtils.getEnvironment();
            if (ObjectUtil.isNull(environment)) {
                LOG.debug("Environment is unavailable, cannot read property");
                return getClassDefaultValue(clazzType);
            }
            R envValue = null;
            try {
                envValue = environment.getProperty(key, clazzType);
            } catch (Exception e) {
                LOG.debug("Direct property conversion failed: key={}, type={}, error={}", key, clazzType.getName(), e.getMessage());
            }
            if (null != envValue) {
                return envValue;
            }
            if (ClassUtil.isSimpleValueType(clazzType)) {
                return getClassDefaultValue(clazzType);
            }

            String rawValue = environment.getProperty(key, String.class);
            if (rawValue == null) {
                return getClassDefaultValue(clazzType);
            }

            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                LOG.debug("ObjectMapper bean is unavailable, cannot convert property: key={}, type={}", key, clazzType.getName());
                return getClassDefaultValue(clazzType);
            }

            return objectMapper.readValue(rawValue, clazzType);
        } catch (JacksonException exception) {
            LOG.error("Failed to convert property: key={}, type={}, error={}", key, clazzType.getName(), exception.getMessage());
            throw new GXConvertException("Property conversion failed", exception);
        } catch (Exception e) {
            LOG.error("Failed to get property: key={}, error={}", key, e.getMessage());
            return getClassDefaultValue(clazzType);
        }
    }

    public static <R> R getEnvironmentValue(String key, Class<R> clazzType, R defaultValue) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("Configuration key must not be blank");
        }
        if (clazzType == null) {
            throw new IllegalArgumentException("Return type must not be null");
        }

        try {
            Environment environment = GXSpringContextUtils.getEnvironment();
            if (ObjectUtil.isNull(environment)) {
                return defaultValue;
            }

            R envValue = null;
            try {
                envValue = environment.getProperty(key, clazzType);
            } catch (Exception e) {
                LOG.debug("Direct property conversion failed: key={}, type={}, error={}", key, clazzType.getName(), e.getMessage());
            }
            if (null != envValue) {
                return envValue;
            }
            if (ClassUtil.isSimpleValueType(clazzType)) {
                return defaultValue;
            }

            String rawValue = environment.getProperty(key, String.class);
            if (rawValue == null) {
                return defaultValue;
            }

            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                LOG.debug("ObjectMapper bean is unavailable, use default property value: key={}, type={}", key, clazzType.getName());
                return defaultValue;
            }

            return objectMapper.readValue(rawValue, clazzType);
        } catch (Exception e) {
            LOG.debug("Failed to get property, use default value: key={}, error={}", key, e.getMessage());
            return defaultValue;
        }
    }

    public static String getActiveProfile() {
        try {
            Environment environment = GXSpringContextUtils.getEnvironment();
            if (ObjectUtil.isNull(environment)) {
                return "default";
            }
            String[] activeProfiles = environment.getActiveProfiles();
            if (activeProfiles.length > 0) {
                return activeProfiles[0];
            }
            String[] defaultProfiles = environment.getDefaultProfiles();
            if (defaultProfiles.length > 0) {
                return defaultProfiles[0];
            }
            return "default";
        } catch (Exception e) {
            LOG.debug("Failed to get active profile: {}", e.getMessage());
            return "default";
        }
    }

    public static <R> @Nullable R getClassDefaultValue(Class<R> clazzType) {
        if (clazzType == null) {
            throw new IllegalArgumentException("Class must not be null");
        }
        try {
            if (ClassUtil.isBasicType(clazzType) && !ClassUtil.isPrimitiveWrapper(clazzType)) {
                return Convert.convert(clazzType, ClassUtil.getDefaultValue(clazzType));
            }
            return ReflectUtil.newInstanceIfPossible(clazzType);
        } catch (Exception e) {
            LOG.debug("Failed to create default value for type: type={}, error={}", clazzType.getName(), e.getMessage());
            return null;
        }
    }

    public static String hiddenPhoneNumber(CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar) {
        if (CharSequenceUtil.isBlank(phoneNumber)) {
            return "";
        }

        if (startInclude < 0 || endExclude > phoneNumber.length() || startInclude >= endExclude) {
            LOG.debug("Invalid phone mask arguments: startInclude={}, endExclude={}, phoneLength={}",
                    startInclude, endExclude, phoneNumber.length());
            return phoneNumber.toString();
        }

        if (Validator.isMobile(phoneNumber)) {
            return CharSequenceUtil.replaceByCodePoint(phoneNumber, startInclude, endExclude, replacedChar);
        }
        return "";
    }

    public static String encryptedData(Dict data, String key, int expiry) {
        if (Objects.isNull(data) || data.isEmpty()) {
            throw new GXBusinessException("Plain data must not be empty");
        }
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("Encrypt key must not be empty");
        }

        int safeExpiry = Math.max(0, expiry);

        String combinedKey = CharSequenceUtil.format("{}{}", key, GXCommonConstant.COMMON_ENCRYPT_KEY);

        String jsonData = JSONUtil.toJsonStr(data);
        return GXAuthCodeUtils.authCodeEncode(jsonData, combinedKey, safeExpiry);
    }

    public static Dict decryptedData(String encryptedStr, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("Decrypt key must not be empty");
        }
        if (CharSequenceUtil.isEmpty(encryptedStr)) {
            LOG.debug("Encrypted string is empty");
            return Dict.create();
        }

        try {
            String combinedKey = CharSequenceUtil.format("{}{}", key, GXCommonConstant.COMMON_ENCRYPT_KEY);

            final String decryptedStr = GXAuthCodeUtils.authCodeDecode(encryptedStr, combinedKey);

            if (CharSequenceUtil.isEmpty(decryptedStr) || "{}".equals(decryptedStr)) {
                return Dict.create();
            }

            return JSONUtil.toBean(decryptedStr, Dict.class);
        } catch (Exception e) {
            LOG.error("Failed to decrypt data: {}", e.getMessage());
            return Dict.create();
        }
    }

    @SuppressWarnings("unchecked")
    public static <S, T> @Nullable T convertSourceToTarget(@Nullable S source, Class<T> tClass, @Nullable String methodName, @Nullable CopyOptions copyOptions, @Nullable Object extraData) {
        if (Objects.isNull(source)) {
            return null;
        }

        if (tClass == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }

        if (ClassUtil.isSimpleTypeOrArray(tClass)) {
            try {
                return tClass.isInstance(source) ? tClass.cast(source) : Convert.convert(tClass, source);
            } catch (Exception e) {
                throw new GXConvertException("Convert failed", e);
            }
        }

        try {
            if (CharSequenceUtil.isBlank(methodName)) {
                methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
            }
            if (Objects.isNull(extraData)) {
                extraData = Dict.create();
            }

            T target = ReflectUtil.newInstanceIfPossible(tClass);
            if (target == null) {
                throw new GXConvertException("Cannot create target type instance: " + tClass.getName());
            }

            if (!TypeToken.of(target.getClass()).isSubtypeOf(Map.class) && TypeToken.of(source.getClass()).isSubtypeOf(GXBaseData.class) && ObjectUtil.isNull(copyOptions)) {
                LOG.debug("Copy properties with CGLIB");
                //GXCglibUtils.copy(source, target, new GXCGLibDataConvert(tClass));
                GXCglibUtils.copy(source, target, GXCGLibDataConvert.getConverter(tClass));
            } else {
                copyOptions = ObjectUtil.defaultIfNull(copyOptions, GXCommonUtils::getDefaultCopyOptions);
                LOG.debug("Copy properties with BeanUtil");
                BeanUtil.copyProperties(source, target, copyOptions);
            }

            if (CharSequenceUtil.isNotEmpty(methodName)) {
                reflectCallObjectMethod(target, methodName, extraData);
            }

            reflectCallObjectMethod(target, "verify");

            return target;
        } catch (Exception e) {
            LOG.error("Object conversion failed: sourceType={}, targetType={}, error={}",
                    source.getClass().getName(), tClass.getName(), e.getMessage());

            Throwable rootCause = e;
            if (ObjectUtil.isNotNull(e.getCause())) {
                rootCause = e.getCause();
                if (ObjectUtil.isNotNull(rootCause.getCause())) {
                    rootCause = rootCause.getCause();
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Object conversion failure details:", e);
            }
            String errorMessage = CharSequenceUtil.format("Object conversion failed: sourceType={}, targetType={}, error={}",
                    source.getClass().getSimpleName(),
                    tClass.getSimpleName(),
                    rootCause.getMessage());
            throw new GXConvertException(errorMessage, rootCause);
        }
    }

    public static <S, T> @Nullable T convertSourceToTarget(@Nullable S source, Class<T> tClass, @Nullable String methodName, @Nullable CopyOptions copyOptions) {
        return convertSourceToTarget(source, tClass, methodName, copyOptions, Dict.create());
    }

    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass) {
        return convertSourceListToTargetList(collection, tClass, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, null);
    }

    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, @Nullable String methodName, @Nullable CopyOptions copyOptions) {
        return convertSourceListToTargetList(collection, tClass, methodName, copyOptions, Dict.create());
    }

    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, @Nullable String methodName, @Nullable CopyOptions copyOptions, @Nullable Object extraData) {
        if (CollUtil.isEmpty(collection)) {
            return Collections.emptyList();
        }
        if (tClass == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }

        return collection.stream()
                .map(source -> convertSourceToTarget(source, tClass, methodName, copyOptions, extraData))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static @Nullable Object reflectCallObjectMethod(Class<?> serviceClass, String methodName, Object... params) {
        if (serviceClass == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        Object target = GXSpringContextUtils.getBean(serviceClass);
        if (target == null) {
            LOG.debug("Spring bean not found: type={}", serviceClass.getName());
            return null;
        }
        return reflectCallObjectMethod(target, methodName, params);
    }

    public static @Nullable Object reflectCallObjectMethod(@Nullable Object object, @Nullable String methodName, Object... params) {
        if (Objects.isNull(object)) {
            LOG.debug("Reflection target object is null");
            return null;
        }

        if (CharSequenceUtil.isEmpty(methodName)) {
            methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
            LOG.debug("Method name is empty, using default method: {}", methodName);
        }

        if (Objects.isNull(params)) {
            params = new Object[0];
        }

        try {
            Class<?>[] paramTypes = resolveParamTypes(params);

            Method method = findMethod(object.getClass(), methodName, paramTypes);

            if (method == METHOD_NOT_FOUND) {
                LOG.debug("Method not found, reflection call skipped: target={}.{}, params={}",
                        object.getClass().getSimpleName(), methodName, Arrays.toString(params));
                return null;
            }

            if (!method.canAccess(object)) {
                method.setAccessible(true);
            }

            return method.invoke(object, params);
        } catch (Exception ex) {
            return handleReflectionException(ex, object, methodName, params);
        }
    }

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        Objects.requireNonNull(clazz, "Target class must not be null");
        Objects.requireNonNull(methodName, "Method name must not be null");
        Objects.requireNonNull(paramTypes, "Parameter type array must not be null");

        final GXMethodCacheKeyUtils.MethodCacheKey methodCacheKey =
                GXMethodCacheKeyUtils.getMethodCacheKey(clazz, methodName, paramTypes);

        Method cachedMethod = METHOD_CACHE.get(methodCacheKey);
        if (cachedMethod != null) {
            return cachedMethod;
        }

        clearMethodCacheIfNeeded();
        Method method = METHOD_CACHE.computeIfAbsent(methodCacheKey, key -> {
            Method resolvedMethod = lookupMethod(clazz, methodName, paramTypes);
            return resolvedMethod == null ? METHOD_NOT_FOUND : resolvedMethod;
        });
        return method;
    }

    private static void clearMethodCacheIfNeeded() {
        if (METHOD_CACHE.size() > MAX_METHOD_CACHE_SIZE && METHOD_CACHE_CLEANING.compareAndSet(false, true)) {
            try {
                if (METHOD_CACHE.size() > MAX_METHOD_CACHE_SIZE) {
                    METHOD_CACHE.clear();
                    LOG.debug("Reflection method cache cleared: maxSize={}", MAX_METHOD_CACHE_SIZE);
                }
            } finally {
                METHOD_CACHE_CLEANING.set(false);
            }
        }
    }

    private static Class<?>[] resolveParamTypes(Object[] params) {
        if (params.length == 0) {
            return EMPTY_PARAM_TYPES;
        }
        Class<?>[] paramTypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            paramTypes[i] = params[i] == null ? NULL_PARAM_TYPE : params[i].getClass();
        }
        return paramTypes;
    }

    private static @Nullable Method lookupMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        Method method = ReflectUtil.getMethod(clazz, methodName, paramTypes);
        if (method != null) {
            return method;
        }

        Method bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        for (Method candidate : clazz.getMethods()) {
            int score = getMethodMatchScore(candidate, methodName, paramTypes);
            if (score < bestScore) {
                bestMatch = candidate;
                bestScore = score;
            }
        }
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                int score = getMethodMatchScore(candidate, methodName, paramTypes);
                if (score < bestScore) {
                    bestMatch = candidate;
                    bestScore = score;
                }
            }
        }
        return bestMatch;
    }

    private static int getMethodMatchScore(Method method, String methodName, Class<?>[] paramTypes) {
        if (!method.getName().equals(methodName) || method.getParameterCount() != paramTypes.length) {
            return Integer.MAX_VALUE;
        }
        int score = 0;
        Class<?>[] methodParamTypes = method.getParameterTypes();
        for (int i = 0; i < methodParamTypes.length; i++) {
            int paramScore = getParamMatchScore(methodParamTypes[i], paramTypes[i]);
            if (paramScore == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            score += paramScore;
        }
        return score;
    }

    private static int getParamMatchScore(Class<?> declaredType, @Nullable Class<?> actualType) {
        if (actualType == NULL_PARAM_TYPE) {
            return declaredType.isPrimitive() ? Integer.MAX_VALUE : 16;
        }
        Class<?> wrappedDeclaredType = wrapPrimitiveType(declaredType);
        Class<?> wrappedActualType = wrapPrimitiveType(actualType);
        if (wrappedDeclaredType.equals(wrappedActualType)) {
            return 0;
        }
        return wrappedDeclaredType.isAssignableFrom(wrappedActualType) ? 8 : Integer.MAX_VALUE;
    }

    private static Class<?> wrapPrimitiveType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        if (type == void.class) {
            return Void.class;
        }
        return type;
    }

    private static Object handleReflectionException(Exception e, Object object, String methodName, Object[] params) {
        switch (e) {
            case InvocationTargetException ite -> {
                throw unwrapInvocationTargetException(ite, object, methodName, params);
            }
            case UtilException utilException -> {
                Throwable cause = utilException.getCause();
                if (cause instanceof InvocationTargetException ite) {
                    throw unwrapInvocationTargetException(ite, object, methodName, params);
                }
                LOG.error("Unexpected reflection utility exception: {}", utilException.getMessage());
                throw utilException;
            }
            case SecurityException se -> {
                LOG.error("Reflection security exception: {}", se.getMessage());
                throw new GXBusinessException("Reflection security check failed: " + se.getMessage(), se);
            }
            case IllegalArgumentException iae -> {
                LOG.error("Reflection argument mismatch: {}", iae.getMessage());
                throw new GXBusinessException("Reflection argument mismatch: " + iae.getMessage(), iae);
            }
            default -> {
                LOG.error("Reflection call failed: {}", e.getMessage());
                throw new GXBusinessException("Reflection call failed: " + e.getMessage(), e);
            }
        }
    }

    private static RuntimeException unwrapInvocationTargetException(InvocationTargetException exception, Object object, String methodName, Object[] params) {
        Throwable targetException = exception.getTargetException();
        if (targetException instanceof GXBeanValidateException beanValidateException) {
            return beanValidateException;
        }
        String exceptionMessage = CharSequenceUtil.isEmpty(targetException.getMessage())
                ? "Reflection invocation failed" : targetException.getMessage();
        LOG.error("Reflection invocation failed: target={}.{}, params={}, errorMessage={}, error={}",
                object.getClass().getSimpleName(), methodName, Arrays.toString(params),
                exceptionMessage, targetException);
        return new GXBusinessException(exceptionMessage, Optional.ofNullable(targetException.getCause()).orElse(targetException));
    }

    public static boolean checkPhone(String phone) {
        if (CharSequenceUtil.isEmpty(phone)) {
            return true;
        }
        final String regex = "^((13[0-9])|(14[5,7,9])|(15[0-3,5-9])|(16[6,8])|(17[0,3,5-8])|(18[0-9])|(19[1,8,9]))\\d{8}$";
        return !ReUtil.isMatch(regex, phone);
    }

    public static boolean checkTelephone(String telephone) {
        if (CharSequenceUtil.isEmpty(telephone)) {
            return true;
        }
        String regex = "^(0\\d{2}-\\d{8}(-\\d{1,4})?)|(0\\d{3}-\\d{7,8}(-\\d{1,4})?)$";
        return !ReUtil.isMatch(regex, telephone);
    }

    public static <R> List<R> buildTree(List<R> sourceList, Object rootParentValue) {
        return buildTree(sourceList, rootParentValue, null);
    }

    public static <R> List<R> buildTree(List<R> sourceList, Object rootParentValue, String getParentMethodName) {
        if (CollUtil.isEmpty(sourceList)) {
            return CollUtil.newArrayList();
        }

        String[] methodNames = new String[]{"getParentId"};
        if (CharSequenceUtil.isNotBlank(getParentMethodName)) {
            methodNames[0] = getParentMethodName;
        }

        List<R> roots = sourceList.stream().filter(obj -> {
            if (Objects.isNull(obj)) {
                return false;
            }
            Object parentId = GXCommonUtils.reflectCallObjectMethod(obj, methodNames[0]);
            return Objects.equals(parentId, rootParentValue);
        }).collect(Collectors.toList());

        List<R> subs = sourceList.stream().filter(obj -> {
            if (Objects.isNull(obj)) {
                return false;
            }
            Object parentId = GXCommonUtils.reflectCallObjectMethod(obj, methodNames[0]);
            return !Objects.equals(parentId, rootParentValue);
        }).collect(Collectors.toList());
        if (!CollUtil.isEmpty(roots)) {
            roots.forEach(root -> buildSubs(root, subs, methodNames[0]));
        }

        return roots;
    }

    private static <R> void buildSubs(R parent, List<R> subs, String getParentMethodName) {
        if (CollUtil.isEmpty(subs) || Objects.isNull(parent)) {
            return;
        }

        List<R> children = subs.stream().filter(sub -> {
            if (Objects.isNull(sub)) {
                return false;
            }
            Object parentId = GXCommonUtils.reflectCallObjectMethod(sub, getParentMethodName);
            Object id = GXCommonUtils.reflectCallObjectMethod(parent, "getId");
            return Objects.equals(parentId, id);
        }).collect(Collectors.toList());

        if (!CollUtil.isEmpty(children)) {
            GXCommonUtils.reflectCallObjectMethod(parent, "setChildren", children);
            children.forEach(child -> buildSubs(child, subs, getParentMethodName));
        }
    }

    public static <R> @Nullable R decodeConnectStr(String connectEncodeStr, Class<R> targetClazz) {
        if (targetClazz == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }

        if (CharSequenceUtil.isEmpty(connectEncodeStr)) {
            return null;
        }

        String secretKey = System.getProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY);

        if (CharSequenceUtil.isEmpty(secretKey)) {
            secretKey = System.getenv(GXCommonConstant.DATA_SOURCE_SECRET_KEY_ENV);
        }

        if (CharSequenceUtil.isEmpty(secretKey)) {
            GXLoggerUtils.logDebug(LOG, "Datasource secret key is empty, skip decrypting connection string");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        if (!isBase64(connectEncodeStr)) {
            GXLoggerUtils.logDebug(LOG, "Connection string is not valid Base64, use raw value");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        String decodedStr = GXAuthCodeUtils.authCodeDecode(connectEncodeStr, secretKey);

        if (CharSequenceUtil.equalsIgnoreCase(decodedStr, "{}")) {
            GXLoggerUtils.logDebug(LOG, "Failed to decode connection string, use raw value");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        decodedStr = HtmlUtil.unescape(decodedStr);

        return Convert.convert(targetClazz, decodedStr);
    }

    @SuppressWarnings("all")
    public static <R> Class<R> getGenericClassType(Class<?> clazz, Integer index) {
        if (clazz == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (index == null) {
            throw new IllegalArgumentException("Generic type index must not be null");
        }
        return (Class<R>) ClassUtil.getTypeArgument(clazz, index);
    }

    public static List<GXCondition<?>> convertTableConditionToConditionExp(String tableNameAlias, Table<String, String, Object> condition) {
        if (condition == null) {
            return new ArrayList<>();
        }

        List<GXCondition<?>> conditions = new ArrayList<>();

        condition.rowMap().forEach((column, datum) -> datum.forEach((op, value) -> {
            Dict data = Dict.create()
                    .set("tableNameAlias", tableNameAlias)
                    .set("fieldName", column);
            if (value instanceof List<?>) {
                var valueSet = new HashSet<>(Convert.toList(value));
                data.set("value", valueSet);
            } else {
                data.set("value", value);
            }

            Function<Dict, GXCondition<?>> function = GXDataSourceConstant.getFunction(op);
            if (Objects.isNull(function)) {
                throw new GXBusinessException(CharSequenceUtil.format("Condition converter is missing: op={}", op));
            }

            conditions.add(function.apply(data));
        }));

        return conditions;
    }

    public static <T> @Nullable T convertStrToTarget(String str, Class<T> targetClazz) {
        if (CharSequenceUtil.isEmpty(str)) {
            return null;
        }
        if (targetClazz == null) {
            LOG.debug("Target type is null, skip string conversion");
            return null;
        }
        if (ReUtil.isMatch(MAP_STR_FORMAT_REGULAR, str)) {
            try {
                Map<String, Object> tmpMap = new HashMap<>();
                String content = str.replace("{", "").replace("}", "");
                if (CharSequenceUtil.isNotEmpty(content)) {
                    Arrays.stream(content.split(","))
                            .map(String::trim)
                            .map(arrayData -> arrayData.split("=", 2))
                            .filter(array -> array.length == 2)
                            .forEach(array -> tmpMap.put(array[0].trim(), array[1].trim()));
                }
                return Convert.convert(targetClazz, tmpMap);
            } catch (Exception ex) {
                LOG.error("Failed to convert map-style string: rawValue={}, error={}", str, ex.getMessage());
                return null;
            }
        }

        if (JSONUtil.isTypeJSON(str)) {
            try {
                return JSONUtil.toBean(str, targetClazz);
            } catch (ConvertException ex) {
                LOG.error("Failed to convert JSON string: rawValue={}, error={}", str, ex.getMessage());
                return null;
            } catch (Exception ex) {
                LOG.error("Unexpected JSON conversion failure: rawValue={}, error={}", str, ex.getMessage());
                return null;
            }
        }

        try {
            return Convert.convert(targetClazz, str);
        } catch (Exception ex) {
            LOG.debug("Direct string conversion failed, rawValue={}", str);
            return null;
        }
    }

    public static boolean isBase64(String base64Str) {
        if (CharSequenceUtil.isEmpty(base64Str)) {
            return false;
        }
        String value = base64Str.trim();
        return ReUtil.isMatch(BASE64_FORMAT_REGULAR, value)
                && Base64.isBase64(value);
    }

    public static boolean checkMethodExists(Class<?> targetClazz, String methodName, Object... params) {
        if (targetClazz == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (CharSequenceUtil.isEmpty(methodName)) {
            throw new IllegalArgumentException("Method name must not be null or empty");
        }
        if (params == null) {
            params = new Object[0];
        }
        Method method = findMethod(targetClazz, methodName, resolveParamTypes(params));
        return method != METHOD_NOT_FOUND;
    }

    public static Integer checkURLReachable(String urlString) {
        if (CharSequenceUtil.isEmpty(urlString)) {
            LOG.debug("URL must not be empty");
            return -1;
        }

        try {
            HttpRequest request = HttpUtil.createRequest(cn.hutool.http.Method.GET, urlString);
            request.timeout(5000);
            try (HttpResponse response = request.execute()) {
                int responseCode = response.getStatus();

                if (responseCode == HttpStatus.HTTP_OK) {
                    LOG.debug("URL reachable: {}", urlString);
                    return HttpStatus.HTTP_OK;
                }
                LOG.debug("URL unreachable: statusCode={}, url={}", responseCode, urlString);
                return responseCode;
            }
        } catch (Exception e) {
            LOG.error("Failed to reach URL: url={}, error={}", urlString, e.getMessage(), e);
        }
        return -1;
    }

    public static String generateHmac(Object data, String secret) {
        if (data == null) {
            throw new GXBusinessException("Signing data must not be null");
        }
        if (CharSequenceUtil.isBlank(secret)) {
            throw new GXBusinessException("Signing secret must not be blank");
        }
        try {
            Mac mac = SecureUtil.createMac("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw new GXBusinessException("ObjectMapper bean is unavailable");
            }

            String jsonData = objectMapper.writeValueAsString(data);
            byte[] hmacBytes = mac.doFinal(jsonData.getBytes(StandardCharsets.UTF_8));
            return Base64Encoder.encode(hmacBytes);
        } catch (JacksonException e) {
            throw new GXBusinessException("JSON serialization failed: " + e.getMessage(), e);
        } catch (InvalidKeyException e) {
            throw new GXBusinessException("Invalid HMAC secret: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GXBusinessException("HMAC signing failed: " + e.getMessage(), e);
        }
    }

    public static boolean checkHmac(String secret, String clientHmac, Object payload) {
        if (CharSequenceUtil.isBlank(secret)) {
            throw new GXBusinessException("Signing secret must not be blank");
        }
        if (CharSequenceUtil.isBlank(clientHmac)) {
            return false;
        }
        if (payload == null) {
            throw new GXBusinessException("Payload must not be null");
        }
        try {
            Mac mac = SecureUtil.createMac("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw new GXBusinessException("ObjectMapper bean is unavailable");
            }
            String jsonData = objectMapper.writeValueAsString(payload);
            byte[] hmacBytes = mac.doFinal(jsonData.getBytes(StandardCharsets.UTF_8));
            String serverHmac = Base64Encoder.encode(hmacBytes);
            return MessageDigest.isEqual(
                    serverHmac.getBytes(StandardCharsets.UTF_8),
                    clientHmac.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            LOG.error("HMAC verification failed: {}", e.getMessage(), e);
            return false;
        }
    }

    public static List<GXUpdateField<?>> convertUpdateFieldRequestLst(List<GXUpdateFieldRequest> updateLst) {
        if (CollUtil.isEmpty(updateLst)) {
            return Collections.emptyList();
        }

        List<GXUpdateField<?>> updateFields = new ArrayList<>(updateLst.size());

        List<GXUpdateField<?>> result = updateLst.stream().map(updateField -> {
            try {
                if (updateField == null) {
                    throw new GXBusinessException("Update field request must not be null");
                }
                String tableName = updateField.tableName();
                String fieldName = updateField.fieldName();
                String className = updateField.className();
                Object value = updateField.value();

                if (CharSequenceUtil.isBlank(className)) {
                    throw new GXBusinessException("Update field class name must not be blank: " + fieldName);
                }

                Class<?> updateFieldClass = UPDATE_FIELD_CLASS_CACHE.computeIfAbsent(className, name -> {
                    try {
                        return Class.forName(name);
                    } catch (ClassNotFoundException e) {
                        throw new GXBusinessException("Update field class not found: " + name, e);
                    }
                });

                Object updateFieldObj = ReflectUtil.newInstance(updateFieldClass, tableName, fieldName, value);

                if (!(updateFieldObj instanceof GXUpdateField<?>)) {
                    throw new GXBusinessException("Created object is not a GXUpdateField: " + className);
                }

                return (GXUpdateField<?>) updateFieldObj;
            } catch (GXBusinessException e) {
                throw e;
            } catch (Exception e) {
                String fieldName = updateField == null ? "unknown" : updateField.fieldName();
                throw new GXBusinessException("Failed to create update field: " + fieldName + ", reason: " + e.getMessage(), e);
            }
        }).collect(Collectors.toList());

        updateFields.addAll(result);

        return updateFields;
    }

    public static double getSystemLoadAverage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double loadAverage = osBean.getSystemLoadAverage();

            if (loadAverage < 0) {
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                    double cpuLoad = sunOsBean.getProcessCpuLoad();
                    return cpuLoad;
                }
                return -1;
            }
            int processors = osBean.getAvailableProcessors();
            if (processors <= 0) {
                LOG.debug("Invalid processor count: {}", processors);
                return loadAverage;
            }

            return Math.min(loadAverage / processors, 1.0);
        } catch (Exception e) {
            LOG.error("Failed to get system load average: {}", e.getMessage());
            return -1;
        }
    }

    private static final class NullParam {
        private NullParam() {
        }
    }
}
