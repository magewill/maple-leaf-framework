package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GXConditionStrIn extends GXCondition<String> {
    private final Set<String> values;

    public GXConditionStrIn(String tableNameAlias, String fieldName, Set<String> value) {
        super(tableNameAlias, fieldName, value);
        this.values = value;
    }

    @Override
    public String getOp() {
        return "in";
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
            throw new GXBusinessException(CharSequenceUtil.format("IN查询条件不能超过{}条数据!", limitCnt));
        }

        // 构建参数化的IN子句
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
        // 清除原来的参数映射，因为IN条件需要特殊处理
        this.paramMap.clear();
        // 为每个值创建单独的参数
        int index = 0;
        for (String str : values) {
            // 检查SQL注入
            if (GXDBStringEscapeUtils.check(str)) {
                throw new GXSqlInjectionException("SQL注入异常");
            }
            String itemParamName = paramName + "_" + index++;
            this.paramMap.put(itemParamName, str);
        }
        return "";
    }
}
