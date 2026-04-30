package cn.maple.core.framework.service.impl;

import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.annotation.GXSensitiveField;
import cn.maple.core.framework.service.GXSensitiveDataDecryptService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Objects;

@Service
public class GXSensitiveDataDecryptServiceImpl implements GXSensitiveDataDecryptService {
    @Override
    public <T> T decrypt(T result) {
        if (Objects.isNull(result)) {
            return null;
        }

        try {
            Class<?> resultClass = result.getClass();
            Field[] declaredFields = resultClass.getDeclaredFields();

            for (Field field : declaredFields) {
                try {
                    GXSensitiveField sensitiveField = field.getAnnotation(GXSensitiveField.class);
                    if (Objects.nonNull(sensitiveField)) {
                        final Field accessible = ReflectUtil.setAccessible(field);
                        Object object = accessible.get(result);
                        if (Objects.nonNull(object) && object instanceof String value) {
                            final Class<?> serviceClazz = sensitiveField.serviceClazz();
                            final String decryAlgorithm = sensitiveField.decryAlgorithm();
                            final String deEncryptKey = sensitiveField.deEncryptKey();
                            final String[] params = sensitiveField.params();

                            final Object bean = GXSpringContextUtils.getBean(serviceClazz);
                            if (Objects.nonNull(bean)) {
                                final Object decryptedValue = ReflectUtil.invoke(bean, decryAlgorithm, value, deEncryptKey, params);
                                if (Objects.nonNull(decryptedValue)) {
                                    ReflectUtil.setFieldValue(result, accessible, decryptedValue);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }

        return result;
    }
}
