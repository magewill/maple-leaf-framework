# leaf-base-retry 模块开发手册

## 1. 模块概述

`leaf-base-retry` 是 Maple Leaf Framework 的通用重试基础模块，基于 **Spring Framework 7** 内置的重试能力（`org.springframework.core.retry.*` 与 `org.springframework.resilience.annotation.*`），**不依赖 spring-retry**。

核心能力：

1. 统一重试配置（`GXRetryConfig`）：默认策略常量、`RetryTemplate` Bean、`@EnableResilientMethods`。
2. 编程式重试（`GXRetryUtil`）：同步 / 异步 / 恢复回调 / 自定义异常映射 / 可选超时与抖动 / 可取消。
3. 重试生命周期日志（`GXRetryListener`）。
4. 回调与上下文抽象（`GXRetryCallback`、`GXRecoveryCallback`、`GXRetryContext`）。

不在本模块内承载业务流程、业务补偿编排或业务异常语义。

## 2. 核心组件

| 类型 | 职责 |
| --- | --- |
| `cn.maple.retry.config.GXRetryConfig` | `@Configuration` + `@EnableResilientMethods`；提供默认策略常量（`DEFAULT_MAX_ATTEMPTS` 等，单点定义）与 `RetryTemplate` Bean |
| `cn.maple.retry.config.GXRetryOptions` | 不可变策略参数载体（次数、间隔、倍率、上限、总超时、抖动、异常映射），构造即校验 |
| `cn.maple.retry.util.GXRetryUtil` | 静态工具入口；按策略参数缓存 `RetryTemplate` |
| `cn.maple.retry.context.GXRetryContext` | 单次尝试上下文；属性跨尝试复制，`parent` 指向上一尝试 |
| `cn.maple.retry.context.GXCancellationToken` | 协作式取消句柄 |
| `cn.maple.retry.exception.GXRetryCancelledException` | 取消时抛出，继承 `GXBusinessException` |
| `cn.maple.retry.listener.GXRetryListener` | 重试日志（开始 / 失败 / 成功 / 耗尽 / 中断 / 超时） |

## 3. 对外接口与配置

### 3.1 Maven 依赖

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-retry</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

模块只依赖 `leaf-base-framework`。装配方式为**组件扫描**（下游使用 `@GXEnableLeafFramework` 时会扫描 `cn.maple`），本模块不提供自动装配注册。

**与自定义 `RetryTemplate` Bean 共存**：本模块的 `RetryTemplate` 标注 `@ConditionalOnMissingBean`，但条件注解在普通 `@Configuration` 上装配顺序不可靠，可能出现**两个** `RetryTemplate` Bean。此时按类型直接注入会抛 `NoUniqueBeanDefinitionException`，请任选一种方式消除歧义：

```java
// 方式一：注入时指定本模块 Bean 的名称
@Autowired
@Qualifier("retryTemplate")
private RetryTemplate retryTemplate;

// 方式二：给自定义 Bean 加 @Primary，按类型注入时优先生效
@Bean
@Primary
public RetryTemplate customRetryTemplate() {
    return new RetryTemplate(RetryPolicy.withMaxRetries(1));
}
```

`GXRetryUtil` 自建模板、不读取容器中的 `RetryTemplate` Bean，因此上述冲突只影响按类型注入的场景。仅导入 `GXRetryConfig`（未提供 `GXRetryListener` Bean）时仍可正常启动，配置类会回退到进程内监听器实例。

### 3.2 默认参数

| 项 | 默认值 | 说明 |
| --- | --- | --- |
| `maxAttempts` | 3 | 含首次尝试；上限 100 |
| `initialInterval` | 1000ms | 上限 300000ms，下限 100ms |
| `multiplier` | 2.0 | 范围 1.0 ~ 10.0，必须为有限值 |
| `maxInterval` | 10000ms | 上限 3600000ms |
| `timeout` | 0（不启用） | 总耗时预算 |
| `jitter` | 0（不启用） | 退避抖动 |

### 3.3 日志开关（系统属性，非 Spring 配置）

| 属性 | 默认 | 语义 |
| --- | --- | --- |
| `maple.framework.retry.log.level` | `INFO` | 阈值：`OFF < ERROR < WARN < INFO < DEBUG`；非法值按 `INFO`；`OFF` 全关 |
| `maple.framework.retry.log.verbose` | `false` | `true` 时额外输出失败堆栈（DEBUG） |

> 两项只能通过 `-D` 或 `System.setProperty` 设置，**无法**写入 `application.yml`，也无法按 profile 区分。级别在每次事件时读取，支持运行期变更。

## 4. 典型用法

### 4.1 编程式重试（默认参数）

```java
String result = GXRetryUtil.retryOperation(context -> {
    // context.getAttemptIndex() / context.getRetryCount() 为 0 基尝试序号
    return remoteCall();
});
```

### 4.2 自定义策略与恢复回调

```java
String result = GXRetryUtil.retryOperation(
        context -> remoteCall(),
        context -> fallbackValue(),     // 重试耗尽、不可重试、中断、超时都会执行
        5,                              // maxAttempts
        200L);                          // initialInterval(ms)
```

**只替换异常映射、其余参数保持默认**：用 `GXRetryOptions.defaults().withRetryExceptions(...)`。该方法返回**新实例**（原实例不变），适合"默认 3 次、1000ms 起、仅调整可重试异常"的场景：

```java
GXRetryOptions options = GXRetryOptions.defaults().withRetryExceptions(Map.of(
        Exception.class, true,
        GXBusinessException.class, false));

String result = GXRetryUtil.retryOperation(context -> remoteCall(), null, options);
```

### 4.3 可选总超时与抖动

```java
GXRetryOptions options = new GXRetryOptions(
        5,          // maxAttempts
        200L,       // initialInterval
        1.5,        // multiplier
        2000L,      // maxInterval
        3000L,      // timeout：整条重试链的总预算，0 表示不限制
        100L,       // jitter：每次退避的随机偏移上限，0 表示不抖动
        Map.of(Exception.class, true));

String result = GXRetryUtil.retryOperation(context -> remoteCall(), null, options);
```

### 4.4 异步重试与取消

```java
GXCancellationToken token = new GXCancellationToken();

CompletableFuture<String> future = GXRetryUtil.retryOperationAsync(
        context -> remoteCall(),
        null,
        GXRetryOptions.defaults(),
        executor,
        token);

token.cancel();   // 下一次尝试前生效，抛 GXRetryCancelledException，且不执行恢复回调
```

注意：

- `future.cancel(true)` **不会**中断在途重试，需要停止请使用 token。
- 异步路径会传播调用线程的 MDC / TraceId（内部使用 `GXMdcThreadUtils`）。
- 异步失败统一包装为 `GXBusinessException`；`Error` 与取消异常原样抛出。

### 4.5 上下文属性跨尝试传递

```java
String result = GXRetryUtil.retryOperation(context -> {
    String endpoint = (String) context.getAttribute("endpoint");
    if (endpoint == null) {
        context.setAttribute("endpoint", pickNextEndpoint());
        throw new IllegalStateException("switch node");
    }
    return call(endpoint);
}, 2, 100L);
```

第 2 次尝试可以读到第 1 次写入的属性；`context.getParent()` 指向上一次尝试的上下文。

回调与恢复回调都允许返回 `null`，工具方法会原样返回 `null`（所有同步入口的返回值均标注 `@Nullable`）。

### 4.6 声明式重试

```java
@Retryable(maxRetries = 2)
public String performOperation() {
    ...
}
```

`@Retryable`（`org.springframework.resilience.annotation.Retryable`）由 `@EnableResilientMethods` 驱动，**只读取注解自身属性**，不受 `GXRetryConfig` 默认值或 `RetryTemplate` Bean 影响。注解默认 `maxRetries = 3`，即最多 4 次尝试。

> **注意**：`@Retryable` 的 `includes` 默认为空，等价于匹配全部 `Throwable`，因此**会重试 `Error` 与 `InterruptedException`**，与 `GXRetryUtil`（不重试 `Error`）和 `RetryTemplate` Bean（不重试中断）的默认集不同。需要与非幂等操作或致命错误隔离时，请显式设置 `excludes`，详见 §5 的入口 × 异常类型矩阵。

## 5. 默认异常集与风险（必读）

| 入口 | 默认可重试异常 | 说明 |
| --- | --- | --- |
| `GXRetryUtil` | **所有 `Exception`** | 含 `GXBusinessException`、`IllegalArgumentException`、`InterruptedException`；不含 `Error` |
| `RetryTemplate` Bean | `GXBusinessException`、`IOException`、`TimeoutException`、`SocketTimeoutException` | 由 `GXRetryConfig` 决定 |
| `@Retryable` | 注解 `includes`（默认为空 = 全部） | 由注解决定 |

**风险**：参数错误、余额不足、权限不足等确定性失败会被重复执行最多 3 次；非幂等操作会放大副作用；`InterruptedException` 也会被重试。请对非幂等操作显式排除：

```java
Map<Class<? extends Throwable>, Boolean> retryExceptions = Map.of(
        Exception.class, true,
        GXBusinessException.class, false,
        InterruptedException.class, false);

GXRetryUtil.retryOperation(callback, null, retryExceptions);
```

匹配规则（与上游一致）：`includes` 为空表示全部匹配；匹配会遍历 `cause` 链；**`excludes` 优先于 `includes`**。

## 6. 上下文与计数（`retryCount` 语义）

| 位置 | 含义 | 3 次尝试耗尽时的取值 |
| --- | --- | --- |
| 回调 `context.getRetryCount()` | 已执行的重试次数（0 基尝试序号） | 最后一次为 `2` |
| 回调 `context.getAttemptIndex()` | 同上（同值别名） | `2` |
| 恢复回调 `context.getRetryCount()` | 已执行的重试次数 | `2` |
| `GXRetryContext.from(RetryState)` | 上游 `RetryState.getRetryCount()`（重试次数） | `2` |

- 不可重试异常（只执行 1 次）时，恢复回调的 `getRetryCount()` 为 `0`。
- 需要"尝试数"时使用 `getAttemptIndex() + 1`。
- 兼容说明：旧版本恢复回调返回的是**尝试数**（3 次尝试返回 `3`，不可重试返回 `1`），升级后数值整体减 1。

## 7. 性能与缓存

- 每次调用固定开销：1 次容器类型查询（`GXRetryConfig`）、1 次异常映射拷贝、1 个缓存键对象；每次尝试 1 个上下文对象（复制前递为 O(属性数)）。
- `RetryTemplate` 按策略参数缓存，上限 256；超过后在专用锁内裁剪到 192。工具类**不读取**容器中的 `RetryTemplate` Bean，自定义 Bean 只影响按类型注入。
- 最坏阻塞：第 k 次重试等待 `min(initialInterval × multiplier^(k-1), maxInterval)`。默认参数（3 次尝试）合计 3000ms；参数上限组合（100 次、初始 300000ms、倍率 10、上限 3600000ms）约 **98 小时**——长重试必须显式设置 `timeout`。
- 日志：每次失败尝试 1 条 WARN（受级别阈值与 `isEnabled` 双重短路）；重试成功 1 条 INFO；每次重试链开始时 1 条 INFO。

## 8. 线程安全

| 对象 | 结论 |
| --- | --- |
| `RetryTemplate` | 线程安全：字段仅构造期赋值，执行期状态为方法内局部对象 |
| `GXRetryUtil` 缓存 | `ConcurrentHashMap` 读路径无锁；淘汰在超阈值时串行化 |
| `GXRetryContext` | 每尝试独立实例；属性由 `ConcurrentHashMap` 承载，跨线程只读快照 |
| `GXRetryListener` | 无状态；Spring 路径使用容器 Bean，静态入口回退到进程内实例 |

## 9. 升级必查

1. 恢复回调 `retryCount` 由"尝试数"改为"重试次数"（3→2、1→0）；依赖该值做告警/对账的代码需同步。
2. `GXRetryListener.clearCache()` 与内部类型缓存已删除（该缓存从未生效）；如有调用请移除。
3. `maple.framework.retry.log.level=OFF` 现在表示"全关"（旧版本反而全开）。
4. 首次重试会新增 1 条 INFO 日志（旧版本"retry started"因判据错误从未输出）。
5. `multiplier` 传入 `NaN`/`Infinity` 现在由本模块直接拒绝。

## 10. 与其他模块的关系

- `leaf-base-framework`：提供 `GXBusinessException`（默认异常集之一）、`GXSpringContextUtils`（取配置 Bean）、`GXMdcThreadUtils`（异步 MDC 传播）。
- Spring AOP：`@Retryable` 依赖 AOP 代理，方法需 `public` 且从外部调用。
- 业务模块：负责幂等、补偿编排与死信，本模块只保证"按策略重复执行"。
