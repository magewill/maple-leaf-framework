package cn.maple.core.framework.util;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manticore Search HTTP API 操作工具类
 */
public class GXMantiCoreUtils {
    // 1. 默认服务器与认证配置（可根据需要修改）
    private static String baseUrl = "http://192.168.7.209:9308";
    private static String username = "britton";
    private static String password = "britton@123!!";

    /**
     * 动态修改连接配置（可在 Spring 项目的 @PostConstruct 或启动配置类中调用）
     */
    public static void initConfig(String url, String user, String pwd) {
        baseUrl = url;
        username = user;
        password = pwd;
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
        payload.put("index", index);
        if (id != null) {
            payload.put("id", id);
        }
        payload.put("doc", doc);

        return sendPost("/insert", JSONUtil.toJsonStr(payload), "application/json");
    }

    /**
     * 批量数据插入 (/bulk) —— 推荐海量数据或批量导入时使用（采用 NDJSON 格式）
     *
     * @param index       索引/表名称
     * @param docList     文档字段 Map 列表
     * @param idFieldName docMap 中作为主键 id 的字段名称（例如 "id"）；若无需指定可传 null
     * @return 接口响应 JSON 字符串
     */
    public static String bulkInsert(String index, List<Map<String, Object>> docList, String idFieldName) {
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> doc : docList) {
            Map<String, Object> insertDetail = new HashMap<>();
            insertDetail.put("index", index);

            if (idFieldName != null && doc.containsKey(idFieldName)) {
                insertDetail.put("id", doc.get(idFieldName));
            }
            insertDetail.put("doc", doc);

            Map<String, Object> line = new HashMap<>();
            line.put("insert", insertDetail);

            ndjson.append(JSONUtil.toJsonStr(line)).append("\n");
        }

        return sendPost("/bulk", ndjson.toString(), "application/x-ndjson");
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
     * 执行原生 Manticore SQL 脚本 (/sql)
     *
     * @param sql 完整的 Manticore SQL 语句
     * @return 接口响应 JSON 字符串
     */
    public static String executeSql(String sql) {
        return sendPost("/sql", "query=" + sql, "application/x-www-form-urlencoded");
    }

    private static String sendPost(String endpoint, String body, String contentType) {
        return HttpRequest.post(baseUrl + endpoint)
                .basicAuth(username, password)
                .header(Header.CONTENT_TYPE, contentType)
                .body(body)
                .execute()
                .body();
    }
}