package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;

public class GXUpdateJsonRemoveField extends GXUpdateField<String> {
    private final String path;

    public GXUpdateJsonRemoveField(String tableNameAlias, String fieldName, String path) {
        super(tableNameAlias, fieldName, null);
        this.path = path;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return path;
    }

    @Override
    public String updateString() {
        // JSON操作需要特殊处理
        // 为JSON路径创建单独的参数
        String pathParamName = paramName + "_path";
        String jsonPath = CharSequenceUtil.isEmpty(path) ? "$" : "$." + path;
        this.paramMap.put(pathParamName, jsonPath);

        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = JSON_REMOVE({}, #{dbQueryParamInnerDto.paramMap.{}})", fieldName, fieldName, pathParamName);
        }
        return CharSequenceUtil.format("{}.{} = JSON_REMOVE({}.{}, #{dbQueryParamInnerDto.paramMap.{}})", tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName);
    }

}
