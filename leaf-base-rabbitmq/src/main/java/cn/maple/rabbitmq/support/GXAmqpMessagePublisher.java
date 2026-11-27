package cn.maple.rabbitmq.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.protonj2.client.Connection;
import org.apache.qpid.protonj2.client.DeliveryState;
import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.Sender;
import org.apache.qpid.protonj2.client.SenderOptions;
import org.apache.qpid.protonj2.client.Tracker;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.apache.qpid.protonj2.client.exceptions.ClientIllegalStateException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.client.AmqpConnectionFactory;
import org.springframework.amqp.client.ProtonUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AMQP 1.0 消息发布器。
 *
 * <p>持有一条匿名 sender 链路（AMQP 1.0 允许在同一链路上按消息 {@code to} 属性切换目标），
 * 发送后等待 Broker 结算，并把 {@link DeliveryState} 映射为 {@link GXSendOutcome}。
 * 链路被 Broker 关闭时自动重建，避免一次失败后永久不可用。</p>
 *
 * <p>结算等待在虚拟线程上完成，不占用调用线程。</p>
 */
@Slf4j
public final class GXAmqpMessagePublisher implements AutoCloseable {
    private final AmqpConnectionFactory connectionFactory;

    private final SenderOptions senderOptions;

    private final ExecutorService settlementExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("rabbitmq-settlement-", 1).factory());

    private final Lock senderLock = new ReentrantLock();

    private volatile Sender sender;

    private volatile boolean closed;

    public GXAmqpMessagePublisher(AmqpConnectionFactory connectionFactory) {
        this(connectionFactory, new SenderOptions());
    }

    public GXAmqpMessagePublisher(AmqpConnectionFactory connectionFactory, SenderOptions senderOptions) {
        this.connectionFactory = connectionFactory;
        this.senderOptions = senderOptions;
    }

    /**
     * 异步发送并返回结算结果。
     *
     * <p>连接建立、链路打开与写出阶段的失败直接抛 {@link org.springframework.amqp.AmqpException}，
     * 与经典模板"同步失败即抛出"的行为一致；结算阶段的结果通过返回的 Future 表达。</p>
     *
     * @param address          AMQP 1.0 v2 地址
     * @param message          Spring AMQP 消息
     * @param settlementMillis 等待结算的毫秒数
     * @return 结算结果
     */
    public CompletableFuture<GXSendOutcome> send(String address, org.springframework.amqp.core.Message message,
                                                 long settlementMillis) {
        if (this.closed) {
            throw new AmqpException("RabbitMQ publisher is closed");
        }
        final Tracker tracker;
        try {
            Message<?> protonMessage = ProtonUtils.toProtonMessage(message);
            protonMessage.to(address);
            tracker = doSend(protonMessage);
        } catch (ClientException e) {
            throw ProtonUtils.toAmqpException(e);
        }
        try {
            return CompletableFuture.supplyAsync(() -> awaitOutcome(tracker, settlementMillis), settlementExecutor);
        } catch (RejectedExecutionException e) {
            // 消息已写出但结算跟踪被拒绝（通常是关闭过程中）：不能当作发送失败重投，按未知结果上报。
            log.error("RabbitMQ settlement tracking rejected: address={}, error={}", address, e.getMessage());
            return CompletableFuture.completedFuture(GXSendOutcome.UNKNOWN);
        }
    }

    private Tracker doSend(Message<?> protonMessage) throws ClientException {
        Sender currentSender = getSender();
        try {
            return currentSender.send(protonMessage);
        } catch (ClientIllegalStateException e) {
            // 本地关闭或 Broker 主动关闭链路/会话（ClientResourceRemotelyClosedException 是其子类）：
            // 本次未真正写出，重建链路后重试一次。
            resetSender(currentSender);
            return getSender().send(protonMessage);
        }
    }

    private GXSendOutcome awaitOutcome(Tracker tracker, long settlementMillis) {
        try {
            tracker.awaitSettlement(settlementMillis, TimeUnit.MILLISECONDS);
        } catch (ClientException e) {
            throw ProtonUtils.toAmqpException(e);
        }
        DeliveryState state = tracker.remoteState();
        if (state == null) {
            log.warn("RabbitMQ send settlement missing: timeoutMillis={}", settlementMillis);
            return GXSendOutcome.UNKNOWN;
        }
        return switch (state.getType()) {
            case ACCEPTED -> GXSendOutcome.ACCEPTED;
            case REJECTED -> GXSendOutcome.REJECTED;
            case RELEASED -> GXSendOutcome.RELEASED;
            case MODIFIED -> GXSendOutcome.MODIFIED;
            case TRANSACTIONAL -> GXSendOutcome.UNKNOWN;
        };
    }

    private Sender getSender() throws ClientException {
        if (this.closed) {
            throw new ClientIllegalStateException("RabbitMQ publisher is closed");
        }
        Sender currentSender = this.sender;
        if (currentSender != null) {
            return currentSender;
        }
        this.senderLock.lock();
        try {
            currentSender = this.sender;
            if (currentSender == null) {
                Connection connection = this.connectionFactory.getConnection();
                Sender opened = connection.openAnonymousSender(this.senderOptions);
                Future<Sender> openFuture = opened.openFuture();
                currentSender = openFuture.get();
                this.sender = currentSender;
            }
            return currentSender;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClientIllegalStateException("Interrupted while opening the AMQP sender");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new ClientIllegalStateException("Failed to open the AMQP sender", e.getCause());
        } finally {
            this.senderLock.unlock();
        }
    }

    private void resetSender(Sender closedSender) {
        this.senderLock.lock();
        try {
            if (this.sender == closedSender) {
                this.sender = null;
                closedSender.closeAsync();
            }
        } finally {
            this.senderLock.unlock();
        }
    }

    @Override
    public void close() {
        this.closed = true;
        Sender currentSender = this.sender;
        this.sender = null;
        if (currentSender != null) {
            try {
                currentSender.close();
            } catch (RuntimeException e) {
                log.debug("RabbitMQ sender close failed: {}", e.getClass().getName());
            }
        }
        this.settlementExecutor.shutdown();
        try {
            if (!this.settlementExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                this.settlementExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.settlementExecutor.shutdownNow();
        }
    }
}
