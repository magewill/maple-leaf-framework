package cn.maple.elasticsearch.support;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Creates an ElasticsearchOperations proxy that delegates to the template selected
 * by GXElasticsearchTemplateContext at invocation time.
 */
public final class GXDynamicElasticsearchOperations {
    private GXDynamicElasticsearchOperations() {
    }

    public static ElasticsearchOperations create(String defaultTemplateName) {
        return create(defaultTemplateName, Function.identity());
    }

    private static ElasticsearchOperations create(String defaultTemplateName, Function<ElasticsearchOperations, ElasticsearchOperations> operationsCustomizer) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args, defaultTemplateName);
            }
            if (IndexOperations.class.equals(method.getReturnType()) && "indexOps".equals(method.getName())) {
                return createIndexOperations(defaultTemplateName, operationsCustomizer, method, args);
            }
            if (ElasticsearchOperations.class.equals(method.getReturnType()) && "withRefreshPolicy".equals(method.getName())) {
                return create(defaultTemplateName, operations -> operationsCustomizer.apply(operations).withRefreshPolicy(args == null ? null : (org.springframework.data.elasticsearch.core.RefreshPolicy) args[0]));
            }
            if (ElasticsearchOperations.class.equals(method.getReturnType()) && "withRouting".equals(method.getName())) {
                return create(defaultTemplateName, operations -> operationsCustomizer.apply(operations).withRouting((org.springframework.data.elasticsearch.core.routing.RoutingResolver) args[0]));
            }
            ElasticsearchOperations target = operationsCustomizer.apply(getTargetTemplate(defaultTemplateName));
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        };
        return (ElasticsearchOperations) Proxy.newProxyInstance(
                ElasticsearchOperations.class.getClassLoader(),
                new Class[]{ElasticsearchOperations.class},
                invocationHandler
        );
    }

    private static IndexOperations createIndexOperations(String defaultTemplateName,
                                                         Function<ElasticsearchOperations, ElasticsearchOperations> operationsCustomizer,
                                                         Method indexOpsMethod,
                                                         Object[] indexOpsArgs) {
        Supplier<IndexOperations> targetSupplier = () -> {
            try {
                return (IndexOperations) indexOpsMethod.invoke(operationsCustomizer.apply(getTargetTemplate(defaultTemplateName)), indexOpsArgs);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e.getTargetException());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        };
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args, defaultTemplateName);
            }
            try {
                return method.invoke(targetSupplier.get(), args);
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        };
        return (IndexOperations) Proxy.newProxyInstance(
                IndexOperations.class.getClassLoader(),
                new Class[]{IndexOperations.class},
                invocationHandler
        );
    }

    private static ElasticsearchTemplate getTargetTemplate(String defaultTemplateName) {
        String templateName = GXElasticsearchTemplateContext.getTemplateName();
        if (CharSequenceUtil.isBlank(templateName)) {
            templateName = defaultTemplateName;
        }
        return GXSpringContextUtils.getBean(templateName, ElasticsearchTemplate.class);
    }

    private static Object invokeObjectMethod(Object proxy, Method method, Object[] args, String defaultTemplateName) {
        return switch (method.getName()) {
            case "toString" -> "GXDynamicElasticsearchOperations[" + defaultTemplateName + "]";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.toString());
        };
    }
}
