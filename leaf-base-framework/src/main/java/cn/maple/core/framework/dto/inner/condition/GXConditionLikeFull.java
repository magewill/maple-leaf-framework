package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.HashMap;
import java.util.Map;

public class GXConditionLikeFull extends GXCondition<String> {
    public GXConditionLikeFull(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "like";
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            throw new GXBusinessException("LIKE condition value must not be null");
        }
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL injection risk detected in LIKE condition value");
        }
        this.paramMap.clear();
        this.paramMap.put(paramName, "%" + value + "%");
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }
        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL injection risk detected in LIKE condition value: " + strValue);
        }
        String escapedValue = GXDBStringEscapeUtils.escapeSqlForLike(strValue);
        if (CharSequenceUtil.contains(escapedValue, "''")) {
            return CharSequenceUtil.format("\"%{}%\"", escapedValue);
        } else {
            return CharSequenceUtil.format("'%{}%'", escapedValue);
        }
    }

    @Override
    public GXConditionSegment toSegment() {
        if (value == null) {
            throw new GXBusinessException("LIKE condition value must not be null");
        }
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL injection risk detected in LIKE condition value");
        }
        String sql = CharSequenceUtil.isEmpty(tableNameAlias)
                ? CharSequenceUtil.format("{} {} #{dbQueryParamInnerDto.paramMap.{}}", getFieldExpression(), getOp(), paramName)
                : CharSequenceUtil.format("{}.{} {} #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, getFieldExpression(), getOp(), paramName);
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, "%" + value + "%");
        return new GXConditionSegment(sql, params);
    }
}
