package cn.maple.core.datasource.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.core.framework.util.GXSpringContextUtils;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.util.Objects;

public interface GXTenantIdService {
    Logger LOG = LoggerFactory.getLogger(GXTenantIdService.class);

    default Expression getTenantId() {
        return new LongValue(0);
    }

    default boolean ignoreTable(String tableName, String tenantIdColumn) {
        if (CharSequenceUtil.isBlank(tableName)) {
            LOG.warn("表名为空，默认忽略租户条件");
            return true;
        }

        try {
            CaffeineCacheManager caffeineCacheManager = GXSpringContextUtils.getBean(CaffeineCacheManager.class);
            if (caffeineCacheManager == null) {
                LOG.warn("缓存管理器不可用，默认忽略租户条件，表名: {}", tableName);
                return true;
            }

            Cache cache = caffeineCacheManager.getCache("FRAMEWORK-CACHE");
            if (cache == null) {
                LOG.warn("缓存对象不可用，默认忽略租户条件，表名: {}", tableName);
                return true;
            }

            Boolean hasTenantIdField = cache.get(tableName, Boolean.class);
            if (Objects.nonNull(hasTenantIdField)) {
                return hasTenantIdField;
            }

            TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
            if (Objects.isNull(tableInfo)) {
                LOG.debug("表[{}]不存在，忽略租户条件", tableName);
                cache.put(tableName, Boolean.TRUE);
                return true;
            }

            boolean contains = !CollUtil.contains(tableInfo.getFieldList(),
                    field -> CharSequenceUtil.equalsIgnoreCase(field.getColumn(), tenantIdColumn));

            cache.put(tableName, contains);
            return contains;
        } catch (Exception e) {
            LOG.error("判断表[{}]是否忽略租户条件时发生异常，默认忽略租户条件", tableName, e);
            return true;
        }
    }
}
