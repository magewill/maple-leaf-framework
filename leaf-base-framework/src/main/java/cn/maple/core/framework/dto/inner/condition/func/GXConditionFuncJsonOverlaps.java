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
        return values.stream().map(s -> {
            String format = "\"{}\"";
            if (s.getClass().isAssignableFrom(Integer.class) || s.getClass().isAssignableFrom(Long.class) || s.getClass().isAssignableFrom(Short.class)) {
                format = "{}";
            }
            return CharSequenceUtil.format(format, s);
        }).collect(Collectors.joining(","));
    }

    @Override
    protected String getFunctionName() {
        return "JSON_OVERLAPS";
    }

    @Override
    public String whereString() {
        String format = CharSequenceUtil.format("{}({})", getFunctionName(), getFieldExpression());
        return CharSequenceUtil.format(format, getOp(), getFieldValue());
    }
}