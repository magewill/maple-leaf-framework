package cn.maple.core.framework.util.manticore;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.*;

/**
 * Manticore Search HTTP API utility facade.
 *
 * <p>Remote operations return {@link GXMantiCoreResDto}. Configure the facade with
 * during application startup; legacy Spring
 * properties remain available as a fallback. SQL is sent as an UTF-8 form field named
 * {@code query}; bulk requests use NDJSON.</p>
 */
public final class GXManticoreQueryOperations {
    private GXManticoreQueryOperations() {
    }

    /**
     * 向量近邻检索 (/search 的 knn 语法)，要求目标表中已定义对应的向量字段
     *
     * @param index       索引/表名称
     * @param knnField    向量字段名
     * @param queryVector 查询向量
     * @param k           返回最近邻的数量
     * @param ef          HNSW 检索时的 ef 参数，越大召回越准但越慢；传 <=0 则不设置，使用默认值
     * @return 接口响应 JSON 字符串
     */
    public static GXMantiCoreResDto<JSON> knnSearch(String index, String knnField, float[] queryVector, int k, int ef) {
        requireNonBlank(index, "index");
        requireNonBlank(knnField, "knnField");
        if (queryVector == null || queryVector.length == 0 || k <= 0) {
            throw new IllegalArgumentException("queryVector must not be empty and k must be positive");
        }
        Map<String, Object> knn = new HashMap<>();
        knn.put("field", knnField);
        knn.put("k", k);
        knn.put("query_vector", toList(queryVector));
        if (ef > 0) {
            knn.put("ef", ef);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
        payload.put("knn", knn);

        return sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 等价于 {"match": {field: keyword}} 的全文匹配条件
     */
    public static Map<String, Object> buildMatchQuery(String field, String keyword) {
        requireNonBlank(field, "field");
        requireNonBlank(keyword, "keyword");
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
        requireNonBlank(field, "field");
        requireNonBlank(phrase, "phrase");
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
        requireNonBlank(field, "field");
        Map<String, Object> exists = new HashMap<>();
        exists.put("field", field);

        Map<String, Object> query = new HashMap<>();
        query.put("exists", exists);
        return query;
    }

    /**
     * 转义 query_string 全文查询语法中的操作符特殊字符（! " $ ' ( ) - / &lt; @ \ ^ | ~），
     * 避免用户输入被当作查询操作符解析、引发查询语法错误或非预期的匹配条件。
     * 仅用于拼接进 query_string 的原始文本；结构化的 match/match_phrase 查询不需要转义。
     * 内部只在字符层面添加转义反斜杠，最终 JSON 序列化时的引号/反斜杠转义由 JSONUtil 自动处理，无需重复转义。
     */
    public static String escapeQueryString(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (QUERY_STRING_SPECIAL_CHARS.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 等价于 {"query_string": text} 的原生 MATCH() 语法查询，text 中的特殊字符建议先用 escapeQueryString 处理
     */
    public static Map<String, Object> buildQueryString(String text) {
        requireNonBlank(text, "text");
        Map<String, Object> query = new HashMap<>();
        query.put("query_string", text);
        return query;
    }

    /**
     * 等价于 {"equals": {field: value}} 的精确匹配条件，常用于数值/字符串属性过滤
     */
    public static Map<String, Object> buildEqualsQuery(String field, Object value) {
        requireNonBlank(field, "field");
        requireNonNull(value, "value");
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
        requireNonBlank(field, "field");
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
        requireNonBlank(field, "field");
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
            requireNonBlank(field, "field");
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
        requireNonBlank(field, "field");
        requireSortOrder(order);
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
            requireNonBlank(entry.getKey(), "field");
            requireSortOrder(entry.getValue());
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

    // -------- /search 响应解析 --------

    /**
     * 从 /search 响应中提取命中文档列表，每条记录包含 _id、_score，
     * 并把 _source 中的字段平铺到同一层，便于直接使用
     */
    public static List<Map<String, Object>> extractHits(String searchResponseJson) {
        List<Map<String, Object>> list = new ArrayList<>();
        JSONObject root = JSONUtil.parseObj(searchResponseJson);
        JSONObject hitsObj = root.getJSONObject("hits");
        if (hitsObj == null) {
            return list;
        }
        JSONArray hits = hitsObj.getJSONArray("hits");
        if (hits == null) {
            return list;
        }
        for (int i = 0; i < hits.size(); i++) {
            JSONObject hit = hits.getJSONObject(i);
            Map<String, Object> row = new HashMap<>();
            row.put("_id", hit.get("_id"));
            row.put("_score", hit.get("_score"));
            JSONObject source = hit.getJSONObject("_source");
            if (source != null) {
                row.putAll(source);
            }
            row.put("_id", hit.get("_id"));
            row.put("_score", hit.get("_score"));
            list.add(row);
        }
        return list;
    }

    /**
     * 从 /search 响应中提取命中总数 (hits.total)，解析失败时返回 0
     */
    public static long extractTotal(String searchResponseJson) {
        JSONObject root = JSONUtil.parseObj(searchResponseJson);
        JSONObject hitsObj = root.getJSONObject("hits");
        if (hitsObj == null) {
            return 0L;
        }
        Object total = hitsObj.get("total");
        if (total == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(total));
        } catch (NumberFormatException e) {
            return 0L;
        }
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

    /**
     * 从 /search 响应中提取指定聚合的分桶结果（aggregations.&lt;aggName&gt;.buckets），
     * 每个桶通常包含 key（分组值）与 doc_count（分组内文档数）
     */
    public static List<Map<String, Object>> extractAggBuckets(String searchResponseJson, String aggName) {
        List<Map<String, Object>> buckets = new ArrayList<>();
        JSONObject root = JSONUtil.parseObj(searchResponseJson);
        JSONObject aggregations = root.getJSONObject("aggregations");
        if (aggregations == null) {
            return buckets;
        }
        JSONObject agg = aggregations.getJSONObject(aggName);
        if (agg == null) {
            return buckets;
        }
        JSONArray bucketArr = agg.getJSONArray("buckets");
        if (bucketArr == null) {
            return buckets;
        }
        for (int i = 0; i < bucketArr.size(); i++) {
            buckets.add(bucketArr.getJSONObject(i));
        }
        return buckets;
    }

    private static void requireSortOrder(String order) {
        if (!"asc".equalsIgnoreCase(order) && !"desc".equalsIgnoreCase(order)) {
            throw new IllegalArgumentException("order must be asc or desc");
        }
    }
}

