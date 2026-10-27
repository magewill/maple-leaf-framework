package cn.maple.rabbitmq.exception;

import cn.hutool.core.lang.Dict;
import cn.maple.core.framework.exception.GXBusinessException;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * RabbitMQ 资源操作失败异常。
 *
 * <p>用于替代"失败返回 null/false 只落日志"的旧行为：调用方可通过 {@link #getOperation()}
 * 区分失败发生在哪一步，通过 {@link #getDetail()} 中的 {@code completedSteps} / {@code failedStep}
 * 判断是否已产生部分资源（交换机已声明但队列失败等）。</p>
 *
 * <p>消息文本用于面向用户，日志一律使用 ASCII。</p>
 */
@Getter
public class GXRabbitMQOperationException extends GXBusinessException {
    private final Operation operation;

    private final String resource;

    private final transient Dict detail;

    public GXRabbitMQOperationException(Operation operation, String resource, String message, Throwable cause) {
        super(message, cause);
        this.operation = Objects.requireNonNull(operation, "operation must not be null");
        this.resource = Objects.requireNonNullElse(resource, "");
        this.detail = Dict.create().set("operation", operation.name()).set("resource", this.resource);
        if (cause != null) {
            this.detail.set("causeType", cause.getClass().getName());
        }
    }

    public GXRabbitMQOperationException(Operation operation, String resource, String message) {
        this(operation, resource, message, null);
    }

    /**
     * 记录通道创建过程中已完成的步骤，返回自身以便链式使用。
     *
     * @param completedSteps 已完成步骤，按执行顺序
     * @param failedStep     失败步骤
     */
    public GXRabbitMQOperationException withProgress(List<String> completedSteps, String failedStep) {
        detail.set("completedSteps", List.copyOf(completedSteps));
        detail.set("failedStep", failedStep);
        return this;
    }

    /**
     * 资源操作类型，与契约文档中的步骤名一致。
     */
    public enum Operation {
        DECLARE_EXCHANGE,
        DECLARE_QUEUE,
        QUERY_QUEUE,
        BIND,
        UNBIND,
        DELETE_QUEUE,
        PURGE_QUEUE,
        SEND
    }
}
