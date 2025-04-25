package cn.maple.statemachine.builder;

import cn.maple.statemachine.GXAction;
import cn.maple.statemachine.GXCondition;
import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXTransition;
import cn.maple.statemachine.impl.GXStateHelper;
import cn.maple.statemachine.impl.GXTransitionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 多源状态转换构建器实现类
 * <p>
 * 此类实现了GXExternalTransitionsBuilder接口，用于构建多个源状态到一个目标状态的转换。
 * 它继承自GXTransitionBuilderImpl，复用了单一转换构建的基础功能，并扩展了处理多源状态的能力。
 * 这种设计模式允许多个不同的源状态在同一事件触发下转换到相同的目标状态，简化了状态机的配置。
 * <p>
 * 泛型参数说明：
 * <ul>
 *     <li>S - 状态(State)类型，通常使用枚举或字符串</li>
 *     <li>E - 事件(Event)类型，通常使用枚举或字符串</li>
 *     <li>C - 上下文(Context)类型，用于在转换过程中传递数据</li>
 * </ul>
 * <p>
 * 线程安全性：
 * <p>
 * 此类本身不是线程安全的，主要用于状态机的构建阶段，应在单线程环境中使用。
 * 内部使用的ArrayList不是线程安全的，如果在多线程环境中构建状态机，可能导致状态不一致。
 * 构建完成后的状态机是线程安全的，可以在多线程环境中使用。
 * <p>
 * 性能考虑：
 * <p>
 * 多源状态转换构建器通过一次性定义多个源状态到同一目标状态的转换，减少了代码重复和配置工作量。
 * 在内部实现上，为每个源状态创建独立的转换对象，确保状态转换的高效执行。
 * 使用ArrayList存储源状态和转换对象，在构建阶段提供良好的性能，且内存占用较小。
 * <p>
 * 使用示例：
 * <pre>
 * // 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = 
 *     GXStateMachineBuilderFactory.create();
 * 
 * // 定义多源状态转换
 * // 例如：待支付、待确认、待发货状态都可以通过取消事件转换到已取消状态
 * builder.externalTransitions()
 *     .fromAmong(OrderStatus.WAIT_PAYMENT, OrderStatus.WAIT_CONFIRM, OrderStatus.WAIT_DELIVER)
 *     .to(OrderStatus.CANCELED)
 *     .on(OrderEvent.CANCEL)
 *     .when(context -> context.getReason() != null && !context.getReason().isEmpty())
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单从 " + from + " 状态取消，转换到 " + to + "，取消原因：" + ctx.getReason());
 *         // 执行取消订单的业务逻辑
 *         ctx.getOrder().setStatus(to);
 *         ctx.getOrder().setCancelReason(ctx.getReason());
 *         ctx.getOrder().setCancelTime(new Date());
 *         ctx.getOrderService().updateOrder(ctx.getOrder());
 *         
 *         // 可以根据不同的源状态执行不同的逻辑
 *         if (from == OrderStatus.WAIT_PAYMENT) {
 *             // 待支付状态取消，无需退款
 *             ctx.getOrderService().releaseInventory(ctx.getOrder().getId());
 *         } else if (from == OrderStatus.WAIT_DELIVER) {
 *             // 待发货状态取消，需要退款
 *             ctx.getPaymentService().refund(ctx.getOrder().getId());
 *             ctx.getOrderService().releaseInventory(ctx.getOrder().getId());
 *         }
 *     });
 * 
 * // 另一个示例：多个状态都可以转换到退款中状态
 * builder.externalTransitions()
 *     .fromAmong(OrderStatus.PAID, OrderStatus.WAIT_DELIVER, OrderStatus.DELIVERING)
 *     .to(OrderStatus.REFUNDING)
 *     .on(OrderEvent.APPLY_REFUND)
 *     .when(context -> context.getRefundAmount().compareTo(BigDecimal.ZERO) > 0)
 *     .perform((from, to, event, ctx) -> {
 *         System.out.println("订单申请退款，从" + from + "状态转换到" + to + "，退款金额：" + ctx.getRefundAmount());
 *         // 创建退款申请
 *         ctx.getRefundService().createRefundApplication(
 *             ctx.getOrder().getId(),
 *             ctx.getRefundAmount(),
 *             ctx.getRefundReason()
 *         );
 *     });
 * 
 * // 构建状态机并使用
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * 
 * // 创建上下文并设置必要数据
 * OrderContext context = new OrderContext();
 * context.setOrder(order);
 * context.setOrderService(orderService);
 * context.setPaymentService(paymentService);
 * context.setReason("客户取消订单");
 * 
 * // 触发状态转换
 * OrderStatus newStatus = stateMachine.fireEvent(order.getStatus(), OrderEvent.CANCEL, context);
 * </pre>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>fromAmong方法接受可变参数，可以指定任意数量的源状态</li>
 *     <li>所有源状态共享相同的目标状态、事件、条件和动作</li>
 *     <li>在动作(perform)中，可以通过from参数区分原始状态，实现针对不同源状态的差异化处理</li>
 *     <li>如果多个转换适用于同一状态和事件，将选择第一个满足条件的转换执行</li>
 *     <li>确保在调用on方法之前已经设置了源状态(fromAmong)和目标状态(to)</li>
 * </ul>
 */
public class GXTransitionsBuilderImpl<S, E, C> extends GXTransitionBuilderImpl<S, E, C> implements GXExternalTransitionsBuilder<S, E, C> {
    /**
     * 源状态列表
     * <p>
     * 存储多个源状态，这些状态将共享同一个目标状态和转换条件。
     * 用于实现fromAmong方法，允许多个源状态指向同一个目标状态。
     * <p>
     * 线程安全性：
     * <p>
     * 此列表使用ArrayList实现，不是线程安全的。在状态机构建阶段，应确保在单线程环境中操作。
     * 构建完成后，此列表不再被修改，状态机变为不可变对象，可以安全地在多线程环境中使用。
     * <p>
     * 性能考虑：
     * <p>
     * ArrayList提供O(1)的随机访问性能，适合在构建阶段频繁遍历。
     * 源状态列表通常不会很大，内存占用较小，符合大多数业务场景的需求。
     * 声明为final确保引用不可变，增强类型安全性。
     */
    private final List<GXState<S, E, C>> sources = new ArrayList<>();

    /**
     * 转换列表
     * <p>
     * 存储为每个源状态创建的转换对象，用于后续设置条件和动作。
     * 在调用on()方法时，会为每个源状态创建一个转换对象并添加到此列表中。
     * 然后when()和perform()方法会遍历此列表，为所有转换设置相同的条件和动作。
     * <p>
     * 线程安全性：
     * <p>
     * 此列表使用ArrayList实现，不是线程安全的。在状态机构建阶段，应确保在单线程环境中操作。
     * 一旦状态机构建完成，此列表不再被修改，转换关系固定，可以安全地在多线程环境中使用。
     * <p>
     * 性能优化：
     * <p>
     * 预先创建所有转换对象，避免运行时的动态创建开销。
     * 所有源状态共享相同的条件和动作逻辑，减少了重复代码和内存占用。
     * 在执行转换时，根据当前状态和事件直接查找匹配的转换，无需遍历此列表，确保高效执行。
     */
    private final List<GXTransition<S, E, C>> transitions = new ArrayList<>();

    /**
     * 构造函数
     *
     * @param stateMap 状态映射表，存储状态机中的所有状态
     * @param transitionType 转换类型，如EXTERNAL或INTERNAL
     */
    public GXTransitionsBuilderImpl(Map<S, GXState<S, E, C>> stateMap, GXTransitionType transitionType) {
        super(stateMap, transitionType);
    }

    /**
     * 指定多个源状态
     * <p>
     * 此方法允许指定多个源状态，这些状态将共享同一个目标状态、事件、条件和动作。
     * 使用@SafeVarargs注解确保在处理可变参数时不会产生类型安全警告。
     *
     * @param stateIds 源状态ID集合，可以是多个状态
     * @return 源状态构建器接口，用于继续构建状态转换
     */
    @SafeVarargs
    @Override
    public final GXFrom<S, E, C> fromAmong(S... stateIds) {
        for (S stateId : stateIds) {
            sources.add(GXStateHelper.getState(super.stateMap, stateId));
        }
        return this;
    }

    /**
     * 指定触发转换的事件
     * <p>
     * 此方法为每个源状态创建一个到目标状态的转换，并指定触发该转换的事件。
     * 所有创建的转换都添加到transitions列表中，以便后续设置条件和动作。
     *
     * @param event 触发转换的事件
     * @return 事件构建器接口，用于继续构建状态转换
     */
    @Override
    public GXOn<S, E, C> on(E event) {
        for (GXState<S, E, C> source : sources) {
            GXTransition<S, E, C> transition = source.addTransition(event, super.target, super.transitionType);
            transitions.add(transition);
        }
        return this;
    }

    /**
     * 指定转换的条件
     * <p>
     * 此方法为所有已创建的转换设置相同的条件。
     * 只有当条件满足时，转换才会执行。
     *
     * @param condition 转换条件，只有满足条件时才会执行转换
     * @return 条件构建器接口，用于继续构建状态转换
     */
    @Override
    public GXWhen<S, E, C> when(GXCondition<C> condition) {
        for (GXTransition<S, E, C> transition : transitions) {
            transition.setCondition(condition);
        }
        return this;
    }

    /**
     * 指定转换时执行的动作
     * <p>
     * 此方法为所有已创建的转换设置相同的动作。
     * 当转换执行时，会调用此动作。
     *
     * @param action 转换时执行的动作，可以包含业务逻辑
     */
    @Override
    public void perform(GXAction<S, E, C> action) {
        for (GXTransition<S, E, C> transition : transitions) {
            transition.setAction(action);
        }
    }
}
