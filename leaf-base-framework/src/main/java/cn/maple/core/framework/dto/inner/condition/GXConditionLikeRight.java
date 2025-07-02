package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

/**
 * 右模糊匹配查询条件实现类
 * <p>
 * 该类实现了字符串的右模糊匹配查询条件，用于构建形如 "field LIKE 'value%'" 的SQL条件。
 * 通过参数化查询和SQL注入检查双重机制确保查询安全。
 * </p>
 *
 * <p>
 * 安全特性：
 * <ul>
 *   <li>使用MyBatis参数化查询(#{})替代字符串拼接，彻底防止SQL注入攻击</li>
 *   <li>对查询值进行SQL注入检查，发现可疑内容立即抛出异常</li>
 *   <li>自动处理LIKE查询中的通配符，避免手动拼接的风险</li>
 * </ul>
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 创建一个简单的右模糊匹配查询条件（如：name LIKE '张%'）
 * GXConditionLikeRight condition = new GXConditionLikeRight("", "name", "张");
 * String whereClause = condition.whereString();
 * // 结果: name like #{dbQueryParamInnerDto.paramMap.condition_name_1}
 * // 参数值会被处理为: 张%
 *
 * // 带表别名的右模糊匹配查询条件
 * GXConditionLikeRight condition = new GXConditionLikeRight("user", "name", "张");
 * String whereClause = condition.whereString();
 * // 结果: user.name like #{dbQueryParamInnerDto.paramMap.condition_name_1}
 *
 * // 在实际应用中与查询构建器结合使用
 * GXModelQueryParamDto paramDto = new GXModelQueryParamDto();
 * paramDto.addCondition(new GXConditionLikeRight("", "name", "张"));
 * paramDto.addCondition(new GXConditionEQ("", "status", 1));
 * List<UserEntity> users = mapper.findByCondition(paramDto);
 * </pre>
 * </p>
 *
 * <p>
 * 性能与最佳实践：
 * <ul>
 *   <li>使用参数化查询允许数据库缓存执行计划，提高性能</li>
 *   <li>右模糊匹配（前缀匹配）可以有效利用索引，比全模糊匹配性能更好</li>
 *   <li>适用于需要按前缀搜索的场景，如姓名、标题等前缀搜索</li>
 * </ul>
 * </p>
 *
 * <p>
 * 注意事项：
 * <ul>
 *   <li>当搜索值为空字符串时，会生成 "field LIKE '%'" 条件，可能导致全表扫描</li>
 *   <li>如果搜索值中已包含通配符字符（如%或_），不会进行特殊处理，可能导致意外的匹配结果</li>
 *   <li>对于需要转义LIKE通配符的场景，应在传入值前进行适当处理</li>
 * </ul>
 * </p>
 *
 * @author 塵渊
 */
public class GXConditionLikeRight extends GXCondition<String> {
    /**
     * 构造函数
     *
     * @param tableNameAlias 表别名，如"user"、"t"等，可以为空
     * @param fieldName      字段名，如"name"、"title"等
     * @param value          搜索值，将自动添加右侧通配符
     */
    public GXConditionLikeRight(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 获取操作符
     *
     * @return 返回"like"操作符
     */
    @Override
    public String getOp() {
        return "like";
    }

    /**
     * 获取字段值并填充参数映射
     * <p>
     * 该方法处理参数映射，添加右侧通配符，并进行SQL注入检查。
     * 虽然方法返回空字符串，但会填充paramMap用于MyBatis参数化查询。
     * </p>
     *
     * @return 空字符串，实际值存储在paramMap中
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        // 进行SQL注入检查，确保输入安全
        if (GXDBStringEscapeUtils.check(value.toString())) {
            throw new GXSqlInjectionException("检测到SQL注入风险：右模糊匹配条件中包含可疑字符或SQL关键字");
        }
        // 清除原参数映射并添加带通配符的参数（右侧添加%）
        this.paramMap.clear();
        this.paramMap.put(paramName, value + "%");
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
            throw new GXSqlInjectionException("SQL注入异常");
        }

        // 使用escapeSqlForLike方法进行更全面的SQL转义，特别适合LIKE查询
        String escapedValue = GXDBStringEscapeUtils.escapeSqlForLike(strValue);

        // 根据内容选择合适的引号包裹方式
        if (CharSequenceUtil.contains(escapedValue, "''")) {
            // 如果包含已转义的单引号，使用双引号包裹
            return CharSequenceUtil.format("\"{}%\"", escapedValue);
        } else {
            // 否则使用单引号包裹
            return CharSequenceUtil.format("'{}%'", escapedValue);
        }
    }
}
