package cn.maple.core.datasource.dto;

/**
 * 数据范围过滤DTO
 * <p>
 * 该类用于封装SQL过滤条件，主要应用于数据权限控制场景。
 * 通过ThreadLocal机制与GXDataFilterThreadLocalUtils配合使用，
 * 实现在同一线程中传递SQL过滤条件。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 创建过滤条件
 * GXDataFilterInnerDto filterDto = new GXDataFilterInnerDto("shop_id = 1 AND status = 'active'");
 *
 * // 获取SQL过滤条件（会自动添加特殊标记$$''$$）
 * String sqlFilter = filterDto.getSqlFilter(); // 返回: $$'shop_id = 1 AND status = 'active'$$
 * </pre>
 * </p>
 *
 * @author 塵渊 britton@126.com
 */
public class GXDataFilterInnerDto {
    /**
     * SQL过滤条件字符串
     */
    private String sqlFilter;

    /**
     * 构造函数
     *
     * @param sqlFilter SQL过滤条件字符串
     */
    public GXDataFilterInnerDto(String sqlFilter) {
        this.sqlFilter = sqlFilter;
    }

    /**
     * 获取SQL过滤条件
     * 返回的SQL过滤条件会被特殊标记$$''$$包围，用于在SQL解析时识别
     *
     * @return 带特殊标记的SQL过滤条件
     */
    public String getSqlFilter() {
        return "$$'" + sqlFilter + "'$$";
    }

    /**
     * 设置SQL过滤条件
     *
     * @param sqlFilter SQL过滤条件字符串
     */
    public void setSqlFilter(String sqlFilter) {
        this.sqlFilter = sqlFilter;
    }

    /**
     * 重写toString方法，直接返回SQL过滤条件
     *
     * @return SQL过滤条件字符串
     */
    @Override
    public String toString() {
        return this.sqlFilter;
    }
}