package cn.maple.core.framework.util;

import cn.hutool.json.JSON;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;
import cn.maple.core.framework.util.manticore.GXManticoreConfiguration;
import cn.maple.core.framework.util.manticore.GXManticoreAutocompleteOperations;
import cn.maple.core.framework.util.manticore.GXManticoreDocumentOperations;
import cn.maple.core.framework.util.manticore.GXManticorePercolateOperations;
import cn.maple.core.framework.util.manticore.GXManticoreQueryOperations;
import cn.maple.core.framework.util.manticore.GXManticoreSqlOperations;

import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for Manticore operations.
 *
 * <p>New code can import the focused classes in the {@code manticore} package directly.
 * This facade remains to preserve existing application code.</p>
 */
public final class GXMantiCoreUtils {
    private GXMantiCoreUtils() {
    }
    public static void initConfig(String baseUrl, String username, String password) {
        GXManticoreConfiguration.initConfig(baseUrl, username, password);
    }

    public static void initConfig(String baseUrl, String username, String password, int connectTimeoutMs, int readTimeoutMs) {
        GXManticoreConfiguration.initConfig(baseUrl, username, password, connectTimeoutMs, readTimeoutMs);
    }

    public static void useBearerToken(String token) {
        GXManticoreConfiguration.useBearerToken(token);
    }

    public static void clearBearerToken() {
        GXManticoreConfiguration.clearBearerToken();
    }

    public static GXMantiCoreResDto<JSON> createOrRotateToken() {
        return GXManticoreConfiguration.createOrRotateToken();
    }

    public static GXMantiCoreResDto<JSON> post(String endpoint, Map<String, ?> payload) {
        return GXManticoreConfiguration.post(endpoint, payload);
    }

    public static void useTableKeyword() {
        GXManticoreConfiguration.useTableKeyword();
    }

    public static void useIndexKeyword() {
        GXManticoreConfiguration.useIndexKeyword();
    }

    public static void setRetryPolicy(int retries, long backoffMs) {
        GXManticoreConfiguration.setRetryPolicy(retries, backoffMs);
    }

    public static GXMantiCoreResDto<JSON> insert(String index, Long id, Map<String, Object> doc) {
        return GXManticoreDocumentOperations.insert(index, id, doc);
    }

    public static GXMantiCoreResDto<JSON> update(String index, Long id, Map<String, Object> doc) {
        return GXManticoreDocumentOperations.update(index, id, doc);
    }

    public static GXMantiCoreResDto<JSON> updateByQuery(String index, Map<String, Object> query, Map<String, Object> doc) {
        return GXManticoreDocumentOperations.updateByQuery(index, query, doc);
    }

    public static GXMantiCoreResDto<JSON> replace(String index, Long id, Map<String, Object> doc) {
        return GXManticoreDocumentOperations.replace(index, id, doc);
    }

    public static GXMantiCoreResDto<JSON> deleteById(String index, Long id) {
        return GXManticoreDocumentOperations.deleteById(index, id);
    }

    public static GXMantiCoreResDto<JSON> deleteByQuery(String index, Map<String, Object> query) {
        return GXManticoreDocumentOperations.deleteByQuery(index, query);
    }

    public static GXMantiCoreResDto<JSON> bulkInsert(String index, List<Map<String, Object>> docList, String idFieldName) {
        return GXManticoreDocumentOperations.bulkInsert(index, docList, idFieldName);
    }

    public static GXMantiCoreResDto<JSON> bulkReplace(String index, List<Map<String, Object>> docList, String idFieldName) {
        return GXManticoreDocumentOperations.bulkReplace(index, docList, idFieldName);
    }

    public static GXMantiCoreResDto<JSON> bulkUpdate(String index, List<Map<String, Object>> docList, String idFieldName) {
        return GXManticoreDocumentOperations.bulkUpdate(index, docList, idFieldName);
    }

    public static GXMantiCoreResDto<JSON> bulkDeleteByIds(String index, List<Long> ids) {
        return GXManticoreDocumentOperations.bulkDeleteByIds(index, ids);
    }

    public static List<GXMantiCoreResDto<JSON>> bulkInsertBatched(String index, List<Map<String, Object>> docList, String idFieldName, int batchSize) {
        return GXManticoreDocumentOperations.bulkInsertBatched(index, docList, idFieldName, batchSize);
    }

    public static List<GXMantiCoreResDto<JSON>> bulkReplaceBatched(String index, List<Map<String, Object>> docList, String idFieldName, int batchSize) {
        return GXManticoreDocumentOperations.bulkReplaceBatched(index, docList, idFieldName, batchSize);
    }

    public static List<GXMantiCoreResDto<JSON>> bulkUpdateBatched(String index, List<Map<String, Object>> docList, String idFieldName, int batchSize) {
        return GXManticoreDocumentOperations.bulkUpdateBatched(index, docList, idFieldName, batchSize);
    }

    public static GXMantiCoreResDto<JSON> search(String index, Map<String, Object> query, int offset, int limit) {
        return GXManticoreDocumentOperations.search(index, query, offset, limit);
    }

    public static GXMantiCoreResDto<JSON> search(String index, Map<String, Object> query, int offset, int limit, Map<String, Object> options) {
        return GXManticoreDocumentOperations.search(index, query, offset, limit, options);
    }

    public static GXMantiCoreResDto<JSON> search(Map<String, Object> fullPayload) {
        return GXManticoreDocumentOperations.search(fullPayload);
    }

    public static GXMantiCoreResDto<JSON> percolate(String index, List<Map<String, Object>> documents, String field) {
        return GXManticorePercolateOperations.percolate(index, documents, field);
    }

    public static GXMantiCoreResDto<JSON> percolate(String index, List<Map<String, Object>> documents, String field,
                                                     int offset, int limit, Map<String, Object> options) {
        return GXManticorePercolateOperations.percolate(index, documents, field, offset, limit, options);
    }

    public static GXMantiCoreResDto<JSON> knnSearch(String index, String knnField, float[] queryVector, int k, int ef) {
        return GXManticoreQueryOperations.knnSearch(index, knnField, queryVector, k, ef);
    }

    public static Map<String, Object> buildMatchQuery(String field, String keyword) {
        return GXManticoreQueryOperations.buildMatchQuery(field, keyword);
    }

    public static Map<String, Object> buildMatchPhraseQuery(String field, String phrase) {
        return GXManticoreQueryOperations.buildMatchPhraseQuery(field, phrase);
    }

    public static Map<String, Object> buildMatchAllQuery() {
        return GXManticoreQueryOperations.buildMatchAllQuery();
    }

    public static Map<String, Object> buildExistsQuery(String field) {
        return GXManticoreQueryOperations.buildExistsQuery(field);
    }

    public static String escapeQueryString(String raw) {
        return GXManticoreQueryOperations.escapeQueryString(raw);
    }

    public static Map<String, Object> buildQueryString(String text) {
        return GXManticoreQueryOperations.buildQueryString(text);
    }

    public static Map<String, Object> buildEqualsQuery(String field, Object value) {
        return GXManticoreQueryOperations.buildEqualsQuery(field, value);
    }

    public static Map<String, Object> buildInQuery(String field, List<?> values) {
        return GXManticoreQueryOperations.buildInQuery(field, values);
    }

    public static Map<String, Object> buildRangeQuery(String field, Object gte, Object lte) {
        return GXManticoreQueryOperations.buildRangeQuery(field, gte, lte);
    }

    public static Map<String, Object> buildBoolQuery(List<Map<String, Object>> must, List<Map<String, Object>> should, List<Map<String, Object>> mustNot) {
        return GXManticoreQueryOperations.buildBoolQuery(must, should, mustNot);
    }

    public static Map<String, Object> buildHighlight(List<String> fields) {
        return GXManticoreQueryOperations.buildHighlight(fields);
    }

    public static Map<String, Object> buildHighlight(List<String> fields, Map<String, Object> globalOptions) {
        return GXManticoreQueryOperations.buildHighlight(fields, globalOptions);
    }

    public static List<Map<String, String>> buildSort(String field, String order) {
        return GXManticoreQueryOperations.buildSort(field, order);
    }

    public static List<Map<String, String>> buildSort(Map<String, String> fieldOrderMap) {
        return GXManticoreQueryOperations.buildSort(fieldOrderMap);
    }

    public static Map<String, Object> buildJoin(String type, String mainTable, String mainField, String joinTable, String joinField, Map<String, Object> joinQuery) {
        return GXManticoreQueryOperations.buildJoin(type, mainTable, mainField, joinTable, joinField, joinQuery);
    }

    public static List<Map<String, Object>> extractHits(String searchResponseJson) {
        return GXManticoreQueryOperations.extractHits(searchResponseJson);
    }

    public static long extractTotal(String searchResponseJson) {
        return GXManticoreQueryOperations.extractTotal(searchResponseJson);
    }

    public static Map<String, Object> buildTermsAgg(String aggName, String field) {
        return GXManticoreQueryOperations.buildTermsAgg(aggName, field);
    }

    public static List<Map<String, Object>> extractAggBuckets(String searchResponseJson, String aggName) {
        return GXManticoreQueryOperations.extractAggBuckets(searchResponseJson, aggName);
    }

    public static GXMantiCoreResDto<JSON> executeSql(String sql) {
        return GXManticoreSqlOperations.executeSql(sql);
    }

    public static GXMantiCoreResDto<JSON> executeReadSql(String sql) {
        return GXManticoreSqlOperations.executeReadSql(sql);
    }

    public static List<Map<String, Object>> parseSqlAsList(String responseJson) {
        return GXManticoreSqlOperations.parseSqlAsList(responseJson);
    }

    public static List<List<Map<String, Object>>> parseSqlMultiAsList(String responseJson) {
        return GXManticoreSqlOperations.parseSqlMultiAsList(responseJson);
    }

    public static GXMantiCoreResDto<JSON> flushAttributes() {
        return GXManticoreSqlOperations.flushAttributes();
    }

    public static GXMantiCoreResDto<JSON> showTables() {
        return GXManticoreSqlOperations.showTables();
    }

    public static GXMantiCoreResDto<JSON> describeTable(String index) {
        return GXManticoreSqlOperations.describeTable(index);
    }

    public static GXMantiCoreResDto<JSON> showCreateTable(String index) {
        return GXManticoreSqlOperations.showCreateTable(index);
    }

    public static GXMantiCoreResDto<JSON> createTable(String createTableSql) {
        return GXManticoreSqlOperations.createTable(createTableSql);
    }

    public static GXMantiCoreResDto<JSON> dropTable(String index, boolean ifExists) {
        return GXManticoreSqlOperations.dropTable(index, ifExists);
    }

    public static GXMantiCoreResDto<JSON> truncateTable(String index) {
        return GXManticoreSqlOperations.truncateTable(index);
    }

    public static GXMantiCoreResDto<JSON> optimizeTable(String index) {
        return GXManticoreSqlOperations.optimizeTable(index);
    }

    public static GXMantiCoreResDto<JSON> pqAddRule(String pqIndex, Long id, Map<String, Object> query, List<String> tags) {
        return GXManticorePercolateOperations.pqAddRule(pqIndex, id, query, tags);
    }

    public static GXMantiCoreResDto<JSON> pqAddRule(String pqIndex, Long id, Map<String, Object> query, List<String> tags, String filters) {
        return GXManticorePercolateOperations.pqAddRule(pqIndex, id, query, tags, filters);
    }

    public static GXMantiCoreResDto<JSON> pqMatchDocument(String pqIndex, Map<String, Object> document) {
        return GXManticorePercolateOperations.pqMatchDocument(pqIndex, document);
    }

    public static GXMantiCoreResDto<JSON> pqMatchDocuments(String pqIndex, List<Map<String, Object>> documents) {
        return GXManticorePercolateOperations.pqMatchDocuments(pqIndex, documents);
    }

    public static GXMantiCoreResDto<JSON> autocomplete(String table, String query) {
        return GXManticoreAutocompleteOperations.autocomplete(table, query);
    }

    public static GXMantiCoreResDto<JSON> autocomplete(String table, String query, Map<String, Object> extraOptions) {
        return GXManticoreAutocompleteOperations.autocomplete(table, query, extraOptions);
    }
}
