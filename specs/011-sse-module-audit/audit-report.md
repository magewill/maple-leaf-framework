# 审查报告：leaf-base-sse 正确性、线程安全与性能

**特性**: `011-sse-module-audit` | **规格**: [spec.md](./spec.md) | **计划**: [plan.md](./plan.md) | **日期**: 2026-10-23

本报告是 `spec.md` 摘要的独立展开（FR-011），每条结论均可按"位置 + 复现方式"复核。

## 1. 范围与基线

| 项 | 值 |
| --- | --- |
| 生产源码 | 6 个文件（`GXSseEmitterService`、`GXSseEmitterServiceImpl`、`GXSseMessageInnerReqDto`、3 个 `package-info`） |
| 既有测试 | 2 个类、10 条用例（实现类 9 + Spring Boot 启动 1） |
| JDK | 25.0.4（编译目标 Java 21） |
| Spring Boot | 4.1.1 |
| spring-web / spring-webmvc | 7.0.9 |
| tomcat-embed | 11.0.24 |
| jspecify | 1.0.1 |
| Hutool | 5.8.47（经 `leaf-base-framework` 传递） |
| 依赖变更 | 无（`leaf-base-sse/pom.xml` 不改） |

**测试基线**：`mvn -o -pl leaf-base-sse test` → `Tests run: 10, Failures: 0, Errors: 0`（`GXSseEmitterServiceImplTest` 9 条 0.036s、`GXSseEmitterServiceSpringBootTest` 1 条 2.417s）。

## 2. 证据表（E-01 ~ E-24）

`kind`：`LOCAL`=本模块代码，`UPSTREAM`=spring-webmvc 7.0.9 源码，`MEASURED`=实测，`DOC`=文档。

| ID | kind | 位置 | 摘要 | 支撑 |
| --- | --- | --- | --- | --- |
| E-01 | LOCAL | `GXSseEmitterServiceImpl.java:214-218,193-197` | 重试耗尽只 `removeEmitter`，从不 `complete()` | F-01、F-06 |
| E-02 | UPSTREAM | `ResponseBodyEmitter.java:179-187` | `IOException` 后容器通知完成请求，无需调用方 `completeWithError` | F-02 |
| E-03 | LOCAL | `GXSseEmitterServiceImpl.java:222-235,129-132` | 重试通过 `scheduler.schedule` 异步执行，`send` 立即返回；分片继续发送 | F-02 |
| E-04 | UPSTREAM | `ResponseBodyEmitterReturnValueHandler.java:232` | `new DeferredResult<>(emitter.getTimeout())`，写操作不重置超时 | F-03 |
| E-05 | UPSTREAM | `ResponseBodyEmitter.java:201-224,123-152` | handler 未初始化时 `send` 进 `earlySendAttempts`，flush 在 `initialize` | F-04 |
| E-06 | LOCAL | `GXSseEmitterServiceImpl.java:79-95` | `put` + `oldEmitter.complete()` + 首帧 `send` 三步非原子 | F-05 |
| E-07 | LOCAL | `GXSseEmitterServiceImpl.java:245-247,206` | `Objects.requireNonNull(data)` 不在 `catch (IOException\|IllegalStateException)` 范围 | F-07 |
| E-08 | LOCAL | `GXSseEmitterServiceImpl.java:99-107` | 先判集合空再判消息空，日志级别不一致 | F-08 |
| E-09 | LOCAL | `GXSseEmitterServiceImpl.java:260` | `splitMessage` 用 `isEmpty` → `" "` 返回 1 段 | F-19 |
| E-10 | LOCAL+DOC | `GXSseEmitterServiceImpl.java:264`、`GXSseEmitterService.java:97-100`、手册 5.1 | `length` 钳制 `[1,100]` / "requested maximum" / "1KB-2KB" 三处不一致 | F-16 |
| E-11 | LOCAL | `GXSseEmitterServiceImpl.java:36-37,47-59` | 单线程调度器同时承载全量心跳与全部重试 | F-09、F-11 |
| E-12 | LOCAL | `GXSseEmitterServiceImpl.java:39-41` | 构造函数内 `scheduleAtFixedRate(this::...)`，`this` 逃逸 | F-10 |
| E-13 | LOCAL | `GXSseEmitterServiceImpl.java:339-343,236-241` | `destroy` 只 `shutdown()`；拒绝任务时 `if (!scheduler.isShutdown())` 连日志都不记 | F-10 |
| E-14 | LOCAL | `GXSseEmitterServiceImpl.java:323-337` | 谓词在调用线程执行，异常穿透 | F-22 |
| E-15 | LOCAL | `GXSseMessageInnerReqDto.java:26-27`、`GXSseEmitterServiceImpl.java:293` | 每 DTO 2 次 UUID；分片无序号 | F-12 |
| E-16 | LOCAL | `GXSseEmitterServiceImpl.java:109-112` | 广播在调用线程串行写 N 个连接，`void` 无结果 | F-11 |
| E-17 | LOCAL | `GXSseEmitterServiceImpl.java:83,155,195,278-281` | 连接增删各 1 条 INFO（带 `size()`）；每分片 1 条 DEBUG | F-20 |
| E-18 | LOCAL | 3 个 `package-info.java`、`GXSseEmitterServiceImpl.java:62-67,299-343` | 包级 `@NullUnmarked` 且模块零 `@Nullable` | F-13 |
| E-19 | LOCAL | `GXSseEmitterServiceImpl.java:299-343`、`GXSseEmitterServiceSpringBootTest.java:26` | 心跳/计数/批量关闭/销毁只在实现类，测试被迫向下转型 | F-17 |
| E-20 | LOCAL | `GXSseEmitterServiceImpl.java:28` | `@Service` 恒装配，无 `maple.framework.enable.*` 开关 | F-21 |
| E-21 | DOC | `docs/leaf-base-sse开发手册.md` 2.3/5.1/5.2/6/7 | 静态字段、`removeUser`、`sendHeartbeatToAll`、重载、同步块、"心跳保活"等 9 处漂移 | F-15、F-18 |
| E-22 | LOCAL+DOC | `GXSseEmitterServiceImpl.java:86-89`、手册 372-389 行 | 初始帧 `data` 为 JSON 字符串，前端按 `dataFromServer.clientId` 解析必然走 catch | F-15 |
| E-23 | MEASURED | `GXSseEmitterServiceImplTest.java`（9 条） | 全部在 handler 未初始化下运行，`send` 恒"缓冲成功"；无失败/重试/并发/代理对用例 | F-14 |
| E-24 | LOCAL | `GXSseEmitterServiceImpl.java:91-94` | `GXBusinessException` 只服务一个真实容器中几乎不可达的分支 | F-04 |

## 3. 发现表（F-01 ~ F-23，八要素）

**级别**：H/M/L。**维度**：正确性 / 线程安全 / 性能 / 契约。

| ID | 级别 | 维度 | 位置 | 触发条件 | 影响 | 证据 | 复现 | 回归设计 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | H | 正确性 | 实现:214-218、193-197 | 发送连续失败至重试耗尽 | 连接留缓存外仍占用容器至超时（僵尸连接） | E-01 | `GXSseEmitterAuditReproTest#f01...` | 4 次尝试 + 缓存空 + `send` 抛 `IllegalStateException` |
| F-02 | H | 正确性 | 实现:199-242 | 首投失败后紧接下一次发送 | 客户端收到乱序消息 | E-02、E-03 | `#f02...` | 接收顺序 M1、M2 |
| F-03 | H | 正确性 | 实现:47-59 | 连接存活超过 `timeout` | 心跳不保活，周期性全量重连 | E-04 | 上游源码 + `quickstart` C-04 | 文档如实声明 + 不改变默认值 |
| F-04 | H | 正确性 | 实现:85-94 | 首帧 flush 失败（真实容器） | 缓存残留至超时，异常语义空壳 | E-05、E-24 | `#f04...` 变体 + 真实 HTTP | 端到端用例断言不残留 |
| F-05 | M | 正确性 | 实现:79-95 | 同 clientId 并发重连 | 被替换方返回 500 | E-06 | `#f04...` | 50 线程零异常 + 缓存 1 条 |
| F-06 | M | 正确性 | 实现:47-59、299-316 | 心跳失败 / 显式心跳失败 | 与 F-01 同类僵尸连接 | E-01、E-11 | `#heartbeatFailure...` | 心跳失败后连接可观测为已关闭 |
| F-07 | M | 正确性 | 实现:245-247 | `data == null` | NPE 抛给调用方；调度线程内静默 | E-07 | `#messageWithNullData...` | 不抛异常 + `drops` 递增 |
| F-08 | M | 契约 | 实现:99-107 | 空消息广播 + 无连接 | 告警缺失，掩盖调用方缺陷 | E-08 | 单测 | 两种状态均 WARN |
| F-09 | M | 线程安全 | 实现:36-37、222-235 | 一个慢连接 | 阻塞其它连接的心跳与重试 | E-11 | `#blockingConnection...` | 慢连接不延迟其它连接 |
| F-10 | M | 线程安全 | 实现:39-41、339-343 | bean 创建失败 / 停机 | 线程泄漏、停机丢消息不可观测 | E-12、E-13 | `#destroy_...` | 取消 + 等待 + 销毁后拒绝新建 |
| F-11 | M | 性能 | 实现:109-112、47-59 | 万级连接广播/心跳 | 调用线程被串行写占满 | E-16、E-11 | 单测计时 | 心跳分批并行；广播失败不再原地退避 |
| F-12 | M | 性能 | 实现:290-297 | 每消息/每分片 | 2 次 UUID + 分片无序号 | E-15 | `#splitChunks...` | 分片共享 `msgId` + `chunk=i/n` |
| F-13 | M | 契约 | 实现:62-67、299-343 | 空值入参/返回 | 空值语义不可被 IDE/编译校验 | E-18 | NullAway 构建 | 全部 `@Nullable` 标注 |
| F-14 | M | 契约 | 测试目录 | 修改发送/生命周期代码 | 失败路径无回归保护 | E-23 | 全量测试 | 新增 5 个测试类 |
| F-15 | M | 契约 | 手册 2.3/5.1/5.2/6/7 | 阅读手册 | 按错误前提设计（心跳保活、静态缓存） | E-21、E-22 | `rg` 检查 | 手册零漂移 |
| F-16 | L | 契约 | 实现:264 + 接口:97-100 | `splitMessage` 传大 `length` | 三方语义不一致 | E-10 | 单测 | Javadoc/实现/手册口径统一 |
| F-17 | L | 契约 | 实现:299-343 | 需要心跳/计数 | 只能向下转型 | E-19 | 启动测试 | `getMetrics()` 进接口 + 文档说明 |
| F-18 | L | 契约 | 实现:135-157 | 服务端关闭连接 | 浏览器立即重连，"关闭"失效 | E-21 | 端到端用例 | 文档显式声明限制 |
| F-19 | L | 正确性 | 实现:260 | `splitMessage(" ")` | 与发送侧 blank 语义冲突 | E-09 | `#blankMessage...` | 返回空列表 |
| F-20 | L | 性能 | 实现:83、155、195 | 高连接数churn | INFO 日志量 = 连接数 × 频率 × 2 | E-17 | 日志断言 | 移除/清理降 DEBUG + 指标 |
| F-21 | L | 契约 | 实现:28 | 下游不想要 SSE | 无法关闭，构造即起线程 | E-20 | 静态检查 | 登记偏差（plan.md §宪法 IV） |
| F-22 | L | 正确性 | 实现:323-337 | 谓词抛异常 | 异常穿透 + 部分关闭不可知 | E-14 | 单测 | 异常语义写入 Javadoc |
| F-23 | L | 契约 | 实现:61-67 | 调用方直接用返回的 emitter | 绕开清理与重试 | E-19 | 手册 | Javadoc 标注风险 |

## 4. 四张矩阵

### 4.1 连接终止矩阵

| 终止原因 | 缓存变化 | 是否 `complete()`（改造前） | 是否 `complete()`（改造后） | 客户端行为 |
| --- | --- | --- | --- | --- |
| 正常完成（容器） | 条件删除 | 容器已完成 | 不重复调用 | 流结束，按 `retry` 重连 |
| 超时（绝对超时） | 条件删除 | 容器已完成 | 不重复调用 | 同上 |
| 错误（容器） | 条件删除 | 容器已完成 | 不重复调用 | 同上 |
| 服务端 `closeConnect` | 删除 | 是（`finally`） | 是 | 收到 `event: close` 后重连 |
| 重试耗尽 | 删除 | **否（缺陷）** | 是 | 立即收到流结束并重连 |

### 4.2 并发风险矩阵

| 对象 | 共享范围 | 现状保护 | 收敛路径 |
| --- | --- | --- | --- |
| 连接缓存 | 全实例 | `ConcurrentHashMap` + 条件删除 + 引用相等 | 保持 |
| 调度器 | 全实例 | 单线程串行（头阻塞） | 触发与执行拆分（D-05） |
| `SseEmitter.writeLock` | 单连接 | Spring 内部 `ReentrantLock` | 保持；单连接串行、跨连接并行 |
| 发送管线 | 单连接 | `synchronized` + `AtomicBoolean` | 新增（D-03） |
| 回调 vs 清理 | 单连接 | 条件删除 | 保持（P-01） |

### 4.3 消息顺序契约

| 场景 | 改造前 | 改造后 |
| --- | --- | --- |
| 首投全部成功 | 保序（同步写 + 写锁） | 保序 |
| 首投失败 + 重试 | **乱序**（重试异步） | 保序（失败消息占队首） |
| 分片发送 | 片间无序风险（同上） | 保序 + `msgId` 共享 + `chunk=i/n` |
| 并发广播到同一客户端 | 写锁保帧完整，不保证跨线程先后 | 同左（写入契约） |

### 4.4 性能口径

| 口径 | 改造前 | 改造后 |
| --- | --- | --- |
| 单条消息 UUID 生成 | 2 次（`@Builder.Default` + 显式 `.msgId()`） | 未拆分：1 次（`sendMessageToOneClient` 仅在 `fragments.size() > 1` 时生成，`buildMessageDto` 收到空串并回落到 `@Builder.Default`，可用 `unsplitMessageUsesDtoDefaultMsgId` 复算）；拆分：1 个共享显式值 + 每片 1 次未使用的默认值（DTO 兼容性所限，不消除） |
| 广播调用线程串行写 | N 次（含失败退避 3×400ms/连接） | N 次（成功路径不变）；失败路径 0 次退避 |
| 心跳执行线程 | 1 条（与重试共享） | 触发 1 条 + 每批 256 一任务 |
| 单连接重连周期 | `timeout` + 2000ms | 不变 |
| 1MB 文本拆分事件数 | ≈10000 | ≈10000（增加序号后缀） |

## 5. 既定契约（P-01 ~ P-06）

| ID | 契约 | 保护用例 |
| --- | --- | --- |
| P-01 | 旧连接回调不误删新连接（引用相等 + 条件删除） | `staleCompletionCallback_...`、`staleTimeoutCallback_...`、`staleErrorCallback_...` |
| P-02 | `ConcurrentHashMap` 弱一致遍历不抛 `ConcurrentModificationException` | `sendMessageToAllClient_...` + 并发用例 |
| P-03 | `SseEmitter` 写锁保证帧完整，`complete()` 后 `send` 抛 `IllegalStateException` | 失败/关闭用例 |
| P-04 | 空/缺失输入安全忽略不抛异常 | `sendMessageMethods_Ignore...` |
| P-05 | 拆分不破坏代理对 | `surrogatePairs_AreNeverBroken` |
| P-06 | `closeConnect`/`destroy` 幂等 | `closeConnect_...`、`destroy_IsIdempotent` |

## 6. 上游语义附录（spring-webmvc 7.0.9）

| 事实 | 位置 |
| --- | --- |
| `protected final Lock writeLock = new ReentrantLock();` | `ResponseBodyEmitter.java:93` |
| `send` 持锁 + `Assert.state(!this.complete)` | `ResponseBodyEmitter.java:201-224` |
| 未初始化时缓冲 `earlySendAttempts`，`initialize` 才 flush | `ResponseBodyEmitter.java:123-152` |
| `complete` / `completeWithError` / 三个回调注册均持锁 | `ResponseBodyEmitter.java:275-360` |
| `IOException` 后由容器通知 Spring MVC 完成处理 | `ResponseBodyEmitter.java:179-187` |
| 异步超时取 `emitter.getTimeout()`，写不重置 | `ResponseBodyEmitterReturnValueHandler.java:232` |
| **无** `isComplete()` 公开 API | `ResponseBodyEmitter.java`、`SseEmitter.java` 全量签名核対 |
| `SseEmitter.send(SseEventBuilder)` 为 public 非 final（可覆写做测试替身） | `SseEmitter.java:127` |

## 7. 故障现场（改造前实测）

> 由 `GXSseEmitterAuditReproTest` 在**改造前**运行产生，见本文件 §7.1 的命令与输出。

### 7.1 运行记录

命令：`mvn -o -pl leaf-base-sse test`（仅 `createEmitter` 可测缝已就位，业务逻辑未改动）

```text
[ERROR] Tests run: 3, Failures: 3, Errors: 0, Skipped: 0 -- in cn.maple.sse.service.impl.GXSseEmitterAuditReproTest
f02FailedMessageIsOvertakenByNextMessage:
  same connection must stay ordered ==> expected: <[M1, M2]> but was: <[M2, M1]>
f01RetryExhaustedConnectionIsNotClosed:
  retry exhaustion must close the connection ==> expected: <true> but was: <false>
f05ConcurrentReplaceReturnsEmitterInsteadOfException:
  a replaced handshake must not throw ==> expected: <null> but was:
  <GXBusinessException(msg=Create SSE connection failed, code=500, data={})>
```

同期既有基线保持 `Tests run: 9, Failures: 0`（`GXSseEmitterServiceImplTest`）与 `Tests run: 1, Failures: 0`（`GXSseEmitterServiceSpringBootTest`），证明三条失败来自缺陷本身而非环境。

旧代码日志同时暴露了 F-01 的第二个证据：重试耗尽后仅输出 `Removed SSE connection`，没有任何关闭动作。

### 7.2 改造后运行记录

同一条命令，三条用例全部转绿：

```text
[INFO] Tests run: 3, Failures: 0, Errors: 0 -- in cn.maple.sse.service.impl.GXSseEmitterAuditReproTest
```

关键变化：`retry-exhausted` 分支输出 `SSE message dropped after retries, clientId: f01-client, attempts: 4, msgId: ...`（ERROR，1 条），随后连接被 `complete()`；`f02` 的接收顺序变为 `[M1, M2]`；`f05` 的被替换方不再抛 `GXBusinessException`。

## 8. 修复批次与任务追溯

| 发现 | 批次 | 主要用例类 | 任务 |
| --- | --- | --- | --- |
| F-01、F-06、F-04、F-05 | B1 生命周期 | `GXSseEmitterServiceImplSendFailureTest`、`...LifecycleTest` | T019-T023 |
| F-02、F-12、F-19 | B2 投递语义 | `...SendFailureTest`、`...SplitMessageTest` | T024、T026-T029、T040 |
| F-09、F-10、F-11、F-17 | B3 执行与销毁 | `...ConcurrencyTest`、`...LifecycleTest` | T022、T027-T028、T034 |
| F-20、F-08 | B4 可观测性 | `...MetricsTest` | T031-T036 |
| F-13、F-16、F-18、F-23 | B5 契约与文档 | `...SplitMessageTest`、手册 | T039-T043 |
| F-14、F-15 | B6 测试与文档 | 全部测试类、手册、AGENTS | T037-T043、T047 |
| F-03、F-07、F-21、F-22 | 文档/偏差登记 | `...MetricsTest`、`...LifecycleTest` | T036、T039、T052 |

## 9. PR 素材

**默认值未变清单**：最小超时 60s、`MAX_RETRIES=3`、退避 100/200/400ms、事件名 `Push Msg`、`retry:2000`、拆分上限 100、心跳周期 60s。

**兼容声明**（详见 [plan.md](./plan.md) §兼容性声明）：7 项，其中 4 项为行为变更（移除即关闭、失败后异步有序投递、并发替换不抛 500、`splitMessage(" ")` 返回空列表），3 项为纯增量（`getMetrics()`、`@Nullable`、日志级别）。

**风险与遗留**：宪法 IV 偏差（无 `maple.framework.enable.sse` 开关）已登记于 `contracts/sse-service-contract.md` §5；无断点续传、无跨实例广播能力，属模块边界外。

## 10. 实施结果与偏差登记

### 10.1 测试结果

```text
mvn -o -pl leaf-base-sse clean test
Tests run: 71, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| 测试类 | 用例数 | 覆盖 |
| --- | --- | --- |
| `GXSseEmitterServiceImplTest`（既有 + 4 条新增） | 13 | 空值/边界/替换/回调/拆分钳制/空负载/裸 emitter 绕过指标 |
| `GXSseEmitterServiceImplSplitMessageTest` | 6 | blank/null/长度边界/500→100 钳制/代理对/字符完整性 |
| `GXSseEmitterServiceImplSendFailureTest` | 11 | 重试耗尽关闭、心跳清理、首帧两分语义、保序、分片 msgId、未拆分回落默认 id、广播排队于重试、死连接丢弃、队列超限丢最旧 |
| `GXSseEmitterServiceImplConcurrencyTest` | 6 | 50 线程同 clientId、创建+关闭并发、慢重试隔离（×3 重复）、广播并发 |
| `GXSseEmitterServiceImplLifecycleTest` | 11 | 销毁关闭、取消重试（×3 重复 + drops 断言）、销毁后拒绝新建、销毁后计数、幂等、管线释放、谓词异常穿透 |
| `GXSseEmitterServiceImplMetricsTest` | 9 | 六项指标、default 实现、日志级别、MDC 传播、空广播告警、广播 100 连接串行写 |
| `GXSseEmitterServiceImplHeartbeatTest` | 2 | 定时触发清理失效连接、300 连接跨批次扫描（>256 分片） |
| `GXSseEmitterServiceImplFailureBranchesTest` | 4 | 握手 IOException、关闭事件 IOException、send 抛 RuntimeException、管线关闭后 offer |
| `GXSseEmitterAuditReproTest` | 3 | F-01/F-02/F-05 回归 |
| `GXSseEmitterServiceSpringBootTest` | 2 | 装配 + 指标 + 重连替换 |
| `GXSseEmitterHttpSpringBootTest` | 3 | 真实 HTTP SSE 线格式与关闭、分片帧共享 id + chunk 序号、客户端断开后写失败清理 |
| `GXSseEmitterContextLifecycleSpringBootTest` | 1 | 容器关闭触发 `@PreDestroy`，连接关闭 + 拒绝新建 |

用例数 10 → 71（单调增长，无删除、无跳过）。

### 10.2 与计划的偏差

| 偏差 | 原因 | 影响 |
| --- | --- | --- |
| 日志断言使用 Log4j2 `AbstractAppender` 而非 Logback `ListAppender` | 模块实际日志实现是 `spring-boot-starter-log4j2`（Logback 不在类路径） | 仅测试实现方式，不影响契约 |
| `splitMessage` 的代理对边界改为"能收缩则收缩"（`endIndex--`），仅当片长上限为 1 时扩张 | 原实现会突破 `length` 上限 1 个字符，与"最多 N 字符"契约冲突 | 分片长度更严格；代理对仍不被切断 |
| `drops` 不重复统计"重试耗尽"的消息（由 `failures` 承担） | 避免同一消息被计两次 | 指标语义：`failures` 是 `drops` 的子集语义，已在契约 §1 说明 |
| DTO 的 `@Builder.Default msgId` 保留 | 兼容既有手工 builder 用法（AGENTS §3.14） | 每 DTO 仍会生成一次默认 UUID；分片共享由显式赋值保证 |
| `dispatchMessage`、`cleanupConnections`、`buildMessageDto` 改为包私有 | FR-023 要求可测且禁止反射 | 仅模块内可见，不影响公开契约 |
| T032/T055/T064 的 UUID 口径 | DTO 的 `@Builder.Default msgId` 必须保留（AGENTS §3.14 默认值兼容） | 已收敛：未拆分时不生成额外 UUID（`sendMessageToOneClient` 仅在 `total > 1` 时生成），`unsplitMessageUsesDtoDefaultMsgId` 固化该口径；拆分消息仍每片生成 1 次未使用的默认值，已在 §4.4 如实登记 |

### 10.3 P 序列保护核对（T045）

| 契约 | 保护用例 | 状态 |
| --- | --- | --- |
| P-01 旧回调不误删新连接 | `staleCompletionCallback_...`、`staleTimeoutCallback_...`、`staleErrorCallback_...` | 保留并通过 |
| P-02 并发遍历安全 | `broadcastWhileClosing`/`concurrentCreateAndClose_...` | 新增并通过 |
| P-03 写锁 + 完成后 `send` 抛异常 | `initialSendFailure_WhenStillCurrent_...`、`deadConnection_DropsMessageWithoutRetry` | 通过 |
| P-04 空/缺失输入安全忽略 | `sendMessageMethods_Ignore...`、`messageWithNullData_...` | 保留并通过 |
| P-05 代理对不被破坏 | `surrogatePairs_AreNeverBroken` | 新增并通过 |
| P-06 关闭/销毁幂等 | `closeConnect_...`、`destroy_IsIdempotent` | 保留并通过 |

### 10.4 验证命令

```powershell
mvn -o -pl leaf-base-sse clean test          # 56 / 0 / 0
mvn -o -pl leaf-base-sse test -Dtest=GXSseEmitterHttpSpringBootTest   # 真实 SSE 线格式
rg -n "log\.(info|warn|error|debug)" leaf-base-sse/src/main/java       # 全 ASCII
rg -n "sendHeartbeatToAll|SSE_CLIENT_CACHE|removeUser|1KB|DEFAULT_MIN_TIMEOUT[^_]" docs/leaf-base-sse开发手册.md  # 无命中
```

## 11. 逐修复项的"旧代码失败"证据（FR-021）

| 修复项 | 旧实现位置（修复前） | 旧代码必然失败的断言 | 新用例 |
| --- | --- | --- | --- |
| F-01 重试耗尽关闭 | `retryWithBackoff` 仅 `removeEmitter`（旧 214–218 行） | 关闭后 `emitter.complete()` 未被调用 → `isCompleted()` 为 `false` | `GXSseEmitterAuditReproTest#f01...`（§7.1 有实测失败输出） |
| F-02 保序 | 旧 `send` 失败即 `scheduler.schedule` 异步重试（旧 222–235 行），后续消息立即发出 | 客户端接收序列为 `[M2, M1]` | `GXSseEmitterAuditReproTest#f02...`（§7.1） |
| F-05 并发替换 | 旧 `catch (IOException \| IllegalStateException)` 一律抛 `GXBusinessException`（旧 91–94 行） | 被替换方的握手抛 500 | `GXSseEmitterAuditReproTest#f05...`（§7.1） |
| F-06 心跳失败关闭 | 旧 `cleanupConnections` 只 `removeEmitter`（旧 47–59 行） | 心跳失败后 `isCompleted()` 仍为 `false` | `GXSseEmitterServiceImplSendFailureTest#heartbeatFailure_ClosesConnection`、`#sendHeartbeatFailure_ClosesConnection` |
| F-09 调度隔离 | 旧实现所有重试共用单线程 `scheduler`（旧 36–37 行） | 一个连接的 800ms 阻塞写会让另一连接的重试延迟 ≥800ms | `GXSseEmitterServiceImplConcurrencyTest#slowRetryDoesNotDelayOtherRetries` |
| F-10 销毁语义 | 旧 `destroy` 只 `shutdown()`、无标记（旧 339–343 行） | 销毁后重试仍在跑；销毁后 `createSseConnect` 仍可注册 | `GXSseEmitterServiceImplLifecycleTest#destroy_CancelsQueuedRetriesAndAwaitsTermination`、`#createSseConnect_AfterDestroyThrowsBusinessException` |
| F-13 空值契约 | 旧 `buildEvent` 用 `Objects.requireNonNull`（旧 245–247 行） | `data == null` 时抛 `NullPointerException` | `GXSseEmitterServiceImplTest#messageWithNullData_IsDroppedWithoutThrowing` |
| F-19 blank 拆分 | 旧 `splitMessage` 用 `isEmpty`（旧 260 行） | `splitMessage(" ", 10)` 返回 1 段 | `GXSseEmitterServiceImplSplitMessageTest#blankMessage_ReturnsEmptyList` |
| F-20 日志级别 | 旧 `removeEmitter` 用 INFO（旧 195 行） | INFO 日志中出现 `Removed SSE connection` | `GXSseEmitterServiceImplMetricsTest#logLevelIsInfoOnlyForLifecycle`（Log4j2 断言） |

> 说明：F-01/F-02/F-05 的失败输出为实测（§7.1）；其余各项的旧代码在修复中被替换，无法再执行，故以"旧实现代码位置 + 该实现必然违反的断言"作为等价证据，并给出当前断言该性质的新用例。本轮收敛同时补齐了 F-06/F-09/F-10/F-19/F-20 此前缺失的可执行用例。

## 12. 文件 → 判定覆盖表（FR-001）

| 文件 | 判定 | 主要证据 / 说明 |
| --- | --- | --- |
| `service/GXSseEmitterService.java` | 缺陷（已修复） | 空值契约缺失（E-18）、辅助能力不进接口（E-19）、Javadoc 与语义不符（E-21） |
| `service/GXSseMetrics.java` | 新增，无缺陷 | 六项指标快照，`empty()` 供接口 default |
| `service/impl/GXSseEmitterServiceImpl.java` | 缺陷（已修复） | F-01~F-12、F-16、F-19~F-22 的主要承载文件 |
| `service/impl/GXSseClientSendPipeline.java` | 新增，无缺陷 | 单连接 FIFO + drain 所有权；队列超限丢最旧（data-model B-2） |
| `dto/GXSseMessageInnerReqDto.java` | 风险（已收敛） | `msgId` 默认值不可移除（兼容），`data` 空值语义改为"丢弃并计数" |
| `dto/package-info.java` | 符合现状约定 | 包级 `@NullUnmarked`（宪法 V 允许），模块内以 `@Nullable` 补足 |
| `service/package-info.java` | 符合现状约定 | 同上 |
| `service/impl/package-info.java` | 符合现状约定 | 同上 |
| `test/GXSseEmitterServiceImplTest.java`（原 9 条） | 覆盖不足（已补强） | 原用例均在 handler 未初始化下运行，`send` 恒"缓冲成功"（E-23） |
| `test/GXSseEmitterServiceSpringBootTest.java`（原 1 条） | 覆盖不足（已补强） | 原仅断言 Bean 装配，未覆盖指标与重连替换 |

## 13. 测试补强（用户追加要求，2026-10-23）

在 `/speckit.converge` 判定收敛之后，按"测试是否全面"的复核结论补了一轮，新增 3 个测试类 + 4 条用例 + 2 处重复运行，用例数 57 → 71。

### 13.1 本轮新增

| 缺口 | 用例 | 文件 |
| --- | --- | --- |
| 定时心跳从未被测试执行 | `heartbeatTriggerRemovesInvalidConnections`、`heartbeatTriggerScansMoreThanOneBatch`（300 连接 × 256 分片） | `GXSseEmitterServiceImplHeartbeatTest` |
| `@PreDestroy` 从未被容器触发 | `containerShutdownClosesConnectionsAndRejectsNewOnes`（`SpringApplicationBuilder(WebApplicationType.NONE)` 自建上下文并 `close()`） | `GXSseEmitterContextLifecycleSpringBootTest` |
| 客户端真实断开后的清理 | `clientDisconnectCleansUpOnNextWrite`（关闭 socket → 写失败 → 清理） | `GXSseEmitterHttpSpringBootTest` |
| 分片线格式未验证 | `splitFramesShareIdAndCarryChunkIndex`（`id:` 共享 + `chunk=1/` 注释行） | `GXSseEmitterHttpSpringBootTest` |
| 异常分支无入口 | 握手 `IOException`、关闭事件 `IOException`、`send` 抛 `RuntimeException`、管线关闭后 `offer` | `GXSseEmitterServiceImplFailureBranchesTest` |
| 广播与有序管线交互 | `broadcastQueuesBehindPendingRetry` | `GXSseEmitterServiceImplSendFailureTest` |
| 中断重试的计数 | `destroy_CancelsQueuedRetriesAndAwaitsTermination` 增加 `drops ≥ 1` 断言 | `GXSseEmitterServiceImplLifecycleTest` |
| 时序用例的稳定性 | `@RepeatedTest(3)` × 2 | `...ConcurrencyTest`、`...LifecycleTest` |

### 13.2 覆盖率度量（受限于离线仓库）

JaCoCo / PIT 未接入：本地 Maven 仓库无 `org.jacoco:jacoco-maven-plugin`，且镜像元数据请求失败（离线），无法下载。故未加覆盖率门禁插件，避免破坏 `mvn -o` 离线构建。当前以"手工分支清单"代替：

- **已覆盖**：握手/关闭事件/发送三类 `IOException`、`IllegalStateException`（已 `complete`）、`RuntimeException`、`offer` 的 `STARTED/QUEUED/CLOSED`、重试 `sleep` 的中断、销毁标记、`triggerHeartbeat` 分片主路径。
- **仍未覆盖（仅防御性分支，无法从公开 API 到达）**：`triggerHeartbeat`/`submitDrain` 的 `RejectedExecutionException`（`destroy` 先置 `destroyed` 标志，二者不可达）、`shutdownExecutors` 的 `awaitTermination` 超时 WARN 与 `InterruptedException`（需一个忽略中断且 >5s 的任务，会拖慢套件）。已按"死防御代码"在代码内保留，不为此造不可达测试。
- 若后续接入网络或升级仓库，可在 `leaf-base-sse/pom.xml` 增配 `jacoco-maven-plugin`（`prepare-agent` + `report` + `check`，对 `GXSseEmitterServiceImpl` 设行覆盖门禁，建议 75%+）后据实测校准阈值。
