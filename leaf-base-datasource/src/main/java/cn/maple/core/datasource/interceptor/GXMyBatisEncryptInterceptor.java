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

@Slf4j
@Component
@Intercepts({
        @Signature(type = ParameterHandler.class, method = "setParameters", args = PreparedStatement.class),
})
public class GXMyBatisEncryptInterceptor implements Interceptor {
    private final ConcurrentHashMap<Class<?>, Boolean> annotationCache = new ConcurrentHashMap<>();

    @Resource
    private GXSensitiveDataEncryptService sensitiveDataEncryptService;

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

    @Override
    public Object plugin(Object target) {
        // 只拦截ParameterHandler类型的对象
        if (target instanceof ParameterHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        // 当前实现不需要额外配置
    }
}
