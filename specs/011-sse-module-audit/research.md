# Phase 0 Research: leaf-base-sse 正确性、线程安全与性能审查

**Feature**: `011-sse-module-audit` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)

本文件记录两类结论：**R-xx（事实核对，不可协商）** 与 **D-xx（设计决策，含备选方案的取舍）**。所有事实核对均可按"文件 + 行号"复核。

## R. 事实核对

### R-01 发送失败后的真实链路（决定重试是否成立）

- **事实**：`ResponseBodyEmitter.send(Object, MediaType)` 在 `IOException` 后**不需要**调用方 `completeWithError`；Javadoc 明确"the Servlet container creates a notification that results in a dispatch where Spring MVC invokes exception resolvers and completes processing"（`spring-webmvc-7.0.9-sources/.../ResponseBodyEmitter.java:179–187`）。该通知会进入 `DefaultSseEmitterHandler.onError` → `deferredResult.onError` → 本模块 `onErrorCallBack` → `removeEmitter`。
- **推论**：`retryWithBackoff` 的调度任务大概率在"连接已被容器判死并被移出缓存"之后执行，`isCurrentEmitter` 检查会把它变成空转；即使抢先执行，也无法把已经断掉的 HTTP 流恢复。故重试的定位必须是"**尚未被容器判死的兜底**"，而不是"提高送达率的手段"。

### R-02 无完成态查询 API（决定重试的判据）

- **事实**：7.0.9 的 `ResponseBodyEmitter` 只公开 `send`/`complete`/`completeWithError`/`onTimeout`/`onError`/`onCompletion`/`getTimeout`，**没有** `isComplete()`/`isInitialized()`（对 `ResponseBodyEmitter.java`、`SseEmitter.java` 全量方法签名核对）。
- **推论**：无法在重试前"探测"连接是否已结束，只能通过 `send` 抛出的异常类型区分：`IllegalStateException`（已 complete，`Assert.state` → `ResponseBodyEmitter.java:204`）= 放弃重试并丢弃；`IOException` = 可有限重试。

### R-03 写锁粒度（决定并发与串行边界）

- **事实**：`writeLock` 为 `ReentrantLock`（第 93 行），`send`（第 201–224 行）、`send(Set)`（第 235–245 行）、`complete`（第 275–285 行）、`completeWithError`（第 299–308 行）、`onTimeout/onError/onCompletion`（第 318–360 行）全部持锁。
- **推论**：
  1. 多线程对同一 emitter 写不会破坏帧结构（P-03 成立）；
  2. 同一 emitter 上的阻塞写会阻塞同连接的其它写（含 `closeConnect` 的关闭事件）→ **单连接必须串行化**；
  3. 不同 emitter 之间无共享锁 → **跨连接必须并行**，否则必然头阻塞。

### R-04 超时是绝对值（否证"心跳保活"）

- **事实**：`ResponseBodyEmitterReturnValueHandler.handleReturnValue` 用 `new DeferredResult<>(emitter.getTimeout())`（第 232 行）建立异步处理，`emitter.getTimeout()` 来自构造参数；后续 `send` 只写响应，不触碰 `DeferredResult`。
- **推论**：连接存活上限 = `timeout` 秒（最小 60s），与心跳频率无关；`cleanupConnections` 的每分钟心跳只能用于"探活与清理"，不能延展寿命。手册 5.1/5.2 的"心跳间隔小于超时即可保持连接"为错误陈述（F-15）。

### R-05 首帧被缓冲（决定首帧失败路径的可达性）

- **事实**：handler 未初始化时 `send` 走 `earlySendAttempts`（第 201–224 行），flush 发生在 `initialize`（第 123–152 行）；`initialize` 由 `handleReturnValue` 在控制器返回后调用（第 242 行）。
- **推论**：`createSseConnect` 的 `try/catch (IOException | IllegalStateException)` 在真实环境中只可能命中"已被替换/已结束"（`IllegalStateException`），命中 `IOException` 需要 flush 已经失败但异常回传到本方法——不可能，因为 flush 在方法返回之后。故 F-04 成立：真实首帧网络失败时缓存会残留到超时。

### R-06 虚拟线程与定时任务

- **事实**：当前用 `Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual()...)`；JDK 文档对虚拟线程的适用场景说明其价值在于"频繁阻塞的 I/O 任务"，而 `ScheduledThreadPoolExecutor` 的线程是**长期持有**的调度线程。
- **推论**：正确用法是"调度线程只做触发，实际 I/O 交给按任务创建的虚拟线程"；否则一次慢写会把所有定时任务（心跳 + 全部重试）串行拖住（F-09）。

### R-07 可测缝的存在性

- **事实**：`SseEmitter.send(SseEventBuilder)` 为 `public` 且**非 final**（`SseEmitter.java:127`），子类可覆盖；`SseEmitter` 类本身非 final；`createSseConnect` 内部 `new SseEmitter(...)`（实现第 72 行）无注入点。
- **推论**：测试可用 `SseEmitter` 子类构造"抛 `IOException` / 抛 `IllegalStateException` / 阻塞 2s"三种现场，但必须先让子类能进入缓存 → 需要 `protected SseEmitter createEmitter(long)` 工厂（FR-023）。

### R-08 既有断言清单（不可破坏）

`GXSseEmitterServiceImplTest`（9 条）：`createSseConnect_WhenClientIdIsBlank...`（最小超时钳到 60000ms）、`createSseConnect_WhenClientIdAlreadyExists...`（替换后仅 1 条）、`closeConnect_...`（blank/缺失幂等）、`sendMessageMethods_Ignore...`（blank/缺失目标不抛）、`splitMessage_ReturnsComplete...`、`splitMessage_Clamps...`（`length=0→1`、`length=200→≤100`）、`staleCompletion/Timeout/ErrorCallback_...`（P-01 三条）。

`GXSseEmitterServiceSpringBootTest`（1 条）：Bean 装配 + `getActiveConnectionCount()` + `closeConnect`。

**结论**：`protected createEmitter` 引入后，既有用例仍通过公开 API 创建真实 `SseEmitter`，不受影响；`splitMessage` 的 blank 语义变更不触碰既有两条用例（它们用 `""` 与普通串）。

## D. 设计决策

### D-01 "移除即关闭"统一入口（F-01、F-06，FR-014）

- **Decision**: 新增包私有 `void closeAndRemove(String clientId, SseEmitter emitter, String reason)`：先 `sseClientCache.remove(clientId, emitter)`（条件删除），**仅当删除成功**时调用 `emitter.complete()`（`try/catch (Exception)` + DEBUG 日志）。心跳失败、`sendHeartbeat` 失败、重试耗尽、`destroy` 批量关闭全部走该入口；`onCompletion`/`onTimeout`/`onError` 回调仍只调用 `removeEmitter`（容器已完成连接，不重复结束）。
- **Rationale**: `complete()` 幂等且在 `writeLock` 下执行，重复调用不会破坏状态；条件删除保证"替换后的新连接不会被旧连接的清理路径关闭"（守住 P-01）。
- **Alternatives considered**:
  1. 无条件 `complete()` 后再 `remove` —— 会关闭已被替换的新连接，破坏 P-01；
  2. 统一在所有路径（含回调）都 `complete()` —— 会在 `DeferredResult` 已 set 后再次 `complete`，产生容器层重复完成告警，收益为零；
  3. 依赖容器超时兜底 —— 僵尸连接最长存活 `timeout` 秒（可配到小时级），FR-014 明确不接受。

### D-02 首帧失败的两分语义（F-04、F-05）

- **Decision**: `createSseConnect` 的 catch 分支改为：
  - `IllegalStateException` 且 `!isCurrentEmitter(clientId, self)` → 说明已被更新连接替换，记录 INFO（含 activeCount），**正常返回 self**，不抛异常；
  - 其余情况（`IOException`，或仍是当前连接）→ `closeAndRemove` + `GXBusinessException("Create SSE connection failed")`。
- **Rationale**: 并发重连时"被替换"是正常语义，不是故障；抛 500 会让一次正常重连变成客户端可见错误（F-05）。而真实失败必须让调用方感知并清理缓存。
- **Alternatives considered**:
  1. 一律抛 `GXBusinessException`（现状）—— 并发下必然出现 500；
  2. 一律不抛 —— 真实失败（缓存残留）被静默；
  3. 用 `compute` 原子替换 —— `compute` 回调内调用 `complete()` 会触发 completion 回调 → 对同一 map 再 `remove`，CHM 明确禁止在 compute 内修改映射，有死锁风险。

### D-03 单连接有序投递管线（F-02、F-12，FR-008）

- **Decision**: 新增包私有 `GXSseClientSendPipeline`：
  - 每个 clientId 至多一个实例，存放于 `ConcurrentHashMap<String, GXSseClientSendPipeline> sendPipelines`，生命周期与连接缓存同键同源（`closeAndRemove` 时一并清除）。
  - 状态：`ArrayDeque<GXSsePendingMessage>`（在 `synchronized(this)` 下操作）、`AtomicBoolean draining`。
  - **仅在首次发送失败时激活**：`send` 捕获 `IOException` 后，把该消息作为队首入队并尝试 `draining.compareAndSet(false, true)` 成功后提交 drain 任务；此后同一 client 的新消息一律入队（队尾）。
  - drain 任务在**共享虚拟线程执行器**上运行：循环取队首 → 若 `!isCurrentEmitter` 或 `IllegalStateException` → 丢弃剩余并退出（记录 `drops`）→ 否则 `sendNow`；`IOException` 则按 100/200/400ms 退避重试该条，3 次仍失败 → 关闭连接（D-01）并清空队列。队列空则 `draining=false` 并退出。
  - 队列容量上限 256，超限丢弃队尾最旧消息并记 `drops`（防止慢客户端把内存打满）。
  - 成功路径（无 pending）保持**同步直发**，默认行为不变。
- **Rationale**: 满足"同一连接保序 + 跨连接并行"；只在失败路径引入异步，把行为变更面压到最小（兼容声明在 plan.md）。
- **Alternatives considered**:
  1. 失败即关闭、不重试 —— 删除既有能力，Clarifications 否决；
  2. 同步 `Thread.sleep` 退避 —— 广播时阻塞调用线程 N × 700ms，且会让一次失败拖垮整轮广播；
  3. 所有消息无条件入队 —— 默认路径变异步，影响面大且无收益；
  4. 每连接一个专用线程 —— 连接数可达万级，线程预算不可控。

### D-04 重试判据（F-02、R-02）

- **Decision**: 重试只在 `IOException` 上发生；`IllegalStateException` 一律视为"连接已结束"，直接丢弃并记 `drops`，不重试、不打 ERROR（降为 DEBUG）。重试次数与退避基数保持 100/200/400ms、最多 3 次（默认不变）。
- **Rationale**: 已完成的连接无论重试多少次都不会成功，现状会把这类噪音放大成 3 条 ERROR。
- **Alternatives considered**: 保留现状（对两种异常都重试）—— 产生必然失败的 3 条 ERROR 日志与 700ms 无效占用。

### D-05 执行器职责拆分（F-09、F-11，FR-016）

- **Decision**:
  - `scheduler`（单线程 `ScheduledExecutorService`，虚拟线程工厂）**只做触发**：每分钟提交一次心跳批次、必要时提交 drain；
  - `taskExecutor = Executors.newVirtualThreadPerTaskExecutor()` 承载真正的 I/O：心跳批次按 `batchSize=256` 分片，每片一个虚拟线程任务，片内串行写；
  - 广播保持"调用线程遍历 + 逐条投递"，但在失败路径上不再原地退避（转 D-03 队列），因此广播耗时从"含退避"降为"仅首投"。
- **Rationale**: 头阻塞的根因是"触发与 I/O 共用一条线程"；拆开后单连接慢不会影响其它连接。
- **Alternatives considered**:
  1. 固定大小线程池 —— 需设并发度，且虚拟线程在此场景更贴合（AGENTS 第 7 条要求优先虚拟线程）；
  2. 心跳完全改成异步并行（每连接一任务）—— 万级连接会瞬时创建万个任务，分片更平稳；
  3. 保持现状 + 文档声明限制 —— FR-016 明确不接受"单连接阻塞全局"。

### D-06 生命周期与销毁（F-10、F-17，FR-017）

- **Decision**:
  - 调度器与任务执行器的创建移到 `@PostConstruct void init()`；构造函数不再启动任何线程（消除 `this` 逃逸）；
  - `destroy()`：`destroyed.compareAndSet(false, true)` → 关闭全部连接（D-01）→ 清空 `sendPipelines` → `scheduler.shutdownNow()` → `taskExecutor.shutdownNow()` → 两级 `awaitTermination(5s)`，超时记 WARN；
  - 销毁后 `createSseConnect` 抛 `GXBusinessException("SSE service has been destroyed")`；`send*` 系列直接返回并记 DEBUG（不抛，避免停机时业务链路连锁失败）；
  - `destroy` 保持幂等（重复调用只做一次实际关闭）。
- **Rationale**: 现状"销毁后仍能注册连接、重试被静默丢弃"会让停机窗口出现不可解释的丢消息；显式失败/显式忽略都可观测。
- **Alternatives considered**:
  1. 保留构造函数启动 —— `this` 逃逸 + bean 创建失败即泄漏线程；
  2. 销毁后仍允许创建 —— 与"服务已下线"语义矛盾；
  3. 销毁时抛异常给 send 调用方 —— 停机期间会把业务链路一起打挂。

### D-07 可观测性（F-20，FR-024）

- **Decision**:
  - `GXSseMetrics`（`record`）字段：`attempts`（发送尝试）、`successes`、`failures`、`retries`、`drops`、`connections`（当前活跃）；实现内用 6 个 `LongAdder`（`connections` 直接取缓存 size）+ 1 个 `AtomicLong startedAt`；
  - 接口新增 `default GXSseMetrics getMetrics()`（返回全 0 快照），实现类覆盖；
  - 日志级别调整：连接创建/关闭保持 INFO（每连接每次生命周期 ≤ 2 条）；移除/清理/耗尽/丢弃降为 DEBUG；所有 DEBUG 用 `isDebugEnabled()` 前置判断避免字符串拼接。
- **Rationale**: 6 项指标覆盖"投递是否健康"的全部判断；INFO 只留生命周期两端，兼顾运维可读性与日志量。
- **Alternatives considered**:
  1. 引入 Micrometer —— 新增依赖（宪法附加约束 3 否决）；
  2. 返回 `Map<String, Long>` —— 违反宪法 III 的裸 Map 风格；
  3. 6 个独立 getter —— 接口膨胀，且无法保证快照一致性。

### D-08 空值契约（F-13，FR-013）

- **Decision**: 在 `@NullUnmarked` 包内显式标注：`getSseEmitterByClientId` 返回 `@Nullable SseEmitter`；`sendHeartbeat(@Nullable String)`、`closeConnections(@Nullable Function<String, Boolean>)` 参数标 `@Nullable`；`GXSseMessageInnerReqDto.data` 标 `@Nullable`；`buildEvent` 遇到 `data == null` 改为"记 WARN + `drops++` + 跳过该消息"（不再 `Objects.requireNonNull` 抛 NPE）。
- **Rationale**: 与模块既有约定"非法输入忽略并记录"一致；NullAway 可在编译期校验标注完整性。
- **Alternatives considered**: 抛 `GXBusinessException`（会改变 `sendMessageToOneClient` 的"不抛异常"既有语义，P-04 冲突）。

### D-09 `splitMessage` 与分片序号（F-12、F-19）

- **Decision**:
  - blank 判定改用 `CharSequenceUtil.isBlank` → `splitMessage(" ")` 返回空列表（与发送侧一致）；
  - 分片仍按 `[1, min(length,100)]` 随机长度切分，保留代理对的边界回退逻辑（P-05）；
  - 拆片发送时在 `comment` 上追加 `":chunk=<i>/<n>"`（原 `":clientId"` 前缀保留），`msgId` 在**同一原始消息的所有分片间共享**（由 `buildMessageDto` 外层生成一次后传入，不再每片重复生成）。
- **Rationale**: 共享 `msgId` + 序号让客户端可在保序前提下校验完整性；顺带消除每分片 2 次 UUID 的浪费（F-12、E-15）。
- **Alternatives considered**: 新增 `event: chunk` 类型（改变线格式，破坏性）；把序号放进 `data`（改变用户负载结构）。

### D-10 空消息广播的日志一致性（F-08）

- **Decision**: `sendMessageToAllClient` 先判 `msg` 空（WARN + 返回），再判集合空（DEBUG + 返回）。
- **Rationale**: 同一输入在不同连接状态下应给出一致告警。
- **Alternatives considered**: 保持现状 —— 排查时"空消息广播"在空集群上不可见，掩盖调用方缺陷。

### D-11 文档重写范围（F-15、F-18）

- **Decision**: 重写手册第 2.3（实现机制）、5.1（服务端最佳实践）、6（FAQ 的"连接意外断开""消息丢失"两节）与 7（对比表"特点"行）：
  - 删掉静态字段、`removeUser`、`sendHeartbeatToAll`、`sendMessageToAllClient(msg, true)`、"同步块"、"复用消息构建器"、"拆分 1KB–2KB"、"错误回调会发送恢复提示"；
  - 新增"连接生命周期与超时真相"（心跳不保活）、"顺序与重试"（何时重试、何时丢弃、如何观测）、"可观测性"（`getMetrics()` 六项 + 日志级别）三节；
  - 明确两条限制：服务端 `closeConnect` 不阻止浏览器重连（无 204）；无 `Last-Event-ID` 重放能力。
- **Rationale**: FR-019 要求以现状实现为基线重写；F-18 要求"关闭即重连"被显式声明。
- **Alternatives considered**: 只做增量补丁 —— 手册既有 6 处错误彼此矛盾，补丁后仍自相矛盾。

### D-12 测试策略（F-14，FR-020 ~ FR-023）

- **Decision**:
  - 生产侧新增 `protected SseEmitter createEmitter(long timeoutMillis)`（唯一创建点，供子类覆盖）+ `protected SseEmitter.SseEventBuilder` 无需暴露；
  - 测试侧新增 4 个类（发送失败 / 并发 / 生命周期 / 拆分）+ 1 个真实 HTTP 端到端类（`@SpringBootTest(webEnvironment = RANDOM_PORT)`，测试内自带 `@RestController` 端点，用 `HttpClient` 读 `text/event-stream` 并断言 `event:`/`id:`/`retry:`/`data:`/连接关闭）；
  - 既有 9 + 1 条用例**一条不删**，仅做"新增断言"式修改。
- **Rationale**: FR-023 禁止反射构造失败现场；RANDOM_PORT 用例是唯一能验证"真实线格式 + 首帧 flush"的方式，也是 F-04 的回归位。
- **Alternatives considered**: 只用 `MockMvc` + `asyncDispatch`（无法读取流式增量内容）；用 Tomcat 之外的轻量容器（无必要，Tomcat 11 已在测试类路径）。
