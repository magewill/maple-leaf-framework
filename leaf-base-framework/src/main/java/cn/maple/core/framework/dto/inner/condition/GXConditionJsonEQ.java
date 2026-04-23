package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.HashMap;
import java.util.Map;

public class GXConditionJsonEQ extends GXCondition<Object> {
    private final String jsonPath;

    public GXConditionJsonEQ(String tableNameAlias, String fieldName, String jsonFieldName, Object value) {
        super(tableNameAlias, fieldName, value);
        this.jsonPath = CharSequenceUtil.format("$.{}", jsonFieldName);
        this.paramMap.clear();
        this.paramMap.put(paramName, value);
        this.paramMap.put(paramName + "_path", jsonPath);
    }

    @Override
    public String getOp() {
        return "=";
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("JSON condition has SQL injection risk");
        }
        if (NumberUtil.isNumber(strValue)) {
            return strValue;
        }
        return CharSequenceUtil.format("'{}'", strValue);
    }

    @Override
    public String whereString() {
        if (value == null || GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("JSON condition has SQL injection risk");
        }
        String field = CharSequenceUtil.isEmpty(tableNameAlias)
                ? getFieldExpression()
                : CharSequenceUtil.format("{}.{}", tableNameAlias, getFieldExpression());
        return CharSequenceUtil.format("JSON_VALUE({}, #{dbQueryParamInnerDto.paramMap.{}}) {} #{dbQueryParamInnerDto.paramMap.{}}",
                field, paramName + "_path", getOp(), paramName);
    }

    @Override
    public Object getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }
        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("JSON condition has SQL injection risk");
        }
        if (NumberUtil.isNumber(strValue)) {
            return strValue;
        }
        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);
        return CharSequenceUtil.format("'{}'", escapedValue);
    }

    public String getJsonPath() {
        return jsonPath;
    }

    @Override
    public GXConditionSegment toSegment() {
        if (value == null || GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("JSON condition has SQL injection risk");
        }
        String sql = whereString();
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, value);
        params.put(paramName + "_path", jsonPath);
        return new GXConditionSegment(sql, params);
    }
}