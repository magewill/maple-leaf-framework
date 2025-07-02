package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

/**
 * 全模糊匹配查询条件实现类
 * <p>
 * 该类实现了字符串的全模糊匹配查询条件，用于构建形如 "field LIKE '%value%'" 的SQL条件
 * 通过参数化查询和SQL注入检查双重机制确保查询安全
 * <p>
 * 安全特性：
 * 1. 使用参数化查询而非字符串拼接，有效防止SQL注入攻击
 * 2. 在参数化查询前进行SQL注入检查，提供额外安全保障
 * 3. 自动处理LIKE查询中的通配符，避免手动拼接的风险
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个简单的全模糊匹配查询条件
 * GXConditionLikeFull condition = new GXConditionLikeFull("", "username", "张");
 * String whereClause = condition.whereString();
 * // 结果: username like #{dbQueryParamInnerDto.paramMap.condition_username_1}
 * // 参数值会被处理为: %张%
 *
 * // 带表别名的全模糊匹配查询条件
 * GXConditionLikeFull condition = new GXConditionLikeFull("user", "username", "张");
 * String whereClause = condition.whereString();
 * // 结果: user.username like #{dbQueryParamInnerDto.paramMap.condition_username_1}
 *
 * // 在实际应用中与查询构建器结合使用
 * GXModelQueryParamDto paramDto = new GXModelQueryParamDto();
 * paramDto.addCondition(new GXConditionLikeFull("", "username", "张"));
 * paramDto.addCondition(new GXConditionEQ("", "status", 1));
 * List<UserEntity> users = mapper.findByCondition(paramDto);
 * </pre>
 * <p>
 * 安全说明：
 * 该类会检查输入值是否包含SQL注入攻击字符，如果检测到潜在风险，将抛出GXSqlInjectionException异常
 */
public class GXConditionLikeFull extends GXCondition<String> {
    public GXConditionLikeFull(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "like";
    }

    @Override
    public String getFieldValue() {
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("SQL注入异常");
        }
        // 清除原参数映射并添加带通配符的参数
        this.paramMap.clear();
        this.paramMap.put(paramName, "%" + value + "%");
        return "";
    }

    @Override
    public String getFieldOriginalValue() {
        if (value == null) {
            return "NULL";
        }

        String strValue = value.toString();
        // 首先检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("模糊匹配条件中检测到SQL注入风险: " + strValue);
        }

        // 使用escapeSqlForLike方法进行更全面的SQL转义，特别适合LIKE查询
        String escapedValue = GXDBStringEscapeUtils.escapeSqlForLike(strValue);

        // 根据内容选择合适的引号包裹方式
        if (CharSequenceUtil.contains(escapedValue, "''")) {
            // 如果包含已转义的单引号，使用双引号包裹
            return CharSequenceUtil.format("\"%{}%\"", escapedValue);
        } else {
            // 否则使用单引号包裹
            return CharSequenceUtil.format("'%{}%'", escapedValue);
        }
    }
}
