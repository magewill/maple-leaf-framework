package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.util.GXCommonUtils;

import java.util.*;
import java.util.stream.Collectors;

public class GXConditionNotIn extends GXCondition<String> {
    private final Set<Number> values;

    public GXConditionNotIn(String tableNameAlias, String fieldName, Set<Number> value) {
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
        List<String> envLst = CollUtil.newArrayList(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        if (CollUtil.contains(envLst, activeProfile)) {
            limitCnt = GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, 50);
        }
        if (CollUtil.size(values) > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("NOT IN查询条件不能超过{}条数据!", limitCnt));
        }
        List<String> paramPlaceholders = new ArrayList<>();
        int index = 0;
        for (Number ignored : values) {
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
        for (Number num : values) {
            String itemParamName = paramName + "_" + index++;
            this.paramMap.put(itemParamName, num);
        }
        String str = values.stream().map(String::valueOf).collect(Collectors.joining(","));
        return CharSequenceUtil.format("({})", str);
    }

    @Override
    public String getFieldOriginalValue() {
        String str = ((Set<Number>) value).stream().map(String::valueOf).collect(Collectors.joining(","));
        return CharSequenceUtil.format("({})", str);
    }

    @Override
    public GXConditionSegment toSegment() {
        String sql = whereString();
        Map<String, Object> params = new HashMap<>();
        int index = 0;
        for (Number num : values) {
            params.put(paramName + "_" + index++, num);
        }
        return new GXConditionSegment(sql, params);
    }
}
