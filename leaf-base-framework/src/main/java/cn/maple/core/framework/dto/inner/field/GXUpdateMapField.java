package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.Map;

/**
 * Map类型字段更新操作类
 * <p>
 * 该类用于将Map类型数据更新到数据库JSON类型字段中。
 * 使用MyBatis的参数化查询和类型处理器，确保数据安全转换为JSON格式。
 * </p>
 *
 * <p>
 * 安全特性：
 * - 使用MyBatis参数化查询机制(#{})，而非字符串拼接，防止SQL注入
 * - 利用MyBatis的类型处理器自动处理Map到JSON的转换
 * - 自动处理JSON序列化，确保数据格式正确
 * - 支持表别名，适用于多表更新场景
 * - 线程安全的参数名生成，避免并发问题
 * - 安全处理null值，避免空指针异常
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建Map数据
 * Map<String, Object> userInfo = new HashMap<>();
 * userInfo.put("name", "张三");
 * userInfo.put("age", 25);
 * userInfo.put("contact", new HashMap<String, Object>() {{
 *     put("email", "zhangsan@example.com");
 *     put("phone", "13800138000");
 * }});
 *
 * // 2. 创建Map更新字段
 * GXUpdateField<?> mapField = new GXUpdateMapField<>("user", "user_info", userInfo);
 *
 * // 3. 将字段添加到更新列表
 * List<GXUpdateField<?>> updateFields = new ArrayList<>();
 * updateFields.add(mapField);
 *
 * // 4. 创建更新条件
 * List<GXCondition<?>> conditions = new ArrayList<>();
 * conditions.add(new GXConditionEQ("user", "id", 100));
 *
 * // 5. 执行更新操作
 * String sql = GXBuildRawSql.updateFieldByCondition("user", updateFields, conditions);
 * // 生成SQL: UPDATE user SET user.user_info = CAST(#{dbQueryParamInnerDto.paramMap.update_user_info_1, javaType=java.util.Map,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS JSON) WHERE user.id = #{dbQueryParamInnerDto.paramMap.condition_id_1}
 * </pre>
 * </p>
 *
 * @param <T> Map类型参数，必须是Map<String, Object>的子类
 * @author magleton
 */
public class GXUpdateMapField<T extends Map<String, Object>> extends GXUpdateField<String> {
    /**
     * 构造函数
     *
     * @param tableNameAlias 表别名，用于多表关联场景，可为空
     * @param fieldName      要更新的字段名
     * @param value          Map类型的值，将被转换为JSON
     */
    public GXUpdateMapField(String tableNameAlias, String fieldName, T value) {
        super(tableNameAlias, fieldName, value);
    }

    /**
     * 获取字段值的字符串表示
     * <p>
     * 该方法将Map对象转换为JSON字符串
     * </p>
     *
     * @return JSON字符串，如果值为null则返回null
     */
    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        // 在参数化查询中，值会通过paramMap传递给MyBatis
        return value != null ? JSONUtil.toJsonStr(value) : null;
    }

    /**
     * 生成更新字段的SQL片段
     * <p>
     * 使用MyBatis参数化查询和类型处理器，将Map对象安全地转换为JSON并更新到数据库
     * </p>
     *
     * @return 格式化的SET子句SQL片段
     */
    @Override
    public String updateString() {
        // 处理null值情况
        if (value == null) {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = NULL", fieldName);
            }
            return CharSequenceUtil.format("{}.{} = NULL", tableNameAlias, fieldName);
        }

        // 根据是否有表别名构建不同的SQL片段
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            // 无表别名的情况
            return CharSequenceUtil.format("{} = CAST(#{dbQueryParamInnerDto.paramMap.{}, javaType=java.util.Map,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS JSON)", fieldName, paramName);
        }
        // 有表别名的情况
        return CharSequenceUtil.format("{}.{} =  CAST(#{dbQueryParamInnerDto.paramMap.{}, javaType=java.util.Map,typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler} AS JSON)", tableNameAlias, fieldName, paramName);
    }
}