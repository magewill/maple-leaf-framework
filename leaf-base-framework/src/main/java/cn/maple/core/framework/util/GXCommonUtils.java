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
import cn.hutool.http.HtmlUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 框架通用工具集：配置读取、对象转换、反射调用、树构建、加解密与系统指标。
 *
 * <p>使用前需注意的几条隐式语义：
 * <ul>
 *     <li>{@link #getEnvironmentValue(String, Class)} 在配置缺失时返回<b>类型的默认值</b>而不是 {@code null}，
 *     例如 {@code String.class} 会得到空串，因此不能用 {@code == null} 判断"配置不存在"。</li>
 *     <li>{@link #convertSourceToTarget} 不传 {@link CopyOptions} 时，两条拷贝分支对"字段类型不兼容"的处理不同：
 *     Hutool 分支（默认 {@code CopyOptions} 已开 {@code ignoreError}）会<b>静默跳过</b>该字段；
 *     CGLIB 分支会尝试转换，失败时记一条 WARN 并把目标字段留空。
 *     两者都<b>不会</b>因单个字段失败而中断整次转换；
 *     但 {@code verify()} 与 {@code customizeProcess()} 抛出的业务/校验异常会<b>原样透传</b>，不会被包装成转换异常。</li>
 *     <li>{@link #reflectCallObjectMethod} 在方法不存在时返回 {@code null}（不抛异常），
 *     调用方需自行判断；方法存在但执行抛异常时会把目标异常解包后抛出。</li>
 * </ul>
 */
@Slf4j
public final class GXCommonUtils {
    private static final String MAP_STR_FORMAT_REGULAR = "\\{(.+?)=(.+?)(, (.+?)=(.+?))*\\}";

    private static final String BASE64_FORMAT_REGULAR = "^[A-Za-z0-9+/]*={0,2}$";

    private static final String DEFAULT_PARENT_METHOD_NAME = "getParentId";

    private static final String DEFAULT_ID_METHOD_NAME = "getId";

    private static final String DEFAULT_CHILDREN_METHOD_NAME = "setChildren";

    private static final String DEFAULT_VERIFY_METHOD_NAME = "verify";

    /**
     * 全局默认拷贝配置。字段名保持小写以兼容外部模块使用的 {@code GXCommonUtils::getDefaultCopyOptions} 方法引用。
     * 该实例是共享且可变的，调用方不应修改它。
     */
    @Getter
    private static final CopyOptions defaultCopyOptions = CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true).setConverter(GXHutoolDataConvert::staticConvert);

    /**
     * 反射方法缓存：按目标类分槽，槽内以 (方法名, 参数类型) 为键。
     * 使用 {@link ClassValue} 而非全局 Map，类卸载时条目自动释放，不会钉住类加载器。
     */
    private static final ClassValue<ConcurrentMap<GXMethodCacheKeyUtils.MethodCacheKey, Method>> METHOD_CACHE =
            new ClassValue<>() {
                @Override
                protected ConcurrentMap<GXMethodCacheKeyUtils.MethodCacheKey, Method> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>(8);
                }
            };

    private static final ConcurrentHashMap<String, Class<?>> UPDATE_FIELD_CLASS_CACHE = new ConcurrentHashMap<>(8);

    /**
     * 单个类的反射缓存上限。键中的参数类型来自运行时实参类型，理论上可被多种 DTO 类型撑大，
     * 因此仍需上限；超限只清理当前类的槽位，不影响其他类。
     */
    private static final int MAX_METHOD_CACHE_SIZE_PER_CLASS = 512;

    private static final int MAX_UPDATE_FIELD_CLASS_CACHE_SIZE = 256;

    private static final Class<?>[] EMPTY_PARAM_TYPES = new Class<?>[0];

    private static final Object[] EMPTY_PARAMS = new Object[0];

    private static final Class<?> NULL_PARAM_TYPE = NullParam.class;

    private static final Method METHOD_NOT_FOUND = initMethodNotFound();

    /**
     * 数值包装类型的拓宽序，<b>必须与 JLS 5.1.2 的拓宽顺序一致</b>：byte &lt; short &lt; int &lt; long &lt; float &lt; double。
     * 重新编号会静默改变方法重载的匹配结果，改动前请先确认 {@link #isWideningConversion} 的语义。
     */
    private static final int RANK_BYTE = 1;

    private static final int RANK_SHORT = 2;

    private static final int RANK_INT = 3;

    private static final int RANK_LONG = 4;

    private static final int RANK_FLOAT = 5;

    private static final int RANK_DOUBLE = 6;

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

    /**
     * 读取配置值。配置缺失或环境不可用时返回 {@code clazzType} 的默认值（基本类型为 0/false，
     * {@code String} 为空串，其余类型尝试无参实例化，失败则为 {@code null}）。
     *
     * @throws GXConvertException 配置存在但无法反序列化为目标类型时抛出
     */
    public static <R> @Nullable R getEnvironmentValue(String key, Class<R> clazzType) {
        validateEnvironmentArguments(key, clazzType);
        return resolveEnvironmentValue(key, clazzType, () -> getClassDefaultValue(clazzType), true);
    }

    /**
     * 读取配置值，配置缺失或环境不可用时返回 {@code defaultValue}。
     * 与 {@link #getEnvironmentValue(String, Class)} 的区别是：反序列化失败也不会抛异常，而是回退到默认值。
     */
    public static <R> @Nullable R getEnvironmentValue(String key, Class<R> clazzType, R defaultValue) {
        validateEnvironmentArguments(key, clazzType);
        return resolveEnvironmentValue(key, clazzType, () -> defaultValue, false);
    }

    private static void validateEnvironmentArguments(String key, Class<?> clazzType) {
        if (CharSequenceUtil.isBlank(key)) {
            throw new IllegalArgumentException("Configuration key must not be blank");
        }
        if (clazzType == null) {
            throw new IllegalArgumentException("Return type must not be null");
        }
    }

    private static <R> @Nullable R resolveEnvironmentValue(String key, Class<R> clazzType, Supplier<R> fallbackSupplier, boolean throwOnConversionFailure) {
        try {
            Environment environment = GXSpringContextUtils.getEnvironment();
            if (ObjectUtil.isNull(environment)) {
                log.debug("Environment is unavailable, cannot read property: key={}", key);
                return fallbackSupplier.get();
            }

            R envValue = null;
            Exception conversionFailure = null;
            try {
                envValue = environment.getProperty(key, clazzType);
            } catch (Exception e) {
                conversionFailure = e;
            }
            if (null != envValue) {
                return envValue;
            }
            if (ClassUtil.isSimpleValueType(clazzType)) {
                // 简单类型不做 JSON 兜底，因此"属性存在但转换失败"必须显式处理，不能静默返回类型默认值
                if (conversionFailure != null && environment.getProperty(key, String.class) != null) {
                    return handleConversionFailure(key, clazzType, conversionFailure, fallbackSupplier, throwOnConversionFailure);
                }
                return fallbackSupplier.get();
            }

            String rawValue = environment.getProperty(key, String.class);
            if (rawValue == null) {
                return fallbackSupplier.get();
            }

            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                // 复杂类型只能靠 JSON 反序列化，缺 ObjectMapper 等于该配置读不出来，用 warn 而不是 debug
                log.warn("ObjectMapper bean is unavailable, cannot convert property: key={}, type={}", key, clazzType.getName());
                return fallbackSupplier.get();
            }

            try {
                return objectMapper.readValue(rawValue, clazzType);
            } catch (Exception exception) {
                // 这里的 try 只包住 readValue 一句，因此 Exception 就等于"反序列化失败"本身。
                // 不能只 catch JacksonException：自定义反序列化器抛出的 IllegalArgumentException / NullPointerException
                // 等会漏到外层 catch，在严格模式下被静默降级成默认值，与"反序列化失败即抛"的契约矛盾
                return handleConversionFailure(key, clazzType, exception, fallbackSupplier, throwOnConversionFailure);
            }
        } catch (GXConvertException e) {
            throw e;
        } catch (Exception e) {
            // 走到这里说明是"环境不可用/占位符无法解析"一类的外部故障，而不是值本身转换失败，
            // 因此按契约回退到默认值。注意：throwOnConversionFailure 只影响日志级别，
            // 不会让这个分支抛异常——真正的转换失败在上面的 handleConversionFailure 里处理
            if (throwOnConversionFailure) {
                log.error("Failed to get property: key={}, error={}", key, e.getMessage(), e);
            } else {
                log.warn("Failed to get property, use default value: key={}, error={}", key, e.getMessage());
            }
            return fallbackSupplier.get();
        }
    }

    private static <R> @Nullable R handleConversionFailure(String key, Class<R> clazzType, Exception failure,
                                                           Supplier<R> fallbackSupplier, boolean throwOnConversionFailure) {
        if (throwOnConversionFailure) {
            log.error("Failed to convert property: key={}, type={}, error={}", key, clazzType.getName(), failure.getMessage());
            throw new GXConvertException("Property conversion failed", failure);
        }
        log.warn("Failed to convert property, use default value: key={}, type={}, error={}", key, clazzType.getName(), failure.getMessage());
        return fallbackSupplier.get();
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
            log.debug("Failed to get active profile: {}", e.getMessage());
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
            log.debug("Failed to create default value for type: type={}, error={}", clazzType.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 手机号脱敏。仅对合法手机号生效：命中时把 {@code [startInclude, endExclude)} 区间替换为 {@code replacedChar}；
     * 参数越界时原样返回；<b>非手机号（含座机、国际号码）返回空串</b>，调用方需自行判断是否接受这种降级。
     */
    public static String hiddenPhoneNumber(@Nullable CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar) {
        if (CharSequenceUtil.isBlank(phoneNumber)) {
            return "";
        }

        if (startInclude < 0 || endExclude > phoneNumber.length() || startInclude >= endExclude) {
            log.debug("Invalid phone mask arguments: startInclude={}, endExclude={}, phoneLength={}",
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

    public static Dict decryptedData(@Nullable String encryptedStr, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("Decrypt key must not be empty");
        }
        if (CharSequenceUtil.isEmpty(encryptedStr)) {
            log.debug("Encrypted string is empty");
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
            log.error("Failed to decrypt data: {}", e.getMessage());
            return Dict.create();
        }
    }

    /**
     * 对象转换。
     *
     * <p>拷贝策略：不传 {@code copyOptions}、{@code source} 是 {@link GXBaseData} 子类、且 {@code target}
     * 不是 {@link Map} 时走 CGLIB 拷贝，其余情况走 Hutool 拷贝。
     * 单个字段类型不兼容时两条分支都不会中断整次转换，但表现不同：Hutool 分支（默认 {@code CopyOptions}
     * 已开 {@code ignoreError}）静默跳过该字段；CGLIB 分支会尝试转换，失败时记一条 WARN 并把目标字段留空。
     *
     * <p>转换完成后会依次调用 {@code customizeProcess(extraData)}（若存在）与 {@code verify()}。
     * 这两个钩子抛出的 {@link GXBusinessException} / {@link GXBeanValidateException} 会原样透传，
     * 不会被包装成 {@link GXConvertException}，以保证上层能按业务码处理。
     *
     * @return {@code source} 为 {@code null} 时返回 {@code null}
     * @throws GXConvertException 目标类型无法实例化或拷贝过程失败
     */
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

        final String resolvedMethodName = CharSequenceUtil.isBlank(methodName)
                ? GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME : methodName;
        final Object resolvedExtraData = Objects.isNull(extraData) ? Dict.create() : extraData;

        try {
            T target = ReflectUtil.newInstanceIfPossible(tClass);
            if (target == null) {
                throw new GXConvertException("Cannot create target type instance: " + tClass.getName());
            }

            if (!Map.class.isAssignableFrom(target.getClass())
                    && GXBaseData.class.isAssignableFrom(source.getClass())
                    && ObjectUtil.isNull(copyOptions)) {
                log.debug("Copy properties with CGLIB");
                GXCglibUtils.copy(source, target, GXCGLibDataConvert.getConverter(tClass));
            } else {
                log.debug("Copy properties with BeanUtil");
                BeanUtil.copyProperties(source, target, ObjectUtil.defaultIfNull(copyOptions, GXCommonUtils::getDefaultCopyOptions));
            }

            // resolvedMethodName 在入口已把 blank 兜底为非空常量，这里无需再判空
            reflectCallObjectMethod(target, resolvedMethodName, resolvedExtraData);

            reflectCallObjectMethod(target, DEFAULT_VERIFY_METHOD_NAME);

            return target;
        } catch (GXBusinessException | GXBeanValidateException e) {
            log.debug("Object conversion aborted by business/validation exception: sourceType={}, targetType={}, error={}",
                    source.getClass().getName(), tClass.getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Object conversion failed: sourceType={}, targetType={}, error={}",
                    source.getClass().getName(), tClass.getName(), e.getMessage(), e);

            Throwable rootCause = e;
            if (ObjectUtil.isNotNull(e.getCause())) {
                rootCause = e.getCause();
                if (ObjectUtil.isNotNull(rootCause.getCause())) {
                    rootCause = rootCause.getCause();
                }
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

    public static <R> List<R> convertSourceListToTargetList(@Nullable Collection<?> collection, Class<R> tClass) {
        return convertSourceListToTargetList(collection, tClass, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, null);
    }

    public static <R> List<R> convertSourceListToTargetList(@Nullable Collection<?> collection, Class<R> tClass, @Nullable String methodName, @Nullable CopyOptions copyOptions) {
        return convertSourceListToTargetList(collection, tClass, methodName, copyOptions, Dict.create());
    }

    /**
     * 批量转换。{@code null} 元素与转换结果为 {@code null} 的元素都会被跳过。
     *
     * @return 始终返回<b>可变</b>的 {@link ArrayList}，空输入返回空的可变列表
     */
    public static <R> List<R> convertSourceListToTargetList(@Nullable Collection<?> collection, Class<R> tClass, @Nullable String methodName, @Nullable CopyOptions copyOptions, @Nullable Object extraData) {
        if (CollUtil.isEmpty(collection)) {
            return new ArrayList<>(0);
        }
        if (tClass == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }

        return collection.stream()
                .map(source -> convertSourceToTarget(source, tClass, methodName, copyOptions, extraData))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static @Nullable Object reflectCallObjectMethod(Class<?> serviceClass, String methodName, @Nullable Object... params) {
        if (serviceClass == null) {
            throw new IllegalArgumentException("Target type must not be null");
        }
        Object target = GXSpringContextUtils.getBean(serviceClass);
        if (target == null) {
            log.debug("Spring bean not found: type={}", serviceClass.getName());
            return null;
        }
        return reflectCallObjectMethod(target, methodName, params);
    }

    /**
     * 反射调用实例方法。
     *
     * <p>方法不存在（含参数无法匹配）时返回 {@code null} 而不抛异常，因此调用方必须自行判断返回值。
     * 支持可变参数方法（会自动把尾部实参打包为数组）。实参与形参类型不一致但可转换时
     * （如 {@code Integer} 实参匹配 {@code long} 或 {@code Long} 形参）会先做一次转换再调用。
     *
     * @throws GXBusinessException   方法执行抛出非校验类异常、反射访问被拒绝，或实参无法转换成形参类型
     * @throws GXBeanValidateException 方法执行抛出校验异常时原样透传
     */
    public static @Nullable Object reflectCallObjectMethod(@Nullable Object object, @Nullable String methodName, @Nullable Object... params) {
        if (Objects.isNull(object)) {
            log.debug("Reflection target object is null");
            return null;
        }

        String resolvedMethodName = methodName;
        if (CharSequenceUtil.isEmpty(resolvedMethodName)) {
            resolvedMethodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
            log.debug("Method name is empty, using default method: {}", resolvedMethodName);
        }

        final Object[] resolvedParams = Objects.isNull(params) ? EMPTY_PARAMS : params;

        try {
            Class<?>[] paramTypes = resolveParamTypes(resolvedParams);

            Method method = findMethod(object.getClass(), resolvedMethodName, paramTypes);

            if (method == METHOD_NOT_FOUND) {
                log.debug("Method not found, reflection call skipped: target={}.{}, params={}",
                        object.getClass().getSimpleName(), resolvedMethodName, Arrays.toString(resolvedParams));
                return null;
            }

            // trySetAccessible 对静态方法同样有效，避免 Method#canAccess(Object) 在静态方法上传非 null 对象抛 IllegalArgumentException
            if (!method.trySetAccessible()) {
                throw new GXBusinessException(CharSequenceUtil.format("Reflection access denied: target={}.{}",
                        object.getClass().getSimpleName(), resolvedMethodName));
            }

            // 先按形参类型对齐实参，再打包可变参数：Method#invoke 只支持"基本类型"形参的拓宽
            // （如 long 形参接受 Integer 实参），对包装类型形参（如 Long）不做任何转换，
            // 因此 setXxx(Long) 收到 Integer 时必须先转成 Long，否则 invoke 抛 IllegalArgumentException
            final Object[] coercedParams = coerceArguments(resolvedParams, method);
            final Object[] invokeParams = method.isVarArgs()
                    ? packVarArgs(coercedParams, method) : coercedParams;
            return method.invoke(object, invokeParams);
        } catch (Exception ex) {
            return handleReflectionException(ex, object, resolvedMethodName, resolvedParams);
        }
    }

    private static Object[] packVarArgs(Object[] params, Method method) {
        final int fixedCount = method.getParameterCount() - 1;
        final Class<?> varArgsType = method.getParameterTypes()[fixedCount];
        if (params.length == fixedCount + 1 && varArgsType.isInstance(params[fixedCount])) {
            // 调用方直接传入了可变参数数组（如 m(String...) 收到 String[]），按 Java 语义原样透传，
            // 不能再包一层，否则会抛 IllegalArgumentException: array element type mismatch
            return params;
        }
        if (params.length < fixedCount) {
            // 实参不足固定参数，交由 invoke 抛出原始的参数不匹配异常
            return params;
        }
        final Class<?> componentType = varArgsType.getComponentType();
        final int varArgCount = params.length - fixedCount;
        final Object varArgs = Array.newInstance(componentType, varArgCount);
        for (int i = 0; i < varArgCount; i++) {
            Array.set(varArgs, i, params[fixedCount + i]);
        }
        final Object[] packed = new Object[method.getParameterCount()];
        System.arraycopy(params, 0, packed, 0, fixedCount);
        packed[fixedCount] = varArgs;
        return packed;
    }

    /**
     * 按形参类型对齐实参，把无法直接赋值的实参转换成形参类型。
     *
     * <p>仅在实参与形参"类型不同但语义可拓宽"时才会真正转换，因此常规调用（类型精确匹配）
     * 不产生任何额外分配：无实参需要转换时直接返回入参数组本身。
     * 调用方传入的可变参数数组不会被就地修改。
     */
    private static Object[] coerceArguments(Object[] params, Method method) {
        final Class<?>[] declaredTypes = method.getParameterTypes();
        final boolean varArgs = method.isVarArgs();
        final int fixedCount = varArgs ? declaredTypes.length - 1 : declaredTypes.length;
        // 与 packVarArgs 保持同一判定：调用方直接传入可变参数数组时，元素类型已由数组本身保证，无需逐个对齐
        final boolean directVarArgsArray = varArgs
                && params.length == fixedCount + 1
                && declaredTypes[fixedCount].isInstance(params[fixedCount]);
        final int fixedLimit = Math.min(params.length, fixedCount);

        Object[] coerced = null;
        for (int i = 0; i < fixedLimit; i++) {
            Object value = coerceValue(params[i], declaredTypes[i], method, i);
            if (value != params[i]) {
                coerced = coerced == null ? params.clone() : coerced;
                coerced[i] = value;
            }
        }
        if (varArgs && !directVarArgsArray) {
            final Class<?> componentType = declaredTypes[fixedCount].getComponentType();
            for (int i = fixedCount; i < params.length; i++) {
                Object value = coerceValue(params[i], componentType, method, i);
                if (value != params[i]) {
                    coerced = coerced == null ? params.clone() : coerced;
                    coerced[i] = value;
                }
            }
        }
        return coerced == null ? params : coerced;
    }

    private static Object coerceValue(@Nullable Object value, Class<?> declaredType, Method method, int index) {
        if (value == null) {
            return null;
        }
        // 基本类型形参要用其包装类型判断，否则 int 形参配 Integer 实参会误判为需要转换
        if (wrapPrimitiveType(declaredType).isInstance(value)) {
            return value;
        }
        try {
            return Convert.convert(declaredType, value);
        } catch (Exception e) {
            log.error("Reflection argument coercion failed: target={}.{}, index={}, declaredType={}, actualType={}",
                    method.getDeclaringClass().getSimpleName(), method.getName(), index,
                    declaredType.getName(), value.getClass().getName());
            throw new GXBusinessException(CharSequenceUtil.format(
                    "Reflection argument type mismatch: {}.{} parameter {} cannot be converted from {} to {}",
                    method.getDeclaringClass().getSimpleName(), method.getName(), index,
                    value.getClass().getSimpleName(), declaredType.getSimpleName()), e);
        }
    }

    private static Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        Objects.requireNonNull(clazz, "Target class must not be null");
        Objects.requireNonNull(methodName, "Method name must not be null");
        Objects.requireNonNull(paramTypes, "Parameter type array must not be null");

        final ConcurrentMap<GXMethodCacheKeyUtils.MethodCacheKey, Method> classCache = METHOD_CACHE.get(clazz);
        if (classCache.size() > MAX_METHOD_CACHE_SIZE_PER_CLASS) {
            classCache.clear();
            log.debug("Reflection method cache cleared: type={}, maxSize={}", clazz.getName(), MAX_METHOD_CACHE_SIZE_PER_CLASS);
        }

        final GXMethodCacheKeyUtils.MethodCacheKey methodCacheKey =
                GXMethodCacheKeyUtils.getMethodCacheKey(clazz, methodName, paramTypes);

        return classCache.computeIfAbsent(methodCacheKey, key -> {
            Method resolvedMethod = lookupMethod(clazz, methodName, paramTypes);
            return resolvedMethod == null ? METHOD_NOT_FOUND : resolvedMethod;
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

    private static @Nullable Method lookupMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        // 注意：Hutool 的 getMethod 在 paramTypes 为空时返回"第一个同名方法"，可能命中带参重载，
        // 因此零实参场景跳过该快捷路径，交给下面的打分逻辑挑选真正的无参方法。
        if (paramTypes.length > 0) {
            Method method = ReflectUtil.getMethod(clazz, methodName, paramTypes);
            if (method != null) {
                return method;
            }
        }

        MethodMatch best = MethodMatch.NONE;
        for (Method candidate : clazz.getMethods()) {
            best = pickBetter(best, candidate, methodName, paramTypes);
        }
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                best = pickBetter(best, candidate, methodName, paramTypes);
            }
        }
        return best.method();
    }

    /**
     * 打分并把候选方法折叠进当前最优解。两个方法来源（{@code getMethods()} 与逐级父类的
     * {@code getDeclaredMethods()}）共用同一套比较规则，避免两处各写一份而漏改。
     */
    private static MethodMatch pickBetter(MethodMatch best, Method candidate, String methodName, Class<?>[] paramTypes) {
        final int score = getMethodMatchScore(candidate, methodName, paramTypes);
        if (score < best.score()) {
            return new MethodMatch(candidate, score);
        }
        if (score == best.score() && score != Integer.MAX_VALUE && isMoreSpecific(candidate, best.method())) {
            return new MethodMatch(candidate, score);
        }
        return best;
    }

    private record MethodMatch(@Nullable Method method, int score) {
        private static final MethodMatch NONE = new MethodMatch(null, Integer.MAX_VALUE);
    }

    /**
     * 同分候选的确定性兜底：优先选择参数类型更具体的方法，避免依赖 {@code getMethods()} 的返回顺序。
     */
    private static boolean isMoreSpecific(@Nullable Method candidate, @Nullable Method current) {
        if (candidate == null || current == null) {
            return false;
        }
        Class<?>[] candidateParams = candidate.getParameterTypes();
        Class<?>[] currentParams = current.getParameterTypes();
        if (candidateParams.length != currentParams.length) {
            return false;
        }
        boolean strictlyMoreSpecific = false;
        for (int i = 0; i < candidateParams.length; i++) {
            Class<?> candidateType = wrapPrimitiveType(candidateParams[i]);
            Class<?> currentType = wrapPrimitiveType(currentParams[i]);
            if (candidateType.equals(currentType)) {
                continue;
            }
            if (!currentType.isAssignableFrom(candidateType)) {
                return false;
            }
            strictlyMoreSpecific = true;
        }
        return strictlyMoreSpecific;
    }

    private static int getMethodMatchScore(Method method, String methodName, Class<?>[] paramTypes) {
        if (!method.getName().equals(methodName)) {
            return Integer.MAX_VALUE;
        }
        Class<?>[] methodParamTypes = method.getParameterTypes();
        if (!method.isVarArgs()) {
            if (methodParamTypes.length != paramTypes.length) {
                return Integer.MAX_VALUE;
            }
            return scoreParams(methodParamTypes, paramTypes, 0, paramTypes.length);
        }

        final int fixedCount = methodParamTypes.length - 1;
        if (paramTypes.length < fixedCount) {
            return Integer.MAX_VALUE;
        }
        int score = scoreParams(methodParamTypes, paramTypes, 0, fixedCount);
        if (score == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        final Class<?> componentType = methodParamTypes[fixedCount].getComponentType();
        for (int i = fixedCount; i < paramTypes.length; i++) {
            int paramScore = getParamMatchScore(componentType, paramTypes[i]);
            if (paramScore == Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            score += paramScore;
        }
        return score;
    }

    private static int scoreParams(Class<?>[] declaredTypes, Class<?>[] actualTypes, int from, int to) {
        int score = 0;
        for (int i = from; i < to; i++) {
            int paramScore = getParamMatchScore(declaredTypes[i], actualTypes[i]);
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
        if (wrappedDeclaredType.isAssignableFrom(wrappedActualType)) {
            return 8;
        }
        // Java 基本类型拓宽：Integer 实参可以匹配 long 形参
        return isWideningConversion(wrappedDeclaredType, wrappedActualType) ? 12 : Integer.MAX_VALUE;
    }

    private static boolean isWideningConversion(Class<?> declaredWrapper, Class<?> actualWrapper) {
        if (declaredWrapper == Boolean.class || actualWrapper == Boolean.class) {
            return false;
        }
        // char 作为源：可拓宽到 int/long/float/double（char 不在 numericRank 表内，需单独判断）
        if (actualWrapper == Character.class) {
            return numericRank(declaredWrapper) >= RANK_INT;
        }
        // char 作为目标：只接受 char，相等情形已在调用方提前返回
        if (declaredWrapper == Character.class) {
            return false;
        }
        final int declaredRank = numericRank(declaredWrapper);
        final int actualRank = numericRank(actualWrapper);
        return declaredRank >= 0 && actualRank >= 0 && declaredRank > actualRank;
    }

    private static int numericRank(Class<?> wrapper) {
        if (wrapper == Byte.class) {
            return RANK_BYTE;
        }
        if (wrapper == Short.class) {
            return RANK_SHORT;
        }
        if (wrapper == Integer.class) {
            return RANK_INT;
        }
        if (wrapper == Long.class) {
            return RANK_LONG;
        }
        if (wrapper == Float.class) {
            return RANK_FLOAT;
        }
        if (wrapper == Double.class) {
            return RANK_DOUBLE;
        }
        return -1;
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
            case GXBusinessException businessException -> throw businessException;
            case InvocationTargetException ite -> {
                throw unwrapInvocationTargetException(ite, object, methodName, params);
            }
            case UtilException utilException -> {
                Throwable cause = utilException.getCause();
                if (cause instanceof InvocationTargetException ite) {
                    throw unwrapInvocationTargetException(ite, object, methodName, params);
                }
                log.error("Unexpected reflection utility exception: {}", utilException.getMessage());
                throw utilException;
            }
            case SecurityException se -> {
                log.error("Reflection security exception: {}", se.getMessage());
                throw new GXBusinessException("Reflection security check failed: " + se.getMessage(), se);
            }
            case IllegalArgumentException iae -> {
                log.error("Reflection argument mismatch: {}", iae.getMessage());
                throw new GXBusinessException("Reflection argument mismatch: " + iae.getMessage(), iae);
            }
            default -> {
                log.error("Reflection call failed: {}", e.getMessage());
                throw new GXBusinessException("Reflection call failed: " + e.getMessage(), e);
            }
        }
    }

    private static RuntimeException unwrapInvocationTargetException(InvocationTargetException exception, Object object, String methodName, Object[] params) {
        Throwable targetException = exception.getTargetException();
        if (targetException instanceof GXBeanValidateException beanValidateException) {
            return beanValidateException;
        }
        if (targetException instanceof GXBusinessException businessException) {
            return businessException;
        }
        String exceptionMessage = CharSequenceUtil.isEmpty(targetException.getMessage())
                ? "Reflection invocation failed" : targetException.getMessage();
        log.error("Reflection invocation failed: target={}.{}, params={}, errorMessage={}, error={}",
                object.getClass().getSimpleName(), methodName, Arrays.toString(params),
                exceptionMessage, targetException);
        return new GXBusinessException(exceptionMessage, Optional.ofNullable(targetException.getCause()).orElse(targetException));
    }

    /**
     * 手机号合法性校验。
     *
     * @return <b>{@code true} 表示"不合法"</b>（空值也视为不合法），{@code false} 表示合法手机号
     */
    public static boolean checkPhone(@Nullable String phone) {
        if (CharSequenceUtil.isEmpty(phone)) {
            return true;
        }
        return !Validator.isMobile(phone);
    }

    /**
     * 固定电话合法性校验。
     *
     * @return <b>{@code true} 表示"不合法"</b>（空值也视为不合法），{@code false} 表示合法座机号
     */
    public static boolean checkTelephone(@Nullable String telephone) {
        if (CharSequenceUtil.isEmpty(telephone)) {
            return true;
        }
        String regex = "^(0\\d{2}-\\d{8}(-\\d{1,4})?)|(0\\d{3}-\\d{7,8}(-\\d{1,4})?)$";
        return !ReUtil.isMatch(regex, telephone);
    }

    /**
     * 构建树形结构。
     *
     * <p>一次遍历按父 id 分组，id 唯一时复杂度为 O(n)（每个节点恰好一次 {@code getParentId}
     * 与一次 {@code getId} 反射调用）。<b>id 重复时不是 O(n)</b>：k 个同 id 节点会各自遍历
     * 同一份 m 个子节点列表，最坏退化为 O(k·m)。
     * 支持通过 {@code getParentMethodName} 自定义父 id 取值方法，id 取值方法固定为 {@code getId}，
     * 子节点挂载方法固定为 {@code setChildren}；缺失 {@code setChildren} 时该层子节点不会被挂载（仅 debug 日志）。
     *
     * <p>父 id 指向不存在节点的孤儿节点会被丢弃；重复 id 或环形引用不会导致无限递归。
     *
     * @param rootParentValue 根节点的父 id 取值
     * @return 始终返回<b>可变</b>列表
     */
    public static <R> List<R> buildTree(@Nullable List<R> sourceList, Object rootParentValue) {
        return buildTree(sourceList, rootParentValue, null);
    }

    public static <R> List<R> buildTree(@Nullable List<R> sourceList, Object rootParentValue, @Nullable String getParentMethodName) {
        if (CollUtil.isEmpty(sourceList)) {
            return new ArrayList<>(0);
        }

        final String parentMethodName = CharSequenceUtil.isNotBlank(getParentMethodName)
                ? getParentMethodName : DEFAULT_PARENT_METHOD_NAME;

        final List<R> roots = new ArrayList<>();
        final Map<Object, List<R>> childrenByParentId = new LinkedHashMap<>();

        for (R item : sourceList) {
            if (Objects.isNull(item)) {
                continue;
            }
            Object parentId = reflectCallObjectMethod(item, parentMethodName);
            if (Objects.equals(parentId, rootParentValue)) {
                roots.add(item);
            } else {
                childrenByParentId.computeIfAbsent(parentId, key -> new ArrayList<>()).add(item);
            }
        }

        if (roots.isEmpty()) {
            return roots;
        }

        final Set<R> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (R root : roots) {
            attachChildren(root, childrenByParentId, visited);
        }

        if (visited.size() < sourceList.size()) {
            log.debug("Tree build dropped unreachable nodes: total={}, attached={}", sourceList.size(), visited.size());
        }
        return roots;
    }

    private static <R> void attachChildren(R parent, Map<Object, List<R>> childrenByParentId, Set<R> visited) {
        if (!visited.add(parent)) {
            return;
        }

        final Object id = reflectCallObjectMethod(parent, DEFAULT_ID_METHOD_NAME);
        if (Objects.isNull(id)) {
            return;
        }

        final List<R> candidates = childrenByParentId.get(id);
        if (CollUtil.isEmpty(candidates)) {
            return;
        }

        final List<R> children = new ArrayList<>(candidates.size());
        for (R candidate : candidates) {
            // parent 在方法入口已加入 visited，自引用会被 contains 覆盖
            if (visited.contains(candidate)) {
                log.debug("Skip cyclic or duplicated tree node: parentId={}, type={}", id, candidate.getClass().getSimpleName());
                continue;
            }
            children.add(candidate);
        }

        if (children.isEmpty()) {
            return;
        }

        reflectCallObjectMethod(parent, DEFAULT_CHILDREN_METHOD_NAME, children);
        for (R child : children) {
            attachChildren(child, childrenByParentId, visited);
        }
    }

    public static <R> @Nullable R decodeConnectStr(@Nullable String connectEncodeStr, Class<R> targetClazz) {
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
            GXLoggerUtils.logDebug(log, "Datasource secret key is empty, skip decrypting connection string");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        if (!isBase64(connectEncodeStr)) {
            GXLoggerUtils.logDebug(log, "Connection string is not valid Base64, use raw value");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        String decodedStr = GXAuthCodeUtils.authCodeDecode(connectEncodeStr, secretKey);

        if (CharSequenceUtil.equalsIgnoreCase(decodedStr, "{}")) {
            GXLoggerUtils.logDebug(log, "Failed to decode connection string, use raw value");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        decodedStr = HtmlUtil.unescape(decodedStr);

        return Convert.convert(targetClazz, decodedStr);
    }

    @SuppressWarnings("unchecked")
    public static <R> @Nullable Class<R> getGenericClassType(Class<?> clazz, Integer index) {
        if (clazz == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (index == null) {
            throw new IllegalArgumentException("Generic type index must not be null");
        }
        return (Class<R>) ClassUtil.getTypeArgument(clazz, index);
    }

    public static List<GXCondition<?>> convertTableConditionToConditionExp(String tableNameAlias, @Nullable Table<String, String, Object> condition) {
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

    /**
     * 字符串转目标类型，支持三种形态：{@code {k=v, k2=v2}} 的 map 风格、JSON 对象/数组、以及 Hutool 可直接转换的标量。
     *
     * <p>map 风格只剥离最外层花括号，并按<b>顶层</b>逗号切分：引号内与 {@code {}/[]/()} 嵌套内的逗号不切分。
     * 因此 {@code {name="a,b"}} 得到 {@code name=a,b}，而 {@code {name=a,b}} 会被切成两个条目
     * （{@code name=a} 与缺 {@code =} 的 {@code b}，后者被丢弃）——值里含逗号时必须加引号。
     *
     * @return 转换失败返回 {@code null}（仅 warn/debug 日志，不抛异常；原始值只在 debug 级别输出）
     */
    public static <T> @Nullable T convertStrToTarget(@Nullable String str, @Nullable Class<T> targetClazz) {
        if (CharSequenceUtil.isEmpty(str)) {
            return null;
        }
        if (targetClazz == null) {
            log.debug("Target type is null, skip string conversion");
            return null;
        }
        if (ReUtil.isMatch(MAP_STR_FORMAT_REGULAR, str)) {
            try {
                return Convert.convert(targetClazz, parseMapStyleString(str));
            } catch (Exception ex) {
                // 原始值可能含连接串/口令，只放在 debug
                log.warn("Failed to convert map-style string: error={}", ex.getMessage());
                log.debug("Map-style string conversion detail: rawValue={}", str);
                return null;
            }
        }

        if (JSONUtil.isTypeJSON(str)) {
            try {
                return JSONUtil.toBean(str, targetClazz);
            } catch (ConvertException ex) {
                log.warn("Failed to convert JSON string: error={}", ex.getMessage());
                log.debug("JSON string conversion detail: rawValue={}", str);
                return null;
            } catch (Exception ex) {
                log.warn("Unexpected JSON conversion failure: error={}", ex.getMessage());
                log.debug("JSON string conversion detail: rawValue={}", str);
                return null;
            }
        }

        try {
            return Convert.convert(targetClazz, str);
        } catch (Exception ex) {
            log.debug("Direct string conversion failed, rawValue={}", str);
            return null;
        }
    }

    private static Map<String, Object> parseMapStyleString(String str) {
        final String trimmed = str.trim();
        final String content = trimmed.substring(1, trimmed.length() - 1);
        final Map<String, Object> result = new HashMap<>(4);
        for (String entry : splitTopLevel(content, ',')) {
            final String trimmedEntry = entry.trim();
            if (trimmedEntry.isEmpty()) {
                continue;
            }
            final int separatorIndex = trimmedEntry.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            final String mapKey = trimmedEntry.substring(0, separatorIndex).trim();
            if (mapKey.isEmpty()) {
                continue;
            }
            result.put(mapKey, unquote(trimmedEntry.substring(separatorIndex + 1).trim()));
        }
        return result;
    }

    /**
     * 按分隔符切分，但忽略引号内与 {@code {}/[]/()} 嵌套内的分隔符。
     */
    private static List<String> splitTopLevel(String content, char separator) {
        final List<String> parts = new ArrayList<>(4);
        int depth = 0;
        int start = 0;
        char quote = 0;
        for (int i = 0; i < content.length(); i++) {
            final char current = content.charAt(i);
            if (quote != 0) {
                if (current == '\\') {
                    i++;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (current) {
                case '"', '\'' -> quote = current;
                case '{', '[', '(' -> depth++;
                case '}', ']', ')' -> depth--;
                default -> {
                    if (current == separator && depth == 0) {
                        parts.add(content.substring(start, i));
                        start = i + 1;
                    }
                }
            }
        }
        parts.add(content.substring(start));
        return parts;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            final char first = value.charAt(0);
            if ((first == '"' || first == '\'') && first == value.charAt(value.length() - 1)) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    public static boolean isBase64(@Nullable String base64Str) {
        if (CharSequenceUtil.isEmpty(base64Str)) {
            return false;
        }
        String value = base64Str.trim();
        return ReUtil.isMatch(BASE64_FORMAT_REGULAR, value)
                && Base64.isBase64(value);
    }

    public static boolean checkMethodExists(Class<?> targetClazz, String methodName, @Nullable Object... params) {
        if (targetClazz == null) {
            throw new IllegalArgumentException("Target class must not be null");
        }
        if (CharSequenceUtil.isEmpty(methodName)) {
            throw new IllegalArgumentException("Method name must not be null or empty");
        }
        final Object[] resolvedParams = params == null ? EMPTY_PARAMS : params;
        Method method = findMethod(targetClazz, methodName, resolveParamTypes(resolvedParams));
        return method != METHOD_NOT_FOUND;
    }

    /**
     * 探测 URL 是否可达。
     *
     * @return HTTP 状态码；参数为空或请求异常时返回 {@code -1}（哨兵值，与真实状态码混用需调用方自行区分）
     */
    public static Integer checkURLReachable(@Nullable String urlString) {
        if (CharSequenceUtil.isEmpty(urlString)) {
            log.debug("URL must not be empty");
            return -1;
        }

        try {
            HttpRequest request = HttpUtil.createRequest(cn.hutool.http.Method.GET, urlString);
            request.timeout(5000);
            try (HttpResponse response = request.execute()) {
                int responseCode = response.getStatus();

                if (responseCode == HttpStatus.HTTP_OK) {
                    log.debug("URL reachable: {}", urlString);
                    return HttpStatus.HTTP_OK;
                }
                log.debug("URL unreachable: statusCode={}, url={}", responseCode, urlString);
                return responseCode;
            }
        } catch (Exception e) {
            log.error("Failed to reach URL: url={}, error={}", urlString, e.getMessage(), e);
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
        return Base64Encoder.encode(hmacSha256(data, secret));
    }

    /**
     * 校验 HMAC 签名。
     *
     * <p>注意：签名计算本身失败（如 {@code ObjectMapper} 不可用）时返回 {@code false} 并记录 error 日志，
     * 不会抛异常——调用方若需要区分"签名不匹配"与"服务端配置异常"，应改为捕获异常语义。
     */
    public static boolean checkHmac(String secret, @Nullable String clientHmac, Object payload) {
        if (CharSequenceUtil.isBlank(secret)) {
            throw new GXBusinessException("Signing secret must not be blank");
        }
        if (CharSequenceUtil.isBlank(clientHmac)) {
            return false;
        }
        if (payload == null) {
            throw new GXBusinessException("Payload must not be null");
        }

        final String serverHmac;
        try {
            serverHmac = Base64Encoder.encode(hmacSha256(payload, secret));
        } catch (Exception e) {
            log.error("HMAC verification failed: {}", e.getMessage(), e);
            return false;
        }
        return MessageDigest.isEqual(
                serverHmac.getBytes(StandardCharsets.UTF_8),
                clientHmac.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static byte[] hmacSha256(Object data, String secret) {
        try {
            Mac mac = SecureUtil.createMac("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(toJson(data).getBytes(StandardCharsets.UTF_8));
        } catch (InvalidKeyException e) {
            throw new GXBusinessException("Invalid HMAC secret: " + e.getMessage(), e);
        }
    }

    private static String toJson(Object data) {
        ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
        if (objectMapper == null) {
            throw new GXBusinessException("ObjectMapper bean is unavailable");
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            throw new GXBusinessException("JSON serialization failed: " + e.getMessage(), e);
        }
    }

    public static List<GXUpdateField<?>> convertUpdateFieldRequestLst(@Nullable List<GXUpdateFieldRequest> updateLst) {
        if (CollUtil.isEmpty(updateLst)) {
            return new ArrayList<>(0);
        }

        List<GXUpdateField<?>> updateFields = new ArrayList<>(updateLst.size());
        for (GXUpdateFieldRequest updateField : updateLst) {
            updateFields.add(convertUpdateFieldRequest(updateField));
        }
        return updateFields;
    }

    private static GXUpdateField<?> convertUpdateFieldRequest(@Nullable GXUpdateFieldRequest updateField) {
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

            Class<?> updateFieldClass = resolveUpdateFieldClass(className);

            Object updateFieldObj = ReflectUtil.newInstance(updateFieldClass, tableName, fieldName, value);

            return (GXUpdateField<?>) updateFieldObj;
        } catch (GXBusinessException e) {
            throw e;
        } catch (Exception e) {
            String fieldName = updateField == null ? "unknown" : updateField.fieldName();
            throw new GXBusinessException("Failed to create update field: " + fieldName + ", reason: " + e.getMessage(), e);
        }
    }

    private static Class<?> resolveUpdateFieldClass(String className) {
        if (UPDATE_FIELD_CLASS_CACHE.size() > MAX_UPDATE_FIELD_CLASS_CACHE_SIZE) {
            UPDATE_FIELD_CLASS_CACHE.clear();
            log.debug("Update field class cache cleared: maxSize={}", MAX_UPDATE_FIELD_CLASS_CACHE_SIZE);
        }
        return UPDATE_FIELD_CLASS_CACHE.computeIfAbsent(className, name -> {
            try {
                Class<?> clazz = Class.forName(name, false, GXUpdateField.class.getClassLoader());
                if (!GXUpdateField.class.isAssignableFrom(clazz)) {
                    throw new GXBusinessException("Update field class must extend GXUpdateField: " + name);
                }
                return clazz;
            } catch (ClassNotFoundException e) {
                throw new GXBusinessException("Update field class not found: " + name, e);
            }
        });
    }

    /**
     * 获取系统负载。
     *
     * <p>优先返回归一化的系统平均负载（{@code loadAverage / processors}，约 0~1）；
     * 平台不支持平均负载时（如 Windows）退化为<b>当前进程</b>的 CPU 占用（同样约 0~1）。
     * 两种指标含义不同，仅用于告警展示，不应用于精确容量决策。
     *
     * @return 0~1 的负载值，无法获取时返回 {@code -1}
     */
    public static double getSystemLoadAverage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double loadAverage = osBean.getSystemLoadAverage();

            if (loadAverage < 0) {
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                    double cpuLoad = sunOsBean.getProcessCpuLoad();
                    // 首次调用可能返回负值或 NaN，统一视为"无法获取"
                    return (Double.isNaN(cpuLoad) || cpuLoad < 0) ? -1 : Math.min(cpuLoad, 1.0);
                }
                return -1;
            }
            int processors = osBean.getAvailableProcessors();
            if (processors <= 0) {
                // 无法归一化，返回未归一化的 loadAverage 会破坏 0~1 的契约
                log.debug("Invalid processor count: {}", processors);
                return -1;
            }

            return Math.min(loadAverage / processors, 1.0);
        } catch (Exception e) {
            log.error("Failed to get system load average: {}", e.getMessage());
            return -1;
        }
    }

    private static final class NullParam {
        private NullParam() {
        }
    }
}
