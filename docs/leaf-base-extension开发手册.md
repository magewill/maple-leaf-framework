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

`cn.maple.extension.GXExtension` 是一个类级别注解，用于标记一个Java类是某个 `GXExtensionPoint` 接口的具体实现，并指定该实现所适用的业务场景。

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(GXExtensions.class) // 允许在同一个类上重复使用
public @interface GXExtension {
    String bizId() default GXBizScenario.DEFAULT_BIZ_ID;       // 业务ID，默认为 "#defaultBizId#"
    String useCase() default GXBizScenario.DEFAULT_USE_CASE;     // 用例ID，默认为 "#defaultUseCase#"
    String scenario() default GXBizScenario.DEFAULT_SCENARIO;   // 场景ID，默认为 "#defaultScenario#"
}
```

-   `bizId`: 业务领域标识，例如 "trade"（交易）、"user"（用户）。
-   `useCase`: 在特定业务领域下的用例，例如 "payment"（支付）、"register"（注册）。
-   `scenario`: 在特定用例下的具体场景，例如 "alipay"（支付宝）、"trial_user"（试用用户）。
-   默认值：如果某个维度未指定，则会使用 `GXBizScenario` 中定义的默认值（如 `GXBizScenario.DEFAULT_BIZ_ID`）。

一个类可以被 `@GXExtension` 注解多次（通过 `@Repeatable(GXExtensions.class)` 实现），或者配合 `@GXExtensions` 注解来支持一个实现类服务于多个业务场景。

### 3.3 `GXExtensions`

`cn.maple.extension.GXExtensions` 注解提供了更灵活的方式来为一个扩展点实现类关联多个业务场景。

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GXExtensions {
    GXExtension[] value() default {};         // 直接指定多个GXExtension注解
    String[] bizId() default {};            // 业务ID列表
    String[] useCase() default {};          // 用例ID列表
    String[] scenario() default {};         // 场景ID列表
}
```

使用方式：

1.  **通过 `value()` 属性**：直接嵌入多个 `@GXExtension` 注解。

    ```java
    @GXExtensions(value = {
        @GXExtension(bizId = "b1", useCase = "uc1", scenario = "s1"),
        @GXExtension(bizId = "b2", useCase = "uc2", scenario = "s2")
    })
    public class MyMultiPurposeExtImpl implements SomeExtPoint { ... }
    ```
2.  **通过 `bizId()`, `useCase()`, `scenario()` 属性的笛卡尔积**：框架会自动将提供的多个 `bizId`、`useCase` 和 `scenario` 值进行组合，生成所有可能的业务场景。

    ```java
    @GXExtensions(
        bizId = {"b1", "b2"},
        useCase = {"uc1"},
        scenario = {"s1", "s2"}
    )
    public class MyCartesianProductExtImpl implements SomeExtPoint { ... }
    // 上述配置会注册以下4个场景的实现：
    // b1.uc1.s1
    // b1.uc1.s2
    // b2.uc1.s1
    // b2.uc1.s2
    ```
    如果某个维度的数组为空或未提供，则该维度会使用其默认值进行组合。

### 3.4 `GXBizScenario`

`cn.maple.extension.GXBizScenario` 类用于封装和表示一个具体的业务场景。它由 `bizId`、`useCase` 和 `scenario` 三个维度构成。

**核心常量**：

-   `GXBizScenario.DEFAULT_BIZ_ID` ("#defaultBizId#")
-   `GXBizScenario.DEFAULT_USE_CASE` ("#defaultUseCase#")
-   `GXBizScenario.DEFAULT_SCENARIO` ("#defaultScenario#")

**创建实例**：

```java
// 完整场景
GXBizScenario s1 = GXBizScenario.valueOf("trade", "payment", "alipay");

// 省略 scenario，使用默认场景
GXBizScenario s2 = GXBizScenario.valueOf("trade", "payment"); 
// 等价于 GXBizScenario.valueOf("trade", "payment", GXBizScenario.DEFAULT_SCENARIO);

// 省略 useCase 和 scenario，使用默认用例和默认场景
GXBizScenario s3 = GXBizScenario.valueOf("trade");
// 等价于 GXBizScenario.valueOf("trade", GXBizScenario.DEFAULT_USE_CASE, GXBizScenario.DEFAULT_SCENARIO);

// 完全默认的业务场景
GXBizScenario s4 = GXBizScenario.newDefault();
// 等价于 GXBizScenario.valueOf(GXBizScenario.DEFAULT_BIZ_ID, GXBizScenario.DEFAULT_USE_CASE, GXBizScenario.DEFAULT_SCENARIO);
```

**获取唯一标识**：

`getUniqueIdentity()`: 返回格式如 `bizId.useCase.scenario` 的字符串，例如 `"trade.payment.alipay"`。
`getIdentityWithDefaultScenario()`: 返回 `bizId.useCase.#defaultScenario#`。
`getIdentityWithDefaultUseCase()`: 返回 `bizId.#defaultUseCase#.#defaultScenario#` (注意这里也会使用默认场景)。

这些标识用于在 `GXExtensionRepository` 中作为Key的一部分进行查找。

### 3.5 `GXExtensionCoordinate`

`cn.maple.extension.GXExtensionCoordinate` 代表一个扩展点的唯一坐标。它由扩展点接口的类名 (`extensionPointName`) 和业务场景的唯一标识 (`bizScenarioUniqueIdentity`) 组成。这个对象被用作 `GXExtensionRepository` 内部 `ConcurrentHashMap` 的Key，因此正确实现了 `equals()` 和 `hashCode()` 方法。

```java
// 创建方式1: 通过Class和GXBizScenario (推荐)
GXExtensionCoordinate coord1 = new GXExtensionCoordinate(PaymentExtPoint.class, bizScenario);
GXExtensionCoordinate coord2 = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario); // 工厂方法

// 创建方式2: 通过字符串 (主要供框架内部使用)
GXExtensionCoordinate coord3 = new GXExtensionCoordinate(PaymentExtPoint.class.getName(), bizScenario.getUniqueIdentity());
```

它包含了扩展点接口的 `Class` 对象和 `GXBizScenario` 对象，方便后续操作。

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

This concludes the comprehensive enhancement of the `leaf-base-extension` development manual, covering all core functionalities, detailed component explanations, workflow, and best practices, ensuring it is well-documented for developers.