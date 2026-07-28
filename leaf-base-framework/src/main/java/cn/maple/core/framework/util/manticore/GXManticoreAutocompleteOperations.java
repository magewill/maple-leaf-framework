package cn.maple.core.framework.util.manticore;

import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manticore Buddy autocomplete operations.
 */
public final class GXManticoreAutocompleteOperations {
    private static final Set<String> RESERVED_OPTION_KEYS = Set.of("table", "query");
    private final GXManticoreClient client;

    GXManticoreAutocompleteOperations(GXManticoreClient client) {
        this.client = client;
    }

    public GXMantiCoreResDto<Dict> autocomplete(String table, String query) {
        return autocomplete(table, query, null);
    }

    public GXMantiCoreResDto<Dict> autocomplete(String table, String query,
                                                Map<String, Object> extraOptions) {
        GXManticoreUtils.requireNonBlank(table, "table");
        GXManticoreUtils.requireNonBlank(query, "query");
        Map<String, Object> payload = new HashMap<>();
        payload.put("table", table);
        payload.put("query", query);
        if (extraOptions != null) {
            if (extraOptions.keySet().stream().anyMatch(RESERVED_OPTION_KEYS::contains)) {
                throw new IllegalArgumentException("extraOptions must not override table or query");
            }
            payload.putAll(extraOptions);
        }
        return client.sendPost("/autocomplete", JSONUtil.toJsonStr(payload), "application/json");
    }
}
