package cn.maple.core.framework.dto.inner.condition;

import cn.maple.core.framework.util.GXDBStringUtils;

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
        return GXDBStringUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString());
    }

    @Override
    public String getFieldValue() {
        return GXDBStringUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString());
    }

    @Override
    public String getFieldOriginalValue() {
        return GXDBStringUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString());
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(GXDBStringUtils.normalizeAndValidateRawSqlCondition(value == null ? null : value.toString()), Collections.emptyMap());
    }
}
