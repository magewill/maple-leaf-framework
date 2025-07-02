package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionStrNE extends GXCondition<String> {
    public GXConditionStrNE(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "!=";
    }

    @Override
    public String getFieldValue() {
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        // 清除原参数映射并添加带通配符的参数
        this.paramMap.clear();
        this.paramMap.put(paramName, value);
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }

        String strValue = value.toString();
        // 首先检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }

        // 使用escapeSql方法进行更全面的SQL转义，而不是仅使用escapeRawString
        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);

        // 根据内容选择合适的引号包裹方式
        if (CharSequenceUtil.contains(escapedValue, "''")) {
            // 如果包含已转义的单引号，使用双引号包裹
            return CharSequenceUtil.format("\"{}\"", escapedValue);
        } else {
            // 否则使用单引号包裹
            return CharSequenceUtil.format("'{}'", escapedValue);
        }
    }
}
