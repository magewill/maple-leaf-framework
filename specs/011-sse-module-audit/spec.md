# Feature Specification: leaf-base-sse 正确性、线程安全与性能审查

**Feature Branch**: `011-sse-module-audit`（本次未通过 hook 创建 git 分支；`before_specify` 无 hook 注册）

**Created**: 2026-10-23

**Status**: Draft

**Input**: User description: "审查 @e:\project\java\maple-leaf-framework\leaf-base-sse 模块的正确性、线程安全性、性能。你在修改完代码之后，一定做完备的测试。"

## 背景与审查结论摘要

本规格把"`leaf-base-sse` 现有实现是否正确、是否线程安全、是否存在性能结构问题"转化为可验收的交付要求，并把"改动后必须做完备测试"固化为硬门槛。审查对象与证据来源：

1. `leaf-base-sse` 全部 6 个生产源码文件（3 个 `package-info`、1 个 DTO、1 个接口、1 个实现）与 2 个既有测试类（共 10 条用例）；
2. 契约来源：`leaf-base-sse/AGENTS.md`、`docs/leaf-base-sse开发手册.md`、`.specify/memory/constitution.md`（重点为 II 稳定契约、IV 显式配置、V 空值安全与资源释放、VI 测试门槛、VII 命名后缀表）；
3. 上游语义核对：直接读取本地仓库 `spring-webmvc-7.0.9-sources.jar` 的 `ResponseBodyEmitter`、`ResponseBodyEmitterReturnValueHandler`、`SseEmitter` 源码逐条比对（`send` 的加锁方式、`complete` 后的异常语义、`earlySendAttempts` 缓冲、`DeferredResult` 超时口径）；
4. 依赖基线：`leaf-base-sse` 仅依赖 `leaf-base-framework` + `spring-boot-starter-test`（`hutool-core` 等经框架传递），实测 JDK 25.0.4 / Spring Boot 4.1.1 / spring-web 7.0.9 / jspecify 1.0.1；
5. 既有测试实测基线：`mvn -o -pl leaf-base-sse test` → **Tests run: 10, Failures: 0, Errors: 0**（`GXSseEmitterServiceImplTest` 9 条 0.036s + `GXSseEmitterServiceSpringBootTest` 1 条 2.417s）；
6. 行为探针实测（临时用例，运行后已删除）：重试耗尽后 `attempts=4 / activeCount=0 / completed=false`；`splitMessage(" ", 10)` 返回 1 段；`closeConnections` 的谓词抛异常时异常穿透到调用方。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 连接建立、替换、广播、关闭的主链路自洽，旧连接回调不会误删新连接；但**重试耗尽后连接被移除却未 complete**、**重试对 `IOException` 基本无效且破坏消息顺序**、**心跳不能延展连接寿命**、**首帧发送失败路径在真实容器中不可达** | 存在高风险缺陷 |
| 线程安全 | 共享连接缓存、条件删除、Spring 侧写锁三处支撑了"无数据竞争"；风险集中在**单线程调度器同时承担全量心跳与全部重试（头阻塞）**、**构造期 `this` 逃逸**、**销毁不等待/不取消/不置标记** | 无致命竞态，需补约束 |
| 性能 | 每条消息固定付出 2 个 UUID + DTO + `Dict` + 事件构建；广播与心跳都在单线程上串行写全部连接；重连周期由 `retry:2000` + 绝对超时决定，形成周期性全量重连 | 存在结构性上限，非阻断 |

关键证据（均可按位置复核）：

- **E-01**：`retryWithBackoff` 在 `retryCount > MAX_RETRIES` 时只调用 `removeEmitter`（第 214–218 行），`removeEmitter`（第 193–197 行）只做 `Map.remove`，**从不 `complete()`**。探针实测：1 次首投 + 3 次重试后 `activeCount=0` 且 `completed=false`。
- **E-02**：`send` 只捕获 `IOException | IllegalStateException`（第 206 行）；上游 `ResponseBodyEmitter.send` 的 Javadoc 明确"`IOException` 后不需要自行 `completeWithError`，容器会产生通知并由 Spring MVC 完成处理"（`ResponseBodyEmitter.java:179–187`），该通知会触发本模块 `onErrorCallBack` → `removeEmitter`，与异步重试构成竞态。
- **E-03**：`retryWithBackoff` 通过 `scheduler.schedule(...)` 异步重试（第 222–235 行），`send` 立即返回；分片发送（第 129–132 行）在任一分片失败后仍继续发送后续分片 → 客户端可能先收到第 N+1 片再收到第 N 片的重试。
- **E-04**：`ResponseBodyEmitterReturnValueHandler.handleReturnValue` 用 `new DeferredResult<>(emitter.getTimeout())`（第 232 行）；写操作不重置该超时，故 `cleanupConnections` 每分钟一次的心跳（第 47–59 行）**不能延展连接寿命**，连接仍在 `timeout` 秒后被终止。
- **E-05**：`createSseConnect` 的首帧发送发生在 handler 初始化之前（第 85–94 行），此时 `send` 走 `earlySendAttempts` 缓冲（`ResponseBodyEmitter.java:201–224`）；真正的 flush 在 `initialize`（第 123–152 行），失败由 `handleReturnValue` 抛出，**不会进入本模块的 `catch → removeEmitter`**。
- **E-06**：`createSseConnect` 的 `put`（第 79 行）与 `oldEmitter.complete()`（第 80–82 行）非原子；同 clientId 并发重连时，被替换方的首帧 `send` 抛 `IllegalStateException` → `GXBusinessException` 抛给调用方，且 `remove(clientId, 旧 emitter)` 为空操作。
- **E-07**：`buildEvent` 内 `Objects.requireNonNull(messageDto.getData(), ...)`（第 245–247 行）不在 `send` 的捕获范围内 → NPE 直接抛给调用方；在重试任务线程内抛出则被 `ScheduledFuture` 静默吞掉（无日志）。
- **E-08**：`sendMessageToAllClient` 先判 `sseClientCache.isEmpty()`（第 99–102 行）再判 `msg` 空（第 104–107 行）→ 无连接时空消息静默返回，有连接时 WARN。
- **E-09**：`splitMessage` 用 `CharSequenceUtil.isEmpty`（第 260 行）→ `" "` 被判为有效消息并返回 1 段（探针实测），与 `sendMessageToOneClient` 的 blank 拒绝语义不一致。
- **E-10**：`splitMessage` 把 `length` 钳制到 `[1, 100]`（第 264 行），接口 Javadoc 写的是 "requested maximum fragment length"（`GXSseEmitterService.java:97–100`），手册写的是"默认 1KB 到 2KB"（手册 5.1 节）；三处口径不同。
- **E-11**：`scheduler` 是**单线程** `ScheduledExecutorService`（第 36–37 行，`Thread.ofVirtual()` 工厂），同时承载每分钟全量心跳与全部重试；一次慢客户端的阻塞写会串行阻塞其它客户端的心跳与重试。
- **E-12**：构造函数里 `scheduler.scheduleAtFixedRate(this::cleanupConnections, 1, 1, MINUTES)`（第 39–41 行）在对象未构造完成时发布 `this`；bean 后续创建失败时调度线程已启动且无释放路径。
- **E-13**：`destroy` 只做 `keySet().forEach(closeConnect)` + `scheduler.shutdown()`（第 339–343 行）：不 `awaitTermination`、不取消已入队重试、不置"已销毁"标记；销毁后再 `createSseConnect` 仍可注册连接，重试被拒绝时因 `if (!scheduler.isShutdown())` 连日志都不记（第 236–241 行）。
- **E-14**：`closeConnections`（第 323–337 行）在调用线程执行用户谓词，谓词抛异常直接穿透（探针实测 `IllegalStateException`），且已关闭数量与断言不匹配时调用方无法感知部分完成。
- **E-15**：`buildMessageDto` 每个分片生成 **2 个 UUID**（`@Builder.Default msgId = IdUtil.fastUUID()`（DTO 第 26–27 行）+ 显式 `.msgId(IdUtil.fastUUID())`（第 293 行）），且分片之间没有序号，客户端无法判断一条消息是否被拆开。
- **E-16**：`sendMessageToAllClient` 在调用线程串行遍历并写所有连接（第 109–112 行），总耗时 ≈ N × 单次写延迟，无异步扇出、无单连接失败隔离、无部分失败统计；`void` 返回值不暴露投递结果。
- **E-17**：日志成本：每次创建/关闭/移除连接各 1 条 INFO 并带 `sseClientCache.size()`（第 83、155、195 行）；重试日志无 `isEnabled` 守卫；`splitMessage` 每个分片 1 条 DEBUG（第 278–281 行）。
- **E-18**：三个 `package-info.java` 均为 `@NullUnmarked`（宪法的现状约定），但模块内**没有任何 `@Nullable`**：`getSseEmitterByClientId` 返回 `null`（第 62–67 行）、`sendHeartbeat(null)` 与 `closeConnections(null)` 被显式支持、`GXSseMessageInnerReqDto.data` 可空却在 `buildEvent` 处 NPE。
- **E-19**：`sendHeartbeat`、`getActiveConnectionCount`、`closeConnections`、`destroy` 只在实现类公开（第 299–343 行），接口不含；`GXSseEmitterServiceSpringBootTest` 只能 `((GXSseEmitterServiceImpl) sseEmitterService).getActiveConnectionCount()`，手册 3.6 节亦自述该缺口。
- **E-20**：`@Service` 恒装配（第 28 行），无 `maple.framework.enable.<feature>` 开关，构造即启动调度线程；与宪法 IV 的"功能开关默认关闭"不一致。
- **E-21**：手册与实现漂移：静态 `SSE_CLIENT_CACHE`/`SCHEDULER`（实现为实例字段）、`removeUser`（实现为 `removeEmitter`）、`sendHeartbeatToAll`、`sendMessageToAllClient(msg, true)` 重载、"`closeConnect` 中的同步块"、"复用消息构建器"、"拆分 1KB–2KB"、"心跳间隔小于超时即可保持连接"（被 E-04 证伪）、"错误回调会发送恢复提示"均与现状不符。
- **E-22**：初始帧 `id=201`、`data` 为 JSON 序列化后的字符串（第 86–89 行）；手册前端示例按 `dataFromServer.clientId` 解析（手册第 372–389 行）必然走 catch 回落分支。
- **E-23**：当前用例集中 9 条实现类用例全部在 handler 未初始化状态下运行，`send` 永远"缓冲成功"，**发送失败、重试、耗尽、心跳清理四条路径零覆盖**；代理对拆分用例与并发用例不在当前文件中，即 `splitMessage` 的代理对保护与并发注册行为均无回归保护。
- **E-24**：`GXBusinessException` 只用于 `createSseConnect` 首帧失败（第 91–94 行），而该分支在真实容器里几乎不可达（E-05），形成"有异常类型但无实际触发路径"的空壳语义。

发现清单（分级）：

- **F-01（高）重试耗尽只移除不 complete，产生僵尸连接**：缓存已清空但异步请求仍持有连接直到 `timeout`；探针实测 `completed=false`（E-01）。
- **F-02（高）重试机制对 `IOException` 基本无效且破坏顺序**：容器在 `IOException` 后即完成异步请求并触发错误回调，重试与移除竞态；异步重试使同一客户端/同一拆分消息乱序（E-02、E-03）。
- **F-03（高）心跳不延展寿命，反而制造周期性全量重连**：`DeferredResult` 绝对超时 + 每条事件带 `retry:2000` → 每个连接每 `timeout` 秒断开并重连一次（E-04）。
- **F-04（高）首帧发送失败路径不可达，失败后缓存残留**：首帧被 `earlySendAttempts` 缓冲，真实 flush 失败绕过 `removeEmitter`，连接留在缓存直到超时（E-05、E-24）。
- **F-05（中）同 clientId 并发重连会让一个请求直接失败**：`put`/`complete`/`send` 非原子，被替换方的请求抛 `GXBusinessException`；该竞态当前无测试锁定（E-06、E-23）。
- **F-06（中）多处 `removeEmitter` 不带 `complete()`**：心跳失败、显式心跳失败、重试耗尽三条路径都只删缓存（E-01、E-11）。
- **F-07（中）`data == null` 时 NPE 逃出捕获范围**：公共 API 会把 NPE 抛给调用方；在调度线程内则静默丢失（E-07）。
- **F-08（中）同一输入因连接集合状态不同而产生不同日志级别**：空消息广播在无连接时不告警（E-08）。
- **F-09（中）单线程调度器头阻塞**：全量心跳与所有重试共享一条线程，慢客户端拖垮全局重试与心跳（E-11）。
- **F-10（中）构造期启动调度线程 + 无销毁标记**：`this` 逃逸、bean 创建失败即泄漏线程；销毁后仍可新建连接（E-12、E-13）。
- **F-11（中）广播与心跳都是"调用线程串行写全部连接"**：无扇出、无限速、无部分失败汇总（E-16、E-11）。
- **F-12（中）每消息 2 个 UUID + 无分片序号**：拆分消息既多付一半 UUID 开销，又无法在客户端重组（E-15）。
- **F-13（中）空值契约未落实**：模块零 `@Nullable`，可空返回/可空入参与 NPE 点均无标注（E-18）。
- **F-14（中）测试覆盖与 AGENTS 第 4 节硬性要求不符**：发送失败/重试/耗尽/心跳清理/并发/代理对/真实 HTTP SSE 全部缺失，且现有用例的"发送成功"是缓冲假象（E-23）。
- **F-15（中）手册与实现大面积漂移**：多处类名、字段、方法、默认值、机制描述与代码不符（E-21、E-10、E-22）。
- **F-16（低）`length` 语义三处不一致**：接口 Javadoc / 实现钳制 / 手册单位互相矛盾（E-10）。
- **F-17（低）辅助能力未进接口**：心跳、计数、批量关闭、销毁只能向下转型调用（E-19）。
- **F-18（低）主动关闭会触发客户端自动重连**：`closeConnect` 发 `event: close` 后 `complete()`，无 204/无抑制手段，"关闭"更像"立即重连"（E-21 中手册 5.2 自述）。
- **F-19（低）`splitMessage(" ")` 返回 1 段**：与发送侧的 blank 拒绝不一致（E-09）。
- **F-20（低）高频 INFO + 无指标**：连接增删各一条 INFO，投递/失败/重试/丢弃全部无计数器（E-17）。
- **F-21（低）无功能开关恒装配**：与宪法 IV 冲突（E-20）。
- **F-22（低）`closeConnections` 谓词异常穿透**：批量关闭的非原子性与异常语义未定义（E-14）。
- **F-23（低）`getSseEmitterByClientId` 返回裸 emitter**：调用方可绕开状态检查与清理（手册 3.4.3 正推荐该用法）。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：`isCurrentEmitter` 使用引用相等（第 254–256 行）+ `removeEmitter` 使用 `remove(key, value)`（第 193–197 行），旧连接的 completion/timeout/error 回调不会误删重连后的新连接（3 条既有用例已锁定）。
- **P-02**：连接缓存为实例级 `ConcurrentHashMap`（第 35 行），广播遍历是弱一致迭代，不会抛 `ConcurrentModificationException`，也不会因并发增删中断。
- **P-03**：Spring 侧 `ResponseBodyEmitter.send/complete/onTimeout/onError/onCompletion` 均由 `ReentrantLock writeLock` 串行化（`ResponseBodyEmitter.java:93、201–224、275–360`），"多线程对同一 emitter 并发写"不会破坏帧结构；`complete()` 后 `send` 抛 `IllegalStateException`，本模块已捕获。
- **P-04**：空 clientId、空消息、缺失连接三类输入均被安全忽略，不抛异常（既有用例锁定）。
- **P-05**：`splitMessage` 对代理对的边界回退（第 271–274 行）不会产出半个码点；该行为当前无回归用例（E-23）。
- **P-06**：`closeConnect` / `destroy` 具备幂等性（重复调用无异常，既有用例锁定），`finally` 中一定 `complete()`。

## Clarifications

### Session 2026-10-23

- Q: 本次交付是"只给审查结论与验证基线"，还是"结论 + 直接修复已确认缺陷"？ → A: 按 `/speckit.specify` 的职责边界，本次交付为**可验收规格 + 可复核结论**；生产代码修复与配套测试在 `/speckit.plan` → `/speckit.implement` 阶段执行。用户"改完代码必须做完备测试"的要求固化为 FR-020～FR-023 的硬门槛，而不是在本阶段改代码。
- Q: 审查是否需要外部系统（真实 HTTP 客户端、注册中心、消息中间件）？ → A: 不需要。真实 HTTP 语义可用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `MockMvc` 异步分派或 JDK `HttpClient` 读取 `text/event-stream` 完成，不引入 Testcontainers。
- Q: 性能结论按什么口径判定？ → A: 不设绝对吞吐/时延门禁；只保留可复算口径（每消息 UUID/对象分配数、广播与心跳的串行写次数、单线程调度器的任务串行度、每连接重连周期）与同环境重跑同量级的复现要求。
- Q: 默认行为是否允许变更（重试次数、心跳周期、最小超时、默认事件名、`retry:2000`）？ → A: 默认值不变（Option A）。修复只允许"新增可配置能力 + 修复资源释放/顺序/可观测性"，默认路径行为必须与现状等价；须变更默认值时单列兼容说明与迁移路径。
- Q: 重试策略应如何收敛（保留异步重试 / 改为同步重试 / 移除重试）？ → A: 保留"指数退避重试"能力但必须满足两点：**重试前置 `complete()` 判定**（连接已失效则放弃并记录丢弃原因）与**同一连接的有序投递**（同一 clientId 的发送串行化，避免乱序）。是否改为同步重试由 `/speckit.plan` 依据顺序性结论决定，本规格只锁定上述可验收性质。
- Q: 心跳与超时的关系应如何处置？ → A: 承认现状语义（`SseEmitter` 超时是绝对超时），**不承诺"心跳保活"**：要么把心跳周期与超时口径在文档与 Javadoc 中如实描述，要么新增"到期前主动重连"的显式能力；两者都不改变默认超时值与默认事件格式。
- Q: 三类"只移除不 complete"的路径应统一到什么行为？ → A: 统一为"**移除即关闭**"：任何从缓存移除连接的路径都必须保证 `complete()` 或 `completeWithError()` 被调用且幂等，异常不得中断后续清理，并纳入回归用例。
- Q: 测试完备性的最低标准是什么？ → A: 任何生产代码改动必须在同一次变更中给出：受影响路径的成功/失败/异常/空值/边界用例、并发用例、`mvn -pl leaf-base-sse test` 全绿输出、以及至少一个真实 Spring MVC SSE 端到端用例；不得通过删除或跳过既有用例来"通过"（P-01、P-04、P-06 为必须保留的既有断言）。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核 (Priority: P1)

框架维护者要判断"能否把该模块直接用于生产环境的实时推送"，需要按维度分级、可逐条跳到代码位置的结论，并能用模块自带测试与最小回归用例复现。

**Why this priority**: 审查的价值取决于结论可信度；F-01～F-04 均直接改变线上行为（僵尸连接、消息乱序、周期性重连、失败不可见）。

**Independent Test**: 随机抽取一条结论，按报告给出的文件与行区间定位，用报告给出的命令或用例复现同一现象。

**Acceptance Scenarios**:

1. **Given** 结论"重试耗尽后连接被移除但未 complete"，**When** 向一个 `send` 必抛 `IOException` 的 emitter 发送消息并等待退避结束，**Then** 观测到 4 次发送尝试、缓存为空、emitter 的 `complete` 标志为 `false`。
2. **Given** 结论"心跳不延展连接寿命"，**When** 核对 `ResponseBodyEmitterReturnValueHandler` 的超时口径并在真实 SSE 连接上持续写入，**Then** 连接仍在 `timeout` 秒后被终止。
3. **Given** 结论"首帧失败路径不可达"，**When** 在真实 Servlet 环境中让首帧 flush 失败，**Then** 缓存中仍残留该连接（未被 `removeEmitter` 清理）。
4. **Given** 结论"`splitMessage(" ")` 返回 1 段"，**When** 直接调用该方法，**Then** 返回单元素列表，与发送侧的 blank 拒绝不一致。

---

### User Story 2 - 连接生命周期与失败处置可判定 (Priority: P1)

接入方需要明确知道：连接何时被服务端关闭、关闭后客户端会怎样、发送失败后消息是否还会到达、失败几次后连接是否可用。

**Why this priority**: SSE 是长连接语义，生命周期判断错误会直接放大为"消息静默丢失"或"连接风暴"。

**Independent Test**: 对"正常完成、超时、错误、服务端主动关闭、重试耗尽"五类终止各构造一次，记录调用方可见结果、缓存状态、客户端是否重连。

**Acceptance Scenarios**:

1. **Given** 一个活跃连接，**When** 调用 `closeConnect`，**Then** 客户端收到 `event: close` 后按 `retry` 值重连，且缓存中该 clientId 已被移除。
2. **Given** 发送持续失败，**When** 第 3 次重试也失败，**Then** 连接被关闭（而非仅移出缓存），后续发送返回"连接不存在"而非静默入队。
3. **Given** 连接因超时被终止，**When** 客户端自动重连，**Then** 新连接替换旧连接且旧连接的回调不会删除新连接（P-01 回归）。
4. **Given** 服务端正在关闭（`@PreDestroy`），**When** 有重试任务在队列中，**Then** 该任务被取消或明确失败，且不产生新的缓存项。

---

### User Story 3 - 并发与顺序安全可验证 (Priority: P1)

维护者需要知道：多线程同时注册/广播/关闭是否安全，同一客户端的消息是否保序，慢客户端会否拖垮其它客户端。

**Why this priority**: 并发问题在单线程用例中不暴露，线上表现为"消息乱序""个别客户端卡住全员重试延迟"。

**Independent Test**: 多线程并发注册同一/不同 clientId、并发广播与关闭、构造一个慢发送 emitter，观察顺序、耗时与最终缓存状态。

**Acceptance Scenarios**:

1. **Given** 50 个线程并发以同一 clientId 注册，**When** 全部完成，**Then** 缓存恰好 1 个连接，且失败的注册请求数量与原因可被观测（不出现"缓存为空"）。
2. **Given** 同一客户端连续发送 M1、M2，且 M1 首次发送失败触发退避重试，**When** 观察客户端收到的顺序，**Then** 顺序必须为 M1、M2（或明确记录"允许乱序"的契约并体现在文档）。
3. **Given** 一个 emitter 的 `send` 阻塞 2 秒，**When** 其它客户端同时发送与心跳，**Then** 其它客户端的投递不被该连接阻塞（或明确记录"共享单线程调度器"的限制）。
4. **Given** 广播过程中有连接被并发移除，**When** 广播完成，**Then** 不抛 `ConcurrentModificationException`，且不向已移除连接重试。

---

### User Story 4 - 性能开销定量化 (Priority: P2)

维护者需要把"单条消息、单次广播、单轮心跳"的开销折算成可复算口径，判断它能否用于万级连接。

**Why this priority**: 该模块是基础能力，会被默认引入；无口径的"较慢"无法转化为改造计划。

**Independent Test**: 按报告口径统计单条消息的分配与 UUID 次数、广播 N 条连接的串行写次数、一轮心跳的任务串行度、单连接的周期性重连间隔。

**Acceptance Scenarios**:

1. **Given** 一次单客户端单条消息投递，**When** 统计固定开销，**Then** 报告给出"UUID 生成次数 / 对象分配清单 / 事件构建次数 / 日志条数"，且可逐条对应代码位置。
2. **Given** 广播到 N=1000 个连接，**When** 统计写入串行度，**Then** 报告给出"调用线程上串行写次数 = N"的结论与实测总耗时量级。
3. **Given** 默认 `timeout=300s` 与 `retry:2000`，**When** 推导单连接重连周期，**Then** 报告给出"每 300 秒一次全量重连"的结论与 1 万连接时的重连速率量级。
4. **Given** 一段 1MB 文本开启拆分，**When** 统计分片数与写次数，**Then** 报告给出"约 1 万次独立事件写"的量级。

---

### User Story 5 - 对外契约与文档一致 (Priority: P3)

使用方依赖 `AGENTS.md`、开发手册、方法签名与默认常量判断行为；不一致处必须单列为发现，而不是顺手改掉。

**Why this priority**: 契约漂移不直接报错，但会让接入方按错误前提设计（例如按手册写 `sendHeartbeatToAll()` 或依赖"心跳保活"）。

**Independent Test**: 逐条核对文档声明的字段、方法、默认值、机制与实现，给出"一致 / 不一致 / 未覆盖"判定。

**Acceptance Scenarios**:

1. **Given** 手册称连接缓存与调度器为静态字段，**When** 核对实现，**Then** 判为不一致并给出证据。
2. **Given** 手册称"心跳间隔小于超时即可保持连接"，**When** 核对上游超时口径，**Then** 判为错误陈述并给出证伪证据。
3. **Given** 手册称拆分长度为 1KB–2KB，**When** 核对实现，**Then** 判为不一致并给出实际区间。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现具备编号、级别、维度、位置、触发条件、影响、证据、回归设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查八项要素是否齐备；抽取任一"既定契约"检查是否被明确标注，避免被误改。

**Acceptance Scenarios**:

1. **Given** F-01，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"先写暴露僵尸连接的用例、再统一移除即关闭"的任务。
2. **Given** P-01（旧回调不误删新连接）为既有断言，**When** 任何修复改动回调或缓存删除逻辑，**Then** 该断言必须保持通过。

---

### Edge Cases

- `timeout` 为 `0 / 1 / 60 / Long.MAX_VALUE`；`createSseConnect` 的 `clientId` 为 `null / "" / " " / 超长字符串 / 含分隔符`。
- `msg` 为 `null / "" / " " / 单字符 / 恰好等于分片上限 / 超长（1MB）/ 以代理对结尾 / 以代理对开头`。
- `splitMessage` 的 `length` 为 `-1 / 0 / 1 / 100 / 101 / Integer.MAX_VALUE`。
- 同一 clientId 的并发注册（2/50 线程）、注册与关闭并发、注册与广播并发、关闭与重试并发。
- emitter 的 `send` 抛 `IOException` / 抛 `IllegalStateException`（已 `complete`）/ 抛 `RuntimeException` / 阻塞 2 秒 / 抛 `Error`。
- `GXSseMessageInnerReqDto.data` 为 `null`、`eventName` 为 `null`、`comment` 为 `null`。
- `closeConnections` 的谓词为 `null`、恒真、恒假、抛异常、在回调中修改缓存。
- `destroy()` 在无连接 / 有连接 / 有排队重试 / 已销毁后再次调用 / 销毁后再 `createSseConnect` 四种状态下。
- 真实 HTTP：客户端在收到初始 `id:201` 前后断开、服务端在写首帧时 flush 失败、连接超时后浏览器自动重连、`retry:2000` 生效后的重连节奏。
- 下游按 `Last-Event-ID` 做断点续传时拿到的 `msgId` 是随机 UUID，服务端无重放能力（超出模块职责，需在文档显式声明）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 6 个生产源码文件与 2 个既有测试类；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能 / 契约与文档）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式：既有用例、可新增的回归用例设计或明确构造步骤；仅凭阅读的判断 MUST 标注为"未验证假设"。
- **FR-004**: 审查 MUST 以**上游框架源码**为准核对 `ResponseBodyEmitter` / `SseEmitter` / `ResponseBodyEmitterReturnValueHandler` 的实际语义（写锁、`complete` 后 `send` 的异常、`earlySendAttempts` 缓冲、`DeferredResult` 超时是否被写操作重置），不得仅凭类名或经验推断。
- **FR-005**: 连接终止语义 MUST 按"正常完成 / 超时 / 错误 / 服务端主动关闭 / 重试耗尽"五类逐条定性，说明缓存变化、`complete` 是否调用、客户端是否重连；"只删缓存不关闭"之处 MUST 列为发现（对应 F-01、F-06）。
- **FR-006**: 线程安全结论 MUST 覆盖三类场景：共享对象（连接缓存、调度器、`SseEmitter` 内部写锁）、生命周期（构造期发布、销毁期取消、调度任务排队）、竞态（同 clientId 并发注册、注册与广播并发、关闭与重试并发）。
- **FR-007**: 性能结论 MUST 给出可复算口径：单条消息的 UUID 次数与对象分配清单、广播/心跳的串行写次数、调度器并发度、单连接周期性重连间隔、拆分消息的事件数。
- **FR-008**: 消息顺序语义 MUST 给出唯一定性：在"首投失败 + 异步重试"和"分片发送"两种场景下，同一客户端是否保序；若不保序 MUST 列为发现并要求修复或显式写入契约（对应 F-02）。
- **FR-009**: 既有测试 MUST 全部执行并记录结果（当前基线：10 执行 / 0 失败）；MUST NOT 通过删除或跳过用例来"通过"；覆盖缺口 MUST 单列为发现（对应 F-14）。
- **FR-010**: 对外契约（`AGENTS.md`、开发手册、公开方法签名、默认常量、事件字段格式）MUST 与实现逐条对照，不一致处 MUST 单列为发现；审查阶段 MUST NOT 修改生产代码。
- **FR-011**: 审查报告 MUST 独立成文（`audit-report.md`），`spec.md` 只保留摘要与引用，两者在交付时 MUST 相互一致。
- **FR-012**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异），且该测试 MUST 无需外部中间件即可运行。
- **FR-013**: 空值语义 MUST 按 JSpecify 收敛：可空返回（`getSseEmitterByClientId`）、可空入参（`sendHeartbeat` / `closeConnections` / `clientId` / `data`）MUST 显式标注 `@Nullable` 或改为不允许为空，并 MUST 与 NullAway 构建检查一致；`buildEvent` 的 `data` 为空 MUST 有明确契约（拒绝并记录 / 使用空负载），不得以 NPE 形式逃逸到调用方（对应 F-07、F-13）。
- **FR-014**: 任何"从缓存移除连接"的路径 MUST 保证连接被关闭（`complete` / `completeWithError`）且幂等；异常 MUST NOT 中断后续清理；该性质 MUST 有专门回归用例（对应 F-01、F-06）。
- **FR-015**: 修复 MUST 保持既有正确性质：P-01（旧回调不误删新连接）、P-02（并发遍历安全）、P-04（空输入安全忽略）、P-06（幂等关闭/销毁）的既有断言 MUST 全部保留并通过。
- **FR-016**: 调度策略修复 MUST 说明并发度口径：心跳、重试、广播三者之间是否共享执行资源；MUST NOT 让单个慢连接阻塞其它连接的心跳与重试，或在文档中显式声明该限制；线程池与虚拟线程都能满足时 MUST 优先虚拟线程并说明理由（对应 F-09、F-11）。
- **FR-017**: 生命周期修复 MUST 覆盖：构造期不得发布未完成初始化的 `this`（调度启动可后置到 `@PostConstruct` 或首次使用时）、`destroy` MUST 取消排队任务并等待终止（或声明不等待的理由）、销毁后新建连接 MUST 有明确语义（拒绝并抛业务异常 / 允许但记录），且 MUST NOT 出现"被拒绝后静默丢消息"（对应 F-10）。
- **FR-018**: 默认行为 MUST 保持不变：最小超时 60s、`MAX_RETRIES=3`、退避基数 100ms、默认事件名 `Push Msg`、`retry:2000`、拆分上限 100；如需变更 MUST 单列兼容说明、迁移路径与受影响调用方清单（当前模块外无调用方，需在报告中标明）。
- **FR-019**: 文档修订 MUST 以现状实现为基线重写（类名、字段、方法、默认值、机制、拆分单位），MUST 删除"心跳保活""静态缓存""`sendHeartbeatToAll`"等不成立表述，并 MUST 明确"服务端关闭不阻止浏览器重连"与"无断点续传"两条限制（对应 F-15、F-18）。
- **FR-020**: 任何生产代码改动 MUST 在同一变更中补齐测试：受影响路径的成功、失败、异常、空值、边界用例各至少 1 条；涉及并发/缓存/生命周期的改动 MUST 补并发用例；涉及 Web 层语义的改动 MUST 至少补 1 个真实 Spring Boot 启动 + SSE 端点用例（`@SpringBootTest` + `MockMvc` 异步分派或 `RANDOM_PORT` 实测读取 `text/event-stream`）。
- **FR-021**: 每个修复项 MUST 附"先在旧代码上失败"的用例证据（用例名 + 失败断言），再给出实现改动；仅补实现不补用例的变更 MUST 视为未完成。
- **FR-022**: 交付前 MUST 提供 `mvn -pl leaf-base-sse test` 的全绿执行输出（含用例总数与耗时），并 MUST 给出"新增 / 修改 / 保留"用例清单，其中保留清单 MUST 包含 P-01、P-04、P-06 对应的既有断言。
- **FR-023**: 测试 MUST NOT 依赖反射访问私有字段/方法来构造"发送失败"现场；发送失败 MUST 通过可注入的 emitter 创建点或等价的可测扩展点构造（如受保护的工厂方法），使失败路径可被正常用例覆盖（对应 F-14 的根因）。
- **FR-024**: 投递可观测性 MUST 至少提供"尝试数 / 成功数 / 失败数 / 丢弃数 / 重试数 / 当前连接数"六项可获取口径（日志或指标均可），且 MUST NOT 以 INFO 级别在每个连接增删时输出无条件全量日志（对应 F-20）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **连接终止矩阵**：终止原因（完成 / 超时 / 错误 / 主动关闭 / 重试耗尽）× 缓存变化 × 是否 `complete` × 客户端行为。
- **并发风险矩阵**：对象（连接缓存 / 调度器 / `SseEmitter` 写锁 / 回调和队列）× 共享范围 × 现状保护手段 × 收敛路径。
- **性能口径**：单消息 UUID 与分配次数、广播串行写次数、心跳任务串行度、单连接重连周期、拆分事件数、测试墙钟耗时。
- **消息顺序契约**：场景（首投成功 / 首投失败 + 重试 / 分片 / 并发广播）× 是否保序 × 依据。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 6 个生产源码文件与 2 个既有测试类，且 100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 维度 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"；0 条仅凭阅读的结论混入已确认项。
- **SC-003**: 连接终止矩阵覆盖 5 类终止 × 至少 4 个观测点，判定率 100%，每条判定附上游源码或实测证据。
- **SC-004**: 消息顺序契约对"首投成功 / 首投失败 + 重试 / 分片 / 并发广播"四种场景各给出唯一定性，覆盖率 100%。
- **SC-005**: 性能结论给出可复算口径，且"单消息 UUID 次数""广播串行写次数 = N""单连接重连周期 = timeout + retry"三项可被独立复算（误差 < 1%）；MUST NOT 以绝对吞吐或时延数值作为通过门槛。
- **SC-006**: 既有测试执行率 100%（基线 10 执行 / 0 失败），测试覆盖缺口 100% 被记录为发现并对应到 `AGENTS.md` 第 4 节的具体条款。
- **SC-007**: 高危发现（F-01 / F-02 / F-03 / F-04）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。
- **SC-008**: 文档一致性核对覆盖手册中 100% 的类名、字段名、方法签名、默认值与机制描述，偏差 100% 列为发现。
- **SC-009**: 修复阶段每个改动项的"旧代码失败用例"证据覆盖率 100%（FR-021），且 `mvn -pl leaf-base-sse test` 全绿、用例数从 10 单调增长（不减少）。

## Assumptions

- 目标运行形态为 JDK 21+ + Spring Boot 4.1.x + Spring Framework 7.0.9（本次实测 JDK 25.0.4），下游通过 `@GXEnableLeafFramework` 的 `@ComponentScan("cn.maple")` 装配 `GXSseEmitterServiceImpl`（模块不提供自动装配清单）。
- 模块外当前无任何调用方（全仓检索 `GXSseEmitterService` / `sendHeartbeat` / `GXSseMessageInnerReqDto` 仅命中本模块与文档），因此本次修复的兼容性影响面限于下游自建项目，需在报告中显式声明该结论的检索范围。
- 本次为审查交付，不修改生产代码；修复在 `/speckit.plan` / `/speckit.implement` 阶段执行，并受 FR-020～FR-023 的测试门槛约束。
- SSE 的"消息不丢"不属于本模块承诺：无持久化、无重放、无 ACK；审查只要求"失败可见、顺序可判定、资源可释放"。
- 既有用例中被锁定的行为（空输入安全忽略、旧回调不误删新连接、关闭/销毁幂等）按"既定契约"处理，修复只能在不破坏这些断言的前提下进行。
- 性能数据以单机、单 JVM、本地回环网络为前提；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- 上游源码以本地仓库 `spring-webmvc-7.0.9-sources.jar` 为准；升级 Spring 版本导致的行为差异不在本次范围。
