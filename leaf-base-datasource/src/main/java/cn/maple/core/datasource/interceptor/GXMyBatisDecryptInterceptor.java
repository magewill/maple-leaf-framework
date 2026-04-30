package cn.maple.core.datasource.interceptor;

import cn.maple.core.framework.annotation.GXSensitiveData;
import cn.maple.core.framework.service.GXSensitiveDataDecryptService;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.*;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Intercepts({@Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})})
public class GXMyBatisDecryptInterceptor implements Interceptor {
    private final ConcurrentHashMap<Class<?>, Boolean> annotationCache = new ConcurrentHashMap<>();

    @Resource
    private GXSensitiveDataDecryptService sensitiveDataDecryptService;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object resultObject = invocation.proceed();
        if (Objects.isNull(resultObject)) {
            return null;
        }
        try {
            if (resultObject instanceof List<?> resultList) {
                if (!CollectionUtils.isEmpty(resultList) && Objects.nonNull(resultList.getFirst())) {
                    Object firstItem = resultList.getFirst();
                    if (checkNeedToDecrypt(firstItem)) {
                        log.debug("检测到敏感数据列表结果: {}, 执行批量解密处理",
                                firstItem.getClass().getName());
                        for (Object result : resultList) {
                            sensitiveDataDecryptService.decrypt(result);
                        }
                    }
                }
            } else {
                if (checkNeedToDecrypt(resultObject)) {
                    log.debug("检测到敏感数据单个结果: {}, 执行解密处理",
                            resultObject.getClass().getName());
                    sensitiveDataDecryptService.decrypt(resultObject);
                }
            }
        } catch (Exception e) {
            log.error("敏感数据解密过程发生异常: {}", e.getMessage(), e);
        }
        return resultObject;
    }

    private boolean checkNeedToDecrypt(Object object) {
        if (object == null) {
            return false;
        }

        Class<?> objectClass = object.getClass();

        Boolean hasAnnotation = annotationCache.get(objectClass);
        if (hasAnnotation == null) {
            GXSensitiveData sensitiveData = AnnotationUtils.findAnnotation(objectClass, GXSensitiveData.class);
            hasAnnotation = Objects.nonNull(sensitiveData);
            annotationCache.put(objectClass, hasAnnotation);
        }

        return hasAnnotation;
    }

    @Override
    public Object plugin(Object target) {
        // 只拦截ResultSetHandler类型的对象
        if (target instanceof ResultSetHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        // 当前实现不需要额外配置
    }
}
