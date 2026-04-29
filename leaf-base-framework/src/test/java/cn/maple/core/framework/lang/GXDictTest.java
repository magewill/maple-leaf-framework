package cn.maple.core.framework.lang;

import cn.hutool.core.lang.Dict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GXDictTest {
    @Test
    void testConstructorsAndFactoryMethods() {
        GXDict emptyDict = new GXDict();
        assertTrue(emptyDict.isEmpty());

        assertDoesNotThrow(() -> new GXDict(null));
        assertTrue(GXDict.create(null).isEmpty());

        Map<String, Object> map = new HashMap<>();
        map.put("name", "zhangsan");
        map.put("age", 25);

        GXDict dict = new GXDict(map);
        assertEquals("zhangsan", dict.getStr("name"));
        assertEquals(25, dict.getInt("age"));

        GXDict created = GXDict.create().set("key", "value");
        assertEquals("value", created.getStr("key"));

        GXDict createdFromMap = GXDict.create(map);
        assertEquals("zhangsan", createdFromMap.getStr("name"));
        assertEquals(25, createdFromMap.getInt("age"));
    }

    @Test
    void testBasicTypeGettersReturnDefaultsOnlyWhenValueIsMissing() {
        GXDict dict = new GXDict();

        assertEquals("default", dict.getStr("missing", "default"));
        assertEquals(0, dict.getInt("missing", 0));
        assertEquals(0L, dict.getLong("missing", 0L));
        assertEquals(0.0D, dict.getDouble("missing", 0.0D));
        assertEquals(0.0F, dict.getFloat("missing", 0.0F));
        assertEquals(BigDecimal.ZERO, dict.getBigDecimal("missing", BigDecimal.ZERO));
        assertEquals(BigInteger.ZERO, dict.getBigInteger("missing", BigInteger.ZERO));
        assertFalse(dict.getBool("missing", false));
        assertEquals((short) 0, dict.getShort("missing", (short) 0));

        dict.set("str", "text")
                .set("int", 100)
                .set("long", 200L)
                .set("double", 3.14D)
                .set("float", 2.718F)
                .set("bigDecimal", new BigDecimal("123.456"))
                .set("bigInteger", new BigInteger("9876543210"))
                .set("bool", true)
                .set("short", (short) 32);

        assertEquals("text", dict.getStr("str", "default"));
        assertEquals(100, dict.getInt("int", 0));
        assertEquals(200L, dict.getLong("long", 0L));
        assertEquals(3.14D, dict.getDouble("double", 0.0D));
        assertEquals(2.718F, dict.getFloat("float", 0.0F));
        assertEquals(new BigDecimal("123.456"), dict.getBigDecimal("bigDecimal", BigDecimal.ZERO));
        assertEquals(new BigInteger("9876543210"), dict.getBigInteger("bigInteger", BigInteger.ZERO));
        assertTrue(dict.getBool("bool", false));
        assertEquals((short) 32, dict.getShort("short", (short) 0));
    }

    @Test
    void testSafeTypeConversion() {
        GXDict dict = new GXDict()
                .set("strInt", "100")
                .set("strDouble", "3.14")
                .set("strBool", "true")
                .set("numZero", 0)
                .set("numOne", 1)
                .set("boolTrue", true)
                .set("boolFalse", false)
                .set("strYes", "yes")
                .set("strY", "y")
                .set("strOn", "on")
                .set("strNo", "no");

        assertEquals(100, dict.getSafe("strInt", Integer.class, 0));
        assertEquals(3.14D, dict.getSafe("strDouble", Double.class, 0.0D));
        assertTrue(dict.getSafe("strBool", Boolean.class, false));
        assertFalse(dict.getSafe("numZero", Boolean.class, true));
        assertTrue(dict.getSafe("numOne", Boolean.class, false));
        assertEquals(1, dict.getSafe("boolTrue", Integer.class, 0));
        assertEquals(0, dict.getSafe("boolFalse", Integer.class, 1));
        assertTrue(dict.getSafe("strYes", Boolean.class, false));
        assertTrue(dict.getSafe("strY", Boolean.class, false));
        assertTrue(dict.getSafe("strOn", Boolean.class, false));
        assertFalse(dict.getSafe("strNo", Boolean.class, true));
    }

    @Test
    void testSafeTypeConversionFallsBackWhenConversionFails() {
        GXDict dict = new GXDict()
                .set("invalidInt", "not a number")
                .set("invalidDate", "not a date")
                .set("invalidEnum", "missing");

        assertEquals(0, dict.getSafe("invalidInt", Integer.class, 0));
        assertEquals(LocalDate.of(2000, 1, 1),
                dict.getSafe("invalidDate", LocalDate.class, LocalDate.of(2000, 1, 1)));
        assertEquals(TestEnum.THREE, dict.getSafe("invalidEnum", TestEnum.class, TestEnum.THREE));
        assertEquals("fallback", dict.getSafe("anything", null, "fallback"));
    }

    @Test
    void testDateTimeHandling() {
        GXDict dict = new GXDict()
                .set("date", "2023-05-15")
                .set("dateTime", "2023-05-15 14:30:25")
                .set("time", "14:30:25");

        assertEquals(LocalDate.of(2023, 5, 15), dict.getLocalDate("date", null));
        assertEquals(LocalDateTime.of(2023, 5, 15, 14, 30, 25), dict.getLocalDateTime("dateTime", null));
        assertEquals(LocalTime.of(14, 30, 25), dict.getLocalTime("time", null));
        assertEquals(LocalDate.of(2000, 1, 1), dict.getLocalDate("missing", LocalDate.of(2000, 1, 1)));
    }

    @Test
    void testEnumConversion() {
        GXDict dict = new GXDict()
                .set("enum1", "ONE")
                .set("enum2", "two")
                .set("enum3", 2)
                .set("enum4", "INVALID");

        assertEquals(TestEnum.ONE, dict.getSafe("enum1", TestEnum.class, null));
        assertEquals(TestEnum.TWO, dict.getSafe("enum2", TestEnum.class, null));
        assertEquals(TestEnum.THREE, dict.getSafe("enum3", TestEnum.class, null));
        assertNull(dict.getSafe("enum4", TestEnum.class, null));
    }

    @Test
    void testCollectionHandling() {
        GXDict dict = new GXDict();
        List<String> stringList = Arrays.asList("a", "b", "c");
        List<String> defaultList = Arrays.asList("x", "y", "z");

        dict.set("stringList", stringList);
        dict.set("notList", "a,b,c");

        assertSame(stringList, dict.getList("stringList", null));
        assertEquals(defaultList, dict.getList("missingList", defaultList));
        assertEquals(defaultList, dict.getList("notList", defaultList));
    }

    @Test
    void testNestedDictHandling() {
        GXDict dict = new GXDict();
        GXDict nestedDict = new GXDict().set("nestedKey", "nestedValue");
        Dict hutoolDict = Dict.create().set("hutoolKey", "hutoolValue");
        Map<Object, Object> mixedKeyMap = new HashMap<>();
        mixedKeyMap.put(100, "numericKey");

        dict.set("nestedDict", nestedDict)
                .set("hutoolDict", hutoolDict)
                .set("mixedKeyMap", mixedKeyMap)
                .set("notDict", "value");

        assertSame(nestedDict, dict.getDict("nestedDict", null));
        assertEquals("hutoolValue", dict.getDict("hutoolDict", null).getStr("hutoolKey"));
        assertEquals("numericKey", dict.getDict("mixedKeyMap", null).getStr("100"));

        GXDict defaultValue = GXDict.create().set("defaultKey", "defaultValue");
        assertSame(defaultValue, dict.getDict("notDict", defaultValue));
    }

    @Test
    void testGetOrCreateDictStoresAndReusesNestedDict() {
        GXDict dict = new GXDict();

        GXDict createdDict = dict.getOrCreateDict("newDict");
        createdDict.set("createdKey", "createdValue");
        assertSame(createdDict, dict.getObj("newDict"));
        assertEquals("createdValue", dict.getDict("newDict", null).getStr("createdKey"));

        Map<String, Object> rawMap = new HashMap<>();
        rawMap.put("rawKey", "rawValue");
        dict.set("rawMap", rawMap);

        GXDict wrapped = dict.getOrCreateDict("rawMap");
        wrapped.set("anotherKey", "anotherValue");
        assertSame(wrapped, dict.getObj("rawMap"));
        assertEquals("rawValue", dict.getDict("rawMap", null).getStr("rawKey"));
        assertEquals("anotherValue", dict.getDict("rawMap", null).getStr("anotherKey"));
    }

    @Test
    void testFunctionalFeatures() {
        GXDict dict = new GXDict()
                .set("name", "zhang san")
                .set("age", 25)
                .set("invalidInt", "not a number");

        assertEquals("ZHANG SAN", dict.computeIfPresent("name", String.class, String::toUpperCase));
        Integer ageAfter10Years = dict.computeIfPresent("age", Integer.class, age -> age + 10);
        assertEquals(35, ageAfter10Years);
        assertNull(dict.computeIfPresent("missing", String.class, String::toUpperCase));
        assertEquals("DEFAULT", dict.computeIfPresent("missing", String.class, String::toUpperCase, "DEFAULT"));
        assertEquals(-1, dict.computeIfPresent("invalidInt", Integer.class, age -> age + 10, -1));

        Integer mapperFailureResult = dict.computeIfPresent("name", String.class, name -> {
            throw new IllegalStateException("boom");
        }, -1);
        assertEquals(-1, mapperFailureResult);

        assertEquals("zhang san", dict.getOrCompute("name", String.class, () -> "default name"));
        assertEquals("computed name", dict.getOrCompute("missing", String.class, () -> "computed name"));
    }

    @Test
    void testChainAndBatchOperations() {
        GXDict dict = new GXDict()
                .set("key1", "value1")
                .set("key2", 100)
                .set("key3", true);

        assertEquals("value1", dict.getStr("key1"));
        assertEquals(100, dict.getInt("key2"));
        assertTrue(dict.getBool("key3"));

        dict.setIfNotNull("key4", "value4")
                .setIfNotNull("key5", null);

        assertEquals("value4", dict.getStr("key4"));
        assertFalse(dict.containsKey("key5"));

        Map<String, Object> batchMap = new HashMap<>();
        batchMap.put("batchKey1", "batchValue1");
        batchMap.put("batchKey2", 200);

        assertSame(dict, dict.setAll(batchMap));
        assertSame(dict, dict.setAll(null));
        assertEquals("batchValue1", dict.getStr("batchKey1"));
        assertEquals(200, dict.getInt("batchKey2"));
    }

    @Test
    void testThreadSafetyForIndependentDictInstances() throws InterruptedException {
        final int threadCount = 10;
        final int iterations = 1000;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        GXDict dict = new GXDict();
                        String key = "key" + threadId + "-" + j;
                        dict.set(key, j);

                        if (dict.getInt(key, -1) == j) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdownNow();
        assertEquals(threadCount * iterations, successCount.get());
    }

    @Test
    void testGetJSONStr() {
        GXDict dict = new GXDict();

        assertEquals("{}", dict.getJSONStr("missing"));

        dict.set("simpleObj", new TestObject("test", 123));
        String jsonStr = dict.getJSONStr("simpleObj");
        assertTrue(jsonStr.contains("\"name\":\"test\""));
        assertTrue(jsonStr.contains("\"value\":123"));

        dict.set("nestedDict", new GXDict().set("nestedKey", "nestedValue"));
        assertTrue(dict.getJSONStr("nestedDict").contains("\"nestedKey\":\"nestedValue\""));
    }

    private enum TestEnum {
        ONE, TWO, THREE
    }

    private record TestObject(String name, int value) {
    }
}
