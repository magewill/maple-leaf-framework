package cn.maple.rabbitmq.dto.inner;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.*;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = true)
public class GXRabbitMQMessageReqDto extends GXBaseReqDto {
    private String exchange;

    private String routingKey;

    private Dict data;

    private String tag;

    private transient CorrelationData correlationData;

    private MessageProperties messageProperties;
}