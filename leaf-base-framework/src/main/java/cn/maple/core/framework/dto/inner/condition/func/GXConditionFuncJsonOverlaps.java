package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL JSON_OVERLAPS函数条件构建类
 * <p>
 * 该类用于构建使用MySQL JSON_OVERLAPS函数的查询条件，用于检查两个JSON文档是否有共同元素。
 * JSON_OVERLAPS函数返回1（真）如果两个JSON数组有至少一个共同元素，或者0（假）如果没有共同元素。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：使用List作为值，检查tags字段是否与给定数组有重叠
 * List&lt;Object&gt; tagList = new ArrayList<>();
 * tagList.add("技术");
 * tagList.add("Java");
 * GXConditionFuncJsonOverlaps condition = new GXConditionFuncJsonOverlaps("t_article", "tags", tagList);
 * 
 * // 示例2：使用Dict作为值，并指定JSON路径
 * Dict dict = Dict.create().set("name", "张三").set("age", 25);
 * GXConditionFuncJsonOverlaps condition = new GXConditionFuncJsonOverlaps("t_user", "user_info", dict, "profile");
 * 
 * // 将条件添加到查询参数中
 * List&lt;GXCondition&lt;?&gt;&gt; conditions = new ArrayList<>();
 * conditions.add(condition);
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("t_user")
 *     .condition(conditions)
 *     .build();
 * </pre>
 * 
 * @author 塵子曦
 * @since 1.0.0
 */
public class GXConditionFuncJsonOverlaps extends GXConditionFunc<String> {
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
     * @param values 要检查重叠的值列表
     */
    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    /**
     * 构造函数，使用List作为值，指定JSON路径
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField JSON字段名，包含要检查的JSON数据
     * @param values 要检查重叠的值列表
     * @param jsonPath JSON路径表达式，如"$.tags"
     */
    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    /**
     * 构造函数，使用Dict作为值，默认JSON路径为空
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField JSON字段名，包含要检查的JSON数据
     * @param values 要检查重叠的值字典
     */
    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, Dict values) {
        this(tableNameAlias, jsonField, values, "");
    }

    /**
     * 构造函数，使用Dict作为值，指定JSON路径
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField JSON字段名，包含要检查的JSON数据
     * @param values 要检查重叠的值字典
     * @param jsonPath JSON路径表达式，如"$.profile"
     */
    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, Dict values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    @Override
    public String getOp() {
        return op;
    }

    /**
     * 获取字段表达式，用于JSON_OVERLAPS函数的参数
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
        String format = "`{}`.`{}`->#{dbQueryParamInnerDto.paramMap.{}_path}, CAST(#{dbQueryParamInnerDto.paramMap.{}} AS JSON)";
        return CharSequenceUtil.format(format, tableNameAlias, getOp(), paramName, paramName);
    }

    /**
     * 获取字段值，将值列表转换为JSON数组格式的字符串
     * 根据值的类型决定是否添加引号
     *
     * @return JSON数组格式的字符串
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
     * @return JSON_OVERLAPS函数名
     */
    @Override
    protected String getFunctionName() {
        return "JSON_OVERLAPS";
    }

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     * 重写父类方法，因为JSON_OVERLAPS函数的参数结构与基类不同
     * 格式为：JSON_OVERLAPS(字段->路径, JSON数组)
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
        return CharSequenceUtil.format("{}({})",
                getFunctionName(),
                getFieldExpression());
    }
}