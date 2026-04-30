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

@Service
@Order
public class GXSensitiveFieldDeEncryptServiceImpl implements GXSensitiveFieldDeEncryptService {
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
            return dataStr;
        }
    }

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
            return dataStr;
        }
    }

    private AES getAES(String key) {
        Objects.requireNonNull(key, "加密密钥不能为null");
        return SecureUtil.aes(key.getBytes(StandardCharsets.UTF_8));
    }

    private AES getAES() {
        String key = GXCommonUtils.getEnvironmentValue("common.sensitive.data.key", String.class);
        if (CharSequenceUtil.isBlank(key)) {
            key = "XhFeV780D2218OBRm0xjcWvv";
        }
        return getAES(key);
    }
}
