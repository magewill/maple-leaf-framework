package cn.maple.core.datasource.interceptor;

import cn.maple.core.framework.annotation.GXSensitiveData;
import cn.maple.core.framework.service.GXSensitiveDataEncryptService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis敏感数据加密拦截器
 * <p>
 * 该拦截器用于在MyBatis执行SQL前自动加密标记了{@link GXSensitiveData}注解的实体类中的敏感字段。
 * 通过拦截{@link ParameterHandler}的setParameters方法，在参数设置到PreparedStatement前进行加密处理。
 * </p>
 *
 * @author britton <britton@126.com>
 */

@Slf4j
@Component
@Intercepts({
        @Signature(type = ParameterHandler.class, method = "setParameters", args = PreparedStatement.class),
})
public class GXMyBatisEncryptInterceptor implements Interceptor {
    /**
     * 注解缓存，用于提高性能，避免重复反射查找相同类的注解
     * <p>
     * 使用ConcurrentHashMap确保线程安全
     * </p>
     */
    private final ConcurrentHashMap<Class<?>, Boolean> annotationCache = new ConcurrentHashMap<>();
    /**
     * 敏感数据加密服务
     * <p>
     * 用于执行实际的字段加密操作，通过Spring的依赖注入机制获取实现类
     * </p>
     */
    @Resource
    private GXSensitiveDataEncryptService sensitiveDataEncryptService;

    /**
     * 拦截方法，在MyBatis执行SQL前处理参数
     * <p>
     * 该方法会检查参数对象是否标记了{@link GXSensitiveData}注解，
     * 如果是，则调用加密服务对敏感字段进行加密处理
     * </p>
     *
     * @param invocation MyBatis拦截器方法调用对象
     * @return 处理后的结果对象
     * @throws Throwable 处理过程中可能抛出的异常
     */
    @Override
    @SuppressWarnings("all")
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            ParameterHandler parameterHandler = (ParameterHandler) invocation.getTarget();
            Field parameterField = ReflectionUtils.findField(parameterHandler.getClass(), "parameterObject");
            if (parameterField == null) {
                log.warn("无法找到parameterObject字段，跳过敏感数据加密处理");
                return invocation.proceed();
            }
            ReflectionUtils.makeAccessible(parameterField);
            Object parameterObject = parameterField.get(parameterHandler);
            if (parameterObject == null) {
                return invocation.proceed();
            }
            Class<?> parameterObjectClass = parameterObject.getClass();
            Boolean hasAnnotation = annotationCache.get(parameterObjectClass);
            if (hasAnnotation == null) {
                GXSensitiveData sensitiveData = AnnotationUtils.findAnnotation(parameterObjectClass, GXSensitiveData.class);
                hasAnnotation = Objects.nonNull(sensitiveData);
                annotationCache.put(parameterObjectClass, hasAnnotation);
            }
            if (hasAnnotation) {
                log.debug("检测到敏感数据类: {}, 执行加密处理", parameterObjectClass.getName());
                List<Field> allFields = new ArrayList<>();
                Class<?> currentClass = parameterObjectClass;
                while (currentClass != null && currentClass != Object.class) {
                    allFields.addAll(Arrays.asList(currentClass.getDeclaredFields()));
                    currentClass = currentClass.getSuperclass();
                }
                sensitiveDataEncryptService.encrypt(allFields.toArray(new Field[0]), parameterObject);
            }
        } catch (Exception e) {
            log.error("敏感数据加密过程发生异常: {}", e.getMessage(), e);
        }
        return invocation.proceed();
    }

    /**
     * 包装目标对象，确保拦截器被添加到拦截器链中
     * <p>
     * 该方法是MyBatis拦截器接口的必要实现，用于将当前拦截器包装到目标对象上
     * </p>
     *
     * @param target 要拦截的目标对象
     * @return 包装后的对象
     */
    @Override
    public Object plugin(Object target) {
        // 只拦截ParameterHandler类型的对象
        if (target instanceof ParameterHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    /**
     * 设置拦截器属性
     * <p>
     * 可通过此方法接收配置参数，当前实现不需要额外配置
     * </p>
     *
     * @param properties 配置属性
     */
    @Override
    public void setProperties(Properties properties) {
        // 当前实现不需要额外配置
    }
}
