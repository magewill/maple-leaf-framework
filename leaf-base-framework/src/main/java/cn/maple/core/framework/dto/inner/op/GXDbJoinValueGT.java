package cn.maple.core.framework.dto.inner.op;

public class GXDbJoinValueGT extends GXDbJoinValue {
    public GXDbJoinValueGT(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return ">";
    }
}
