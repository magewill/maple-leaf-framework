package cn.maple.core.framework.constant;

public class GXBuilderConstant {
    public static final String SEARCH_CONDITION_NAME = "searchCondition";

    public static final String IS_NULL = "{} IS NULL";

    public static final String EQ = " = {}";

    public static final String NOT_EQ = " != {}";

    public static final String LT = " < {}";

    public static final String LE = " <= {}";

    public static final String GT = " > {}";

    public static final String GE = " >= {}";

    public static final String IN = " IN ({})";

    public static final String NOT_IN = " NOT IN ({})";

    public static final String LEFT_LIKE = " like '%{}'";

    public static final String RIGHT_LIKE = " like '{}%'";

    public static final String LIKE = " like '%{}%'";

    public static final String STR_EQ = "STR_ = '{}'";

    public static final String STR_NOT_EQ = "STR_ != '{}'";

    public static final String STR_IN = "STR_ IN ({})";

    public static final String STR_NOT_IN = "STR_ NOT IN ({})";

    public static final String T_FUNC_MARK = "T_FUNC";

    public static final String LEFT_JOIN_TYPE = "left";

    public static final String RIGHT_JOIN_TYPE = "right";

    public static final String INNER_JOIN_TYPE = "inner";

    public static final String REMOVE_JSON_FIELD_PREFIX_FLAG = "-";

    public static final String DELETED_FLAG_FIELD_NAME = "is_deleted";

    public static final String EXCLUSION_DELETED_CONDITION_FLAG = "exclusion_deleted_condition";

    public static final String JSON_SEARCH_EXPRESSION_TEMPLATE = "{}->'{}' , CAST('[{}]' as JSON)";

    public static final String AND_OP = " AND ";

    public static final String OR_OP = " OR ";

    public static final String JSON_SEARCH_FUNC_ONE = "one";

    public static final String JSON_SEARCH_FUNC_ALL = "all";

    private GXBuilderConstant() {
    }
}
