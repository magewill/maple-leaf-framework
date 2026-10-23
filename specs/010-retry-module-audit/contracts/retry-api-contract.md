# 契约：重试公开 API

**Feature**: `010-retry-module-audit` | **Scope**: `cn.maple.retry.*` 的公开类型与方法 | **依据**: FR-013、FR-015、FR-019、FR-023；research D-03/D-04/D-05/D-07

本契约规定"改什么、不许改什么"。**既有 10 个 `retryOperation` 重载、2 个 `retryOperationAsync` 重载、2 个 `retrySupplier` 重载的签名与默认行为不得变更**（FR-013/FR-018）；改造后 `retryOperation` 共 11 个（新增 `options` 重载）、`retryOperationAsync` 共 4 个（新增 `(options, executor)` 与 `(options, executor, token)`）。

## 1. API 变更总表

| 类型 | 变更 | 性质 | 兼容性 |
| --- | --- | --- | --- |
| `GXRetryContext` | 新增 `getAttemptIndex()`；新增 `parent` 取值语义（指向上一尝试实例）；新增带 `parent` 的构造器与 `from(RetryState, parent)` 重载 | 加法式 | 兼容；`getRetryCount()` 返回值不变 |
| `GXRetryContext` | `getRetryCount()` 语义固定为"已执行的重试次数（首次执行为 0）" | 语义澄清 | 回调侧数值不变；恢复侧见 §3 |
| `GXRetryOptions` | 新增 record（7 个组件 + `defaults()` + `withRetryExceptions(Map<...>)`）；`withRetryExceptions` 返回**新实例**，语义为"仅替换异常映射、其余参数保持不变"，供"在默认参数上只改重试异常"的场景使用 | 加法式 | 兼容 |
| `GXCancellationToken` | 新增（`cancel()`、`isCancelled()`） | 加法式 | 兼容 |
| `GXRetryCancelledException` | 新增，`extends GXBusinessException` | 加法式 | 兼容 |
| `GXRetryUtil` | 新增 3 个重载：`retryOperation(callback, recovery, options)`、`retryOperationAsync(callback, recovery, options, executor)`、`retryOperationAsync(callback, recovery, options, executor, token)` | 加法式 | 兼容 |
| `GXRetryConfig` | 新增 7 参 `createCustomRetryTemplate(...)` 与 `createCustomRetryTemplate(GXRetryOptions)`（后者为 `GXRetryUtil` 的入口，参数已由 record 校验）；`DEFAULT_*` 提升为 `public static final` | 加法式 | 兼容 |
| `GXRetryListener` | 删除 `clearCache()` 与类型缓存 | 删除 | **不兼容**（模块内仅测试引用），升级说明中列明 |
| `GXRetryListener` | `getConfigInfo()` 返回内容去掉 `cacheSize` | 行为变更 | 仅诊断用途 |

## 2. 上下文契约

```text
new GXRetryContext(int retryCount, @Nullable Throwable lastThrowable)
new GXRetryContext(int retryCount, @Nullable Throwable lastThrowable, @Nullable GXRetryContext parent)  // 新增
```

- 每次尝试由框架新建实例；构造时把 `parent` 的全部属性**复制**到本实例（`D-03`）。
- `getRetryCount()` == `getAttemptIndex()` == 本次尝试的 0 基序号。
- `getParent()` 在第一次尝试返回 `null`，之后指向上一尝试实例；链长等于尝试次数 - 1。
- 属性复制是"值复制"，不做深拷贝：可变值对象跨尝试共享同一引用（与现状一致，不新增语义）。
- 修复前 `setAttribute` 在第二次尝试读不到；修复后**可以**读到（回归用例 `T-05`）。

## 3. 重试次数契约（FR-023）

| 位置 | 修复前 | 修复后 | 说明 |
| --- | --- | --- | --- |
| 回调 `context.getRetryCount()` | 0,1,2…（尝试序号） | 不变 | 数值上等于重试次数 |
| 回调 `context.getAttemptIndex()` | 不存在 | 0,1,2… | 与 `getRetryCount()` 同值 |
| 恢复回调 `context.getRetryCount()` | 3（尝试数，`getExceptions().size()`） | **2**（重试次数，`RetryException.getRetryCount()`） | 3 次尝试 = 2 次重试 |
| 不可重试异常时恢复回调 | 1 | **0** | 0 次重试 |
| `GXRetryContext.from(RetryState)` | `retryState.getRetryCount()` | 不变 | 同为重试次数 |

**必须**同步更新的既有断言：`GXRetryUtilTest` 中 3、1、2 三处（见 `tasks.md`）。

## 4. 取消契约（D-07）

- 无 token 的既有异步方法：行为完全不变，失败仍统一包装为 `GXBusinessException`；**文档必须声明** `CompletableFuture.cancel(true)` 不会中断在途重试。
- 带 token 的重载：`token.cancel()` 后，在下一次尝试开始前（即退避结束后、回调执行前）的检查点抛 `GXRetryCancelledException`；**不执行恢复回调**；`CancellationException` 不由框架产生。已进入退避的 `Thread.sleep` 不可被打断，取消在退避结束后生效。
- 工作线程已中断（`Thread.currentThread().isInterrupted()`）时同样在检查点终止，并抛 `GXBusinessException`（无 token 版本）或 `GXRetryCancelledException`（有 token 版本）。

## 5. 异常传播契约（保持不变）

| 场景 | 同步路径 | 异步路径 |
| --- | --- | --- |
| 重试耗尽、无恢复回调 | 原始异常原样抛出（sneaky throw，保留受检类型） | 包装为 `GXBusinessException(e.getMessage(), e)` |
| 恢复回调执行 | 返回恢复结果；恢复自身抛错则原样抛出 | 同左，异常再被包装 |
| `Error`（如 `AssertionError`） | 不重试、原样抛出 | 不包装为 `GXBusinessException`，原样抛出 |
| 参数非法（次数/间隔/倍率/超时/抖动越界） | `IllegalArgumentException`，且**不触达回调** | 同左（提交前或执行前抛出） |

## 6. 空值契约

- 包级仍为 `@NullUnmarked`（保持现状）；**新增/修改的公开 API 对可能为空的入参与返回值补 `@Nullable`**：`GXRecoveryCallback` 参数、`GXRetryOptions.retryExceptions` 元素、`GXRetryContext.setAttribute(String, Object)` 与 `getAttribute(String)` 的返回值、`retryOperation` 的返回值。
- `GXRetryOptions` 的 `retryExceptions` 传 `null`/空 map 时按"默认 `Exception.class → true`"处理（与现状一致）。
- 同步入口（11 个 `retryOperation*` 与 2 个 `retrySupplier*`）的返回值 MUST 标注 `@Nullable`：回调或恢复回调返回 `null` 时原样返回 `null`。异步入口返回 `CompletableFuture<T>` 本身非空，不标注。
