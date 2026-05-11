package cn.maple.core.framework.dto.inner.op;

@SuppressWarnings("all")
public class GXDbJoinLE extends GXDbJoinOp {
    public GXDbJoinLE(String masterFieldName, String joinFieldName) {
        super(masterFieldName, joinFieldName);
    }

    @Override
    public String getOp() {
        return "<=";
    }
}
