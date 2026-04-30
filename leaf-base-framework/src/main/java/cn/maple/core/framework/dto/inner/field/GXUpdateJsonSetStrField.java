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
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = JSON_SET({}, #{dbQueryParamInnerDto.paramMap.{}}, NULL)",
                        fieldName, fieldName, pathParamName);
            }
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{dbQueryParamInnerDto.paramMap.{}}, NULL)",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName);
        }

        String strValue = value.toString();
        this.paramMap.put(paramName, strValue);

        if (JSONUtil.isTypeJSON(strValue)) {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = JSON_SET({}, #{dbQueryParamInnerDto.paramMap.{}}, CAST(#{dbQueryParamInnerDto.paramMap.{}} as JSON))",
                        fieldName, fieldName, pathParamName, paramName);
            }
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{dbQueryParamInnerDto.paramMap.{}}, CAST(#{dbQueryParamInnerDto.paramMap.{}} as JSON))",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName, paramName);
        } else {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = JSON_SET({}, #{dbQueryParamInnerDto.paramMap.{}}, #{dbQueryParamInnerDto.paramMap.{}})",
                        fieldName, fieldName, pathParamName, paramName);
            }
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{dbQueryParamInnerDto.paramMap.{}}, #{dbQueryParamInnerDto.paramMap.{}})",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName, paramName);
        }
    }
}