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
import java.util.concurrent.locks.Lock;

/**
 * 缓存失效切面
 * <p>
 * 用于拦截标记了{@link GXCacheEvict}注解的方法，实现方法执行后清除指定缓存的功能
 * 该切面是线程安全的，使用了分布式锁机制确保缓存清除的原子性
 * </p>
 *
 * @author maple
 */

@Aspect
@Component
@Slf4j
public class GXCacheEvictAspect {
    /**
     * 参数名称发现器，用于获取方法参数名
     * 使用static final修饰，确保线程安全且只初始化一次
     */
    private static final StandardReflectionParameterNameDiscoverer parameterNameDiscover = new StandardReflectionParameterNameDiscoverer();
    
    /**
     * 缓存表达式解析结果，提高性能
     * 使用ConcurrentHashMap确保线程安全
     */
    private static final ConcurrentHashMap<String, List<String>> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    /**
     * 定义缓存失效切点
     * 拦截标记了@GXCacheEvict注解的方法或类
     */
    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXCacheEvict) " + "|| @within(cn.maple.core.framework.annotation.GXCacheEvict)")
    public void evictCachePointCut() {
        // 切点定义，无需实现
    }

    /**
     * 缓存失效环绕通知
     * <p>
     * 在目标方法执行后清除指定的缓存
     * 使用分布式锁确保缓存清除的原子性，防止并发问题
     * </p>
     *
     * @param point 切点对象，包含目标方法的信息和参数
     * @return 目标方法的执行结果
     */
    @Around("evictCachePointCut()")
    public Object around(ProceedingJoinPoint point) {
        // 获取方法签名和相关信息
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = parameterNameDiscover.getParameterNames(method);
        Class<?> targetClass = point.getTarget().getClass();
        Object[] args = point.getArgs();
        
        // 获取缓存失效注解
        GXCacheEvict cacheEvict = method.getAnnotation(GXCacheEvict.class);
        if (cacheEvict == null) {
            // 如果方法上没有注解，尝试从类上获取
            cacheEvict = targetClass.getAnnotation(GXCacheEvict.class);
        }
        
        // 生成缓存键
        String cacheKey = parseCacheKey(parameterNames, targetClass, method, cacheEvict, args);
        log.debug("生成的缓存失效键: {}", cacheKey);
        
        Object proceed;
        try {
            // 执行目标方法
            if (CollUtil.isNotEmpty(Arrays.asList(args))) {
                proceed = point.proceed(args);
            } else {
                proceed = point.proceed();
            }
            
            // 如果方法执行成功且结果不为空，则清除缓存
            if (Objects.nonNull(proceed)) {
                // 获取分布式锁，确保缓存清除的原子性
                Lock cacheLock = getCacheLock(cacheKey);
                try {
                    log.debug("获取缓存锁: {}", cacheKey);
                    cacheLock.lock();
                    log.debug("清除缓存: {}", cacheKey);
                    // 调用目标对象的evictCacheData方法清除缓存
                    GXCommonUtils.reflectCallObjectMethod(point.getTarget(), "evictCacheData", cacheKey, args);
                } catch (Exception e) {
                    log.error("清除缓存时发生异常: {}", e.getMessage(), e);
                    // 缓存清除异常不应影响主业务流程
                } finally {
                    // 确保锁一定会被释放
                    cacheLock.unlock();
                    log.debug("释放缓存锁: {}", cacheKey);
                }
            }
            
            return proceed;
        } catch (Throwable e) {
            log.error("执行缓存失效方法时发生异常: {}", e.getMessage(), e);
            throw new GXBusinessException("执行缓存失效方法时发生异常: " + e.getMessage(), e);
        }
    }

    /**
     * 解析缓存键
     * <p>
     * 根据注解中的cacheKey表达式和方法参数生成缓存键
     * 支持静态文本和参数引用(#paramName)的混合表达式
     * 该方法是线程安全的，使用了线程安全的集合和不可变对象
     * </p>
     *
     * @param parameterNames 方法参数名数组
     * @param targetClass 目标类
     * @param method 目标方法
     * @param cacheEvict 缓存失效注解
     * @param args 方法参数值数组
     * @return 生成的缓存键
     */
    @SuppressWarnings("all")
    private String parseCacheKey(String[] parameterNames, Class<?> targetClass, Method method, GXCacheEvict cacheEvict, Object[] args) {
        // 用于存储缓存键的各个部分
        List<String> tmpValues = new ArrayList<>();
        String cacheKey = cacheEvict.cacheKey();
        
        // 尝试从缓存中获取已解析的表达式
        String cacheKeyExpression = targetClass.getName() + "." + method.getName() + "." + cacheKey;
        List<String> expressions = EXPRESSION_CACHE.computeIfAbsent(cacheKeyExpression, 
                k -> CharSequenceUtil.split(cacheKey, '+'));

        // 处理每个表达式部分
        for (String expr : expressions) {
            expr = CharSequenceUtil.trim(expr);
            if (CharSequenceUtil.isEmpty(expr)) {
                continue;
            }
            
            if (CharSequenceUtil.startWith(expr, "#")) {
                // 处理参数引用表达式 (#paramName)
                String paramValue = dealParam(expr, parameterNames, args);
                if (CharSequenceUtil.isNotEmpty(paramValue)) {
                    tmpValues.add(paramValue);
                }
            } else {
                // 处理静态文本，移除引号
                tmpValues.add(CharSequenceUtil.replace(expr, "'", ""));
            }
        }

        // 生成最终的缓存键
        String retCacheKey;
        if (CollUtil.isNotEmpty(tmpValues)) {
            // 如果有解析出的部分，使用它们组合成缓存键
            retCacheKey = CollUtil.join(tmpValues, ":");
        } else {
            // 否则使用默认格式：类名:方法名或自定义键
            retCacheKey = CharSequenceUtil.format("{}:{}", 
                    CharSequenceUtil.lowerFirst(targetClass.getSimpleName()), 
                    CharSequenceUtil.isEmpty(cacheKey) ? method.getName() : cacheKey);
        }
        
        return retCacheKey;
    }

    /**
     * 处理参数引用表达式
     * <p>
     * 解析形如#paramName或#paramName.property的表达式
     * 支持直接引用参数值或获取参数对象的属性值
     * 该方法是线程安全的，不修改任何共享状态
     * </p>
     *
     * @param expr 参数表达式，如#userId或#user.id
     * @param parameterNames 方法参数名数组
     * @param args 方法参数值数组
     * @return 解析后的参数值字符串，如果解析失败则返回null
     */
    @SuppressWarnings("all")
    private String dealParam(String expr, String[] parameterNames, Object[] args) {
        // 移除表达式中的#前缀
        String targetParamName = CharSequenceUtil.replace(expr, "#", "");
        String getMethodName = "";
        
        // 处理属性访问表达式，如#user.id
        if (CharSequenceUtil.contains(targetParamName, '.')) {
            List<String> split = CharSequenceUtil.split(targetParamName, '.', 2);
            targetParamName = split.get(0); // 参数名
            // 构造getter方法名，如getId
            getMethodName = CharSequenceUtil.format("get{}", CharSequenceUtil.upperFirst(split.get(1)));
        }
        
        // 查找匹配的参数
        int length = parameterNames.length;
        for (int index = 0; index < length; index++) {
            if (CharSequenceUtil.equals(targetParamName, parameterNames[index])) {
                Object arg = args[index];
                if (arg == null) {
                    log.debug("参数{}的值为null", targetParamName);
                    return "null";
                }
                
                // 如果需要获取属性值
                if (CharSequenceUtil.isNotEmpty(getMethodName)) {
                    try {
                        // 获取getter方法
                        Method method = ReflectUtil.getMethod(arg.getClass(), getMethodName);
                        if (Objects.isNull(method)) {
                            log.error("在类{}中未找到方法{}", arg.getClass().getName(), getMethodName);
                            throw new GXBusinessException("Leaf框架需要的缓存方法不存在: " + getMethodName);
                        }
                        
                        // 调用getter方法获取属性值
                        Object propertyValue = GXCommonUtils.reflectCallObjectMethod(arg, getMethodName);
                        return Objects.isNull(propertyValue) ? "null" : propertyValue.toString();
                    } catch (Exception e) {
                        log.error("获取参数属性值时发生异常: {}", e.getMessage(), e);
                        throw new GXBusinessException("获取缓存键属性值失败: " + e.getMessage(), e);
                    }
                } else {
                    // 直接使用参数值
                    return arg.toString();
                }
            }
        }
        
        log.warn("未找到匹配的参数: {}", targetParamName);
        return null;
    }

    /**
     * 获取缓存锁
     * <p>
     * 通过缓存锁服务获取指定名称的分布式锁
     * 使用分布式锁确保在分布式环境中缓存清除操作的原子性
     * </p>
     *
     * @param lockName 锁的名字，通常是缓存键
     * @return Lock 分布式锁对象
     * @throws GXBusinessException 如果无法获取锁服务或锁对象
     */
    @SuppressWarnings("all")
    private Lock getCacheLock(String lockName) {
        GXBaseCacheLockService lockService = GXSpringContextUtils.getBean(GXBaseCacheLockService.class);
        if (lockService == null) {
            log.error("未找到GXBaseCacheLockService的实现类");
            throw new GXBusinessException("缓存锁服务不可用");
        }
        
        Lock lock = lockService.getLock(lockName);
        if (lock == null) {
            log.error("无法为{}获取锁对象", lockName);
            throw new GXBusinessException("无法获取缓存锁: " + lockName);
        }
        
        return lock;
    }
}