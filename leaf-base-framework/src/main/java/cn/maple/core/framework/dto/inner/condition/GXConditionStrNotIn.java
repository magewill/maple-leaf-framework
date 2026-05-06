package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.*;
import java.util.stream.Collectors;

public class GXConditionStrNotIn extends GXCondition<String> {
    private static final int DEFAULT_NOT_IN_LIMIT_COUNT = 100000;
    private static final int DEV_LOCAL_NOT_IN_LIMIT_COUNT = 50;
    private final Set<String> values;

    public GXConditionStrNotIn(String tableNameAlias, String fieldName, Set<String> value) {
        super(tableNameAlias, fieldName, value);
        this.values = value;
    }

    private static void validateStringValue(String value) {
        if (value == null) {
            throw new GXBusinessException("NOT IN condition value item must not be null");
        }
        if (GXDBStringEscapeUtils.check(value)) {
            throw new GXSqlInjectionException("SQL injection risk detected in NOT IN condition value");
        }
    }

    @Override
    public String getOp() {
        return "not in";
    }

    @Override
    public String whereString() {
        validateSize();
        String inClause = String.join(",", buildParamPlaceholders());
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} ({})", getFieldExpression(), getOp(), inClause);
        }
        return CharSequenceUtil.format("{}.{} {} ({})", tableNameAlias, getFieldExpression(), getOp(), inClause);
    }

    @Override
    public String getFieldValue() {
        this.paramMap.clear();
        this.paramMap.putAll(buildValidatedParams());
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        validateSize();
        String serialized = values.stream().map(v -> {
            validateStringValue(v);
            return CharSequenceUtil.format("'{}'", GXDBStringEscapeUtils.escapeSql(v));
        }).collect(Collectors.joining(","));
        return CharSequenceUtil.format("({})", serialized);
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(whereString(), buildValidatedParams());
    }

    private void validateSize() {
        if (CollUtil.isEmpty(values)) {
            throw new GXBusinessException("NOT IN condition values must not be empty");
        }
        int limitCnt = resolveLimitCount();
        if (values.size() > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("NOT IN condition values exceed limit: {}", limitCnt));
        }
    }

    private int resolveLimitCount() {
        String activeProfile = GXCommonUtils.getActiveProfile();
        List<String> envList = List.of(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        if (CollUtil.contains(envList, activeProfile)) {
            return GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, DEV_LOCAL_NOT_IN_LIMIT_COUNT);
        }
        return DEFAULT_NOT_IN_LIMIT_COUNT;
    }

    private List<String> buildParamPlaceholders() {
        List<String> placeholders = new ArrayList<>();
        int index = 0;
        for (String ignored : values) {
            placeholders.add("#{dbQueryParamInnerDto.paramMap." + paramName + "_" + index++ + "}");
        }
        return placeholders;
    }

    private Map<String, Object> buildValidatedParams() {
        validateSize();
        Map<String, Object> params = new HashMap<>();
        int index = 0;
        for (String str : values) {
            validateStringValue(str);
            params.put(paramName + "_" + index++, str);
        }
        return params;
    }
}
