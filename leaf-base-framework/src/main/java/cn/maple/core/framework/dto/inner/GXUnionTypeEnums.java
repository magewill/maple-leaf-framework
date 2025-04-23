package cn.maple.core.framework.dto.inner;

/**
 * SQL UNION类型枚举
 * <p>
 * 该枚举定义了SQL查询中可用的UNION类型，包括UNION和UNION ALL。
 * 用于构建复合查询时指定结果集合并的方式。
 * </p>
 * 
 * <p>
 * UNION与UNION ALL的区别：
 * - UNION: 合并结果集并去除重复行
 * - UNION ALL: 合并结果集但保留重复行，性能通常优于UNION
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建第一个查询条件
 * GXBaseQueryParamInnerDto query1 = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .addCondition(new GXConditionLike("", "username", "张%"))
 *     .build();
 * 
 * // 2. 创建第二个查询条件
 * GXBaseQueryParamInnerDto query2 = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .addCondition(new GXConditionLike("", "email", "%@example.com"))
 *     .build();
 * 
 * // 3. 创建主查询条件
 * GXBaseQueryParamInnerDto masterQuery = GXBaseQueryParamInnerDto.builder()
 *     .addCondition(new GXConditionEQ("tmp", "status", 1))
 *     .build();
 * 
 * // 4. 执行UNION查询
 * List<GXBaseQueryParamInnerDto> unionQueries = Arrays.asList(query1, query2);
 * String sql = GXBuildRawSql.unionFindByCondition(masterQuery, unionQueries, GXUnionTypeEnums.UNION);
 * // 生成SQL: SELECT tmp.* FROM (SELECT user.* FROM user WHERE username LIKE '张%' UNION SELECT user.* FROM user WHERE email LIKE '%@example.com') tmp WHERE tmp.status = 1
 * </pre>
 * </p>
 *
 * @author magleton
 */
public enum GXUnionTypeEnums {
    /**
     * UNION ALL操作符
     * 合并两个或多个SELECT语句的结果集，保留重复行
     * 性能通常优于UNION，因为不需要额外的排序和去重操作
     */
    UNION_ALL("union all", "UNION ALL"),
    
    /**
     * UNION操作符
     * 合并两个或多个SELECT语句的结果集，自动去除重复行
     * 需要额外的排序和去重操作，性能可能低于UNION ALL
     */
    UNION("union", "UNION");
    
    /**
     * UNION类型的SQL关键字
     */
    private final String unionType;
    
    /**
     * UNION类型的描述
     */
    private final String desc;

    /**
     * 构造函数
     *
     * @param unionType UNION类型的SQL关键字
     * @param desc      UNION类型的描述
     */
    GXUnionTypeEnums(String unionType, String desc) {
        this.unionType = unionType;
        this.desc = desc;
    }

    /**
     * 获取UNION类型的SQL关键字
     *
     * @return UNION类型字符串，用于SQL拼接
     */
    public String getUnionType() {
        return unionType;
    }
    
    /**
     * 获取UNION类型的描述
     *
     * @return UNION类型的描述
     */
    public String getDesc() {
        return desc;
    }
}
