package cn.maple.rocketmq.exception;

import cn.hutool.core.lang.Dict;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpStatus;
import cn.maple.core.framework.exception.GXBusinessException;

import java.util.List;

/**
 * Raised when at least one CDC listener fails while dispatching a single event.
 *
 * <p>All listeners are still invoked; the original failures are attached as suppressed
 * exceptions so the consumer can retry the message without losing the failure detail.</p>
 */
public class GXCdcDispatchException extends GXBusinessException {
    private final int failedListenerCount;

    private final String bizType;

    private final String op;

    private final String tsMs;

    /**
     * @param bizType normalized business type, may be blank
     * @param op      raw operation code, may be blank
     * @param tsMs    event timestamp in milliseconds, may be blank
     * @param cause   first listener failure, never null
     */
    public GXCdcDispatchException(String bizType, String op, String tsMs, List<Throwable> failures, Throwable cause) {
        super(buildMessage(bizType, op, tsMs, failures.size()), HttpStatus.HTTP_INTERNAL_ERROR, buildData(bizType, op, tsMs), cause);
        if (failures.isEmpty()) {
            throw new IllegalArgumentException("CDC listener failures must not be empty");
        }
        this.failedListenerCount = failures.size();
        this.bizType = bizType;
        this.op = op;
        this.tsMs = tsMs;
        failures.forEach(this::addSuppressed);
    }

    public int getFailedListenerCount() {
        return failedListenerCount;
    }

    public String getBizType() {
        return bizType;
    }

    public String getOp() {
        return op;
    }

    public String getTsMs() {
        return tsMs;
    }

    private static String buildMessage(String bizType, String op, String tsMs, int failedListenerCount) {
        return CharSequenceUtil.format("CDC dispatch failed. failedListeners={}, bizType={}, op={}, tsMs={}",
                failedListenerCount, bizType, op, tsMs);
    }

    private static Dict buildData(String bizType, String op, String tsMs) {
        Dict data = Dict.create();
        data.set("bizType", bizType);
        data.set("op", op);
        data.set("tsMs", tsMs);
        return data;
    }
}
