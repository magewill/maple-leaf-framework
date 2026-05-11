package cn.maple.core.framework.dto.inner.op;

public class GXDbJoinValueNE extends GXDbJoinValue {
    public GXDbJoinValueNE(String tableNameAlias, String fieldName, Object fieldValue) {
        super(tableNameAlias, fieldName, fieldValue);
    }

    @Override
    String getOp() {
        return "!=";
    }
}
