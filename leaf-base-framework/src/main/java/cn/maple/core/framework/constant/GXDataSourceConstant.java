package cn.maple.core.framework.constant;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.condition.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@SuppressWarnings("all")
public class GXDataSourceConstant {
    /**
     * 处理自定义条件的函数列表
     */
    public static final Map<String, Function<Dict, GXCondition<?>>> CONDITION_FUNCTION = new HashMap<>();

    /**
     * 忽略不需要数据过滤的条件的OP值
     */
    public static final String IGNORE_DATA_FILTER_CONDITION_OP_VALUE = "IGNORE_DATA_FILTER_CONDITION_OP_VALUE";

    static {
        CONDITION_FUNCTION.put(GXBuilderConstant.EQ, data -> new GXConditionEQ(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getLong("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.STR_EQ, data -> new GXConditionStrEQ(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getStr("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.STR_NOT_EQ, data -> new GXConditionStrNE(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getStr("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.IN, data -> new GXConditionIn(data.getStr("tableNameAlias"), data.getStr("fieldName"), (Set<Number>) data.get("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.STR_IN, data -> new GXConditionStrIn(data.getStr("tableNameAlias"), data.getStr("fieldName"), (Set<String>) data.get("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.NOT_IN, data -> new GXConditionNotIn(data.getStr("tableNameAlias"), data.getStr("fieldName"), (Set<Number>) data.get("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.STR_NOT_IN, data -> new GXConditionStrNotIn(data.getStr("tableNameAlias"), data.getStr("fieldName"), (Set<String>) data.get("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.RIGHT_LIKE, data -> new GXConditionLikeRight(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getStr("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.LIKE, data -> new GXConditionLikeFull(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getStr("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.LEFT_LIKE, data -> new GXConditionLikeLeft(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getStr("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.GE, data -> new GXConditionGE(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getLong("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.GT, data -> new GXConditionGT(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getLong("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.LE, data -> new GXConditionLE(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getLong("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.LT, data -> new GXConditionLT(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getLong("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.NOT_EQ, data -> new GXConditionNE(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getLong("value")));
        CONDITION_FUNCTION.put(GXBuilderConstant.IS_NULL, data -> new GXConditionIsNULL(data.getStr("tableNameAlias"), data.getStr("fieldName")));
        CONDITION_FUNCTION.put(GXBuilderConstant.EXCLUSION_DELETED_CONDITION_FLAG, data -> new GXExclusionDeletedFieldCondition(data.getStr("tableNameAlias"), data.getStr("fieldName"), data.getLong("value")));
    }

    private GXDataSourceConstant() {

    }

    public static Function<Dict, GXCondition<?>> getFunction(String op) {
        return CONDITION_FUNCTION.get(op);
    }
}
