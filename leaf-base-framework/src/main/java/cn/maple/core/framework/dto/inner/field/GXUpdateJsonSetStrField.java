package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

public class GXUpdateJsonSetStrField extends GXUpdateField<String> {
    private final String path;

    public GXUpdateJsonSetStrField(String tableNameAlias, String fieldName, String path, String value) {
        super(tableNameAlias, fieldName, value);
        this.path = path;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return value.toString();
    }

    @Override
    public String updateString() {
        // JSON操作需要特殊处理
        if (JSONUtil.isTypeJSON(value.toString())) {
            // 对于JSON值，使用CAST函数
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, '$.{}', CAST(#{dbQueryParamInnerDto.paramMap.{}} as JSON))",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, path, paramName);
        } else {
            // 对于普通字符串值
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, '$.{}', #{dbQueryParamInnerDto.paramMap.{}})",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, path, paramName);
        }
    }
}
