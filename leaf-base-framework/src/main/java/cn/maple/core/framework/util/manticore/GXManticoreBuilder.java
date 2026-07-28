package cn.maple.core.framework.util.manticore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless Manticore query construction utilities.
 */
public final class GXManticoreBuilder {
    private GXManticoreBuilder() {
    }

    /**
     * 等价于 {"match": {field: keyword}} 的全文匹配条件
     */
    public static Map<String, Object> buildMatchQuery(String field, String keyword) {
        GXManticoreUtils.requireNonBlank(field, "field");
        GXManticoreUtils.requireNonBlank(keyword, "keyword");
        Map<String, Object> match = new HashMap<>();
        match.put(field, keyword);

        Map<String, Object> query = new HashMap<>();
        query.put("match", match);
        return query;
    }

    /**
     * Equivalent to {@code {"match_phrase": {field: phrase}}}; the terms in the phrase
     * must occur consecutively in the specified full-text field.
     */
    public static Map<String, Object> buildMatchPhraseQuery(String field, String phrase) {
        GXManticoreUtils.requireNonBlank(field, "field");
        GXManticoreUtils.requireNonBlank(phrase, "phrase");
        Map<String, Object> matchPhrase = new HashMap<>();
        matchPhrase.put(field, phrase);

        Map<String, Object> query = new HashMap<>();
        query.put("match_phrase", matchPhrase);
        return query;
    }

    /**
     * Equivalent to {@code {"match_all": {}}}, matching every document.
     */
    public static Map<String, Object> buildMatchAllQuery() {
        Map<String, Object> query = new HashMap<>();
        query.put("match_all", new HashMap<>());
        return query;
    }

    /**
     * Equivalent to {@code {"exists": {"field": field}}}, matching documents that
     * contain the specified attribute or field.
     */
    public static Map<String, Object> buildExistsQuery(String field) {
        GXManticoreUtils.requireNonBlank(field, "field");
        Map<String, Object> exists = new HashMap<>();
        exists.put("field", field);

        Map<String, Object> query = new HashMap<>();
        query.put("exists", exists);
        return query;
    }

    /**
     * 等价于 {"query_string": text} 的原生 MATCH() 语法查询，text 中的特殊字符建议先用 escapeQueryString 处理
     */
    public static Map<String, Object> buildQueryString(String text) {
        GXManticoreUtils.requireNonBlank(text, "text");
        Map<String, Object> query = new HashMap<>();
        query.put("query_string", text);
        return query;
    }

    /**
     * 等价于 {"equals": {field: value}} 的精确匹配条件，常用于数值/字符串属性过滤
     */
    public static Map<String, Object> buildEqualsQuery(String field, Object value) {
        GXManticoreUtils.requireNonBlank(field, "field");
        GXManticoreUtils.requireNonNull(value, "value");
        Map<String, Object> equalsMap = new HashMap<>();
        equalsMap.put(field, value);

        Map<String, Object> query = new HashMap<>();
        query.put("equals", equalsMap);
        return query;
    }

    /**
     * 等价于 {"in": {field: [values...]}} 的多值匹配条件
     */
    public static Map<String, Object> buildInQuery(String field, List<?> values) {
        GXManticoreUtils.requireNonBlank(field, "field");
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("values must contain non-null values");
        }
        Map<String, Object> in = new HashMap<>();
        in.put(field, values);

        Map<String, Object> query = new HashMap<>();
        query.put("in", in);
        return query;
    }

    /**
     * 等价于 {"range": {field: {"gte": ..., "lte": ...}}} 的范围查询条件，
     * gte / lte 任一传 null 表示不限制该侧边界
     */
    public static Map<String, Object> buildRangeQuery(String field, Object gte, Object lte) {
        GXManticoreUtils.requireNonBlank(field, "field");
        if (gte == null && lte == null) {
            throw new IllegalArgumentException("at least one range boundary must be specified");
        }
        Map<String, Object> bounds = new HashMap<>();
        if (gte != null) {
            bounds.put("gte", gte);
        }
        if (lte != null) {
            bounds.put("lte", lte);
        }

        Map<String, Object> range = new HashMap<>();
        range.put(field, bounds);

        Map<String, Object> query = new HashMap<>();
        query.put("range", range);
        return query;
    }

    /**
     * 组合多个查询条件，等价于 {"bool": {"must": [...], "should": [...], "must_not": [...]}}，
     * 三个参数均可传 null 或空列表表示不使用该子句
     */
    public static Map<String, Object> buildBoolQuery(List<Map<String, Object>> must,
                                                     List<Map<String, Object>> should,
                                                     List<Map<String, Object>> mustNot) {
        Map<String, Object> bool = new HashMap<>();
        if (must != null && !must.isEmpty()) {
            bool.put("must", must);
        }
        if (should != null && !should.isEmpty()) {
            bool.put("should", should);
        }
        if (mustNot != null && !mustNot.isEmpty()) {
            bool.put("must_not", mustNot);
        }

        Map<String, Object> query = new HashMap<>();
        query.put("bool", bool);
        return query;
    }

    /**
     * 构造 highlight 片段参数，等价于 {"fields": {field1: {}, field2: {}, ...}}
     */
    public static Map<String, Object> buildHighlight(List<String> fields) {
        return buildHighlight(fields, null);
    }

    /**
     * 构造 highlight 片段参数，并附带全局选项（如 before_match、after_match、limit、
     * limit_words、limit_snippets、allow_empty 等，参考官方文档 Highlighting 选项列表）
     *
     * @param fields        需要高亮的字段列表
     * @param globalOptions 全局高亮选项，可传 null
     */
    public static Map<String, Object> buildHighlight(List<String> fields, Map<String, Object> globalOptions) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        Map<String, Object> fieldsMap = new HashMap<>();
        for (String field : fields) {
            GXManticoreUtils.requireNonBlank(field, "field");
            fieldsMap.put(field, new HashMap<>());
        }


        Map<String, Object> highlight = new HashMap<>();
        highlight.put("fields", fieldsMap);
        if (globalOptions != null) {
            highlight.putAll(globalOptions);
        }
        return highlight;
    }

    /**
     * 构造排序参数，等价于 [{field: order}]，order 传 "asc" 或 "desc"，
     * 可直接赋值给完整请求体的 "sort" 字段
     */
    public static List<Map<String, String>> buildSort(String field, String order) {
        GXManticoreUtils.requireNonBlank(field, "field");
        GXManticoreUtils.requireSortOrder(order);
        List<Map<String, String>> sort = new ArrayList<>();
        Map<String, String> item = new HashMap<>();
        item.put(field, order);
        sort.add(item);
        return sort;
    }

    /**
     * 构造多字段排序参数，等价于 [{field1: order1}, {field2: order2}, ...]。
     * 传入 LinkedHashMap 以保证排序字段的先后顺序生效
     */
    public static List<Map<String, String>> buildSort(Map<String, String> fieldOrderMap) {
        if (fieldOrderMap == null || fieldOrderMap.isEmpty()) {
            throw new IllegalArgumentException("fieldOrderMap must not be empty");
        }
        List<Map<String, String>> sort = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldOrderMap.entrySet()) {
            GXManticoreUtils.requireNonBlank(entry.getKey(), "field");
            GXManticoreUtils.requireSortOrder(entry.getValue());
            Map<String, String> item = new HashMap<>();
            item.put(entry.getKey(), entry.getValue());
            sort.add(item);
        }
        return sort;
    }

    /**
     * 构造一个等值条件 JOIN（INNER/LEFT），覆盖最常见的单条件等值关联场景。
     * 复杂的多条件 JOIN、跨 JSON 属性 JOIN 请直接手工构造 Map，参考官方文档 Joining 页面。
     *
     * @param type      "inner" 或 "left"
     * @param mainTable 主表（左表）名称，需与 payload 中的表名一致
     * @param mainField 主表关联字段
     * @param joinTable 被关联的表（右表）名称
     * @param joinField 右表关联字段
     * @param joinQuery 对右表的可选全文/属性过滤条件，可传 null 表示不过滤
     * @return 可直接放入 payload 的 "join" 数组中的单个元素
     */
    public static Map<String, Object> buildJoin(String type, String mainTable, String mainField,
                                                String joinTable, String joinField,
                                                Map<String, Object> joinQuery) {
        Map<String, Object> left = new HashMap<>();
        left.put("table", mainTable);
        left.put("field", mainField);

        Map<String, Object> right = new HashMap<>();
        right.put("table", joinTable);
        right.put("field", joinField);

        Map<String, Object> on = new HashMap<>();
        on.put("left", left);
        on.put("operator", "eq");
        on.put("right", right);

        List<Map<String, Object>> onList = new ArrayList<>();
        onList.add(on);

        Map<String, Object> join = new HashMap<>();
        join.put("type", type);
        join.put("table", joinTable);
        if (joinQuery != null) {
            join.put("query", joinQuery);
        }
        join.put("on", onList);
        return join;
    }

    /**
     * 构造按字段分组统计的聚合请求（对应 SQL 的 GROUP BY / FACET），
     * 等价于 {aggName: {"terms": {"field": field}}}，可直接赋值给完整请求体的 "aggs" 字段
     */
    public static Map<String, Object> buildTermsAgg(String aggName, String field) {
        Map<String, Object> terms = new HashMap<>();
        terms.put("field", field);

        Map<String, Object> agg = new HashMap<>();
        agg.put("terms", terms);

        Map<String, Object> aggs = new HashMap<>();
        aggs.put(aggName, agg);
        return aggs;
    }
}
