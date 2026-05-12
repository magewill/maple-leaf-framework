package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringUtils;
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
        try {
            return GXDBStringUtils.normalizeAndValidateRawSqlExpression(strValue);
        } catch (GXSqlInjectionException ex) {
            log.error("SQL injection risk detected in raw update field: {}", strValue);
            throw new GXSqlInjectionException("SQL injection risk detected in raw update field");
        }
    }

    @Override
    public String updateString() {
        log.warn("Raw SQL update field is used: {}.{}", tableNameAlias, fieldName);
        String checkedValue = getFieldValue();
        return CharSequenceUtil.format("{} = {}", qualifiedFieldName(), checkedValue);
    }
}
