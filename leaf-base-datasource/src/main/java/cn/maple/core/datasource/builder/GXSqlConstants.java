package cn.maple.core.datasource.builder;

import java.util.Set;
import java.util.regex.Pattern;

final class GXSqlConstants {
    static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    static final Pattern HAVING_TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    static final Pattern DANGEROUS_SQL_TOKEN_PATTERN = Pattern.compile("(?i)\\b(update|delete|insert|alter|drop|truncate|create|grant|revoke|call|exec|merge)\\b");
    static final Set<String> HAVING_KEYWORD_WHITELIST = Set.of(
            "AND", "OR", "NOT", "NULL", "IS", "LIKE", "IN", "BETWEEN", "AS", "DISTINCT",
            "CASE", "WHEN", "THEN", "ELSE", "END", "SUM", "COUNT", "AVG", "MIN", "MAX", "FILTER", "OVER"
    );
    static final Set<String> HAVING_FUNCTION_WHITELIST = Set.of(
            "SUM", "COUNT", "AVG", "MIN", "MAX", "COALESCE", "NULLIF", "ROUND", "ABS", "CEIL", "FLOOR",
            "JSON_VALUE", "JSON_EXTRACT", "DATE_TRUNC", "DATE_FORMAT", "TO_CHAR", "CAST"
    );
    static final Set<String> MYSQL_LIKE_DIALECTS = Set.of("mysql", "mariadb", "h2", "sqlite");
    static final Set<String> POSTGRES_DIALECTS = Set.of("postgres", "postgresql", "postgre_sql");
    static final Set<String> SQLSERVER_DIALECTS = Set.of("sqlserver", "sql_server", "mssql", "sql-server");
    static final Set<String> ORACLE_DIALECTS = Set.of("oracle");
    static final Pattern SQL_FUNCTION_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*\\)$", Pattern.DOTALL);
    static final Pattern EXPRESSION_ALIAS_PATTERN = Pattern.compile("^(.*?)(?:(?i)\\s+as\\s+|\\s+)([A-Za-z_][A-Za-z0-9_]*)\\s*$", Pattern.DOTALL);

    static final Set<String> SQL_FUNCTION_KEYWORDS = Set.of(
            "ifnull", "isnull", "coalesce", "nullif",
            "sum", "count", "avg", "min", "max",
            "upper", "lower", "trim", "length", "concat", "substring", "replace",
            "cast", "convert", "round", "floor", "ceil", "ceiling", "abs",
            "date", "year", "month", "day", "now", "unix_timestamp", "curdate",
            "group_concat", "json_extract", "json_value",
            "case", "when", "then", "else", "end",
            "as", "asc", "desc", "and", "or", "not", "in", "is", "null",
            "distinct", "over", "partition", "by", "order",
            "varchar", "nvarchar", "int", "integer", "tinyint", "smallint", "bigint", "decimal", "numeric",
            "float", "double", "real", "char", "nchar", "text", "clob", "blob", "boolean", "bool",
            "signed", "unsigned", "time", "datetime", "timestamp", "json"
    );

    private GXSqlConstants() {
    }
}
