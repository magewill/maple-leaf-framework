package cn.maple.redisson.properties;

import cn.hutool.core.text.CharSequenceUtil;
import lombok.Data;

@Data
public class GXRedissonStreamMQProperties {
    private String streamPrefix = "mq:stream:";

    private String delayZsetPrefix = "mq:delay:";

    private String dlqPrefix = "mq:dlq:";

    private String processedMessagePrefix = "mq:stream:processed:";

    private String delayTransferPrefix = "mq:delay:transfer:";

    private String consumerGroup = "default-group";

    private String consumerName = "consumer-1";

    private String consumerInstanceId;

    private boolean appendInstanceIdToConsumerName = true;

    private long maxPendingMillis = 30_000;

    private long pendingRecoveryInterval = 10_000;

    private long delayScanInterval = 1_000;

    private long delayTransferRecoveryMillis = 60_000;

    private long processedMessageTtlMillis = 86_400_000;

    private int batchSize = 10;

    private long streamMaxLen = 10_000;

    private long blockingTimeoutMillis = 2_000;

    private int consumerThreads = 2;

    public void validate() {
        validateText(streamPrefix, "streamPrefix");
        validateText(delayZsetPrefix, "delayZsetPrefix");
        validateText(dlqPrefix, "dlqPrefix");
        validateText(processedMessagePrefix, "processedMessagePrefix");
        validateText(delayTransferPrefix, "delayTransferPrefix");
        validateText(consumerGroup, "consumerGroup");
        validateText(consumerName, "consumerName");
        if (maxPendingMillis <= 0) {
            throw new IllegalArgumentException("maxPendingMillis must be greater than 0");
        }
        if (pendingRecoveryInterval <= 0) {
            throw new IllegalArgumentException("pendingRecoveryInterval must be greater than 0");
        }
        if (delayScanInterval <= 0) {
            throw new IllegalArgumentException("delayScanInterval must be greater than 0");
        }
        if (delayTransferRecoveryMillis <= 0) {
            throw new IllegalArgumentException("delayTransferRecoveryMillis must be greater than 0");
        }
        if (processedMessageTtlMillis <= 0) {
            throw new IllegalArgumentException("processedMessageTtlMillis must be greater than 0");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than 0");
        }
        if (streamMaxLen <= 0) {
            throw new IllegalArgumentException("streamMaxLen must be greater than 0");
        }
        if (blockingTimeoutMillis < 0) {
            throw new IllegalArgumentException("blockingTimeoutMillis must not be negative");
        }
        if (consumerThreads <= 0) {
            throw new IllegalArgumentException("consumerThreads must be greater than 0");
        }
        streamMaxLenAsInt();
    }

    public int streamMaxLenAsInt() {
        return Math.toIntExact(streamMaxLen);
    }

    private void validateText(String value, String name) {
        if (CharSequenceUtil.isBlank(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
