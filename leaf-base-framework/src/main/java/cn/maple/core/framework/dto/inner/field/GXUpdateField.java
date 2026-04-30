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

    protected String generateParamName(String fieldName) {
        String simplifiedName = CharSequenceUtil.toUnderlineCase(fieldName);
        return "update_" + simplifiedName + "_" + PARAM_COUNTER.incrementAndGet();
    }

    public String updateString() {
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} = #{dbQueryParamInnerDto.paramMap.{}}", fieldName, paramName);
        }
        return CharSequenceUtil.format("{}.{} = #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, fieldName, paramName);
    }

    public abstract T getFieldValue();
}