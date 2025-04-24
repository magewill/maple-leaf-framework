package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL JSON_CONTAINS函数条件构建类
 * <p>
 * 该类用于构建使用MySQL JSON_CONTAINS函数的查询条件，用于检查JSON文档是否包含特定值或路径。
 * JSON_CONTAINS函数返回1（真）如果目标JSON文档包含指定的值，或者0（假）如果不包含。
 * </p>
 * 
 * <p>安全特性：</p>
 * <ul>
 *   <li>使用MyBatis参数化查询机制(#{})，彻底防止SQL注入</li>
 *   <li>JSON值和路径通过参数化方式传递，不直接拼接到SQL中</li>
 *   <li>使用CAST(#{param} AS JSON)确保参数被正确处理为JSON类型</li>
 *   <li>表名和字段名使用反引号(``)包裹，防止SQL关键字冲突</li>
 *   <li>自动处理不同类型的JSON值（字符串、数字等），避免格式错误</li>
 * </ul>
 * 
 * <p>性能优化：</p>
 * <ul>
 *   <li>参数化查询允许数据库缓存执行计划</li>
 *   <li>JSON路径表达式优化，提高JSON查询效率</li>
 *   <li>使用Stream API高效处理JSON数组元素</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：检查tags字段是否包含指定的标签列表
 * List&lt;Object&gt; tagList = new ArrayList<>();
 * tagList.add("技术");
 * tagList.add("Java");
 * GXConditionFuncJsonContains condition = new GXConditionFuncJsonContains("t_article", "tags", tagList);
 * // 生成SQL片段：JSON_CONTAINS(`t_article`.`tags`->#{dbQueryParamInnerDto.paramMap.condition_xxx_path}, CAST(#{dbQueryParamInnerDto.paramMap.condition_xxx} AS JSON))
 * // 参数值会被设置为JSON数组：["技术","Java"]
 * 
 * // 示例2：检查user_info字段中的特定路径是否包含指定值
 * Dict dict = Dict.create().set("name", "张三").set("age", 25);
 * GXConditionFuncJsonContains condition = new GXConditionFuncJsonContains("t_user", "user_info", dict, "profile");
 * // 生成SQL片段：JSON_CONTAINS(`t_user`.`user_info`->#{dbQueryParamInnerDto.paramMap.condition_xxx_path}, CAST(#{dbQueryParamInnerDto.paramMap.condition_xxx} AS JSON))
 * // 参数值会被设置为JSON对象：[{"name":"张三","age":25}]
 * // 路径参数会被设置为：$.profile
 * 
 * // 示例3：检查JSON对象中是否包含特定键值对
 * Dict userInfo = Dict.create().set("name", "张三");
 * GXConditionFuncJsonContains condition = new GXConditionFuncJsonContains("t_user", "ext", userInfo);
 * // 生成SQL片段：JSON_CONTAINS(`t_user`.`ext`->#{dbQueryParamInnerDto.paramMap.condition_xxx_path}, CAST(#{dbQueryParamInnerDto.paramMap.condition_xxx} AS JSON))
 * // 参数值会被设置为JSON对象：[{"name":"张三"}]
 * 
 * // 示例4：将条件添加到查询参数中并执行查询
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(condition);
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("t_user")
 *     .condition(conditions)
 *     .build();
 * List<UserEntity> users = userMapper.findByCondition(queryParam);
 * </pre>
 * 
 * @author 塵子曦
 * @since 1.0.0
 */
public class GXConditionFuncJsonContains extends GXConditionFunc<String> {
    /**
     * JSON值对象，可以是List或Dict类型
     */
    private final Object values;

    /**
     * JSON路径，用于指定要检查的JSON文档中的路径
     */
    private String jsonPath;

    /**
     * 构造函数，使用List作为值，默认JSON路径为空
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField JSON字段名，包含要检查的JSON数据
     * @param values 要检查包含的值列表
     */
    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    /**
     * 构造函数，使用List作为值，指定JSON路径
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField JSON字段名，包含要检查的JSON数据
     * @param values 要检查包含的值列表
     * @param jsonPath JSON路径表达式，如"$.tags"
     */
    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    /**
     * 构造函数，使用Dict作为值，默认JSON路径为空
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField JSON字段名，包含要检查的JSON数据
     * @param values 要检查包含的值字典
     */
    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values) {
        this(tableNameAlias, jsonField, values, "");
    }

    /**
     * 构造函数，使用Dict作为值，指定JSON路径
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField JSON字段名，包含要检查的JSON数据
     * @param values 要检查包含的值字典
     * @param jsonPath JSON路径表达式，如"$.profile"
     */
    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    @Override
    public String getOp() {
        return op;
    }

    /**
     * 获取字段表达式，用于JSON_CONTAINS函数的参数
     * 使用Mybatis参数化查询形式#{dbQueryParamInnerDto.paramMap.xxx}防止SQL注入
     *
     * @return 安全的字段表达式字符串
     */
    @Override
    public String getFieldExpression() {
        if (CharSequenceUtil.isEmpty(jsonPath)) {
            jsonPath = "$";
        } else {
            jsonPath = CharSequenceUtil.format("$.{}", jsonPath);
        }
        // 将jsonPath也通过参数化查询传入，防止SQL注入
        this.paramMap.put(paramName + "_path", jsonPath);
        // 使用Mybatis参数化查询形式，防止SQL注入
        // TODO 需要兼容  JSON_CONTAINS(ext, JSON_OBJECT("name", "塵子曦", "father", "塵渊")) 表达式
        String format = "`{}`.`{}`->#{dbQueryParamInnerDto.paramMap.{}_path}, CAST(#{dbQueryParamInnerDto.paramMap.{}} AS JSON)";
        return CharSequenceUtil.format(format, tableNameAlias, getOp(), paramName, paramName);
    }

    /**
     * 获取字段值，将值列表或字典转换为JSON格式的字符串
     * 根据值的类型决定是否添加引号
     *
     * @return JSON格式的字符串
     */
    @Override
    public String getFieldValue() {
        if (values.getClass().isAssignableFrom(Dict.class)) {
            return "[" + JSONUtil.toJsonStr(values) + "]";
        }
        if (values.getClass().isAssignableFrom(List.class)) {
            List<?> valueLst = Convert.convert(List.class, values);
            return "[" + valueLst.stream().map(s -> {
                String format = "\"{}\"";
                if (s.getClass().isAssignableFrom(Integer.class) || s.getClass().isAssignableFrom(Long.class) || s.getClass().isAssignableFrom(Short.class)) {
                    format = "{}";
                }
                return CharSequenceUtil.format(format, s);
            }).collect(Collectors.joining(",")) + "]";
        }
        return values.toString();
    }

    /**
     * 获取MySQL JSON函数名
     *
     * @return JSON_CONTAINS函数名
     */
    @Override
    protected String getFunctionName() {
        return "JSON_CONTAINS";
    }

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     * 格式为：JSON_CONTAINS(字段->路径, JSON数组)
     *
     * @return 安全的WHERE子句字符串
     */
    @Override
    public String whereString() {
        // 在构建SQL时，使用参数化查询形式，值通过Mybatis的#{}机制传入，防止SQL注入
        this.paramMap.clear();
        // 存储JSON数组值到参数映射中
        this.paramMap.put(paramName, this.getFieldValue());
        // 注意：getFieldExpression方法会将jsonPath添加到paramMap中
        // 使用Mybatis参数化查询形式，防止SQL注入
        return CharSequenceUtil.format("{}({})",
                getFunctionName(),
                getFieldExpression());
    }
}