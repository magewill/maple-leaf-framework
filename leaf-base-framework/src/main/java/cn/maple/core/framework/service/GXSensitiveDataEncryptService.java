package cn.maple.core.framework.service;

import java.lang.reflect.Field;

/**
 * 敏感数据加密服务接口
 * <p>
 * 该接口用于对标记了敏感数据注解的对象进行自动加密处理。主要应用于将数据写入数据库前，
 * 对其中的敏感字段（如手机号、身份证号、银行卡号等）进行加密，以保护数据安全。
 * </p>
 *
 * <p>线程安全说明：</p>
 * <p>该接口的实现类应当保证线程安全，因为在并发环境下可能会同时处理多个对象的加密操作。</p>
 *
 * <p>性能考虑：</p>
 * <p>1. 加密操作应当高效，尤其是在批量处理大量数据时</p>
 * <p>2. 可以考虑使用缓存机制避免重复反射操作</p>
 * <p>3. 对于复杂对象，应当支持递归加密嵌套对象中的敏感字段</p>
 *
 * <p>安全考虑：</p>
 * <p>1. 加密算法应当选择安全性高的算法，如AES</p>
 * <p>2. 密钥管理应当安全，避免硬编码在代码中</p>
 * <p>3. 应当考虑密钥轮换机制，定期更换密钥</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在实体类上添加敏感数据注解
 * @GXSensitiveData
 * public class UserEntity {
 *     private Long id;
 *     private String username;
 *
 *     // 标记需要加密的字段
 *     @GXSensitiveField
 *     private String mobile;
 *
 *     @GXSensitiveField
 *     private String idCard;
 *
 *     // getter和setter方法
 * }
 *
 * // 2. 在Mapper层使用加密服务
 * @Mapper
 * public interface UserMapper {
 *     // 在MyBatis拦截器中会自动调用加密服务
 *     void insert(UserEntity user);
 * }
 *
 * // 3. 在Service层手动使用加密服务
 * @Service
 * public class UserServiceImpl implements UserService {
 *     @Autowired
 *     private UserMapper userMapper;
 *
 *     @Autowired
 *     private GXSensitiveDataEncryptService encryptService;
 *
 *     @Override
 *     public void saveUser(UserEntity user) {
 *         try {
 *             // 获取对象的所有字段
 *             Field[] fields = user.getClass().getDeclaredFields();
 *             // 对对象进行加密处理
 *             encryptService.encrypt(fields, user);
 *             // 保存到数据库
 *             userMapper.insert(user);
 *         } catch (IllegalAccessException e) {
 *             log.error("加密用户数据失败", e);
 *             throw new RuntimeException("保存用户失败", e);
 *         }
 *     }
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 * @see cn.maple.core.framework.annotation.GXSensitiveData 敏感数据类注解
 * @see cn.maple.core.framework.annotation.GXSensitiveField 敏感字段注解
 * @see GXSensitiveDataDecryptService 敏感数据解密服务接口
 */
public interface GXSensitiveDataEncryptService {
    /**
     * 加密对象中的敏感字段
     * <p>
     * 该方法会自动识别对象中标记了{@link cn.maple.core.framework.annotation.GXSensitiveField}注解的字段，
     * 并使用配置的加密算法和密钥对这些字段进行加密处理。支持处理单个对象的所有敏感字段。
     * </p>
     * <p>
     * 加密过程说明：
     * 1. 遍历对象的所有字段
     * 2. 识别标记了{@link cn.maple.core.framework.annotation.GXSensitiveField}注解的字段
     * 3. 根据注解配置的加密算法和密钥对字段值进行加密
     * 4. 将加密后的值设置回对象
     * </p>
     * <p>
     * 实现细节：
     * - 对于字符串类型的字段，直接进行加密处理
     * - 对于集合类型的字段，遍历集合中的每个元素并递归处理
     * - 对于数组类型的字段，遍历数组中的每个元素并递归处理
     * - 对于自定义对象类型的字段，递归处理该对象的所有字段
     * </p>
     * <p>
     * 异常处理策略：
     * - 当字段不可访问时，会抛出IllegalAccessException异常
     * - 实现类应当考虑捕获并处理加密过程中可能出现的其他异常
     * </p>
     * <p>
     * 性能优化建议：
     * - 实现类可以考虑缓存字段的注解信息，避免重复反射操作
     * - 对于频繁加密的相同类型对象，可以缓存其字段结构信息
     * </p>
     *
     * @param declaredFields 对象声明的所有字段数组，通常通过Class.getDeclaredFields()获取
     * @param paramsObject   需要加密的对象实例
     * @return 加密后的对象，与输入对象是同一个实例
     * @throws IllegalAccessException 当字段不可访问时抛出此异常，通常是因为字段是私有的且没有对应的访问方法
     */
    <T> T encrypt(Field[] declaredFields, T paramsObject) throws IllegalAccessException;

    /**
     * 加密对象中的敏感字段（便捷方法）
     * <p>
     * 该方法是{@link #encrypt(Field[], Object)}的便捷版本，自动获取对象的字段信息，
     * 无需调用者手动提供字段数组。适用于大多数简单的加密场景。
     * </p>
     * <p>
     * 实现细节：
     * - 自动通过反射获取对象的所有声明字段
     * - 调用{@link #encrypt(Field[], Object)}方法进行实际的加密处理
     * - 支持处理单个对象、集合对象以及嵌套对象中的敏感字段
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * @Autowired
     * private GXSensitiveDataEncryptService encryptService;
     *
     * public void saveUser(UserEntity user) {
     *     // 直接加密对象，无需手动获取字段
     *     encryptService.encrypt(user);
     *     // 保存到数据库
     *     userMapper.insert(user);
     * }
     * </pre>
     * </p>
     * <p>
     * 注意事项：
     * - 该方法仅获取对象自身的声明字段，不包括继承的字段
     * - 如果需要处理继承字段，请使用{@link #encrypt(Field[], Object)}方法并手动提供完整的字段列表
     * - 对于复杂对象结构，该方法会递归处理嵌套对象
     * </p>
     *
     * @param paramsObject 需要加密的对象实例
     * @return 加密后的对象，与输入对象是同一个实例
     * @throws IllegalAccessException 当字段不可访问时抛出此异常
     */
    default <T> T encrypt(T paramsObject) throws IllegalAccessException {
        return paramsObject;
    }
}
