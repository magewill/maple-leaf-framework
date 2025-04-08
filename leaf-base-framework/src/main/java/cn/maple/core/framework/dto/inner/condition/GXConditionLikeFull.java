package cn.maple.core.framework.dto.inner.condition;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXDBStringEscapeUtils;

public class GXConditionLikeFull extends GXCondition<String> {
    public GXConditionLikeFull(String tableNameAlias, String fieldName, String value) {
        super(tableNameAlias, fieldName, value);
    }

    @Override
    public String getOp() {
        return "like";
    }

    /**
     * 获取模糊匹配条件的字段值
     * <p>
     * 该方法实现了多层次的SQL注入防护措施：
     * 1. 检查值是否为null
     * 2. 使用GXDBStringEscapeUtils.check进行全面的SQL注入风险检测
     * 3. 使用escapeSqlForLike方法进行专门针对LIKE查询的字符串转义
     * 4. 根据内容选择合适的引号包裹方式，避免引号嵌套问题
     * </p>
     * <p>
     * 注意：模糊匹配查询特别容易受到SQL注入攻击，因此需要特别注意安全处理
     * </p>
     *
     * @return 安全的字段值字符串表示
     * @throws GXSqlInjectionException 如果检测到SQL注入风险
     */
    @Override
    public String getFieldValue() {
        if (value == null) {
            return "NULL";
        }
        
        String strValue = value.toString();
        // 首先检查是否存在SQL注入风险
        if (GXDBStringEscapeUtils.check(strValue)) {
            throw new GXSqlInjectionException("模糊匹配条件中检测到SQL注入风险: " + strValue);
        }
        
        // 使用escapeSqlForLike方法进行更全面的SQL转义，特别适合LIKE查询
        String escapedValue = GXDBStringEscapeUtils.escapeSqlForLike(strValue);
        
        // 根据内容选择合适的引号包裹方式
        if (CharSequenceUtil.contains(escapedValue, "''")) {
            // 如果包含已转义的单引号，使用双引号包裹
            return CharSequenceUtil.format("\"%{}%\"", escapedValue);
        } else {
            // 否则使用单引号包裹
            return CharSequenceUtil.format("'%{}%'", escapedValue);
        }
    }
}
