package cn.maple.core.framework.dto.inner.condition;

public class GXExclusionDeletedFieldCondition extends GXCondition<String> {
    public GXExclusionDeletedFieldCondition(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    public GXExclusionDeletedFieldCondition(String tableNameAlias, String fieldName, Long value) {
        super(tableNameAlias, fieldName, value);
    }

    public GXExclusionDeletedFieldCondition() {
        this("", "", "");
    }

    @Override
    public String getOp() {
        return null;
    }

    @Override
    public String getFieldValue() {
        return null;
    }

    @Override
    public String getFieldOriginalValue() {
        return null;
    }
}
