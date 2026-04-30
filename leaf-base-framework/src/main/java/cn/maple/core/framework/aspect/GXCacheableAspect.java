package cn.maple.core.framework.aspect;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.maple.core.framework.annotation.GXCacheable;
import cn.maple.core.framework.dto.res.GXBaseResDto;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;
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
public class GXCacheableAspect {
    private static final StandardReflectionParameterNameDiscoverer parameterNameDiscover = new StandardReflectionParameterNameDiscoverer();

    private static final ConcurrentHashMap<String, List<String>> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXCacheable)" + "|| @within(cn.maple.core.framework.annotation.GXCacheable)")
    public void cacheablePointCut() {
    }


    @Around("cacheablePointCut()")
    public Object around(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = parameterNameDiscover.getParameterNames(method);
        Class<?> targetClass = point.getTarget().getClass();
        Object[] args = point.getArgs();

        GXCacheable cacheable = method.getAnnotation(GXCacheable.class);
        if (cacheable == null) {
            cacheable = targetClass.getAnnotation(GXCacheable.class);
        }

        Class<? extends GXBaseResDto> tClass = cacheable.retType();
        String methodName = cacheable.methodName();

        String cacheKey = parseCacheKey(parameterNames, targetClass, method, cacheable, args);
        log.debug("生成的缓存键: {}", cacheKey);

        Object obtainData = GXCommonUtils.reflectCallObjectMethod(point.getTarget(), "getDataFromCache", cacheKey, args);
        if (Objects.nonNull(obtainData)) {
            log.debug("从缓存中获取到数据: {}", cacheKey);
            if (tClass.isAssignableFrom(GXBaseResDto.class)) {
                return obtainData;
            }
            return GXCommonUtils.convertSourceToTarget(obtainData, tClass, methodName, null, Dict.create());
        }

        log.debug("缓存中未找到数据，执行目标方法: {}", method.getName());
        try {
            Object proceed;
            if (CollUtil.isNotEmpty(Arrays.asList(args))) {
                proceed = point.proceed(args);
            } else {
                proceed = point.proceed();
            }

            if (Objects.nonNull(proceed)) {
                log.debug("将方法执行结果存入缓存: {}", cacheKey);
                GXCommonUtils.reflectCallObjectMethod(point.getTarget(), "setCacheData", cacheKey, proceed, args);
            }

            return proceed;
        } catch (Throwable e) {
            log.error("执行缓存方法时发生异常: {}", e.getMessage(), e);
            throw new GXBusinessException("执行缓存方法时发生异常: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("all")
    private String parseCacheKey(String[] parameterNames, Class<?> targetClass, Method method, GXCacheable cacheable, Object[] args) {
        List<String> tmpLst = new ArrayList<>();
        String cacheKey = cacheable.cacheKey();

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
                    tmpLst.add(paramValue);
                }
            } else {
                tmpLst.add(CharSequenceUtil.replace(expr, "'", ""));
            }
        }

        String retCacheKey;
        if (CollUtil.isNotEmpty(tmpLst)) {
            retCacheKey = CollUtil.join(tmpLst, ":");
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
