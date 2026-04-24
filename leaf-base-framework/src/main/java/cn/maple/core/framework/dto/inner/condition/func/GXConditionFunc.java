package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.exception.GXBusinessException;

import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class GXConditionFunc<T> extends GXCondition<T> {
    protected String op;
    @SuppressWarnings("all")
    protected Object[] expression;

    protected GXConditionFunc(String tableNameAlias, String fieldExpression, Object value) {
        super(tableNameAlias, fieldExpression, value);
    }

    protected GXConditionFunc(String tableNameAlias, String op, Object value, Object... expression) {
        this(tableNameAlias, "", value);
        this.op = op;
        this.expression = expression;
    }

    @Override
    public String getFieldExpression() {
        if (expression == null || expression.length == 0) {
            throw new GXBusinessException("Function expression fields must not be empty");
        }
        return Arrays.stream(expression)
                .map(this::toQualifiedColumn)
                .collect(Collectors.joining(","));
    }

    protected abstract String getFunctionName();

    @Override
    public String whereString() {
        return CharSequenceUtil.format("{}({})", getFunctionName(), getFieldExpression());
    }

    private String toQualifiedColumn(Object token) {
        String column = token == null ? "" : CharSequenceUtil.trim(token.toString());
        if (CharSequenceUtil.isBlank(column)) {
            throw new GXBusinessException("Function expression field item must not be blank");
        }
        if (column.contains(".")) {
            return column;
        }
        if (CharSequenceUtil.isBlank(tableNameAlias)) {
            return column;
        }
        return CharSequenceUtil.format("{}.{}", tableNameAlias, column);
    }
}
