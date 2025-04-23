package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.extern.log4j.Log4j2;

/**
 * 原始SQL字段更新操作类
 * <p>
 * 该类用于特殊场景下需要使用原始SQL表达式更新字段的情况。
 * 与其他更新字段类不同，此类不使用参数化查询，而是直接将值拼接到SQL中，
 * 因此在使用时需要格外小心SQL注入风险。
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 使用GXDBStringEscapeUtils进行SQL注入检查，发现风险时抛出异常
 * - 记录警告日志，提醒开发者注意SQL注入风险
 * - 仅在特殊场景下使用，如需要使用数据库函数或表达式时
 * </p>
 * 
 * <p>
 * 使用场景：
 * 1. 需要使用数据库函数：如NOW(), CURRENT_TIMESTAMP(), UUID()等
 * 2. 需要使用数据库表达式：如字段自增/自减 count = count + 1
 * 3. 需要使用子查询作为更新值
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 使用数据库函数更新时间字段
 * GXUpdateField<?> updateTimeField = new GXUpdateRawField("user", "last_login_time", "NOW()");
 * 
 * // 2. 字段值自增
 * GXUpdateField<?> counterField = new GXUpdateRawField("article", "view_count", "view_count + 1");
 * 
 * // 3. 使用子查询作为更新值
 * GXUpdateField<?> subQueryField = new GXUpdateRawField("user", "department_name", 
 *     "(SELECT name FROM department WHERE id = user.department_id)");
 * 
 * // 4. 将字段添加到更新列表
 * List<GXUpdateField<?>> updateFields = new ArrayList<>();
 * updateFields.add(updateTimeField);
 * 
 * // 5. 创建更新条件
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("user", "id", 100));
 * 
 * // 6. 执行更新操作
 * String sql = GXBuildRawSql.updateFieldByCondition("user", updateFields, conditions);
 * // 生成SQL: UPDATE user SET user.last_login_time = NOW() WHERE user.id = #{dbQueryParamInnerDto.paramMap.condition_id_1}
 * </pre>
 * </p>
 * 
 * <p>
 * 安全警告：
 * - 此类不使用参数化查询，存在潜在的SQL注入风险
 * - 仅在必要时使用，优先考虑其他安全的更新字段类
 * - 确保传入的值已经过严格的SQL注入检查
 * - 不要将用户输入直接传入此类，应先进行过滤和转义
 * </p>
 *
 * @author magleton
 */
@Log4j2
public class GXUpdateRawField extends GXUpdateField<String> {
    /**
     * 创建一个原始SQL更新字段
     * <p>
     * 注意：使用此构造函数时，需确保strValue不包含SQL注入风险
     * </p>
     *
     * @param tableNameAlias 表别名，用于多表关联场景，可为空
     * @param fieldName      要更新的字段名
     * @param strValue       原始SQL表达式或值，如"NOW()", "column + 1"等
     */
    public GXUpdateRawField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    /**
     * 获取字段值的字符串表示
     * <p>
     * 该方法会检查SQL注入风险，发现风险时抛出异常
     * </p>
     * 
     * @return 原始SQL表达式或值
     * @throws GXSqlInjectionException 当检测到SQL注入风险时抛出
     */
    @Override
    public String getFieldValue() {
        String strValue = value.toString();
        // 检查SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            // 发现SQL注入风险时抛出异常，而不是继续处理
            log.error("原始字段更新时检测到SQL注入风险: {}", strValue);
            throw new GXSqlInjectionException("原始字段更新时检测到SQL注入风险");
        }
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return strValue;
    }

    /**
     * 生成更新字段的SQL片段
     * <p>
     * 直接将原始值拼接到SQL中，不使用参数化查询
     * </p>
     * <p>
     * 安全警告：此方法不使用参数化查询，存在潜在的SQL注入风险
     * </p>
     * 
     * @return 格式化的SET子句SQL片段
     */
    @Override
    public String updateString() {
        // 使用原始值，不参数化（需要确保已经进行了SQL注入检查）
        log.warn("使用原始SQL值更新字段 {}.{}，请确保已进行SQL注入检查", tableNameAlias, fieldName);
        // 根据是否有表别名构建不同的SQL片段
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            // 无表别名的情况
            return CharSequenceUtil.format("{} = {}", fieldName, value);
        }
        // 有表别名的情况
        return CharSequenceUtil.format("{}.{} = {}", tableNameAlias, fieldName, value);
    }
}
