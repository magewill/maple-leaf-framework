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

    /**
     * 获取原始条件值的字符串表示
     * <p>
     * 该方法处理原始SQL条件，这是一种高风险的操作，因此实现了严格的安全检查：
     * 1. 检查值是否为null
     * 2. 使用GXDBStringEscapeUtils.check进行全面的SQL注入风险检测
     * 3. 记录警告日志，提醒开发者注意安全风险
     * </p>
     * <p>
     * 注意：原始条件应当谨慎使用，因为它们可能引入SQL注入风险
     * </p>
     *
     * @return 原始条件的字符串表示
     * @throws GXSqlInjectionException 如果检测到SQL注入风险
     */
    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        
        // 检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            log.error("原始条件中检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("原始条件中检测到SQL注入风险: " + strValue);
        }
        
        // 使用更全面的检查 - 检查是否包含可疑的SQL关键字
        if (strValue.matches("(?i).*(select|insert|update|delete|drop|alter|exec|union|into|outfile).*")) {
            log.error("原始条件中包含可疑的SQL关键字: {}", strValue);
            throw new GXSqlInjectionException("原始条件中包含可疑的SQL关键字: " + strValue);
        }
        
        log.warn("~~使用原始条件，请确保数据安全: {}~~", strValue);
        return CharSequenceUtil.format("{}", strValue);
    }
}
