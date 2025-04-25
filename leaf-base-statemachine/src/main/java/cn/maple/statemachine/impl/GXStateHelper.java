package cn.maple.statemachine.impl;

import cn.maple.statemachine.GXState;

import java.util.Map;

/**
 * 状态帮助类
 * <p>
 * 此工具类提供了状态管理的辅助功能，主要用于在状态机中获取或创建状态对象。
 * 它采用了"获取或创建"的模式，确保每个状态ID只对应一个状态对象实例。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *   <li>根据状态ID从状态映射中获取状态对象</li>
 *   <li>如果状态对象不存在，则创建新的状态对象并添加到映射中</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建状态映射
 * Map<OrderStatus, GXState<OrderStatus, OrderEvent, OrderContext>> stateMap = new HashMap<>();
 * 
 * // 获取或创建状态
 * GXState<OrderStatus, OrderEvent, OrderContext> waitPaymentState = 
 *     GXStateHelper.getState(stateMap, OrderStatus.WAIT_PAYMENT);
 * 
 * // 此时如果再次获取相同ID的状态，将返回同一个实例
 * GXState<OrderStatus, OrderEvent, OrderContext> sameState = 
 *     GXStateHelper.getState(stateMap, OrderStatus.WAIT_PAYMENT);
 * // waitPaymentState == sameState 为true
 * }
 * </pre>
 */
public class GXStateHelper {
    /**
     * 私有构造函数
     * <p>
     * 防止实例化此工具类，因为它只包含静态方法。
     */
    private GXStateHelper() {
    }

    /**
     * 获取或创建状态对象
     * <p>
     * 根据提供的状态ID从状态映射中获取对应的状态对象。
     * 如果状态对象不存在，则创建一个新的状态对象并将其添加到映射中。
     * 这确保了每个状态ID只对应一个状态对象实例，避免重复创建。
     *
     * @param stateMap 状态映射，键为状态ID，值为状态对象
     * @param stateId 要获取或创建的状态ID
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 上下文类型
     * @return 对应状态ID的状态对象
     */
    public static <S, E, C> GXState<S, E, C> getState(Map<S, GXState<S, E, C>> stateMap, S stateId) {
        GXState<S, E, C> state = stateMap.get(stateId);
        if (state == null) {
            state = new GXStateImpl<>(stateId);
            stateMap.put(stateId, state);
        }
        return state;
    }
}
