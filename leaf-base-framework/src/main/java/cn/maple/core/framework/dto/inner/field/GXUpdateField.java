package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.Getter;

import java.io.Serializable;

/**
 * 数据库字段更新抽象基类
 * <p>
 * 该类为所有数据库字段更新操作的基础类，提供了通用的字段更新功能。
 * 子类需要实现具体的字段值获取逻辑，确保不同类型的字段能够正确地转换为SQL语句。
 * </p>
 *
 * @param <T> 字段值的类型参数
 * @author 塵子曦
 */
public abstract class GXUpdateField<T> implements Serializable {
    /**
     * 表名别名，用于多表操作时指定表
     */
    protected String tableNameAlias;

    /**
     * 字段名，自动转换为下划线命名格式
     */
    @Getter
    protected String fieldName;

    /**
     * 字段值，类型由子类决定
     */
    @SuppressWarnings("all")
    protected Object value;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName      字段名，会自动转换为下划线格式
     * @param value          字段值
     */
    protected GXUpdateField(String tableNameAlias, String fieldName, Object value) {
        this.tableNameAlias = tableNameAlias;
        this.fieldName = CharSequenceUtil.toUnderlineCase(fieldName);
        this.value = value;
    }

    /**
     * 生成用于UPDATE语句的字段更新表达式
     * <p>
     * 根据是否有表名别名，生成不同格式的更新表达式：
     * - 有表名别名：table_alias.field_name = value
     * - 无表名别名：field_name = value
     * </p>
     * <p>
     * 该方法实现了多层次的SQL注入防护措施：
     * 1. 检查表名别名的安全性
     * 2. 检查字段名的安全性
     * 3. 字段值的安全性由各子类的getFieldValue方法负责
     * 4. 构建更新表达式后进行最终的安全验证
     * </p>
     *
     * @return 格式化的字段更新表达式
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public String updateString() {
        // 安全检查1：验证表名别名
        if (CharSequenceUtil.isNotEmpty(tableNameAlias)) {
            if (GXDBStringEscapeUtils.check(tableNameAlias)) {
                throw new GXSqlInjectionException("表名别名中包含SQL注入风险: " + tableNameAlias);
            }
        }

        // 安全检查2：验证字段名
        if (GXDBStringEscapeUtils.check(fieldName)) {
            throw new GXSqlInjectionException("字段名中包含SQL注入风险: " + fieldName);
        }

        // 获取字段值（由子类实现，应当包含安全检查）
        Object fieldVal = getFieldValue();

        // 构建更新表达式
        String updateExpr;
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            updateExpr = CharSequenceUtil.format("{} = {}", fieldName, fieldVal);
        } else {
            updateExpr = CharSequenceUtil.format("{}.{} = {}", tableNameAlias, fieldName, fieldVal);
        }

        // 安全检查3：最终验证生成的完整更新表达式
        if (updateExpr != null && GXDBStringEscapeUtils.check(updateExpr)) {
            throw new GXSqlInjectionException("生成的更新表达式中包含SQL注入风险: " + updateExpr);
        }

        return updateExpr;
    }

    /**
     * 获取字段值的抽象方法
     * <p>
     * 子类必须实现此方法，提供适合SQL语句的字段值表示。
     * 实现时应当注意SQL注入防护和类型转换。
     * </p>
     *
     * @return 格式化后的字段值，可直接用于SQL语句
     */
    public abstract T getFieldValue();
}