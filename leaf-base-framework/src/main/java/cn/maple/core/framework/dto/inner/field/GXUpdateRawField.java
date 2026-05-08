package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class GXUpdateRawField extends GXUpdateField<String> {
    public GXUpdateRawField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            throw new GXBusinessException("Raw update field value must not be null");
        }
        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            log.error("SQL injection risk detected in raw update field: {}", strValue);
            throw new GXSqlInjectionException("SQL injection risk detected in raw update field");
        }
        return strValue;
    }

    @Override
    public String updateString() {
        log.warn("Raw SQL update field is used: {}.{}", tableNameAlias, fieldName);
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = {}", fieldName, value);
        }
        return CharSequenceUtil.format("{}.{} = {}", tableNameAlias, fieldName, value);
    }
}
