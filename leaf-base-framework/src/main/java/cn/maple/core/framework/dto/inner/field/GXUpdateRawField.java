package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class GXUpdateRawField extends GXUpdateField<String> {
    /**
     * 创建一个原始SQL更新字段
     *
     * @param tableNameAlias 表别名
     * @param fieldName      字段名
     * @param strValue       字段值
     */
    public GXUpdateRawField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    @Override
    public String getFieldValue() {
        String strValue = value.toString();
        if (GXDBStringEscapeUtils.check(strValue)) {
            // 发现SQL注入风险时抛出异常，而不是继续处理
            log.error("原始字段更新时检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("原始字段更新时检测到SQL注入风险");
        }
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return strValue;
    }

    @Override
    public String updateString() {
        // 使用原始值，不参数化（需要确保已经进行了SQL注入检查）
        log.warn("使用原始SQL值更新字段 {}.{}，请确保已进行SQL注入检查", tableNameAlias, fieldName);
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = {}", fieldName, value);
        }
        return CharSequenceUtil.format("{}.{} = {}", tableNameAlias, fieldName, value);
    }
}
