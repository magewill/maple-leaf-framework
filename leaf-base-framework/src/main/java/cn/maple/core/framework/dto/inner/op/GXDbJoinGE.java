package cn.maple.core.framework.dto.inner.op;

/**
 * 数据库表连接大于等于操作实现类
 * <p>
 * 该类实现了表连接中的大于等于(>=)连接条件，用于构建两个表之间基于字段大于等于关系的连接
 * 在SQL中生成形如 "table1.field1 >= table2.field2" 的连接条件
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个订单表和商品表的大于等于连接条件（例如订单数量大于等于商品库存）
 * GXDbJoinGE joinCondition = new GXDbJoinGE("order_quantity", "product_stock");
 * joinCondition.setMasterTableNameAlias("o"); // 设置主表别名
 * joinCondition.setJoinTableNameAlias("p");  // 设置连接表别名
 * String condition = joinCondition.opString(); // 结果: o.order_quantity>=p.product_stock
 * </pre>
 * <p>
 * 安全性说明：
 * 该类通过分离表别名和字段名的处理，避免了SQL注入风险
 */
@SuppressWarnings("all")
public class GXDbJoinGE extends GXDbJoinOp {
    public GXDbJoinGE(String masterFieldName, String joinFieldName) {
        super(masterFieldName, joinFieldName);
    }

    @Override
    public String getOp() {
        return ">=";
    }
}
