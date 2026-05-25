package cn.maple.core.datasource.config;

import cn.hutool.core.text.CharSequenceUtil;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class GXDynamicDbTypeRegistry {
    private static final Map<String, String> DB_TYPE_BY_DATASOURCE = new ConcurrentHashMap<>();

    private GXDynamicDbTypeRegistry() {
    }

    public static void register(String dataSourceName, String dbType) {
        if (CharSequenceUtil.isBlank(dataSourceName) || CharSequenceUtil.isBlank(dbType)) {
            return;
        }
        DB_TYPE_BY_DATASOURCE.put(dataSourceName, dbType.trim());
    }

    public static String resolve(String dataSourceName) {
        if (CharSequenceUtil.isBlank(dataSourceName)) {
            return null;
        }
        return DB_TYPE_BY_DATASOURCE.get(dataSourceName);
    }

    public static String resolveCurrent() {
        return Optional.ofNullable(GXDynamicContextHolder.peek())
                .map(GXDynamicDbTypeRegistry::resolve)
                .orElse(null);
    }

    public static void clear() {
        DB_TYPE_BY_DATASOURCE.clear();
    }
}
