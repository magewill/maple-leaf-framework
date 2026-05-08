package cn.maple.rocketmq.dto.inner;

import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * RocketMQ message request.
 *
 * <p>Constructors that accept an {@link Object} serialize the value with
 * {@link JSONUtil#toJsonStr(Object)}. Set {@link #topic} before sending when
 * using a constructor that only accepts the message body.</p>
 *
 * <pre>
 * GXRocketMQMessageReqDto message = new GXRocketMQMessageReqDto("order", "created", order, 0, "ORDER-1");
 * </pre>
 */
@Data
@ToString
@EqualsAndHashCode(callSuper = true)
public class GXRocketMQMessageReqDto extends GXBaseReqDto {
    /**
     * RocketMQ topic. It must not be blank when sending.
     */
    private String topic;

    /**
     * Optional RocketMQ tag. A blank tag sends to the topic only.
     */
    private String tag = "";

    /**
     * Message payload. Object-based constructors serialize this value to JSON.
     */
    private String body;

    /**
     * Delay time in seconds for delayed messages.
     */
    private long deliverTime;

    /**
     * RocketMQ message key for querying and tracing messages.
     */
    private String messageKey;

    /**
     * Creates an empty request.
     */
    public GXRocketMQMessageReqDto() {

    }

    /**
     * Creates a request with a JSON serialized body.
     *
     * @param body message body object
     */
    public GXRocketMQMessageReqDto(Object body) {
        this.body = JSONUtil.toJsonStr(body);
    }

    /**
     * Creates a request with a tag and a JSON serialized body.
     *
     * @param tag message tag
     * @param body message body object
     */
    public GXRocketMQMessageReqDto(String tag, Object body) {
        this.tag = tag;
        this.body = JSONUtil.toJsonStr(body);
    }

    /**
     * Creates a complete request with a JSON serialized body.
     *
     * @param topic RocketMQ topic
     * @param tag RocketMQ tag
     * @param body message body object
     * @param deliverTime delay time in seconds
     * @param messageKey RocketMQ message key
     */
    public GXRocketMQMessageReqDto(String topic, String tag, Object body, int deliverTime, String messageKey) {
        this.topic = topic;
        this.tag = tag;
        this.deliverTime = deliverTime;
        this.messageKey = messageKey;
        this.body = JSONUtil.toJsonStr(body);
    }
}
