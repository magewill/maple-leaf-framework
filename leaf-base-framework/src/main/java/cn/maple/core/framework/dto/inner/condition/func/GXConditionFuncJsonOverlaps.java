package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.List;
import java.util.stream.Collectors;

public class GXConditionFuncJsonOverlaps extends GXConditionFunc<String> {
    private final List<Object> values;

    private String jsonPath;

    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    @Override
    public String getOp() {
        return op;
    }

    @Override
    public String getFieldExpression() {
        // 不再在此方法中构建完整表达式
        // 仅返回字段名，完整表达式将在whereString中构建
        return fieldExpression;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return "";
    }

    @Override
    protected String getFunctionName() {
        return "JSON_OVERLAPS";
    }

    @Override
    public String whereString() {
        // 清除原来的参数映射，因为JSON函数需要特殊处理
        this.paramMap.clear();
        
        // 处理JSON路径
        String jsonPathParamName = paramName + "_path";
        String normalizedJsonPath = CharSequenceUtil.isEmpty(jsonPath) ? "$" : "$." + jsonPath;
        this.paramMap.put(jsonPathParamName, normalizedJsonPath);
        
        // 处理JSON值数组
        String valuesParamName = paramName + "_values";
        String jsonArray = "[" + values.stream().map(v -> {
            if (v instanceof Number) {
                return v.toString();
            } else {
                return "\"" + v + "\"";
            }
        }).collect(Collectors.joining(",")) + "]";
        this.paramMap.put(valuesParamName, jsonArray);
        
        // 构建参数化的JSON_OVERLAPS函数调用
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("JSON_OVERLAPS({}, CAST(#{{{}} as JSON), #{{}})", 
                getFieldExpression(), valuesParamName, jsonPathParamName);
        }
        return CharSequenceUtil.format("JSON_OVERLAPS({}.{}, CAST(#{{{}} as JSON), #{{}})", 
            tableNameAlias, getFieldExpression(), valuesParamName, jsonPathParamName);
    }
}