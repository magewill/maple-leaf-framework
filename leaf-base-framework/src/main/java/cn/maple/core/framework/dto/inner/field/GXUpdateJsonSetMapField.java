package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * JSON字段Map类型设置更新类
 * <p>
 * 用于更新JSON类型字段中特定路径的Map值。
 * 使用MySQL的JSON_SET函数实现，能够正确处理JSON路径和Map值的转义。
 * </p>
 *
 * @param <T> Map类型参数
 * @author 塵子曦
 */
@Slf4j
public class GXUpdateJsonSetMapField<T extends Map<String, Object>> extends GXUpdateField<String> {
    /**
     * JSON路径，不包含根路径标识($)
     */
    private String path;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName 字段名，会自动转换为下划线格式
     * @param path JSON路径，不包含根路径标识
     * @param value 要设置的Map值
     */
    public GXUpdateJsonSetMapField(String tableNameAlias, String fieldName, String path, T value) {
        super(tableNameAlias, fieldName, value);
        this.path = path;
    }

    /**
     * 获取JSON_SET函数调用表达式
     * <p>
     * 将Map值转换为JSON字符串，并使用JSON_SET函数设置到指定路径。
     * 如果值为null，则设置为NULL。
     * </p>
     *
     * @return JSON_SET函数调用表达式
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        // 构建完整的JSON路径
        String formattedPath;
        if (CharSequenceUtil.isEmpty(path)) {
            formattedPath = "$";
        } else {
            formattedPath = CharSequenceUtil.format("$.{}", path);
        }
        
        // 转义JSON路径
        String escapedPath = GXDBStringEscapeUtils.escapeJsonPath(formattedPath);
        
        if (value == null) {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("JSON_SET({} , '{}' , NULL)", fieldName, escapedPath);
            } else {
                return CharSequenceUtil.format("JSON_SET({}.{} , '{}' , NULL)", tableNameAlias, fieldName, escapedPath);
            }
        }

        String strValue = JSONUtil.toJsonStr(this.value);

        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            // 发现SQL注入风险时抛出异常，而不是继续处理
            log.error("JSON字段Map设置更新时检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("JSON字段Map设置更新时检测到SQL注入风险");
        }
        
        // 使用专门的JSON字符串SQL转义方法，避免双重转义
        String escapedValue = CharSequenceUtil.format("CAST('{}' as JSON)", GXDBStringEscapeUtils.escapeJsonForSql(strValue));
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("JSON_SET({} , '{}' , {})", fieldName, escapedPath, escapedValue);
        } else {
            return CharSequenceUtil.format("JSON_SET({}.{} , '{}' , {})", tableNameAlias, fieldName, escapedPath, escapedValue);
        }
    }
}
