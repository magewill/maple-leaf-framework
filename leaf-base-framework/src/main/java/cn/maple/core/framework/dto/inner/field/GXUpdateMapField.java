package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

import java.util.Map;

public class GXUpdateMapField<T extends Map<String, Object>> extends GXUpdateField<String> {
    public GXUpdateMapField(String tableNameAlias, String fieldName, T value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = JSONUtil.toJsonStr(value);
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            // 发现SQL注入风险时抛出异常，而不是继续处理
            throw new GXSqlInjectionException("Map字段更新时检测到SQL注入风险");
        }
        
        // JSON内容使用JSON专用转义并保持SQL标准单引号转义
        // String escapedJson = GXDBStringEscapeUtils.escapeJson(strValue);
        // 再对转义后的JSON进行SQL转义（使用SQL标准的单引号转义）
        return CharSequenceUtil.format("'{}'", GXDBStringEscapeUtils.escapeSql(strValue));
    }
}
