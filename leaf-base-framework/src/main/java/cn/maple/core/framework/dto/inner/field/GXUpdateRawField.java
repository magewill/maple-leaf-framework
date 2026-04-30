package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
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
        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            log.error("原始字段更新时检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("原始字段更新时检测到SQL注入风险");
        }
        return strValue;
    }

    @Override
    public String updateString() {
        log.warn("使用原始SQL值更新字段 {}.{}，请确保已进行SQL注入检查", tableNameAlias, fieldName);
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = {}", fieldName, value);
        }
        return CharSequenceUtil.format("{}.{} = {}", tableNameAlias, fieldName, value);
    }
}
