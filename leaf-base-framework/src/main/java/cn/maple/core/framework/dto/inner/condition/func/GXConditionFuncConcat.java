package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringUtils;

import java.util.Map;

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
        if (GXDBStringUtils.check(raw)) {
            throw new GXSqlInjectionException("SQL injection risk detected in CONCAT condition value");
        }
        return raw + "%";
    }

    @Override
    protected String getFunctionName() {
        return "concat";
    }

    @Override
    public String whereString() {
        return toSegment().sql();
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(
                CharSequenceUtil.format("{}({}) LIKE #{dbQueryParamInnerDto.paramMap.{}}",
                        getFunctionName(), getFieldExpression(), paramName),
                Map.of(paramName, getFieldValue()));
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "'%'";
        }
        String strValue = value.toString();
        if (GXDBStringUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL injection risk detected in CONCAT condition value");
        }
        String escapedValue = GXDBStringUtils.escapeSql(strValue);
        return CharSequenceUtil.format("'{}%'", escapedValue);
    }
}
