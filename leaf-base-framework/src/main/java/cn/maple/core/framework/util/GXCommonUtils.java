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
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GXCommonUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXCommonUtils.class);

    private static final String MAP_STR_FORMAT_REGULAR = "\\{(.+?)=(.+?)(, (.+?)=(.+?))*\\}";

    @Getter
    private static final CopyOptions defaultCopyOptions = CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true).setConverter(GXHutoolDataConvert::staticConvert);

    @Getter
    private static final Map<GXMethodCacheKeyUtils.MethodCacheKey, Method> METHOD_CACHE = new ConcurrentHashMap<>(64);

    private static final Class<?>[] EMPTY_PARAM_TYPES = new Class<?>[0];

    private static final Class<?> NULL_PARAM_TYPE = NullParam.class;

    private static final Method METHOD_NOT_FOUND = initMethodNotFound();

    private GXCommonUtils() {
        throw new AssertionError("不能实例化 GXCommonUtils 工具类");
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
            throw new IllegalArgumentException("配置键名不能为空");
        }
        if (clazzType == null) {
            throw new IllegalArgumentException("返回值类型不能为null");
        }

        try {
            Environment environment = GXSpringContextUtils.getEnvironment();
            if (ObjectUtil.isNull(environment)) {
                LOG.debug("未找到Environment Bean，无法获取配置值!");
                return getClassDefaultValue(clazzType);
            }
            boolean simpleValueType = ClassUtil.isSimpleValueType(clazzType);
            if (simpleValueType) {
                final R envValue = environment.getProperty(key, clazzType);
                if (null == envValue) {
                    return getClassDefaultValue(clazzType);
                }
                return envValue;
            }

            String envValue = environment.getProperty(key, String.class);
            if (envValue == null) {
                return getClassDefaultValue(clazzType);
            }

            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                LOG.warn("未找到ObjectMapper Bean，无法进行复杂类型转换");
                return getClassDefaultValue(clazzType);
            }

            return objectMapper.readValue(envValue, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            LOG.error("配置值[{}]转换为类型[{}]失败: {}", key, clazzType.getName(), exception.getMessage());
            throw new GXConvertException("转换失败", exception);
        } catch (Exception e) {
            LOG.error("获取配置值过程中发生异常: {}", e.getMessage());
            return getClassDefaultValue(clazzType);
        }
    }

    public static <R> R getEnvironmentValue(String key, Class<R> clazzType, R defaultValue) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("配置键名不能为空");
        }
        if (clazzType == null) {
            throw new IllegalArgumentException("返回值类型不能为null");
        }

        try {
            final R envValue = Objects.requireNonNull(GXSpringContextUtils.getEnvironment()).getProperty(key, clazzType);
            if (null == envValue) {
                return defaultValue;
            }
            return envValue;
        } catch (Exception e) {
            LOG.warn("获取配置值[{}]失败，使用默认值: {}", key, e.getMessage());
            return defaultValue;
        }
    }

    public static String getActiveProfile() {
        try {
            String[] activeProfiles = Objects.requireNonNull(GXSpringContextUtils.getEnvironment()).getActiveProfiles();
            if (activeProfiles.length > 0) {
                return activeProfiles[0];
            }
            return GXSpringContextUtils.getEnvironment().getDefaultProfiles()[0];
        } catch (Exception e) {
            LOG.warn("获取激活的Profile失败: {}", e.getMessage());
            return "default";
        }
    }

    public static <R> R getClassDefaultValue(Class<R> clazzType) {
        if (clazzType == null) {
            throw new IllegalArgumentException("Class对象不能为null");
        }
        try {
            if (ClassUtil.isBasicType(clazzType) && !ClassUtil.isPrimitiveWrapper(clazzType)) {
                return Convert.convert(clazzType, ClassUtil.getDefaultValue(clazzType));
            }
            return ReflectUtil.newInstanceIfPossible(clazzType);
        } catch (Exception e) {
            LOG.warn("为类型[{}]创建默认值失败: {}", clazzType.getName(), e.getMessage());
            return null;
        }
    }

    public static String hiddenPhoneNumber(CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar) {
        if (CharSequenceUtil.isBlank(phoneNumber)) {
            return "";
        }

        if (startInclude < 0 || endExclude > phoneNumber.length() || startInclude >= endExclude) {
            LOG.warn("手机号掩码参数无效: startInclude={}, endExclude={}, phoneLength={}",
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
            throw new GXBusinessException("加密数据明文不能为空");
        }
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("加密KEY不能为空");
        }

        int safeExpiry = Math.max(0, expiry);

        String combinedKey = CharSequenceUtil.format("{}{}", key, GXCommonConstant.COMMON_ENCRYPT_KEY);

        String jsonData = JSONUtil.toJsonStr(data);
        return GXAuthCodeUtils.authCodeEncode(jsonData, combinedKey, safeExpiry);
    }

    public static Dict decryptedData(String encryptedStr, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("解密KEY不能为空");
        }
        if (CharSequenceUtil.isEmpty(encryptedStr)) {
            LOG.warn("待解密的字符串为空");
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
            LOG.error("数据解密失败: {}", e.getMessage());
            return Dict.create();
        }
    }

    @SuppressWarnings("unchecked")
    public static <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions, Object extraData) {
        if (Objects.isNull(source)) {
            return null;
        }

        if (tClass == null) {
            throw new IllegalArgumentException("目标对象类型不能为null");
        }

        if (ClassUtil.isSimpleTypeOrArray(tClass)) {
            return (T) source;
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
                throw new GXConvertException("无法创建目标类型的实例: " + tClass.getName());
            }

            if (!TypeToken.of(target.getClass()).isSubtypeOf(Map.class) && TypeToken.of(source.getClass()).isSubtypeOf(GXBaseData.class) && ObjectUtil.isNull(copyOptions)) {
                LOG.debug("使用CGLIB进行高效属性复制!!");
                //GXCglibUtils.copy(source, target, new GXCGLibDataConvert(tClass));
                GXCglibUtils.copy(source, target, GXCGLibDataConvert.getConverter(tClass));
            } else {
                // 使用默认的复制选项（如果未指定）
                copyOptions = ObjectUtil.defaultIfNull(copyOptions, GXCommonUtils::getDefaultCopyOptions);
                LOG.debug("使用BeanUtil进行属性复制!!");
                BeanUtil.copyProperties(source, target, copyOptions);
            }

            if (CharSequenceUtil.isNotEmpty(methodName)) {
                reflectCallObjectMethod(target, methodName, extraData);
            }

            reflectCallObjectMethod(target, "verify");

            return target;
        } catch (Exception e) {
            LOG.error("对象转换失败: 源类型[{}], 目标类型[{}], 错误: {}",
                    source.getClass().getName(), tClass.getName(), e.getMessage());

            Throwable rootCause = e;
            if (ObjectUtil.isNotNull(e.getCause())) {
                rootCause = e.getCause();
                if (ObjectUtil.isNotNull(rootCause.getCause())) {
                    rootCause = rootCause.getCause();
                }
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("对象转换异常详细信息:", e);
            }
            String errorMessage = CharSequenceUtil.format("对象转换失败: 源类型[{}]转换为目标类型[{}]时发生错误: {}",
                    source.getClass().getSimpleName(),
                    tClass.getSimpleName(),
                    rootCause.getMessage());
            throw new GXConvertException(errorMessage, rootCause);
        }
    }

    public static <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions) {
        return convertSourceToTarget(source, tClass, methodName, copyOptions, Dict.create());
    }

    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass) {
        return convertSourceListToTargetList(collection, tClass, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, null);
    }

    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions) {
        return convertSourceListToTargetList(collection, tClass, methodName, copyOptions, Dict.create());
    }

    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions, Object extraData) {
        if (CollUtil.isEmpty(collection)) {
            return Collections.emptyList();
        }
        if (tClass == null) {
            throw new IllegalArgumentException("目标对象类型不能为null");
        }

        if (collection.size() > 1000) {
            return collection.parallelStream()
                    .map(source -> convertSourceToTarget(source, tClass, methodName, copyOptions, extraData))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return collection.stream()
                .map(source -> convertSourceToTarget(source, tClass, methodName, copyOptions, extraData))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static Object reflectCallObjectMethod(Class<?> serviceClass, String methodName, Object... params) {
        if (serviceClass == null) {
            throw new IllegalArgumentException("目标对象类型不能为null");
        }
        Object target = GXSpringContextUtils.getBean(serviceClass);
        if (target == null) {
            LOG.warn("Spring容器中未找到类型为{}的Bean", serviceClass.getName());
            return null;
        }
        return reflectCallObjectMethod(target, methodName, params);
    }

    public static <R> Object reflectCallObjectMethod(R object, String methodName, Object... params) {
        if (Objects.isNull(object)) {
            LOG.warn("反射调用的object对象为null");
            return null;
        }

        if (CharSequenceUtil.isEmpty(methodName)) {
            methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
            LOG.debug("方法名为空，使用默认方法名: {}", methodName);
        }

        if (Objects.isNull(params)) {
            params = new Object[0];
        }

        try {
            Class<?>[] paramTypes = resolveParamTypes(params);

            Method method = findMethod(object.getClass(), methodName, paramTypes);

            if (method == METHOD_NOT_FOUND) {
                LOG.warn("方法{}.{}({})不存在,反射调用失败!", object.getClass().getSimpleName(), methodName, Arrays.toString(params));
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
        Objects.requireNonNull(clazz, "目标类不能为null");
        Objects.requireNonNull(methodName, "方法名不能为null");
        Objects.requireNonNull(paramTypes, "参数类型数组不能为null");

        final GXMethodCacheKeyUtils.MethodCacheKey methodCacheKey =
                GXMethodCacheKeyUtils.getMethodCacheKey(clazz, methodName, paramTypes);

        return METHOD_CACHE.computeIfAbsent(methodCacheKey, key -> {
            Method method = lookupMethod(clazz, methodName, paramTypes);
            return method == null ? METHOD_NOT_FOUND : method;
        });
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

    private static Method lookupMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
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

    private static int getParamMatchScore(Class<?> declaredType, Class<?> actualType) {
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
                LOG.error("反射调用过程中发生未知异常: {}", utilException.getMessage());
                throw utilException;
            }
            case SecurityException se -> {
                LOG.error("反射调用过程中发生安全异常: {}", se.getMessage());
                throw new GXBusinessException("反射调用安全检查失败: " + se.getMessage(), se);
            }
            case IllegalArgumentException iae -> {
                LOG.error("反射调用参数不匹配: {}", iae.getMessage());
                throw new GXBusinessException("反射调用参数不匹配: " + iae.getMessage(), iae);
            }
            default -> {
                LOG.error("反射调用过程中发生异常: {}", e.getMessage());
                throw new GXBusinessException("反射调用失败: " + e.getMessage(), e);
            }
        }
    }

    private static RuntimeException unwrapInvocationTargetException(InvocationTargetException exception, Object object, String methodName, Object[] params) {
        Throwable targetException = exception.getTargetException();
        if (targetException instanceof GXBeanValidateException beanValidateException) {
            return beanValidateException;
        }
        String exceptionMessage = CharSequenceUtil.isEmpty(targetException.getMessage())
                ? "系统反射调用失败" : targetException.getMessage();
        LOG.error("系统反射调用{}.{}({})失败 , [错误消息 : {}] [错误原因 : {}]",
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

        // 找出当前节点的所有直接子节点
        List<R> children = subs.stream().filter(sub -> {
            if (Objects.isNull(sub)) {
                return false;
            }
            Object parentId = GXCommonUtils.reflectCallObjectMethod(sub, getParentMethodName);
            Object id = GXCommonUtils.reflectCallObjectMethod(parent, "getId");
            return Objects.equals(parentId, id);
        }).collect(Collectors.toList());

        if (!CollUtil.isEmpty(children)) {
            // 有子分类的情况，设置子节点并递归构建
            GXCommonUtils.reflectCallObjectMethod(parent, "setChildren", children);
            // 递归处理每个子节点
            children.forEach(child -> buildSubs(child, subs, getParentMethodName));
        }
    }

    public static <R> R decodeConnectStr(String connectEncodeStr, Class<R> targetClazz) {
        if (targetClazz == null) {
            throw new IllegalArgumentException("目标类型不能为null");
        }

        if (CharSequenceUtil.isEmpty(connectEncodeStr)) {
            return null;
        }

        String secretKey = System.getProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY);

        if (CharSequenceUtil.isEmpty(secretKey)) {
            secretKey = System.getenv(GXCommonConstant.DATA_SOURCE_SECRET_KEY_ENV);
        }

        if (CharSequenceUtil.isEmpty(secretKey)) {
            GXLoggerUtils.logDebug(LOG, "解密密钥为空, 连接信息不进行解密操作");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        if (!Base64.isBase64(connectEncodeStr)) {
            GXLoggerUtils.logDebug(LOG, "连接信息不是有效的Base64编码, 将直接转换原始字符串");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        String decodedStr = GXAuthCodeUtils.authCodeDecode(connectEncodeStr, secretKey);

        if (CharSequenceUtil.equalsIgnoreCase(decodedStr, "{}")) {
            GXLoggerUtils.logDebug(LOG, "链接信息参数解码失败, 将使用原始的链接信息");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        decodedStr = HtmlUtil.unescape(decodedStr);

        return Convert.convert(targetClazz, decodedStr);
    }

    @SuppressWarnings("all")
    public static <R> Class<R> getGenericClassType(Class<?> clazz, Integer index) {
        if (clazz == null) {
            throw new IllegalArgumentException("目标Class对象不能为null");
        }
        if (index == null) {
            throw new IllegalArgumentException("泛型索引不能为null");
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
            if (TypeToken.of(value.getClass()).isSubtypeOf(List.class)) {
                var valueSet = new HashSet<>(Convert.toList(value));
                data.set("value", valueSet);
            } else {
                data.set("value", value);
            }

            Function<Dict, GXCondition<?>> function = GXDataSourceConstant.getFunction(op);
            if (Objects.isNull(function)) {
                throw new GXBusinessException(CharSequenceUtil.format("请完善{}类型数据转换器", op));
            }

            conditions.add(function.apply(data));
        }));

        return conditions;
    }

    public static <T> T convertStrToTarget(String str, Class<T> targetClazz) {
        if (CharSequenceUtil.isEmpty(str)) {
            return null;
        }
        if (targetClazz == null) {
            LOG.warn("目标类型为null，无法进行转换");
            return null;
        }
        if (ReUtil.isMatch(MAP_STR_FORMAT_REGULAR, str)) {
            try {
                Map<String, Object> tmpMap = new HashMap<>();
                String content = str.replace("{", "").replace("}", "");
                if (CharSequenceUtil.isNotEmpty(content)) {
                    Arrays.stream(content.split(","))
                            .map(String::trim)
                            .map(arrayData -> arrayData.split("=", 2)) // 限制分割次数为2，避免值中包含=符号导致问题
                            .filter(array -> array.length == 2) // 确保数组有两个元素
                            .forEach(array -> tmpMap.put(array[0].trim(), array[1].trim()));
                }
                return Convert.convert(targetClazz, tmpMap);
            } catch (Exception ex) {
                LOG.error("Map格式字符串转换失败! 原始字符串: {}, 错误信息: {}", str, ex.getMessage());
                return null;
            }
        }

        if (JSONUtil.isTypeJSON(str)) {
            try {
                return JSONUtil.toBean(str, targetClazz);
            } catch (ConvertException ex) {
                LOG.error("JSON数据转换失败! 原始字符串: {}, 错误信息: {}", str, ex.getMessage());
                return null;
            } catch (Exception ex) {
                LOG.error("JSON数据转换过程中发生未知异常! 原始字符串: {}, 错误信息: {}", str, ex.getMessage());
                return null;
            }
        }

        try {
            return Convert.convert(targetClazz, str);
        } catch (Exception ex) {
            LOG.debug("直接转换失败，不是有效的JSON或Map格式: {}", str);
            return null;
        }
    }

    public static boolean isBase64(String base64Str) {
        if (CharSequenceUtil.isEmpty(base64Str)) {
            return false;
        }
        return Base64.isBase64(base64Str);
    }

    public static boolean checkMethodExists(Class<?> targetClazz, String methodName, Object... params) {
        if (targetClazz == null) {
            throw new IllegalArgumentException("目标类不能为null");
        }
        if (CharSequenceUtil.isEmpty(methodName)) {
            throw new IllegalArgumentException("方法名不能为null或空");
        }
        if (params == null) {
            params = new Object[0];
        }
        Class<?>[] classes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            if (Objects.nonNull(params[i])) {
                classes[i] = params[i].getClass();
            }
        }
        Method method = ReflectUtil.getMethod(targetClazz, methodName, classes);
        return ObjectUtil.isNotNull(method);
    }

    public static Integer checkURLReachable(String urlString) {
        if (CharSequenceUtil.isEmpty(urlString)) {
            LOG.warn("URL地址不能为空");
            return -1;
        }

        try {
            HttpRequest request = HttpUtil.createRequest(cn.hutool.http.Method.GET, urlString);
            request.timeout(5000);
            HttpResponse response = request.execute();
            int responseCode = response.getStatus();

            if (responseCode == HttpStatus.HTTP_OK) {
                LOG.info("URL可访问: {}", urlString);
                return HttpStatus.HTTP_OK;
            } else {
                LOG.info("URL不可访问，返回状态码: {}，URL: {}", responseCode, urlString);
                return responseCode;
            }
        } catch (Exception e) {
            LOG.error(CharSequenceUtil.format("访问URL时发生错误: {}，URL: {}", e.getMessage(), urlString), e);
        }
        return -1;
    }

    public static String generateHmac(Object data, String secret) {
        if (data == null) {
            throw new GXBusinessException("待签名数据不能为null");
        }
        if (CharSequenceUtil.isBlank(secret)) {
            throw new GXBusinessException("签名密钥不能为空");
        }
        try {
            Mac mac = SecureUtil.createMac("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw new GXBusinessException("Spring容器中不存在ObjectMapper Bean!!!");
            }

            String jsonData = objectMapper.writeValueAsString(data);
            byte[] hmacBytes = mac.doFinal(jsonData.getBytes(StandardCharsets.UTF_8));
            return Base64Encoder.encode(hmacBytes);
        } catch (JacksonException e) {
            throw new GXBusinessException("JSON序列化失败: " + e.getMessage(), e);
        } catch (InvalidKeyException e) {
            throw new GXBusinessException("无效的HMAC密钥: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new GXBusinessException("HMAC签名生成失败: " + e.getMessage(), e);
        }
    }

    public static boolean checkHmac(String secret, String clientHmac, Object payload) {
        if (CharSequenceUtil.isBlank(secret)) {
            throw new GXBusinessException("签名密钥不能为空");
        }
        if (CharSequenceUtil.isBlank(clientHmac)) {
            return false;
        }
        if (payload == null) {
            throw new GXBusinessException("待验证数据不能为null");
        }
        try {
            Mac mac = SecureUtil.createMac("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw new GXBusinessException("Spring上下文中没有ObjectMapper");
            }
            String jsonData = objectMapper.writeValueAsString(payload);
            byte[] hmacBytes = mac.doFinal(jsonData.getBytes(StandardCharsets.UTF_8));
            String serverHmac = Base64Encoder.encode(hmacBytes);
            return MessageDigest.isEqual(
                    serverHmac.getBytes(StandardCharsets.UTF_8),
                    clientHmac.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            LOG.error("HMAC验证失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public static List<GXUpdateField<?>> convertUpdateFieldRequestLst(List<GXUpdateFieldRequest> updateLst) {
        if (CollUtil.isEmpty(updateLst)) {
            return Collections.emptyList();
        }

        List<GXUpdateField<?>> updateFields = new ArrayList<>(updateLst.size());

        final ConcurrentHashMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(8);

        Stream<GXUpdateFieldRequest> stream = updateLst.size() > 100 ?
                updateLst.parallelStream() : updateLst.stream();

        List<GXUpdateField<?>> result = stream.map(updateField -> {
            try {
                String tableName = updateField.tableName();  // 表名
                String fieldName = updateField.fieldName();  // 字段名
                String className = updateField.className();  // 更新字段类名
                Object value = updateField.value();          // 更新字段值

                if (CharSequenceUtil.isBlank(className)) {
                    throw new GXBusinessException("更新字段类名不能为空: " + fieldName);
                }

                Class<?> updateFieldClass = CLASS_CACHE.computeIfAbsent(className, name -> {
                    try {
                        return Class.forName(name);
                    } catch (ClassNotFoundException e) {
                        throw new GXBusinessException("更新字段类未找到: " + name, e);
                    }
                });

                Object updateFieldObj = ReflectUtil.newInstance(updateFieldClass, tableName, fieldName, value);

                if (!(updateFieldObj instanceof GXUpdateField<?>)) {
                    throw new GXBusinessException("创建的对象不是GXUpdateField类型: " + className);
                }

                return (GXUpdateField<?>) updateFieldObj;
            } catch (GXBusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new GXBusinessException("创建更新字段对象失败: " + updateField.fieldName() + ", 原因: " + e.getMessage(), e);
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
                LOG.warn("获取到的处理器数量无效: {}", processors);
                return loadAverage;
            }

            return Math.min(loadAverage / processors, 1.0);
        } catch (Exception e) {
            LOG.error("获取系统负载失败: {}", e.getMessage());
            return -1;
        }
    }

    private static final class NullParam {
        private NullParam() {
        }
    }
}
