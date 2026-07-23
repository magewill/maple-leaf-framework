package cn.maple.core.framework.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
 * <p>覆盖了增/删/改/查、批量写入（/bulk）、按条件更新与删除、原生 SQL（/sql）、
 * 常用表管理语句以及向量检索（KNN）等常见场景。所有方法返回的都是 Manticore
 * 接口原始的 JSON 字符串，调用方可自行用 {@code JSONUtil} 解析，
 * 也可以使用 {@link #executeSqlAsList(String)} 等便捷方法直接拿到结构化结果。</p>
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

    // 1. 默认服务器与认证配置（建议通过 initConfig 在启动时覆盖，而不是直接改这里）
    private static String baseUrl = "http://192.168.7.209:9308";
    private static String username = "britton";
    private static String password = "britton@123!!";
    private static int connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS;
    private static int readTimeout = DEFAULT_READ_TIMEOUT_MS;

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

    // ==================== 单条写入 ====================

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
        payload.put("index", index);
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
        payload.put("index", index);
        payload.put("id", id);
        payload.put("doc", doc);

        return sendPost("/update", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 按查询条件批量更新 (/update) —— 更新所有匹配 query 条件的文档，无需预先知道 id
     *
     * @param index 索引/表名称
     * @param query 匹配条件 Map（结构同 /search 中的 query，如 equals / range 等）
     * @param doc   需要修改的字段键值对 Map
     * @return 接口响应 JSON 字符串
     */
    public static String updateByQuery(String index, Map<String, Object> query, Map<String, Object> doc) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("index", index);
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
        payload.put("index", index);
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
        payload.put("index", index);
        payload.put("id", id);

        return sendPost("/delete", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 根据条件批量删除数据 (/delete)
     *
     * @param index 索引/表名称
     * @param query 删除条件 Map（如 equals 匹配、range 范围等）
     * @return 接口响应 JSON 字符串
     */
    public static String deleteByQuery(String index, Map<String, Object> query) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("index", index);
        payload.put("query", query);

        return sendPost("/delete", JSONUtil.toJsonStr(payload), "application/json");
    }

    // ==================== 批量写入 /bulk（NDJSON） ====================

    /**
     * 批量插入 (/bulk) —— 推荐海量数据或批量导入时使用
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
            deleteDetail.put("index", index);
            deleteDetail.put("id", id);

            Map<String, Object> line = new HashMap<>();
            line.put("delete", deleteDetail);
            ndjson.append(JSONUtil.toJsonStr(line)).append("\n");
        }
        return sendPost("/bulk", ndjson.toString(), "application/x-ndjson");
    }

    private static String bulkAction(String action, String index, List<Map<String, Object>> docList,
                                     String idFieldName, boolean idRequired) {
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> doc : docList) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("index", index);

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

    // ==================== 查询 ====================

    /**
     * JSON DSL 查询 (/search)
     *
     * @param index  索引/表名称
     * @param query  查询条件 Map（如 query_string 全文检索、bool 组合条件等）
     * @param offset 偏移量/跳过条数 (如 0)
     * @param limit  获取条数/每页大小 (如 10)
     * @return 接口响应 JSON 字符串
     */
    public static String search(String index, Map<String, Object> query, int offset, int limit) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("index", index);
        if (query != null) {
            payload.put("query", query);
        }
        payload.put("offset", offset);
        payload.put("limit", limit);

        return sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 完整自定义查询 (/search) —— 当需要 sort / highlight / source / aggs / knn 等
     * 高级参数时，直接传入完整的请求体 Map（需自行包含 index 字段）
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
        payload.put("index", index);
        payload.put("knn", knn);

        return sendPost("/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 构造一个简单的全文匹配查询条件，等价于 {"match": {field: keyword}}，
     * 可直接作为 {@link #search(String, Map, int, int)} 的 query 参数使用
     */
    public static Map<String, Object> buildMatchQuery(String field, String keyword) {
        Map<String, Object> match = new HashMap<>();
        match.put(field, keyword);

        Map<String, Object> query = new HashMap<>();
        query.put("match", match);
        return query;
    }

    // ==================== 原生 SQL (/sql) ====================

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
            throw new ManticoreException("SQL 执行失败: " + error, resp);
        }

        JSONArray data = first.getJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.size(); i++) {
                result.add(data.getJSONObject(i));
            }
        }
        return result;
    }

    // ==================== 表管理（基于 /sql） ====================

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

    // ==================== 内部工具方法 ====================

    private static List<Float> toList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float f : arr) {
            list.add(f);
        }
        return list;
    }

    private static String sendPost(String endpoint, String body, String contentType) {
        HttpResponse response;
        try {
            response = HttpRequest.post(baseUrl + endpoint)
                    .basicAuth(username, password)
                    .header(Header.CONTENT_TYPE, contentType)
                    .charset(StandardCharsets.UTF_8)
                    .setConnectionTimeout(connectTimeout)
                    .setReadTimeout(readTimeout)
                    .body(body)
                    .execute();
        } catch (Exception e) {
            log.error("调用 Manticore 接口异常, endpoint={}, body={}", endpoint, body, e);
            throw new ManticoreException("调用 Manticore 接口异常: " + e.getMessage(), null);
        }

        String result = response.body();
        if (!response.isOk()) {
            log.warn("Manticore 接口调用失败, endpoint={}, status={}, response={}",
                    endpoint, response.getStatus(), result);
            throw new ManticoreException(
                    "Manticore 接口调用失败, status=" + response.getStatus() + ", endpoint=" + endpoint, result);
        }
        return result;
    }

    /**
     * 封装 Manticore 接口调用过程中的错误，rawResponse 保留原始响应体便于排查问题
     */
    public static class ManticoreException extends RuntimeException {

        private final String rawResponse;

        public ManticoreException(String message, String rawResponse) {
            super(message);
            this.rawResponse = rawResponse;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }
}
