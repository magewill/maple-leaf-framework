package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.Getter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 更新字段抽象基类
 * <p>
 * 该类为SQL更新操作提供基础功能，支持参数化查询以防止SQL注入。
 * 所有更新字段类型都应继承此类并实现特定的字段值处理逻辑。
 * </p>
 * 
 * <p>
 * 安全特性：
 * - 使用MyBatis参数化查询机制(#{})，而非字符串拼接，彻底防止SQL注入
 * - 使用AtomicLong生成唯一参数名，确保线程安全
 * - 自动转换字段名为下划线格式，统一命名规范
 * - 安全处理null值，避免空指针异常
 * - 参数值与SQL语句分离，提高安全性
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 创建字符串类型的更新字段
 * GXUpdateField<?> nameField = new GXUpdateStrField("user", "userName", "张三");
 * 
 * // 2. 创建整数类型的更新字段
 * GXUpdateField<?> statusField = new GXUpdateIntegerField("user", "status", 1);
 * 
 * // 3. 将字段添加到更新列表
 * List<GXUpdateField<?>> updateFields = Arrays.asList(nameField, statusField);
 * 
 * // 4. 创建更新条件
 * List<GXCondition<?>> conditions = Arrays.asList(
 *     new GXConditionEQ("user", "id", 100)
 * );
 * 
 * // 5. 执行更新操作
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("user")
 *     .condition(conditions)
 *     .build();
 * String sql = GXBaseBuilder.updateFieldByCondition(queryParam, updateFields);
 * </pre>
 * </p>
 *
 * @param <T> 字段值的类型参数
 * @author 塵子曦
 */
public abstract class GXUpdateField<T> implements Serializable {
    /**
     * 参数计数器，用于生成唯一的参数名
     * 使用AtomicLong确保线程安全
     */
    private static final AtomicLong PARAM_COUNTER = new AtomicLong(0);

    /**
     * 表名别名，用于多表关联场景
     */
    protected String tableNameAlias;

    /**
     * 字段名，将被转换为下划线格式
     */
    @Getter
    protected String fieldName;

    /**
     * 字段值
     */
    @SuppressWarnings("all")
    protected Object value;

    /**
     * 参数名，用于MyBatis参数化查询
     */
    @Getter
    protected String paramName;

    /**
     * 参数映射，存储参数名和值的映射关系
     */
    @Getter
    protected Map<String, Object> paramMap = new HashMap<>();

    protected GXUpdateField(String tableNameAlias, String fieldName, Object value) {
        this.tableNameAlias = tableNameAlias;
        this.fieldName = CharSequenceUtil.toUnderlineCase(fieldName);
        this.value = value;
        this.paramName = generateParamName(fieldName);
        if (value != null) {
            this.paramMap.put(paramName, value);
        }
    }

    /**
     * 生成唯一的参数名
     * 确保在同一次操作中不会有重复的参数名
     *
     * @param fieldName 字段名
     * @return 参数名
     */
    protected String generateParamName(String fieldName) {
        String simplifiedName = CharSequenceUtil.toUnderlineCase(fieldName);
        return "update_" + simplifiedName + "_" + PARAM_COUNTER.incrementAndGet();
    }

    /**
     * 生成更新字段的SQL片段
     * 使用MyBatis参数化查询方式，防止SQL注入
     *
     * @return SQL片段
     */
    public String updateString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = #{dbQueryParamInnerDto.paramMap.{}}", fieldName, paramName);
        }
        return CharSequenceUtil.format("{}.{} = #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, fieldName, paramName);
    }

    /**
     * 获取字段值的表示形式
     * 子类需要重写此方法以返回适当的参数化表示
     * 注意：此方法在参数化查询中不再直接用于SQL拼接，而是用于特殊情况处理
     *
     * @return 字段值的表示形式
     */
    public abstract T getFieldValue();
}