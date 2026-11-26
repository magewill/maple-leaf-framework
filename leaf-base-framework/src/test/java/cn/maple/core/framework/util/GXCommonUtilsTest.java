package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.api.dto.req.GXUpdateFieldRequest;
import cn.maple.core.framework.constant.GXBuilderConstant;
import cn.maple.core.framework.constant.GXCommonConstant;
import cn.maple.core.framework.dto.inner.condition.GXCondition;
import cn.maple.core.framework.dto.inner.field.GXUpdateField;
import cn.maple.core.framework.dto.inner.field.GXUpdateStrField;
import cn.maple.core.framework.exception.GXBeanValidateException;
import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.core.framework.exception.GXConvertException;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GXCommonUtilsTest {
    @Test
    void shouldDistinguishStrictAndLenientOverloadsOnSimpleTypeConversionFailure() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("demo.age", Integer.class)).thenThrow(new IllegalArgumentException("bad value"));
        when(environment.getProperty("demo.age", String.class)).thenReturn("abc");

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);

            assertThrows(GXConvertException.class, () -> GXCommonUtils.getEnvironmentValue("demo.age", Integer.class));
            assertEquals(-1, GXCommonUtils.getEnvironmentValue("demo.age", Integer.class, -1));
        }
    }

    @Test
    void shouldParseJsonPropertyForComplexTypeWhenDefaultValueProvided() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("demo.names", List.class)).thenReturn(null);
        when(environment.getProperty("demo.names", String.class)).thenReturn("[\"alpha\",\"beta\"]");

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());

            List<?> names = GXCommonUtils.getEnvironmentValue("demo.names", List.class, List.of("fallback"));

            assertEquals(List.of("alpha", "beta"), names);
        }
    }

    @Test
    void shouldPropagateNonJacksonDeserializationFailureFromStrictOverload() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("demo.payload", String.class)).thenReturn("[\"alpha\"]");

        // 自定义反序列化器抛出的不是 JacksonException，若内层只 catch JacksonException，
        // 异常会漏到外层 catch，在严格模式下被静默降级成默认值
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        Mockito.doThrow(new IllegalArgumentException("custom deserializer failed"))
                .when(objectMapper).readValue(Mockito.anyString(), Mockito.any(Class.class));

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(objectMapper);

            assertThrows(GXConvertException.class, () -> GXCommonUtils.getEnvironmentValue("demo.payload", List.class));
            assertEquals(List.of("fallback"),
                    GXCommonUtils.getEnvironmentValue("demo.payload", List.class, List.of("fallback")));
        }
    }

    @Test
    void shouldParseJsonPropertyForComplexTypeWithoutDefaultValue() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("demo.names", List.class)).thenReturn(null);
        when(environment.getProperty("demo.names", String.class)).thenReturn("[\"alpha\",\"beta\"]");

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());

            List<?> names = GXCommonUtils.getEnvironmentValue("demo.names", List.class);

            assertEquals(List.of("alpha", "beta"), names);
        }
    }

    @Test
    void shouldReturnDefaultValueWhenEnvironmentUnavailable() {
        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(null);

            String value = GXCommonUtils.getEnvironmentValue("demo.name", String.class, "fallback");

            assertEquals("fallback", value);
        }
    }

    @Test
    void shouldReturnDefaultValueWhenObjectMapperUnavailableForComplexProperty() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("demo.bean", JsonBean.class)).thenReturn(null);
        when(environment.getProperty("demo.bean", String.class)).thenReturn("{\"name\":\"alpha\"}");

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(null);

            JsonBean value = GXCommonUtils.getEnvironmentValue("demo.bean", JsonBean.class, new JsonBean("fallback"));

            assertEquals("fallback", value.getName());
        }
    }

    @Test
    void shouldRejectInvalidEnvironmentArguments() {
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.getEnvironmentValue("", String.class));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.getEnvironmentValue("demo", null));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.getEnvironmentValue("", String.class, "fallback"));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.getEnvironmentValue("demo", null, "fallback"));
    }

    @Test
    void shouldReturnActiveProfileBeforeDefaultProfile() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "gray"});
        when(environment.getDefaultProfiles()).thenReturn(new String[]{"defaultProfile"});

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);

            assertEquals("prod", GXCommonUtils.getActiveProfile());
        }
    }

    @Test
    void shouldReturnDefaultProfileWhenNoActiveProfileExists() {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(environment.getDefaultProfiles()).thenReturn(new String[]{"fallbackProfile"});

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(environment);

            assertEquals("fallbackProfile", GXCommonUtils.getActiveProfile());
        }
    }

    @Test
    void shouldReturnDefaultProfileWhenEnvironmentUnavailable() {
        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(GXSpringContextUtils::getEnvironment).thenReturn(null);

            assertEquals("default", GXCommonUtils.getActiveProfile());
        }
    }

    @Test
    void shouldCreateClassDefaultValuesForPrimitiveAndBean() {
        assertEquals(0, GXCommonUtils.getClassDefaultValue(int.class));

        JsonBean value = GXCommonUtils.getClassDefaultValue(JsonBean.class);

        assertNotNull(value);
        assertNull(value.getName());
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.getClassDefaultValue(null));
    }

    @Test
    void shouldMaskValidPhoneAndKeepInvalidMaskArgumentsUnchanged() {
        assertEquals("138****8000", GXCommonUtils.hiddenPhoneNumber("13812348000", 3, 7, '*'));
        assertEquals("13812348000", GXCommonUtils.hiddenPhoneNumber("13812348000", -1, 7, '*'));
        assertEquals("", GXCommonUtils.hiddenPhoneNumber("12345", 1, 3, '*'));
        assertEquals("", GXCommonUtils.hiddenPhoneNumber("", 1, 3, '*'));
    }

    @Test
    void shouldReturnTrueWhenPhoneOrTelephoneIsInvalid() {
        assertFalse(GXCommonUtils.checkPhone("13812348000"));
        assertTrue(GXCommonUtils.checkPhone("12345"));
        assertTrue(GXCommonUtils.checkPhone(""));
        assertFalse(GXCommonUtils.checkTelephone("010-12345678"));
        assertTrue(GXCommonUtils.checkTelephone("01012345678"));
        assertTrue(GXCommonUtils.checkTelephone(""));
    }

    @Test
    void shouldRejectPhoneWithCommaWhenValidatingMobile() {
        assertTrue(GXCommonUtils.checkPhone("14,12345678"));
        assertTrue(GXCommonUtils.checkPhone("15,12345678"));
        assertTrue(GXCommonUtils.checkPhone("17,12345678"));
        assertTrue(GXCommonUtils.checkPhone("19,12345678"));
    }

    @Test
    void shouldAcceptModernMobilePrefixWhenValidatingMobile() {
        assertFalse(GXCommonUtils.checkPhone("19512345678"));
        assertFalse(GXCommonUtils.checkPhone("19212345678"));
        assertFalse(GXCommonUtils.checkPhone("17112345678"));
        assertFalse(GXCommonUtils.checkPhone("16212345678"));
    }

    @Test
    void shouldEncryptAndDecryptDictData() {
        Dict data = Dict.create().set("name", "alpha").set("age", 18);

        String encrypted = GXCommonUtils.encryptedData(data, "secret", 60);
        Dict decrypted = GXCommonUtils.decryptedData(encrypted, "secret");

        assertEquals("alpha", decrypted.getStr("name"));
        assertEquals(18, decrypted.getInt("age"));
        assertTrue(GXCommonUtils.decryptedData("not-cipher", "secret").isEmpty());
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.encryptedData(Dict.create(), "secret", 0));
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.encryptedData(data, "", 0));
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.decryptedData(encrypted, ""));
    }

    @Test
    void shouldConvertSimpleSourceToSimpleTargetType() {
        Integer value = GXCommonUtils.convertSourceToTarget("123", Integer.class, null, null);

        assertEquals(123, value);
    }

    @Test
    void shouldConvertBeanAndInvokeProcessAndVerifyHooks() {
        SourceBean source = new SourceBean();
        source.setName("alpha");

        TargetBean target = GXCommonUtils.convertSourceToTarget(source, TargetBean.class, null, null, Dict.create().set("suffix", "-done"));

        assertEquals("alpha-done", target.getName());
        assertTrue(target.isVerified());
    }

    @Test
    void shouldHandleNullAndInvalidSourceConversionArguments() {
        assertNull(GXCommonUtils.convertSourceToTarget(null, TargetBean.class, null, null));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.convertSourceToTarget(new SourceBean(), null, null, null));
        assertThrows(GXConvertException.class, () -> GXCommonUtils.convertSourceToTarget("abc", Integer.class, null, null));
        assertEquals(Collections.emptyList(), GXCommonUtils.convertSourceListToTargetList(null, TargetBean.class));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.convertSourceListToTargetList(List.of(new SourceBean()), null));
    }

    @Test
    void shouldConvertSourceListToTargetList() {
        SourceBean first = new SourceBean();
        first.setName("alpha");
        SourceBean second = new SourceBean();
        second.setName("beta");

        List<TargetBean> targets = GXCommonUtils.convertSourceListToTargetList(List.of(first, second), TargetBean.class, "customizeProcess", null, Dict.create());

        assertEquals(2, targets.size());
        assertEquals("alpha", targets.getFirst().getName());
        assertTrue(targets.getFirst().isVerified());
    }

    @Test
    void shouldReflectCallMatchingMethodsAndPrivateMethods() {
        ReflectionTarget target = new ReflectionTarget();

        assertEquals("int:7", GXCommonUtils.reflectCallObjectMethod(target, "accept", 7));
        assertEquals("text:null", GXCommonUtils.reflectCallObjectMethod(target, "accept", (Object) null));
        assertEquals("hidden:value", GXCommonUtils.reflectCallObjectMethod(target, "hidden", "value"));
        assertEquals("default", GXCommonUtils.reflectCallObjectMethod(target, null));
        assertNull(GXCommonUtils.reflectCallObjectMethod(target, "missing"));
        assertNull(GXCommonUtils.reflectCallObjectMethod((Object) null, "missing"));
    }

    @Test
    void shouldReflectCallMethodsConcurrently() throws Exception {
        ReflectionTarget target = new ReflectionTarget();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tasks.add(() -> GXCommonUtils.reflectCallObjectMethod(target, "accept", 7));
            tasks.add(() -> GXCommonUtils.reflectCallObjectMethod(target, "accept", (Object) null));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Object>> futures = executor.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i += 2) {
                assertEquals("int:7", futures.get(i).get());
                assertEquals("text:null", futures.get(i + 1).get());
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCacheReflectionMethodPerClassWithoutGlobalClear() throws Exception {
        Field cacheField = GXCommonUtils.class.getDeclaredField("METHOD_CACHE");
        cacheField.setAccessible(true);
        ClassValue<Map<GXMethodCacheKeyUtils.MethodCacheKey, Method>> cache =
                (ClassValue<Map<GXMethodCacheKeyUtils.MethodCacheKey, Method>>) cacheField.get(null);

        Map<GXMethodCacheKeyUtils.MethodCacheKey, Method> targetCache = cache.get(ReflectionTarget.class);
        targetCache.clear();
        Map<GXMethodCacheKeyUtils.MethodCacheKey, Method> otherCache = cache.get(CustomTreeNode.class);
        otherCache.clear();

        ReflectionTarget target = new ReflectionTarget();

        assertEquals("int:7", GXCommonUtils.reflectCallObjectMethod(target, "accept", 7));
        assertEquals(1, targetCache.size());
        assertEquals(0, otherCache.size());

        assertEquals("int:8", GXCommonUtils.reflectCallObjectMethod(target, "accept", 8));
        assertEquals(1, targetCache.size());
        assertEquals(0, otherCache.size());
    }

    @Test
    void shouldKeepCacheKeyImmutableWhenCallerMutatesParamTypesArray() {
        Class<?>[] paramTypes = {Integer.class};
        GXMethodCacheKeyUtils.MethodCacheKey key =
                GXMethodCacheKeyUtils.getMethodCacheKey(ReflectionTarget.class, "accept", paramTypes);

        paramTypes[0] = String.class;

        assertArrayEquals(new Class<?>[]{Integer.class}, key.paramTypes(),
                "构造器应克隆入参数组，调用方事后修改不得影响键");
        assertEquals(GXMethodCacheKeyUtils.getMethodCacheKey(ReflectionTarget.class, "accept", new Class<?>[]{Integer.class}), key);
        assertNotEquals(GXMethodCacheKeyUtils.getMethodCacheKey(ReflectionTarget.class, "accept", new Class<?>[]{String.class}), key);

        Class<?>[] exposed = key.paramTypes();
        exposed[0] = String.class;
        assertArrayEquals(new Class<?>[]{Integer.class}, key.paramTypes(),
                "访问器应返回克隆，外部修改返回值不得影响键");
    }

    @Test
    void shouldClearPerClassMethodCacheWhenExceedingLimit() throws Exception {
        Field cacheField = GXCommonUtils.class.getDeclaredField("METHOD_CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ClassValue<Map<GXMethodCacheKeyUtils.MethodCacheKey, Method>> cache =
                (ClassValue<Map<GXMethodCacheKeyUtils.MethodCacheKey, Method>>) cacheField.get(null);

        Map<GXMethodCacheKeyUtils.MethodCacheKey, Method> classCache = cache.get(ReflectionTarget.class);
        classCache.clear();

        Method probe = ReflectionTarget.class.getDeclaredMethod("accept", int.class);
        for (int i = 0; i < 513; i++) {
            classCache.put(GXMethodCacheKeyUtils.getMethodCacheKey(ReflectionTarget.class, "dummy" + i, new Class<?>[]{int.class}), probe);
        }
        assertEquals(513, classCache.size());

        // 超过上限（512）后，下一次查找会先清空当前类的槽位，再写入本次结果
        assertEquals("int:7", GXCommonUtils.reflectCallObjectMethod(new ReflectionTarget(), "accept", 7));

        assertEquals(1, classCache.size(), "超限清理后应只保留本次写入的条目");
    }

    @Test
    void shouldCallStaticMethodThroughInstance() {
        ReflectionTarget target = new ReflectionTarget();

        assertEquals("static:5", GXCommonUtils.reflectCallObjectMethod(target, "staticAccept", 5));
    }

    @Test
    void shouldMatchVarArgsAndWideningArguments() {
        ReflectionTarget target = new ReflectionTarget();

        assertEquals("sum:6", GXCommonUtils.reflectCallObjectMethod(target, "sum", 1, 2, 3));
        assertEquals("sum:0", GXCommonUtils.reflectCallObjectMethod(target, "sum"));
        assertEquals("long:7", GXCommonUtils.reflectCallObjectMethod(target, "acceptLong", 7));
    }

    @Test
    void shouldPassArrayArgumentThroughToVarArgsMethod() {
        ReflectionTarget target = new ReflectionTarget();

        assertEquals("sum:6", GXCommonUtils.reflectCallObjectMethod(target, "sum", (Object) new int[]{1, 2, 3}));
        assertEquals("join:a-b", GXCommonUtils.reflectCallObjectMethod(target, "join", (Object) new String[]{"a", "b"}));
    }

    @Test
    void shouldCoerceIntegerArgumentToWrapperParameter() {
        ReflectionTarget target = new ReflectionTarget();

        // Method#invoke 只对"基本类型形参"做拓宽，Long 形参收到 Integer 实参时会抛
        // IllegalArgumentException: argument type mismatch，因此这条路径依赖调用前的实参对齐
        assertEquals("Long:7", GXCommonUtils.reflectCallObjectMethod(target, "acceptLongWrapper", 7));
        assertEquals("long:7", GXCommonUtils.reflectCallObjectMethod(target, "acceptLong", 7));
    }

    @Test
    void shouldCoerceArgumentsForFixedAndVarArgsTogether() {
        ReflectionTarget target = new ReflectionTarget();

        assertEquals("total:6", GXCommonUtils.reflectCallObjectMethod(target, "prefixJoin", "total", 1, 2, 3));
    }

    @Test
    void shouldNotMutateCallerArgumentArrayWhenCoercing() {
        ReflectionTarget target = new ReflectionTarget();
        Object[] params = {7};

        assertEquals("Long:7", GXCommonUtils.reflectCallObjectMethod(target, "acceptLongWrapper", params));

        assertInstanceOf(Integer.class, params[0], "实参对齐不得就地改写调用方传入的数组");
    }

    @Test
    void shouldTreatSingleNullArgumentAsVarArgsArrayWithNullElement() {
        ReflectionTarget target = new ReflectionTarget();

        // javac 会把 join((String) null) 编译成 join(new String[]{null})，这里保持同样语义
        assertEquals("join:null", GXCommonUtils.reflectCallObjectMethod(target, "join", (Object) null));
    }

    @Test
    void shouldPropagateValidationExceptionFromVerifyHook() {
        SourceBean source = new SourceBean();
        source.setName("alpha");

        assertThrows(GXBeanValidateException.class,
                () -> GXCommonUtils.convertSourceToTarget(source, ValidatingBean.class, null, null));
    }

    @Test
    void shouldPropagateBusinessExceptionFromVerifyHook() {
        SourceBean source = new SourceBean();
        source.setName("alpha");

        GXBusinessException exception = assertThrows(GXBusinessException.class,
                () -> GXCommonUtils.convertSourceToTarget(source, BusinessRejectingBean.class, null, null));

        assertEquals("biz rejected", exception.getMessage());
        assertEquals(400, exception.getCode());
    }

    @Test
    void shouldReturnMutableListWhenSourceIsEmpty() {
        List<TargetBean> result = GXCommonUtils.convertSourceListToTargetList(null, TargetBean.class);

        assertTrue(result.isEmpty());
        assertDoesNotThrow(() -> result.add(new TargetBean()));
    }

    @Test
    void shouldBuildTreeWithoutInfiniteRecursionOnDuplicatedIds() {
        TreeNode root = new TreeNode(1L, 0L);
        TreeNode selfLoop = new TreeNode(1L, 1L);

        List<TreeNode> roots = assertDoesNotThrow(
                () -> GXCommonUtils.buildTree(new ArrayList<>(List.of(root, selfLoop)), 0L));

        assertEquals(List.of(root), roots);
        assertEquals(List.of(selfLoop), root.getChildren());
        assertEquals(Collections.emptyList(), selfLoop.getChildren());
    }

    @Test
    void shouldReflectCallSpringBeanByClass() {
        ReflectionTarget target = new ReflectionTarget();

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ReflectionTarget.class)).thenReturn(target);

            assertEquals("int:3", GXCommonUtils.reflectCallObjectMethod(ReflectionTarget.class, "accept", 3));
        }

        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.reflectCallObjectMethod((Class<?>) null, "accept"));
    }

    @Test
    void shouldWrapOrPassThroughReflectionExceptions() {
        ReflectionTarget target = new ReflectionTarget();

        GXBusinessException businessException =
                assertThrows(GXBusinessException.class, () -> GXCommonUtils.reflectCallObjectMethod(target, "failBusiness"));
        assertEquals("boom", businessException.getMsg());

        GXBeanValidateException validateException =
                assertThrows(GXBeanValidateException.class, () -> GXCommonUtils.reflectCallObjectMethod(target, "failValidate"));
        assertEquals("invalid", validateException.getMsg());
    }

    @Test
    void shouldCheckMethodExistsWhenArgumentIsNull() {
        assertTrue(GXCommonUtils.checkMethodExists(NullArgumentTarget.class, "setName", (Object) null));
        assertFalse(GXCommonUtils.checkMethodExists(NullArgumentTarget.class, "setAge", (Object) null));
        assertTrue(GXCommonUtils.checkMethodExists(NullArgumentTarget.class, "setAge", 1));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.checkMethodExists(null, "setAge"));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.checkMethodExists(NullArgumentTarget.class, ""));
    }

    @Test
    void shouldBuildTreeWithDefaultAndCustomParentMethod() {
        TreeNode root = new TreeNode(1L, 0L);
        TreeNode child = new TreeNode(2L, 1L);
        TreeNode grandchild = new TreeNode(3L, 2L);
        TreeNode otherRoot = new TreeNode(4L, 0L);

        List<TreeNode> roots = GXCommonUtils.buildTree(new ArrayList<>(List.of(child, grandchild, root, otherRoot)), 0L);

        assertEquals(List.of(root, otherRoot), roots);
        assertEquals(List.of(child), root.getChildren());
        assertEquals(List.of(grandchild), child.getChildren());
        assertEquals(Collections.emptyList(), GXCommonUtils.buildTree(Collections.emptyList(), 0L));

        CustomTreeNode customRoot = new CustomTreeNode(1L, null);
        CustomTreeNode customChild = new CustomTreeNode(2L, 1L);
        List<CustomTreeNode> customRoots = GXCommonUtils.buildTree(List.of(customChild, customRoot), null, "parentKey");

        assertEquals(List.of(customChild), customRoots.getFirst().getChildren());
    }

    @Test
    void shouldDecodeConnectStringWithoutSecretKey() {
        String originalSecret = System.getProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY);
        try {
            System.clearProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY);

            assertNull(GXCommonUtils.decodeConnectStr("", String.class));
            assertEquals("jdbc:test", GXCommonUtils.decodeConnectStr("jdbc:test", String.class));
            assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.decodeConnectStr("jdbc:test", null));
        } finally {
            if (originalSecret == null) {
                System.clearProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY);
            } else {
                System.setProperty(GXCommonConstant.DATA_SOURCE_SECRET_KEY, originalSecret);
            }
        }
    }

    @Test
    void shouldResolveGenericClassType() {
        assertEquals(String.class, GXCommonUtils.getGenericClassType(StringBox.class, 0));
        assertNull(GXCommonUtils.getGenericClassType(RawBox.class, 0));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.getGenericClassType(null, 0));
        assertThrows(IllegalArgumentException.class, () -> GXCommonUtils.getGenericClassType(StringBox.class, null));
    }

    @Test
    void shouldConvertTableConditionToConditionExpressions() {
        Table<String, String, Object> table = HashBasedTable.create();
        table.put("userName", GXBuilderConstant.STR_EQ, "alpha");
        table.put("age", GXBuilderConstant.EQ, 18);

        List<GXCondition<?>> conditions = GXCommonUtils.convertTableConditionToConditionExp("u", table);

        assertEquals(2, conditions.size());
        assertTrue(conditions.stream().anyMatch(condition -> condition.whereString().startsWith("u.user_name =")));
        assertTrue(conditions.stream().anyMatch(condition -> condition.whereString().startsWith("u.age =")));
        assertEquals(Collections.emptyList(), GXCommonUtils.convertTableConditionToConditionExp("u", null));

        Table<String, String, Object> nullValue = mock(Table.class);
        Map<String, Map<String, Object>> nullValueRows = new HashMap<>();
        Map<String, Object> nullValueOps = new HashMap<>();
        nullValueOps.put(GXBuilderConstant.STR_EQ, null);
        nullValueRows.put("userName", nullValueOps);
        when(nullValue.rowMap()).thenReturn(nullValueRows);
        List<GXCondition<?>> nullValueConditions = GXCommonUtils.convertTableConditionToConditionExp("u", nullValue);
        assertEquals(1, nullValueConditions.size());
        assertNull(nullValueConditions.getFirst().getValue());

        Table<String, String, Object> invalid = HashBasedTable.create();
        invalid.put("age", "unknown", 18);
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.convertTableConditionToConditionExp("u", invalid));
    }

    @Test
    void shouldConvertStringToTargetForMapJsonAndScalarInputs() {
        Map<?, ?> map = GXCommonUtils.convertStrToTarget("{name=alpha, age=18}", Map.class);
        JsonBean bean = GXCommonUtils.convertStrToTarget("{\"name\":\"beta\"}", JsonBean.class);

        assertEquals("alpha", map.get("name"));
        assertEquals("18", map.get("age"));
        assertEquals("beta", bean.getName());
        assertEquals(123, GXCommonUtils.convertStrToTarget("123", Integer.class));
        assertNull(GXCommonUtils.convertStrToTarget("", JsonBean.class));
        assertNull(GXCommonUtils.convertStrToTarget("abc", Integer.class));
        assertNull(GXCommonUtils.convertStrToTarget("123", null));
    }

    @Test
    void shouldPreserveCommaAndNestedBracesInMapStyleString() {
        Map<?, ?> withQuotedComma = GXCommonUtils.convertStrToTarget("{name=\"a,b\"}", Map.class);
        Map<?, ?> withNested = GXCommonUtils.convertStrToTarget("{data={\"x\":1}}", Map.class);
        Map<?, ?> multiple = GXCommonUtils.convertStrToTarget("{name=alpha, age=18}", Map.class);

        assertEquals("a,b", withQuotedComma.get("name"));
        assertEquals("{\"x\":1}", withNested.get("data"));
        assertEquals("alpha", multiple.get("name"));
        assertEquals("18", multiple.get("age"));
    }

    @Test
    void shouldDetectBase64Input() {
        assertTrue(GXCommonUtils.isBase64("YWxwaGE="));
        assertFalse(GXCommonUtils.isBase64("not base64"));
        assertFalse(GXCommonUtils.isBase64("not_base64!"));
        assertFalse(GXCommonUtils.isBase64(""));
    }

    @Test
    void shouldGenerateAndCheckHmac() {
        Dict payload = Dict.create().set("name", "alpha");

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());

            String hmac = GXCommonUtils.generateHmac(payload, "secret");

            assertTrue(GXCommonUtils.checkHmac("secret", hmac, payload));
            assertFalse(GXCommonUtils.checkHmac("secret", "bad", payload));
            assertFalse(GXCommonUtils.checkHmac("secret", "", payload));
        }
    }

    @Test
    void shouldValidateHmacArgumentsAndMissingObjectMapper() {
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.generateHmac(null, "secret"));
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.generateHmac(Dict.create(), ""));
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.checkHmac("", "client", Dict.create()));
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.checkHmac("secret", "client", null));

        try (MockedStatic<GXSpringContextUtils> springContextUtils = Mockito.mockStatic(GXSpringContextUtils.class)) {
            springContextUtils.when(() -> GXSpringContextUtils.getBean(ObjectMapper.class)).thenReturn(null);

            assertThrows(GXBusinessException.class, () -> GXCommonUtils.generateHmac(Dict.create().set("name", "alpha"), "secret"));
            assertFalse(GXCommonUtils.checkHmac("secret", "client", Dict.create().set("name", "alpha")));
        }
    }

    @Test
    void shouldConvertUpdateFieldRequests() {
        List<GXUpdateField<?>> updateFields = GXCommonUtils.convertUpdateFieldRequestLst(List.of(
                new GXUpdateFieldRequest("u", "userName", GXUpdateStrField.class.getName(), "alpha")
        ));

        assertEquals(1, updateFields.size());
        GXUpdateField<?> updateField = updateFields.getFirst();
        assertInstanceOf(GXUpdateStrField.class, updateField);
        assertEquals("user_name", updateField.getFieldName());
        assertEquals("alpha", updateField.getFieldValue());
        assertTrue(updateField.updateString().startsWith("u.user_name ="));
        assertEquals(Collections.emptyList(), GXCommonUtils.convertUpdateFieldRequestLst(Collections.emptyList()));
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.convertUpdateFieldRequestLst(List.of(
                new GXUpdateFieldRequest("u", "userName", "", "alpha")
        )));
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.convertUpdateFieldRequestLst(List.of(
                new GXUpdateFieldRequest("u", "userName", "missing.ClassName", "alpha")
        )));
        List<GXUpdateFieldRequest> requests = new ArrayList<>();
        requests.add(null);
        assertThrows(GXBusinessException.class, () -> GXCommonUtils.convertUpdateFieldRequestLst(requests));
    }

    @Test
    void shouldRejectNonUpdateFieldClassWithoutInstantiation() {
        NonUpdateFieldTarget.INSTANTIATED.set(false);

        assertThrows(GXBusinessException.class, () -> GXCommonUtils.convertUpdateFieldRequestLst(List.of(
                new GXUpdateFieldRequest("u", "userName", NonUpdateFieldTarget.class.getName(), "alpha")
        )));
        assertFalse(NonUpdateFieldTarget.INSTANTIATED.get());
    }

    @Test
    void shouldReturnMinusOneWhenUrlIsEmpty() {
        assertEquals(-1, GXCommonUtils.checkURLReachable(""));
    }

    @Test
    void shouldReturnNormalizedSystemLoadAverage() {
        double loadAverage = assertDoesNotThrow(GXCommonUtils::getSystemLoadAverage);

        assertTrue(loadAverage >= -1.0D && loadAverage <= 1.0D);
    }

    static class NonUpdateFieldTarget {
        static final AtomicBoolean INSTANTIATED = new AtomicBoolean(false);

        public NonUpdateFieldTarget(String tableName, String fieldName, Object value) {
            INSTANTIATED.set(true);
        }
    }

    static class NullArgumentTarget {
        @SuppressWarnings("unused")
        public void setName(String name) {
        }

        @SuppressWarnings("unused")
        public void setAge(int age) {
        }
    }

    public static class JsonBean {
        private String name;

        public JsonBean() {
        }

        JsonBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class SourceBean {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class TargetBean {
        private String name;
        private boolean verified;

        public void customizeProcess(Dict extraData) {
            if (extraData.containsKey("suffix")) {
                this.name = this.name + extraData.getStr("suffix");
            }
        }

        public void verify() {
            this.verified = true;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isVerified() {
            return verified;
        }
    }

    static class ReflectionTarget {
        @SuppressWarnings("unused")
        public String accept(int value) {
            return "int:" + value;
        }

        @SuppressWarnings("unused")
        public String accept(String value) {
            return "text:" + value;
        }

        @SuppressWarnings("unused")
        public String customizeProcess() {
            return "default";
        }

        @SuppressWarnings("unused")
        private String hidden(String value) {
            return "hidden:" + value;
        }

        @SuppressWarnings("unused")
        public void failBusiness() {
            throw new IllegalStateException("boom");
        }

        @SuppressWarnings("unused")
        public void failValidate() {
            throw new GXBeanValidateException("invalid");
        }

        @SuppressWarnings("unused")
        public static String staticAccept(int value) {
            return "static:" + value;
        }

        @SuppressWarnings("unused")
        public String sum(int... values) {
            int total = 0;
            for (int value : values) {
                total += value;
            }
            return "sum:" + total;
        }

        @SuppressWarnings("unused")
        public String acceptLong(long value) {
            return "long:" + value;
        }

        @SuppressWarnings("unused")
        public String acceptLongWrapper(Long value) {
            return "Long:" + value;
        }

        @SuppressWarnings("unused")
        public String prefixJoin(String prefix, int... values) {
            int total = 0;
            for (int value : values) {
                total += value;
            }
            return prefix + ":" + total;
        }

        @SuppressWarnings("unused")
        public String join(String... values) {
            return "join:" + String.join("-", values);
        }
    }

    public static class BusinessRejectingBean {
        private String name;

        public void verify() {
            throw new GXBusinessException("biz rejected", 400);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class ValidatingBean {
        private String name;

        public void verify() {
            throw new GXBeanValidateException("name is invalid", 400);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class TreeNode {
        private final Long id;
        private final Long parentId;
        private List<TreeNode> children = Collections.emptyList();

        TreeNode(Long id, Long parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        public Long getId() {
            return id;
        }

        public Long getParentId() {
            return parentId;
        }

        public List<TreeNode> getChildren() {
            return children;
        }

        public void setChildren(List<TreeNode> children) {
            this.children = children;
        }
    }

    static class CustomTreeNode {
        private final Long id;
        private final Long parentKey;
        private List<CustomTreeNode> children = Collections.emptyList();

        CustomTreeNode(Long id, Long parentKey) {
            this.id = id;
            this.parentKey = parentKey;
        }

        public Long getId() {
            return id;
        }

        @SuppressWarnings("unused")
        public Long parentKey() {
            return parentKey;
        }

        public List<CustomTreeNode> getChildren() {
            return children;
        }

        public void setChildren(List<CustomTreeNode> children) {
            this.children = children;
        }
    }

    static class Box<T> {
    }

    static class StringBox extends Box<String> {
    }

    @SuppressWarnings("rawtypes")
    static class RawBox extends Box {
    }
}
