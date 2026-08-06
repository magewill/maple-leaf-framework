package cn.maple.core.datasource.aspect.advisor;

import cn.maple.core.datasource.aspect.GXDataSourceAspect;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Spring AOP 在 CGLIB 代理下按实现方法匹配切点，无法通过 {@code @annotation}
 * 识别仅标注在接口方法上的 {@code @GXDataSource}。此 Advisor 只匹配该场景，
 * 以复用既有数据源切换与事务处理逻辑，避免扩大全局切点或遗漏数据源切换。
 */
@Component
public class GXDataSourceInterfaceMethodAdvisor extends StaticMethodMatcherPointcutAdvisor {
    private final GXDataSourceAspect dataSourceAspect;

    public GXDataSourceInterfaceMethodAdvisor(GXDataSourceAspect dataSourceAspect) {
        this.dataSourceAspect = dataSourceAspect;
        setAdvice((MethodInterceptor) invocation -> {
            Object target = invocation.getThis();
            if (target == null) {
                return invocation.proceed();
            }
            return dataSourceAspect.around(target, invocation.getMethod(), invocation::proceed);
        });
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return dataSourceAspect.findInterfaceMethodAnnotation(targetClass, method) != null;
    }
}
