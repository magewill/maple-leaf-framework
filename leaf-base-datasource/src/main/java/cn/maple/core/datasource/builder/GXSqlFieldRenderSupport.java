package cn.maple.core.datasource.builder;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.exception.GXDBConditionException;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.GXCommonUtils;
import cn.maple.core.framework.util.GXDBStringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

final class GXSqlFieldRenderSupport {
    private GXSqlFieldRenderSupport() {
    }

    enum SqlFieldRenderMode {
        AUTO_UNDERLINE,
        PRESERVE_ORIGINAL;

        boolean autoUnderline() {
            return this == AUTO_UNDERLINE;
        }
    }

    static SqlFieldRenderMode resolveSqlFieldRenderMode() {
        return isAutoUnderlineFieldEnabled() ? SqlFieldRenderMode.AUTO_UNDERLINE : SqlFieldRenderMode.PRESERVE_ORIGINAL;
    }

    static void registerSelectOutputColumn(Set<String> allowedColumns, String expression, String tableAlias) {
        String trimmed = CharSequenceUtil.trim(expression);
        if (CharSequenceUtil.isBlank(trimmed) || "*".equals(trimmed) || GXBaseBuilder.QUALIFIED_WILDCARD_PATTERN.matcher(trimmed).matches()) {
            return;
        }
        Matcher matcher = GXBaseBuilder.EXPRESSION_ALIAS_PATTERN.matcher(trimmed);
        if (matcher.matches() && !isReservedTrailingKeyword(matcher.group(2))) {
            registerOutputAlias(allowedColumns, matcher.group(2), tableAlias);
            return;
        }
        String expressionPart = extractExpressionPart(trimmed);
        if (GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(expressionPart).matches()) {
            registerOutputAlias(allowedColumns, GXSqlTableMetadataSupport.extractBareColumnName(expressionPart), tableAlias);
        }
    }

    static String sanitizeSelectColumn(String column, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(column);
        if ("1".equals(trimmed)) {
            return "1";
        }
        return sanitizeStructuralColumn(trimmed, allowedColumns, "SELECT", false);
    }

    static String renderSelectColumn(String originalColumn, String sanitizedColumn, Set<String> allowedColumns, SqlFieldRenderMode fieldRenderMode) {
        String trimmedOriginal = CharSequenceUtil.trim(originalColumn);
        String trimmedSanitized = CharSequenceUtil.trim(sanitizedColumn);
        if (!fieldRenderMode.autoUnderline()) {
            return trimmedOriginal;
        }
        if (!isComplexExpression(trimmedSanitized)) {
            return CharSequenceUtil.toUnderlineCase(trimmedSanitized);
        }
        return renderExpressionToUnderline(trimmedOriginal, allowedColumns);
    }

    static String normalizeRequestedSelectColumn(String column) {
        String trimmed = CharSequenceUtil.trim(column);
        if (CharSequenceUtil.isBlank(trimmed)) {
            return trimmed;
        }
        String expressionPart = extractExpressionPart(trimmed);
        if ("*".equals(expressionPart) || GXBaseBuilder.QUALIFIED_WILDCARD_PATTERN.matcher(expressionPart).matches() || isComplexExpression(trimmed)) {
            return trimmed;
        }
        return CharSequenceUtil.toUnderlineCase(trimmed);
    }

    static String sanitizeGroupBy(String column, Set<String> allowedColumns, String tableName, String tableAlias, SqlFieldRenderMode fieldRenderMode) {
        String normalizedColumn = normalizeRequestedSelectColumn(column);
        if (fieldRenderMode.autoUnderline()) {
            registerLegacyGroupOrderColumn(allowedColumns, normalizedColumn, tableName, tableAlias);
        }
        return renderGroupOrderColumn(column, normalizedColumn, allowedColumns, "GROUP BY", fieldRenderMode);
    }

    static String sanitizeOrderBy(String column, String direction, Set<String> allowedColumns, String tableName, String tableAlias, SqlFieldRenderMode fieldRenderMode) {
        String normalizedColumn = normalizeRequestedSelectColumn(column);
        if (fieldRenderMode.autoUnderline()) {
            registerLegacyGroupOrderColumn(allowedColumns, normalizedColumn, tableName, tableAlias);
        }
        return renderOrderBy(column, normalizedColumn, direction, allowedColumns, fieldRenderMode);
    }

    static String sanitizeHavingClause(String clause, Set<String> allowedColumns, SqlFieldRenderMode fieldRenderMode) {
        String trimmed = CharSequenceUtil.trim(clause);
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException("HAVING clause must not be blank");
        }
        if (allowedColumns.isEmpty()) {
            throw new GXDBConditionException("HAVING column whitelist is unavailable");
        }
        try {
            GXDBStringUtils.normalizeAndValidateRawSqlExpression(trimmed);
        } catch (GXSqlInjectionException ex) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("HAVING clause has SQL injection risk: {}", clause));
        }
        if (GXBaseBuilder.DANGEROUS_SQL_TOKEN_PATTERN.matcher(trimmed).find()) {
            throw new GXSqlInjectionException(CharSequenceUtil.format("HAVING clause contains dangerous tokens: {}", clause));
        }

        String searchableClause = stripQuotedStringLiterals(trimmed);
        Matcher matcher = GXBaseBuilder.HAVING_TOKEN_PATTERN.matcher(searchableClause);
        while (matcher.find()) {
            String token = matcher.group();
            String upper = token.toUpperCase(Locale.ROOT);
            if (GXBaseBuilder.HAVING_KEYWORD_WHITELIST.contains(upper)) {
                continue;
            }
            String normalizedToken = token.toLowerCase(Locale.ROOT);
            String snakeToken = CharSequenceUtil.toUnderlineCase(token).toLowerCase(Locale.ROOT);
            if (allowedColumns.contains(normalizedToken)
                    || allowedColumns.contains(GXSqlTableMetadataSupport.extractBareColumnName(normalizedToken))
                    || allowedColumns.contains(snakeToken)
                    || allowedColumns.contains(GXSqlTableMetadataSupport.extractBareColumnName(snakeToken))) {
                continue;
            }
            if (isFunctionToken(searchableClause, matcher.end(), token)) {
                continue;
            }
            throw new GXDBConditionException(CharSequenceUtil.format("HAVING token is not in whitelist: {}", token));
        }
        return fieldRenderMode.autoUnderline() ? GXSqlExpressionIdentifierRenderer.renderIdentifierTokensToUnderline(trimmed, allowedColumns) : trimmed;
    }

    static String renderOrderBy(String column, String direction, Set<String> allowedColumns) {
        return renderOrderBy(column, normalizeRequestedSelectColumn(column), direction, allowedColumns, SqlFieldRenderMode.AUTO_UNDERLINE);
    }

    static String extractExpressionPart(String expression) {
        String trimmed = CharSequenceUtil.trim(expression);
        Matcher matcher = GXBaseBuilder.EXPRESSION_ALIAS_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return trimmed;
        }
        String alias = matcher.group(2);
        if (isReservedTrailingKeyword(alias)) {
            return trimmed;
        }
        String baseExpression = CharSequenceUtil.trim(matcher.group(1));
        return CharSequenceUtil.isBlank(baseExpression) ? trimmed : baseExpression;
    }

    static boolean isReservedTrailingKeyword(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        return GXBaseBuilder.SQL_FUNCTION_KEYWORDS.contains(normalized)
                || GXBaseBuilder.HAVING_KEYWORD_WHITELIST.contains(token.toUpperCase(Locale.ROOT))
                || "asc".equals(normalized)
                || "desc".equals(normalized);
    }

    private static void registerOutputAlias(Set<String> allowedColumns, String alias, String tableAlias) {
        String exactAlias = alias.toLowerCase(Locale.ROOT);
        String normalizedAlias = CharSequenceUtil.toUnderlineCase(alias).toLowerCase(Locale.ROOT);
        allowedColumns.add(exactAlias);
        allowedColumns.add(normalizedAlias);
        if (CharSequenceUtil.isNotBlank(tableAlias)) {
            allowedColumns.add(CharSequenceUtil.format("{}.{}", tableAlias.toLowerCase(Locale.ROOT), exactAlias));
            allowedColumns.add(CharSequenceUtil.format("{}.{}", tableAlias.toLowerCase(Locale.ROOT), normalizedAlias));
        }
    }

    private static String renderExpressionToUnderline(String expression, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(expression);
        Matcher matcher = GXBaseBuilder.EXPRESSION_ALIAS_PATTERN.matcher(trimmed);
        if (matcher.matches() && !isReservedTrailingKeyword(matcher.group(2))) {
            String baseExpression = CharSequenceUtil.trim(matcher.group(1));
            if (CharSequenceUtil.isBlank(baseExpression)) {
                return trimmed;
            }
            return CharSequenceUtil.format("{} AS {}",
                    GXSqlExpressionIdentifierRenderer.renderIdentifierTokensToUnderline(baseExpression, allowedColumns),
                    CharSequenceUtil.toUnderlineCase(matcher.group(2)));
        }
        return GXSqlExpressionIdentifierRenderer.renderIdentifierTokensToUnderline(trimmed, allowedColumns);
    }

    private static boolean isComplexExpression(String trimmed) {
        return trimmed.contains("(")
                || CharSequenceUtil.containsIgnoreCase(trimmed, " as ")
                || hasTrailingAlias(trimmed);
    }

    private static String sanitizeStructuralColumn(String column, Set<String> allowedColumns, String clauseName, boolean whitelistRequired) {
        String trimmed = CharSequenceUtil.trim(column);
        if (CharSequenceUtil.isBlank(trimmed)) {
            throw new GXDBConditionException(CharSequenceUtil.format("{} field is blank", clauseName));
        }
        String expressionPart = extractExpressionPart(trimmed);
        if ("*".equals(expressionPart) || GXBaseBuilder.QUALIFIED_WILDCARD_PATTERN.matcher(expressionPart).matches()) {
            if (!"SELECT".equalsIgnoreCase(clauseName)) {
                throw new GXDBConditionException(CharSequenceUtil.format("{} field does not support wildcard: {}", clauseName, column));
            }
            if ("*".equals(expressionPart)) {
                GXBaseBuilder.LOGGER.error("SELECT field '*' is allowed for compatibility, please prefer explicit columns when possible.");
            }
            return trimmed;
        }
        if (isComplexExpression(trimmed)) {
            if (GXDBStringUtils.check(trimmed)) {
                throw new GXSqlInjectionException(
                        CharSequenceUtil.format("{} field has SQL injection risk: {}", clauseName, column));
            }
            if (whitelistRequired && allowedColumns.isEmpty()) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} column whitelist is unavailable", clauseName));
            }
            if (!allowedColumns.isEmpty()) {
                GXSqlExpressionIdentifierRenderer.validateColumnRefsInExpression(trimmed, allowedColumns, clauseName);
            }
        } else {
            if (!GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(expressionPart).matches()) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} field is invalid: {}", clauseName, column));
            }
            if (GXDBStringUtils.check(trimmed)) {
                throw new GXSqlInjectionException(
                        CharSequenceUtil.format("{} field has SQL injection risk: {}", clauseName, column));
            }
            if (whitelistRequired && allowedColumns.isEmpty()) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} column whitelist is unavailable", clauseName));
            }
            String normalizedColumn = expressionPart.toLowerCase(Locale.ROOT);
            String bareColumn = GXSqlTableMetadataSupport.extractBareColumnName(normalizedColumn);
            if (!allowedColumns.isEmpty() && !allowedColumns.contains(normalizedColumn) && !allowedColumns.contains(bareColumn)) {
                throw new GXDBConditionException(
                        CharSequenceUtil.format("{} field is not in whitelist: {}", clauseName, column));
            }
        }
        return trimmed;
    }

    private static void registerLegacyGroupOrderColumn(Set<String> allowedColumns, String column, String tableName, String tableAlias) {
        String trimmed = CharSequenceUtil.trim(column);
        if (CharSequenceUtil.isBlank(trimmed) || isComplexExpression(trimmed)) {
            return;
        }
        String expressionPart = extractExpressionPart(trimmed);
        if (GXBaseBuilder.SAFE_IDENTIFIER_PATTERN.matcher(expressionPart).matches()) {
            GXSqlTableMetadataSupport.registerAllowedColumn(allowedColumns, expressionPart, tableName, tableAlias);
        }
    }

    private static boolean isAutoUnderlineFieldEnabled() {
        return GXCommonUtils.getEnvironmentValue(GXBaseBuilder.AUTO_UNDERLINE_FIELD_ENABLED_KEY, Boolean.class, Boolean.TRUE);
    }

    private static String renderOrderBy(String originalColumn, String normalizedColumn, String direction, Set<String> allowedColumns, SqlFieldRenderMode fieldRenderMode) {
        if (CharSequenceUtil.isBlank(originalColumn)) {
            throw new GXDBConditionException("ORDER BY column must not be blank");
        }
        String safeColumn = renderGroupOrderColumn(originalColumn, normalizedColumn, allowedColumns, "ORDER BY", fieldRenderMode);
        String safeDirection = java.util.Optional.ofNullable(direction).map(CharSequenceUtil::trim).orElse("").toUpperCase(Locale.ROOT);
        if (!"ASC".equals(safeDirection) && !"DESC".equals(safeDirection)) {
            throw new GXDBConditionException(CharSequenceUtil.format("ORDER BY direction is invalid: {}", direction));
        }
        return CharSequenceUtil.format("{} {}", safeColumn, safeDirection);
    }

    private static String renderGroupOrderColumn(String originalColumn, String normalizedColumn, Set<String> allowedColumns, String clauseName, SqlFieldRenderMode fieldRenderMode) {
        String safeColumn = sanitizeStructuralColumn(normalizedColumn, allowedColumns, clauseName, true);
        String trimmedOriginal = CharSequenceUtil.trim(originalColumn);
        if (!fieldRenderMode.autoUnderline()) {
            if (isComplexExpression(trimmedOriginal)) {
                return safeColumn;
            }
            if (isAllowedColumnReference(trimmedOriginal, allowedColumns)) {
                return trimmedOriginal;
            }
        }
        return isComplexExpression(safeColumn)
                ? GXSqlExpressionIdentifierRenderer.renderIdentifierTokensToUnderline(safeColumn, allowedColumns)
                : CharSequenceUtil.toUnderlineCase(safeColumn);
    }

    private static boolean isAllowedColumnReference(String column, Set<String> allowedColumns) {
        String trimmed = CharSequenceUtil.trim(column);
        if (CharSequenceUtil.isBlank(trimmed)) {
            return false;
        }
        String normalizedColumn = trimmed.toLowerCase(Locale.ROOT);
        String snakeColumn = CharSequenceUtil.toUnderlineCase(trimmed).toLowerCase(Locale.ROOT);
        return allowedColumns.contains(normalizedColumn)
                || allowedColumns.contains(GXSqlTableMetadataSupport.extractBareColumnName(normalizedColumn))
                || allowedColumns.contains(snakeColumn)
                || allowedColumns.contains(GXSqlTableMetadataSupport.extractBareColumnName(snakeColumn));
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

    private static boolean hasTrailingAlias(String expression) {
        return !CharSequenceUtil.equals(extractExpressionPart(expression), CharSequenceUtil.trim(expression));
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
