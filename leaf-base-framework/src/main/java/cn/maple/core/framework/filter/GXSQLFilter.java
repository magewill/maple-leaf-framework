package cn.maple.core.framework.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;

/**
 * SQL注入过滤工具类
 * <p>
 * 该类提供了防止SQL注入攻击的过滤功能，用于处理用户输入的字符串，
 * 移除或转义可能导致SQL注入的特殊字符和SQL关键字。
 * 主要用于那些无法使用参数化查询（PreparedStatement）的场景。
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * // 1. 基本用法 - 过滤可能包含SQL注入的用户输入
 * String userInput = request.getParameter("keyword");
 * String safeInput = GXSQLFilter.sqlInject(userInput);
 * 
 * // 2. 在动态SQL中使用
 * String orderBy = GXSQLFilter.sqlInject(request.getParameter("orderBy"));
 * String sql = "SELECT * FROM users ORDER BY " + orderBy;
 * 
 * // 3. 处理查询条件
 * String searchTerm = GXSQLFilter.sqlInject(request.getParameter("search"));
 * String whereSql = "name LIKE '%" + searchTerm + "%'";
 * </pre>
 * </p>
 * 
 * <p>
 * 安全说明：
 * 1. 该过滤器提供基本的SQL注入防护，但不应替代参数化查询
 * 2. 对于需要构建动态SQL的场景，应优先考虑使用MyBatis等ORM框架的安全机制
 * 3. 该过滤器主要用于处理无法使用参数化查询的边缘情况
 * </p>
 * 
 * @author maple
 */
public class GXSQLFilter {

    /**
     * SQL注入过滤
     * <p>
     * 该方法对输入字符串进行以下处理：
     * 1. 移除常见的SQL注入字符，如单引号、双引号、分号和反斜杠
     * 2. 将字符串转换为小写，便于检测SQL关键字
     * 3. 检查是否包含危险的SQL关键字，如SELECT、UPDATE等
     * </p>
     *
     * @param str 待验证的字符串，可以是用户输入或其他不可信来源的字符串
     * @return 过滤后的安全字符串，如果输入为空则返回null
     * @throws GXBusinessException 当检测到SQL注入攻击时抛出此异常
     */
    public static String sqlInject(String str) {
        if (CharSequenceUtil.isBlank(str)) {
            return null;
        }
        //去掉'|"|;|\字符
        str = CharSequenceUtil.replace(str, "'", "");
        str = CharSequenceUtil.replace(str, "\"", "");
        str = CharSequenceUtil.replace(str, ";", "");
        str = CharSequenceUtil.replace(str, "\\", "");

        //转换成小写
        str = str.toLowerCase();

        //非法字符 - SQL危险关键字列表
        String[] keywords = {
            "master", "truncate", "insert", "select", "delete", "update", "declare", "alter", "drop", 
            "exec", "execute", "union", "create", "table", "grant", "revoke", "database", 
            "information_schema", "sys", "where", "or", "and", "--", "/*", "*/", "xp_"
        };

        //判断是否包含非法字符
        for (String keyword : keywords) {
            if (str.contains(keyword)) {
                throw new GXBusinessException("包含非法字符");
            }
        }
        return str;
    }
}
