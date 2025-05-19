package cn.maple.core.framework.dto.inner.field;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;

import java.util.Objects;

/**
 * JSON字段字符串更新类
 * <p>
 * 该类用于处理JSON类型字段的字符串值更新操作，支持通过JSON_SET函数更新JSON对象中的特定路径。
 * 根据值的类型自动选择合适的SQL语句格式，确保安全地处理JSON数据。
 * </p>
 *
 * <p>
 * 安全特性：
 * - 使用参数化查询(#{})防止SQL注入
 * - 自动检测JSON类型并使用适当的CAST函数
 * - 路径参数安全处理，避免路径注入
 * - 支持嵌套JSON路径访问
 * - 线程安全设计，继承自GXUpdateField的线程安全特性
 * - 增强的空值处理，防止NullPointerException
 * </p>
 *
 * <p>
 * 使用场景：
 * - 更新用户偏好设置（如主题、语言等）
 * - 更新产品属性（如规格、特性等）
 * - 更新配置信息（如系统参数、应用设置等）
 * - 更新地址信息（如省市区、详细地址等）
 * </p>
 *
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 更新用户配置中的主题颜色
 * GXUpdateField<?> themeField = new GXUpdateJsonSetStrField("user", "config", "theme.color", "#FF5733");
 *
 * // 2. 更新产品属性中的JSON对象
 * String jsonValue = "{\"width\": 100, \"height\": 200}";
 * GXUpdateField<?> sizeField = new GXUpdateJsonSetStrField("product", "attributes", "size", jsonValue);
 *
 * // 3. 将字段添加到更新列表
 * List<GXUpdateField<?>> updateFields = Arrays.asList(themeField, sizeField);
 *
 * // 4. 创建更新条件
 * List<GXCondition<?>> conditions = Arrays.asList(
 *     new GXConditionEQ("product", "id", 100)
 * );
 *
 * // 5. 执行更新操作
 * GXBaseQueryParamInnerDto queryParam = GXBaseQueryParamInnerDto.builder()
 *     .tableName("product")
 *     .condition(conditions)
 *     .build();
 * String sql = GXBaseBuilder.updateFieldByCondition(queryParam, updateFields);
 * </pre>
 * </p>
 *
 * <p>
 * 注意事项：
 * - 路径格式必须符合JSON路径规范，使用点号分隔层级
 * - 对于复杂JSON对象，建议先验证JSON格式的有效性
 * - 当更新大型JSON对象时，考虑性能影响
 * - 支持设置null值，会在JSON中将指定路径设置为NULL
 * </p>
 */
public class GXUpdateJsonSetStrField extends GXUpdateField<String> {
    /**
     * JSON路径，指定要更新的JSON对象中的具体位置
     * 例如："info.address" 表示更新JSON对象中info字段下的address属性
     */
    private final String path;

    /**
     * 构造函数
     *
     * @param tableNameAlias 表名或表别名，用于SQL生成时指定表
     * @param fieldName      字段名，JSON类型的字段名
     * @param path           JSON路径，使用点号分隔的路径表示法，如"user.address.city"
     * @param value          要设置的字符串值，可以是普通字符串或JSON格式字符串，允许为null
     * @throws NullPointerException 当fieldName为null时抛出
     */
    public GXUpdateJsonSetStrField(String tableNameAlias, String fieldName, String path, String value) {
        super(tableNameAlias, fieldName, value);
        this.path = Objects.requireNonNullElse(path, "");
    }

    /**
     * 获取字段值
     * <p>
     * 该方法返回字符串值，用于特殊情况处理
     * 安全处理null值，避免NullPointerException
     * </p>
     *
     * @return 字段值的字符串表示，可能为null
     */
    @Override
    public String getFieldValue() {
        // 此方法不再用于SQL拼接，而是用于特殊情况处理
        // 在参数化查询中，值会通过paramMap传递给MyBatis
        return value != null ? value.toString() : null;
    }

    /**
     * 生成更新JSON字段的SQL片段
     * <p>
     * 根据值的类型自动选择合适的SQL格式：
     * 1. 如果值本身是JSON，使用CAST确保正确处理
     * 2. 如果值是普通字符串，直接使用参数化查询
     * 3. 如果值为null，则设置JSON路径为null
     * </p>
     *
     * @return 格式化的SQL更新语句片段
     */
    @Override
    public String updateString() {
        // 为JSON路径创建单独的参数
        String pathParamName = paramName + "_path";
        // 构建标准JSON路径格式 $ 或 $.property
        String jsonPath = CharSequenceUtil.isEmpty(path) ? "$" : "$." + path;
        this.paramMap.put(pathParamName, jsonPath);

        // 检查值是否为null
        if (value == null) {
            // 对于null值，设置JSON路径为null
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = JSON_SET({}, #{dbQueryParamInnerDto.paramMap.{}}, NULL)",
                        fieldName, fieldName, pathParamName);
            }
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{dbQueryParamInnerDto.paramMap.{}}, NULL)",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName);
        }

        // 获取值的字符串表示
        String strValue = value.toString();
        this.paramMap.put(paramName, strValue);

        // JSON操作需要特殊处理
        if (JSONUtil.isTypeJSON(strValue)) {
            // 对于JSON值，使用CAST函数确保正确的JSON类型转换
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = JSON_SET({}, #{dbQueryParamInnerDto.paramMap.{}}, CAST(#{dbQueryParamInnerDto.paramMap.{}} as JSON))",
                        fieldName, fieldName, pathParamName, paramName);
            }
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{dbQueryParamInnerDto.paramMap.{}}, CAST(#{dbQueryParamInnerDto.paramMap.{}} as JSON))",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName, paramName);
        } else {
            // 对于普通字符串值，使用参数化查询防止SQL注入
            if (CharSequenceUtil.isEmpty(tableNameAlias)) {
                return CharSequenceUtil.format("{} = JSON_SET({}, #{dbQueryParamInnerDto.paramMap.{}}, #{dbQueryParamInnerDto.paramMap.{}})",
                        fieldName, fieldName, pathParamName, paramName);
            }
            return CharSequenceUtil.format("{}.{} = JSON_SET({}.{}, #{dbQueryParamInnerDto.paramMap.{}}, #{dbQueryParamInnerDto.paramMap.{}})",
                    tableNameAlias, fieldName, tableNameAlias, fieldName, pathParamName, paramName);
        }
    }
}