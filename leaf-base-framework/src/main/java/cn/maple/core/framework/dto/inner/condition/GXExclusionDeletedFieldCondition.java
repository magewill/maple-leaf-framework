package cn.maple.core.framework.dto.inner.condition;

/**
 * 排除已删除字段的条件类
 * <p>
 * 该类用于在查询中排除已被标记为删除的数据。在实现软删除功能的系统中，
 * 数据通常不会被物理删除，而是通过标记字段来表示删除状态。此条件类用于构建
 * 排除这些已标记为删除数据的查询条件。
 * </p>
 *
 * <p><strong>核心功能：</strong></p>
 * <ul>
 *   <li>作为特殊标记条件，指示SQL生成器添加软删除过滤条件</li>
 *   <li>支持字符串和Long类型的删除标记值</li>
 *   <li>可以指定特定表的软删除字段和未删除状态值</li>
 *   <li>与其他查询条件可以组合使用</li>
 * </ul>
 *
 * <p><strong>安全特性：</strong></p>
 * <ul>
 *   <li>继承自GXCondition，享有基类的参数化查询安全机制</li>
 *   <li>通过特殊处理机制，确保在SQL构建过程中被正确识别和处理</li>
 *   <li>支持不同数据类型的删除标记值，适应各种数据库设计</li>
 * </ul>
 *
 * <p><strong>使用示例：</strong></p>
 * <pre>
 * // 示例1：使用默认构造函数创建排除删除数据的条件
 * GXCondition<?> excludeDeletedCondition = new GXExclusionDeletedFieldCondition();
 * conditionList.add(excludeDeletedCondition);
 *
 * // 示例2：指定表和字段名创建排除条件
 * GXCondition<?> tableExcludeCondition = new GXExclusionDeletedFieldCondition("user_table", "is_deleted", "0");
 * conditionList.add(tableExcludeCondition);
 *
 * // 示例3：使用Long类型的值
 * GXCondition<?> longValueCondition = new GXExclusionDeletedFieldCondition("user_table", "is_deleted", 0L);
 * conditionList.add(longValueCondition);
 *
 * // 示例4：在复杂查询中结合其他条件使用
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("user", "status", 1));
 * conditions.add(new GXExclusionDeletedFieldCondition());
 * // 此查询将返回所有状态为1且未被标记为删除的用户记录
 * </pre>
 *
 * <p><strong>性能与最佳实践：</strong></p>
 * <ul>
 *   <li>此条件通常应该在所有查询中默认添加，以避免查询到已删除的数据</li>
 *   <li>在需要查询包括已删除数据在内的所有数据时，可以不添加此条件</li>
 *   <li>对于频繁查询的表，建议在软删除标记字段上创建索引，提高查询性能</li>
 *   <li>在设计数据库时，应统一软删除字段的命名和值表示方式，便于条件的统一处理</li>
 * </ul>
 *
 * <p><strong>线程安全性：</strong></p>
 * <ul>
 *   <li>此类是线程安全的，不包含可变状态</li>
 *   <li>可以安全地在多线程环境中使用</li>
 *   <li>适合在并发查询场景中使用</li>
 * </ul>
 *
 * <p>
 * 注意：此类的getOp()和getFieldValue()方法返回null，表示这是一个特殊的标记条件，
 * 需要在SQL构建过程中特殊处理。实际的条件构建逻辑应在SQL生成器中实现。
 * </p>
 *
 * @author 塵子曦
 */
/**
 * 排除已删除字段的条件类
 * <p>
 * 该类作为特殊条件标记，指示SQL生成器在构建查询时添加排除已删除数据的过滤条件。
 * 通常用于实现软删除功能，确保查询结果中不包含已标记为删除的数据。
 * </p>
 */
public class GXExclusionDeletedFieldCondition extends GXCondition<String> {
    /**
     * 带参数构造函数（字符串值）
     * <p>
     * 创建一个指定表别名、字段名和字符串值的排除已删除数据条件。
     * 适用于使用字符串类型标记删除状态的场景，如 "0"表示未删除，"1"表示已删除。
     * </p>
     *
     * @param tableNameAlias 表别名，可以为空字符串
     * @param fieldName      软删除标记字段名，如"is_deleted"、"deleted_flag"等
     * @param value          未删除状态的值，通常为"0"或"N"等
     */
    public GXExclusionDeletedFieldCondition(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 带参数构造函数（Long值）
     * <p>
     * 创建一个指定表别名、字段名和Long值的排除已删除数据条件。
     * 适用于使用数值类型标记删除状态的场景，如0表示未删除，1表示已删除。
     * </p>
     *
     * @param tableNameAlias 表别名，可以为空字符串
     * @param fieldName      软删除标记字段名，如"is_deleted"、"deleted_flag"等
     * @param value          未删除状态的值，通常为0L
     */
    public GXExclusionDeletedFieldCondition(String tableNameAlias, String fieldName, Long value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 无参构造函数
     * <p>
     * 创建一个默认的排除已删除数据条件。
     * 这是最常用的构造方式，适用于使用系统默认的软删除字段和值进行过滤的场景。
     * SQL生成器会根据系统配置自动添加适当的过滤条件。
     * </p>
     */
    public GXExclusionDeletedFieldCondition() {
        this("", "", "");
    }

    /**
     * 获取操作符
     * <p>
     * 返回null表示这是一个特殊的标记条件，需要在SQL生成器中特殊处理。
     * SQL生成器会根据此返回值识别出这是一个排除已删除数据的条件，并添加相应的过滤语句。
     * </p>
     *
     * @return null，表示需要特殊处理
     */
    @Override
    public String getOp() {
        return null;
    }

    /**
     * 获取字段值
     * <p>
     * 返回null表示这是一个特殊的标记条件，具体的字段值会在SQL生成器中根据系统配置决定。
     * 在实际的SQL生成过程中，会根据系统配置的软删除字段和未删除状态值来构建过滤条件。
     * </p>
     *
     * @return null，表示需要特殊处理
     */
    @Override
    public String getFieldValue() {
        return null;
    }
}
