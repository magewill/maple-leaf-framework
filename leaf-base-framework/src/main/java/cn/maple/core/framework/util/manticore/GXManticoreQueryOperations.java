package cn.maple.core.framework.util.manticore;

import cn.hutool.json.JSONUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Search and KNN operations bound to one {@link GXManticoreClient}.
 *
 * <p>Every remote operation returns the original Manticore response body.</p>
 */
public final class GXManticoreQueryOperations {
    private final GXManticoreClient client;

    GXManticoreQueryOperations(GXManticoreClient client) {
        this.client = client;
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
    public String knnSearch(String index, String knnField, float[] queryVector, int k, int ef) {
        GXManticoreUtils.requireNonBlank(index, "index");
        GXManticoreUtils.requireNonBlank(knnField, "knnField");
        if (queryVector == null || queryVector.length == 0 || k <= 0) {
            throw new IllegalArgumentException("queryVector must not be empty and k must be positive");
        }
        Map<String, Object> knn = new HashMap<>();
        knn.put("field", knnField);
        knn.put("k", k);
        knn.put("query_vector", GXManticoreUtils.toList(queryVector));
        if (ef > 0) {
            knn.put("ef", ef);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        payload.put("knn", knn);

        return client.sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * JSON DSL 查询 (/search)
     *
     * @param index  索引/表名称
     * @param query  查询条件 Map（如 query_string 全文检索、bool 组合条件等，可用 build* 系列方法构造）
     * @param offset 偏移量/跳过条数 (如 0)
     * @param limit  获取条数/每页大小 (如 10)
     * @return 接口响应 JSON 字符串
     */
    public String search(String index, Map<String, Object> query, int offset, int limit) {
        GXManticoreUtils.requireSearchArguments(index, offset, limit);
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        if (query != null) {
            payload.put("query", query);
        }
        payload.put("offset", offset);
        payload.put("limit", limit);

        return client.sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * JSON DSL 查询 (/search)，并附带查询选项（对应 SQL 的 OPTION 子句），
     * 如 ranker、max_matches、field_weights、cutoff 等，参考 Manticore 官方文档 Search Options
     *
     * @param index   索引/表名称
     * @param query   查询条件 Map
     * @param offset  偏移量/跳过条数
     * @param limit   获取条数/每页大小
     * @param options 查询选项 Map，如 {"ranker": "bm25", "max_matches": 3000}；传 null 或空则不附带
     * @return 接口响应 JSON 字符串
     */
    public String search(String index, Map<String, Object> query, int offset, int limit,
                         Map<String, Object> options) {
        GXManticoreUtils.requireSearchArguments(index, offset, limit);
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        if (query != null) {
            payload.put("query", query);
        }
        payload.put("offset", offset);
        payload.put("limit", limit);
        if (options != null && !options.isEmpty()) {
            payload.put("options", options);
        }

        return client.sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 完整自定义查询 (/search) —— 当需要 sort / highlight / source / aggs / knn 等
     * 高级参数时，直接传入完整的请求体 Map（需自行包含表名字段）
     *
     * @param fullPayload 完整的 /search 请求体
     * @return 接口响应 JSON 字符串
     */
    public String search(Map<String, Object> fullPayload) {
        GXManticoreUtils.requireNonEmpty(fullPayload, "fullPayload");
        return client.sendPost("/search", JSONUtil.toJsonStr(fullPayload), "application/json");
    }
}
