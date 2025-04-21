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
 * <p>使用示例：</p>
 * <pre>
 * // 在user_info字段中搜索包含"张三"的JSON路径，使用'one'模式（找到第一个匹配项就返回）
 * GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch("t_user", "user_info", "张三");
 * 
 * // 在user_info字段中搜索包含"张三"的JSON路径，使用'all'模式（返回所有匹配项）
 * GXConditionFuncJsonSearch condition = new GXConditionFuncJsonSearch(
 *     "t_user", "user_info", "张三", GXBuilderConstant.JSON_SEARCH_FUNC_ALL);
 * 
 * // 将条件添加到查询参数中
 * List<GXCondition<?>> conditions = new ArrayList<>();
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