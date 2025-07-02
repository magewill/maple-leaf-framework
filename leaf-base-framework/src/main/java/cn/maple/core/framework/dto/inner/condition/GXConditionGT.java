package cn.maple.core.framework.dto.inner.condition;

public class GXConditionGT extends GXCondition<Number> {
    public GXConditionGT(String tableNameAlias, String fieldName, Number value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return ">";
    }

    @Override
    public Number getFieldValue() {
        return (Number) value;
    }

    @Override
    public Number getFieldOriginalValue() {
        return (Number) value;
    }
}
