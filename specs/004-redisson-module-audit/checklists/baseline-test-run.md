# 基线测试记录

**Feature**: `004-redisson-module-audit`
**执行日期**: 2026-10-22
**工作目录**: `e:\project\java\maple-leaf-framework`

## 1. 环境

| 项 | 值 |
| --- | --- |
| 命令 | `mvn -pl leaf-base-redisson -o test` |
| 离线模式 | 是（`-o`），本地仓库 `F:\repository` |
| 依赖坐标 | `org.redisson:redisson:4.7.0`、`org.redisson:redisson-spring-cache:4.7.0` |
| 框架快照 | `cn.maple.framework:leaf-base-framework:4.3.0-SNAPSHOT`（本地已安装） |
| 真实 Redis | 未使用（全部用例基于 Mock/反射） |

## 2. 结果（变更前基线）

```text
Tests run: 38, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.480 s
Test set: cn.maple.redisson.RedissonUtilityRegressionTest
BUILD SUCCESS
```

结论：既有 38 个用例全部通过，可作为本次审查的行为基线（SC-006 前半段成立）。

## 3. 基线覆盖缺口（审查输入）

`RedissonUtilityRegressionTest` 是模块内唯一测试类，命名上覆盖了 `GXRedissonCacheServiceImpl`、`GXRedissonMQUtils`、`GXRedissonDelayMQPostProcessor`、`GXRedissonStreamMQManager`、`GXRedissonStreamPendingMessageRecovery` 等关键路径，但以下方面未被任何用例触及：

1. 订阅缓存失效后的"再订阅"路径（F-01）；
2. 延迟转发在途消息的进程崩溃窗口（F-02）；
3. stream 无 handler / DLQ 写入失败（F-03）；
4. 全局锁下的延迟转移串行性（F-04）；
5. 在途消息无上限增长（F-05）；
6. 重试退避时序（F-06）；
7. 消费者数量与注册顺序的关系（F-07）；
8. `RDelayedQueue` 调用方的销毁一致性（F-08）；
9. 静态缓存跨客户端复用（F-09）；
10. `wrapListener` 异常吞噬（F-10）；
11. 锁释放的往返次数与超时返回值（F-11）。

上述缺口即 US1～US4 中新增审计用例的输入。

## 4. 变更后复测

命令：`mvn -pl leaf-base-redisson -o clean test`

```text
Tests run: 65, Failures: 0, Errors: 0, Skipped: 5
```

说明：60 个为实际执行的用例（38 既有 + 22 审计），5 个为 `GXRedissonRealServiceVerificationTest` 在未设置 `AUDIT_REDIS_HOST` 时的正常跳过；`Failures` 与 `Errors` 均为 0，`BUILD SUCCESS`。

分解（实际执行的 60 个）：

| 测试类 | 用例数 | 结果 |
| --- | --- | --- |
| `RedissonUtilityRegressionTest`（既有基线） | 38 | 全绿 |
| `GXRedissonConnectConfigAuditTest` | 3 | 全绿 |
| `GXRedissonConsumerConcurrencyAuditTest` | 3 | 全绿 |
| `GXRedissonDelayForwardingAuditTest` | 4 | 全绿（含 5.2s 时序用例） |
| `GXRedissonLockSemanticsAuditTest` | 3 | 全绿 |
| `GXRedissonStreamReliabilityAuditTest` | 3 | 全绿 |
| `GXRedissonStreamTransferAuditTest` | 3 | 全绿 |
| `GXRedissonTopicSubscriptionAuditTest` | 3 | 全绿 |

结论：既有行为未受影响（生产代码零改动），新增 22 个审计用例全部通过，无真实 Redis 依赖（SC-006 成立）。

## 5. 真实服务验证（可选）

| 项 | 值 |
| --- | --- |
| 目标 | `<redis-host>:6379`（database 0），地址与密码分别由 `AUDIT_REDIS_HOST` / `AUDIT_REDIS_PASSWORD` 环境变量提供，未写入任何源码、`pom.xml` 或 `config/` 模板 |
| 命令 | `mvn -pl leaf-base-redisson -o clean test -Dtest=GXRedissonRealServiceVerificationTest` |
| 结果 | `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`（10.66 s） |
| 默认构建 | 未设置 `AUDIT_REDIS_HOST` 时该类整体跳过：`Skipped: 5`，其余 60 个用例全绿 |

实机确认的行为：

1. 可靠主题：`publish` 返回 1，订阅方收到 `hello-real-redis`；`unsubscribe` 后 `publish` 返回 0（消费组已销毁）→ F-01 只在"移除失败"时才有缺陷。
2. 延迟队列：500ms 延迟消息准时进入阻塞队列 → 链路可用，但构造时输出 `RDelayedQueue object is deprecated ... Use RReliableQueue object instead.`（error 级），实机确认 F-16。
3. 计数器：自增 1/2 且 TTL ≤ 2000ms，2.5s 后 key 消失 → F-11 正常路径成立。
4. 分布式锁：4 线程并发最大并发度 1，4 次操作全部执行 → 锁互斥有效。
5. stream：`sendImmediate` 后 handler 在 10s 内收到消息 → F-03/F-12 的正常路径成立。

## 6. 注意事项

- 仓库中的 `leaf-base-redisson/target/` 曾存在与当前源码不一致的陈旧 class，导致 `mvn test` 复用旧字节码并出现"Unresolved compilation problems"。复测时统一使用 `clean test`，避免误判为源码编译错误。
- `GXRedissonDelayForwardingAuditTest#inFlightMessagesGrowWithoutBackpressure` 依赖 20 条消息在 5s 内进入 in-flight，单次执行约 5s，属可接受的冒烟成本。
- 真实服务用例通过 `AUDIT_REDIS_HOST` 开启、`AUDIT_REDIS_PASSWORD` 取密码；切勿将凭据写入 `pom.xml`、源码或 `config/` 模板（宪法"敏感值只允许 `${ENV_VAR:default}` 形式注入"）。
