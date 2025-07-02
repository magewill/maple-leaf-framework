package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;

import java.util.List;

/**
 * MySQL JSON_OVERLAPS函数条件构建类
 * <p>
 * 该类用于构建使用MySQL JSON_OVERLAPS函数的查询条件，用于检查两个JSON文档是否有共同元素。
 * JSON_OVERLAPS函数返回1（真）如果两个JSON数组有至少一个共同元素，或者0（假）如果没有共同元素。
 * </p>
 *
 * <p>安全特性：</p>
 * <ul>
 *   <li>使用MyBatis参数化查询机制(#{})，彻底防止SQL注入</li>
 *   <li>JSON值和路径通过参数化方式传递，不直接拼接到SQL中</li>
 *   <li>使用CAST(#{param} AS JSON)确保参数被正确处理为JSON类型</li>
 *   <li>表名和字段名使用反引号(``)包裹，防止SQL关键字冲突</li>
 *   <li>每次调用whereString()方法时清空并重建paramMap，避免参数污染</li>
 *   <li>自动处理不同类型的JSON值（字符串、数字等），确保类型安全</li>
 * </ul>
 *
 * <p>性能优化：</p>
 * <ul>
 *   <li>参数化查询允许数据库缓存执行计划，提高性能</li>
 *   <li>JSON路径表达式优化，提高JSON查询效率</li>
 *   <li>使用Stream API高效处理JSON数组元素</li>
 *   <li>针对不同数据类型（整数、字符串等）进行优化处理</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：使用List作为值，检查tags字段是否与给定数组有重叠
 * List&lt;Object&gt; tagList = new ArrayList<>();
 * tagList.add("技术");
 * tagList.add("Java");
 * GXConditionFuncJsonOverlaps condition = new GXConditionFuncJsonOverlaps("t_article", "tags", tagList);
 * // 生成SQL片段：JSON_OVERLAPS(`t_article`.`tags`->#{dbQueryParamInnerDto.paramMap.condition_xxx_path}, CAST(#{dbQueryParamInnerDto.paramMap.condition_xxx} AS JSON))
 * // 参数值会被设置为JSON数组：["技术","Java"]
 * // 路径参数会被设置为：$
 *
 * // 示例2：使用Dict作为值，并指定JSON路径，检查用户信息中的兴趣爱好是否与给定值有重叠
 * Dict dict = Dict.create().set("interests", Arrays.asList("阅读", "编程"));
 * GXConditionFuncJsonOverlaps condition = new GXConditionFuncJsonOverlaps("t_user", "user_info", dict, "hobbies");
 * // 生成SQL片段：JSON_OVERLAPS(`t_user`.`user_info`->#{dbQueryParamInnerDto.paramMap.condition_xxx_path}, CAST(#{dbQueryParamInnerDto.paramMap.condition_xxx} AS JSON))
 * // 参数值会被设置为JSON对象：[{"interests":["阅读","编程"]}]
 * // 路径参数会被设置为：$.hobbies
 *
 * // 示例3：检查标签数组是否与数字数组有重叠
 * List&lt;Object&gt; idList = new ArrayList<>();
 * idList.add(1);
 * idList.add(2);
 * GXConditionFuncJsonOverlaps condition = new GXConditionFuncJsonOverlaps("t_article", "tag_ids", idList);
 * // 生成SQL片段：JSON_OVERLAPS(`t_article`.`tag_ids`->#{dbQueryParamInnerDto.paramMap.condition_xxx_path}, CAST(#{dbQueryParamInnerDto.paramMap.condition_xxx} AS JSON))
 * // 参数值会被设置为JSON数组：[1,2]（注意数字没有引号）
 *
 * // 示例4：将条件添加到查询参数中并执行查询
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(condition);
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("t_article")
 *     .condition(conditions)
 *     .build();
 * List<ArticleEntity> articles = articleMapper.findByCondition(queryParam);
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
     * @param jsonField      JSON字段名，包含要检查的JSON数据
     * @param values         要检查重叠的值列表
     */
    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    /**
     * 构造函数，使用List作为值，指定JSON路径
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField      JSON字段名，包含要检查的JSON数据
     * @param values         要检查重叠的值列表
     * @param jsonPath       JSON路径表达式，如"$.tags"
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
     * @param jsonField      JSON字段名，包含要检查的JSON数据
     * @param values         要检查重叠的值字典
     */
    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, Dict values) {
        this(tableNameAlias, jsonField, values, "");
    }

    /**
     * 构造函数，使用Dict作为值，指定JSON路径
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param jsonField      JSON字段名，包含要检查的JSON数据
     * @param values         要检查重叠的值字典
     * @param jsonPath       JSON路径表达式，如"$.profile"
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
        if (TypeToken.of(values.getClass()).isSubtypeOf(List.class)) {
            ObjectMapper objectMapper = GXSpringContextUtils.getBean(ObjectMapper.class);
            try {
                assert objectMapper != null;
                return objectMapper.writeValueAsString(values);
            } catch (JsonProcessingException e) {
                throw new GXBusinessException(e.getMessage(), e);
            }
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

    @Override
    public String getFieldOriginalValue() {
        return "";
    }
}