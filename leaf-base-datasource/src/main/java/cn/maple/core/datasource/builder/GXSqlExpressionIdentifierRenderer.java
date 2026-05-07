package cn.maple.core.datasource.builder;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXDBConditionException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GXSqlExpressionIdentifierRenderer {
    private static final Pattern EXPRESSION_ALIAS_PATTERN = Pattern.compile("^(.*?)(?:(?i)\\s+as\\s+|\\s+)([A-Za-z_][A-Za-z0-9_]*)\\s*$", Pattern.DOTALL);

    private GXSqlExpressionIdentifierRenderer() {
    }

    static void validateColumnRefsInExpression(String expr, Set<String> allowedColumns, String clauseName) {
        Set<String> columnRefs = resolveColumnReferences(expr);
        for (String token : columnRefs) {
            if (!isAllowedColumn(token, allowedColumns)) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} expression references column [{}] which is not in whitelist: {}",
                                clauseName, token, expr));
            }
        }
    }

    static String renderIdentifierTokensToUnderline(String expression, Set<String> allowedColumns) {
        Set<String> columnRefs = tryParseColumnReferences(expression);
        if (columnRefs.isEmpty() && !allowedColumns.isEmpty()) {
            columnRefs = collectFallbackColumnReferences(expression, allowedColumns);
        }
        if (columnRefs.isEmpty() && allowedColumns.isEmpty()) {
            return renderByFallbackTokens(expression, allowedColumns);
        }
        return renderByColumnReferences(expression, allowedColumns, columnRefs);
    }

    private static Set<String> resolveColumnReferences(String expression) {
        Set<String> columnRefs = tryParseColumnReferences(expression);
        if (!columnRefs.isEmpty()) {
            return columnRefs;
        }
        return collectFallbackColumnReferences(expression, Set.of());
    }

    private static Set<String> tryParseColumnReferences(String expression) {
        try {
            Expression parsedExpression = CCJSqlParserUtil.parseExpression(extractExpressionPart(expression));
            Set<String> columnRefs = new HashSet<>();
            parsedExpression.accept(new ExpressionVisitorAdapter<Void>() {
                @Override
                public <S> Void visit(Column column, S context) {
                    registerColumnReference(columnRefs, column.getFullyQualifiedName());
                    registerColumnReference(columnRefs, column.getColumnName());
                    return null;
                }
            }, null);
            return columnRefs;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static Set<String> collectFallbackColumnReferences(String expression, Set<String> allowedColumns) {
        String searchableExpression = stripQuotedStringLiterals(extractExpressionPart(expression));
        Matcher matcher = GXBaseBuilder.HAVING_TOKEN_PATTERN.matcher(searchableExpression);
        Set<String> columnRefs = new HashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (shouldTreatFallbackTokenAsColumn(searchableExpression, matcher.end(), token, allowedColumns)) {
                registerColumnReference(columnRefs, token);
            }
        }
        return columnRefs;
    }

    private static String renderByColumnReferences(String expression, Set<String> allowedColumns, Set<String> columnRefs) {
        String searchableExpression = stripQuotedStringLiterals(expression);
        Matcher matcher = GXBaseBuilder.HAVING_TOKEN_PATTERN.matcher(searchableExpression);
        StringBuilder rendered = new StringBuilder(expression.length());
        int lastIndex = 0;
        while (matcher.find()) {
            String token = matcher.group();
            rendered.append(expression, lastIndex, matcher.start());
            if (isColumnReference(token, columnRefs) && (allowedColumns.isEmpty() || isAllowedColumn(token, allowedColumns))) {
                rendered.append(CharSequenceUtil.toUnderlineCase(token));
            } else {
                rendered.append(expression, matcher.start(), matcher.end());
            }
            lastIndex = matcher.end();
        }
        rendered.append(expression, lastIndex, expression.length());
        return rendered.toString();
    }

    private static String renderByFallbackTokens(String expression, Set<String> allowedColumns) {
        String searchableExpression = stripQuotedStringLiterals(expression);
        Matcher matcher = GXBaseBuilder.HAVING_TOKEN_PATTERN.matcher(searchableExpression);
        StringBuilder rendered = new StringBuilder(expression.length());
        int lastIndex = 0;
        while (matcher.find()) {
            String token = matcher.group();
            rendered.append(expression, lastIndex, matcher.start());
            if (shouldTreatFallbackTokenAsColumn(searchableExpression, matcher.end(), token, allowedColumns)) {
                rendered.append(CharSequenceUtil.toUnderlineCase(token));
            } else {
                rendered.append(expression, matcher.start(), matcher.end());
            }
            lastIndex = matcher.end();
        }
        rendered.append(expression, lastIndex, expression.length());
        return rendered.toString();
    }

    private static boolean shouldTreatFallbackTokenAsColumn(String searchableExpression, int tokenEndIndex, String token, Set<String> allowedColumns) {
        String upper = token.toUpperCase(Locale.ROOT);
        if (GXBaseBuilder.HAVING_KEYWORD_WHITELIST.contains(upper)
                || GXBaseBuilder.SQL_FUNCTION_KEYWORDS.contains(token.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (isFunctionToken(searchableExpression, tokenEndIndex, token)) {
            return false;
        }
        return allowedColumns.isEmpty() || isAllowedColumn(token, allowedColumns);
    }

    private static boolean isFunctionToken(String clause, int tokenEndIndex, String token) {
        if (!GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(token).matches()) {
            return false;
        }
        int index = tokenEndIndex;
        while (index < clause.length() && Character.isWhitespace(clause.charAt(index))) {
            index++;
        }
        return index < clause.length()
                && clause.charAt(index) == '('
                && GXBaseBuilder.HAVING_FUNCTION_WHITELIST.contains(token.toUpperCase(Locale.ROOT));
    }

    private static boolean isAllowedColumn(String token, Set<String> allowedColumns) {
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        String snakeToken = CharSequenceUtil.toUnderlineCase(token).toLowerCase(Locale.ROOT);
        return allowedColumns.contains(normalizedToken)
                || allowedColumns.contains(extractBareColumnName(normalizedToken))
                || allowedColumns.contains(snakeToken)
                || allowedColumns.contains(extractBareColumnName(snakeToken));
    }

    private static boolean isColumnReference(String token, Set<String> columnRefs) {
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        String bareToken = extractBareColumnName(normalizedToken);
        return columnRefs.contains(normalizedToken) || columnRefs.contains(bareToken);
    }

    private static void registerColumnReference(Set<String> columnRefs, String columnName) {
        String normalizedColumn = CharSequenceUtil.trim(columnName);
        if (CharSequenceUtil.isBlank(normalizedColumn)) {
            return;
        }
        String lowerColumn = normalizedColumn.toLowerCase(Locale.ROOT);
        columnRefs.add(lowerColumn);
        columnRefs.add(extractBareColumnName(lowerColumn));
    }

    private static String extractExpressionPart(String expression) {
        String trimmed = CharSequenceUtil.trim(expression);
        Matcher matcher = EXPRESSION_ALIAS_PATTERN.matcher(trimmed);
        if (!matcher.matches() || isReservedTrailingKeyword(matcher.group(2))) {
            return trimmed;
        }
        String baseExpression = CharSequenceUtil.trim(matcher.group(1));
        return CharSequenceUtil.isBlank(baseExpression) ? trimmed : baseExpression;
    }

    private static boolean isReservedTrailingKeyword(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return GXBaseBuilder.SQL_FUNCTION_KEYWORDS.contains(normalized)
                || GXBaseBuilder.HAVING_KEYWORD_WHITELIST.contains(token.toUpperCase(Locale.ROOT))
                || "asc".equals(normalized)
                || "desc".equals(normalized);
    }

    private static String extractBareColumnName(String identifier) {
        int dotIndex = identifier.lastIndexOf('.');
        if (dotIndex >= 0) {
            return identifier.substring(dotIndex + 1);
        }
        return identifier;
    }

    private static String stripQuotedStringLiterals(String expression) {
        StringBuilder result = new StringBuilder(expression.length());
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (inSingleQuote) {
                result.append(' ');
                if (current == '\'' && i + 1 < expression.length() && expression.charAt(i + 1) == '\'') {
                    result.append(' ');
                    i++;
                } else if (current == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                result.append(' ');
                if (current == '"' && i + 1 < expression.length() && expression.charAt(i + 1) == '"') {
                    result.append(' ');
                    i++;
                } else if (current == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (current == '\'') {
                inSingleQuote = true;
                result.append(' ');
            } else if (current == '"') {
                inDoubleQuote = true;
                result.append(' ');
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }
}
