package cn.maple.core.framework.aspect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.annotation.GXCacheEvict;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.service.GXBaseCacheLockService;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
@Slf4j
public class GXCacheEvictAspect {
    private static final StandardReflectionParameterNameDiscoverer parameterNameDiscover = new StandardReflectionParameterNameDiscoverer();

    private static final ConcurrentHashMap<String, List<String>> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXCacheEvict) " + "|| @within(cn.maple.core.framework.annotation.GXCacheEvict)")
    public void evictCachePointCut() {
    }

    @Around("evictCachePointCut()")
    public Object around(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = parameterNameDiscover.getParameterNames(method);
        Class<?> targetClass = point.getTarget().getClass();
        Object[] args = point.getArgs();

        GXCacheEvict cacheEvict = method.getAnnotation(GXCacheEvict.class);
        if (cacheEvict == null) {
            cacheEvict = targetClass.getAnnotation(GXCacheEvict.class);
        }

        String cacheKey = parseCacheKey(parameterNames, targetClass, method, cacheEvict, args);
        log.debug("生成的缓存失效键: {}", cacheKey);

        Object proceed;
        try {
            if (CollUtil.isNotEmpty(Arrays.asList(args))) {
                proceed = point.proceed(args);
            } else {
                proceed = point.proceed();
            }

            if (Objects.nonNull(proceed)) {
                GXBaseCacheLockService cacheLockService = GXSpringContextUtils.getBean(GXBaseCacheLockService.class);
                assert cacheLockService != null;
                try {
                    log.debug("获取缓存锁: {}", cacheKey);
                    cacheLockService.tryLock(cacheKey);
                    log.debug("清除缓存: {}", cacheKey);
                    GXCommonUtils.reflectCallObjectMethod(point.getTarget(), "evictCacheData", cacheKey, args);
                } catch (Exception e) {
                    log.error("清除缓存时发生异常: {}", e.getMessage(), e);
                } finally {
                    cacheLockService.releaseLock(cacheKey);
                    log.debug("释放缓存锁: {}", cacheKey);
                }
            }

            return proceed;
        } catch (Throwable e) {
            log.error("执行缓存失效方法时发生异常: {}", e.getMessage(), e);
            throw new GXBusinessException("执行缓存失效方法时发生异常: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("all")
    private String parseCacheKey(String[] parameterNames, Class<?> targetClass, Method method, GXCacheEvict cacheEvict, Object[] args) {
        List<String> tmpValues = new ArrayList<>();
        String cacheKey = cacheEvict.cacheKey();

        String cacheKeyExpression = targetClass.getName() + "." + method.getName() + "." + cacheKey;
        List<String> expressions = EXPRESSION_CACHE.computeIfAbsent(cacheKeyExpression,
                k -> CharSequenceUtil.split(cacheKey, '+'));

        for (String expr : expressions) {
            expr = CharSequenceUtil.trim(expr);
            if (CharSequenceUtil.isEmpty(expr)) {
                continue;
            }

            if (CharSequenceUtil.startWith(expr, "#")) {
                String paramValue = dealParam(expr, parameterNames, args);
                if (CharSequenceUtil.isNotEmpty(paramValue)) {
                    tmpValues.add(paramValue);
                }
            } else {
                tmpValues.add(CharSequenceUtil.replace(expr, "'", ""));
            }
        }

        String retCacheKey;
        if (CollUtil.isNotEmpty(tmpValues)) {
            retCacheKey = CollUtil.join(tmpValues, ":");
        } else {
            retCacheKey = CharSequenceUtil.format("{}:{}",
                    CharSequenceUtil.lowerFirst(targetClass.getSimpleName()),
                    CharSequenceUtil.isEmpty(cacheKey) ? method.getName() : cacheKey);
        }

        return retCacheKey;
    }

    @SuppressWarnings("all")
    private String dealParam(String expr, String[] parameterNames, Object[] args) {
        String targetParamName = CharSequenceUtil.replace(expr, "#", "");
        String getMethodName = "";

        if (CharSequenceUtil.contains(targetParamName, '.')) {
            List<String> split = CharSequenceUtil.split(targetParamName, '.', 2);
            targetParamName = split.get(0);
            getMethodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst(split.get(1)));
        }

        int length = parameterNames.length;
        for (int index = 0; index < length; index++) {
            if (CharSequenceUtil.equals(targetParamName, parameterNames[index])) {
                Object arg = args[index];
                if (arg == null) {
                    log.debug("参数{}的值为null", targetParamName);
                    return "null";
                }

                if (CharSequenceUtil.isNotEmpty(getMethodName)) {
                    try {
                        Method method = ReflectUtil.getMethod(arg.getClass(), getMethodName);
                        if (Objects.isNull(method)) {
                            log.error("在类{}中未找到方法{}", arg.getClass().getName(), getMethodName);
                            throw new GXBusinessException("Leaf框架需要的缓存方法不存在: " + getMethodName);
                        }

                        Object propertyValue = GXCommonUtils.reflectCallObjectMethod(arg, getMethodName);
                        return Objects.isNull(propertyValue) ? "null" : propertyValue.toString();
                    } catch (Exception e) {
                        log.error("获取参数属性值时发生异常: {}", e.getMessage(), e);
                        throw new GXBusinessException("获取缓存键属性值失败: " + e.getMessage(), e);
                    }
                } else {
                    return arg.toString();
                }
            }
        }

        log.warn("未找到匹配的参数: {}", targetParamName);
        return null;
    }
}