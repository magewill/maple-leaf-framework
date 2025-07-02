package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

/**
 * JSON字段等值查询条件类
 * <p>
 * 该类用于构建针对JSON类型字段的等值查询条件，支持查询JSON对象中的特定属性。
 * 使用MySQL的JSON_EXTRACT(column->path)语法实现，并通过参数化查询防止SQL注入。
 * </p>
 *
 * <p>
 * 安全特性：
 * - 使用MyBatis参数化查询机制(#{})，而非字符串拼接，防止SQL注入
 * - 对JSON路径和查询值进行分离处理，增强安全性
 * - 自动检测SQL注入风险，发现风险时抛出异常
 * - 根据值类型自动处理引号，避免类型错误
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 假设user_info字段内容为: {"contact":{"email":"test@example.com","phone":"123456"}}
 *
 * // 1. 查询JSON字段中email等于特定值的记录
 * GXCondition<?> jsonEqCondition = new GXConditionJsonEQ("user", "user_info", "contact.email", "test@example.com");
 *
 * // 2. 将条件添加到条件列表
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(jsonEqCondition);
 *
 * // 3. 创建查询参数并执行查询
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .condition(conditions)
 *     .build();
 * String sql = GXBuildRawSql.findByCondition(queryParam);
 * // 生成SQL: SELECT user.* FROM user WHERE user.`user_info`->#{dbQueryParamInnerDto.paramMap.condition_user_info_1_path} = #{dbQueryParamInnerDto.paramMap.condition_user_info_1}
 * </pre>
 * </p>
 *
 * @author magleton
 */
public class GXConditionJsonEQ extends GXCondition<Object> {
    /**
     * JSON路径，用于指定要查询的JSON属性位置
     * 格式为$.property或$.property.nested_property
     */
    private final String jsonPath;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表别名，用于多表关联场景，可为空
     * @param fieldName      JSON类型的字段名
     * @param jsonFieldName  JSON路径，指定要查询的属性，如"contact.email"
     * @param value          查询值，将与JSON属性值进行比较
     */
    public GXConditionJsonEQ(String tableNameAlias, String fieldName, String jsonFieldName, Object value) {
        super(tableNameAlias, fieldName, value);
        // 构建标准JSON路径格式 $.property.nested_property
        this.jsonPath = CharSequenceUtil.format("$.{}", jsonFieldName);
        // 清除原参数映射并添加JSON路径参数
        this.paramMap.clear();
        // 将查询值作为参数，防止SQL注入
        this.paramMap.put(paramName, value);
        // 将JSON路径作为单独参数，增强安全性
        this.paramMap.put(paramName + "_path", jsonPath);
    }

    /**
     * 获取操作符
     *
     * @return 返回等号操作符"="
     */
    @Override
    public String getOp() {
        return "=";
    }

    /**
     * 获取字段值的字符串表示
     * <p>
     * 该方法根据值的类型自动处理引号：
     * - 数字类型不添加引号
     * - 非数字类型添加单引号
     * </p>
     * <p>
     * 安全处理：
     * - 检测SQL注入风险，发现风险时抛出异常
     * </p>
     *
     * @return 格式化后的字段值字符串
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        // 检查SQL注入风险
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        // 根据值类型决定是否添加引号
        if (NumberUtil.isNumber(value.toString())) {
            return CharSequenceUtil.format("{}", Integer.valueOf(value.toString()));
        }
        return CharSequenceUtil.format("'{}'", value);
    }

    /**
     * 生成WHERE子句的SQL片段
     * <p>
     * 使用MySQL的JSON_EXTRACT语法(->)和参数化查询，构建安全的JSON字段查询条件
     * </p>
     * <p>
     * 安全处理：
     * - 再次检测SQL注入风险，双重保障安全
     * - 使用参数化查询传递JSON路径和查询值
     * - 自动处理表别名，支持多表查询场景
     * </p>
     *
     * @return 格式化的WHERE条件SQL片段
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String whereString() {
        // 再次检查SQL注入风险，双重保障安全
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }

        // 根据是否有表别名构建不同的SQL片段
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            // 无表别名的情况
            return CharSequenceUtil.format("`{}`->#{dbQueryParamInnerDto.paramMap.{}} {} #{dbQueryParamInnerDto.paramMap.{}}",
                    fieldExpression, paramName + "_path", getOp(), paramName);
        }
        // 有表别名的情况
        return CharSequenceUtil.format("{}.`{}`->#{dbQueryParamInnerDto.paramMap.{}} {} #{dbQueryParamInnerDto.paramMap.{}}",
                tableNameAlias, fieldExpression, paramName + "_path", getOp(), paramName);
    }

    @Override
    public Object getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }

        String strValue = value.toString();

        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }

        if (NumberUtil.isNumber(strValue)) {
            return CharSequenceUtil.format("{}", strValue);
        }

        // 使用escapeSql方法进行更全面的SQL转义
        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);
        return CharSequenceUtil.format("'{}'", escapedValue);
    }
}
