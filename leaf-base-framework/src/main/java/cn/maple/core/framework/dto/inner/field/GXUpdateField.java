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