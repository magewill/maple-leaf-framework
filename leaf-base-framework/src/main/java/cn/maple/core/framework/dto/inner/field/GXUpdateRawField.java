package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class GXUpdateRawField extends GXUpdateField<String> {
    private final boolean useRawValue;

    /**
     * 创建一个原始SQL更新字段
     *
     * @param tableNameAlias 表别名
     * @param fieldName 字段名
     * @param strValue 字段值
     */
    public GXUpdateRawField(String tableNameAlias, String fieldName, String strValue) {
        this(tableNameAlias, fieldName, strValue, false);
    }

    /**
     * 创建一个原始SQL更新字段
     *
     * @param tableNameAlias 表别名
     * @param fieldName 字段名
     * @param strValue 字段值
     * @param useRawValue 是否使用原始值（不参数化）
     */
    public GXUpdateRawField(String tableNameAlias, String fieldName, String strValue, boolean useRawValue) {
        super(tableNameAlias, fieldName, strValue);
        this.useRawValue = useRawValue;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return value.toString();
    }

    @Override
    public String updateString() {
        if (useRawValue) {
            // 使用原始值，不参数化（需要确保已经进行了SQL注入检查）
            log.warn("使用原始SQL值更新字段 {}.{}，请确保已进行SQL注入检查", tableNameAlias, fieldName);
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = {}", fieldName, value);
            }
            return CharSequenceUtil.format("{}.{} = {}", tableNameAlias, fieldName, value);
        } else {
            // 使用参数化查询
            return super.updateString();
        }
    }
}
