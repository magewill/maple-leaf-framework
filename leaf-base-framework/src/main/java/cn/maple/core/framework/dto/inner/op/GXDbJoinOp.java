package cn.maple.core.framework.dto.inner.op;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.Getter;
import lombok.Setter;

public abstract class GXDbJoinOp {
    @Getter
    @Setter
    protected String masterTableNameAlias;

    protected String masterFieldName;

    @Setter
    @Getter
    protected String joinTableNameAlias;

    protected String joinFieldName;

    protected GXDbJoinOp() {

    }

    protected GXDbJoinOp(String masterFieldName, String subFieldName) {
        this.masterFieldName = masterFieldName;
        this.joinFieldName = subFieldName;
    }

    abstract String getOp();

    public String opString() {
        if (CharSequenceUtil.contains(masterFieldName, ".")) {
            masterFieldName = masterFieldName.split("\\.")[1];
        }
        if (CharSequenceUtil.contains(joinFieldName, ".")) {
            joinFieldName = joinFieldName.split("\\.")[1];
        }
        if (CharSequenceUtil.isNotEmpty(masterTableNameAlias) && CharSequenceUtil.isNotEmpty(masterFieldName)) {
            masterFieldName = CharSequenceUtil.format("{}.{}", masterTableNameAlias, masterFieldName);
        }
        if (CharSequenceUtil.isNotEmpty(joinTableNameAlias) && CharSequenceUtil.isNotEmpty(joinFieldName)) {
            joinFieldName = CharSequenceUtil.format("{}.{}", joinTableNameAlias, joinFieldName);
        }
        return masterFieldName + getOp() + joinFieldName;
    }
}
