package cn.maple.core.framework.dto.inner.op;

@SuppressWarnings("all")
public class GXDbJoinLT extends GXDbJoinOp {
    public GXDbJoinLT(String masterFieldName, String joinFieldName) {
        super(masterFieldName, joinFieldName);
    }

    @Override
    public String getOp() {
        return "<";
    }
}
