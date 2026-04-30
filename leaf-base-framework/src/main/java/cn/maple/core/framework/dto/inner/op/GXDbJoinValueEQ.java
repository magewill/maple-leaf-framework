package cn.maple.core.framework.dto.inner.op;

public class GXDbJoinValueEQ extends GXDbJoinValue {
    public GXDbJoinValueEQ(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return "=";
    }
}
