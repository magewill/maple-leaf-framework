package cn.maple.core.framework.util;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GXMantiCoreUtilsTest {
    private HttpServer server;
    private String requestPath;
    private String requestBody;
    private String contentType;
    private String authorization;
    private int responseStatus;
    private String responseBody;
    private String responseContentType;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        responseBody = "{\"id\":7}";
        responseContentType = "application/json";
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::respond);
        server.start();
        GXMantiCoreUtils.initConfig("http://localhost:" + server.getAddress().getPort() + "/", "user", "pass", 1000, 1000);
        GXMantiCoreUtils.clearBearerToken();
        GXMantiCoreUtils.useIndexKeyword();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void insertSendsAuthenticatedJsonAndReturnsStructuredResponse() {
        GXMantiCoreResDto<JSON> response = GXMantiCoreUtils.insert("articles", 7L, Map.of("title", "Manticore"));

        assertEquals("/insert", requestPath);
        assertEquals("Basic dXNlcjpwYXNz", authorization);
        assertTrue(contentType.startsWith("application/json"));
        JSONObject body = JSONUtil.parseObj(requestBody);
        assertEquals("articles", body.getStr("index"));
        assertEquals(7L, body.getLong("id"));
        assertEquals("Manticore", body.getJSONObject("doc").getStr("title"));
        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertEquals(7, ((JSONObject) response.getData()).getInt("id"));
    }

    @Test
    void executeSqlUsesUtf8FormEncoding() {
        GXMantiCoreUtils.executeSql("SELECT * FROM articles WHERE title='A&B'");

        assertEquals("/sql", requestPath);
        assertTrue(contentType.startsWith("application/x-www-form-urlencoded"));
        assertEquals("query=SELECT+%2A+FROM+articles+WHERE+title%3D%27A%26B%27", requestBody);
    }

    @Test
    void bulkAndPercolateBuildManticoreRequests() {
        GXMantiCoreUtils.bulkInsert("articles", List.of(Map.of("id", 1, "title", "A")), "id");
        assertEquals("/bulk", requestPath);
        assertTrue(requestBody.endsWith("\n"));
        JSONObject bulkLine = JSONUtil.parseObj(requestBody.trim());
        assertEquals(1, bulkLine.getJSONObject("insert").getInt("id"));

        GXMantiCoreUtils.percolate("queries", List.of(Map.of("title", "A")), "query");
        JSONObject searchBody = JSONUtil.parseObj(requestBody);
        assertEquals("/search", requestPath);
        assertEquals("queries", searchBody.getStr("index"));
        assertEquals("query", searchBody.getJSONObject("query").getJSONObject("percolate").getStr("field"));
    }

    @Test
    void percolateAcceptsPagingAndSearchOptions() {
        GXMantiCoreUtils.percolate("queries", List.of(Map.of("title", "A")), "query", 20, 30,
                Map.of("max_matches", 500));

        JSONObject searchBody = JSONUtil.parseObj(requestBody);
        assertEquals("/search", requestPath);
        assertEquals(20, searchBody.getInt("offset"));
        assertEquals(30, searchBody.getInt("limit"));
        assertEquals(500, searchBody.getJSONObject("options").getInt("max_matches"));
        assertEquals("query", searchBody.getJSONObject("query").getJSONObject("percolate").getStr("field"));
    }

    @Test
    void nonSuccessfulHttpResponseBecomesFailedStructuredResponse() {
        responseStatus = 400;
        responseBody = "{\"error\":\"bad query\"}";

        GXMantiCoreResDto<JSON> response = GXMantiCoreUtils.search("articles", null, 0, 10);

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
        assertEquals(responseBody, response.getRawBody());
        assertEquals("bad query", ((JSONObject) response.getData()).getStr("error"));
    }

    @Test
    void successfulNonJsonAndMalformedJsonResponsesKeepDiagnostics() {
        responseBody = "OK";
        responseContentType = "text/plain";
        GXMantiCoreResDto<JSON> plain = GXMantiCoreUtils.post("/health", Map.of());
        assertTrue(plain.isSuccess());
        assertNull(plain.getData());
        assertEquals("OK", plain.getRawBody());

        responseBody = "{";
        responseContentType = "application/json";
        GXMantiCoreResDto<JSON> malformed = GXMantiCoreUtils.search("articles", null, 0, 10);
        assertFalse(malformed.isSuccess());
        assertEquals("{", malformed.getRawBody());
        assertTrue(malformed.getErrorMessage().contains("JSON"));
    }

    @Test
    void tokenCreationUsesBasicAuthenticationWhenBearerTokenIsConfigured() {
        GXMantiCoreUtils.useBearerToken("old-token");

        GXMantiCoreUtils.createOrRotateToken();

        assertEquals("Basic dXNlcjpwYXNz", authorization);
    }

    @Test
    void invalidInputsAreRejectedBeforeRequests() {
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.initConfig("localhost", "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.insert(" ", 1L, Map.of("x", 1)));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.insert("articles", 1L, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.search("articles", null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.search("articles", null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.post("/../token", Map.of()));
    }

    @Test
    void everyWriteAndSearchOperationRejectsMalformedInputsBeforeRequests() {
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.update(" ", 1L, Map.of("title", "A")));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.update("articles", null, Map.of("title", "A")));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.replace("articles", 1L, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.deleteById("articles", null));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.bulkInsert("articles", null, "id"));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.bulkUpdate("articles", List.of(Map.of("title", "A")), "id"));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.knnSearch("articles", "embedding", null, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.autocomplete(" ", "maple"));
    }

    @Test
    void tableManagementRejectsUnsafeIdentifiersBeforeBuildingSql() {
        String injectedIdentifier = "articles; DROP TABLE audit_log";

        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.describeTable(injectedIdentifier));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.showCreateTable(injectedIdentifier));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.dropTable(injectedIdentifier, false));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.truncateTable(injectedIdentifier));
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.optimizeTable(injectedIdentifier));
    }

    @Test
    void pqOperationsRejectUnsafePathIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> GXMantiCoreUtils.pqMatchDocument("rules/other", Map.of("title", "A")));
        assertThrows(IllegalArgumentException.class,
                () -> GXMantiCoreUtils.pqMatchDocuments("rules?target=other", List.of(Map.of("title", "A"))));
    }

    @Test
    void remoteHttpConfigurationRejectsCredentialTransmission() {
        assertThrows(IllegalArgumentException.class,
                () -> GXMantiCoreUtils.initConfig("http://manticore.example:9308", "user", "pass"));

        GXMantiCoreUtils.initConfig("http://manticore.example:9308", null, null);
        assertThrows(IllegalArgumentException.class, () -> GXMantiCoreUtils.useBearerToken("token"));
    }

    @Test
    void sqlBodyErrorMakesSuccessfulHttpResponseFail() {
        responseBody = "[{\"error\":\"syntax error\"}]";

        GXMantiCoreResDto<JSON> response = GXMantiCoreUtils.executeSql("SELECT broken");

        assertFalse(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertEquals("syntax error", response.getErrorMessage());
    }

    @Test
    void sqlBodyErrorInLaterResultSetMakesSuccessfulHttpResponseFail() {
        responseBody = "[{\"error\":\"\"},{\"error\":\"second statement failed\"}]";

        GXMantiCoreResDto<JSON> response = GXMantiCoreUtils.executeSql("SELECT 1; SELECT broken");

        assertFalse(response.isSuccess());
        assertEquals("second statement failed", response.getErrorMessage());
    }

    @Test
    void executeReadSqlRejectsWriteAndMultiStatementInput() {
        assertThrows(GXSqlInjectionException.class,
                () -> GXMantiCoreUtils.executeReadSql("DROP TABLE articles"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXMantiCoreUtils.executeReadSql("SELECT * FROM articles; DROP TABLE audit_log"));
    }

    @Test
    void buildMatchPhraseQueryCreatesPhraseDsl() {
        Map<String, Object> query = GXMantiCoreUtils.buildMatchPhraseQuery("title", "manticore search");

        assertEquals("manticore search", ((Map<?, ?>) query.get("match_phrase")).get("title"));
    }

    @Test
    void buildMatchAllQueryCreatesEmptyMatchAllClause() {
        assertEquals(Map.of("match_all", Map.of()), GXMantiCoreUtils.buildMatchAllQuery());
    }

    @Test
    void buildExistsQueryCreatesFieldExistenceDsl() {
        Map<String, Object> query = GXMantiCoreUtils.buildExistsQuery("published_at");

        assertEquals("published_at", ((Map<?, ?>) query.get("exists")).get("field"));
    }

    @Test
    void buildCombinationQuery() {
        Map<String, Object> query = GXMantiCoreUtils.buildBoolQuery(
                List.of(
                        GXMantiCoreUtils.buildMatchPhraseQuery("_all", "极权"),
                        GXMantiCoreUtils.buildEqualsQuery("tenant_id", 1),
                        GXMantiCoreUtils.buildRangeQuery("published_at", "2025-01-01", null)
                ),
                List.of(
                        GXMantiCoreUtils.buildMatchQuery("title", "政体"),
                        GXMantiCoreUtils.buildQueryString("author:Arendt"),
                        GXMantiCoreUtils.buildInQuery("category_id", List.of(10, 20))
                ),
                List.of(GXMantiCoreUtils.buildExistsQuery("top_at"))
        );

        Map<String, String> sortFields = new LinkedHashMap<>();
        sortFields.put("top_at", "desc");
        sortFields.put("id", "desc");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", "manticore_periodical_articles");
        payload.put("query", query);
        payload.put("sort", GXMantiCoreUtils.buildSort(sortFields));
        payload.put("limit", 10);
        payload.put("offset", 0);

        GXMantiCoreResDto<JSON> response = GXMantiCoreUtils.search(payload);

        assertTrue(response.isSuccess());
        assertEquals("/search", requestPath);
        JSONObject request = JSONUtil.parseObj(requestBody);
        JSONObject bool = request.getJSONObject("query").getJSONObject("bool");
        assertEquals("极权", bool.getJSONArray("must").getJSONObject(0)
                .getJSONObject("match_phrase").getStr("_all"));
        assertEquals(1, bool.getJSONArray("must").getJSONObject(1)
                .getJSONObject("equals").getInt("tenant_id"));
        assertEquals("2025-01-01", bool.getJSONArray("must").getJSONObject(2)
                .getJSONObject("range").getJSONObject("published_at").getStr("gte"));
        assertEquals("政体", bool.getJSONArray("should").getJSONObject(0)
                .getJSONObject("match").getStr("title"));
        assertEquals("author:Arendt", bool.getJSONArray("should").getJSONObject(1).getStr("query_string"));
        assertEquals(20, bool.getJSONArray("should").getJSONObject(2)
                .getJSONObject("in").getJSONArray("category_id").getInt(1));
        assertEquals("top_at", bool.getJSONArray("must_not").getJSONObject(0)
                .getJSONObject("exists").getStr("field"));
        assertEquals("desc", request.getJSONArray("sort").getJSONObject(1).getStr("id"));
    }

    private void respond(HttpExchange exchange) throws IOException {
        requestPath = exchange.getRequestURI().getPath();
        authorization = exchange.getRequestHeaders().getFirst("Authorization");
        contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", responseContentType);
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
