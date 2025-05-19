# Leaf-Base-Extension 模块开发手册

## 1. 模块概述

Leaf-Base-Extension 模块是 Maple-Leaf-Framework 框架的核心组成部分，它提供了一套强大且灵活的扩展点机制。该模块旨在帮助开发者轻松应对复杂多变的业务需求，通过面向接口编程和依赖倒置的原则，实现业务逻辑的解耦和动态替换。其核心价值在于允许系统在不修改核心代码的情况下，通过插件式的扩展点实现来适应不同的业务场景、渠道或个性化需求，从而提高系统的可扩展性、可维护性和业务响应速度。

### 1.1 主要功能

-   **扩展点定义**：通过Java接口定义清晰的业务扩展契约。
-   **业务场景标识**：采用 `bizId`（业务）、`useCase`（用例）、`scenario`（场景）三维坐标精确描述和定位业务场景。
-   **注解驱动**：使用 `@GXExtension` 和 `@GXExtensions` 注解简化扩展点实现的声明和元数据配置。
-   **自动注册**：应用启动时，自动扫描并注册所有标记的扩展点实现到统一的扩展仓库。
-   **动态定位与执行**：运行时，根据当前业务场景动态查找并执行最匹配的扩展点实现。
-   **灵活的降级策略**：当找不到完全匹配的扩展点实现时，支持按优先级（精确场景 -> 默认场景 -> 默认用例的默认场景）进行降级查找，保证业务流程的健壮性。
-   **Spring集成**：与Spring框架无缝集成，扩展点实现本身可以是Spring Bean，享受Spring的依赖注入等特性。
-   **AOP兼容**：能够正确处理被Spring AOP代理的扩展点实现类。

### 1.2 核心组件概览

-   **`GXExtensionPoint`**：所有业务扩展点接口必须继承的标记接口。
-   **`GXExtension`**：注解，用于标记一个类是某个特定业务场景的扩展点实现。
-   **`GXExtensions`**：注解，允许一个扩展点实现类服务于多个业务场景，支持注解的重复声明或笛卡尔积组合。
-   **`GXBizScenario`**：封装了业务场景的三维坐标（`bizId`, `useCase`, `scenario`），并提供生成唯一标识和默认场景的能力。
-   **`GXExtensionCoordinate`**：扩展点坐标，由扩展点接口类和业务场景唯一标识组成，作为在扩展仓库中查找扩展点实现的Key。
-   **`GXExtensionRepository`**：扩展点仓库，负责存储所有注册的扩展点实现实例，使用 `ConcurrentHashMap` 保证线程安全。
-   **`GXExtensionRegister`**：扩展点注册器，负责解析扩展点实现类上的注解，并将其注册到 `GXExtensionRepository`。
-   **`GXExtensionBootstrap`**：扩展点引导程序，在Spring容器初始化完成后，调用 `GXExtensionRegister` 完成所有扩展点的注册。
-   **`GXExtensionExecutor`**：扩展点执行器，是业务代码调用扩展点的入口，负责根据业务场景从 `GXExtensionRepository` 查找并执行扩展点方法。
-   **`GXAbstractComponentExecutor`**：`GXExtensionExecutor` 的抽象基类，提供了执行扩展点的模板方法。
-   **`autoconfigure/ExtensionAutoConfiguration`**：Spring Boot自动配置类，负责将上述核心组件注册为Spring Bean。
-   **`exception/GXExtensionException`**：模块专属的运行时异常，用于指示扩展点相关的错误。

## 2. 快速入门

### 2.1 引入依赖

确保项目中已引入 `leaf-base-extension` 模块的Maven依赖：

```xml
<dependency>
    <groupId>cn.maple</groupId>
    <artifactId>leaf-base-extension</artifactId>
    <version>${maple-leaf-framework.version}</version>
</dependency>
```

### 2.2 定义扩展点接口

扩展点接口需要继承 `cn.maple.extension.GXExtensionPoint` 接口。

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.dto.OrderDTO;
import com.example.dto.PayResultDTO;

// 定义支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResultDTO pay(OrderDTO order);
}
```

### 2.3 实现扩展点接口

为不同的业务场景实现扩展点接口，并使用 `@GXExtension` 注解标记。扩展点实现类本身可以是Spring Bean，可以注入其他服务。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXBizScenario;
import com.example.dto.OrderDTO;
import com.example.dto.PayResultDTO;
import com.example.extension.point.PaymentExtPoint;
import org.springframework.stereotype.Component;

// 支付宝支付实现
@Component // 可以是Spring Bean
@GXExtension(bizId = "trade", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExtImpl implements PaymentExtPoint {
    @Override
    public PayResultDTO pay(OrderDTO order) {
        System.out.println("Processing Alipay payment for order: " + order.getOrderId());
        // 支付宝支付具体逻辑
        return new PayResultDTO("SUCCESS", "Alipay payment successful");
    }
}

// 微信支付实现
@Component
@GXExtension(bizId = "trade", useCase = "payment", scenario = "wechatpay")
public class WechatPaymentExtImpl implements PaymentExtPoint {
    @Override
    public PayResultDTO pay(OrderDTO order) {
        System.out.println("Processing WeChat Pay payment for order: " + order.getOrderId());
        // 微信支付具体逻辑
        return new PayResultDTO("SUCCESS", "WeChat Pay payment successful");
    }
}

// 默认支付实现 (用于降级)
@Component
@GXExtension(bizId = "trade", useCase = "payment", scenario = GXBizScenario.DEFAULT_SCENARIO)
public class DefaultPaymentExtImpl implements PaymentExtPoint {
    @Override
    public PayResultDTO pay(OrderDTO order) {
        System.out.println("Processing Default payment for order: " + order.getOrderId());
        // 默认支付逻辑，例如记录日志或返回一个通用提示
        return new PayResultDTO("PENDING", "Default payment processing");
    }
}
```

### 2.4 使用扩展点

在业务代码中注入 `cn.maple.extension.GXExtensionExecutor`，并使用它来执行扩展点方法。

```java
package com.example.service;

import cn.maple.extension.GXBizScenario;
import cn.maple.extension.GXExtensionExecutor;
import com.example.dto.OrderDTO;
import com.example.dto.PayResultDTO;
import com.example.extension.point.PaymentExtPoint;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;

    public PayResultDTO processPayment(OrderDTO order, String paymentChannel) {
        // 1. 创建业务场景
        // bizId: "trade", useCase: "payment", scenario: 根据传入的支付渠道动态决定
        GXBizScenario scenario = GXBizScenario.valueOf("trade", "payment", paymentChannel);

        // 2. 执行扩展点方法
        // extensionExecutor会根据scenario动态查找到对应的PaymentExtPoint实现
        // 并执行其pay方法
        PayResultDTO payResult = extensionExecutor.execute(
                PaymentExtPoint.class, // 扩展点接口类
                scenario,             // 当前业务场景
                extension -> extension.pay(order) // 具体执行的lambda表达式
        );

        System.out.println("Payment result: " + payResult.getMessage());
        return payResult;
    }
}
```

## 3. 核心组件详解

### 3.1 `GXExtensionPoint`

`cn.maple.extension.GXExtensionPoint` 是一个核心标记接口。所有希望被 `leaf-base-extension` 框架管理的业务扩展点接口都必须直接或间接继承此接口。它本身不包含任何方法，其作用是向框架声明“这是一个扩展点定义”。

```java
public interface GXExtensionPoint {
}
```

### 3.2 `GXExtension`

`cn.maple.extension.GXExtension` 是一个类级别注解，用于标记一个Java类是某个 `GXExtensionPoint` 接口的具体实现。它通过 `bizId`、`useCase` 和 `scenario` 三个属性，精确定位该扩展点实现所适用的业务场景。

该注解可以标记在任何实现了 `GXExtensionPoint` 接口的类上。它支持 `@Repeatable(GXExtensions.class)`，允许在同一个类上多次使用，以便一个实现类能够服务于多个独立的业务场景。或者，也可以配合 `@GXExtensions` 注解来更灵活地管理多个业务场景的映射。

**注解属性**：

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(GXExtensions.class)
public @interface GXExtension {
    String bizId() default GXBizScenario.DEFAULT_BIZ_ID;       // 业务ID，默认为 "#defaultBizId#"
    String useCase() default GXBizScenario.DEFAULT_USE_CASE;     // 用例ID，默认为 "#defaultUseCase#"
    String scenario() default GXBizScenario.DEFAULT_SCENARIO;   // 场景ID，默认为 "#defaultScenario#"
}
```

-   `bizId`: 字符串类型，表示业务领域标识。例如：`"trade"`（交易）、`"user"`（用户管理）。如果未指定，则默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase`: 字符串类型，表示在特定业务领域下的用例。例如：`"payment"`（支付）、`"register"`（用户注册）。如果未指定，则默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario`: 字符串类型，表示在特定用例下的具体场景。例如：`"alipay"`（支付宝支付）、`"trial_user"`（试用用户注册）。如果未指定，则默认为 `GXBizScenario.DEFAULT_SCENARIO`。

**使用示例**：

```java
// 示例1: 为单个特定业务场景提供实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    // ... 支付宝支付实现 ...
}

// 示例2: 使用重复注解为多个业务场景提供同一个实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatpay_mini_program") // 例如微信小程序支付
public class WechatPaymentExt implements PaymentExtPoint {
    // ... 微信支付实现 ...
}
```

**线程安全性**：该注解本身不涉及线程安全问题。开发者需要关注的是其标记的扩展点实现类自身的线程安全性。

### 3.3 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 的补充，它提供了更为强大的方式来为一个扩展点实现类关联多个业务场景。这主要通过两种方式实现：

1.  **聚合多个 `@GXExtension`**：通过其 `value()` 属性，可以直接嵌入一个 `@GXExtension` 注解数组。
2.  **笛卡尔积组合**：通过其 `bizId()`、`useCase()` 和 `scenario()` 属性（均为字符串数组），框架会自动将这些数组中的值进行所有可能的组合，为每一种组合注册该扩展点实现。

**注解属性**：

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GXExtensions {
    GXExtension[] value() default {};         // 直接指定多个GXExtension注解实例
    String[] bizId() default {};            // 业务ID列表，用于笛卡尔积
    String[] useCase() default {};          // 用例ID列表，用于笛卡尔积
    String[] scenario() default {};         // 场景ID列表，用于笛卡尔积
}
```

**使用方式与示例**：

1.  **通过 `value()` 属性聚合 `@GXExtension`**：

    ```java
    @GXExtensions(value = {
        @GXExtension(bizId = "b1", useCase = "uc1", scenario = "s1"),
        @GXExtension(bizId = "b2", useCase = "uc2", scenario = "s2")
    })
    public class MyMultiPurposeExtImpl implements SomeExtPoint { 
        // ... 实现逻辑 ...
    }
    ```

### 3.6 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

### 3.7 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

### 3.8 `GXExtensionPoint`

`cn.maple.extension.GXExtensionPoint` 是一个核心标记接口，所有业务自定义的扩展点接口都需要继承此接口。它本身不包含任何方法，主要作用是向框架标识一个接口是扩展点定义。

**核心作用**：

*   **扩展点标识**：通过继承 `GXExtensionPoint`，开发者可以将一个普通的Java接口声明为一个扩展点。框架会识别这些接口，并根据业务场景动态查找和执行其实现。
*   **规范扩展定义**：它为所有扩展点提供了一个统一的父类型，便于框架进行管理和识别。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并让该接口继承 `GXExtensionPoint`。例如，定义一个支付相关的扩展点：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.OrderDTO;

/**
 * 支付扩展点
 */
public interface PaymentExtPoint extends GXExtensionPoint {

    /**
     * 支付操作
     * @param order 订单信息
     * @return 支付结果
     */
    String pay(OrderDTO order);
}
```

**实现扩展点**：

扩展点的具体实现类需要使用 `@GXExtension` 注解来标记，并指定其对应的业务场景。框架会扫描这些注解，并将实现注册到 `GXExtensionRepository` 中。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 */
@GXExtension(bizId = "order", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身是线程安全的，因为它只是一个标记接口，不包含任何状态或逻辑。

然而，**扩展点的具体实现类必须自行确保其线程安全性**。如果扩展点实现类中包含共享的可变状态，开发者需要采取适当的同步措施（如使用 `synchronized` 关键字、`java.util.concurrent` 包中的并发工具类等）来保证在多线程环境下的正确性和数据一致性。

通过 `GXExtensionPoint` 和 `@GXExtension` 注解的配合，框架能够灵活地管理和调用不同业务场景下的扩展实现，实现了业务逻辑的解耦和动态扩展。

### 3.9 `@GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它与 `GXExtensionPoint` 接口协同工作，帮助框架识别和注册不同业务场景下的扩展逻辑。

**核心作用**：

*   **声明扩展实现**：将一个普通的Java类标记为一个扩展点实现。
*   **指定业务场景**：通过 `bizId`、`useCase` 和 `scenario` 属性，精确定义该扩展实现所适用的业务场景。这些属性的值会与 `GXBizScenario` 对象进行匹配。
*   **支持多场景**：该注解是可重复的（`@Repeatable(GXExtensions.class)`），意味着同一个扩展点实现类可以服务于多个不同的业务场景。开发者可以在同一个类上使用多个 `@GXExtension` 注解，或者使用 `@GXExtensions` 注解来包裹多个 `@GXExtension`。

**注解属性**：

*   `bizId()`: 字符串类型，表示业务ID。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
*   `useCase()`: 字符串类型，表示用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
*   `scenario()`: 字符串类型，表示场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

**如何使用**：

将 `@GXExtension` 注解应用到实现了某个 `GXExtensionPoint` 接口的类上。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXBizScenario;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "alipay" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}

/**
 * 微信支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "wechat" 和 "wechatMini" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini") // 可重复注解
public class WechatPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用微信支付，订单号：" + order.getOrderNo());
        // 具体的微信支付逻辑
        return "Wechat payment successful for order: " + order.getOrderNo();
    }
}

// 或者使用 @GXExtensions (如果适用)
// @GXExtensions({
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat"),
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
// })
// public class WechatPaymentExtAlternatives implements PaymentExtPoint { ... }
```

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见，允许框架通过反射读取注解信息。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Repeatable(GXExtensions.class)`: 允许在同一个类上多次使用 `@GXExtension` 注解。

**线程安全性**：

`@GXExtension` 注解本身不涉及线程安全问题。然而，被此注解标记的扩展点实现类的线程安全性需要由开发者自行保证。如果实现类中存在共享的可变状态，必须采取适当的同步措施。

通过 `@GXExtension` 注解，开发者可以清晰地将业务逻辑的变体与特定的业务场景关联起来，框架则依据这些注解信息在运行时动态地选择和执行合适的扩展实现。

### 3.10 `@GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 的容器注解，主要用于支持一个扩展点实现类服务于多个业务场景的声明。当同一个类需要被多个 `@GXExtension` 注解标记时，或者需要通过属性组合（笛卡尔积）来批量定义业务场景时，可以使用 `@GXExtensions`。

**核心作用**：

*   **聚合多个 `@GXExtension`**：作为 `@GXExtension` 的 `value()` 属性的容器，允许在一个地方列出多个独立的业务场景定义。
*   **笛卡尔积生成业务场景**：提供 `bizId()`, `useCase()`, 和 `scenario()` 数组属性。框架会将这些数组中的元素进行笛卡尔积组合，为每一种组合自动生成一个业务场景，并将当前的扩展点实现注册到所有这些场景下。这对于一个实现需要覆盖大量有规律的场景组合时非常有用。
*   **Spring组件扫描**：此注解本身也被 `@Component` 标记，这意味着被 `@GXExtensions` 注解的类也会被Spring扫描为组件（如果Spring的组件扫描配置包含了该类所在的包）。

**注解属性**：

*   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。当每个业务场景需要精确、独立定义时使用。默认为空数组。
*   `bizId()`: `String[]` 类型，业务ID数组。默认为 `{GXBizScenario.DEFAULT_BIZ_ID}`。
*   `useCase()`: `String[]` 类型，用例数组。默认为 `{GXBizScenario.DEFAULT_USE_CASE}`。
*   `scenario()`: `String[]` 类型，场景数组。默认为 `{GXBizScenario.DEFAULT_SCENARIO}`。

**如何使用**：

**方式一：直接聚合多个 `@GXExtension`**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.NotificationExtPoint;

@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "confirmation", scenario = "email"),
    @GXExtension(bizId = "order", useCase = "shipment", scenario = "sms")
})
public class OrderNotificationExt implements NotificationExtPoint {
    @Override
    public void send(OrderDTO order, String message) {
        // 根据具体坐标执行不同的通知逻辑，或通用逻辑
        System.out.println("Sending notification for order: " + order.getOrderNo() + ", Message: " + message);
    }
}
```

**方式二：使用笛卡尔积组合**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.ReportingExtPoint;

// 此实现将注册到以下4个业务场景：
// 1. finance.monthly.summary
// 2. finance.monthly.detail
// 3. sales.monthly.summary
// 4. sales.monthly.detail
@GXExtensions(
    bizId = {"finance", "sales"},
    useCase = {"monthly"},
    scenario = {"summary", "detail"}
)
public class MonthlyReportExt implements ReportingExtPoint {
    @Override
    public String generateReport(OrderDTO criteria) {
        // 生成报表逻辑
        return "Generated monthly report.";
    }
}
```

**注意**：如果同时使用了 `value()` 属性和笛卡尔积属性（`bizId`, `useCase`, `scenario`），框架通常会优先处理 `value()` 中定义的 `@GXExtension`，然后处理笛卡尔积生成的场景。具体行为可能依赖于框架的实现细节，建议查阅框架文档或通过测试确认。

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Component`: 将被此注解标记的类声明为Spring组件。

**线程安全性**：

与 `@GXExtension` 类似，`@GXExtensions` 注解本身不影响线程安全。被标记的扩展点实现类的线程安全性由开发者负责。

`@GXExtensions` 提供了更灵活和强大的方式来管理扩展点实现与多个业务场景的映射关系，特别是在需要批量定义或组合场景时，能显著减少代码重复。

### 3.11 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate`（扩展坐标）是框架中用于唯一定位一个扩展点实现的标识对象。它封装了区分一个扩展点实现所需的两个关键信息：扩展点接口的定义和具体的业务场景。

**核心作用**：

*   **唯一标识扩展点实现**：通过组合扩展点接口的类名和业务场景的唯一标识，`GXExtensionCoordinate` 为每一个具体的扩展点实现提供了一个全局唯一的键。
*   **作为仓库的Key**：在 `GXExtensionRepository` 中，`GXExtensionCoordinate` 对象被用作 `ConcurrentHashMap` 的键，来存储和检索对应的扩展点实现实例。
*   **解耦扩展点调用**：调用方通过构建 `GXExtensionCoordinate` 来指定希望执行的扩展点和业务场景，而无需直接依赖具体的实现类。

**核心属性**：

*   `extensionPointName`: `String` 类型，存储扩展点接口的完全限定类名。
*   `bizScenarioUniqueIdentity`: `String` 类型，存储业务场景的唯一标识，格式通常为 `bizId.useCase.scenario`。
*   `extensionPointClass`: `Class<?>` 类型，运行时扩展点接口的 `Class` 对象。主要用于类型安全和反射操作。
*   `bizScenario`: `GXBizScenario` 类型，封装了业务场景的三个维度（`bizId`, `useCase`, `scenario`）。

**构造方式**：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象（推荐）**：

    ```java
    Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
    GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
    GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extPointClass, bizScenario);
    // 或者使用静态工厂方法
    GXExtensionCoordinate coordinateViaFactory = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
    ```
    这种方式在编译期就能进行类型检查，并且保留了完整的类型信息。

2.  **通过扩展点类名字符串和业务场景唯一标识字符串**：

    ```java
    String extPointName = "com.example.extension.point.PaymentExtPoint";
    String bizScenarioIdentity = "order.payment.alipay";
    GXExtensionCoordinate coordinateFromString = new GXExtensionCoordinate(extPointName, bizScenarioIdentity);
    ```
    这种方式相对灵活，但牺牲了编译期类型安全，`extensionPointClass` 和 `bizScenario` 属性在此情况下会是 `null`。

**重要方法**：

*   `getExtensionPointClass()`: 返回扩展点接口的 `Class` 对象。
*   `getBizScenario()`: 返回 `GXBizScenario` 对象。
*   `getExtensionPointName()`: 返回扩展点接口的完全限定名。
*   `getBizScenarioUniqueIdentity()`: 返回业务场景的唯一标识字符串。
*   `equals(Object obj)` 和 `hashCode()`: 正确实现了这两个方法，确保了 `GXExtensionCoordinate` 对象可以作为 `HashMap` 等集合的键进行正确操作。

**线程安全性**：

`GXExtensionCoordinate` 类是不可变的（所有字段都是 `final` 并在构造时初始化），因此它是线程安全的。可以安全地在多线程环境中使用和共享。

通过 `GXExtensionCoordinate`，扩展框架能够清晰、高效地管理和定位在不同业务场景下的扩展点实现，是实现框架动态性的关键组件之一。

### 3.12 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

**使用示例**：

```java
// 假设有一个订单处理服务
@Service
public class OrderService {

    @Resource
    private GXExtensionExecutor extensionExecutor;

    // 订单处理扩展点接口
    public interface OrderProcessExtPoint extends GXExtensionPoint {
        OrderResult process(Order orderData);
    }

    // 订单数据类
    public static class Order { /* ... */ }
    public static class OrderResult { /* ... */ }

    public OrderResult handleOrder(Order orderData, String bizId, String useCase, String scenario) {
        // 1. 创建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);

        // 2. 执行扩展点方法
        //    - 第一个参数：扩展点接口的Class对象
        //    - 第二个参数：业务场景对象
        //    - 第三个参数：一个函数式接口，接收找到的扩展点实例，并调用其方法
        return extensionExecutor.execute(
            OrderProcessExtPoint.class, 
            bizScenario, 
            extPoint -> extPoint.process(orderData)
        );
    }

    public void notifyUser(Order orderData, String bizId, String useCase, String scenario) {
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
        // 如果扩展点方法无返回值，可以使用 executeVoid
        extensionExecutor.executeVoid(
            NotificationExtPoint.class, // 假设有 NotificationExtPoint 扩展点
            bizScenario,
            extPoint -> extPoint.sendNotification(orderData) // 假设 sendNotification 是 void 方法
        );
    }
}
```

在上面的示例中，`extensionExecutor.execute(...)` 会首先尝试查找 `bizId.useCase.scenario` 对应的 `OrderProcessExtPoint` 实现。如果找不到，它会依次尝试 `bizId.useCase.#defaultScenario#` 和 `bizId.#defaultUseCase#.#defaultScenario#`。

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。
    这种方式下，`MyMultiPurposeExtImpl` 会被注册为 `b1.uc1.s1` 和 `b2.uc2.s2` 两个业务场景的扩展点实现。

2.  **通过 `bizId()`, `useCase()`, `scenario()` 属性的笛卡尔积**：

    ```java
    @GXExtensions(
        bizId = {"order", "refund"},      // 业务ID: 订单, 退款
        useCase = {"validation"},       // 用例ID: 校验
        scenario = {"pre_submit", "post_submit"} // 场景ID: 提交前, 提交后
    )
    public class ValidationExtImpl implements SomeExtPoint {
        // ... 实现逻辑 ... 
    }
    ```

### 3.6 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

### 3.7 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

### 3.8 `GXExtensionPoint`

`cn.maple.extension.GXExtensionPoint` 是一个核心标记接口，所有业务自定义的扩展点接口都需要继承此接口。它本身不包含任何方法，主要作用是向框架标识一个接口是扩展点定义。

**核心作用**：

*   **扩展点标识**：通过继承 `GXExtensionPoint`，开发者可以将一个普通的Java接口声明为一个扩展点。框架会识别这些接口，并根据业务场景动态查找和执行其实现。
*   **规范扩展定义**：它为所有扩展点提供了一个统一的父类型，便于框架进行管理和识别。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并让该接口继承 `GXExtensionPoint`。例如，定义一个支付相关的扩展点：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.OrderDTO;

/**
 * 支付扩展点
 */
public interface PaymentExtPoint extends GXExtensionPoint {

    /**
     * 支付操作
     * @param order 订单信息
     * @return 支付结果
     */
    String pay(OrderDTO order);
}
```

**实现扩展点**：

扩展点的具体实现类需要使用 `@GXExtension` 注解来标记，并指定其对应的业务场景。框架会扫描这些注解，并将实现注册到 `GXExtensionRepository` 中。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 */
@GXExtension(bizId = "order", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身是线程安全的，因为它只是一个标记接口，不包含任何状态或逻辑。

然而，**扩展点的具体实现类必须自行确保其线程安全性**。如果扩展点实现类中包含共享的可变状态，开发者需要采取适当的同步措施（如使用 `synchronized` 关键字、`java.util.concurrent` 包中的并发工具类等）来保证在多线程环境下的正确性和数据一致性。

通过 `GXExtensionPoint` 和 `@GXExtension` 注解的配合，框架能够灵活地管理和调用不同业务场景下的扩展实现，实现了业务逻辑的解耦和动态扩展。

### 3.9 `@GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它与 `GXExtensionPoint` 接口协同工作，帮助框架识别和注册不同业务场景下的扩展逻辑。

**核心作用**：

*   **声明扩展实现**：将一个普通的Java类标记为一个扩展点实现。
*   **指定业务场景**：通过 `bizId`、`useCase` 和 `scenario` 属性，精确定义该扩展实现所适用的业务场景。这些属性的值会与 `GXBizScenario` 对象进行匹配。
*   **支持多场景**：该注解是可重复的（`@Repeatable(GXExtensions.class)`），意味着同一个扩展点实现类可以服务于多个不同的业务场景。开发者可以在同一个类上使用多个 `@GXExtension` 注解，或者使用 `@GXExtensions` 注解来包裹多个 `@GXExtension`。

**注解属性**：

*   `bizId()`: 字符串类型，表示业务ID。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
*   `useCase()`: 字符串类型，表示用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
*   `scenario()`: 字符串类型，表示场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

**如何使用**：

将 `@GXExtension` 注解应用到实现了某个 `GXExtensionPoint` 接口的类上。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXBizScenario;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "alipay" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}

/**
 * 微信支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "wechat" 和 "wechatMini" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini") // 可重复注解
public class WechatPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用微信支付，订单号：" + order.getOrderNo());
        // 具体的微信支付逻辑
        return "Wechat payment successful for order: " + order.getOrderNo();
    }
}

// 或者使用 @GXExtensions (如果适用)
// @GXExtensions({
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat"),
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
// })
// public class WechatPaymentExtAlternatives implements PaymentExtPoint { ... }
```

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见，允许框架通过反射读取注解信息。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Repeatable(GXExtensions.class)`: 允许在同一个类上多次使用 `@GXExtension` 注解。

**线程安全性**：

`@GXExtension` 注解本身不涉及线程安全问题。然而，被此注解标记的扩展点实现类的线程安全性需要由开发者自行保证。如果实现类中存在共享的可变状态，必须采取适当的同步措施。

通过 `@GXExtension` 注解，开发者可以清晰地将业务逻辑的变体与特定的业务场景关联起来，框架则依据这些注解信息在运行时动态地选择和执行合适的扩展实现。

### 3.10 `@GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 的容器注解，主要用于支持一个扩展点实现类服务于多个业务场景的声明。当同一个类需要被多个 `@GXExtension` 注解标记时，或者需要通过属性组合（笛卡尔积）来批量定义业务场景时，可以使用 `@GXExtensions`。

**核心作用**：

*   **聚合多个 `@GXExtension`**：作为 `@GXExtension` 的 `value()` 属性的容器，允许在一个地方列出多个独立的业务场景定义。
*   **笛卡尔积生成业务场景**：提供 `bizId()`, `useCase()`, 和 `scenario()` 数组属性。框架会将这些数组中的元素进行笛卡尔积组合，为每一种组合自动生成一个业务场景，并将当前的扩展点实现注册到所有这些场景下。这对于一个实现需要覆盖大量有规律的场景组合时非常有用。
*   **Spring组件扫描**：此注解本身也被 `@Component` 标记，这意味着被 `@GXExtensions` 注解的类也会被Spring扫描为组件（如果Spring的组件扫描配置包含了该类所在的包）。

**注解属性**：

*   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。当每个业务场景需要精确、独立定义时使用。默认为空数组。
*   `bizId()`: `String[]` 类型，业务ID数组。默认为 `{GXBizScenario.DEFAULT_BIZ_ID}`。
*   `useCase()`: `String[]` 类型，用例数组。默认为 `{GXBizScenario.DEFAULT_USE_CASE}`。
*   `scenario()`: `String[]` 类型，场景数组。默认为 `{GXBizScenario.DEFAULT_SCENARIO}`。

**如何使用**：

**方式一：直接聚合多个 `@GXExtension`**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.NotificationExtPoint;

@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "confirmation", scenario = "email"),
    @GXExtension(bizId = "order", useCase = "shipment", scenario = "sms")
})
public class OrderNotificationExt implements NotificationExtPoint {
    @Override
    public void send(OrderDTO order, String message) {
        // 根据具体坐标执行不同的通知逻辑，或通用逻辑
        System.out.println("Sending notification for order: " + order.getOrderNo() + ", Message: " + message);
    }
}
```

**方式二：使用笛卡尔积组合**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.ReportingExtPoint;

// 此实现将注册到以下4个业务场景：
// 1. finance.monthly.summary
// 2. finance.monthly.detail
// 3. sales.monthly.summary
// 4. sales.monthly.detail
@GXExtensions(
    bizId = {"finance", "sales"},
    useCase = {"monthly"},
    scenario = {"summary", "detail"}
)
public class MonthlyReportExt implements ReportingExtPoint {
    @Override
    public String generateReport(OrderDTO criteria) {
        // 生成报表逻辑
        return "Generated monthly report.";
    }
}
```

**注意**：如果同时使用了 `value()` 属性和笛卡尔积属性（`bizId`, `useCase`, `scenario`），框架通常会优先处理 `value()` 中定义的 `@GXExtension`，然后处理笛卡尔积生成的场景。具体行为可能依赖于框架的实现细节，建议查阅框架文档或通过测试确认。

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Component`: 将被此注解标记的类声明为Spring组件。

**线程安全性**：

与 `@GXExtension` 类似，`@GXExtensions` 注解本身不影响线程安全。被标记的扩展点实现类的线程安全性由开发者负责。

`@GXExtensions` 提供了更灵活和强大的方式来管理扩展点实现与多个业务场景的映射关系，特别是在需要批量定义或组合场景时，能显著减少代码重复。

### 3.11 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate`（扩展坐标）是框架中用于唯一定位一个扩展点实现的标识对象。它封装了区分一个扩展点实现所需的两个关键信息：扩展点接口的定义和具体的业务场景。

**核心作用**：

*   **唯一标识扩展点实现**：通过组合扩展点接口的类名和业务场景的唯一标识，`GXExtensionCoordinate` 为每一个具体的扩展点实现提供了一个全局唯一的键。
*   **作为仓库的Key**：在 `GXExtensionRepository` 中，`GXExtensionCoordinate` 对象被用作 `ConcurrentHashMap` 的键，来存储和检索对应的扩展点实现实例。
*   **解耦扩展点调用**：调用方通过构建 `GXExtensionCoordinate` 来指定希望执行的扩展点和业务场景，而无需直接依赖具体的实现类。

**核心属性**：

*   `extensionPointName`: `String` 类型，存储扩展点接口的完全限定类名。
*   `bizScenarioUniqueIdentity`: `String` 类型，存储业务场景的唯一标识，格式通常为 `bizId.useCase.scenario`。
*   `extensionPointClass`: `Class<?>` 类型，运行时扩展点接口的 `Class` 对象。主要用于类型安全和反射操作。
*   `bizScenario`: `GXBizScenario` 类型，封装了业务场景的三个维度（`bizId`, `useCase`, `scenario`）。

**构造方式**：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象（推荐）**：

    ```java
    Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
    GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
    GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extPointClass, bizScenario);
    // 或者使用静态工厂方法
    GXExtensionCoordinate coordinateViaFactory = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
    ```
    这种方式在编译期就能进行类型检查，并且保留了完整的类型信息。

2.  **通过扩展点类名字符串和业务场景唯一标识字符串**：

    ```java
    String extPointName = "com.example.extension.point.PaymentExtPoint";
    String bizScenarioIdentity = "order.payment.alipay";
    GXExtensionCoordinate coordinateFromString = new GXExtensionCoordinate(extPointName, bizScenarioIdentity);
    ```
    这种方式相对灵活，但牺牲了编译期类型安全，`extensionPointClass` 和 `bizScenario` 属性在此情况下会是 `null`。

**重要方法**：

*   `getExtensionPointClass()`: 返回扩展点接口的 `Class` 对象。
*   `getBizScenario()`: 返回 `GXBizScenario` 对象。
*   `getExtensionPointName()`: 返回扩展点接口的完全限定名。
*   `getBizScenarioUniqueIdentity()`: 返回业务场景的唯一标识字符串。
*   `equals(Object obj)` 和 `hashCode()`: 正确实现了这两个方法，确保了 `GXExtensionCoordinate` 对象可以作为 `HashMap` 等集合的键进行正确操作。

**线程安全性**：

`GXExtensionCoordinate` 类是不可变的（所有字段都是 `final` 并在构造时初始化），因此它是线程安全的。可以安全地在多线程环境中使用和共享。

通过 `GXExtensionCoordinate`，扩展框架能够清晰、高效地管理和定位在不同业务场景下的扩展点实现，是实现框架动态性的关键组件之一。

### 3.12 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

**使用示例**：

```java
// 假设有一个订单处理服务
@Service
public class OrderService {

    @Resource
    private GXExtensionExecutor extensionExecutor;

    // 订单处理扩展点接口
    public interface OrderProcessExtPoint extends GXExtensionPoint {
        OrderResult process(Order orderData);
    }

    // 订单数据类
    public static class Order { /* ... */ }
    public static class OrderResult { /* ... */ }

    public OrderResult handleOrder(Order orderData, String bizId, String useCase, String scenario) {
        // 1. 创建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);

        // 2. 执行扩展点方法
        //    - 第一个参数：扩展点接口的Class对象
        //    - 第二个参数：业务场景对象
        //    - 第三个参数：一个函数式接口，接收找到的扩展点实例，并调用其方法
        return extensionExecutor.execute(
            OrderProcessExtPoint.class, 
            bizScenario, 
            extPoint -> extPoint.process(orderData)
        );
    }

    public void notifyUser(Order orderData, String bizId, String useCase, String scenario) {
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
        // 如果扩展点方法无返回值，可以使用 executeVoid
        extensionExecutor.executeVoid(
            NotificationExtPoint.class, // 假设有 NotificationExtPoint 扩展点
            bizScenario,
            extPoint -> extPoint.sendNotification(orderData) // 假设 sendNotification 是 void 方法
        );
    }
}
```

在上面的示例中，`extensionExecutor.execute(...)` 会首先尝试查找 `bizId.useCase.scenario` 对应的 `OrderProcessExtPoint` 实现。如果找不到，它会依次尝试 `bizId.useCase.#defaultScenario#` 和 `bizId.#defaultUseCase#.#defaultScenario#`。

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。
    上述配置会为 `ValidationExtImpl` 注册以下 \(2 \times 1 \times 2 = 4\) 个业务场景的实现：
    -   `order.validation.pre_submit`
    -   `order.validation.post_submit`
    -   `refund.validation.pre_submit`
    -   `refund.validation.post_submit`

    **注意**：如果在笛卡尔积配置中，某个维度的数组（如 `bizId()`）为空或未提供，则该维度会使用其对应的默认值（如 `GXBizScenario.DEFAULT_BIZ_ID`）参与组合。

**线程安全性**：与 `@GXExtension` 类似，`@GXExtensions` 注解本身不涉及线程安全问题，开发者需确保扩展点实现类的线程安全。

### 3.4 `GXBizScenario`

`cn.maple.extension.GXBizScenario` 类用于封装和表示一个具体的业务场景。它是扩展点框架的核心类之一，用于唯一标识一个业务场景，从而找到对应的扩展点实现。业务场景由 `bizId`（业务标识）、`useCase`（用例标识）和 `scenario`（场景标识）三个维度构成。该类是不可变的，所有字段在创建后不可修改，因此是线程安全的。

**核心概念**：

-   **`bizId`**：业务标识，用于区分不同的业务领域。例如：`"order"`（订单）、`"user"`（用户）、`"product"`（商品）。如果系统中只有一个业务，可以使用默认值。
-   **`useCase`**：用例标识，用于区分同一业务领域下的不同用例。例如：`"placeOrder"`（下单）、`"payment"`（支付）、`"refund"`（退款）。不能为空，如果未指定则使用默认值。
-   **`scenario`**：场景标识，用于区分同一用例下的不同场景。例如：`"normal"`（普通下单）、`"promotion"`（促销下单）、`"seckill"`（秒杀下单）。不能为空，如果未指定则使用默认值。

**核心常量**：

-   `GXBizScenario.DEFAULT_BIZ_ID` ("#defaultBizId#")：默认业务ID，当没有指定业务ID时使用此默认值。
-   `GXBizScenario.DEFAULT_USE_CASE` ("#defaultUseCase#")：默认用例ID，当没有指定用例时使用此默认值。
-   `GXBizScenario.DEFAULT_SCENARIO` ("#defaultScenario#")：默认场景ID，当没有指定场景时使用此默认值。
-   `DOT_SEPARATOR` (".")：分隔符，用于连接业务ID、用例和场景，形成唯一标识。

**创建实例**：

`GXBizScenario` 提供了多个静态工厂方法 `valueOf` 和 `newDefault` 来创建实例：

```java
// 1. 创建一个完整的业务场景 (bizId, useCase, scenario)
GXBizScenario scenario1 = GXBizScenario.valueOf("order", "place", "normal");

// 2. 创建一个业务场景，使用默认场景 (bizId, useCase, DEFAULT_SCENARIO)
GXBizScenario scenario2 = GXBizScenario.valueOf("order", "place");
// 等价于 GXBizScenario.valueOf("order", "place", GXBizScenario.DEFAULT_SCENARIO);

// 3. 创建一个业务场景，使用默认用例和默认场景 (bizId, DEFAULT_USE_CASE, DEFAULT_SCENARIO)
GXBizScenario scenario3 = GXBizScenario.valueOf("order");
// 等价于 GXBizScenario.valueOf("order", GXBizScenario.DEFAULT_USE_CASE, GXBizScenario.DEFAULT_SCENARIO);

// 4. 创建一个完全默认的业务场景 (DEFAULT_BIZ_ID, DEFAULT_USE_CASE, DEFAULT_SCENARIO)
GXBizScenario scenario4 = GXBizScenario.newDefault();
// 等价于 GXBizScenario.valueOf(GXBizScenario.DEFAULT_BIZ_ID, GXBizScenario.DEFAULT_USE_CASE, GXBizScenario.DEFAULT_SCENARIO);
```

**获取唯一标识**：

`GXBizScenario` 提供了以下方法来获取不同形式的场景标识，这些标识主要用于在 `GXExtensionRepository` 中作为Key的一部分进行查找：

-   **`getUniqueIdentity()`**: 返回业务场景的完整唯一标识，格式为 `bizId.useCase.scenario`。
    例如：`"order.place.normal"`。

-   **`getIdentityWithDefaultScenario()`**: 返回使用默认场景的业务场景标识，格式为 `bizId.useCase.#defaultScenario#`。
    例如：`"order.place.#defaultScenario#"`。

-   **`getIdentityWithDefaultUseCase()`**: 返回使用默认用例和默认场景的业务场景标识，格式为 `bizId.#defaultUseCase#.#defaultScenario#`。
    例如：`"order.#defaultUseCase#.#defaultScenario#"`。

这些方法不修改对象状态，只读取字段值，因此是线程安全的。

### 3.5 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate`（扩展坐标）用于唯一定位一个扩展点的实现。它由 **扩展点接口的Class对象** 和 **业务场景 `GXBizScenario` 对象** 两部分组成。当框架需要查找某个业务场景下的特定扩展点实现时，就会使用这个坐标对象作为Key在 `GXExtensionRepository` 中进行查找。

该类是不可变的（所有字段均为 `final`），因此是线程安全的。它正确实现了 `equals()` 和 `hashCode()` 方法，这对于其作为 `HashMap` 的Key至关重要。

**核心属性**：

-   `extensionPointName`: `String` 类型，扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，扩展点接口的 `Class` 对象，用于在运行时获取类型信息。
-   `bizScenario`: `GXBizScenario` 类型，业务场景对象实例。

**创建实例**：

主要通过构造函数或静态工厂方法 `valueOf` 创建：

1.  **通过 `Class` 和 `GXBizScenario` 对象（推荐方式）**：

    ```java
    // 假设 PaymentExtPoint 是一个扩展点接口, bizScenario 是一个GXBizScenario实例
    Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
    GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

    // 使用构造函数创建
    GXExtensionCoordinate coord1 = new GXExtensionCoordinate(extPointClass, bizScenario);

    // 使用静态工厂方法创建 (内部也是调用构造函数)
    GXExtensionCoordinate coord2 = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
    ```

### 3.6 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

### 3.7 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

### 3.8 `GXExtensionPoint`

`cn.maple.extension.GXExtensionPoint` 是一个核心标记接口，所有业务自定义的扩展点接口都需要继承此接口。它本身不包含任何方法，主要作用是向框架标识一个接口是扩展点定义。

**核心作用**：

*   **扩展点标识**：通过继承 `GXExtensionPoint`，开发者可以将一个普通的Java接口声明为一个扩展点。框架会识别这些接口，并根据业务场景动态查找和执行其实现。
*   **规范扩展定义**：它为所有扩展点提供了一个统一的父类型，便于框架进行管理和识别。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并让该接口继承 `GXExtensionPoint`。例如，定义一个支付相关的扩展点：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.OrderDTO;

/**
 * 支付扩展点
 */
public interface PaymentExtPoint extends GXExtensionPoint {

    /**
     * 支付操作
     * @param order 订单信息
     * @return 支付结果
     */
    String pay(OrderDTO order);
}
```

**实现扩展点**：

扩展点的具体实现类需要使用 `@GXExtension` 注解来标记，并指定其对应的业务场景。框架会扫描这些注解，并将实现注册到 `GXExtensionRepository` 中。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 */
@GXExtension(bizId = "order", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身是线程安全的，因为它只是一个标记接口，不包含任何状态或逻辑。

然而，**扩展点的具体实现类必须自行确保其线程安全性**。如果扩展点实现类中包含共享的可变状态，开发者需要采取适当的同步措施（如使用 `synchronized` 关键字、`java.util.concurrent` 包中的并发工具类等）来保证在多线程环境下的正确性和数据一致性。

通过 `GXExtensionPoint` 和 `@GXExtension` 注解的配合，框架能够灵活地管理和调用不同业务场景下的扩展实现，实现了业务逻辑的解耦和动态扩展。

### 3.9 `@GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它与 `GXExtensionPoint` 接口协同工作，帮助框架识别和注册不同业务场景下的扩展逻辑。

**核心作用**：

*   **声明扩展实现**：将一个普通的Java类标记为一个扩展点实现。
*   **指定业务场景**：通过 `bizId`、`useCase` 和 `scenario` 属性，精确定义该扩展实现所适用的业务场景。这些属性的值会与 `GXBizScenario` 对象进行匹配。
*   **支持多场景**：该注解是可重复的（`@Repeatable(GXExtensions.class)`），意味着同一个扩展点实现类可以服务于多个不同的业务场景。开发者可以在同一个类上使用多个 `@GXExtension` 注解，或者使用 `@GXExtensions` 注解来包裹多个 `@GXExtension`。

**注解属性**：

*   `bizId()`: 字符串类型，表示业务ID。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
*   `useCase()`: 字符串类型，表示用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
*   `scenario()`: 字符串类型，表示场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

**如何使用**：

将 `@GXExtension` 注解应用到实现了某个 `GXExtensionPoint` 接口的类上。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXBizScenario;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "alipay" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}

/**
 * 微信支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "wechat" 和 "wechatMini" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini") // 可重复注解
public class WechatPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用微信支付，订单号：" + order.getOrderNo());
        // 具体的微信支付逻辑
        return "Wechat payment successful for order: " + order.getOrderNo();
    }
}

// 或者使用 @GXExtensions (如果适用)
// @GXExtensions({
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat"),
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
// })
// public class WechatPaymentExtAlternatives implements PaymentExtPoint { ... }
```

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见，允许框架通过反射读取注解信息。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Repeatable(GXExtensions.class)`: 允许在同一个类上多次使用 `@GXExtension` 注解。

**线程安全性**：

`@GXExtension` 注解本身不涉及线程安全问题。然而，被此注解标记的扩展点实现类的线程安全性需要由开发者自行保证。如果实现类中存在共享的可变状态，必须采取适当的同步措施。

通过 `@GXExtension` 注解，开发者可以清晰地将业务逻辑的变体与特定的业务场景关联起来，框架则依据这些注解信息在运行时动态地选择和执行合适的扩展实现。

### 3.10 `@GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 的容器注解，主要用于支持一个扩展点实现类服务于多个业务场景的声明。当同一个类需要被多个 `@GXExtension` 注解标记时，或者需要通过属性组合（笛卡尔积）来批量定义业务场景时，可以使用 `@GXExtensions`。

**核心作用**：

*   **聚合多个 `@GXExtension`**：作为 `@GXExtension` 的 `value()` 属性的容器，允许在一个地方列出多个独立的业务场景定义。
*   **笛卡尔积生成业务场景**：提供 `bizId()`, `useCase()`, 和 `scenario()` 数组属性。框架会将这些数组中的元素进行笛卡尔积组合，为每一种组合自动生成一个业务场景，并将当前的扩展点实现注册到所有这些场景下。这对于一个实现需要覆盖大量有规律的场景组合时非常有用。
*   **Spring组件扫描**：此注解本身也被 `@Component` 标记，这意味着被 `@GXExtensions` 注解的类也会被Spring扫描为组件（如果Spring的组件扫描配置包含了该类所在的包）。

**注解属性**：

*   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。当每个业务场景需要精确、独立定义时使用。默认为空数组。
*   `bizId()`: `String[]` 类型，业务ID数组。默认为 `{GXBizScenario.DEFAULT_BIZ_ID}`。
*   `useCase()`: `String[]` 类型，用例数组。默认为 `{GXBizScenario.DEFAULT_USE_CASE}`。
*   `scenario()`: `String[]` 类型，场景数组。默认为 `{GXBizScenario.DEFAULT_SCENARIO}`。

**如何使用**：

**方式一：直接聚合多个 `@GXExtension`**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.NotificationExtPoint;

@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "confirmation", scenario = "email"),
    @GXExtension(bizId = "order", useCase = "shipment", scenario = "sms")
})
public class OrderNotificationExt implements NotificationExtPoint {
    @Override
    public void send(OrderDTO order, String message) {
        // 根据具体坐标执行不同的通知逻辑，或通用逻辑
        System.out.println("Sending notification for order: " + order.getOrderNo() + ", Message: " + message);
    }
}
```

**方式二：使用笛卡尔积组合**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.ReportingExtPoint;

// 此实现将注册到以下4个业务场景：
// 1. finance.monthly.summary
// 2. finance.monthly.detail
// 3. sales.monthly.summary
// 4. sales.monthly.detail
@GXExtensions(
    bizId = {"finance", "sales"},
    useCase = {"monthly"},
    scenario = {"summary", "detail"}
)
public class MonthlyReportExt implements ReportingExtPoint {
    @Override
    public String generateReport(OrderDTO criteria) {
        // 生成报表逻辑
        return "Generated monthly report.";
    }
}
```

**注意**：如果同时使用了 `value()` 属性和笛卡尔积属性（`bizId`, `useCase`, `scenario`），框架通常会优先处理 `value()` 中定义的 `@GXExtension`，然后处理笛卡尔积生成的场景。具体行为可能依赖于框架的实现细节，建议查阅框架文档或通过测试确认。

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Component`: 将被此注解标记的类声明为Spring组件。

**线程安全性**：

与 `@GXExtension` 类似，`@GXExtensions` 注解本身不影响线程安全。被标记的扩展点实现类的线程安全性由开发者负责。

`@GXExtensions` 提供了更灵活和强大的方式来管理扩展点实现与多个业务场景的映射关系，特别是在需要批量定义或组合场景时，能显著减少代码重复。

### 3.11 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate`（扩展坐标）是框架中用于唯一定位一个扩展点实现的标识对象。它封装了区分一个扩展点实现所需的两个关键信息：扩展点接口的定义和具体的业务场景。

**核心作用**：

*   **唯一标识扩展点实现**：通过组合扩展点接口的类名和业务场景的唯一标识，`GXExtensionCoordinate` 为每一个具体的扩展点实现提供了一个全局唯一的键。
*   **作为仓库的Key**：在 `GXExtensionRepository` 中，`GXExtensionCoordinate` 对象被用作 `ConcurrentHashMap` 的键，来存储和检索对应的扩展点实现实例。
*   **解耦扩展点调用**：调用方通过构建 `GXExtensionCoordinate` 来指定希望执行的扩展点和业务场景，而无需直接依赖具体的实现类。

**核心属性**：

*   `extensionPointName`: `String` 类型，存储扩展点接口的完全限定类名。
*   `bizScenarioUniqueIdentity`: `String` 类型，存储业务场景的唯一标识，格式通常为 `bizId.useCase.scenario`。
*   `extensionPointClass`: `Class<?>` 类型，运行时扩展点接口的 `Class` 对象。主要用于类型安全和反射操作。
*   `bizScenario`: `GXBizScenario` 类型，封装了业务场景的三个维度（`bizId`, `useCase`, `scenario`）。

**构造方式**：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象（推荐）**：

    ```java
    Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
    GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
    GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extPointClass, bizScenario);
    // 或者使用静态工厂方法
    GXExtensionCoordinate coordinateViaFactory = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
    ```
    这种方式在编译期就能进行类型检查，并且保留了完整的类型信息。

2.  **通过扩展点类名字符串和业务场景唯一标识字符串**：

    ```java
    String extPointName = "com.example.extension.point.PaymentExtPoint";
    String bizScenarioIdentity = "order.payment.alipay";
    GXExtensionCoordinate coordinateFromString = new GXExtensionCoordinate(extPointName, bizScenarioIdentity);
    ```
    这种方式相对灵活，但牺牲了编译期类型安全，`extensionPointClass` 和 `bizScenario` 属性在此情况下会是 `null`。

**重要方法**：

*   `getExtensionPointClass()`: 返回扩展点接口的 `Class` 对象。
*   `getBizScenario()`: 返回 `GXBizScenario` 对象。
*   `getExtensionPointName()`: 返回扩展点接口的完全限定名。
*   `getBizScenarioUniqueIdentity()`: 返回业务场景的唯一标识字符串。
*   `equals(Object obj)` 和 `hashCode()`: 正确实现了这两个方法，确保了 `GXExtensionCoordinate` 对象可以作为 `HashMap` 等集合的键进行正确操作。

**线程安全性**：

`GXExtensionCoordinate` 类是不可变的（所有字段都是 `final` 并在构造时初始化），因此它是线程安全的。可以安全地在多线程环境中使用和共享。

通过 `GXExtensionCoordinate`，扩展框架能够清晰、高效地管理和定位在不同业务场景下的扩展点实现，是实现框架动态性的关键组件之一。

### 3.12 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

**使用示例**：

```java
// 假设有一个订单处理服务
@Service
public class OrderService {

    @Resource
    private GXExtensionExecutor extensionExecutor;

    // 订单处理扩展点接口
    public interface OrderProcessExtPoint extends GXExtensionPoint {
        OrderResult process(Order orderData);
    }

    // 订单数据类
    public static class Order { /* ... */ }
    public static class OrderResult { /* ... */ }

    public OrderResult handleOrder(Order orderData, String bizId, String useCase, String scenario) {
        // 1. 创建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);

        // 2. 执行扩展点方法
        //    - 第一个参数：扩展点接口的Class对象
        //    - 第二个参数：业务场景对象
        //    - 第三个参数：一个函数式接口，接收找到的扩展点实例，并调用其方法
        return extensionExecutor.execute(
            OrderProcessExtPoint.class, 
            bizScenario, 
            extPoint -> extPoint.process(orderData)
        );
    }

    public void notifyUser(Order orderData, String bizId, String useCase, String scenario) {
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
        // 如果扩展点方法无返回值，可以使用 executeVoid
        extensionExecutor.executeVoid(
            NotificationExtPoint.class, // 假设有 NotificationExtPoint 扩展点
            bizScenario,
            extPoint -> extPoint.sendNotification(orderData) // 假设 sendNotification 是 void 方法
        );
    }
}
```

在上面的示例中，`extensionExecutor.execute(...)` 会首先尝试查找 `bizId.useCase.scenario` 对应的 `OrderProcessExtPoint` 实现。如果找不到，它会依次尝试 `bizId.useCase.#defaultScenario#` 和 `bizId.#defaultUseCase#.#defaultScenario#`。

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。
    这种方式会进行参数校验，确保 `extPtClass` 和 `bizScenario` 不为 `null`。

2.  **通过扩展点类名字符串和业务场景唯一标识字符串（主要供框架内部或特定场景使用）**：

    ```java
    String extPointClassName = "com.example.extension.point.PaymentExtPoint";
    String scenarioIdentity = "order.payment.alipay";

    GXExtensionCoordinate coord3 = new GXExtensionCoordinate(extPointClassName, scenarioIdentity);
    GXExtensionCoordinate coord4 = GXExtensionCoordinate.valueOf(extPointClassName, scenarioIdentity);
    ```

### 3.6 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

### 3.7 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

### 3.8 `GXExtensionPoint`

`cn.maple.extension.GXExtensionPoint` 是一个核心标记接口，所有业务自定义的扩展点接口都需要继承此接口。它本身不包含任何方法，主要作用是向框架标识一个接口是扩展点定义。

**核心作用**：

*   **扩展点标识**：通过继承 `GXExtensionPoint`，开发者可以将一个普通的Java接口声明为一个扩展点。框架会识别这些接口，并根据业务场景动态查找和执行其实现。
*   **规范扩展定义**：它为所有扩展点提供了一个统一的父类型，便于框架进行管理和识别。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并让该接口继承 `GXExtensionPoint`。例如，定义一个支付相关的扩展点：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.OrderDTO;

/**
 * 支付扩展点
 */
public interface PaymentExtPoint extends GXExtensionPoint {

    /**
     * 支付操作
     * @param order 订单信息
     * @return 支付结果
     */
    String pay(OrderDTO order);
}
```

**实现扩展点**：

扩展点的具体实现类需要使用 `@GXExtension` 注解来标记，并指定其对应的业务场景。框架会扫描这些注解，并将实现注册到 `GXExtensionRepository` 中。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 */
@GXExtension(bizId = "order", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身是线程安全的，因为它只是一个标记接口，不包含任何状态或逻辑。

然而，**扩展点的具体实现类必须自行确保其线程安全性**。如果扩展点实现类中包含共享的可变状态，开发者需要采取适当的同步措施（如使用 `synchronized` 关键字、`java.util.concurrent` 包中的并发工具类等）来保证在多线程环境下的正确性和数据一致性。

通过 `GXExtensionPoint` 和 `@GXExtension` 注解的配合，框架能够灵活地管理和调用不同业务场景下的扩展实现，实现了业务逻辑的解耦和动态扩展。

### 3.9 `@GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它与 `GXExtensionPoint` 接口协同工作，帮助框架识别和注册不同业务场景下的扩展逻辑。

**核心作用**：

*   **声明扩展实现**：将一个普通的Java类标记为一个扩展点实现。
*   **指定业务场景**：通过 `bizId`、`useCase` 和 `scenario` 属性，精确定义该扩展实现所适用的业务场景。这些属性的值会与 `GXBizScenario` 对象进行匹配。
*   **支持多场景**：该注解是可重复的（`@Repeatable(GXExtensions.class)`），意味着同一个扩展点实现类可以服务于多个不同的业务场景。开发者可以在同一个类上使用多个 `@GXExtension` 注解，或者使用 `@GXExtensions` 注解来包裹多个 `@GXExtension`。

**注解属性**：

*   `bizId()`: 字符串类型，表示业务ID。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
*   `useCase()`: 字符串类型，表示用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
*   `scenario()`: 字符串类型，表示场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

**如何使用**：

将 `@GXExtension` 注解应用到实现了某个 `GXExtensionPoint` 接口的类上。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXBizScenario;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "alipay" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}

/**
 * 微信支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "wechat" 和 "wechatMini" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini") // 可重复注解
public class WechatPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用微信支付，订单号：" + order.getOrderNo());
        // 具体的微信支付逻辑
        return "Wechat payment successful for order: " + order.getOrderNo();
    }
}

// 或者使用 @GXExtensions (如果适用)
// @GXExtensions({
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat"),
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
// })
// public class WechatPaymentExtAlternatives implements PaymentExtPoint { ... }
```

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见，允许框架通过反射读取注解信息。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Repeatable(GXExtensions.class)`: 允许在同一个类上多次使用 `@GXExtension` 注解。

**线程安全性**：

`@GXExtension` 注解本身不涉及线程安全问题。然而，被此注解标记的扩展点实现类的线程安全性需要由开发者自行保证。如果实现类中存在共享的可变状态，必须采取适当的同步措施。

通过 `@GXExtension` 注解，开发者可以清晰地将业务逻辑的变体与特定的业务场景关联起来，框架则依据这些注解信息在运行时动态地选择和执行合适的扩展实现。

### 3.10 `@GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 的容器注解，主要用于支持一个扩展点实现类服务于多个业务场景的声明。当同一个类需要被多个 `@GXExtension` 注解标记时，或者需要通过属性组合（笛卡尔积）来批量定义业务场景时，可以使用 `@GXExtensions`。

**核心作用**：

*   **聚合多个 `@GXExtension`**：作为 `@GXExtension` 的 `value()` 属性的容器，允许在一个地方列出多个独立的业务场景定义。
*   **笛卡尔积生成业务场景**：提供 `bizId()`, `useCase()`, 和 `scenario()` 数组属性。框架会将这些数组中的元素进行笛卡尔积组合，为每一种组合自动生成一个业务场景，并将当前的扩展点实现注册到所有这些场景下。这对于一个实现需要覆盖大量有规律的场景组合时非常有用。
*   **Spring组件扫描**：此注解本身也被 `@Component` 标记，这意味着被 `@GXExtensions` 注解的类也会被Spring扫描为组件（如果Spring的组件扫描配置包含了该类所在的包）。

**注解属性**：

*   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。当每个业务场景需要精确、独立定义时使用。默认为空数组。
*   `bizId()`: `String[]` 类型，业务ID数组。默认为 `{GXBizScenario.DEFAULT_BIZ_ID}`。
*   `useCase()`: `String[]` 类型，用例数组。默认为 `{GXBizScenario.DEFAULT_USE_CASE}`。
*   `scenario()`: `String[]` 类型，场景数组。默认为 `{GXBizScenario.DEFAULT_SCENARIO}`。

**如何使用**：

**方式一：直接聚合多个 `@GXExtension`**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.NotificationExtPoint;

@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "confirmation", scenario = "email"),
    @GXExtension(bizId = "order", useCase = "shipment", scenario = "sms")
})
public class OrderNotificationExt implements NotificationExtPoint {
    @Override
    public void send(OrderDTO order, String message) {
        // 根据具体坐标执行不同的通知逻辑，或通用逻辑
        System.out.println("Sending notification for order: " + order.getOrderNo() + ", Message: " + message);
    }
}
```

**方式二：使用笛卡尔积组合**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.ReportingExtPoint;

// 此实现将注册到以下4个业务场景：
// 1. finance.monthly.summary
// 2. finance.monthly.detail
// 3. sales.monthly.summary
// 4. sales.monthly.detail
@GXExtensions(
    bizId = {"finance", "sales"},
    useCase = {"monthly"},
    scenario = {"summary", "detail"}
)
public class MonthlyReportExt implements ReportingExtPoint {
    @Override
    public String generateReport(OrderDTO criteria) {
        // 生成报表逻辑
        return "Generated monthly report.";
    }
}
```

**注意**：如果同时使用了 `value()` 属性和笛卡尔积属性（`bizId`, `useCase`, `scenario`），框架通常会优先处理 `value()` 中定义的 `@GXExtension`，然后处理笛卡尔积生成的场景。具体行为可能依赖于框架的实现细节，建议查阅框架文档或通过测试确认。

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Component`: 将被此注解标记的类声明为Spring组件。

**线程安全性**：

与 `@GXExtension` 类似，`@GXExtensions` 注解本身不影响线程安全。被标记的扩展点实现类的线程安全性由开发者负责。

`@GXExtensions` 提供了更灵活和强大的方式来管理扩展点实现与多个业务场景的映射关系，特别是在需要批量定义或组合场景时，能显著减少代码重复。

### 3.11 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate`（扩展坐标）是框架中用于唯一定位一个扩展点实现的标识对象。它封装了区分一个扩展点实现所需的两个关键信息：扩展点接口的定义和具体的业务场景。

**核心作用**：

*   **唯一标识扩展点实现**：通过组合扩展点接口的类名和业务场景的唯一标识，`GXExtensionCoordinate` 为每一个具体的扩展点实现提供了一个全局唯一的键。
*   **作为仓库的Key**：在 `GXExtensionRepository` 中，`GXExtensionCoordinate` 对象被用作 `ConcurrentHashMap` 的键，来存储和检索对应的扩展点实现实例。
*   **解耦扩展点调用**：调用方通过构建 `GXExtensionCoordinate` 来指定希望执行的扩展点和业务场景，而无需直接依赖具体的实现类。

**核心属性**：

*   `extensionPointName`: `String` 类型，存储扩展点接口的完全限定类名。
*   `bizScenarioUniqueIdentity`: `String` 类型，存储业务场景的唯一标识，格式通常为 `bizId.useCase.scenario`。
*   `extensionPointClass`: `Class<?>` 类型，运行时扩展点接口的 `Class` 对象。主要用于类型安全和反射操作。
*   `bizScenario`: `GXBizScenario` 类型，封装了业务场景的三个维度（`bizId`, `useCase`, `scenario`）。

**构造方式**：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象（推荐）**：

    ```java
    Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
    GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
    GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extPointClass, bizScenario);
    // 或者使用静态工厂方法
    GXExtensionCoordinate coordinateViaFactory = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
    ```
    这种方式在编译期就能进行类型检查，并且保留了完整的类型信息。

2.  **通过扩展点类名字符串和业务场景唯一标识字符串**：

    ```java
    String extPointName = "com.example.extension.point.PaymentExtPoint";
    String bizScenarioIdentity = "order.payment.alipay";
    GXExtensionCoordinate coordinateFromString = new GXExtensionCoordinate(extPointName, bizScenarioIdentity);
    ```
    这种方式相对灵活，但牺牲了编译期类型安全，`extensionPointClass` 和 `bizScenario` 属性在此情况下会是 `null`。

**重要方法**：

*   `getExtensionPointClass()`: 返回扩展点接口的 `Class` 对象。
*   `getBizScenario()`: 返回 `GXBizScenario` 对象。
*   `getExtensionPointName()`: 返回扩展点接口的完全限定名。
*   `getBizScenarioUniqueIdentity()`: 返回业务场景的唯一标识字符串。
*   `equals(Object obj)` 和 `hashCode()`: 正确实现了这两个方法，确保了 `GXExtensionCoordinate` 对象可以作为 `HashMap` 等集合的键进行正确操作。

**线程安全性**：

`GXExtensionCoordinate` 类是不可变的（所有字段都是 `final` 并在构造时初始化），因此它是线程安全的。可以安全地在多线程环境中使用和共享。

通过 `GXExtensionCoordinate`，扩展框架能够清晰、高效地管理和定位在不同业务场景下的扩展点实现，是实现框架动态性的关键组件之一。

### 3.12 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

**使用示例**：

```java
// 假设有一个订单处理服务
@Service
public class OrderService {

    @Resource
    private GXExtensionExecutor extensionExecutor;

    // 订单处理扩展点接口
    public interface OrderProcessExtPoint extends GXExtensionPoint {
        OrderResult process(Order orderData);
    }

    // 订单数据类
    public static class Order { /* ... */ }
    public static class OrderResult { /* ... */ }

    public OrderResult handleOrder(Order orderData, String bizId, String useCase, String scenario) {
        // 1. 创建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);

        // 2. 执行扩展点方法
        //    - 第一个参数：扩展点接口的Class对象
        //    - 第二个参数：业务场景对象
        //    - 第三个参数：一个函数式接口，接收找到的扩展点实例，并调用其方法
        return extensionExecutor.execute(
            OrderProcessExtPoint.class, 
            bizScenario, 
            extPoint -> extPoint.process(orderData)
        );
    }

    public void notifyUser(Order orderData, String bizId, String useCase, String scenario) {
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
        // 如果扩展点方法无返回值，可以使用 executeVoid
        extensionExecutor.executeVoid(
            NotificationExtPoint.class, // 假设有 NotificationExtPoint 扩展点
            bizScenario,
            extPoint -> extPoint.sendNotification(orderData) // 假设 sendNotification 是 void 方法
        );
    }
}
```

在上面的示例中，`extensionExecutor.execute(...)` 会首先尝试查找 `bizId.useCase.scenario` 对应的 `OrderProcessExtPoint` 实现。如果找不到，它会依次尝试 `bizId.useCase.#defaultScenario#` 和 `bizId.#defaultUseCase#.#defaultScenario#`。

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。
    这种方式创建的 `GXExtensionCoordinate` 对象中，`extensionPointClass` 和 `bizScenario` 属性会是 `null`，因为它仅基于字符串名称。参数会校验是否为 `null` 或空字符串。

**主要用途**：

-   作为 `GXExtensionRepository` 内部 `Map` 的Key，存储和检索扩展点实现。
-   在 `GXExtensionExecutor` 中，根据业务代码提供的扩展点接口类和业务场景，构建此坐标以查找相应的扩展点实现。

**`equals()` 和 `hashCode()` 实现**：

`GXExtensionCoordinate` 的 `equals()` 方法比较的是 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。`hashCode()` 方法也是基于这两个属性计算的。这意味着，只要扩展点接口的类名和业务场景的唯一标识相同，两个 `GXExtensionCoordinate` 对象就被认为是相等的。这确保了在 `HashMap` 中能够正确定位。


### 3.6 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` 是扩展点实例的中央存储仓库。它内部使用 `java.util.concurrent.ConcurrentHashMap<GXExtensionCoordinate, GXExtensionPoint>` 来存储已注册的扩展点实现，确保了线程安全的并发访问。

-   **注册**：`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)` 方法用于将一个扩展点实现与一个扩展坐标关联起来并存入仓库。如果坐标已存在，则会覆盖旧的实现。
-   **查找**：`findExtension(GXExtensionCoordinate coordinate)` 方法根据坐标查找扩展点实现，返回 `Optional<GXExtensionPoint>`。
-   `getExtensionRepo()`: 返回底层的 `Map` 引用，但不推荐直接操作此Map。

### 3.7 `GXExtensionRegister`

`cn.maple.extension.register.GXExtensionRegister` 负责实际的扩展点注册逻辑。它被 `GXExtensionBootstrap` 调用。

-   **`doRegistration(GXExtensionPoint extensionObject)`**：处理单个 `@GXExtension` 注解的注册。
-   **`doRegistrationExtensions(GXExtensionPoint extensionObject)`**：处理 `@GXExtensions` 注解的注册，包括解析 `value()` 中的多个 `@GXExtension` 和处理 `bizId`, `useCase`, `scenario` 数组的笛卡尔积。
-   **AOP处理**：在注册前，会检查 `extensionObject` 是否为AOP代理对象（使用 `AopUtils.isAopProxy` 和 `ClassUtils.getUserClass`），如果是，则获取其原始目标类来读取注解信息，确保AOP不影响扩展点机制。
-   **扩展点接口推断**：`calculateExtensionPoint(Class<?> targetClz)` 方法会检查实现类所实现的所有接口，找到其中简单名称包含 `EXTENSION_EXT_PT_NAMING` ("ExtPoint") 的接口作为扩展点接口。这是框架约定的一部分。
-   **重复注册处理**：如果尝试注册一个已存在的 `GXExtensionCoordinate`，会打印警告日志，但不会抛出异常，新的实现会覆盖旧的。

### 3.8 `GXExtensionBootstrap`

`cn.maple.extension.register.GXExtensionBootstrap` 是一个Spring组件 (`@Component`)，它利用 `@PostConstruct` 注解，在Spring容器完成Bean的初始化之后，自动执行扩展点的扫描和注册流程。

-   **`init()` 方法**：
    1.  通过 `GXSpringContextUtils.getApplicationContext()` 获取Spring应用上下文。
    2.  获取所有被 `@GXExtension` 注解的Bean，并调用 `extensionRegister.doRegistration()` 进行注册。
    3.  获取所有被 `@GXExtensions` 注解的Bean，并调用 `extensionRegister.doRegistrationExtensions()` 进行注册。
    4.  确保被注解的Bean都实现了 `GXExtensionPoint` 接口，否则抛出 `IllegalStateException`。

### 3.9 `GXAbstractComponentExecutor` 和 `GXExtensionExecutor`

-   **`cn.maple.extension.register.GXAbstractComponentExecutor`**：这是一个抽象类，定义了执行扩展点的基本框架。它提供了两个核心的 `execute` (有返回值) 和 `executeVoid` (无返回值) 方法的重载版本。这些方法接受扩展点接口类、业务场景以及一个函数式接口（`Function` 或 `Consumer`）作为参数。它将定位组件的逻辑委托给抽象方法 `locateComponent`。

-   **`cn.maple.extension.GXExtensionExecutor`**：这是 `GXAbstractComponentExecutor` 的具体实现类，也是业务代码直接注入和使用的扩展点执行入口。它实现了 `locateComponent` 方法，该方法内部调用 `locateExtension`。
    -   **`locateExtension(Class<E> targetClz, GXBizScenario bizScenario)` 的查找降级策略**：
        1.  **精确匹配**：尝试使用 `bizScenario.getUniqueIdentity()` (如 `biz.useCase.scenario`) 查找。
        2.  **默认场景**：如果未找到，尝试使用 `bizScenario.getIdentityWithDefaultScenario()` (如 `biz.useCase.#defaultScenario#`) 查找。
        3.  **默认用例和默认场景**：如果仍未找到，尝试使用 `bizScenario.getIdentityWithDefaultUseCase()` (如 `biz.#defaultUseCase#.#defaultScenario#`) 查找。
        4.  **未找到**：如果所有尝试都失败，则抛出 `GXExtensionException`。
    -   它依赖 `GXExtensionRepository` 来实际获取扩展点实例。

### 3.10 `autoconfigure/ExtensionAutoConfiguration`

`cn.maple.extension.autoconfigure.ExtensionAutoConfiguration` 是一个Spring Boot的自动配置类 (`@Configuration`)。它负责将 `leaf-base-extension` 模块的核心组件（如 `GXExtensionBootstrap`, `GXExtensionRepository`, `GXExtensionExecutor`, `GXExtensionRegister`）自动注册为Spring容器中的Bean。

-   使用了 `@ConditionalOnMissingBean` 注解，这意味着如果用户在自己的配置中定义了同类型的Bean，则自动配置会跳过，允许用户自定义覆盖这些核心组件的实现。
-   `GXExtensionBootstrap` Bean定义中使用了 `initMethod = "init"`，确保在Bean实例化并注入依赖后，其 `init()` 方法被调用，从而启动扩展点注册流程。

### 3.11 `exception/GXExtensionException`

`cn.maple.extension.exception.GXExtensionException` 是模块自定义的运行时异常，继承自 `cn.maple.core.framework.exception.GXBusinessException`。当框架在处理扩展点过程中遇到问题时（例如找不到扩展点实现、配置错误等），会抛出此异常。它提供了多个构造函数，方便包装错误信息、错误码和原始异常。

## 4. 工作流程

`leaf-base-extension` 模块的工作流程可以概括为以下几个阶段：

1.  **定义阶段**：
    *   开发者定义业务扩展点接口，该接口必须继承 `GXExtensionPoint`。

2.  **实现阶段**：
    *   开发者针对不同的业务场景，创建扩展点接口的实现类。
    *   使用 `@GXExtension` 或 `@GXExtensions` 注解标记实现类，并指定其适用的业务场景坐标 (`bizId`, `useCase`, `scenario`)。
    *   实现类本身可以是Spring Bean，可以享受Spring的依赖注入等特性。

3.  **注册阶段（应用启动时）**：
    *   Spring Boot应用启动，`ExtensionAutoConfiguration` 自动配置类被加载。
    *   核心组件 (`GXExtensionRepository`, `GXExtensionRegister`, `GXExtensionExecutor`, `GXExtensionBootstrap`) 被创建并注册为Spring Bean。
    *   `GXExtensionBootstrap` 的 `init()` 方法（通过 `@PostConstruct` 或 `initMethod`）被调用。
    *   `GXExtensionBootstrap` 从Spring上下文中获取所有被 `@GXExtension` 和 `@GXExtensions` 注解的Bean。
    *   对于每个找到的扩展点实现Bean：
        *   `GXExtensionRegister` 解析其注解，确定扩展点接口和业务场景坐标。
        *   如果实现类是AOP代理对象，则获取其原始类以读取注解。
        *   根据扩展点接口名（需包含 "ExtPoint"）和业务场景生成 `GXExtensionCoordinate`。
        *   将 `GXExtensionCoordinate` 和扩展点实现实例存入 `GXExtensionRepository` 的 `ConcurrentHashMap` 中。

4.  **执行阶段（应用运行时）**：
    *   业务代码需要执行某个扩展点逻辑时，首先注入 `GXExtensionExecutor`。
    *   业务代码根据当前上下文创建 `GXBizScenario` 对象。
    *   调用 `GXExtensionExecutor` 的 `execute` 或 `executeVoid` 方法，传入扩展点接口的 `Class` 对象、`GXBizScenario` 对象以及一个Lambda表达式（定义了如何调用扩展点方法）。
    *   `GXExtensionExecutor` 根据传入的 `Class` 和 `GXBizScenario`，按照降级策略（精确场景 -> 默认场景 -> 默认用例的默认场景）构造 `GXExtensionCoordinate`。
    *   使用 `GXExtensionCoordinate` 从 `GXExtensionRepository` 中查找对应的扩展点实现实例。
    *   如果找到实现，则执行传入的Lambda表达式，将查找到的扩展点实例作为参数传入。
    *   如果未找到任何匹配的实现（包括降级查找），则抛出 `GXExtensionException`。

```mermaid
graph LR
    A[开发者: 定义 ExtPoint接口] --> B(开发者: 实现 ExtPoint接口);
    B -- 注解 --> C{@GXExtension / @GXExtensions};
    
    subgraph 应用启动时 (Spring初始化)
        D[ExtensionAutoConfiguration] --> E{GXExtensionBootstrap (Bean)};
        E -- @PostConstruct --> F[init()方法];
        F -- 获取Spring上下文 --> G[扫描带注解的Bean];
        G --> H{GXExtensionRegister (Bean)};
        H -- 解析注解/处理AOP --> I[生成 GXExtensionCoordinate];
        I --> J{GXExtensionRepository (Bean)};
        H -- 注册扩展点 --> J;
    end

    subgraph 应用运行时
        K[业务代码] -- 注入 --> L{GXExtensionExecutor (Bean)};
        K --> M[创建 GXBizScenario];
        L -- 调用 execute/executeVoid --> N[查找扩展点 (含降级)];
        N -- 使用 GXExtensionCoordinate --> J;
        J -- 返回扩展点实例 --> N;
        N -- 执行Lambda --> O[执行扩展点业务逻辑];
        N -- 未找到 --> P[抛出 GXExtensionException];
    end
```

## 5. 高级特性

### 5.1 默认实现与降级策略

当 `GXExtensionExecutor` 尝试定位一个扩展点实现时，如果找不到与当前业务场景完全匹配的实现，它会自动尝试降级查找：

1.  **精确匹配**：`bizId.useCase.scenario`
2.  **默认场景**：`bizId.useCase.#defaultScenario#` (使用 `GXBizScenario.DEFAULT_SCENARIO`)
3.  **默认用例和默认场景**：`bizId.#defaultUseCase#.#defaultScenario#` (使用 `GXBizScenario.DEFAULT_USE_CASE` 和 `GXBizScenario.DEFAULT_SCENARIO`)

这允许开发者为某些业务或用例提供一个通用的“默认”实现，以应对未被显式配置的场景，增强系统的容错性。

```java
// 为 "trade.payment" 提供一个默认的支付实现
@Component
@GXExtension(bizId = "trade", useCase = "payment", scenario = GXBizScenario.DEFAULT_SCENARIO)
public class DefaultTradePaymentExtImpl implements PaymentExtPoint { /* ... */ }

// 为 "trade" 业务下的所有用例提供一个最终的默认实现
@Component
@GXExtension(bizId = "trade", useCase = GXBizScenario.DEFAULT_USE_CASE, scenario = GXBizScenario.DEFAULT_SCENARIO)
public class DefaultTradeExtImpl implements SomeOtherExtPoint { /* ... */ }
```

### 5.2 多业务场景支持

一个扩展点实现类可以服务于多个业务场景，主要通过以下两种方式配置：

1.  **重复使用 `@GXExtension` 注解** (需要类声明 `@Repeatable(GXExtensions.class)`)：

    ```java
    @Component
    @GXExtension(bizId = "b1", useCase = "uc1", scenario = "s1")
    @GXExtension(bizId = "b1", useCase = "uc1", scenario = "s2")
    public class MyExtImpl implements SomeExtPoint { /* ... */ }
    ```

### 3.6 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

### 3.7 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

### 3.8 `GXExtensionPoint`

`cn.maple.extension.GXExtensionPoint` 是一个核心标记接口，所有业务自定义的扩展点接口都需要继承此接口。它本身不包含任何方法，主要作用是向框架标识一个接口是扩展点定义。

**核心作用**：

*   **扩展点标识**：通过继承 `GXExtensionPoint`，开发者可以将一个普通的Java接口声明为一个扩展点。框架会识别这些接口，并根据业务场景动态查找和执行其实现。
*   **规范扩展定义**：它为所有扩展点提供了一个统一的父类型，便于框架进行管理和识别。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并让该接口继承 `GXExtensionPoint`。例如，定义一个支付相关的扩展点：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.OrderDTO;

/**
 * 支付扩展点
 */
public interface PaymentExtPoint extends GXExtensionPoint {

    /**
     * 支付操作
     * @param order 订单信息
     * @return 支付结果
     */
    String pay(OrderDTO order);
}
```

**实现扩展点**：

扩展点的具体实现类需要使用 `@GXExtension` 注解来标记，并指定其对应的业务场景。框架会扫描这些注解，并将实现注册到 `GXExtensionRepository` 中。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 */
@GXExtension(bizId = "order", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身是线程安全的，因为它只是一个标记接口，不包含任何状态或逻辑。

然而，**扩展点的具体实现类必须自行确保其线程安全性**。如果扩展点实现类中包含共享的可变状态，开发者需要采取适当的同步措施（如使用 `synchronized` 关键字、`java.util.concurrent` 包中的并发工具类等）来保证在多线程环境下的正确性和数据一致性。

通过 `GXExtensionPoint` 和 `@GXExtension` 注解的配合，框架能够灵活地管理和调用不同业务场景下的扩展实现，实现了业务逻辑的解耦和动态扩展。

### 3.9 `@GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它与 `GXExtensionPoint` 接口协同工作，帮助框架识别和注册不同业务场景下的扩展逻辑。

**核心作用**：

*   **声明扩展实现**：将一个普通的Java类标记为一个扩展点实现。
*   **指定业务场景**：通过 `bizId`、`useCase` 和 `scenario` 属性，精确定义该扩展实现所适用的业务场景。这些属性的值会与 `GXBizScenario` 对象进行匹配。
*   **支持多场景**：该注解是可重复的（`@Repeatable(GXExtensions.class)`），意味着同一个扩展点实现类可以服务于多个不同的业务场景。开发者可以在同一个类上使用多个 `@GXExtension` 注解，或者使用 `@GXExtensions` 注解来包裹多个 `@GXExtension`。

**注解属性**：

*   `bizId()`: 字符串类型，表示业务ID。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
*   `useCase()`: 字符串类型，表示用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
*   `scenario()`: 字符串类型，表示场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

**如何使用**：

将 `@GXExtension` 注解应用到实现了某个 `GXExtensionPoint` 接口的类上。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXBizScenario;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "alipay" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}

/**
 * 微信支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "wechat" 和 "wechatMini" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini") // 可重复注解
public class WechatPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用微信支付，订单号：" + order.getOrderNo());
        // 具体的微信支付逻辑
        return "Wechat payment successful for order: " + order.getOrderNo();
    }
}

// 或者使用 @GXExtensions (如果适用)
// @GXExtensions({
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat"),
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
// })
// public class WechatPaymentExtAlternatives implements PaymentExtPoint { ... }
```

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见，允许框架通过反射读取注解信息。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Repeatable(GXExtensions.class)`: 允许在同一个类上多次使用 `@GXExtension` 注解。

**线程安全性**：

`@GXExtension` 注解本身不涉及线程安全问题。然而，被此注解标记的扩展点实现类的线程安全性需要由开发者自行保证。如果实现类中存在共享的可变状态，必须采取适当的同步措施。

通过 `@GXExtension` 注解，开发者可以清晰地将业务逻辑的变体与特定的业务场景关联起来，框架则依据这些注解信息在运行时动态地选择和执行合适的扩展实现。

### 3.10 `@GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 的容器注解，主要用于支持一个扩展点实现类服务于多个业务场景的声明。当同一个类需要被多个 `@GXExtension` 注解标记时，或者需要通过属性组合（笛卡尔积）来批量定义业务场景时，可以使用 `@GXExtensions`。

**核心作用**：

*   **聚合多个 `@GXExtension`**：作为 `@GXExtension` 的 `value()` 属性的容器，允许在一个地方列出多个独立的业务场景定义。
*   **笛卡尔积生成业务场景**：提供 `bizId()`, `useCase()`, 和 `scenario()` 数组属性。框架会将这些数组中的元素进行笛卡尔积组合，为每一种组合自动生成一个业务场景，并将当前的扩展点实现注册到所有这些场景下。这对于一个实现需要覆盖大量有规律的场景组合时非常有用。
*   **Spring组件扫描**：此注解本身也被 `@Component` 标记，这意味着被 `@GXExtensions` 注解的类也会被Spring扫描为组件（如果Spring的组件扫描配置包含了该类所在的包）。

**注解属性**：

*   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。当每个业务场景需要精确、独立定义时使用。默认为空数组。
*   `bizId()`: `String[]` 类型，业务ID数组。默认为 `{GXBizScenario.DEFAULT_BIZ_ID}`。
*   `useCase()`: `String[]` 类型，用例数组。默认为 `{GXBizScenario.DEFAULT_USE_CASE}`。
*   `scenario()`: `String[]` 类型，场景数组。默认为 `{GXBizScenario.DEFAULT_SCENARIO}`。

**如何使用**：

**方式一：直接聚合多个 `@GXExtension`**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.NotificationExtPoint;

@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "confirmation", scenario = "email"),
    @GXExtension(bizId = "order", useCase = "shipment", scenario = "sms")
})
public class OrderNotificationExt implements NotificationExtPoint {
    @Override
    public void send(OrderDTO order, String message) {
        // 根据具体坐标执行不同的通知逻辑，或通用逻辑
        System.out.println("Sending notification for order: " + order.getOrderNo() + ", Message: " + message);
    }
}
```

**方式二：使用笛卡尔积组合**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.ReportingExtPoint;

// 此实现将注册到以下4个业务场景：
// 1. finance.monthly.summary
// 2. finance.monthly.detail
// 3. sales.monthly.summary
// 4. sales.monthly.detail
@GXExtensions(
    bizId = {"finance", "sales"},
    useCase = {"monthly"},
    scenario = {"summary", "detail"}
)
public class MonthlyReportExt implements ReportingExtPoint {
    @Override
    public String generateReport(OrderDTO criteria) {
        // 生成报表逻辑
        return "Generated monthly report.";
    }
}
```

**注意**：如果同时使用了 `value()` 属性和笛卡尔积属性（`bizId`, `useCase`, `scenario`），框架通常会优先处理 `value()` 中定义的 `@GXExtension`，然后处理笛卡尔积生成的场景。具体行为可能依赖于框架的实现细节，建议查阅框架文档或通过测试确认。

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Component`: 将被此注解标记的类声明为Spring组件。

**线程安全性**：

与 `@GXExtension` 类似，`@GXExtensions` 注解本身不影响线程安全。被标记的扩展点实现类的线程安全性由开发者负责。

`@GXExtensions` 提供了更灵活和强大的方式来管理扩展点实现与多个业务场景的映射关系，特别是在需要批量定义或组合场景时，能显著减少代码重复。

### 3.11 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate`（扩展坐标）是框架中用于唯一定位一个扩展点实现的标识对象。它封装了区分一个扩展点实现所需的两个关键信息：扩展点接口的定义和具体的业务场景。

**核心作用**：

*   **唯一标识扩展点实现**：通过组合扩展点接口的类名和业务场景的唯一标识，`GXExtensionCoordinate` 为每一个具体的扩展点实现提供了一个全局唯一的键。
*   **作为仓库的Key**：在 `GXExtensionRepository` 中，`GXExtensionCoordinate` 对象被用作 `ConcurrentHashMap` 的键，来存储和检索对应的扩展点实现实例。
*   **解耦扩展点调用**：调用方通过构建 `GXExtensionCoordinate` 来指定希望执行的扩展点和业务场景，而无需直接依赖具体的实现类。

**核心属性**：

*   `extensionPointName`: `String` 类型，存储扩展点接口的完全限定类名。
*   `bizScenarioUniqueIdentity`: `String` 类型，存储业务场景的唯一标识，格式通常为 `bizId.useCase.scenario`。
*   `extensionPointClass`: `Class<?>` 类型，运行时扩展点接口的 `Class` 对象。主要用于类型安全和反射操作。
*   `bizScenario`: `GXBizScenario` 类型，封装了业务场景的三个维度（`bizId`, `useCase`, `scenario`）。

**构造方式**：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象（推荐）**：

    ```java
    Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
    GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
    GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extPointClass, bizScenario);
    // 或者使用静态工厂方法
    GXExtensionCoordinate coordinateViaFactory = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
    ```
    这种方式在编译期就能进行类型检查，并且保留了完整的类型信息。

2.  **通过扩展点类名字符串和业务场景唯一标识字符串**：

    ```java
    String extPointName = "com.example.extension.point.PaymentExtPoint";
    String bizScenarioIdentity = "order.payment.alipay";
    GXExtensionCoordinate coordinateFromString = new GXExtensionCoordinate(extPointName, bizScenarioIdentity);
    ```
    这种方式相对灵活，但牺牲了编译期类型安全，`extensionPointClass` 和 `bizScenario` 属性在此情况下会是 `null`。

**重要方法**：

*   `getExtensionPointClass()`: 返回扩展点接口的 `Class` 对象。
*   `getBizScenario()`: 返回 `GXBizScenario` 对象。
*   `getExtensionPointName()`: 返回扩展点接口的完全限定名。
*   `getBizScenarioUniqueIdentity()`: 返回业务场景的唯一标识字符串。
*   `equals(Object obj)` 和 `hashCode()`: 正确实现了这两个方法，确保了 `GXExtensionCoordinate` 对象可以作为 `HashMap` 等集合的键进行正确操作。

**线程安全性**：

`GXExtensionCoordinate` 类是不可变的（所有字段都是 `final` 并在构造时初始化），因此它是线程安全的。可以安全地在多线程环境中使用和共享。

通过 `GXExtensionCoordinate`，扩展框架能够清晰、高效地管理和定位在不同业务场景下的扩展点实现，是实现框架动态性的关键组件之一。

### 3.12 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

**使用示例**：

```java
// 假设有一个订单处理服务
@Service
public class OrderService {

    @Resource
    private GXExtensionExecutor extensionExecutor;

    // 订单处理扩展点接口
    public interface OrderProcessExtPoint extends GXExtensionPoint {
        OrderResult process(Order orderData);
    }

    // 订单数据类
    public static class Order { /* ... */ }
    public static class OrderResult { /* ... */ }

    public OrderResult handleOrder(Order orderData, String bizId, String useCase, String scenario) {
        // 1. 创建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);

        // 2. 执行扩展点方法
        //    - 第一个参数：扩展点接口的Class对象
        //    - 第二个参数：业务场景对象
        //    - 第三个参数：一个函数式接口，接收找到的扩展点实例，并调用其方法
        return extensionExecutor.execute(
            OrderProcessExtPoint.class, 
            bizScenario, 
            extPoint -> extPoint.process(orderData)
        );
    }

    public void notifyUser(Order orderData, String bizId, String useCase, String scenario) {
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
        // 如果扩展点方法无返回值，可以使用 executeVoid
        extensionExecutor.executeVoid(
            NotificationExtPoint.class, // 假设有 NotificationExtPoint 扩展点
            bizScenario,
            extPoint -> extPoint.sendNotification(orderData) // 假设 sendNotification 是 void 方法
        );
    }
}
```

在上面的示例中，`extensionExecutor.execute(...)` 会首先尝试查找 `bizId.useCase.scenario` 对应的 `OrderProcessExtPoint` 实现。如果找不到，它会依次尝试 `bizId.useCase.#defaultScenario#` 和 `bizId.#defaultUseCase#.#defaultScenario#`。

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。
2.  **使用 `@GXExtensions` 注解**：
    *   通过 `value()` 属性指定多个 `@GXExtension`。
    *   通过 `bizId()`, `useCase()`, `scenario()` 属性数组的笛卡尔积组合。

    参考 [3.3 `GXExtensions`](#33-gxextensions) 章节的示例。

### 5.3 AOP支持

`leaf-base-extension` 框架能够正确处理被Spring AOP代理的扩展点实现类。在注册阶段，`GXExtensionRegister` 会使用 `org.springframework.aop.support.AopUtils.isAopProxy()` 和 `org.springframework.util.ClassUtils.getUserClass()` 来检测Bean是否为AOP代理。如果是，它会获取原始的目标类（User Class），并从原始类上读取 `@GXExtension` 或 `@GXExtensions` 注解信息。这确保了即使扩展点实现被AOP增强（例如事务管理、日志记录等），其扩展点元数据也能被正确识别和注册。

### 5.4 异常处理

框架统一使用 `cn.maple.extension.exception.GXExtensionException` 来报告与扩展点相关的错误。常见的抛出场景包括：

-   在执行阶段，经过所有降级尝试后仍未找到任何匹配的扩展点实现。
-   在注册阶段，被注解的Bean未实现 `GXExtensionPoint` 接口。
-   在注册阶段，扩展点实现类没有实现任何名称包含 "ExtPoint" 的接口。

业务代码应该准备捕获此异常，并根据需要进行处理。

```java
try {
    PayResultDTO result = extensionExecutor.execute(PaymentExtPoint.class, scenario, 
            extension -> extension.pay(order));
    // ...
} catch (GXExtensionException e) {
    log.error("Extension execution failed for scenario {}: {}", scenario.getUniqueIdentity(), e.getMessage(), e);
    // 进行错误处理，例如返回默认结果或提示用户
}
```

## 6. 实际应用示例

（此部分与原文档基本一致，仅作格式微调和代码块语言标识）

以下是基于框架测试代码的实际应用示例，展示了扩展点框架在客户管理系统中的应用。

### 6.1 定义常量

首先定义业务场景相关的常量：

```java
package com.example.common;

public class GXConstants {
    public static final String BIZ_1 = "BIZ_ONE";
    public static final String USE_CASE_1 = "USE_CASE_ONE";
    public static final String SCENARIO_1 = "SCENARIO_ONE";
    public static final String BIZ_2 = "BIZ_TWO";
    
    public static final String SOURCE_AD = "Advertisement";
    public static final String SOURCE_WB = "Web site";
    public static final String SOURCE_RFQ = "Request For Quota";
    public static final String SOURCE_MARKETING = "Marketing";
    public static final String SOURCE_OFFLINE = "Off Line";
}
```

### 6.2 定义扩展点接口

定义客户验证扩展点接口：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.command.AddCustomerCmd;

public interface AddCustomerValidatorExtPoint extends GXExtensionPoint {
    void validate(AddCustomerCmd addCustomerCmd);
}
```

定义客户规则扩展点接口：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.domain.customer.CustomerEntity;

public interface CustomerRuleExtPoint extends GXExtensionPoint {
    boolean addCustomerCheck(CustomerEntity customerEntity);

    default void customerUpgradePolicy(CustomerEntity customerEntity) {
        //Nothing special
    }
}
```

### 6.3 实现扩展点接口

为不同业务场景实现客户验证扩展点：

```java
package com.example.extension.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.extension.GXExtension;
import com.example.command.AddCustomerCmd;
import com.example.common.GXConstants;
import com.example.domain.customer.CustomerType;
import com.example.extension.point.AddCustomerValidatorExtPoint;
import org.springframework.stereotype.Component;

@GXExtension(bizId = GXConstants.BIZ_1)
@Component
public class AddCustomerBizOneValidator implements AddCustomerValidatorExtPoint {
    public void validate(AddCustomerCmd addCustomerCmd) {
        //For BIZ ONE CustomerType could not be VIP
        if (CustomerType.VIP == addCustomerCmd.getCustomerDTO().getCustomerType())
            throw new GXBusinessException("Customer Type could not be VIP for Biz One");
    }
}
```

为不同业务场景实现客户规则扩展点：

```java
package com.example.extension.impl;

import cn.maple.core.framework.exception.GXBusinessException;
import cn.maple.extension.GXExtension;
import com.example.common.GXConstants;
import com.example.domain.customer.CustomerEntity;
import com.example.domain.customer.SourceType;
import com.example.extension.point.CustomerRuleExtPoint;
import org.springframework.stereotype.Component;

@GXExtension(bizId = GXConstants.BIZ_1)
@Component
public class CustomerBizOneRuleExt implements CustomerRuleExtPoint {
    @Override
    public boolean addCustomerCheck(CustomerEntity customerEntity) {
        if (SourceType.AD == customerEntity.getSourceType()) {
            throw new GXBusinessException("Sorry, Customer from advertisement can not be added in this period");
        }
        return true;
    }
}
```

### 6.4 在业务逻辑中使用扩展点

在命令执行器中使用扩展点：

```java
package com.example.command.executor;

import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.extension.GXExtensionExecutor;
import com.example.command.AddCustomerCmd;
import com.example.domain.customer.CustomerEntity;
// import com.example.domain.event.DomainEventPublisher; // 假设的领域事件发布器
import com.example.extension.convertor.CustomerConvertorExtPoint; // 假设的转换器扩展点
import com.example.extension.point.AddCustomerValidatorExtPoint;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddCustomerCmdExe {
    private final Logger logger = LoggerFactory.getLogger(AddCustomerCmdExe.class);

    @Resource
    private GXExtensionExecutor extensionExecutor;

    // @Resource
    // private DomainEventPublisher domainEventPublisher; // 假设的领域事件发布器

    public GXResultUtils<String> execute(AddCustomerCmd cmd) {
        logger.info("Start processing command:" + cmd);

        //validation
        extensionExecutor.executeVoid(AddCustomerValidatorExtPoint.class, cmd.getBizScenario(), 
                extension -> extension.validate(cmd));

        //Convert CO to Entity
        CustomerEntity customerEntity = extensionExecutor.execute(CustomerConvertorExtPoint.class, 
                cmd.getBizScenario(), extension -> extension.clientToEntity(cmd));

        //Call Domain Entity for business logic processing
        logger.info("Call Domain Entity for business logic processing..." + customerEntity);
        customerEntity.addNewCustomer(); // 假设CustomerEntity有此方法

        //domainEventPublisher.publish(new CustomerCreatedEvent());
        logger.info("End processing command:" + cmd);
        return GXResultUtils.ok("Success");
    }
}
```

### 6.5 测试扩展点

编写测试用例验证扩展点功能：

```java
package com.example.test;

import cn.maple.core.framework.util.GXResultUtils;
import cn.maple.extension.GXBizScenario;
import com.example.command.AddCustomerCmd;
import com.example.common.GXConstants;
import com.example.dto.CustomerDto;
import com.example.common.CustomerType; // 假设的客户类型枚举
import com.example.service.CustomerService; // 假设的客户服务
import jakarta.annotation.Resource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ExtTestApplication.class) // ExtTestApplication是测试用的Spring Boot启动类
public class ExtensionTest {
    @Resource
    private CustomerService customerService; // 假设CustomerService内部调用AddCustomerCmdExe

    @Test
    public void testBiz1UseCase1Scenario1AddCustomerSuccess() {
        //1. Prepare
        AddCustomerCmd addCustomerCmd = new AddCustomerCmd();
        CustomerDto customerDTO = new CustomerDto();
        customerDTO.setCompanyName("alibaba");
        // customerDTO.setSource(GXConstants.SOURCE_RFQ); // 假设SourceType在DTO中
        customerDTO.setCustomerType(CustomerType.IMPORTANT);
        addCustomerCmd.setCustomerDTO(customerDTO);
        GXBizScenario scenario = GXBizScenario.valueOf(GXConstants.BIZ_1, 
                GXConstants.USE_CASE_1, GXConstants.SCENARIO_1);
        addCustomerCmd.setBizScenario(scenario);

        //2. Execute
        GXResultUtils<String> response = customerService.addCustomer(addCustomerCmd);

        //3. Expect Success
        Assert.assertTrue(response != null && response.isSuccess());
    }
}
```

## 7. 最佳实践

### 7.1 扩展点命名规范

-   **扩展点接口**：名称建议以 `ExtPoint` 结尾，例如 `PaymentExtPoint`、`UserValidationExtPoint`。
-   **扩展点实现类**：名称建议清晰反映其业务场景和功能，可以 `Ext` 或 `ExtImpl` 结尾，例如 `AlipayPaymentExtImpl`、`VipUserValidationExt`。

### 7.2 业务场景定义规范

-   `bizId`：应具有业务领域代表性，如 `trade` (交易), `member` (会员), `promotion` (营销)。保持简洁明了。
-   `useCase`：描述具体的业务操作或流程，如 `createOrder`, `refund`, `applyCoupon`。
-   `scenario`：区分同一用例下的不同情况或渠道，如 `alipay`, `wechatpay`, `newUserDiscount`, `oldUserSpecial`。
-   **常量化**：建议将常用的 `bizId`, `useCase`, `scenario` 值定义为常量，便于维护和引用。
-   **粒度适中**：避免过度细化场景导致扩展点爆炸，也要避免场景过于宽泛导致扩展点内部逻辑复杂。

### 7.3 线程安全性

-   `leaf-base-extension` 框架的核心组件（如 `GXExtensionRepository`, `GXExtensionExecutor`）设计为线程安全的。
-   **扩展点实现类的线程安全责任**：开发者需要自行确保扩展点实现类的线程安全性。如果实现类是无状态的，或者状态是不可变的，则通常是线程安全的。如果实现类有可变状态，并且会被多线程并发访问（例如作为单例Spring Bean），则需要采取适当的同步措施（如使用 `synchronized`、`java.util.concurrent` 包下的工具）或设计为无状态服务。

### 7.4 性能优化

-   **避免在扩展点方法中执行长时间阻塞操作**：如果扩展点逻辑涉及IO密集型或CPU密集型操作，考虑将其设计为异步执行，或优化其内部实现。
-   **合理设计业务场景**：避免不必要的场景细分，这可能导致扩展点数量过多，增加查找开销（尽管 `ConcurrentHashMap` 查找效率高，但过多的Key依然有影响）。
-   **利用默认实现**：对于通用逻辑，使用默认实现可以减少具体场景实现类的数量。
-   **缓存**：如果扩展点方法的计算结果可以被缓存，并且适用于特定场景，可以在扩展点实现内部或外部增加缓存逻辑。

### 7.5 日志记录

-   **框架日志**：`leaf-base-extension` 框架本身在关键步骤（如扩展点注册、查找失败）会输出日志（通常是DEBUG或WARN级别）。
-   **扩展点实现日志**：建议在扩展点实现的关键业务逻辑处添加清晰的日志记录，包括输入参数、重要决策点和输出结果。这对于问题排查和行为审计至关重要。
    ```java
    @Component
    @GXExtension(bizId = "logistics", useCase = "calculateFee", scenario = "sf_express")
    public class SfExpressFeeExtImpl implements FeeCalculationExtPoint {
        private static final Logger log = LoggerFactory.getLogger(SfExpressFeeExtImpl.class);
        
        @Override
        public BigDecimal calculate(OrderInfo order) {
            log.info("[SF_EXPRESS_FEE] Calculating fee for order: {}, weight: {}", order.getId(), order.getWeight());
            // ... calculation logic ...
            BigDecimal fee = ...;
            log.info("[SF_EXPRESS_FEE] Calculated fee: {} for order: {}", fee, order.getId());
            return fee;
        }
    }
    ```

### 3.6 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

### 3.7 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

### 3.8 `GXExtensionPoint`

`cn.maple.extension.GXExtensionPoint` 是一个核心标记接口，所有业务自定义的扩展点接口都需要继承此接口。它本身不包含任何方法，主要作用是向框架标识一个接口是扩展点定义。

**核心作用**：

*   **扩展点标识**：通过继承 `GXExtensionPoint`，开发者可以将一个普通的Java接口声明为一个扩展点。框架会识别这些接口，并根据业务场景动态查找和执行其实现。
*   **规范扩展定义**：它为所有扩展点提供了一个统一的父类型，便于框架进行管理和识别。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并让该接口继承 `GXExtensionPoint`。例如，定义一个支付相关的扩展点：

```java
package com.example.extension.point;

import cn.maple.extension.GXExtensionPoint;
import com.example.OrderDTO;

/**
 * 支付扩展点
 */
public interface PaymentExtPoint extends GXExtensionPoint {

    /**
     * 支付操作
     * @param order 订单信息
     * @return 支付结果
     */
    String pay(OrderDTO order);
}
```

**实现扩展点**：

扩展点的具体实现类需要使用 `@GXExtension` 注解来标记，并指定其对应的业务场景。框架会扫描这些注解，并将实现注册到 `GXExtensionRepository` 中。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 */
@GXExtension(bizId = "order", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身是线程安全的，因为它只是一个标记接口，不包含任何状态或逻辑。

然而，**扩展点的具体实现类必须自行确保其线程安全性**。如果扩展点实现类中包含共享的可变状态，开发者需要采取适当的同步措施（如使用 `synchronized` 关键字、`java.util.concurrent` 包中的并发工具类等）来保证在多线程环境下的正确性和数据一致性。

通过 `GXExtensionPoint` 和 `@GXExtension` 注解的配合，框架能够灵活地管理和调用不同业务场景下的扩展实现，实现了业务逻辑的解耦和动态扩展。

### 3.9 `@GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它与 `GXExtensionPoint` 接口协同工作，帮助框架识别和注册不同业务场景下的扩展逻辑。

**核心作用**：

*   **声明扩展实现**：将一个普通的Java类标记为一个扩展点实现。
*   **指定业务场景**：通过 `bizId`、`useCase` 和 `scenario` 属性，精确定义该扩展实现所适用的业务场景。这些属性的值会与 `GXBizScenario` 对象进行匹配。
*   **支持多场景**：该注解是可重复的（`@Repeatable(GXExtensions.class)`），意味着同一个扩展点实现类可以服务于多个不同的业务场景。开发者可以在同一个类上使用多个 `@GXExtension` 注解，或者使用 `@GXExtensions` 注解来包裹多个 `@GXExtension`。

**注解属性**：

*   `bizId()`: 字符串类型，表示业务ID。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
*   `useCase()`: 字符串类型，表示用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
*   `scenario()`: 字符串类型，表示场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

**如何使用**：

将 `@GXExtension` 注解应用到实现了某个 `GXExtensionPoint` 接口的类上。

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXBizScenario;
import com.example.OrderDTO;
import com.example.extension.point.PaymentExtPoint;

/**
 * 支付宝支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "alipay" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用支付宝支付，订单号：" + order.getOrderNo());
        // 具体的支付宝支付逻辑
        return "Alipay payment successful for order: " + order.getOrderNo();
    }
}

/**
 * 微信支付扩展点实现
 * 适用于 "mall" 业务的 "payment" 用例下的 "wechat" 和 "wechatMini" 场景
 */
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini") // 可重复注解
public class WechatPaymentExt implements PaymentExtPoint {

    @Override
    public String pay(OrderDTO order) {
        System.out.println("使用微信支付，订单号：" + order.getOrderNo());
        // 具体的微信支付逻辑
        return "Wechat payment successful for order: " + order.getOrderNo();
    }
}

// 或者使用 @GXExtensions (如果适用)
// @GXExtensions({
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat"),
//    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
// })
// public class WechatPaymentExtAlternatives implements PaymentExtPoint { ... }
```

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见，允许框架通过反射读取注解信息。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Repeatable(GXExtensions.class)`: 允许在同一个类上多次使用 `@GXExtension` 注解。

**线程安全性**：

`@GXExtension` 注解本身不涉及线程安全问题。然而，被此注解标记的扩展点实现类的线程安全性需要由开发者自行保证。如果实现类中存在共享的可变状态，必须采取适当的同步措施。

通过 `@GXExtension` 注解，开发者可以清晰地将业务逻辑的变体与特定的业务场景关联起来，框架则依据这些注解信息在运行时动态地选择和执行合适的扩展实现。

### 3.10 `@GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 的容器注解，主要用于支持一个扩展点实现类服务于多个业务场景的声明。当同一个类需要被多个 `@GXExtension` 注解标记时，或者需要通过属性组合（笛卡尔积）来批量定义业务场景时，可以使用 `@GXExtensions`。

**核心作用**：

*   **聚合多个 `@GXExtension`**：作为 `@GXExtension` 的 `value()` 属性的容器，允许在一个地方列出多个独立的业务场景定义。
*   **笛卡尔积生成业务场景**：提供 `bizId()`, `useCase()`, 和 `scenario()` 数组属性。框架会将这些数组中的元素进行笛卡尔积组合，为每一种组合自动生成一个业务场景，并将当前的扩展点实现注册到所有这些场景下。这对于一个实现需要覆盖大量有规律的场景组合时非常有用。
*   **Spring组件扫描**：此注解本身也被 `@Component` 标记，这意味着被 `@GXExtensions` 注解的类也会被Spring扫描为组件（如果Spring的组件扫描配置包含了该类所在的包）。

**注解属性**：

*   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。当每个业务场景需要精确、独立定义时使用。默认为空数组。
*   `bizId()`: `String[]` 类型，业务ID数组。默认为 `{GXBizScenario.DEFAULT_BIZ_ID}`。
*   `useCase()`: `String[]` 类型，用例数组。默认为 `{GXBizScenario.DEFAULT_USE_CASE}`。
*   `scenario()`: `String[]` 类型，场景数组。默认为 `{GXBizScenario.DEFAULT_SCENARIO}`。

**如何使用**：

**方式一：直接聚合多个 `@GXExtension`**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtension;
import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.NotificationExtPoint;

@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "confirmation", scenario = "email"),
    @GXExtension(bizId = "order", useCase = "shipment", scenario = "sms")
})
public class OrderNotificationExt implements NotificationExtPoint {
    @Override
    public void send(OrderDTO order, String message) {
        // 根据具体坐标执行不同的通知逻辑，或通用逻辑
        System.out.println("Sending notification for order: " + order.getOrderNo() + ", Message: " + message);
    }
}
```

**方式二：使用笛卡尔积组合**

```java
package com.example.extension.impl;

import cn.maple.extension.GXExtensions;
import com.example.OrderDTO;
import com.example.extension.point.ReportingExtPoint;

// 此实现将注册到以下4个业务场景：
// 1. finance.monthly.summary
// 2. finance.monthly.detail
// 3. sales.monthly.summary
// 4. sales.monthly.detail
@GXExtensions(
    bizId = {"finance", "sales"},
    useCase = {"monthly"},
    scenario = {"summary", "detail"}
)
public class MonthlyReportExt implements ReportingExtPoint {
    @Override
    public String generateReport(OrderDTO criteria) {
        // 生成报表逻辑
        return "Generated monthly report.";
    }
}
```

**注意**：如果同时使用了 `value()` 属性和笛卡尔积属性（`bizId`, `useCase`, `scenario`），框架通常会优先处理 `value()` 中定义的 `@GXExtension`，然后处理笛卡尔积生成的场景。具体行为可能依赖于框架的实现细节，建议查阅框架文档或通过测试确认。

**元注解**：

*   `@Inherited`: 表示该注解可以被子类继承。
*   `@Retention(RetentionPolicy.RUNTIME)`: 表示该注解在运行时可见。
*   `@Target({ElementType.TYPE})`: 表示该注解只能应用于类级别。
*   `@Component`: 将被此注解标记的类声明为Spring组件。

**线程安全性**：

与 `@GXExtension` 类似，`@GXExtensions` 注解本身不影响线程安全。被标记的扩展点实现类的线程安全性由开发者负责。

`@GXExtensions` 提供了更灵活和强大的方式来管理扩展点实现与多个业务场景的映射关系，特别是在需要批量定义或组合场景时，能显著减少代码重复。

### 3.11 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate`（扩展坐标）是框架中用于唯一定位一个扩展点实现的标识对象。它封装了区分一个扩展点实现所需的两个关键信息：扩展点接口的定义和具体的业务场景。

**核心作用**：

*   **唯一标识扩展点实现**：通过组合扩展点接口的类名和业务场景的唯一标识，`GXExtensionCoordinate` 为每一个具体的扩展点实现提供了一个全局唯一的键。
*   **作为仓库的Key**：在 `GXExtensionRepository` 中，`GXExtensionCoordinate` 对象被用作 `ConcurrentHashMap` 的键，来存储和检索对应的扩展点实现实例。
*   **解耦扩展点调用**：调用方通过构建 `GXExtensionCoordinate` 来指定希望执行的扩展点和业务场景，而无需直接依赖具体的实现类。

**核心属性**：

*   `extensionPointName`: `String` 类型，存储扩展点接口的完全限定类名。
*   `bizScenarioUniqueIdentity`: `String` 类型，存储业务场景的唯一标识，格式通常为 `bizId.useCase.scenario`。
*   `extensionPointClass`: `Class<?>` 类型，运行时扩展点接口的 `Class` 对象。主要用于类型安全和反射操作。
*   `bizScenario`: `GXBizScenario` 类型，封装了业务场景的三个维度（`bizId`, `useCase`, `scenario`）。

**构造方式**：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象（推荐）**：

    ```java
    Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
    GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
    GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extPointClass, bizScenario);
    // 或者使用静态工厂方法
    GXExtensionCoordinate coordinateViaFactory = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);
    ```
    这种方式在编译期就能进行类型检查，并且保留了完整的类型信息。

2.  **通过扩展点类名字符串和业务场景唯一标识字符串**：

    ```java
    String extPointName = "com.example.extension.point.PaymentExtPoint";
    String bizScenarioIdentity = "order.payment.alipay";
    GXExtensionCoordinate coordinateFromString = new GXExtensionCoordinate(extPointName, bizScenarioIdentity);
    ```
    这种方式相对灵活，但牺牲了编译期类型安全，`extensionPointClass` 和 `bizScenario` 属性在此情况下会是 `null`。

**重要方法**：

*   `getExtensionPointClass()`: 返回扩展点接口的 `Class` 对象。
*   `getBizScenario()`: 返回 `GXBizScenario` 对象。
*   `getExtensionPointName()`: 返回扩展点接口的完全限定名。
*   `getBizScenarioUniqueIdentity()`: 返回业务场景的唯一标识字符串。
*   `equals(Object obj)` 和 `hashCode()`: 正确实现了这两个方法，确保了 `GXExtensionCoordinate` 对象可以作为 `HashMap` 等集合的键进行正确操作。

**线程安全性**：

`GXExtensionCoordinate` 类是不可变的（所有字段都是 `final` 并在构造时初始化），因此它是线程安全的。可以安全地在多线程环境中使用和共享。

通过 `GXExtensionCoordinate`，扩展框架能够清晰、高效地管理和定位在不同业务场景下的扩展点实现，是实现框架动态性的关键组件之一。

### 3.12 `GXExtensionRepository`

`cn.maple.extension.GXExtensionRepository` (扩展仓库) 是扩展点框架的核心组件之一，扮演着存储和管理所有扩展点实现的中心角色。当应用程序启动时，框架会自动扫描所有被 `@GXExtension` 或 `@GXExtensions` 注解标记的类，将它们实例化，并根据其定义的业务场景和扩展点接口，以 `GXExtensionCoordinate` 作为Key，扩展点实现实例作为Value，存入 `GXExtensionRepository` 中。

当业务逻辑需要执行某个扩展点时，会构建一个 `GXExtensionCoordinate` 来描述目标扩展点和当前业务场景，然后使用这个坐标从 `GXExtensionRepository` 中检索相应的扩展点实现。

**核心特性**：

-   **中央存储**：集中管理所有的扩展点实现实例。
-   **快速查找**：通过 `GXExtensionCoordinate` 高效定位扩展点实现。
-   **线程安全**：内部使用 `java.util.concurrent.ConcurrentHashMap` 来存储扩展点，保证了在多线程环境下的并发安全访问和修改。

**核心API**：

-   **`getExtensionRepo()`**: 返回一个 `Map<GXExtensionCoordinate, GXExtensionPoint>`，这是实际存储扩展点实现的内部 `ConcurrentHashMap` 的只读视图（虽然返回的是原始Map引用，但其本身是线程安全的）。通常不直接操作此Map，而是通过其他方法间接访问。

-   **`findExtension(GXExtensionCoordinate coordinate)`**: 根据给定的 `GXExtensionCoordinate` 查找对应的扩展点实现。如果找到，则返回一个包含该实现的 `Optional<GXExtensionPoint>`；如果未找到，则返回 `Optional.empty()`。这是获取扩展点实现的主要方式。
    ```java
    // 假设 extensionRepository 是注入的 GXExtensionRepository 实例
    // 假设 coordinate 是已经构建好的 GXExtensionCoordinate 实例
    Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                    .map(ext -> (PaymentExtPoint) ext);
    if (paymentExtPointOpt.isPresent()) {
        PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
        // 使用 paymentExtPoint
    } else {
        // 处理未找到扩展点的情况
    }
    ```

-   **`registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`**: 用于手动注册一个扩展点实现。这在某些动态注册或测试场景下可能有用，但通常情况下，扩展点的注册是由框架在启动时自动完成的。如果指定的坐标已存在扩展点实现，新的实现会覆盖旧的。

**使用示例**：

```java
// 1. 在Spring组件中注入 GXExtensionRepository
@Service
public class OrderService {

    @Autowired
    private GXExtensionRepository extensionRepository;

    public void processPayment(OrderDTO order, String paymentType) {
        // 2. 构建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", paymentType);

        // 3. 构建扩展坐标
        GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);

        // 4. 从仓库查找扩展点实现
        Optional<PaymentExtPoint> paymentExtPointOpt = extensionRepository.findExtension(coordinate)
                                                                        .map(ext -> (PaymentExtPoint) ext);

        if (paymentExtPointOpt.isPresent()) {
            PaymentExtPoint paymentExtPoint = paymentExtPointOpt.get();
            // 5. 执行扩展点方法
            paymentExtPoint.pay(order);
        } else {
            // 处理未找到支付扩展点的情况，例如抛出异常或执行默认逻辑
            System.out.println("未找到支付类型 " + paymentType + " 的扩展点实现。");
        }
    }
}
```

`GXExtensionRepository` 的设计确保了扩展点框架的灵活性和可维护性，使得业务方可以方便地根据不同的业务场景调用相应的扩展功能，而无需硬编码具体的实现类。

### 3.13 `GXExtensionExecutor`

`cn.maple.extension.GXExtensionExecutor` (扩展执行器) 是实际执行扩展点逻辑的组件。它继承自 `cn.maple.extension.register.GXAbstractComponentExecutor`，并依赖 `GXExtensionRepository` 来查找和定位扩展点实现。

当业务代码需要调用某个扩展点时，通常会通过 `GXExtensionExecutor` 来完成。执行器会根据传入的扩展点接口类 (`Class<E>`) 和业务场景 (`GXBizScenario`)，在 `GXExtensionRepository` 中查找匹配的扩展点实现。

**核心功能**：

-   **扩展点定位**：实现了 `locateComponent` 方法，该方法内部调用 `locateExtension` 来查找扩展点。
-   **执行扩展点**：提供了 `execute` 和 `executeVoid` 方法（继承自 `GXAbstractComponentExecutor`）来执行查找到的扩展点的方法。

**扩展点查找策略（降级机制）**：

`GXExtensionExecutor` 在查找扩展点实现时，采用了一种降级策略，以提高灵活性和容错性：

1.  **精确匹配**：首先，尝试使用业务场景的完整唯一标识（`bizId.useCase.scenario`）进行查找。例如，如果业务场景是 `order.payment.alipay`，则查找此精确场景下的扩展点。

2.  **默认场景匹配**：如果精确匹配未找到，则尝试使用带有默认场景的标识（`bizId.useCase.#defaultScenario#`）进行查找。例如，如果 `order.payment.alipay` 未找到，则尝试查找 `order.payment.#defaultScenario#`。

3.  **默认用例和默认场景匹配**：如果上述两种方式均未找到，则尝试使用带有默认用例和默认场景的标识（`bizId.#defaultUseCase#.#defaultScenario#`）进行查找。例如，如果 `order.payment.#defaultScenario#` 仍未找到，则尝试查找 `order.#defaultUseCase#.#defaultScenario#`。

4.  **查找失败**：如果所有尝试都失败，即在上述三个层级都找不到对应的扩展点实现，则会抛出 `GXExtensionException` 异常，提示找不到扩展点。

这种降级策略允许开发者为通用场景提供默认的扩展点实现，同时为特定场景提供定制化的实现。

**线程安全性**：

`GXExtensionExecutor` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionExecutor` 也是线程安全的，可以在多线程环境中安全使用。

**使用示例**：

```java
// 在业务代码中使用
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String bizId) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf(bizId, "process", "normal");
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, scenario, 
                extension -> extension.process(order));
    }
}
```

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

**使用示例**：

```java
// 假设有一个订单处理服务
@Service
public class OrderService {

    @Resource
    private GXExtensionExecutor extensionExecutor;

    // 订单处理扩展点接口
    public interface OrderProcessExtPoint extends GXExtensionPoint {
        OrderResult process(Order orderData);
    }

    // 订单数据类
    public static class Order { /* ... */ }
    public static class OrderResult { /* ... */ }

    public OrderResult handleOrder(Order orderData, String bizId, String useCase, String scenario) {
        // 1. 创建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);

        // 2. 执行扩展点方法
        //    - 第一个参数：扩展点接口的Class对象
        //    - 第二个参数：业务场景对象
        //    - 第三个参数：一个函数式接口，接收找到的扩展点实例，并调用其方法
        return extensionExecutor.execute(
            OrderProcessExtPoint.class, 
            bizScenario, 
            extPoint -> extPoint.process(orderData)
        );
    }

    public void notifyUser(Order orderData, String bizId, String useCase, String scenario) {
        GXBizScenario bizScenario = GXBizScenario.valueOf(bizId, useCase, scenario);
        // 如果扩展点方法无返回值，可以使用 executeVoid
        extensionExecutor.executeVoid(
            NotificationExtPoint.class, // 假设有 NotificationExtPoint 扩展点
            bizScenario,
            extPoint -> extPoint.sendNotification(orderData) // 假设 sendNotification 是 void 方法
        );
    }
}
```

在上面的示例中，`extensionExecutor.execute(...)` 会首先尝试查找 `bizId.useCase.scenario` 对应的 `OrderProcessExtPoint` 实现。如果找不到，它会依次尝试 `bizId.useCase.#defaultScenario#` 和 `bizId.#defaultUseCase#.#defaultScenario#`。

`GXExtensionExecutor` 简化了扩展点的调用过程，并内置了灵活的降级查找机制，是业务方与扩展点框架交互的主要入口。

### 3.14 `GXExtensionPoint`

### 3.15 `GXExtension`

`cn.maple.extension.GXExtension` 注解用于标记一个类是扩展点的具体实现。它使得开发者能够清晰地指明该实现类适用于哪些业务场景。

#### 核心作用

1.  **实现与场景的绑定**：通过 `bizId`、`useCase` 和 `scenario` 三个属性，`GXExtension` 注解可以将一个扩展点实现类精确地映射到一个或多个业务场景。这使得框架能够在运行时根据当前的业务上下文动态选择并执行合适的扩展点实现。
2.  **支持多场景实现**：`GXExtension` 注解是可重复的（`@Repeatable(GXExtensions.class)`），这意味着同一个实现类可以被多次标记，分别对应不同的业务场景。此外，也可以通过 `@GXExtensions` 注解一次性为单个实现类配置多个业务场景。

#### 注解属性详解

-   `bizId()`: 字符串类型，用于定义业务身份，默认为 `GXBizScenario.DEFAULT_BIZ_ID`。例如，`"order"` 代表订单业务，`"user"` 代表用户业务。
-   `useCase()`: 字符串类型，用于定义业务用例，默认为 `GXBizScenario.DEFAULT_USE_CASE`。例如，在订单业务下，可以有 `"create"`（创建订单）、`"cancel"`（取消订单）等用例。
-   `scenario()`: 字符串类型，用于定义业务场景，默认为 `GXBizScenario.DEFAULT_SCENARIO`。例如，在创建订单用例下，可以有 `"normal"`（普通下单）、`"promotion"`（促销下单）等场景。

#### 使用示例

以下示例展示了如何使用 `GXExtension` 注解来标记扩展点实现：

```java
// 定义一个支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    void processPayment(OrderDetails orderDetails);
}

// 实现1：支付宝支付，特定业务场景
@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")
public class AlipayPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现支付宝支付逻辑
        System.out.println("Processing Alipay payment for order: " + orderDetails.getOrderId());
    }
}

// 实现2：微信支付，支持多个业务场景（标准微信支付和微信小程序支付）
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatpay")
@GXExtension(bizId = "trading", useCase = "payment", scenario = "wechatMiniProgram")
public class WechatPaymentProvider implements PaymentExtPoint {
    @Override
    public void processPayment(OrderDetails orderDetails) {
        // 实现微信支付逻辑
        System.out.println("Processing WeChat payment for order: " + orderDetails.getOrderId());
    }
}

// 辅助类
class OrderDetails {
    private String orderId;

    public OrderDetails(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
```

在这个例子中：

-   `AlipayPaymentProvider` 类通过 `@GXExtension(bizId = "trading", useCase = "payment", scenario = "alipay")` 注解，明确了它处理的是 “trading” 业务下 “payment” 用例中的 “alipay” 场景。
-   `WechatPaymentProvider` 类则通过两个 `@GXExtension` 注解，表明它可以处理 “wechatpay” 和 “wechatMiniProgram” 两种场景下的支付请求。

#### 线程安全性

`GXExtension` 注解本身是元数据，不涉及线程安全问题。然而，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的，因为这些实现可能被并发调用。

### 3.16 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解是 `@GXExtension` 注解的容器注解，主要用于在一个扩展点实现类上同时声明多个业务场景。它提供了两种方式来定义多个业务场景的绑定。

#### 核心作用

1.  **聚合多个 `@GXExtension`**：当一个扩展点实现需要服务于多个不相关的业务场景时，可以使用 `GXExtensions` 的 `value` 属性来直接聚合多个 `@GXExtension` 注解。
2.  **笛卡尔积组合场景**：当多个业务维度（如 `bizId`, `useCase`, `scenario`）存在多种取值，并且希望它们的任意组合都对应同一个扩展点实现时，可以通过直接在 `GXExtensions` 注解上设置这些维度的数组值，框架会自动计算它们的笛卡尔积，为每一种组合注册该扩展点实现。

#### 注解属性详解

-   `value()`: `GXExtension[]` 类型，用于直接指定一个或多个 `@GXExtension` 注解。这是 `@Repeatable` 特性的标准用法，允许一个类被多个 `@GXExtension` 标记。
-   `bizId()`: `String[]` 类型，定义一个或多个业务身份。默认为 `GXBizScenario.DEFAULT_BIZ_ID`。
-   `useCase()`: `String[]` 类型，定义一个或多个业务用例。默认为 `GXBizScenario.DEFAULT_USE_CASE`。
-   `scenario()`: `String[]` 类型，定义一个或多个业务场景。默认为 `GXBizScenario.DEFAULT_SCENARIO`。

当同时使用 `bizId`, `useCase`, `scenario` 数组属性时，框架会将它们进行笛卡尔积组合，为每一个组合生成一个业务场景，并将当前的扩展点实现注册到这些场景下。如果同时指定了 `value` 属性和笛卡尔积属性，则两者都会生效。

#### 使用示例

**方式一：直接聚合多个 `@GXExtension`**

```java
@GXExtensions(value = {
    @GXExtension(bizId = "order", useCase = "create", scenario = "normal"),
    @GXExtension(bizId = "order", useCase = "cancel", scenario = "userRequest")
})
public class OrderOperationExt implements OrderExtPoint {
    // ... 实现逻辑
}
```

**方式二：使用笛卡尔积组合业务场景**

```java
@GXExtensions(
    bizId = {"product", "inventory"},
    useCase = {"update"},
    scenario = {"adminAction", "systemSync"}
)
public class ProductInventoryUpdateExt implements ProductUpdateExtPoint, InventoryUpdateExtPoint {
    // 此实现将对应以下4个业务场景：
    // 1. product.update.adminAction
    // 2. product.update.systemSync
    // 3. inventory.update.adminAction
    // 4. inventory.update.systemSync
    // ... 实现逻辑
}
```

#### 线程安全性

`GXExtensions` 注解本身是元数据，不涉及线程安全问题。与 `@GXExtension` 类似，被标记的扩展点实现类必须自行确保其内部逻辑是线程安全的。

### 3.17 `GXExtensionCoordinate`

### 3.18 GXExtensionRepository：扩展点仓库

`GXExtensionRepository` 用于存储和管理扩展点实现。它是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。

**核心功能：**

*   **存储扩展点实现：** 系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。
*   **查找扩展点实现：** 当需要执行扩展点时，框架会根据扩展坐标从扩展仓库中查找对应的实现类。

**主要方法：**

*   `getExtensionRepo()`: 获取扩展点缓存对象。内部使用 `ConcurrentHashMap` 存储扩展点实现，key 为 `GXExtensionCoordinate`，value 为扩展点实现实例。
*   `findExtension(GXExtensionCoordinate coordinate)`: 根据扩展坐标查找扩展点实现。如果找不到对应的实现，则返回空的 `Optional`。
*   `registerExtension(GXExtensionCoordinate coordinate, GXExtensionPoint extension)`: 注册扩展点实现。如果已经存在相同扩展坐标的实现，则会覆盖原有的实现。

**线程安全性：**

`GXExtensionRepository` 使用 `ConcurrentHashMap` 存储扩展点实现，因此是线程安全的。所有的读写操作都是原子的，可以安全地在多线程环境下使用。

**使用示例：**

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXBizScenario bizScenario = GXBizScenario.valueOf("payment", "alipay", "pc");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);

// 执行扩展点方法
Order order = new Order();
extension.pay(order);
```

### 3.19 GXExtensionRegister：扩展点注册器

`GXExtensionRegister` 类是扩展框架的核心组件之一，负责在Spring容器初始化完成后，自动扫描并注册所有标记了 `@GXExtension` 或 `@GXExtensions` 注解的扩展点实现到 `GXExtensionRepository` 中。它是连接扩展点定义与实际存储和查找机制的桥梁。

#### 核心职责

1.  **扩展点扫描与识别**：
    *   `GXExtensionRegister` 实现了 Spring 的 `ApplicationListener<ContextRefreshedEvent>` 接口，在Spring容器刷新完成后触发。
    *   它会遍历Spring容器中所有的Bean，查找带有 `@GXExtension` 或 `@GXExtensions` 注解的类。
2.  **处理AOP代理**：
    *   在注册过程中，`GXExtensionRegister` 能够智能处理AOP代理。如果一个Bean是AOP代理对象，它会尝试获取原始目标对象，以确保能正确读取到类上定义的注解信息。
3.  **注册到GXExtensionRepository**：
    *   对于每一个识别出的扩展点实现，`GXExtensionRegister` 会构造相应的 `GXExtensionCoordinate`。
    *   然后调用 `GXExtensionRepository` 的 `registerExtension` 方法，将扩展点实例及其坐标注册到仓库中。
    *   如果扩展点使用了 `@GXExtensions` 注解（用于支持业务场景的笛卡尔积组合），`GXExtensionRegister` 会为每种组合都生成一个 `GXExtensionCoordinate` 并进行注册。

#### 关键方法

*   `onApplicationEvent(ContextRefreshedEvent event)`: Spring容器刷新事件的监听方法，是扩展点注册流程的入口。
*   `doRegistration(Object bean, Class<?> targetClass)`: 处理单个 `@GXExtension` 注解的注册逻辑。
*   `doRegistrationExtensions(Object bean, Class<?> targetClass)`: 处理 `@GXExtensions` 注解的注册逻辑，支持业务场景的笛卡尔积。
*   `buildCoordinates(GXExtension extension, Class<?> extensionPointClass)`: 根据 `@GXExtension` 注解信息构建 `GXExtensionCoordinate`。
*   `buildCoordinatesForExtensions(GXExtensions extensions, Class<?> extensionPointClass)`: 根据 `@GXExtensions` 注解信息构建多个 `GXExtensionCoordinate`。

#### 线程安全性

`GXExtensionRegister` 本身的注册过程通常在Spring容器启动的单线程环境中执行，因此其自身的并发问题较少。其主要依赖的 `GXExtensionRepository` 是线程安全的（通过 `ConcurrentHashMap` 实现），保证了并发读写扩展点仓库的安全性。

#### 使用示例

`GXExtensionRegister` 通常由框架自动管理和调用，开发者一般不需要直接与之交互。它的存在确保了所有定义的扩展点能够在系统启动时被正确加载和注册。

以下是一个概念性的演示，说明其内部大致的工作流程（实际代码更复杂，涉及Spring上下文交互）：

```java
// 假设这是Spring容器中的一个Bean
@GXExtension(bizScenario = "DEFAULT_BIZ, ACTIVITY_SCENE", extPoint = IMessageHandler.class)
public class DefaultMessageHandler implements IMessageHandler {
    @Override
    public void handle(Message message) {
        System.out.println("Default message handler processing: " + message.getContent());
    }
}

// GXExtensionRegister 内部逻辑 (简化版)
public class GXExtensionRegister {
    private GXExtensionRepository extensionRepository;

    // 构造函数注入
    public GXExtensionRegister(GXExtensionRepository extensionRepository) {
        this.extensionRepository = extensionRepository;
    }

    public void register(Object bean) {
        Class<?> targetClass = bean.getClass(); // 实际会处理AOP代理
        GXExtension gxExtension = targetClass.getAnnotation(GXExtension.class);
        if (gxExtension != null) {
            Class<?> extensionPoint = gxExtension.extPoint();
            String[] bizScenarios = gxExtension.bizScenario().split(",");
            for (String bizId : bizScenarios) {
                GXBizScenario scenario = GXBizScenario.valueOf(bizId.trim());
                GXExtensionCoordinate coordinate = new GXExtensionCoordinate(extensionPoint, scenario);
                extensionRepository.registerExtension(coordinate, bean);
                System.out.println("Registered: " + coordinate + " -> " + bean);
            }
        }
        // 此处省略 @GXExtensions 的处理逻辑
    }
}

// 在Spring启动后，GXExtensionRegister会被调用
// contextRefreshedEvent -> {
//   for (String beanName : applicationContext.getBeanDefinitionNames()) {
//     Object bean = applicationContext.getBean(beanName);
//     extensionRegister.register(bean);
//   }
// }
```

### 3.20 GXExtensionTemplate：扩展点执行模板

`GXExtensionTemplate` 类提供了一个便捷的方式来执行扩展点方法。它封装了从 `GXExtensionRepository` 获取扩展点并执行其逻辑的通用流程，简化了业务代码中调用扩展点的复杂度。

#### 核心功能

1.  **扩展点查找与执行**：
    *   `GXExtensionTemplate` 依赖 `GXExtensionRepository` 来查找注册的扩展点。
    *   它提供了 `execute` 和 `executeAndReturn` 方法，用于执行无返回值和有返回值的扩展点方法。
2.  **参数化执行**：
    *   执行方法接受扩展点接口类 (`Class<T>`)、业务场景 (`GXBizScenario`) 以及一个函数式接口 (`Consumer<T>` 或 `Function<T, R>`)作为参数。
    *   函数式接口允许调用者定义如何与获取到的扩展点实例进行交互，例如调用其特定的方法。
3.  **AOP代理支持**：
    *   与 `GXExtensionRegister` 类似，`GXExtensionTemplate` 在获取扩展点时，如果发现是AOP代理，会尝试获取目标对象，以确保调用的是原始的扩展点实现逻辑（尽管通常情况下，通过代理调用也是期望的行为，以应用AOP切面）。

#### 关键方法

*   `execute(Class<T> extensionPointClass, GXBizScenario bizScenario, Consumer<T> consumer)`: 执行无返回值的扩展点方法。它会根据 `extensionPointClass` 和 `bizScenario` 查找到对应的扩展点实例，然后将该实例传递给 `consumer` 进行处理。
*   `executeAndReturn(Class<T> extensionPointClass, GXBizScenario bizScenario, Function<T, R> function)`: 执行有返回值的扩展点方法。它查找扩展点实例，并将其传递给 `function`，然后返回 `function` 的执行结果。

#### 线程安全性

`GXExtensionTemplate` 本身是无状态的，其操作依赖于线程安全的 `GXExtensionRepository`。因此，`GXExtensionTemplate` 的实例可以被安全地并发使用。

#### 使用示例

假设我们有一个支付扩展点 `IPaymentExt` 和一个具体的实现 `AliPaymentExt`：

```java
// 扩展点接口
public interface IPaymentExt {
    String pay(Order order);
}

// 扩展点实现
@GXExtension(bizScenario = "ALI_PAY", extPoint = IPaymentExt.class)
public class AliPaymentExt implements IPaymentExt {
    @Override
    public String pay(Order order) {
        return "AliPay processed order: " + order.getId();
    }
}

// 业务代码中使用 GXExtensionTemplate
public class PaymentService {
    private final GXExtensionTemplate extensionTemplate;

    // 构造函数注入
    public PaymentService(GXExtensionTemplate extensionTemplate) {
        this.extensionTemplate = extensionTemplate;
    }

    public String processPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel); // e.g., "ALI_PAY"

        // 执行并获取返回值
        return extensionTemplate.executeAndReturn(IPaymentExt.class, scenario, ext -> ext.pay(order));
    }

    public void notifyPayment(Order order, String paymentChannel) {
        GXBizScenario scenario = GXBizScenario.valueOf(paymentChannel);

        // 执行无返回值的方法
        extensionTemplate.execute(IPaymentExt.class, scenario, ext -> {
            // 假设IPaymentExt有notify方法
            // ext.notify(order.getId(), "SUCCESS"); 
            System.out.println("Notification sent for order: " + order.getId() + " via " + paymentChannel);
        });
    }
}

// 配置 (Spring)
@Configuration
public class AppConfig {
    @Bean
    public GXExtensionRepository gxExtensionRepository() {
        return new GXExtensionRepository();
    }

    @Bean
    public GXExtensionRegister gxExtensionRegister(GXExtensionRepository repository) {
        return new GXExtensionRegister(repository);
    }

    @Bean
    public GXExtensionTemplate gxExtensionTemplate(GXExtensionRepository repository) {
        return new GXExtensionTemplate(repository);
    }

    @Bean
    public AliPaymentExt aliPaymentExt() {
        return new AliPaymentExt();
    }

    @Bean
    public PaymentService paymentService(GXExtensionTemplate template) {
        return new PaymentService(template);
    }
}

// 客户端调用
public class Main {
    public static void main(String[] args) {
        // 假设Spring容器已初始化
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        PaymentService paymentService = context.getBean(PaymentService.class);

        Order order = new Order("ORDER_123");
        String result = paymentService.processPayment(order, "ALI_PAY");
        System.out.println(result); // 输出: AliPay processed order: ORDER_123

        paymentService.notifyPayment(order, "ALI_PAY"); // 输出类似: Notification sent for order: ORDER_123 via ALI_PAY
        
        context.close();
    }
}
```

通过 `GXExtensionTemplate`，业务代码无需直接与 `GXExtensionRepository` 交互，使得调用扩展点的代码更加简洁和统一。

#### 核心作用

1.  **唯一标识扩展点实现**：通过组合扩展点接口的完全限定名和业务场景的唯一标识（`bizId.useCase.scenario`），`GXExtensionCoordinate` 为每一个扩展点实现提供了一个全局唯一的坐标。
2.  **支持扩展点查找**：框架使用 `GXExtensionCoordinate` 对象作为 Key，在扩展点仓库（`GXExtensionRepository`）中查找和获取相应的扩展点实现实例。

#### 主要属性

-   `extensionPointName`: `String` 类型，表示扩展点接口的完全限定类名。
-   `bizScenarioUniqueIdentity`: `String` 类型，表示业务场景的唯一标识，格式为 `bizId.useCase.scenario`。
-   `extensionPointClass`: `Class<?>` 类型，表示扩展点接口的 `Class` 对象。在通过类和业务场景对象构造时填充。
-   `bizScenario`: `GXBizScenario` 类型，表示业务场景对象。在通过类和业务场景对象构造时填充。

#### 构造方式

`GXExtensionCoordinate` 提供了两种构造方式：

1.  **通过 `Class` 对象和 `GXBizScenario` 对象构造**：
    ```java
    public GXExtensionCoordinate(Class<?> extPtClass, GXBizScenario bizScenario)
    ```
    这是推荐的构造方式，它会同时填充 `extensionPointClass` 和 `bizScenario` 属性。

2.  **通过字符串名称构造**：
    ```java
    public GXExtensionCoordinate(String extensionPointName, String bizScenarioUniqueIdentity)
    ```
    这种方式主要用于某些场景下只有扩展点名称和业务场景标识字符串的情况。此时，`extensionPointClass` 和 `bizScenario` 属性将为 `null`。

还提供了一个静态工厂方法 `valueOf`，封装了第一种构造方式：

```java
public static GXExtensionCoordinate valueOf(Class<?> extPtClass, GXBizScenario bizScenario)
```

#### 使用示例

```java
// 假设存在 PaymentExtPoint 扩展点接口和相应的业务场景
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");

// 创建扩展坐标
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用坐标从 GXExtensionExecutor (或 GXExtensionRepository) 中查找扩展点实现
// PaymentExtPoint paymentExt = extensionExecutor.findExtension(coordinate);
```

#### `equals()` 和 `hashCode()`

`GXExtensionCoordinate` 重写了 `equals()` 和 `hashCode()` 方法，它们基于 `extensionPointName` 和 `bizScenarioUniqueIdentity` 这两个字符串属性。这意味着，只要这两个核心标识相同，即使是通过不同构造方式创建的 `GXExtensionCoordinate` 对象（例如一个包含 `Class` 对象，一个不包含），它们也会被认为是相等的。这对于在 `Map` 等集合中作为 Key 使用非常重要。

#### 线程安全性

`GXExtensionCoordinate` 类是不可变的（immutable），其所有字段都是 `final` 的，并且在构造后不能被修改。因此，它是线程安全的，可以在多线程环境中安全共享和使用，无需额外的同步措施。


`cn.maple.extension.GXExtensionPoint` 是一个核心的标记接口，所有自定义的业务扩展点接口都需要继承它。

**核心作用**：

-   **标记接口**：它本身不包含任何方法，仅用作一个标记，表明实现了该接口的接口是一个“扩展点”。
-   **类型约束**：框架通过此接口来识别和管理扩展点。只有继承了 `GXExtensionPoint` 的接口才能被框架识别为有效的扩展点接口。
-   **设计契约**：它定义了一种设计契约，开发者通过继承此接口来表明其定义的接口是用于业务扩展的。

**如何定义扩展点**：

开发者需要定义自己的业务扩展点接口，并且让该接口继承 `GXExtensionPoint`。例如：

```java
// 定义一个支付相关的扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

**实现扩展点**：

定义了扩展点接口后，需要提供具体的实现类。这些实现类需要使用 `@GXExtension` 或 `@GXExtensions` 注解来标记其适用的业务场景。

```java
// 支付宝支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付的具体逻辑
        System.out.println("使用支付宝支付...");
        return new PayResult("SUCCESS", "支付宝支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款的具体逻辑
        System.out.println("使用支付宝退款...");
        return new RefundResult("SUCCESS", "支付宝退款成功");
    }
}

// 微信支付扩展点实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付的具体逻辑
        System.out.println("使用微信支付...");
        return new PayResult("SUCCESS", "微信支付成功");
    }

    @Override
    public RefundResult refund(Order order) {
        // 微信退款的具体逻辑
        System.out.println("使用微信退款...");
        return new RefundResult("SUCCESS", "微信退款成功");
    }
}
```

**线程安全性**：

`GXExtensionPoint` 接口本身不涉及线程安全问题。但是，其实现类（即具体的扩展点逻辑）需要开发者自行确保线程安全，尤其是在被多个线程并发调用时。

通过继承 `GXExtensionPoint`，开发者可以清晰地定义业务的扩展边界，并利用框架的能力实现业务逻辑的灵活插拔和动态切换。

## 8. 常见问题 (FAQ)

1.  **Q: 如何为一个扩展点实现类配置多个不相关的业务场景？**
    A: 使用 `@GXExtensions` 注解，并在其 `value()` 属性中列出多个 `@GXExtension` 定义，每个定义对应一个独立的业务场景。或者，如果场景之间有规律可循，可以考虑使用 `bizId()`, `useCase()`, `scenario()` 属性的笛卡尔积功能。

2.  **Q: 如果一个Bean同时被 `@GXExtension` 和 `@GXExtensions` 注解，会发生什么？**
    A: `GXExtensionBootstrap` 会分别处理这两种注解。因此，由 `@GXExtension` 定义的场景和由 `@GXExtensions` 定义的场景都会被注册。如果两者定义的场景有重叠，后注册的会覆盖先注册的（具体顺序取决于Spring加载Bean的顺序和 `GXExtensionBootstrap` 内部处理顺序），并可能在日志中看到重复注册的警告。

3.  **Q: 扩展点实现类必须是Spring Bean吗？**
    A: 是的，`GXExtensionBootstrap` 通过扫描Spring应用上下文来发现和注册扩展点实现。因此，扩展点实现类需要被Spring容器管理（例如使用 `@Component`, `@Service` 等注解）。

4.  **Q: 如果找不到任何匹配的扩展点实现（包括默认实现），会发生什么？**
    A: `GXExtensionExecutor` 在所有降级尝试都失败后，会抛出 `GXExtensionException`。

5.  **Q: 如何调试扩展点的注册和查找过程？**
    A: 
    *   检查 `GXExtensionBootstrap` 和 `GXExtensionRegister` 的日志（通常是DEBUG级别），看扩展点是否按预期被扫描和注册。
    *   检查 `GXExtensionExecutor` 的日志，看它尝试查找的业务场景坐标是什么，以及查找的结果。
    *   在 `GXExtensionRepository` 的 `getExtensionRepo()` 方法返回的Map上打断点，检查注册的扩展点坐标和实例是否正确。
    *   确保扩展点接口的命名符合规范（包含 "ExtPoint"）。

6.  **Q: 扩展点框架是否支持泛型扩展点？**
    A: 框架本身在注册和查找时主要依赖 `Class` 对象和注解元数据，对泛型没有特殊处理。如果扩展点接口定义了泛型，那么在实现和执行时，Java的泛型机制会正常工作。但业务场景的区分仍然依赖于注解中的字符串标识，而不是泛型参数类型。

7.  **Q: `GXBizScenario` 中的 `bizId`, `useCase`, `scenario` 可以包含特殊字符吗？**
    A: 这些标识最终会拼接成字符串作为 `GXExtensionCoordinate` 的一部分。虽然技术上可以包含大部分字符，但建议使用字母、数字、下划线 (`_`) 或短横线 (`-`) 组合，以保持清晰和避免潜在的编码或分隔问题。默认值中包含 `#` 符号，这是框架内部约定的特殊标记。

8.  **Q: 如果扩展点方法需要抛出受检异常怎么办？**
    A: `GXExtensionExecutor` 的 `execute` 和 `executeVoid` 方法接受的Lambda表达式本身可以抛出异常。如果扩展点方法声明了受检异常，Lambda表达式也需要处理或声明它。`GXExtensionExecutor` 不会捕获和包装业务方法抛出的受检异常（除非是 `RuntimeException` 或其子类，如 `GXExtensionException`）。业务代码在调用 `execute` 时需要处理这些可能的受检异常。

```

本文档旨在帮助开发者理解和使用 `leaf-base-extension` 模块。如有疑问或建议，请联系模块维护者。