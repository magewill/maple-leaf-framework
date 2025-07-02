package cn.maple.core.framework.dto.inner.condition;

import cn.maple.core.framework.constant.GXDataSourceConstant;

/**
 * 忽略数据权限过滤条件类
 * <p>
 * 该类用于标记不需要进行数据权限验证的查询条件。在某些特殊场景下，
 * 我们可能需要绕过系统的数据权限过滤机制，直接查询所有数据，此时可以使用此条件类。
 * </p>
 *
 * <p><strong>核心功能：</strong></p>
 * <ul>
 *   <li>作为标记条件，指示SQL生成器忽略数据权限过滤</li>
 *   <li>支持全局忽略或针对特定表的忽略</li>
 *   <li>与其他条件可以组合使用</li>
 * </ul>
 *
 * <p><strong>安全特性：</strong></p>
 * <ul>
 *   <li>继承自GXCondition，享有基类的参数化查询安全机制</li>
 *   <li>使用特殊的操作符值，确保在SQL构建过程中被正确识别</li>
 *   <li>不直接暴露敏感数据，仅作为标记使用</li>
 * </ul>
 *
 * <p><strong>使用示例：</strong></p>
 * <pre>
 * // 示例1：在构建查询条件时添加忽略数据过滤标记
 * GXCondition<?> ignoreCondition = new GXIgnoreDataFilterCondition();
 * conditionList.add(ignoreCondition);
 *
 * // 示例2：在特定表的查询中忽略数据过滤
 * GXCondition<?> tableIgnoreCondition = new GXIgnoreDataFilterCondition("user_table", "id", "");
 * conditionList.add(tableIgnoreCondition);
 *
 * // 示例3：在复杂查询中结合其他条件使用
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("user", "status", 1));
 * conditions.add(new GXIgnoreDataFilterCondition());
 * // 此查询将返回所有状态为1的用户记录，不受数据权限限制
 * </pre>
 *
 * <p><strong>性能与最佳实践：</strong></p>
 * <ul>
 *   <li>谨慎使用此条件，因为它会绕过数据权限控制，可能导致敏感数据泄露</li>
 *   <li>建议在必要的业务场景（如管理员查看全局数据）中使用</li>
 *   <li>在多租户系统中使用时需特别小心，确保不会跨租户访问数据</li>
 *   <li>考虑结合日志记录，跟踪此类特权操作的使用情况</li>
 * </ul>
 *
 * <p><strong>线程安全性：</strong></p>
 * <ul>
 *   <li>该类是线程安全的，不包含可变状态</li>
 *   <li>可以安全地在多线程环境中使用</li>
 *   <li>继承自GXCondition类，使用AtomicLong生成唯一参数名，确保线程安全</li>
 * </ul>
 *
 * <p><strong>线程安全性：</strong></p>
 * <ul>
 *   <li>此类是线程安全的，不包含可变状态</li>
 *   <li>可以安全地在多线程环境中使用</li>
 * </ul>
 *
 * @author 塵子曦
 */

/**
 * 忽略数据权限过滤条件类
 * <p>
 * 该类作为特殊条件标记，指示SQL生成器在构建查询时忽略数据权限过滤规则。
 * 通常用于需要查询全部数据而不受数据权限限制的场景，如管理员查看全局数据。
 * </p>
 */
public class GXIgnoreDataFilterCondition extends GXCondition<String> {
    /**
     * 带参数构造函数
     * <p>
     * 创建一个指定表别名和字段名的忽略数据权限过滤条件。
     * 参数可以为空字符串，因为该类主要作为标记使用。
     * </p>
     *
     * @param tableNameAlias 表别名，可以为空字符串
     * @param fieldName      字段名，可以为空字符串
     * @param value          字段值，可以为空字符串
     */
    public GXIgnoreDataFilterCondition(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 无参构造函数
     * <p>
     * 创建一个默认的忽略数据权限过滤条件。
     * 这是最常用的构造方式，适用于全局忽略数据权限过滤的场景。
     * </p>
     */
    public GXIgnoreDataFilterCondition() {
        this("", "", "");
    }

    /**
     * 获取操作符
     * <p>
     * 返回特殊的操作符值，该值在SQL生成器中会被识别为忽略数据权限过滤的标记。
     * SQL生成器在处理条件时，会根据此值决定是否应用数据权限过滤规则。
     * </p>
     *
     * @return 特殊的操作符值常量
     */
    @Override
    public String getOp() {
        return GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE;
    }

    /**
     * 获取字段值
     * <p>
     * 由于该类主要用作标记，实际不需要字段值，因此返回空字符串。
     * 在SQL生成过程中，此方法的返回值通常不会被使用。
     * </p>
     *
     * @return 空字符串
     */
    @Override
    public String getFieldValue() {
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        return "";
    }
}
