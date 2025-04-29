package cn.maple.core.framework.service;

/**
 * 敏感字段加解密服务接口
 * <p>
 * 该接口提供敏感数据的加密和解密功能，用于保护系统中的敏感信息，如个人身份信息、
 * 联系方式、银行账号等。实现类应当提供高效且安全的加解密算法。
 * </p>
 *
 * <p>功能特点：</p>
 * <ul>
 *   <li>提供字符串级别的加解密操作</li>
 *   <li>支持自定义加密密钥</li>
 *   <li>支持默认系统密钥</li>
 *   <li>异常处理机制确保业务流程不中断</li>
 *   <li>与{@link cn.maple.core.framework.annotation.GXSensitiveField}注解配合使用</li>
 * </ul>
 *
 * <p>安全考虑：</p>
 * <ul>
 *   <li>生产环境应配置自定义密钥，避免使用默认密钥</li>
 *   <li>密钥应妥善保管，避免泄露</li>
 *   <li>敏感数据应限制访问权限</li>
 *   <li>加密数据传输应使用HTTPS等安全协议</li>
 * </ul>
 *
 * <p>性能说明：</p>
 * <ul>
 *   <li>加解密操作为CPU密集型，大量数据处理可能影响性能</li>
 *   <li>建议只对必要的敏感字段进行加密，避免过度加密</li>
 *   <li>可考虑使用缓存减少重复加解密操作</li>
 * </ul>
 *
 * <p>线程安全性：</p>
 * <p>接口的实现类应当保证线程安全，建议采用无状态设计，避免使用实例变量存储中间状态。</p>
 *
 * <p>使用场景：</p>
 * <ul>
 *   <li>数据库存储前的敏感字段加密</li>
 *   <li>从数据库读取后的敏感字段解密</li>
 *   <li>API接口传输中的敏感数据保护</li>
 *   <li>日志记录中的敏感信息保护</li>
 * </ul>
 *
 * @author britton <britton@126.com>
 * @see cn.maple.core.framework.annotation.GXSensitiveField 敏感字段注解
 * @see cn.maple.core.framework.annotation.GXSensitiveData 敏感数据类注解
 * @see cn.maple.core.framework.service.GXSensitiveDataEncryptService 敏感数据加密服务
 * @see cn.maple.core.framework.service.GXSensitiveDataDecryptService 敏感数据解密服务
 */
public interface GXSensitiveFieldDeEncryptService {
    /**
     * 加密数据
     * <p>
     * 使用指定的加密算法和密钥对敏感数据进行加密，并返回加密后的密文。
     * 如果提供了自定义密钥，则使用该密钥进行加密；否则使用系统配置的默认密钥。
     * </p>
     * <p>
     * 实现细节：
     * 1. 首先检查输入数据是否为null，如果是则直接返回null
     * 2. 根据是否提供了自定义密钥，选择相应的加密对象
     * 3. 使用加密算法进行加密，通常会进行Base64编码，确保输出可以安全传输和存储
     * 4. 捕获所有异常并返回原文，确保业务流程不会因加密失败而中断
     * </p>
     * <p>
     * 异常处理策略：
     * - 当加密过程发生异常时，实现类应记录异常信息但不抛出异常
     * - 返回原始数据，确保数据不会因加密失败而丢失
     * - 这种策略适用于加密是增强功能而非必要功能的场景
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 使用默认密钥加密
     * String encrypted = encryptAlgorithm("13800138000", "");
     *
     * // 使用自定义密钥加密
     * String encrypted = encryptAlgorithm("13800138000", "myCustomKey");
     *
     * // 使用额外参数
     * String encrypted = encryptAlgorithm("13800138000", "myCustomKey", "param1", "param2");
     * </pre>
     * </p>
     *
     * @param dataStr 需要加密的数据，不能为null，否则直接返回null
     * @param key     加密密钥，可以为空，为空时使用系统默认密钥
     * @param params  额外参数，预留扩展用，可用于传递特定的加密选项或配置
     * @return 加密后的数据(密文)，如果输入为null则返回null，如果加密过程出现异常则返回原文
     */
    String encryptAlgorithm(String dataStr, String key, String... params);

    /**
     * 解密数据
     * <p>
     * 使用指定的解密算法和密钥对密文进行解密，并返回解密后的明文。
     * 如果提供了自定义密钥，则使用该密钥进行解密；否则使用系统配置的默认密钥。
     * </p>
     * <p>
     * 实现细节：
     * 1. 首先检查输入数据是否为null或空字符串，如果是则直接返回原文
     * 2. 根据是否提供了自定义密钥，选择相应的解密对象
     * 3. 对密文进行解密，通常需要先进行Base64解码，然后再解密
     * 4. 捕获所有异常并返回原文，确保业务流程不会因解密失败而中断
     * </p>
     * <p>
     * 安全考虑：
     * - 解密失败可能意味着数据被篡改或使用了错误的密钥
     * - 在生产环境中，可能需要记录解密失败的情况并进行监控
     * - 对于高安全要求的场景，可能需要修改异常处理策略
     * </p>
     * <p>
     * 异常处理策略：
     * - 当解密过程发生异常时，实现类应记录异常信息但不抛出异常
     * - 返回原始数据，确保业务流程不中断
     * - 对于需要严格安全控制的场景，可能需要自定义实现类修改此行为
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 使用默认密钥解密
     * String decrypted = decryptAlgorithm(encryptedData, "");
     *
     * // 使用自定义密钥解密
     * String decrypted = decryptAlgorithm(encryptedData, "myCustomKey");
     *
     * // 使用额外参数
     * String decrypted = decryptAlgorithm(encryptedData, "myCustomKey", "param1", "param2");
     * </pre>
     * </p>
     *
     * @param dataStr 需要解密的数据（密文），如果为null或空字符串则直接返回原文
     * @param key     解密密钥，可以为空，为空时使用系统默认密钥
     * @param params  额外参数，预留扩展用，可用于传递特定的解密选项或配置
     * @return 解密后的数据(明文)，如果输入为null或空字符串或解密失败则返回原文
     */
    String decryptAlgorithm(String dataStr, String key, String... params);
}
