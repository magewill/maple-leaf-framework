package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GXConditionIn extends GXCondition<String> {
    public GXConditionIn(String tableNameAlias, String fieldName, Set<Number> value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "in";
    }

    @Override
    public String getFieldValue() {
        String activeProfile = GXCommonUtils.getActiveProfile();
        int limitCnt = 100000;
        List<String> envLst = CollUtil.newArrayList(GXCommonConstant.RUN_ENV_DEV, GXCommonConstant.RUN_ENV_LOCAL);
        if (CollUtil.contains(envLst, activeProfile)/* && GXCurrentRequestContextUtils.isHTTP()*/) {
            limitCnt = GXCommonUtils.getEnvironmentValue("db.in.limit.cnt", Integer.class, 50);
        }
        if (CollUtil.size(value) > limitCnt) {
            throw new GXBusinessException(CharSequenceUtil.format("IN查询条件不能超过{}条数据!", limitCnt));
        }
        String str = ((Set<Number>) value).stream().map(v -> {
            if (v == null) {
                return "NULL";
            }
            
            String numStr = String.valueOf(v);
            // 即使是数字，也需要检查是否有SQL注入风险
            // 例如，某些数据库可能允许在数字中嵌入SQL注入
            if (GXDBStringEscapeUtils.check(numStr)) {
                throw new GXSqlInjectionException("SQL注入异常");
            }
            return numStr;
        }).collect(Collectors.joining(","));
        return CharSequenceUtil.format("({})", str);
    }
}
