# Leaf-Base-Extension 模块开发手册

## 1. 模块概述

Leaf-Base-Extension 模块是 Maple-Leaf-Framework 框架中的扩展点框架，提供了一种灵活的机制，允许在不同的业务场景下实现不同的业务逻辑。该模块基于 SPI (Service Provider Interface) 设计理念，通过注解和自动注册机制，简化了扩展点的定义、实现和使用过程。

### 1.1 主要功能

- **扩展点定义**：通过接口定义扩展点，支持多种业务场景下的不同实现
- **业务场景标识**：使用三维坐标（业务ID、用例、场景）唯一标识业务场景
- **自动注册**：自动扫描并注册所有扩展点实现
- **动态定位**：根据业务场景动态定位并执行对应的扩展点实现
- **降级策略**：当找不到精确匹配的实现时，支持降级到默认实现

### 1.2 核心组件

- **GXExtensionPoint**：扩展点接口，所有扩展点接口的父接口
- **GXExtension**：扩展点注解，用于标记扩展点实现类
- **GXExtensions**：扩展点集合注解，支持一个实现类适用于多个业务场景
- **GXBizScenario**：业务场景，用于唯一标识一个业务场景
- **GXExtensionExecutor**：扩展执行器，负责定位并执行扩展点
- **GXExtensionRepository**：扩展仓库，存储所有扩展点实现

## 2. 快速入门

### 2.1 定义扩展点接口

扩展点接口需要继承 `GXExtensionPoint` 接口，并定义业务方法：

```java
// 定义支付扩展点接口
public interface PaymentExtPoint extends GXExtensionPoint {
    // 支付方法
    PayResult pay(Order order);
    
    // 退款方法
    RefundResult refund(Order order);
}
```

### 2.2 实现扩展点接口

为不同的业务场景实现扩展点接口，并使用 `@GXExtension` 注解标记：

```java
// 支付宝支付实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay")
public class AlipayPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 支付宝支付实现
        return new PayResult();
    }
    
    @Override
    public RefundResult refund(Order order) {
        // 支付宝退款实现
        return new RefundResult();
    }
}

// 微信支付实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
public class WechatPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 微信支付实现
        return new PayResult();
    }
    
    @Override
    public RefundResult refund(Order order) {
        // 微信退款实现
        return new RefundResult();
    }
}
```

### 2.3 使用扩展点

在业务代码中注入 `GXExtensionExecutor`，并使用它来执行扩展点方法：

```java
@Component
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public PayResult processPayment(Order order, String payType) {
        // 创建业务场景
        GXBizScenario scenario = GXBizScenario.valueOf("mall", "payment", payType);
        
        // 执行扩展点方法
        return extensionExecutor.execute(PaymentExtPoint.class, scenario, 
                extension -> extension.pay(order));
    }
}
```

## 3. 核心组件详解

### 3.1 GXExtensionPoint

`GXExtensionPoint` 是所有扩展点接口的父接口，它是一个标记接口，没有定义任何方法。所有的扩展点接口都必须继承该接口，以便被扩展点框架识别和管理。

```java
public interface GXExtensionPoint {
}
```

### 3.2 GXExtension

`GXExtension` 是一个注解，用于标记一个类是扩展点的实现类。通过 `bizId`、`useCase` 和 `scenario` 三个属性，可以精确定位扩展点实现类适用的业务场景。

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Repeatable(GXExtensions.class)
public @interface GXExtension {
    /**
     * 业务ID
     */
    String bizId() default GXBizScenario.DEFAULT_BIZ_ID;

    /**
     * 用例
     */
    String useCase() default GXBizScenario.DEFAULT_USE_CASE;

    /**
     * 场景
     */
    String scenario() default GXBizScenario.DEFAULT_SCENARIO;
}
```

该注解支持可重复注解，可以在同一个类上多次使用该注解，以支持一个实现类适用于多个业务场景。

### 3.3 GXExtensions

`GXExtensions` 是 `GXExtension` 注解的补充，用于支持多个业务场景坐标。有两种使用方式：

1. 通过 `value` 属性直接指定多个 `GXExtension` 注解
2. 通过 `bizId`、`useCase` 和 `scenario` 属性指定多个值，框架会自动生成这些值的笛卡尔积组合

```java
// 方式一：直接指定多个GXExtension
@GXExtensions(value = {
    @GXExtension(bizId = "mall", useCase = "payment", scenario = "alipay"),
    @GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
})
public class PaymentExtImpl implements PaymentExtPoint {
    // 实现方法
}

// 方式二：使用笛卡尔积组合
@GXExtensions(
    bizId = {"mall", "b2b"},
    useCase = {"payment"},
    scenario = {"alipay", "wechat"}
)
public class MultiScenarioPaymentExtImpl implements PaymentExtPoint {
    // 这将注册4个扩展点实现：
    // mall.payment.alipay
    // mall.payment.wechat
    // b2b.payment.alipay
    // b2b.payment.wechat
}
```

### 3.4 GXBizScenario

`GXBizScenario`（业务场景）由三部分组成：

1. `bizId` - 业务标识，用于区分不同的业务领域，如订单、用户、商品等
2. `useCase` - 用例标识，用于区分同一业务领域下的不同用例，如下单、支付、退款等
3. `scenario` - 场景标识，用于区分同一用例下的不同场景，如普通下单、促销下单、秒杀下单等

```java
// 创建一个完整的业务场景
GXBizScenario scenario1 = GXBizScenario.valueOf("order", "place", "normal");

// 创建一个只有业务ID和用例的业务场景，场景使用默认值
GXBizScenario scenario2 = GXBizScenario.valueOf("order", "place");

// 创建一个只有业务ID的业务场景，用例和场景使用默认值
GXBizScenario scenario3 = GXBizScenario.valueOf("order");

// 创建一个默认的业务场景，业务ID、用例和场景都使用默认值
GXBizScenario scenario4 = GXBizScenario.newDefault();

// 获取业务场景的唯一标识
String identity = scenario1.getUniqueIdentity(); // 返回 "order.place.normal"
```

### 3.5 GXExtensionCoordinate

`GXExtensionCoordinate`（扩展坐标）用于唯一定位一个扩展点实现，由扩展点类型和业务场景两部分组成。

```java
// 创建一个扩展坐标
Class<PaymentExtPoint> extPointClass = PaymentExtPoint.class;
GXBizScenario bizScenario = GXBizScenario.valueOf("order", "payment", "alipay");
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(extPointClass, bizScenario);

// 使用扩展坐标查找扩展点实现
PaymentExtPoint extension = extensionExecutor.findExtension(coordinate);
```

### 3.6 GXExtensionExecutor

`GXExtensionExecutor` 是扩展点执行框架的核心实现类，负责根据业务场景定位并执行扩展点。它提供了以下方法：

- `execute`：执行扩展点方法并返回结果
- `executeVoid`：执行扩展点方法，无返回值

```java
// 执行有返回值的扩展点方法
PayResult result = extensionExecutor.execute(PaymentExtPoint.class, scenario, 
        extension -> extension.pay(order));

// 执行无返回值的扩展点方法
extensionExecutor.executeVoid(NotificationExtPoint.class, scenario, 
        extension -> extension.notify(order));
```

扩展点查找策略：

1. 首先尝试使用完整的业务场景标识（bizId.useCase.scenario）查找
2. 如果找不到，尝试使用默认场景（bizId.useCase.#defaultScenario#）查找
3. 如果仍找不到，尝试使用默认用例和默认场景（bizId.#defaultUseCase#.#defaultScenario#）查找
4. 如果所有尝试都失败，抛出异常

### 3.7 GXExtensionRepository

`GXExtensionRepository` 是扩展点框架的核心组件之一，负责存储扩展点实现的实例，并提供查找功能。当系统启动时，框架会自动扫描所有带有 `@GXExtension` 注解的类，并将其实例化后存储到扩展仓库中。

```java
// 注入扩展仓库
@Autowired
private GXExtensionRepository extensionRepository;

// 获取扩展点实现
GXExtensionCoordinate coordinate = GXExtensionCoordinate.valueOf(PaymentExtPoint.class, bizScenario);
Map<GXExtensionCoordinate, GXExtensionPoint> repo = extensionRepository.getExtensionRepo();
PaymentExtPoint extension = (PaymentExtPoint) repo.get(coordinate);
```

## 4. 高级特性

### 4.1 默认实现

当找不到精确匹配的扩展点实现时，框架会按照以下顺序尝试查找默认实现：

1. 使用默认场景：`bizId.useCase.#defaultScenario#`
2. 使用默认用例和默认场景：`bizId.#defaultUseCase#.#defaultScenario#`

这允许开发者提供默认实现，以处理未明确定义的业务场景：

```java
// 默认支付实现
@GXExtension(bizId = "mall", useCase = "payment", scenario = GXBizScenario.DEFAULT_SCENARIO)
public class DefaultPaymentExt implements PaymentExtPoint {
    @Override
    public PayResult pay(Order order) {
        // 默认支付实现
        return new PayResult();
    }
    
    @Override
    public RefundResult refund(Order order) {
        // 默认退款实现
        return new RefundResult();
    }
}
```

### 4.2 多业务场景支持

一个扩展点实现可以支持多个业务场景，有两种方式：

1. 使用可重复注解 `@GXExtension`

```java
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechat")
@GXExtension(bizId = "mall", useCase = "payment", scenario = "wechatMini")
public class WechatPaymentExt implements PaymentExtPoint {
    // 实现方法
}
```

2. 使用 `@GXExtensions` 注解

```java
@GXExtensions(
    bizId = {"mall", "b2b"},
    useCase = {"payment"},
    scenario = {"alipay", "wechat"}
)
public class MultiScenarioPaymentExtImpl implements PaymentExtPoint {
    // 实现方法
}
```

### 4.3 异常处理

扩展点框架提供了 `GXExtensionException` 类，用于处理扩展点框架中的异常情况：

```java
try {
    PaymentExtPoint extension = extensionExecutor.findExtension(coordinate);
    if (extension == null) {
        throw new GXExtensionException("找不到对应的支付扩展点实现");
    }
    extension.pay(order);
} catch (GXExtensionException e) {
    // 处理异常
    log.error("扩展点异常: {}", e.getMsg(), e);
}
```

## 5. 实际应用示例

以下是基于框架测试代码的实际应用示例，展示了扩展点框架在客户管理系统中的应用。

### 5.1 定义常量

首先定义业务场景相关的常量：

```java
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

### 5.2 定义扩展点接口

定义客户验证扩展点接口：

```java
public interface AddCustomerValidatorExtPoint extends GXExtensionPoint {
    void validate(AddCustomerCmd addCustomerCmd);
}
```

定义客户规则扩展点接口：

```java
public interface CustomerRuleExtPoint extends GXExtensionPoint {
    boolean addCustomerCheck(CustomerEntity customerEntity);

    default void customerUpgradePolicy(CustomerEntity customerEntity) {
        //Nothing special
    }
}
```

### 5.3 实现扩展点接口

为不同业务场景实现客户验证扩展点：

```java
@GXExtension(bizId = GXConstants.BIZ_1)
@Component
public class AddCustomerBizOneValidator implements AddCustomerValidatorExtPoint {
    public void validate(AddCustomerCmd addCustomerCmd) {
        //For BIZ ONE CustomerTYpe could not be VIP
        if (CustomerType.VIP == addCustomerCmd.getCustomerDTO().getCustomerType())
            throw new GXBusinessException("Customer Type could not be VIP for Biz One");
    }
}
```

为不同业务场景实现客户规则扩展点：

```java
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

### 5.4 在业务逻辑中使用扩展点

在命令执行器中使用扩展点：

```java
@Component
public class AddCustomerCmdExe {
    private final Logger logger = LoggerFactory.getLogger(AddCustomerCmd.class);

    @Resource
    private GXExtensionExecutor extensionExecutor;

    @Resource
    private DomainEventPublisher domainEventPublisher;

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
        customerEntity.addNewCustomer();

        //domainEventPublisher.publish(new CustomerCreatedEvent());
        logger.info("End processing command:" + cmd);
        return GXResultUtils.ok("Success");
    }
}
```

### 5.5 测试扩展点

编写测试用例验证扩展点功能：

```java
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ExtTestApplication.class)
public class ExtensionTest {
    @Resource
    private CustomerService customerService;

    @Test
    public void testBiz1UseCase1Scenario1AddCustomerSuccess() {
        //1. Prepare
        AddCustomerCmd addCustomerCmd = new AddCustomerCmd();
        CustomerDto customerDTO = new CustomerDto();
        customerDTO.setCompanyName("alibaba");
        customerDTO.setSource(GXConstants.SOURCE_RFQ);
        customerDTO.setCustomerType(CustomerType.IMPORTANT);
        addCustomerCmd.setCustomerDTO(customerDTO);
        GXBizScenario scenario = GXBizScenario.valueOf(GXConstants.BIZ_1, 
                GXConstants.USE_CASE_1, GXConstants.SCENARIO_1);
        addCustomerCmd.setBizScenario(scenario);

        //2. Execute
        GXResultUtils<String> response = customerService.addCustomer(addCustomerCmd);

        //3. Expect Success
        Assert.assertTrue(response != null);
    }
}
```

## 6. 最佳实践

### 6.1 扩展点命名规范

- 扩展点接口名称建议以 `ExtPoint` 结尾，例如 `PaymentExtPoint`
- 扩展点实现类名称建议以 `Ext` 结尾，例如 `AlipayPaymentExt`

### 5.2 业务场景定义规范

- `bizId`：业务领域标识，如 `order`、`user`、`product` 等
- `useCase`：用例标识，如 `create`、`update`、`delete` 等
- `scenario`：场景标识，如 `normal`、`promotion`、`vip` 等

### 5.3 线程安全性

扩展点框架的核心组件都设计为线程安全的：

- `GXBizScenario` 和 `GXExtensionCoordinate` 是不可变的，所有字段都是 final 的
- `GXExtensionRepository` 使用 ConcurrentHashMap 存储扩展点实现
- `GXExtensionExecutor` 和 `GXExtensionRegister` 不存储状态，主要依赖 `GXExtensionRepository` 的线程安全性

但是，扩展点实现类需要自行保证线程安全性。

### 5.4 性能优化

- 避免在扩展点方法中执行耗时操作，如果必须执行，考虑使用异步方式
- 合理设计业务场景，避免过多的扩展点实现类
- 使用默认实现来减少代码重复

## 6. 常见问题

### 6.1 找不到扩展点实现

如果找不到扩展点实现，可能有以下原因：

1. 扩展点实现类没有正确标记 `@GXExtension` 注解
2. 扩展点实现类没有被 Spring 容器管理（没有添加 `@Component` 等注解）
3. 业务场景坐标不匹配

解决方法：

1. 检查扩展点实现类是否正确标记了 `@GXExtension` 注解
2. 确保扩展点实现类被 Spring 容器管理
3. 检查业务场景坐标是否匹配

### 6.2 扩展点实现被多次注册

如果一个扩展点实现被多次注册，可能会在日志中看到警告信息。这通常不会影响功能，但可能会导致性能问题。

解决方法：

1. 检查扩展点实现类是否被多次标记了 `@GXExtension` 注解
2. 检查是否有多个扩展点实现类使用了相同的业务场景坐标

### 6.3 扩展点方法执行失败

如果扩展点方法执行失败，可能有以下原因：

1. 扩展点实现类中的方法抛出了异常
2. 扩展点实现类中的方法依赖的资源不可用

解决方法：

1. 在扩展点实现类中添加异常处理
2. 确保扩展点实现类中的方法依赖的资源可用

## 7. 示例代码

### 7.1 完整示例

```java
// 定义扩展点接口
public interface OrderProcessExtPoint extends GXExtensionPoint {
    OrderResult process(Order order);
}

// 普通订单处理实现
@Component
@GXExtension(bizId = "mall", useCase = "order", scenario = "normal")
public class NormalOrderProcessExt implements OrderProcessExtPoint {
    @Override
    public OrderResult process(Order order) {
        // 普通订单处理逻辑
        return new OrderResult("normal", true);
    }
}

// 促销订单处理实现
@Component
@GXExtension(bizId = "mall", useCase = "order", scenario = "promotion")
public class PromotionOrderProcessExt implements OrderProcessExtPoint {
    @Override
    public OrderResult process(Order order) {
        // 促销订单处理逻辑
        return new OrderResult("promotion", true);
    }
}

// 默认订单处理实现
@Component
@GXExtension(bizId = "mall", useCase = "order", scenario = GXBizScenario.DEFAULT_SCENARIO)
public class DefaultOrderProcessExt implements OrderProcessExtPoint {
    @Override
    public OrderResult process(Order order) {
        // 默认订单处理逻辑
        return new OrderResult("default", true);
    }
}

// 在业务代码中使用
@Service
public class OrderService {
    @Resource
    private GXExtensionExecutor extensionExecutor;
    
    public OrderResult processOrder(Order order, String scenario) {
        // 创建业务场景
        GXBizScenario bizScenario = GXBizScenario.valueOf("mall", "order", scenario);
        
        // 执行扩展点方法
        return extensionExecutor.execute(OrderProcessExtPoint.class, bizScenario, 
                extension -> extension.process(order));
    }
}
```

## 8. 总结

Leaf-Base-Extension 模块提供了一种灵活的扩展点机制，允许在不同的业务场景下实现不同的业务逻辑。通过注解和自动注册机制，简化了扩展点的定义、实现和使用过程。该模块的核心特性包括：

- 基于注解的扩展点定义和实现
- 三维业务场景坐标（业务ID、用例、场景）
- 自动扫描和注册扩展点实现
- 动态定位和执行扩展点
- 默认实现和降级策略

通过合理使用扩展点框架，可以实现业务逻辑的灵活扩展和复用，提高代码的可维护性和可扩展性。