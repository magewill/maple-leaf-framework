package cn.maple.core.framework.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class GXSignatureUtils {
    private static final Logger logger = LoggerFactory.getLogger(GXSignatureUtils.class);

    private GXSignatureUtils() {
    }

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
                sb.append(k).append("=").append(value.trim()).append("&");
            }
        }
        sb.append("geoxus_sign_key=").append(key);
        logger.debug("待签名数据: {}", sb);
        return md5(sb.toString()).toUpperCase();
    }

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
