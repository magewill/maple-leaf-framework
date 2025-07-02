package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.log4j.Log4j2;

/**
 * 原始SQL条件实现类
 * <p>
 * 该类用于处理需要直接使用原始SQL片段的特殊场景，例如复杂的函数调用或特定数据库语法
 * 由于直接使用原始SQL存在SQL注入风险，该类实现了严格的安全检查机制
 * <p>
 * 安全特性：
 * 1. 对输入的SQL片段进行SQL注入检查
 * 2. 记录警告日志，提醒开发者注意潜在风险
 * 3. 对特殊字符进行转义处理
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个原始SQL条件（谨慎使用！）
 * GXConditionRaw condition = new GXConditionRaw("DATE(created_at) = CURDATE()");
 * String whereClause = condition.whereString();
 * // 结果: DATE(created_at) = CURDATE()
 *
 * // 在实际应用中与查询构建器结合使用
 * GXModelQueryParamDto paramDto = new GXModelQueryParamDto();
 * paramDto.addCondition(condition);
 * List<UserEntity> users = mapper.findByCondition(paramDto);
 * </pre>
 * <p>
 * 安全警告：
 * 1. 仅在绝对必要时使用此类，优先考虑参数化查询
 * 2. 确保传入的SQL片段不包含用户输入，或已经过严格的验证和转义
 * 3. 使用此类可能会绕过框架的SQL注入防护机制，增加安全风险
 */
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
    public String whereString() {
        // 使用原始值，不参数化（需要确保已经进行了SQL注入检查）
        log.warn("使用原始SQL条件，请确保已进行SQL注入检查: {}", value);
        return value.toString();
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
            // 此方法不再用于SQL拼接，而是用于特殊情况处理
            log.warn("原始条件中检测到SQL注入风险，~~请确保使用GXDBStringEscapeUtils.escapeSql(str)函数对用户传入的数据进行了SQL注入检测~~: {}", strValue);
            //throw new GXSqlInjectionException("原始条件中检测到SQL注入风险: " + strValue);
        }

        // 使用更全面的检查 - 检查是否包含可疑的SQL关键字
        if (strValue.matches("(?i).*(select|insert|update|delete|drop|alter|exec|union|into|outfile).*")) {
            log.warn("原始条件中包含可疑的SQL关键字，~~请确保使用GXDBStringEscapeUtils.escapeSql(str)函数对用户传入的数据进行了SQL注入检测~~: {}", strValue);
            //throw new GXSqlInjectionException("原始条件中包含可疑的SQL关键字: " + strValue);
        }

        log.warn("~~使用原始条件，请确保数据安全: {}~~", strValue);
        return CharSequenceUtil.format("{}", strValue);
    }

    @Override
    public String getFieldOriginalValue() {
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
