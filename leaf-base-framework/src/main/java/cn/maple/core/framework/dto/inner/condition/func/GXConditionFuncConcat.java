package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionFuncConcat extends GXConditionFunc<String> {
    public GXConditionFuncConcat(String tableNameAlias, String op, String value, Object... expression) {
        super(tableNameAlias, op, value, expression);
    }

    @Override
    public String getOp() {
        return op;
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            return "'%'";
        }
        
        String strValue = value.toString();
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        
        // 使用escapeSql方法进行更全面的SQL转义
        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);
        return CharSequenceUtil.format("'{}%'", escapedValue);
    }

    @Override
    protected String getFunctionName() {
        return "concat";
    }
}
