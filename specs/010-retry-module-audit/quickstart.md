# Quickstart: 验证 leaf-base-retry 审查结论与修复

**Feature**: `010-retry-module-audit` | **Spec**: [spec.md](./spec.md) | **Contracts**: [retry-api-contract.md](./contracts/retry-api-contract.md)、[retry-policy-contract.md](./contracts/retry-policy-contract.md)、[observability-contract.md](./contracts/observability-contract.md)

本指南用于"评审他人提交"或"自测本次交付"，只给命令与判定标准，不含实现代码。

## 0. 前置条件

| 项 | 要求 |
| --- | --- |
| JDK | 21+（ErrorProne + NullAway 参与编译） |
| Maven | 3.10+，本地仓库已具备 `leaf-base-framework 4.3.0-SNAPSHOT` |
| 网络 | 不需要；全部命令可离线执行（`-o`） |
| 外部系统 | 不需要（无 Broker/DB/注册中心） |

## 1. 基线（审查结论复现）

```powershell
cd e:\project\java\maple-leaf-framework
mvn -o -pl leaf-base-retry test
```

**判定**：当前仓库应输出 `Tests run: 30, Failures: 0, Errors: 0`，其中 `GXRetryUtilTest` 21 条、耗时约 16.5s。若不是 30/0/0，先确认为"交付前基线"还是"交付后回归"。

## 2. 审查结论抽检（5 分钟）

| 结论 | 复现方式 | 期望 |
| --- | --- | --- |
| F-01 默认重试所有 `Exception` | 阅读 `GXRetryUtil.java:233-239`；对照 `testRetryOperation_DefaultParams_AllAttemptsFail` | 默认 `Exception.class → true`；`RuntimeException` 被执行 3 次 |
| F-02 最坏阻塞 98 小时 | 按 `contracts/retry-policy-contract.md` §4 公式复算 | 300000 + 98 × 3600000 = 353100000ms ≈ 98h |
| F-03 属性不跨尝试 | 跑 `GXRetryContextTest`（交付后新增 `T-05` 用例） | 交付前：第二次尝试读不到；交付后：读得到 |
| F-04 三入口不一致 | 阅读 `GXRetryConfig.java:48-66` 与 `RetryAnnotationBeanPostProcessor`（`spring-core/spring-context` 7.0.9 源码） | `@Retryable` 不读取 Bean/全局默认值 |
| F-05 文档错位 | 打开 `docs/leaf-base-retry开发手册.md` 第 22/166/213 行 | 出现 `@EnableRetry`、`org.springframework.retry.*`、`RetryCallback`（交付后应消失） |

## 3. 交付后验证

```powershell
# 3.1 编译 + 静态检查（ErrorProne/NullAway 必须零错误）
mvn -o -pl leaf-base-retry clean compile

# 3.2 全量测试
mvn -o -pl leaf-base-retry test

# 3.3 只跑回归相关的四个测试类
mvn -o -pl leaf-base-retry test -Dtest=GXRetryContextTest,GXRetryUtilTest,GXRetryListenerTest,GXRetrySpringBootTest
```

**判定标准**：

| 检查 | 期望 |
| --- | --- |
| 编译 | `BUILD SUCCESS`，无 NullAway 报错 |
| 全量测试 | 0 失败 0 错误；新增用例数 ≥ 12（当前全量 82 条，以 `mvn -o -pl leaf-base-retry test` 输出为准）；耗时口径分两档：`GXRetryUtilTest` 单类墙钟约 8.8s（原定 8s 工程目标因新增 2 条必用默认退避的重载用例而小幅上浮，仍为非门禁目标），全量约 13s（含 Spring 上下文启动） |
| 既有 3 条计数断言 | 已按 2 / 0 / 1 更新，且用例名体现"重试次数"而非"总尝试数" |
| `GXRetryListener.clearCache` | 全仓 `grep -r "clearCache" leaf-base-retry/src` 无结果 |

## 4. 分场景验收

### 4.1 上下文跨尝试传递（FR-015）

1. 在首次尝试 `setAttribute("k", "v1")`，第二次尝试读取。
2. **期望**：读到 `"v1"`；`getParent()` 非空且链长 = 尝试数 - 1；`getAttemptIndex()` 依次为 0、1、2。

### 4.2 计数语义（FR-023）

| 场景 | 期望 |
| --- | --- |
| 3 次尝试耗尽 | 最后一次回调 `getRetryCount()==2`；恢复回调 `getRetryCount()==2` |
| 不可重试异常（仅 1 次尝试） | 恢复回调 `getRetryCount()==0` |

### 4.3 可选超时与抖动（FR-020）

1. `GXRetryOptions` 设 `timeout=300ms`、回调每次阻塞 200ms、`maxAttempts=5`。
2. **期望**：在超时附近终止，尝试次数 < 5，抛出最后一次业务异常，恢复回调被执行。
3. 设 `jitter=100ms`、其余默认：**期望**尝试次数仍为 3，总耗时在 2 个抖动区间内波动（`newBehavior` 只验证次数与区间，不做精确时序断言）。

### 4.4 取消与 MDC（FR-019/FR-021）

```powershell
mvn -o -pl leaf-base-retry test -Dtest=GXRetryUtilAsyncTest
```

| 场景 | 期望 |
| --- | --- |
| `token.cancel()` 后 | 下一次检查点抛 `GXRetryCancelledException`，恢复回调**不**执行，后续尝试数为 0 |
| 无 token + `future.cancel(true)` | 在途重试**继续**（现状契约），文档已明确声明 |
| 带 TraceId 的请求内发起异步重试 | 回调与监听器日志中可读到同一 TraceId |

### 4.5 并发与缓存（FR-019）

```powershell
mvn -o -pl leaf-base-retry test -Dtest=GXRetryUtilConcurrencyTest
```

**期望**：32 线程 × 8 组参数完成后，每组执行次数与异常类型符合各自参数；缓存 `size() <= 256`；无死锁（用例设 30s 超时）。

## 5. 文档与契约核对（SC-008）

```powershell
# 5.1 手册不得再出现 spring-retry 表述
Select-String -Path docs\leaf-base-retry开发手册.md -Pattern "EnableRetry|SimpleRetryPolicy|ExponentialBackOffPolicy|org\.springframework\.retry|RetryCallback"

# 5.2 三张表必须在场
Select-String -Path docs\leaf-base-retry开发手册.md -Pattern "默认异常集|retryCount|最坏阻塞"
```

**期望**：5.1 无输出；5.2 三个模式均命中。

## 6. 失败处置

| 现象 | 处置 |
| --- | --- |
| 全量测试耗时 > 12s | 检查是否仍有用例使用默认 1s/2s 退避（只允许 1 条） |
| `NaN` 倍率未被本模块拒绝 | 校验顺序问题：`Double.isFinite` 必须在区间比较之前 |
| 缓存 `size() > 256` | 淘汰裁剪目标（192）与阈值判断未在同一锁内二次确认 |
| MDC 断言偶发失败 | 断言应放在回调内部（工作线程），不要放在 `future.join()` 之后的主线程 |
