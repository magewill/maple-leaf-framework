package cn.maple.core.framework.dto.inner.condition.func;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.List;
import java.util.stream.Collectors;

public class GXConditionFuncJsonOverlaps extends GXConditionFunc<String> {
    private final Object values;

    private String jsonPath;

    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, List<Object> values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, List<Object> values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, Dict values) {
        this(tableNameAlias, jsonField, values, "");
    }

    public GXConditionFuncJsonOverlaps(String tableNameAlias, String jsonField, Dict values, String jsonPath) {
        super(tableNameAlias, jsonField, "", null);
        this.values = values;
        this.jsonPath = jsonPath;
    }

    @Override
    public String getOp() {
        return op;
    }

    /**
     * 获取字段表达式，用于JSON_OVERLAPS函数的参数
     * 使用Mybatis参数化查询形式#{dbQueryParamInnerDto.paramMap.xxx}防止SQL注入
     *
     * @return 安全的字段表达式字符串
     */
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
        String format = "`{}`.`{}`->#{dbQueryParamInnerDto.paramMap.{}_path}, CAST(#{dbQueryParamInnerDto.paramMap.{}} AS JSON)";
        return CharSequenceUtil.format(format, tableNameAlias, getOp(), paramName, paramName);
    }

    /**
     * 获取字段值，将值列表转换为JSON数组格式的字符串
     * 根据值的类型决定是否添加引号
     *
     * @return JSON数组格式的字符串
     */
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

    /**
     * 获取MySQL JSON函数名
     *
     * @return JSON_OVERLAPS函数名
     */
    @Override
    protected String getFunctionName() {
        return "JSON_OVERLAPS";
    }

    /**
     * 生成WHERE子句字符串，使用Mybatis参数化查询形式防止SQL注入
     * 重写父类方法，因为JSON_OVERLAPS函数的参数结构与基类不同
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
        return CharSequenceUtil.format("{}({})",
                getFunctionName(),
                getFieldExpression());
    }
}