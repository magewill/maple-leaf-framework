package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

/**
 * MySQL CONCAT函数条件构建类
 * <p>
 * 该类用于构建使用MySQL CONCAT函数的查询条件，通常用于字符串连接后进行LIKE查询。
 * CONCAT函数将多个字符串连接成一个字符串，常用于构建复杂的模糊查询条件。
 * </p>
 *
 * <p>安全特性：</p>
 * <ul>
 *   <li>使用MyBatis参数化查询机制(#{})，防止SQL注入攻击</li>
 *   <li>查询值自动添加到paramMap中，而非直接拼接到SQL中</li>
 *   <li>LIKE查询的通配符(%)在参数中处理，避免手动拼接带来的安全风险</li>
 * </ul>
 *
 * <p>性能优化：</p>
 * <ul>
 *   <li>使用参数化查询允许数据库优化执行计划</li>
 *   <li>仅在值后添加%，实现右模糊查询，提高索引利用效率</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：连接用户名和手机号进行模糊查询
 * // 假设需要查询用户名或手机号以"张"开头的记录
 * GXConditionFuncConcat condition = new GXConditionFuncConcat(
 *     "t_user", "CONCAT(username, '-', mobile)", "张", "username", "mobile");
 * // 生成SQL片段：concat(t_user.username,t_user.mobile) LIKE #{dbQueryParamInnerDto.paramMap.condition_xxx}
 * // 参数值会被设置为"张%"，实现右模糊查询
 *
 * // 示例2：连接多个字段进行模糊查询
 * // 假设需要查询产品名称或产品代码以"A001"开头的记录
 * GXConditionFuncConcat condition = new GXConditionFuncConcat(
 *     "t_product", "CONCAT(product_name, '-', product_code)", "A001",
 *     "product_name", "product_code");
 * // 生成SQL片段：concat(t_product.product_name,t_product.product_code) LIKE #{dbQueryParamInnerDto.paramMap.condition_xxx}
 * // 参数值会被设置为"A001%"，实现右模糊查询
 *
 * // 示例3：将条件添加到查询参数中并执行查询
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(condition);
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("t_product")
 *     .condition(conditions)
 *     .build();
 * List<ProductEntity> products = productMapper.findByCondition(queryParam);
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
     * @param op             操作表达式，通常是CONCAT函数调用，如"CONCAT(field1, '-', field2)"
     * @param value          查询值，将自动添加%用于LIKE查询
     * @param fieldNames     要连接的字段名列表（可变参数）
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

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "'%'";
        }

        String strValue = value.toString();

        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }

        // 使用escapeSql方法进行更全面的SQL转义
        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);
        return CharSequenceUtil.format("'{}%'", escapedValue);
    }
}
