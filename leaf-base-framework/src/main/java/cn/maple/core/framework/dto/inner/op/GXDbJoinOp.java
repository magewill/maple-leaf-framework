package cn.maple.core.framework.dto.inner.op;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.Collections;

public abstract class GXDbJoinOp {
    @Getter
    @Setter
    protected @Nullable String masterTableNameAlias;

    protected @Nullable String masterFieldName;

    @Setter
    @Getter
    protected @Nullable String joinTableNameAlias;

    protected @Nullable String joinFieldName;

    protected GXDbJoinOp() {

    }

    protected GXDbJoinOp(@Nullable String masterFieldName, @Nullable String subFieldName) {
        this.masterFieldName = masterFieldName;
        this.joinFieldName = subFieldName;
    }

    abstract String getOp();

    public String opString() {
        String masterField = normalizeFieldName(masterFieldName, "JOIN master field");
        String joinField = normalizeFieldName(joinFieldName, "JOIN field");
        if (CharSequenceUtil.isNotEmpty(masterTableNameAlias) && CharSequenceUtil.isNotEmpty(masterField)) {
            masterField = CharSequenceUtil.format("{}.{}", validateAlias(masterTableNameAlias, "JOIN master alias"), masterField);
        }
        if (CharSequenceUtil.isNotEmpty(joinTableNameAlias) && CharSequenceUtil.isNotEmpty(joinField)) {
            joinField = CharSequenceUtil.format("{}.{}", validateAlias(joinTableNameAlias, "JOIN alias"), joinField);
        }
        return masterField + getOp() + joinField;
    }

    public GXConditionSegment toSegment(String paramName) {
        return new GXConditionSegment(opString(), Collections.emptyMap());
    }

    protected static String normalizeFieldName(@Nullable String fieldName, String label) {
        String trimmed = CharSequenceUtil.trim(fieldName);
        GXDBStringEscapeUtils.validateSqlIdentifier(trimmed, label);
        int dotIndex = trimmed.lastIndexOf('.');
        String bareField = dotIndex >= 0 ? trimmed.substring(dotIndex + 1) : trimmed;
        return GXDBStringEscapeUtils.validateSqlAlias(bareField, label);
    }

    protected static String validateAlias(@Nullable String alias, String label) {
        return GXDBStringEscapeUtils.validateSqlAlias(CharSequenceUtil.trim(alias), label);
    }
}
