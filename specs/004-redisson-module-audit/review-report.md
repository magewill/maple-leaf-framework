# leaf-base-redisson 审查报告

**Feature**: `004-redisson-module-audit` | **日期**: 2026-10-22 | **需求**: [spec.md](./spec.md)

## 1. 结论摘要

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 配置装配、Spring Cache 基础读写、锁/计数器参数校验路径基本可用；但**订阅缓存无法自愈**、**延迟转发退化为至多一次**、**stream 无 handler 时静默丢弃**三条链路存在"无报错但消息丢失"的风险 | 存在高风险缺陷 |
| 线程安全 | 显式共享状态以 `ConcurrentHashMap`/`volatile` 保护为主，`destroy` 路径做了二次清理；但**消费者并发度随注册顺序变化**、**静态缓存跨上下文持有旧客户端**、**in-flight 无界累积**三处缺少确定性约束 | 有并发风险，需约束 |
| 性能 | 单一消费路径的 RTT 数量与锁粒度缺乏上限约束：延迟 stream 转移为**全局单锁 + 每消息 6 次串行往返**，延迟队列转发**无背压**，锁工具**每次加解锁多 2 次往返** | 存在结构性瓶颈 |

**风险排序依据（三因子）**：每条发现按"影响面（消息丢失/资金风险 = 高；吞吐与资源劣化 = 中；语义与文档 = 低）× 触发概率（常态路径 = 高；特定时序/失败路径 = 中；需升级或误配 = 低）× 可观测性（无日志无补偿 = 低；有 warn 但无补偿 = 中；启动即失败或 error 明确 = 高）"打分，三档折算 3/2/1 后相加，得分高者批次靠前；同分时按"是否影响消息可靠性"优先。各发现的取值见 §3 每条标题后的〔影响·概率·可观测〕标注与 §3.0 汇总表。

## 2. 审查范围与基线

| 项 | 值 |
| --- | --- |
| 生产代码 | 51 个 `.java`（含 15 个 `package-info.java`）+ `src/main/resources/application.yml` |
| 既有测试 | `RedissonUtilityRegressionTest`，38 个用例（Mock/反射，无真实 Redis） |
| 新增审计用例 | 7 个类 22 个用例，全部通过（见 §10） |
| 实机验证 | 8 个类 27 个用例，其中 5 个为可选真实服务用例：设置 `AUDIT_REDIS_HOST`（地址与密码均由环境变量提供，不在文档与代码中固化）后执行，`5/5` 通过；未设置时整类跳过（`Skipped: 5`），默认构建仍为 `60/60` |
| 依赖基线 | `org.redisson:redisson:4.7.0`、`redisson-spring-cache:4.7.0`、JDK 21 虚拟线程 |
| 基线测试 | `Tests run: 38, Failures: 0, Errors: 0, Skipped: 0`（7.480 s） |
| 证据等级 | `S` 本仓库代码位置；`D` 依赖源码（`redisson-4.7.0-sources.jar`）；`T` 可执行用例（详见 [research.md](./research.md) R-01） |
| 覆盖率台账 | [checklists/coverage-ledger.md](./checklists/coverage-ledger.md)（52 行） |
| 依赖事实 | [checklists/dependency-facts.md](./checklists/dependency-facts.md)（D-1～D-5） |
| 与 plan 的偏差 | ①用例文件数：实际新增 9 个 java 文件（8 个测试类 + 1 个 `GXRedissonAuditTestSupport`），超出 plan 的"≤ 8 个文件"——超出来源是收敛阶段追加的真实服务用例类，独立文件便于默认跳过，未拆分是为了保持用例边界清晰；②support 只提供反射调用、属性构造与 `newRecord` helper，未实现 plan 所述的 `RBlockingQueue`/`RDelayedQueue`/`RReliableTopic`/`RStream` mock 工厂——各用例的 mock 形态差异较大（带超时的 `drainTo` Answer、变长 `entryRange` 等），集中工厂反而降低可读性，故由用例本地构造 |
| 真实服务用例来源 | `GXRedissonRealServiceVerificationTest` 不在 spec/plan 的原始范围内，是后续现场验证需求（对真实 Redis 确认正常路径）引入的；默认跳过条件为未设置 `AUDIT_REDIS_HOST`；其结论边界是"只确认正常路径可用与弃用告警可复现，不推翻任何缺陷定性"，需在下一版 plan 修订时登记 |

## 3. 发现清单

### 3.0 排序三因子汇总

标题后的〔H·M·L〕依次表示 **影响面 / 触发概率 / 可观测性**（H=高、M=中、L=低），与下表一一对应。批次为综合判定：三因子决定"风险有多大"，修复成本与契约影响决定"什么时候修"，因此个别高影响项（F-10、F-16）因成本高而后置。标题中的 `[高/中/低]` 是**综合严重级别**，与三因子的"影响面"不同口径（例：F-04 影响面为"中"，但性能结构问题使其整体定为"高"）。

| 发现 | 影响面 | 触发概率 | 可观测性 | 批次 | 批次理由 |
| --- | --- | --- | --- | --- | --- |
| F-01 | 高 | 中 | 低 | P0 | 直接丢失消息且无补偿，属可靠性底线 |
| F-02 | 高 | 中 | 低 | P0 | 同上，且承诺与实现不一致 |
| F-03 | 高 | 中 | 低 | P0 | 静默丢弃 + 毒消息常驻 |
| F-04 | 中 | 高 | 中 | P1 | 常态路径的结构性吞吐瓶颈，可观测 |
| F-05 | 中 | 中 | 低 | P1 | 资源无上限增长，需压力才暴露 |
| F-06 | 低 | 高 | 中 | P2 | 时机问题，有日志可查 |
| F-07 | 中 | 高 | 低 | P2 | 容量不可复现，但无数据风险 |
| F-08 | 中 | 低 | 低 | P1 | 资源不释放，需竞争窗口才触发 |
| F-09 | 中 | 低 | 低 | P1 | 上下文重建场景在测试环境高频 |
| F-10 | 高 | 中 | 中 | P2 | 修复会改变回调异常语义，成本高 |
| F-11 | 中 | 高 | 中 | P2 | 语义歧义，调用方可自行兜底 |
| F-12 | 中 | 中 | 低 | P2 | 依赖去重标记写入失败，概率有限 |
| F-13 | 中 | 低 | 高 | P2 | 启动即失败，问题暴露成本低 |
| F-14 | 高 | 低 | 中 | P1 | 线程不可回收，属资源硬缺陷 |
| F-15 | 低 | 低 | 高 | 仅文档 | 已被用例锁定的既定契约 |
| F-16 | 高 | 低 | 高 | P2 | 升级即断，当前可用且改造面大 |

### F-01 [高] 订阅缓存无法自愈[H.M.L]

- **维度**: 逻辑正确性
- **证据等级**: D
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonMQUtils.java:81-93;136-144`
- **触发条件**: `removeListener` 抛异常（Redis 抖动、客户端关闭中、权限变化），或监听器被外部移除后调用 `subscribe`
- **影响**: 调用方拿到非空 `listenerId` 并认为订阅成功，实际已无投递通道；消息静默丢失，且代码中不存在任何补偿入口
- **证据**: E-01（`computeIfAbsent` 命中即返回）+ E-15（依赖侧 `removeListenerAsync` 先同步清空本地 listeners、再异步清理 Redis 消费组，异常时本地已无监听器）
- **复现**: `GXRedissonTopicSubscriptionAuditTest#subscribeReturnsStaleListenerIdWhenUnsubscribeFails` → 第二次 `subscribe` 返回 `listener-1`，`addListener` 仅被调用 1 次
- **回归测试设计**: 输入：`removeListener` 抛 `IllegalStateException` → `unsubscribe` → `subscribe`；断言：修复后第二次 `subscribe` 必须重新调用 `addListener`，或显式抛出"缓存与监听器不一致"；翻转点：当前断言 `assertEquals("listener-1", ...)` 改为 `verify(topic, times(2)).addListener(...)`
- **修复批次**: P0
- **契约影响**: `subscribe` 返回值语义不变；需为"缓存失效"新增一条 warn 日志，不新增配置键

### F-02 [高] 延迟转发退化为至多一次[H.M.L]

- **维度**: 逻辑正确性
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonDelayMQPostProcessor.java:176-188;442-450`
- **触发条件**: 消息经 `drainTo` 移出 Redis 后、写入下游主题前进程崩溃；或 `requeueToBlockingQueue` 返回 `false`
- **影响**: 消息在 Redis 与下游均不可见，只存在于进程内 `inFlightMessages`；"延迟队列可靠转发"的承诺不成立
- **证据**: E-02（`inFlightMessages` 为 `ConcurrentHashMap`，仅本地）
- **复现**: `GXRedissonDelayForwardingAuditTest#requeueFailureKeepsMessageInMemoryOnly` → `blockingQueue.offer` 返回 `false` 时消息只留在 `inFlightMessages`，`delayedQueue.offer` 从未被调用
- **回归测试设计**: 输入：`offer` 返回 `false`；断言：修复后消息必须回落到可见存储（Redis 阻塞队列或延迟队列），或抛出可被上层感知的异常；翻转点：`assertEquals(1, inFlightMessages.size())` 改为 `verify(delayedQueue).offer(...)` 且断言 `inFlightMessages` 为空
- **修复批次**: P0
- **契约影响**: 若改为"先确认后删除"，会改变 Redis 数据结构（新增处理中集合），需在修复规格中评估

### F-03 [高] 无处理器即丢弃 + 毒消息常驻[H.M.L]

- **维度**: 逻辑正确性
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/stream/queue/GXRedissonStreamImmediateMQ.java:184-200`
- **触发条件**: 消息在 handler 注册前到达；或畸形消息的 DLQ 写入失败
- **影响**: 前者被 `ack` 丢弃；后者永久 pending，每轮恢复重投但不递增重试计数，永远无法升级为死信，形成"毒消息"循环
- **证据**: E-08（`handler == null` 直接 `ack`；DLQ 异常时 `return` 不 `ack`）
- **复现**: `GXRedissonStreamReliabilityAuditTest#messageWithoutHandlerIsAcknowledged` → `ack` 被调用且 `getStream("mq:dlq:...")` 从未被调用
- **回归测试设计**: 输入：未注册 handler 时投递一条消息；断言：修复后消息必须留在 pending 或写入 DLQ；翻转点：`verify(client, never()).getStream(DLQ_KEY)` 改为 `verify(stream, never()).ack(...)`
- **修复批次**: P0
- **契约影响**: `none`（消费语义内部修正）

### F-04 [高] 延迟转移全局锁 + 串行往返[M.H.M]

- **维度**: 性能
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/stream/queue/GXRedissonStreamDelayedMQ.java:149-166;187-210`
- **触发条件**: 存在到期延迟消息，且多实例或多 topic 并发
- **影响**: 固定锁键 `mq:delay:lock` 把全部 topic 与全部实例串行化；单条消息 6 次串行往返，单轮上限 200 条/topic，锁窗口与最慢节点耦合，抢占失败的实例持续空转
- **证据**: E-09（锁范围 + 6 步操作序列）
- **复现**: `GXRedissonStreamTransferAuditTest#oneDueMessageCostsSixRoundTrips`（6 次操作各 1 次）、`#oneGlobalLockSerialisesEveryTopic`（`getLock("mq:delay:lock")` + `unlock`）
- **回归测试设计**: 输入：2 个 topic 各 1 条到期消息；断言：修复后锁粒度改为按 topic（`getLock("mq:delay:lock:" + topic)`）或多条消息 pipeline；翻转点：`verify(client).getLock("mq:delay:lock")` 改为按 topic 的键断言
- **修复批次**: P1
- **契约影响**: 锁键名变化对下游不可见（`none`），但滚动升级期间新旧实例会各持一把锁，需在修复规格中说明过渡策略

### F-05 [中] 转发链路无背压[M.M.L]

- **维度**: 性能
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonDelayMQPostProcessor.java:250-276;278-320`
- **触发条件**: handler 变慢或下游主题不可用
- **影响**: 本地队列有界（5000），但 in-flight 只受"超时 × 消费速率"间接约束；实测 20 条消息在 4 个消费者下同时滞留，随负载线性增长，最终表现为堆压力与重试风暴
- **证据**: E-03（提交 future 后立即返回）+ E-14（`drainTo` 为单次 Lua，抓取本身不低效）
- **复现**: `GXRedissonDelayForwardingAuditTest#inFlightMessagesGrowWithoutBackpressure` → 20 条 in-flight 与 4 个消费者并存
- **回归测试设计**: 输入：handler 返回永不完成的 future，注入 100 条；断言：修复后 in-flight 不超过窗口上限（如 `2 × consumers`）；翻转点：`assertEquals(20, inFlightMessages.size())` 改为 `assertTrue(inFlightMessages.size() <= window)`
- **修复批次**: P1
- **契约影响**: `none`（内部流控），新增上限常量/配置项属修复范围

### F-06 [中] 重试节奏固定且窗口不可控[L.H.M]

- **维度**: 逻辑正确性
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonDelayMQPostProcessor.java:296;322-344`
- **触发条件**: 下游主题持续失败
- **影响**: 重试间隔固定为 1s、2s（`RETRY_DELAY × attempt`），无指数退避与抖动；单次尝试可占用 30s 超时，最坏情况下一条消息在 in-flight 中滞留 93 秒（3 × 30s 超时 + 1s + 2s 退避）；恢复后仍按固定节奏倾泻
- **证据**: E-04（`RETRY_DELAY = 1000`、`MAX_RETRY_TIMES = 3`、`TASK_EXECUTION_TIMEOUT = 30`）
- **复现**: `GXRedissonDelayForwardingAuditTest#retryDelayIsFixedLinearStep` → 300ms 内不重试、2s 内完成首次重试
- **回归测试设计**: 输入：`attempt = 1` 触发 `scheduleRetry`；断言：修复后首次重试延迟应带抖动或指数因子（如 1s ± 20%）；翻转点：把"300ms 内必须不重试"改为"延迟落在 [800ms, 1500ms] 且相邻两次间隔递增"
- **修复批次**: P2
- **契约影响**: `none`

### F-07 [中] 消费者并发度不确定[M.H.L]

- **维度**: 线程安全
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonDelayMQPostProcessor.java:132;244-248`
- **触发条件**: 多个延迟队列监听器先后注册
- **影响**: 每队列消费者数 = `min(4, max(1, 池大小 / 已注册队列数))`，且调用发生在 `putIfAbsent` 之后；同一份配置在不同 Bean 注册顺序下得到不同并发度，容量规划与压测结果不可复现
- **证据**: E-05（`listenerConfigs.size()` 作为分母）
- **复现**: `GXRedissonConsumerConcurrencyAuditTest#consumerCountDependsOnRegisteredQueueCount` → 1 个队列时 4 个消费者，64 个队列时 1 个
- **回归测试设计**: 输入：同一 `listenerConfigs` 以不同顺序填充；断言：修复后并发度只由配置（而非注册顺序/队列数）决定；翻转点：`assertEquals(4, single)` / `assertEquals(1, many)` 改为两值相等且等于配置值
- **修复批次**: P2
- **契约影响**: 新增配置项（每队列消费者数）时需补 `additional-spring-configuration-metadata.json`

### F-08 [中] 重复注册路径抬高延迟队列引用计数[M.L.L]

- **维度**: 线程安全
- **证据等级**: D
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/processor/GXRedissonDelayMQPostProcessor.java:111-115;132-136`
- **触发条件**: 两个线程同时为同一队列注册（Bean 重复定义、上下文刷新），或注册后未走 `destroy`
- **影响**: `startListenerTask` 为"先查后放"，竞争时后到者已创建 `RDelayedQueue` 才在 `putIfAbsent` 失败返回；该实例不再被持有也不会 `destroy()`。依赖侧 `QueueTransferService` 按队列名做引用计数，计数只增不减，导致容器关闭后转移任务仍在运行
- **证据**: E-06（提前 return 未销毁）+ E-13（依赖侧引用计数语义）
- **复现**: `GXRedissonDelayForwardingAuditTest#duplicateRegistrationSkipsSecondDelayedQueue` → 已注册时不再创建第二个实例，`destroy()` 只减一次；竞争窗口由 E-06/E-13 静态证据确认
- **回归测试设计**: 输入：两个线程并发调用 `startListenerTask`；断言：修复后 `client.getDelayedQueue` 的每次成功创建都必须对应一次 `destroy()`（或改用 `putIfAbsent` 前先创建、失败即销毁）；翻转点：新增 `verify(client, times(1)).getDelayedQueue(...)` 的严格计数
- **修复批次**: P1
- **契约影响**: `none`

### F-09 [中] 静态缓存跨容器持有旧客户端[M.L.L]

- **维度**: 线程安全
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonMQUtils.java:32-33;254-257`；`processor/GXRedissonMQPostProcessor.java:108-127`
- **触发条件**: 应用上下文重建（测试、热部署、多上下文）、`RedissonClient` 重启
- **影响**: `TOPIC_CACHE`/`LISTENER_REGISTRATION_CACHE` 为 `static`，绑定首个客户端；新上下文继续操作旧客户端派生的 topic 句柄。`destroy()` 仅在"本地缓存已空"时才整体清理，残留条目会跨上下文存活
- **证据**: E-07（静态缓存 + 清理条件）
- **复现**: `GXRedissonTopicSubscriptionAuditTest#topicCacheKeepsFirstClientInstance` → 第二次读取返回同一实例，`secondClient.getReliableTopic` 从未被调用
- **回归测试设计**: 输入：两个 `RedissonClient` 依次注入；断言：修复后缓存键包含客户端标识或随上下文销毁清空；翻转点：`assertSame(cached, secondRead)` 改为 `assertNotSame(...)`
- **修复批次**: P1
- **契约影响**: `none`

### F-10 [中] 业务异常被吞[H.M.M]

- **维度**: 逻辑正确性
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonMQUtils.java:294-302`
- **触发条件**: 业务监听器抛异常
- **影响**: 包装器记录 error 后正常返回，Redisson 视为消费成功并推进消费组偏移；可靠主题退化为"尽力投递"，且无补偿、无指标
- **证据**: E-10（`catch (Exception e) { LOGGER.error(...) }` 后不重抛）
- **复现**: `GXRedissonTopicSubscriptionAuditTest#businessListenerExceptionIsSwallowed` → `wrapped.onMessage` 不抛异常
- **回归测试设计**: 输入：业务监听器抛 `IllegalStateException`；断言：修复后应重抛（由 Redisson 决定是否重投）或写入失败队列；翻转点：`assertDoesNotThrow` 改为 `assertThrows`
- **修复批次**: P2
- **契约影响**: 重抛会改变使用方回调的异常可见性，属语义变更，需在修复规格中声明

### F-11 [中] 锁与计数器语义/开销[M.H.M]

- **维度**: 逻辑正确性
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonUtils.java:111-119;175-198;362-370`
- **触发条件**: 加锁超时、释放锁、读取计数器失败
- **影响**: (1) 加锁超时返回 `null`，与"操作正常返回 `null`"不可区分；(2) 释放路径先 `isLocked()` 再 `isHeldByCurrentThread()` 再 `unlock()`，每次多 2 次网络往返；(3) `getCounterValue` 异常返回 `0`，掩盖故障
- **证据**: E-11（三处实现）
- **复现**: `GXRedissonLockSemanticsAuditTest` 三个用例（超时返回 `null`、释放 3 次调用、计数器异常返回 0）
- **回归测试设计**: 输入：`tryLock` 返回 `false`；断言：修复后返回可判别的结果（抛 `GXBusinessException` 或返回 `Optional`）；翻转点：`assertNull` 改为 `assertThrows`；释放路径改为单次 `unlock()`
- **修复批次**: P2
- **契约影响**: `executeWithLock` 返回语义变化属破坏性，必须走版本升级与迁移说明

### F-12 [中] stream 去重与恢复不完整[M.M.L]

- **维度**: 逻辑正确性
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/stream/queue/GXRedissonStreamImmediateMQ.java:202-229;259-303`
- **触发条件**: 去重标记写入失败；pending 列表头部存在无法 `ack` 的消息
- **影响**: `markProcessed` 失败仅 warn 仍 `ack`，去重窗口失效导致重复执行；`recoverPendingMessages` 单次只取头部 `batchSize × 2`（上限 100）条且无分页游标，头部受阻时后续消息长期饥饿；每条 pending 单独 `claim`，N 条 N 次往返
- **证据**: E-08（`markProcessed` 异常被吞）+ 用例
- **复现**: `GXRedissonStreamReliabilityAuditTest#ackHappensWhenDedupeMarkerFails`、`#pendingRecoveryScansOnlyHeadOfPendingList`
- **回归测试设计**: 输入：`fastPut` 抛异常；断言：修复后不得 `ack`（交由 pending 重投）；翻转点：`verify(stream).ack(...)` 改为 `verify(stream, never()).ack(...)`；恢复侧改为按 `lastId` 游标翻页
- **修复批次**: P2
- **契约影响**: `none`

### F-13 [中] 连接配置边界[M.L.H]

- **维度**: 逻辑正确性
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/config/GXRedissonSpringDataConfig.java:99-117;147-156`（`GXRedissonMQConfig` 同构，第 41-60 行）
- **触发条件**: 地址仅含分隔符（如 `","`）；配置键含 `cluster` 但只有一个地址
- **影响**: 前者抛 `ArrayIndexOutOfBoundsException`，启动失败信息不可诊断；后者被静默提升为集群模式，行为与配置意图不符。两处装配类重复实现同一段解析逻辑
- **证据**: E-11 同源解析代码 + 用例
- **复现**: `GXRedissonConnectConfigAuditTest` 三个用例（越界、键名决定集群、单机保持）
- **回归测试设计**: 输入：`address = ","`；断言：修复后抛带键名与原始值的 `IllegalStateException`；翻转点：`assertThrows(ArrayIndexOutOfBoundsException.class, ...)` 改为 `IllegalStateException` 且消息包含键名
- **修复批次**: P2
- **契约影响**: 失败异常类型变化，需在文档中说明（原为运行时越界，非显式契约）

### F-14 [中] 生命周期不闭合[H.L.M]

- **维度**: 线程安全
- **证据等级**: T
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/stream/GXRedissonStreamMQManager.java:53-67`；`properties/GXRedissonStreamMQProperties.java:70-85`；`annotation/GXRedissonDelayMQToTopic.java:20-22`；`processor/GXRedissonDelayMQPostProcessor.java:100`
- **触发条件**: `start()` 过程中某个组件启动失败；`blockingTimeoutMillis = 0`；使用注解默认超时
- **影响**: (1) `started` 未置位，先前已启动的消费者线程无法被 `stop()` 回收；(2) 0 被接受，`XREADGROUP` 进入永久阻塞，`shutdownNow` 之外无退出路径；(3) 注解 `timeout()` 默认 1800 秒而常量 `DEFAULT_POLL_TIMEOUT_SECONDS = 30` 只在 `timeout <= 0` 时生效，默认阻塞时长实为 30 分钟，注释与实际不符
- **证据**: E-12（半途失败）+ E-05(D-5)（BLOCK 0 语义）+ S 级注解默认值
- **复现**: `GXRedissonConsumerConcurrencyAuditTest#stopDoesNotReleaseAfterPartialStartFailure`、`#zeroBlockingTimeoutIsAccepted`
- **回归测试设计**: 输入：`delayedQueue.start()` 抛异常；断言：修复后 `stop()` 必须回收已启动组件（`try/finally` 或 `started` 先置位再回滚）；翻转点：`verify(immediate, never()).stop()` 改为 `verify(immediate).stop()`
- **修复批次**: P1
- **契约影响**: 若收紧 `blockingTimeoutMillis >= 1`，属配置校验收紧，需在发布说明中提示

### F-15 [低] 缓存语义待书面化[L.L.H]

- **维度**: 逻辑正确性
- **证据等级**: S
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/services/impl/GXRedissonCacheServiceImpl.java:88-108;175-186`
- **触发条件**: 对无过期时间的条目调用 `updateCacheExpiredTime`；对大 bucket 调用 `getBucketAllData`
- **影响**: TTL = -1 时直接返回 `true` 但不设置过期；`getBucketAllData` 全量物化键与值。二者均已被既有用例锁定，属**既定契约**而非缺陷，风险在于文档未写明
- **证据**: E-12(spec) 对应实现 + 既有用例 `updateCacheExpiredTimeKeepsExistingNoExpiryEntry`、`getBucketAllDataUsesCountAsScanBatchSizeWithoutTruncatingResults`
- **复现**: `mvn -pl leaf-base-redisson -o test -Dtest=RedissonUtilityRegressionTest#updateCacheExpiredTimeKeepsExistingNoExpiryEntry`
- **回归测试设计**: 无需翻转断言；修复动作仅限文档（`AGENTS.md` 与模块手册写明"TTL=-1 不刷新、全量读取会物化"）
- **修复批次**: 仅文档
- **契约影响**: `none`

### F-16 [中] 建立在弃用 API 之上[H.L.H]

- **维度**: 逻辑正确性
- **证据等级**: D
- **位置**: `leaf-base-redisson/src/main/java/cn/maple/redisson/util/GXRedissonDelayMQUtils.java:31-33;60-66`；`processor/GXRedissonDelayMQPostProcessor.java:119-120`
- **触发条件**: 升级 Redisson 至移除 `RDelayedQueue` 的版本
- **影响**: 延迟队列发送（`GXRedissonDelayMQUtils` 静态缓存）与转发（`GXRedissonDelayMQPostProcessor`）全部失效；且两处各持一个 `RDelayedQueue` 实例，使引用计数至少为 2，任一处未 `destroy` 都会让转移任务常驻
- **证据**: E-13（依赖侧弃用日志与替代建议 `RReliableQueue`）
- **复现**: `Get-Content $tmp\Redisson.java | Select-String "deprecated due to github issues"`（见 quickstart §2）
- **回归测试设计**: 输入：升级后编译；断言：修复后改用 `RReliableQueue` 且延迟转发用例全绿；翻转点：`GXRedissonDelayForwardingAuditTest` 全部用例在替换实现后仍需通过
- **修复批次**: P2
- **契约影响**: 替换传输底座会改变 Redis 键结构，需版本升级 + 迁移说明

## 4. 链路可靠性矩阵

| 链路 | 定性 | 决定性代码行 | 失败时消息归属 | 守护用例 |
| --- | --- | --- | --- | --- |
| 可靠主题订阅/发布（`GXRedissonMQUtils`） | 尽力投递 | `wrapListener` 捕获后不重抛（294-302） | 业务异常后被视为已消费 | `GXRedissonTopicSubscriptionAuditTest` |
| 延迟队列转发（`GXRedissonDelayMQPostProcessor`） | 至多一次 | 移出 Redis 后仅写 `inFlightMessages`（176-188） | 进程崩溃即丢失；重投失败留内存 | `GXRedissonDelayForwardingAuditTest` |
| stream 立即消费（`GXRedissonStreamImmediateMQ`） | 至少一次（有重复窗口） | `ack` 前写 `processed`（224-229），失败仍 `ack` | 未 `ack` → pending；无 handler → 丢弃 | `GXRedissonStreamReliabilityAuditTest` |
| stream 延迟转移（`GXRedissonStreamDelayedMQ`） | 至少一次 | `transferSet` 先登记后删除（198-205），`recoverAbandonedTransfers` 兜底 | 转移失败 → zset/hash 残留，下轮重试 | `GXRedissonStreamTransferAuditTest` |
| pending 恢复（`GXRedissonStreamPendingMessageRecovery`） | 至多一次 | 全局单锁 + 仅恢复持锁实例的 topic（18、86） | 非持锁实例的 topic 长期不被恢复 | `GXRedissonStreamReliabilityAuditTest` |

## 5. 并发与生命周期矩阵

| 组件 | 创建时机 | 并发度来源 | 停止路径 | 泄漏风险 |
| --- | --- | --- | --- | --- |
| `fetchExecutor`（虚拟线程） | 构造器 | 每队列 1 个抓取任务 | `destroy` → `shutdownExecutor` | 低 |
| `workerExecutor`（虚拟线程） | 构造器 | `getConsumerCountForQueue`（受注册顺序影响） | 同上 | 中：F-07 |
| `retryScheduler`（2 线程 daemon） | 构造器 | `scheduleRetry` | 同上 | 中：任务持有 `PendingMessage`，最长 93 秒（F-06） |
| `RDelayedQueue` 转移任务（上游） | `getDelayedQueue` 调用 | 上游 `QueueTransferService` 引用计数 | 仅 `destroy()` 减计数 | 中：F-08、F-16 |
| stream 消费者/调度器 | `GXRedissonStreamMQManager.start()` | `consumerThreads` | `stop()`（受 `started` 守卫） | 中：半途失败不可回收（F-14） |

**在途消息处理**：延迟转发 `destroy()` 会把本地队列与 in-flight 全部回投 Redis（`requeuePendingMessage`），回投失败则仅留内存；stream 侧 `stop()` 不处理 pending，依赖下次启动的恢复任务。

### 5.1 四条释放路径逐项判定（SC-003）

| 组件 | 重复注册 | 启动失败 | 容器关闭 | Redis 关闭中 |
| --- | --- | --- | --- | --- |
| `fetchExecutor` | 不新增第二个抓取任务，重复注册在 `startListenerTask` 早退（F-08 的竞争窗口仅泄漏队列对象，不泄漏线程） | 线程已提交；`startListenerTask` 的 catch 会 `cancel(true)` 并回滚 `listenerConfigs`（`DelayMQPostProcessor:145-152`） | `destroy` → `shutdownExecutor`（先 `shutdown`，30s 未收敛再 `shutdownNow`） | `isRedissonShutdownException` 命中后置 `redissonShutdown=true` 并退出循环（193-196） |
| `workerExecutor` | 消费者数随注册顺序变化（F-07），但线程不会因重复注册而叠加 | 同上，随 `listenerConfigs` 回滚一并 cancel | 同上；消费者循环在 1s 内退出 | 循环条件含 `!redissonShutdown`，命中后退出；在途消息由 `destroy` 回投 |
| `retryScheduler` | 线程池只在构造器创建一次，重复注册不新增 | `schedule` 抛异常时回投消息到阻塞队列（340-343） | `shutdownExecutor`；未执行任务对应的消息仍在 `inFlightMessages`，由 `requeueInFlightMessages` 回投 | `scheduleRetry` 先判 `redissonShutdown` → 回投，不再调度 |
| `RDelayedQueue` 转移任务（上游） | **未回收**：引用计数只增不减（F-08） | **未回收**：`putIfAbsent` 失败路径未 `destroy()`（F-08） | `destroyDelayedQueue` 对每个配置减 1 次计数；本模块另有 `GXRedissonDelayMQUtils` 的静态缓存也持有一份，需两处都释放 | `destroy()` 抛 shutdown 异常时被忽略（401-404），不阻断关闭 |
| stream 消费者/调度器 | `start` 由 `started`/`running` 守卫，重复调用直接返回 | **不可回收**：`started` 未置位，`stop()` 直接返回（F-14） | `stop()` 关闭消费者与两个调度器；pending 不处理，依赖下次启动恢复 | 读循环捕获异常后 1s 退避重试，未识别 shutdown 语义，会持续重试直至 `stop()` |

**判定汇总**：20 格中 4 格不达标（`RDelayedQueue` 的"重复注册/启动失败"、stream 的"启动失败/Redis 关闭中"），其余 16 格达标；不达标项分别对应 F-08 与 F-14。

### 5.2 共享可变状态与竞态判定（FR-004）

FR-004 要求线程安全结论覆盖三类场景，逐类落点如下：

| FR-004 场景 | 具体对象 | 结论 | 证据 / 用例 |
| --- | --- | --- | --- |
| 共享可变状态 | `GXRedissonMQUtils` 的 `static TOPIC_CACHE` / `LISTENER_REGISTRATION_CACHE` | 缺陷：静态缓存绑定首个客户端，跨上下文存活 | F-09；`GXRedissonTopicSubscriptionAuditTest#topicCacheKeepsFirstClientInstance` |
| 共享可变状态 | `properties/nacos/*` 热更新实例的 `long/int` 字段（非 `volatile`） | 风险：Nacos 刷新线程写入、消费者虚拟线程读取，缺少显式 happens-before；字段为原生类型与引用赋值，实践中不产生对象撕裂，故不单列 Finding，登记于覆盖台账 | `checklists/coverage-ledger.md` nacos 四行（风险） |
| 共享可变状态 | `inFlightMessages`（`ConcurrentHashMap`）、`localQueue`（`LinkedBlockingQueue`） | 无缺陷：容器本身线程安全；容量语义问题另见 F-05 | F-05；`GXRedissonDelayForwardingAuditTest#inFlightMessagesGrowWithoutBackpressure` |
| 生命周期 | 抓取/消费/重试线程池、`RDelayedQueue` 转移任务、stream 消费者与调度器 | 见 §5 与 §5.1：20 格中 4 格不达标 | F-07、F-08、F-14；`GXRedissonConsumerConcurrencyAuditTest` |
| 竞态 | 延迟队列重复注册（check-then-`putIfAbsent`） | 缺陷：竞争时后到者创建的队列无人销毁，引用计数只增不减 | F-08；依赖事实 E-13 |
| 竞态 | stream 去重（`containsKey` → `handle` → `fastPut` → `ack`） | 缺陷：非原子，且 `markProcessed` 失败仍 `ack` | F-12；`GXRedissonStreamReliabilityAuditTest#ackHappensWhenDedupeMarkerFails` |

**结论**：三类场景均有判定与落点，其中共享可变状态 1 项缺陷、1 项风险、1 项无缺陷；生命周期与竞态的不达标项已在 §3 定级。Nacos 字段可见性因"影响可观测性低、无法在无 Nacos 环境复现"而保持风险级，不升级为缺陷。

## 6. 性能口径

| 路径 | 单条往返次数 | 单轮上限 | 临界区 | 复算方式 |
| --- | --- | --- | --- | --- |
| 延迟队列转发 | 抓取 1（Lua `drainTo`）+ 失败回退 1（`offer`）+ 重投 1（`delayedQueue.offer`） | 20 条/轮 | 本地 `LinkedBlockingQueue`（5000），无分布式锁 | `verify(blockingQueue, times(1)).drainTo(...)` |
| stream 延迟转移 | 6（`hash.get`/`transferSet.add`/`zset.remove`/`publish`/`hash.remove`/`transferSet.remove`） | 200 条/topic/轮 | **全局锁** `mq:delay:lock` 覆盖全部 topic | 用例逐操作 `verify(times(1))` |
| stream 立即消费 | 4（`containsKey`×2 + `fastPut` + `ack`），去重命中 1 | `batchSize`（默认 10） | 无锁，逐条往返 | `GXRedissonStreamReliabilityAuditTest` + `verify` 计数 |
| 锁工具 | 获取 1（`tryLock`）+ 释放 3（`isLocked`/`isHeldByCurrentThread`/`unlock`） | — | 锁本身 | `GXRedissonLockSemanticsAuditTest` |
| 可靠主题批量发布（`GXRedissonMQUtils#publishBatch`） | 每条 1（`reliableTopic.publish`）；`null` 元素跳过，不产生往返 | 无内建上限，等于入参 `List` 大小 | 无锁，逐条串行 | `verify(reliableTopic, times(n)).publish(any())` |

**批量发布的失败口径（US3/AC4）**：`publishBatch` 对每条消息独立 `publish`，成功则 `successCount++`；失败只 `LOGGER.error` 并继续下一条，**不重试、不回滚、不抛出**，最终返回 `successCount`。因此"是否静默吞掉"的答案是：**日志可见、异常不可见**——调用方只能通过 `返回值 < 输入条数` 推断存在失败，且无法区分"失败"与"输入含 `null` 被跳过"。1000 条输入的复算方式：stub 让 `publish` 对前 k 条抛异常，断言返回 `1000 - k - nullCount`，并用 `verify(topic, times(1000 - nullCount)).publish(any())` 核对往返次数；耗时不写固定毫秒，只记录"往返次数 = 有效条数"。

## 7. 契约对照表

| 文档承诺 / 签名 | 实现位置 | 判定 |
| --- | --- | --- |
| `AGENTS.md`：可靠主题订阅幂等 | `GXRedissonMQUtils:81-93` | 不一致：幂等依赖缓存，缓存失效后无法自愈（F-01） |
| `AGENTS.md`：延迟转发失败后的重试、回退和清理策略必须保持可观测 | `GXRedissonDelayMQPostProcessor:322-375` | 部分一致：有 warn/error 日志，但"至多一次"语义未在文档声明（F-02） |
| `AGENTS.md`：缓存 TTL/批量写入/空参数处理语义明确 | `GXRedissonCacheServiceImpl:88-108` | 不一致：TTL=-1 行为仅由用例体现，文档未写明（F-15） |
| `AGENTS.md`：锁/计数器必须明确释放、超时和异常封装语义 | `GXRedissonUtils:111-119,175-198` | 不一致：超时返回 `null`、异常返回 `0`（F-11） |
| `GXRedissonDelayMQToTopic.timeout()` 默认 1800 | 与 `DEFAULT_POLL_TIMEOUT_SECONDS = 30` 并存（`DelayMQPostProcessor:54,100`） | 不一致：默认值实际为 30 分钟，常量只对 `timeout <= 0` 生效（F-14） |
| `GXRedissonStreamMQProperties.blockingTimeoutMillis >= 0` | `validate()` 第 73 行 | 一致但不充分：0 等价于永久阻塞（F-14） |
| 配置键 `maple.framework.mq.redisson.enable` | `GXRedissonMQConfig`/`GXRedissonDelayMQPostProcessor` | 一致：默认关闭，两条链路同开关 |
| `application.yml` 排除官方自动配置 | `src/main/resources/application.yml` | 一致：与自装配互斥，无重复 Bean |

### 7.1 日志级别与可观测性判定（FR-013）

判定口径：**"已启用但未生效"必须 warn 及以上**；"类路径无该能力/未启用"允许 debug；静默降级（无日志）视为不达标。

| 失败路径 | 代码位置 | 现级别 | 判定 |
| --- | --- | --- | --- |
| 无 handler 时丢弃消息 | `GXRedissonStreamImmediateMQ:196-200` | warn | 不达标：后果是消息丢失，级别应为 error，且不应以 ack 结束 |
| 畸形消息 DLQ 写入失败 | `GXRedissonStreamImmediateMQ:184-190` | error | 达标（有 error 且不 ack），但缺少重试计数导致"毒消息"无法升级（F-03） |
| `markProcessed` 失败仍 ack | `GXRedissonStreamImmediateMQ:226-228` | warn | 不达标：去重窗口失效属"已生效但不可靠"，应 error 或不 ack（F-12） |
| `markDispatched` 失败 | `GXRedissonStreamImmediateMQ:251-255` | warn | 部分达标：有日志、有 `transferSet` 兜底，但同样在失败后 ack |
| 消费者组创建失败（非 BUSYGROUP） | `GXRedissonStreamImmediateMQ:354-359` | warn | 不达标：网络/权限类硬失败被降级为 warn，随后每秒报错（F-14 相关） |
| 转发重试耗尽、回投延迟队列 | `GXRedissonDelayMQPostProcessor:361-375` | error | 达标：含次数与队列名，可检索 |
| 订阅缓存移除失败 | `GXRedissonMQUtils:141-144` | error | 达标（日志维度），但缓存未同步清理（F-01） |
| 容器关闭中 `RDelayedQueue.destroy` 失败 | `GXRedissonDelayMQPostProcessor:397-405` | warn / 静默忽略 shutdown 异常 | 达标：放行关闭，不制造噪声 |
| `GXRedissonStreamMQManager.start()` 半途失败 | `GXRedissonStreamMQManager:53-58` | 无日志 | 不达标：仅向上抛异常，缺少"哪个组件启动成功/失败"的定位信息（F-14） |
| 类路径无 `redissonMQClient`（未启用 MQ） | `@ConditionalOnBean(name="redissonMQClient")` | 无日志 | 达标：属正常形态，不要求告警 |

**结论**：10 条失败路径中 4 条不达标（无 handler 丢弃、`markProcessed` 失败、建组硬失败、启动半途失败），其余 6 条级别与后果匹配。不达标项已并入 F-03/F-12/F-14 的修复建议，不单列新发现。

## 8. 未验证假设

（无）

> 说明：所有发现均具备 S/D/T 级证据。F-08 的并发窗口未用用例复现（避免不稳定测试），其结论由 E-06（代码）与 E-13（上游引用计数）共同支撑，已标注证据等级 D。

## 9. 证据附录

| 编号 | 等级 | 来源 | 摘录 | 被引用 |
| --- | --- | --- | --- | --- |
| E-01 | S | `GXRedissonMQUtils.java:81-93,136-144` | `computeIfAbsent` 命中即返回旧 `listenerId`；`unsubscribe` 异常前不清理缓存 | F-01 |
| E-02 | S | `GXRedissonDelayMQPostProcessor.java:176-188` | 消息移出 Redis 后写入本地 `inFlightMessages` | F-02 |
| E-03 | S | `GXRedissonDelayMQPostProcessor.java:278-320` | 提交 `CompletableFuture` 后立即返回，不等待结果 | F-05 |
| E-04 | S | `GXRedissonDelayMQPostProcessor.java:296,322-344` | `delay = RETRY_DELAY * attempt`（1s/2s），`orTimeout` 30s，重试上限 3 | F-06 |
| E-05 | S | `GXRedissonDelayMQPostProcessor.java:132,244-248` | `min(4, max(1, 池大小 / listenerConfigs.size()))` | F-07、F-14 |
| E-06 | S | `GXRedissonDelayMQPostProcessor.java:111-115,132-136` | 已注册即 return；`putIfAbsent` 失败时未销毁已创建的队列对象 | F-08 |
| E-07 | S | `GXRedissonMQUtils.java:32-33,254-257`；`GXRedissonMQPostProcessor.java:108-127` | 静态缓存绑定首个客户端；清理仅在缓存为空时执行 | F-09 |
| E-08 | S | `GXRedissonStreamImmediateMQ.java:184-200,224-229` | 无 handler 直接 `ack`；`markProcessed` 失败仍 `ack` | F-03、F-12 |
| E-09 | S | `GXRedissonStreamDelayedMQ.java:149-166,187-210` | 固定锁 `mq:delay:lock` + 6 步串行操作 + 200 条上限 | F-04 |
| E-10 | S | `GXRedissonMQUtils.java:294-302` | 业务异常被 `catch` 后仅记录日志 | F-10 |
| E-11 | S | `GXRedissonUtils.java:111-119,175-198,362-370` | 超时返回 `null`；释放 3 次调用；计数器异常返回 0 | F-11 |
| E-12 | S | `GXRedissonStreamMQManager.java:53-67` | 三个组件依次启动后置 `started = true` | F-14 |
| E-13 | D | `Redisson.java`（4.7.0）`getDelayedQueue`；`QueueTransferService#schedule/remove` | 构造即打印弃用 error；引用计数 `incUsage/decUsage`。实机 2026-10-22 已复现该 error 日志 | F-08、F-16 |
| E-14 | D | `RedissonBlockingQueue#drainToAsync(Collection,int)` | 单段 Lua（`lrange`+`ltrim`），1 次往返 | F-05 |
| E-15 | D | `RedissonReliableTopic#removeListenerAsync` | 先同步移除本地 listeners，再异步清理消费组 | F-01 |

## 10. 复现索引

| 发现 | 复现入口 |
| --- | --- |
| F-01 | `GXRedissonTopicSubscriptionAuditTest#subscribeReturnsStaleListenerIdWhenUnsubscribeFails` |
| F-02 | `GXRedissonDelayForwardingAuditTest#requeueFailureKeepsMessageInMemoryOnly` |
| F-03 | `GXRedissonStreamReliabilityAuditTest#messageWithoutHandlerIsAcknowledged` |
| F-04 | `GXRedissonStreamTransferAuditTest#oneDueMessageCostsSixRoundTrips`、`#oneGlobalLockSerialisesEveryTopic` |
| F-05 | `GXRedissonDelayForwardingAuditTest#inFlightMessagesGrowWithoutBackpressure` |
| F-06 | `GXRedissonDelayForwardingAuditTest#retryDelayIsFixedLinearStep` |
| F-07 | `GXRedissonConsumerConcurrencyAuditTest#consumerCountDependsOnRegisteredQueueCount` |
| F-08 | `GXRedissonDelayForwardingAuditTest#duplicateRegistrationSkipsSecondDelayedQueue` + `checklists/dependency-facts.md` D-3 |
| F-09 | `GXRedissonTopicSubscriptionAuditTest#topicCacheKeepsFirstClientInstance` |
| F-10 | `GXRedissonTopicSubscriptionAuditTest#businessListenerExceptionIsSwallowed` |
| F-11 | `GXRedissonLockSemanticsAuditTest`（3 个用例） |
| F-12 | `GXRedissonStreamReliabilityAuditTest#ackHappensWhenDedupeMarkerFails`、`#pendingRecoveryScansOnlyHeadOfPendingList` |
| F-13 | `GXRedissonConnectConfigAuditTest`（3 个用例） |
| F-14 | `GXRedissonConsumerConcurrencyAuditTest#stopDoesNotReleaseAfterPartialStartFailure`、`#zeroBlockingTimeoutIsAccepted` |
| F-15 | `mvn -pl leaf-base-redisson -o test -Dtest=RedissonUtilityRegressionTest` |
| F-16 | `checklists/dependency-facts.md` D-4（弃用日志核对命令）；实机用例 `GXRedissonRealServiceVerificationTest#delayedQueueDeliversExpiredMessage` 运行日志可见同一条 error |

统一执行：

```powershell
mvn -pl leaf-base-redisson -o test -Dtest="GXRedisson*AuditTest"
```

预期：`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`（默认不含真实服务类）。

真实服务（可选，凭据走环境变量）：

```powershell
$env:AUDIT_REDIS_HOST="<redis-host>"
$env:AUDIT_REDIS_PASSWORD="<env only>"
mvn -pl leaf-base-redisson -o test -Dtest="GXRedissonRealServiceVerificationTest"
```

预期：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。该验证用于确认"正常路径确实可用"——它不推翻任何缺陷结论，只把 F-01/F-11/F-16 的定性从"仅静态推断"补齐为"实机可复现其正常路径与弃用告警"。
