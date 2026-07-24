package cn.maple.core.framework.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXManticoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manticore Search HTTP API 操作工具类。
 *
 * <p>覆盖了增/删/改/查、批量写入（/bulk，含自动分批）、按条件更新与删除、
 * 原生 SQL（/sql）、常用表管理语句、向量检索（KNN）、常用查询 DSL 构造器、
 * 结果解析、Percolate（反向搜索）以及 Autocomplete 自动补全等场景。
 * 所有方法默认返回 Manticore 接口原始的 JSON 字符串，调用方可自行用
 * {@code JSONUtil} 解析，也可以使用 {@link #executeSqlAsList(String)}、
 * {@link #extractHits(String)} 等便捷方法直接拿到结构化结果。</p>
 *
 * <p><b>安全提示：</b>示例中的 baseUrl / username / password 仅作为默认值占位，
 * 生产环境请勿把真实账号密码硬编码在源码中，建议通过配置中心 / 环境变量
 * 在应用启动时（例如 Spring 的 {@code @PostConstruct}）调用
 * {@link #initConfig(String, String, String)} 完成注入。</p>
 */
public class GXMantiCoreUtils {
    private static final Logger log = LoggerFactory.getLogger(GXMantiCoreUtils.class);

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 15000;
    private static final int DEFAULT_BULK_BATCH_SIZE = 1000;
    // query_string 语法中作为操作符使用、必须转义的特殊字符
    private static final String QUERY_STRING_SPECIAL_CHARS = "!\"$'()-/<@\\^|~";
    // 1. 默认服务器与认证配置（建议通过 initConfig 在启动时覆盖，而不是直接改这里）
    private static String baseUrl = "http://192.168.7.209:9308";
    private static String username = "britton";
    private static String password = "britton@123!!";
    private static int connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS;
    private static int readTimeout = DEFAULT_READ_TIMEOUT_MS;
    // 请求体中表示"表/索引名称"的字段名。旧版本 Manticore 用 "index"，
    // 当前官方文档（Manticore 6.x+）已统一改为 "table"（"index" 作为兼容别名仍可使用）。
    // 默认保持 "index" 以兼容本类历史用法，若使用较新版本建议启动时调用 useTableKeyword()。
    private static volatile String tableFieldName = "index";
    // 网络抖动重试策略，默认不重试（maxRetries=0），避免掩盖真实问题；
    // 可通过 setRetryPolicy 按需开启。仅对连接/IO 异常重试，HTTP 状态码错误不重试。
    private static volatile int maxRetries = 0;
    private static volatile long retryBackoffMs = 200L;

    private GXMantiCoreUtils() {
    }

    /**
     * 动态修改连接配置（可在 Spring 项目的 @PostConstruct 或启动配置类中调用）
     */
    public static void initConfig(String url, String user, String pwd) {
        initConfig(url, user, pwd, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    /**
     * 动态修改连接配置，并自定义连接/读取超时时间
     *
     * @param url              服务地址，如 http://127.0.0.1:9308
     * @param user             basic auth 用户名
     * @param pwd              basic auth 密码
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读取超时（毫秒）
     */
    public static void initConfig(String url, String user, String pwd, int connectTimeoutMs, int readTimeoutMs) {
        baseUrl = StrUtil.removeSuffix(url, "/");
        username = user;
        password = pwd;
        connectTimeout = connectTimeoutMs;
        readTimeout = readTimeoutMs;
    }

    /**
     * 请求体中改用 "table" 作为表名字段（较新版本 Manticore 推荐写法）
     */
    public static void useTableKeyword() {
        tableFieldName = "table";
    }

    /**
     * 请求体中改用 "index" 作为表名字段（默认，兼容老版本）
     */
    public static void useIndexKeyword() {
        tableFieldName = "index";
    }

    /**
     * 配置网络异常时的重试策略
     *
     * @param retries   最大重试次数，0 表示不重试
     * @param backoffMs 每次重试之间的基础退避时间（毫秒），实际等待时间 = backoffMs * 当前重试次数
     */
    public static void setRetryPolicy(int retries, long backoffMs) {
        maxRetries = Math.max(0, retries);
        retryBackoffMs = Math.max(0, backoffMs);
    }

    /**
     * 单条数据插入 (/insert)
     *
     * @param index 索引/表名称（如 manticore_periodical_articles）
     * @param id    文档 ID（如传 null，Manticore 会自动生成 64位长整型 ID）
     * @param doc   文档字段键值对 Map
     * @return 接口响应 JSON 字符串
     */
    public static String insert(String index, Long id, Map<String, Object> doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("doc", doc);

        return sendPost("/insert", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 局部更新 (/update) —— 只更新 doc 中传入的指定字段
     *
     * @param index 索引/表名称
     * @param id    文档 ID（必须指定）
     * @param doc   需要修改的字段键值对 Map
     * @return 接口响应 JSON 字符串
     */
    public static String update(String index, Long id, Map<String, Object> doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
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
    public static String updateByQuery(String index, Map<String, Object> query, Map<String, Object> doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
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
    public static String replace(String index, Long id, Map<String, Object> doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
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
    public static String deleteById(String index, Long id) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
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
    public static String deleteByQuery(String index, Map<String, Object> query) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
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
    public static String bulkInsert(String index, List<Map<String, Object>> docList, String idFieldName) {
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
    public static String bulkReplace(String index, List<Map<String, Object>> docList, String idFieldName) {
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
    public static String bulkUpdate(String index, List<Map<String, Object>> docList, String idFieldName) {
        return bulkAction("update", index, docList, idFieldName, true);
    }

    /**
     * 按 ID 列表批量删除 (/bulk)
     *
     * @param index 索引/表名称
     * @param ids   待删除的文档 ID 列表
     * @return 接口响应 JSON 字符串
     */
    public static String bulkDeleteByIds(String index, List<Long> ids) {
        StringBuilder ndjson = new StringBuilder();
        for (Long id : ids) {
            Map<String, Object> deleteDetail = new HashMap<>();
            deleteDetail.put(tableFieldName, index);
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
     * @param batchSize 每批条数，&lt;=0 时使用默认值 {@value #DEFAULT_BULK_BATCH_SIZE}
     * @return 每一批请求各自的接口响应 JSON 字符串列表，按发送顺序排列
     */
    public static List<String> bulkInsertBatched(String index, List<Map<String, Object>> docList,
                                                 String idFieldName, int batchSize) {
        return batchedBulkAction("insert", index, docList, idFieldName, false, batchSize);
    }

    /**
     * 自动分批的批量替换，用法同 {@link #bulkInsertBatched(String, List, String, int)}
     */
    public static List<String> bulkReplaceBatched(String index, List<Map<String, Object>> docList,
                                                  String idFieldName, int batchSize) {
        return batchedBulkAction("replace", index, docList, idFieldName, true, batchSize);
    }

    /**
     * 自动分批的批量更新，用法同 {@link #bulkInsertBatched(String, List, String, int)}
     */
    public static List<String> bulkUpdateBatched(String index, List<Map<String, Object>> docList,
                                                 String idFieldName, int batchSize) {
        return batchedBulkAction("update", index, docList, idFieldName, true, batchSize);
    }

    private static List<String> batchedBulkAction(String action, String index, List<Map<String, Object>> docList,
                                                  String idFieldName, boolean idRequired, int batchSize) {
        int bs = batchSize > 0 ? batchSize : DEFAULT_BULK_BATCH_SIZE;
        List<String> responses = new ArrayList<>();
        int total = docList.size();
        for (int start = 0; start < total; start += bs) {
            int end = Math.min(start + bs, total);
            List<Map<String, Object>> sub = docList.subList(start, end);
            responses.add(bulkAction(action, index, sub, idFieldName, idRequired));
        }
        return responses;
    }

    private static String bulkAction(String action, String index, List<Map<String, Object>> docList,
                                     String idFieldName, boolean idRequired) {
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> doc : docList) {
            Map<String, Object> detail = new HashMap<>();
            detail.put(tableFieldName, index);

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

    /**
     * JSON DSL 查询 (/search)
     *
     * @param index  索引/表名称
     * @param query  查询条件 Map（如 query_string 全文检索、bool 组合条件等，可用 build* 系列方法构造）
     * @param offset 偏移量/跳过条数 (如 0)
     * @param limit  获取条数/每页大小 (如 10)
     * @return 接口响应 JSON 字符串
     */
    public static String search(String index, Map<String, Object> query, int offset, int limit) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
        if (query != null) {
            payload.put("query", query);
        }
        payload.put("offset", offset);
        payload.put("limit", limit);

        return sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
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
    public static String search(String index, Map<String, Object> query, int offset, int limit,
                                Map<String, Object> options) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(tableFieldName, index);
        if (query != null) {
            payload.put("query", query);
        }
        payload.put("offset", offset);
        payload.put("limit", limit);
        if (options != null && !options.isEmpty()) {
            payload.put("options", options);
        }

        return sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 完整自定义查询 (/search) —— 当需要 sort / highlight / source / aggs / knn 等
     * 高级参数时，直接传入完整的请求体 Map（需自行包含表名字段）
     *
     * @param fullPayload 完整的 /search 请求体
     * @return 接口响应 JSON 字符串
     */
    public static String search(Map<String, Object> fullPayload) {
        return sendPost("/search", JSONUtil.toJsonStr(fullPayload), "application/json");
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
    public static String knnSearch(String index, String knnField, float[] queryVector, int k, int ef) {
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
        Map<String, Object> match = new HashMap<>();
        match.put(field, keyword);

        Map<String, Object> query = new HashMap<>();
        query.put("match", match);
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
        Map<String, Object> query = new HashMap<>();
        query.put("query_string", text);
        return query;
    }

    /**
     * 等价于 {"equals": {field: value}} 的精确匹配条件，常用于数值/字符串属性过滤
     */
    public static Map<String, Object> buildEqualsQuery(String field, Object value) {
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
        Map<String, Object> fieldsMap = new HashMap<>();
        for (String field : fields) {
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
        List<Map<String, String>> sort = new ArrayList<>();
        for (Map.Entry<String, String> entry : fieldOrderMap.entrySet()) {
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

    /**
     * 执行原生 Manticore SQL 脚本 (/sql)，返回原始响应
     *
     * @param sql 完整的 Manticore SQL 语句
     * @return 接口响应 JSON 字符串
     */
    public static String executeSql(String sql) {
        String body = "query=" + URLUtil.encode(sql, StandardCharsets.UTF_8);
        return sendPost("/sql", body, "application/x-www-form-urlencoded");
    }

    /**
     * 执行 SELECT 类型的 SQL 并将结果解析为 List&lt;Map&gt;，便于直接使用。
     * 对于 DDL/DML 语句（无 data 字段返回），返回空列表。
     *
     * @param sql SELECT 语句
     * @return 每行数据组成的 List，每行是列名到值的 Map
     */
    public static List<Map<String, Object>> executeSqlAsList(String sql) {
        String resp = executeSql(sql);
        List<Map<String, Object>> result = new ArrayList<>();

        JSONArray arr = JSONUtil.parseArray(resp);
        if (arr.isEmpty()) {
            return result;
        }
        JSONObject first = arr.getJSONObject(0);
        String error = first.getStr("error");
        if (StrUtil.isNotBlank(error)) {
            throw new GXManticoreException("SQL 执行失败: " + error, resp);
        }

        JSONArray data = first.getJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                result.add(data.getJSONObject(i));
            }
        }
        return result;
    }

    /**
     * SHOW TABLES —— 列出所有表/索引
     */
    public static String showTables() {
        return executeSql("SHOW TABLES");
    }

    /**
     * DESCRIBE table —— 查看表结构
     */
    public static String describeTable(String index) {
        return executeSql("DESCRIBE " + index);
    }

    /**
     * SHOW CREATE TABLE —— 查看建表语句
     */
    public static String showCreateTable(String index) {
        return executeSql("SHOW CREATE TABLE " + index);
    }

    /**
     * 执行任意建表语句，例如 "CREATE TABLE products(title text, price float)"
     */
    public static String createTable(String createTableSql) {
        return executeSql(createTableSql);
    }

    /**
     * DROP TABLE，可选 IF EXISTS 避免表不存在时报错
     */
    public static String dropTable(String index, boolean ifExists) {
        String sql = ifExists ? "DROP TABLE IF EXISTS " + index : "DROP TABLE " + index;
        return executeSql(sql);
    }

    /**
     * TRUNCATE TABLE —— 清空表数据，保留结构
     */
    public static String truncateTable(String index) {
        return executeSql("TRUNCATE TABLE " + index);
    }

    /**
     * OPTIMIZE INDEX —— 合并磁盘 chunk，回收已删除文档占用的空间
     */
    public static String optimizeTable(String index) {
        return executeSql("OPTIMIZE INDEX " + index);
    }

    // ==================== Percolate Query 反向搜索（PQ） ====================
    // PQ 的用法是"反过来"的：先把一批"查询条件"当作文档存进 PQ 表，
    // 后续来一条新文档时，用它去匹配这些查询条件，看命中了哪些规则——
    // 常用于内容匹配报警、订阅推送等场景。

    /**
     * 向 PQ（percolate）表中存储一条查询规则。
     * PQ 规则本质上也是通过 /insert 写入的特殊文档，因此复用了普通的 insert 逻辑。
     *
     * @param pqIndex percolate 表名称（需提前用 CREATE TABLE ... type='pq' 建好）
     * @param id      规则 ID，传 null 由 Manticore 自动生成
     * @param query   规则对应的查询条件（与 /search 的 query DSL 语法一致，可用 build* 方法构造）
     * @param tags    可选标签列表，便于后续按标签筛选或删除规则
     * @return 接口响应 JSON 字符串
     */
    public static String pqAddRule(String pqIndex, Long id, Map<String, Object> query, List<String> tags) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("query", query);
        if (tags != null && !tags.isEmpty()) {
            doc.put("tags", tags);
        }
        return insert(pqIndex, id, doc);
    }

    /**
     * 用一条文档去匹配 PQ 表中存储的规则，返回命中的规则列表（/pq/&lt;index&gt;/search）
     *
     * @param pqIndex  percolate 表名称
     * @param document 待匹配的文档内容
     * @return 接口响应 JSON 字符串，命中的规则位于 hits.hits 数组中
     */
    public static String pqMatchDocument(String pqIndex, Map<String, Object> document) {
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("document", document);

        Map<String, Object> query = new HashMap<>();
        query.put("percolate", percolate);

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);

        return sendPost("/pq/" + pqIndex + "/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 用多条文档批量匹配 PQ 表中存储的规则（/pq/&lt;index&gt;/search）
     *
     * @param pqIndex   percolate 表名称
     * @param documents 待匹配的文档列表
     * @return 接口响应 JSON 字符串
     */
    public static String pqMatchDocuments(String pqIndex, List<Map<String, Object>> documents) {
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("documents", documents);

        Map<String, Object> query = new HashMap<>();
        query.put("percolate", percolate);

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);

        return sendPost("/pq/" + pqIndex + "/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    // ==================== 自动补全 (/autocomplete) ====================
    // 依赖 Manticore Buddy 组件，且目标表需要开启 min_infix_len 才能生效。

    /**
     * 输入前缀，获取自动补全建议
     *
     * @param table 表名称
     * @param query 用户已输入的前缀文本
     * @return 接口响应 JSON 字符串
     */
    public static String autocomplete(String table, String query) {
        return autocomplete(table, query, null);
    }

    /**
     * 输入前缀，获取自动补全建议，并支持传入额外参数
     * （如 fuzziness、expansion_len、layouts、max_matches 等，参考 Manticore 官方文档）
     *
     * @param table        表名称
     * @param query        用户已输入的前缀文本
     * @param extraOptions 额外参数，可传 null
     * @return 接口响应 JSON 字符串
     */
    public static String autocomplete(String table, String query, Map<String, Object> extraOptions) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("table", table);
        payload.put("query", query);
        if (extraOptions != null) {
            payload.putAll(extraOptions);
        }
        return sendPost("/autocomplete", JSONUtil.toJsonStr(payload), "application/json");
    }

    private static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }

    private static String sendPost(String endpoint, String body, String contentType) {
        int attempt = 0;
        while (true) {
            try {
                HttpResponse response = HttpRequest.post(baseUrl + endpoint)
                        .basicAuth(username, password)
                        .header(Header.CONTENT_TYPE, contentType)
                        .charset(StandardCharsets.UTF_8)
                        .setConnectionTimeout(connectTimeout)
                        .setReadTimeout(readTimeout)
                        .body(body)
                        .execute();

                String result = response.body();
                if (!response.isOk()) {
                    log.warn("Manticore 接口调用失败, endpoint={}, status={}, response={}",
                            endpoint, response.getStatus(), result);
                    throw new GXManticoreException(
                            "Manticore 接口调用失败, status=" + response.getStatus() + ", endpoint=" + endpoint, result);
                }
                return result;
            } catch (GXManticoreException e) {
                throw e;
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries) {
                    log.error("调用 Manticore 接口异常（已重试 {} 次）, endpoint={}", attempt - 1, endpoint, e);
                    throw new GXManticoreException("调用 Manticore 接口异常: " + e.getMessage(), null);
                }
                log.warn("调用 Manticore 接口异常，准备第 {} 次重试, endpoint={}, error={}",
                        attempt, endpoint, e.getMessage());
                sleepQuietly(retryBackoffMs * attempt);
            }
        }
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}