package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringUtils;

import java.util.HashMap;
import java.util.Map;

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
        return GXConditionFuncDialectSupport.safeColumn(jsonField);
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            throw new GXBusinessException("JSON_SEARCH value must not be null");
        }
        if (GXDBStringUtils.check(value)) {
            throw new GXSqlInjectionException("SQL injection risk detected in JSON_SEARCH condition value");
        }
        return value;
    }

    @Override
    protected String getFunctionName() {
        return "JSON_SEARCH";
    }

    @Override
    public String whereString() {
        return renderSql();
    }

    @Override
    public GXConditionSegment toSegment() {
        String oneOrAllParamName = paramName + "_oneOrAll";
        String field = GXConditionFuncDialectSupport.qualifiedColumn(tableNameAlias, jsonField);
        Map<String, Object> params = new HashMap<>();
        params.put(oneOrAllParamName, oneOrAll);
        params.put(paramName, getFieldValue());
        return new GXConditionSegment(renderSql(field, oneOrAllParamName), params);
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }
        if (GXDBStringUtils.check(value)) {
            throw new GXSqlInjectionException("SQL injection risk detected in JSON_SEARCH condition value");
        }
        return CharSequenceUtil.format("'{}'", GXDBStringUtils.escapeSql(value));
    }

    private String renderSql() {
        return renderSql(GXConditionFuncDialectSupport.qualifiedColumn(tableNameAlias, jsonField), paramName + "_oneOrAll");
    }

    private String renderSql(String field, String oneOrAllParamName) {
        return GXConditionFuncDialectSupport.renderJsonSearch(field, oneOrAllParamName, paramName);
    }
}
