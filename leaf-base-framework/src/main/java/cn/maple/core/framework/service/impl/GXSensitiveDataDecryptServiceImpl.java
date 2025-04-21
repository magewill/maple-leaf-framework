package cn.maple.core.framework.service.impl;

import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.annotation.GXSensitiveField;
import cn.maple.core.framework.service.GXSensitiveDataDecryptService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * 敏感数据解密服务实现类
 * <p>
 * 本类负责对标注了{@link GXSensitiveField}注解的字段进行解密处理。
 * 通过反射机制识别并处理带有该注解的字段，调用相应的解密服务进行数据解密。
 * 目前仅支持String类型字段的解密。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 定义包含敏感字段的实体类
 * public class UserEntity {
 *     @GXSensitiveField
 *     private String idCard;  // 将被自动解密
 *
 *     private String name;     // 不会被解密
 *
 *     // getter/setter...
 * }
 *
 * // 2. 在服务层使用
 * @Autowired
 * private GXSensitiveDataDecryptService decryptService;
 *
 * public UserEntity getUser(Long userId) {
 *     // 从数据库获取加密的用户数据
 *     UserEntity user = userMapper.selectById(userId);
 *     // 解密标记了@GXSensitiveField注解的字段
 *     return decryptService.decrypt(user);
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 */
@Service
public class GXSensitiveDataDecryptServiceImpl implements GXSensitiveDataDecryptService {
    /**
     * 对标注了{@link GXSensitiveField}注解的字段进行解密处理
     * <p>
     * 遍历对象的所有字段，识别带有{@link GXSensitiveField}注解的字段，
     * 并调用注解中指定的解密服务和算法对字段值进行解密。
     * 当前版本仅支持String类型字段的解密。
     * </p>
     *
     * @param result 需要处理的对象实例
     * @return 处理后的对象实例
     * @throws IllegalAccessException 当字段访问权限不足时抛出
     */
    @Override
    public <T> T decrypt(T result) throws IllegalAccessException {
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
                        // 只实现对String的解密
                        if (Objects.nonNull(object) && object instanceof String value) {
                            final Class<?> serviceClazz = sensitiveField.serviceClazz();
                            final String decryAlgorithm = sensitiveField.decryAlgorithm();
                            final String deEncryptKey = sensitiveField.deEncryptKey();
                            final String[] params = sensitiveField.params();

                            // 获取解密服务实例
                            final Object bean = GXSpringContextUtils.getBean(serviceClazz);
                            if (Objects.nonNull(bean)) {
                                // 调用解密方法
                                final Object decryptedValue = ReflectUtil.invoke(bean, decryAlgorithm, value, deEncryptKey, params);
                                // 将解密后的值设置回对象
                                if (Objects.nonNull(decryptedValue)) {
                                    ReflectUtil.setFieldValue(result, accessible, decryptedValue);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 记录异常但不抛出，保证单个字段解密失败不影响整体流程
                    // 这里可以添加日志记录
                    continue;
                }
            }
        } catch (Exception e) {
            // 处理可能的反射异常，但不影响原始数据返回
            // 这里可以添加日志记录
        }

        return result;
    }
}
