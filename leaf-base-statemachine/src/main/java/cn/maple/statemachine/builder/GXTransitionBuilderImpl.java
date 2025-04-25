package cn.maple.statemachine.builder;

import cn.maple.statemachine.GXAction;
import cn.maple.statemachine.GXCondition;
import cn.maple.statemachine.GXState;
import cn.maple.statemachine.GXTransition;
import cn.maple.statemachine.impl.GXStateHelper;
import cn.maple.statemachine.impl.GXTransitionType;

import java.util.Map;

/**
 * 状态转换构建器实现类
 * <p>
 * 此类是状态机框架的核心组件之一，实现了多个构建器接口，用于构建单一状态转换。
 * 它采用流式API设计，通过链式调用方法来定义状态转换的各个组成部分。
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
 * 此类本身不是线程安全的，主要用于状态机的构建阶段，通常在应用初始化时使用。
 * 构建完成后的状态机是线程安全的，可以在多线程环境中使用。
 * 状态转换构建器应在单线程环境中使用，避免并发修改导致的不一致状态。
 * <p>
 * 性能考虑：
 * <p>
 * 状态转换的构建是一次性操作，发生在应用启动阶段，因此构建过程的性能不是关键因素。
 * 构建完成后，状态转换的执行效率是关键，本实现通过预先构建转换关系，确保运行时高效执行。
 * <p>
 * 使用示例：
 * <pre>
 * // 创建状态机构建器
 * GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = 
 *     GXStateMachineBuilderFactory.create();
 * 
 * // 定义外部转换（源状态和目标状态不同）
 * builder.externalTransition()
 *     .from(OrderStatus.WAIT_PAYMENT)      // 指定源状态
 *     .to(OrderStatus.WAIT_DELIVER)        // 指定目标状态
 *     .on(OrderEvent.PAYED)                // 指定触发事件
 *     .when(context -> context.getOrder().getAmount() > 0)  // 可选：添加转换条件
 *     .perform((from, to, event, ctx) -> { // 可选：添加转换动作
 *         System.out.println("订单支付完成，状态从 " + from + " 变更为 " + to);
 *         // 执行订单支付后的业务逻辑
 *         ctx.getOrder().setPayTime(new Date());
 *         ctx.getOrderService().updateOrderStatus(ctx.getOrder().getId(), to);
 *     });
 * 
 * // 定义内部转换（源状态和目标状态相同）
 * builder.internalTransition()
 *     .within(OrderStatus.WAIT_DELIVER)     // 指定状态
 *     .on(OrderEvent.UPDATE_DELIVERY_ADDRESS) // 指定触发事件
 *     .when(context -> context.getAddress() != null) // 可选：添加地址非空检查
 *     .perform((from, to, event, ctx) -> { // 可选：添加转换动作
 *         System.out.println("更新订单配送地址，状态保持不变");
 *         // 执行更新地址的业务逻辑
 *         ctx.getOrder().setAddress(ctx.getAddress());
 *         ctx.getOrderService().updateOrderAddress(ctx.getOrder().getId(), ctx.getAddress());
 *     });
 * 
 * // 构建状态机并使用
 * GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = builder.build("订单状态机");
 * 
 * // 创建上下文并设置必要数据
 * OrderContext context = new OrderContext();
 * context.setOrder(order);
 * context.setOrderService(orderService);
 * 
 * // 触发状态转换
 * OrderStatus newStatus = stateMachine.fireEvent(OrderStatus.WAIT_PAYMENT, OrderEvent.PAYED, context);
 * </pre>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>调用顺序很重要，必须先设置状态(from/within)，然后设置事件(on)，最后设置条件(when)和动作(perform)</li>
 *     <li>条件(when)和动作(perform)是可选的，如果不设置条件，转换将无条件执行</li>
 *     <li>同一个事件可以有多个转换，系统会选择第一个满足条件的转换执行</li>
 *     <li>转换动作中抛出的异常不会被状态机捕获，需要调用者自行处理</li>
 * </ul>
 */
class GXTransitionBuilderImpl<S, E, C> implements GXExternalTransitionBuilder<S, E, C>, GXInternalTransitionBuilder<S, E, C>, GXFrom<S, E, C>, GXOn<S, E, C>, GXTo<S, E, C> {
    /**
     * 状态映射表
     * <p>
     * 存储状态机中的所有状态，用于在构建转换时查找和获取状态对象。
     * <p>
     * 线程安全性：
     * <p>
     * 此映射表通常由GXStateMachineBuilderImpl创建并传入，使用ConcurrentHashMap实现，
     * 确保在多线程环境下的安全访问。但构建器本身应在单线程环境中使用，避免并发修改问题。
     * <p>
     * 性能考虑：
     * <p>
     * 状态查找操作频繁发生，使用Map数据结构确保O(1)的查找复杂度，提高状态转换的执行效率。
     */
    final Map<S, GXState<S, E, C>> stateMap;

    /**
     * 转换类型
     * <p>
     * 指定当前构建的转换类型，可以是外部转换(EXTERNAL)或内部转换(INTERNAL)。
     * 外部转换会改变状态，内部转换不会改变状态。
     * <p>
     * 外部转换和内部转换的区别：
     * <ul>
     *     <li>外部转换：源状态和目标状态不同，会触发源状态的退出动作和目标状态的进入动作</li>
     *     <li>内部转换：源状态和目标状态相同，不会触发状态的退出和进入动作，只执行转换动作</li>
     * </ul>
     * <p>
     * 声明为final确保转换类型一旦设置就不能更改，增强类型安全性。
     */
    final GXTransitionType transitionType;

    /**
     * 目标状态
     * <p>
     * 转换执行后的目标状态。对于外部转换，它与源状态不同；对于内部转换，它与源状态相同。
     * 声明为protected以便子类GXTransitionsBuilderImpl可以访问。
     * <p>
     * 在构建过程中，目标状态通过to()或within()方法设置，并在创建转换对象时使用。
     * 对于多源状态转换，所有源状态共享同一个目标状态，因此需要子类访问。
     */
    protected GXState<S, E, C> target;

    /**
     * 源状态
     * <p>
     * 转换的起始状态，事件发生前的状态。
     * <p>
     * 在构建过程中，源状态通过from()或within()方法设置，并在创建转换对象时使用。
     * 声明为private，因为子类GXTransitionsBuilderImpl使用自己的sources列表管理多个源状态。
     * <p>
     * 安全性考虑：
     * <p>
     * 将字段设为private，防止外部直接修改，确保状态转换的完整性和正确性。
     */
    private GXState<S, E, C> source;

    /**
     * 转换对象
     * <p>
     * 表示一个完整的状态转换，包含源状态、目标状态、事件、条件和动作。
     * <p>
     * 在调用on()方法时创建，然后可以通过when()和perform()方法设置条件和动作。
     * 转换对象是状态机执行状态转换的核心，包含了转换的所有必要信息。
     * <p>
     * 性能优化：
     * <p>
     * 转换对象在构建阶段创建，运行时直接使用，避免了动态创建的开销。
     * 条件和动作采用函数式接口实现，支持Lambda表达式，提高代码可读性和维护性。
     */
    private GXTransition<S, E, C> transition;

    /**
     * 构造函数
     *
     * @param stateMap 状态映射表，存储状态机中的所有状态
     * @param transitionType 转换类型，如EXTERNAL或INTERNAL
     */
    public GXTransitionBuilderImpl(Map<S, GXState<S, E, C>> stateMap, GXTransitionType transitionType) {
        this.stateMap = stateMap;
        this.transitionType = transitionType;
    }

    /**
     * 指定转换的源状态
     * <p>
     * 此方法用于外部转换，设置转换的起始状态。
     * 在流式API调用链中，通常是第一个被调用的方法。
     * <p>
     * 调用此方法会从状态映射表中查找对应的状态对象，并将其设置为源状态。
     * 如果状态不存在，GXStateHelper会自动创建一个新的状态对象并添加到映射表中。
     * <p>
     * 安全性考虑：
     * <p>
     * 此方法应在单线程环境中调用，避免并发修改导致的状态不一致。
     * 状态ID应该是不可变的，通常使用枚举或字符串常量。
     *
     * @param stateId 源状态ID，不能为null
     * @return 源状态构建器接口，用于继续构建状态转换
     * @throws IllegalArgumentException 如果stateId为null
     */
    @Override
    public GXFrom<S, E, C> from(S stateId) {
        source = GXStateHelper.getState(stateMap, stateId);
        return this;
    }

    /**
     * 指定转换的目标状态
     * <p>
     * 此方法用于外部转换，设置转换的目标状态。
     * 在流式API调用链中，通常在from()方法之后调用。
     * <p>
     * 调用此方法会从状态映射表中查找对应的状态对象，并将其设置为目标状态。
     * 如果状态不存在，GXStateHelper会自动创建一个新的状态对象并添加到映射表中。
     * <p>
     * 性能优化：
     * <p>
     * 状态对象在首次访问时创建，并缓存在状态映射表中，避免重复创建的开销。
     * 这种延迟初始化策略减少了内存占用，特别是对于大型状态机。
     *
     * @param stateId 目标状态ID，不能为null
     * @return 目标状态构建器接口，用于继续构建状态转换
     * @throws IllegalArgumentException 如果stateId为null
     */
    @Override
    public GXTo<S, E, C> to(S stateId) {
        target = GXStateHelper.getState(stateMap, stateId);
        return this;
    }

    /**
     * 指定内部转换的状态
     * <p>
     * 此方法用于内部转换，设置转换发生的状态。
     * 内部转换的源状态和目标状态是同一个状态，事件触发时状态不会改变。
     * <p>
     * 内部转换的特点：
     * <ul>
     *     <li>状态不会改变，只执行转换动作</li>
     *     <li>不会触发状态的退出和进入动作</li>
     *     <li>适用于需要在同一状态下处理不同事件的场景</li>
     * </ul>
     * <p>
     * 使用场景：
     * <p>
     * 内部转换适用于需要在不改变状态的情况下执行某些操作的场景，例如：
     * <ul>
     *     <li>更新订单的配送地址，但订单状态保持不变</li>
     *     <li>记录日志或发送通知，但不改变业务状态</li>
     *     <li>执行定时检查或刷新操作，但状态保持不变</li>
     * </ul>
     *
     * @param stateId 状态ID，不能为null
     * @return 目标状态构建器接口，用于继续构建状态转换
     * @throws IllegalArgumentException 如果stateId为null
     */
    @Override
    public GXTo<S, E, C> within(S stateId) {
        source = target = GXStateHelper.getState(stateMap, stateId);
        return this;
    }

    /**
     * 指定转换的条件
     * <p>
     * 此方法设置转换的条件，只有当条件满足时，转换才会执行。
     * 如果不设置条件，转换将无条件执行。
     * <p>
     * 条件是一个函数式接口，接收上下文对象作为参数，返回布尔值表示条件是否满足。
     * 可以使用Lambda表达式简洁地定义条件逻辑。
     * <p>
     * 性能考虑：
     * <p>
     * 条件应该是轻量级的，避免在条件判断中执行耗时操作，如数据库查询或远程调用。
     * 如果需要复杂的条件逻辑，建议在上下文中预先计算并存储结果，然后在条件中直接使用。
     * <p>
     * 注意事项：
     * <p>
     * 此方法必须在on()方法之后调用，因为转换对象是在on()方法中创建的。
     * 如果在调用on()方法之前调用此方法，会导致NullPointerException。
     *
     * @param condition 转换条件，只有满足条件时才会执行转换，不能为null
     * @return 条件构建器接口，用于继续构建状态转换
     * @throws NullPointerException 如果在调用on()方法之前调用此方法
     */
    @Override
    public GXWhen<S, E, C> when(GXCondition<C> condition) {
        transition.setCondition(condition);
        return this;
    }

    /**
     * 指定触发转换的事件
     * <p>
     * 此方法设置触发状态转换的事件，并创建转换对象。
     * 在调用此方法之前，必须已经设置了源状态和目标状态。
     * <p>
     * 调用流程：
     * <ol>
     *     <li>从源状态对象中添加一个新的转换，指向目标状态</li>
     *     <li>将创建的转换对象保存到transition字段中，供后续设置条件和动作</li>
     * </ol>
     * <p>
     * 线程安全性：
     * <p>
     * 此方法修改了状态对象的内部结构，应在单线程环境中调用。
     * 一旦状态机构建完成，转换关系就固定下来，可以安全地在多线程环境中使用。
     * <p>
     * 注意事项：
     * <p>
     * 如果同一个源状态对同一个事件定义了多个转换，系统会按照定义顺序选择第一个满足条件的转换执行。
     * 事件类型通常使用枚举或字符串常量，确保类型安全和可维护性。
     *
     * @param event 触发转换的事件，不能为null
     * @return 事件构建器接口，用于继续构建状态转换
     * @throws IllegalStateException 如果在调用此方法之前未设置源状态或目标状态
     */
    @Override
    public GXOn<S, E, C> on(E event) {
        transition = source.addTransition(event, target, transitionType);
        return this;
    }

    /**
     * 指定转换时执行的动作
     * <p>
     * 此方法设置转换执行时的动作，可以包含业务逻辑。
     * 如果不设置动作，转换将只改变状态而不执行任何操作。
     * <p>
     * 动作是一个函数式接口，接收源状态、目标状态、事件和上下文对象作为参数。
     * 可以使用Lambda表达式简洁地定义动作逻辑。
     * <p>
     * 最佳实践：
     * <ul>
     *     <li>动作应该是幂等的，多次执行不会产生副作用</li>
     *     <li>避免在动作中执行长时间运行的操作，如果必要，可以使用异步方式</li>
     *     <li>动作中的异常不会被状态机捕获，需要在动作内部处理或由调用者捕获</li>
     *     <li>动作可以访问上下文对象中的数据，但不应该修改状态机的结构</li>
     * </ul>
     * <p>
     * 注意事项：
     * <p>
     * 此方法必须在on()方法之后调用，因为转换对象是在on()方法中创建的。
     * 如果在调用on()方法之前调用此方法，会导致NullPointerException。
     * 此方法是流式API调用链的最后一个方法，没有返回值。
     *
     * @param action 转换时执行的动作，可以包含业务逻辑，不能为null
     * @throws NullPointerException 如果在调用on()方法之前调用此方法
     */
    @Override
    public void perform(GXAction<S, E, C> action) {
        transition.setAction(action);
    }
}
