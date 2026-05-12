package cn.maple.core.framework.dto.inner.condition;

import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.Collections;

public class GXConditionRaw extends GXCondition<String> {
    public GXConditionRaw(String value) {
        super("", "", value);
    }

    @Override
    public String getOp() {
        return "";
    }

    @Override
    public String whereString() {
        return GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString());
    }

    @Override
    public String getFieldValue() {
        return GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString());
    }

    @Override
    public String getFieldOriginalValue() {
        return GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString());
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(GXDBStringEscapeUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString()), Collections.emptyMap());
    }
}
