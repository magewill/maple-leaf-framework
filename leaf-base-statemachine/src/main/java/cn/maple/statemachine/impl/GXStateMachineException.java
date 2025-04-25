package cn.maple.statemachine.impl;

/**
 * 状态机异常类
 * <p>
 * 此异常类用于表示状态机在运行过程中可能出现的各种异常情况，例如：
 * <ul>
 *   <li>状态机未初始化完成就尝试使用</li>
 *   <li>找不到指定的状态</li>
 *   <li>尝试添加重复的转换</li>
 *   <li>转换验证失败（如内部转换的源状态和目标状态不一致）</li>
 * </ul>
 * </p>
 * <p>
 * 作为运行时异常（RuntimeException的子类），使用者不需要强制捕获此异常，
 * 但在使用状态机时应当注意处理可能出现的异常情况。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * try {
 *     // 尝试触发状态转换
 *     OrderStatus newStatus = stateMachine.fireEvent(OrderStatus.WAIT_PAYMENT, OrderEvent.PAY, context);
 *     // 处理新状态
 * } catch (GXStateMachineException e) {
 *     // 处理状态机异常
 *     logger.error("状态机异常：" + e.getMessage(), e);
 * }
 * }
 * </pre>
 */
public class GXStateMachineException extends RuntimeException {
    public GXStateMachineException(String message) {
        super(message);
    }
}
