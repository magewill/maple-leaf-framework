package cn.maple.core.framework.util.manticore;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXBusinessException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class GXManticoreUtils {
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public static void requireNonBlank(String value, String name) {
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    public static void requireSortOrder(String order) {
        if (!"asc".equalsIgnoreCase(order) && !"desc".equalsIgnoreCase(order)) {
            throw new IllegalArgumentException("order must be asc or desc");
        }
    }

    public static boolean isQueryStringSpecialCharacter(char value) {
        return value == 33 || value == 34 || value == 36 || value == 39 || value == 40 || value == 41
                || value == 45 || value == 47 || value == 60 || value == 64 || value == 92 || value == 94
                || value == 124 || value == 126;
    }

    /**
     * 从 /search 响应中提取指定聚合的分桶结果（aggregations.<aggName>.buckets），
     * 每个桶通常包含 key（分组值）与 doc_count（分组内文档数）
     *
     * @param searchResponseJson 搜索响应 JSON 字符串
     * @param aggName            聚合名称（例如 "by_category"）
     * @return 分桶 Map 列表
     */
    public static List<Map<String, Object>> extractAggBuckets(String searchResponseJson, String aggName) {
        List<Map<String, Object>> buckets = new ArrayList<>();
        if (StrUtil.isBlank(searchResponseJson) || StrUtil.isBlank(aggName)) {
            return buckets;
        }

        try {
            JSONObject root = JSONUtil.parseObj(searchResponseJson);
            JSONObject aggregations = root.getJSONObject("aggregations");
            if (aggregations == null) {
                aggregations = root.getJSONObject("aggs");
            }
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
                JSONObject bucketObj = bucketArr.getJSONObject(i);
                if (bucketObj != null) {
                    buckets.add(new HashMap<>(bucketObj));
                }
            }
        } catch (Exception e) {
            throw new GXBusinessException("Manticore返回的数据格式有误!", e);
        }

        return buckets;
    }

    /**
     * 从 /search 响应中提取命中文档列表，每条记录包含 _id、_score，
     * 并把 _source 中的字段平铺到同一层，便于直接使用
     */
    public static List<Map<String, Object>> extractHits(String searchResponseJson) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (StrUtil.isBlank(searchResponseJson)) {
            return list;
        }

        try {
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
                if (hit == null) {
                    continue;
                }

                Map<String, Object> row = new HashMap<>();
                JSONObject source = hit.getJSONObject("_source");
                if (source != null) {
                    row.putAll(source);
                }
                row.put("_id", hit.get("_id"));
                row.put("_score", hit.get("_score"));

                list.add(row);
            }
        } catch (Exception e) {
            throw new GXBusinessException("Manticore返回的数据格式有误!", e);
        }
        return list;
    }

    /**
     * 从 /search 响应中提取命中总数 (hits.total)，解析失败时返回 0
     */
    public static long extractTotal(String searchResponseJson) {
        if (StrUtil.isBlank(searchResponseJson)) {
            return 0L;
        }
        try {
            JSONObject root = JSONUtil.parseObj(searchResponseJson);
            JSONObject hitsObj = root.getJSONObject("hits");
            if (hitsObj == null) {
                return 0L;
            }

            Object total = hitsObj.get("total");
            switch (total) {
                case null -> {
                    return 0L;
                }
                case Number number -> {
                    return number.longValue();
                }
                case JSONObject totalObj -> {
                    Long value = totalObj.getLong("value");
                    return value != null ? value : 0L;
                }
                default -> {
                }
            }

            return Long.parseLong(String.valueOf(total));
        } catch (Exception e) {
            return 0L;
        }
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
            if (isQueryStringSpecialCharacter(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static void requireNonEmpty(Map<?, ?> value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }

    public static void requireSearchArguments(String index, int offset, int limit) {
        requireNonBlank(index, "index");
        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException("offset must be non-negative and limit must be positive");
        }
    }

    public static void requireIdentifier(String value, String name) {
        if (StrUtil.isBlank(value) || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a valid Manticore identifier");
        }
    }

    public static Dict parseJson(String responseContentType, String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        if (!StrUtil.containsIgnoreCase(responseContentType, "json") && JSONUtil.isTypeJSON(body)) {
            return null;
        }
        Dict searchResult = JSONUtil.toBean(body, Dict.class);
        if (searchResult == null) {
            return null;
        }
        boolean timedOut = searchResult.getBool("timed_out");
        /*Dict hits = Convert.convert(Dict.class, searchResult.getObj("hits"));
        Integer total = Convert.convert(Integer.class, hits.getObj("total"));
        List<Dict> lastSearchResult = JSONUtil.toList(hits.getStr("hits"), Dict.class);
        List<Dict> records = new ArrayList<>();
        Dict retData = new Dict();
        for (Dict d : lastSearchResult) {
            int id = d.getInt("_id");
            records.add(Dict.create().set("id", id).set("data", d));
        }*/
        List<Map<String, Object>> records = GXManticoreUtils.extractHits(body);
        long total = GXManticoreUtils.extractTotal(body);
        return Dict.create().set("timedOut", timedOut).set("total", total).set("records", records);
    }
}
