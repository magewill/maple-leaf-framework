package cn.maple.rabbitmq.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.json.JSONUtil;
import cn.maple.rabbitmq.support.GXRabbitMQJsonSupport;
import cn.maple.rabbitmq.support.GXTestRabbitFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;

/**
 * 锁定出站 wire format 与消费端兼容性。
 *
 * <p>出站仍使用 Hutool：{@code java.util.Date}/{@code LocalDateTime} 写毫秒时间戳，
 * 与修复前完全一致；消费端 {@code JacksonJsonMessageConverter} 必须能读取该格式。</p>
 */
@ExtendWith(MockitoExtension.class)
class GXMessageSerializationTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitAdmin rabbitAdmin;

    private GXSendRabbitMQServiceImpl service;

    @BeforeEach
    void setUp() {
        service = GXTestRabbitFixtures.newService(rabbitTemplate, rabbitAdmin);
    }

    @AfterEach
    void tearDown() {
        service.shutdownAsyncExecutor();
    }

    @Test
    void shouldKeepHutoolWireFormat() {
        Dict payload = Dict.create()
                .set("text", "plain")
                .set("number", 42)
                .set("nested", Dict.create().set("deep", true))
                .set("list", List.of(1, 2, 3))
                .set("date", DateUtil.parse("2026-10-25 10:00:00"))
                .set("time", java.time.LocalDateTime.of(2026, 10, 25, 10, 0, 0));

        service.sendNormalMessage(requestWith(payload));

        assertThat(new String(captureSentMessage().getBody(), StandardCharsets.UTF_8))
                .isEqualTo(JSONUtil.toJsonStr(payload));
    }

    @Test
    void shouldOmitNullValues() {
        Dict payload = Dict.create().set("present", "value").set("missing", null);

        assertThat(JSONUtil.toJsonStr(payload)).contains("present").doesNotContain("missing");
    }

    @Test
    void shouldWriteTypeIdHeader() {
        service.sendNormalMessage(GXTestRabbitFixtures.newMessageRequest());

        assertThat((Object) captureSentMessage().getMessageProperties().getHeader("__TypeId__"))
                .isEqualTo("cn.hutool.core.lang.Dict");
    }

    @Test
    void shouldKeepUnicodeContent() {
        Dict payload = Dict.create().set("message", "订单创建成功");

        service.sendNormalMessage(requestWith(payload));

        assertThat(new String(captureSentMessage().getBody(), StandardCharsets.UTF_8))
                .contains("订单创建成功");
    }

    @Test
    void shouldDeserializeHutoolPayloadWithJacksonConverter() {
        org.springframework.amqp.support.converter.DefaultClassMapper classMapper =
                new org.springframework.amqp.support.converter.DefaultClassMapper();
        classMapper.setTrustedPackages("cn.hutool.core.lang", "cn.hutool.core", "cn.maple");
        org.springframework.amqp.support.converter.JacksonJsonMessageConverter converter =
                new org.springframework.amqp.support.converter.JacksonJsonMessageConverter(GXRabbitMQJsonSupport.jsonMapper());
        converter.setClassMapper(classMapper);

        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader(GXRabbitMQJsonSupport.TYPE_ID_HEADER, GXRabbitMQJsonSupport.DICT_TYPE_ID);
        Message message = new Message("{\"orderId\":1,\"date\":1792893600000}".getBytes(StandardCharsets.UTF_8), properties);

        Object converted = converter.fromMessage(message);

        assertThat(converted).isInstanceOf(Dict.class);
        assertThat(((Dict) converted).getInt("orderId")).isEqualTo(1);
    }

    private static cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto requestWith(Dict payload) {
        return cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto.builder()
                .exchange("test.exchange")
                .routingKey("test.key")
                .data(payload)
                .messageProperties(new MessageProperties())
                .build();
    }

    private Message captureSentMessage() {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture(), nullable(CorrelationData.class));
        return captor.getValue();
    }
}
