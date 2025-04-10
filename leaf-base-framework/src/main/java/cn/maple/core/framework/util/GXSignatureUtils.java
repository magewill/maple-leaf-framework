package cn.maple.core.framework.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 数据签名工具类
 * <p>
 * 用于生成与PHP通信时的数据签名。
 * 此类实现了标准的签名生成算法，确保与PHP端的签名验证能够正确匹配。
 * 注意：此类不可随意更改，否则可能导致双方的数据签名不能匹配。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 准备待签名数据
 * Map<String, String> data = new HashMap<>();
 * data.put("name", "test");
 * data.put("age", "20");
 * 
 * // 生成签名
 * String signature = GXSignatureUtils.generateSignature(data, "your_api_key");
 * }
 * </pre>
 * </p>
 *
 * @author zj chen <britton@126.com>
 */
public class GXSignatureUtils {
    /**
     * 日志对象
     */
    private static final Logger logger = LoggerFactory.getLogger(GXSignatureUtils.class);

    /**
     * 私有构造函数，防止实例化
     */
    private GXSignatureUtils() {
    }

    /**
     * 生成签名
     * <p>
     * 根据指定的数据和API密钥生成签名。
     * 签名生成规则：
     * 1. 对参数名进行字典序排序
     * 2. 拼接参数名和参数值，格式为：key=value&
     * 3. 在末尾添加API密钥
     * 4. 对拼接后的字符串进行MD5加密
     * </p>
     *
     * @param data 待签名数据，key-value格式
     * @param key  API密钥
     * @return 生成的签名，大写形式
     * @throws IllegalArgumentException 当参数为null或空时抛出
     * @throws Exception 当签名生成过程中发生错误时抛出
     */
    public static String generateSignature(final Map<String, String> data, String key) throws Exception {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("待签名数据不能为空");
        }
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("API密钥不能为空");
        }

        Set<String> keySet = data.keySet();
        String[] keyArray = keySet.toArray(new String[0]);
        Arrays.sort(keyArray);
        StringBuilder sb = new StringBuilder();
        for (String k : keyArray) {
            if (k.equals("sign")) {
                continue;
            }
            String value = data.get(k);
            if (value != null && value.trim().length() > 0) {
                // 参数值为空，则不参与签名
                sb.append(k).append("=").append(value.trim()).append("&");
            }
        }
        sb.append("geoxus_sign_key=").append(key);
        logger.debug("待签名数据: {}", sb.toString());
        return md5(sb.toString()).toUpperCase();
    }

    /**
     * 生成MD5摘要
     * <p>
     * 使用MD5算法对输入字符串进行加密，返回大写形式的十六进制字符串。
     * </p>
     *
     * @param data 待处理数据
     * @return MD5加密结果，大写形式
     * @throws NoSuchAlgorithmException 当MD5算法不可用时抛出
     * @throws IllegalArgumentException 当输入数据为null或空时抛出
     */
    private static String md5(String data) throws NoSuchAlgorithmException {
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("待加密数据不能为空");
        }

        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] array = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte item : array) {
            sb.append(Integer.toHexString((item & 0xFF) | 0x100), 1, 3);
        }
        return sb.toString().toUpperCase();
    }
}
