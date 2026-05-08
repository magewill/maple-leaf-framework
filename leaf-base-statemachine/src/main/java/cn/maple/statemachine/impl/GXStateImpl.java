package cn.maple.statemachine.impl;

import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXTransition;
import cn.maple.statemachine.GXVisitor;

import java.util.Collection;
import java.util.List;

/**
 * 状态实现类
 * <p>
 * 此类实现了{@link GXState}接口，代表状态机中的一个状态。
 * 每个状态包含一个唯一的标识符和一组从该状态出发的转换。
 * 状态是状态机的基本组成元素，负责管理与特定状态相关的所有转换。
 * </p>
 * <p>
 * 主要功能：
 * <ul>
 *   <li>维护状态标识符</li>
 *   <li>管理从该状态出发的所有转换</li>
 *   <li>提供添加和查询转换的方法</li>
 *   <li>支持访问者模式，用于状态机的可视化和调试</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建状态
 * GXStateImpl<OrderStatus, OrderEvent, OrderContext> paidState =
 *     new GXStateImpl<>(OrderStatus.PAID);
 *
 * // 添加转换
 * GXState<OrderStatus, OrderEvent, OrderContext> deliveringState =
 *     new GXStateImpl<>(OrderStatus.DELIVERING);
 * paidState.addTransition(OrderEvent.DELIVER, deliveringState, GXTransitionType.EXTERNAL);
 *
 * // 获取特定事件的转换
 * List<GXTransition<OrderStatus, OrderEvent, OrderContext>> transitions =
 *     paidState.getEventTransitions(OrderEvent.DELIVER);
 * }
 * </pre>
 */
public class GXStateImpl<S, E, C> implements GXState<S, E, C> {
    /**
     * 状态ID
     * <p>
     * 唯一标识此状态的标识符，通常使用枚举或字符串类型。
     */
    protected final S stateId;

    /**
     * 事件转换管理器
     * <p>
     * 管理从此状态出发的所有转换，按事件类型组织。
     * 一个事件可以触发多个不同的转换，具体执行哪个转换取决于转换条件。
     */
    private final GXEventTransitions<S, E, C> eventTransitions = new GXEventTransitions<>();

    /**
     * 构造函数
     * <p>
     * 创建一个新的状态实例，使用指定的状态ID作为唯一标识符。
     *
     * @param stateId 状态的唯一标识符
     */
    GXStateImpl(S stateId) {
        this.stateId = stateId;
    }

    /**
     * 添加转换
     * <p>
     * 创建并配置一个新的转换，从当前状态到目标状态，由指定的事件触发。
     * 转换类型决定了状态转换的行为（内部、本地或外部）。
     *
     * @param event          触发转换的事件
     * @param target         转换的目标状态
     * @param transitionType 转换类型（INTERNAL、LOCAL或EXTERNAL）
     * @return 新创建的转换对象，可以进一步配置（如添加条件或动作）
     */
    @Override
    public GXTransition<S, E, C> addTransition(E event, GXState<S, E, C> target, GXTransitionType transitionType) {
        GXTransition<S, E, C> newTransition = new GXTransitionImpl<>();
        newTransition.setSource(this);
        newTransition.setTarget(target);
        newTransition.setEvent(event);
        newTransition.setType(transitionType);
        GXDebugger.debug("Begin to add new transition: " + newTransition);
        eventTransitions.put(event, newTransition);
        return newTransition;
    }

    /**
     * 获取事件转换
     * <p>
     * 返回当前状态下由指定事件触发的所有转换列表。
     * 如果没有对应的转换，返回null或空列表。
     *
     * @param event 要查询的事件
     * @return 事件对应的转换列表，如果没有则返回null
     */
    @Override
    public List<GXTransition<S, E, C>> getEventTransitions(E event) {
        return eventTransitions.get(event);
    }

    /**
     * 获取所有转换
     * <p>
     * 返回从当前状态出发的所有转换的集合。
     *
     * @return 所有转换的集合
     */
    @Override
    public Collection<GXTransition<S, E, C>> getAllTransitions() {
        return eventTransitions.allTransitions();
    }

    /**
     * 获取状态ID
     * <p>
     * 返回此状态的唯一标识符。
     *
     * @return 状态ID
     */
    @Override
    public S getId() {
        return stateId;
    }

    /**
     * 接受访问者
     * <p>
     * 实现访问者模式，允许访问者对象访问此状态。
     * 这通常用于状态机的可视化、调试或序列化。
     *
     * @param visitor 访问者对象
     * @return 访问结果字符串
     */
    @Override
    public String accept(GXVisitor visitor) {
        String entry = visitor.visitOnEntry(this);
        String exit = visitor.visitOnExit(this);
        return entry + exit;
    }

    /**
     * 判断相等性
     * <p>
     * 两个状态相等当且仅当它们的状态ID相等。
     * 这确保了状态的唯一性，即使是不同类型的状态实现。
     *
     * @param anObject 要比较的对象
     * @return 如果对象是状态且ID相等则返回true，否则返回false
     */
    @Override
    public boolean equals(Object anObject) {
        if (anObject instanceof GXState) {
            GXState<?, ?, ?> other = (GXState<?, ?, ?>) anObject;
            return this.stateId.equals(other.getId());
        }
        return false;
    }

    /**
     * 转换为字符串
     * <p>
     * 返回此状态的字符串表示，通常是状态ID的字符串形式。
     *
     * @return 状态的字符串表示
     */
    @Override
    public String toString() {
        return stateId.toString();
    }
}
