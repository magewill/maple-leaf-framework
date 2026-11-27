package cn.maple.rabbitmq.dto.inner;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 发送关联数据，替代经典协议中的 {@code CorrelationData}。
 *
 * <p>AMQP 1.0 没有 publisher confirm 帧，结算结果由 delivery outcome 表达；
 * 本类同时提供 {@link #settlement()} 便于调用方等待本次发送的最终结果，
 * 不等待时仍以 {@code GXConfirmCallback} 回调为准。</p>
 */
public final class GXCorrelationData {
    private final String id;

    private final transient @Nullable Object payload;

    private final transient CompletableFuture<Boolean> settlement = new CompletableFuture<>();

    public GXCorrelationData(String id) {
        this(id, null);
    }

    public GXCorrelationData(String id, @Nullable Object payload) {
        this.id = Objects.requireNonNull(id, "correlation id must not be null");
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public @Nullable Object getPayload() {
        return payload;
    }

    /**
     * 本次发送的结算结果：{@code true} 表示 Broker 返回 accepted。
     */
    public CompletableFuture<Boolean> settlement() {
        return settlement;
    }

    /**
     * 等待结算结果。
     *
     * @param timeout 最长等待时间
     * @return {@code true} 表示 accepted；{@code false} 表示 rejected/released/超时
     */
    public boolean await(Duration timeout) {
        try {
            return settlement.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "GXCorrelationData{id='" + id + "'}";
    }
}
