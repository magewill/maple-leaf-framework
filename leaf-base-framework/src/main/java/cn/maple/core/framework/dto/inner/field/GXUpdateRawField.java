package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 原始SQL片段字段更新类
 * <p>
 * 用于处理原始SQL片段的字段更新操作。此类不对值进行转义，
 * 因此使用时需要特别小心，确保传入的值不包含SQL注入风险。
 * 主要用于已经构建好且确认安全的SQL表达式。
 * </p>
 *
 * @author 塵子曦
 */
@Slf4j
public class GXUpdateRawField extends GXUpdateField<String> {
    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName 字段名，会自动转换为下划线格式
     * @param strValue 原始SQL片段
     */
    public GXUpdateRawField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    /**
     * 获取原始字段值
     * <p>
     * 由于这是原始字段，不进行转义处理，但会检查SQL注入风险。
     * 如果检测到风险，会抛出异常而不是继续处理。
     * </p>
     *
     * @return 原始字段值
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            // 发现SQL注入风险时抛出异常，而不是继续处理
            log.error("原始字段更新时检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("原始字段更新时检测到SQL注入风险");
        }
        
        // 原始字段不进行转义，直接返回
        return CharSequenceUtil.format("{}", strValue);
    }
}
