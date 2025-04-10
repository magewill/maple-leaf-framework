package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Spring EL表达式工具类
 * 封装了Spring EL表达式的常用操作，包括表达式计算、方法调用、属性设置等
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 计算表达式
 * Dict data = Dict.create().set("name", "jack").set("age", 12);
 * String result = GXSpELToolUtils.calculateSpELExpression(data, "#data['name']", String.class);
 * 
 * // 调用方法
 * String retVal = GXSpELToolUtils.registerFunctionSpELExpression(
 *     StringUtils.class,
 *     "concat",
 *     String.class,
 *     new Class[]{String.class, String.class},
 *     "hello", "world"
 * );
 * }
 * </pre>
 * </p>
 *
 * @author britton@126.com
 */
public class GXSpELToolUtils {
    /**
     * 日志对象
     */
    private static final Logger LOG = LoggerFactory.getLogger(GXSpELToolUtils.class);

    /**
     * 目标类中方法不存在的提示信息模板
     */
    private static final String METHOD_NOT_FOUND_TIPS_TEMPLATE = "目标类{}中没有满足签名为{}({})的方法存在~~~";

    /**
     * 表达式为空的提示信息
     */
    private static final String EXPRESSION_EMPTY_TIPS = "表达式不能为空";

    /**
     * 数据对象为空的提示信息
     */
    private static final String DATA_EMPTY_TIPS = "数据对象不能为空";

    /**
     * 私有构造函数，防止实例化
     */
    private GXSpELToolUtils() {
    }

    /**
     * 计算SpEL表达式
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * Dict data = Dict.create().set("name", "jack").set("age", 12);
     * String expression = "#data['name']=='jack' and #data['flags']==true";
     * calculateSpELExpression(data, expression, String.class, "data");
     * }
     * </pre>
     * </p>
     *
     * @param data             数据对象
     * @param expressionString 表达式字符串
     * @param beanClass        返回类型
     * @param dataKey          数据键名
     * @param <T>              返回类型
     * @return 表达式计算结果
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
        LOG.debug("开始计算SpEL表达式: {}, 数据键名: {}", expressionString, dataKey);
        ExpressionParser parser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext();
        dataKey = Objects.isNull(dataKey) ? "data" : dataKey;
        context.setVariable(dataKey, data);
        try {
            final Expression expression = parser.parseExpression(expressionString);
            T result = expression.getValue(context, beanClass);
            LOG.debug("SpEL表达式计算成功: {}, 结果: {}", expressionString, result);
            return result;
        } catch (SpelEvaluationException e) {
            LOG.error("SpEL表达式计算失败, 表达式: {}, 异常信息: {}", expressionString, e.getMessage());
        }
        return GXCommonUtils.getClassDefaultValue(beanClass);
    }

    /**
     * 计算SpEL的表达式
     * <p>
     * 使用示例：
     * <pre>
     *  {@code
     *  Dict data = Dict.create().set("name","jack").set("age",12);
     *  String expression = "#data['name']=='jack' and #data['flags']==true";
     *  calculateSpELExpression(data ,expression ,String.class, "data");
     * } </pre>
     *
     * @param data             数据对象
     * @param expressionString 表达式字符串
     * @param beanClass        返回类型
     * @param <T>              返回类型
     * @return 表达式计算结果
     */
    public static <T> T calculateSpELExpression(Dict data, String expressionString, Class<T> beanClass) {
        return calculateSpELExpression(data, expressionString, beanClass, "data");
    }

    /**
     * 计算目标对象的SpEL表达式
     * <p>
     * 使用示例：
     * <pre>
     *     {@code
     *     eg1:
     *      Dict data = GXSpELToolUtils.calculateSpELExpression(entity , "test == 'world' ? {'username':'枫叶'} :{'kk' : 'jack'}" , Dict.class);
     *      String data = GXSpELToolUtils.calculateSpELExpression(entity , "test == 'world' ? '枫叶' :{'jack'" , Dict.class);
     *     eg2:
     *      final TestDTO testDTO = new TestDTO();
     *      testDTO.setTest("test");
     *      testDTO.setContent("content");
     *      testDTO.setRoster(Arrays.asList("hello", "jack", "jerry"));
     *      GXSpELToolUtils.calculateSpELExpression(testDTO, "roster[1]", String.class);
     *     }
     * </pre>
     * </p>
     *
     * @param targetObject     目标对象
     * @param expressionString 表达式字符串
     * @param beanClazz        返回类型
     * @param <T>              返回类型
     * @return 表达式计算结果
     */
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
            ExpressionParser parser = new SpelExpressionParser();
            StandardEvaluationContext context = new StandardEvaluationContext(targetObject);
            T result = parser.parseExpression(expressionString).getValue(context, beanClazz);
            LOG.debug("目标对象SpEL表达式计算成功: {}, 结果: {}", expressionString, result);
            return result;
        } catch (SpelEvaluationException e) {
            LOG.error("目标对象SpEL表达式计算失败, 表达式: {}, 异常信息: {}", expressionString, e.getMessage());
        }
        return GXCommonUtils.getClassDefaultValue(beanClazz);
    }

    /**
     * 在计算时动态为目标对象设置值
     *
     * <pre> {@code
     *  public class Inventor{
     *      private String name;
     *      private int age;
     *      private int roleId;
     *  }
     *  Inventor targetObj = new Inventor();
     *  String value = assignmentSpELExpression(targetObj , Dict.create().set("name" , "枫叶思源").set("age" , 12) , "name" , String.class);
     *  // 如果目标对象没有对应的字段存在
     *  // 则会抛出异常信息,eg:
     *  // String value = assignmentSpELExpression(targetObj , Dict.create().set("name111" , "枫叶思源").set("age" , 12) , "name" , String.class);
     * }</pre>
     *
     * @param targetObj 目标对象
     * @param data      属性值数据
     * @param targetKey 目标属性名
     * @param clazz     返回类型
     * @param <T>       返回类型
     * @return 设置后的属性值
     */
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
        final StandardEvaluationContext inventorContext = new StandardEvaluationContext(targetObj);
        final ExpressionParser parser = new SpelExpressionParser();
        if (data.isEmpty()) {
            return GXCommonUtils.getClassDefaultValue(clazz);
        }
        data.forEach((key, value) -> parser.parseExpression(key).setValue(inventorContext, value));
        T result = parser.parseExpression(targetKey).getValue(inventorContext, clazz);
        LOG.debug("目标对象属性值设置成功, 目标属性: {}, 结果: {}", targetKey, result);
        return result;
    }

    /**
     * 动态注册函数并调用它进行计算
     *
     * <pre>{@code
     *   public class StringUtils {
     *       public static String concat(String str1, String str2, String str3) {
     *         return "链接字符串 : " + str1 + " ---- " + str2 + " ==== " + str3;
     *     }
     *   }
     *   String retVal = registerFunctionSpELExpression(StringUtils.class, String.class, "concat", new Class[]{String.class, String.class, String.class}, "hello", "britton", "枫叶思源");
     *   }</pre>
     *
     * @param targetClass      目标类
     * @param methodName       方法名
     * @param clazz            返回类型
     * @param methodParamTypes 方法参数类型
     * @param params           方法参数
     * @param <T>              返回类型
     * @return 方法调用结果
     */
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
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("params", params);
        if (methodNotExists(targetClass, methodName, methodParamTypes)) {
            return null;
        }
        context.registerFunction(methodName, ReflectUtil.getMethod(targetClass, methodName, methodParamTypes));
        final String format = CharSequenceUtil.format("#{}({})", methodName, parsePlaceholderParams(methodParamTypes, params));
        T result = parser.parseExpression(format).getValue(context, clazz);
        LOG.debug("函数调用成功, 方法名: {}, 结果: {}", methodName, result);
        return result;
    }

    /**
     * 调用指定Bean的方法
     * <p>
     * 使用示例：
     * <pre>
     *     {@code
     *     String s = GXSpELToolUtils.callBeanMethodSpELExpression(
     *     HelloService.class,
     *     "hello",
     *     String.class,
     *     new Class[]{String.class, int.class, TestEntity.class},
     *     "枫叶思源", 98 , testEntity);
     *     }
     * </pre>
     * </p>
     *
     * @param beanClazz        Bean类型
     * @param methodName       方法名
     * @param clazz            返回类型
     * @param methodParamTypes 方法参数类型
     * @param params           方法参数
     * @param <T>              返回类型
     * @return 方法调用结果
     */
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
        final ExpressionParser expressionParser = new SpelExpressionParser();
        final StandardEvaluationContext context = new StandardEvaluationContext(beanObj);
        final String expressionString = CharSequenceUtil.format("{}({})", methodName,
                parseArgumentParams(context, methodParamTypes, params));
        T result = expressionParser.parseExpression(expressionString).getValue(context, clazz);
        LOG.debug("Bean方法调用成功, 方法名: {}, 结果: {}", methodName, result);
        return result;
    }

    /**
     * 判断目标类是否包含指定方法
     *
     * @param beanClazz        目标类
     * @param methodName       方法名
     * @param methodParamTypes 方法参数类型
     * @return 是否包含方法
     */
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

    /**
     * 调用指定对象的方法
     * <p>
     * 使用示例：
     * <pre>
     *  {@code
     *    String s = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
     *    GXSpringContextUtils.getBean(HelloService.class),
     *    "hello",
     *    String.class,
     *    new Class[]{String.class, int.class,TestEntity.class},
     *    "枫叶思源", 98 , testEntity);
     *  }
     * </pre>
     * </p>
     *
     * @param targetObject     目标对象
     * @param methodName       方法名
     * @param clazz            返回类型
     * @param methodParamTypes 方法参数类型
     * @param params           方法参数
     * @param <T>              返回类型
     * @return 方法调用结果
     */
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
        final Method method = ReflectUtil.getMethod(targetObject.getClass(), methodName, methodParamTypes);
        if (Objects.isNull(method)) {
            final String paramStr = Arrays.stream(methodParamTypes)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(","));
            LOG.debug(METHOD_NOT_FOUND_TIPS_TEMPLATE, targetObject.getClass().getSimpleName(), methodName, paramStr);
            return null;
        }
        final ExpressionParser expressionParser = new SpelExpressionParser();
        final StandardEvaluationContext context = new StandardEvaluationContext(targetObject);
        final String expressionString = CharSequenceUtil.format("{}({})", methodName,
                parseArgumentParams(context, methodParamTypes, params));
        T result = expressionParser.parseExpression(expressionString).getValue(context, clazz);
        LOG.debug("目标对象方法调用成功, 方法名: {}, 结果: {}", methodName, result);
        return result;
    }

    /**
     * 设置对象属性值
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     *  final TestDTO testDTO = new TestDTO();
     *  testDTO.setTest("ceshi");
     *  testDTO.setContent("content");
     *  testDTO.setRoster(Arrays.asList("hello", "jack", "jerry"));
     *  String oldValue = GXSpELToolUtils.setValueBySpELExpression(testDTO, "roster[1]", String.class, "newValue");
     * }
     * </pre>
     * </p>
     *
     * @param targetObject     目标对象
     * @param expressionString 表达式
     * @param oldValueClazz    旧值类型
     * @param newValue         新值
     * @param <T>              值类型
     * @return 旧值
     */
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
        final ExpressionParser expressionParser = new SpelExpressionParser();
        final StandardEvaluationContext context = new StandardEvaluationContext(targetObject);
        final T oldValue = expressionParser.parseExpression(expressionString).getValue(context, oldValueClazz);
        expressionParser.parseExpression(expressionString).setValue(context, newValue);
        LOG.debug("对象属性值设置成功, 表达式: {}, 旧值: {}, 新值: {}", expressionString, oldValue, newValue);
        return oldValue;
    }

    /**
     * 设置Dict对象属性值
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * Dict dict = Dict.create().set("username", "枫叶思源");
     * String oldValue = GXSpELToolUtils.setValueBySpELExpression(
     *     dict,
     *     "#data['username']",
     *     String.class,
     *     "newValue"
     * );
     * }
     * </pre>
     * </p>
     *
     * @param dict             目标Dict对象
     * @param expressionString 表达式
     * @param oldValueClazz    旧值类型
     * @param newValue         新值
     * @param <T>              值类型
     * @return 旧值
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
            LOG.warn("新值不能为空");
            return null;
        }
        LOG.debug("开始设置Dict对象属性值, 表达式: {}", expressionString);
        ExpressionParser expressionParser = new SpelExpressionParser();
        EvaluationContext context = new StandardEvaluationContext();
        String dataKey = "data";
        context.setVariable(dataKey, dict);
        final T oldValue = expressionParser.parseExpression(expressionString).getValue(context, oldValueClazz);
        expressionParser.parseExpression(expressionString).setValue(context, newValue);
        LOG.debug("Dict对象属性值设置成功, 表达式: {}, 旧值: {}, 新值: {}", expressionString, oldValue, newValue);
        return oldValue;
    }

    /**
     * 解析方法参数占位符
     *
     * @param methodParamTypes 参数类型
     * @param params           参数值
     * @return 参数占位符字符串
     */
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

    /**
     * 解析方法实际参数
     *
     * @param context          上下文
     * @param methodParamTypes 参数类型
     * @param params           参数值
     * @return 参数字符串
     */
    private static String parseArgumentParams(EvaluationContext context, Class<?>[] methodParamTypes, Object... params) {
        StringBuilder methodParam = new StringBuilder();
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
    }
}
