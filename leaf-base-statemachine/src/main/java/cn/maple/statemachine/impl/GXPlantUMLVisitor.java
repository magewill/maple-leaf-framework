package cn.maple.statemachine.impl;

import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXStateMachine;
import cn.maple.statemachine.GXTransition;
import cn.maple.statemachine.GXVisitor;

import java.util.Collection;

/**
 * PlantUML访问者实现类
 * <p>
 * 该类实现了GXVisitor接口，用于将状态机结构转换为PlantUML格式的UML状态图描述。
 * PlantUML是一种通过简单的文本语言来定义图表的工具，可以生成各种UML图，包括状态图。
 * </p>
 * <p>
 * PlantUML访问者的主要功能：
 * <ul>
 *   <li>生成状态图的开始和结束标记</li>
 *   <li>将状态转换关系转换为PlantUML语法</li>
 *   <li>支持状态之间的转换可视化</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * 
 * // 使用PlantUML访问者生成UML图
 * GXPlantUMLVisitor plantUMLVisitor = new GXPlantUMLVisitor();
 * String umlContent = stateMachine.accept(plantUMLVisitor);
 * 
 * // 将生成的内容保存到文件
 * try (PrintWriter writer = new PrintWriter(new FileWriter("order-state-machine.puml"))) {
 *     writer.write(umlContent);
 * }
 * 
 * // 也可以直接使用状态机的便捷方法
 * String uml = stateMachine.generatePlantUML();
 * }
 * </pre>
 * </p>
 * <p>
 * 生成的PlantUML内容示例：
 * <pre>
 * @startuml
 * WAIT_PAYMENT --> PAID : PAY
 * PAID --> DELIVERING : DELIVER
 * DELIVERING --> RECEIVED : RECEIVE
 * @enduml
 * </pre>
 * </p>
 * 
 * @see GXVisitor 访问者接口
 * @see GXStateMachine#generatePlantUML() 状态机生成PlantUML的便捷方法
 */
public class GXPlantUMLVisitor implements GXVisitor {
    /**
     * 访问状态机入口
     * <p>
     * 生成PlantUML状态图的开始标记。由于状态机是无状态的，没有初始状态，
     * 所以这里只生成PlantUML的开始标记。
     * </p>
     * <p>
     * 注意：在实际应用中，如果需要标记初始状态，可以在此方法中添加
     * "[*] -> initialState"语法，但这需要知道初始状态是什么。
     * </p>
     *
     * @param visitable 被访问的状态机对象
     * @return PlantUML的开始标记
     */
    @Override
    public String visitOnEntry(GXStateMachine<?, ?, ?> visitable) {
        return "@startuml" + LF;
    }

    /**
     * 访问状态机出口
     * <p>
     * 生成PlantUML状态图的结束标记。
     * </p>
     *
     * @param visitable 被访问的状态机对象
     * @return PlantUML的结束标记
     */
    @Override
    public String visitOnExit(GXStateMachine<?, ?, ?> visitable) {
        return "@enduml";
    }

    /**
     * 访问状态入口
     * <p>
     * 生成当前状态的所有转换的PlantUML表示。
     * 对于每个转换，生成一行格式为"源状态 --> 目标状态 : 事件"的文本。
     * </p>
     * <p>
     * 例如，对于从WAIT_PAYMENT到PAID的PAY事件转换，生成：
     * "WAIT_PAYMENT --> PAID : PAY"
     * </p>
     *
     * @param state 被访问的状态对象
     * @return 包含所有转换的PlantUML表示的字符串
     */
    @Override
    public String visitOnEntry(GXState<?, ?, ?> state) {
        StringBuilder sb = new StringBuilder();
        Collection<? extends GXTransition<?, ?, ?>> allTransitions = state.getAllTransitions();
        for (GXTransition<?, ?, ?> transition : allTransitions) {
            sb.append(transition.getSource().getId())
                    .append(" --> ")
                    .append(transition.getTarget().getId())
                    .append(" : ")
                    .append(transition.getEvent())
                    .append(LF);
        }
        return sb.toString();
    }

    /**
     * 访问状态出口
     * <p>
     * 在当前实现中，此方法不生成任何内容。
     * 如果需要在状态图中添加额外的状态信息，可以在此方法中实现。
     * </p>
     *
     * @param state 被访问的状态对象
     * @return 空字符串
     */
    @Override
    public String visitOnExit(GXState<?, ?, ?> state) {
        return "";
    }
}
