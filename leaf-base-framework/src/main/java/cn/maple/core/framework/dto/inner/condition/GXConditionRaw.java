package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class GXConditionRaw extends GXCondition<String> {
    public GXConditionRaw(String value) {
        super("", "", value);
    }

    @Override
    public String getOp() {
        return "";
    }

    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            log.error("原始条件中检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("原始条件中检测到SQL注入风险");
        }
        
        log.info("~~使用原始条件，请确保数据安全~~");
        return CharSequenceUtil.format("{}", strValue);
    }
}
