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

class GXDBStringEscapeUtilsTest {
    @Test
    void escapeStringAndSqlKeepNullAndEscapeSpecialCharacters() {
        assertNull(GXDBStringEscapeUtils.escapeRawString(null));
        assertNull(GXDBStringEscapeUtils.escapeString(null));
        assertNull(GXDBStringEscapeUtils.escapeSql(null));

        assertEquals("O\\'Reilly", GXDBStringEscapeUtils.escapeRawString("O'Reilly"));
        assertEquals("'O''Reilly'", GXDBStringEscapeUtils.escapeString("'O'Reilly'"));
        assertEquals("a\\;b\\=c", GXDBStringEscapeUtils.escapeSql("a;b=c"));
    }

    @Test
    void escapeSqlForLikeEscapesWildcardAndEscapeCharacter() {
        assertNull(GXDBStringEscapeUtils.escapeSqlForLike(null));
        assertEquals("a\\\\\\\\b\\%\\_", GXDBStringEscapeUtils.escapeSqlForLike("a\\b%_"));
    }

    @Test
    void checkDetectsSqlXssAndJsonInjection() {
        assertTrue(GXDBStringEscapeUtils.check("name' or '1'='1"));
        assertTrue(GXDBStringEscapeUtils.checkComprehensive("<script>alert(1)</script>"));
        assertTrue(GXDBStringEscapeUtils.checkJsonInjection("{\"$where\":\"this.age > 1\"}"));
        assertFalse(GXDBStringEscapeUtils.check("normal value"));
        assertThrows(NullPointerException.class, () -> GXDBStringEscapeUtils.check(null));
    }

    @Test
    void validatesIdentifiersAndBuildsSafeConditions() {
        assertTrue(GXDBStringEscapeUtils.isValidIdentifier("user_table.name"));
        assertFalse(GXDBStringEscapeUtils.isValidIdentifier("user-table.name"));

        assertDoesNotThrow(() -> GXDBStringEscapeUtils.validateTableName("user_table"));
        assertDoesNotThrow(() -> GXDBStringEscapeUtils.validateColumnName("user.name"));
        assertThrows(IllegalArgumentException.class, () -> GXDBStringEscapeUtils.validateTableName(""));
        assertThrows(GXSqlInjectionException.class, () -> GXDBStringEscapeUtils.validateColumnName("user-name"));

        assertEquals("user_name LIKE '%maple%' ESCAPE '\\'",
                GXDBStringEscapeUtils.buildSafeLikeCondition("user_name", "maple", "anywhere"));
        assertThrows(IllegalArgumentException.class,
                () -> GXDBStringEscapeUtils.buildSafeLikeCondition("user_name", "maple", null));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.buildSafeLikeCondition("user-name", "maple", "start"));
    }

    @Test
    void cleansInputAndEscapesJsonPath() {
        assertNull(GXDBStringEscapeUtils.validateAndCleanInput(null));
        assertEquals("abc", GXDBStringEscapeUtils.validateAndCleanInput(" abc "));
        assertThrows(GXSqlInjectionException.class, () -> GXDBStringEscapeUtils.validateAndCleanInput("1 or 1=1"));

        assertNull(GXDBStringEscapeUtils.escapeJsonPath(null));
        assertEquals("$.user.name", GXDBStringEscapeUtils.escapeJsonPath("$.user.name"));
        assertThrows(GXSqlInjectionException.class, () -> GXDBStringEscapeUtils.escapeJsonPath("$.user;drop table t"));
    }

    @Test
    void buildsParameterizedQueriesAndValidatesBatchValues() {
        Object[] query = GXDBStringEscapeUtils.prepareParameterizedQuery(List.of(1, 2, 3));
        assertArrayEquals(new Object[]{"(?, ?, ?)", List.of(1, 2, 3)}, query);
        assertEquals("()", GXDBStringEscapeUtils.prepareParameterizedQuery(List.of())[0]);

        List<List<Object>> batch = List.of(List.of("a", 1), List.of("b", 2));
        Object[] batchQuery = GXDBStringEscapeUtils.prepareBatchParameterizedQuery(batch);
        assertEquals("(?, ?)", batchQuery[0]);
        assertEquals(batch, batchQuery[1]);
        assertThrows(IllegalArgumentException.class,
                () -> GXDBStringEscapeUtils.prepareBatchParameterizedQuery(List.of(List.of(1), List.of(1, 2))));

        List<List<Object>> injectionBatch = new ArrayList<>();
        injectionBatch.add(List.of("safe"));
        injectionBatch.add(List.of("1 or 1=1"));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.prepareBatchParameterizedQuery(injectionBatch));
    }

    @Test
    void processesBatchSqlAndSafeInClause() {
        assertEquals(List.of("select_name"), GXDBStringEscapeUtils.processBatchSqlStatements(List.of("select_name")));
        List<String> statementsWithNull = new ArrayList<>();
        statementsWithNull.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> GXDBStringEscapeUtils.processBatchSqlStatements(statementsWithNull));
        assertThrows(GXSqlInjectionException.class,
                () -> GXDBStringEscapeUtils.processBatchSqlStatements(List.of("select * from user")));

        assertEquals("('a', 'b')", GXDBStringEscapeUtils.buildSafeInClause(List.of("a", "b")));
        assertEquals("('')", GXDBStringEscapeUtils.buildSafeInClause(List.of("1 or 1=1")));
    }

    @Test
    void checkCanRunConcurrentlyWithoutThreadLocalState() throws Exception {
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tasks.add(() -> GXDBStringEscapeUtils.check("normal value"));
            tasks.add(() -> GXDBStringEscapeUtils.check("1 or 1=1"));
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
