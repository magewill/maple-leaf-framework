package cn.maple.core.framework.service;

public interface GXSensitiveDataDecryptService {
    <T> T decrypt(T result) throws IllegalAccessException;
}