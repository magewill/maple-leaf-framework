package cn.maple.core.datasource.builder;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class GXSqlBuildContext {
    private final Map<String, Optional<TableInfo>> tableInfoCache = new ConcurrentHashMap<>();

    TableInfo tableInfo(String tableName) {
        String normalizedTableName = CharSequenceUtil.trim(tableName);
        if (CharSequenceUtil.isBlank(normalizedTableName) || normalizedTableName.startsWith("(")) {
            return null;
        }
        return tableInfoCache.computeIfAbsent(normalizedTableName, GXSqlBuildContext::loadTableInfo).orElse(null);
    }

    private static Optional<TableInfo> loadTableInfo(String tableName) {
        try {
            return Optional.ofNullable(TableInfoHelper.getTableInfo(tableName));
        } catch (Exception ignored) {
            // Keep the existing fail-soft metadata lookup behavior.
            return Optional.empty();
        }
    }
}
