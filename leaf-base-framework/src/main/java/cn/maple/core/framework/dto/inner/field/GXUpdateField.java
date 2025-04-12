package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.Getter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public abstract class GXUpdateField<T> implements Serializable {
    private static final AtomicLong PARAM_COUNTER = new AtomicLong(0);
    
    protected String tableNameAlias;

    @Getter
    protected String fieldName;

    @SuppressWarnings("all")
    protected Object value;
    
    @Getter
    protected String paramName;
    
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
     *
     * @param fieldName 字段名
     * @return 参数名
     */
    protected String generateParamName(String fieldName) {
        return "update_" + CharSequenceUtil.toUnderlineCase(fieldName) + "_" + PARAM_COUNTER.incrementAndGet();
    }

    /**
     * 生成更新字段的SQL片段
     * 使用MyBatis参数化查询方式，防止SQL注入
     *
     * @return SQL片段
     */
    public String updateString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = #{{}}", fieldName, paramName);
        }
        return CharSequenceUtil.format("{}.{} = #{{}}", tableNameAlias, fieldName, paramName);
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