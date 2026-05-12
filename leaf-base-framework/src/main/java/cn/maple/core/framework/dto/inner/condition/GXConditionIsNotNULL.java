package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;

public class GXConditionIsNotNULL extends GXCondition<Object> {
    public GXConditionIsNotNULL(String tableNameAlias, String fieldName, @Nullable Object value) {
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
            return CharSequenceUtil.format("{} IS NOT NULL", getFieldExpression());
        }
        return CharSequenceUtil.format("{}.{} IS NOT NULL", tableNameAlias, getFieldExpression());
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(whereString(), Collections.emptyMap());
    }
}
