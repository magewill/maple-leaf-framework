package cn.maple.statemachine.impl;

import cn.maple.statemachine.GXTransition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 事件转换管理器
 * <p>
 * 此类负责管理事件与状态转换的映射关系，是状态机实现的核心组件之一。
 * 它维护了一个从事件到转换列表的映射，支持一个事件触发多个不同的状态转换。
 * <p>
 * 泛型参数说明：
 * <ul>
 *     <li>S - 状态(State)类型，通常使用枚举或字符串</li>
 *     <li>E - 事件(Event)类型，通常使用枚举或字符串</li>
 *     <li>C - 上下文(Context)类型，用于在转换过程中传递数据</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * // 创建事件转换管理器
 * GXEventTransitions<OrderStatus, OrderEvent, OrderContext> eventTransitions = new GXEventTransitions<>();
 *
 * // 添加转换
 * GXTransition<OrderStatus, OrderEvent, OrderContext> transition1 = new GXTransitionImpl<>();
 * // 设置转换的源状态、目标状态等属性
 * eventTransitions.put(OrderEvent.PAYED, transition1);
 *
 * // 获取事件对应的所有转换
 * List<GXTransition<OrderStatus, OrderEvent, OrderContext>> transitions =
 *     eventTransitions.get(OrderEvent.PAYED);
 * </pre>
 */
public class GXEventTransitions<S, E, C> {
    /**
     * 事件到转换列表的映射
     * <p>
     * 键是事件，值是该事件可能触发的所有转换列表。
     * 同一个事件可以触发多个不同的转换，但每个源状态和目标状态的组合只允许有一个转换。
     */
    private final HashMap<E, List<GXTransition<S, E, C>>> eventTransitions;

    /**
     * 构造函数
     * <p>
     * 初始化事件转换映射表。
     */
    public GXEventTransitions() {
        eventTransitions = new HashMap<>();
    }

    /**
     * 添加事件转换
     * <p>
     * 将指定的转换添加到对应事件的转换列表中。
     * 如果事件还没有关联的转换列表，会创建一个新的列表。
     * 添加前会验证是否已存在相同的转换（相同的源状态和目标状态）。
     *
     * @param event      触发转换的事件
     * @param transition 要添加的转换
     * @throws GXStateMachineException 如果已存在相同的转换
     */
    public void put(E event, GXTransition<S, E, C> transition) {
        if (eventTransitions.get(event) == null) {
            List<GXTransition<S, E, C>> transitions = new ArrayList<>();
            transitions.add(transition);
            eventTransitions.put(event, transitions);
        } else {
            List<GXTransition<S, E, C>> existingTransitions = eventTransitions.get(event);
            verify(existingTransitions, transition);
            existingTransitions.add(transition);
        }
    }

    /**
     * 验证转换的唯一性
     * <p>
     * 对于同一个源状态和目标状态，只允许存在一个转换。
     * 此方法检查新的转换是否与已有的转换冲突。
     *
     * @param existingTransitions 已存在的转换列表
     * @param newTransition       新的转换
     * @throws GXStateMachineException 如果新转换与已有转换冲突
     */
    private void verify(List<GXTransition<S, E, C>> existingTransitions, GXTransition<S, E, C> newTransition) {
        for (GXTransition<S, E, C> transition : existingTransitions) {
            if (transition.equals(newTransition)) {
                throw new GXStateMachineException(transition + " already Exist, you can not add another one");
            }
        }
    }

    /**
     * 获取事件对应的转换列表
     * <p>
     * 返回指定事件关联的所有转换。如果事件没有关联的转换，返回null。
     *
     * @param event 要查询的事件
     * @return 事件关联的转换列表，如果没有则返回null
     */
    public List<GXTransition<S, E, C>> get(E event) {
        return eventTransitions.get(event);
    }

    /**
     * 获取所有转换
     * <p>
     * 返回所有事件关联的所有转换的合并列表。
     *
     * @return 包含所有转换的列表
     */
    public List<GXTransition<S, E, C>> allTransitions() {
        List<GXTransition<S, E, C>> allTransitions = new ArrayList<>();
        for (List<GXTransition<S, E, C>> transitions : eventTransitions.values()) {
            allTransitions.addAll(transitions);
        }
        return allTransitions;
    }
}
