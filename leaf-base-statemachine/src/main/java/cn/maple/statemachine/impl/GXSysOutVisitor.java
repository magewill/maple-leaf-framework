package cn.maple.statemachine.impl;

import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXStateMachine;
import cn.maple.statemachine.GXTransition;
import cn.maple.statemachine.GXVisitor;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统输出访问者实现类
 * <p>
 * 该类实现了GXVisitor接口，用于将状态机结构输出到日志系统，便于调试和可视化状态机。
 * 它使用SLF4J日志框架记录状态机的结构信息，包括状态机ID、状态和转换等。
 * </p>
 * <p>
 * 系统输出访问者的主要功能：
 * <ul>
 *   <li>输出状态机的标识和边界</li>
 *   <li>输出每个状态及其关联的转换</li>
 *   <li>使用日志系统记录状态机结构，便于调试</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * 
 * // 使用系统输出访问者查看状态机结构
 * GXSysOutVisitor sysOutVisitor = new GXSysOutVisitor();
 * String result = stateMachine.accept(sysOutVisitor);
 * 
 * // 也可以直接使用状态机的便捷方法
 * stateMachine.showStateMachine();
 * }
 * </pre>
 * </p>
 * <p>
 * 输出示例：
 * <pre>
 * -----StateMachine:订单状态机-------
 * State:WAIT_PAYMENT
 *     Transition:WAIT_PAYMENT-[PAY, EXTERNAL]->PAID
 * State:PAID
 *     Transition:PAID-[DELIVER, EXTERNAL]->DELIVERING
 * State:DELIVERING
 *     Transition:DELIVERING-[RECEIVE, EXTERNAL]->RECEIVED
 * State:RECEIVED
 * ------------------------
 * </pre>
 * </p>
 * 
 * @see GXVisitor 访问者接口
 * @see GXStateMachine#showStateMachine() 状态机显示结构的便捷方法
 */
@Slf4j
public class GXSysOutVisitor implements GXVisitor {
    /**
     * 访问状态机入口
     * <p>
     * 生成状态机的头部信息，包括分隔线和状态机ID。
     * 同时将信息输出到日志系统。
     * </p>
     *
     * @param stateMachine 被访问的状态机对象
     * @return 状态机头部信息字符串
     */
    @Override
    public String visitOnEntry(GXStateMachine<?, ?, ?> stateMachine) {
        String entry = "-----StateMachine:" + stateMachine.getMachineId() + "-------";
        log.debug(entry);
        return entry;
    }

    /**
     * 访问状态机出口
     * <p>
     * 生成状态机的尾部信息，包括分隔线。
     * 同时将信息输出到日志系统。
     * </p>
     *
     * @param stateMachine 被访问的状态机对象
     * @return 状态机尾部信息字符串
     */
    @Override
    public String visitOnExit(GXStateMachine<?, ?, ?> stateMachine) {
        String exit = "------------------------";
        log.debug(exit);
        return exit;
    }

    /**
     * 访问状态入口
     * <p>
     * 生成状态的信息，包括状态ID和所有关联的转换。
     * 同时将信息输出到日志系统。
     * </p>
     * <p>
     * 对于每个状态，首先输出状态ID，然后遍历并输出该状态的所有转换。
     * 转换信息包括源状态、事件、转换类型和目标状态。
     * </p>
     *
     * @param state 被访问的状态对象
     * @return 包含状态信息的字符串
     */
    @Override
    public String visitOnEntry(GXState<?, ?, ?> state) {
        StringBuilder sb = new StringBuilder();
        String stateStr = "State:" + state.getId();
        sb.append(stateStr).append(LF);
        log.debug(stateStr);
        for (GXTransition<?, ?, ?> transition : state.getAllTransitions()) {
            String transitionStr = "    Transition:" + transition;
            sb.append(transitionStr).append(LF);
            log.debug(transitionStr);
        }
        return sb.toString();
    }

    /**
     * 访问状态出口
     * <p>
     * 在当前实现中，此方法不生成任何内容。
     * 如果需要在状态输出中添加额外的尾部信息，可以在此方法中实现。
     * </p>
     *
     * @param visitable 被访问的状态对象
     * @return 空字符串
     */
    @Override
    public String visitOnExit(GXState<?, ?, ?> visitable) {
        return "";
    }
}
