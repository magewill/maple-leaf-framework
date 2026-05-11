package cn.maple.core.framework.dto.inner.op;

@SuppressWarnings("all")
public class GXDbJoinNE extends GXDbJoinOp {
    public GXDbJoinNE(String masterFieldName, String joinFieldName) {
        super(masterFieldName, joinFieldName);
    }

    @Override
    public String getOp() {
        return "!=";
    }
}
