package cn.maple.rabbitmq.support;

import cn.hutool.core.lang.Dict;
import cn.maple.rabbitmq.dto.inner.GXRabbitMQMessageReqDto;
import cn.maple.rabbitmq.service.impl.GXSendRabbitMQServiceImpl;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 测试夹具：构造已注入配置字段的服务实例，避免每个用例重复反射。
 */
public final class GXTestRabbitFixtures {
    private GXTestRabbitFixtures() {
    }

    public static GXSendRabbitMQServiceImpl newService(GXAmqpMessagePublisher publisher,
                                                       @Nullable GXAmqpManagementClient managementClient) {
        return newService(publisher, managementClient, 1, 1, 16);
    }

    public static GXSendRabbitMQServiceImpl newService(GXAmqpMessagePublisher publisher,
                                                       @Nullable GXAmqpManagementClient managementClient,
                                                       int coreSize, int maxSize, int queueCapacity) {
        GXSendRabbitMQServiceImpl service = new GXSendRabbitMQServiceImpl(publisher, managementClient);
        ReflectionTestUtils.setField(service, "confirmTimeoutMillis", 2_000L);
        ReflectionTestUtils.setField(service, "threadPoolCoreSize", coreSize);
        ReflectionTestUtils.setField(service, "threadPoolMaxSize", maxSize);
        ReflectionTestUtils.setField(service, "threadPoolQueueCapacity", queueCapacity);
        ReflectionTestUtils.setField(service, "applicationName", "test-app");
        return service;
    }

    public static GXRabbitMQMessageReqDto newMessageRequest() {
        return GXRabbitMQMessageReqDto.builder()
                .exchange("test.exchange")
                .routingKey("test.key")
                .data(Dict.create().set("id", 1).set("name", "demo"))
                .messageProperties(new MessageProperties())
                .build();
    }
}
