package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GXConditionIn extends GXCondition<String> {
    private static final int DEFAULT_IN_LIMIT_COUNT = 100000;
    private static final int DEV_LOCAL_IN_LIMIT_COUNT = 50;
    private final Set<Number> numbers;

    public GXConditionIn(String tableNameAlias, String fieldName, Set<Number> value) {
        super(tableNameAlias, fieldName, value);
        this.numbers = value;
    }

    @Override
    public String getOp() {
        return "in";
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
        String serialized = numbers.stream().map(v -> {
            validateNumberValue(v);
            return String.valueOf(v);
        }).collect(Collectors.joining(","));
        return CharSequenceUtil.format("({})", serialized);
    }

    @Override
    public GXConditionSegment toSegment() {
        return new GXConditionSegment(whereString(), buildValidatedParams());
    }

    private void validateSize() {
        if (CollUtil.isEmpty(numbers)) {
            throw new GXBusinessException("IN condition values must not be empty");
        }
        int limitCnt = resolveLimitCount();
        if (numbers.size() > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("IN condition values exceed limit: {}", limitCnt));
        }
    }

    private int resolveLimitCount() {
        String activeProfile = GXCommonUtils.getActiveProfile();
        List<String> envList = List.of(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        if (CollUtil.contains(envList, activeProfile)) {
            return GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, DEV_LOCAL_IN_LIMIT_COUNT);
        }
        return DEFAULT_IN_LIMIT_COUNT;
    }

    private List<String> buildParamPlaceholders() {
        List<String> placeholders = new ArrayList<>();
        int index = 0;
        for (Number ignored : numbers) {
            placeholders.add("#{dbQueryParamInnerDto.paramMap." + paramName + "_" + index++ + "}");
        }
        return placeholders;
    }

    private Map<String, Object> buildValidatedParams() {
        validateSize();
        Map<String, Object> params = new HashMap<>();
        int index = 0;
        for (Number number : numbers) {
            validateNumberValue(number);
            params.put(paramName + "_" + index++, number);
        }
        return params;
    }

    private static void validateNumberValue(Number number) {
        if (number == null) {
            throw new GXBusinessException("IN condition value item must not be null");
        }
        String numStr = String.valueOf(number);
        if (GXDBStringEscapeUtils.check(numStr)) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("SQL injection risk detected in IN condition numeric value: {}", numStr));
        }
    }
}
