package cn.maple.core.framework.util.manticore;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.requireIdentifier;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.requireNonEmpty;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.requireNonBlank;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.requireSearchArguments;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.sendPost;
import static cn.maple.core.framework.util.manticore.GXManticoreDocumentOperations.insert;
import static cn.maple.core.framework.util.manticore.GXManticoreDocumentOperations.search;

/**
 * Operations for Manticore percolate tables and reverse queries.
 */
public final class GXManticorePercolateOperations {
    private GXManticorePercolateOperations() {
    }

    /**
     * Runs a reverse percolate search with the default first page of ten results.
     */
    public static GXMantiCoreResDto<JSON> percolate(String index, List<Map<String, Object>> documents,
                                                     String field) {
        return percolate(index, documents, field, 0, 10, null);
    }

    /**
     * Runs a reverse percolate search with caller-controlled pagination and search options.
     */
    public static GXMantiCoreResDto<JSON> percolate(String index, List<Map<String, Object>> documents,
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
        return search(index, query, offset, limit, options);
    }

    /**
     * Adds a rule to a percolate table.
     */
    public static GXMantiCoreResDto<JSON> pqAddRule(String pqIndex, Long id, Map<String, Object> query,
                                                     List<String> tags) {
        return pqAddRule(pqIndex, id, query, tags, null);
    }

    /**
     * Adds a rule to a percolate table, optionally constrained by attribute filters.
     */
    public static GXMantiCoreResDto<JSON> pqAddRule(String pqIndex, Long id, Map<String, Object> query,
                                                     List<String> tags, String filters) {
        requireNonBlank(pqIndex, "pqIndex");
        requireNonEmpty(query, "query");
        Map<String, Object> doc = new HashMap<>();
        doc.put("query", query);
        if (tags != null && !tags.isEmpty()) {
            doc.put("tags", tags);
        }
        if (StrUtil.isNotBlank(filters)) {
            doc.put("filters", filters);
        }
        return insert(pqIndex, id, doc);
    }

    /**
     * Matches one document against the rules stored in a percolate table.
     */
    public static GXMantiCoreResDto<JSON> pqMatchDocument(String pqIndex, Map<String, Object> document) {
        requireIdentifier(pqIndex, "pqIndex");
        requireNonEmpty(document, "document");
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("document", document);
        return searchPercolateTable(pqIndex, percolate);
    }

    /**
     * Matches multiple documents against the rules stored in a percolate table.
     */
    public static GXMantiCoreResDto<JSON> pqMatchDocuments(String pqIndex, List<Map<String, Object>> documents) {
        requireIdentifier(pqIndex, "pqIndex");
        if (documents == null || documents.isEmpty() || documents.stream().anyMatch(doc -> doc == null || doc.isEmpty())) {
            throw new IllegalArgumentException("documents must contain non-empty documents");
        }
        Map<String, Object> percolate = new HashMap<>();
        percolate.put("documents", documents);
        return searchPercolateTable(pqIndex, percolate);
    }

    private static GXMantiCoreResDto<JSON> searchPercolateTable(String pqIndex, Map<String, Object> percolate) {
        Map<String, Object> query = new HashMap<>();
        query.put("percolate", percolate);
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", query);
        return sendPost("/pq/" + pqIndex + "/search", JSONUtil.toJsonStr(payload), "application/json");
    }
}
