package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;

public class GXConditionIsNotNULL extends GXCondition<Object> {
    public GXConditionIsNotNULL(String tableNameAlias, String fieldName, Object value) {
        super(tableNameAlias, fieldName, value);
    }

    public GXConditionIsNotNULL(String tableNameAlias, String fieldName) {
        this(tableNameAlias, fieldName, null);
    }

    @Override
    public String getOp() {
        return "is not";
    }
    
    @Override
    public String whereString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} NULL", getFieldExpression(), getOp());
        }
        return CharSequenceUtil.format("{}.{} {} NULL", tableNameAlias, getFieldExpression(), getOp());
    }

    @Override
    public Object getFieldValue() {
        return null;
    }
}