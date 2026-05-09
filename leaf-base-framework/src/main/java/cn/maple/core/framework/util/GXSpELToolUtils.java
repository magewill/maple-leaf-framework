package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.*;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility methods for evaluating SpEL expressions against framework objects.
 *
 * <p>The parsed expression cache is shared and thread-safe. Evaluation contexts
 * are created per invocation because {@link StandardEvaluationContext} contains
 * mutable variables and must not be reused across concurrent calls.</p>
 */
public class GXSpELToolUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXSpELToolUtils.class);

    private static final String METHOD_NOT_FOUND_TIPS_TEMPLATE = "Target class {} has no method named {}({}).";

    private static final String EXPRESSION_EMPTY_TIPS = "Expression must not be blank.";

    private static final String DATA_EMPTY_TIPS = "Data object must not be null.";

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private static final int MAX_CACHE_SIZE = 1024;

    private static final Cache<String, Expression> EXPRESSION_CACHE = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    private static final Method METHOD_NOT_FOUND = initMethodNotFound();

    private static final Cache<String, Method> METHOD_CACHE = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    private GXSpELToolUtils() {
        throw new AssertionError("Utility class, cannot be instantiated");
    }

    private static Method initMethodNotFound() {
        try {
            return GXSpELToolUtils.class.getDeclaredMethod("__methodNotFound");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unused")
    private static void __methodNotFound() {
    }

    /**
     * Evaluates an expression with a {@link Dict} exposed as a named variable.
     *
     * <p>Example: {@code calculateSpELExpression(dict, "#data['amount'] * 2", Integer.class, "data")}.</p>
     */
    public static <T> T calculateSpELExpression(Dict data, String expressionString, Class<T> beanClass, String dataKey) {
        if (Objects.isNull(data)) {
            LOG.warn(DATA_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(beanClass);
        }
        if (CharSequenceUtil.isBlank(expressionString)) {
            LOG.warn(EXPRESSION_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(beanClass);
        }
        String variableName = CharSequenceUtil.isBlank(dataKey) ? "data" : dataKey;
        EvaluationContext context = contextBuilder()
                .addVariable(variableName, data)
                .build();
        return getValue(context, expressionString, beanClass);
    }

    public static <T> T calculateSpELExpression(Dict data, String expressionString, Class<T> beanClass) {
        return calculateSpELExpression(data, expressionString, beanClass, "data");
    }

    /**
     * Evaluates an expression with the supplied target object as the SpEL root.
     */
    public static <T> T calculateSpELExpression(Object targetObject, String expressionString, Class<T> beanClazz) {
        if (Objects.isNull(targetObject)) {
            LOG.warn("Target object must not be null.");
            return GXCommonUtils.getClassDefaultValue(beanClazz);
        }
        if (CharSequenceUtil.isBlank(expressionString)) {
            LOG.warn(EXPRESSION_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(beanClazz);
        }
        StandardEvaluationContext context = contextBuilder(targetObject).build();
        return getValue(context, expressionString, beanClazz);
    }

    /**
     * Assigns multiple SpEL paths on a target object and returns the value of
     * {@code targetKey} after assignment.
     */
    public static <T> T assignmentSpELExpression(Object targetObj, Dict data, String targetKey, Class<T> clazz) {
        if (Objects.isNull(targetObj)) {
            LOG.warn("Target object must not be null.");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        if (Objects.isNull(data)) {
            LOG.warn(DATA_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        if (CharSequenceUtil.isBlank(targetKey)) {
            LOG.warn("Target property expression must not be blank.");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        final StandardEvaluationContext context = contextBuilder(targetObj).build();
        if (data.isEmpty()) {
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        try {
            data.forEach((key, value) -> {
                final Expression expression = getOrCreateExpression(String.valueOf(key));
                expression.setValue(context, value);
            });
            final Expression expression = getOrCreateExpression(targetKey);
            return expression.getValue(context, clazz);
        } catch (SpelEvaluationException e) {
            LOG.error("Failed to assign SpEL expression: targetKey={}, error={}", targetKey, e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error while assigning SpEL expression: targetKey={}, type={}, error={}",
                    targetKey, e.getClass().getName(), e.getMessage());
        }
        return GXCommonUtils.getClassDefaultValue(clazz);
    }

    /**
     * Registers a static method as a SpEL function and invokes it.
     */
    public static <T> T registerFunctionSpELExpression(Class<?> targetClass, String methodName, Class<T> clazz,
                                                       Class<?>[] methodParamTypes, Object... params) {
        if (Objects.isNull(targetClass)) {
            LOG.warn("Target class must not be null.");
            return null;
        }
        if (CharSequenceUtil.isBlank(methodName)) {
            LOG.warn("Method name must not be blank.");
            return null;
        }
        methodParamTypes = normalizeParameterTypes(methodParamTypes);
        params = normalizeParams(params);
        if (invalidArgumentCount(methodName, methodParamTypes, params)) {
            return null;
        }

        if (methodNotExists(targetClass, methodName, methodParamTypes)) {
            return null;
        }

        StandardEvaluationContext context = contextBuilder()
                .addVariable("params", params)
                .registerFunction(methodName, targetClass, methodName, methodParamTypes)
                .build();
        final String expressionString = CharSequenceUtil.format("#{}({})", methodName, parsePlaceholderParams(methodParamTypes, params));
        final Expression expression = getOrCreateExpression(expressionString);
        try {
            return expression.getValue(context, clazz);
        } catch (SpelEvaluationException e) {
            LOG.error("Failed to invoke registered function: method={}, error={}", methodName, e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error while invoking registered function: method={}, type={}, error={}",
                    methodName, e.getClass().getName(), e.getMessage());
        }
        return null;
    }

    /**
     * Gets a Spring bean by class and invokes one of its methods through SpEL.
     */
    public static <T> T callBeanMethodSpELExpression(Class<?> beanClazz, String methodName, Class<T> clazz,
                                                     Class<?>[] methodParamTypes, Object... params) {
        if (Objects.isNull(beanClazz)) {
            LOG.warn("Bean class must not be null.");
            return null;
        }
        if (CharSequenceUtil.isBlank(methodName)) {
            LOG.warn("Method name must not be blank.");
            return null;
        }
        methodParamTypes = normalizeParameterTypes(methodParamTypes);
        params = normalizeParams(params);
        if (invalidArgumentCount(methodName, methodParamTypes, params)) {
            return null;
        }
        final Object beanObj = GXSpringContextUtils.getBean(beanClazz);
        if (Objects.isNull(beanObj)) {
            LOG.warn("Spring bean not found: type={}", beanClazz.getName());
            return null;
        }
        if (methodNotExists(beanClazz, methodName, methodParamTypes)) {
            return null;
        }

        return invokeRootMethod(beanObj, methodName, clazz, methodParamTypes, params);
    }

    public static <T> T callTargetObjectMethodSpELExpression(@NotNull Object targetObject, String methodName,
                                                             Class<T> clazz, Class<?>[] methodParamTypes,
                                                             Object... params) {
        if (Objects.isNull(targetObject)) {
            LOG.warn("Target object must not be null.");
            return null;
        }
        if (CharSequenceUtil.isBlank(methodName)) {
            LOG.warn("Method name must not be blank.");
            return null;
        }
        methodParamTypes = normalizeParameterTypes(methodParamTypes);
        params = normalizeParams(params);
        if (invalidArgumentCount(methodName, methodParamTypes, params)) {
            return null;
        }

        final Method method = getMethodFromCache(targetObject.getClass(), methodName, methodParamTypes);
        if (Objects.isNull(method)) {
            final String paramStr = parameterTypesToString(methodParamTypes);
            LOG.debug(METHOD_NOT_FOUND_TIPS_TEMPLATE, targetObject.getClass().getSimpleName(), methodName, paramStr);
            return null;
        }

        return invokeRootMethod(targetObject, methodName, clazz, methodParamTypes, params);
    }

    /**
     * Sets an expression value on a root object and returns the previous value.
     */
    public static <T> T setValueBySpELExpression(Object targetObject, String expressionString,
                                                 Class<T> oldValueClazz, T newValue) {
        if (Objects.isNull(targetObject)) {
            LOG.warn("Target object must not be null.");
            return null;
        }
        if (CharSequenceUtil.isBlank(expressionString)) {
            LOG.warn(EXPRESSION_EMPTY_TIPS);
            return null;
        }
        if (Objects.isNull(newValue)) {
            LOG.warn("New value must not be null.");
            return null;
        }

        StandardEvaluationContext context = contextBuilder(targetObject).build();
        final Expression expression = getOrCreateExpression(expressionString);

        try {
            final T oldValue = expression.getValue(context, oldValueClazz);
            expression.setValue(context, newValue);
            return oldValue;
        } catch (SpelEvaluationException e) {
            LOG.error("Failed to set object value: expression={}, newValue={}, error={}", expressionString, newValue, e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error while setting object value: expression={}, newValue={}, type={}, error={}",
                    expressionString, newValue, e.getClass().getName(), e.getMessage());
        }
        return null;
    }

    /**
     * Sets a value inside a {@link Dict} exposed as {@code #data} and returns
     * the previous value.
     */
    public static <T> T setValueBySpELExpression(Dict dict, String expressionString,
                                                 Class<T> oldValueClazz, T newValue) {
        if (Objects.isNull(dict)) {
            LOG.warn(DATA_EMPTY_TIPS);
            return null;
        }
        if (CharSequenceUtil.isBlank(expressionString)) {
            LOG.warn(EXPRESSION_EMPTY_TIPS);
            return null;
        }
        if (Objects.isNull(newValue)) {
            LOG.warn("New value must not be null.");
            return null;
        }
        EvaluationContext context = contextBuilder()
                .addVariable("data", dict)
                .build();
        final Expression expression = getOrCreateExpression(expressionString);
        try {
            final T oldValue = expression.getValue(context, oldValueClazz);
            expression.setValue(context, newValue);
            return oldValue;
        } catch (SpelEvaluationException e) {
            LOG.error("Failed to set dict value: expression={}, newValue={}, error={}", expressionString, newValue, e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error while setting dict value: expression={}, newValue={}, type={}, error={}",
                    expressionString, newValue, e.getClass().getName(), e.getMessage());
        }
        return null;
    }

    public static void clearExpressionCache() {
        EXPRESSION_CACHE.invalidateAll();
        LOG.debug("SpEL expression cache cleared.");
    }

    public static void clearMethodCache() {
        METHOD_CACHE.invalidateAll();
        LOG.debug("SpEL method cache cleared.");
    }

    public static void clearCaches() {
        clearExpressionCache();
        clearMethodCache();
    }

    public static ContextBuilder contextBuilder(Object rootObject) {
        return new ContextBuilder(rootObject);
    }

    public static ContextBuilder contextBuilder() {
        return new ContextBuilder();
    }

    private static <T> T getValue(EvaluationContext context, String expressionString, Class<T> beanClass) {
        try {
            final Expression expression = getOrCreateExpression(expressionString);
            return expression.getValue(context, beanClass);
        } catch (SpelEvaluationException e) {
            LOG.error("Failed to evaluate SpEL expression: expression={}, error={}", expressionString, e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error while evaluating SpEL expression: expression={}, type={}, error={}",
                    expressionString, e.getClass().getName(), e.getMessage());
        }
        return GXCommonUtils.getClassDefaultValue(beanClass);
    }

    private static boolean methodNotExists(Class<?> beanClazz, String methodName, Class<?>[] methodParamTypes) {
        final Method method = getMethodFromCache(beanClazz, methodName, methodParamTypes);
        if (Objects.isNull(method)) {
            LOG.error(METHOD_NOT_FOUND_TIPS_TEMPLATE, beanClazz.getSimpleName(), methodName, parameterTypesToString(methodParamTypes));
            return true;
        }
        return false;
    }

    private static <T> T invokeRootMethod(Object rootObject, String methodName, Class<T> clazz,
                                          Class<?>[] methodParamTypes, Object... params) {
        StandardEvaluationContext context = contextBuilder(rootObject).build();
        final String expressionString = CharSequenceUtil.format("{}({})", methodName,
                parseArgumentParams(context, methodParamTypes, params));
        final Expression expression = getOrCreateExpression(expressionString);

        try {
            return expression.getValue(context, clazz);
        } catch (SpelEvaluationException e) {
            LOG.error("Failed to invoke root method: method={}, error={}", methodName, e.getMessage());
        } catch (Exception e) {
            LOG.error("Unexpected error while invoking root method: method={}, type={}, error={}",
                    methodName, e.getClass().getName(), e.getMessage());
        }
        return null;
    }

    private static String parsePlaceholderParams(Class<?>[] methodParamTypes, Object... params) {
        params = normalizeParams(params);
        StringBuilder methodParam = new StringBuilder();
        for (int i = 0; i < methodParamTypes.length; i++) {
            if (i >= params.length) {
                methodParam.append("null , ");
                continue;
            }
            methodParam.append("#params[").append(i).append("] , ");
        }
        return trimLastComma(methodParam);
    }

    private static String parseArgumentParams(EvaluationContext context, Class<?>[] methodParamTypes, Object... params) {
        params = normalizeParams(params);
        StringBuilder methodParam = new StringBuilder();
        try {
            for (int i = 0; i < methodParamTypes.length; i++) {
                if (i >= params.length || params[i] == null) {
                    methodParam.append("null , ");
                    continue;
                }
                final String name = "p" + i;
                context.setVariable(name, params[i]);
                methodParam.append("#").append(name).append(" , ");
            }
            return trimLastComma(methodParam);
        } catch (Exception e) {
            LOG.error("Failed to parse method arguments: type={}, error={}", e.getClass().getName(), e.getMessage());
            return "";
        }
    }

    private static String trimLastComma(StringBuilder methodParam) {
        return CharSequenceUtil.subBefore(methodParam.toString(), ',', true).trim();
    }

    private static Class<?>[] normalizeParameterTypes(Class<?>[] methodParamTypes) {
        return methodParamTypes == null ? new Class[0] : methodParamTypes;
    }

    private static Object[] normalizeParams(Object[] params) {
        return params == null ? new Object[0] : params;
    }

    private static boolean invalidArgumentCount(String methodName, Class<?>[] methodParamTypes, Object[] params) {
        if (methodParamTypes.length == params.length) {
            return false;
        }
        LOG.warn("Method argument count mismatch: method={}, expected={}, actual={}",
                methodName, methodParamTypes.length, params.length);
        return true;
    }

    private static String parameterTypesToString(Class<?>[] parameterTypes) {
        if (parameterTypes == null || parameterTypes.length == 0) {
            return "";
        }
        return Arrays.stream(parameterTypes)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(","));
    }

    private static Expression getOrCreateExpression(String expressionString) {
        return EXPRESSION_CACHE.get(expressionString, PARSER::parseExpression);
    }

    private static Method getMethodFromCache(Class<?> targetClass, String methodName, Class<?>[] parameterTypes) {
        Class<?>[] actualParameterTypes = normalizeParameterTypes(parameterTypes);
        String cacheKey = generateMethodCacheKey(targetClass, methodName, actualParameterTypes);

        Method method = METHOD_CACHE.get(cacheKey, key -> {
            try {
                Method resolvedMethod = ReflectUtil.getMethod(targetClass, methodName, actualParameterTypes);
                if (resolvedMethod != null) {
                    return resolvedMethod;
                }
                return targetClass.getDeclaredMethod(methodName, actualParameterTypes);
            } catch (Exception e) {
                LOG.debug("Failed to resolve method: class={}, method={}, parameterTypes={}, error={}",
                        targetClass.getName(), methodName,
                        Arrays.toString(actualParameterTypes),
                        e.getMessage());
                return METHOD_NOT_FOUND;
            }
        });
        return method == METHOD_NOT_FOUND ? null : method;
    }

    static long methodCacheEstimatedSize() {
        METHOD_CACHE.cleanUp();
        return METHOD_CACHE.estimatedSize();
    }

    private static String generateMethodCacheKey(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
        return clazz.getName() + "#" + methodName + "#" +
                (parameterTypes.length > 0 ?
                        Arrays.stream(parameterTypes)
                                .map(Class::getName)
                                .collect(Collectors.joining(",")) :
                        "");
    }

    public static class ContextBuilder {
        private final StandardEvaluationContext context;

        private ContextBuilder(@NotNull Object rootObject) {
            this.context = new StandardEvaluationContext(rootObject);
        }

        private ContextBuilder() {
            this.context = new StandardEvaluationContext();
        }

        public ContextBuilder addVariable(@NotNull String name, Object value) {
            if (CharSequenceUtil.isBlank(name)) {
                throw new IllegalArgumentException("Variable name must not be blank.");
            }
            context.setVariable(name, value);
            return this;
        }

        public ContextBuilder addVariables(Map<String, Object> variables) {
            if (variables != null && !variables.isEmpty()) {
                variables.forEach((name, value) -> {
                    if (CharSequenceUtil.isNotBlank(name)) {
                        context.setVariable(name, value);
                    }
                });
            }
            return this;
        }

        public ContextBuilder registerFunction(@NotNull String name, @NotNull Class<?> targetClass,
                                               @NotNull String methodName, Class<?>[] parameterTypes) {
            if (CharSequenceUtil.isBlank(name)) {
                throw new IllegalArgumentException("Function name must not be blank.");
            }
            if (targetClass == null) {
                throw new IllegalArgumentException("Target class must not be null.");
            }
            if (CharSequenceUtil.isBlank(methodName)) {
                throw new IllegalArgumentException("Method name must not be blank.");
            }

            Class<?>[] actualParameterTypes = normalizeParameterTypes(parameterTypes);
            try {
                Method method = getMethodFromCache(targetClass, methodName, actualParameterTypes);
                if (method != null) {
                    context.registerFunction(name, method);
                } else {
                    LOG.warn("Failed to register SpEL function: class={}, method={}, error=method not found", targetClass.getName(), methodName);
                }
            } catch (Exception e) {
                LOG.error("Unexpected error while registering SpEL function: class={}, method={}, type={}, error={}",
                        targetClass.getName(), methodName, e.getClass().getName(), e.getMessage());
            }
            return this;
        }

        public ContextBuilder setTypeConverter(@NotNull TypeConverter typeConverter) {
            if (typeConverter == null) {
                throw new IllegalArgumentException("TypeConverter must not be null.");
            }
            context.setTypeConverter(typeConverter);
            return this;
        }

        public ContextBuilder setPropertyAccessors(@NotNull List<PropertyAccessor> propertyAccessors) {
            if (propertyAccessors == null || propertyAccessors.isEmpty()) {
                throw new IllegalArgumentException("PropertyAccessors must not be null or empty.");
            }
            context.setPropertyAccessors(propertyAccessors);
            return this;
        }

        public ContextBuilder setMethodResolvers(@NotNull List<MethodResolver> methodResolvers) {
            if (methodResolvers == null || methodResolvers.isEmpty()) {
                throw new IllegalArgumentException("MethodResolvers must not be null or empty.");
            }
            context.setMethodResolvers(methodResolvers);
            return this;
        }

        public StandardEvaluationContext build() {
            return context;
        }
    }
}
