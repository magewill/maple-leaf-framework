package cn.maple.core.framework.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXBusinessService;
import cn.maple.core.framework.util.GXCommonUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 业务通用服务实现类
 * <p>
 * 该类提供了一系列通用的业务操作方法，包括但不限于：
 * - 敏感数据（如手机号）的加解密和脱敏处理
 * - 复杂对象字段值的提取和转换
 * - 对象之间的转换和映射
 * </p>
 *
 * <p>线程安全说明：</p>
 * <p>该实现类是线程安全的，因为：</p>
 * <p>1. 不维护任何可变状态（无实例变量）</p>
 * <p>2. 所有方法都基于输入参数进行操作，不依赖实例状态</p>
 * <p>3. 使用的工具类都是线程安全的</p>
 *
 * <p>性能考虑：</p>
 * <p>1. 对象转换操作可能涉及反射，在高并发场景下应注意性能影响</p>
 * <p>2. 处理大量数据时应当考虑分批处理</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * @Service
 * public class UserService {
 *     @Autowired
 *     private GXBusinessService businessService;
 *
 *     // 示例1：手机号加密
 *     public String saveUserWithEncryptedPhone(String phone, String secretKey) {
 *         // 加密手机号
 *         String encryptedPhone = businessService.encryptedPhoneNumber(phone, secretKey);
 *         // 保存到数据库...
 *         return encryptedPhone;
 *     }
 *
 *     // 示例2：对象转换
 *     public UserVO getUserDetail(Long userId) {
 *         // 从数据库获取用户实体
 *         UserEntity user = userMapper.selectById(userId);
 *         // 将实体转换为VO对象
 *         return businessService.convertSourceToTarget(user, UserVO.class);
 *     }
 *
 *     // 示例3：提取复杂对象中的字段值
 *     public String getUserExtInfo(UserEntity user, String fieldPath) {
 *         // 从用户扩展信息JSON中提取指定字段值
 *         return businessService.getSingleFieldValueByEntity(user, "extInfo." + fieldPath, String.class);
 *     }
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 */
public class GXBusinessServiceImpl implements GXBusinessService {
    /**
     * 加密手机号码
     * <p>
     * 该方法将明文手机号进行加密处理，返回加密后的密文。加密过程使用指定的密钥，
     * 并将手机号包装在Dict对象中进行处理，增强安全性。
     * </p>
     * <p>
     * 安全说明：
     * 1. 密钥不能为空，否则会抛出业务异常
     * 2. 加密结果可用于数据存储或传输，确保敏感信息不会泄露
     * </p>
     *
     * @param phoneNumber 明文手机号，需要加密的原始手机号码
     * @param key         加密密钥，用于加密算法的密钥，不能为空
     * @return 加密后的手机号密文
     * @throws GXBusinessException 当加密密钥为空时抛出此异常
     */
    @Override
    public String encryptedPhoneNumber(String phoneNumber, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("手机号加密key不能为空");
        }
        Dict data = Dict.create().set("phone", phoneNumber);
        return GXCommonUtils.encryptedData(data, key, 0);
    }

    /**
     * 解密手机号码
     * <p>
     * 该方法将加密的手机号密文解密为明文。解密过程使用指定的密钥，
     * 并从解密后的Dict对象中提取手机号字段值。
     * </p>
     * <p>
     * 安全说明：
     * 1. 密钥不能为空，否则会抛出业务异常
     * 2. 解密操作应当在安全的环境中进行，避免明文泄露
     * </p>
     *
     * @param encryptPhoneNumber 加密的手机号密文，需要解密的数据
     * @param key                解密密钥，用于解密算法的密钥，不能为空
     * @return 解密后的明文手机号
     * @throws GXBusinessException 当解密密钥为空时抛出此异常
     */
    @Override
    public String decryptedPhoneNumber(String encryptPhoneNumber, String key) {
        if (CharSequenceUtil.isEmpty(key)) {
            throw new GXBusinessException("手机号解密key不能为空");
        }
        Dict data = GXCommonUtils.decryptedData(encryptPhoneNumber, key);
        return data.getStr("phone");
    }

    /**
     * 隐藏手机号码的指定几位为指定的字符
     * <p>
     * 该方法用于对手机号进行脱敏处理，将指定范围内的字符替换为指定字符。
     * 常用于在显示手机号时保护用户隐私，如将"13812345678"显示为"138****5678"。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 将手机号中间4位替换为*
     * String maskedPhone = hiddenPhoneNumber("13812345678", 3, 7, '*');
     * // 结果: "138****5678"
     * </pre>
     * </p>
     *
     * @param phoneNumber  需要脱敏的手机号码
     * @param startInclude 开始替换的位置(包含,从0开始)，如需要替换第4-7位，则此值为3
     * @param endExclude   结束替换的位置(不包含)，如需要替换第4-7位，则此值为7
     * @param replacedChar 用于替换的字符，通常使用'*'或'x'
     * @return 脱敏后的手机号码
     */
    @Override
    public String hiddenPhoneNumber(CharSequence phoneNumber, int startInclude, int endExclude, char replacedChar) {
        return GXCommonUtils.hiddenPhoneNumber(phoneNumber, startInclude, endExclude, replacedChar);
    }

    /**
     * 获取实体中指定路径的字段值
     * <p>
     * 该方法用于从复杂对象中提取指定路径的字段值，支持多级路径和类型转换。
     * 常用于从包含嵌套结构或JSON字段的实体中提取数据。
     * </p>
     * <p>
     * 路径格式说明：
     * 1. 简单路径：直接使用字段名，如"username"
     * 2. 嵌套路径：使用点号分隔，如"address.city"
     * 3. 特殊路径：使用双冒号分隔主字段和子字段，如"extInfo::phone"
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取商品实体中扩展信息里的名称字段，并转换为Integer类型
     * Integer nameValue = getSingleFieldValueByEntity(
     *   goodsEntity,
     *   "ext.name",
     *   Integer.class
     * );
     * </pre>
     * </p>
     *
     * @param entity 源实体对象，包含需要提取的字段
     * @param path   字段路径，指定要提取的字段位置
     * @param type   目标类型，指定返回值的类型
     * @return 提取并转换后的字段值，如果字段不存在则返回该类型的默认值
     */
    @Override
    public <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type) {
        return getSingleFieldValueByEntity(entity, path, type, GXCommonUtils.getClassDefaultValue(type));
    }

    /**
     * 获取实体中指定路径的字段值（带默认值）
     * <p>
     * 该方法是{@link #getSingleFieldValueByEntity(Object, String, Class)}的增强版本，
     * 允许指定当字段不存在或提取失败时的默认返回值。
     * </p>
     * <p>
     * 实现细节：
     * 1. 将实体对象转换为JSON结构
     * 2. 解析路径格式（普通路径或特殊路径）
     * 3. 根据路径提取字段值
     * 4. 将提取的值转换为指定类型
     * 5. 如果提取失败则返回默认值
     * </p>
     * <p>
     * 特殊路径处理：
     * - 如果路径包含"::", 则分别处理主字段和子字段
     * - 如果提取的值本身是JSON结构，则进行适当的转换
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 获取用户扩展信息中的年龄，如果不存在则返回默认值18
     * Integer age = getSingleFieldValueByEntity(
     *   userEntity,
     *   "extInfo::age",
     *   Integer.class,
     *   18
     * );
     * </pre>
     * </p>
     *
     * @param entity       源实体对象，包含需要提取的字段
     * @param path         字段路径，指定要提取的字段位置
     * @param type         目标类型，指定返回值的类型
     * @param defaultValue 默认值，当字段不存在或提取失败时返回此值
     * @return 提取并转换后的字段值，如果字段不存在则返回指定的默认值
     * @throws GXBusinessException 当使用特殊路径格式且主字段不存在时抛出此异常
     */
    @Override
    public <T, R> R getSingleFieldValueByEntity(T entity, String path, Class<R> type, R defaultValue) {
        JSON json = JSONUtil.parse(JSONUtil.toJsonStr(entity));
        int index = CharSequenceUtil.indexOfIgnoreCase(path, "::");
        if (index == -1) {
            if (null == json.getByPath(path)) {
                return defaultValue;
            }
            if (JSONUtil.isTypeJSON(json.getByPath(path).toString())) {
                Dict data = Dict.create();
                Dict dict = JSONUtil.toBean(json.getByPath(path).toString(), Dict.class);
                if (!dict.isEmpty()) {
                    for (Map.Entry<String, Object> entry : dict.entrySet()) {
                        data.set(entry.getKey(), entry.getValue());
                    }
                }
                return Convert.convert(type, data);
            }
            return Convert.convert(type, json.getByPath(path));
        }
        String mainField = CharSequenceUtil.sub(path, 0, index);
        if (null == json.getByPath(mainField)) {
            throw new GXBusinessException(CharSequenceUtil.format("实体的主字段{}不存在!", mainField));
        }
        String subField = CharSequenceUtil.sub(path, index + 2, path.length());
        JSON parse = JSONUtil.parse(json.getByPath(mainField));
        if (null == parse) {
            return defaultValue;
        }
        return Convert.convert(type, parse.getByPath(subField), defaultValue);
    }

    /**
     * 将任意对象转换为指定类型的对象（简化版）
     * <p>
     * 该方法提供了一种简便的方式将源对象转换为目标类型的对象，使用默认的处理方法和复制选项。
     * 适用于简单的对象转换场景，无需自定义转换逻辑。
     * </p>
     * <p>
     * 性能考虑：
     * 1. 该方法内部使用反射机制，在高并发场景下可能影响性能
     * 2. 对于频繁调用的转换操作，建议使用缓存或预编译的转换器
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 示例1：将Dict对象转换为DTO
     * Dict source = Dict.create().set("username","britton").set("realName","枫叶思源");
     * PersonResDto dto = convertSourceToTarget(source, PersonResDto.class);
     *
     * // 示例2：将请求协议对象转换为实体对象
     * PersonReqProtocol req = new PersonReqProtocol();
     * req.setUsername("britton");
     * req.setRealName("枫叶思源");
     * UserEntity entity = convertSourceToTarget(req, UserEntity.class);
     * </pre>
     * </p>
     *
     * @param source 源对象，可以是任意类型的对象，如Map、Dict、POJO等
     * @param tClass 目标对象类型的Class对象，必须有默认构造函数
     * @return 转换后的目标类型对象
     */
    @Override
    public <S, T> T convertSourceToTarget(S source, Class<T> tClass) {
        return convertSourceToTarget(source, tClass, GXCommonConstant.DEFAULT_CUSTOMER_PROCESS_METHOD_NAME, null);
    }

    /**
     * 将任意对象转换为指定类型的对象（完整版）
     * <p>
     * 该方法提供了最完整的对象转换功能，支持自定义处理方法、复制选项和额外数据。
     * 适用于复杂的对象转换场景，需要精细控制转换过程。
     * </p>
     * <p>
     * 转换过程说明：
     * 1. 首先将源对象转换为JSON字符串
     * 2. 然后将JSON字符串转换为目标类型的对象
     * 3. 如果指定了自定义处理方法，会在目标对象上调用该方法进行后处理
     * 4. 如果提供了复制选项，会按照选项进行属性复制
     * 5. 额外数据会传递给自定义处理方法，用于辅助转换
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 创建复制选项，忽略特定字段
     * CopyOptions options = CopyOptions.create().setIgnoreProperties("createTime", "updateTime");
     *
     * // 创建额外数据
     * Dict extraData = Dict.create().set("currentUserId", 10001L);
     *
     * // 将请求对象转换为实体对象，并调用customerProcess方法进行后处理
     * UserEntity entity = convertSourceToTarget(
     *     userRequest,
     *     UserEntity.class,
     *     "customerProcess",
     *     options,
     *     extraData
     * );
     * </pre>
     * </p>
     *
     * @param source      源对象，可以是任意类型的对象
     * @param tClass      目标对象类型的Class对象
     * @param methodName  目标对象中需要调用的后处理方法名，如果为null则不调用
     * @param copyOptions 复制选项，用于控制属性复制的行为，如忽略特定字段等
     * @param extraData   额外数据，会传递给后处理方法，用于辅助转换
     * @return 转换后的目标类型对象
     */
    @Override
    public <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions, Dict extraData) {
        return GXCommonUtils.convertSourceToTarget(source, tClass, methodName, copyOptions, extraData);
    }

    /**
     * 将任意对象转换为指定类型的对象（标准版）
     * <p>
     * 该方法是{@link #convertSourceToTarget(Object, Class, String, CopyOptions, Dict)}的简化版本，
     * 使用空的额外数据字典，适用于不需要传递额外数据的场景。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 创建复制选项，忽略特定字段
     * CopyOptions options = CopyOptions.create().setIgnoreProperties("password");
     *
     * // 将用户请求对象转换为用户实体，并调用validate方法进行数据验证
     * UserEntity entity = convertSourceToTarget(
     *     userRequest,
     *     UserEntity.class,
     *     "validate",
     *     options
     * );
     * </pre>
     * </p>
     *
     * @param source      源对象，可以是任意类型的对象
     * @param tClass      目标对象类型的Class对象
     * @param methodName  目标对象中需要调用的后处理方法名
     * @param copyOptions 复制选项，用于控制属性复制的行为
     * @return 转换后的目标类型对象
     */
    @Override
    public <S, T> T convertSourceToTarget(S source, Class<T> tClass, String methodName, CopyOptions copyOptions) {
        return convertSourceToTarget(source, tClass, methodName, copyOptions, Dict.create());
    }

    /**
     * 将集合对象转换为指定类型的对象列表
     * <p>
     * 该方法用于批量转换集合中的对象，将每个源对象转换为目标类型的对象，并返回转换后的列表。
     * 适用于需要批量转换数据的场景，如将数据库实体列表转换为DTO列表。
     * </p>
     * <p>
     * 性能考虑：
     * 1. 对于大量数据的转换，可能会消耗较多内存和CPU资源
     * 2. 建议在必要时进行分批处理，避免一次性转换过多对象
     * 3. 可以考虑使用并行流进行处理，提高转换效率
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 从数据库查询用户列表
     * List<UserEntity> userEntities = userMapper.selectList(wrapper);
     *
     * // 创建复制选项，忽略敏感字段
     * CopyOptions options = CopyOptions.create().setIgnoreProperties("password", "salt");
     *
     * // 将实体列表转换为DTO列表，并调用formatData方法进行数据格式化
     * List<UserDTO> userDTOs = convertSourceListToTargetList(
     *     userEntities,
     *     UserDTO.class,
     *     "formatData",
     *     options,
     *     Dict.create().set("includeDetail", true)
     * );
     * </pre>
     * </p>
     *
     * @param collection  需要转换的对象集合，可以是List、Set等任何Collection实现
     * @param tClass      目标对象的类型，指定转换后的对象类型
     * @param methodName  转换后对象需要调用的后处理方法名
     * @param copyOptions 复制选项，用于控制属性复制的行为
     * @param extraData   额外数据，会传递给后处理方法，用于辅助转换
     * @return 转换后的目标类型对象列表
     */
    @Override
    public <R> List<R> convertSourceListToTargetList(Collection<?> collection, Class<R> tClass, String methodName, CopyOptions copyOptions, Dict extraData) {
        return GXCommonUtils.convertSourceListToTargetList(collection, tClass, methodName, copyOptions, extraData);
    }
}
