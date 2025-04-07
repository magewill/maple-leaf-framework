package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionLikeRight extends GXCondition<String> {
    public GXConditionLikeRight(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "like";
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        // 首先检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        
        // 使用escapeSqlForLike方法进行更全面的SQL转义，特别适合LIKE查询
        String escapedValue = GXDBStringEscapeUtils.escapeSqlForLike(strValue);
        
        // 根据内容选择合适的引号包裹方式
        if (CharSequenceUtil.contains(escapedValue, "''")) {
            // 如果包含已转义的单引号，使用双引号包裹
            return CharSequenceUtil.format("\"{}%\"", escapedValue);
        } else {
            // 否则使用单引号包裹
            return CharSequenceUtil.format("'{}%'", escapedValue);
        }
    }
}
