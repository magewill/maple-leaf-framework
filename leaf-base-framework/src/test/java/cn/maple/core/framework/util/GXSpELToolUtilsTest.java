package cn.maple.core.framework.util;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.config.aware.GXApplicationContextSingleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GXSpELToolUtilsTest {
    private ApplicationContext originalContext;
    private GenericApplicationContext testContext;

    @BeforeEach
    void setUp() throws Exception {
        originalContext = GXSpringContextUtils.getApplicationContext();
        testContext = new GenericApplicationContext();
        testContext.getBeanFactory().registerSingleton("sampleBean", new SampleBean());
        testContext.refresh();
        replaceApplicationContext(testContext);
        GXSpELToolUtils.clearExpressionCache();
    }

    @AfterEach
    void tearDown() throws Exception {
        replaceApplicationContext(originalContext);
        if (testContext != null) {
            testContext.close();
        }
        GXSpELToolUtils.clearExpressionCache();
    }

    @Test
    void calculateDictExpressionUsesDefaultDataVariable() {
        Dict data = Dict.create().set("amount", 7);

        Integer result = GXSpELToolUtils.calculateSpELExpression(data, "#data['amount'] + 5", Integer.class);

        assertEquals(12, result);
    }

    @Test
    void calculateDictExpressionUsesCustomDataVariable() {
        Dict data = Dict.create().set("name", "maple");

        String result = GXSpELToolUtils.calculateSpELExpression(data, "#payload['name'].toUpperCase()", String.class, "payload");

        assertEquals("MAPLE", result);
    }

    @Test
    void calculateTargetExpressionUsesRootObject() {
        SampleTarget target = new SampleTarget();
        target.name = "leaf";

        String result = GXSpELToolUtils.calculateSpELExpression(target, "name + '-framework'", String.class);

        assertEquals("leaf-framework", result);
    }

    @Test
    void calculateExpressionReturnsDefaultValueWhenInputInvalid() {
        String result = GXSpELToolUtils.calculateSpELExpression((Dict) null, "#data['name']", String.class);

        assertEquals("", result);
    }

    @Test
    void assignmentExpressionSetsMultipleObjectProperties() {
        SampleTarget target = new SampleTarget();
        Dict assignments = Dict.create()
                .set("name", "assigned")
                .set("nested.value", 42);

        Integer result = GXSpELToolUtils.assignmentSpELExpression(target, assignments, "nested.value", Integer.class);

        assertEquals(42, result);
        assertEquals("assigned", target.name);
        assertEquals(42, target.nested.value);
    }

    @Test
    void registerFunctionInvokesStaticMethodWithArguments() {
        String result = GXSpELToolUtils.registerFunctionSpELExpression(
                SampleFunctions.class,
                "staticJoin",
                String.class,
                new Class[]{String.class, String.class},
                "a'b",
                "c"
        );

        assertEquals("a'b:c", result);
    }

    @Test
    void registerFunctionSupportsNoArgMethod() {
        String result = GXSpELToolUtils.registerFunctionSpELExpression(
                SampleFunctions.class,
                "staticNoArg",
                String.class,
                null
        );

        assertEquals("static-ok", result);
    }

    @Test
    void callBeanMethodUsesSpringContextBean() {
        String result = GXSpELToolUtils.callBeanMethodSpELExpression(
                SampleBean.class,
                "join",
                String.class,
                new Class[]{String.class, Integer.class},
                "bean",
                3
        );

        assertEquals("bean:3", result);
    }

    @Test
    void callTargetObjectMethodSupportsNoArgMethods() {
        SampleTarget target = new SampleTarget();

        String result = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
                target,
                "noArg",
                String.class,
                new Class[0]
        );

        assertEquals("ok", result);
    }

    @Test
    void callTargetObjectMethodBindsArgumentsAsVariables() {
        SampleTarget target = new SampleTarget();

        String result = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
                target,
                "join",
                String.class,
                new Class[]{String.class, String.class},
                "a'b",
                "c"
        );

        assertEquals("a'b:c", result);
    }

    @Test
    void callTargetObjectMethodReturnsNullWhenMethodMissing() {
        SampleTarget target = new SampleTarget();

        String result = GXSpELToolUtils.callTargetObjectMethodSpELExpression(
                target,
                "missing",
                String.class,
                new Class[0]
        );

        assertNull(result);
    }

    @Test
    void setObjectValueReturnsOldValueAndUpdatesTarget() {
        SampleTarget target = new SampleTarget();
        target.name = "old";

        String oldValue = GXSpELToolUtils.setValueBySpELExpression(target, "name", String.class, "new");

        assertEquals("old", oldValue);
        assertEquals("new", target.name);
    }

    @Test
    void setDictValueReturnsOldValueAndUpdatesDict() {
        Dict dict = Dict.create().set("name", "old");

        String oldValue = GXSpELToolUtils.setValueBySpELExpression(dict, "#data['name']", String.class, "new");

        assertEquals("old", oldValue);
        assertEquals("new", dict.getStr("name"));
    }

    @Test
    void contextBuilderAddsVariablesAndValidatesBlankName() {
        EvaluationContext context = GXSpELToolUtils.contextBuilder()
                .addVariable("one", 1)
                .addVariables(Map.of("two", 2))
                .build();

        assertEquals(1, context.lookupVariable("one"));
        assertEquals(2, context.lookupVariable("two"));
        assertThrows(IllegalArgumentException.class, () -> GXSpELToolUtils.contextBuilder().addVariable(" ", 1));
    }

    @Test
    void contextBuilderRegistersFunction() {
        EvaluationContext context = GXSpELToolUtils.contextBuilder()
                .addVariable("left", "x")
                .addVariable("right", "y")
                .registerFunction("join", SampleFunctions.class, "staticJoin", new Class[]{String.class, String.class})
                .build();

        String result = new SpelExpressionParser()
                .parseExpression("#join(#left, #right)")
                .getValue(context, String.class);

        assertEquals("x:y", result);
    }

    private static void replaceApplicationContext(ApplicationContext applicationContext) throws Exception {
        Field field = GXApplicationContextSingleton.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(GXApplicationContextSingleton.INSTANCE, applicationContext);
    }

    static class SampleTarget {
        public String name;
        public NestedTarget nested = new NestedTarget();

        public String noArg() {
            return "ok";
        }

        public String join(String left, String right) {
            return left + ":" + right;
        }
    }

    static class NestedTarget {
        public int value;
    }

    static class SampleBean {
        public String join(String left, Integer right) {
            return left + ":" + right;
        }
    }

    static class SampleFunctions {
        public static String staticNoArg() {
            return "static-ok";
        }

        public static String staticJoin(String left, String right) {
            return left + ":" + right;
        }
    }
}
