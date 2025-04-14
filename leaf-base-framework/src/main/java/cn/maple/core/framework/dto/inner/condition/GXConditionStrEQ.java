package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionStrEQ extends GXCondition<String> {
    public GXConditionStrEQ(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
        // 检查SQL注入
        if (GXDBStringEscapeUtils.check(value)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
    }

    @Override
    public String getOp() {
        return "=";
    }
    
    @Override
    public String whereString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} #{dbQueryParamInnerDto.paramMap.{}}", getFieldExpression(), getOp(), paramName);
        }
        return CharSequenceUtil.format("{}.{} {} #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, getFieldExpression(), getOp(), paramName);
    }

    @Override
    public String getFieldValue() {
        // 此方法不再使用，但为了兼容性保留
        return "";
    }
}
