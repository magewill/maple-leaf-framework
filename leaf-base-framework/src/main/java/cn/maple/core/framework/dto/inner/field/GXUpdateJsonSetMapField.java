package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.Map;

/**
 * JSON字段Map类型更新操作类
 * <p>
 * 该类用于更新JSON类型字段中的Map数据，支持在指定路径设置或替换JSON对象。
 * 使用MySQL的JSON_SET函数实现，通过参数化查询防止SQL注入。
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 使用MyBatis参数化查询机制(#{})，而非字符串拼接，防止SQL注入
 * - 对JSON路径和JSON值进行分离处理，增强安全性
 * - 自动处理JSON序列化，确保数据格式正确
 * - 支持表别名，适用于多表更新场景
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 假设user_info字段是JSON类型，当前值为: {"contact":{}}
 * 
 * // 1. 创建Map数据
 * Map<String, Object> contactInfo = new HashMap<>();
 * contactInfo.put("email", "test@example.com");
 * contactInfo.put("phone", "123456789");
 * 
 * // 2. 创建JSON更新字段（将Map设置到contact路径）
 * GXUpdateField<?> jsonSetField = new GXUpdateJsonSetMapField<>("user", "user_info", "contact", contactInfo);
 * 
 * // 3. 将字段添加到更新列表
 * List<GXUpdateField<?>> updateFields = new ArrayList<>();
 * updateFields.add(jsonSetField);
 * 
 * // 4. 创建更新条件
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("user", "id", 100));
 * 
 * // 5. 执行更新操作
 * String sql = GXBuildRawSql.updateFieldByCondition("user", updateFields, conditions);
 * // 生成SQL: UPDATE user SET user.user_info = JSON_SET(user.user_info, #{dbQueryParamInnerDto.paramMap.update_user_info_1_path}, CAST(#{dbQueryParamInnerDto.paramMap.update_user_info_1} as JSON)) WHERE user.id = #{dbQueryParamInnerDto.paramMap.condition_id_1}
 * // 更新后的JSON值: {"contact":{"email":"test@example.com","phone":"123456789"}}
 * </pre>
 * </p>
 *
 * @param <T> Map类型参数，必须是Map<String, Object>的子类
 * @author magleton
 */
public class GXUpdateJsonSetMapField<T extends Map<String, Object>> extends GXUpdateField<String> {
    /**
     * JSON路径，指定要更新的JSON属性位置
     * 例如："contact"表示更新JSON对象中的contact属性
     */
    private String path;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表别名，用于多表关联场景，可为空
     * @param fieldName      JSON类型的字段名
     * @param path           JSON路径，指定要更新的属性，如"contact"
     * @param value          Map类型的值，将被转换为JSON并设置到指定路径
     */
    public GXUpdateJsonSetMapField(String tableNameAlias, String fieldName, String path, T value) {
        super(tableNameAlias, fieldName, value);
        this.path = path;
    }

    /**
     * 获取字段值的字符串表示
     * <p>
     * 该方法将Map对象转换为JSON字符串
     * </p>
     * 
     * @return JSON字符串
     */
    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        return JSONUtil.toJsonStr(this.value);
    }

    /**
     * 生成更新字段的SQL片段
     * <p>
     * 使用MySQL的JSON_SET函数和参数化查询，构建安全的JSON字段更新语句
     * </p>
     * 
     * @return 格式化的SET子句SQL片段
     */
    @Override
    public String updateString() {
        // JSON操作需要特殊处理
        // 为JSON路径创建单独的参数
        String pathParamName = paramName + "_path";
        // 构建标准JSON路径格式 $ 或 $.property
        String jsonPath = CharSequenceUtil.isEmpty(path) ? "$" : "$." + path;
        this.paramMap.put(pathParamName, jsonPath);

        // 将JSON值作为参数传递
        String jsonValue = JSONUtil.toJsonStr(this.value);
        this.paramMap.put(paramName, jsonValue);

        // 根据是否有表别名构建不同的SQL片段
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            // 无表别名的情况
            return CharSequenceUtil.format("{} = JSON_SET({}, #{dbQueryParamInnerDto.paramMap.{}}, CAST(#{dbQueryParamInnerDto.paramMap.{}} as JSON))",
                fieldName, fieldName, pathParamName, paramName);
        }
        // 有表别名的情况
        return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{dbQueryParamInnerDto.paramMap.{}}, CAST(#{dbQueryParamInnerDto.paramMap.{}} as JSON))",
            tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName, paramName);
    }
}
