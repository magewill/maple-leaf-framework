package cn.maple.core.framework.dto.inner.field;

public class GXUpdateNumberField extends GXUpdateField<Number> {
    public GXUpdateNumberField(String tableNameAlias, String fieldName, int numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }

    public GXUpdateNumberField(String tableNameAlias, String fieldName, long numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }

    public GXUpdateNumberField(String tableNameAlias, String fieldName, double numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }

    public GXUpdateNumberField(String tableNameAlias, String fieldName, Number numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }

    @Override
    public Number getFieldValue() {
        return (Number) value;
    }

    @Override
    public String updateString() {
        return super.updateString();
    }
}
