package cn.maple.core.framework.util;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.exception.GXSqlInjectionException;
import cn.maple.core.framework.util.manticore.GXManticoreBuilder;
import cn.maple.core.framework.util.manticore.GXManticoreClient;
import cn.maple.core.framework.util.manticore.GXManticoreQueryOperations;
import cn.maple.core.framework.util.manticore.GXManticoreUtils;
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

class GXManticoreBuilderTest {
    private HttpServer server;
    private GXManticoreClient client;
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
        client = new GXManticoreClient("http://localhost:" + server.getAddress().getPort() + "/", "user", "pass", 1000, 1000);
        client.clearBearerToken();
        client.useIndexKeyword();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void documentOperationsUseOwningClientConfiguration() throws IOException {
        String[] secondPath = new String[1];
        String[] secondAuthorization = new String[1];
        HttpServer secondServer = HttpServer.create(new InetSocketAddress(0), 0);
        secondServer.createContext("/", exchange -> {
            secondPath[0] = exchange.getRequestURI().getPath();
            secondAuthorization[0] = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] bytes = "{\"id\":2}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        secondServer.start();
        try {
            GXManticoreClient first = new GXManticoreClient(
                    "http://localhost:" + server.getAddress().getPort(), "first", "one", 1000, 1000);
            GXManticoreClient second = new GXManticoreClient(
                    "http://localhost:" + secondServer.getAddress().getPort(), "second", "two", 1000, 1000);

            first.getDocumentOperations().insert("articles", 1L, Map.of("title", "First"));
            second.getDocumentOperations().insert("articles", 2L, Map.of("title", "Second"));

            assertEquals("/insert", requestPath);
            assertEquals("Basic Zmlyc3Q6b25l", authorization);
            assertEquals("/insert", secondPath[0]);
            assertEquals("Basic c2Vjb25kOnR3bw==", secondAuthorization[0]);
        } finally {
            secondServer.stop(0);
        }
    }

    @Test
    void queryOperationsExposeOnlyClientBoundRemoteQueries() {
        assertThrows(NoSuchMethodException.class,
                () -> GXManticoreQueryOperations.class.getMethod("buildMatchQuery", String.class, String.class));
    }

    @Test
    void clientStoresRetryPolicyAsOneStateObject() {
        assertDoesNotThrow(() -> GXManticoreClient.class.getDeclaredField("retryPolicy"));
        assertThrows(NoSuchFieldException.class, () -> GXManticoreClient.class.getDeclaredField("maxRetries"));
        assertThrows(NoSuchFieldException.class, () -> GXManticoreClient.class.getDeclaredField("retryBackoffMs"));
    }

    @Test
    void insertSendsAuthenticatedJsonAndReturnsStructuredResponse() {
        String response = client.getDocumentOperations().insert("articles", 7L, Map.of("title", "Manticore"));

        assertEquals("/insert", requestPath);
        assertEquals("Basic dXNlcjpwYXNz", authorization);
        assertTrue(contentType.startsWith("application/json"));
        JSONObject body = JSONUtil.parseObj(requestBody);
        assertEquals("articles", body.getStr("index"));
        assertEquals(7L, body.getLong("id"));
        assertEquals("Manticore", body.getJSONObject("doc").getStr("title"));
    }

    @Test
    void executeSqlUsesUtf8FormEncoding() {
        client.getSqlOperations().executeSql("SELECT * FROM articles WHERE title='A&B'");

        assertEquals("/sql", requestPath);
        assertTrue(contentType.startsWith("application/x-www-form-urlencoded"));
        assertEquals("query=SELECT+%2A+FROM+articles+WHERE+title%3D%27A%26B%27", requestBody);
    }

    @Test
    void bulkAndPercolateBuildManticoreRequests() {
        client.getDocumentOperations().bulkInsert("articles", List.of(Map.of("id", 1, "title", "A")), "id");
        assertEquals("/bulk", requestPath);
        assertTrue(requestBody.endsWith("\n"));
        JSONObject bulkLine = JSONUtil.parseObj(requestBody.trim());
        assertEquals(1, bulkLine.getJSONObject("insert").getInt("id"));

        client.getPercolateOperations().percolate("queries", List.of(Map.of("title", "A")), "query");
        JSONObject searchBody = JSONUtil.parseObj(requestBody);
        assertEquals("/search", requestPath);
        assertEquals("queries", searchBody.getStr("index"));
        assertEquals("query", searchBody.getJSONObject("query").getJSONObject("percolate").getStr("field"));
    }

    @Test
    void percolateAcceptsPagingAndSearchOptions() {
        client.getPercolateOperations().percolate("queries", List.of(Map.of("title", "A")), "query", 20, 30,
                Map.of("max_matches", 500));

        JSONObject searchBody = JSONUtil.parseObj(requestBody);
        assertEquals("/search", requestPath);
        assertEquals(20, searchBody.getInt("offset"));
        assertEquals(30, searchBody.getInt("limit"));
        assertEquals(500, searchBody.getJSONObject("options").getInt("max_matches"));
        assertEquals("query", searchBody.getJSONObject("query").getJSONObject("percolate").getStr("field"));
    }

    @Test
    void nonSuccessfulHttpResponseReturnsRawResponseBody() {
        responseStatus = 400;
        responseBody = "{\"error\":\"bad query\"}";

        String response = client.getQueryOperations().search("articles", null, 0, 10);

        assertEquals(responseBody, response);
    }

    @Test
    void nonJsonHttpErrorReturnsRawResponseBody() {
        responseStatus = 502;
        responseBody = "Bad Gateway";
        responseContentType = "text/plain";

        String response = assertDoesNotThrow(() -> client.post("/health", Map.of()));

        assertEquals(responseBody, response);
    }

    @Test
    void jsonBodyIsReturnedWithoutContentTypeBasedParsing() {
        responseBody = "{\"id\":7}";
        responseContentType = "text/plain";

        String response = client.post("/health", Map.of());

        assertEquals(responseBody, response);
    }

    @Test
    void successfulNonJsonAndMalformedJsonResponsesKeepDiagnostics() {
        responseBody = "OK";
        responseContentType = "text/plain";
        String plain = client.post("/health", Map.of());
        assertEquals(responseBody, plain);

        responseBody = "{";
        responseContentType = "application/json";
        String malformed = client.getQueryOperations().search("articles", null, 0, 10);
        assertEquals(responseBody, malformed);
    }

    @Test
    void tokenCreationUsesBasicAuthenticationWhenBearerTokenIsConfigured() {
        client.useBearerToken("old-token");

        client.createOrRotateToken();

        assertEquals("Basic dXNlcjpwYXNz", authorization);
    }

    @Test
    void invalidInputsAreRejectedBeforeRequests() {
        assertThrows(IllegalArgumentException.class, () -> new GXManticoreClient("localhost", "u", "p"));
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().insert(" ", 1L, Map.of("x", 1)));
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().insert("articles", 1L, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> client.getQueryOperations().search("articles", null, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> client.getQueryOperations().search("articles", null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> client.post("/../token", Map.of()));
    }

    @Test
    void everyWriteAndSearchOperationRejectsMalformedInputsBeforeRequests() {
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().update(" ", 1L, Map.of("title", "A")));
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().update("articles", null, Map.of("title", "A")));
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().replace("articles", 1L, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().deleteById("articles", null));
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().bulkInsert("articles", null, "id"));
        assertThrows(IllegalArgumentException.class, () -> client.getDocumentOperations().bulkUpdate("articles", List.of(Map.of("title", "A")), "id"));
        assertThrows(IllegalArgumentException.class, () -> client.getQueryOperations().knnSearch("articles", "embedding", null, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> client.getAutocompleteOperations().autocomplete(" ", "maple"));
    }

    @Test
    void batchedBulkOperationsRejectNullOrEmptyDocuments() {
        assertThrows(IllegalArgumentException.class,
                () -> client.getDocumentOperations().bulkInsertBatched("articles", null, "id", 100));
        assertThrows(IllegalArgumentException.class,
                () -> client.getDocumentOperations().bulkReplaceBatched("articles", List.of(), "id", 100));
        assertThrows(IllegalArgumentException.class,
                () -> client.getDocumentOperations().bulkUpdateBatched("articles", List.of(), "id", 100));
    }

    @Test
    void extractHitsPreservesReservedMetadataWhenSourceContainsSameKeys() {
        List<Map<String, Object>> hits = GXManticoreUtils.extractHits("""
                {"hits":{"hits":[{"_id":7,"_score":1.5,
                "_source":{"_id":"document-id","_score":99,"title":"Manticore"}}]}}
                """);

        assertEquals(7, hits.getFirst().get("_id"));
        assertEquals(1.5D, ((Number) hits.getFirst().get("_score")).doubleValue());
        assertEquals("Manticore", hits.getFirst().get("title"));
    }

    @Test
    void queryBuildersRejectMalformedArguments() {
        assertThrows(IllegalArgumentException.class, () -> GXManticoreBuilder.buildMatchQuery(" ", "manticore"));
        assertThrows(IllegalArgumentException.class, () -> GXManticoreBuilder.buildRangeQuery("created_at", null, null));
        assertThrows(IllegalArgumentException.class, () -> GXManticoreBuilder.buildHighlight(List.of()));
        assertThrows(IllegalArgumentException.class, () -> GXManticoreBuilder.buildSort("id", "descending"));
    }

    @Test
    void tableManagementRejectsUnsafeIdentifiersBeforeBuildingSql() {
        String injectedIdentifier = "articles; DROP TABLE audit_log";

        assertThrows(IllegalArgumentException.class, () -> client.getSqlOperations().describeTable(injectedIdentifier));
        assertThrows(IllegalArgumentException.class, () -> client.getSqlOperations().showCreateTable(injectedIdentifier));
        assertThrows(IllegalArgumentException.class, () -> client.getSqlOperations().dropTable(injectedIdentifier, false));
        assertThrows(IllegalArgumentException.class, () -> client.getSqlOperations().truncateTable(injectedIdentifier));
        assertThrows(IllegalArgumentException.class, () -> client.getSqlOperations().optimizeTable(injectedIdentifier));
    }

    @Test
    void createTableRejectsNonCreateAndMultiStatementSql() {
        assertThrows(GXSqlInjectionException.class,
                () -> client.getSqlOperations().createTable("DROP TABLE articles"));
        assertThrows(GXSqlInjectionException.class,
                () -> client.getSqlOperations().createTable("CREATE TABLE articles(title text); DROP TABLE audit_log"));

        client.getSqlOperations().createTable("CREATE TABLE articles(title text)");
        assertEquals("/sql", requestPath);
        assertTrue(requestBody.contains("CREATE+TABLE+articles"));
    }

    @Test
    void autocompleteRejectsReservedOptionKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> client.getAutocompleteOperations().autocomplete("articles", "man", Map.of("table", "other")));
        assertThrows(IllegalArgumentException.class,
                () -> client.getAutocompleteOperations().autocomplete("articles", "man", Map.of("query", "other")));
    }

    @Test
    void pqOperationsRejectUnsafePathIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> client.getPercolateOperations().pqMatchDocument("rules/other", Map.of("title", "A")));
        assertThrows(IllegalArgumentException.class,
                () -> client.getPercolateOperations().pqMatchDocuments("rules?target=other", List.of(Map.of("title", "A"))));
        assertThrows(IllegalArgumentException.class,
                () -> client.getPercolateOperations().pqAddRule("rules; DROP TABLE audit_log", 1L,
                        Map.of("match_all", Map.of()), List.of()));
    }

    @Test
    void pqMatchSupportsPagingAndSearchOptions() {
        client.getPercolateOperations().pqMatchDocument("rules", Map.of("title", "Manticore"), 5, 10,
                Map.of("max_matches", 100));

        assertEquals("/pq/rules/search", requestPath);
        JSONObject body = JSONUtil.parseObj(requestBody);
        assertEquals(5, body.getInt("offset"));
        assertEquals(10, body.getInt("limit"));
        assertEquals(100, body.getJSONObject("options").getInt("max_matches"));
        assertEquals("Manticore", body.getJSONObject("query").getJSONObject("percolate")
                .getJSONObject("document").getStr("title"));
    }

    @Test
    void remoteHttpConfigurationRejectsCredentialTransmission() {
        assertThrows(IllegalArgumentException.class,
                () -> new GXManticoreClient("http://manticore.example:9308", "user", "pass"));
        assertThrows(IllegalArgumentException.class,
                () -> new GXManticoreClient("http://192.0.2.1:9308", "user", "pass"));

        GXManticoreClient remoteClient = new GXManticoreClient("http://manticore.example:9308", null, null);
        assertThrows(IllegalArgumentException.class, () -> remoteClient.useBearerToken("token"));
    }

    @Test
    void sqlBodyErrorMakesSuccessfulHttpResponseFail() {
        responseBody = "[{\"error\":\"syntax error\"}]";

        String response = client.getSqlOperations().executeSql("SELECT broken");
    }

    @Test
    void sqlBodyErrorInLaterResultSetMakesSuccessfulHttpResponseFail() {
        responseBody = "[{\"error\":\"\"},{\"error\":\"second statement failed\"}]";

        String response = client.getSqlOperations().executeSql("SELECT 1; SELECT broken");
    }

    @Test
    void executeReadSqlRejectsWriteAndMultiStatementInput() {
        assertThrows(GXSqlInjectionException.class,
                () -> client.getSqlOperations().executeReadSql("DROP TABLE articles"));
        assertThrows(GXSqlInjectionException.class,
                () -> client.getSqlOperations().executeReadSql("SELECT * FROM articles; DROP TABLE audit_log"));
    }

    @Test
    void buildMatchPhraseQueryCreatesPhraseDsl() {
        Map<String, Object> query = GXManticoreBuilder.buildMatchPhraseQuery("title", "manticore search");

        assertEquals("manticore search", ((Map<?, ?>) query.get("match_phrase")).get("title"));
    }

    @Test
    void buildMatchAllQueryCreatesEmptyMatchAllClause() {
        assertEquals(Map.of("match_all", Map.of()), GXManticoreBuilder.buildMatchAllQuery());
    }

    @Test
    void buildExistsQueryCreatesFieldExistenceDsl() {
        Map<String, Object> query = GXManticoreBuilder.buildExistsQuery("published_at");

        assertEquals("published_at", ((Map<?, ?>) query.get("exists")).get("field"));
    }

    @Test
    void buildCombinationQuery() {
        Map<String, Object> query = GXManticoreBuilder.buildBoolQuery(
                List.of(
                        GXManticoreBuilder.buildMatchPhraseQuery("_all", "manticore"),
                        GXManticoreBuilder.buildEqualsQuery("tenant_id", 1),
                        GXManticoreBuilder.buildRangeQuery("published_at", "2025-01-01", null)
                ),
                List.of(
                        GXManticoreBuilder.buildMatchQuery("title", "topic"),
                        GXManticoreBuilder.buildQueryString("author:Arendt"),
                        GXManticoreBuilder.buildInQuery("category_id", List.of(10, 20))
                ),
                List.of(GXManticoreBuilder.buildExistsQuery("top_at"))
        );

        Map<String, String> sortFields = new LinkedHashMap<>();
        sortFields.put("top_at", "desc");
        sortFields.put("id", "desc");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("table", "manticore_periodical_articles");
        payload.put("query", query);
        payload.put("sort", GXManticoreBuilder.buildSort(sortFields));
        payload.put("limit", 10);
        payload.put("offset", 0);

        String response = client.getQueryOperations().search(payload);

        assertEquals("/search", requestPath);
        JSONObject request = JSONUtil.parseObj(requestBody);
        JSONObject bool = request.getJSONObject("query").getJSONObject("bool");
        assertEquals("manticore", bool.getJSONArray("must").getJSONObject(0)
                .getJSONObject("match_phrase").getStr("_all"));
        assertEquals(1, bool.getJSONArray("must").getJSONObject(1)
                .getJSONObject("equals").getInt("tenant_id"));
        assertEquals("2025-01-01", bool.getJSONArray("must").getJSONObject(2)
                .getJSONObject("range").getJSONObject("published_at").getStr("gte"));
        assertEquals("topic", bool.getJSONArray("should").getJSONObject(0)
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
