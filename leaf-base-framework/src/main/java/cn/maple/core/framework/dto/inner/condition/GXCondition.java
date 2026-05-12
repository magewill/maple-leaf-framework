package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public abstract class GXCondition<T> implements Serializable {
    private static final AtomicLong PARAM_COUNTER = new AtomicLong(0);
    protected final String fieldExpression;
    @Getter
    protected String tableNameAlias;
    @SuppressWarnings("all")
    @Getter
    protected @Nullable Object value;
    @Getter
    protected String paramName;
    @Getter
    protected Map<String, Object> paramMap = new HashMap<>();

    protected GXCondition(String fieldExpression, @Nullable Object value) {
        this("", fieldExpression, value);
    }

    protected GXCondition(String tableNameAlias, String fieldExpression, @Nullable Object value) {
        setTableNameAlias(tableNameAlias);
        this.fieldExpression = fieldExpression;
        this.value = value;
        this.paramName = generateParamName(fieldExpression);
        if (value != null) {
            this.paramMap.put(paramName, value);
        }
    }

    protected String generateParamName(String fieldExpression) {
        String simplifiedName;
        if (fieldExpression.contains("(")) {
            simplifiedName = "func" + Math.abs(fieldExpression.hashCode());
        } else {
            simplifiedName = CharSequenceUtil.toUnderlineCase(fieldExpression);
            simplifiedName = simplifiedName.replaceAll("[^a-zA-Z0-9_]", "_");
            if (CharSequenceUtil.isBlank(simplifiedName)) {
                simplifiedName = "field";
            }
        }
        return "condition_" + simplifiedName + "_" + PARAM_COUNTER.incrementAndGet();
    }

    public abstract String getOp();

    public void setTableNameAlias(String tableNameAlias) {
        this.tableNameAlias = CharSequenceUtil.isBlank(tableNameAlias)
                ? tableNameAlias
                : GXDBStringEscapeUtils.validateSqlAlias(tableNameAlias, "Condition table alias");
    }

    public String whereString() {
        String opStr = getOp();
        if (CharSequenceUtil.isEmpty(opStr) || CharSequenceUtil.equals(opStr, GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE)) {
            return "";
        }
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} #{dbQueryParamInnerDto.paramMap.{}}", getFieldExpression(), opStr, paramName);
        }
        return CharSequenceUtil.format("{}.{} {} #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, getFieldExpression(), opStr, paramName);
    }

    public String getFieldExpression() {
        return GXDBStringEscapeUtils.validateSqlIdentifier(CharSequenceUtil.toUnderlineCase(fieldExpression), "Condition field");
    }

    public GXConditionSegment toSegment() {
        return new GXConditionSegment(whereString(), new HashMap<>(paramMap));
    }

    public abstract @Nullable T getFieldValue();

    public abstract @Nullable T getFieldOriginalValue();
}
