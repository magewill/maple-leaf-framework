package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionFuncJsonSearch extends GXConditionFunc<String> {
    private final String value;

    private final String oneOrAll;

    public GXConditionFuncJsonSearch(String tableNameAlias, String field, String value) {
        this(tableNameAlias, field, value, GXBuilderConstant.JSON_SEARCH_FUNC_ONE);
    }

    public GXConditionFuncJsonSearch(String tableNameAlias, String field, String value, String oneOrAll) {
        super(tableNameAlias, field, value, oneOrAll);
        this.value = value;
        this.oneOrAll = CharSequenceUtil.isEmpty(oneOrAll) ? GXBuilderConstant.JSON_SEARCH_FUNC_ONE : oneOrAll;
    }

    @Override
    public String getOp() {
        return op;
    }

    @Override
    public String getFieldExpression() {
        // 此方法在当前实现中不再被whereString使用
        // 但为了保持一致性和可能的未来使用，提供正确实现
        return fieldExpression;
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(value)) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        
        // 使用escapeSql方法进行更全面的SQL转义
        String escapedValue = GXDBStringEscapeUtils.escapeSql(value);
        return CharSequenceUtil.format("'{}'", escapedValue);
    }

    @Override
    protected String getFunctionName() {
        return "JSON_SEARCH";
    }

    @Override
    public String whereString() {
        // 正确构建JSON_SEARCH函数调用
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{}({}, '{}', {})", getFunctionName(), fieldExpression, oneOrAll, getFieldValue());
        } else {
            return CharSequenceUtil.format("{}({}.{}, '{}', {})", getFunctionName(), tableNameAlias, fieldExpression, oneOrAll, getFieldValue());
        }
    }
}