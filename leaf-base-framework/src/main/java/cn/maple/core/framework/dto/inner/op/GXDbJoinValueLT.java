package cn.maple.core.framework.dto.inner.op;

public class GXDbJoinValueLT extends GXDbJoinValue {
    public GXDbJoinValueLT(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return "<";
    }
}
