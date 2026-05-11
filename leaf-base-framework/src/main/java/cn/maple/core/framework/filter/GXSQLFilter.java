package cn.maple.core.framework.filter;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXBusinessException;

import java.util.regex.Pattern;

public class GXSQLFilter {
    private static final Pattern[] SQL_KEYWORD_PATTERNS = new Pattern[]{
            Pattern.compile("(?i)\\bmaster\\b"),
            Pattern.compile("(?i)\\btruncate\\b"),
            Pattern.compile("(?i)\\binsert\\b"),
            Pattern.compile("(?i)\\bselect\\b"),
            Pattern.compile("(?i)\\bdelete\\b"),
            Pattern.compile("(?i)\\bupdate\\b"),
            Pattern.compile("(?i)\\bdeclare\\b"),
            Pattern.compile("(?i)\\balter\\b"),
            Pattern.compile("(?i)\\bdrop\\b"),
            Pattern.compile("(?i)\\bexec\\b"),
            Pattern.compile("(?i)\\bexecute\\b"),
            Pattern.compile("(?i)\\bunion\\b"),
            Pattern.compile("(?i)\\bcreate\\b"),
            Pattern.compile("(?i)\\btable\\b"),
            Pattern.compile("(?i)\\bgrant\\b"),
            Pattern.compile("(?i)\\brevoke\\b"),
            Pattern.compile("(?i)\\bdatabase\\b"),
            Pattern.compile("(?i)information_schema"),
            Pattern.compile("(?i)\\bsys\\b"),
            Pattern.compile("(?i)\\bwhere\\b"),
            Pattern.compile("(?i)\\bor\\b"),
            Pattern.compile("(?i)\\band\\b"),
            Pattern.compile("--"),
            Pattern.compile("/\\*"),
            Pattern.compile("\\*/"),
            Pattern.compile("(?i)xp_")
    };

    public static String sqlInject(String str) {
        if (CharSequenceUtil.isBlank(str)) {
            return null;
        }
        String normalized = CharSequenceUtil.replace(str, "'", "");
        normalized = CharSequenceUtil.replace(normalized, "\"", "");
        normalized = CharSequenceUtil.replace(normalized, ";", "");
        normalized = CharSequenceUtil.replace(normalized, "\\", "");

        for (Pattern keywordPattern : SQL_KEYWORD_PATTERNS) {
            if (keywordPattern.matcher(normalized).find()) {
                throw new GXBusinessException("包含非法字符");
            }
        }
        return normalized;
    }
}
