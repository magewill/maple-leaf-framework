package cn.maple.core.framework.dto.inner.op;

/**
 * 数据库表连接等值操作实现类
 * <p>
 * 该类实现了表连接中的等值(=)连接条件，用于构建两个表之间基于字段相等的连接
 * 在SQL中生成形如 "table1.field1 = table2.field2" 的连接条件
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个用户表和订单表的等值连接条件
 * GXDbJoinEQ joinCondition = new GXDbJoinEQ("user_id", "id");
 * joinCondition.setMasterTableNameAlias("u"); // 设置主表别名
 * joinCondition.setJoinTableNameAlias("o");  // 设置连接表别名
 * String condition = joinCondition.opString(); // 结果: u.user_id=o.id
 * </pre>
 * <p>
 * 安全性说明：
 * 该类通过分离表别名和字段名的处理，避免了SQL注入风险
 */
@SuppressWarnings("all")
public class GXDbJoinEQ extends GXDbJoinOp {
    public GXDbJoinEQ(String masterFieldName, String joinFieldName) {
        super(masterFieldName, joinFieldName);
    }

    @Override
    public String getOp() {
        return "=";
    }
}
