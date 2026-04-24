package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

public class GXConditionRaw extends GXCondition<String> {
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile("(?i)\\b(update|delete|insert|alter|drop|truncate|create|grant|revoke|call|exec|merge)\\b");

    public GXConditionRaw(String value) {
        super("", "", value);
    }

    private static String normalizeAndValidateRawSql(String raw) {
        if (CharSequenceUtil.isBlank(raw)) {
            throw new GXSqlInjectionException("Raw SQL condition must not be blank");
        }
        String normalized = raw.trim();
        if (GXDBStringEscapeUtils.check(normalized)) {
            throw new GXSqlInjectionException("SQL injection risk detected in raw SQL condition");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains(";") || lower.contains("--") || lower.contains("/*") || lower.contains("*/")) {
            throw new GXSqlInjectionException("Raw SQL condition contains illegal SQL control symbols");
        }
        if (DANGEROUS_PATTERN.matcher(normalized).find()) {
            throw new GXSqlInjectionException("Raw SQL condition contains dangerous SQL keywords");
        }
        return normalized;
    }

    @Override
    public String getOp() {
        return "";
    }

    @Override
    public String whereString() {
        return normalizeAndValidateRawSql(value == null ? null : value.toString());
    }

    @Override
    public String getFieldValue() {
        return normalizeAndValidateRawSql(value == null ? null : value.toString());
    }

    @Override
    public String getFieldOriginalValue() {
        return normalizeAndValidateRawSql(value == null ? null : value.toString());
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(normalizeAndValidateRawSql(value == null ? null : value.toString()), Collections.emptyMap());
    }
}
