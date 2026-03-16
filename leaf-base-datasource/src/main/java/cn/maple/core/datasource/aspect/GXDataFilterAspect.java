package cn.maple.core.datasource.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.dto.GXDataFilterInnerDto;
import cn.maple.core.datasource.service.GXDataScopeService;
import cn.maple.core.datasource.util.GXDataFilterThreadLocalUtils;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/**
 * 数据权限过滤，切面处理类
 * <p>
 * 该切面用于处理数据权限过滤，通过拦截标注了@GXDataFilter注解的方法，
 * 在方法执行前根据用户权限动态添加SQL过滤条件，实现数据权限控制。
 * 支持超级管理员不受数据权限限制的特性。
 * </p>
 *
 * @author 塵渊 britton@126.com
 */
@Aspect
@Component
@Slf4j
public class GXDataFilterAspect {
    /**
     * 方法注解缓存，避免频繁反射计算
     */
    private static final Map<Method, DataFilterCacheEntry> METHOD_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();

    /**
     * 服务单例缓存，避免频繁从容器中获取
     */
    private volatile GXDataScopeService cachedDataScopeService;

    /**
     * 定义数据过滤切点，拦截所有标注了@GXDataFilter注解的方法或类
     * <p>
     * 扩展为支持类级别注解，提高了开发框架的易用性
     * </p>
     */
    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataFilter) || " +
            "@within(cn.maple.core.datasource.annotation.GXDataFilter)")
    public void dataFilterPointCut() {
        // 切点定义，不需要实现
    }

    /**
     * 环绕通知：方法执行前后的处理
     * <p>
     * 根据用户权限获取数据过滤的SQL条件，并存入ThreadLocal中供后续查询使用。
     * 支持嵌套调用，执行目标方法前暂存外层过滤条件，执行后恢复，确保数据权限不会在嵌套调用时丢失。
     * 超级管理员不受数据权限限制，直接跳过过滤条件设置。
     * 该方法是线程安全的，每个请求都有独立的ThreadLocal存储空间。
     * </p>
     *
     * @param point 环绕切点对象
     * @return 目标方法执行结果
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("dataFilterPointCut()")
    public Object dataFilterAround(ProceedingJoinPoint point) throws Throwable {
        // 1. 获取外层的 ThreadLocal 环境（用于支持嵌套调用，防止被覆盖后丢失）
        GXDataFilterInnerDto oldFilterDto = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
        boolean hasSetNewFilter = false;

        try {
            // 获取数据权限服务实现类，使用懒加载缓存避免频繁反射查找
            if (cachedDataScopeService == null) {
                synchronized (this) {
                    if (cachedDataScopeService == null) {
                        GXDataScopeService tempService = GXSpringContextUtils.getBean(GXDataScopeService.class);
                        if (Objects.isNull(tempService)) {
                            log.error("数据权限过滤失败：未找到GXDataScopeService接口实现类");
                            throw new GXBusinessException(CharSequenceUtil.format("请实现{}接口", GXDataScopeService.class.getName()));
                        }
                        cachedDataScopeService = tempService;
                    }
                }
            }
            GXDataScopeService dataScopeService = cachedDataScopeService;

            // 判断当前用户是否为超级管理员
            boolean isSuperAdmin = dataScopeService.isSuperAdmin();
            MethodSignature signature = (MethodSignature) point.getSignature();
            String methodName = signature.getDeclaringTypeName() + "." + signature.getName();

            log.trace("开始处理数据权限过滤，方法: {}", methodName);

            // 如果不是超级管理员，则不跳过，进行数据过滤
            if (!isSuperAdmin) {
                try {
                    // 获取SQL过滤条件
                    String sqlFilter = getSqlFilter(point, dataScopeService);

                    // 安全检查：如果获取的SQL过滤条件为空，有可能导致越权（无WHERE条件），这里记录警告并跳过
                    // （注：具体视dataScopeService的实现而定，一般应该返回明确的阻断SQL如 1=0 而非空字符串）
                    if (CharSequenceUtil.isEmpty(sqlFilter)) {
                        log.debug("未获取到SQL过滤条件，主动清除继承的过滤条件以防泄露: {}", methodName);
                        GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
                        hasSetNewFilter = true;
                    } else {
                        // SQL注入防护检查：警告可能的SQL注入风险
                        String lowerSqlFilter = sqlFilter.toLowerCase();
                        if (sqlFilter.contains("'") || sqlFilter.contains(";")
                                || lowerSqlFilter.contains("delete ")
                                || lowerSqlFilter.contains("update ")
                                || lowerSqlFilter.contains("drop ")) {
                            log.warn("SQL过滤条件可能存在注入风险，请确保使用参数化查询: {}", sqlFilter);
                        }

                        // 创建数据过滤对象并存入ThreadLocal
                        GXDataFilterInnerDto dataScope = new GXDataFilterInnerDto(sqlFilter);
                        GXDataFilterThreadLocalUtils.setDataFilterInnerDto(dataScope);
                        hasSetNewFilter = true;
                        log.debug("已应用数据权限过滤条件: {}, 方法: {}", sqlFilter, methodName);
                    }
                } catch (Exception e) {
                    log.error("应用数据权限过滤条件时发生异常: {}", e.getMessage(), e);
                    throw new GXBusinessException("应用数据权限过滤条件失败: " + e.getMessage(), e);
                }
            } else {
                log.debug("超级管理员访问，清理可能的过滤条件并跳过: {}", methodName);
                if (oldFilterDto != null) {
                    GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
                    hasSetNewFilter = true;
                }
            }

            // 2. 执行真正的目标方法
            return point.proceed();

        } finally {
            // 3. 恢复外层的 ThreadLocal 环境
            // 只有当前切面确实介入并改变了上下文（hasSetNewFilter为true）时，才需要进行恢复或清理动作
            // 防范异常中断陷阱：如果在获取SQL过滤条件时就报错，尚未设置新值，此时不应破坏原有的上下文状态
            if (hasSetNewFilter) {
                if (oldFilterDto != null) {
                    log.debug("当前方法执行完毕(或中断)，恢复外层数据过滤条件: {}", oldFilterDto.getSqlFilter());
                    GXDataFilterThreadLocalUtils.setDataFilterInnerDto(oldFilterDto);
                } else {
                    log.debug("当前方法执行完毕(或中断)，清除当前挂载的数据过滤条件");
                    GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
                }
            } else {
                log.trace("当前切面未挂载新过滤条件，跳过上下文恢复/清理");
            }
        }
    }

    /**
     * 获取数据过滤的SQL条件
     * <p>
     * 从目标方法上获取@GXDataFilter注解，并通过GXDataScopeService接口获取对应的SQL过滤条件
     * 该方法通过Spring工具类获取方法上的注解信息，防止反射多态抛出异常
     * </p>
     *
     * @param point            切点对象，包含目标方法的相关信息
     * @param dataScopeService 数据权限服务
     * @return 返回SQL过滤条件字符串，如果没有过滤条件则返回空字符串
     * @throws Exception 获取时可能抛出异常
     */
    private String getSqlFilter(JoinPoint point, GXDataScopeService dataScopeService) throws Exception {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object target = point.getTarget();

        // 获取真正的目标实现方法，防止由于代理/接口/泛型导致的找不到注解
        if (target != null) {
            method = ClassUtils.getMostSpecificMethod(method, target.getClass());
        }

        Method finalMethod = method;
        // 尝试从缓存中获取，或自动沿着层级或组合注解寻找（先找方法，再找类）
        DataFilterCacheEntry cacheEntry = METHOD_ANNOTATION_CACHE.computeIfAbsent(finalMethod, m -> {
            GXDataFilter annotation = AnnotatedElementUtils.findMergedAnnotation(m, GXDataFilter.class);
            if (annotation != null) {
                return new DataFilterCacheEntry(true, annotation);
            }
            if (target != null) {
                // 如果方法上没有，则查看类上是否有
                annotation = AnnotatedElementUtils.findMergedAnnotation(target.getClass(), GXDataFilter.class);
                if (annotation != null) {
                    return new DataFilterCacheEntry(true, annotation);
                }
                // 扫描实现的接口
                for (Class<?> ifc : target.getClass().getInterfaces()) {
                    annotation = AnnotatedElementUtils.findMergedAnnotation(ifc, GXDataFilter.class);
                    if (annotation != null) {
                        return new DataFilterCacheEntry(true, annotation);
                    }
                }
            }
            return new DataFilterCacheEntry(false, null);
        });

        if (!cacheEntry.hasAnnotation() || cacheEntry.annotation() == null) {
            throw new GXBusinessException("无法找到 @GXDataFilter 注解, 请确认切面拦截目标是否正确");
        }

        // 调用服务获取SQL过滤条件
        String sqlFilter = dataScopeService.getSqlFilter(cacheEntry.annotation(), point);
        log.debug("获取到SQL过滤条件: {}", sqlFilter);
        return sqlFilter;
    }

    /**
     * 注解缓存实体
     */
    private record DataFilterCacheEntry(boolean hasAnnotation, GXDataFilter annotation) {
    }
}