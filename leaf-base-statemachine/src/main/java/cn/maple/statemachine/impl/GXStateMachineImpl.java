package cn.maple.statemachine.impl;

import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXStateMachine;
import cn.maple.statemachine.GXTransition;
import cn.maple.statemachine.GXVisitor;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 状态机实现类
 * <p>
 * 出于性能考虑，状态机被设计为"无状态"的。
 * 一旦构建完成，它可以被多个线程共享使用。
 * </p>
 * 
 * <p>
 * 一个副作用是，由于状态机本身是无状态的，我们无法从状态机中获取当前状态。
 * 状态信息需要由调用者维护。
 * </p>
 * 
 * <p>
 * 线程安全性：
 * <ul>
 *   <li>状态机实例是线程安全的，可以在多线程环境中并发使用</li>
 *   <li>状态转换的执行是无副作用的，不会修改状态机内部状态</li>
 *   <li>状态机不维护任何会话状态，每次调用都是独立的</li>
 *   <li>如果在转换动作中有共享资源访问，需要调用者自行处理同步</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 性能优化：
 * <ul>
 *   <li>状态和转换信息在构建时预先计算并缓存，避免运行时查找开销</li>
 *   <li>使用Map数据结构存储状态和转换，提供O(1)的查找性能</li>
 *   <li>转换条件的评估采用短路策略，一旦找到匹配的转换即停止查找</li>
 *   <li>无状态设计允许单个实例处理大量并发请求，减少内存占用</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 主要功能：
 * <ul>
 *   <li>触发事件并执行状态转换</li>
 *   <li>根据源状态和事件查找匹配的转换</li>
 *   <li>支持访问者模式，用于状态机的可视化和调试</li>
 *   <li>生成状态机的PlantUML图表</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 应用场景：
 * <ul>
 *   <li>订单处理系统：管理订单从创建到完成的各个状态</li>
 *   <li>工作流引擎：控制业务流程的执行路径</li>
 *   <li>游戏逻辑：管理游戏角色或游戏世界的状态变化</li>
 *   <li>设备控制：管理设备的不同工作模式和状态转换</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 使用示例：
 * <pre>
 * {@code
 * // 1. 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = GXStateMachineBuilderFactory.create();
 * 
 * // 2. 配置状态转换
 * // 支付成功转换
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)
 *     .to(OrderStatus.PAID)
 *     .on(OrderEvent.PAY)
 *     .when(ctx -> ctx.getAmount() > 0)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已支付：" + ctx.getOrderId());
 *         // 执行支付后的业务逻辑
 *         notifyPaymentSuccess(ctx.getOrderId());
 *     });
 * 
 * // 发货转换
 * builder.externalTransition()
 *     .from(OrderStatus.PAID)
 *     .to(OrderStatus.DELIVERED)
 *     .on(OrderEvent.SHIP)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单已发货：" + ctx.getOrderId());
 *         // 执行发货业务逻辑
 *         updateLogistics(ctx.getOrderId());
 *     });
 * 
 * // 3. 构建状态机
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * 
 * // 4. 使用状态机处理事件
 * // 创建上下文对象
 * OrderContext context = new OrderContext();
 * context.setOrderId("ORDER_123456");
 * context.setAmount(100);
 * 
 * // 触发支付事件
 * OrderStatus newStatus = stateMachine.fireEvent(OrderStatus.WAIT_PAYMENT, OrderEvent.PAY, context);
 * System.out.println("支付后状态：" + newStatus); // 输出：支付后状态：PAID
 * 
 * // 触发发货事件
 * newStatus = stateMachine.fireEvent(newStatus, OrderEvent.SHIP, context);
 * System.out.println("发货后状态：" + newStatus); // 输出：发货后状态：DELIVERED
 * 
 * // 5. 生成状态机图表（可选）
 * String plantUML = stateMachine.generatePlantUML();
 * saveToFile("order-state-machine.puml", plantUML);
 * }
 * </pre>
 * </p>
 * 
 * <p>
 * 注意事项：
 * <ul>
 *   <li>状态机不存储当前状态，需要调用者跟踪并提供当前状态</li>
 *   <li>转换条件和动作应避免副作用，保持幂等性</li>
 *   <li>复杂业务逻辑应封装在上下文对象中，而非直接写在转换动作里</li>
 *   <li>对于大规模状态机，建议按业务域拆分为多个小型状态机</li>
 * </ul>
 * </p>
 */
public class GXStateMachineImpl<S, E, C> implements GXStateMachine<S, E, C> {
    /**
     * 状态映射
     * <p>
     * 存储状态机中所有状态的映射，键为状态ID，值为状态对象。
     * 这是状态机的核心数据结构，用于快速查找和访问状态。
     */
    private final Map<S, GXState<S, E, C>> stateMap;

    /**
     * 状态机ID
     * <p>
     * 状态机的唯一标识符，用于在日志和调试中区分不同的状态机实例。
     */
    private String machineId;

    /**
     * 就绪标志
     * <p>
     * 表示状态机是否已完成初始化并准备好处理事件。
     * 只有当此标志为true时，状态机才能正常工作。
     */
    private boolean ready;

    /**
     * 构造函数
     * <p>
     * 创建一个新的状态机实例，使用指定的状态映射。
     * 注意：创建后需要设置machineId和ready标志才能使用。
     *
     * @param stateMap 状态映射，键为状态ID，值为状态对象
     */
    public GXStateMachineImpl(Map<S, GXState<S, E, C>> stateMap) {
        this.stateMap = stateMap;
    }

    /**
     * 触发事件
     * <p>
     * 根据当前状态和事件，执行状态转换并返回新的状态ID。
     * 如果没有匹配的转换，则保持原状态不变。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>检查状态机是否就绪</li>
     *   <li>查找匹配的转换</li>
     *   <li>如果找到转换，执行转换并返回新状态</li>
     *   <li>如果没有找到转换，返回原状态</li>
     * </ol>
     *
     * @param sourceStateId 源状态ID
     * @param event 触发事件
     * @param ctx 上下文对象，包含转换所需的数据
     * @return 转换后的状态ID，如果没有转换则返回源状态ID
     * @throws GXStateMachineException 如果状态机未就绪或状态不存在
     */
    @Override
    public S fireEvent(S sourceStateId, E event, C ctx) {
        isReady();
        GXTransition<S, E, C> transition = routeTransition(sourceStateId, event, ctx);

        if (transition == null) {
            GXDebugger.debug("There is no Transition for " + event);
            return sourceStateId;
        }

        return transition.transit(ctx, false).getId();
    }

    /**
     * 路由转换
     * <p>
     * 根据源状态ID、事件和上下文，查找匹配的转换。
     * 如果有多个转换匹配同一个事件，会按以下规则选择：
     * <ol>
     *   <li>如果有满足条件的转换，选择第一个满足条件的转换</li>
     *   <li>如果没有满足条件的转换但有无条件转换，选择第一个无条件转换</li>
     *   <li>如果没有匹配的转换，返回null</li>
     * </ol>
     *
     * @param sourceStateId 源状态ID
     * @param event 触发事件
     * @param ctx 上下文对象
     * @return 匹配的转换，如果没有则返回null
     */
    private GXTransition<S, E, C> routeTransition(S sourceStateId, E event, C ctx) {
        GXState<S, E, C> sourceState = getState(sourceStateId);

        List<GXTransition<S, E, C>> transitions = sourceState.getEventTransitions(event);

        if (transitions == null || transitions.isEmpty()) {
            return null;
        }

        GXTransition<S, E, C> transit = null;
        for (GXTransition<S, E, C> transition : transitions) {
            if (transition.getCondition() == null) {
                transit = transition;
            } else if (transition.getCondition().isSatisfied(ctx)) {
                transit = transition;
                break;
            }
        }

        return transit;
    }

    /**
     * 获取状态
     * <p>
     * 根据状态ID获取对应的状态对象。
     * 如果状态不存在，抛出异常并显示状态机信息以便调试。
     *
     * @param currentStateId 当前状态ID
     * @return 状态对象
     * @throws GXStateMachineException 如果状态不存在
     */
    private GXState<S, E, C> getState(S currentStateId) {
        GXState<S, E, C> state = GXStateHelper.getState(stateMap, currentStateId);
        if (Objects.isNull(state)) {
            showStateMachine();
            throw new GXStateMachineException(currentStateId + " is not found, please check state machine");
        }
        return state;
    }

    /**
     * 检查状态机是否就绪
     * <p>
     * 验证状态机是否已完成初始化并准备好处理事件。
     * 如果状态机未就绪，抛出异常。
     *
     * @throws GXStateMachineException 如果状态机未就绪
     */
    private void isReady() {
        if (!ready) {
            throw new GXStateMachineException("State machine is not built yet, can not work");
        }
    }

    /**
     * 接受访问者
     * <p>
     * 实现访问者模式，允许访问者对象访问状态机及其所有状态。
     * 这通常用于状态机的可视化、调试或序列化。
     * <p>
     * 访问流程：
     * <ol>
     *   <li>调用访问者的visitOnEntry方法访问状态机</li>
     *   <li>依次让访问者访问状态机中的所有状态</li>
     *   <li>调用访问者的visitOnExit方法完成访问</li>
     * </ol>
     *
     * @param visitor 访问者对象
     * @return 访问结果字符串
     */
    @Override
    public String accept(GXVisitor visitor) {
        StringBuilder sb = new StringBuilder();
        sb.append(visitor.visitOnEntry(this));
        for (GXState<S, E, C> state : stateMap.values()) {
            sb.append(state.accept(visitor));
        }
        sb.append(visitor.visitOnExit(this));
        return sb.toString();
    }

    /**
     * 显示状态机
     * <p>
     * 使用系统输出访问者打印状态机的结构信息到日志。
     * 这对于调试和理解状态机的配置非常有用。
     */
    @Override
    public void showStateMachine() {
        GXSysOutVisitor sysOutVisitor = new GXSysOutVisitor();
        accept(sysOutVisitor);
    }

    /**
     * 生成PlantUML图表
     * <p>
     * 生成状态机的PlantUML表示，可用于可视化状态机结构。
     * PlantUML是一种通过简单的文本语言来定义图表的工具。
     * <p>
     * 使用示例：
     * <pre>
     * {@code
     * // 生成PlantUML代码
     * String plantUML = stateMachine.generatePlantUML();
     * 
     * // 将代码保存到文件
     * try (PrintWriter out = new PrintWriter("state-machine.puml")) {
     *     out.println(plantUML);
     * }
     * 
     * // 然后可以使用PlantUML工具将其转换为图像
     * }
     * </pre>
     *
     * @return 状态机的PlantUML表示
     */
    @Override
    public String generatePlantUML() {
        GXPlantUMLVisitor plantUMLVisitor = new GXPlantUMLVisitor();
        return accept(plantUMLVisitor);
    }

    /**
     * 获取状态机ID
     * <p>
     * 返回此状态机的唯一标识符。
     *
     * @return 状态机ID
     */
    @Override
    public String getMachineId() {
        return machineId;
    }

    /**
     * 设置状态机ID
     * <p>
     * 设置此状态机的唯一标识符。
     * 通常在构建状态机时由构建器调用。
     *
     * @param machineId 状态机ID
     */
    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    /**
     * 设置就绪标志
     * <p>
     * 标记状态机是否已完成初始化并准备好处理事件。
     * 通常在构建状态机时由构建器调用。
     *
     * @param ready 就绪标志，true表示状态机已就绪
     */
    public void setReady(boolean ready) {
        this.ready = ready;
    }
}
