package cn.maple.canal.service.support;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.GXProcessCanalDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXCanalHandlerResolverTest {
    private final GXProcessCanalDataService dedicated = new StubHandler("dedicated");

    private final GXProcessCanalDataService fallback = new StubHandler("fallback");

    private GenericApplicationContext context;

    private GXCanalConsumeStatistics statistics;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void shouldResolveDedicatedHandlerFromSnapshot() {
        GXCanalHandlerResolver resolver = resolver(true);

        assertSame(dedicated, resolver.resolve("db", "user"));
        assertSame(fallback, resolver.resolve("db", "other_table"));
    }

    @Test
    void shouldFallBackToDefaultHandlerAndCountMiss() {
        GXCanalHandlerResolver resolver = resolver(true);

        assertSame(fallback, resolver.resolve("db", "unknown_table"));
        assertEquals(1, statistics.handlerMissCount());
    }

    @Test
    void shouldReturnNullWhenNoHandlerAndNoDefault() {
        GXCanalHandlerResolver resolver = resolver(false);

        assertNull(resolver.resolve("db", "unknown_table"));
        assertEquals(1, statistics.handlerMissCount());
    }

    @Test
    void shouldCacheNegativeResults() {
        GXCanalHandlerResolver resolver = resolver(true);

        resolver.resolve("db", "unknown_table");
        resolver.resolve("db", "unknown_table");
        resolver.resolve("db", "unknown_table");

        assertEquals(1, statistics.handlerMissCount(), "negative results must be cached");
    }

    @Test
    void shouldKeepWorkingWhenCacheLimitIsExceeded() {
        GXCanalProperties properties = new GXCanalProperties();
        properties.setHandlerCacheSize(2);
        GXCanalHandlerResolver resolver = newResolver(properties, true);

        for (int index = 0; index < 10; index++) {
            resolver.resolve("db", "table_" + index);
        }

        assertSame(fallback, resolver.resolve("db", "table_0"));
    }

    @Test
    void shouldBeConsistentUnderConcurrentAccess() throws Exception {
        GXCanalHandlerResolver resolver = resolver(true);
        ExecutorService executor = Executors.newFixedThreadPool(32);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int index = 0; index < 1000; index++) {
                tasks.add(() -> resolver.resolve("db", "user") == dedicated);
                tasks.add(() -> resolver.resolve("db", "other_" + Thread.currentThread().threadId()) == fallback);
            }
            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                assertTrue(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
        assertTrue(statistics.handlerMissCount() > 0);
    }

    @Test
    void shouldBuildCamelCaseBeanName() {
        assertEquals("dbUserService", GXCanalHandlerResolver.toServiceBeanName("db", "user"));
        assertEquals("dbUserDetailService", GXCanalHandlerResolver.toServiceBeanName("db", "user_detail"));
    }

    /**
     * Covers the "name looks invalid" warning branch: dots, dashes and upper case characters are
     * reported once per key, while resolution still falls back to the default handler.
     */
    @Test
    void shouldStillResolveWhenDatabaseOrTableLooksInvalid() {
        GXCanalHandlerResolver resolver = resolver(true);

        assertSame(fallback, resolver.resolve("demo.db", "UPPER-Case"));
        assertSame(fallback, resolver.resolve("demo.db", "UPPER-Case"));
        assertEquals(1, statistics.handlerMissCount(), "the negative result must be cached after the warning");
    }

    /**
     * Covers the name conflict branch: {@code a_b.c} and {@code a.b_c} both map to the same bean name,
     * so the resolver reports the conflict once and keeps resolving through the default handler.
     */
    @Test
    void shouldWarnWhenDifferentTablesMapToTheSameBeanName() {
        GXCanalHandlerResolver resolver = resolver(true);
        String firstName = GXCanalHandlerResolver.toServiceBeanName("a_b", "c");
        String secondName = GXCanalHandlerResolver.toServiceBeanName("a", "b_c");

        assertEquals(firstName, secondName, "precondition: both database/table pairs share one bean name");
        assertSame(fallback, resolver.resolve("a_b", "c"));
        assertSame(fallback, resolver.resolve("a", "b_c"));
        assertEquals(2, statistics.handlerMissCount());
    }

    private GXCanalHandlerResolver resolver(boolean withDefaultHandler) {
        return newResolver(new GXCanalProperties(), withDefaultHandler);
    }

    private GXCanalHandlerResolver newResolver(GXCanalProperties properties, boolean withDefaultHandler) {
        context = new GenericApplicationContext();
        context.registerBean("dbUserService", GXProcessCanalDataService.class, () -> dedicated);
        if (withDefaultHandler) {
            context.registerBean("defaultProcessCanalDataService", GXProcessCanalDataService.class, () -> fallback);
        }
        context.refresh();
        statistics = new GXCanalConsumeStatistics();
        GXCanalHandlerResolver resolver = new GXCanalHandlerResolver(context, properties, statistics);
        resolver.afterSingletonsInstantiated();
        assertNotNull(resolver);
        return resolver;
    }

    private static final class StubHandler implements GXProcessCanalDataService {
        private final String name;

        private StubHandler(String name) {
            this.name = name;
        }

        @Override
        public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
            return Dict.create().set("handler", name);
        }

        @Override
        public Dict processInsert(GXCanalDataDto canalData, Dict param) {
            return Dict.create().set("handler", name);
        }

        @Override
        public Dict processDelete(GXCanalDataDto canalData, Dict param) {
            return Dict.create().set("handler", name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
