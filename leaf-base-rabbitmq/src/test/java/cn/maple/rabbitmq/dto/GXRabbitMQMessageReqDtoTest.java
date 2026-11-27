package cn.maple.rabbitmq.dto;

import cn.hutool.core.lang.Dict;
import cn.maple.rabbitmq.dto.inner.GXCorrelationData;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GXRabbitMQMessageReqDtoTest {
    @Test
    @SuppressWarnings("deprecation")
    void shouldBuildWithAllFields() {
        GXCorrelationData correlationData = new GXCorrelationData("id-1", "payload");
        GXRabbitMQMessageReqDto dto = GXRabbitMQMessageReqDto.builder()
                .exchange("order.exchange")
                .routingKey("order.created")
                .data(Dict.create().set("orderId", 1))
                .tag("legacy-tag")
                .correlationData(correlationData)
                .messageProperties(new MessageProperties())
                .build();

        assertThat(dto.getExchange()).isEqualTo("order.exchange");
        assertThat(dto.getRoutingKey()).isEqualTo("order.created");
        assertThat(dto.getCorrelationData()).isSameAs(correlationData);
        assertThat(dto.getMessageProperties()).isNotNull();
    }

    @Test
    void shouldSupportNoArgsConstructor() {
        GXRabbitMQMessageReqDto dto = new GXRabbitMQMessageReqDto();
        dto.setExchange("e");
        dto.setRoutingKey("r");
        dto.setData(Dict.create());

        assertThat(dto.getExchange()).isEqualTo("e");
        assertThat(dto.getData()).isNotNull();
    }

    @Test
    void shouldAllowNullCorrelationData() {
        GXRabbitMQMessageReqDto dto = GXRabbitMQMessageReqDto.builder()
                .exchange("e")
                .routingKey("r")
                .data(Dict.create())
                .messageProperties(new MessageProperties())
                .build();

        assertThat(dto.getCorrelationData()).isNull();
    }

    @Test
    void shouldExposeBuilderDefaults() {
        GXRabbitMQMessageReqDto dto = GXRabbitMQMessageReqDto.builder().exchange("e").routingKey("r").build();

        assertThat(dto.getExchange()).isEqualTo("e");
        assertThat(dto.getRoutingKey()).isEqualTo("r");
        assertThat(dto.getData()).isNull();
        assertThat(dto.getCorrelationData()).isNull();
    }

    @Test
    void shouldExposeSettlementResult() {
        GXCorrelationData correlationData = new GXCorrelationData("id-1");

        assertThat(correlationData.await(Duration.ofMillis(10))).isFalse();

        correlationData.settlement().complete(true);

        assertThat(correlationData.await(Duration.ofMillis(10))).isTrue();
        assertThat(correlationData.getId()).isEqualTo("id-1");
    }
}
