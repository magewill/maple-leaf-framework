package cn.maple.canal.service.support;

import cn.hutool.core.text.CharSequenceUtil;
import cn.maple.canal.constant.GXCanalConstant;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.GXProcessCanalDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves table level handlers without touching the Spring container on the hot path.
 *
 * <p>A read only snapshot of all {@link GXProcessCanalDataService} beans is taken once, after all
 * singletons are ready. Resolved names are cached, including negative results, so a table without a
 * dedicated handler costs one map lookup. The cache is bounded: when it grows past
 * {@code canal.handler-cache-size} it is cleared, which keeps memory bounded for pathological key
 * spaces without pulling in an eviction library.</p>
 */
@Slf4j
@Component
public class GXCanalHandlerResolver implements SmartInitializingSingleton {
    private static final String DEFAULT_HANDLER_BEAN_NAME = "defaultProcessCanalDataService";

    private static final ResolvedHandler NO_HANDLER = new ResolvedHandler(null);

    private final ApplicationContext applicationContext;

    private final GXCanalProperties canalProperties;

    private final GXCanalConsumeStatistics statistics;

    private final Map<String, GXProcessCanalDataService> snapshot = new ConcurrentHashMap<>();

    private final Map<String, ResolvedHandler> resolvedCache = new ConcurrentHashMap<>();

    private final Map<String, String> beanNameOwners = new ConcurrentHashMap<>();

    private final Map<String, AtomicBoolean> warnedKeys = new ConcurrentHashMap<>();

    private volatile GXProcessCanalDataService defaultHandler;

    public GXCanalHandlerResolver(ApplicationContext applicationContext, GXCanalProperties canalProperties,
                                  GXCanalConsumeStatistics statistics) {
        this.applicationContext = applicationContext;
        this.canalProperties = canalProperties;
        this.statistics = statistics;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, GXProcessCanalDataService> handlers =
                applicationContext.getBeansOfType(GXProcessCanalDataService.class);
        snapshot.putAll(handlers);
        defaultHandler = handlers.get(DEFAULT_HANDLER_BEAN_NAME);
        log.info("Canal handler snapshot built: handlerCount={}, defaultHandlerPresent={}",
                handlers.size(), defaultHandler != null);
    }

    /**
     * Resolves the handler for a table.
     *
     * @param database database name, must not be blank
     * @param table    table name, must not be blank
     * @return dedicated handler, the default handler, or null when neither exists
     */
    public @Nullable GXProcessCanalDataService resolve(String database, String table) {
        String cacheKey = database + "." + table;
        ResolvedHandler cached = resolvedCache.get(cacheKey);
        if (cached != null) {
            return cached.handler();
        }
        ResolvedHandler resolved = doResolve(database, table, cacheKey);
        if (resolvedCache.size() >= cacheSizeLimit()) {
            resolvedCache.clear();
        }
        ResolvedHandler previous = resolvedCache.putIfAbsent(cacheKey, resolved);
        return (previous == null ? resolved : previous).handler();
    }

    /**
     * Builds the bean name used to look up a table handler.
     *
     * @param database database name
     * @param table    table name
     * @return camel case service bean name
     */
    public static String toServiceBeanName(String database, String table) {
        return CharSequenceUtil.toCamelCase(CharSequenceUtil.format("{}_{}_Service", database, table));
    }

    private ResolvedHandler doResolve(String database, String table, String cacheKey) {
        String serviceBeanName = toServiceBeanName(database, table);
        warnIfNameLooksInvalid(database, table, cacheKey);
        String previousOwner = beanNameOwners.putIfAbsent(serviceBeanName, cacheKey);
        if (previousOwner != null && !previousOwner.equals(cacheKey)) {
            warnOnce("conflict:" + serviceBeanName, "Canal handler name conflict: beanName={}, first={}, second={}",
                    serviceBeanName, previousOwner, cacheKey);
        }
        GXProcessCanalDataService handler = snapshot.get(serviceBeanName);
        if (handler != null) {
            return new ResolvedHandler(handler);
        }
        statistics.recordHandlerMiss();
        warnHandlerMiss(serviceBeanName, cacheKey);
        return defaultHandler == null ? NO_HANDLER : new ResolvedHandler(defaultHandler);
    }

    private void warnIfNameLooksInvalid(String database, String table, String cacheKey) {
        if (containsUnsupportedCharacter(database) || containsUnsupportedCharacter(table)) {
            warnOnce("invalid:" + cacheKey,
                    "Canal handler name may be invalid: database={}, table={}", database, table);
        }
    }

    private boolean containsUnsupportedCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '.' || current == '-' || Character.isUpperCase(current)) {
                return true;
            }
        }
        return false;
    }

    private void warnHandlerMiss(String serviceBeanName, String cacheKey) {
        String level = canalProperties.getHandlerMissLogLevel();
        if (GXCanalConstant.LOG_LEVEL_OFF.equalsIgnoreCase(level)) {
            return;
        }
        if (GXCanalConstant.LOG_LEVEL_WARN.equalsIgnoreCase(level)) {
            log.warn("No table handler for beanName={}, key={}; using default handler", serviceBeanName, cacheKey);
            return;
        }
        if (GXCanalConstant.LOG_LEVEL_DEBUG.equalsIgnoreCase(level)) {
            log.debug("No table handler for beanName={}, key={}; using default handler", serviceBeanName, cacheKey);
            return;
        }
        // LOG_LEVEL_WARN_ONCE is both the configured default and the fallback for unknown values.
        warnOnce("miss:" + cacheKey, "No table handler for beanName={}, key={}; using default handler",
                serviceBeanName, cacheKey);
    }

    private void warnOnce(String warnKey, String format, Object... arguments) {
        AtomicBoolean warned = warnedKeys.computeIfAbsent(warnKey, key -> new AtomicBoolean(false));
        if (warned.compareAndSet(false, true)) {
            log.warn(format, arguments);
        }
    }

    private int cacheSizeLimit() {
        return Math.max(1, canalProperties.getHandlerCacheSize());
    }

    private record ResolvedHandler(@Nullable GXProcessCanalDataService handler) {
    }
}
