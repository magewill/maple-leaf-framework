package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionJsonEQ extends GXCondition<String> {
    private final String jsonPath;

    public GXConditionJsonEQ(String tableNameAlias, String fieldName, String jsonFieldName, String value) {
        super(tableNameAlias, fieldName, value);
        this.jsonPath = CharSequenceUtil.format("$.{}", jsonFieldName);
        // 清除原参数映射并添加JSON路径参数
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
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        if (NumberUtil.isNumber(value.toString())) {
            return CharSequenceUtil.format("{}", value);
        }
        return CharSequenceUtil.format("'{}'", value);
    }

    @Override
    public String whereString() {
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }

        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("`{}`->#{dbQueryParamInnerDto.paramMap.{}} {} #{dbQueryParamInnerDto.paramMap.{}}",
                    fieldExpression, paramName + "_path", getOp(), paramName);
        }
        return CharSequenceUtil.format("{}.`{}`->#{dbQueryParamInnerDto.paramMap.{}} {} #{dbQueryParamInnerDto.paramMap.{}}",
                tableNameAlias, fieldExpression, paramName + "_path", getOp(), paramName);
    }
}
