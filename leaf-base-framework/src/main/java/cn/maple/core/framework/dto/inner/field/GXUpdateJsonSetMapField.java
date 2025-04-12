package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.Map;

public class GXUpdateJsonSetMapField<T extends Map<String, Object>> extends GXUpdateField<String> {
    private String path;

    public GXUpdateJsonSetMapField(String tableNameAlias, String fieldName, String path, T value) {
        super(tableNameAlias, fieldName, value);
        this.path = path;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return JSONUtil.toJsonStr(this.value);
    }
    
    @Override
    public String updateString() {
        // JSON操作需要特殊处理
        // 为JSON路径创建单独的参数
        String pathParamName = paramName + "_path";
        String jsonPath = CharSequenceUtil.isEmpty(path) ? "$" : "$." + path;
        this.paramMap.put(pathParamName, jsonPath);
        
        // 将JSON值作为参数传递
        String jsonValue = JSONUtil.toJsonStr(this.value);
        this.paramMap.put(paramName, jsonValue);
        
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = JSON_SET({}, #{{{}}}, CAST(#{{{}} as JSON))", 
                fieldName, fieldName, pathParamName, paramName);
        }
        return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{{{}}}, CAST(#{{{}} as JSON))", 
            tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName, paramName);
    }
}
