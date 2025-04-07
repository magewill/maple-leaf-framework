package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.List;
import java.util.stream.Collectors;

public class GXConditionFuncJsonContains extends GXConditionFunc<String> {
    private final List<Object> values;

    private String jsonPath;

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
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
        if (CharSequenceUtil.isEmpty(jsonPath)) {
            jsonPath = "$";
        } else {
            jsonPath = CharSequenceUtil.format("$.{}", jsonPath);
        }
        // TODO 需要兼容  JSON_CONTAINS(ext, JSON_OBJECT("name", "塵子曦", "father", "塵渊")) 表达式
        String format = "`{}`.`{}`->'" + jsonPath + "', CAST('[{}]' AS JSON)";
        return CharSequenceUtil.format(format, tableNameAlias);
    }

    @Override
    public String getFieldValue() {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        
        return values.stream().map(s -> {
            // 检查是否存在SQL注入风险
            if (s != null && GXDBStringEscapeUtils.check(s.toString())) {
                throw new GXSqlInjectionException("SQL注入异常");
            }
            
            String format = "\"{}\"";
            if (s == null) {
                return "null";
            } else if (s instanceof Number) {
                format = "{}";
            }
            
            // 根据类型选择合适的转义方法
            if (s instanceof String) {
                String escapedValue = GXDBStringEscapeUtils.escapeSql(s.toString());
                return CharSequenceUtil.format(format, escapedValue);
            } else {
                return CharSequenceUtil.format(format, s);
            }
        }).collect(Collectors.joining(","));
    }

    @Override
    protected String getFunctionName() {
        return "JSON_CONTAINS";
    }

    @Override
    public String whereString() {
        String format = CharSequenceUtil.format("{}({})", getFunctionName(), getFieldExpression());
        return CharSequenceUtil.format(format, getOp(), getFieldValue());
    }
}