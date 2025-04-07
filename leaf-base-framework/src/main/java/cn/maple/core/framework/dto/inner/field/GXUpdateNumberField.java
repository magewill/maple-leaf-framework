package cn.maple.core.framework.dto.inner.field;

/**
 * 数字类型字段更新类
 * <p>
 * 用于处理数字类型字段的更新操作，直接返回数字值而不需要额外的转义处理。
 * </p>
 *
 * @author 塵子曦
 */
public class GXUpdateNumberField extends GXUpdateField<Number> {
    /**
     * 构造函数
     *
     * @param tableNameAlias 表名别名，可以为空
     * @param fieldName 字段名，会自动转换为下划线格式
     * @param numberValue 数字值
     */
    public GXUpdateNumberField(String tableNameAlias, String fieldName, int numberValue) {
        super(tableNameAlias, fieldName, numberValue);
    }

    /**
     * 获取数字字段值
     * <p>
     * 直接返回数字值，不需要额外的转义处理。
     * 数字类型通常不存在SQL注入风险。
     * </p>
     *
     * @return 数字字段值
     */
    @Override
    public Number getFieldValue() {
        return (Number) value;
    }
}
