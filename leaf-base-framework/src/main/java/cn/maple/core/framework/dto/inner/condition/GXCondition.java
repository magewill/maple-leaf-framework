package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.constant.GXDataSourceConstant;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQL条件构建抽象基类
 * <p>
 * 该类为SQL条件构建提供基础功能，支持参数化查询以防止SQL注入。
 * 所有条件类型都应继承此类并实现特定的条件逻辑。
 * </p>
 *
 * @param <T> 字段值的类型参数
 * @author 塵子曦
 */
public abstract class GXCondition<T> implements Serializable {
    /**
     * 参数计数器，用于生成唯一的参数名
     * 使用AtomicLong确保线程安全
     */
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
    /**
     * 生成唯一的参数名
     * 使用原子计数器确保在并发环境下参数名的唯一性
     *
     * @param fieldExpression 字段表达式
     * @return 唯一的参数名
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
