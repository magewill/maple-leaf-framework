package cn.maple.core.datasource.aspect;

import cn.maple.core.datasource.annotation.GXDataSource;
import cn.maple.core.datasource.config.GXDynamicContextHolder;
import cn.maple.core.datasource.config.GXDynamicDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Dynamic data source switching aspect.
 */
@Aspect
@Component
@Order(-1000)
@Slf4j
public class GXDataSourceAspect {
    private static final Map<Class<?>, DataSourceCacheEntry> CLASS_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();
    private static final Map<MethodCacheKey, DataSourceCacheEntry> METHOD_ANNOTATION_CACHE = new ConcurrentReferenceHashMap<>();

    @Resource
    private BeanFactory beanFactory;

    @Resource(name = "dynamicDataSource")
    private GXDynamicDataSource dynamicDataSource;

    @Pointcut("@annotation(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "@within(cn.maple.core.datasource.annotation.GXDataSource) || " +
            "target(cn.maple.core.datasource.repository.GXMyBatisRepository+) || " +
            "target(cn.maple.core.datasource.service.GXMyBatisBaseService+)")
    public void dataSourcePointCut() {
        // Pointcut marker.
    }

    private DataSourceCacheEntry getDataSourceAnnotationFromClass(Class<?> targetClass) {
        if (targetClass == null) {
            return new DataSourceCacheEntry(false, "");
        }

        return CLASS_ANNOTATION_CACHE.computeIfAbsent(targetClass, clazz -> {
            GXDataSource annotation = AnnotatedElementUtils.findMergedAnnotation(clazz, GXDataSource.class);
            if (annotation != null) {
                String dataSourceValue = normalizeDataSourceValue(annotation.value());
                if (StringUtils.hasText(dataSourceValue)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found class-level @GXDataSource on {}, value={}", clazz.getName(), dataSourceValue);
                    }
                    return new DataSourceCacheEntry(true, dataSourceValue);
                }
                if (log.isWarnEnabled()) {
                    log.warn("Class {} has @GXDataSource but value is blank, fallback to default datasource", clazz.getName());
                }
            }

            for (Class<?> ifc : ClassUtils.getAllInterfacesForClass(clazz)) {
                annotation = AnnotatedElementUtils.findMergedAnnotation(ifc, GXDataSource.class);
                if (annotation != null) {
                    String dataSourceValue = normalizeDataSourceValue(annotation.value());
                    if (StringUtils.hasText(dataSourceValue)) {
                        if (log.isDebugEnabled()) {
                            log.debug("Found interface-level @GXDataSource on {} -> {}, value={}",
                                    clazz.getName(), ifc.getName(), dataSourceValue);
                        }
                        return new DataSourceCacheEntry(true, dataSourceValue);
                    }
                    if (log.isWarnEnabled()) {
                        log.warn("Interface {} on class {} has blank @GXDataSource value, continue searching",
                                ifc.getName(), clazz.getName());
                    }
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("No @GXDataSource found on class {} or its interfaces", clazz.getName());
            }
            return new DataSourceCacheEntry(false, "");
        });
    }

    private DataSourceCacheEntry getDataSourceAnnotationFromMethod(Class<?> targetClass, Method method) {
        if (targetClass == null || method == null) {
            return new DataSourceCacheEntry(false, "");
        }

        MethodCacheKey cacheKey = new MethodCacheKey(targetClass, method);
        return METHOD_ANNOTATION_CACHE.computeIfAbsent(cacheKey, k -> {
            Method targetMethod = AopUtils.getMostSpecificMethod(k.method(), k.targetClass());
            GXDataSource annotation = AnnotatedElementUtils.findMergedAnnotation(targetMethod, GXDataSource.class);
            if (annotation == null) {
                annotation = findInterfaceMethodAnnotation(k.targetClass(), k.method());
            }
            if (annotation != null) {
                String dataSourceValue = normalizeDataSourceValue(annotation.value());
                if (StringUtils.hasText(dataSourceValue)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Found method-level @GXDataSource on {}.{}, value={}",
                                targetClass.getName(), targetMethod.getName(), dataSourceValue);
                    }
                    return new DataSourceCacheEntry(true, dataSourceValue);
                }
                if (log.isWarnEnabled()) {
                    log.warn("Method {}.{} has @GXDataSource but value is blank, fallback to class/default datasource",
                            targetClass.getName(), targetMethod.getName());
                }
            }
            return new DataSourceCacheEntry(false, "");
        });
    }

    private GXDataSource findInterfaceMethodAnnotation(Class<?> targetClass, Method method) {
        for (Class<?> ifc : ClassUtils.getAllInterfacesForClass(targetClass)) {
            Method interfaceMethod = ClassUtils.getMethodIfAvailable(ifc, method.getName(), method.getParameterTypes());
            if (interfaceMethod == null) {
                continue;
            }
            GXDataSource annotation = AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, GXDataSource.class);
            if (annotation != null) {
                return annotation;
            }
        }
        return null;
    }

    private String normalizeDataSourceValue(String dataSourceValue) {
        return dataSourceValue == null ? "" : dataSourceValue.trim();
    }

    @Around("dataSourcePointCut()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        if (point == null) {
            log.error("ProceedingJoinPoint is null, cannot switch datasource");
            throw new IllegalArgumentException("ProceedingJoinPoint cannot be null");
        }

        if (!(point.getSignature() instanceof MethodSignature signature)) {
            if (log.isErrorEnabled()) {
                log.error("JoinPoint signature is not MethodSignature, skip datasource switch");
            }
            return point.proceed();
        }

        Object target = point.getTarget();
        if (target == null) {
            if (log.isTraceEnabled()) {
                log.trace("Target object is null, skip datasource switch");
            }
            return point.proceed();
        }

        Class<?> targetClass = target.getClass();
        Method method = signature.getMethod();

        boolean isTraceEnabled = log.isTraceEnabled();
        boolean isDebugEnabled = log.isDebugEnabled();
        String threadName = null;
        String methodName = null;
        String className = null;

        if (isTraceEnabled || isDebugEnabled) {
            threadName = Thread.currentThread().getName();
            methodName = method.getName();
            className = targetClass.getName();
            if (isTraceEnabled) {
                log.trace("Start datasource switch handling, thread={}, class={}, method={}", threadName, className, methodName);
            }
        }

        boolean needSwitchDataSource;
        String dataSourceValue;

        DataSourceCacheEntry methodEntry = getDataSourceAnnotationFromMethod(targetClass, method);
        if (methodEntry.needSwitch()) {
            needSwitchDataSource = true;
            dataSourceValue = methodEntry.dataSourceValue();
        } else {
            DataSourceCacheEntry classEntry = getDataSourceAnnotationFromClass(targetClass);
            needSwitchDataSource = classEntry.needSwitch();
            dataSourceValue = classEntry.dataSourceValue();

            if (!needSwitchDataSource && isTraceEnabled) {
                log.trace("No @GXDataSource found on {} or hierarchy, use default datasource", className);
            }
        }

        boolean pushed = false;
        try {
            if (needSwitchDataSource) {
                String previousDataSource = GXDynamicContextHolder.peek();
                GXDynamicContextHolder.push(dataSourceValue);
                pushed = true;

                if (isDebugEnabled) {
                    log.debug("Thread {} datasource switched from [{}] to [{}]", threadName,
                            (previousDataSource != null ? previousDataSource : "default"), dataSourceValue);
                }

                boolean springTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();
                if (springTransactionActive && isDifferentDatasource(previousDataSource, dataSourceValue)) {
                    TransactionAttribute transactionAttribute = getTransactionAttribute(targetClass, method);
                    validateCrossDatasourcePropagation(transactionAttribute);
                    if (shouldCreateIndependentTransaction(transactionAttribute)) {
                        return proceedInIndependentTransaction(point, transactionAttribute, targetClass);
                    }
                }
            }

            if (isTraceEnabled) {
                log.trace("Thread {} executing method: {}.{}", threadName, className, methodName);
            }
            Object result = point.proceed();
            if (isTraceEnabled) {
                log.trace("Thread {} method executed successfully: {}.{}", threadName, className, methodName);
            }
            return result;
        } finally {
            if (pushed) {
                GXDynamicContextHolder.poll();
                if (isDebugEnabled) {
                    String restoredDataSource = GXDynamicContextHolder.peek();
                    log.debug("Thread {} restore datasource from [{}] to [{}]", threadName, dataSourceValue,
                            (restoredDataSource != null ? restoredDataSource : "default"));
                }
            }
        }
    }

    /**
     * A routing datasource binds its physical connection to the outer transaction. Suspending that transaction
     * after switching the datasource allows the nested invocation to acquire an independent connection.
     */
    private TransactionAttribute getTransactionAttribute(Class<?> targetClass, Method method) {
        TransactionAttributeSource transactionAttributeSource = beanFactory.getBeanProvider(TransactionAttributeSource.class)
                .getIfUnique();
        if (transactionAttributeSource == null) {
            return null;
        }
        Method targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        TransactionAttribute attribute = transactionAttributeSource.getTransactionAttribute(targetMethod, targetClass);
        if (attribute == null && targetMethod != method) {
            attribute = transactionAttributeSource.getTransactionAttribute(method, targetClass);
        }
        return attribute;
    }

    private boolean isDifferentDatasource(String previousDataSource, String dataSourceValue) {
        return !dynamicDataSource.isSameDataSource(previousDataSource, dataSourceValue);
    }

    private boolean shouldCreateIndependentTransaction(TransactionAttribute transactionAttribute) {
        return transactionAttribute == null
                || transactionAttribute.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED;
    }

    private void validateCrossDatasourcePropagation(TransactionAttribute transactionAttribute) {
        if (transactionAttribute == null) {
            return;
        }
        int propagation = transactionAttribute.getPropagationBehavior();
        if (propagation == TransactionDefinition.PROPAGATION_REQUIRED
                || propagation == TransactionDefinition.PROPAGATION_REQUIRES_NEW
                || propagation == TransactionDefinition.PROPAGATION_NOT_SUPPORTED
                || propagation == TransactionDefinition.PROPAGATION_NEVER) {
            return;
        }
        throw new IllegalStateException("Cannot switch physical datasource within an active transaction using propagation "
                + propagation);
    }

    private Object proceedInIndependentTransaction(ProceedingJoinPoint point,
                                                   TransactionAttribute transactionAttribute,
                                                   Class<?> targetClass) throws Throwable {
        DefaultTransactionDefinition definition = transactionAttribute == null
                ? new DefaultTransactionDefinition()
                : new DefaultTransactionDefinition(transactionAttribute);
        definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        PlatformTransactionManager transactionManager = resolveTransactionManager(transactionAttribute, targetClass);
        TransactionStatus transactionStatus = null;
        try {
            transactionStatus = transactionManager.getTransaction(definition);
            Object result = point.proceed();
            transactionManager.commit(transactionStatus);
            return result;
        } catch (Throwable throwable) {
            if (transactionStatus != null) {
                boolean shouldRollback = transactionAttribute == null || transactionAttribute.rollbackOn(throwable);
                if (!transactionStatus.isCompleted() && shouldRollback) {
                    transactionManager.rollback(transactionStatus);
                } else if (!transactionStatus.isCompleted()) {
                    transactionManager.commit(transactionStatus);
                }
            }
            throw throwable;
        }
    }

    private PlatformTransactionManager resolveTransactionManager(TransactionAttribute transactionAttribute, Class<?> targetClass) {
        String qualifier = transactionAttribute == null ? null : transactionAttribute.getQualifier();
        if (StringUtils.hasText(qualifier)) {
            return BeanFactoryAnnotationUtils.qualifiedBeanOfType(beanFactory, PlatformTransactionManager.class, qualifier);
        }

        String typeQualifier = BeanFactoryAnnotationUtils.getQualifierValue(targetClass);
        if (StringUtils.hasText(typeQualifier)) {
            try {
                return BeanFactoryAnnotationUtils.qualifiedBeanOfType(beanFactory, PlatformTransactionManager.class, typeQualifier);
            } catch (NoSuchBeanDefinitionException ignored) {
                // Spring treats a type-level qualifier as optional and falls back to the configured default manager.
            }
        }

        TransactionManagementConfigurer configurer = beanFactory
                .getBeanProvider(TransactionManagementConfigurer.class)
                .getIfUnique();
        if (configurer != null) {
            TransactionManager transactionManager = configurer.annotationDrivenTransactionManager();
            if (transactionManager instanceof PlatformTransactionManager platformTransactionManager) {
                return platformTransactionManager;
            }
            throw new IllegalStateException("A PlatformTransactionManager is required for nested cross-datasource transactions");
        }

        return beanFactory.getBean(PlatformTransactionManager.class);
    }

    private record MethodCacheKey(Class<?> targetClass, Method method) {
    }

    private record DataSourceCacheEntry(boolean needSwitch, String dataSourceValue) {
    }
}
