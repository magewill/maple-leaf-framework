package cn.maple.core.framework.dto.inner.condition;

/**
 * 数值等值查询条件实现类
 * <p>
 * 该类实现了数值类型的等值(=)查询条件，用于构建形如 "field = value" 的SQL条件
 * 通过参数化查询机制确保查询安全，防止SQL注入
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个简单的等值查询条件
 * GXConditionEQ condition = new GXConditionEQ("age", 18);
 * String whereClause = condition.whereString();
 * // 结果: age = #{dbQueryParamInnerDto.paramMap.condition_age_1}
 *
 * // 带表别名的等值查询条件
 * GXConditionEQ condition = new GXConditionEQ("user", "age", 18);
 * String whereClause = condition.whereString();
 * // 结果: user.age = #{dbQueryParamInnerDto.paramMap.condition_age_1}
 *
 * // 在实际应用中与查询构建器结合使用
 * GXModelQueryParamDto paramDto = new GXModelQueryParamDto();
 * paramDto.addCondition(new GXConditionEQ("user_id", 1001));
 * paramDto.addCondition(new GXConditionEQ("status", 1));
 * List<UserEntity> users = mapper.findByCondition(paramDto);
 * </pre>
 */
public class GXConditionEQ extends GXCondition<Number> {
    public GXConditionEQ(String tableNameAlias, String fieldName, Number value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "=";
    }

    @Override
    public Number getFieldValue() {
        return (Number) value;
    }

    @Override
    public Number getFieldOriginalValue() {
        return (Number) value;
    }
}
