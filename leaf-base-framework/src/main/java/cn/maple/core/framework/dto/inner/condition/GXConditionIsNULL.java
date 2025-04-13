package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;

public class GXConditionIsNULL extends GXCondition<Object> {
    public GXConditionIsNULL(String tableNameAlias, String fieldName, Object value) {
        super(tableNameAlias, fieldName, null); // 确保值为null
    }

    public GXConditionIsNULL(String tableNameAlias, String fieldName) {
        this(tableNameAlias, fieldName, null);
    }

    @Override
    public String getOp() {
        return "is";
    }
    
    @Override
    public String whereString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} IS NULL", getFieldExpression());
        }
        return CharSequenceUtil.format("{}.{} IS NULL", tableNameAlias, getFieldExpression());
    }

    @Override
    public Object getFieldValue() {
        return null;
    }
}
