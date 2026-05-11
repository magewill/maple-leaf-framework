package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.Map;
import java.util.Objects;

public class GXUpdateJsonSetMapField<T extends Map<String, Object>> extends GXUpdateField<String> {
    private final String path;

    public GXUpdateJsonSetMapField(String tableNameAlias, String fieldName, String path, T value) {
        super(tableNameAlias, fieldName, value);
        this.path = Objects.requireNonNullElse(path, "");
    }

    @Override
    public String getFieldValue() {
        return value != null ? JSONUtil.toJsonStr(this.value) : null;
    }

    @Override
    public String updateString() {
        String pathParamName = paramName + "_path";
        String jsonPath = CharSequenceUtil.isEmpty(path) ? "$" : "$." + path;
        this.paramMap.put(pathParamName, jsonPath);

        if (value == null) {
            return GXUpdateJsonDialectSupport.renderJsonSet(tableNameAlias, fieldName, pathParamName, null, true);
        }

        String jsonValue = JSONUtil.toJsonStr(this.value);
        this.paramMap.put(paramName, jsonValue);
        return GXUpdateJsonDialectSupport.renderJsonSet(tableNameAlias, fieldName, pathParamName, paramName, true);
    }
}
