package cn.maple.core.framework.dto.inner.op;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("all")
public abstract class GXDbJoinValue extends GXDbJoinOp {
    private static final AtomicLong PARAM_COUNTER = new AtomicLong(0);
    
    /**
     * 表别名
     */
    private final String tableNameAlias;

    /**
     * 表字段
     */
    private final String fieldName;

    /**
     * 字段的值
     * 用于查询固定值的场景
     */
    private Object fieldValue;
    
    /**
     * 参数名称
     */
    private String paramName;
    
    /**
     * 参数映射
     */
    private Map<String, Object> paramMap = new HashMap<>();

    public GXDbJoinValue(String tableNameAlias, String fieldName, Object fieldValue) {
        this.tableNameAlias = tableNameAlias;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.paramName = generateParamName(fieldName);
        if (fieldValue != null) {
            this.paramMap.put(paramName, fieldValue);
        }
    }
    
    /**
     * 生成唯一的参数名
     *
     * @param fieldName 字段名
     * @return 参数名
     */
    protected String generateParamName(String fieldName) {
        return "join_" + CharSequenceUtil.toUnderlineCase(fieldName) + "_" + PARAM_COUNTER.incrementAndGet();
    }
    
    /**
     * 获取参数映射
     *
     * @return 参数映射
     */
    public Map<String, Object> getParamMap() {
        return paramMap;
    }

    @Override
    public String opString() {
        return tableNameAlias + "." + fieldName + getOp() + "#{" + paramName + "}";
    }
}
