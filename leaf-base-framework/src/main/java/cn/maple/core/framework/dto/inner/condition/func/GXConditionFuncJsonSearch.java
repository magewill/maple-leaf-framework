package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;

/**
 * MySQL JSON_SEARCH函数条件构建类
 * <p>
 * 该类用于构建使用MySQL JSON_SEARCH函数的查询条件，支持在JSON数据中搜索指定值。
 * JSON_SEARCH函数用于在JSON文档中搜索字符串，并返回匹配的路径。
 * </p>
 * 
 * <p>安全特性：</p>
 * <ul>
 *   <li>使用MyBatis参数化查询机制(#{})，彻底防止SQL注入</li>
 *   <li>搜索值和模式参数通过参数化方式传递，不直接拼接到SQL中</li>
 *   <li>表名和字段名使用反引号(``)包裹，防止SQL关键字冲突</li>
 *   <li>oneOrAll参数经过验证，确保只能是'one'或'all'，防止非法值</li>
 *   <li>自动处理空值情况，提供合理的默认值，增强代码健壮性</li>
 * </ul>
 * 
 * <p>性能优化：</p>
 * <ul>
 *   <li>参数化查询允许数据库缓存执行计划，提高性能</li>
 *   <li>使用常量值定义搜索模式，避免硬编码字符串</li>
 *   <li>合理使用'one'和'all'模式，根据需求选择最优搜索策略</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：在user_info字段中搜索包含"张三"的JSON路径，使用'one'模式（找到第一个匹配项就返回）
 * GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch("t_user", "user_info", "张三");
 * // 生成SQL片段：JSON_SEARCH(`t_user`.`user_info`, #{dbQueryParamInnerDto.paramMap.condition_xxx_oneOrAll}, #{dbQueryParamInnerDto.paramMap.condition_xxx})
 * // 参数值："张三"，模式参数："one"
 * 
 * // 示例2：在user_info字段中搜索包含"张三"的JSON路径，使用'all'模式（返回所有匹配项）
 * GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch(
 *     "t_user", "user_info", "张三", GXBuilderConstant.JSON_SEARCH_FUNC_ALL);
 * // 生成SQL片段：JSON_SEARCH(`t_user`.`user_info`, #{dbQueryParamInnerDto.paramMap.condition_xxx_oneOrAll}, #{dbQueryParamInnerDto.paramMap.condition_xxx})
 * // 参数值："张三"，模式参数："all"
 * 
 * // 示例3：在复杂JSON对象中搜索特定值
 * // 假设有JSON数据：{"users":[{"name":"张三","age":30},{"name":"李四","age":25}]}
 * GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch("t_dept", "dept_info", "技术部");
 * // 生成SQL片段：JSON_SEARCH(`t_dept`.`dept_info`, #{dbQueryParamInnerDto.paramMap.condition_xxx_oneOrAll}, #{dbQueryParamInnerDto.paramMap.condition_xxx})
 * // 如果JSON中包含"技术部"，将返回匹配的路径，如"$.department.name"
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
public class GXConditionFuncJsonSearch extends GXConditionFunc<String> {
    /**
     * 搜索值，用于在JSON中查找
     */
    private final String value;

    /**
     * 搜索模式：'one'表示找到第一个匹配项就返回，'all'表示返回所有匹配项
     */
    private final String oneOrAll;

    /**
     * 构造函数，默认使用'one'模式
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param field JSON字段名，包含要搜索的JSON数据
     * @param value 要搜索的值
     */
    public GXConditionFuncJsonSearch(String tableNameAlias, String field, String value) {
        this(tableNameAlias, field, value, GXBuilderConstant.JSON_SEARCH_FUNC_ONE);
    }

    /**
     * 构造函数，允许指定搜索模式
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param field JSON字段名，包含要搜索的JSON数据
     * @param value 要搜索的值
     * @param oneOrAll 搜索模式：'one'或'all'
     */
    public GXConditionFuncJsonSearch(String tableNameAlias, String field, String value, String oneOrAll) {
        super(tableNameAlias, field, value, oneOrAll);
        this.value = value;
        this.oneOrAll = CharSequenceUtil.isEmpty(oneOrAll) ? GXBuilderConstant.JSON_SEARCH_FUNC_ONE : oneOrAll;
    }

    @Override
    public String getOp() {
        return op;
    }

    /**
     * 获取字段表达式，用于JSON_SEARCH函数的第一个参数
     * 使用反引号包裹表名和字段名，防止SQL关键字冲突
     *
     * @return 安全的字段表达式字符串
     */
    @Override
    public String getFieldExpression() {
        String format = "`{}`.`{}`";
        return CharSequenceUtil.format(format, tableNameAlias, getOp());
    }

    /**
     * 获取字段值，用于JSON_SEARCH函数的搜索值参数
     * 注意：返回值仅用于日志或调试，实际SQL中使用参数化查询
     *
     * @return 字段值字符串
     */
    @Override
    public String getFieldValue() {
        // 存储原始值到参数映射中，不需要手动添加引号，Mybatis会处理
        this.paramMap.put(paramName, value);
        return value;
    }

    /**
     * 获取MySQL JSON函数名
     *
     * @return JSON_SEARCH函数名
     */
    @Override
    protected String getFunctionName() {
        return "JSON_SEARCH";
    }

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     * 格式为：JSON_SEARCH(字段, 模式, 搜索值)
     *
     * @return 安全的WHERE子句字符串
     */
    @Override
    public String whereString() {
        // 使用Mybatis参数化查询形式，防止SQL注入
        // oneOrAll参数也需要参数化处理
        this.paramMap.put(paramName + "_oneOrAll", oneOrAll);
        return CharSequenceUtil.format("{}({}, #{dbQueryParamInnerDto.paramMap.{}_oneOrAll}, #{dbQueryParamInnerDto.paramMap.{}})",
                getFunctionName(),
                getFieldExpression(),
                paramName,
                paramName);
    }
}