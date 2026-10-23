# Implementation Plan: leaf-base-sse 正确性、线程安全与性能审查

**Branch**: `011-sse-module-audit`（`before_plan` 未注册 hook，未创建 git 分支） | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-sse-module-audit/spec.md`

## Summary

把 23 条审查发现（高 4 / 中 11 / 低 8）与 6 条既定契约（P-01 ~ P-06）落成"可实测、可回归、可迁移"的交付：**审查报告 + 连接生命周期闭环 + 投递顺序与重试语义 + 执行器职责拆分 + 可观测性 + 空值契约与文档重写 + 测试补强**。

技术路径（Phase 0 已逐条确认，详见 [research.md](./research.md)）：

1. **移除即关闭**（F-01、F-06，FR-014）：新增内部入口 `closeAndRemove(clientId, emitter, reason)`，先条件删除（`remove(key, value)` 成功）再 `complete()`；心跳失败、显式心跳失败、重试耗尽三条路径统一走它。回调路径（`onCompletion`/`onTimeout`/`onError`）仍只做条件删除，避免对容器已完成的连接重复结束。
2. **投递有序**（F-02、F-12，FR-008）：新增包私有 `GXSseClientSendPipeline`，**只在首次发送失败时启用**——失败消息进入该 client 的有序队列，后续消息按 FIFO 跟队，由共享虚拟线程执行器串行 drain；成功路径保持同步直发，默认行为不变。分片消息在 `comment` 中补分片序号（`/总片数`），消除"无法重组"。
3. **执行职责拆分**（F-09、F-10、F-11、F-17，FR-016、FR-017）：`ScheduledExecutorService` 退化**纯触发器**（只负责按周期提交任务，不做 I/O）；心跳批次、重试 drain 交给 `Executors.newVirtualThreadPerTaskExecutor()`；调度器启动从构造函数移到 `@PostConstruct`；`destroy` 置销毁标记 + `shutdownNow` + `awaitTermination(5s)` + 清空管线。
4. **并发替换语义收敛**（F-05）：首帧发送失败时区分"已被更新连接替换"（不抛异常，交给容器结束）与"真实失败"（`removeEmitter` + `GXBusinessException`），消除并发重连时被替换方 500。
5. **可观测性**（F-20，FR-024）：新增 `GXSseMetrics`（不可变快照）与 `LongAdder` 计数，接口以 `default` 方法暴露；连接"移除/清理/耗尽"日志降为 DEBUG，创建/关闭保持 INFO（每连接每次生命周期 ≤ 2 条 INFO）。
6. **空值与契约**（F-13、F-16、F-17、F-19）：`@Nullable` 标注、`data` 为空改为"记录 + 丢弃 + 计数"、`splitMessage` 统一 blank 语义、接口新增 default 方法、Javadoc 与手册按现状重写。
7. **测试补强**（F-14，FR-020 ~ FR-023）：新增可覆盖的 emitter 工厂 `protected SseEmitter createEmitter(long)`，据此构造失败/慢速/已关闭连接；新增 4 个测试类 + 1 个真实 HTTP 端点端到端测试，既有 9 条断言全部保留。

## Technical Context

**Language/Version**: Java 21（根 POM `<release>21</release>`；`maven-compiler-plugin` + ErrorProne（`NullAway:ERROR`、`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`））；实测运行 JDK 25.0.4

**Primary Dependencies**: Spring Boot 4.1.1 / Spring Framework 7.0.9（`spring-webmvc` 的 `SseEmitter`/`ResponseBodyEmitter`，`spring-boot-starter-tomcat` 11.0.24 供真实 SSE 测试）；`leaf-base-framework 4.3.0-SNAPSHOT`（`GXBusinessException`、`GXBusinessServiceImpl`）；Hutool 5.8.47（`IdUtil`/`Dict`/`CharSequenceUtil`/`RandomUtil`，经框架传递）；jspecify 1.0.1；Lombok。**零新增依赖**

**Storage**: N/A（库模块，无持久化；仅 JVM 内 `ConcurrentHashMap` 连接缓存与发送管线）

**Testing**: JUnit 5（Jupiter）+ `spring-boot-starter-test`；`@SpringBootTest(webEnvironment = RANDOM_PORT)` 真实 HTTP SSE 读取；并发用 `ExecutorService`/`CountDownLatch`；失败/慢速连接用 `SseEmitter` 子类覆盖 `send(SseEventBuilder)`（7.0.9 该方法为 public 非 final）；无外部中间件，全部离线可跑

**Target Platform**: JDK 21 库（jar，随 `leaf-framework` 聚合门面下发）；Windows（CRLF）与 Linux（LF）结果一致

**Project Type**: library（Maven 多模块仓库中的单个 `leaf-base-*` 模块）

**Performance Goals**: 不设吞吐/时延验收门禁（SC-005）。只保留可复算口径：单消息 UUID 次数由 2 降为 1；广播调用线程串行写次数**保持 N**（成功路径不改），但失败路径不再在调用线程退避（单次广播的额外阻塞由最坏 N×700ms 降为 0）；心跳批次写不再占用触发线程；单连接重连周期 = `timeout` + `retry`（`retry:2000` 不变）；1MB 文本拆分事件数 ≈ 10000（不改变，但补分片序号）。**工程目标（非门禁）**：既有 10 条用例墙钟 ≤ 5s，新增用例总数 ≤ 25 且单类 ≤ 3s

**Constraints**: 只改 `leaf-base-sse` 模块 + 2 处文档（`docs/leaf-base-sse开发手册.md`、`leaf-base-sse/AGENTS.md`）；根 POM 与其它模块不改；不新增依赖、不新增配置键；默认值（最小超时 60s、重试 3 次、退避基数 100ms、事件名 `Push Msg`、`retry:2000`、拆分上限 100）全部保持不变；ErrorProne + NullAway 零错误；日志全部 ASCII

**Scale/Scope**: 6 个生产源码文件 + 2 个测试类；23 条发现（高 4 / 中 11 / 低 8）；新增 2 个生产类型（`GXSseMetrics`、`GXSseClientSendPipeline`）+ 修改 4 个生产文件；新增 4 个测试类 + 修改 2 个测试类；重写 1 份手册 + 修改 `AGENTS.md`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 / 约束 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 仍只依赖 `leaf-base-framework`；保持 `@Service` + 组件扫描，不迁自动装配清单；不新增可选中间件；`Executors.newVirtualThreadPerTaskExecutor()` 为 JDK 能力，零依赖 |
| II. 稳定契约优先 | PASS（附兼容声明） | 公开方法签名不改（接口仅**新增** `default getMetrics()`）；默认值全部不变（FR-018）；两处语义变更按兼容声明处理：`splitMessage(" ")` 由 1 段变 0 段、并发重连时被替换方不再抛 500 |
| III. 统一响应与统一异常 | PASS | `createSseConnect` 在已销毁或真实首帧失败时抛 `GXBusinessException`（沿用既有形状）；不新增异常类型；`GXSseMetrics` 为库内快照，不作为 HTTP 响应体 |
| IV. 显式配置与双配置源 | PASS（含已知偏差） | 本计划**不新增任何配置键**（FR-018 锁定默认值）；不引入 `maple.framework.enable.sse` 开关：该模块是既有常开能力，新增开关默认关闭会破坏下游装配，偏差登记于 [contracts/sse-service-contract.md](./contracts/sse-service-contract.md) §5 |
| V. 空值安全与上下文纪律 | PASS | 新增/修改 API 在 `@NullUnmarked` 包内对可空参数/返回值标注 `@Nullable`（FR-013）；所有持有资源的组件（连接缓存、两个执行器、发送管线）在 `destroy` 中有明确释放路径；不裸 `new Thread`（用虚拟线程执行器） |
| VI. 测试与静态检查门槛 | PASS | 每个修复项先给"旧代码失败"用例（FR-021）；新增 `@SpringBootTest` 真实 SSE 端点用例（FR-020）；并发/生命周期用例离线可跑；不改任何编译参数 |
| VII. 命名与包结构一致性 | PASS | 新增 `GXSseMetrics`（`GX<概念>` 快照类型，后缀表未覆盖"指标快照"，理由见 Complexity Tracking）、`GXSseClientSendPipeline`（包私有实现细节）；不重命名历史类型；日志 ASCII |
| 附加约束 2/3（依赖治理） | PASS | 零新增依赖；有序队列用 `ArrayDeque` + `AtomicBoolean` 实现，不引入 Disruptor/Reactor |

**Post-Phase 1 复评**：`contracts/` 三份契约（服务面、投递语义、可观测性）与 `data-model.md` 未引入新的原则冲突；`GXSseMetrics` 与 `GXSseClientSendPipeline` 的必要性已在 `Complexity Tracking` 说明；复评结论 **PASS**。

**兼容性声明（非违反项，需在 PR 与手册中体现）**：

| 变更 | 影响面 | 迁移路径 |
| --- | --- | --- |
| 重试耗尽/心跳失败从"只删缓存"改为"删缓存 + `complete()`" | 依赖"连接仍可被后续 send 复活"的下游（事实上不可能，属缺陷修复） | 无需迁移；客户端会立即收到流结束并按 `retry` 重连 |
| 首次发送失败后，后续消息改为按 FIFO 排队异步投递 | 依赖"`sendMessageToOneClient` 返回即已写入"的下游 | 该语义从未被契约承诺（`void` 无返回值）；如需确认投递结果改用 `getMetrics()` 的成功/失败计数 |
| 并发同 clientId 注册时，被替换方返回 emitter 而非抛 500 | 依赖 500 做重试判定的下游 | 该分支属竞态修复；如需区分，读 `getMetrics()` 的 `droppedConnections` |
| `splitMessage(" ")` 返回空列表 | 直接调用该方法并依赖返回 1 段的下游 | 与 `sendMessageToOneClient` 的 blank 拒绝语义对齐；如需发送纯空白，用 `"\u00A0"` 等实际内容 |
| 接口新增 `default getMetrics()` + 新增 `@Nullable` 标注 | 自定义实现 `GXSseEmitterService` 的下游 | default 方法无需实现；`@Nullable` 仅注解，二进制兼容 |
| 连接"移除/清理/耗尽"日志 INFO → DEBUG | 依赖日志统计连接移除的下游运维 | 改用 `getMetrics()`；连接创建/关闭仍为 INFO |
| 分片消息 `comment` 增加 `:chunk=1/3` 等序号 | 依赖 `comment` 精确值（`":clientId"`）的下游 | 规范未定义 comment 语义；如需原值，从服务端模板回退 |

## Project Structure

### Documentation (this feature)

```text
specs/011-sse-module-audit/
├── plan.md                          # 本文件
├── research.md                      # Phase 0：D-01 ~ D-12 决策 + R-01 ~ R-08 事实核对
├── data-model.md                    # Phase 1：发现/证据/矩阵/指标/管线实体
├── quickstart.md                    # Phase 1：可执行验证指南
├── contracts/
│   ├── sse-service-contract.md      # 服务面契约（方法、空值、销毁、指标、兼容声明）
│   ├── sse-delivery-contract.md     # 投递语义契约（顺序、重试、丢弃、关闭、事件线格式）
│   └── sse-observability-contract.md# 指标名、日志事件与级别、禁止事项
├── audit-report.md                  # 实施期产出：审查报告（与 spec 摘要互引，FR-011）
└── checklists/requirements.md       # 规格质量清单（16/16 通过）
```

### Source Code (repository root)

```text
leaf-base-sse/
├── pom.xml                                                   # 不改（零新增依赖）
└── src/
    ├── main/java/cn/maple/sse/
    │   ├── dto/
    │   │   ├── GXSseMessageInnerReqDto.java                  # 改：data/message @Nullable；msgId 默认值只生成一次
    │   │   └── package-info.java                              # 保持（@NullUnmarked）
    │   ├── service/
    │   │   ├── GXSseEmitterService.java                       # 改：Javadoc 语义修正 + @Nullable + default getMetrics()
    │   │   ├── GXSseMetrics.java                              # 新增：record 快照（attempts/successes/failures/drops/retries/active）
    │   │   └── package-info.java                              # 保持
    │   └── service/impl/
    │       ├── GXSseEmitterServiceImpl.java                    # 改：移除即关闭、有序管线、执行器拆分、销毁标记、指标、日志级别、createEmitter 工厂
    │       ├── GXSseClientSendPipeline.java                    # 新增（包私有）：单连接 FIFO 队列 + 单次 drain 保证
    │       └── package-info.java                               # 保持
    └── test/java/cn/maple/sse/
        ├── service/
        │   ├── GXSseEmitterServiceSpringBootTest.java          # 改：装配 + default getMetrics + 重连替换
        │   └── GXSseEmitterHttpSpringBootTest.java             # 新增：RANDOM_PORT 真实 SSE（event/id/retry/data/close 行）
        └── impl/
            ├── GXSseEmitterServiceImplTest.java                # 改：保留 9 条 + 空值/边界/首帧失败/指标
            ├── GXSseEmitterServiceImplSendFailureTest.java     # 新增：重试、耗尽关闭、保序、IllegalState、data 为空
            ├── GXSseEmitterServiceImplConcurrencyTest.java     # 新增：50 线程同 clientId、广播+关闭、慢连接隔离
            ├── GXSseEmitterServiceImplLifecycleTest.java       # 新增：@PostConstruct、destroy 取消/等待、销毁后创建
            └── GXSseEmitterServiceImplSplitMessageTest.java    # 新增：代理对、length 边界、blank

docs/leaf-base-sse开发手册.md                                  # 重写 2.x/5.x/6.x：生命周期真相、顺序与重试、可观测性
leaf-base-sse/AGENTS.md                                        # 改：§3 补"移除即关闭/保序/销毁"红线，§4 补测试清单
```

**Structure Decision**: 保持"单模块 + 包内分层"现状，不新增子工程、不新增配置资源。生产侧新增 2 个类型（`GXSseMetrics` 对外快照、`GXSseClientSendPipeline` 包私有实现）并修改 4 个既有类型；测试侧新增 4 个类、修改 2 个类；文档改 2 份。`pom.xml`、根 POM、其它模块**不改**。

## Phase 0 → Phase 1 结论摘要

- **R-01（发送失败的真实语义）**：`ResponseBodyEmitter.send` 的 Javadoc 明确 `IOException` 后无需自行 `completeWithError`，容器会产生通知并由 Spring MVC 完成处理（`ResponseBodyEmitter.java:179–187`）；该通知触发 `errorCallback` → 本模块 `removeEmitter`。因此"发送失败后靠退避重试救回消息"在真实容器中不成立，重试只应作为"尚未被容器判死"的兜底，且必须与移除逻辑串行。
- **R-02（无完成态查询）**：7.0.9 的 `ResponseBodyEmitter` **没有公开 `isComplete()`**（仅有 `send`/`complete`/`completeWithError`），判断"连接是否已结束"只能通过 `send` 抛出的 `IllegalStateException` 捕获；故重试前无法预先探测，必须在发送失败时区分异常类型：`IllegalStateException` → 放弃重试并丢弃，`IOException` → 允许有限重试。
- **R-03（写锁粒度）**：`send`/`complete`/`onTimeout`/`onError`/`onCompletion` 均持 `ReentrantLock writeLock`（`ResponseBodyEmitter.java:93、201–224、275–360`），"移除即关闭"与"正在发送"不会互相破坏帧结构；但**同一 emitter 上的阻塞写会阻塞同连接的其它写**，这是单连接必须串行、跨连接必须并行的直接依据。
- **R-04（超时绝对值）**：`ResponseBodyEmitterReturnValueHandler` 用 `new DeferredResult<>(emitter.getTimeout())`（第 232 行）建立异步超时，写操作不重置它；心跳只能探测活性，不能保活（F-03 成立，文档必须改写）。
- **R-05（首帧缓冲）**：handler 未初始化时 `send` 进入 `earlySendAttempts`（`ResponseBodyEmitter.java:201–224`），flush 发生在 `initialize`（第 123–152 行），失败由 `handleReturnValue` 抛出——本模块的 `catch` 只覆盖"已结束/已被替换"两类，无法覆盖真实网络失败（F-04 成立）。
- **R-06（虚线程与定时器）**：JDK 对 `ScheduledExecutorService` 使用虚拟线程无收益（任务本身不阻塞时才该用），故方案采用"调度器只做触发 + 虚拟线程按任务执行"的拆分，避免单线程同时承担 I/O（F-09 成立）。
- **R-07（可测缝）**：`SseEmitter.send(SseEventBuilder)` 在 7.0.9 为 public 非 final（`SseEmitter.java:127`），可被子类覆盖抛异常/阻塞；`createSseConnect` 内部 `new SseEmitter(...)` 使子类无法注入，故新增 `protected SseEmitter createEmitter(long timeoutMillis)` 作为唯一创建点（FR-023）。
- **R-08（既有断言清单）**：9 条实现类用例中，`createSseConnect_WhenClientIdIsBlank...`（最小超时 60s）、`createSseConnect_WhenClientIdAlreadyExists...`（替换）、三条 `staleXxxCallback_...`（P-01）、`splitMessage_Clamps...`（钳制区间）、`sendMessageMethods_Ignore...`（P-04）、`closeConnect_...`/`destroy_...`（P-06）必须在改造后继续通过。
- 其余设计决策（移除即关闭入口、有序管线、执行器拆分、销毁语义、指标、空值、文档）见 [research.md](./research.md)。

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 新增 `GXSseClientSendPipeline`（单连接 FIFO 队列 + drain 串行） | FR-008 要求"首投失败 + 重试"场景保序；现状异步重试会让 M2 越过 M1 | 改为"失败即关闭、不重试"会删除既有重试能力（Clarifications 否决）；改为同步 `Thread.sleep` 退避会阻塞调用线程，广播时最坏 N × 700ms |
| 新增 `GXSseMetrics` 快照类型 | FR-024 要求 6 项可获取口径，且不能靠高频 INFO 日志承载 | 返回 `Map<String, Long>` 违反宪法 III 的"禁止裸 Map"风格；新增 6 个 getter 方法会污染服务接口；复用 Micrometer 需新增依赖 |
| 执行器拆分为"调度器 + 虚拟线程任务执行器" | FR-016 要求单慢连接不得阻塞其它连接的心跳与重试 | 单线程调度器同时做 I/O 会把头阻塞固化（现状缺陷）；用固定大小线程池需设定并发度且可能耗尽，虚拟线程按任务创建更贴合 SSE 的 I/O 密集特征 |
| `protected createEmitter(long)` 测试缝 | FR-023 禁止反射构造失败现场，而当前创建点不可注入 | 反射改私有缓存只能测"已注册之后"的行为，无法测首帧失败；改为构造器注入 `Supplier<SseEmitter>` 会改变 `@Service` 无参构造契约 |
| 分片 `comment` 增加序号后缀 | 客户端无法判断分片归属与总片数，也无法在保序修复后校验完整性 | 新增独立事件类型会改变线格式（破坏性）；放入 `data` 会改变用户负载结构 |

## Phase 2 预告

`/speckit.tasks` 将按"测试先行"拆分为 6 个批次：B1 生命周期闭环（F-01/F-06/F-04/F-05）→ B2 投递语义（F-02/F-12/F-19）→ B3 执行与销毁（F-09/F-10/F-11/F-17）→ B4 可观测性（F-20）→ B5 空值与接口（F-13/F-16/F-17）→ B6 文档与端到端（F-14/F-15），每批次内顺序为"先用例（旧代码失败）→ 最小实现 → `mvn -pl leaf-base-sse test` 全绿"。
