package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON字段字符串设置更新类
 * <p>
 * 用于更新JSON类型字段中特定路径的字符串值。
 * 使用MySQL的JSON_SET函数实现，能够正确处理JSON路径和值的转义。
 * </p>
 *
 * @author 塵子曦
 */
@Slf4j
public class GXUpdateJsonSetStrField extends GXUpdateField<String> {
    /**
     * JSON路径，不包含根路径标识($)
     */
    private final String path;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName 字段名，会自动转换为下划线格式
     * @param path JSON路径，不包含根路径标识
     * @param value 要设置的值
     */
    public GXUpdateJsonSetStrField(String tableNameAlias, String fieldName, String path, String value) {
        super(tableNameAlias, fieldName, value);
        this.path = path;
    }

    /**
     * 获取JSON_SET函数调用表达式
     * <p>
     * 根据值的类型生成不同的JSON_SET表达式：
     * - null值：设置为NULL
     * - JSON格式：转换为JSON类型
     * - 普通字符串：使用SQL字符串转义
     * </p>
     *
     * @return JSON_SET函数调用表达式
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        // 构建完整的JSON路径
        String jsonPathStr;
        if (CharSequenceUtil.isEmpty(path)) {
            jsonPathStr = "$";
        } else {
            jsonPathStr = CharSequenceUtil.format("$.{}", path);
        }
        
        // 转义JSON路径
        String escapedPath = GXDBStringEscapeUtils.escapeJsonPath(jsonPathStr);
        
        if (value == null) {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("JSON_SET({} , '{}' , NULL)", fieldName, escapedPath);
            } else {
                return CharSequenceUtil.format("JSON_SET({}.{} , '{}' , NULL)", tableNameAlias, fieldName, escapedPath);
            }
        }
        
        String strValue = value.toString();
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            // 发现SQL注入风险时抛出异常，而不是继续处理
            log.error("JSON字段字符串设置更新时检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("JSON字段字符串设置更新时检测到SQL注入风险");
        }
        
        String escapedValue;
        if (JSONUtil.isTypeJSON(strValue)) {
            // 使用专门的JSON字符串SQL转义方法，避免双重转义
            // 直接使用escapeJsonForSql处理JSON字符串，然后用CAST转换为JSON类型
            escapedValue = CharSequenceUtil.format("CAST('{}' as JSON)", GXDBStringEscapeUtils.escapeJsonForSql(strValue));
        } else {
            // 非JSON内容使用SQL字符串转义，并添加单引号
            escapedValue = GXDBStringEscapeUtils.escapeString(strValue);
        }
        
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("JSON_SET({} , '{}' , {})", fieldName, escapedPath, escapedValue);
        } else {
            return CharSequenceUtil.format("JSON_SET({}.{} , '{}' , {})", tableNameAlias, fieldName, escapedPath, escapedValue);
        }
    }
}
