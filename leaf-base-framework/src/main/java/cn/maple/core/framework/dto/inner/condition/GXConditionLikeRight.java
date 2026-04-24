package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.HashMap;
import java.util.Map;

public class GXConditionLikeRight extends GXCondition<String> {
    public GXConditionLikeRight(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "like";
    }

    @Override
    public String getFieldValue() {
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("检测到SQL注入风险：右模糊匹配条件含可疑内容");
        }
        this.paramMap.clear();
        this.paramMap.put(paramName, value + "%");
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }

        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }

        String escapedValue = GXDBStringEscapeUtils.escapeSqlForLike(strValue);
        if (CharSequenceUtil.contains(escapedValue, "''")) {
            return CharSequenceUtil.format("\"{}%\"", escapedValue);
        }
        return CharSequenceUtil.format("'{}%'", escapedValue);
    }

    @Override
    public GXConditionSegment toSegment() {
        if (value == null || GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("检测到SQL注入风险：右模糊匹配条件含可疑内容");
        }
        String sql = CharSequenceUtil.isEmpty(tableNameAlias)
                ? CharSequenceUtil.format("{} {} #{dbQueryParamInnerDto.paramMap.{}}", getFieldExpression(), getOp(), paramName)
                : CharSequenceUtil.format("{}.{} {} #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, getFieldExpression(), getOp(), paramName);
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, value + "%");
        return new GXConditionSegment(sql, params);
    }
}
