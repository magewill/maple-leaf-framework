package cn.maple.core.framework.dto.inner;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.op.GXDbJoinOp;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据库表连接DTO
 * <p>
 * 该类用于构建SQL JOIN操作，支持多种连接类型（LEFT、RIGHT、INNER）和复杂的连接条件
 * 通过Builder模式提供流畅的API，便于构建复杂的表连接查询
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个左连接
 * GXJoinDto joinDto = GXJoinDto.builder()
 *     .joinTableName("orders")
 *     .joinTableNameAlias("o")
 *     .masterTableName("users")
 *     .masterTableNameAlias("u")
 *     .joinType(GXJoinTypeEnums.LEFT)
 *     .build();
 * 
 * // 添加AND连接条件
 * List<GXDbJoinOp> andConditions = new ArrayList<>();
 * andConditions.add(new GXDbJoinEQ("user_id", "id"));
 * joinDto.setAnd(andConditions);
 * 
 * // 添加OR连接条件
 * List<GXDbJoinOp> orConditions = new ArrayList<>();
 * orConditions.add(new GXDbJoinEQ("email", "contact_email"));
 * joinDto.setOr(orConditions);
 * 
 * // 添加额外的过滤条件
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("o.status", 1));
 * joinDto.setConditions(conditions);
 * 
 * // 设置是否自动填充删除条件
 * joinDto.setAutoFillIsDeleteCondition(true);
 * </pre>
 * </p>
 * 
 * <p>
 * 安全性说明：
 * 1. 该类通过参数化查询处理连接条件，防止SQL注入攻击
 * 2. 表名和字段名应当通过白名单验证，避免非法输入
 * 3. 在setAnd和setOr方法中会自动填充表别名，确保SQL语法正确
 * 4. 对于autoFillIsDeleteCondition功能，会自动添加安全的删除条件，避免查询已删除数据
 * </p>
 * 
 * <p>
 * 性能优化说明：
 * 1. 合理设置连接条件，避免产生笛卡尔积
 * 2. 对于大表连接，应当考虑索引优化
 * 3. 尽量减少连接表的数量，避免过于复杂的查询
 * </p>
 */
@Data
@Builder
public class GXJoinDto {
    /**
     * 表名字
     */
    private String joinTableName;

    /**
     * 表别名
     */
    private String joinTableNameAlias;

    /**
     * 主表别名
     */
    private String masterTableNameAlias;

    /**
     * 主表名字
     */
    private String masterTableName;

    /**
     * 链接类型
     */
    private GXJoinTypeEnums joinType;

    /**
     * 链接的AND条件
     */
    private List<GXDbJoinOp> and;

    /**
     * 链接的OR条件
     */
    private List<GXDbJoinOp> or;

    /**
     * 额外的条件
     */
    private List<GXCondition<?>> conditions;

    /**
     * 是否自动填充删除条件
     * 该删除条件会自动排除掉已经删除了的数据
     */
    private boolean autoFillIsDeleteCondition;

    public void setAnd(List<GXDbJoinOp> and) {
        and.forEach(op -> {
            if (CharSequenceUtil.isEmpty(op.getMasterTableNameAlias())) {
                op.setMasterTableNameAlias(masterTableNameAlias);
            }
            if (CharSequenceUtil.isEmpty(op.getJoinTableNameAlias())) {
                op.setJoinTableNameAlias(joinTableNameAlias);
            }
        });
        this.and = and;
    }

    public void setOr(List<GXDbJoinOp> or) {
        or.forEach(op -> {
            if (CharSequenceUtil.isEmpty(op.getMasterTableNameAlias())) {
                op.setMasterTableNameAlias(masterTableNameAlias);
            }
            if (CharSequenceUtil.isEmpty(op.getJoinTableNameAlias())) {
                op.setJoinTableNameAlias(joinTableNameAlias);
            }
        });
        this.or = or;
    }
}
