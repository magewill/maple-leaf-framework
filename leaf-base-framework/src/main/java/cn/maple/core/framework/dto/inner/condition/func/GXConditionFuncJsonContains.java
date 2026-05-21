package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GXConditionFuncJsonContains extends GXConditionFunc<String> {
    private final Object values;
    private final String jsonField;
    private final String rawJsonPath;

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
        super(tableNameAlias, jsonField, "");
        this.values = values;
        this.jsonField = jsonField;
        this.rawJsonPath = jsonPath;
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values, String jsonPath) {
        super(tableNameAlias, jsonField, "");
        this.values = values;
        this.jsonField = jsonField;
        this.rawJsonPath = jsonPath;
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
        return GXConditionFuncDialectSupport.toJsonString(values, getFunctionName());
    }

    @Override
    protected String getFunctionName() {
        return "JSON_CONTAINS";
    }

    @Override
    public String whereString() {
        return renderSql();
    }

    @Override
    public GXConditionSegment toSegment() {
        String pathParamName = paramName + "_path";
        String field = GXConditionFuncDialectSupport.qualifiedColumn(tableNameAlias, jsonField);
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, getFieldValue());
        params.put(pathParamName, GXConditionFuncDialectSupport.normalizeJsonPath(rawJsonPath));
        return new GXConditionSegment(renderSql(field, pathParamName), params);
    }

    @Override
    public String getFieldOriginalValue() {
        return getFieldValue();
    }

    private String renderSql() {
        return renderSql(GXConditionFuncDialectSupport.qualifiedColumn(tableNameAlias, jsonField), paramName + "_path");
    }

    private String renderSql(String field, String pathParamName) {
        return GXConditionFuncDialectSupport.renderJsonContains(field, pathParamName, paramName);
    }
}
