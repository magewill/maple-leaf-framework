package cn.maple.core.framework.dto.inner.condition;

import cn.maple.core.framework.constant.GXDataSourceConstant;

/**
 * 忽略数据权限过滤条件类
 * <p>
 * 该类用于标记不需要进行数据权限验证的查询条件。在某些特殊场景下，
 * 我们可能需要绕过系统的数据权限过滤机制，直接查询所有数据，此时可以使用此条件类。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 示例1：在构建查询条件时添加忽略数据过滤标记
 * GXCondition<?> ignoreCondition = new GXIgnoreDataFilterCondition();
 * conditionList.add(ignoreCondition);
 * 
 * // 示例2：在特定表的查询中忽略数据过滤
 * GXCondition<?> tableIgnoreCondition = new GXIgnoreDataFilterCondition("user_table", "id", "");
 * conditionList.add(tableIgnoreCondition);
 * </pre>
 * 
 * @author 塵子曦
 */
public class GXIgnoreDataFilterCondition extends GXCondition<String> {
    /**
     * 带参数构造函数
     * 
     * @param tableNameAlias 表名别名，可以为空字符串
     * @param fieldName 字段名，可以为空字符串
     * @param value 字段值，通常为空字符串，因为此类主要用作标记
     */
    public GXIgnoreDataFilterCondition(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 无参构造函数
     * 创建一个默认的忽略数据过滤条件，所有参数均为空字符串
     */
    public GXIgnoreDataFilterCondition() {
        this("", "", "");
    }

    /**
     * 获取操作符
     * 返回一个特殊的操作符值，用于在SQL构建过程中标识此条件为忽略数据过滤条件
     * 
     * @return 特殊的操作符值，由GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE定义
     */
    @Override
    public String getOp() {
        return GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE;
    }

    /**
     * 获取字段值
     * 由于此类主要用作标记，实际不需要字段值，因此返回空字符串
     * 
     * @return 空字符串
     */
    @Override
    public String getFieldValue() {
        return "";
    }
}
