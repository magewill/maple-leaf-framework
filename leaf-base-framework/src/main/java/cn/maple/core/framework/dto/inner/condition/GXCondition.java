package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 数据库查询条件抽象基类
 * <p>
 * 该类为所有数据库查询条件的基础类，提供了通用的条件构建功能。
 * 子类需要实现具体的操作符和字段值获取逻辑，确保不同类型的条件能够正确地转换为SQL语句。
 * 所有子类都应当确保生成的SQL条件安全可靠，防止SQL注入攻击。
 * </p>
 *
 * @param <T> 条件值的类型参数
 * @author 塵子曦
 */
public abstract class GXCondition<T> implements Serializable {
    /**
     * 字段表达式
     * <p>
     * 可以是一个具体的字段名字，例如：goods_name
     * 也可以是一个函数表达式，例如：concat(g_goods.goods_name, '-', g_goods.goods_sn)
     * </p>
     */
    protected final String fieldExpression;

    /**
     * 表名别名，用于多表操作时指定表
     */
    @Setter
    @Getter
    protected String tableNameAlias;

    /**
     * 条件值，类型由子类决定
     */
    @SuppressWarnings("all")
    @Getter
    protected Object value;

    /**
     * 构造函数（无表名别名）
     *
     * @param fieldExpression 字段表达式
     * @param value 条件值
     */
    protected GXCondition(String fieldExpression, Object value) {
        this("", fieldExpression, value);
    }

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldExpression 字段表达式
     * @param value 条件值
     */
    protected GXCondition(String tableNameAlias, String fieldExpression, Object value) {
        this.tableNameAlias = tableNameAlias;
        this.fieldExpression = fieldExpression;
        this.value = value;
    }

    /**
     * 获取操作符的抽象方法
     * <p>
     * 子类必须实现此方法，提供适合SQL语句的操作符表示。
     * 例如：=, >, <, LIKE, IN 等
     * </p>
     *
     * @return SQL操作符
     */
    public abstract String getOp();

    /**
     * 生成用于WHERE子句的条件表达式
     * <p>
     * 根据是否有表名别名，生成不同格式的条件表达式：
     * - 有表名别名：table_alias.field_expression op field_value
     * - 无表名别名：field_expression op field_value
     * </p>
     * <p>
     * 该方法实现了多层次的SQL注入防护措施：
     * 1. 检查操作符是否为空或特殊值
     * 2. 分别验证字段表达式、操作符和字段值的安全性
     * 3. 构建条件表达式后进行最终的安全验证
     * </p>
     *
     * @return 格式化的WHERE条件表达式
     * @throws GXSqlInjectionException 如果检测到潜在的SQL注入攻击
     */
    public String whereString() {
        // 获取操作符并检查是否为空或特殊值
        String opStr = getOp();
        // 如果操作符为空或等于忽略数据过滤条件的特殊值，则返回空字符串
        // 这允许某些条件被标记为不参与WHERE子句的构建
        if (CharSequenceUtil.isEmpty(opStr) || CharSequenceUtil.equals(opStr, GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE)) {
            return "";
        }
        
        // 获取字段表达式和字段值
        String fieldExpr = getFieldExpression();
        Object fieldVal = getFieldValue();
        
        // 安全检查1：验证字段表达式
        if (fieldExpr != null) {
            // 首先使用GXDBStringEscapeUtils.check进行全面的SQL注入风险检测
            // 这会检查常见的SQL注入模式，如SQL注释、联合查询等
            if (GXDBStringEscapeUtils.check(fieldExpr)) {
                throw new GXSqlInjectionException("字段表达式中包含SQL注入风险: " + fieldExpr);
            }
            
            // 验证字段名格式是否符合安全标准（可选的额外检查）
            // 允许字母、数字、下划线、点、括号、空格和常见SQL函数字符
            // 这个正则表达式支持常见的SQL函数表达式，如：
            // - 字符串函数: concat(field1, '-', field2)
            // - 日期函数: date_format(created_at, '%Y-%m-%d')
            // - JSON函数: json_extract(data, '$.name'), json_contains()
            // - 聚合函数: group_concat(field SEPARATOR ','), sum(), avg()
            // - 类型转换: cast(field as type), convert(expr using charset)
            // - 数学函数: round(), floor(), ceil()
            // - 条件函数: if(), case when ... then ... end
            // 注意：此正则表达式可能需要根据实际使用的SQL函数进行调整
            if (!fieldExpr.matches("^[a-zA-Z0-9_\\.()(\\s),\\-\\+\\*\\/\\%\\[\\]'\\$\\{\\}:]+$")) {
                throw new GXSqlInjectionException("字段表达式包含不安全的字符: " + fieldExpr);
            }
        }
        
        // 安全检查2：验证操作符
        if (opStr != null) {
            // 首先使用GXDBStringEscapeUtils.check进行全面的SQL注入风险检测
            // 这会检查操作符中是否包含SQL注入攻击模式
            if (GXDBStringEscapeUtils.check(opStr)) {
                throw new GXSqlInjectionException("操作符中包含SQL注入风险: " + opStr);
            }
            
            // 验证操作符是否为已知的安全操作符（白名单检查）
            // 支持所有标准SQL比较操作符和常见的条件操作符
            // 包括：
            // - 比较操作符: =, !=, <>, >, <, >=, <= 
            // - 模糊匹配: LIKE, NOT LIKE
            // - 集合操作: IN, NOT IN
            // - NULL值检查: IS, IS NOT
            // - 范围检查: BETWEEN, NOT BETWEEN
            // - 空白字符（用于特殊条件）
            // 注意：如果需要支持其他操作符，请在此正则表达式中添加
            // 使用(?i)前缀使正则表达式不区分大小写
            if (!opStr.matches("(?i)^(=|!=|<>|>|<|>=|<=|LIKE|NOT\\s+LIKE|IN|NOT\\s+IN|IS|IS\\s+NOT|BETWEEN|NOT\\s+BETWEEN|\\s*)$")) {
                throw new GXSqlInjectionException("不支持的操作符: " + opStr);
            }
        }
        
        // 安全检查3：字段值的安全性由各子类的getFieldValue方法负责
        // 此处不再重复检查，但确保fieldVal不为null
        if (fieldVal == null) {
            fieldVal = "NULL";
        }
        
        // 构建条件表达式
        String whereExpr;
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            whereExpr = CharSequenceUtil.format("{} {} {}", fieldExpr, opStr, fieldVal);
        } else {
            // 安全检查4：验证表名别名
            if (GXDBStringEscapeUtils.check(tableNameAlias)) {
                throw new GXSqlInjectionException("表名别名中包含SQL注入风险: " + tableNameAlias);
            }
            whereExpr = CharSequenceUtil.format("{}.{} {} {}", tableNameAlias, fieldExpr, opStr, fieldVal);
        }
        
        // 安全检查5：最终验证生成的完整条件表达式
        // 注意：此检查可能会对已经通过前面安全检查的表达式进行重复检查
        // 在某些复杂的SQL函数调用场景下，可能会误报SQL注入风险
        // 例如：GROUP_CONCAT、JSON_ARRAYAGG等聚合函数
        // 如果确认前面的安全检查已经足够严格，可以考虑在特定场景下放宽此检查
        if (whereExpr != null && GXDBStringEscapeUtils.check(whereExpr)) {
            throw new GXSqlInjectionException("生成的条件表达式中包含SQL注入风险: " + whereExpr);
        }
        
        return whereExpr;
    }

    /**
     * 获取格式化后的字段表达式
     * <p>
     * 将字段表达式转换为下划线命名格式。
     * </p>
     *
     * @return 格式化后的字段表达式
     */
    public String getFieldExpression() {
        return CharSequenceUtil.toUnderlineCase(fieldExpression);
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
