package cn.maple.core.framework.dto.inner.op;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;

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
        String masterField = masterFieldName;
        String joinField = joinFieldName;
        if (CharSequenceUtil.contains(masterField, ".")) {
            masterField = masterField.split("\\.")[1];
        }
        if (CharSequenceUtil.contains(joinField, ".")) {
            joinField = joinField.split("\\.")[1];
        }
        if (CharSequenceUtil.isNotEmpty(masterTableNameAlias) && CharSequenceUtil.isNotEmpty(masterField)) {
            masterField = CharSequenceUtil.format("{}.{}", masterTableNameAlias, masterField);
        }
        if (CharSequenceUtil.isNotEmpty(joinTableNameAlias) && CharSequenceUtil.isNotEmpty(joinField)) {
            joinField = CharSequenceUtil.format("{}.{}", joinTableNameAlias, joinField);
        }
        return masterField + getOp() + joinField;
    }

    public GXConditionSegment toSegment(String paramName) {
        return new GXConditionSegment(opString(), Collections.emptyMap());
    }
}
