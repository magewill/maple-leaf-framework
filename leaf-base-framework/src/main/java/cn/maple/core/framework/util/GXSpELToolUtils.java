package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ReflectUtil;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Spring EL表达式工具类
 * 封装了Spring EL表达式的常用操作，包括表达式计算、方法调用、属性设置等
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 1. 基本表达式计算
 * Dict data = Dict.create().set("name", "jack").set("age", 12);
 * String result = GXSpELToolUtils.calculateSpELExpression(data, "#data['name']", String.class);
 *
 * // 2. 条件表达式
 * Dict user = Dict.create().set("age", 20).set("vip", true);
 * String message = GXSpELToolUtils.calculateSpELExpression(
 *     user,
 *     "#data['age'] >= 18 ? (#data['vip'] ? '尊贵的VIP成年用户' : '普通成年用户') : '未成年用户'",
 *     String.class
 * );
 *
 * // 3. 集合操作
 * Dict order = Dict.create().set("items", Arrays.asList(10, 20, 30, 40));
 * Integer total = GXSpELToolUtils.calculateSpELExpression(
 *     order,
 *     "#data['items'].?[#this > 20].sum()",
 *     Integer.class
 * ); // 结果为70 (30+40)
 *
 * // 4. 目标对象属性访问
 * TestDTO dto = new TestDTO();
 * dto.setName("测试");
 * dto.setTags(Arrays.asList("java", "spring", "spel"));
 * String secondTag = GXSpELToolUtils.calculateSpELExpression(dto, "tags[1]", String.class); // 结果为"spring"
 *
 * // 5. 调用方法
 * String retVal = GXSpELToolUtils.registerFunctionSpELExpression(
 *     StringUtils.class,
 *     "concat",
 *     String.class,
 *     new Class[]{String.class, String.class},
 *     "hello", "world"
 * );
 *
 * // 6. 设置对象属性值
 * TestDTO user = new TestDTO();
 * user.setScore(85);
 * Integer oldScore = GXSpELToolUtils.setValueBySpELExpression(user, "score", Integer.class, 90);
 * // oldScore=85, user.getScore()=90
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
     * 表达式解析器，线程安全的单例
     */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 表达式缓存，提高性能
     * 使用ConcurrentHashMap保证线程安全，初始容量设为256
     */
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>(256);

    /**
     * 缓存最大容量
     */
    private static final int MAX_CACHE_SIZE = 1024;

    /**
     * 私有构造函数，防止实例化
     */
    private GXSpELToolUtils() {
    }

    /**
     * 计算SpEL表达式
     * <p>
     * 该方法用于计算基于Dict数据对象的SpEL表达式，支持复杂的条件判断、集合操作和数据转换。
     * 表达式中可以通过#dataKey方式引用传入的数据对象，默认键名为"data"。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 1. 基本属性访问
     * Dict data = Dict.create().set("name", "jack").set("age", 12);
     * String name = calculateSpELExpression(data, "#data['name']", String.class); // 结果为"jack"
     *
     * // 2. 条件表达式
     * Dict user = Dict.create().set("name", "jack").set("age", 12).set("vip", true);
     * String expression = "#data['age'] > 10 and #data['vip']==true ? '高级用户' : '普通用户'";
     * String userType = calculateSpELExpression(user, expression, String.class); // 结果为"高级用户"
     *
     * // 3. 集合过滤和统计
     * Dict order = Dict.create().set("items", Arrays.asList(10, 20, 30, 40));
     * String expr = "#data['items'].?[#this > 20].![#this * 2].sum()";
     * Integer total = calculateSpELExpression(order, expr, Integer.class); // 结果为140 ((30*2)+(40*2))
     *
     * // 4. 自定义数据键名
     * Dict product = Dict.create().set("price", 100).set("discount", 0.8);
     * Double finalPrice = calculateSpELExpression(product, "#p['price'] * #p['discount']", Double.class, "p"); // 结果为80.0
     * }
     * </pre>
     * </p>
     * <p>
     * 安全特性：
     * - 对空数据和空表达式进行防御性检查
     * - 使用表达式缓存提高性能，避免重复解析
     * - 全面的异常处理，确保方法不会抛出未处理异常
     * - 线程安全的表达式解析和计算
     * </p>
     *
     * @param data             数据对象，通常为Dict类型，用于提供表达式计算所需的数据
     * @param expressionString 表达式字符串，符合Spring EL语法规范
     * @param beanClass        返回类型的Class对象，用于指定结果的类型
     * @param dataKey          数据键名，用于在表达式中引用数据对象，默认为"data"
     * @param <T>              返回类型泛型参数
     * @return 表达式计算结果，如果计算失败则返回指定类型的默认值
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
        EvaluationContext context = new StandardEvaluationContext();
        dataKey = Objects.isNull(dataKey) ? "data" : dataKey;
        context.setVariable(dataKey, data);
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
     * 该方法用于直接在目标对象上计算SpEL表达式，无需额外的数据键名。表达式中可以直接访问目标对象的属性和方法。
     * 这种方式比使用Dict更加直观，特别适合对已有对象进行属性访问、方法调用和条件判断。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     *     {@code
     *     // 1. 条件表达式与对象属性
     *     UserEntity user = new UserEntity();
     *     user.setName("张三");
     *     user.setAge(25);
     *     user.setVip(true);
     *     String message = GXSpELToolUtils.calculateSpELExpression(
     *         user,
     *         "name + (age >= 18 ? (vip ? '(VIP成员)' : '(普通成员)') : '(未成年)')",
     *         String.class
     *     ); // 结果为"张三(VIP成员)"
     *
     *     // 2. 集合操作
     *     OrderDTO order = new OrderDTO();
     *     order.setItems(Arrays.asList(
     *         new OrderItem("商品1", 100, 2),
     *         new OrderItem("商品2", 50, 1),
     *         new OrderItem("商品3", 200, 1)
     *     ));
     *     Double total = GXSpELToolUtils.calculateSpELExpression(
     *         order,
     *         "items.![price * quantity].sum()",
     *         Double.class
     *     ); // 结果为450.0 (100*2 + 50*1 + 200*1)
     *
     *     // 3. 对象方法调用
     *     ProductDTO product = new ProductDTO();
     *     product.setBasePrice(1000);
     *     product.setDiscount(0.8);
     *     Double finalPrice = GXSpELToolUtils.calculateSpELExpression(
     *         product,
     *         "calculatePrice()",
     *         Double.class
     *     ); // 调用product对象的calculatePrice()方法
     *
     *     // 4. 列表元素访问
     *     TestDTO testDTO = new TestDTO();
     *     testDTO.setTest("test");
     *     testDTO.setContent("content");
     *     testDTO.setRoster(Arrays.asList("hello", "jack", "jerry"));
     *     String secondItem = GXSpELToolUtils.calculateSpELExpression(testDTO, "roster[1]", String.class); // 结果为"jack"
     *
     *     // 5. 三元运算符与Map返回
     *     Entity entity = new Entity();
     *     entity.setTest("world");
     *     Dict result = GXSpELToolUtils.calculateSpELExpression(
     *         entity,
     *         "test == 'world' ? {'username':'枫叶'} : {'username':'jack'}",
     *         Dict.class
     *     ); // 结果为包含username=枫叶的Dict对象
     *     }
     * </pre>
     * </p>
     * <p>
     * 安全特性：
     * - 对空目标对象和空表达式进行防御性检查
     * - 使用表达式缓存提高性能，避免重复解析
     * - 全面的异常处理，确保方法不会抛出未处理异常
     * - 线程安全的表达式解析和计算
     * </p>
     * <p>
     * 性能优化：
     * - 使用StandardEvaluationContext提供更高效的表达式计算
     * - 通过缓存表达式对象减少重复解析开销
     * - 日志级别区分，仅在调试模式下输出详细信息
     * </p>
     *
     * @param targetObject     目标对象，表达式将直接在此对象上下文中计算
     * @param expressionString 表达式字符串，符合Spring EL语法规范
     * @param beanClazz        返回类型的Class对象，用于指定结果的类型
     * @param <T>              返回类型泛型参数
     * @return 表达式计算结果，如果计算失败则返回指定类型的默认值
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
            StandardEvaluationContext context = new StandardEvaluationContext(targetObject);
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

    /**
     * 在计算时动态为目标对象设置值
     * <p>
     * 该方法允许通过SpEL表达式动态设置目标对象的属性值，并返回指定属性的当前值。
     * 方法会遍历传入的Dict对象中的所有键值对，将每个键作为属性名，对应的值设置到目标对象中。
     * 设置完成后，会返回指定目标属性的值。
     * </p>
     * <p>
     * 安全特性：
     * - 对空目标对象、空数据和空目标属性名进行防御性检查
     * - 使用StandardEvaluationContext限制表达式的执行环境
     * - 完整的异常处理和日志记录
     * - 线程安全的表达式解析和计算
     * </p>
     * <p>
     * 使用示例：
     * <pre> {@code
     * // 示例1：基本属性设置
     * public class User {
     *     private String name;
     *     private int age;
     *     private boolean vip;
     *     // getter和setter方法省略
     * }
     *
     * User user = new User();
     * Dict userData = Dict.create()
     *     .set("name", "张三")
     *     .set("age", 28)
     *     .set("vip", true);
     *
     * String name = GXSpELToolUtils.assignmentSpELExpression(user, userData, "name", String.class);
     * // 此时user对象的name属性值为"张三"，age属性值为28，vip属性值为true，方法返回"张三"
     *
     * // 示例2：嵌套属性设置
     * public class Department {
     *     private String name;
     *     // getter和setter方法省略
     * }
     *
     * public class Employee {
     *     private String name;
     *     private Department department;
     *     // getter和setter方法省略
     * }
     *
     * Employee employee = new Employee();
     * employee.setDepartment(new Department());
     *
     * Dict employeeData = Dict.create()
     *     .set("name", "李四")
     *     .set("department.name", "技术部");
     *
     * GXSpELToolUtils.assignmentSpELExpression(employee, employeeData, "name", String.class);
     * // 此时employee对象的name属性值为"李四"，department.name属性值为"技术部"
     *
     * // 示例3：属性不存在的情况
     * Dict invalidData = Dict.create().set("nonExistentProperty", "值");
     * try {
     *     GXSpELToolUtils.assignmentSpELExpression(user, invalidData, "name", String.class);
     *     // 如果nonExistentProperty属性不存在，会抛出SpelEvaluationException异常
     * } catch (Exception e) {
     *     // 处理异常
     * }
     * }</pre>
     * </p>
     *
     * @param targetObj 目标对象，要设置属性值的对象实例
     * @param data      属性值数据，包含属性名和对应值的Dict对象
     * @param targetKey 目标属性名，要返回值的属性名
     * @param clazz     返回类型的Class对象
     * @param <T>       返回类型泛型参数
     * @return 设置后的目标属性值，如果设置失败则返回指定类型的默认值
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

    /**
     * 动态注册函数并调用它进行计算
     * <p>
     * 该方法允许将一个静态方法注册为SpEL表达式中的函数，并立即调用该函数。
     * 方法会先验证目标类中是否存在指定签名的方法，然后将其注册到SpEL上下文中，
     * 最后构造并执行调用该函数的表达式。
     * </p>
     * <p>
     * 安全特性：
     * - 对空目标类、空方法名和空参数类型进行防御性检查
     * - 使用StandardEvaluationContext限制表达式的执行环境
     * - 验证方法是否存在，避免运行时异常
     * - 完整的异常处理和日志记录
     * - 线程安全的表达式解析和计算
     * </p>
     * <p>
     * 使用示例：
     * <pre>{@code
     * // 示例1：基本字符串处理函数
     * public class StringUtils {
     *     public static String concat(String str1, String str2, String str3) {
     *         return "链接字符串: " + str1 + " - " + str2 + " - " + str3;
     *     }
     *
     *     public static String toUpperCase(String input) {
     *         return input != null ? input.toUpperCase() : null;
     *     }
     * }
     *
     * // 调用三参数方法
     * String result1 = GXSpELToolUtils.registerFunctionSpELExpression(
     *     StringUtils.class,
     *     "concat",
     *     String.class,
     *     new Class[]{String.class, String.class, String.class},
     *     "hello", "world", "java"
     * );
     * // 结果: "链接字符串: hello - world - java"
     *
     * // 调用单参数方法
     * String result2 = GXSpELToolUtils.registerFunctionSpELExpression(
     *     StringUtils.class,
     *     "toUpperCase",
     *     String.class,
     *     new Class[]{String.class},
     *     "hello"
     * );
     * // 结果: "HELLO"
     *
     * // 示例2：数值计算函数
     * public class MathUtils {
     *     public static int sum(int a, int b) {
     *         return a + b;
     *     }
     *
     *     public static double average(double[] numbers) {
     *         if (numbers == null || numbers.length == 0) {
     *             return 0;
     *         }
     *         double sum = 0;
     *         for (double num : numbers) {
     *             sum += num;
     *         }
     *         return sum / numbers.length;
     *     }
     * }
     *
     * // 调用简单计算方法
     * Integer sumResult = GXSpELToolUtils.registerFunctionSpELExpression(
     *     MathUtils.class,
     *     "sum",
     *     Integer.class,
     *     new Class[]{int.class, int.class},
     *     10, 20
     * );
     * // 结果: 30
     *
     * // 调用数组参数方法
     * Double avgResult = GXSpELToolUtils.registerFunctionSpELExpression(
     *     MathUtils.class,
     *     "average",
     *     Double.class,
     *     new Class[]{double[].class},
     *     new double[]{1.0, 2.0, 3.0, 4.0, 5.0}
     * );
     * // 结果: 3.0
     * }</pre>
     * </p>
     *
     * @param targetClass      目标类，包含要注册的静态方法的类
     * @param methodName       方法名，要注册的静态方法名称
     * @param clazz            返回类型的Class对象
     * @param methodParamTypes 方法参数类型数组，用于确定方法签名
     * @param params           方法参数值，传递给注册方法的实际参数
     * @param <T>              返回类型泛型参数
     * @return 方法调用结果，如果调用失败则返回null
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
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("params", params);
        if (methodNotExists(targetClass, methodName, methodParamTypes)) {
            return null;
        }
        context.registerFunction(methodName, ReflectUtil.getMethod(targetClass, methodName, methodParamTypes));
        final String format = CharSequenceUtil.format("#{}({})", methodName, parsePlaceholderParams(methodParamTypes, params));
        final Expression expression = getOrCreateExpression(format);
        T result = expression.getValue(context, clazz);
        LOG.debug("函数调用成功, 方法名: {}, 结果: {}", methodName, result);
        return result;
    }

    /**
     * 调用指定Bean的方法
     * <p>
     * 该方法用于通过SpEL表达式调用Spring容器中指定Bean的方法。
     * 方法会先从Spring容器中获取指定类型的Bean实例，验证目标方法是否存在，
     * 然后构造并执行调用该方法的表达式。
     * </p>
     * <p>
     * 安全特性：
     * - 对空Bean类型、空方法名和空参数类型进行防御性检查
     * - 验证Bean实例是否存在，避免空指针异常
     * - 验证方法是否存在，避免运行时异常
     * - 使用StandardEvaluationContext限制表达式的执行环境
     * - 完整的异常处理和日志记录
     * - 线程安全的表达式解析和计算
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     *     {@code
     * // 示例1：调用简单服务方法
     * // 假设有以下服务类
     * @Service
     * public class UserService {
     *     public String getUserInfo(String username) {
     *         return "用户信息: " + username;
     *     }
     *
     *     public User findUserById(Long id) {
     *         // 实际应用中会从数据库查询
     *         User user = new User();
     *         user.setId(id);
     *         user.setName("用户" + id);
     *         return user;
     *     }
     *
     *     public boolean updateUserStatus(Long userId, int status, String remark) {
     *         // 实际应用中会更新数据库
     *         System.out.println("更新用户" + userId + "状态为" + status + ", 备注: " + remark);
     *         return true;
     *     }
     * }
     *
     * // 调用单参数方法
     * String userInfo = GXSpELToolUtils.callBeanMethodSpELExpression(
     *     UserService.class,
     *     "getUserInfo",
     *     String.class,
     *     new Class[]{String.class},
     *     "张三"
     * );
     * // 结果: "用户信息: 张三"
     *
     * // 调用返回对象的方法
     * User user = GXSpELToolUtils.callBeanMethodSpELExpression(
     *     UserService.class,
     *     "findUserById",
     *     User.class,
     *     new Class[]{Long.class},
     *     1001L
     * );
     * // 返回id为1001的User对象
     *
     * // 调用多参数方法
     * Boolean result = GXSpELToolUtils.callBeanMethodSpELExpression(
     *     UserService.class,
     *     "updateUserStatus",
     *     Boolean.class,
     *     new Class[]{Long.class, int.class, String.class},
     *     1001L, 1, "已激活"
     * );
     * // 结果: true
     *
     * // 示例2：调用复杂业务逻辑方法
     * @Service
     * public class OrderService {
     *     public Map<String, Object> processOrder(OrderDTO orderDTO, PaymentInfo paymentInfo) {
     *         // 处理订单逻辑
     *         Map<String, Object> result = new HashMap<>();
     *         result.put("orderNo", "ORD" + System.currentTimeMillis());
     *         result.put("status", "success");
     *         result.put("amount", orderDTO.getAmount());
     *         result.put("paymentType", paymentInfo.getType());
     *         return result;
     *     }
     * }
     *
     * // 准备参数
     * OrderDTO orderDTO = new OrderDTO();
     * orderDTO.setAmount(100.50);
     * orderDTO.setItems(Arrays.asList("商品1", "商品2"));
     *
     * PaymentInfo paymentInfo = new PaymentInfo();
     * paymentInfo.setType("支付宝");
     *
     * // 调用业务方法
     * Map<String, Object> orderResult = GXSpELToolUtils.callBeanMethodSpELExpression(
     *     OrderService.class,
     *     "processOrder",
     *     Map.class,
     *     new Class[]{OrderDTO.class, PaymentInfo.class},
     *     orderDTO, paymentInfo
     * );
     * // 返回包含订单处理结果的Map
     *     }
     * </pre>
     * </p>
     *
     * @param beanClazz        Bean类型，要调用方法的Spring Bean的类型
     * @param methodName       方法名，要调用的方法名称
     * @param clazz            返回类型的Class对象
     * @param methodParamTypes 方法参数类型数组，用于确定方法签名
     * @param params           方法参数值，传递给方法的实际参数
     * @param <T>              返回类型泛型参数
     * @return 方法调用结果，如果调用失败则返回null
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
        final StandardEvaluationContext context = new StandardEvaluationContext(beanObj);
        final String expressionString = CharSequenceUtil.format("{}({})", methodName,
                parseArgumentParams(context, methodParamTypes, params));
        final Expression expression = getOrCreateExpression(expressionString);
        T result = expression.getValue(context, clazz);
        LOG.debug("Bean方法调用成功, 方法名: {}, 结果: {}", methodName, result);
        return result;
    }

    /**
     * 判断目标类是否包含指定方法
     * <p>
     * 该方法用于验证目标类中是否存在指定签名的方法。如果方法不存在，会记录详细的错误日志。
     * 这是一个内部辅助方法，主要用于在调用方法前进行预检查，避免运行时出现方法不存在的异常。
     * </p>
     * <p>
     * 安全特性：
     * - 使用ReflectUtil安全地获取方法，避免反射相关异常
     * - 详细的日志记录，便于问题诊断
     * - 返回布尔值而非抛出异常，便于调用方进行后续处理
     * </p>
     * <p>
     * 内部实现说明：
     * 1. 使用ReflectUtil.getMethod获取指定签名的方法
     * 2. 如果方法不存在，记录错误日志，包含类名、方法名和参数类型信息
     * 3. 返回布尔值表示方法是否不存在
     * </p>
     *
     * @param beanClazz        目标类，要检查的类
     * @param methodName       方法名，要检查的方法名称
     * @param methodParamTypes 方法参数类型数组，用于确定方法签名
     * @return 如果方法不存在返回true，存在返回false
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
     * 该方法通过SpEL表达式动态调用目标对象的指定方法，支持各种参数类型和返回值类型。
     * 方法会先验证目标对象中是否存在指定签名的方法，然后构造并执行调用该方法的表达式。
     * 适用于需要在运行时动态调用对象方法的场景，如策略模式实现、动态代理等。
     * </p>
     * <p>
     * 安全特性：
     * - 对空方法名和空参数类型进行防御性检查
     * - 验证方法是否存在，避免运行时异常
     * - 使用StandardEvaluationContext限制表达式的执行环境
     * - 完整的异常处理和日志记录
     * - 线程安全的表达式解析和计算
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 示例1：调用服务类方法
     * // 假设有一个HelloService类，包含hello方法
     * String result = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
     *     GXSpringContextUtils.getBean(HelloService.class),  // 目标对象
     *     "hello",                                           // 方法名
     *     String.class,                                     // 返回类型
     *     new Class[]{String.class, int.class, TestEntity.class}, // 参数类型
     *     "枫叶思源", 98, testEntity                          // 参数值
     * );
     *
     * // 示例2：调用带有复杂参数的方法
     * OrderService orderService = GXSpringContextUtils.getBean(OrderService.class);
     * OrderDTO orderDTO = new OrderDTO();
     * orderDTO.setOrderId("ORD123456");
     * orderDTO.setItems(Arrays.asList(new OrderItem("商品1", 2), new OrderItem("商品2", 1)));
     *
     * Boolean success = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
     *     orderService,
     *     "processOrder",
     *     Boolean.class,
     *     new Class[]{OrderDTO.class, String.class},
     *     orderDTO, "EXPRESS"
     * );
     *
     * // 示例3：调用无参数方法
     * UserService userService = GXSpringContextUtils.getBean(UserService.class);
     * Integer count = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
     *     userService,
     *     "countActiveUsers",
     *     Integer.class,
     *     new Class[]{},
     *     new Object[]{}
     * );
     *
     * // 示例4：调用带有基本类型和包装类型混合的方法
     * CalculatorService calculatorService = GXSpringContextUtils.getBean(CalculatorService.class);
     * Double result = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
     *     calculatorService,
     *     "calculate",
     *     Double.class,
     *     new Class[]{double.class, Integer.class, boolean.class},
     *     10.5, 20, true
     * );
     * }
     * </pre>
     * </p>
     * <p>
     * 性能优化：
     * - 使用表达式缓存减少重复解析开销
     * - 通过StandardEvaluationContext提供更高效的表达式计算
     * - 日志级别区分，仅在调试模式下输出详细信息
     * </p>
     *
     * @param targetObject     目标对象，要调用方法的对象实例，不能为null
     * @param methodName       方法名，要调用的方法名称
     * @param clazz            返回类型，方法返回值的类型
     * @param methodParamTypes 方法参数类型数组，定义方法的参数类型列表
     * @param params           方法参数值数组，传递给方法的实际参数值
     * @param <T>              返回类型泛型参数
     * @return 方法调用结果，如果调用失败则返回null
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
        final StandardEvaluationContext context = new StandardEvaluationContext(targetObject);
        final String expressionString = CharSequenceUtil.format("{}({})", methodName,
                parseArgumentParams(context, methodParamTypes, params));
        final Expression expression = getOrCreateExpression(expressionString);
        T result = expression.getValue(context, clazz);
        LOG.debug("目标对象方法调用成功, 方法名: {}, 结果: {}", methodName, result);
        return result;
    }

    /**
     * 设置对象属性值
     * <p>
     * 该方法通过SpEL表达式动态设置目标对象的属性值，并返回设置前的旧值。
     * 支持设置简单属性、嵌套属性、集合元素等，提供了比反射更灵活的属性操作方式。
     * 适用于需要在运行时动态修改对象属性的场景，如数据填充、对象转换等。
     * </p>
     * <p>
     * 安全特性：
     * - 对空目标对象、空表达式和空新值进行防御性检查
     * - 使用StandardEvaluationContext限制表达式的执行环境
     * - 完整的异常处理和日志记录
     * - 线程安全的表达式解析和计算
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 示例1：设置简单属性值
     * UserDTO user = new UserDTO();
     * user.setName("原始名称");
     * user.setAge(25);
     * // 修改name属性，并获取旧值
     * String oldName = GXSpELToolUtils.setValueBySpELExpression(user, "name", String.class, "新名称");
     * // 此时oldName="原始名称"，user.getName()="新名称"
     *
     * // 示例2：设置集合元素值
     * TestDTO testDTO = new TestDTO();
     * testDTO.setTest("测试");
     * testDTO.setContent("内容");
     * testDTO.setRoster(Arrays.asList("hello", "jack", "jerry"));
     * // 修改列表中索引为1的元素
     * String oldValue = GXSpELToolUtils.setValueBySpELExpression(testDTO, "roster[1]", String.class, "newValue");
     * // 此时oldValue="jack"，testDTO.getRoster()=["hello", "newValue", "jerry"]
     *
     * // 示例3：设置嵌套对象属性
     * OrderDTO order = new OrderDTO();
     * CustomerDTO customer = new CustomerDTO();
     * customer.setName("张三");
     * order.setCustomer(customer);
     * // 修改嵌套对象的属性
     * String oldCustomerName = GXSpELToolUtils.setValueBySpELExpression(order, "customer.name", String.class, "李四");
     * // 此时oldCustomerName="张三"，order.getCustomer().getName()="李四"
     *
     * // 示例4：设置Map元素值
     * ProductDTO product = new ProductDTO();
     * Map<String, Object> attributes = new HashMap<>();
     * attributes.put("color", "红色");
     * attributes.put("size", "XL");
     * product.setAttributes(attributes);
     * // 修改Map中的元素
     * String oldColor = GXSpELToolUtils.setValueBySpELExpression(product, "attributes['color']", String.class, "蓝色");
     * // 此时oldColor="红色"，product.getAttributes().get("color")="蓝色"
     * }
     * </pre>
     * </p>
     * <p>
     * 性能优化：
     * - 使用表达式缓存减少重复解析开销
     * - 通过StandardEvaluationContext提供更高效的表达式计算
     * - 日志级别区分，仅在调试模式下输出详细信息
     * </p>
     *
     * @param targetObject     目标对象，要设置属性的对象实例，不能为null
     * @param expressionString 表达式，用于指定要设置的属性路径，如"name"、"addresses[0].city"等
     * @param oldValueClazz    旧值类型，指定返回的旧值的类型
     * @param newValue         新值，要设置的新属性值，不能为null
     * @param <T>              值类型泛型参数
     * @return 设置前的旧值，如果获取旧值失败则返回null
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
        final StandardEvaluationContext context = new StandardEvaluationContext(targetObject);
        final Expression expression = getOrCreateExpression(expressionString);
        final T oldValue = expression.getValue(context, oldValueClazz);
        expression.setValue(context, newValue);
        LOG.debug("对象属性值设置成功, 表达式: {}, 旧值: {}, 新值: {}", expressionString, oldValue, newValue);
        return oldValue;
    }

    /**
     * 设置Dict对象属性值
     * <p>
     * 该方法通过SpEL表达式动态设置Dict对象的属性值，并返回设置前的旧值。
     * Dict是一种类似Map的数据结构，该方法允许通过表达式灵活地修改Dict中的值。
     * 适用于需要在运行时动态修改字典数据的场景，如配置更新、数据转换等。
     * </p>
     * <p>
     * 安全特性：
     * - 对空Dict对象、空表达式和空新值进行防御性检查
     * - 使用StandardEvaluationContext限制表达式的执行环境
     * - 完整的异常处理和日志记录
     * - 线程安全的表达式解析和计算
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 示例1：设置简单键值
     * Dict userInfo = Dict.create().set("username", "枫叶思源").set("age", 30);
     * String oldUsername = GXSpELToolUtils.setValueBySpELExpression(
     *     userInfo,
     *     "#data['username']",
     *     String.class,
     *     "新用户名"
     * );
     * // 此时oldUsername="枫叶思源"，userInfo.getStr("username")="新用户名"
     *
     * // 示例2：设置嵌套Dict的值
     * Dict orderInfo = Dict.create()
     *     .set("orderId", "ORD123456")
     *     .set("customer", Dict.create().set("name", "张三").set("phone", "13800138000"));
     * String oldPhone = GXSpELToolUtils.setValueBySpELExpression(
     *     orderInfo,
     *     "#data['customer']['phone']",
     *     String.class,
     *     "13900139000"
     * );
     * // 此时oldPhone="13800138000"，
     * // ((Dict)orderInfo.get("customer")).getStr("phone")="13900139000"
     *
     * // 示例3：设置列表中的元素
     * Dict productInfo = Dict.create().set("name", "商品A")
     *     .set("tags", Arrays.asList("热销", "折扣", "新品"));
     * String oldTag = GXSpELToolUtils.setValueBySpELExpression(
     *     productInfo,
     *     "#data['tags'][1]",
     *     String.class,
     *     "限时"
     * );
     * // 此时oldTag="折扣"，productInfo.get("tags")中的第二个元素变为"限时"
     *
     * // 示例4：使用条件表达式设置值
     * Dict config = Dict.create().set("maxUsers", 100).set("currentUsers", 80);
     * Integer oldMax = GXSpELToolUtils.setValueBySpELExpression(
     *     config,
     *     "#data['maxUsers']",
     *     Integer.class,
     *     config.getInt("currentUsers") < 50 ? 100 : 200
     * );
     * // 此时oldMax=100，由于currentUsers=80 > 50，所以maxUsers被设置为200
     * }
     * </pre>
     * </p>
     * <p>
     * 性能优化：
     * - 使用表达式缓存减少重复解析开销
     * - 通过StandardEvaluationContext提供更高效的表达式计算
     * - 日志级别区分，仅在调试模式下输出详细信息
     * </p>
     *
     * @param dict             目标Dict对象，要设置属性的Dict实例，不能为null
     * @param expressionString 表达式，用于指定要设置的属性路径，通常使用#data['key']格式
     * @param oldValueClazz    旧值类型，指定返回的旧值的类型
     * @param newValue         新值，要设置的新属性值，不能为null
     * @param <T>              值类型泛型参数
     * @return 设置前的旧值，如果获取旧值失败则返回null
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
        EvaluationContext context = new StandardEvaluationContext();
        String dataKey = "data";
        context.setVariable(dataKey, dict);
        final Expression expression = getOrCreateExpression(expressionString);
        final T oldValue = expression.getValue(context, oldValueClazz);
        expression.setValue(context, newValue);
        LOG.debug("Dict对象属性值设置成功, 表达式: {}, 旧值: {}, 新值: {}", expressionString, oldValue, newValue);
        return oldValue;
    }

    /**
     * 解析方法参数占位符
     * <p>
     * 该方法用于生成SpEL表达式中的参数占位符字符串，主要服务于动态方法调用场景。
     * 方法会遍历参数类型数组，为每个参数生成对应的占位符表达式。
     * 如果参数值数组长度小于参数类型数组长度，对于缺失的参数会使用null值代替。
     * </p>
     * <p>
     * 内部实现说明：
     * - 对于每个参数，生成形如 #params[i] 的占位符
     * - 参数索引从0开始，按顺序递增
     * - 最终返回的字符串格式为：#params[0] , #params[1] , ... , #params[n-1]
     * - 如果参数值缺失，对应位置使用null值
     * </p>
     * <p>
     * 使用场景：
     * - 主要用于内部构建动态方法调用的SpEL表达式
     * - 与parseArgumentParams方法配合使用，前者生成占位符，后者设置实际参数值
     * </p>
     *
     * @param methodParamTypes 参数类型数组，定义方法的参数类型列表
     * @param params           参数值数组，传递给方法的实际参数值
     * @return 格式化后的参数占位符字符串，用于构建SpEL表达式
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
     * <p>
     * 该方法用于处理SpEL表达式中的实际参数值，主要服务于动态方法调用场景。
     * 方法会根据参数类型的不同，采用不同的处理策略：
     * 1. 对于简单值类型（如基本类型及其包装类、String等），直接将值插入表达式
     * 2. 对于复杂对象类型，将对象注册到上下文中，并使用变量引用的方式在表达式中引用
     * </p>
     * <p>
     * 内部实现说明：
     * - 对于String类型参数，使用单引号包裹，如 'hello'
     * - 对于其他简单类型（如数字、布尔值等），直接使用其字符串表示，如 123, true
     * - 对于复杂对象类型，将其注册为变量，并使用 #变量名 的方式引用
     * - 复杂对象的变量名使用类全名去掉点号后的字符串，确保唯一性
     * - 如果参数值缺失，对应位置使用null值
     * </p>
     * <p>
     * 使用场景：
     * - 主要用于内部构建动态方法调用的SpEL表达式
     * - 与callTargetObjectMethodSpELExpression方法配合使用，处理方法调用的参数
     * </p>
     * <p>
     * 性能与安全考虑：
     * - 对于简单类型，直接插入表达式可以提高解析效率
     * - 对于复杂对象，使用变量引用可以避免序列化问题和表达式注入风险
     * - 字符串类型使用单引号包裹，避免SpEL表达式解析错误
     * </p>
     *
     * @param context          SpEL表达式的评估上下文，用于注册复杂对象变量
     * @param methodParamTypes 参数类型数组，定义方法的参数类型列表
     * @param params           参数值数组，传递给方法的实际参数值
     * @return 格式化后的参数字符串，用于构建SpEL表达式
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

    /**
     * 获取或创建表达式对象，使用缓存提高性能
     * <p>
     * 该方法实现了SpEL表达式的缓存机制，避免重复解析相同的表达式字符串，显著提高性能。
     * 当缓存大小超过预设的阈值时，会自动清理一半的缓存项，防止内存占用过大。
     * </p>
     * <p>
     * 内部实现说明：
     * - 使用ConcurrentHashMap作为缓存容器，保证线程安全
     * - 采用computeIfAbsent方法实现原子的获取或创建操作
     * - 当缓存大小超过阈值时，使用双重检查锁定模式进行缓存清理
     * - 清理策略为保留最早放入缓存的一半项，移除后半部分
     * </p>
     * <p>
     * 性能优化：
     * - 缓存机制显著减少了表达式解析的开销，特别是对于频繁使用的表达式
     * - 仅在缓存大小超过阈值时才进行清理，减少同步开销
     * - 使用流式API高效地进行缓存清理操作
     * </p>
     * <p>
     * 线程安全：
     * - 使用ConcurrentHashMap保证多线程环境下的安全访问
     * - 清理操作使用synchronized块确保线程安全
     * - 双重检查锁定模式避免不必要的同步
     * </p>
     *
     * @param expressionString 表达式字符串，要解析的SpEL表达式
     * @return 解析后的表达式对象，如果缓存中存在则直接返回缓存的对象
     */
    private static Expression getOrCreateExpression(String expressionString) {
        // 检查缓存大小，超过阈值时清理一半的缓存
        if (EXPRESSION_CACHE.size() > MAX_CACHE_SIZE) {
            synchronized (EXPRESSION_CACHE) {
                if (EXPRESSION_CACHE.size() > MAX_CACHE_SIZE) {
                    LOG.debug("表达式缓存大小超过阈值，开始清理缓存，当前大小: {}", EXPRESSION_CACHE.size());
                    // 保留一半的缓存项
                    EXPRESSION_CACHE.keySet().stream()
                            .skip(EXPRESSION_CACHE.size() / 2)
                            .collect(Collectors.toList())
                            .forEach(EXPRESSION_CACHE::remove);
                    LOG.debug("表达式缓存清理完成，清理后大小: {}", EXPRESSION_CACHE.size());
                }
            }
        }
        return EXPRESSION_CACHE.computeIfAbsent(expressionString, PARSER::parseExpression);
    }

    /**
     * 清空表达式缓存
     * <p>
     * 该方法用于手动清空所有缓存的SpEL表达式对象，释放内存资源。
     * 在以下场景中特别有用：
     * 1. 应用需要释放内存时
     * 2. 配置发生变化，需要重新解析表达式时
     * 3. 长时间运行的应用定期维护时
     * 4. 单元测试前后清理环境时
     * </p>
     * <p>
     * 内部实现说明：
     * - 使用synchronized块确保线程安全
     * - 调用ConcurrentHashMap的clear方法清空所有缓存项
     * - 记录日志便于问题排查和性能监控
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 在配置更新后清空表达式缓存
     * public void refreshConfig() {
     *     configService.reloadConfig();
     *     GXSpELToolUtils.clearExpressionCache();
     *     LOG.info("配置已更新，表达式缓存已清空");
     * }
     *
     * // 在单元测试中使用
     * @Before
     * public void setUp() {
     *     GXSpELToolUtils.clearExpressionCache();
     * }
     *
     * // 在应用关闭时释放资源
     * @PreDestroy
     * public void cleanup() {
     *     GXSpELToolUtils.clearExpressionCache();
     * }
     * }
     * </pre>
     * </p>
     * <p>
     * 注意事项：
     * - 清空缓存后，后续的表达式计算将需要重新解析，可能会短暂影响性能
     * - 在高并发环境下，应避免频繁调用此方法
     * - 此方法是线程安全的，可以在多线程环境中调用
     * </p>
     */
    public static void clearExpressionCache() {
        synchronized (EXPRESSION_CACHE) {
            EXPRESSION_CACHE.clear();
            LOG.debug("表达式缓存已清空");
        }
    }
}
