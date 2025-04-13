package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public abstract class GXCondition<T> implements Serializable {
    private static final AtomicLong PARAM_COUNTER = new AtomicLong(0);
    
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
        return "condition_" + simplifiedName + "_" + PARAM_COUNTER.incrementAndGet();
    }

    public abstract String getOp();

    public String whereString() {
        String opStr = getOp();
        if (CharSequenceUtil.isEmpty(opStr) || CharSequenceUtil.equals(opStr, GXDataSourceConstant.IGNORE_DATA_FILTER_CONDITION_OP_VALUE)) {
            return "";
        }
        
        // 特殊处理IS NULL和IS NOT NULL情况
        if (("is".equalsIgnoreCase(opStr) || "is not".equalsIgnoreCase(opStr)) && value == null) {
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} {} NULL", getFieldExpression(), opStr);
            }
            return CharSequenceUtil.format("{}.{} {} NULL", tableNameAlias, getFieldExpression(), opStr);
        }
        
        if (CharSequenceUtil.isEmpty(tableNameAlias)) {
            return CharSequenceUtil.format("{} {} #{{}}", getFieldExpression(), opStr, paramName);
        }
        return CharSequenceUtil.format("{}.{} {} #{{}}", tableNameAlias, getFieldExpression(), opStr, paramName);
    }

    public String getFieldExpression() {
        return CharSequenceUtil.toUnderlineCase(fieldExpression);
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
