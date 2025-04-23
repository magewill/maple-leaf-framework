package cn.maple.core.framework.dto.inner.op;

/**
 * 数据库表字段等于固定值的连接条件类
 * <p>
 * 该类用于构建表字段等于固定值的连接条件，继承自GXDbJoinValue基类
 * 主要用于JOIN查询中的条件构建，生成形如 "table_alias.field_name = value" 的SQL片段
 * 这是最常用的连接条件类型
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个表字段等于固定值的连接条件
 * GXDbJoinValueEQ joinCondition = new GXDbJoinValueEQ("user", "status", 1);
 * // 生成的条件将是：user.status = 1
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
 * 3. 对于字符串类型值，应使用参数化查询(#{})而非直接拼接SQL，避免SQL注入风险
 * 4. 在处理NULL值时，会自动转换为"IS NULL"语法，确保SQL语法正确
 * </p>
 */
public class GXDbJoinValueEQ extends GXDbJoinValue {
    public GXDbJoinValueEQ(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return "=";
    }
}
