package cn.maple.core.framework.dto.inner.field;

/**
 * 字符串类型字段更新实现类
 * <p>
 * 该类实现了字符串类型字段的更新操作，用于构建形如 "field = 'value'" 的SQL更新语句
 * 通过参数化查询机制确保更新操作安全，防止SQL注入
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个简单的字符串字段更新
 * GXUpdateStrField updateField = new GXUpdateStrField("", "username", "张三");
 * String updateClause = updateField.updateString(); 
 * // 结果: username = #{dbQueryParamInnerDto.paramMap.update_username_1}
 * 
 * // 带表别名的字符串字段更新
 * GXUpdateStrField updateField = new GXUpdateStrField("user", "username", "张三");
 * String updateClause = updateField.updateString();
 * // 结果: user.username = #{dbQueryParamInnerDto.paramMap.update_username_1}
 * 
 * // 在实际应用中与更新构建器结合使用
 * GXModelQueryParamDto paramDto = new GXModelQueryParamDto();
 * paramDto.addUpdateField(new GXUpdateStrField("", "username", "张三"));
 * paramDto.addUpdateField(new GXUpdateStrField("", "email", "zhangsan@example.com"));
 * paramDto.addCondition(new GXConditionEQ("", "id", 1001));
 * mapper.updateByCondition(paramDto);
 * </pre>
 * 
 * 安全性说明：
 * 即使输入的字符串包含SQL注入攻击字符（如单引号、分号等），也会被安全处理，
 * 因为值是通过参数映射传递给MyBatis的，而不是直接拼接到SQL中
 */
public class GXUpdateStrField extends GXUpdateField<String> {
    public GXUpdateStrField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        // 在参数化查询中，值会通过paramMap传递给MyBatis
        return value.toString();
    }
}
