package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GXSpELToolUtils {
    private static final Logger LOG = LoggerFactory.getLogger(GXSpELToolUtils.class);

    private static final String METHOD_NOT_FOUND_TIPS_TEMPLATE = "目标类{}中没有满足签名为{}({})的方法存在~~~";

    private static final String EXPRESSION_EMPTY_TIPS = "表达式不能为空";

    private static final String DATA_EMPTY_TIPS = "数据对象不能为空";

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>(256, 0.75f);

    private static final int MAX_CACHE_SIZE = 1024;

    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>(256);

    private GXSpELToolUtils() {
    }

    public static <T> T calculateSpELExpression(Dict data, String expressionString, Class<T> beanClass, String dataKey) {
        if (Objects.isNull(data)) {
            LOG.warn(DATA_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(beanClass);
        }
        if (CharSequenceUtil.isBlank(expressionString)) {
            LOG.warn(EXPRESSION_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(beanClass);
        }
        LOG.debug("开始计算SpEL表达式: {}, 数据键名: {}", expressionString, dataKey);
        dataKey = Objects.isNull(dataKey) ? "data" : dataKey;
        EvaluationContext context = contextBuilder()
                .addVariable(dataKey, data)
                .build();
        try {
            final Expression expression = getOrCreateExpression(expressionString);
            T result = expression.getValue(context, beanClass);
            LOG.debug("SpEL表达式计算成功: {}, 结果: {}", expressionString, result);
            return result;
        } catch (SpelEvaluationException e) {
            LOG.error("SpEL表达式计算失败, 表达式: {}, 异常信息: {}", expressionString, e.getMessage());
        } catch (Exception e) {
            LOG.error("SpEL表达式计算发生未知异常, 表达式: {}, 异常类型: {}, 异常信息: {}", expressionString, e.getClass().getName(), e.getMessage());
        }
        return GXCommonUtils.getClassDefaultValue(beanClass);
    }

    public static <T> T calculateSpELExpression(Dict data, String expressionString, Class<T> beanClass) {
        return calculateSpELExpression(data, expressionString, beanClass, "data");
    }

    public static <T> T calculateSpELExpression(Object targetObject, String expressionString, Class<T> beanClazz) {
        if (Objects.isNull(targetObject)) {
            LOG.warn("目标对象不能为空");
            return GXCommonUtils.getClassDefaultValue(beanClazz);
        }
        if (CharSequenceUtil.isBlank(expressionString)) {
            LOG.warn(EXPRESSION_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(beanClazz);
        }
        LOG.debug("开始计算目标对象SpEL表达式: {}, 目标对象类型: {}", expressionString, targetObject.getClass().getName());
        try {
            StandardEvaluationContext context = contextBuilder(targetObject).build();
            final Expression expression = getOrCreateExpression(expressionString);
            T result = expression.getValue(context, beanClazz);
            LOG.debug("目标对象SpEL表达式计算成功: {}, 结果: {}", expressionString, result);
            return result;
        } catch (SpelEvaluationException e) {
            LOG.error("目标对象SpEL表达式计算失败, 表达式: {}, 异常信息: {}", expressionString, e.getMessage());
        } catch (Exception e) {
            LOG.error("目标对象SpEL表达式计算发生未知异常, 表达式: {}, 异常类型: {}, 异常信息: {}", expressionString, e.getClass().getName(), e.getMessage());
        }
        return GXCommonUtils.getClassDefaultValue(beanClazz);
    }

    public static <T> T assignmentSpELExpression(Object targetObj, Dict data, String targetKey, Class<T> clazz) {
        if (Objects.isNull(targetObj)) {
            LOG.warn("目标对象不能为空");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        if (Objects.isNull(data)) {
            LOG.warn(DATA_EMPTY_TIPS);
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        if (CharSequenceUtil.isBlank(targetKey)) {
            LOG.warn("目标属性名不能为空");
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        LOG.debug("开始设置目标对象属性值, 目标对象类型: {}, 目标属性: {}", targetObj.getClass().getName(), targetKey);
        final StandardEvaluationContext inventorContext = contextBuilder(targetObj).build();
        if (data.isEmpty()) {
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        data.forEach((key, value) -> {
            final Expression expression = getOrCreateExpression(key);
            expression.setValue(inventorContext, value);
        });
        final Expression expression = getOrCreateExpression(targetKey);
        T result = expression.getValue(inventorContext, clazz);
        LOG.debug("目标对象属性值设置成功, 目标属性: {}, 结果: {}", targetKey, result);
        return result;
    }

    public static <T> T registerFunctionSpELExpression(Class<?> targetClass, String methodName, Class<T> clazz,
                                                       Class<?>[] methodParamTypes, Object... params) {
        if (Objects.isNull(targetClass)) {
            LOG.warn("目标类不能为空");
            return null;
        }
        if (CharSequenceUtil.isBlank(methodName)) {
            LOG.warn("方法名不能为空");
            return null;
        }
        if (Objects.isNull(methodParamTypes) || methodParamTypes.length == 0) {
            LOG.warn("方法参数类型不能为空");
            return null;
        }
        LOG.debug("开始注册并调用函数, 目标类: {}, 方法名: {}", targetClass.getName(), methodName);

        if (methodNotExists(targetClass, methodName, methodParamTypes)) {
            return null;
        }

        StandardEvaluationContext context = contextBuilder()
                .addVariable("params", params)
                .registerFunction(methodName, targetClass, methodName, methodParamTypes)
                .build();
        final String format = CharSequenceUtil.format("#{}({})", methodName, parsePlaceholderParams(methodParamTypes, params));
        final Expression expression = getOrCreateExpression(format);
        T result = expression.getValue(context, clazz);
        LOG.debug("函数调用成功, 方法名: {}, 结果: {}", methodName, result);
        return result;
    }

    public static <T> T callBeanMethodSpELExpression(Class<?> beanClazz, String methodName, Class<T> clazz,
                                                     Class<?>[] methodParamTypes, Object... params) {
        if (Objects.isNull(beanClazz)) {
            LOG.warn("Bean类型不能为空");
            return null;
        }
        if (CharSequenceUtil.isBlank(methodName)) {
            LOG.warn("方法名不能为空");
            return null;
        }
        if (Objects.isNull(methodParamTypes) || methodParamTypes.length == 0) {
            LOG.warn("方法参数类型不能为空");
            return null;
        }
        LOG.debug("开始调用Bean方法, Bean类型: {}, 方法名: {}", beanClazz.getName(), methodName);
        final Object beanObj = GXSpringContextUtils.getBean(beanClazz);
        if (Objects.isNull(beanObj)) {
            LOG.warn("未找到Bean实例, Bean类型: {}", beanClazz.getName());
            return null;
        }
        if (methodNotExists(beanClazz, methodName, methodParamTypes)) {
            return null;
        }

        StandardEvaluationContext context = contextBuilder(beanObj).build();
        final String expressionString = CharSequenceUtil.format("{}({})", methodName,
                parseArgumentParams(context, methodParamTypes, params));
        final Expression expression = getOrCreateExpression(expressionString);
        try {
            T result = expression.getValue(context, clazz);
            LOG.debug("Bean方法调用成功, 方法名: {}, 结果: {}", methodName, result);
            return result;
        } catch (SpelEvaluationException e) {
            LOG.error("Bean方法调用失败, 方法名: {}, 异常信息: {}", methodName, e.getMessage());
        } catch (Exception e) {
            LOG.error("Bean方法调用发生未知异常, 方法名: {}, 异常类型: {}, 异常信息: {}",
                    methodName, e.getClass().getName(), e.getMessage());
        }
        return null;
    }

    private static boolean methodNotExists(Class<?> beanClazz, String methodName, Class<?>[] methodParamTypes) {
        final Method method = ReflectUtil.getMethod(beanClazz, methodName, methodParamTypes);
        if (Objects.isNull(method)) {
            final String paramStr = Arrays.stream(methodParamTypes)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(","));
            LOG.error(METHOD_NOT_FOUND_TIPS_TEMPLATE, beanClazz.getSimpleName(), methodName, paramStr);
            return true;
        }
        return false;
    }

    public static <T> T callTargetObjectMethodSpELExpression(@NotNull Object targetObject, String methodName,
                                                             Class<T> clazz, Class<?>[] methodParamTypes,
                                                             Object... params) {
        if (CharSequenceUtil.isBlank(methodName)) {
            LOG.warn("方法名不能为空");
            return null;
        }
        if (Objects.isNull(methodParamTypes) || methodParamTypes.length == 0) {
            LOG.warn("方法参数类型不能为空");
            return null;
        }
        LOG.debug("开始调用目标对象方法, 目标对象类型: {}, 方法名: {}", targetObject.getClass().getName(), methodName);

        final Method method = getMethodFromCache(targetObject.getClass(), methodName, methodParamTypes);
        if (Objects.isNull(method)) {
            final String paramStr = Arrays.stream(methodParamTypes)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(","));
            LOG.debug(METHOD_NOT_FOUND_TIPS_TEMPLATE, targetObject.getClass().getSimpleName(), methodName, paramStr);
            return null;
        }

        StandardEvaluationContext context = contextBuilder(targetObject).build();
        final String expressionString = CharSequenceUtil.format("{}({})", methodName,
                parseArgumentParams(context, methodParamTypes, params));
        final Expression expression = getOrCreateExpression(expressionString);

        try {
            T result = expression.getValue(context, clazz);
            LOG.debug("目标对象方法调用成功, 方法名: {}, 结果: {}", methodName, result);
            return result;
        } catch (SpelEvaluationException e) {
            LOG.error("目标对象方法调用失败, 方法名: {}, 异常信息: {}", methodName, e.getMessage());
        } catch (Exception e) {
            LOG.error("目标对象方法调用发生未知异常, 方法名: {}, 异常类型: {}, 异常信息: {}",
                    methodName, e.getClass().getName(), e.getMessage());
        }
        return null;
    }

    public static <T> T setValueBySpELExpression(Object targetObject, String expressionString,
                                                 Class<T> oldValueClazz, T newValue) {
        if (Objects.isNull(targetObject)) {
            LOG.warn("目标对象不能为空");
            return null;
        }
        if (CharSequenceUtil.isBlank(expressionString)) {
            LOG.warn(EXPRESSION_EMPTY_TIPS);
            return null;
        }
        if (Objects.isNull(newValue)) {
            LOG.warn("新值不能为空");
            return null;
        }
        LOG.debug("开始设置对象属性值, 目标对象类型: {}, 表达式: {}", targetObject.getClass().getName(), expressionString);

        StandardEvaluationContext context = contextBuilder(targetObject).build();
        final Expression expression = getOrCreateExpression(expressionString);

        try {
            final T oldValue = expression.getValue(context, oldValueClazz);
            expression.setValue(context, newValue);
            LOG.debug("对象属性值设置成功, 表达式: {}, 旧值: {}, 新值: {}", expressionString, oldValue, newValue);
            return oldValue;
        } catch (SpelEvaluationException e) {
            LOG.error("对象属性值设置失败, 表达式: {}, 新值: {}, 异常信息: {}", expressionString, newValue, e.getMessage());
        } catch (Exception e) {
            LOG.error("对象属性值设置发生未知异常, 表达式: {}, 新值: {}, 异常类型: {}, 异常信息: {}",
                    expressionString, newValue, e.getClass().getName(), e.getMessage());
        }
        return null;
    }

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
            LOG.warn("新值不能为空");
            return null;
        }
        LOG.debug("开始设置Dict对象属性值, 表达式: {}", expressionString);
        EvaluationContext context = new StandardEvaluationContext();
        String dataKey = "data";
        context.setVariable(dataKey, dict);
        final Expression expression = getOrCreateExpression(expressionString);
        final T oldValue = expression.getValue(context, oldValueClazz);
        expression.setValue(context, newValue);
        LOG.debug("Dict对象属性值设置成功, 表达式: {}, 旧值: {}, 新值: {}", expressionString, oldValue, newValue);
        return oldValue;
    }

    private static String parsePlaceholderParams(Class<?>[] methodParamTypes, Object... params) {
        StringBuilder methodParam = new StringBuilder();
        for (int i = 0; i < methodParamTypes.length; i++) {
            if (i >= params.length) {
                methodParam.append("null , ");
                continue;
            }
            methodParam.append("#params[").append(i).append("] , ");
        }
        return CharSequenceUtil.subBefore(methodParam.toString(), ',', true);
    }

    private static String parseArgumentParams(EvaluationContext context, Class<?>[] methodParamTypes, Object... params) {
        StringBuilder methodParam = new StringBuilder();
        try {
            for (int i = 0; i < methodParamTypes.length; i++) {
                if (i >= params.length) {
                    methodParam.append("null , ");
                    continue;
                }
                final Class<?> type = methodParamTypes[i];
                if (ClassUtil.isSimpleValueType(type)) {
                    if (type.getSimpleName().equalsIgnoreCase("string")) {
                        methodParam.append("'").append(params[i]).append("' , ");
                    } else {
                        methodParam.append(params[i]).append(" , ");
                    }
                } else {
                    final String name = CharSequenceUtil.replace(type.getName(), ".", "");
                    context.setVariable(name, params[i]);
                    methodParam.append("#").append(name).append(" , ");
                }
            }
            return CharSequenceUtil.subBefore(methodParam.toString(), ',', true);
        } catch (Exception e) {
            LOG.error("解析方法参数失败, 异常类型: {}, 异常信息: {}", e.getClass().getName(), e.getMessage());
            return "";
        }
    }

    private static Expression getOrCreateExpression(String expressionString) {
        // 检查缓存大小，超过阈值时清理一半的缓存
        if (EXPRESSION_CACHE.size() > MAX_CACHE_SIZE) {
            synchronized (EXPRESSION_CACHE) {
                if (EXPRESSION_CACHE.size() > MAX_CACHE_SIZE) {
                    LOG.debug("表达式缓存大小超过阈值，开始清理缓存，当前大小: {}", EXPRESSION_CACHE.size());
                    // 保留一半的缓存项
                    EXPRESSION_CACHE.keySet().stream()
                            .skip(EXPRESSION_CACHE.size() / 2)
                            .toList()
                            .forEach(EXPRESSION_CACHE::remove);
                    LOG.debug("表达式缓存清理完成，清理后大小: {}", EXPRESSION_CACHE.size());
                }
            }
        }
        return EXPRESSION_CACHE.computeIfAbsent(expressionString, PARSER::parseExpression);
    }

    public static void clearExpressionCache() {
        synchronized (EXPRESSION_CACHE) {
            EXPRESSION_CACHE.clear();
            LOG.debug("表达式缓存已清空");
        }
    }

    public static ContextBuilder contextBuilder(Object rootObject) {
        return new ContextBuilder(rootObject);
    }

    public static ContextBuilder contextBuilder() {
        return new ContextBuilder();
    }

    private static Method getMethodFromCache(Class<?> targetClass, String methodName, Class<?>[] parameterTypes) {
        String cacheKey = targetClass.getName() + "#" + methodName + "#" +
                (parameterTypes == null ? "" : Arrays.stream(parameterTypes)
                        .map(Class::getName)
                        .collect(Collectors.joining(",")));

        return METHOD_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                return ReflectUtil.getMethod(targetClass, methodName, parameterTypes);
            } catch (Exception e) {
                LOG.debug("获取方法失败: {}.{}, 参数类型: {}, 错误: {}",
                        targetClass.getName(), methodName,
                        parameterTypes == null ? "[]" : Arrays.toString(parameterTypes),
                        e.getMessage());
                return null;
            }
        });
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
                throw new IllegalArgumentException("变量名不能为空");
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
                throw new IllegalArgumentException("函数名不能为空");
            }
            if (targetClass == null) {
                throw new IllegalArgumentException("目标类不能为null");
            }
            if (CharSequenceUtil.isBlank(methodName)) {
                throw new IllegalArgumentException("方法名不能为空");
            }

            try {
                Method method = getMethodFromCache(targetClass, methodName, parameterTypes != null ? parameterTypes : new Class[0]);
                if (method == null) {
                    method = targetClass.getDeclaredMethod(methodName, parameterTypes != null ? parameterTypes : new Class[0]);
                    if (ObjectUtil.isNotNull(method)) {
                        String cacheKey = generateMethodCacheKey(targetClass, methodName, parameterTypes != null ? parameterTypes : new Class[0]);
                        METHOD_CACHE.putIfAbsent(cacheKey, method);
                    }
                }
                context.registerFunction(name, method);
            } catch (NoSuchMethodException e) {
                LOG.warn("注册函数失败: {}.{}, 错误: {}", targetClass.getName(), methodName, e.getMessage());
            } catch (Exception e) {
                LOG.error("注册函数时发生未知异常: {}.{}, 异常类型: {}, 异常信息: {}",
                        targetClass.getName(), methodName, e.getClass().getName(), e.getMessage());
            }
            return this;
        }

        public ContextBuilder setTypeConverter(@NotNull TypeConverter typeConverter) {
            if (typeConverter == null) {
                throw new IllegalArgumentException("类型转换器不能为null");
            }
            context.setTypeConverter(typeConverter);
            return this;
        }

        public ContextBuilder setPropertyAccessors(@NotNull List<PropertyAccessor> propertyAccessors) {
            if (propertyAccessors == null || propertyAccessors.isEmpty()) {
                throw new IllegalArgumentException("属性访问器列表不能为null或空");
            }
            context.setPropertyAccessors(propertyAccessors);
            return this;
        }

        public ContextBuilder setMethodResolvers(@NotNull List<MethodResolver> methodResolvers) {
            if (methodResolvers == null || methodResolvers.isEmpty()) {
                throw new IllegalArgumentException("方法解析器列表不能为null或空");
            }
            context.setMethodResolvers(methodResolvers);
            return this;
        }

        public StandardEvaluationContext build() {
            return context;
        }

        private String generateMethodCacheKey(Class<?> clazz, String methodName, Class<?>[] parameterTypes) {
            return clazz.getName() + "#" + methodName + "#" +
                    (parameterTypes.length > 0 ?
                            Arrays.stream(parameterTypes)
                                    .map(Class::getName)
                                    .collect(Collectors.joining(",")) :
                            "");
        }
    }
}
