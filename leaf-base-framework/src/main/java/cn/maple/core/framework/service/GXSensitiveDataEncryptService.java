package cn.maple.core.framework.service;

import java.lang.reflect.Field;

public interface GXSensitiveDataEncryptService {
    <T> T encrypt(Field[] declaredFields, T paramsObject) throws IllegalAccessException;

    default <T> T encrypt(T paramsObject) throws IllegalAccessException {
        return paramsObject;
    }
}
