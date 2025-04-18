package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXCondition;

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
        return Arrays.stream(expression).map(o -> CharSequenceUtil.format("{}.{}", tableNameAlias, o)).collect(Collectors.joining(","));
    }

    protected abstract String getFunctionName();

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     *
     * @return 安全的WHERE子句字符串
     */
    @Override
    public String whereString() {
        return CharSequenceUtil.format("{}({})",
                getFunctionName(),
                getFieldExpression());
    }
}
