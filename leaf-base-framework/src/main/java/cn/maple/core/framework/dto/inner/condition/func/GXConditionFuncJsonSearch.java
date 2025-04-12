package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXBuilderConstant;

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
        // 不再在此方法中构建完整表达式
        // 仅返回字段名，完整表达式将在whereString中构建
        return fieldExpression;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return "";
    }

    @Override
    protected String getFunctionName() {
        return "JSON_SEARCH";
    }

    @Override
    public String whereString() {
        // 清除原来的参数映射，因为JSON函数需要特殊处理
        this.paramMap.clear();
        
        // 处理oneOrAll参数
        String oneOrAllParamName = paramName + "_one_or_all";
        this.paramMap.put(oneOrAllParamName, oneOrAll);
        
        // 处理搜索值参数
        String valueParamName = paramName + "_value";
        this.paramMap.put(valueParamName, value);
        
        // 构建参数化的JSON_SEARCH函数调用
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("JSON_SEARCH({}, #{{{}}}, #{{}})", 
                getFieldExpression(), oneOrAllParamName, valueParamName);
        }
        return CharSequenceUtil.format("JSON_SEARCH({}.{}, #{{{}}}, #{{}})", 
            tableNameAlias, getFieldExpression(), oneOrAllParamName, valueParamName);
    }
}