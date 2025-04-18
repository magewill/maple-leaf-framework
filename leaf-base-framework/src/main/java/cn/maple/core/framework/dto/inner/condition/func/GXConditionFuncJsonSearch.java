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
        String format = "`{}`.`{}`";
        return CharSequenceUtil.format(format, tableNameAlias, getOp());
    }

    @Override
    public String getFieldValue() {
        return CharSequenceUtil.format("'{}'", value);
    }

    @Override
    protected String getFunctionName() {
        return "JSON_SEARCH";
    }

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     *
     * @return 安全的WHERE子句字符串
     */
    @Override
    public String whereString() {
        // 使用Mybatis参数化查询形式，防止SQL注入
        // oneOrAll参数也需要参数化处理
        this.paramMap.put(paramName + "_oneOrAll", oneOrAll);
        return CharSequenceUtil.format("{}({}, #{dbQueryParamInnerDto.paramMap.{}_oneOrAll}, #{dbQueryParamInnerDto.paramMap.{}})",
                getFunctionName(),
                getFieldExpression(),
                paramName,
                paramName);
    }
}