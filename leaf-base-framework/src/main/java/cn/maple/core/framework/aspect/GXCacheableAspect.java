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

/**
 * 缓存切面
 * <p>
 * 用于拦截标记了{@link GXCacheable}注解的方法，实现方法返回值的缓存功能。
 * 该切面是线程安全的，使用了线程安全的反射和缓存键生成机制。
 * 通过缓存机制可以显著提高频繁调用且计算成本较高的方法的性能。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 在方法上使用@GXCacheable注解
 * @GXCacheable(cacheKey = "user:detail:#userId", retType = UserDto.class)
 * public UserDto getUserDetail(Long userId) {
 *     // 方法实现，只有在缓存未命中时才会执行...
 *     return userDto;
 * }
 * 
 * // 2. 在类上使用@GXCacheable注解，为类中所有方法提供默认的缓存配置
 * @GXCacheable(cacheKey = "product")
 * @Service
 * public class ProductServiceImpl implements ProductService {
 *     
 *     // 使用类上定义的缓存键前缀，最终缓存键为"product:getById"
 *     public ProductDto getById(Long id) {
 *         // 方法实现...
 *     }
 *     
 *     // 自定义缓存键，支持参数引用，最终缓存键为"product:detail:123"
 *     @GXCacheable(cacheKey = "product:detail:#productId")
 *     public ProductDetailDto getProductDetail(Long productId) {
 *         // 方法实现...
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * 缓存键生成规则：
 * <ol>
 *   <li>支持静态文本：如 "user:list"</li>
 *   <li>支持参数引用：如 "user:#userId" 引用方法参数</li>
 *   <li>支持参数属性引用：如 "user:#user.id" 引用参数的属性</li>
 *   <li>支持多部分组合：如 "user:#userId:detail:#type"</li>
 *   <li>如果未指定cacheKey，默认使用 "类名:方法名" 作为缓存键</li>
 * </ol>
 * </p>
 * <p>
 * 缓存实现要求：
 * <ul>
 *   <li>目标类需要实现 getDataFromCache 方法用于从缓存获取数据</li>
 *   <li>目标类需要实现 setCacheData 方法用于将数据存入缓存</li>
 *   <li>可以通过 retType 和 methodName 属性指定返回值类型转换</li>
 * </ul>
 * </p>
 * <p>
 * 性能优化特点：
 * <ul>
 *   <li>使用ConcurrentHashMap缓存解析后的表达式，提高性能</li>
 *   <li>线程安全设计，适用于高并发环境</li>
 *   <li>异常处理机制确保缓存操作异常不影响主业务流程</li>
 * </ul>
 * </p>
 *
 * @author maple
 */

@Aspect
@Component
@Slf4j
public class GXCacheableAspect {
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
     * 定义缓存切点
     * 拦截标记了@GXCacheable注解的方法或类
     */
    @Pointcut("@annotation(cn.maple.core.framework.annotation.GXCacheable)" + "|| @within(cn.maple.core.framework.annotation.GXCacheable)")
    public void cacheablePointCut() {
        // 切点定义，无需实现
    }

    /**
     * 缓存环绕通知
     * <p>
     * 在目标方法执行前检查缓存，如果缓存存在则直接返回缓存数据
     * 否则执行目标方法并将结果存入缓存
     * 该方法是线程安全的，每个请求都有独立的执行上下文
     * </p>
     *
     * @param point 切点对象，包含目标方法的信息和参数
     * @return 目标方法的执行结果或缓存的结果
     */
    @Around("cacheablePointCut()")
    public Object around(ProceedingJoinPoint point) {
        // 获取方法签名和相关信息
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String[] parameterNames = parameterNameDiscover.getParameterNames(method);
        Class<?> targetClass = point.getTarget().getClass();
        Object[] args = point.getArgs();
        
        // 获取缓存注解及其属性
        GXCacheable cacheable = method.getAnnotation(GXCacheable.class);
        if (cacheable == null) {
            // 如果方法上没有注解，尝试从类上获取
            cacheable = targetClass.getAnnotation(GXCacheable.class);
        }
        
        Class<? extends GXBaseResDto> tClass = cacheable.retType();
        String methodName = cacheable.methodName();
        
        // 生成缓存键
        String cacheKey = parseCacheKey(parameterNames, targetClass, method, cacheable, args);
        log.debug("生成的缓存键: {}", cacheKey);
        
        // 尝试从缓存获取数据
        Object obtainData = GXCommonUtils.reflectCallObjectMethod(point.getTarget(), "getDataFromCache", cacheKey, args);
        if (Objects.nonNull(obtainData)) {
            log.debug("从缓存中获取到数据: {}", cacheKey);
            // 如果返回类型是GXBaseResDto，直接返回缓存数据
            if (tClass.isAssignableFrom(GXBaseResDto.class)) {
                return obtainData;
            }
            // 否则将缓存数据转换为目标类型
            return GXCommonUtils.convertSourceToTarget(obtainData, tClass, methodName, null, Dict.create());
        }
        
        log.debug("缓存中未找到数据，执行目标方法: {}", method.getName());
        try {
            // 执行目标方法
            Object proceed;
            if (CollUtil.isNotEmpty(Arrays.asList(args))) {
                proceed = point.proceed(args);
            } else {
                proceed = point.proceed();
            }
            
            // 如果方法执行结果不为空，则将结果存入缓存
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
     * @param cacheable 缓存注解
     * @param args 方法参数值数组
     * @return 生成的缓存键
     */
    @SuppressWarnings("all")
    private String parseCacheKey(String[] parameterNames, Class<?> targetClass, Method method, GXCacheable cacheable, Object[] args) {
        // 用于存储缓存键的各个部分
        List<String> tmpLst = new ArrayList<>();
        String cacheKey = cacheable.cacheKey();
        
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
                    tmpLst.add(paramValue);
                }
            } else {
                // 处理静态文本，移除引号
                tmpLst.add(CharSequenceUtil.replace(expr, "'", ""));
            }
        }

        // 生成最终的缓存键
        String retCacheKey;
        if (CollUtil.isNotEmpty(tmpLst)) {
            // 如果有解析出的部分，使用它们组合成缓存键
            retCacheKey = CollUtil.join(tmpLst, ":");
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
}
