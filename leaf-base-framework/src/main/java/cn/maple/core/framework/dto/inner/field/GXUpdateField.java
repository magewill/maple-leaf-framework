package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXDBStringUtils;
import lombok.Getter;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

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
    protected Map<String, Object> paramMap = new ConcurrentHashMap<>();

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
        return CharSequenceUtil.format("{} = #{dbQueryParamInnerDto.paramMap.{}}", qualifiedFieldName(), paramName);
    }

    protected String qualifiedFieldName() {
        String safeFieldName = GXDBStringUtils.validateSqlIdentifier(fieldName, "Update field name");
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return safeFieldName;
        }
        String safeAlias = GXDBStringUtils.validateSqlAlias(tableNameAlias, "Update table alias");
        return CharSequenceUtil.format("{}.{}", safeAlias, safeFieldName);
    }

    public abstract T getFieldValue();
}
