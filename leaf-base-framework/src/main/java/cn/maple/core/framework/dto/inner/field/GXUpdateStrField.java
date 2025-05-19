package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;

/**
 * 字符串类型字段更新实现类
 * <p>
 * 该类实现了字符串类型字段的更新操作，用于构建形如 "field = 'value'" 的SQL更新语句。
 * 通过参数化查询机制确保更新操作安全，防止SQL注入。支持空值处理和线程安全操作。
 * </p>
 *
 * <p>
 * 安全特性：
 * - 使用参数化查询(#{})防止SQL注入
 * - 安全处理null值，避免空指针异常
 * - 线程安全的参数名生成
 * - 字符串值的安全转换
 * </p>
 *
 * <p>
 * 使用场景：
 * - 更新用户名、邮箱等字符串类型信息
 * - 更新状态描述、备注等文本字段
 * - 更新配置项、标识符等字符串参数
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建一个简单的字符串字段更新
 * GXUpdateField<?> usernameField = new GXUpdateStrField("", "username", "张三");
 * String updateClause = usernameField.updateString();
 * // 结果: username = #{dbQueryParamInnerDto.paramMap.update_username_1}
 *
 * // 2. 带表别名的字符串字段更新
 * GXUpdateField<?> emailField = new GXUpdateStrField("user", "email", "zhangsan@example.com");
 * String updateClause = emailField.updateString();
 * // 结果: user.email = #{dbQueryParamInnerDto.paramMap.update_email_1}
 *
 * // 3. 处理可能为null的字符串值
 * String description = getDescriptionFromSomewhere(); // 可能返回null
 * GXUpdateField<?> descField = new GXUpdateStrField("product", "description", description);
 * // 安全处理null值
 *
 * // 4. 在实际应用中与更新构建器结合使用
 * GXModelQueryParamDto paramDto = new GXModelQueryParamDto();
 * paramDto.addUpdateField(new GXUpdateStrField("", "username", "张三"));
 * paramDto.addUpdateField(new GXUpdateStrField("", "email", "zhangsan@example.com"));
 * paramDto.addCondition(new GXConditionEQ("", "id", 1001));
 * mapper.updateByCondition(paramDto);
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * - 即使输入的字符串包含SQL注入攻击字符（如单引号、分号等），也会被安全处理
 * - 当传入null值时，会在SQL中生成 "field = null" 的语句
 * - 在高并发环境下，参数名生成保证线程安全
 * </p>
 *
 * @author magleton
 * @since 1.0.0
 */
public class GXUpdateStrField extends GXUpdateField<String> {

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名或表别名，用于SQL生成时指定表，可为空字符串
     * @param fieldName      字段名，将被转换为下划线格式
     * @param strValue       字符串值，可以为null
     */
    public GXUpdateStrField(String tableNameAlias, String fieldName, String strValue) {
        super(tableNameAlias, fieldName, strValue);
    }

    /**
     * 获取字段值的字符串表示
     * <p>
     * 该方法安全处理可能为null的值，返回其字符串表示或null
     * 在参数化查询中，值会通过paramMap传递给MyBatis
     * </p>
     *
     * @return 字符串值，如果原值为null则返回null
     */
    @Override
    public String getFieldValue() {
        return value != null ? value.toString() : null;
    }

    /**
     * 生成更新字段的SQL片段
     * <p>
     * 重写父类方法，为字符串类型提供特殊处理，包括null值的安全处理
     * </p>
     *
     * @return 格式化的SQL更新语句片段
     */
    @Override
    public String updateString() {
        // 对于null值的特殊处理
        if (value == null) {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = null", fieldName);
            }
            return CharSequenceUtil.format("{}.{} = null", tableNameAlias, fieldName);
        }

        // 非null值使用父类的标准参数化处理
        return super.updateString();
    }
}