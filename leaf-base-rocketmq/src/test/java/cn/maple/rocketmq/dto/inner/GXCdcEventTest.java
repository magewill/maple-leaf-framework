package cn.maple.rocketmq.dto.inner;

import cn.hutool.core.lang.Dict;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GXCdcEventTest {
    @Test
    void exposesNoSetters() {
        for (Method method : GXCdcEvent.class.getMethods()) {
            assertThat(method.getName()).as("setter found: %s", method.getName()).doesNotStartWith("set");
        }
    }

    @Test
    void copiesPayloadMapsDefensively() {
        Dict after = Dict.create().set("id", 1);
        Dict payload = Dict.create().set("op", "c").set("after", after);

        GXCdcEvent event = GXCdcEvent.fromPayload(payload, "order", "{}");
        after.set("id", 99);

        assertThat(event.getAfter().getInt("id")).isEqualTo(1);
        assertThat(event.getOperation()).isEqualTo(GXCdcOperation.CREATE);
    }

    @Test
    void detectsSingleChangedField() {
        Dict before = Dict.create().set("id", 1).set("updated_at", "old");
        Dict after = Dict.create().set("id", 1).set("updated_at", "new");
        GXCdcEvent event = GXCdcEvent.fromPayload(Dict.create().set("op", "u").set("before", before).set("after", after),
                "order", "{}");

        assertThat(event.isOnlyChanged("updated_at")).isTrue();
        assertThat(event.isOnlyChanged("id")).isFalse();
        assertThat(event.isOnlyChanged(null)).isFalse();
    }

    @Test
    void resolvesSnakeCaseAndCamelCaseTimestamps() {
        GXCdcEvent snake = GXCdcEvent.fromPayload(Dict.create().set("op", "c").set("ts_ms", 1710000000000L), "order", "{}");
        GXCdcEvent camel = GXCdcEvent.fromPayload(Dict.create().set("op", "c").set("tsMs", 1710000000001L), "order", "{}");

        assertThat(snake.getTsMs()).isEqualTo("1710000000000");
        assertThat(camel.getTsMs()).isEqualTo("1710000000001");
    }

    @Test
    void returnsNullOperationForUnknownCode() {
        GXCdcEvent event = GXCdcEvent.fromPayload(Dict.create().set("op", "x"), "order", "{}");

        assertThat(event.getOperation()).isNull();
        assertThat(event.getBefore()).isEmpty();
        assertThat(event.getAfter()).isEmpty();
        assertThat(event.getSource()).isEmpty();
        assertThat(event.getTransaction()).isEmpty();
    }
}
