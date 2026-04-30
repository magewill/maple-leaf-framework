package cn.maple.core.framework.dto.inner.op;

public class GXDbJoinValueLEQ extends GXDbJoinValue {
    public GXDbJoinValueLEQ(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return "<=";
    }
}
