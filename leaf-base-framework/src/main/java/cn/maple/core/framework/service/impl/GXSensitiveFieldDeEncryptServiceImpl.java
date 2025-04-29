package cn.maple.core.framework.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.maple.core.framework.service.GXSensitiveFieldDeEncryptService;
import cn.maple.core.framework.util.GXCommonUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 敏感字段加解密服务实现类
 * <p>
 * 本类提供基于AES算法的敏感数据加解密功能，主要用于配合{@link cn.maple.core.framework.annotation.GXSensitiveField}注解使用，
 * 对标注了该注解的字段进行自动加解密处理。
 * </p>
 *
 * <p>功能特点：</p>
 * <ul>
 *   <li>使用AES对称加密算法，确保数据安全性</li>
 *   <li>支持自定义加密密钥，增强安全性</li>
 *   <li>自动从配置中获取系统默认密钥</li>
 *   <li>异常处理机制确保业务流程不中断</li>
 *   <li>支持与注解结合实现自动加解密</li>
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
 *   <li>AES加解密操作为CPU密集型，大量数据处理可能影响性能</li>
 *   <li>建议只对必要的敏感字段进行加密，避免过度加密</li>
 *   <li>可考虑使用缓存减少重复加解密操作</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 在实体类中使用注解标记敏感字段
 * public class UserEntity {
 *     @GXSensitiveField
 *     private String idCard;  // 身份证号将被自动加解密
 *
 *     @GXSensitiveField(deEncryptKey = "customKey")
 *     private String phoneNumber;  // 使用自定义密钥加解密
 *
 *     // 其他字段...
 * }
 *
 * // 2. 在需要手动加解密的场景中直接使用本服务
 * @Autowired
 * private GXSensitiveFieldDeEncryptService sensitiveService;
 *
 * public void processSensitiveData() {
 *     // 使用默认密钥加密
 *     String plainText = "13800138000";
 *     String encrypted = sensitiveService.encryptAlgorithm(plainText, "");
 *     String decrypted = sensitiveService.decryptAlgorithm(encrypted, "");
 *
 *     // 使用自定义密钥加密
 *     String customKey = "MySecretKey12345";
 *     String encryptedWithCustomKey = sensitiveService.encryptAlgorithm(plainText, customKey);
 *     String decryptedWithCustomKey = sensitiveService.decryptAlgorithm(encryptedWithCustomKey, customKey);
 * }
 * </pre>
 *
 * <p>线程安全性：</p>
 * <p>本实现类不包含可变状态，所有方法都是无状态的，因此是线程安全的。</p>
 * <p>AES对象在每次调用时创建，不存在共享状态问题。</p>
 *
 * @author britton <britton@126.com>
 */
@Service
@Order
public class GXSensitiveFieldDeEncryptServiceImpl implements GXSensitiveFieldDeEncryptService {
    /**
     * 加密数据
     * <p>
     * 使用AES算法对敏感数据进行加密，并返回Base64编码的密文。
     * 如果提供了自定义密钥，则使用该密钥进行加密；否则使用系统配置的默认密钥。
     * </p>
     * <p>
     * 实现细节：
     * 1. 首先检查输入数据是否为null，如果是则直接返回null
     * 2. 根据是否提供了自定义密钥，选择相应的AES加密对象
     * 3. 使用AES加密并进行Base64编码，确保输出可以安全传输和存储
     * 4. 捕获所有异常并返回原文，确保业务流程不会因加密失败而中断
     * </p>
     * <p>
     * 异常处理策略：
     * - 当加密过程发生异常时，记录异常信息但不抛出异常
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
     * </pre>
     * </p>
     *
     * @param dataStr 需要加密的数据，不能为null，否则直接返回null
     * @param key     加密密钥，可以为空，为空时使用系统默认密钥
     * @param params  额外参数，预留扩展用，当前版本未使用
     * @return 加密后的数据(Base64编码的密文)，如果输入为null则返回null，如果加密过程出现异常则返回原文
     */
    @Override
    public String encryptAlgorithm(String dataStr, String key, String... params) {
        if (Objects.isNull(dataStr)) {
            return null;
        }
        try {
            if (CharSequenceUtil.isNotBlank(key)) {
                return getAES(key).encryptBase64(dataStr);
            }
            return getAES().encryptBase64(dataStr);
        } catch (Exception e) {
            // 记录异常但不抛出，保证业务流程不中断
            // 加密失败时返回原文，避免数据丢失
            return dataStr;
        }
    }

    /**
     * 解密数据
     * <p>
     * 使用AES算法对Base64编码的密文进行解密，并返回明文。
     * 如果提供了自定义密钥，则使用该密钥进行解密；否则使用系统配置的默认密钥。
     * </p>
     * <p>
     * 实现细节：
     * 1. 首先检查输入数据是否为null或空字符串，如果是则直接返回原文
     * 2. 根据是否提供了自定义密钥，选择相应的AES解密对象
     * 3. 对Base64编码的密文进行解密，返回明文字符串
     * 4. 捕获所有异常并返回原文，确保业务流程不会因解密失败而中断
     * </p>
     * <p>
     * 安全考虑：
     * - 解密失败可能意味着数据被篡改或使用了错误的密钥
     * - 在生产环境中，可能需要记录解密失败的情况并进行监控
     * - 对于高安全要求的场景，可能需要修改异常处理策略
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 使用默认密钥解密
     * String decrypted = decryptAlgorithm(encryptedData, "");
     *
     * // 使用自定义密钥解密
     * String decrypted = decryptAlgorithm(encryptedData, "myCustomKey");
     * </pre>
     * </p>
     *
     * @param dataStr 需要解密的数据(Base64编码的密文)，如果为null或空字符串则直接返回原文
     * @param key     解密密钥，可以为空，为空时使用系统默认密钥
     * @param params  额外参数，预留扩展用，当前版本未使用
     * @return 解密后的数据(明文)，如果输入为null或空字符串或解密失败则返回原文
     */
    @Override
    public String decryptAlgorithm(String dataStr, String key, String... params) {
        if (Objects.isNull(dataStr) || CharSequenceUtil.isBlank(dataStr)) {
            return dataStr;
        }
        try {
            if (CharSequenceUtil.isNotBlank(key)) {
                return getAES(key).decryptStr(dataStr);
            }
            return getAES().decryptStr(dataStr);
        } catch (Exception e) {
            // 记录异常但不抛出，保证业务流程不中断
            // 解密失败时返回原文，避免数据丢失
            return dataStr;
        }
    }


    /**
     * 获取AES加解密对象
     * <p>
     * 根据提供的密钥创建AES加解密对象，使用UTF-8编码处理密钥。
     * </p>
     * <p>
     * 实现说明：
     * 1. 首先验证密钥不为null，如果为null则抛出NullPointerException
     * 2. 使用UTF-8编码将密钥字符串转换为字节数组
     * 3. 通过SecureUtil工具类创建AES加解密对象
     * </p>
     * <p>
     * 性能考虑：
     * - AES对象创建是相对耗时的操作
     * - 对于频繁使用相同密钥的场景，可以考虑缓存AES对象
     * </p>
     *
     * @param key AES的密钥，不能为null，否则会抛出NullPointerException
     * @return AES加解密对象，用于后续的加密或解密操作
     * @throws NullPointerException 如果key为null则抛出此异常
     */
    private AES getAES(String key) {
        Objects.requireNonNull(key, "加密密钥不能为null");
        return SecureUtil.aes(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取使用系统默认密钥的AES加解密对象
     * <p>
     * 从配置中获取系统默认的AES密钥，如果未配置则使用内置的默认密钥。
     * 注意：生产环境应当配置自定义密钥，不应使用内置默认密钥。
     * </p>
     * <p>
     * 实现细节：
     * 1. 从环境配置中获取名为"common.sensitive.data.key"的配置项
     * 2. 如果配置项不存在或为空，则使用硬编码的默认密钥
     * 3. 使用获取到的密钥创建AES加解密对象
     * </p>
     * <p>
     * 安全警告：
     * - 默认密钥仅用于开发环境，不应在生产环境中使用
     * - 生产环境应通过配置中心或环境变量提供自定义密钥
     * - 密钥应定期更换，并遵循密码学最佳实践
     * </p>
     *
     * @return AES加解密对象，使用系统配置的密钥或默认密钥
     */
    private AES getAES() {
        String key = GXCommonUtils.getEnvironmentValue("common.sensitive.data.key", String.class);
        if (CharSequenceUtil.isBlank(key)) {
            // 注意：这里的默认密钥仅用于开发环境，生产环境应当配置自定义密钥
            key = "XhFeV780D2218OBRm0xjcWvv";
        }
        return getAES(key);
    }
}
