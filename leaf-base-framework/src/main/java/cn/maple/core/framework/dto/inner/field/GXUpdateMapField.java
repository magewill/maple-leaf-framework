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
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        // 在参数化查询中，值会通过paramMap传递给MyBatis
        return JSONUtil.toJsonStr(value);
    }

    /**
     * 生成更新字段的SQL片段
     * 使用MyBatis参数化查询方式，防止SQL注入
     *
     * @return SQL片段
     */
    public String updateString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = CAST(#{dbQueryParamInnerDto.paramMap.{}, javaType=java.util.Map,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS JSON)", fieldName, paramName);
        }
        return CharSequenceUtil.format("{}.{} =  CAST(#{dbQueryParamInnerDto.paramMap.{}, javaType=java.util.Map,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS JSON)", tableNameAlias, fieldName, paramName);
    }
}
