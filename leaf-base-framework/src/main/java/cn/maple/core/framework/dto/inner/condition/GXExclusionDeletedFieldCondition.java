package cn.maple.core.framework.dto.inner.condition;

/**
 * 排除已删除字段的条件类
 * <p>
 * 该类用于在查询中排除已被标记为删除的数据。在实现软删除功能的系统中，
 * 数据通常不会被物理删除，而是通过标记字段来表示删除状态。此条件类用于构建
 * 排除这些已标记为删除数据的查询条件。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：使用默认构造函数创建排除删除数据的条件
 * GXCondition<?> excludeDeletedCondition = new GXConditionExclusionDeletedField();
 * conditionList.add(excludeDeletedCondition);
 * 
 * // 示例2：指定表和字段名创建排除条件
 * GXCondition<?> tableExcludeCondition = new GXConditionExclusionDeletedField("user_table", "is_deleted", "0");
 * conditionList.add(tableExcludeCondition);
 * 
 * // 示例3：使用Long类型的值
 * GXCondition<?> longValueCondition = new GXConditionExclusionDeletedField("user_table", "is_deleted", 0L);
 * conditionList.add(longValueCondition);
 * </pre>
 * 
 * <p>
 * 注意：此类的getOp()和getFieldValue()方法返回null，表示这是一个特殊的标记条件，
 * 需要在SQL构建过程中特殊处理。实际的条件构建逻辑应在SQL生成器中实现。
 * </p>
 * 
 * @author 塵子曦
 */
public class GXExclusionDeletedFieldCondition extends GXCondition<String> {
    /**
     * 带字符串值的构造函数
     * 
     * @param tableNameAlias 表名别名
     * @param fieldName 表示删除状态的字段名
     * @param value 表示未删除状态的值（字符串类型）
     */
    public GXExclusionDeletedFieldCondition(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 带Long值的构造函数
     * 
     * @param tableNameAlias 表名别名
     * @param fieldName 表示删除状态的字段名
     * @param value 表示未删除状态的值（Long类型）
     */
    public GXExclusionDeletedFieldCondition(String tableNameAlias, String fieldName, Long value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 无参构造函数
     * 创建一个默认的排除已删除字段条件，所有参数均为空字符串
     */
    public GXExclusionDeletedFieldCondition() {
        this("", "", "");
    }

    /**
     * 获取操作符
     * 由于这是一个特殊的标记条件，返回null，实际的条件构建逻辑应在SQL生成器中实现
     * 
     * @return null
     */
    @Override
    public String getOp() {
        return null;
    }

    /**
     * 获取字段值
     * 由于这是一个特殊的标记条件，返回null，实际的条件构建逻辑应在SQL生成器中实现
     * 
     * @return null
     */
    @Override
    public String getFieldValue() {
        return null;
    }
}
