package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

/**
 * 字符串类型字段更新类
 * <p>
 * 用于处理字符串类型字段的更新操作，提供SQL注入防护和适当的字符串转义。
 * 能够识别并正确处理JSON格式的字符串。
 * </p>
 *
 * @author 塵子曦
 */
public class GXUpdateStrField extends GXUpdateField<String> {
    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName 字段名，会自动转换为下划线格式
     * @param strValue 字符串值
     */
    public GXUpdateStrField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    /**
     * 获取格式化后的字段值
     * <p>
     * 根据字段值的类型进行不同的处理：
     * - null值：返回NULL关键字
     * - JSON格式：使用JSON专用转义并用单引号包裹
     * - 普通字符串：使用SQL字符串转义
     * </p>
     *
     * @return 格式化后的字段值，可直接用于SQL语句
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            // 发现SQL注入风险时抛出异常，而不是继续处理
            throw new GXSqlInjectionException("字符串字段更新时检测到SQL注入风险");
        }
        
        // 根据内容类型选择不同的转义方法
        if (JSONUtil.isTypeJSON(strValue)) {
            // JSON内容使用专门的JSON字符串SQL转义方法，避免双重转义
            // 使用CAST确保MySQL正确处理JSON格式
            return CharSequenceUtil.format("CAST('{}' as JSON)", GXDBStringEscapeUtils.escapeJsonForSql(strValue));
        } else {
            // 非JSON内容直接使用SQL字符串转义
            return GXDBStringEscapeUtils.escapeString(strValue);
        }
    }
}
