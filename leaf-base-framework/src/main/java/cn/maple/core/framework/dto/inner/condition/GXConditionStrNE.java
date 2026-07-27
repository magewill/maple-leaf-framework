package cn.maple.core.framework.dto.inner.condition;

import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionStrNE extends GXCondition<Object> {
    public GXConditionStrNE(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "!=";
    }

    @Override
    public Object getFieldValue() {
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        // 清除原参数映射并添加带通配符的参数
        this.paramMap.clear();
        this.paramMap.put(paramName, value);
        return value;
    }
}
