package cn.maple.core.framework.dto.inner.condition.func;

public class GXConditionFuncConcat extends GXConditionFunc<String> {
    public GXConditionFuncConcat(String tableNameAlias, String op, String value, Object... fieldNames) {
        super(tableNameAlias, op, value, fieldNames);
    }

    @Override
    public String getOp() {
        return op;
    }

    /**
     * 获取字段值，添加%用于LIKE查询
     *
     * @return 字段值
     */
    @Override
    public String getFieldValue() {
        // 在参数映射中添加带有%的值，用于LIKE查询
        this.paramMap.put(paramName, value + "%");
        return value + "%";
    }

    @Override
    protected String getFunctionName() {
        return "concat";
    }
}
