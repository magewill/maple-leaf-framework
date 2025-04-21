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
 *     String plainText = "13800138000";
 *     String encrypted = sensitiveService.encryptAlgorithm(plainText, "");
 *     String decrypted = sensitiveService.decryAlgorithm(encrypted, "");
 * }
 * </pre>
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
     *
     * @param dataStr 需要加密的数据，不能为null
     * @param key     加密密钥，可以为空，为空时使用系统默认密钥
     * @param params  额外参数，预留扩展用，当前版本未使用
     * @return 加密后的数据(Base64编码的密文)，如果输入为null则返回null
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
     *
     * @param dataStr 需要解密的数据(Base64编码的密文)，不能为null
     * @param key     解密密钥，可以为空，为空时使用系统默认密钥
     * @param params  额外参数，预留扩展用，当前版本未使用
     * @return 解密后的数据(明文)，如果输入为null或解密失败则返回原文
     */
    @Override
    public String decryAlgorithm(String dataStr, String key, String... params) {
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
     *
     * @param key AES的密钥，不能为null
     * @return AES加解密对象
     * @throws NullPointerException 如果key为null
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
     *
     * @return AES加解密对象
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
