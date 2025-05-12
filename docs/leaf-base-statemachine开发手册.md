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

状态机是框架的核心接口，定义了事件触发、状态转换等基本功能。

```java
public interface GXStateMachine<S, E, C> {
    // 触发事件，执行状态转换
    S fireEvent(S sourceState, E event, C context);
    
    // 获取状态机ID
    String getMachineId();
    
    // 显示状态机结构
    void showStateMachine();
    
    // 生成PlantUML图表
    String generatePlantUML();
    
    // 接受访问者
    void accept(GXVisitor visitor);
}
```

### 2.2 状态（GXState）

状态是状态机的基本组成元素，代表系统在特定时刻的状态。

```java
public interface GXState<S, E, C> extends GXVisitable {
    // 获取状态标识符
    S getId();
    
    // 添加状态转换
    GXTransition<S, E, C> addTransition(E event, GXState<S, E, C> target, GXTransitionType transitionType);
    
    // 获取指定事件的所有转换
    List<GXTransition<S, E, C>> getEventTransitions(E event);
    
    // 获取所有转换
    Collection<GXTransition<S, E, C>> getAllTransitions();
}
```

### 2.3 转换（GXTransition）

转换定义了从一个状态到另一个状态的规则，包括触发事件、条件和动作。

```java
public interface GXTransition<S, E, C> {
    // 获取源状态
    GXState<S, E, C> getSource();
    
    // 设置源状态
    void setSource(GXState<S, E, C> state);
    
    // 获取触发事件
    E getEvent();
    
    // 设置触发事件
    void setEvent(E event);
    
    // 获取目标状态
    GXState<S, E, C> getTarget();
    
    // 设置目标状态
    void setTarget(GXState<S, E, C> state);
    
    // 获取转换类型
    GXTransitionType getType();
    
    // 设置转换类型
    void setType(GXTransitionType type);
    
    // 获取转换条件
    GXCondition<C> getCondition();
    
    // 设置转换条件
    void setCondition(GXCondition<C> condition);
    
    // 获取转换动作
    GXAction<S, E, C> getAction();
    
    // 设置转换动作
    void setAction(GXAction<S, E, C> action);
    
    // 执行转换
    void execute(S source, S target, E event, C ctx);
    
    // 评估条件
    boolean evaluate(C ctx);
}
```

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

状态机构建器提供了流畅的API来定义状态机的结构。

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

#### 3.2.1 定义状态和事件

```java
// 定义订单状态
enum OrderStatus {
    WAIT_PAYMENT,    // 等待支付
    PAID,            // 已支付
    DELIVERING,      // 配送中
    RECEIVED         // 已签收
}

// 定义订单事件
enum OrderEvent {
    PAY,             // 支付事件
    DELIVER,         // 发货事件
    RECEIVE          // 收货事件
}

// 定义上下文，用于传递数据
class OrderContext {
    private String orderId;
    private double amount;
    private String deliveryAddress;
    
    // getter和setter方法
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

#### 3.3.1 条件选择（Choice）

可以为同一个事件配置多个转换，通过条件来决定使用哪个转换。

```java
// 根据不同条件，选择不同的转换路径
builder.internalTransition()
    .within(OrderStatus.PAID)
    .on(OrderEvent.UPDATE)
    .when(ctx -> "ADDRESS".equals(ctx.getUpdateType()))
    .perform((from, to, event, ctx) -> {
        System.out.println("更新配送地址：" + ctx.getDeliveryAddress());
    });

builder.externalTransition()
    .from(OrderStatus.PAID)
    .to(OrderStatus.CANCELLED)
    .on(OrderEvent.UPDATE)
    .when(ctx -> "CANCEL".equals(ctx.getUpdateType()))
    .perform((from, to, event, ctx) -> {
        System.out.println("取消订单：" + ctx.getOrderId());
    });
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

#### 3.3.3 内部转换

内部转换不会改变状态，但会执行动作。

```java
// 内部转换不改变状态，只执行动作
builder.internalTransition()
    .within(OrderStatus.PAID)
    .on(OrderEvent.REMIND)
    .perform((from, to, event, ctx) -> {
        System.out.println("发送发货提醒");
    });
```

#### 3.3.4 状态机可视化

可以生成状态机的PlantUML图表，便于理解和文档化。

```java
// 显示状态机结构
stateMachine.showStateMachine();

// 生成PlantUML图表
String uml = stateMachine.generatePlantUML();
System.out.println(uml);
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

### 4.4 状态机管理

- 为每个状态机指定唯一的ID，便于管理和获取
- 在应用启动时初始化所有状态机
- 使用工厂方法获取状态机实例，避免重复创建

## 5. 常见问题

### 5.1 状态机注册问题

**问题**：尝试注册已存在ID的状态机时出现异常。

**解决方案**：确保每个状态机都有唯一的ID，避免重复注册。

### 5.2 条件评估问题

**问题**：多个条件都满足时，状态机的行为不确定。

**解决方案**：确保条件之间是互斥的，或者按照优先级顺序定义条件。

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