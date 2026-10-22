---

description: "Task list for leaf-base-rocketmq 审查与修复"
---

# Tasks: leaf-base-rocketmq 逻辑正确性、线程安全与性能审查

**Input**: Design documents from `specs/006-rocketmq-module-audit/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: 本特性**要求测试**——spec FR-009（既有用例全部执行）、FR-012（每条发现附回归设计）、FR-019（真实 Broker 集成用例）、AGENTS.md「测试要求」均明确要求；因此每个修复任务前都有对应的失败回归用例。

**Organization**: 任务按 6 个用户故事分组，每个故事可独立实现与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1–US6）
- 所有路径相对于仓库根 `e:/project/java/maple-leaf-framework`

## Path Conventions

- 生产代码：`leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/`
- 资源：`leaf-base-rocketmq/src/main/resources/META-INF/`
- 测试：`leaf-base-rocketmq/src/test/java/cn/maple/rocketmq/`
- 设计产物：`specs/006-rocketmq-module-audit/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立交付骨架与包结构，不做任何行为变更

- [X] T001 创建审查报告骨架 `specs/006-rocketmq-module-audit/review-report.md`，包含"18 个生产文件覆盖表、E-xx/F-xx 证据索引、27 个既有用例结果、真实 Broker 实测记录"四个固定小节
- [X] T002 运行 `mvn -pl leaf-base-rocketmq -am test` 并原样记录输出摘要（27 执行 / 1 失败：`GXRocketMQConfigPropertiesTest$LocalConfigSourceTest.loadsLocalConfigSource`，`Expected size: 1 but was: 0`）到 `specs/006-rocketmq-module-audit/review-report.md`
- [X] T003 [P] 创建 `leaf-base-rocketmq/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 空骨架（`{"groups":[],"properties":[]}`），该文件当前缺失
- [X] T004 [P] 创建 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/exception/package-info.java` 与 `handler/package-info.java`，均标注 `@org.jspecify.annotations.NullUnmarked`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: US2/US3 共用的新类型与配置元数据；**完成前任何故事不得开始编码**

**⚠️ CRITICAL**: 本阶段完成前 US2–US4 的修复任务不得启动

- [X] T005 [P] 新增 `GXCdcDispatchException` 于 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/exception/GXCdcDispatchException.java`：继承 `GXBusinessException`，字段 `int code`、`int failedListenerCount`、`String bizType`、`String op`、`String tsMs`；不变量：`failedListenerCount >= 1` 且 `getSuppressed().length == failedListenerCount`，消息体不含原始消息全文
- [X] T006 [P] 新增 `GXCdcDispatchStatistics` 于 `cn/maple/rocketmq/dispatcher/GXCdcDispatchStatistics.java`：`LongAdder` 五键 `success / parseFailure / unknownOperation / listenerFailure / deadLetter`；构造注入 `ObjectProvider<MeterRegistry>`，仅当存在时注册 `rocketmq.cdc.dispatch.*` 计数
- [X] T007 [P] 新增 `GXRocketMQAsyncSendFailureContext` record 于 `cn/maple/rocketmq/handler/GXRocketMQAsyncSendFailureContext.java`：`topic`、`tag`、`messageKey`（均非空，空串表示未设置）、`String msgId`（可空）、`SendStatus sendStatus`（可空）、`Throwable cause`（可空）；不变量：`sendStatus == null` ⟺ `cause != null`
- [X] T008 [P] 新增函数式接口 `GXRocketMQAsyncSendFailureHandler` 于 `cn/maple/rocketmq/handler/GXRocketMQAsyncSendFailureHandler.java`，唯一方法 `void onFailure(GXRocketMQAsyncSendFailureContext context)`
- [X] T009 在 `leaf-base-rocketmq/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 补两项：`maple.framework.cdc.active-status`（boolean，默认回退旧键 `cdc.active.status`）、`maple.framework.cdc.dead-letter-topic`（String，默认空串=不启用），含中文 description 与 defaultValues（依赖 T003）

---

## Phase 3: User Story 1 - 审查结论可复核、可用真实 Broker 复现 (Priority: P1)

**Goal**: 交付可逐条复核的分级结论，每条都能跳到代码位置与复现方式

**Independent Test**: 随机抽取任一发现，按报告给出的位置与命令复现，现象与结论一致

### Tests (证据固化)

- [X] T010 [US1] 在 `specs/006-rocketmq-module-audit/review-report.md` 逐文件填写 18 个生产文件的判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因 + 行区间），不得留空
- [X] T011 [P] [US1] 记录 27 个既有用例的执行与失败详情（含失败断言原文与根因归因到 E-01），写入 `review-report.md` 的"基线"小节
- [X] T012 [P] [US1] 为 F-01 / F-02 / F-03 各写"故障现场构造步骤"（输入、环境、期望现象），写入 `review-report.md`
- [X] T013 [US1] 复核 `review-report.md` 与 `spec.md` 的 E-xx / F-xx / FR 编号与数值一致（含 1304µs、2304µs、2690/s 等实测值）

**Checkpoint**: US1 可独立交付——报告成形即可验收，不依赖任何生产代码改动

---

## Phase 4: User Story 2 - 发送链路语义与配置装配可验证 (Priority: P1)

**Goal**: 让 `rocketmq.config-source` 真正生效；补齐发送校验与异步失败出口；真实 Broker 用例入库

**Independent Test**: 三态装配断言 + 发送契约断言 + env 门控真实 Broker 用例；全部可在无 Broker 环境下通过（IT 自动跳过）

### Tests (先行，必须先失败)

- [X] T014 [P] [US2] 重写 `leaf-base-rocketmq/src/test/java/cn/maple/rocketmq/properties/GXRocketMQConfigPropertiesTest.java`：四态（`local` / `nacos` / 未配置 / `bogus`）各一例，断言 Bean 存在性、`rocketmq.test-marker` 可读性、bogus 启动失败消息含 `unsupported`
- [X] T015 [P] [US2] 在 `src/test/java/cn/maple/rocketmq/service/impl/GXSendRocketMQServiceImplTest.java` 补校验用例：`null` body 抛异常、topic 含 `:` 抛异常、topic 超 127 字符、tag 超 128 / 含 `:`、`messageKey` 超 255、body 超 `max-message-size`，均断言 `verifyNoInteractions(rocketMQTemplate)`
- [X] T016 [P] [US2] 新增 `src/test/java/cn/maple/rocketmq/handler/GXRocketMQAsyncSendFailureHandlerTest.java`：无实现仅日志、有实现收到 context、实现抛异常不影响后续实现、两实现按 `@Order` 执行、`sendAsync` 仍返回 `true` 且 `asyncSend` 仅调用一次
- [X] T017 [P] [US2] 新增 `src/test/java/cn/maple/rocketmq/integration/GXRocketMQRealBrokerIT.java`：`@Tag("integration")` + `@EnabledIfEnvironmentVariable(named="ROCKETMQ_ACCESS_KEY")` 等三门控；覆盖 5 类发送、错误 secret 触发 `GXBusinessException`、8 线程×25 条并发采样并打印 `count/avg/p50/p95/max`

### Implementation

- [X] T018 [US2] 实现三态条件装配：在 `cn/maple/rocketmq/properties/` 新增 `GXRocketMQConfigSourceCondition`（`AnyNestedCondition` 三分支），改造 `GXLocalRocketMQConfigProperties.java` 与 `GXNacosRocketMQConfigProperties.java` 的条件注解（依赖 T014 失败）
- [X] T019 [US2] 修复本地源路径与日志：`leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/properties/local/GXLocalRocketMQConfigProperties.java` 的 `@PropertySource` 改为 `classpath:/${spring.profiles.active:default}/rocket-mq.yml`，装配时输出 `RocketMQ config source: local, profile=<p>, resource=<path|NOT_FOUND>` 及组件扫描提示行
- [X] T020 [US2] 收敛 Nacos 分支：`leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/properties/nacos/GXNacosRocketMQConfigProperties.java` 移除"注解即生效"的假象，新增启动校验（`config-source=nacos` 且 `rocketmq.name-server` 为空 → 抛 `GXBusinessException`；`producer.group` 为空 → `warn`），日志写明依赖 `spring.config.import=nacos:rocket-mq.yml`
- [X] T021 [US2] 补齐 `GXSendRocketMQServiceImpl` 前置校验：topic 正则 `^[%a-zA-Z0-9_-]{1,127}$`、tag ≤128 且不含 `:`、messageKey ≤255、body UTF-8 ≤ `max-message-size`、拒绝字面量 `"null"`；全部在触达 Broker 前抛出
- [X] T022 [US2] 修复 `GXRocketMQMessageReqDto` 的 `Object` 构造器：`null` 直接抛 `GXBusinessException`（不再生成 `"null"`），并在 javadoc 中说明 `(Object)` 与 `(String, Object)` 的重载歧义与正确用法
- [X] T023 [US2] 接入异步失败扩展点：`GXSendRocketMQServiceImpl` 构造器增加 `ObjectProvider<GXRocketMQAsyncSendFailureHandler>`；`onSuccess(null)` / 非 `SEND_OK` / `onException` 三路逐个隔离调用，无实现时仅 `error` 日志

**Checkpoint**: US2 独立可验收——`mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQConfigPropertiesTest,GXSendRocketMQServiceImplTest,GXRocketMQAsyncSendFailureHandlerTest` 全绿，且原失败用例转通过

---

## Phase 5: User Story 3 - 并发与上下文安全可验证 (Priority: P1)

**Goal**: CDC 分层失败处置与监听器隔离；事件只读；异步与 CDC 上下文可关联

**Independent Test**: 毒消息不抛且计数；前者监听器抛错后者仍执行；并发复用 DTO 不被串改；失败日志带 TraceId

### Tests (先行，必须先失败)

- [X] T024 [P] [US3] 在 `src/test/java/cn/maple/rocketmq/service/impl/GXCdcMessageDispatcherImplTest.java` 补分层处置用例：非 JSON / 空白 / `op=x` 均不抛且计数递增；开关关闭时日志级别为 `warn`；死信 topic 转发失败不抛
- [X] T025 [P] [US3] 在同一测试类补监听器隔离用例：两监听器前者抛 `RuntimeException` → 后者被调用一次，最终抛 `GXCdcDispatchException` 且 `getSuppressed().length == 1`
- [X] T026 [P] [US3] 新增 `src/test/java/cn/maple/rocketmq/service/impl/GXSendRocketMQServiceImplConcurrencyTest.java`：8 线程复用同一 DTO 并在提交后篡改 topic/tag/key，断言已提交消息头与目的地不受影响
- [X] T027 [P] [US3] 新增 `src/test/java/cn/maple/rocketmq/dto/inner/GXCdcEventTest.java`：事件不可修改（无 setter / 集合不可变）、`isOnlyChanged` 语义保持、diff 只计算一次

### Implementation

- [X] T028 [US3] 改造 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/dispatcher/impl/GXCdcMessageDispatcherImpl.java`：分层失败处置（解析失败/空 payload/未知 op → `warn` + 计数 + 可选死信后返回）、开关关闭改 `warn`、新键 `maple.framework.cdc.active-status` 优先并兼容旧键（一次性迁移提示）（依赖 T005、T006、T024、T025）
- [X] T029 [US3] 在 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/dispatcher/impl/GXCdcMessageDispatcherImpl.java` 实现监听器隔离与汇总：每个监听器独立 `try-catch`，异常累积后构造 `GXCdcDispatchException` 并 `addSuppressed`，保证其余监听器仍执行
- [X] T030 [US3] 事件只读化：`leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/dto/inner/GXCdcEvent.java` 去掉 setter（改构造期赋值 + getter），集合字段返回不可变视图，`isOnlyChanged` 结果按事件缓存
- [X] T031 [US3] 补 `MDC`/TraceId 传递：在 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/handler/` 与 `dispatcher/impl/GXCdcMessageDispatcherImpl.java` 中，异步失败回调与 CDC 分发前恢复调用上下文，复用既有 `GXMdcWrapper*`，禁止新建线程；回调后清理
- [X] T032 [US3] 监听器集合改为可解析：修改 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/dispatcher/impl/GXCdcMessageDispatcherImpl.java` 的构造注入为 `ObjectProvider<GXCdcEventListener>` 或等价方式，保证运行期新增监听器可见且保持 `@Order` 顺序

**Checkpoint**: US3 独立可验收——`GXCdcMessageDispatcherImplTest`、`GXCdcEventTest`、`GXSendRocketMQServiceImplConcurrencyTest` 全绿

---

## Phase 6: User Story 4 - 性能瓶颈定量化 (Priority: P2)

**Goal**: 把"每消息 1 次 RTT、成功路径 0 条 info、CDC 每事件分配 ≤4 与 diff 1 次"落到可复核

**Independent Test**: 按 `quickstart.md` 第 6 节表格逐项复算，数值与代码路径一致

- [X] T033 [P] [US4] 在 `src/test/java/cn/maple/rocketmq/service/impl/GXSendRocketMQServiceImplTest.java` 增加日志级别断言用例（成功路径 `info` 计数为 0、失败路径 `error` 存在）
- [X] T034 [US4] 将 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/service/impl/GXSendRocketMQServiceImpl.java` 成功路径的 5 处 `log.info` 降为 `debug`（`sendNormalMessage`/`sendDelayMessage`/`syncSend`/`sendAsync` 成功回调/`sendOneway`），失败路径保持 `error`
- [X] T035 [US4] 降低 CDC 单事件开销：在 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/dto/inner/GXCdcEvent.java` 与 `dispatcher/impl/GXCdcMessageDispatcherImpl.java` 中把 `Dict` 构造点收敛到 ≤4，`Maps.difference` 每事件仅 1 次（与 T030 联动），必要时复用空 Map 常量
- [X] T036 [US4] 在 `review-report.md` 写入"结构开销复核表"（往返次数、info 日志数、每事件分配数、diff 次数）并逐项标注代码位置

**Checkpoint**: US4 独立可验收——日志断言用例通过 + 复核表可复算

---

## Phase 7: User Story 5 - 对外契约与文档一致 (Priority: P3)

**Goal**: 消除文档与实现的漂移，不改代码行为

**Independent Test**: 按 `quickstart.md` 第 7 节命令抽查关键词，逐条与实现对照

- [X] T037 [P] [US5] 更新 `docs/leaf-base-rocketmq开发手册.md`：`config-source` 三态与默认行为、Nacos 侧 `spring.config.import` 前置条件、`sendNormalMessage` 实际调用 `syncSend`、延迟消息机制改为 Broker 定时消息（`timerMaxDelaySec`）、成功日志级别
- [X] T038 [P] [US5] 更新 `leaf-base-rocketmq/AGENTS.md`：`config-source` 生效语义、新增 `maple.framework.cdc.*` 键、CDC 分层失败处置契约、异步失败扩展点
- [X] T039 [US5] 在 `review-report.md` 输出"文档 ↔ 实现对照表"，逐条给出 一致 / 不一致（已修文档）/ 未覆盖 的判定

**Checkpoint**: US5 独立可验收——对照表 100% 有判定，无"待确认"

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 每条发现具备八要素，可直接拆成任务

**Independent Test**: 抽取任一发现，八要素齐备（编号 / 级别 / 维度 / 位置 / 触发 / 影响 / 证据 / 回归设计）

- [X] T040 [US6] 在 `review-report.md` 补"发现 → 任务"映射表（F-xx → FR-xx → T0xx）与风险排序依据（影响面 × 触发概率 × 可观测性）
- [X] T041 [US6] 标注"既定契约 vs 缺陷"：`sendAsync` 恒 `true`、`sendNormalMessage` 返回 `void`、`sendOneway` 恒 `true` 等列为契约，仅要求文档同步

**Checkpoint**: US6 独立可验收——映射表无空列，契约项不进入修复任务

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 全量验证与交付卫生

- [X] T042 运行 `mvn -pl leaf-base-rocketmq -am test`，要求 0 失败、用例数 ≥35，且 ErrorProne / NullAway 零告警
- [X] T043 [P] 设置 `ROCKETMQ_NAME_SERVER` / `ROCKETMQ_ACCESS_KEY` / `ROCKETMQ_SECRET_KEY` 后复跑 `GXRocketMQRealBrokerIT`，记录与基线同量级的数据到 `review-report.md`
- [X] T044 [P] 交付卫生检查：在仓库根执行 `git diff --stat`，结果仅含 `leaf-base-rocketmq/` 与 `specs/006-rocketmq-module-audit/`；并检查 `leaf-base-rocketmq/src/test/` 与 `config/` 下无真实凭据、无 `System.out`、无调试残留、无被跳过的既有用例
- [ ] T045 更新 `specs/006-rocketmq-module-audit/checklists/requirements.md` 的澄清条目数（3 → 8）并复核 12 项仍全部通过（按 /speckit.implement 规则，checklists 目录为只读门禁，本次未修改清单文件）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始；T003 与 T004 可并行
- **Foundational (Phase 2)**: 依赖 Setup；T005–T008 互相独立可并行，T009 依赖 T003
- **US1 (Phase 3)**: 仅依赖 Setup（T001、T002）；与 US2–US6 完全解耦，可最早交付
- **US2 (Phase 4)**: 依赖 Foundational（T007、T008、T009）；T014–T017 测试先行
- **US3 (Phase 5)**: 依赖 Foundational（T005、T006）；T030 与 T035 共享 `GXCdcEvent`，需串行
- **US4 (Phase 6)**: 依赖 US2（发送代码就位）与 US3（事件改造就位）
- **US5 (Phase 7)**: 依赖 US2、US3 的最终契约（文档必须描述已定行为）
- **US6 (Phase 8)**: 依赖 US1–US5 的产出
- **Polish (Phase 9)**: 依赖全部

### Story Completion Order

```text
Setup → Foundational ─┬─→ US1 (报告, 独立)
                       ├─→ US2 (配置+发送) ──┐
                       └─→ US3 (CDC+并发) ──┼─→ US4 (性能口径)
                                            ├─→ US5 (文档)
                                            └─→ US6 (排期映射)
                                                    ↓
                                                 Polish
```

### Within Each Story

- 测试先行：T014–T017 / T024–T027 / T033 必须先失败
- 模型/类型 → 服务 → 集成（US2：T018→T023；US3：T028→T032）
- 同一文件的任务必须串行：`GXCdcMessageDispatcherImpl.java`（T028、T029、T035）、`GXCdcEvent.java`（T030、T035）、`GXSendRocketMQServiceImpl.java`（T021、T023、T034）

---

## Parallel Execution Examples

### User Story 2

```bash
# 四个测试文件互不重叠，可同时开工
Task: "T014 三态装配回归 specs/.../GXRocketMQConfigPropertiesTest.java"
Task: "T015 发送校验回归 leaf-base-rocketmq/src/test/java/cn/maple/rocketmq/service/impl/GXSendRocketMQServiceImplTest.java"
Task: "T016 失败扩展点用例 leaf-base-rocketmq/src/test/java/cn/maple/rocketmq/handler/GXRocketMQAsyncSendFailureHandlerTest.java"
Task: "T017 真实 Broker IT leaf-base-rocketmq/src/test/java/cn/maple/rocketmq/integration/GXRocketMQRealBrokerIT.java"
```

### User Story 3

```bash
Task: "T024 CDC 分层处置用例"
Task: "T026 并发复用 DTO 用例"
Task: "T027 GXCdcEvent 只读用例"
```

### Foundational

```bash
Task: "T005 GXCdcDispatchException"
Task: "T006 GXCdcDispatchStatistics"
Task: "T007 失败 context record"
Task: "T008 失败 handler 接口"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup（T001–T004）
2. Phase 3 US1（T010–T013）——不碰生产代码即可交付可复核报告
3. **STOP & VALIDATE**：随机抽一条发现按报告复现
4. 交付"审查结论基线"（对应 SC-001、SC-002）

### Incremental Delivery

1. Setup + Foundational → 类型与骨架就位
2. US2 → 配置装配修复 + 发送契约收敛（当前失败用例转通过）→ 交付
3. US3 → CDC 可靠性与并发安全 → 交付
4. US4 → 结构开销达标 → 交付
5. US5 + US6 → 文档对齐 + 可排期映射 → 交付
6. Polish → 全量验证与卫生检查

### Parallel Team Strategy

- 开发 A：US2（装配 + 发送）
- 开发 B：US3（CDC + 并发）
- 开发 C：US5/US6（文档与报告映射，待 A/B 契约稳定后收尾）
- US1 可由审查者独立完成，与 A/B 无文件冲突

---

## Notes

- [P] 任务 = 不同文件且无未完成依赖
- 同一 `*.java` 的多条任务必须串行，已在上文列明
- 真实 Broker 用例必须 env 门控，缺失时**跳过而非失败**
- 严禁删除或 `@Disabled` 既有失败用例来"通过"
- 每个测试先行任务都必须先观察到失败，再进入实现任务
- 提交粒度：一个逻辑组一次提交，提交信息遵循 `类型(范围): 简述`

---

## Phase 10: Convergence

> 由 `/speckit.converge` 于 2026-10-22 追加；仅追加，不修改既有任务。

- [X] T046 CRITICAL: 将 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/dispatcher/GXCdcDispatchStatistics.java` 的 `@Log4j2` 改为 `@Slf4j`，新代码统一走 SLF4J 门面 per Constitution VII (contradicts)
- [X] T047 在 `specs/006-rocketmq-module-audit/review-report.md` 补"结构开销复核表"：每消息网络往返次数、成功路径 `info` 日志条数、CDC 每事件容器分配数、每事件 `Maps.difference` 次数，逐项标注代码位置与实测/推算值 per FR-007 (missing)
- [X] T048 在 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/service/impl/GXSendRocketMQServiceImpl.java` 的失败日志中补齐 destination 与 messageKey：同步失败在 `checkSendResult` 调用处传入目的地，异步失败在 `handleAsyncException` 补 messageKey per contracts/send-contract.md §5 (partial)
- [X] T049 新增 MDC 回归测试 `leaf-base-rocketmq/src/test/java/cn/maple/rocketmq/service/impl/GXSendRocketMQServiceImplAsyncMdcTest.java`：请求线程写入 MDC/TraceId 后触发异步失败回调，断言回调线程可见同一 TraceId，且回调结束后不污染回调线程 per US3/AC4 (missing)
- [X] T050 在 `leaf-base-rocketmq/src/test/java/cn/maple/rocketmq/integration/GXRocketMQRealBrokerIT.java` 增加"经 `GXSendRocketMQService` 的错误凭据"用例：用错误 secret 构建独立上下文，断言抛 `GXBusinessException` 且 cause 链保留客户端异常，替代当前仅用裸 `DefaultMQProducer` 的验证 per US2/AC2 (partial)
- [X] T051 修正 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/properties/nacos/GXNacosRocketMQConfigProperties.java` 与本地源日志：自动判定输出 `auto-detected` 与 `set rocketmq.config-source to make it explicit`，显式配置输出 `explicit`，使三态在日志中可区分 per contracts/config-source-contract.md §1 (partial)

---

## Phase 11: Convergence

> 由 `/speckit.converge` 于 2026-10-22（第二次）追加；仅追加，不修改既有任务。

- [X] T052 在 `specs/006-rocketmq-module-audit/review-report.md` 补三张矩阵：发送语义矩阵（5 个方法 × 返回值 × 失败表达 × 是否阻塞 × 投递语义"至少一次/至多一次/尽力投递"）、配置来源矩阵（local/nacos/下游 application.yml × 装配条件 × 是否真实加载 × 证据）、并发风险矩阵（请求 DTO/CDC 事件/监听器集合/异步回调 × 共享范围 × 现状保护 × 收敛路径） per FR-008 (missing)
- [X] T053 在 `specs/006-rocketmq-module-audit/review-report.md` 的发现列表为每条发现补齐"代码位置（文件 + 行区间）、触发条件、复现方式、回归测试设计"四项字段，使其满足八要素要求 per SC-002 (partial)
- [X] T054 在 `specs/006-rocketmq-module-audit/review-report.md` 为 F-01 / F-02 / F-03 各写"故障现场构造步骤"（输入、环境、操作、期望现象），可直接照做复现 per SC-008 (missing)
- [X] T055 将 `leaf-base-rocketmq/src/main/java/cn/maple/rocketmq/properties/local/GXLocalRocketMQConfigProperties.java` 的启动日志按契约输出 `resource=<path>` 与 `NOT_FOUND` 标记（资源缺失时），替换现有 `exists=true/false` 写法 per contracts/config-source-contract.md §1 (partial)
- [X] T056 在 `specs/006-rocketmq-module-audit/review-report.md` 记录与 `plan.md` D-01 的实现偏差（以显式 `Condition` 实现替代 `AnyNestedCondition`，原因：嵌套 DSL 中 `@ConditionalOnExpression` 分支在 `ApplicationContextRunner` 下不生效）及等价性说明 per plan: D-01 (partial)
