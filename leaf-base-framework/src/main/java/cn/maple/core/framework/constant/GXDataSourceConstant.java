package cn.maple.core.framework.constant;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.inner.condition.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 数据源常量类
 * <p>
 * 该类提供了数据库查询条件构建的函数映射，用于动态生成各种类型的查询条件。
 * 通过函数式接口实现，支持多种条件操作符，如等于、不等于、大于、小于、包含等。
 * 该类是线程安全的，所有常量和函数映射在类加载时完成初始化，不可修改。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * // 获取等于条件的构建函数
 * Function<Dict, GXCondition<?>> eqFunction = GXDataSourceConstant.getFunction(GXBuilderConstant.EQ);
 * 
 * // 构建条件参数
 * Dict params = Dict.create()
 *     .set("tableNameAlias", "t")
 *     .set("fieldName", "id")
 *     .set("value", 1L);
 * 
 * // 生成条件对象
 * GXCondition<?> condition = eqFunction.apply(params);
 * </pre>
 * </p>
 *
 * @author britton chen <britton@126.com>
 */
@SuppressWarnings("all")
public class GXDataSourceConstant {
    /**
     * 处理自定义条件的函数列表
     * <p>
     * 该映射存储了不同条件操作符对应的条件构建函数。
     * 键为条件操作符（定义在GXBuilderConstant中），值为接受Dict参数并返回GXCondition对象的函数。
     * 使用静态初始化块进行初始化，确保线程安全。
     * </p>
     */
    public static final Map<String, Function<Dict, GXCondition<?>>> CONDITION_FUNCTION = new HashMap<>();

    /**
     * 忽略不需要数据过滤的条件的OP值
     * <p>
     * 当条件操作符等于此值时，表示该条件不需要进行数据过滤处理。
     * 在构建复杂查询时，可以使用此常量来标记某些特殊条件。
     * </p>
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

    /**
     * 私有构造函数
     * <p>
     * 防止实例化，该类仅提供静态常量和方法。
     * 符合工具类的设计模式。
     * </p>
     */
    private GXDataSourceConstant() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取指定操作符对应的条件构建函数
     * <p>
     * 根据提供的操作符从CONDITION_FUNCTION映射中获取对应的条件构建函数。
     * 如果操作符不存在，则返回null。
     * 该方法是线程安全的。
     * </p>
     * 
     * @param op 条件操作符，通常使用GXBuilderConstant中定义的常量
     * @return 对应的条件构建函数，如果操作符不存在则返回null
     */
    public static Function<Dict, GXCondition<?>> getFunction(String op) {
        return CONDITION_FUNCTION.get(op);
    }
}
