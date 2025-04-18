package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.List;
import java.util.stream.Collectors;

public class GXConditionFuncJsonContains extends GXConditionFunc<String> {
    private final Object values;

    private String jsonPath;

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonContains(String tableNameAlias, String jsonField, Dict values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    @Override
    public String getOp() {
        return op;
    }

    @Override
    public String getFieldExpression() {
        if (CharSequenceUtil.isEmpty(jsonPath)) {
            jsonPath = "$";
        } else {
            jsonPath = CharSequenceUtil.format("$.{}", jsonPath);
        }
        // 将jsonPath也通过参数化查询传入，防止SQL注入
        this.paramMap.put(paramName + "_path", jsonPath);
        // 使用Mybatis参数化查询形式，防止SQL注入
        // TODO 需要兼容  JSON_CONTAINS(ext, JSON_OBJECT("name", "塵子曦", "father", "塵渊")) 表达式
        String format = "`{}`.`{}`->#{dbQueryParamInnerDto.paramMap.{}_path}, CAST(#{dbQueryParamInnerDto.paramMap.{}} AS JSON)";
        return CharSequenceUtil.format(format, tableNameAlias, getOp(), paramName, paramName);
    }

    @Override
    public String getFieldValue() {
        if (values.getClass().isAssignableFrom(Dict.class)) {
            return "[" + JSONUtil.toJsonStr(values) + "]";
        }
        if (values.getClass().isAssignableFrom(List.class)) {
            List<?> valueLst = Convert.convert(List.class, values);
            return "[" + valueLst.stream().map(s -> {
                String format = "\"{}\"";
                if (s.getClass().isAssignableFrom(Integer.class) || s.getClass().isAssignableFrom(Long.class) || s.getClass().isAssignableFrom(Short.class)) {
                    format = "{}";
                }
                return CharSequenceUtil.format(format, s);
            }).collect(Collectors.joining(",")) + "]";
        }
        return values.toString();
    }

    @Override
    protected String getFunctionName() {
        return "JSON_CONTAINS";
    }

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     *
     * @return 安全的WHERE子句字符串
     */
    @Override
    public String whereString() {
        // 在构建SQL时，使用参数化查询形式，值通过Mybatis的#{}机制传入，防止SQL注入
        this.paramMap.clear();
        // 存储JSON数组值到参数映射中
        this.paramMap.put(paramName, this.getFieldValue());
        // 注意：getFieldExpression方法会将jsonPath添加到paramMap中
        // 使用Mybatis参数化查询形式，防止SQL注入
        return CharSequenceUtil.format("{}({})",
                getFunctionName(),
                getFieldExpression());
    }
}