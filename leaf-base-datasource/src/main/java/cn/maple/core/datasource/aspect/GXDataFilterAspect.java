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
import org.springframework.core.MethodClassKey;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

@Aspect
@Component
@Slf4j
public class GXDataFilterAspect {
    private static final Map<MethodClassKey, DataFilterCacheEntry> METHOD_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();

    private volatile GXDataScopeService cachedDataScopeService;

    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataFilter) || " +
            "@within(cn.maple.core.datasource.annotation.GXDataFilter)")
    public void dataFilterPointCut() {
        // 切点定义，不需要实现
    }

    @Around("dataFilterPointCut()")
    public Object dataFilterAround(ProceedingJoinPoint point) throws Throwable {
        GXDataFilterInnerDto oldFilterDto = GXDataFilterThreadLocalUtils.getDataFilterInnerDto();
        boolean hasSetNewFilter = false;

        try {
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

            boolean isSuperAdmin = dataScopeService.isSuperAdmin();
            MethodSignature signature = (MethodSignature) point.getSignature();
            String methodName = signature.getDeclaringTypeName() + "." + signature.getName();

            log.trace("开始处理数据权限过滤，方法: {}", methodName);

            if (!isSuperAdmin) {
                try {
                    String sqlFilter = getSqlFilter(point, dataScopeService);

                    if (CharSequenceUtil.isEmpty(sqlFilter)) {
                        log.debug("未获取到SQL过滤条件，主动清除继承的过滤条件以防泄露: {}", methodName);
                        GXDataFilterThreadLocalUtils.cleanDataFilterInnerDto();
                        hasSetNewFilter = true;
                    } else {
                        String lowerSqlFilter = sqlFilter.toLowerCase();
                        if (sqlFilter.contains("'") || sqlFilter.contains(";")
                                || lowerSqlFilter.contains("delete ")
                                || lowerSqlFilter.contains("update ")
                                || lowerSqlFilter.contains("drop ")) {
                            log.warn("SQL过滤条件可能存在注入风险，请确保使用参数化查询: {}", sqlFilter);
                        }

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

            return point.proceed();

        } finally {
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

    private String getSqlFilter(JoinPoint point, GXDataScopeService dataScopeService) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object target = point.getTarget();

        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();

        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);

        MethodClassKey cacheKey = new MethodClassKey(specificMethod, targetClass);

        DataFilterCacheEntry cacheEntry = METHOD_ANNOTATION_CACHE.computeIfAbsent(cacheKey, key -> {
            GXDataFilter annotation = AnnotatedElementUtils.findMergedAnnotation(specificMethod, GXDataFilter.class);
            if (annotation != null) {
                return new DataFilterCacheEntry(true, annotation);
            }
            if (target != null) {
                annotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, GXDataFilter.class);
                if (annotation != null) {
                    return new DataFilterCacheEntry(true, annotation);
                }
            }
            return new DataFilterCacheEntry(false, null);
        });

        if (!cacheEntry.hasAnnotation() || cacheEntry.annotation() == null) {
            throw new GXBusinessException("无法找到 @GXDataFilter 注解, 请确认切面拦截目标是否正确");
        }

        String sqlFilter = dataScopeService.getSqlFilter(cacheEntry.annotation(), point);
        log.debug("获取到SQL过滤条件: {}", sqlFilter);
        return sqlFilter;
    }

    private record DataFilterCacheEntry(boolean hasAnnotation, GXDataFilter annotation) {
    }
}