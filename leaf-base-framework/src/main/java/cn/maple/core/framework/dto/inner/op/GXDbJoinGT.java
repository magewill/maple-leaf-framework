package cn.maple.core.framework.dto.inner.op;

@SuppressWarnings("all")
public class GXDbJoinGT extends GXDbJoinOp {
    public GXDbJoinGT(String masterFieldName, String joinFieldName) {
        super(masterFieldName, joinFieldName);
    }

    @Override
    public String getOp() {
        return ">";
    }
}
