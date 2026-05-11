package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.Map;

public class GXUpdateMapField<T extends Map<String, Object>> extends GXUpdateField<String> {
    public GXUpdateMapField(String tableNameAlias, String fieldName, T value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getFieldValue() {
        return value != null ? JSONUtil.toJsonStr(value) : null;
    }

    @Override
    public String updateString() {
        if (value == null) {
            return CharSequenceUtil.format("{} = NULL", GXUpdateJsonDialectSupport.qualifiedField(tableNameAlias, fieldName));
        }
        return GXUpdateJsonDialectSupport.renderMapSet(tableNameAlias, fieldName, paramName);
    }
}
