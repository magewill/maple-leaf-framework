package cn.maple.core.framework.dto.inner.op;

import cn.hutool.core.text.CharSequenceUtil;

/**
 * 数据库表连接操作的抽象基类
 * <p>
 * 该类用于构建SQL JOIN操作中的连接条件，支持不同类型的连接操作（如等于、大于等于等）
 * 通过将表别名和字段名分离处理，提高了SQL构建的灵活性和安全性
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个表连接等于条件
 * GXDbJoinOp joinCondition = new GXDbJoinEQ("user_id", "id");
 * joinCondition.setMasterTableNameAlias("u");
 * joinCondition.setJoinTableNameAlias("o");
 * String condition = joinCondition.opString(); // 结果: u.user_id=o.id
 * 
 * // 在实际应用中与其他组件结合使用
 * GXJoinDto joinDto = new GXJoinDto();
 * joinDto.setJoinTable("orders");
 * joinDto.setJoinTableAlias("o");
 * joinDto.setJoinCondition(joinCondition);
 * </pre>
 */
public abstract class GXDbJoinOp {
    /**
     * 主表别名
     */
    protected String masterTableNameAlias;

    /**
     * 主表字段
     */
    protected String masterFieldName;

    /**
     * 子表别名
     */
    protected String joinTableNameAlias;

    /**
     * 子表字段
     */
    protected String joinFieldName;

    protected GXDbJoinOp() {

    }

    protected GXDbJoinOp(String masterFieldName, String subFieldName) {
        this.masterFieldName = masterFieldName;
        this.joinFieldName = subFieldName;
    }

    public String getMasterTableNameAlias() {
        return masterTableNameAlias;
    }

    public void setMasterTableNameAlias(String masterTableNameAlias) {
        this.masterTableNameAlias = masterTableNameAlias;
    }

    public String getJoinTableNameAlias() {
        return joinTableNameAlias;
    }

    public void setJoinTableNameAlias(String joinTableNameAlias) {
        this.joinTableNameAlias = joinTableNameAlias;
    }

    /**
     * 获取操作符
     * 由子类实现，返回具体的操作符（如=, >=等）
     *
     * @return 操作符字符串
     */
    abstract String getOp();

    /**
     * 生成表连接条件的SQL片段
     * <p>
     * 该方法处理表别名和字段名，构建安全的表连接条件：
     * 1. 如果字段名包含点号(.)，提取实际字段名部分
     * 2. 根据表别名和字段名，构建完整的字段引用（如 table.field）
     * 3. 使用操作符连接主表字段和连接表字段
     * <p>
     * 安全处理：
     * - 使用CharSequenceUtil.format进行字符串格式化，避免直接拼接
     * - 分离处理表别名和字段名，降低SQL注入风险
     * - 规范化字段引用格式，确保SQL语法正确
     *
     * @return 完整的表连接条件SQL片段，如 "t1.field1=t2.field2"
     */
    public String opString() {
        if (CharSequenceUtil.contains(masterFieldName, ".")) {
            masterFieldName = masterFieldName.split("\\.")[1];
        }
        if (CharSequenceUtil.contains(joinFieldName, ".")) {
            joinFieldName = joinFieldName.split("\\.")[1];
        }
        if (CharSequenceUtil.isNotEmpty(masterTableNameAlias) && CharSequenceUtil.isNotEmpty(masterFieldName)) {
            masterFieldName = CharSequenceUtil.format("{}.{}", masterTableNameAlias, masterFieldName);
        }
        if (CharSequenceUtil.isNotEmpty(joinTableNameAlias) && CharSequenceUtil.isNotEmpty(joinFieldName)) {
            joinFieldName = CharSequenceUtil.format("{}.{}", joinTableNameAlias, joinFieldName);
        }
        return masterFieldName + getOp() + joinFieldName;
    }
}
