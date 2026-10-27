package cn.maple.rabbitmq.dto.inner;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * 消息发送请求。
 *
 * <p>{@link #messageProperties} 由调用方提供，发送时会做深拷贝后再写入追踪头，
 * 因此发送后调用方对象保持原样；{@link #tag} 仅为历史兼容保留，发送侧不再写入
 * {@code consumerTag}（该属性属于消费侧）。</p>
 */
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

    /**
     * @deprecated 发送侧忽略，请使用 {@link #correlationData} 或自定义 header 承载关联标识。
     */
    @Deprecated(since = "4.3.0")
    private String tag;

    @Nullable
    private transient CorrelationData correlationData;

    private MessageProperties messageProperties;
}
