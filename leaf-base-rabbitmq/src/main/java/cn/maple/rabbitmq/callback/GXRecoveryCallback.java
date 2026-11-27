package cn.maple.rabbitmq.callback;

/**
 * 发送失败恢复回调。
 *
 * <p>当发送在写出阶段或结算阶段失败（连接中断、链路被关闭、结算超时）时触发，
 * 调用方可在此记录补偿动作。返回值当前不参与后续流程，保留以兼容历史签名。</p>
 */
@FunctionalInterface
public interface GXRecoveryCallback {
    Object recover(Throwable throwable);
}
