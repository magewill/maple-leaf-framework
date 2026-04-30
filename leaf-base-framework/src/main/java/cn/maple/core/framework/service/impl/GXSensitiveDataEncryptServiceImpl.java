package cn.maple.core.framework.service.impl;

import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.annotation.GXSensitiveField;
import cn.maple.core.framework.service.GXSensitiveDataEncryptService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Objects;

@Service
public class GXSensitiveDataEncryptServiceImpl implements GXSensitiveDataEncryptService {
    @Override
    public <T> T encrypt(Field[] declaredFields, T paramsObject) {
        if (Objects.isNull(paramsObject) || Objects.isNull(declaredFields)) {
            return paramsObject;
        }

        for (Field field : declaredFields) {
            try {
                GXSensitiveField sensitiveField = field.getAnnotation(GXSensitiveField.class);
                if (Objects.nonNull(sensitiveField)) {
                    final Field accessible = ReflectUtil.setAccessible(field);
                    Object object = accessible.get(paramsObject);
                    if (Objects.nonNull(object) && object instanceof String value) {
                        final Class<?> serviceClazz = sensitiveField.serviceClazz();
                        final String encryptAlgorithm = sensitiveField.encryptAlgorithm();
                        final String deEncryptKey = sensitiveField.deEncryptKey();
                        final String[] params = sensitiveField.params();

                        final Object bean = GXSpringContextUtils.getBean(serviceClazz);
                        if (Objects.nonNull(bean)) {
                            final Object encryptedValue = ReflectUtil.invoke(bean, encryptAlgorithm, value, deEncryptKey, params);
                            if (Objects.nonNull(encryptedValue)) {
                                ReflectUtil.setFieldValue(paramsObject, accessible, encryptedValue);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return paramsObject;
    }
}
