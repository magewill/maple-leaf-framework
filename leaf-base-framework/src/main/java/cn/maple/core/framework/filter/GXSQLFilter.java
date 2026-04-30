package cn.maple.core.framework.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;

public class GXSQLFilter {
    public static String sqlInject(String str) {
        if (CharSequenceUtil.isBlank(str)) {
            return null;
        }
        str = CharSequenceUtil.replace(str, "'", "");
        str = CharSequenceUtil.replace(str, "\"", "");
        str = CharSequenceUtil.replace(str, ";", "");
        str = CharSequenceUtil.replace(str, "\\", "");

        str = str.toLowerCase();

        String[] keywords = {
                "master", "truncate", "insert", "select", "delete", "update", "declare", "alter", "drop",
                "exec", "execute", "union", "create", "table", "grant", "revoke", "database",
                "information_schema", "sys", "where", "or", "and", "--", "/*", "*/", "xp_"
        };

        for (String keyword : keywords) {
            if (str.contains(keyword)) {
                throw new GXBusinessException("包含非法字符");
            }
        }
        return str;
    }
}
