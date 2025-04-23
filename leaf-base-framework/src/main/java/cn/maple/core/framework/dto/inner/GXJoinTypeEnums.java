package cn.maple.core.framework.dto.inner;

import cn.maple.core.framework.constant.GXBuilderConstant;

/**
 * SQL连接类型枚举
 * <p>
 * 该枚举定义了SQL查询中可用的JOIN类型，包括左连接、右连接和内连接。
 * 用于构建SQL JOIN子句时指定连接类型。
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建一个左连接配置
 * GXJoinDto leftJoin = GXJoinDto.builder()
 *     .joinType(GXJoinTypeEnums.LEFT)
 *     .joinTableName("user_address")
 *     .joinTableNameAlias("ua")
 *     .masterTableName("user")
 *     .masterTableNameAlias("u")
 *     .build();
 * 
 * // 2. 添加连接条件
 * leftJoin.addAnd(new GXDbJoinOp("u.id", "=", "ua.user_id"));
 * 
 * // 3. 将连接添加到查询参数
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .tableNameAlias("u")
 *     .addJoin(leftJoin)
 *     .build();
 * 
 * // 4. 执行查询
 * String sql = GXBuildRawSql.findByCondition(queryParam);
 * // 生成SQL: SELECT u.* FROM user u LEFT OUTER JOIN user_address ua ON (u.id = ua.user_id)
 * </pre>
 * </p>
 *
 * @author magleton
 */
public enum GXJoinTypeEnums {
    /**
     * 左连接(LEFT OUTER JOIN)
     * 返回左表中的所有记录，即使右表中没有匹配。右表中没有匹配的记录结果是NULL。
     */
    LEFT(GXBuilderConstant.LEFT_JOIN_TYPE, "左连接"),

    /**
     * 右连接(RIGHT OUTER JOIN)
     * 返回右表中的所有记录，即使左表中没有匹配。左表中没有匹配的记录结果是NULL。
     */
    RIGHT(GXBuilderConstant.RIGHT_JOIN_TYPE, "右连接"),

    /**
     * 内连接(INNER JOIN)
     * 仅返回两个表中都匹配的行。
     */
    INNER(GXBuilderConstant.INNER_JOIN_TYPE, "内连接");

    /**
     * JOIN类型的字符串表示
     * 对应SQL中的JOIN关键字
     */
    private final String joinType;

    /**
     * JOIN类型的中文描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param joinType JOIN类型的字符串表示
     * @param desc     JOIN类型的中文描述
     */
    GXJoinTypeEnums(String joinType, String desc) {
        this.joinType = joinType;
        this.desc = desc;
    }

    /**
     * 获取JOIN类型的字符串表示
     *
     * @return JOIN类型字符串，用于SQL拼接
     */
    public String getJoinType() {
        return joinType;
    }
    
    /**
     * 获取JOIN类型的中文描述
     *
     * @return JOIN类型的中文描述
     */
    public String getDesc() {
        return desc;
    }
}
