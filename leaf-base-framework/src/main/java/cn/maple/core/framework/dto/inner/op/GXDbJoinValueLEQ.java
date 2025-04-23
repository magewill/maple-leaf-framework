package cn.maple.core.framework.dto.inner.op;

/**
 * 数据库表字段小于等于固定值的连接条件类
 * <p>
 * 该类用于构建表字段小于等于固定值的连接条件，继承自GXDbJoinValue基类
 * 主要用于JOIN查询中的条件构建，生成形如 "table_alias.field_name <= value" 的SQL片段
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个表字段小于等于固定值的连接条件
 * GXDbJoinValueLEQ joinCondition = new GXDbJoinValueLEQ("user", "age", 30);
 * // 生成的条件将是：user.age <= 30
 * 
 * // 在实际应用中与GXJoinDto结合使用
 * List<GXDbJoinOp> conditions = new ArrayList<>();
 * conditions.add(joinCondition);
 * 
 * GXJoinDto joinDto = GXJoinDto.builder()
 *     .joinTableName("user_profiles")
 *     .joinTableNameAlias("up")
 *     .masterTableNameAlias("u")
 *     .joinType(GXJoinTypeEnums.LEFT)
 *     .and(conditions)
 *     .build();
 * </pre>
 * </p>
 * 
 * <p>
 * 安全性说明：
 * 1. 该类通过参数化查询处理字段值，防止SQL注入攻击
 * 2. 对于不同类型的值，会进行适当的类型处理，确保SQL语句的正确性
 * 3. 在处理字符串类型值时，应当使用参数化查询而非直接拼接SQL
 * </p>
 */
public class GXDbJoinValueLEQ extends GXDbJoinValue {
    public GXDbJoinValueLEQ(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return "<=";
    }
}
