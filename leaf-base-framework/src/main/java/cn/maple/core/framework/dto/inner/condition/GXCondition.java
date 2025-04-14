package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public abstract class GXCondition<T> implements Serializable {
    /**
     * 可以是一个具体的字段名字  goods_name
     * <p>
     * 也可以是一个函数表达式  concat(g_goods.goods_name , '-' , g_goods.goods_sn)
     */
    protected final String fieldExpression;

    @Setter
    @Getter
    protected String tableNameAlias;

    @SuppressWarnings("all")
    @Getter
    protected Object value;

    @Getter
    protected String paramName;

    @Getter
    protected Map<String, Object> paramMap = new HashMap<>();

    protected GXCondition(String fieldExpression, Object value) {
        this("", fieldExpression, value);
    }

    protected GXCondition(String tableNameAlias, String fieldExpression, Object value) {
        this.tableNameAlias = tableNameAlias;
        this.fieldExpression = fieldExpression;
        this.value = value;
        this.paramName = generateParamName(fieldExpression);
        if (value != null) {
            this.paramMap.put(paramName, value);
        }
    }

    /**
     * 生成唯一的参数名
     *
     * @param fieldExpression 字段表达式
     * @return 参数名
     */
    protected String generateParamName(String fieldExpression) {
        String simplifiedName;
        // 如果是函数表达式，提取一个简化名称
        if (fieldExpression.contains("(")) {
            simplifiedName = "func" + Math.abs(fieldExpression.hashCode());
        } else {
            simplifiedName = CharSequenceUtil.toUnderlineCase(fieldExpression);
        }
        return "condition_" + simplifiedName /*+ "_" + PARAM_COUNTER.incrementAndGet()*/;
    }

    public abstract String getOp();

    public String whereString() {
        String opStr = getOp();
        if (CharSequenceUtil.isEmpty(opStr) && CharSequenceUtil.equals(opStr, GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE)) {
            return "";
        }
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} #{dbQueryParamInnerDto.paramMap.{}}", getFieldExpression(), opStr, /*getFieldValue()*/paramName);
        }
        return CharSequenceUtil.format("{}.{} {} #{dbQueryParamInnerDto.paramMap.{}}", tableNameAlias, getFieldExpression(), opStr, /*getFieldValue()*/paramName);
    }

    public String getFieldExpression() {
        return CharSequenceUtil.toUnderlineCase(fieldExpression);
    }

    public abstract T getFieldValue();
}
