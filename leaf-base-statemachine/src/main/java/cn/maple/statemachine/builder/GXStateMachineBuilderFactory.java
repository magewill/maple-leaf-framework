package cn.maple.statemachine.builder;

/**
 * 状态机构建器工厂类
 * <p>
 * 此工厂类用于创建状态机构建器实例，是使用状态机框架的入口点。
 * 采用工厂模式，隐藏了具体实现类的细节，提供了统一的创建接口。
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
 * // 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = 
 *     GXStateMachineBuilderFactory.create();
 * 
 * // 使用构建器定义状态转换
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.WAIT_DELIVER)
 *     .on(OrderEvent.PAYED)
 *     .when(orderContext -> orderContext.getOrder().getAmount() > 0)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单支付完成，状态从 " + from + " 变更为 " + to);
 *         // 执行状态转换相关的业务逻辑
 *     });
 * 
 * // 构建状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> orderStateMachine = 
 *     builder.build("订单状态机");
 * </pre>
 */
public class GXStateMachineBuilderFactory {
    /**
     * 私有构造函数，防止实例化
     */
    private GXStateMachineBuilderFactory() {
    }

    /**
     * 创建状态机构建器实例
     * <p>
     * 此方法是使用状态机框架的主要入口点，返回一个可用于构建状态机的构建器。
     *
     * @param <S> 状态类型
     * @param <E> 事件类型
     * @param <C> 上下文类型
     * @return 状态机构建器实例
     */
    public static <S, E, C> GXStateMachineBuilder<S, E, C> create() {
        return new GXStateMachineBuilderImpl<>();
    }
}
