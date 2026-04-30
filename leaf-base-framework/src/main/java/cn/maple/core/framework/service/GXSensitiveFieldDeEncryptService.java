package cn.maple.core.framework.service;

public interface GXSensitiveFieldDeEncryptService {
    String encryptAlgorithm(String dataStr, String key, String... params);

    String decryptAlgorithm(String dataStr, String key, String... params);
}
