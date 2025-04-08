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

    /**
     * 获取IN条件的字段值
     * <p>
     * 该方法实现了多层次的SQL注入防护措施：
     * 1. 检查值集合的大小，防止过大的IN条件导致性能问题
     * 2. 对每个数值进行SQL注入风险检测
     * 3. 构建安全的IN子句
     * </p>
     * <p>
     * 注意：即使是数字类型，也需要进行SQL注入检测，因为某些数据库可能允许在数字中嵌入SQL注入
     * </p>
     *
     * @return 安全的IN条件字符串表示
     * @throws GXBusinessException 如果IN条件包含的数据过多
     * @throws GXSqlInjectionException 如果检测到SQL注入风险
     */
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
                throw new GXSqlInjectionException("IN条件中的数值存在SQL注入风险: " + numStr);
            }
            return numStr;
        }).collect(Collectors.joining(","));
        return CharSequenceUtil.format("({})", str);
    }
}
