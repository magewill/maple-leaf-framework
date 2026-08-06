package cn.maple.core.datasource.aspect.advisor;

import cn.maple.core.datasource.annotation.GXDataFilter;
import cn.maple.core.datasource.aspect.GXDataFilterAspect;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Spring AOP 在 CGLIB 代理下按实现方法匹配切点，无法通过 {@code @annotation}
 * 识别仅标注在接口方法上的 {@link GXDataFilter}。此 Advisor 只匹配该场景，
 * 以触发既有数据过滤切面，避免扩大全局切点或遗漏数据权限过滤。
 */
@Component
public class GXDataFilterInterfaceMethodAdvisor extends StaticMethodMatcherPointcutAdvisor {
    private final GXDataFilterAspect dataFilterAspect;

    public GXDataFilterInterfaceMethodAdvisor(GXDataFilterAspect dataFilterAspect) {
        this.dataFilterAspect = dataFilterAspect;
        setAdvice((MethodInterceptor) invocation -> {
            Object target = invocation.getThis();
            Class<?> targetClass = target == null ? invocation.getMethod().getDeclaringClass() : target.getClass();
            GXDataFilter dataFilter = dataFilterAspect.findInterfaceMethodAnnotation(targetClass, invocation.getMethod());
            String methodName = invocation.getMethod().getDeclaringClass().getName() + "." + invocation.getMethod().getName();
            return dataFilterAspect.invokeWithDataFilter(invocation.getArguments(), methodName, dataFilter, invocation::proceed);
        });
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return dataFilterAspect.findInterfaceMethodAnnotation(targetClass, method) != null;
    }
}
