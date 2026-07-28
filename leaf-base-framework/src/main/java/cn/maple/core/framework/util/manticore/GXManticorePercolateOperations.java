package cn.maple.core.framework.util.manticore;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Operations for Manticore percolate tables and reverse queries.
 */
public final class GXManticorePercolateOperations {
    private final GXManticoreClient client;

    GXManticorePercolateOperations(GXManticoreClient client) {
        this.client = client;
    }

    /**
     * Runs a reverse percolate search with the default first page of ten results.
     */
    public GXMantiCoreResDto<Dict> percolate(String index, List<Map<String, Object>> documents,
                                             String field) {
        return percolate(index, documents, field, 0, 10, null);
    }

    /**
     * Runs a reverse percolate search with caller-controlled pagination and search options.
     */
    public GXMantiCoreResDto<Dict> percolate(String index, List<Map<String, Object>> documents,
                                             String field, int offset, int limit,
                                             Map<String, Object> options) {
        requireNonBlank(index, "index");
        requireNonBlank(field, "field");
        requireSearchArguments(index, offset, limit);
        if (documents == null || documents.isEmpty() || documents.stream().anyMatch(doc -> doc == null || doc.isEmpty())) {
            throw new IllegalArgumentException("documents must contain non-empty documents");
        }
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("field", field);
        percolate.put("documents", documents);
        Map<String, Object> query = new HashMap<>();
        query.put("percolate", percolate);
        return client.getQueryOperations().search(index, query, offset, limit, options);
    }

    /**
     * Adds a rule to a percolate table.
     */
    public GXMantiCoreResDto<Dict> pqAddRule(String pqIndex, Long id, Map<String, Object> query,
                                             List<String> tags) {
        return pqAddRule(pqIndex, id, query, tags, null);
    }

    /**
     * Adds a rule to a percolate table, optionally constrained by attribute filters.
     */
    public GXMantiCoreResDto<Dict> pqAddRule(String pqIndex, Long id, Map<String, Object> query,
                                             List<String> tags, String filters) {
        requireIdentifier(pqIndex, "pqIndex");
        requireNonEmpty(query, "query");
        Map<String, Object> doc = new HashMap<>();
        doc.put("query", query);
        if (tags != null && !tags.isEmpty()) {
            doc.put("tags", tags);
        }
        if (StrUtil.isNotBlank(filters)) {
            doc.put("filters", filters);
        }
        return client.getDocumentOperations().insert(pqIndex, id, doc);
    }

    /**
     * Matches one document against the rules stored in a percolate table.
     */
    public GXMantiCoreResDto<Dict> pqMatchDocument(String pqIndex, Map<String, Object> document) {
        requireIdentifier(pqIndex, "pqIndex");
        requireNonEmpty(document, "document");
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("document", document);
        return searchPercolateTable(pqIndex, percolate);
    }

    /**
     * Matches one document against stored rules with caller-controlled pagination and search options.
     */
    public GXMantiCoreResDto<Dict> pqMatchDocument(String pqIndex, Map<String, Object> document,
                                                   int offset, int limit, Map<String, Object> options) {
        requireIdentifier(pqIndex, "pqIndex");
        requireNonEmpty(document, "document");
        requireSearchArguments(pqIndex, offset, limit);
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("document", document);
        return searchPercolateTable(pqIndex, percolate, offset, limit, options);
    }

    /**
     * Matches multiple documents against the rules stored in a percolate table.
     */
    public GXMantiCoreResDto<Dict> pqMatchDocuments(String pqIndex, List<Map<String, Object>> documents) {
        requireIdentifier(pqIndex, "pqIndex");
        if (documents == null || documents.isEmpty() || documents.stream().anyMatch(doc -> doc == null || doc.isEmpty())) {
            throw new IllegalArgumentException("documents must contain non-empty documents");
        }
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("documents", documents);
        return searchPercolateTable(pqIndex, percolate);
    }

    /**
     * Matches multiple documents against stored rules with caller-controlled pagination and search options.
     */
    public GXMantiCoreResDto<Dict> pqMatchDocuments(String pqIndex, List<Map<String, Object>> documents,
                                                    int offset, int limit, Map<String, Object> options) {
        requireIdentifier(pqIndex, "pqIndex");
        requireSearchArguments(pqIndex, offset, limit);
        if (documents == null || documents.isEmpty() || documents.stream().anyMatch(doc -> doc == null || doc.isEmpty())) {
            throw new IllegalArgumentException("documents must contain non-empty documents");
        }
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("documents", documents);
        return searchPercolateTable(pqIndex, percolate, offset, limit, options);
    }

    private GXMantiCoreResDto<Dict> searchPercolateTable(String pqIndex, Map<String, Object> percolate) {
        Map<String, Object> query = new HashMap<>();
        query.put("percolate", percolate);
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        return sendPost("/pq/" + pqIndex + "/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    private GXMantiCoreResDto<Dict> searchPercolateTable(String pqIndex, Map<String, Object> percolate,
                                                         int offset, int limit, Map<String, Object> options) {
        Map<String, Object> query = new HashMap<>();
        query.put("percolate", percolate);
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        payload.put("offset", offset);
        payload.put("limit", limit);
        if (options != null && !options.isEmpty()) {
            payload.put("options", options);
        }
        return sendPost("/pq/" + pqIndex + "/search", JSONUtil.toJsonStr(payload), "application/json");
    }

    private void requireIdentifier(String value, String name) {
        GXManticoreUtils.requireIdentifier(value, name);
    }

    private void requireNonEmpty(Map<?, ?> value, String name) {
        GXManticoreUtils.requireNonEmpty(value, name);
    }

    private void requireNonBlank(String value, String name) {
        GXManticoreUtils.requireNonBlank(value, name);
    }

    private void requireSearchArguments(String index, int offset, int limit) {
        GXManticoreUtils.requireSearchArguments(index, offset, limit);
    }

    private GXMantiCoreResDto<Dict> sendPost(String endpoint, String body, String contentType) {
        return client.sendPost(endpoint, body, contentType);
    }
}
