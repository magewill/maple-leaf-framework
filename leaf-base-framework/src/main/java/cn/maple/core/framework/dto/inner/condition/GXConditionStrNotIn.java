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
    private final Set<String> values;

    public GXConditionStrNotIn(String tableNameAlias, String fieldName, Set<String> value) {
        super(tableNameAlias, fieldName, value);
        this.values = value;
    }

    @Override
    public String getOp() {
        return "not in";
    }

    @Override
    public String whereString() {
        String activeProfile = GXCommonUtils.getActiveProfile();
        int limitCnt = 100000;
        List<String> envLst = List.of(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        if (CollUtil.contains(envLst, activeProfile)) {
            limitCnt = GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, 50);
        }
        if (values.size() > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("NOT IN查询条件不能超过{}条数据", limitCnt));
        }

        List<String> paramPlaceholders = new ArrayList<>();
        int index = 0;
        for (String ignored : values) {
            String itemParamName = paramName + "_" + index++;
            paramPlaceholders.add("#{dbQueryParamInnerDto.paramMap." + itemParamName + "}");
        }

        String inClause = String.join(",", paramPlaceholders);
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} ({})", getFieldExpression(), getOp(), inClause);
        }
        return CharSequenceUtil.format("{}.{} {} ({})", tableNameAlias, getFieldExpression(), getOp(), inClause);
    }

    @Override
    public String getFieldValue() {
        this.paramMap.clear();
        int index = 0;
        for (String str : values) {
            if (GXDBStringEscapeUtils.check(str)) {
                throw new GXSqlInjectionException("检测到SQL注入风险：包含可疑字符或SQL关键字");
            }
            String itemParamName = paramName + "_" + index++;
            this.paramMap.put(itemParamName, str);
        }
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        String activeProfile = GXCommonUtils.getActiveProfile();
        int limitCnt = 100000;
        List<String> envLst = CollUtil.newArrayList(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        if (CollUtil.contains(envLst, activeProfile)) {
            limitCnt = GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, 50);
        }
        if (CollUtil.size(value) > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("NOT IN查询条件不能超过{}条数据", limitCnt));
        }
        String str = ((Set<String>) value).stream().map(v -> {
            if (GXDBStringEscapeUtils.check(v)) {
                throw new GXSqlInjectionException("SQL注入异常");
            }
            String val = GXDBStringEscapeUtils.escapeRawString(v);
            String format = "'{}'";
            if (CharSequenceUtil.contains(val, "\\'")) {
                format = "\"{}\"";
            }
            return CharSequenceUtil.format(format, val);
        }).collect(Collectors.joining(","));
        return CharSequenceUtil.format("({})", str);
    }

    @Override
    public GXConditionSegment toSegment() {
        String sql = whereString();
        Map<String, Object> params = new HashMap<>();
        int index = 0;
        for (String str : values) {
            if (GXDBStringEscapeUtils.check(str)) {
                throw new GXSqlInjectionException("检测到SQL注入风险：包含可疑字符或SQL关键字");
            }
            params.put(paramName + "_" + index++, str);
        }
        return new GXConditionSegment(sql, params);
    }
}
