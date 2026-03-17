package cn.maple.core.datasource.interceptor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.TypeUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis敏感数据解密拦截器
 * <p>
 * 该拦截器用于在MyBatis查询结果返回前自动解密标记了{@link GXSensitiveData}注解的实体类中的敏感字段。
 * 通过拦截{@link ResultSetHandler}的handleResultSets方法，在结果集处理后进行解密处理。
 * </p>
 *
 * @author britton <britton@126.com>
 */

@Slf4j
@Component
@Intercepts({@Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})})
public class GXMyBatisDecryptInterceptor implements Interceptor {
    /**
     * 注解缓存，用于提高性能，避免重复反射查找相同类的注解
     * <p>
     * 使用ConcurrentHashMap确保线程安全
     * </p>
     */
    private final ConcurrentHashMap<Class<?>, Boolean> annotationCache = new ConcurrentHashMap<>();
    /**
     * 敏感数据解密服务
     * <p>
     * 用于执行实际的字段解密操作，通过Spring的依赖注入机制获取实现类
     * </p>
     */
    @Resource
    private GXSensitiveDataDecryptService sensitiveDataDecryptService;

    /**
     * 拦截方法，在MyBatis查询结果返回前处理结果集
     * <p>
     * 该方法会检查结果对象是否标记了{@link GXSensitiveData}注解，
     * 如果是，则调用解密服务对敏感字段进行解密处理
     * </p>
     *
     * @param invocation MyBatis拦截器方法调用对象
     * @return 处理后的结果对象
     * @throws Throwable 处理过程中可能抛出的异常
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            // 执行原方法，获取查询结果
            Object resultObject = invocation.proceed();
            if (Objects.isNull(resultObject)) {
                return null;
            }

            // 处理List类型的查询结果（selectList方法的返回值）
            if (ArrayList.class.getName().equalsIgnoreCase(TypeUtil.getClass(resultObject.getClass()).getName())) {
                List<?> resultList = Convert.convert(new TypeReference<>() {
                }, resultObject);

                // 检查列表是否为空，以及第一个元素是否需要解密
                if (!CollectionUtils.isEmpty(resultList) && Objects.nonNull(resultList.get(0))) {
                    // 获取第一个元素的类型，检查是否需要解密
                    Object firstItem = resultList.get(0);
                    if (checkNeedToDecrypt(firstItem)) {
                        log.debug("检测到敏感数据列表结果: {}, 执行批量解密处理", firstItem.getClass().getName());
                        // 对列表中的每个元素进行解密
                        for (Object result : resultList) {
                            sensitiveDataDecryptService.decrypt(result);
                        }
                    }
                }
            }
            // 处理单个对象的查询结果（selectOne方法的返回值）
            else {
                if (checkNeedToDecrypt(resultObject)) {
                    log.debug("检测到敏感数据单个结果: {}, 执行解密处理", resultObject.getClass().getName());
                    sensitiveDataDecryptService.decrypt(resultObject);
                }
            }

            return resultObject;
        } catch (Exception e) {
            // 记录异常但不中断流程，确保查询结果能够正常返回
            log.error("敏感数据解密过程发生异常: {}", e.getMessage(), e);
            return invocation.proceed();
        }
    }

    /**
     * 检查对象是否需要进行解密处理
     * <p>
     * 通过检查对象类上是否标记了{@link GXSensitiveData}注解来判断
     * 使用缓存提高性能，避免重复反射操作
     * </p>
     *
     * @param object 要检查的对象
     * @return 如果对象需要解密则返回true，否则返回false
     */
    private boolean checkNeedToDecrypt(Object object) {
        if (object == null) {
            return false;
        }

        // 获取对象类型
        Class<?> objectClass = object.getClass();

        // 从缓存中查找是否已检查过该类型
        Boolean hasAnnotation = annotationCache.get(objectClass);
        if (hasAnnotation == null) {
            // 首次检查，查找注解并缓存结果
            GXSensitiveData sensitiveData = AnnotationUtils.findAnnotation(objectClass, GXSensitiveData.class);
            hasAnnotation = Objects.nonNull(sensitiveData);
            annotationCache.put(objectClass, hasAnnotation);
        }

        return hasAnnotation;
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
        // 只拦截ResultSetHandler类型的对象
        if (target instanceof ResultSetHandler) {
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
