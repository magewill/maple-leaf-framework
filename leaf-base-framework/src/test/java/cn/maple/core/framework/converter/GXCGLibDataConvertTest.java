package cn.maple.core.framework.converter;

import cn.maple.core.framework.convert.GXCGLibDataConvert;
import cn.maple.core.framework.util.cglib.GXCglibUtils;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class GXCGLibDataConvertTest {
    private final GXCGLibDataConvert converter = GXCGLibDataConvert.getConverter(TargetBean.class);

    @Test
    void getConverterReturnsSameInstanceForClass() {
        GXCGLibDataConvert first = GXCGLibDataConvert.getConverter(TargetBean.class);
        GXCGLibDataConvert second = GXCGLibDataConvert.getConverter(TargetBean.class);

        assertSame(first, second);
    }

    @Test
    void beanCopierCacheUsesClassValueAndReusesCopierForSameTypePair() throws Exception {
        Field cacheField = GXCGLibDataConvert.class.getDeclaredField("BEAN_COPIER_CACHE");
        cacheField.setAccessible(true);
        // 旧的 WeakKeyConcurrentMap 实现会在这里失败：键是每次新建的记录对象，条目随时被 GC
        assertInstanceOf(ClassValue.class, cacheField.get(null));

        Method getBeanCopier = GXCGLibDataConvert.class.getDeclaredMethod("getBeanCopier", Class.class, Class.class);
        getBeanCopier.setAccessible(true);

        Object first = getBeanCopier.invoke(null, SourceBean.class, TargetBean.class);
        Object second = getBeanCopier.invoke(null, SourceBean.class, TargetBean.class);

        assertSame(first, second);
    }

    @Test
    void nullPrimitiveValuesUseDefaultValues() {
        assertEquals(0, converter.convert(null, int.class, null));
        assertEquals(false, converter.convert(null, boolean.class, null));
        assertEquals('\0', converter.convert(null, char.class, null));
        assertNull(converter.convert(null, String.class, null));
    }

    @Test
    void compatiblePrimitiveWrapperValuesReturnDirectly() {
        Integer value = 12;

        Object result = converter.convert(value, int.class, null);

        assertSame(value, result);
    }

    @Test
    void convertsStringToSimpleTypes() {
        assertEquals(42, converter.convert("42", Integer.class, null));
        assertEquals(true, converter.convert("true", Boolean.class, null));
        assertEquals("abc", converter.convert("abc", String.class, null));
    }

    @Test
    void convertsTemporalTypes() {
        Object date = converter.convert("2026-04-29 10:15:30", Date.class, null);
        Object calendar = converter.convert("2026-04-29 10:15:30", Calendar.class, null);
        Object localDateTime = converter.convert("2026-04-29T10:15:30", LocalDateTime.class, null);

        assertInstanceOf(Date.class, date);
        assertInstanceOf(Calendar.class, calendar);
        assertEquals(LocalDateTime.of(2026, 4, 29, 10, 15, 30), localDateTime);
    }

    @Test
    void convertsEnumCaseOrdinalAndInvalidValues() {
        assertEquals(Color.RED, converter.convert("red", Color.class, null));
        assertEquals(Color.GREEN, converter.convert(1, Color.class, null));
        assertNull(converter.convert("missing", Color.class, null));
        assertNull(converter.convert(100, Color.class, null));
    }

    @Test
    void convertsJsonObjectToBean() {
        Object result = converter.convert("{\"name\":\"Alice\",\"age\":\"31\",\"enabled\":true}", SimpleBean.class, null);

        assertInstanceOf(SimpleBean.class, result);
        SimpleBean bean = (SimpleBean) result;
        assertEquals("Alice", bean.getName());
        assertEquals(31, bean.getAge());
        assertTrue(bean.isEnabled());
    }

    @Test
    void convertsJsonObjectToMap() {
        Object result = converter.convert("{\"a\":1,\"b\":\"2\"}", Map.class, null);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(1, map.get("a"));
        assertEquals("2", map.get("b"));
    }

    @Test
    void convertsJsonArrayToTypedArray() {
        Object result = converter.convert("[1,2,3]", Integer[].class, null);

        assertArrayEquals(new Integer[]{1, 2, 3}, (Integer[]) result);
    }

    @Test
    void convertsArrayToCollection() {
        Object result = converter.convert(new String[]{"a", "b"}, List.class, null);

        assertInstanceOf(List.class, result);
        assertEquals(List.of("a", "b"), result);
    }

    @Test
    void convertsCollectionToObjectCollectionWithIdentityPreserved() {
        List<Object> source = new ArrayList<>();
        Object marker = new Object();
        source.add("A");
        source.add(1);
        source.add(marker);

        Object result = converter.convert(source, List.class, null);

        assertInstanceOf(List.class, result);
        List<?> list = (List<?>) result;
        assertEquals(3, list.size());
        assertSame(marker, list.get(2));
        assertEquals("A", list.get(0));
        assertEquals(1, list.get(1));
    }

    @Test
    void convertsMapToMapReturnsDirectEntriesWhenObjectTypes() {
        Object key = new Object();
        Object value = new Object();
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put(key, value);

        Object result = converter.convert(source, Map.class, null);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertSame(value, map.get(key));
    }

    @Test
    void convertsCollectionWithNullToPrimitiveArrayDefaultValue() {
        Object result = converter.convert(Arrays.asList(1, null, 3), int[].class, null);

        assertArrayEquals(new int[]{1, 0, 3}, (int[]) result);
    }

    @Test
    void convertsMapToConcurrentHashMapWithoutNullEntries() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("a", 1);
        source.put(null, 2);
        source.put("b", null);

        Object result = converter.convert(source, ConcurrentHashMap.class, null);

        assertInstanceOf(ConcurrentHashMap.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals(1, map.size());
        assertEquals(1, map.get("a"));
        assertEquals(Set.of("a"), map.keySet());
    }

    @Test
    void convertsMapToTreeMapWithNonComparableKeySafely() {
        Object key = new Object();
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put(key, "value");

        Object result = assertDoesNotThrow(() -> converter.convert(source, TreeMap.class, null));

        assertInstanceOf(TreeMap.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("value", map.get(key.toString()));
    }

    @Test
    void convertsMapToTreeMapSkipsNullKeyButKeepsNullValue() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put(null, 1);
        source.put("a", null);

        Object result = assertDoesNotThrow(() -> converter.convert(source, TreeMap.class, null));

        assertInstanceOf(TreeMap.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertFalse(map.keySet().stream().anyMatch(Objects::isNull));
        assertTrue(map.containsKey("a"));
        assertNull(map.get("a"));
    }

    @Test
    void convertsBeanToMap() {
        SimpleBean bean = new SimpleBean();
        bean.setName("Bean");
        bean.setAge(9);
        bean.setEnabled(true);

        Object result = converter.convert(bean, Map.class, null);

        assertInstanceOf(Map.class, result);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("Bean", map.get("name"));
        assertEquals(9, map.get("age"));
        assertEquals(true, map.get("enabled"));
    }

    @Test
    void convertsNestedBeanListUsingSetterGenericType() {
        SourceBean source = new SourceBean();
        source.setName("source");
        source.setItems(List.of(new SourceItem("A", "1"), new SourceItem("B", "2")));

        TargetBean target = GXCglibUtils.copy(source, TargetBean.class, converter);

        assertEquals("source", target.getName());
        assertEquals(2, target.getItems().size());
        assertEquals(TargetItem.class, target.getItems().get(0).getClass());
        assertEquals("A", target.getItems().get(0).getCode());
        assertEquals(1, target.getItems().get(0).getQty());
    }

    @Test
    void convertsNestedMapValueUsingSetterGenericType() {
        SourceMapBean source = new SourceMapBean();
        source.setScores(Map.of("math", "98", "english", "87"));

        TargetMapBean target = GXCglibUtils.copy(source, TargetMapBean.class,
                GXCGLibDataConvert.getConverter(TargetMapBean.class));

        assertEquals(98, target.getScores().get("math"));
        assertEquals(87, target.getScores().get("english"));
    }

    @Test
    void convertsJsonArrayStringUsingSetterGenericType() {
        SourceJsonListBean source = new SourceJsonListBean();
        source.setItems("[{\"code\":\"A\",\"qty\":\"1\"},{\"code\":\"B\",\"qty\":\"2\"}]");

        TargetBean target = GXCglibUtils.copy(source, TargetBean.class,
                GXCGLibDataConvert.getConverter(TargetBean.class));

        assertEquals(2, target.getItems().size());
        assertEquals(TargetItem.class, target.getItems().get(0).getClass());
        assertEquals(2, target.getItems().get(1).getQty());
    }

    @Test
    void convertsOptionalUsingSetterGenericType() {
        SourceOptionalBean source = new SourceOptionalBean();
        source.setQty("42");

        TargetOptionalBean target = GXCglibUtils.copy(source, TargetOptionalBean.class,
                GXCGLibDataConvert.getConverter(TargetOptionalBean.class));

        assertEquals(Optional.of(42), target.getQty());
    }

    @Test
    void convertsOptionalSourceUsingSetterGenericType() {
        SourceOptionalValueBean source = new SourceOptionalValueBean();
        source.setQty(Optional.of("42"));

        TargetOptionalBean target = GXCglibUtils.copy(source, TargetOptionalBean.class,
                GXCGLibDataConvert.getConverter(TargetOptionalBean.class));

        assertEquals(Optional.of(42), target.getQty());
    }

    @Test
    void convertsNullToEmptyOptional() {
        assertEquals(Optional.empty(), converter.convert(null, Optional.class, null));
    }

    @Test
    void fillBeanConvertsValuesAndIgnoresUnknownProperties() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", 123);
        source.put("age", "42");
        source.put("enabled", null);
        source.put("unknown", "ignored");

        SimpleBean bean = GXCglibUtils.toBean(source, SimpleBean.class);

        assertEquals("123", bean.getName());
        assertEquals(42, bean.getAge());
        assertFalse(bean.isEnabled());
    }

    @Test
    void fillBeanConvertsNestedCollectionUsingFieldGenericType() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("items", List.of(
                Map.of("code", "A", "qty", "1"),
                Map.of("code", "B", "qty", "2")
        ));

        TargetBean bean = GXCglibUtils.toBean(source, TargetBean.class);

        assertEquals(2, bean.getItems().size());
        assertEquals(TargetItem.class, bean.getItems().get(0).getClass());
        assertEquals(1, bean.getItems().get(0).getQty());
    }

    @Test
    void fillBeanConvertsConcurrentMapValuesAndSkipsNullEntries() {
        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("math", "98");
        scores.put("empty", null);
        Map<String, Object> source = Map.of("scores", scores);

        TargetConcurrentMapBean bean = GXCglibUtils.toBean(source, TargetConcurrentMapBean.class);

        assertInstanceOf(ConcurrentHashMap.class, bean.getScores());
        assertEquals(1, bean.getScores().size());
        assertEquals(98, bean.getScores().get("math"));
        assertFalse(bean.getScores().containsKey("empty"));
    }

    @Test
    void copyListUsesConverterAndSkipsNullElements() {
        SourceBean first = new SourceBean();
        first.setName("first");
        first.setItems(List.of(new SourceItem("A", "1")));
        SourceBean second = new SourceBean();
        second.setName("second");
        second.setItems(List.of(new SourceItem("B", "2")));

        List<TargetBean> result = GXCglibUtils.copyList(
                Arrays.asList(first, null, second),
                TargetBean::new,
                GXCGLibDataConvert.getConverter(TargetBean.class)
        );

        assertEquals(2, result.size());
        assertEquals("first", result.get(0).getName());
        assertEquals(TargetItem.class, result.get(0).getItems().get(0).getClass());
        assertEquals(2, result.get(1).getItems().get(0).getQty());
    }

    @Test
    void copyListInvokesCallbackForCopiedItemsOnly() {
        SourceBean first = new SourceBean();
        first.setName("first");
        SourceBean second = new SourceBean();
        second.setName("second");
        AtomicInteger callbackCount = new AtomicInteger();

        List<TargetBean> result = GXCglibUtils.copyList(
                Arrays.asList(first, null, second),
                TargetBean::new,
                GXCGLibDataConvert.getConverter(TargetBean.class),
                (source, target) -> {
                    callbackCount.incrementAndGet();
                    target.setName(target.getName() + "-done");
                }
        );

        assertEquals(2, callbackCount.get());
        assertEquals(2, result.size());
        assertEquals("first-done", result.get(0).getName());
        assertEquals("second-done", result.get(1).getName());
    }

    @Test
    void copyListSupportsTargetClassOverload() {
        SourceBean source = new SourceBean();
        source.setName("source");
        source.setItems(List.of(new SourceItem("A", "1")));

        List<TargetBean> result = GXCglibUtils.copyList(
                List.of(source),
                TargetBean.class,
                GXCGLibDataConvert.getConverter(TargetBean.class)
        );

        assertEquals(1, result.size());
        assertEquals(TargetItem.class, result.get(0).getItems().get(0).getClass());
        assertEquals(1, result.get(0).getItems().get(0).getQty());
    }

    @Test
    void fillBeanNormalizesNonStringKeys() {
        Map<Object, Object> source = new LinkedHashMap<>();
        source.put(new StringBuilder("name"), 123);
        source.put("age", "42");

        SimpleBean bean = GXCglibUtils.toBean(source, SimpleBean.class);

        assertEquals("123", bean.getName());
        assertEquals(42, bean.getAge());
    }

    @Test
    void fillBeanSkipsReadOnlyPropertyInsteadOfFailing() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "alpha");
        source.put("label", "ignored");

        ReadOnlyBean bean = GXCglibUtils.toBean(source, ReadOnlyBean.class);

        assertEquals("alpha", bean.getName());
        assertEquals("fixed", bean.getLabel());
    }

    @Test
    void fillBeanPropagatesSetterValidationFailure() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "alpha");

        // setter 内部校验拒绝时必须向上抛出，不能被当成"只读属性"静默跳过
        assertThrows(IllegalArgumentException.class,
                () -> GXCglibUtils.toBean(source, ValidatingBean.class));
    }

    @Test
    void copyListRejectsNullTargetInstance() {
        SourceBean source = new SourceBean();
        source.setName("source");

        assertThrows(IllegalStateException.class,
                () -> GXCglibUtils.copyList(List.of(source), () -> null, null, null));
    }

    @Test
    void copyListReturnsMutableListWhenSourceIsEmpty() {
        List<TargetBean> result = GXCglibUtils.copyList(List.of(), TargetBean.class);

        assertTrue(result.isEmpty());
        assertDoesNotThrow(() -> result.add(new TargetBean()));
    }

    @Test
    void copyListReResolvesCopierWhenSupplierReturnsDifferentSubclasses() {
        SourceBean first = new SourceBean();
        first.setName("first");
        SourceBean second = new SourceBean();
        second.setName("second");

        // 工厂每次返回不同的目标子类，用于覆盖 copyList 快路径里
        // "currentTargetClass != lastTargetClass 时重新取 copier" 的分支
        AtomicInteger calls = new AtomicInteger();
        Supplier<TargetBean> factory = () -> calls.getAndIncrement() % 2 == 0
                ? new TargetBean() : new ExtendedTargetBean();

        List<TargetBean> result = GXCglibUtils.copyList(
                List.of(first, second),
                factory,
                GXCGLibDataConvert.getConverter(TargetBean.class)
        );

        assertEquals(2, result.size());
        assertEquals(TargetBean.class, result.get(0).getClass());
        assertEquals(ExtendedTargetBean.class, result.get(1).getClass());
        assertEquals("first", result.get(0).getName());
        assertEquals("second", result.get(1).getName());
    }

    @Test
    void copyAndFillRejectNullArguments() {
        SourceBean source = new SourceBean();
        TargetBean target = new TargetBean();

        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copy(null, TargetBean.class));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copy(source, (Class<TargetBean>) null));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copy(null, target));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copy(source, (Object) null));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copyList(null, TargetBean.class));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copyList(List.of(source), (Class<TargetBean>) null));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copyList(null, TargetBean::new));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.copyList(List.of(source), (SupplierTargetFactory<TargetBean>) null));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.toMap(null));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.fillBean(null, target));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.fillBean(Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.toBean(null, TargetBean.class));
        assertThrows(IllegalArgumentException.class, () -> GXCglibUtils.toBean(Map.of(), null));
        assertThrows(RuntimeException.class, () -> GXCglibUtils.copy(source, NoInstantiableType.class));
        assertThrows(RuntimeException.class, () -> GXCglibUtils.toBean(Map.of("name", "alpha"), NoInstantiableType.class));
    }

    @Test
    void returnsNullWhenConversionFails() {
        assertNull(converter.convert("1", null, null));
        assertNull(converter.convert(new Object(), Integer.class, null));
        assertNull(converter.convert("[1,2]", SimpleBean.class, null));
    }

    @Test
    void converterCanBeUsedConcurrently() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> converter.convert("RED", Color.class, null) == Color.RED,
                    () -> Arrays.equals((int[]) converter.convert(Arrays.asList(1, null, 3), int[].class, null),
                            new int[]{1, 0, 3}),
                    () -> GXCglibUtils.toBean(Map.of("age", "7"), SimpleBean.class).getAge() == 7,
                    () -> GXCGLibDataConvert.getConverter(TargetBean.class) == converter,
                    () -> ((Map<?, ?>) converter.convert(Map.of("a", 1), ConcurrentHashMap.class, null)).get("a").equals(1),
                    () -> ((Integer[]) converter.convert("[4,5]", Integer[].class, null))[1].equals(5)
            );

            for (Future<Boolean> future : executor.invokeAll(tasks)) {
                assertTrue(future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    enum Color {
        RED,
        GREEN
    }

    @Data
    static class SimpleBean {
        private String name;
        private int age;
        private boolean enabled;
    }

    @Data
    static class SourceBean {
        private String name;
        private List<SourceItem> items;
    }

    @Data
    static class TargetBean {
        private String name;
        private List<TargetItem> items;
    }

    /**
     * 与 {@link TargetBean} 属性完全一致的子类，只用于验证 {@code copyList} 在目标类型变化时重新解析 copier。
     */
    static class ExtendedTargetBean extends TargetBean {
    }

    @Data
    static class SourceMapBean {
        private Map<String, String> scores;
    }

    @Data
    static class SourceJsonListBean {
        private String items;
    }

    @Data
    static class SourceOptionalBean {
        private String qty;
    }

    @Data
    static class TargetOptionalBean {
        private Optional<Integer> qty;
    }

    @Data
    static class SourceOptionalValueBean {
        private Optional<String> qty;
    }

    @Data
    static class TargetMapBean {
        private Map<String, Integer> scores;
    }

    @Data
    static class TargetConcurrentMapBean {
        private ConcurrentHashMap<String, Integer> scores;
    }

    @Data
    static class SourceItem {
        private final String code;
        private final String qty;
    }

    @Data
    static class TargetItem {
        private String code;
        private int qty;
    }

    @Data
    static class ReadOnlyBean {
        private String name;

        public String getLabel() {
            return "fixed";
        }
    }

    static class ValidatingBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            throw new IllegalArgumentException("name rejected by setter");
        }
    }

    interface NoInstantiableType {
    }

    @FunctionalInterface
    interface SupplierTargetFactory<T> extends java.util.function.Supplier<T> {
    }
}
