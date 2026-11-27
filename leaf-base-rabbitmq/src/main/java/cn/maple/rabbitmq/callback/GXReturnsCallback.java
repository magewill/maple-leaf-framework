package cn.maple.rabbitmq.callback;

/**
 * 不可路由消息回调，等价于经典协议的 {@code basic.return}。
 */
@FunctionalInterface
public interface GXReturnsCallback {
    void returnedMessage(GXReturnedMessage returned);
}
