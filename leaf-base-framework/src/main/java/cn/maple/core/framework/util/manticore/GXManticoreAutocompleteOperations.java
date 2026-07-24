package cn.maple.core.framework.util.manticore;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.inner.GXMantiCoreResDto;

import java.util.HashMap;
import java.util.Map;

import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.requireNonBlank;
import static cn.maple.core.framework.util.manticore.GXManticoreConfiguration.sendPost;

/**
 * Manticore Buddy autocomplete operations.
 */
public final class GXManticoreAutocompleteOperations {
    private GXManticoreAutocompleteOperations() {
    }

    public static GXMantiCoreResDto<JSON> autocomplete(String table, String query) {
        return autocomplete(table, query, null);
    }

    public static GXMantiCoreResDto<JSON> autocomplete(String table, String query,
                                                        Map<String, Object> extraOptions) {
        requireNonBlank(table, "table");
        requireNonBlank(query, "query");
        Map<String, Object> payload = new HashMap<>();
        payload.put("table", table);
        payload.put("query", query);
        if (extraOptions != null) {
            payload.putAll(extraOptions);
        }
        return sendPost("/autocomplete", JSONUtil.toJsonStr(payload), "application/json");
    }
}
