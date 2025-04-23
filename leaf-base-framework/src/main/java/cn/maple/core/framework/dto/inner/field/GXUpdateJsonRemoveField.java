package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;

/**
 * JSON字段移除操作类
 * <p>
 * 该类用于从JSON类型字段中移除指定路径的数据。
 * 使用MySQL的JSON_REMOVE函数实现，支持参数化查询以防止SQL注入。
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 使用MyBatis参数化查询机制(#{})，而非字符串拼接，防止SQL注入
 * - 路径参数单独处理并通过参数映射传递，增强安全性
 * - 自动处理空路径情况，默认使用根路径($)
 * - 支持表别名，适用于多表更新场景
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 从JSON字段中移除特定路径的数据
 * // 假设user_info字段内容为: {"contact":{"email":"test@example.com","phone":"123456"}}
 * 
 * // 1. 移除contact.email (结果: {"contact":{"phone":"123456"}})
 * GXUpdateField<?> removeField = new GXUpdateJsonRemoveField("user", "user_info", "contact.email");
 * 
 * // 2. 将字段添加到更新列表
 * List<GXUpdateField<?>> updateFields = new ArrayList<>();
 * updateFields.add(removeField);
 * 
 * // 3. 创建更新条件
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("user", "id", 100));
 * 
 * // 4. 执行更新操作
 * String sql = GXBuildRawSql.updateFieldByCondition("user", updateFields, conditions);
 * // 生成SQL: UPDATE user SET user.user_info = JSON_REMOVE(user.user_info, #{dbQueryParamInnerDto.paramMap.update_user_info_1_path}) WHERE user.id = #{dbQueryParamInnerDto.paramMap.condition_id_1}
 * </pre>
 * </p>
 *
 * @author magleton
 */
public class GXUpdateJsonRemoveField extends GXUpdateField<String> {
    /**
     * JSON路径，指定要移除的元素位置
     * 例如："address.city"表示移除address对象中的city属性
     */
    private final String path;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表别名，用于多表关联场景，可为空
     * @param fieldName      JSON类型的字段名
     * @param path           JSON路径，指定要移除的元素位置，如"address.city"
     */
    public GXUpdateJsonRemoveField(String tableNameAlias, String fieldName, String path) {
        super(tableNameAlias, fieldName, null);
        this.path = path;
    }

    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return path;
    }

    @Override
    public String updateString() {
        // JSON操作需要特殊处理
        // 为JSON路径创建单独的参数
        String pathParamName = paramName + "_path";
        String jsonPath = CharSequenceUtil.isEmpty(path) ? "$" : "$." + path;
        this.paramMap.put(pathParamName, jsonPath);

        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = JSON_REMOVE({}, #{dbQueryParamInnerDto.paramMap.{}})", fieldName, fieldName, pathParamName);
        }
        return CharSequenceUtil.format("{}.{} = JSON_REMOVE({}.{}, #{dbQueryParamInnerDto.paramMap.{}})", tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName);
    }

}
