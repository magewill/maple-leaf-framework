package cn.maple.core.framework.util.manticore;

import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Document and bulk operations bound to one {@link GXManticoreClient}.
 *
 * <p>Every remote operation returns the original Manticore response body.</p>
 */
public final class GXManticoreDocumentOperations {
    private final GXManticoreClient client;

    GXManticoreDocumentOperations(GXManticoreClient client) {
        this.client = client;
    }

    /**
     * 单条数据插入 (/insert)
     *
     * @param index 索引/表名称（如 manticore_periodical_articles）
     * @param id    文档 ID（如传 null，Manticore 会自动生成 64位长整型 ID）
     * @param doc   文档字段键值对 Map
     * @return 接口响应 JSON 字符串
     */
    public String insert(String index, Long id, Map<String, Object> doc) {
        requireNonBlank(index, "index");
        requireNonEmpty(doc, "doc");
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("doc", doc);

        return sendPost("/insert", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 局部更新 (/update) —— 只更新 doc 中传入的指定字段（按行覆盖属性值）。
     * <p><b>注意：</b>UPDATE 无法修改全文字段（text 类型）和 columnar 属性，doc 中传入这些字段会被忽略或报错；
     * 如需修改全文字段/columnar 属性的内容，必须用 {@link #replace(String, Long, Map)} 整条替换。
     *
     * @param index 索引/表名称
     * @param id    文档 ID（必须指定）
     * @param doc   需要修改的字段键值对 Map（不能包含全文字段/columnar 属性）
     * @return 接口响应 JSON 字符串
     */
    public String update(String index, Long id, Map<String, Object> doc) {
        requireNonBlank(index, "index");
        requireNonNull(id, "id");
        requireNonEmpty(doc, "doc");
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        payload.put("id", id);
        payload.put("doc", doc);

        return sendPost("/update", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 按查询条件批量更新 (/update) —— 更新所有匹配 query 条件的文档，无需预先知道 id
     *
     * @param index 索引/表名称
     * @param query 匹配条件 Map（结构同 /search 中的 query，如 equals / range 等，可用 build* 系列方法构造）
     * @param doc   需要修改的字段键值对 Map
     * @return 接口响应 JSON 字符串
     */
    public String updateByQuery(String index, Map<String, Object> query, Map<String, Object> doc) {
        requireNonBlank(index, "index");
        requireNonEmpty(query, "query");
        requireNonEmpty(doc, "doc");
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        payload.put("query", query);
        payload.put("doc", doc);

        return sendPost("/update", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 全量覆盖/替换 (/replace) —— 替换整个文档；若 ID 不存在则直接创建
     *
     * @param index 索引/表名称
     * @param id    文档 ID
     * @param doc   完整文档字段键值对 Map
     * @return 接口响应 JSON 字符串
     */
    public String replace(String index, Long id, Map<String, Object> doc) {
        requireNonBlank(index, "index");
        requireNonEmpty(doc, "doc");
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("doc", doc);

        return sendPost("/replace", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 根据 ID 删除单条数据 (/delete)
     *
     * @param index 索引/表名称
     * @param id    要删除的文档 ID
     * @return 接口响应 JSON 字符串
     */
    public String deleteById(String index, Long id) {
        requireNonBlank(index, "index");
        requireNonNull(id, "id");
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        payload.put("id", id);

        return sendPost("/delete", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 根据条件批量删除数据 (/delete)
     *
     * @param index 索引/表名称
     * @param query 删除条件 Map（如 equals 匹配、range 范围等，可用 build* 系列方法构造）
     * @return 接口响应 JSON 字符串
     */
    public String deleteByQuery(String index, Map<String, Object> query) {
        requireNonBlank(index, "index");
        requireNonEmpty(query, "query");
        Map<String, Object> payload = new HashMap<>();
        payload.put(client.getTableFieldName(), index);
        payload.put("query", query);

        return sendPost("/delete", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 批量插入 (/bulk) —— 推荐海量数据或批量导入时使用。
     * 若数据量很大，建议改用 {@link #bulkInsertBatched(String, List, String, int)} 自动分批发送。
     *
     * @param index       索引/表名称
     * @param docList     文档字段 Map 列表
     * @param idFieldName docMap 中作为主键 id 的字段名称（例如 "id"）；若无需指定可传 null，由 Manticore 自动生成
     * @return 接口响应 JSON 字符串
     */
    public String bulkInsert(String index, List<Map<String, Object>> docList, String idFieldName) {
        return bulkAction("insert", index, docList, idFieldName, false);
    }

    /**
     * 批量替换 (/bulk) —— 每条记录必须包含 id，若不存在则新建，存在则整条覆盖
     *
     * @param index       索引/表名称
     * @param docList     文档字段 Map 列表
     * @param idFieldName docMap 中作为主键 id 的字段名称，必须存在
     * @return 接口响应 JSON 字符串
     */
    public String bulkReplace(String index, List<Map<String, Object>> docList, String idFieldName) {
        return bulkAction("replace", index, docList, idFieldName, true);
    }

    /**
     * 批量局部更新 (/bulk) —— 每条记录必须包含 id，只更新 doc 中出现的字段
     *
     * @param index       索引/表名称
     * @param docList     文档字段 Map 列表
     * @param idFieldName docMap 中作为主键 id 的字段名称，必须存在
     * @return 接口响应 JSON 字符串
     */
    public String bulkUpdate(String index, List<Map<String, Object>> docList, String idFieldName) {
        return bulkAction("update", index, docList, idFieldName, true);
    }

    /**
     * 按 ID 列表批量删除 (/bulk)
     *
     * @param index 索引/表名称
     * @param ids   待删除的文档 ID 列表
     * @return 接口响应 JSON 字符串
     */
    public String bulkDeleteByIds(String index, List<Long> ids) {
        requireNonBlank(index, "index");
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("ids must contain non-null IDs");
        }
        StringBuilder ndjson = new StringBuilder();
        for (Long id : ids) {
            Map<String, Object> deleteDetail = new HashMap<>();
            deleteDetail.put(client.getTableFieldName(), index);
            deleteDetail.put("id", id);

            Map<String, Object> line = new HashMap<>();
            line.put("delete", deleteDetail);
            ndjson.append(JSONUtil.toJsonStr(line)).append("\n");
        }
        return sendPost("/bulk", ndjson.toString(), "application/x-ndjson");
    }

    /**
     * 自动分批的批量插入 —— 将 docList 按 batchSize 切分为多次 /bulk 请求，
     * 避免单次请求体过大导致超时或被服务端拒绝
     *
     * @param batchSize 每批条数，&lt;=0 时使用默认值
     * @return 每一批请求各自的接口响应 JSON 字符串列表，按发送顺序排列
     */
    public List<String> bulkInsertBatched(String index, List<Map<String, Object>> docList,
                                          String idFieldName, int batchSize) {
        return batchedBulkAction("insert", index, docList, idFieldName, false, batchSize);
    }

    /**
     * 自动分批的批量替换，用法同 {@link #bulkInsertBatched(String, List, String, int)}
     */
    public List<String> bulkReplaceBatched(String index, List<Map<String, Object>> docList,
                                           String idFieldName, int batchSize) {
        return batchedBulkAction("replace", index, docList, idFieldName, true, batchSize);
    }

    /**
     * 自动分批的批量更新，用法同 {@link #bulkInsertBatched(String, List, String, int)}
     */
    public List<String> bulkUpdateBatched(String index, List<Map<String, Object>> docList,
                                          String idFieldName, int batchSize) {
        return batchedBulkAction("update", index, docList, idFieldName, true, batchSize);
    }

    private List<String> batchedBulkAction(String action, String index, List<Map<String, Object>> docList,
                                           String idFieldName, boolean idRequired, int batchSize) {
        if (docList == null || docList.isEmpty()) {
            throw new IllegalArgumentException("docList must contain non-empty documents");
        }
        int bs = batchSize > 0 ? batchSize : client.getDefaultBulkBatchSize();
        List<String> responses = new ArrayList<>();
        int total = docList.size();
        for (int start = 0; start < total; start += bs) {
            int end = Math.min(start + bs, total);
            List<Map<String, Object>> sub = docList.subList(start, end);
            responses.add(bulkAction(action, index, sub, idFieldName, idRequired));
        }
        return responses;
    }

    private String bulkAction(String action, String index, List<Map<String, Object>> docList,
                              String idFieldName, boolean idRequired) {
        requireNonBlank(index, "index");
        if (docList == null || docList.isEmpty() || docList.stream().anyMatch(doc -> doc == null || doc.isEmpty())) {
            throw new IllegalArgumentException("docList must contain non-empty documents");
        }
        if (idRequired) {
            requireNonBlank(idFieldName, "idFieldName");
        }
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> doc : docList) {
            Map<String, Object> detail = new HashMap<>();
            detail.put(client.getTableFieldName(), index);

            Object idVal = idFieldName != null ? doc.get(idFieldName) : null;
            if (idVal != null) {
                detail.put("id", idVal);
            } else if (idRequired) {
                throw new IllegalArgumentException(
                        "action=" + action + " 需要文档中包含有效的 id 字段: " + idFieldName);
            }
            detail.put("doc", doc);

            Map<String, Object> line = new HashMap<>();
            line.put(action, detail);
            ndjson.append(JSONUtil.toJsonStr(line)).append("\n");
        }
        return sendPost("/bulk", ndjson.toString(), "application/x-ndjson");
    }

    private void requireNonBlank(String value, String name) {
        GXManticoreUtils.requireNonBlank(value, name);
    }

    private void requireNonEmpty(Map<?, ?> value, String name) {
        GXManticoreUtils.requireNonEmpty(value, name);
    }

    private void requireNonNull(Object value, String name) {
        GXManticoreUtils.requireNonNull(value, name);
    }

    private void requireSearchArguments(String index, int offset, int limit) {
        GXManticoreUtils.requireSearchArguments(index, offset, limit);
    }

    private String sendPost(String endpoint, String body, String contentType) {
        return client.sendPost(endpoint, body, contentType);
    }
}
