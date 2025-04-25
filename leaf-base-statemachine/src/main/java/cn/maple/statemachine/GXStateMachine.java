package cn.maple.statemachine;

/**
 * 状态机接口
 * <p>
 * 状态机是一种行为模型，用于描述系统在不同状态下的行为以及状态之间的转换条件。
 * 本接口定义了状态机的核心功能，包括事件触发、状态转换、状态机可视化等。
 * </p>
 * <p>
 * 状态机的主要组成部分：
 * <ul>
 *   <li>状态(State)：系统在特定时刻的状态</li>
 *   <li>事件(Event)：触发状态转换的事件</li>
 *   <li>转换(Transition)：从一个状态到另一个状态的转换规则</li>
 *   <li>上下文(Context)：状态转换过程中传递的数据</li>
 * </ul>
 * </p>
 * <p>
 * 状态机的设计特点：
 * <ul>
 *   <li>无状态设计：状态机本身不存储当前状态，由调用者维护状态信息</li>
 *   <li>线程安全：状态机实例可以被多个线程安全地共享使用</li>
 *   <li>可扩展性：支持添加自定义条件和动作，灵活适应各种业务场景</li>
 *   <li>可视化支持：提供状态机结构的可视化功能，便于理解和调试</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 定义状态、事件和上下文类型
 * public enum OrderStatus { WAIT_PAYMENT, PAID, DELIVERING, RECEIVED }
 * public enum OrderEvent { PAY, DELIVER, RECEIVE }
 * public class OrderContext { 
 *     private String orderId;
 *     private double amount;
 *     // getter and setter
 * }
 * 
 * // 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = 
 *     GXStateMachineBuilderFactory.create();
 * 
 * // 配置状态转换 - 支付流程
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(ctx -> ctx.getAmount() > 0) // 添加条件：金额必须大于0
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已支付：" + ctx.getOrderId());
 *         // 执行支付后的业务逻辑，如更新订单状态、发送通知等
 *     });
 * 
 * // 配置状态转换 - 发货流程
 * builder.externalTransition()
 *     .from(OrderStatus.PAID)
 *     .to(OrderStatus.DELIVERING)
 *     .on(OrderEvent.DELIVER)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已发货：" + ctx.getOrderId());
 *     });
 * 
 * // 配置状态转换 - 收货流程
 * builder.externalTransition()
 *     .from(OrderStatus.DELIVERING)
 *     .to(OrderStatus.RECEIVED)
 *     .on(OrderEvent.RECEIVE)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已签收：" + ctx.getOrderId());
 *     });
 * 
 * // 构建状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = 
 *     builder.build("订单状态机");
 * 
 * // 使用状态机处理事件
 * OrderContext context = new OrderContext();
 * context.setOrderId("ORDER_123456");
 * context.setAmount(100);
 * 
 * // 触发支付事件
 * OrderStatus newStatus = stateMachine.fireEvent(
 *     OrderStatus.WAIT_PAYMENT, OrderEvent.PAY, context);
 * System.out.println("支付后状态：" + newStatus); // 输出：支付后状态：PAID
 * 
 * // 触发发货事件
 * newStatus = stateMachine.fireEvent(newStatus, OrderEvent.DELIVER, context);
 * System.out.println("发货后状态：" + newStatus); // 输出：发货后状态：DELIVERING
 * 
 * // 触发收货事件
 * newStatus = stateMachine.fireEvent(newStatus, OrderEvent.RECEIVE, context);
 * System.out.println("收货后状态：" + newStatus); // 输出：收货后状态：RECEIVED
 * 
 * // 可视化状态机结构
 * stateMachine.showStateMachine();
 * 
 * // 生成PlantUML图表
 * String uml = stateMachine.generatePlantUML();
 * System.out.println(uml);
 * // 可以将生成的UML保存到文件，然后使用PlantUML工具生成图像
 * // try (PrintWriter writer = new PrintWriter("order-state-machine.puml")) {
 * //     writer.write(uml);
 * // }
 * }
 * </pre>
 * </p>
 * <p>
 * 高级用法 - 多个条件的状态转换：
 * <pre>
 * {@code
 * // 定义多个条件
 * GXCondition<OrderContext> amountCondition = ctx -> ctx.getAmount() > 0;
 * GXCondition<OrderContext> vipCondition = ctx -> ctx.isVipUser();
 * 
 * // 普通用户支付流程
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(amountCondition)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("普通用户订单支付完成");
 *     });
 * 
 * // VIP用户支付流程（可能有特殊处理）
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(ctx -> amountCondition.isSatisfied(ctx) && vipCondition.isSatisfied(ctx))
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("VIP用户订单支付完成，享受优先处理");
 *     });
 * }
 * </pre>
 * </p>
 *
 * @param <S> 状态类型，通常使用枚举或字符串
 * @param <E> 事件类型，通常使用枚举或字符串
 * @param <C> 用户自定义上下文类型，用于在状态转换过程中传递数据
 * 
 * @see GXState 状态接口
 * @see GXTransition 转换接口
 * @see GXAction 动作接口
 * @see GXCondition 条件接口
 * @see cn.maple.statemachine.impl.GXStateMachineImpl 状态机实现类
 * @see cn.maple.statemachine.builder.GXStateMachineBuilder 状态机构建器接口
 */
public interface GXStateMachine<S, E, C> extends GXVisitable {
    /**
     * 触发状态机事件
     * <p>
     * 根据当前状态和事件，执行状态转换。如果存在多个可能的转换，
     * 会按照条件(Condition)的满足情况选择合适的转换。
     * 如果没有找到匹配的转换，将保持原状态不变。
     * </p>
     * <p>
     * 状态转换的执行流程：
     * <ol>
     *   <li>查找源状态下匹配事件的所有可能转换</li>
     *   <li>依次检查每个转换的条件是否满足</li>
     *   <li>选择第一个满足条件的转换执行，如果没有条件的转换则选择第一个无条件转换</li>
     *   <li>执行选中转换的动作</li>
     *   <li>返回转换后的目标状态</li>
     * </ol>
     * </p>
     * <p>
     * 注意事项：
     * <ul>
     *   <li>状态机本身是无状态的，不会存储当前状态，需要调用者维护状态信息</li>
     *   <li>如果没有找到匹配的转换，将返回源状态，不会抛出异常</li>
     *   <li>转换动作中的异常不会被状态机捕获，需要在动作实现中妥善处理</li>
     * </ul>
     * </p>
     *
     * @param sourceState 源状态，当前系统所处的状态
     * @param event       要触发的事件
     * @param ctx         用户自定义上下文，包含状态转换可能需要的数据
     * @return 转换后的目标状态，如果没有匹配的转换则返回源状态
     * @throws cn.maple.statemachine.impl.GXStateMachineException 如果状态机未就绪或状态不存在
     */
    S fireEvent(S sourceState, E event, C ctx);

    /**
     * 获取状态机标识符
     * <p>
     * 状态机标识符用于唯一标识一个状态机实例，便于在多状态机环境中区分不同的状态机。
     * 标识符在构建状态机时指定，并在注册到工厂时使用。
     * </p>
     * <p>
     * 使用场景：
     * <ul>
     *   <li>从状态机工厂获取特定的状态机实例</li>
     *   <li>在日志中标识不同的状态机</li>
     *   <li>在调试和监控中区分状态机实例</li>
     * </ul>
     * </p>
     *
     * @return 状态机标识符
     * @see GXStateMachineFactory#get(String) 通过ID从工厂获取状态机
     */
    String getMachineId();

    /**
     * 显示状态机结构
     * <p>
     * 使用访问者模式遍历状态机结构，并将结构信息输出到日志系统。
     * 这对于调试和验证状态机配置非常有用。
     * </p>
     * <p>
     * 输出内容包括：
     * <ul>
     *   <li>状态机ID</li>
     *   <li>所有状态及其ID</li>
     *   <li>每个状态的所有转换，包括源状态、事件、转换类型和目标状态</li>
     * </ul>
     * </p>
     * <p>
     * 内部实现使用{@link cn.maple.statemachine.impl.GXSysOutVisitor}访问者。
     * </p>
     * 
     * @see cn.maple.statemachine.impl.GXSysOutVisitor 系统输出访问者
     */
    void showStateMachine();

    /**
     * 生成PlantUML图
     * <p>
     * 使用访问者模式遍历状态机结构，生成符合PlantUML语法的UML状态图描述。
     * 可以将生成的文本复制到支持PlantUML的工具中查看可视化的状态图。
     * </p>
     * <p>
     * PlantUML是一种通过简单的文本语言来定义图表的工具，可以生成各种UML图，
     * 包括状态图、类图、序列图等。生成的状态图可以直观地展示状态机的结构和转换关系。
     * </p>
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 生成PlantUML代码
     * String plantUML = stateMachine.generatePlantUML();
     * 
     * // 将代码保存到文件
     * try (PrintWriter writer = new PrintWriter("state-machine.puml")) {
     *     writer.write(plantUML);
     * }
     * 
     * // 然后可以使用PlantUML工具将其转换为图像
     * }
     * </pre>
     * </p>
     * <p>
     * 内部实现使用{@link cn.maple.statemachine.impl.GXPlantUMLVisitor}访问者。
     * </p>
     *
     * @return PlantUML格式的状态图文本
     * @see cn.maple.statemachine.impl.GXPlantUMLVisitor PlantUML访问者
     */
    String generatePlantUML();
}
