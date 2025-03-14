package cn.maple.core.framework.dto.inner.op;

public class GXDbJoinValueGEQ extends GXDbJoinValue {
    public GXDbJoinValueGEQ(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return ">=";
    }
}
