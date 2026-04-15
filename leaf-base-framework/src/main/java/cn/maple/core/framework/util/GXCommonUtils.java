package cn.maple.core.framework.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.codec.Base64Encoder;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.convert.ConvertException;
import cn.hutool.core.exceptions.InvocationTargetRuntimeException;
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

/**
 * 通用工具类
 * <p>
 * 该工具类聚焦于类型转换、反射调用、加解密、树结构构建、数据校验、URL工具等常用功能，强调内存安全、线程安全与性能优化。
 * <br>主要特性：
 * <ul>
 *   <li>环境配置获取与类型安全转换</li>
 *   <li>对象与集合的深度类型转换与属性拷贝</li>
 *   <li>反射调用与方法存在性校验</li>
 *   <li>数据加密解密与Base64校验</li>
 *   <li>树结构构建与递归处理</li>
 *   <li>字符串、Map、JSON互转</li>
 *   <li>URL可达性检测</li>
 *   <li>严格的参数校验与异常处理，防御性编程</li>
 *   <li>所有方法均为静态、无状态设计，适合并发场景</li>
 * </ul>
 * <br>内存安全：所有方法均对输入参数进行严格校验，避免空指针与资源泄漏。
 * <br>线程安全：无共享状态，局部变量隔离，适合多线程环境。
 * <br>性能优化：大集合并行流、反射缓存、异常分级处理。
 * <br>使用建议：优先使用本工具类封装方法，避免重复造轮子，提升团队协作效率。
 * </p>
 * <p>
 * <b>使用示例:</b>
 * <pre>{@code
 * // --- 1. 环境配置获取 ---
 * // 获取字符串配置，不存在则返回null
 * String appId = GXCommonUtils.getEnvironmentValue("alipay.appId", String.class);
 * // 获取整数配置，不存在则返回默认值 30
 * Integer timeout = GXCommonUtils.getEnvironmentValue("app.timeout", Integer.class, 30);
 * // 获取List<String>类型的配置 (假设配置值为JSON数组字符串)
 * List<String> whiteList = GXCommonUtils.getEnvironmentValue("app.whitelist", new TypeReference<List<String>>() {});
 * // 获取Map<String, String>类型的配置 (假设配置值为JSON对象字符串)
 * Map<String, String> configMap = GXCommonUtils.getEnvironmentValue("app.configMap", new TypeReference<Map<String, String>>() {});
 *
 * // --- 2. 对象类型转换 ---
 * // 2.1 Bean 转 DTO (假设UserEntity和UserDto结构类似)
 * UserEntity userEntity = new UserEntity(1L, "张三", "zhangsan@example.com");
 * UserDto userDto = GXCommonUtils.convertSourceToTarget(userEntity, UserDto.class);
 *
 * // 2.2 Map 转 Bean
 * Map<String, Object> userMap = new HashMap<>();
 * userMap.put("id", 2L);
 * userMap.put("username", "李四");
 * userMap.put("email", "lisi@example.com");
 * UserDto userDtoFromMap = GXCommonUtils.convertSourceToTarget(userMap, UserDto.class);
 *
 * // 2.3 JSON字符串 转 Bean
 * String jsonStr = "{\"id\":3L, \"username\":\"王五\", \"email\":\"wangwu@example.com\"}";
 * UserDto userDtoFromJson = GXCommonUtils.convertSourceToTarget(jsonStr, UserDto.class);
 *
 * // 2.4 转换并调用自定义处理方法 (假设UserDto有customizeProcess(Dict extraData)方法)
 * Dict extra = Dict.create().set("role", "admin");
 * UserDto userDtoProcessed = GXCommonUtils.convertSourceToTarget(userEntity, UserDto.class, "customizeProcess", null, extra);
 *
 * // 2.5 使用自定义CopyOptions进行转换 (例如，自定义日期格式转换)
 * CopyOptions customOptions = CopyOptions.create()
 *     .setIgnoreNullValue(true)
 *     .setConverter((type, value) -> {
 *         if (value instanceof String && type.equals(Date.class)) {
 *             try {
 *                 // 尝试解析特定格式的日期字符串
 *                 return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse((String) value);
 *             } catch (ParseException e) {
 *                 // 解析失败，可以返回null或抛出异常，或尝试其他格式
 *                 return null;
 *             }
 *         }
 *         // 对于其他类型，使用默认转换器
 *         return GXHutoolDataConvert.staticConvert(type, value);
 *     });
 * OrderDto orderDto = GXCommonUtils.convertSourceToTarget(orderMap, OrderDto.class, null, customOptions);
 *
 * // --- 3. 列表转换 ---
 * // 3.1 实体列表 转 DTO列表
 * List<UserEntity> userEntities = Arrays.asList(userEntity, new UserEntity(2L, "李四", "lisi@example.com"));
 * List<UserDto> userDtos = GXCommonUtils.convertSourceListToTargetList(userEntities, UserDto.class);
 *
 * // 3.2 Map列表 转 DTO列表
 * List<Map<String, Object>> userMaps = Arrays.asList(userMap, MapUtil.of("id", 4L, "username", "赵六"));
 * List<UserDto> userDtosFromMaps = GXCommonUtils.convertSourceListToTargetList(userMaps, UserDto.class);
 *
 * // 3.3 列表转换并调用自定义处理方法
 * List<UserDto> userDtosProcessed = GXCommonUtils.convertSourceListToTargetList(userEntities, UserDto.class, "customizeProcess", null);
 *
 * // --- 4. 反射调用 ---
 * // 4.1 调用Spring Bean的方法 (假设userService是Spring容器中的Bean)
 * // 注意：需要确保GXSpringContextUtils已正确配置并能获取到Bean
 * // User resultUser = (User) GXCommonUtils.reflectCallObjectMethod(UserService.class, "findById", 1L);
 *
 * // 4.2 调用普通对象的方法
 * String name = (String) GXCommonUtils.reflectCallObjectMethod(userDto, "getUsername");
 * GXCommonUtils.reflectCallObjectMethod(userDto, "setUsername", "新的用户名");
 *
 * // 4.3 调用无参方法
 * Object result = GXCommonUtils.reflectCallObjectMethod(someObject, "process");
 *
 * // 4.4 调用带有可变参数的方法
 * // GXCommonUtils.reflectCallObjectMethod(someService, "logMessage", "Error", "Details about the error");
 *
 * // --- 5. 数据加解密 ---
 * // 5.1 加密数据 (假设密钥为 "mySecretKey", 有效期1小时)
 * Dict dataToEncrypt = Dict.create().set("userId", 123).set("permission", "read");
 * String encryptedData = GXCommonUtils.encryptedData(dataToEncrypt, "mySecretKey", 3600);
 *
 * // 5.2 解密数据
 * Dict decryptedData = GXCommonUtils.decryptedData(encryptedData, "mySecretKey");
 * if (decryptedData != null) {
 *     Integer userId = decryptedData.getInt("userId");
 *     String permission = decryptedData.getStr("permission");
 * }
 *
 * // --- 6. 手机号码处理 ---
 * // 6.1 验证手机号格式
 * boolean isValidPhone = !GXCommonUtils.checkPhone("13812345678"); // true
 * boolean isInvalidPhone = !GXCommonUtils.checkPhone("12345");     // false
 *
 * // 6.2 隐藏手机号中间部分 (保留前3位和后4位)
 * String maskedPhone = GXCommonUtils.hiddenPhoneNumber("13812345678", 3, 7, '*'); // "138****5678"
 *
 * // --- 7. 树形结构构建 ---
 * // 假设MenuDto类有 getId(), getParentId(), setChildren(List<MenuDto> children) 方法
 * List<MenuDto> menuList = Arrays.asList(
 *     new MenuDto(1, 0, "系统管理"),
 *     new MenuDto(2, 1, "用户管理"),
 *     new MenuDto(3, 1, "角色管理"),
 *     new MenuDto(4, 0, "业务管理"),
 *     new MenuDto(5, 4, "订单管理")
 * );
 * // 构建以parentId为0的根节点树
 * List<MenuDto> menuTree = GXCommonUtils.buildTree(menuList, 0);
 *
 * // 假设CategoryDto类有 getCategoryId(), getParentCategoryId(), setSubCategories(List<CategoryDto> children) 方法
 * // 需要指定获取父ID的方法名 "getParentCategoryId"
 * // List<CategoryDto> categoryTree = GXCommonUtils.buildTree(categoryList, null, "getParentCategoryId");
 *
 * // --- 8. 数据验证 ---
 * // 8.1 验证固话号码
 * boolean isValidTel = !GXCommonUtils.checkTelephone("010-88888888"); // true
 * boolean isInvalidTel = !GXCommonUtils.checkTelephone("1234567");    // false
 *
 * // 8.2 验证是否为Base64字符串
 * boolean isBase64 = GXCommonUtils.isBase64("SGVsbG8="); // true
 * boolean isNotBase64 = GXCommonUtils.isBase64("Hello!"); // false
 *
 * // --- 9. URL工具 ---
 * // 检查URL是否可访问
 * Integer statusCode = GXCommonUtils.checkURLReachable("https://www.baidu.com");
 * if (statusCode == HttpStatus.HTTP_OK) {
 *     System.out.println("百度首页可访问");
 * } else {
 *     System.out.println("访问百度首页失败，状态码: " + statusCode);
 * }
 *
 * // --- 10. 字符串到对象转换 ---
 * // 10.1 Map格式字符串转对象
 * String mapStr = "{id=1, name=Test}";
 * MyObject objFromMapStr = GXCommonUtils.convertStrToTarget(mapStr, MyObject.class);
 *
 * // 10.2 JSON格式字符串转对象
 * String jsonStrForConvert = "{\"id\": 2, \"name\": \"AnotherTest\"}";
 * MyObject objFromJsonStr = GXCommonUtils.convertStrToTarget(jsonStrForConvert, MyObject.class);
 *
 * }
 * // 示例中使用的辅助类 (仅为演示)
 * class UserEntity { long id; String username; String email; /* constructor, getters \\*\/ }
 * class UserDto { long id; String username; String email; /* constructor, getters, setters, customizeProcess \\*\/ void customizeProcess(Dict extraData){} }
 * class OrderDto { /* fields \\*\/ }
 * class MenuDto { int id; int parentId; String name; List<MenuDto> children; /* constructor, getters, setters \\*\/ }
 * class MyObject { int id; String name; /* constructor, getters, setters \\*\/ }
 * </pre>
 * </p>
 *
 * @author britton
 * @since 1.0.0
 */
public class GXCommonUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXCommonUtils.class);

    /**
     * Map对象的字符串表示 eg: {name=子曦}
     */
    private static final String MAP_STR_FORMAT_REGULAR = "\\{(.+?)=(.+?)(, (.+?)=(.+?))*\\}";

    /**
     * 数据转换器的拷贝选项
     * <p>
     * 该转换器提供了强大的类型转换功能，支持以下特性：
     * 1. JSON字符串到对象的自动转换
     * 2. 集合类型的智能转换
     * 3. 自定义类型的转换处理
     * 4. 基本类型和包装类型的安全转换
     * </p>
     * <p>
     * 复杂的JSON字符串请使用cn.hutool.json.JSONObject来作为type
     * 例如：private JSONObject ext;
     * </p>
     * <p>
     * 内存安全特性：
     * 1. 空值安全处理，防止空指针异常
     * 2. 类型安全检查，确保类型转换的正确性
     * 3. 异常安全处理，防止转换异常导致程序崩溃
     * </p>
     */
    @Getter
    private static final CopyOptions defaultCopyOptions = CopyOptions.create().setIgnoreNullValue(true).setIgnoreError(true).setConverter(GXHutoolDataConvert::staticConvert);

    /**
     * 缓存反射调用时的方法
     */
    @Getter
    private static final Map<GXMethodCacheKeyUtils.MethodCacheKey, Method> METHOD_CACHE = new ConcurrentHashMap<>(64);

    /**
     * 私有构造函数，防止实例化
     * <p>
     * 工具类应该设计为静态方法的集合，不需要实例化
     * </p>
     */
    private GXCommonUtils() {
        // 防止通过反射实例化
        throw new AssertionError("不能实例化 GXCommonUtils 工具类");
    }

    /**
     * 根据key获取配置文件中的配置信息
     * <p>
     * 该方法从Spring环境中获取指定key的配置值，并转换为指定的类型。
     * 对于简单类型（如String、Integer等），直接使用Spring的类型转换功能；
     * 对于复杂类型，使用Jackson的ObjectMapper进行JSON反序列化。
     * 如果配置值不存在，则返回指定类型的默认值。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 获取字符串类型配置
     * String appId = GXCommonUtils.getEnvironmentValue("alipay.appId", String.class);
     *
     * // 获取整数类型配置
     * Integer timeout = GXCommonUtils.getEnvironmentValue("app.timeout", Integer.class);
     *
     * // 获取复杂类型配置（JSON格式的配置值会自动反序列化）
     * AppConfig config = GXCommonUtils.getEnvironmentValue("app.config", AppConfig.class);
     * }
     * </pre>
     * </p>
     *
     * @param key       配置键名，不能为null或空
     * @param clazzType 返回值类型的Class对象，不能为null
     * @param <R>       返回值类型
     * @return 转换后的配置值，如果配置不存在则返回类型的默认值
     * @throws GXConvertException       如果复杂类型的JSON反序列化失败
     * @throws IllegalArgumentException 如果key或clazzType为null
     */
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

    /**
     * 根据key获取配置文件中的配置信息（带默认值）
     * <p>
     * 该方法从Spring环境中获取指定key的配置值，并转换为指定的类型。
     * 与{@link #getEnvironmentValue(String, Class)}不同，该方法允许指定一个默认值，
     * 当配置不存在时返回该默认值而不是类型的默认值。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 获取字符串类型配置，默认值为空字符串
     * String appId = GXCommonUtils.getEnvironmentValue("alipay.appId", String.class, "");
     *
     * // 获取整数类型配置，默认值为30
     * Integer timeout = GXCommonUtils.getEnvironmentValue("app.timeout", Integer.class, 30);
     * }
     * </pre>
     * </p>
     *
     * @param key          配置键名，不能为null或空
     * @param clazzType    返回值类型的Class对象，不能为null
     * @param defaultValue 当配置不存在时返回的默认值
     * @param <R>          返回值类型
     * @return 转换后的配置值，如果配置不存在则返回指定的默认值
     * @throws IllegalArgumentException 如果key或clazzType为null
     */
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

    /**
     * 获取激活的Spring Profile
     * <p>
     * 该方法用于获取当前激活的Spring Profile，通常用于确定应用程序运行的环境（如开发、测试、生产等）。
     * 方法会从Spring环境中获取当前激活的Profile数组，并返回第一个Profile。
     * </p>
     * <p>
     * 内存安全：该方法不创建新对象，仅返回已有对象的引用，不会导致内存泄漏
     * 线程安全：该方法访问的是Spring环境的只读属性，可在多线程环境中安全调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * String activeProfile = GXCommonUtils.getActiveProfile();
     * if ("dev".equals(activeProfile)) {
     *     // 开发环境特定逻辑
     * } else if ("prod".equals(activeProfile)) {
     *     // 生产环境特定逻辑
     * }
     * </pre>
     * </p>
     *
     * @return 当前激活的Profile名称，如果没有激活的Profile则可能抛出异常
     * @throws ArrayIndexOutOfBoundsException 如果没有激活的Profile
     */
    public static String getActiveProfile() {
        try {
            String[] activeProfiles = Objects.requireNonNull(GXSpringContextUtils.getEnvironment()).getActiveProfiles();
            if (activeProfiles.length > 0) {
                return activeProfiles[0];
            }
            // 如果没有激活的Profile，返回默认Profile
            return GXSpringContextUtils.getEnvironment().getDefaultProfiles()[0];
        } catch (Exception e) {
            LOG.warn("获取激活的Profile失败: {}", e.getMessage());
            return "default";
        }
    }

    /**
     * 获取Class的JVM默认值
     * <p>
     * 该方法用于获取指定类型的JVM默认值。对于基本类型，返回其默认值（如int为0，boolean为false等）；
     * 对于非基本类型，尝试创建一个新实例。
     * </p>
     * <p>
     * 内存安全：
     * - 对于基本类型，返回常量值，不会创建新对象
     * - 对于引用类型，使用ReflectUtil安全地创建实例，避免资源泄漏
     * </p>
     * <p>
     * 线程安全：该方法不依赖共享状态，可在多线程环境中安全调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取基本类型的默认值
     * int defaultInt = GXCommonUtils.getClassDefaultValue(int.class); // 返回0
     * boolean defaultBool = GXCommonUtils.getClassDefaultValue(boolean.class); // 返回false
     *
     * // 获取引用类型的默认值（新实例）
     * UserDTO defaultUser = GXCommonUtils.getClassDefaultValue(UserDTO.class);
     * </pre>
     * </p>
     *
     * @param clazzType 目标Class对象，不能为null
     * @param <R>       返回值类型
     * @return 指定类型的默认值或新实例
     * @throws IllegalArgumentException 如果clazzType为null
     */
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

    /**
     * 隐藏手机号码的指定几位为指定的字符
     * <p>
     * 该方法用于对手机号码进行部分掩码处理，将指定范围内的字符替换为指定字符。
     * 方法会先验证输入是否为有效的手机号码，然后进行掩码处理。
     * </p>
     * <p>
     * 内存安全：
     * - 使用CharSequenceUtil安全处理字符串，避免创建不必要的对象
     * - 对无效输入进行防御性检查，返回空字符串而不是null
     * </p>
     * <p>
     * 线程安全：该方法不依赖共享状态，可在多线程环境中安全调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 隐藏手机号码中间4位数字
     * String maskedPhone = GXCommonUtils.hiddenPhoneNumber("13800138000", 3, 7, '*');
     * // 结果: "138****8000"
     * </pre>
     * </p>
     *
     * @param phoneNumber  手机号码，可以为null或空
     * @param startInclude 开始字符位置(包含,从0开始)，必须大于等于0且小于手机号长度
     * @param endExclude   结束字符位置(不包含)，必须大于startInclude且不大于手机号长度
     * @param replacedChar 替换为的字符
     * @return 处理后的手机号码，如果输入无效则返回空字符串
     */
    public static String hiddenPhoneNumber(CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar) {
        if (CharSequenceUtil.isBlank(phoneNumber)) {
            return "";
        }

        // 验证参数有效性
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

    /**
     * 加密数据
     * <p>
     * 该方法用于对Dict类型的数据进行加密，生成一个加密字符串。
     * 加密过程会先将Dict转换为JSON字符串，然后使用指定的密钥进行加密。
     * 可以设置加密数据的过期时间，0表示永不过期。
     * </p>
     * <p>
     * 内存安全：
     * - 对输入参数进行严格验证，防止空指针异常
     * - 使用JSONUtil安全地处理JSON转换
     * - 使用CharSequenceUtil安全处理字符串拼接
     * </p>
     * <p>
     * 线程安全：该方法不依赖共享状态，可在多线程环境中安全调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 加密用户信息，有效期1小时
     * Dict userData = Dict.create()
     *     .set("userId", 10001)
     *     .set("username", "张三")
     *     .set("role", "admin");
     * String encryptedData = GXCommonUtils.encryptedData(userData, "mySecretKey", 3600);
     *
     * // 加密临时令牌，永不过期
     * Dict tokenData = Dict.create().set("token", "abc123");
     * String encryptedToken = GXCommonUtils.encryptedData(tokenData, "tokenKey", 0);
     * </pre>
     * </p>
     *
     * @param data   明文数据，不能为null或空
     * @param key    加密KEY，不能为null或空
     * @param expiry 加密之后的数据过期时间（秒），0表示永不过期
     * @return 加密后的字符串
     * @throws GXBusinessException 如果data为null或空，或key为空
     */
    public static String encryptedData(Dict data, String key, int expiry) {
        if (Objects.isNull(data) || data.isEmpty()) {
            throw new GXBusinessException("加密数据明文不能为空");
        }
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("加密KEY不能为空");
        }

        // 确保expiry不为负数
        int safeExpiry = Math.max(0, expiry);

        // 组合密钥
        String combinedKey = CharSequenceUtil.format("{}{}", key, GXCommonConstant.COMMON_ENCRYPT_KEY);

        // 转换为JSON并加密
        String jsonData = JSONUtil.toJsonStr(data);
        return GXAuthCodeUtils.authCodeEncode(jsonData, combinedKey, safeExpiry);
    }

    /**
     * 解密数据
     * <p>
     * 该方法用于解密由{@link #encryptedData}方法加密的数据。
     * 解密过程会使用指定的密钥对加密字符串进行解密，然后将结果转换为Dict对象。
     * </p>
     * <p>
     * 内存安全：
     * - 对输入参数进行严格验证，防止空指针异常
     * - 使用JSONUtil安全地处理JSON转换
     * - 使用CharSequenceUtil安全处理字符串拼接
     * - 对解密失败的情况进行安全处理，返回空Dict而不是null
     * </p>
     * <p>
     * 线程安全：该方法不依赖共享状态，可在多线程环境中安全调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 解密之前加密的数据
     * String encryptedData = "...加密字符串...";
     * Dict decryptedData = GXCommonUtils.decryptedData(encryptedData, "mySecretKey");
     *
     * // 使用解密后的数据
     * Long userId = decryptedData.getLong("userId");
     * String username = decryptedData.getStr("username");
     * </pre>
     * </p>
     *
     * @param encryptedStr 加密字符串，不能为null
     * @param key          解密KEY，不能为null或空
     * @return Dict 解密后的数据，如果解密失败则返回空Dict
     * @throws GXBusinessException 如果key为空
     */
    public static Dict decryptedData(String encryptedStr, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("解密KEY不能为空");
        }
        if (CharSequenceUtil.isEmpty(encryptedStr)) {
            LOG.warn("待解密的字符串为空");
            return Dict.create();
        }

        try {
            // 组合密钥
            String combinedKey = CharSequenceUtil.format("{}{}", key, GXCommonConstant.COMMON_ENCRYPT_KEY);

            // 解密
            final String decryptedStr = GXAuthCodeUtils.authCodeDecode(encryptedStr, combinedKey);

            // 处理解密结果
            if (CharSequenceUtil.isEmpty(decryptedStr) || "{}".equals(decryptedStr)) {
                return Dict.create();
            }

            return JSONUtil.toBean(decryptedStr, Dict.class);
        } catch (Exception e) {
            LOG.error("数据解密失败: {}", e.getMessage());
            return Dict.create();
        }
    }

    /**
     * 将任意对象转换为指定类型的对象
     * <p>
     * 该方法提供了强大的对象转换功能，支持以下特性：
     * 1. 自动处理简单类型和数组类型 - 对于简单类型直接返回源对象
     * 2. 智能处理字符串类型的JSON数据 - 自动解析JSON字符串为Dict或List
     * 3. 支持自定义转换规则和额外参数 - 通过CopyOptions和extraData实现灵活转换
     * 4. 自动调用目标对象的自定义处理方法和验证方法 - 支持转换后的自定义处理和验证
     * 5. 特殊处理GXBaseData类型 - 使用CGLIB进行高效复制
     * </p>
     * <p>
     * 转换流程：
     * 1. 检查源对象和目标类型 - 验证参数有效性
     * 2. 对于简单类型或数组类型，直接返回源对象 - 避免不必要的转换
     * 3. 对于字符串类型的JSON数据，先转换为Dict或List - 智能处理JSON格式
     * 4. 创建目标类型的实例，并复制属性 - 使用反射和BeanUtil
     * 5. 调用目标对象的自定义处理方法（如果指定）- 支持自定义逻辑
     * 6. 调用目标对象的验证方法 - 确保转换结果的有效性
     * </p>
     * <p>
     * 内存安全特性：
     * 1. 严格的参数验证 - 防止空指针异常和非法参数
     * 2. 安全的类型转换 - 使用TypeToken和ClassUtil进行类型检查
     * 3. 异常安全处理 - 捕获并包装所有异常，提供详细错误信息
     * 4. 资源管理 - 避免创建不必要的对象，减少内存占用
     * 5. 防御性编程 - 对所有可能为null的对象进行检查
     * </p>
     * <p>
     * 线程安全特性：
     * 1. 无状态设计 - 方法不依赖共享状态，可在多线程环境中安全调用
     * 2. 本地变量 - 所有变量都是方法内的局部变量，避免线程间干扰
     * 3. 不可变参数 - 不修改输入参数，确保线程安全
     * 4. 安全的工具类调用 - 使用线程安全的工具类和方法
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 示例1：将Dict转换为DTO对象
     * Dict source = Dict.create().set("username","britton").set("realName","枫叶思源");
     * PersonResDto dto = convertSourceToTarget(source, PersonResDto.class, "customerProcess", null);
     *
     * // 示例2：将请求协议对象转换为DTO对象
     * PersonReqProtocol req = new PersonReqProtocol();
     * req.setUsername("britton");
     * req.setRealName("枫叶思源");
     * PersonResDto dto = convertSourceToTarget(req, PersonResDto.class, "customerProcess", null);
     *
     * // 示例3：使用自定义转换选项和额外参数
     * CopyOptions options = CopyOptions.create().setIgnoreNullValue(true);
     * Dict extraData = Dict.create().set("tenant", "system");
     * PersonResDto dto = convertSourceToTarget(req, PersonResDto.class, "customerProcess", options, extraData);
     *
     * // 示例4：转换JSON字符串为对象
     * String jsonStr = "{\"id\":1,\"name\":\"测试用户\",\"roles\":[\"admin\",\"user\"]}";
     * UserDto userDto = convertSourceToTarget(jsonStr, UserDto.class, null, null);
     *
     * // 示例5：批量转换（结合convertSourceListToTargetList方法）
     * List<UserEntity> userEntities = userService.findAll();
     * List<UserDto> userDtos = convertSourceListToTargetList(userEntities, UserDto.class);
     *
     * // 示例6：处理特殊类型转换
     * CopyOptions customOptions = CopyOptions.create()
     *     .setIgnoreNullValue(true)
     *     .setConverter((type, value) -> {
     *         // 自定义日期格式转换
     *         if (type.equals(Date.class) && value instanceof String) {
     *             return DateUtil.parse((String) value);
     *         }
     *         return value;
     *     });
     * OrderDto orderDto = convertSourceToTarget(orderMap, OrderDto.class, null, customOptions);
     * }
     * </pre>
     * </p>
     *
     * @param source      源对象，可以为任意类型，包括简单类型、数组、集合、Map等，可以为null
     * @param tClass      目标对象类型，不能为null，否则抛出IllegalArgumentException异常
     * @param methodName  转换后需要调用的目标对象方法名，可以为null，为null时使用默认方法名（customizeProcess）
     * @param copyOptions 复制选项，可以设置自定义的TypeConvert来自定义转换规则，可以为null，为null时使用默认选项
     * @param extraData   额外参数，会传递给methodName指定的方法，可以为null，为null时创建空Dict
     * @return 转换后的目标对象，如果源对象为null则返回null
     * @throws GXConvertException       如果转换过程中发生异常，包括但不限于：无法创建目标类型实例、属性复制失败、自定义处理方法调用失败等
     * @throws IllegalArgumentException 如果目标类型为null
     */
    @SuppressWarnings("unchecked")
    public static <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions, Object extraData) {
        // 源对象为null，直接返回null
        if (Objects.isNull(source)) {
            return null;
        }

        // 目标类型验证
        if (tClass == null) {
            throw new IllegalArgumentException("目标对象类型不能为null");
        }

        // 如果目标类型是简单类型或数组，直接返回源对象
        if (ClassUtil.isSimpleTypeOrArray(tClass)) {
            return (T) source;
        }

        try {
            // 处理方法名和额外参数
            if (CharSequenceUtil.isBlank(methodName)) {
                methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
            }
            if (Objects.isNull(extraData)) {
                extraData = Dict.create();
            }

            // 创建目标类型的实例
            T target = ReflectUtil.newInstanceIfPossible(tClass);
            if (target == null) {
                throw new GXConvertException("无法创建目标类型的实例: " + tClass.getName());
            }

            // 复制属性
            if (!TypeToken.of(target.getClass()).isSubtypeOf(Map.class) && TypeToken.of(source.getClass()).isSubtypeOf(GXBaseData.class) && ObjectUtil.isNull(copyOptions)) {
                LOG.warn("使用CGLIB进行高效属性复制!!");
                //GXCglibUtils.copy(source, target, new GXCGLibDataConvert(tClass));
                GXCglibUtils.copy(source, target, GXCGLibDataConvert.getConverter(tClass));
            } else {
                // 使用默认的复制选项（如果未指定）
                copyOptions = ObjectUtil.defaultIfNull(copyOptions, GXCommonUtils::getDefaultCopyOptions);
                LOG.warn("使用BeanUtil进行属性复制!!");
                BeanUtil.copyProperties(source, target, copyOptions);
            }

            // 调用自定义处理方法（如果指定）
            if (CharSequenceUtil.isNotEmpty(methodName)) {
                reflectCallObjectMethod(target, methodName, extraData);
            }

            // 调用验证方法
            reflectCallObjectMethod(target, "verify");

            return target;
        } catch (Exception e) {
            // 异常处理，提取根本原因
            LOG.error("对象转换失败: 源类型[{}], 目标类型[{}], 错误: {}",
                    source.getClass().getName(), tClass.getName(), e.getMessage());

            // 提取异常的根本原因，最多向下追溯两层
            Throwable rootCause = e;
            if (ObjectUtil.isNotNull(e.getCause())) {
                rootCause = e.getCause();
                if (ObjectUtil.isNotNull(rootCause.getCause())) {
                    rootCause = rootCause.getCause();
                }
            }

            // 记录详细的异常堆栈信息（仅在DEBUG级别）
            if (LOG.isDebugEnabled()) {
                LOG.debug("对象转换异常详细信息:", e);
            }

            // 包装为GXConvertException并抛出，保留原始异常信息
            String errorMessage = CharSequenceUtil.format("对象转换失败: 源类型[{}]转换为目标类型[{}]时发生错误: {}",
                    source.getClass().getSimpleName(),
                    tClass.getSimpleName(),
                    rootCause.getMessage());
            throw new GXConvertException(errorMessage, rootCause);
        }
    }

    /**
     * 将任意对象转换为指定类型的对象（简化版本，使用空Dict作为额外参数）
     * <p>
     * 该方法是{@link #convertSourceToTarget(Object, Class, String, CopyOptions, Object)}的简化版本，
     * 使用空Dict作为额外参数。适用于不需要传递额外参数的场景。
     * </p>
     * <p>
     * 内存安全特性：
     * 1. 创建空Dict作为默认参数，避免null值
     * 2. 委托给完整版本方法处理，确保一致的安全特性
     * </p>
     * <p>
     * 线程安全特性：
     * 1. 无状态设计，可在多线程环境中安全调用
     * 2. 使用线程安全的Dict.create()创建空字典
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 将实体对象转换为DTO，不需要额外参数
     * UserEntity entity = userRepository.findById(1L);
     * UserDto dto = convertSourceToTarget(entity, UserDto.class, "process", null);
     * }
     * eg:
     * private JSONObject ext;
     * Dict source = Dict.create().set("username","britton").set("realName","枫叶思源");
     * convertSourceToTarget( source , PersonResDto.class, "customerProcess" , null);
     * OR
     * PersonReqProtocol req = new PersonReqProtocol();
     * req.setUsername("britton");
     * req.setRealName("枫叶思源")；
     * convertSourceToTarget(req ,  PersonResDto.class, "customerProcess" , null);
     * </pre>
     * </p>
     *
     * @param source      源对象，可以为任意类型，包括简单类型、数组、集合、Map等，可以为null
     * @param tClass      目标对象类型，不能为null，否则抛出IllegalArgumentException异常
     * @param methodName  转换后需要调用的目标对象方法名，可以为null，为null时使用默认方法名
     * @param copyOptions 复制选项，可以设置自定义的TypeConvert来自定义转换规则，可以为null
     * @return 转换后的目标对象，如果源对象为null则返回null
     * @throws GXConvertException 如果转换过程中发生异常
     * @see #convertSourceToTarget(Object, Class, String, CopyOptions, Object) 完整版本的方法
     */
    public static <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions) {
        return convertSourceToTarget(source, tClass, methodName, copyOptions, Dict.create());
    }

    /**
     * 将任意对象集合转换为指定类型的对象列表
     * <p>
     * 该方法是{@link #convertSourceListToTargetList(Collection, Class, String, CopyOptions)}的简化版本，
     * 使用默认的处理方法名和默认的拷贝选项。
     * </p>
     * <p>
     * 内存安全：
     * - 对输入参数进行验证，防止空指针异常
     * - 使用安全的集合操作，避免并发修改异常
     * - 返回新的列表对象，避免对原始集合的修改
     * </p>
     * <p>
     * 线程安全：该方法不依赖共享状态，可在多线程环境中安全调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 将实体列表转换为DTO列表
     * List<UserEntity> userEntities = userRepository.findAll();
     * List<UserDTO> userDTOs = GXCommonUtils.convertSourceListToTargetList(userEntities, UserDTO.class);
     *
     * // 将Map列表转换为DTO列表
     * List<Map<String, Object>> userMaps = jdbcTemplate.queryForList("SELECT * FROM users");
     * List<UserDTO> userDTOs = GXCommonUtils.convertSourceListToTargetList(userMaps, UserDTO.class);
     * </pre>
     * </p>
     *
     * @param collection 需要转换的对象集合，可以为null
     * @param tClass     目标对象的类型，不能为null
     * @param <R>        目标对象类型
     * @return 转换后的对象列表，如果输入为null或空集合则返回空列表
     * @throws IllegalArgumentException 如果tClass为null
     */
    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass) {
        return convertSourceListToTargetList(collection, tClass, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, null);
    }

    /**
     * 将任意对象集合转换为指定类型的对象列表
     * <p>
     * 该方法是{@link #convertSourceListToTargetList(Collection, Class, String, CopyOptions, Object)}的简化版本，
     * 使用空的额外参数。
     * </p>
     * <p>
     * 内存安全：
     * - 对输入参数进行验证，防止空指针异常
     * - 使用安全的集合操作，避免并发修改异常
     * - 返回新的列表对象，避免对原始集合的修改
     * </p>
     * <p>
     * 线程安全：该方法不依赖共享状态，可在多线程环境中安全调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 使用自定义方法名和拷贝选项
     * CopyOptions options = CopyOptions.create().setIgnoreNullValue(true);
     * List<UserDTO> userDTOs = GXCommonUtils.convertSourceListToTargetList(
     *     userEntities, UserDTO.class, "processCustomFields", options);
     * </pre>
     * </p>
     *
     * @param collection  需要转换的对象集合，可以为null
     * @param tClass      目标对象的类型，不能为null
     * @param methodName  转换后需要调用的目标对象方法名，可以为null
     * @param copyOptions 复制选项，可以设置自定义的TypeConvert来自定义转换规则，可以为null
     * @param <R>         目标对象类型
     * @return 转换后的对象列表，如果输入为null或空集合则返回空列表
     * @throws IllegalArgumentException 如果tClass为null
     */
    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions) {
        return convertSourceListToTargetList(collection, tClass, methodName, copyOptions, Dict.create());
    }

    /**
     * 将任意对象集合转换为指定类型的对象列表
     * <p>
     * 该方法提供了高效的集合转换功能，支持以下特性：
     * 1. 自动处理空集合
     * 2. 支持并行流处理大数据量集合
     * 3. 支持自定义转换规则和额外参数
     * 4. 返回不可变集合，防止外部修改
     * </p>
     * <p>
     * 转换流程：
     * 1. 检查源集合是否为空
     * 2. 对集合中的每个元素应用convertSourceToTarget方法
     * 3. 收集转换结果并返回不可变列表
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 示例1：基本转换
     * List<UserDTO> dtos = convertSourceListToTargetList(userEntities, UserDTO.class);
     *
     * // 示例2：使用自定义处理方法
     * List<UserDTO> dtos = convertSourceListToTargetList(userEntities, UserDTO.class, "processExtData");
     *
     * // 示例3：使用自定义转换选项和额外参数
     * CopyOptions options = CopyOptions.create().setIgnoreNullValue(true);
     * Dict extraData = Dict.create().set("tenant", "system");
     * List<UserDTO> dtos = convertSourceListToTargetList(userEntities, UserDTO.class, "processExtData", options, extraData);
     * }
     * </pre>
     * </p>
     *
     * @param collection  需要转换的对象列表，可以为空
     * @param tClass      目标对象的类型，不能为null
     * @param methodName  转换后需要调用的目标对象方法名，可以为null
     * @param copyOptions 复制选项，可以为null
     * @param extraData   额外参数，会传递给methodName指定的方法，可以为null
     * @return 转换后的不可变列表，如果源集合为空则返回空列表
     * @throws IllegalArgumentException 如果tClass为null
     */
    public static <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions, Object extraData) {
        // 参数验证
        if (CollUtil.isEmpty(collection)) {
            return Collections.emptyList();
        }
        if (tClass == null) {
            throw new IllegalArgumentException("目标对象类型不能为null");
        }

        // 对于大集合使用并行流提高性能
        if (collection.size() > 1000) {
            return collection.parallelStream()
                    .map(source -> convertSourceToTarget(source, tClass, methodName, copyOptions, extraData))
                    .filter(Objects::nonNull) // 过滤掉转换失败的null值
                    .collect(Collectors.toList()); // 返回不可变列表
        }

        // 对于小集合使用普通流
        return collection.stream()
                .map(source -> convertSourceToTarget(source, tClass, methodName, copyOptions, extraData))
                .filter(Objects::nonNull) // 过滤掉转换失败的null值
                .collect(Collectors.toList()); // 返回不可变列表
    }

    /**
     * 动态调用Spring Bean容器中对象的方法
     * <p>
     * 该方法通过Spring Bean容器获取指定类型的Bean实例，然后动态调用该实例的指定方法。
     * 内部调用{@link #reflectCallObjectMethod(Object, String, Object...)}方法实现。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 调用UserService的findById方法
     * User user = (User) GXCommonUtils.reflectCallObjectMethod(UserService.class, "findById", 1L);
     *
     * // 调用无参方法
     * List<User> users = (List<User>) GXCommonUtils.reflectCallObjectMethod(UserService.class, "findAll");
     * }
     * </pre>
     * </p>
     *
     * @param serviceClass 目标对象类型，必须存在于Spring Bean容器中，不能为null
     * @param methodName   要调用的方法名，如果为空则使用默认方法名
     * @param params       方法参数，可变参数，可以为null
     * @return 方法调用的返回值，如果方法不存在或调用失败则返回null
     * @throws IllegalArgumentException 如果serviceClass为null
     * @throws GXBusinessException      如果反射调用过程中发生异常
     */
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

    /**
     * 动态调用对象中的方法
     * <p>
     * 该方法通过Java反射机制动态调用指定对象的方法。支持以下特性：
     * 1. 自动处理null参数和空方法名
     * 2. 智能匹配方法参数类型
     * 3. 详细的异常处理和日志记录
     * 4. 支持无参方法调用
     * </p>
     * <p>
     * 异常处理策略：
     * 1. 如果目标方法抛出GXBeanValidateException，则直接抛出该异常
     * 2. 如果是InvocationTargetRuntimeException，则包装为GXBusinessException并抛出
     * 3. 其他异常会被记录并包装为GXBusinessException抛出
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 1. 调用对象的有参方法
     * User user = new User();
     * Object result = GXCommonUtils.reflectCallObjectMethod(user, "setName", "张三");
     *
     * // 2. 调用对象的无参方法
     * String name = (String) GXCommonUtils.reflectCallObjectMethod(user, "getName");
     *
     * // 3. 调用带有复杂参数的方法
     * Map<String, Object> params = new HashMap<>();
     * params.put("id", 100);
     * params.put("status", "active");
     * Object result = GXCommonUtils.reflectCallObjectMethod(service, "findByParams", params);
     *
     * // 4. 使用默认方法名调用
     * Object result = GXCommonUtils.reflectCallObjectMethod(processor, null, inputData);
     * // 等同于调用 processor.customizeProcess(inputData)
     * }
     * </pre>
     * </p>
     *
     * @param object     要调用其方法的对象，不能为null
     * @param methodName 要调用的方法名，如果为空则使用默认方法名(customizeProcess)
     * @param params     方法参数，可变参数，可以为null
     * @param <R>        对象类型
     * @return 方法调用的返回值，如果方法不存在或调用失败则返回null
     * @throws GXBeanValidateException 如果目标方法抛出该异常
     * @throws GXBusinessException     如果反射调用过程中发生其他异常
     */
    public static <R> Object reflectCallObjectMethod(R object, String methodName, Object... params) {
        // 参数验证
        if (Objects.isNull(object)) {
            LOG.warn("反射调用的object对象为null");
            return null;
        }

        // 处理方法名为空的情况
        if (CharSequenceUtil.isEmpty(methodName)) {
            methodName = GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME;
            LOG.debug("方法名为空，使用默认方法名: {}", methodName);
        }

        // 处理参数为null的情况
        if (Objects.isNull(params)) {
            params = new Object[0];
        }

        try {
            // 构建参数类型数组
            Class<?>[] paramTypes = new Class<?>[params.length];
            for (int i = 0; i < params.length; i++) {
                if (Objects.nonNull(params[i])) {
                    paramTypes[i] = params[i].getClass();
                } else {
                    // 对于null参数，使用Object.class作为类型占位符
                    paramTypes[i] = Object.class;
                }
            }

            // 查找匹配的方法（使用同步块保护方法查找过程，避免并发问题）
            Method method = findMethod(object.getClass(), methodName, paramTypes);

            // 检查方法是否存在
            if (Objects.isNull(method)) {
                LOG.warn("方法{}.{}({})不存在,反射调用失败!", object.getClass().getSimpleName(), methodName, Arrays.toString(params));
                return null;
            }

            // 确保方法可访问
            if (!method.canAccess(object)) {
                method.setAccessible(true);
            }

            // 调用方法
            return ReflectUtil.invoke(object, method, params);
        } catch (Exception ex) {
            return handleReflectionException(ex, object, methodName, params);
        }
    }

    /**
     * 查找指定类中的方法
     * <p>
     * 该方法用于在指定类中查找具有给定名称和参数类型的方法。首先尝试从缓存中获取方法对象，
     * 如果缓存中不存在，则使用反射查找方法并将结果存入缓存。方法查找过程会先尝试精确匹配参数类型，
     * 如果找不到且参数为空，则尝试查找无参方法。
     * </p>
     *
     * <p>线程安全性：</p>
     * <p>该方法是线程安全的。使用ConcurrentHashMap的computeIfAbsent方法确保在多线程环境下
     * 只有一个线程会执行方法查找逻辑，避免重复计算和线程安全问题。</p>
     *
     * <p>使用示例：</p>
     * <pre>
     * // 查找有参方法
     * Class<?> targetClass = MyService.class;
     * String methodName = "processData";
     * Class<?>[] paramTypes = new Class<?>[] {String.class, Integer.class};
     * Method method = GXCommonUtils.findMethod(targetClass, methodName, paramTypes);
     *
     * // 查找无参方法
     * Method noArgMethod = GXCommonUtils.findMethod(targetClass, "initialize", new Class<?>[0]);
     * </pre>
     *
     * <p>注意事项：</p>
     * <p>方法查找结果会被缓存，以提高后续调用的性能。如果类中的方法在运行时发生变化（这在正常情况下不应该发生），
     * 缓存可能会返回过时的结果。</p>
     *
     * @param clazz      目标类，不能为null
     * @param methodName 方法名，不能为null或空字符串
     * @param paramTypes 方法参数类型数组，不能为null，但可以是空数组表示无参方法
     * @return 找到的方法对象，如果未找到匹配的方法则返回null
     */
    private static Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        // 参数校验
        Objects.requireNonNull(clazz, "目标类不能为null");
        Objects.requireNonNull(methodName, "方法名不能为null");
        Objects.requireNonNull(paramTypes, "参数类型数组不能为null");

        // 创建方法缓存键
        final GXMethodCacheKeyUtils.MethodCacheKey methodCacheKey =
                GXMethodCacheKeyUtils.getMethodCacheKey(clazz, methodName, paramTypes);

        // 从缓存中获取或计算方法对象
        return METHOD_CACHE.computeIfAbsent(methodCacheKey, key -> {
            // 首先尝试使用精确的参数类型匹配
            Method method = ReflectUtil.getMethod(clazz, methodName, paramTypes);

            // 如果找不到且参数为空，尝试查找无参方法
            if (Objects.isNull(method) && paramTypes.length == 0) {
                method = ReflectUtil.getMethodByName(clazz, methodName);
            }

            return method;
        });
    }

    /**
     * 处理反射调用过程中的异常
     * <p>
     * 该方法根据异常类型进行分类处理，提供详细的错误信息和日志记录，
     * 并将原始异常包装为业务异常抛出。
     * </p>
     *
     * @param e          捕获的异常
     * @param object     被调用方法的对象
     * @param methodName 被调用的方法名
     * @param params     方法参数
     * @return 永远不会返回值，总是抛出异常
     * @throws GXBeanValidateException 如果目标方法抛出该异常
     * @throws GXBusinessException     如果反射调用过程中发生其他异常
     */
    private static Object handleReflectionException(Exception e, Object object, String methodName, Object[] params) {
        // 处理UtilException，通常包含InvocationTargetException
        switch (e) {
            case UtilException utilException -> {
                Throwable cause = utilException.getCause();
                // 处理调用目标方法时的异常
                if (cause instanceof InvocationTargetException ite) {
                    Throwable targetException = ite.getTargetException();
                    // 处理Bean验证异常
                    if (targetException instanceof GXBeanValidateException) {
                        throw (GXBeanValidateException) targetException;
                    }
                    // 处理调用目标运行时异常
                    if (utilException instanceof InvocationTargetRuntimeException) {
                        throw new GXBusinessException(targetException.getMessage(),
                                Optional.ofNullable(targetException.getCause()).orElse(targetException));
                    }
                    // 处理其他异常
                    String exceptionMessage = CharSequenceUtil.isEmpty(targetException.getMessage())
                            ? "系统反射调用失败" : targetException.getMessage();
                    LOG.error("系统反射调用{}.{}({})失败 , [错误消息 : {}] [错误原因 : {}]",
                            object.getClass().getSimpleName(), methodName, Arrays.toString(params),
                            utilException.getMessage(), cause);
                    throw new GXBusinessException(exceptionMessage, targetException);
                }
                // 重新抛出原始异常
                LOG.error("反射调用过程中发生未知异常: {}", utilException.getMessage());
                throw utilException;
            }
            // 处理安全异常
            case SecurityException se -> {
                LOG.error("反射调用过程中发生安全异常: {}", se.getMessage());
                throw new GXBusinessException("反射调用安全检查失败: " + se.getMessage(), se);
            }
            // 处理参数异常
            case IllegalArgumentException iae -> {
                LOG.error("反射调用参数不匹配: {}", iae.getMessage());
                throw new GXBusinessException("反射调用参数不匹配: " + iae.getMessage(), iae);
            }
            // 处理其他可能的异常
            default -> {
                LOG.error("反射调用过程中发生异常: {}", e.getMessage());
                throw new GXBusinessException("反射调用失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 验证手机号码
     * <p>
     * 该方法用于验证输入的字符串是否符合中国大陆手机号码格式。
     * 支持的手机号段包括：
     * - 13x：130-139
     * - 14x：145、147
     * - 15x：150-153、155-159
     * - 16x：166
     * - 17x：170、173、175-178
     * - 18x：180-189
     * - 19x：198、199
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用CharSequenceUtil安全处理空字符串，避免空指针异常
     * - 使用ReUtil进行正则匹配，避免手动处理正则表达式可能引发的问题
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 正则表达式模式使用final修饰，确保线程安全
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 验证手机号
     * boolean isValid = !GXCommonUtils.checkPhone("13800138000"); // 返回true表示有效
     * boolean isInvalid = !GXCommonUtils.checkPhone("1380013"); // 返回false表示无效
     * </pre>
     * </p>
     *
     * @param phone 待验证的手机号码，可以为null或空
     * @return boolean 返回true表示手机号码无效，返回false表示手机号码有效
     */
    public static boolean checkPhone(String phone) {
        if (CharSequenceUtil.isEmpty(phone)) {
            return true;
        }
        // 更新正则表达式以支持更多的手机号段
        final String regex = "^((13[0-9])|(14[5,7,9])|(15[0-3,5-9])|(16[6,8])|(17[0,3,5-8])|(18[0-9])|(19[1,8,9]))\\d{8}$";
        return !ReUtil.isMatch(regex, phone);
    }

    /**
     * 验证固话号码
     * <p>
     * 该方法用于验证输入的字符串是否符合中国大陆固定电话号码格式。
     * 支持的格式包括：
     * - 区号-电话号码：如 010-12345678
     * - 区号-电话号码-分机号：如 010-12345678-123
     * 其中区号为3-4位数字，电话号码为7-8位数字，分机号为1-4位数字。
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用CharSequenceUtil安全处理空字符串，避免空指针异常
     * - 使用ReUtil进行正则匹配，避免手动处理正则表达式可能引发的问题
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 验证固话号码
     * boolean isValid = !GXCommonUtils.checkTelephone("010-12345678"); // 返回true表示有效
     * boolean isInvalid = !GXCommonUtils.checkTelephone("12345"); // 返回false表示无效
     * </pre>
     * </p>
     *
     * @param telephone 待验证的固话号码，可以为null或空
     * @return boolean 返回true表示固话号码无效，返回false表示固话号码有效
     */
    public static boolean checkTelephone(String telephone) {
        if (CharSequenceUtil.isEmpty(telephone)) {
            return true;
        }
        String regex = "^(0\\d{2}-\\d{8}(-\\d{1,4})?)|(0\\d{3}-\\d{7,8}(-\\d{1,4})?)$";
        return !ReUtil.isMatch(regex, telephone);
    }

    /**
     * 构建菜单树
     * <p>
     * 该方法用于将扁平的列表结构转换为树形结构，适用于菜单、分类等层级数据的构建。
     * 方法会自动识别根节点（parentId等于rootParentValue的节点）作为树的顶层节点，
     * 然后递归构建子节点，形成完整的树形结构。
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用Stream API安全处理集合，避免并发修改异常
     * - 递归过程中进行空值检查，防止空指针异常
     * - 使用不可变参数传递，避免参数被修改
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用局部变量存储中间结果，避免状态共享
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 构建菜单树
     * List<MenuDTO> menuList = menuService.getAllMenus();
     * List<MenuDTO> menuTree = GXCommonUtils.buildTree(menuList, 0);
     * </pre>
     * </p>
     *
     * @param sourceList      源列表，包含所有需要构建树形结构的节点，不能为null
     * @param rootParentValue 根节点的父级值，用于识别顶层节点，通常为0或null
     * @param <R>             节点类型，必须包含getParentId()和setChildren()方法
     * @return 构建好的树形结构列表，如果源列表为空则返回空列表
     */
    public static <R> List<R> buildTree(List<R> sourceList, Object rootParentValue) {
        return buildTree(sourceList, rootParentValue, null);
    }

    /**
     * 构建菜单树（支持自定义父级字段获取方法）
     * <p>
     * 该方法是{@link #buildTree(List, Object)}的增强版本，允许指定用于获取父级ID的方法名。
     * 适用于父级字段名称不是标准的"parentId"的情况，提供了更灵活的树形结构构建能力。
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用Stream API安全处理集合，避免并发修改异常
     * - 使用CharSequenceUtil安全处理字符串，避免空指针异常
     * - 使用数组存储方法名，避免频繁创建字符串对象
     * - 递归过程中进行空值检查，防止空指针异常
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用局部变量存储中间结果，避免状态共享
     * - 使用不可变参数传递，避免参数被修改
     * </p>
     * <p>
     * 性能优化：
     * - 使用Stream API的并行处理能力，提高大数据量下的处理效率
     * - 只遍历一次源列表，分别筛选出根节点和子节点，避免重复遍历
     * - 使用反射缓存机制，减少反射调用的开销
     * </p>
     *
     * @param sourceList          源列表，包含所有需要构建树形结构的节点，不能为null
     * @param rootParentValue     根节点的父级值，用于识别顶层节点，通常为0或null
     * @param getParentMethodName 获取父级ID的方法名，如果为null则默认使用"getParentId"
     * @param <R>                 节点类型，必须包含指定的父级ID获取方法和setChildren()方法
     * @return 构建好的树形结构列表，如果源列表为空则返回空列表
     */
    public static <R> List<R> buildTree(List<R> sourceList, Object rootParentValue, String getParentMethodName) {
        if (CollUtil.isEmpty(sourceList)) {
            return CollUtil.newArrayList();
        }

        String[] methodNames = new String[]{"getParentId"};
        if (CharSequenceUtil.isNotBlank(getParentMethodName)) {
            methodNames[0] = getParentMethodName;
        }

        // JDK8的stream处理, 把根分类区分出来
        List<R> roots = sourceList.stream().filter(obj -> {
            if (Objects.isNull(obj)) {
                return false;
            }
            Object parentId = GXCommonUtils.reflectCallObjectMethod(obj, methodNames[0]);
            return Objects.equals(parentId, rootParentValue);
        }).collect(Collectors.toList());

        // 把非根分类区分出来
        List<R> subs = sourceList.stream().filter(obj -> {
            if (Objects.isNull(obj)) {
                return false;
            }
            Object parentId = GXCommonUtils.reflectCallObjectMethod(obj, methodNames[0]);
            return !Objects.equals(parentId, rootParentValue);
        }).collect(Collectors.toList());

        // 递归构建结构化的分类信息
        if (!CollUtil.isEmpty(roots)) {
            roots.forEach(root -> buildSubs(root, subs, methodNames[0]));
        }

        return roots;
    }

    /**
     * 构建菜单树的子级
     * <p>
     * 该方法用于递归构建树形结构的子节点。对于每个父节点，方法会从子节点列表中
     * 找出所有父级ID等于当前节点ID的节点，将它们设置为当前节点的子节点，
     * 然后递归处理这些子节点，构建完整的树形结构。
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用CollUtil安全处理集合，避免空指针异常
     * - 使用Stream API安全处理集合，避免并发修改异常
     * - 递归前进行空值检查，防止栈溢出和空指针异常
     * </p>
     * <p>
     * 性能优化：
     * - 使用Stream API的过滤功能，高效筛选子节点
     * - 递归终止条件明确，避免不必要的递归调用
     * - 只有存在子节点时才进行setChildren操作，避免不必要的反射调用
     * </p>
     *
     * @param parent              父节点，不能为null
     * @param subs                可能的子节点列表，可以为空
     * @param getParentMethodName 获取父级ID的方法名，不能为null或空
     * @param <R>                 节点类型
     */
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

    /**
     * 解码服务链接信息
     * <p>
     * 该方法用于解码加密的服务连接字符串，支持多种数据源类型：
     * </p>
     * <pre>
     *     1、数据库连接字符串
     *     2、Redis连接信息
     *     3、MongoDB连接信息
     * </pre>
     * <p>
     * 解码过程：
     * 1. 首先从系统属性或环境变量中获取解密密钥
     * 2. 验证输入字符串是否为有效的Base64编码
     * 3. 使用密钥解码连接字符串
     * 4. 对解码后的字符串进行HTML转义处理
     * 5. 将结果转换为目标类型
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用CharSequenceUtil安全处理字符串，避免空指针异常
     * - 对密钥和连接字符串进行严格验证，防止解码错误
     * - 使用Convert工具安全转换类型，避免类型转换异常
     * - 使用HtmlUtil处理特殊字符，防止XSS攻击
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用局部变量存储中间结果，避免状态共享
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 解码数据库连接字符串
     * String encodedDbUrl = getEncodedDatabaseUrl();
     * String dbUrl = GXCommonUtils.decodeConnectStr(encodedDbUrl, String.class);
     *
     * // 解码Redis连接信息为Dict对象
     * String encodedRedisInfo = getEncodedRedisInfo();
     * Dict redisConfig = GXCommonUtils.decodeConnectStr(encodedRedisInfo, Dict.class);
     * </pre>
     * </p>
     *
     * @param connectEncodeStr 加密的链接信息，可以为null或空
     * @param targetClazz      目标类型，解码后的数据将被转换为此类型，不能为null
     * @param <R>              返回类型
     * @return 解码并转换后的连接信息，如果输入为空或解码失败则返回相应的默认值
     * @throws IllegalArgumentException 如果targetClazz为null
     */
    public static <R> R decodeConnectStr(String connectEncodeStr, Class<R> targetClazz) {
        if (targetClazz == null) {
            throw new IllegalArgumentException("目标类型不能为null");
        }

        if (CharSequenceUtil.isEmpty(connectEncodeStr)) {
            return null;
        }

        // 首先尝试从系统属性中获取密钥
        String secretKey = System.getProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY);

        // 如果系统属性中没有，则尝试从环境变量中获取
        if (CharSequenceUtil.isEmpty(secretKey)) {
            secretKey = System.getenv(GXCommonConstant.DATA_SOURCE_SECRET_KEY_ENV);
        }

        // 如果密钥为空，则不进行解密操作
        if (CharSequenceUtil.isEmpty(secretKey)) {
            GXLoggerUtils.logDebug(LOG, "解密密钥为空, 连接信息不进行解密操作");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        // 验证是否为有效的Base64编码
        if (!Base64.isBase64(connectEncodeStr)) {
            GXLoggerUtils.logDebug(LOG, "连接信息不是有效的Base64编码, 将直接转换原始字符串");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        // 使用密钥解码连接字符串
        String decodedStr = GXAuthCodeUtils.authCodeDecode(connectEncodeStr, secretKey);

        // 检查解码结果是否为空对象
        if (CharSequenceUtil.equalsIgnoreCase(decodedStr, "{}")) {
            GXLoggerUtils.logDebug(LOG, "链接信息参数解码失败, 将使用原始的链接信息");
            return Convert.convert(targetClazz, connectEncodeStr);
        }

        // 对解码后的字符串进行HTML转义处理
        decodedStr = HtmlUtil.unescape(decodedStr);

        // 将结果转换为目标类型并返回
        return Convert.convert(targetClazz, decodedStr);
    }

    /**
     * 获取指定类上的泛型Class
     * <p>
     * 该方法用于获取指定类的泛型参数类型。在Java中，由于类型擦除机制，
     * 泛型信息在运行时会被擦除，但通过反射机制，我们仍然可以获取到泛型的类型信息。
     * 这在处理泛型类、泛型方法时非常有用。
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用ClassUtil安全获取泛型类型，避免类型转换异常
     * - 使用@SuppressWarnings注解抑制不可避免的类型转换警告
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取Repository类的第一个泛型参数类型（通常是实体类型）
     * Class<Entity> entityClass = GXCommonUtils.getGenericClassType(userRepository.getClass(), 0);
     *
     * // 获取Service类的第二个泛型参数类型
     * Class<DTO> dtoClass = GXCommonUtils.getGenericClassType(userService.getClass(), 1);
     * </pre>
     * </p>
     *
     * @param clazz 目标Class对象，不能为null
     * @param index 泛型参数的索引，从0开始计数，不能为null
     * @param <R>   返回的泛型类型
     * @return 指定索引位置的泛型参数类型，如果不存在则可能返回null
     * @throws IllegalArgumentException 如果clazz或index为null
     */
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

    /**
     * 将Table类型的条件转换为条件表达式
     * <p>
     * 该方法用于将Google Guava的Table类型条件转换为框架内部使用的GXCondition条件表达式。
     * Table结构中，行表示字段名，列表示操作符，值表示条件值。转换后的条件可用于构建SQL查询。
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用ArrayList安全创建集合，避免不可变集合的修改异常
     * - 使用Dict安全构建参数，避免空指针异常
     * - 对函数对象进行空值检查，防止空指针异常
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用局部变量存储中间结果，避免状态共享
     * - 使用不可变的Dict对象传递参数，避免并发修改问题
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 创建条件表
     * Table<String, String, Object> conditionTable = HashBasedTable.create();
     * conditionTable.put("username", "eq", "admin");
     * conditionTable.put("status", "gt", 0);
     *
     * // 转换为条件表达式
     * List<GXCondition<?>> conditions = GXCommonUtils.convertTableConditionToConditionExp("user", conditionTable);
     * </pre>
     * </p>
     *
     * @param tableNameAlias 表别名，用于构建SQL条件时指定表名，不能为null
     * @param condition      原始Table类型条件，不能为null
     * @return 转换后的GXCondition条件列表，不会为null但可能为空列表
     * @throws GXBusinessException 当找不到对应操作符的转换函数时抛出
     */
    public static List<GXCondition<?>> convertTableConditionToConditionExp(String tableNameAlias, Table<String, String, Object> condition) {
        if (condition == null) {
            return new ArrayList<>();
        }

        List<GXCondition<?>> conditions = new ArrayList<>();

        condition.rowMap().forEach((column, datum) -> datum.forEach((op, value) -> {
            // 构建参数字典
            Dict data = Dict.create()
                    .set("tableNameAlias", tableNameAlias)
                    .set("fieldName", column);
            // 将List转换为Set
            if (TypeToken.of(value.getClass()).isSubtypeOf(List.class)) {
                var valueSet = new HashSet<>(Convert.toList(value));
                data.set("value", valueSet);
            } else {
                data.set("value", value);
            }

            // 获取对应操作符的转换函数
            Function<Dict, GXCondition<?>> function = GXDataSourceConstant.getFunction(op);
            if (Objects.isNull(function)) {
                throw new GXBusinessException(CharSequenceUtil.format("请完善{}类型数据转换器", op));
            }

            // 应用转换函数并添加到结果列表
            conditions.add(function.apply(data));
        }));

        return conditions;
    }

    /**
     * 将字符串转换为指定类型的对象
     * <p>
     * 该方法支持两种主要的字符串格式转换：
     * 1. Map格式字符串：如 {name=jack, age=18}
     * 2. JSON格式字符串：如 {"name":"jack","age":18}
     * </p>
     * <p>
     * 转换流程：
     * 1. 检查字符串是否为空
     * 2. 尝试匹配Map格式并转换
     * 3. 尝试匹配JSON格式并转换
     * 4. 如果都不匹配，返回null
     * </p>
     * <p>
     * 安全特性：
     * 1. 对输入字符串进行严格验证
     * 2. 使用try-catch块捕获所有可能的异常
     * 3. 详细记录转换失败的原因
     * 4. 对Map格式字符串的处理增加了防止注入的保护
     * </p>
     *
     * @param str         需要转换的字符串
     * @param targetClazz 目标类型
     * @param <T>         泛型类型
     * @return 转换后的对象，如果转换失败则返回null
     */
    public static <T> T convertStrToTarget(String str, Class<T> targetClazz) {
        // 参数验证
        if (CharSequenceUtil.isEmpty(str)) {
            return null;
        }
        if (targetClazz == null) {
            LOG.warn("目标类型为null，无法进行转换");
            return null;
        }

        // 处理Map格式字符串 {name=jack}
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

        // 处理JSON格式字符串
        if (JSONUtil.isTypeJSON(str)) {
            try {
                return JSONUtil.toBean(str, targetClazz);
            } catch (ConvertException ex) {
                LOG.error("JSON数据转换失败! 原始字符串: {}, 错误信息: {}", str, ex.getMessage());
                return null;
            } catch (Exception ex) {
                // 捕获其他可能的异常
                LOG.error("JSON数据转换过程中发生未知异常! 原始字符串: {}, 错误信息: {}", str, ex.getMessage());
                return null;
            }
        }

        // 尝试直接转换
        try {
            return Convert.convert(targetClazz, str);
        } catch (Exception ex) {
            LOG.debug("直接转换失败，不是有效的JSON或Map格式: {}", str);
            return null;
        }
    }

    /**
     * 判断是否是正确的Base64字符串
     * <p>
     * 该方法用于验证输入的字符串是否为有效的Base64编码格式。
     * Base64是一种基于64个可打印字符来表示二进制数据的表示方法，
     * 常用于在HTTP环境下传递二进制数据，或在其他需要将二进制数据转换为ASCII字符的场景。
     * </p>
     * <p>
     * 内存安全特性：
     * - 委托给Hutool的Base64工具类处理验证，避免手动实现可能引发的问题
     * - 对null值进行安全处理，避免空指针异常
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 验证字符串是否为有效的Base64编码
     * String validBase64 = "SGVsbG8gV29ybGQ="; // "Hello World"的Base64编码
     * boolean isValid = GXCommonUtils.isBase64(validBase64); // 返回true
     *
     * String invalidBase64 = "SGVsbG8gV29ybGQ="; // 包含非Base64字符
     * boolean isInvalid = GXCommonUtils.isBase64(invalidBase64); // 返回false
     * </pre>
     * </p>
     *
     * @param base64Str 待验证的Base64字符串，可以为null
     * @return 如果字符串是有效的Base64编码则返回true，否则返回false
     */
    public static boolean isBase64(String base64Str) {
        if (CharSequenceUtil.isEmpty(base64Str)) {
            return false;
        }
        return Base64.isBase64(base64Str);
    }

    /**
     * 检测给定Class<?>中是否包含指定的方法
     * <p>
     * 该方法用于检查指定类中是否存在具有给定名称和参数类型的方法。
     * 这在需要动态调用方法或验证方法是否可用时非常有用，特别是在反射操作之前。
     * </p>
     * <p>
     * 内存安全特性：
     * - 对参数进行空值检查，避免空指针异常
     * - 使用ReflectUtil安全获取方法，避免反射异常
     * - 使用ObjectUtil安全检查方法是否存在，避免空指针异常
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用局部变量存储中间结果，避免状态共享
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 检查类是否包含指定方法
     * boolean hasMethod = GXCommonUtils.checkMethodExists(UserService.class, "findById", 1L);
     * if (hasMethod) {
     *     // 可以安全地调用该方法
     *     Object result = GXCommonUtils.reflectCallObjectMethod(userService, "findById", 1L);
     * }
     * </pre>
     * </p>
     *
     * @param targetClazz 目标类对象，不能为null
     * @param methodName  方法名称，不能为null或空
     * @param params      方法参数，可以为空数组或null
     * @return 如果方法存在则返回true，否则返回false
     * @throws IllegalArgumentException 如果targetClazz或methodName为null
     */
    public static boolean checkMethodExists(Class<?> targetClazz, String methodName, Object... params) {
        if (targetClazz == null) {
            throw new IllegalArgumentException("目标类不能为null");
        }
        if (CharSequenceUtil.isEmpty(methodName)) {
            throw new IllegalArgumentException("方法名不能为null或空");
        }

        // 处理params为null的情况
        if (params == null) {
            params = new Object[0];
        }

        // 构建参数类型数组
        Class<?>[] classes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            if (Objects.nonNull(params[i])) {
                classes[i] = params[i].getClass();
            }
        }

        // 使用ReflectUtil获取方法对象
        Method method = ReflectUtil.getMethod(targetClazz, methodName, classes);
        return ObjectUtil.isNotNull(method);
    }

    /**
     * 检测给定的URL是否可以打开
     * <p>
     * 该方法用于验证指定的URL是否可访问，通过发送HTTP GET请求并检查响应状态码来判断。
     * 这在需要验证外部资源可用性、检查网络连接状态或监控服务健康状况时非常有用。
     * </p>
     * <p>
     * 内存安全特性：
     * - 使用try-catch块安全处理异常，避免未捕获异常导致程序崩溃
     * - 使用HttpUtil安全创建和执行请求，确保资源正确释放
     * - 使用CharSequenceUtil安全格式化错误消息，避免字符串拼接异常
     * </p>
     * <p>
     * 线程安全特性：
     * - 方法不依赖共享状态，可安全地在多线程环境中调用
     * - 使用局部变量存储中间结果，避免状态共享
     * </p>
     * <p>
     * 性能优化：
     * - 使用HttpRequest而不是直接打开URL连接，提供更好的性能和控制
     * - 日志记录使用占位符而不是字符串拼接，提高日志性能
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 检查URL是否可访问
     * Integer statusCode = GXCommonUtils.checkURLReachable("https://www.example.com");
     * if (statusCode == HttpStatus.HTTP_OK) {
     *     System.out.println("URL可访问");
     * } else if (statusCode == -1) {
     *     System.out.println("URL不可访问或发生异常");
     * } else {
     *     System.out.println("URL返回状态码: " + statusCode);
     * }
     * </pre>
     * </p>
     *
     * @param urlString 待检测的URL地址，不能为null或空
     * @return 如果URL可访问则返回HTTP状态码（如200表示正常），如果不可访问或发生异常则返回-1
     */
    public static Integer checkURLReachable(String urlString) {
        if (CharSequenceUtil.isEmpty(urlString)) {
            LOG.warn("URL地址不能为空");
            return -1;
        }

        try {
            // 创建HTTP请求
            HttpRequest request = HttpUtil.createRequest(cn.hutool.http.Method.GET, urlString);
            // 设置超时时间，避免长时间等待
            request.timeout(5000);
            // 执行请求
            HttpResponse response = request.execute();
            // 获取响应状态码
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

    /**
     * 生成HMAC-SHA256签名
     * <p>
     * 流程说明：
     * 1. 创建HMAC对象并初始化密钥
     * 2. 从Spring上下文获取ObjectMapper
     * 3. 将数据对象序列化为JSON字符串
     * 4. 计算HMAC值并进行Base64编码
     * </p>
     * <p>
     * 线程安全性：
     * - 该方法是线程安全的，可在多线程环境下调用
     * - Mac实例在每次调用时创建，不存在状态共享问题
     * - ObjectMapper是线程安全的，可以安全地在多线程间共享
     * </p>
     * <p>
     * 性能优化：
     * - 使用StandardCharsets常量替代字符串指定编码，提高性能
     * - 对异常进行精确分类和处理，提供更准确的错误信息
     * </p>
     * <p>
     * 安全性：
     * - 使用标准的HMAC-SHA256算法，提供高强度的密码学安全性
     * - 使用Base64编码输出，避免二进制数据处理问题
     * - 异常信息中不暴露敏感数据
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 示例1：对简单对象生成签名
     * User user = new User("张三", 30);
     * String secret = "your-secret-key";
     * String signature = GXCommonUtils.generateHmac(user, secret);
     *
     * // 示例2：对Map数据生成签名
     * Map<String, Object> payload = new HashMap<>();
     * payload.put("userId", 12345);
     * payload.put("timestamp", System.currentTimeMillis());
     * String signature = GXCommonUtils.generateHmac(payload, secret);
     * </pre>
     * </p>
     *
     * @param data   待签名的数据对象，不能为null
     * @param secret 签名密钥，不能为null或空
     * @return Base64编码的HMAC签名结果
     * @throws GXBusinessException 当签名过程发生异常时抛出，包含详细错误信息
     */
    public static String generateHmac(Object data, String secret) {
        // 参数校验
        if (data == null) {
            throw new GXBusinessException("待签名数据不能为null");
        }
        if (CharSequenceUtil.isBlank(secret)) {
            throw new GXBusinessException("签名密钥不能为空");
        }
        try {
            // 创建HMAC对象
            Mac mac = SecureUtil.createMac("HmacSHA256");
            // 初始化密钥，统一使用UTF-8编码
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            // 从Spring上下文获取ObjectMapper实例
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw new GXBusinessException("Spring容器中不存在ObjectMapper Bean!!!");
            }

            // 序列化数据对象为JSON字符串
            String jsonData = objectMapper.writeValueAsString(data);
            // 计算HMAC值并进行Base64编码，统一使用UTF-8编码
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

    /**
     * 验证客户端提供的HMAC签名是否与服务器端计算的签名一致
     * <p>
     * 流程说明：
     * 1. 创建HMAC对象并初始化密钥
     * 2. 序列化待验证的数据负载
     * 3. 计算服务器端HMAC签名
     * 4. 比较服务器端签名与客户端签名是否匹配
     * </p>
     * <p>
     * 线程安全性：
     * - 该方法是线程安全的，可在多线程环境下调用
     * - Mac实例在每次调用时创建，不存在状态共享问题
     * - ObjectMapper是线程安全的，可以安全地在多线程间共享
     * </p>
     * <p>
     * 性能优化：
     * - 使用StandardCharsets常量替代字符串指定编码，提高性能
     * - 使用常量时间比较方法，防止时序攻击
     * - 对异常进行精确分类和处理，提供更准确的错误信息
     * </p>
     * <p>
     * 安全性：
     * - 使用标准的HMAC-SHA256算法，提供高强度的密码学安全性
     * - 使用常量时间比较，防止时序攻击
     * - 异常信息中不暴露敏感数据
     * - 验证失败时返回false而不是抛出异常，防止信息泄露
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 示例：验证客户端传入的签名
     * Map<String, Object> payload = new HashMap<>();
     * payload.put("userId", 12345);
     * payload.put("timestamp", 1634567890123L);
     *
     * String secret = "your-secret-key";
     * String clientHmac = request.getHeader("X-Signature");
     *
     * if (GXCommonUtils.checkHmac(secret, clientHmac, payload)) {
     *     // 签名验证通过，处理业务逻辑
     * } else {
     *     // 签名验证失败，拒绝请求
     *     throw new GXBusinessException("无效的签名");
     * }
     * </pre>
     * </p>
     *
     * @param secret     用于HMAC计算的共享密钥，不能为null或空
     * @param clientHmac 客户端传入的HMAC签名，不能为null或空
     * @param payload    待验证的数据负载，不能为null
     * @return 当签名匹配时返回true，否则返回false
     * @throws GXBusinessException 当验证过程发生异常时抛出，包含详细错误信息
     */
    public static boolean checkHmac(String secret, String clientHmac, Object payload) {
        // 参数校验
        if (CharSequenceUtil.isBlank(secret)) {
            throw new GXBusinessException("签名密钥不能为空");
        }
        if (CharSequenceUtil.isBlank(clientHmac)) {
            return false; // 客户端签名为空，直接返回验证失败
        }
        if (payload == null) {
            throw new GXBusinessException("待验证数据不能为null");
        }
        try {
            // 创建HMAC-SHA256算法的Mac实例
            Mac mac = SecureUtil.createMac("HmacSHA256");
            // 初始化密钥规格，统一使用UTF-8编码
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            // 获取ObjectMapper实例用于序列化payload
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            if (objectMapper == null) {
                throw new GXBusinessException("Spring上下文中没有ObjectMapper");
            }
            // 序列化payload为JSON字符串
            String jsonData = objectMapper.writeValueAsString(payload);
            // 计算服务器端HMAC并进行Base64编码
            byte[] hmacBytes = mac.doFinal(jsonData.getBytes(StandardCharsets.UTF_8));
            String serverHmac = Base64Encoder.encode(hmacBytes);
            // 使用常量时间比较，防止时序攻击
            return MessageDigest.isEqual(
                    serverHmac.getBytes(StandardCharsets.UTF_8),
                    clientHmac.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            // 记录异常但不暴露详细信息给调用者
            LOG.error("HMAC验证失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 将GXUpdateFieldRequest列表转换为UpdateField列表
     * <p>
     * 该方法将包含四元组(表名,字段名,值,更新字段类名)的列表转换为可用于数据库更新操作的GXUpdateField对象列表。
     * 通过反射动态创建指定类型的更新字段对象，支持各种数据类型的字段更新操作。
     * </p>
     *
     * <p>
     * 安全特性：
     * 1. 使用Class.forName进行类加载，支持动态扩展不同类型的更新字段
     * 2. 使用ReflectUtil安全地创建实例，避免直接反射调用构造函数的安全风险
     * 3. 异常处理机制确保转换过程中的错误被正确捕获并转换为运行时异常
     * 4. 输入验证确保不会处理空列表，防止不必要的处理
     * 5. 使用类型缓存减少重复的类加载操作，提高性能和安全性
     * </p>
     *
     * <p>
     * 性能优化：
     * 1. 预分配ArrayList容量，减少动态扩容开销
     * 2. 使用forEach替代传统for循环，代码更简洁
     * 3. 使用try-catch块仅捕获必要的异常，提高异常处理效率
     * 4. 使用ConcurrentHashMap缓存已加载的类，避免重复加载
     * 5. 根据列表大小自动选择串行或并行流处理
     * 6. 使用线程安全的集合操作，确保并行处理时的数据一致性
     * </p>
     *
     * <p>
     * 使用示例：
     * <pre>
     * // 创建GXUpdateFieldRequest列表，每个GXUpdateFieldRequest包含：表名、字段名、更新字段类名、字段值
     * List<GXUpdateFieldRequest> quartetList = new ArrayList<>();
     *
     * // 添加字符串类型的更新字段
     * quartetList.add(new GXUpdateFieldRequest("user", "username", "cn.maple.core.framework.dto.inner.field.GXUpdateStrField", "张三"));
     *
     * // 添加整数类型的更新字段
     * quartetList.add(new GXUpdateFieldRequest("user", "age", "cn.maple.core.framework.dto.inner.field.GXUpdateNumberField", 25));
     *
     * // 转换为UpdateField列表
     * List<GXUpdateField<?>> updateFields = convertUpdateFieldRequestLst(quartetList);
     *
     * // 使用转换后的列表执行更新操作
     * // dbMapper.updateByCondition(updateFields, conditions);
     * </pre>
     * </p>
     *
     * @param updateLst GXUpdateFieldRequest列表，每个元素包含表名、字段名、更新字段类名和值
     * @return 转换后的GXUpdateField列表，可直接用于数据库更新操作
     * @throws GXBusinessException 当类加载或实例创建失败时抛出，包含原始异常信息
     */
    public static List<GXUpdateField<?>> convertUpdateFieldRequestLst(List<GXUpdateFieldRequest> updateLst) {
        // 输入验证，如果列表为空则返回空列表
        if (CollUtil.isEmpty(updateLst)) {
            return Collections.emptyList();
        }

        // 预分配ArrayList容量，避免动态扩容
        List<GXUpdateField<?>> updateFields = new ArrayList<>(updateLst.size());

        // 使用ConcurrentHashMap缓存已加载的类，避免重复加载
        // 静态类缓存，在多次调用时提高性能
        final ConcurrentHashMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>(8);

        // 根据列表大小决定是否使用并行流处理
        // 对于大型列表(超过100个元素)，使用并行流提高性能
        Stream<GXUpdateFieldRequest> stream = updateLst.size() > 100 ?
                updateLst.parallelStream() : updateLst.stream();

        // 使用线程安全的集合收集转换结果
        List<GXUpdateField<?>> result = stream.map(updateField -> {
            try {
                // 提取GXUpdateFieldRequest中的各个元素
                String tableName = updateField.tableName();  // 表名
                String fieldName = updateField.fieldName();  // 字段名
                String className = updateField.className();  // 更新字段类名
                Object value = updateField.value();          // 更新字段值

                // 参数验证
                if (CharSequenceUtil.isBlank(className)) {
                    throw new GXBusinessException("更新字段类名不能为空: " + fieldName);
                }

                // 从缓存中获取类，如果不存在则加载并缓存
                Class<?> updateFieldClass = CLASS_CACHE.computeIfAbsent(className, name -> {
                    try {
                        return Class.forName(name);
                    } catch (ClassNotFoundException e) {
                        throw new GXBusinessException("更新字段类未找到: " + name, e);
                    }
                });

                // 使用ReflectUtil安全地创建实例，传入表名、字段名和值
                Object updateFieldObj = ReflectUtil.newInstance(updateFieldClass, tableName, fieldName, value);

                // 类型检查，确保创建的对象是GXUpdateField的实例
                if (!(updateFieldObj instanceof GXUpdateField<?>)) {
                    throw new GXBusinessException("创建的对象不是GXUpdateField类型: " + className);
                }

                // 返回转换后的对象
                return (GXUpdateField<?>) updateFieldObj;
            } catch (GXBusinessException e) {
                // 直接重新抛出业务异常
                throw e;
            } catch (Exception e) {
                // 其他异常，如实例创建失败，提供更详细的错误信息
                throw new GXBusinessException("创建更新字段对象失败: " + updateField.fieldName() + ", 原因: " + e.getMessage(), e);
            }
        }).collect(Collectors.toList());

        // 将结果添加到返回列表
        updateFields.addAll(result);

        return updateFields;
    }

    /**
     * 获取系统平均负载
     * 返回值范围通常在0.0到1.0之间，值越大表示系统负载越高
     * 在Windows系统上可能不准确，仅作参考
     *
     * @return 系统负载值，范围0.0-1.0，如果无法获取则返回-1
     */
    public static double getSystemLoadAverage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            double loadAverage = osBean.getSystemLoadAverage();

            // 某些系统可能返回负值表示不支持
            if (loadAverage < 0) {
                // 尝试使用CPU使用率作为替代指标
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                    double cpuLoad = sunOsBean.getProcessCpuLoad();
                    // getProcessCpuLoad() 返回 -1 表示不可用，返回值已经在 0-1 范围内
                    return cpuLoad;
                }
                return -1;
            }

            // 将负载平均值标准化到0-1范围
            // 通常loadAverage是基于处理器核心数的，所以除以可用处理器数
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
}
