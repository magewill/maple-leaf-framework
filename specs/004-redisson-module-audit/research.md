# Phase 0 Research: leaf-base-redisson 审查方法与口径

**Feature**: `004-redisson-module-audit` | **Date**: 2026-10-22

本文件解决 plan 阶段的技术未知项。所有结论要么来自本仓库源码位置，要么来自本地依赖产物 `redisson-4.7.0-sources.jar`（`F:\repository\org\redisson\redisson\4.7.0`），不依赖网络资料与真实 Redis 实例。

## R-01 审查证据分级：源码事实 ＞ 依赖事实 ＞ 可执行验证

**Decision**: 每条结论必须归属以下三级证据之一，报告字段 `证据等级` 记录：

| 等级 | 含义 | 判定方式 |
| --- | --- | --- |
| S（Source） | 本仓库代码可直接定位 | 文件 + 行区间 + 关键语句摘录 |
| D（Dependency） | 依赖库实际实现决定的行为 | `redisson-4.7.0-sources.jar` 中类名 + 方法名 + 语义摘录 |
| T（Test） | 可用 Mock 用例复现 | 用例名 + 断言点 + 期望现象 |

**Rationale**: 审查最常见的失效模式是"读起来像缺陷"与"实际行为是缺陷"混为一谈。分级后，`未验证假设` 有明确出口（无 S/D/T 支撑），不会污染已确认结论（FR-003）。

**Alternatives considered**: 只用 T 级（可执行验证）——被否决：性能与锁粒度类结论无法在不压测的前提下用断言表达，强行要求会得出"无法审查"的结论；只用 S 级——被否决：F-01/F-08 的严重性正是由依赖实现决定的。

## R-02 依赖侧事实核对结果（决定四条结论的成立与措辞）

**Decision**: 采信以下四项依赖事实，并据此修订发现措辞：

1. **可靠主题订阅不可自愈（支撑 F-01）**：`RedissonReliableTopic.removeListenerAsync` 先执行 `listeners.keySet().removeAll(...)`（本地同步移除），再异步 `evalWriteAsync` 清理 Redis 消费组与超时集合，且当 `listeners.isEmpty()` 时取消 `readFuture` 与 `timeoutTask`。因此"移除失败"时：本地已无监听器 → 不再投递；而 `GXRedissonMQUtils` 的缓存条目仍在 → 后续 `subscribe` 的 `computeIfAbsent` 直接返回旧 id，调用方拿到"订阅成功"。
2. **抓取批次本身是单次往返（修正 F-05 措辞）**：`RedissonBlockingQueue.drainTo(c, maxElements)` 走 `drainToAsync(c, maxElements)`，实现为一段 Lua（`lrange` + `ltrim`），**1 次网络往返**；`drainTo(c)` 亦为单次 Lua。故"每批 20 条要 20 次往返"的猜测不成立，F-05 只主张"in-flight 无界 + 无背压"，不得主张"抓取路径低效"。
3. **延迟队列转移任务按队列名引用计数（改写 F-08 后果）**：`QueueTransferService.schedule(name, task)` 在已有同名任务时只 `incUsage()` 并复用；`remove(name)` 在 `decUsage() == 0` 时才 `stop()`。`Redisson.getDelayedQueue(queue)` **每次都 new 一个 `RedissonDelayedQueue`**（不做实例复用），构造即 `schedule`。因此"重复注册未 `destroy()`"的真实后果是**计数虚高 → 容器关闭后转移任务仍不停**，而不是"新增第二个后台任务"。
4. **`RDelayedQueue` 已被上游弃用（新增 F-16）**：`Redisson.getDelayedQueue` 首次调用即打印 `RDelayedQueue object is deprecated due to github issues #3020, #2998, #1057. Use RReliableQueue object instead.`（error 级），替代方案为 `RReliableQueue`。

**Rationale**: 这四项直接改变了三条发现的严重性与措辞。若不核对，报告会出现"抓取低效"这类与实现相反的结论，属于审查质量事故（SC-002）。

**Alternatives considered**: 直接采信阅读印象——被否决：F-05 的初判即被推翻，说明必须核对依赖；把依赖 jar 反编译后写入报告附录——被否决：报告应给"结论 + 一句可核对摘录"，不复制上游源码。

## R-03 审计用例形态：特征化测试（CURRENT BEHAVIOR），兼作修复验收开关

**Decision**: 新增 7 个审计用例类，放在 `cn.maple.redisson.audit`，断言**当前实际行为**（含缺陷行为），类名 `GX<能力>AuditTest`，每个用例注释与显示名标注 `CURRENT BEHAVIOR`，例如：

- `GXRedissonTopicSubscriptionAuditTest`：使 `removeListener` 抛异常后再 `subscribe`，断言"返回非空 listenerId 且未发生第二次 `addListener`"——即锁住 F-01。
- `GXRedissonDelayForwardingAuditTest`：断言 `destroy()` 后 `inFlightMessages` 清空依赖重投成功；构造"重投失败"分支断言消息只留在内存（F-02）。
- `GXRedissonConsumerConcurrencyAuditTest`：以两个队列、不同注册顺序断言 `getConsumerCountForQueue` 差异（F-07）。

**Rationale**: 本规格不改生产代码（FR-011），若用例断言"期望行为"会直接失败，流水线不可用。特征化测试既提供 T 级证据，又能在修复时通过"断言翻转"验证改动——满足 FR-010 的"旧代码失败、新代码通过"设计前提（翻转后即为该回归测试）。

**Alternatives considered**: 用 `@Disabled` 承载期望断言——被否决：禁用用例不产生证据、不守护行为；只写文档不写测试——被否决：SC-007 要求高危发现可用例复现。

## R-04 并发验证手法：Mockito + 反射 + CountDownLatch，不启真实容器

**Decision**: 并发与生命周期结论一律用既有手法验证：`ReflectionTestUtils` 注入 `ApplicationContext`/执行器，`Mockito.mockStatic(GXSpringContextUtils.class)` 提供客户端，`CountDownLatch` 控制线程时序。仅当需要证明"Bean 生命周期语义"（如 `DisposableBean.destroy` 与 `SmartLifecycle.stop` 的调用次序）时使用 `ApplicationContextRunner`——`spring-boot-starter-test` 已在依赖中，无需新增依赖。

**Rationale**: 与 `RedissonUtilityRegressionTest` 的既有风格一致（该文件已用 `ReflectionTestUtils` + `MockedStatic` + `CountDownLatch`），维护者无需学习第二套测试范式；同时满足"无外部中间件"的宪法门槛。

**Alternatives considered**: Testcontainers + 真实 Redis——被否决：违反"缺环境可优雅跳过"的约束，且容器启动会让审计用例从秒级退化到分钟级；`@SpringBootTest` 全量容器——被否决：51 个文件里只有少量装配相关结论需要容器语义。

## R-05 性能口径：解析式计数 + `verify` 次数复算，不做压测

**Decision**: 每条性能结论必须给出三元组 `(单条消息往返次数, 单轮处理上限, 临界区持锁范围)`，并附复算方式：

| 路径 | 往返构成 | 单轮上限 | 临界区 |
| --- | --- | --- | --- |
| 延迟队列转发（`GXRedissonDelayMQPostProcessor`） | 抓取 1（`drainTo` Lua）+ 本地入队 0；失败回退 1（`offer`）+ 延迟重投 1 | `BATCH_POLL_SIZE = 20`/轮 | 无分布式锁；临界区为本地 `LinkedBlockingQueue`（5000） |
| stream 延迟转移（`GXRedissonStreamDelayedMQ`） | `hash.get` + `transferSet.add` + `zset.remove` + `publish` + `hash.remove` + `transferSet.remove` = 6 | `MAX_TRANSFER_PER_ROUND = 200`/轮 | **全局固定锁** `mq:delay:lock` 覆盖全部 topic |
| stream 立即消费（`GXRedissonStreamImmediateMQ`） | `containsKey`×2 + `fastPut` + `ack` = 4（去重命中则 1） | `batchSize`/次读取 | 无锁；每条逐次往返 |

复算方式：Mock 客户端 + `verify(mockClient, times(n))` 统计调用次数；锁窗口以"单轮上限 × 单条往返数 × 平均 RTT"表达为关系式，不填具体毫秒（避免伪造测量值）。

**Rationale**: FR-005 要求可复算口径。真实 RTT 依赖部署环境，任何绝对毫秒数在本仓库都无法自证；用"次数 × 上限"可被任何读者按代码复算，且不随环境漂移。

**Alternatives considered**: JMH 微基准——被否决：需真实网络栈与 Redis，且与"审查不改代码"的定位不匹配；只做定性描述——被否决：违反 SC-004。

## R-06 覆盖率台账：可脚本复现的静态核对，不引入覆盖率插件

**Decision**: 51 个生产文件的覆盖判定采用"测试类引用扫描 + 手工判定"两段式：先用 `Select-String` 扫出测试文件对生产类型的直接引用，再对未引用类型逐一给出"不可测原因"（如纯 `package-info`、纯 `record`、仅被反射使用）。台账字段见 `data-model.md` 的"覆盖率台账"。

**Rationale**: 引入 JaCoCo 需要改 `pom.xml`（违反"零生产改动"与"不新增依赖"的自我约束），且覆盖率数字不能回答"哪个类完全没被触及"这一真正问题；静态引用扫描配合人工判定更贴近宪法"已知偏差登记"的诉求。

**Alternatives considered**: 临时加 JaCoCo 参数跑一次再撤——被否决：`pom.xml` 改动会污染审查基线，且报告读者无法复现；只给"未覆盖清单"不给原因——被否决：无法据此排期。

## R-07 缺陷 / 风险 / 既定契约 / 未验证假设的判定规则

**Decision**: 双条件判定：

- **缺陷**：与 `AGENTS.md` 或公开方法语义承诺冲突，且具备 S/D/T 级证据 → 计为缺陷（F-01/F-02/F-03/F-04/F-10/F-11/F-13 等）。
- **风险**：当前行为可接受，但条件变化（上游升级、负载上升、配置变更）即失效 → 计为风险（F-09/F-16）。
- **既定契约**：已被既有用例锁定且文档未作相反承诺 → 只要求文档同步，不计缺陷（F-15）。
- **未验证假设**：无 S/D/T 支撑 → 单列附录，不得进入发现清单。

**Rationale**: FR-014 要求区分契约与缺陷，否则计划阶段会把"测试已锁定的行为"当 bug 改掉，引发下游回归。F-15（`updateCacheExpiredTime` 对 TTL=-1 直接返回 `true`）已有用例 `updateCacheExpiredTimeKeepsExistingNoExpiryEntry` 锁定，属典型例证。

**Alternatives considered**: 一律计为缺陷——被否决：会与既有用例冲突，且违反宪法"稳定契约优先"；一律降级为建议——被否决：F-01 这类会造成消息丢失的问题必须显式定级。

## R-08 修复建议的分层口径（不进本次实现）

**Decision**: 报告为每条发现给出 `建议批次`：

- **P0（阻断消息可靠性）**：F-01、F-02、F-03；
- **P1（性能与资源）**：F-04、F-05、F-08、F-09、F-14；
- **P2（语义与可观测）**：F-06、F-07、F-10、F-11、F-12、F-13、F-16；
- **仅文档**：F-15。

每条建议必须写明"对现有成功路径与配置语义的影响"，供后续规格审批（FR-011）。

**Rationale**: 审查的价值在于可排期。批次划分依据"影响面 × 触发概率 × 可观测性"，与 SC-007 对齐；同时避免把修复方案写成设计文档（超出本次范围）。

**Alternatives considered**: 只给级别不给批次——被否决：级别不含"先修哪个"的信息；直接给出补丁——被否决：违反"不改生产代码"。

## R-09 无 `NEEDS CLARIFICATION` 残留

**Decision**: Technical Context 中的全部字段均已确定（语言、依赖、测试命令、平台、性能口径、约束、规模），无待澄清项。

**Rationale**: 两处实质分歧（是否包含修复、是否覆盖 stream 链路）已在 `spec.md` 的 Clarifications 中定案：本次只出结论与基线，两条链路同权审查。
