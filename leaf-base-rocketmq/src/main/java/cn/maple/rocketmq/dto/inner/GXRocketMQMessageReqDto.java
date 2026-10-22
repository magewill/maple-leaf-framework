package cn.maple.rocketmq.dto.inner;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.json.JSONUtil;
import cn.maple.core.framework.dto.req.GXBaseReqDto;
import cn.maple.core.framework.exception.GXBusinessException;
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
     * <p>Note: a {@link String} argument also matches this constructor, so
     * {@code new GXRocketMQMessageReqDto("paid")} serializes the text into
     * {@code "\"paid\""}. Use the setter or the full constructor when the body is
     * already a JSON string.</p>
     *
     * @param body message body object, must not be null
     */
    public GXRocketMQMessageReqDto(Object body) {
        this.body = serializeBody(body);
    }

    /**
     * Creates a request with a tag and a JSON serialized body.
     *
     * @param tag  message tag
     * @param body message body object, must not be null
     */
    public GXRocketMQMessageReqDto(String tag, Object body) {
        this.tag = tag;
        this.body = serializeBody(body);
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
        this.body = serializeBody(body);
    }

    private static String serializeBody(Object body) {
        if (body == null) {
            throw new GXBusinessException("Message body must not be null");
        }
        String json = JSONUtil.toJsonStr(body);
        if (CharSequenceUtil.isBlank(json) || "null".equals(json.trim())) {
            throw new GXBusinessException("Message body must not be serialized to null");
        }
        return json;
    }
}
