package cn.maple.core.framework.dto.inner.condition;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record GXConditionSegment(String sql, Map<String, Object> params) {
    public GXConditionSegment(String sql, Map<String, Object> params) {
        this.sql = sql;
        this.params = params == null ? Collections.emptyMap() : Map.copyOf(params);
    }
}
