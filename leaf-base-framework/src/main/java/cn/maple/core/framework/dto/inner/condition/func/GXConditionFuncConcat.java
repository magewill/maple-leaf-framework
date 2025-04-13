package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;

public class GXConditionFuncConcat extends GXConditionFunc<String> {
    public GXConditionFuncConcat(String tableNameAlias, String op, String value, Object... expression) {
        super(tableNameAlias, op, value, expression);
    }

    @Override
    public String getOp() {
        return op;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return value.toString();
    }
    
    @Override
    public String whereString() {
        // 清除原来的参数映射，因为CONCAT函数需要特殊处理
        this.paramMap.clear();
        
        // 为值创建参数
        String valueParamName = paramName + "_value";
        this.paramMap.put(valueParamName, value + "%");
        
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{}({}) {} #{{}}", 
                getFunctionName(), getFieldExpression(), getOp(), valueParamName);
        }
        return CharSequenceUtil.format("{}({}) {} #{{}}", 
            getFunctionName(), getFieldExpression(), getOp(), valueParamName);
    }

    @Override
    protected String getFunctionName() {
        return "concat";
    }
}
