package cn.maple.core.framework.dto.inner.field;

/**
 * 数值类型字段更新类
 * <p>
 * 该类用于处理数值类型字段的更新操作，支持整数、浮点数等各种数值类型。
 * 通过参数化查询机制确保数值类型安全地传递到SQL语句中。
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 使用参数化查询(#{})防止SQL注入
 * - 自动类型转换，确保数值类型正确性
 * - 避免浮点数精度问题
 * - 统一处理各种数值类型(Integer, Long, Double等)
 * </p>
 * 
 * <p>
 * 使用场景：
 * - 更新商品价格、库存等数值信息
 * - 更新计数器、统计数据等
 * - 更新用户积分、余额等账户数据
 * - 更新配置参数中的数值设置
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 更新商品价格
 * GXUpdateField<?> priceField = new GXUpdateNumberField("product", "price", 99.99);
 * 
 * // 2. 更新库存数量
 * GXUpdateField<?> stockField = new GXUpdateNumberField("product", "stock", 100);
 * 
 * // 3. 将字段添加到更新列表
 * List<GXUpdateField<?>> updateFields = Arrays.asList(priceField, stockField);
 * 
 * // 4. 创建更新条件
 * List<GXCondition<?>> conditions = Arrays.asList(
 *     new GXConditionEQ("product", "id", 100)
 * );
 * 
 * // 5. 执行更新操作
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("product")
 *     .condition(conditions)
 *     .build();
 * String sql = GXBaseBuilder.updateFieldByCondition(queryParam, updateFields);
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * - 对于精度要求高的数值（如金额），建议使用BigDecimal类型
 * - 更新数值字段时，注意数据库字段类型与Java类型的匹配
 * - 当需要进行数值计算时，可以结合数据库函数使用
 * </p>
 */
public class GXUpdateNumberField extends GXUpdateField<Number> {
    /**
     * 构造函数 - 整数类型
     *
     * @param tableNameAlias 表名或表别名，用于SQL生成时指定表
     * @param fieldName      字段名，数值类型的字段名
     * @param numberValue    整数值
     */
    public GXUpdateNumberField(String tableNameAlias, String fieldName, int numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }
    
    /**
     * 构造函数 - 长整数类型
     *
     * @param tableNameAlias 表名或表别名，用于SQL生成时指定表
     * @param fieldName      字段名，数值类型的字段名
     * @param numberValue    长整数值
     */
    public GXUpdateNumberField(String tableNameAlias, String fieldName, long numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }
    
    /**
     * 构造函数 - 浮点数类型
     *
     * @param tableNameAlias 表名或表别名，用于SQL生成时指定表
     * @param fieldName      字段名，数值类型的字段名
     * @param numberValue    浮点数值
     */
    public GXUpdateNumberField(String tableNameAlias, String fieldName, double numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }
    
    /**
     * 构造函数 - 通用数值类型
     *
     * @param tableNameAlias 表名或表别名，用于SQL生成时指定表
     * @param fieldName      字段名，数值类型的字段名
     * @param numberValue    数值，可以是任何Number类型的值(Integer, Long, Double, BigDecimal等)
     */
    public GXUpdateNumberField(String tableNameAlias, String fieldName, Number numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }

    /**
     * 获取字段值
     * 
     * @return 数值类型的字段值
     */
    @Override
    public Number getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        // 在参数化查询中，值会通过paramMap传递给MyBatis
        return (Number) value;
    }
    
    /**
     * 生成更新数值字段的SQL片段
     * 使用参数化查询确保数值安全传递，防止SQL注入
     *
     * @return 格式化的SQL更新语句片段
     */
    @Override
    public String updateString() {
        // 使用父类的默认实现，通过参数化查询防止SQL注入
        return super.updateString();
    }
}
