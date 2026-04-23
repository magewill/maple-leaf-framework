package cn.maple.core.framework.dto.inner.condition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable rendered SQL condition segment.
 */
public class GXConditionSegment {
    private final String sql;
    private final Map<String, Object> params;

    public GXConditionSegment(String sql, Map<String, Object> params) {
        this.sql = sql;
        this.params = params == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(params));
    }

    public String getSql() {
        return sql;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
