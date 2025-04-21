package cn.maple.core.framework.service.impl;

import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.annotation.GXSensitiveField;
import cn.maple.core.framework.service.GXSensitiveDataEncryptService;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * 敏感数据加密服务实现类
 * <p>
 * 本类负责对标注了{@link GXSensitiveField}注解的字段进行加密处理。
 * 通过反射机制识别并处理带有该注解的字段，调用相应的加密服务进行数据加密。
 * 目前仅支持String类型字段的加密。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 1. 定义包含敏感字段的实体类
 * public class UserDTO {
 *     @GXSensitiveField
 *     private String idCard;  // 将被自动加密
 *
 *     private String name;     // 不会被加密
 *
 *     // getter/setter...
 * }
 *
 * // 2. 在服务层使用
 * @Autowired
 * private GXSensitiveDataEncryptService encryptService;
 *
 * public void processUser(UserDTO user) {
 *     // 获取类的所有字段
 *     Field[] fields = user.getClass().getDeclaredFields();
 *     // 加密标记了@GXSensitiveField注解的字段
 *     encryptService.encrypt(fields, user);
 *     // 继续处理业务逻辑...
 * }
 * </pre>
 *
 * @author britton <britton@126.com>
 */
@Service
public class GXSensitiveDataEncryptServiceImpl implements GXSensitiveDataEncryptService {
    /**
     * 对标注了{@link GXSensitiveField}注解的字段进行加密处理
     * <p>
     * 遍历对象的所有字段，识别带有{@link GXSensitiveField}注解的字段，
     * 并调用注解中指定的加密服务和算法对字段值进行加密。
     * 当前版本仅支持String类型字段的加密。
     * </p>
     *
     * @param declaredFields 需要处理的字段数组，通常是对象的所有声明字段
     * @param paramsObject   需要处理的对象实例
     * @return 处理后的对象实例
     * @throws IllegalAccessException 当字段访问权限不足时抛出
     */
    @Override
    public <T> T encrypt(Field[] declaredFields, T paramsObject) throws IllegalAccessException {
        if (Objects.isNull(paramsObject) || Objects.isNull(declaredFields)) {
            return paramsObject;
        }

        for (Field field : declaredFields) {
            try {
                GXSensitiveField sensitiveField = field.getAnnotation(GXSensitiveField.class);
                if (Objects.nonNull(sensitiveField)) {
                    final Field accessible = ReflectUtil.setAccessible(field);
                    Object object = accessible.get(paramsObject);
                    // 只实现String类型的加密
                    if (Objects.nonNull(object) && object instanceof String value) {
                        final Class<?> serviceClazz = sensitiveField.serviceClazz();
                        final String encryptAlgorithm = sensitiveField.encryptAlgorithm();
                        final String deEncryptKey = sensitiveField.deEncryptKey();
                        final String[] params = sensitiveField.params();

                        // 获取加密服务实例
                        final Object bean = GXSpringContextUtils.getBean(serviceClazz);
                        if (Objects.nonNull(bean)) {
                            // 调用加密方法
                            final Object encryptedValue = ReflectUtil.invoke(bean, encryptAlgorithm, value, deEncryptKey, params);
                            // 将加密后的值设置回对象
                            if (Objects.nonNull(encryptedValue)) {
                                ReflectUtil.setFieldValue(paramsObject, accessible, encryptedValue);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 记录异常但不抛出，保证单个字段加密失败不影响整体流程
                // 这里可以添加日志记录
                continue;
            }
        }
        return paramsObject;
    }
}
