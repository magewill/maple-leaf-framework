package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionStrEQ extends GXCondition<String> {
    public GXConditionStrEQ(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "=";
    }

    @Override
    public String getFieldValue() {
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
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
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("字符串等值条件中检测到SQL注入风险: " + strValue);
        }

        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);

        if (CharSequenceUtil.contains(escapedValue, "''")) {
            return CharSequenceUtil.format("\"{}\"", escapedValue);
        } else {
            return CharSequenceUtil.format("'{}'", escapedValue);
        }
    }
}
