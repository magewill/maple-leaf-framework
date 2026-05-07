package cn.maple.core.framework.dto.inner.op;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("all")
public abstract class GXDbJoinValue extends GXDbJoinOp {
    private final String tableNameAlias;

    private final String fieldName;

    private Object fieldValue;

    public GXDbJoinValue(String tableNameAlias, String fieldName, Object fieldValue) {
        this.tableNameAlias = tableNameAlias;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    @Override
    public String opString() {
        return tableNameAlias + "." + fieldName + getOp() + fieldValue;
    }

    @Override
    public GXConditionSegment toSegment(String paramName) {
        GXDBStringEscapeUtils.validateColumnName(tableNameAlias);
        GXDBStringEscapeUtils.validateColumnName(fieldName);
        String sql = CharSequenceUtil.format("{}.{}{}#{{dbQueryParamInnerDto.paramMap.{}}}",
                tableNameAlias, fieldName, getOp(), paramName);
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, fieldValue);
        return new GXConditionSegment(sql, params);
    }
}
