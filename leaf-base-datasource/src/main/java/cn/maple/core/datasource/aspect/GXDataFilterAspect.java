package cn.maple.core.datasource.aspect;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.dto.GXDataFilterContext;
import cn.maple.core.datasource.service.GXDataFilterSqlResolver;
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
    }

    @Around("dataFilterPointCut()")
    public Object dataFilterAround(ProceedingJoinPoint point) throws Throwable {
        GXDataFilterContext oldFilterContext = GXDataFilterThreadLocalUtils.getDataFilterContext();
        boolean hasSetNewFilter = false;

        try {
            if (cachedDataScopeService == null) {
                synchronized (this) {
                    if (cachedDataScopeService == null) {
                        GXDataScopeService tempService = GXSpringContextUtils.getBean(GXDataScopeService.class);
                        if (Objects.isNull(tempService)) {
                            log.error("Data filter failed, GXDataScopeService implementation was not found.");
                            throw new GXBusinessException(CharSequenceUtil.format("Please implement {}", GXDataScopeService.class.getName()));
                        }
                        cachedDataScopeService = tempService;
                    }
                }
            }
            GXDataScopeService dataScopeService = cachedDataScopeService;

            boolean isSuperAdmin = dataScopeService.isSuperAdmin();
            MethodSignature signature = (MethodSignature) point.getSignature();
            String methodName = signature.getDeclaringTypeName() + "." + signature.getName();

            log.trace("Start data filter handling, method={}", methodName);

            if (!isSuperAdmin) {
                try {
                    GXDataFilterContext dataFilterContext = resolveDataFilterContext(point, dataScopeService, methodName);
                    String sqlFilter = dataFilterContext.getSqlFilter();

                    if (CharSequenceUtil.isEmpty(sqlFilter)) {
                        log.debug("No SQL filter found, clearing inherited filter to avoid leakage. method={}", methodName);
                        GXDataFilterThreadLocalUtils.cleanDataFilterContext();
                        hasSetNewFilter = true;
                    } else {
                        String lowerSqlFilter = sqlFilter.toLowerCase();
                        if (sqlFilter.contains("'") || sqlFilter.contains(";")
                                || lowerSqlFilter.contains("delete ")
                                || lowerSqlFilter.contains("update ")
                                || lowerSqlFilter.contains("drop ")) {
                            log.warn("SQL filter may contain injection risk, ensure parameterized query usage. filter={}", sqlFilter);
                        }

                        GXDataFilterThreadLocalUtils.setDataFilterContext(dataFilterContext);
                        hasSetNewFilter = true;
                        log.debug("Applied data filter. filter={}, method={}", sqlFilter, methodName);
                    }
                } catch (Exception e) {
                    log.error("Failed to apply data filter: {}", e.getMessage(), e);
                    throw new GXBusinessException("Failed to apply data filter: " + e.getMessage(), e);
                }
            } else {
                log.debug("Super admin access, clearing possible data filter and skipping. method={}", methodName);
                if (oldFilterContext != null) {
                    GXDataFilterThreadLocalUtils.cleanDataFilterContext();
                    hasSetNewFilter = true;
                }
            }

            return point.proceed();

        } finally {
            if (hasSetNewFilter) {
                if (oldFilterContext != null) {
                    log.debug("Restoring outer data filter after method completion. filter={}", oldFilterContext.getSqlFilter());
                    GXDataFilterThreadLocalUtils.setDataFilterContext(oldFilterContext);
                } else {
                    log.debug("Clearing current data filter after method completion.");
                    GXDataFilterThreadLocalUtils.cleanDataFilterContext();
                }
            } else {
                log.trace("No new data filter was mounted, skip context restore.");
            }
        }
    }

    private GXDataFilterContext resolveDataFilterContext(JoinPoint point, GXDataScopeService dataScopeService, String methodName) {
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
            throw new GXBusinessException("Unable to find @GXDataFilter annotation for the intercepted target.");
        }

        GXDataFilterContext context = GXDataFilterSqlResolver.resolve(dataScopeService, cacheEntry.annotation(), point, methodName);
        log.debug("Resolved SQL filter: {}", context.getSqlFilter());
        return context;
    }

    private record DataFilterCacheEntry(boolean hasAnnotation, GXDataFilter annotation) {
    }
}
