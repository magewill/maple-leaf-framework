package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.Objects;

public class GXUpdateJsonSetStrField extends GXUpdateField<String> {
    private final String path;

    public GXUpdateJsonSetStrField(String tableNameAlias, String fieldName, String path, String value) {
        super(tableNameAlias, fieldName, value);
        this.path = Objects.requireNonNullElse(path, "");
    }

    @Override
    public String getFieldValue() {
        return value != null ? value.toString() : null;
    }

    @Override
    public String updateString() {
        String pathParamName = paramName + "_path";
        String jsonPath = CharSequenceUtil.isEmpty(path) ? "$" : "$." + path;
        this.paramMap.put(pathParamName, jsonPath);

        if (value == null) {
            return GXUpdateJsonDialectSupport.renderJsonSet(tableNameAlias, fieldName, pathParamName, null, false);
        }

        String strValue = value.toString();
        this.paramMap.put(paramName, strValue);

        if (JSONUtil.isTypeJSON(strValue)) {
            return GXUpdateJsonDialectSupport.renderJsonSet(tableNameAlias, fieldName, pathParamName, paramName, true);
        }
        return GXUpdateJsonDialectSupport.renderJsonSet(tableNameAlias, fieldName, pathParamName, paramName, false);
    }
}
