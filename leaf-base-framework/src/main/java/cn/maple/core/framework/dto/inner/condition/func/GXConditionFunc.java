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

    @Override
    public String whereString() {
        // 清除原来的参数映射，因为函数条件需要特殊处理
        this.paramMap.clear();
        
        // 为值创建参数
        this.paramMap.put(paramName, value);
        
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{}({}) {} #{{}}", 
                getFunctionName(), getFieldExpression(), getOp(), paramName);
        }
        return CharSequenceUtil.format("{}({}) {} #{{}}", 
            getFunctionName(), getFieldExpression(), getOp(), paramName);
    }
}
