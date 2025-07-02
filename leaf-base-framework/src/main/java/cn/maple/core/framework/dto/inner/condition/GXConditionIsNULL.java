package cn.maple.core.framework.dto.inner.condition;

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
}
