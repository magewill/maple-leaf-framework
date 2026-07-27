package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringUtils;

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
        if (value == null) {
            throw new GXBusinessException("String condition value must not be null");
        }
        if (GXDBStringUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL injection risk detected in string condition value");
        }
        this.paramMap.clear();
        this.paramMap.put(paramName, value);
        return (String) value;
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }

        String strValue = value.toString();
        if (GXDBStringUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL injection risk detected in string condition value");
        }

        String escapedValue = GXDBStringUtils.escapeSql(strValue);

        if (CharSequenceUtil.contains(escapedValue, "''")) {
            return CharSequenceUtil.format("\"{}\"", escapedValue);
        } else {
            return CharSequenceUtil.format("'{}'", escapedValue);
        }
    }
}
