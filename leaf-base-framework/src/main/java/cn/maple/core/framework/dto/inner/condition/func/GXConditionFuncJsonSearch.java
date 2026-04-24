package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionFuncJsonSearch extends GXConditionFunc<String> {
    private final String jsonField;
    private final String value;
    private final String oneOrAll;

    public GXConditionFuncJsonSearch(String tableNameAlias, String field, String value) {
        this(tableNameAlias, field, value, GXBuilderConstant.JSON_SEARCH_FUNC_ONE);
    }

    public GXConditionFuncJsonSearch(String tableNameAlias, String field, String value, String oneOrAll) {
        super(tableNameAlias, field, value);
        this.jsonField = field;
        this.value = value;
        String normalizedMode = CharSequenceUtil.emptyIfNull(oneOrAll).trim().toLowerCase();
        if (CharSequenceUtil.isBlank(normalizedMode)) {
            normalizedMode = GXBuilderConstant.JSON_SEARCH_FUNC_ONE;
        }
        if (!GXBuilderConstant.JSON_SEARCH_FUNC_ONE.equals(normalizedMode)
                && !GXBuilderConstant.JSON_SEARCH_FUNC_ALL.equals(normalizedMode)) {
            throw new GXBusinessException("JSON_SEARCH mode must be 'one' or 'all'");
        }
        this.oneOrAll = normalizedMode;
    }

    @Override
    public String getOp() {
        return jsonField;
    }

    @Override
    public String getFieldExpression() {
        return CharSequenceUtil.format("`{}`.`{}`", tableNameAlias, jsonField);
    }

    @Override
    public String getFieldValue() {
        this.paramMap.put(paramName, value);
        return value;
    }

    @Override
    protected String getFunctionName() {
        return "JSON_SEARCH";
    }

    @Override
    public String whereString() {
        this.paramMap.clear();
        this.paramMap.put(paramName + "_oneOrAll", oneOrAll);
        this.paramMap.put(paramName, value);
        return CharSequenceUtil.format("{}({}, #{dbQueryParamInnerDto.paramMap.{}_oneOrAll}, #{dbQueryParamInnerDto.paramMap.{}})",
                getFunctionName(), getFieldExpression(), paramName, paramName);
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }
        if (GXDBStringEscapeUtils.check(value)) {
            throw new GXSqlInjectionException("SQL injection risk detected in JSON_SEARCH condition value");
        }
        return CharSequenceUtil.format("'{}'", GXDBStringEscapeUtils.escapeSql(value));
    }
}
