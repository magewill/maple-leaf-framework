package cn.maple.core.framework.service;

/**
 * 敏感数据解密服务接口
 * <p>
 * 该接口用于对标记了敏感数据注解的对象进行自动解密处理。主要应用于从数据库查询出的数据返回给前端前，
 * 对其中的敏感字段（如手机号、身份证号、银行卡号等）进行解密，使其恢复为明文形式。
 * </p>
 *
 * <p>功能特点：</p>
 * <ul>
 *   <li>自动识别并解密标记了敏感字段注解的数据</li>
 *   <li>支持处理单个对象、集合对象以及嵌套对象</li>
 *   <li>支持自定义解密算法和密钥</li>
 *   <li>异常处理机制确保业务流程不中断</li>
 * </ul>
 *
 * <p>线程安全说明：</p>
 * <p>该接口的实现类应当保证线程安全，因为在并发环境下可能会同时处理多个对象的解密操作。</p>
 * <p>建议实现类采用无状态设计，避免使用实例变量存储中间状态。</p>
 *
 * <p>性能考虑：</p>
 * <ul>
 *   <li>解密操作应当高效，尤其是在批量处理大量数据时</li>
 *   <li>可以考虑使用缓存机制避免重复反射操作</li>
 *   <li>对于复杂对象，应当支持递归解密嵌套对象中的敏感字段</li>
 *   <li>可以考虑使用并行处理提高大批量数据的解密效率</li>
 * </ul>
 *
 * <p>安全考虑：</p>
 * <ul>
 *   <li>解密操作应在安全的服务器环境中进行，避免在客户端解密</li>
 *   <li>解密后的敏感数据应避免不必要的日志记录</li>
 *   <li>考虑对解密后的数据进行脱敏处理后再返回给前端</li>
 *   <li>定期更换加解密密钥，提高系统安全性</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在实体类上添加敏感数据注解
 * @GXSensitiveData
 * public class UserEntity {
 *     private Long id;
 *     private String username;
 *
 *     // 标记需要解密的字段
 *     @GXSensitiveField
 *     private String mobile;
 *
 *     @GXSensitiveField(deEncryptKey = "customKey")
 *     private String idCard;
 *
 *     // getter和setter方法
 * }
 *
 * // 2. 在Service层使用解密服务
 * @Service
 * public class UserServiceImpl implements UserService {
 *     @Autowired
 *     private UserMapper userMapper;
 *
 *     @Autowired
 *     private GXSensitiveDataDecryptService decryptService;
 *
 *     @Override
 *     public UserEntity getUserById(Long id) {
 *         // 从数据库查询（数据库中存储的是加密后的敏感数据）
 *         UserEntity user = userMapper.selectById(id);
 *
 *         try {
 *             // 对查询结果进行解密处理
 *             return decryptService.decrypt(user);
 *         } catch (IllegalAccessException e) {
 *             log.error("解密用户数据失败", e);
 *             return user; // 解密失败时返回原始数据
 *         }
 *     }
 *
 *     @Override
 *     public List<UserEntity> listUsers() {
 *         // 查询用户列表
 *         List<UserEntity> users = userMapper.selectList(null);
 *
 *         try {
 *             // 对集合对象进行解密处理
 *             return decryptService.decrypt(users);
 *         } catch (IllegalAccessException e) {
 *             log.error("批量解密用户数据失败", e);
 *             return users; // 解密失败时返回原始数据
 *         }
 *     }
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 * @see cn.maple.core.framework.annotation.GXSensitiveData 敏感数据类注解
 * @see cn.maple.core.framework.annotation.GXSensitiveField 敏感字段注解
 * @see cn.maple.core.framework.service.GXSensitiveDataEncryptService 敏感数据加密服务接口
 */
public interface GXSensitiveDataDecryptService {
    /**
     * 解密对象中的敏感字段
     * <p>
     * 该方法会自动识别对象中标记了{@link cn.maple.core.framework.annotation.GXSensitiveField}注解的字段，
     * 并使用配置的解密算法和密钥对这些字段进行解密处理。支持处理单个对象、集合对象以及嵌套对象。
     * </p>
     * <p>
     * 解密过程说明：
     * 1. 检查对象是否标记了{@link cn.maple.core.framework.annotation.GXSensitiveData}注解
     * 2. 通过反射获取对象的所有字段
     * 3. 识别标记了{@link cn.maple.core.framework.annotation.GXSensitiveField}注解的字段
     * 4. 根据注解配置的解密算法和密钥对字段值进行解密
     * 5. 将解密后的值设置回对象
     * </p>
     * <p>
     * 实现细节：
     * - 对于字符串类型的字段，直接进行解密处理
     * - 对于集合类型的字段，遍历集合中的每个元素并递归处理
     * - 对于数组类型的字段，遍历数组中的每个元素并递归处理
     * - 对于自定义对象类型的字段，递归处理该对象的所有字段
     * - 对于基本类型或不需要解密的字段，保持原值不变
     * </p>
     * <p>
     * 异常处理策略：
     * - 当字段不可访问时，会抛出IllegalAccessException异常
     * - 实现类应当考虑捕获并处理解密过程中可能出现的其他异常
     * - 对于解密失败的字段，建议保留原始加密值，避免数据丢失
     * </p>
     * <p>
     * 性能优化建议：
     * - 实现类可以考虑缓存字段的注解信息，避免重复反射操作
     * - 对于频繁解密的相同类型对象，可以缓存其字段结构信息
     * - 对于大批量数据，可以考虑并行处理提高效率
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 解密单个对象
     * UserEntity user = userMapper.selectById(1L);
     * decryptService.decrypt(user);
     *
     * // 解密集合对象
     * List<OrderEntity> orders = orderMapper.selectByUserId(userId);
     * decryptService.decrypt(orders);
     * </pre>
     * </p>
     *
     * @param result 需要解密的对象，可以是单个实体对象或集合对象
     * @return 解密后的对象，与输入对象是同一个实例
     * @throws IllegalAccessException 当字段不可访问时抛出此异常，通常是因为字段是私有的且没有对应的访问方法
     */
    <T> T decrypt(T result) throws IllegalAccessException;
}