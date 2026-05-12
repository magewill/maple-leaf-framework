package cn.maple.core.framework.util;

import cn.maple.core.framework.exception.GXSqlInjectionException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GXDBStringUtilsTest {
    @Test
    void escapeStringAndSqlKeepNullAndEscapeSpecialCharacters() {
        assertNull(GXDBStringUtils.escapeRawString(null));
        assertNull(GXDBStringUtils.escapeString(null));
        assertNull(GXDBStringUtils.escapeSql(null));

        assertEquals("O\\'Reilly", GXDBStringUtils.escapeRawString("O'Reilly"));
        assertEquals("'O''Reilly'", GXDBStringUtils.escapeString("'O'Reilly'"));
        assertEquals("a\\;b\\=c", GXDBStringUtils.escapeSql("a;b=c"));
    }

    @Test
    void escapeSqlForLikeEscapesWildcardAndEscapeCharacter() {
        assertNull(GXDBStringUtils.escapeSqlForLike(null));
        assertEquals("a\\\\\\\\b\\%\\_", GXDBStringUtils.escapeSqlForLike("a\\b%_"));
    }

    @Test
    void checkDetectsSqlXssAndJsonInjection() {
        assertTrue(GXDBStringUtils.check("name' or '1'='1"));
        assertTrue(GXDBStringUtils.checkComprehensive("<script>alert(1)</script>"));
        assertTrue(GXDBStringUtils.checkJsonInjection("{\"$where\":\"this.age > 1\"}"));
        assertFalse(GXDBStringUtils.check("normal value"));
        assertThrows(NullPointerException.class, () -> GXDBStringUtils.check(null));
    }

    @Test
    void validatesIdentifiersAndBuildsSafeConditions() {
        assertTrue(GXDBStringUtils.isValidIdentifier("user_table.name"));
        assertFalse(GXDBStringUtils.isValidIdentifier("user-table.name"));

        assertDoesNotThrow(() -> GXDBStringUtils.validateTableName("user_table"));
        assertDoesNotThrow(() -> GXDBStringUtils.validateColumnName("user.name"));
        assertThrows(IllegalArgumentException.class, () -> GXDBStringUtils.validateTableName(""));
        assertThrows(GXSqlInjectionException.class, () -> GXDBStringUtils.validateColumnName("user-name"));

        assertEquals("user_name LIKE '%maple%' ESCAPE '\\'",
                GXDBStringUtils.buildSafeLikeCondition("user_name", "maple", "anywhere"));
        assertThrows(IllegalArgumentException.class,
                () -> GXDBStringUtils.buildSafeLikeCondition("user_name", "maple", null));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringUtils.buildSafeLikeCondition("user-name", "maple", "start"));
    }

    @Test
    void cleansInputAndEscapesJsonPath() {
        assertNull(GXDBStringUtils.validateAndCleanInput(null));
        assertEquals("abc", GXDBStringUtils.validateAndCleanInput(" abc "));
        assertThrows(GXSqlInjectionException.class, () -> GXDBStringUtils.validateAndCleanInput("1 or 1=1"));

        assertNull(GXDBStringUtils.escapeJsonPath(null));
        assertEquals("$.user.name", GXDBStringUtils.escapeJsonPath("$.user.name"));
        assertThrows(GXSqlInjectionException.class, () -> GXDBStringUtils.escapeJsonPath("$.user;drop table t"));
    }

    @Test
    void buildsParameterizedQueriesAndValidatesBatchValues() {
        Object[] query = GXDBStringUtils.prepareParameterizedQuery(List.of(1, 2, 3));
        assertArrayEquals(new Object[]{"(?, ?, ?)", List.of(1, 2, 3)}, query);
        assertEquals("()", GXDBStringUtils.prepareParameterizedQuery(List.of())[0]);

        List<List<Object>> batch = List.of(List.of("a", 1), List.of("b", 2));
        Object[] batchQuery = GXDBStringUtils.prepareBatchParameterizedQuery(batch);
        assertEquals("(?, ?)", batchQuery[0]);
        assertEquals(batch, batchQuery[1]);
        assertThrows(IllegalArgumentException.class,
                () -> GXDBStringUtils.prepareBatchParameterizedQuery(List.of(List.of(1), List.of(1, 2))));

        List<List<Object>> injectionBatch = new ArrayList<>();
        injectionBatch.add(List.of("safe"));
        injectionBatch.add(List.of("1 or 1=1"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringUtils.prepareBatchParameterizedQuery(injectionBatch));
    }

    @Test
    void processesBatchSqlAndSafeInClause() {
        assertEquals(List.of("select_name"), GXDBStringUtils.processBatchSqlStatements(List.of("select_name")));
        List<String> statementsWithNull = new ArrayList<>();
        statementsWithNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> GXDBStringUtils.processBatchSqlStatements(statementsWithNull));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringUtils.processBatchSqlStatements(List.of("select * from user")));

        assertEquals("('a', 'b')", GXDBStringUtils.buildSafeInClause(List.of("a", "b")));
        assertEquals("('')", GXDBStringUtils.buildSafeInClause(List.of("1 or 1=1")));
    }

    @Test
    void checkCanRunConcurrentlyWithoutThreadLocalState() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tasks.add(() -> GXDBStringUtils.check("normal value"));
            tasks.add(() -> GXDBStringUtils.check("1 or 1=1"));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i += 2) {
                assertFalse(futures.get(i).get());
                assertTrue(futures.get(i + 1).get());
            }
        }
    }
}
