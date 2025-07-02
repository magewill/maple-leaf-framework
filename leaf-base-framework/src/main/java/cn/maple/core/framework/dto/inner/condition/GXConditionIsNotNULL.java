package cn.maple.core.framework.dto.inner.condition;

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
    public Object getFieldValue() {
        return null;
    }

    @Override
    public Object getFieldOriginalValue() {
        return null;
    }
}
