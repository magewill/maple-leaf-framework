package cn.maple.core.framework.dto.inner.op;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.dto.inner.condition.GXConditionSegment;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringUtils;

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
        String safeAlias = validateAlias(tableNameAlias, "JOIN value alias");
        String safeFieldName = normalizeFieldName(fieldName, "JOIN value field");
        return safeAlias + "." + safeFieldName + getOp() + renderLiteralValue(fieldValue);
    }

    @Override
    public GXConditionSegment toSegment(String paramName) {
        String safeAlias = validateAlias(tableNameAlias, "JOIN value alias");
        String safeFieldName = normalizeFieldName(fieldName, "JOIN value field");
        String sql = CharSequenceUtil.format("{}.{}{}", safeAlias, safeFieldName, getOp())
                + "#{dbQueryParamInnerDto.paramMap." + paramName + "}";
        Map<String, Object> params = new HashMap<>();
        params.put(paramName, fieldValue);
        return new GXConditionSegment(sql, params);
    }

    private static String renderLiteralValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        String strValue = value.toString();
        if (GXDBStringUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL injection risk detected in JOIN value");
        }
        return CharSequenceUtil.format("'{}'", GXDBStringUtils.escapeSql(strValue));
    }
}
