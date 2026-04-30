package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;

public class GXUpdateStrField extends GXUpdateField<String> {
    public GXUpdateStrField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    @Override
    public String getFieldValue() {
        return value != null ? value.toString() : null;
    }

    @Override
    public String updateString() {
        if (value == null) {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = null", fieldName);
            }
            return CharSequenceUtil.format("{}.{} = null", tableNameAlias, fieldName);
        }

        return super.updateString();
    }
}