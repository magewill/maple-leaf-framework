package cn.maple.rocketmq.service.impl;

import cn.maple.rocketmq.dispatcher.impl.GXCdcMessageDispatcherImpl;
import cn.maple.rocketmq.dto.inner.GXCdcEvent;
import cn.maple.rocketmq.listener.GXCdcEventListener;
import cn.maple.rocketmq.dispatcher.GXCdcMessageDispatcher;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(
        classes = GXCdcMessageDispatcherImplTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.config.import-check.enabled=false",
                "maple.framework.cdc.enable=true",
                "cdc.active.status=enabled",
                "logging.level.cn.maple.rocketmq.dispatcher.impl.GXCdcMessageDispatcherImpl=OFF"
        }
)
class GXCdcMessageDispatcherImplTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestApplication.class)
            .withPropertyValues("spring.cloud.nacos.config.import-check.enabled=false");

    @Resource
    private GXCdcMessageDispatcher cdcMessageDispatcher;

    @Resource
    private RecordingOrderCdcListener orderListener;

    @Resource
    private RecordingInventoryCdcListener inventoryListener;

    @BeforeEach
    void setUp() {
        orderListener.events.clear();
        inventoryListener.events.clear();
    }

    @Test
    void dispatchesCreateEventToSupportedListener() {
        cdcMessageDispatcher.dispatch("""
                {"op":"c","before":null,"after":{"id":1},"source":{"db":"shop","table":"orders"},"ts_ms":1710000000000}
                """, "order");

        assertThat(orderListener.events).containsExactly("create:order:1:1710000000000");
        assertThat(inventoryListener.events).isEmpty();
    }

    @Test
    void dispatchesSnakeCaseTimestampAndTransactionObject() {
        cdcMessageDispatcher.dispatch("""
                {"op":"u","before":{"id":2},"after":{"id":2},"transaction":{"id":"tx-1"},"ts_ms":1710000000001}
                """, "order");

        assertThat(orderListener.events).containsExactly("update:tx-1:1710000000001");
    }

    @Test
    void ignoresEventWhenListenerRequestsIgnore() {
        cdcMessageDispatcher.dispatch("""
                {"op":"u","before":{"id":3,"updated_at":"old"},"after":{"id":3,"updated_at":"new"}}
                """, "order");

        assertThat(orderListener.events).isEmpty();
    }

    @Test
    void unsupportedOperationDoesNotBlockMessageConsumption() {
        assertThatCode(() -> cdcMessageDispatcher.dispatch("{\"op\":\"x\"}", "order"))
                .doesNotThrowAnyException();

        assertThat(orderListener.events).isEmpty();
    }

    @Test
    void invalidPayloadDoesNotBlockMessageConsumption() {
        assertThatCode(() -> cdcMessageDispatcher.dispatch("not-json", "order"))
                .doesNotThrowAnyException();
        assertThatCode(() -> cdcMessageDispatcher.dispatch(" ", "order"))
                .doesNotThrowAnyException();
        assertThatCode(() -> cdcMessageDispatcher.dispatch(null, "order"))
                .doesNotThrowAnyException();

        assertThat(orderListener.events).isEmpty();
    }

    @Test
    void disabledCdcSwitchSkipsDispatching() {
        contextRunner
                .withPropertyValues("maple.framework.cdc.enable=false")
                .run(context -> assertThat(context).doesNotHaveBean(GXCdcMessageDispatcher.class));
    }

    @Test
    void missingCdcSwitchSkipsDispatchingByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(GXCdcMessageDispatcher.class));
    }

    @Test
    void disabledBusinessCdcStatusSkipsDispatching() {
        contextRunner
                .withPropertyValues(
                        "maple.framework.cdc.enable=true",
                        "cdc.active.status=disabled")
                .run(context -> {
                    GXCdcMessageDispatcher dispatcher = context.getBean(GXCdcMessageDispatcher.class);
                    RecordingOrderCdcListener listener = context.getBean(RecordingOrderCdcListener.class);

                    dispatcher.dispatch("{\"op\":\"c\",\"after\":{\"id\":1}}", "order");

                    assertThat(listener.events).isEmpty();
                });
    }

    @Test
    void missingBusinessCdcStatusSkipsDispatchingByDefault() {
        contextRunner
                .withPropertyValues("maple.framework.cdc.enable=true")
                .run(context -> {
                    GXCdcMessageDispatcher dispatcher = context.getBean(GXCdcMessageDispatcher.class);
                    RecordingOrderCdcListener listener = context.getBean(RecordingOrderCdcListener.class);

                    dispatcher.dispatch("{\"op\":\"c\",\"after\":{\"id\":1}}", "order");

                    assertThat(listener.events).isEmpty();
                });
    }

    @Test
    void businessCdcStatusIgnoresCaseAndWhitespace() {
        contextRunner
                .withPropertyValues(
                        "maple.framework.cdc.enable=true",
                        "cdc.active.status= ENABLED ")
                .run(context -> {
                    GXCdcMessageDispatcher dispatcher = context.getBean(GXCdcMessageDispatcher.class);
                    RecordingOrderCdcListener listener = context.getBean(RecordingOrderCdcListener.class);

                    dispatcher.dispatch("{\"op\":\"c\",\"after\":{\"id\":1}}", "order");

                    assertThat(listener.events).containsExactly("create:order:1:");
                });
    }

    @SpringBootConfiguration
    @Import(GXCdcMessageDispatcherImpl.class)
    static class TestApplication {
        @Bean
        RecordingOrderCdcListener orderListener() {
            return new RecordingOrderCdcListener();
        }

        @Bean
        RecordingInventoryCdcListener inventoryListener() {
            return new RecordingInventoryCdcListener();
        }
    }

    static class RecordingOrderCdcListener implements GXCdcEventListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public boolean supports(GXCdcEvent event) {
            return "order".equals(event.getBizType());
        }

        @Override
        public boolean shouldIgnore(GXCdcEvent event) {
            return event.isOnlyChanged("updated_at");
        }

        @Override
        public void onCreate(GXCdcEvent event) {
            events.add("create:" + event.getBizType() + ":" + event.getAfter().getStr("id") + ":" + event.getTsMs());
        }

        @Override
        public void onUpdate(GXCdcEvent event) {
            events.add("update:" + event.getTransaction().getStr("id") + ":" + event.getTsMs());
        }
    }

    static class RecordingInventoryCdcListener implements GXCdcEventListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public boolean supports(GXCdcEvent event) {
            return "inventory".equals(event.getBizType());
        }

        @Override
        public void onCreate(GXCdcEvent event) {
            events.add("create");
        }
    }
}
