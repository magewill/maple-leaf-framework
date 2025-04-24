package cn.maple.core.framework.constant;

/**
 * SQL构建器常量类
 * <p>
 * 本类定义了SQL构建过程中使用的各种常量，包括查询条件、连接类型、JSON操作等。
 * 这些常量用于构建安全、高效的SQL语句，特别是在动态SQL构建场景中。
 * </p>
 * 
 * <p>
 * 安全说明：
 * 1. 所有SQL条件常量都使用参数化查询方式（使用{}占位符），防止SQL注入攻击
 * 2. 字符串类型的条件使用单引号包裹，确保类型安全
 * 3. 特殊处理了JSON类型字段的查询条件，确保类型转换安全
 * 4. 提供了不同的连接类型常量，支持复杂查询场景
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 创建查询条件
 * Table<String, String, Object> condition = HashBasedTable.create();
 * 
 * // 数字等于条件
 * condition.put("user_id", GXBuilderConstant.EQ, 10001);
 * 
 * // 字符串模糊查询
 * condition.put("username", GXBuilderConstant.LIKE, "admin");
 * 
 * // IN条件查询
 * condition.put("status", GXBuilderConstant.IN, Arrays.asList(1, 2, 3));
 * 
 * // JSON字段查询
 * condition.put("ext->$.address", GXBuilderConstant.JSON_SEARCH_EXPRESSION_TEMPLATE, "成都");
 * 
 * // 执行查询
 * List<UserEntity> users = userService.findByCondition(condition);
 * </pre>
 * </p>
 */
public class GXBuilderConstant {
    /**
     * 搜索条件的名字
     * <p>
     * 在构建查询条件时，用于标识条件集合的键名
     * </p>
     */
    public static final String SEARCH_CONDITION_NAME = "searchCondition";

    /**
     * 字段值是NULL条件
     * <p>
     * 用于构建IS NULL条件，{}将被替换为字段名
     * </p>
     * <p>
     * 示例：condition.put("update_time", GXBuilderConstant.IS_NULL, null);
     * </p>
     */
    public static final String IS_NULL = "{} IS NULL";

    /**
     * 数字相等条件
     * <p>
     * 用于构建等于条件，{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("user_id", GXBuilderConstant.EQ, 10001);
     * </p>
     */
    public static final String EQ = " = {}";

    /**
     * 数字不相等条件
     * <p>
     * 用于构建不等于条件，{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("status", GXBuilderConstant.NOT_EQ, 0);
     * </p>
     */
    public static final String NOT_EQ = " != {}";

    /**
     * 数字小于条件
     * <p>
     * 用于构建小于条件，{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("age", GXBuilderConstant.LT, 18);
     * </p>
     */
    public static final String LT = " < {}";

    /**
     * 数字小于等于条件
     * <p>
     * 用于构建小于等于条件，{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("price", GXBuilderConstant.LE, 100.00);
     * </p>
     */
    public static final String LE = " <= {}";

    /**
     * 数字大于条件
     * <p>
     * 用于构建大于条件，{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("score", GXBuilderConstant.GT, 60);
     * </p>
     */
    public static final String GT = " > {}";

    /**
     * 数字大于等于条件
     * <p>
     * 用于构建大于等于条件，{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("quantity", GXBuilderConstant.GE, 10);
     * </p>
     */
    public static final String GE = " >= {}";

    /**
     * IN条件
     * <p>
     * 用于构建IN条件，{}将被替换为参数值列表
     * </p>
     * <p>
     * 示例：condition.put("status", GXBuilderConstant.IN, Arrays.asList(1, 2, 3));
     * </p>
     */
    public static final String IN = " IN ({})";

    /**
     * NOT IN条件
     * <p>
     * 用于构建NOT IN条件，{}将被替换为参数值列表
     * </p>
     * <p>
     * 示例：condition.put("status", GXBuilderConstant.NOT_IN, Arrays.asList(0, -1));
     * </p>
     */
    public static final String NOT_IN = " NOT IN ({})";

    /**
     * 左模糊查询条件
     * <p>
     * 用于构建左模糊查询条件（以指定字符串结尾），{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("email", GXBuilderConstant.LEFT_LIKE, "gmail.com");
     * </p>
     */
    public static final String LEFT_LIKE = " like '%{}'";

    /**
     * 右模糊查询条件
     * <p>
     * 用于构建右模糊查询条件（以指定字符串开头），{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("name", GXBuilderConstant.RIGHT_LIKE, "张");
     * </p>
     */
    public static final String RIGHT_LIKE = " like '{}%'";

    /**
     * 全模糊查询条件
     * <p>
     * 用于构建全模糊查询条件（包含指定字符串），{}将被替换为参数值
     * </p>
     * <p>
     * 示例：condition.put("address", GXBuilderConstant.LIKE, "北京");
     * </p>
     */
    public static final String LIKE = " like '%{}%'";

    /**
     * 字符串相等条件
     * <p>
     * 用于构建字符串等于条件，{}将被替换为参数值，并用单引号包裹
     * </p>
     * <p>
     * 示例：condition.put("username", GXBuilderConstant.STR_EQ, "admin");
     * </p>
     */
    public static final String STR_EQ = "STR_ = '{}'";

    /**
     * 字符串不相等条件
     * <p>
     * 用于构建字符串不等于条件，{}将被替换为参数值，并用单引号包裹
     * </p>
     * <p>
     * 示例：condition.put("role", GXBuilderConstant.STR_NOT_EQ, "guest");
     * </p>
     */
    public static final String STR_NOT_EQ = "STR_ != '{}'";

    /**
     * 字符串IN条件
     * <p>
     * 用于构建字符串IN条件，{}将被替换为参数值列表，每个值用单引号包裹
     * </p>
     * <p>
     * 示例：condition.put("status", GXBuilderConstant.STR_IN, Arrays.asList("active", "pending"));
     * </p>
     */
    public static final String STR_IN = "STR_ IN ({})";

    /**
     * 字符串NOT IN条件
     * <p>
     * 用于构建字符串NOT IN条件，{}将被替换为参数值列表，每个值用单引号包裹
     * </p>
     * <p>
     * 示例：condition.put("status", GXBuilderConstant.STR_NOT_IN, Arrays.asList("deleted", "banned"));
     * </p>
     */
    public static final String STR_NOT_IN = "STR_ NOT IN ({})";

    /**
     * 函数条件标记
     * <p>
     * 标识查询条件是一个函数类型，用于特殊SQL函数的处理
     * </p>
     * <p>
     * 示例：condition.put("JSON_OVERLAPS(items->'$.zipcode', CAST('[94536]' AS JSON))", GXBuilderConstant.T_FUNC_MARK, "");
     * </p>
     */
    public static final String T_FUNC_MARK = "T_FUNC";

    /**
     * 左连接类型
     * <p>
     * 用于指定SQL JOIN操作的类型为LEFT JOIN
     * </p>
     */
    public static final String LEFT_JOIN_TYPE = "left";

    /**
     * 右连接类型
     * <p>
     * 用于指定SQL JOIN操作的类型为RIGHT JOIN
     * </p>
     */
    public static final String RIGHT_JOIN_TYPE = "right";

    /**
     * 内连接类型
     * <p>
     * 用于指定SQL JOIN操作的类型为INNER JOIN
     * </p>
     */
    public static final String INNER_JOIN_TYPE = "inner";

    /**
     * 更新字段时需要移除的json字段的前缀
     * <p>
     * 在更新JSON类型字段时，如果字段名前带有此前缀，表示需要从JSON中移除该字段
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * // 创建扩展数据表
     * final Table<String, String, Object> extData = HashBasedTable.create();
     * // 添加普通字段
     * extData.put("ext", "name", "jack");
     * extData.put("ext", "address", "四川成都");
     * // 添加需要移除的字段（使用前缀标记）
     * extData.put("ext", GXBuilderConstant.REMOVE_JSON_FIELD_PREFIX_FLAG + "salary" , "");
     * // 创建更新数据
     * final Dict data = Dict.create().set("category_name", "打折商品").set("ext", extData);
     * // 创建更新条件
     * Table<String , String , Object> condition = HashBasedTable.create();
     * condition.put("category_id" ,  GXBuilderConstant.STR_EQ , 11111);
     * // 执行更新
     * updateFieldByCondition("s_category", data, condition);
     * </pre>
     * </p>
     */
    public static final String REMOVE_JSON_FIELD_PREFIX_FLAG = "-";

    /**
     * 数据库中删除标记的字段名字
     * <p>
     * 用于实现逻辑删除功能，标记记录是否已删除
     * </p>
     */
    public static final String DELETED_FLAG_FIELD_NAME = "is_deleted";

    /**
     * 排除删除条件的标识
     * <p>
     * 如果额外传输了该字段，则查询SQL中会查询所有满足条件的数据，无论删除与否
     * </p>
     * <p>
     * 示例：condition.put(GXBuilderConstant.EXCLUSION_DELETED_CONDITION_FLAG, GXBuilderConstant.EQ, 1);
     * </p>
     */
    public static final String EXCLUSION_DELETED_CONDITION_FLAG = "exclusion_deleted_condition";

    /**
     * 数据库JSON查询字符串模板表达式
     * <p>
     * 用于构建JSON字段的查询条件，格式为：JSON字段->'$.路径', CAST('[值]' as JSON)
     * </p>
     * <p>
     * 示例：condition.put("ext", String.format(GXBuilderConstant.JSON_SEARCH_EXPRESSION_TEMPLATE, "ext", "$.address", "成都"));
     * </p>
     */
    public static final String JSON_SEARCH_EXPRESSION_TEMPLATE = "{}->'{}' , CAST('[{}]' as JSON)";

    /**
     * SQL AND操作符
     * <p>
     * 用于连接多个查询条件，表示所有条件都必须满足
     * </p>
     */
    public static final String AND_OP = " AND ";

    /**
     * SQL OR操作符
     * <p>
     * 用于连接多个查询条件，表示满足任一条件即可
     * </p>
     */
    public static final String OR_OP = " OR ";

    /**
     * JSON_SEARCH函数中的one_or_all参数值：one
     * <p>
     * 表示只返回第一个匹配的路径
     * </p>
     */
    public static final String JSON_SEARCH_FUNC_ONE = "one";

    /**
     * JSON_SEARCH函数中的one_or_all参数值：all
     * <p>
     * 表示返回所有匹配的路径
     * </p>
     */
    public static final String JSON_SEARCH_FUNC_ALL = "all";


    /**
     * 私有构造函数，防止实例化
     */
    private GXBuilderConstant() {
    }
}
