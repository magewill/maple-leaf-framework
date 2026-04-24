package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionFuncConcat extends GXConditionFunc<String> {
    public GXConditionFuncConcat(String tableNameAlias, String op, String value, Object... fieldNames) {
        super(tableNameAlias, op, value, fieldNames);
    }

    @Override
    public String getOp() {
        return op;
    }

    @Override
    public String getFieldValue() {
        String raw = value == null ? "" : value.toString();
        if (GXDBStringEscapeUtils.check(raw)) {
            throw new GXSqlInjectionException("SQL injection risk detected in CONCAT condition value");
        }
        this.paramMap.put(paramName, raw + "%");
        return raw + "%";
    }

    @Override
    protected String getFunctionName() {
        return "concat";
    }

    @Override
    public String whereString() {
        this.paramMap.clear();
        getFieldValue();
        return CharSequenceUtil.format("{}({}) LIKE #{{dbQueryParamInnerDto.paramMap.{}}}",
                getFunctionName(), getFieldExpression(), paramName);
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "'%'";
        }
        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL injection risk detected in CONCAT condition value");
        }
        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);
        return CharSequenceUtil.format("'{}%'", escapedValue);
    }
}
