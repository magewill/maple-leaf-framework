package cn.maple.canal.benchmark;

import cn.hutool.core.lang.Dict;
import cn.maple.canal.dto.GXCanalDataDto;
import cn.maple.canal.dto.inner.GXCanalParseResultInnerDto;
import cn.maple.canal.enums.GXCanalOutcomeEnums;
import cn.maple.canal.metrics.GXCanalConsumeStatistics;
import cn.maple.canal.properties.GXCanalProperties;
import cn.maple.canal.service.GXProcessCanalDataService;
import cn.maple.canal.service.impl.GXCanalMessageParseServiceImpl;
import cn.maple.canal.service.support.GXCanalHandlerResolver;
import cn.maple.canal.service.support.GXCanalLogThrottle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.support.GenericApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Micro benchmarks for the hot path.
 *
 * <p>The framework side must not touch the Spring container per message: handler resolution is a
 * snapshot lookup plus a bounded cache. The second test measures the lookup layer against the legacy
 * behaviour (a failing {@code getBean} call driven by an exception) to quantify the improvement. The
 * thresholds are deliberately generous so shared build agents stay stable.</p>
 */
class GXCanalParseBenchmarkTest {
    private static final int MESSAGE_COUNT = 50_000;

    private static final String PAYLOAD = "{\"database\":\"db\",\"table\":\"user\",\"type\":\"INSERT\","
            + "\"pkNames\":[\"id\"],\"data\":[{\"id\":\"1\",\"name\":\"alice\"}]}";

    private static final String SERVICE_BEAN_NAME = "dbUserService";

    @Test
    void shouldStayWithinHotPathBudget() {
        GenericApplicationContext context = contextWithHandler();
        try {
            GXCanalHandlerResolver resolver = new GXCanalHandlerResolver(context, new GXCanalProperties(),
                    new GXCanalConsumeStatistics());
            resolver.afterSingletonsInstantiated();
            GXCanalMessageParseServiceImpl service =
                    new GXCanalMessageParseServiceImpl(resolver, new GXCanalLogThrottle());
            service.parse(PAYLOAD);

            long startNanos = System.nanoTime();
            for (int index = 0; index < MESSAGE_COUNT; index++) {
                List<GXCanalParseResultInnerDto> results = service.parse(PAYLOAD);
                assertEquals(GXCanalOutcomeEnums.SUCCESS, results.get(0).outcome());
            }
            double averageMicros = (double) (System.nanoTime() - startNanos) / MESSAGE_COUNT / 1_000d;

            System.out.printf("canal hot path benchmark: messages=%d, averageMicros=%.2f%n",
                    MESSAGE_COUNT, averageMicros);
            assertTrue(averageMicros < 2_000d, "average parse cost must stay far below 2ms per message");
        } finally {
            context.close();
        }
    }

    @Test
    void shouldReduceLookupCostByAtLeastEightyPercent() {
        GenericApplicationContext context = contextWithHandler();
        try {
            GXCanalHandlerResolver resolver = new GXCanalHandlerResolver(context, new GXCanalProperties(),
                    new GXCanalConsumeStatistics());
            resolver.afterSingletonsInstantiated();
            resolver.resolve("db", "user");

            long legacyStart = System.nanoTime();
            for (int index = 0; index < MESSAGE_COUNT; index++) {
                resolveLegacyStyle(context);
            }
            double legacyMicros = (double) (System.nanoTime() - legacyStart) / MESSAGE_COUNT / 1_000d;

            long currentStart = System.nanoTime();
            for (int index = 0; index < MESSAGE_COUNT; index++) {
                resolver.resolve("db", "user");
            }
            double currentMicros = (double) (System.nanoTime() - currentStart) / MESSAGE_COUNT / 1_000d;

            double improvement = 1d - (currentMicros / legacyMicros);
            System.out.printf("canal lookup baseline: legacyMicros=%.3f, currentMicros=%.3f, improvement=%.1f%%%n",
                    legacyMicros, currentMicros, improvement * 100d);
            assertTrue(improvement >= 0.8d,
                    "snapshot lookup must be at least 80% cheaper than the legacy getBean + exception path");
        } finally {
            context.close();
        }
    }

    private void resolveLegacyStyle(GenericApplicationContext context) {
        try {
            context.getBean(SERVICE_BEAN_NAME);
        } catch (NoSuchBeanDefinitionException e) {
            // Legacy control flow: a miss was signalled by an exception per message.
            return;
        }
        throw new IllegalStateException("unexpected bean: " + SERVICE_BEAN_NAME);
    }

    private GenericApplicationContext contextWithHandler() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean("defaultProcessCanalDataService", GXProcessCanalDataService.class, NoopHandler::new);
        context.refresh();
        return context;
    }

    private static final class NoopHandler implements GXProcessCanalDataService {
        @Override
        public Dict processUpdate(GXCanalDataDto canalData, Dict param) {
            return Dict.create();
        }

        @Override
        public Dict processInsert(GXCanalDataDto canalData, Dict param) {
            return Dict.create().set("status", "success");
        }

        @Override
        public Dict processDelete(GXCanalDataDto canalData, Dict param) {
            return Dict.create();
        }
    }
}
