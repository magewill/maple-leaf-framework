package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class GXConditionRaw extends GXCondition<String> {
    private final boolean useRawValue;
    
    /**
     * 创建一个原始SQL条件
     * 
     * @param value 条件值
     */
    public GXConditionRaw(String value) {
        this(value, false);
    }
    
    /**
     * 创建一个原始SQL条件
     * 
     * @param value 条件值
     * @param useRawValue 是否使用原始值（不参数化）
     */
    public GXConditionRaw(String value, boolean useRawValue) {
        super("", "", value);
        this.useRawValue = useRawValue;
    }

    @Override
    public String getOp() {
        return "";
    }
    
    @Override
    public String whereString() {
        if (useRawValue) {
            // 使用原始值，不参数化（需要确保已经进行了SQL注入检查）
            log.warn("使用原始SQL条件，请确保已进行SQL注入检查: {}", value);
            return value.toString();
        } else {
            // 使用参数化查询
            this.paramMap.clear();
            this.paramMap.put(paramName, value);
            return "#{" + paramName + "}";
        }
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        log.info("~~请确保使用GXDBStringEscapeUtils.check(str)函数对用户传入的数据进行了SQL注入检测~~");
        return value.toString();
    }
}
