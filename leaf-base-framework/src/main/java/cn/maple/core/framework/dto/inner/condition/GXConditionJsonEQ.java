package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.NumberUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionJsonEQ extends GXCondition<String> {
    private final String jsonPath;

    public GXConditionJsonEQ(String tableNameAlias, String fieldName, String jsonFieldName, String value) {
        super(tableNameAlias, fieldName, value);
        this.jsonPath = CharSequenceUtil.format("$.{}", jsonFieldName);
    }

    @Override
    public String getOp() {
        return "=";
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        
        if (NumberUtil.isNumber(strValue)) {
            return CharSequenceUtil.format("{}", strValue);
        }
        
        // 使用escapeSql方法进行更全面的SQL转义
        String escapedValue = GXDBStringEscapeUtils.escapeSql(strValue);
        return CharSequenceUtil.format("'{}'", escapedValue);
    }

    @Override
    public String getFieldExpression() {
        // 转义JSON路径，防止SQL注入
        String escapedPath = GXDBStringEscapeUtils.escapeJsonPath(jsonPath);
        
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            String format = "`{}`->'" + escapedPath + "'";
            return CharSequenceUtil.format(format, fieldExpression);
        } else {
            String format = "`{}`.`{}`->'" + escapedPath + "'";
            return CharSequenceUtil.format(format, tableNameAlias, fieldExpression);
        }
    }
}
