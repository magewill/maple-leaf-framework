package cn.maple.core.framework.dto.inner.field;

public class GXUpdateNumberField extends GXUpdateField<Number> {
    public GXUpdateNumberField(String tableNameAlias, String fieldName, int numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }

    @Override
    public Number getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        // 在参数化查询中，值会通过paramMap传递给MyBatis
        return (Number) value;
    }
}
