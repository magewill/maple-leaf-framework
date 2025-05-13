# leaf-base-statemachine 状态机模块开发手册

## 1. 模块概述

`leaf-base-statemachine` 是一个轻量级的状态机框架，用于简化系统中状态流转的管理。该模块提供了一种声明式的方式来定义状态、事件和转换规则，使得复杂的状态管理变得简单和可维护。

### 1.1 核心特性

- **声明式API**：提供流畅的链式调用API，使状态机的定义直观易读
- **类型安全**：基于泛型设计，在编译时提供类型检查
- **条件转换**：支持基于条件的状态转换，增强业务逻辑的灵活性
- **可视化支持**：内置PlantUML支持，可以生成状态机图表，便于理解和文档化
- **线程安全**：状态机实例可以被多个线程安全地共享使用
- **无状态设计**：状态机本身不存储当前状态，由调用者维护状态信息

### 1.2 适用场景

- 订单处理流程：订单从创建、支付、发货到完成的状态管理
- 审批流程：各种需要多步骤审批的业务场景
- 任务状态管理：任务从创建、分配、执行到完成的状态流转
- 工作流引擎：作为工作流引擎的核心组件，管理工作项的状态变化
- 游戏状态：管理游戏中角色或环境的状态变化

## 2. 核心组件

### 2.1 状态机（GXStateMachine）

状态机（`GXStateMachine`）是框架的核心接口，定义了事件触发、状态转换、状态机可视化等基本功能。它是一种行为模型，用于描述系统在不同状态下的行为以及状态之间的转换条件。

状态机的主要组成部分包括：
- **状态 (State)**：系统在特定时刻所处的情况。
- **事件 (Event)**：触发状态转换的外部或内部激励。
- **转换 (Transition)**：从一个状态到另一个状态的规则，通常由事件触发，并可能伴随条件和动作。
- **上下文 (Context)**：在状态转换过程中用于传递数据的对象。

该状态机具有以下设计特点：
- **无状态设计**：状态机本身不存储当前状态，当前状态由调用者负责维护和传入。这使得状态机实例可以被安全地重用和共享。
- **线程安全**：状态机实例一旦构建完成，就可以被多个线程安全地共享使用，因为其内部状态是不可变的。
- **可扩展性**：支持通过自定义条件（`GXCondition`）和动作（`GXAction`）来灵活适应各种复杂的业务场景。
- **可视化支持**：内置了对PlantUML的支持，可以方便地生成状态机的UML图表，有助于理解、调试和文档化。

```java
public interface GXStateMachine<S, E, C> extends GXVisitable {
    /**
     * 触发状态机事件。
     * 根据当前状态 (sourceState) 和发生的事件 (event)，以及可选的上下文 (context)，
     * 状态机将查找匹配的转换规则。如果找到满足条件的转换，则执行该转换（包括关联的动作），
     * 并返回转换后的新状态。如果没有找到匹配的转换，或者转换条件不满足，
     * 将返回原始的 sourceState。
     *
     * 状态转换的执行流程大致如下：
     * 1. 查找当前状态下由指定事件触发的所有可能转换。
     * 2. 依次评估每个转换的条件（如果存在）。
     * 3. 选择第一个满足条件的转换（或第一个无条件的转换）来执行。
     * 4. 执行选中转换所关联的动作。
     * 5. 返回转换定义的目标状态。
     *
     * @param sourceState 当前状态
     * @param event 触发的事件
     * @param context 用户自定义的上下文对象，用于传递数据
     * @return 转换后的目标状态；如果没有发生转换，则返回 sourceState
     * @throws cn.maple.statemachine.impl.GXStateMachineException 如果状态机尚未就绪或源状态不存在
     */
    S fireEvent(S sourceState, E event, C context);

    /**
     * 获取状态机的唯一标识符。
     * 此ID在构建状态机时指定，可用于从状态机工厂中获取特定实例，或在日志、监控中区分不同的状态机。
     *
     * @return 状态机ID
     */
    String getMachineId();

    /**
     * 显示状态机的结构。
     * 通常是通过一个默认的访问者（如打印到控制台的访问者）来展示状态机的状态和转换信息。
     * 便于快速预览状态机定义。
     */
    void showStateMachine();

    /**
     * 生成状态机的PlantUML表示文本。
     * 返回的字符串可以直接用于PlantUML工具，生成状态图。
     *
     * @return PlantUML格式的字符串
     */
    String generatePlantUML();

    /**
     * 接受一个访问者。
     * 这是访问者模式的实现，允许外部定义的访问者遍历状态机的内部结构（状态和转换），
     * 以执行自定义操作，例如生成特定格式的文档、进行校验等，而无需修改状态机本身的代码。
     *
     * @param visitor 访问者实例
     * @see GXVisitor
     * @see GXVisitable
     */
    void accept(GXVisitor visitor);
}
```

`GXStateMachine` 接口继承了 `GXVisitable` 接口，表明它可以被 `GXVisitor` 访问。

### 2.2 状态（GXState）

状态（`GXState`）是状态机的基本组成单元，代表系统在某个特定时间点所处的情况或模式。每个状态都有一个唯一的标识符，并且可以包含多个从该状态出发的转换（`GXTransition`）。当状态机处于某个状态时，如果接收到一个事件，它会查找与当前状态和该事件相关联的转换规则，以决定是否以及如何迁移到新的状态。

状态的主要职责包括：
- 维护状态的唯一标识符。
- 管理从该状态出发的所有转换规则。
- 提供查询和添加转换的方法。

```java
public interface GXState<S, E, C> extends GXVisitable {
    /**
     * 获取状态的唯一标识符。
     *
     * @return 状态ID
     */
    S getId();

    /**
     * 为当前状态添加一个新的转换规则。
     *
     * @param event 触发此转换的事件
     * @param target 转换的目标状态
     * @param transitionType 转换的类型 (INTERNAL, LOCAL, EXTERNAL)
     * @return 新创建并添加的转换对象，可以后续配置其条件和动作
     * @see cn.maple.statemachine.impl.GXTransitionType
     */
    GXTransition<S, E, C> addTransition(E event, GXState<S, E, C> target, GXTransitionType transitionType);

    /**
     * 获取当前状态下，由指定事件触发的所有转换规则列表。
     * 一个事件可能对应多个转换（例如，基于不同条件的选择分支）。
     *
     * @param event 要查询的事件
     * @return 与该事件关联的转换列表；如果无匹配转换，则可能返回空列表
     */
    List<GXTransition<S, E, C>> getEventTransitions(E event);

    /**
     * 获取从当前状态出发的所有转换规则的集合。
     *
     * @return 包含所有出站转换的集合
     */
    Collection<GXTransition<S, E, C>> getAllTransitions();
}
```

`GXState` 接口同样继承了 `GXVisitable`，允许被访问者遍历。

### 2.3 转换（GXTransition）

转换（`GXTransition`）定义了状态机从一个源状态（Source State）迁移到一个目标状态（Target State）的具体规则。一个转换通常由一个特定的事件（Event）触发，并且可以包含一个可选的条件（`GXCondition`）来决定转换是否发生，以及一个可选的动作（`GXAction`）在转换执行时被调用。

转换的主要组成部分：
- **源状态 (Source State)**：转换的起始状态。
- **目标状态 (Target State)**：转换成功后进入的状态。
- **事件 (Event)**：触发此转换的信号。
- **条件 (Condition)**：一个布尔表达式，只有当其评估为真时，转换才会发生。如果无条件，则事件触发即发生转换。
- **动作 (Action)**：在状态转换过程中执行的一段逻辑代码，例如更新数据、发送通知等。
- **类型 (Type)**：定义转换的行为特性，分为内部（`INTERNAL`）、本地（`LOCAL`）和外部（`EXTERNAL`）三种。

```java
public interface GXTransition<S, E, C> {
    /** @return 转换的源状态 */
    GXState<S, E, C> getSource();
    /** 设置转换的源状态 */
    void setSource(GXState<S, E, C> state);

    /** @return 触发转换的事件 */
    E getEvent();
    /** 设置触发转换的事件 */
    void setEvent(E event);

    /** @return 转换的目标状态 */
    GXState<S, E, C> getTarget();
    /** 设置转换的目标状态 */
    void setTarget(GXState<S, E, C> state);

    /** @return 转换的类型 (INTERNAL, LOCAL, EXTERNAL) */
    GXTransitionType getType();
    /** 设置转换的类型 */
    void setType(GXTransitionType type);

    /** @return 转换的条件；如果无条件，则返回null */
    GXCondition<C> getCondition();
    /** 设置转换的条件 */
    void setCondition(GXCondition<C> condition);

    /** @return 转换的动作；如果无动作，则返回null */
    GXAction<S, E, C> getAction();
    /** 设置转换的动作 */
    void setAction(GXAction<S, E, C> action);

    /**
     * 执行状态转换。
     * 此方法通常由状态机内部调用。
     * @param ctx 上下文对象
     * @param checkCondition 是否需要检查条件（通常为true，但在fireEvent内部路由后可能为false）
     * @return 转换后的目标状态；如果条件不满足，则返回源状态
     */
    GXState<S, E, C> transit(C ctx, boolean checkCondition);

    /**
     * 评估转换的条件是否满足。
     * @param ctx 上下文对象
     * @return 如果条件满足或无条件，则返回true；否则返回false
     */
    boolean evaluate(C ctx);

    /**
     * 执行转换的动作。
     * @param source 源状态ID
     * @param target 目标状态ID
     * @param event 触发事件
     * @param ctx 上下文对象
     */
    void executeAction(S source, S target, E event, C ctx);
}
```

#### 2.3.1 转换类型（GXTransitionType）

`GXTransitionType` 是一个枚举类型，定义了状态转换的三种不同行为模式：

-   **`EXTERNAL` (外部转换)**：这是最常见的转换类型。当外部转换发生时，状态机会完全退出源状态（执行源状态的退出动作，如果有的话），然后进入目标状态（执行目标状态的进入动作，如果有的话）。即使源状态和目标状态是同一个，也会执行退出和进入动作。
    ```java
    // 示例：从 WAIT_PAYMENT 外部转换到 PAID
    builder.externalTransition()
        .from(OrderStatus.WAIT_PAYMENT)
        .to(OrderStatus.PAID)
        .on(OrderEvent.PAY);
    ```

-   **`INTERNAL` (内部转换)**：内部转换发生时，状态机不会退出或进入任何状态，即不会触发当前状态的退出或进入动作。它主要用于在当前状态内部处理事件并执行某些动作，而不改变状态本身。源状态和目标状态必须是同一个。
    ```java
    // 示例：在 PAID 状态内部处理 UPDATE_INFO 事件
    builder.internalTransition()
        .within(OrderStatus.PAID) // 源状态和目标状态都是 PAID
        .on(OrderEvent.UPDATE_INFO)
        .perform((from, to, event, ctx) -> {
            System.out.println("订单信息已更新，但状态仍为PAID");
        });
    ```

-   **`LOCAL` (本地转换)**：本地转换通常用于复合状态（即包含子状态的状态）的场景。当本地转换发生时，如果源状态和目标状态位于同一个复合状态内部，则状态机不会退出该复合状态，但会退出源子状态并进入目标子状态。如果源状态和目标状态是同一个简单状态，其行为类似于外部转换。
    *（注意：当前 `leaf-base-statemachine` 的实现中，复合状态的概念可能没有完全体现在API层面，`LOCAL` 转换的具体行为需要参照其实现细节。在简单状态机中，`LOCAL` 和 `EXTERNAL` 的行为可能相似。）*

选择正确的转换类型对于精确控制状态机的行为至关重要。

### 2.4 条件（GXCondition）

条件用于决定是否执行状态转换，增加了状态转换的灵活性。

```java
public interface GXCondition<C> {
    // 判断条件是否满足
    boolean isSatisfied(C context);
    
    // 获取条件名称
    default String name() {
        return this.getClass().getSimpleName();
    }
}
```

### 2.5 动作（GXAction）

动作定义了状态转换时要执行的操作，封装了业务逻辑。

```java
public interface GXAction<S, E, C> {
    // 执行动作
    void execute(S from, S to, E event, C context);
}
```

### 2.6 状态机构建器（GXStateMachineBuilder）

`GXStateMachineBuilder` 是用于以声明方式定义状态机结构的核心接口。它提供了一套流畅的、链式调用的API，使得状态、事件和转换规则的配置过程直观且易于阅读。通过构建器，开发者可以指定状态之间的转换、转换的触发事件、条件以及执行的动作。

通常通过 `GXStateMachineBuilderFactory.create()` 方法获取构建器实例。

```java
public interface GXStateMachineBuilder<S, E, C> {
    // 创建外部转换构建器
    GXExternalTransitionBuilder<S, E, C> externalTransition();
    
    // 创建多重外部转换构建器
    GXExternalTransitionsBuilder<S, E, C> externalTransitions();
    
    // 创建内部转换构建器
    GXInternalTransitionBuilder<S, E, C> internalTransition();
    
    // 构建状态机
    GXStateMachine<S, E, C> build(String machineId);
}
```

## 3. 使用指南

### 3.1 基本使用流程

使用状态机框架的基本步骤如下：

1. 定义状态和事件（通常使用枚举）
2. 创建状态机构建器
3. 配置状态转换规则
4. 构建状态机
5. 使用状态机处理事件

### 3.2 示例代码

#### 3.2.1 定义状态、事件和上下文

状态（State）、事件（Event）和上下文（Context）是状态机的基本元素。通常建议使用Java枚举（Enum）来定义状态和事件，以获得类型安全和更好的可读性。上下文对象则是一个普通的Java类（POJO），用于在状态转换过程中携带和传递业务数据。

```java
// 定义订单状态 (State)
public enum OrderStatus {
    WAIT_PAYMENT,    // 等待支付
    PAID,            // 已支付
    DELIVERING,      // 配送中
    RECEIVED,        // 已签收
    CANCELLED        // 已取消
}

// 定义订单事件 (Event)
public enum OrderEvent {
    PAY,             // 支付事件
    DELIVER,         // 发货事件
    RECEIVE,         // 收货事件
    CANCEL,          // 取消事件
    UPDATE,          // 更新事件 (用于条件选择示例)
    REMIND           // 提醒事件 (用于内部转换示例)
}

// 定义上下文 (Context)，用于传递数据
public class OrderContext {
    private String orderId;
    private double amount;
    private String deliveryAddress;
    private String updateType; // 用于条件选择示例

    public OrderContext(String orderId) {
        this.orderId = orderId;
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getUpdateType() { return updateType; }
    public void setUpdateType(String updateType) { this.updateType = updateType; }
}
```

#### 3.2.2 创建状态机

```java
// 创建状态机构建器
GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = 
    GXStateMachineBuilderFactory.create();

// 配置状态转换 - 支付流程
builder.externalTransition()
    .from(OrderStatus.WAIT_PAYMENT)
    .to(OrderStatus.PAID)
    .on(OrderEvent.PAY)
    .when(ctx -> ctx.getAmount() > 0) // 添加条件：金额必须大于0
    .perform((from, to, event, ctx) -> {
        System.out.println("订单已支付：" + ctx.getOrderId());
        // 执行支付后的业务逻辑，如更新订单状态、发送通知等
    });

// 配置状态转换 - 发货流程
builder.externalTransition()
    .from(OrderStatus.PAID)
    .to(OrderStatus.DELIVERING)
    .on(OrderEvent.DELIVER)
    .perform((from, to, event, ctx) -> {
        System.out.println("订单已发货：" + ctx.getOrderId());
    });

// 配置状态转换 - 收货流程
builder.externalTransition()
    .from(OrderStatus.DELIVERING)
    .to(OrderStatus.RECEIVED)
    .on(OrderEvent.RECEIVE)
    .perform((from, to, event, ctx) -> {
        System.out.println("订单已签收：" + ctx.getOrderId());
    });

// 构建状态机
GXStateMachine<OrderStatus, OrderEvent, OrderContext> stateMachine = 
    builder.build("订单状态机");
```

#### 3.2.3 使用状态机

```java
// 创建上下文对象
OrderContext context = new OrderContext();
context.setOrderId("ORDER_123456");
context.setAmount(100);

// 触发支付事件
OrderStatus newStatus = stateMachine.fireEvent(
    OrderStatus.WAIT_PAYMENT, OrderEvent.PAY, context);
System.out.println("支付后状态：" + newStatus); // 输出：支付后状态：PAID

// 触发发货事件
newStatus = stateMachine.fireEvent(newStatus, OrderEvent.DELIVER, context);
System.out.println("发货后状态：" + newStatus); // 输出：发货后状态：DELIVERING

// 触发收货事件
newStatus = stateMachine.fireEvent(newStatus, OrderEvent.RECEIVE, context);
System.out.println("收货后状态：" + newStatus); // 输出：收货后状态：RECEIVED
```

### 3.3 高级特性

#### 3.3.1 条件选择（Choice Transition）

状态机支持基于条件的转换选择。这意味着对于同一个源状态和同一个触发事件，可以定义多个转换路径，每个路径关联一个不同的条件（`GXCondition`）。当事件发生时，状态机会评估这些条件，并选择第一个满足条件的转换来执行。这为实现复杂的业务逻辑分支提供了灵活性。

如果多个转换的条件都满足，或者存在无条件的转换，则通常会选择在构建器中先定义的那个满足条件的转换。因此，条件的定义顺序可能很重要。

```java
// 假设在 PAID 状态，收到 UPDATE 事件
// 条件1: 如果 updateType 是 "ADDRESS"
builder.internalTransition() // 内部转换，不改变 PAID 状态
    .within(OrderStatus.PAID)
    .on(OrderEvent.UPDATE)
    .when(ctx -> "ADDRESS".equals(ctx.getUpdateType()))
    .perform((from, to, event, ctx) -> {
        System.out.println("订单 [" + ctx.getOrderId() + "] 更新配送地址为: " + ctx.getDeliveryAddress());
        // 实际业务逻辑：更新数据库中的地址信息
    });

// 条件2: 如果 updateType 是 "CANCEL"
builder.externalTransition()
    .from(OrderStatus.PAID)
    .to(OrderStatus.CANCELLED) // 外部转换到 CANCELLED 状态
    .on(OrderEvent.UPDATE)
    .when(ctx -> "CANCEL".equals(ctx.getUpdateType()))
    .perform((from, to, event, ctx) -> {
        System.out.println("订单 [" + ctx.getOrderId() + "] 已取消。");
        // 实际业务逻辑：更新订单状态为已取消，可能触发退款等
    });

// 使用示例
OrderContext contextAddress = new OrderContext("ORDER_789");
contextAddress.setUpdateType("ADDRESS");
contextAddress.setDeliveryAddress("新地址123号");
stateMachine.fireEvent(OrderStatus.PAID, OrderEvent.UPDATE, contextAddress);
// 输出: 订单 [ORDER_789] 更新配送地址为: 新地址123号

OrderContext contextCancel = new OrderContext("ORDER_012");
contextCancel.setUpdateType("CANCEL");
stateMachine.fireEvent(OrderStatus.PAID, OrderEvent.UPDATE, contextCancel);
// 输出: 订单 [ORDER_012] 已取消。
// 此时订单状态变为 CANCELLED
```

#### 3.3.2 多源状态转换

可以定义多个源状态到同一个目标状态的转换。

```java
// 多个状态可以通过同一个事件转换到相同的目标状态
builder.externalTransitions()
    .fromAmong(OrderStatus.WAIT_PAYMENT, OrderStatus.PAID, OrderStatus.DELIVERING)
    .to(OrderStatus.CANCELLED)
    .on(OrderEvent.CANCEL)
    .perform((from, to, event, ctx) -> {
        System.out.println("订单已取消，原状态：" + from);
    });
```

#### 3.3.3 内部转换（Internal Transition）

内部转换（`internalTransition()`）是一种特殊的转换，它在当前状态内部响应事件并执行动作，但不会导致状态的退出和重新进入。也就是说，状态本身保持不变，相关的进入/退出动作（如果定义了的话）不会被触发。这对于处理那些不应引起状态迁移，但需要在当前状态下执行某些逻辑的事件非常有用。

关于内部转换的更详细说明，请参考章节 [2.3.1 转换类型（GXTransitionType）](#231-转换类型gxtransitiontype)。

```java
// 示例：当订单处于 PAID 状态时，接收到 REMIND 事件，发送发货提醒
builder.internalTransition()
    .within(OrderStatus.PAID) // 状态保持在 PAID
    .on(OrderEvent.REMIND)
    .perform((from, to, event, ctx) -> {
        // from 和 to 都是 OrderStatus.PAID
        System.out.println("订单 [" + ctx.getOrderId() + "]: 已发送发货提醒邮件/短信。");
        // 实际业务逻辑：调用通知服务发送提醒
    });

// 使用示例
OrderContext remindContext = new OrderContext("ORDER_REMIND_001");
OrderStatus statusBeforeRemind = OrderStatus.PAID;
OrderStatus statusAfterRemind = stateMachine.fireEvent(statusBeforeRemind, OrderEvent.REMIND, remindContext);

System.out.println("提醒前状态: " + statusBeforeRemind);
System.out.println("提醒后状态: " + statusAfterRemind); // 状态仍为 PAID
// 输出:
// 订单 [ORDER_REMIND_001]: 已发送发货提醒邮件/短信。
// 提醒前状态: PAID
// 提醒后状态: PAID
```

#### 3.3.4 状态机可视化与访问者模式

`leaf-base-statemachine` 框架通过访问者（Visitor）模式提供了对状态机结构进行操作和展示的扩展能力。核心接口包括 `GXVisitor` 和 `GXVisitable`（由 `GXStateMachine` 和 `GXState` 实现）。

**`GXVisitor` 接口**：
```java
public interface GXVisitor {
    char LF = '\n'; // 换行符
    String visitOnEntry(GXStateMachine<?, ?, ?> visitable); // 访问状态机开始
    String visitOnExit(GXStateMachine<?, ?, ?> visitable);  // 访问状态机结束
    String visitOnEntry(GXState<?, ?, ?> visitable);      // 访问状态开始
    String visitOnExit(GXState<?, ?, ?> visitable);        // 访问状态结束
}
```

通过实现 `GXVisitor` 接口，可以创建自定义的访问者来执行特定任务，例如：
-   生成PlantUML图表（内置实现 `GXPlantUMLVisitor`）
-   将状态机结构打印到控制台（内置实现 `GXSysOutVisitor`）
-   校验状态机配置的合法性
-   将状态机定义导出为其他格式（如XML, JSON）

**使用内置访问者生成PlantUML图表**：

```java
// 1. 显示状态机结构到控制台 (使用 GXSysOutVisitor)
System.out.println("--- 状态机结构 (showStateMachine) ---");
stateMachine.showStateMachine(); 
// showStateMachine() 内部调用了 accept(new GXSysOutVisitor(System.out))

// 2. 生成PlantUML图表字符串 (使用 GXPlantUMLVisitor)
System.out.println("\n--- PlantUML 定义 ---");
String plantUMLDefinition = stateMachine.generatePlantUML(); 
// generatePlantUML() 内部调用了 accept(new GXPlantUMLVisitor())
System.out.println(plantUMLDefinition);

/*
生成的 plantUMLDefinition 类似如下内容：
@startuml
hide empty description
[*] --> WAIT_PAYMENT
WAIT_PAYMENT --> PAID : PAY [amount > 0]
PAID --> DELIVERING : DELIVER
DELIVERING --> RECEIVED : RECEIVE
PAID --> CANCELLED : UPDATE [updateType == CANCEL]
PAID -> PAID : UPDATE [updateType == ADDRESS]
PAID -> PAID : REMIND
WAIT_PAYMENT --> CANCELLED : CANCEL
PAID --> CANCELLED : CANCEL
DELIVERING --> CANCELLED : CANCEL
@enduml

可以将此字符串保存到 .puml 文件，然后使用 PlantUML 工具 (如 http://www.plantuml.com/plantuml) 生成图像。
*/
```

通过这种方式，状态机的核心逻辑与展示逻辑解耦，增强了框架的灵活性和可扩展性。

### 3.4 状态机实现细节 (`GXStateMachineImpl`)

`GXStateMachineImpl` 是 `GXStateMachine` 接口的默认实现。它在设计上考虑了性能和线程安全：

-   **无状态与线程安全**：状态机实例本身是无状态的（不存储当前业务状态），这使得一旦构建完成，同一个状态机实例可以被多个线程安全地共享和并发使用。实际的当前状态由调用方在 `fireEvent` 时传入。
-   **性能优化**：
    -   状态和转换信息在构建时（通过 `GXStateMachineBuilder`）被处理并存储在高效的数据结构中（如 `Map`），以便在运行时快速查找。
    -   转换条件的评估采用短路逻辑，一旦找到第一个满足条件的转换就会停止搜索。

### 3.5 调试 (`GXDebugger`)

框架提供了一个简单的调试工具类 `cn.maple.statemachine.impl.GXDebugger`，用于控制状态机内部详细日志的输出。这在开发和问题排查阶段非常有用。

-   `GXDebugger.enableDebug()`: 开启调试日志输出（基于SLF4J的DEBUG级别）。
-   `GXDebugger.disableDebug()`: 关闭调试日志输出。
-   `GXDebugger.isDebugEnabled()`: 检查调试模式是否开启。

默认情况下，调试模式是关闭的。日志的具体输出行为依赖于项目中配置的SLF4J实现（如Logback, Log4j2）。

```java
// 在应用初始化时或需要调试时开启
GXDebugger.enableDebug();

// ... 执行状态机操作 ...

// 不需要时可以关闭
// GXDebugger.disableDebug();
```

## 4. 最佳实践

### 4.1 状态和事件定义

- 使用枚举定义状态和事件，提供类型安全
- 状态和事件命名应具有描述性，反映业务含义
- 为枚举添加注释，说明每个状态和事件的含义

### 4.2 转换规则设计

- 保持转换规则的简单性和清晰性
- 避免复杂的条件逻辑，必要时拆分为多个简单条件
- 确保所有可能的状态转换都有明确定义

### 4.3 动作实现

- 动作应该是幂等的，避免重复执行导致数据不一致
- 避免在动作中抛出未检查异常，以免中断状态机的正常流程
- 对于耗时操作，考虑使用异步方式执行，避免阻塞状态机

### 4.4 状态机管理与创建

-   **唯一ID**：为每个状态机定义指定一个唯一的字符串ID（在 `build(String machineId)` 时传入）。这个ID有助于在复杂应用中管理和区分不同的状态机实例，也常用于日志记录和监控。
-   **初始化时机**：状态机定义通常是静态的，可以在应用程序启动时构建和初始化所有需要的状态机实例。这样可以避免在运行时重复构建，提高性能。
-   **工厂创建**：使用 `GXStateMachineBuilderFactory.create()` 来获取状态机构建器的实例。这是推荐的创建方式，它隐藏了构建器具体实现类的细节。
    ```java
    // 获取构建器
    GXStateMachineBuilder<OrderStatus, OrderEvent, OrderContext> builder = 
        GXStateMachineBuilderFactory.create();
    // ... 配置转换 ...
    // 构建并指定ID
    GXStateMachine<OrderStatus, OrderEvent, OrderContext> orderStateMachine = 
        builder.build("OrderStateMachine_v1");
    ```
-   **状态机注册与复用（可选）**：虽然当前 `GXStateMachineBuilderFactory` 仅用于创建构建器，但在更复杂的应用中，可以考虑实现一个状态机注册表（Registry）或使用依赖注入框架（如Spring）来管理状态机实例的生命周期和复用。`leaf-base-statemachine` 本身不直接提供注册表功能，但其设计兼容此类用法。

## 5. 常见问题

### 5.1 状态机ID冲突

**问题**：如果尝试使用一个已经存在的ID来构建或注册一个新的状态机实例（例如，在一个自定义的状态机工厂或注册表中），可能会导致冲突或覆盖。

**解决方案**：确保为每个独立的状态机定义分配一个全局唯一的ID。在 `builder.build("uniqueMachineId")` 时仔细选择ID。

### 5.2 条件评估顺序与优先级

**问题**：当同一个源状态、同一个事件对应多个转换，并且这些转换的条件有重叠（即多个条件可能同时为真）时，状态机会选择哪一个转换？

**解决方案**：状态机实现通常会按照转换规则在构建器中被添加的顺序来评估条件。它会选择第一个条件评估为 `true` 的转换。因此：
1.  **确保条件互斥**：最佳实践是设计互斥的条件，使得在任何给定上下文中，最多只有一个条件为真。
2.  **利用定义顺序**：如果条件无法完全互斥，可以将更 специфичные (specific) 或更高优先级的条件放在前面定义。将最通用的或默认的转换（例如无条件的转换）放在最后定义。

    ```java
    // 高优先级条件先定义
    builder.externalTransition().from(S.A).to(S.B).on(E.X).when(conditionHighPriority).perform(...);
    // 低优先级条件后定义
    builder.externalTransition().from(S.A).to(S.C).on(E.X).when(conditionLowPriority).perform(...);
    // 默认或无条件转换最后定义
    builder.externalTransition().from(S.A).to(S.D).on(E.X).perform(...); // 无条件
    ```

### 5.3 状态丢失问题

**问题**：状态转换后，新状态没有被正确保存。

**解决方案**：状态机本身不存储状态，需要调用者负责保存状态转换的结果。

```java
// 正确的状态保存方式
OrderStatus currentStatus = OrderStatus.WAIT_PAYMENT;
OrderContext context = new OrderContext();

// 触发事件并保存新状态
currentStatus = stateMachine.fireEvent(currentStatus, OrderEvent.PAY, context);
// 将新状态保存到数据库或其他持久化存储
orderRepository.updateStatus(orderId, currentStatus);
```

## 6. 总结

`leaf-base-statemachine` 模块提供了一个强大而灵活的状态机框架，可以有效地管理复杂的状态转换逻辑。通过声明式的API和类型安全的设计，使得状态管理变得简单、可维护和可测试。该框架适用于各种需要状态管理的业务场景，如订单处理、审批流程、任务管理等。

通过本开发手册，开发人员可以快速了解状态机的核心概念、使用方法和最佳实践，从而在实际项目中高效地应用状态机框架，提高代码质量和开发效率。