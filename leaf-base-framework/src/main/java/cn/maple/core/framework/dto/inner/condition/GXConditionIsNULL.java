package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.Collections;

public class GXConditionIsNULL extends GXCondition<Object> {
    public GXConditionIsNULL(String tableNameAlias, String fieldName, Object value) {
        super(tableNameAlias, fieldName, value);
    }

    public GXConditionIsNULL(String tableNameAlias, String fieldName) {
        this(tableNameAlias, fieldName, null);
    }

    @Override
    public String getOp() {
        return "is";
    }

    @Override
    public Object getFieldValue() {
        return null;
    }

    @Override
    public Object getFieldOriginalValue() {
        return null;
    }

    @Override
    public String whereString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} IS NULL", getFieldExpression());
        }
        return CharSequenceUtil.format("{}.{} IS NULL", tableNameAlias, getFieldExpression());
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(whereString(), Collections.emptyMap());
    }
}
