package cn.maple.core.framework.dto.inner.condition.func;

/**
 * MySQL CONCAT函数条件构建类
 * <p>
 * 该类用于构建使用MySQL CONCAT函数的查询条件，通常用于字符串连接后进行LIKE查询。
 * CONCAT函数将多个字符串连接成一个字符串，常用于构建复杂的模糊查询条件。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：连接用户名和手机号进行模糊查询
 * GXConditionFuncConcat condition = new GXConditionFuncConcat(
 *     "t_user", "CONCAT(username, '-', mobile)", "张三", "username", "mobile");
 * 
 * // 示例2：连接多个字段进行模糊查询
 * GXConditionFuncConcat condition = new GXConditionFuncConcat(
 *     "t_product", "CONCAT(product_name, '-', product_code)", "A001", 
 *     "product_name", "product_code");
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
public class GXConditionFuncConcat extends GXConditionFunc<String> {
    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，用于SQL查询
     * @param op 操作表达式，通常是CONCAT函数调用，如"CONCAT(field1, '-', field2)"
     * @param value 查询值，将自动添加%用于LIKE查询
     * @param fieldNames 要连接的字段名列表（可变参数）
     */
    public GXConditionFuncConcat(String tableNameAlias, String op, String value, Object... fieldNames) {
        super(tableNameAlias, op, value, fieldNames);
    }

    @Override
    public String getOp() {
        return op;
    }

    /**
     * 获取字段值，添加%用于LIKE查询
     * 该方法会自动在值后面添加%，用于构建右模糊查询条件
     *
     * @return 添加了%的字段值，用于LIKE查询
     */
    @Override
    public String getFieldValue() {
        // 在参数映射中添加带有%的值，用于LIKE查询
        this.paramMap.put(paramName, value + "%");
        return value + "%";
    }

    /**
     * 获取MySQL函数名
     *
     * @return concat函数名
     */
    @Override
    protected String getFunctionName() {
        return "concat";
    }
}
