# leaf-base-retry 模块开发手册

## 1. 模块概述

`leaf-base-retry` 模块是基于 Spring Retry 实现的统一重试框架，旨在为应用程序提供声明式和编程式的重试能力，以处理那些可能由于瞬时问题（如网络抖动、服务临时不可用等）导致的失败操作。通过引入该模块，开发者可以方便地为可能失败的操作配置重试逻辑，提高系统的健壮性和容错性。

核心功能包括：

- **声明式重试**：通过 `@Retryable` 注解轻松为方法添加重试功能。
- **编程式重试**：提供 `GXRetryUtil` 工具类和可注入的 `RetryTemplate` Bean，支持更灵活的重试逻辑控制。
- **可配置的重试策略**：支持配置最大重试次数、可重试的异常类型等。
- **可配置的退避策略**：支持配置初始退避间隔、退避乘数、最大退避间隔，默认采用指数退避。
- **恢复操作**：支持在所有重试尝试失败后执行指定的恢复逻辑（Fallback）。
- **事件监听**：提供 `GXRetryListener`，用于在重试的各个阶段记录日志，方便追踪和调试。

## 2. 核心组件与关键类职责

### 2.1. `cn.maple.retry.config.GXRetryConfig`

- **职责**：Spring Retry 的核心配置类。
- **主要功能**：
    - 通过 `@EnableRetry` 注解启用 Spring Retry 功能。
    - 定义并配置了一个默认的 `RetryTemplate` Bean，供应用程序通过依赖注入使用。
    - 默认 `RetryTemplate` 配置了：
        - **重试策略 (`SimpleRetryPolicy`)**：
            - 默认最大尝试次数：3次（包括首次尝试）。
            - 默认可重试异常：`cn.maple.core.framework.exception.GXBusinessException` 及其子类。
        - **退避策略 (`ExponentialBackOffPolicy`)**：
            - 默认初始退避间隔：1000毫秒（1秒）。
            - 默认退避乘数：2.0。
            - 默认最大退避间隔：10000毫秒（10秒）。
        - **监听器**：注册了 `GXRetryListener` 用于日志记录。
    - 提供了创建自定义配置 `RetryTemplate` 实例的方法（虽然在当前版本中，该方法主要在文档注释中作为示例，实际 Bean 的创建是固定的）。
- **线程安全**：`RetryTemplate` 本身是线程安全的，`GXRetryConfig` 创建的 `RetryTemplate` Bean 是单例的，可在整个应用中安全使用。

### 2.2. `cn.maple.retry.listener.GXRetryListener`

- **职责**：自定义的 Spring Retry 监听器实现。
- **主要功能**：
    - 实现 `RetryListener` 接口，在重试操作的生命周期事件（如开始、错误、结束）中记录详细日志。
    - `open()`：在重试操作开始时记录日志。
    - `onError()`：在每次重试尝试失败并抛出异常时记录错误信息。
    - `close()`：在重试操作完成（成功或所有尝试均失败）时记录日志。
    - 包含对回调类型和上下文ID的解析，以提供更丰富的日志信息。
- **线程安全**：该监听器实现是无状态的，可以安全地被多个 `RetryTemplate` 实例共享。

### 2.3. `cn.maple.retry.util.GXRetryUtil`

- **职责**：提供编程式重试操作的静态工具类。
- **主要功能**：
    - 封装了 `RetryTemplate` 的创建和执行逻辑，简化了编程式重试的使用。
    - 提供了多个重载的 `retryOperation`静态方法，支持：
        - 使用默认配置进行重试。
        - 自定义最大尝试次数、初始退避时间、退避乘数、最大退避时间。
        - 指定需要重试的特定异常类型。
        - 提供恢复回调函数 (`RecoveryCallback`)，在所有重试失败后执行。
    - 每次调用 `retryOperation` 都会创建一个新的 `RetryTemplate` 实例，确保了配置的隔离性和线程安全。
    - 自动注册 `GXRetryListener` 到创建的 `RetryTemplate` 实例中。
- **线程安全**：由于每次调用都创建新的 `RetryTemplate`，因此工具类本身的方法调用是线程安全的。

## 3. 对外提供的接口与配置

### 3.1. Maven 依赖

要使用此模块，需要在项目的 `pom.xml` 文件中添加以下依赖：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-retry</artifactId>
    <version>${project.parent.version}</version> <!-- 请替换为实际版本号 -->
</dependency>
```

该模块依赖于 `leaf-base-framework` 和 `spring-retry`。

### 3.2. 主要配置项

`leaf-base-retry` 模块的核心配置主要通过 `GXRetryConfig` 类完成，大部分配置是硬编码的默认值。如果需要调整默认的 `RetryTemplate` 行为（例如最大尝试次数、退避策略参数、默认重试的异常类型），目前需要直接修改 `GXRetryConfig.java` 文件中的常量或 `retryTemplate()` 方法的实现。

未来可以考虑将这些配置项外部化到 `application.properties` 或 `application.yml` 文件中，通过 `@Value` 注解注入，以提供更大的灵活性。

### 3.3. 可注入的 Bean

- **`RetryTemplate`**：可以直接注入由 `GXRetryConfig` 配置的默认 `RetryTemplate` 实例。

  ```java
  @Autowired
  private RetryTemplate retryTemplate;
  ```

## 4. 典型使用场景示例

### 4.1. 使用 `@Retryable` 注解 (声明式重试)

这是最简单和推荐的使用方式，适用于方法级别的重试。

```java
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import cn.maple.core.framework.exception.GXBusinessException;

@Service
public class MyRetryableService {

    private int attemptCount = 0;

    // 当方法抛出 GXBusinessException 或其子类异常时，会进行重试
    // maxAttempts: 最大尝试次数，包括首次尝试 (默认为3，由GXRetryConfig中的默认RetryTemplate决定，但注解可以覆盖)
    // backoff: 退避策略
    //    delay: 初始延迟时间 (毫秒)
    //    multiplier: 延迟乘数
    //    maxDelay: 最大延迟时间 (毫秒)
    @Retryable(value = {GXBusinessException.class, java.io.IOException.class}, 
               maxAttempts = 4, 
               backoff = @Backoff(delay = 1500, multiplier = 1.5, maxDelay = 15000))
    public String performOperation(String input) throws GXBusinessException, java.io.IOException {
        attemptCount++;
        System.out.println("Attempting operation for input: " + input + ", attempt: " + attemptCount);
        if (attemptCount < 3) { // 模拟前两次失败
            if (attemptCount % 2 == 0) {
                 throw new java.io.IOException("Simulated IO error on attempt " + attemptCount);
            }
            throw new GXBusinessException("Simulated business error on attempt " + attemptCount);
        }
        System.out.println("Operation successful for input: " + input + " on attempt " + attemptCount);
        return "Operation successful with input: " + input;
    }

    // 恢复方法：当 @Retryable 标记的方法所有重试都失败后，会调用此方法
    // 注意：恢复方法的第一个参数必须是导致重试失败的异常，后续参数需要与 @Retryable 方法的参数列表匹配
    @Recover
    public String recoverFromError(GXBusinessException e, String input) {
        System.err.println("Recovery after GXBusinessException for input: " + input + ". Error: " + e.getMessage());
        attemptCount = 0; // 重置计数器
        return "Recovered from GXBusinessException for input: " + input;
    }

    @Recover
    public String recoverFromIOError(java.io.IOException e, String input) {
        System.err.println("Recovery after IOException for input: " + input + ". Error: " + e.getMessage());
        attemptCount = 0; // 重置计数器
        return "Recovered from IOException for input: " + input;
    }
    
    // 可以定义一个通用的恢复方法来处理所有未被特定恢复方法捕获的异常
    @Recover
    public String generalRecover(Throwable t, String input) {
        System.err.println("General recovery for input: " + input + ". Error: " + t.getMessage());
        attemptCount = 0;
        return "General recovery for: " + input;
    }
}
```

**注意**：要使 `@Retryable` 和 `@Recover` 注解生效，需要在配置类上添加 `@EnableRetry` 注解（`GXRetryConfig` 已包含此注解），并且被注解的方法必须由 Spring AOP 代理（通常意味着方法是 public 的，并且是从外部调用的）。

### 4.2. 使用注入的 `RetryTemplate` (编程式重试)

适用于更复杂的场景，或者当需要在代码中动态控制重试逻辑时。

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import cn.maple.core.framework.exception.GXBusinessException;

@Service
public class MyProgrammaticRetryService {

    private final RetryTemplate retryTemplate;

    @Autowired
    public MyProgrammaticRetryService(RetryTemplate retryTemplate) {
        this.retryTemplate = retryTemplate;
    }

    public String performOperationWithDefaultTemplate(String data) {
        return retryTemplate.execute(context -> {
            // context 包含当前重试状态，如重试次数 context.getRetryCount()
            System.out.println("Executing with default RetryTemplate, attempt: " + (context.getRetryCount() + 1));
            if (context.getRetryCount() < 2) { // 模拟前两次失败
                throw new GXBusinessException("Simulated failure for default template");
            }
            return "Successfully processed: " + data;
        }, context -> {
            // RecoveryCallback: 所有重试失败后执行
            System.err.println("All retries failed for default template. Last exception: " + context.getLastThrowable().getMessage());
            return "Fallback result for: " + data;
        });
    }
}
```

### 4.3. 使用 `GXRetryUtil` 工具类 (编程式重试)

提供静态方法，无需注入 `RetryTemplate`，方便在非 Spring Bean 或需要快速使用重试的场景。

```java
import cn.maple.retry.util.GXRetryUtil;
import cn.maple.core.framework.exception.GXBusinessException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RetryUtilDemo {

    public void demonstrateRetryUtil() {
        // 示例1: 基本重试，默认配置 (3次尝试，特定异常，指数退避)
        try {
            String result = GXRetryUtil.retryOperation((RetryCallback<String, Throwable>) context -> {
                System.out.println("GXRetryUtil: Attempting operation (default config)...");
                if (Math.random() < 0.7) { // 70% 概率失败
                    throw new GXBusinessException("Default config: Operation failed");
                }
                return "Default config: Operation successful";
            });
            System.out.println(result);
        } catch (GXBusinessException e) {
            System.err.println("Default config: All retries failed: " + e.getMessage());
        }

        System.out.println("-----");

        // 示例2: 自定义重试次数和退避参数，并指定只重试 IOException
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(IOException.class, true); // 只重试 IOException
        retryableExceptions.put(GXBusinessException.class, false); // 不重试 GXBusinessException

        try {
            String customResult = GXRetryUtil.retryOperation((RetryCallback<String, Throwable>) context -> {
                        System.out.println("GXRetryUtil: Attempting operation (custom config)...");
                        double r = Math.random();
                        if (r < 0.4) {
                            throw new IOException("Custom config: Simulated IOException");
                        } else if (r < 0.8) {
                            throw new GXBusinessException("Custom config: Simulated GXBusinessException - should not retry");
                        }
                        return "Custom config: Operation successful";
                    },
                    context -> { // RecoveryCallback
                        System.err.println("Custom config: All retries failed. Last exception: " + context.getLastThrowable().getMessage());
                        return "Custom config: Fallback value";
                    },
                    5,       // maxAttempts
                    500,     // initialInterval (ms)
                    1.2,     // multiplier
                    5000,    // maxInterval (ms)
                    retryableExceptions
            );
            System.out.println(customResult);
        } catch (Throwable e) { // Catch Throwable because GXBusinessException might still be thrown if not retried
            System.err.println("Custom config: Error during retry execution or unretried exception: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        RetryUtilDemo demo = new RetryUtilDemo();
        demo.demonstrateRetryUtil();
    }
}
```

## 5. 与其他模块的交互关系

- **`leaf-base-framework`**: `leaf-base-retry` 依赖于 `leaf-base-framework`，特别是其中的 `cn.maple.core.framework.exception.GXBusinessException`，默认情况下，`GXRetryConfig` 配置的 `RetryTemplate` 会重试此类型的异常。
- **Spring Core / Spring AOP**: 声明式重试（`@Retryable`）依赖于 Spring AOP。编程式重试则直接使用 Spring Retry 库的核心类。
- **SLF4J / Logback (或其他日志实现)**: `GXRetryListener` 使用 SLF4J 进行日志记录，具体的日志输出行为取决于项目中集成的日志实现（如 Logback）。

## 6. 线程安全与性能考量

- **`RetryTemplate`**: Spring 的 `RetryTemplate` 本身设计为线程安全的。`GXRetryConfig` 中定义的 `RetryTemplate` Bean 是一个单例，可以安全地在多线程环境中使用。每次 `execute` 调用都会创建一个新的 `RetryContext`，确保了线程间的隔离。
- **`GXRetryListener`**: 实现是无状态的，因此也是线程安全的。
- **`GXRetryUtil`**: 该工具类的静态方法在每次调用时都会创建一个全新的 `RetryTemplate` 实例及其关联的策略和监听器。这种设计确保了不同调用之间的完全隔离，从而保证了线程安全，但也意味着在高频率调用时可能会有轻微的性能开销（对象创建）。对于性能敏感且配置固定的场景，推荐使用注入的单例 `RetryTemplate`。
- **退避策略**: 默认的指数退避策略有助于在高并发场景下分散重试请求，避免对下游服务造成冲击（雪崩效应）。
- **异常匹配**: `SimpleRetryPolicy` 在匹配异常时，如果设置了 `traverseCauses` 为 `true`（当前配置是 `true`），会遍历异常的 cause 链，这在某些复杂异常场景下可能略有性能影响，但能更准确地匹配到需要重试的根本原因。

## 7. 注意事项与最佳实践

- **幂等性**: 被重试的操作应该是幂等的。即多次执行同一个操作（由于重试）应该与执行一次产生相同的结果或副作用。
- **避免无限重试**: 合理设置最大尝试次数，对于非瞬时错误（如业务逻辑错误、权限问题），不应进行重试。
- **异常分类**: 明确哪些异常是瞬时且可恢复的（适合重试），哪些是永久性的（不应重试）。`GXRetryUtil` 和 `SimpleRetryPolicy` 都支持配置可重试的异常类型。
- **日志级别**: `GXRetryListener` 会记录不同级别的日志。确保项目的日志配置能够捕获这些信息，特别是在生产环境中，可能需要调整 `WARN` 或 `ERROR` 级别日志的关注度。
- **异步重试**: 对于耗时较长的操作，直接在同步方法中使用重试可能会阻塞调用线程。可以考虑将重试逻辑包装在异步任务中（例如使用 `@Async` 注解或 `CompletableFuture`），`leaf-base-retry` 本身不直接提供异步执行器，但其组件可以被用于异步场景。
- **配置外部化**: 当前模块的很多默认配置是硬编码的。在实际项目中，可以考虑将这些配置（如最大尝试次数、退避参数）通过 Spring Boot 的配置属性（`application.properties`/`yml`）进行外部化，以增强灵活性。

## 8. 未来展望

- **配置属性化**：将 `GXRetryConfig` 中的默认值通过 `@Value` 从配置文件加载。
- **更细致的监听器**：提供更多维度的监听事件或指标上报能力，例如集成 Micrometer 监控重试次数、成功率等。
- **断路器集成**：考虑与断路器模式（如 Resilience4j, Sentinel）结合，提供更全面的容错方案。
- **动态配置更新**：支持在运行时动态调整重试策略（例如通过配置中心）。

---

本文档旨在帮助开发者理解和使用 `leaf-base-retry` 模块。如有疑问或建议，请联系模块维护者。