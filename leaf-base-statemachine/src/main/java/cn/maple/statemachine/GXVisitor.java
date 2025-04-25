package cn.maple.statemachine;

/**
 * 访问者接口
 * <p>
 * 该接口是访问者模式的核心组件之一，定义了访问状态机各个元素的方法。
 * 通过实现该接口，可以在不改变状态机结构的情况下，为状态机添加新的操作，
 * 如生成状态机的UML图、打印状态机结构、验证状态机配置等。
 * </p>
 * <p>
 * 访问者模式在状态机中的应用：
 * <ul>
 *   <li>分离了数据结构和数据操作</li>
 *   <li>使得添加新的操作变得简单，只需要实现新的访问者</li>
 *   <li>允许以不同方式处理状态机结构，如可视化、验证等</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 实现自定义访问者
 * public class CustomVisitor implements GXVisitor {
 *     @Override
 *     public String visitOnEntry(GXStateMachine<?, ?, ?> stateMachine) {
 *         return "开始访问状态机：" + stateMachine.getMachineId() + LF;
 *     }
 *     
 *     @Override
 *     public String visitOnExit(GXStateMachine<?, ?, ?> stateMachine) {
 *         return "结束访问状态机" + LF;
 *     }
 *     
 *     @Override
 *     public String visitOnEntry(GXState<?, ?, ?> state) {
 *         return "访问状态：" + state.getId() + LF;
 *     }
 *     
 *     @Override
 *     public String visitOnExit(GXState<?, ?, ?> state) {
 *         return "";
 *     }
 * }
 * 
 * // 使用自定义访问者
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * String result = stateMachine.accept(new CustomVisitor());
 * }
 * </pre>
 * </p>
 * 
 * @see GXVisitable 可访问接口
 * @see cn.maple.statemachine.impl.GXPlantUMLVisitor PlantUML访问者实现
 * @see cn.maple.statemachine.impl.GXSysOutVisitor 系统输出访问者实现
 */
public interface GXVisitor {
    /**
     * 换行符常量
     * <p>
     * 用于在生成文本输出时添加换行
     * </p>
     */
    char LF = '\n';

    /**
     * 访问状态机入口
     * <p>
     * 当开始访问状态机时调用此方法，通常用于生成状态机的头部信息
     * </p>
     *
     * @param visitable 被访问的状态机对象
     * @return 访问结果字符串
     */
    String visitOnEntry(GXStateMachine<?, ?, ?> visitable);

    /**
     * 访问状态机出口
     * <p>
     * 当结束访问状态机时调用此方法，通常用于生成状态机的尾部信息
     * </p>
     *
     * @param visitable 被访问的状态机对象
     * @return 访问结果字符串
     */
    String visitOnExit(GXStateMachine<?, ?, ?> visitable);

    /**
     * 访问状态入口
     * <p>
     * 当开始访问状态时调用此方法，通常用于生成状态的信息，
     * 包括状态的ID、转换等
     * </p>
     *
     * @param visitable 被访问的状态对象
     * @return 访问结果字符串
     */
    String visitOnEntry(GXState<?, ?, ?> visitable);

    /**
     * 访问状态出口
     * <p>
     * 当结束访问状态时调用此方法，通常用于生成状态的尾部信息
     * </p>
     *
     * @param visitable 被访问的状态对象
     * @return 访问结果字符串
     */
    String visitOnExit(GXState<?, ?, ?> visitable);
}
