package cn.maple.statemachine;

/**
 * 可访问接口
 * <p>
 * 该接口是访问者模式的核心组件之一，实现了该接口的类可以接受访问者的访问。
 * 在状态机框架中，状态机和状态类都实现了该接口，使得可以通过访问者模式遍历状态机结构，
 * 实现如状态机可视化、状态机结构验证等功能。
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 创建一个状态机实例
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * 
 * // 使用PlantUML访问者生成UML图
 * String plantUML = stateMachine.accept(new GXPlantUMLVisitor());
 * 
 * // 使用系统输出访问者打印状态机结构
 * stateMachine.accept(new GXSysOutVisitor());
 * }
 * </pre>
 * </p>
 * 
 * @see GXVisitor 访问者接口
 * @see cn.maple.statemachine.impl.GXPlantUMLVisitor PlantUML访问者实现
 * @see cn.maple.statemachine.impl.GXSysOutVisitor 系统输出访问者实现
 */
public interface GXVisitable {
    /**
     * 接受访问者访问
     * <p>
     * 该方法是访问者模式的核心方法，实现该方法的类需要调用访问者的相应方法，
     * 将自身结构暴露给访问者进行操作。
     * </p>
     *
     * @param visitor 访问者对象
     * @return 访问结果，通常是字符串形式的输出
     */
    String accept(final GXVisitor visitor);
}
