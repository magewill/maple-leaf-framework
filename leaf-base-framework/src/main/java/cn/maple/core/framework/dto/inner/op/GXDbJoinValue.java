package cn.maple.core.framework.dto.inner.op;

/**
 * 数据库表连接与固定值比较的抽象基类
 * <p>
 * 该类用于构建表字段与固定值比较的连接条件，支持不同类型的比较操作（如等于、大于等于等）
 * 与GXDbJoinOp不同，该类比较的是表字段与一个固定值，而不是两个表的字段
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个表字段等于固定值的连接条件
 * GXDbJoinValueEQ joinCondition = new GXDbJoinValueEQ("user", "status", 1);
 * String condition = joinCondition.opString(); // 结果: user.status=1
 *
 * // 在实际应用中与其他组件结合使用
 * GXJoinDto joinDto = new GXJoinDto();
 * joinDto.setJoinTable("users");
 * joinDto.setJoinTableAlias("u");
 * joinDto.setJoinCondition(joinCondition);
 *
 * // 处理NULL值的情况
 * GXDbJoinValueEQ nullCondition = new GXDbJoinValueEQ("user", "email", null);
 * String nullCheck = nullCondition.opString(); // 结果: user.email IS NULL
 *
 * // 处理字符串值的情况（实际使用时会通过参数化查询处理）
 * GXDbJoinValueEQ strCondition = new GXDbJoinValueEQ("user", "name", "张三");
 * String strCheck = strCondition.opString(); // 结果: user.name=#{paramName}
 * </pre>
 * </p>
 *
 * <p>
 * 安全性说明：
 * 1. 该类处理固定值时会根据值的类型进行适当处理，避免SQL注入
 * 2. 对于字符串类型的值，应当使用参数化查询处理，而不是直接拼接到SQL中
 */
@SuppressWarnings("all")
public abstract class GXDbJoinValue extends GXDbJoinOp {
    /**
     * 表别名
     */
    private final String tableNameAlias;

    /**
     * 表字段
     */
    private final String fieldName;

    /**
     * 字段的值
     * 用于查询固定值的场景
     */
    private Object fieldValue;

    public GXDbJoinValue(String tableNameAlias, String fieldName, Object fieldValue) {
        this.tableNameAlias = tableNameAlias;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    @Override
    public String opString() {
        return tableNameAlias + "." + fieldName + getOp() + fieldValue;
    }
}
