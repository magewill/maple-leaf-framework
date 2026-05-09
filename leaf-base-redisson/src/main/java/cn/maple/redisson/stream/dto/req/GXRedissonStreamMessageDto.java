package cn.maple.redisson.stream.dto.req;

import cn.maple.core.framework.dto.GXBaseDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GXRedissonStreamMessageDto extends GXBaseDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private String messageId = UUID.randomUUID().toString();

    @Builder.Default
    private long createTime = System.currentTimeMillis();

    @Builder.Default
    private long deliveryTime = System.currentTimeMillis();

    @Builder.Default
    private int retryCount = 0;

    @Builder.Default
    private int maxRetry = 3;

    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();

    private String topic;

    private String payload;

    private String payloadClass;

    private String source;

    private transient String streamMessageId;

    public static GXRedissonStreamMessageDto immediate(String topic, String payload, String payloadClass) {
        validateBaseArgs(topic, payload, payloadClass);
        return GXRedissonStreamMessageDto.builder()
                .topic(topic)
                .payload(payload)
                .payloadClass(payloadClass)
                .build();
    }

    public static GXRedissonStreamMessageDto delayed(String topic, String payload, String payloadClass, long delayMillis) {
        validateBaseArgs(topic, payload, payloadClass);
        if (delayMillis <= 0) {
            throw new IllegalArgumentException("delayMillis must be greater than 0");
        }
        long now = System.currentTimeMillis();
        return GXRedissonStreamMessageDto.builder()
                .topic(topic)
                .payload(payload)
                .payloadClass(payloadClass)
                .createTime(now)
                .deliveryTime(now + delayMillis)
                .build();
    }

    public boolean isDue() {
        return System.currentTimeMillis() >= deliveryTime;
    }

    public boolean isExhausted() {
        return retryCount >= maxRetry;
    }

    public GXRedissonStreamMessageDto nextRetry() {
        return GXRedissonStreamMessageDto.builder()
                .messageId(Objects.requireNonNullElseGet(this.messageId, () -> UUID.randomUUID().toString()))
                .topic(this.topic)
                .payload(this.payload)
                .payloadClass(this.payloadClass)
                .createTime(this.createTime)
                .deliveryTime(System.currentTimeMillis())
                .retryCount(this.retryCount + 1)
                .maxRetry(this.maxRetry)
                .attributes(this.attributes == null ? new HashMap<>() : new HashMap<>(this.attributes))
                .source(this.source)
                .build();
    }

    public void normalizeDefaults() {
        if (messageId == null || messageId.isBlank()) {
            messageId = UUID.randomUUID().toString();
        }
        if (createTime <= 0) {
            createTime = System.currentTimeMillis();
        }
        if (deliveryTime <= 0) {
            deliveryTime = createTime;
        }
        if (attributes == null) {
            attributes = new HashMap<>();
        }
    }

    private static void validateBaseArgs(String topic, String payload, String payloadClass) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (payloadClass == null || payloadClass.isBlank()) {
            throw new IllegalArgumentException("payloadClass must not be blank");
        }
    }

    @Override
    public String toString() {
        return "Message{id='" + messageId + "', topic='" + topic +
                "', retry=" + retryCount + "/" + maxRetry +
                ", deliveryTime=" + Instant.ofEpochMilli(deliveryTime) + "}";
    }
}
