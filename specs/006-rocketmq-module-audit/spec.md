# Feature Specification: leaf-base-rocketmq 逻辑正确性、线程安全与性能审查

**Feature Branch**: `006-rocketmq-module-audit`（本次未通过 hook 创建 git 分支）

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "审查 @leaf-base-rocketmq 模块，这个模块是对 rocketmq 的封装，你需要验证正确性、线程安全性与性能。你在验证时可以使用 accessKey:britton，secretKey:britton123 来做真实的测试。"

## 背景与审查结论摘要

本规格把"`leaf-base-rocketmq` 现有实现是否正确、是否线程安全、是否存在性能结构问题"转化为可验收的交付要求。审查对象与证据来源：

1. `leaf-base-rocketmq` 全部 18 个生产源码文件与 3 个既有测试类、1 个测试配置；
2. 契约来源：`leaf-base-rocketmq/AGENTS.md`、`docs/leaf-base-rocketmq开发手册.md`、`.specify/memory/constitution.md`；
3. 依赖基线：`rocketmq-spring-boot-starter 2.3.6`，根 POM 强制 `rocketmq-client/common/remoting/acl 5.3.2`；本模块硬依赖 `leaf-base-nacos`（`nacos-client 3.1.1`）；
4. **真实 Broker 实测**（用户授权凭据 `britton / britton123`，本机 `127.0.0.1:9876`，Broker 开启 `aclEnable=true`、`timerMaxDelaySec=604800`）：5 类发送全部成功；顺序同步发送 200 条 avg 1304µs / p50 1130µs / p95 2304µs / max 14303µs；8 线程 × 50 条并发 avg 2902µs / p95 8513µs，吞吐约 2690 msg/s；错误 secret 被 Broker 拒绝（3 次重试后 `MQClientException`，携带 `AclException: Check signature failed for accessKey=britton`，耗时 192ms）。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 发送服务的参数校验、目的地拼装、消息 Key、延迟时间换算与发送结果判定在代码层面自洽，真实 Broker 冒烟通过；但**配置来源选择（local/nacos）整体失效**、**异步发送结果不可知且不可补偿**、**CDC 无效消息/毒消息静默丢弃**三条链路存在"无报错但行为与承诺不符"的问题 | 存在高风险缺陷 |
| 线程安全 | 发送服务无共享可变状态，且把可变请求对象快照为不可变 record，并发路径干净；风险集中在 **CDC 事件对象跨监听器共享可变**、**监听器集合启动期固化**、**异步回调丢失 MDC/TraceId** | 无致命竞态，需补约束 |
| 性能 | 模块只提供单条同步语义，无批量封装；每消息 1 次网络往返 + 2 条 info 日志；CDC 每事件多次容器分配且每监听器重复全表 diff；异步路径无背压、无按消息超时覆盖 | 存在结构性上限，非阻断 |

关键证据（均可按位置复核）：

- **E-01**：`GXLocalRocketMQConfigProperties` 以 `@ConditionalOnMissingClass("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties")` 判定本地源（第 15 行），而 `nacos-client 3.1.1`（本模块硬依赖经 `leaf-base-nacos` 引入）**确实包含该类**，条件恒为 false，本地配置类永不装配。
- **E-02**：`GXNacosRocketMQConfigProperties` 使用 `@NacosConfigurationProperties`（第 16–22 行），但依赖树中**不存在** `nacos-spring-boot` / `nacos-spring-context` 处理器，注解无执行者，Nacos 侧同样不加载。
- **E-03**：本次运行 `mvn -pl leaf-base-rocketmq test` 结果 **27 个用例、1 个失败**：`GXRocketMQConfigPropertiesTest$LocalConfigSourceTest.loadsLocalConfigSource` 断言 `Expected size: 1 but was: 0`，与 E-01 完全吻合；且 `AGENTS.md` 第 30 行承诺的 `rocketmq.config-source` 配置键在全部源码中**不存在**。
- **E-04**：`sendAsync` 提交后立即 `return true`（第 126 行）；`onSuccess` 对非 `SEND_OK` 仅记 error 并 return（第 107–112 行）；`onException` 仅记 error（第 117–122 行）。调用方与框架均无成功/失败回传通道。
- **E-05**：`checkSendResult` 只接受 `SendStatus.SEND_OK`（第 180–189 行）；`FLUSH_DISK_TIMEOUT` / `FLUSH_SLAVE_TIMEOUT` / `SLAVE_NOT_AVAILABLE` 一律抛 `GXBusinessException`，而生产端默认 `retryTimesWhenSendFailed=2`（实测失败一次成本 3 次尝试、192ms）。
- **E-06**：`MessageRequest.from` 仅校验 topic/body 非空（第 200–205 行），未校验 topic 字符集与长度、tag/messageKey 长度、body 大小；`GXRocketMQMessageReqDto(Object body)` 对 `null` 调用 `JSONUtil.toJsonStr(null)` 得到字符串 `"null"`，可通过 `isBlank` 校验并真实投递。
- **E-07**：`GXRocketMQMessageReqDto` 三个构造器在字符串入参下存在重载歧义（第 61–74 行）：`new GXRocketMQMessageReqDto("a","b")` 命中 `(String tag, Object body)`，body 变为 `"\"b\""` 双重编码。
- **E-08**：`sendNormalMessage`（第 27–44 行）与 `syncSend`（第 74–93 行）实现几乎重复，返回值分别为 `void` / `boolean` / `String`；手册第 271 行称前者调用 `rocketMQTemplate.send`，实现为 `syncSend`。
- **E-09**：CDC 双开关：Bean 级 `maple.framework.cdc.enable`（第 21 行）+ 运行期 `cdc.active.status`（第 66–69 行，**未置于 `maple.framework.*` 命名空间**）；关闭时仅 `log.debug` 后 return（第 38–41 行）。
- **E-10**：`parseEvent` 捕获全部异常返回 `null`（第 80–83 行），`dispatch` 随后正常返回（第 48–51 行），消费方将 ack，畸形消息永久丢失；未知 op 仅 `warn`（第 54–57 行）。
- **E-11**：`dispatchToListener` 不捕获监听器异常（第 91–98 行），任一监听器抛错即中断后续监听器，并把异常抛给消费方；重试将导致已执行监听器重复处理。
- **E-12**：`GXCdcEvent` 为 `@Data` 可变对象（第 17–34 行），同一实例顺序传给全部监听器，先执行者可改变后续监听器可见的 `before/after/op`。
- **E-13**：`GXCdcMessageDispatcherImpl` 构造注入固定 `List<GXCdcEventListener>`（第 29–34 行），运行期新增/替换监听器 Bean 不会被感知。
- **E-14**：`getDestination` 直接 `topic:tag` 拼接（第 156–162 行），topic 含 `:` 时目的地歧义；tag 与 key 未做长度/字符校验。
- **E-15**：`GXCdcEvent.fromPayload` 为 `before/after/source/transaction` 各创建 `Dict`（第 36–47 行），`isOnlyChanged` 每个监听器调用一次都重算 `Maps.difference`（第 53–57 行），宽表 × N 监听器时开销随列数与监听器数相乘增长。
- **E-16**：每个发送方法在成功路径固定输出 1 条 `info`（第 37、64、85、113、146 行），`sendAsync` 回调成功亦输出 `info`，均位于发送关键路径。
- **E-17**：`sendDelayMessage` 使用 `syncSendDeliverTimeMills`（第 61 行），依赖 Broker 定时消息能力；本机 Broker `timerMaxDelaySec=604800`，5 秒延迟实测成功。
- **E-18**：模块无 `src/main/resources`，无自动装配注册；4 个 `@Service`/`@Component` 类依赖下游 `@GXEnableLeafFramework`（`@ComponentScan("cn.maple")`）或等价扫描，框架 `GXFrameworkConfig` 中的 `@ComponentScan` 已被注释（第 27 行）。

发现清单（分级）：

- **F-01（高）配置来源选择整体失效**：`rocketmq.config-source` 从未被读取；本地源因类路径条件恒假而永不生效，Nacos 源因缺少注解处理器而永不生效。`config/dev|test|pre|prod/rocket-mq.yml` 是死配置，文档宣称的"优先 Nacos、回退本地"不存在（E-01、E-02、E-03）。
- **F-02（高）异步发送结果不可知且不可补偿**：`true` 仅表示提交；非 `SEND_OK`、异常、空结果都只落日志，无回调、无指标、无重试与死信，业务无法区分"已投递"与"已丢失"（E-04）。
- **F-03（高）CDC 无效消息/毒消息静默丢弃**：解析失败、无 payload、未知 op、开关关闭四种情况均正常返回，消费方 ack 即丢失；仅靠日志，无死信、无计数（E-09、E-10）。
- **F-04（中）非 SEND_OK 一律抛异常导致重复消息**：状态可能已落盘仍抛错，叠加客户端内建重试与上游重试，形成重复投递；失败场景单次阻塞达 3×发送超时（E-05）。
- **F-05（中）参数校验存在缺口**：topic 字符集/长度、tag/key 长度、body 大小均未校验；`null` body 会以字符串 `"null"` 通过校验真实发出（E-06）。
- **F-06（中）请求构造器重载歧义与双重 JSON 编码**：字符串 body 易被序列化两次，且编译期无提示（E-07）。
- **F-07（中）发送接口语义重复且返回值三元化**：`sendNormalMessage`（void）/ `syncSend`（boolean）/ `sendDelayMessage`（msgId）/ `sendAsync`、`sendOneway`（恒 true），失败一律抛异常，"boolean 返回值"无信息量（E-08）。
- **F-08（中）CDC 监听器无隔离**：单个监听器异常中断后续监听器并触发消费方重试，造成部分投递 + 重复执行（E-11）。
- **F-09（中）CDC 事件对象跨监听器共享可变状态**：后执行监听器看到的事件可能被前者篡改（E-12）。
- **F-10（中）监听器集合启动期固化**：动态注册/替换监听器静默不生效（E-13）。
- **F-11（中）异步与 CDC 路径丢失上下文**：`sendAsync` 回调、CDC 分发均未做 MDC/TraceId 传递，异步失败日志无法与请求关联（宪法 V）。
- **F-12（中）发送关键路径每消息 info 日志**：高吞吐场景日志成为瓶颈（E-16）。
- **F-13（中）无批量发送封装**：接口层只提供单条语义，调用方循环导致每消息 1 次 RTT；实测并发 8 线程时 p95 从 2.3ms 升至 8.5ms。
- **F-14（低）目的地拼装不设防**：topic 含 `:` 时 `topic:tag` 目的地歧义（E-14）。
- **F-15（低）CDC 事件分配与比较开销**：每事件 4 个 `Dict` + 每监听器全表 diff（E-15）。
- **F-16（低）异步无背压与按消息超时覆盖**：提交后可无限涌入，生产者内部信号量状态不暴露；超时/重试只能改全局配置。
- **F-17（低）配置类为空壳**：两个 Properties 类无任何字段，`@Data` 无意义；无 `additional-spring-configuration-metadata.json`，IDE 无提示（E-01、E-02）。
- **F-18（低）装配依赖下游包扫描**：模块未提供自动装配注册，4 个组件类仅在下游启用 `@GXEnableLeafFramework` 或显式扫描 `cn.maple.rocketmq` 时生效，缺少"未装配"的可观测信号（E-18）。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：`GXSendRocketMQServiceImpl` 无共享可变状态，发送前把 DTO 快照为不可变 `record`（第 195–212 行），异步回调只读快照，调用方并发修改请求对象不影响在途发送（既有用例已覆盖）。
- **P-02**：延迟时间换算使用 `Math.multiplyExact` / `Math.addExact`（第 172–178 行）并对非正数前置拒绝，溢出与负数边界处理正确。
- **P-03**：目的地拼装 `topic` / `topic:tag` 与 `RocketMQHeaders.KEYS` 写入语义稳定，真实 Broker 端实测 5 类发送全部成功。

## Clarifications

### Session 2026-10-22

- Q: 本次交付是"只给审查结论与验证基线"，还是"结论 + 直接修复已确认缺陷"？ → A: 结论 + 可执行验证基线为本次必交付；生产代码修复由后续 `/speckit.plan` 依据发现分级排期，本规格只要求每条发现可复现、可写出回归测试设计，不要求本次改动生产代码。
- Q: 真实测试使用哪些凭据、是否写入仓库？ → A: 使用用户提供的 `britton / britton123` 对本机 `127.0.0.1:9876` 做真实冒烟与采样；凭据一律由环境变量注入（`ROCKETMQ_ACCESS_KEY` / `ROCKETMQ_SECRET_KEY`），**不得写入源码、测试或 `config/` 模板**，缺失时相关用例优雅跳过。
- Q: 审查范围是否包含消费侧（消费者容器、重试、幂等）？ → A: 不包含。模块本身不承载消费容器；CDC 仅审查"原始消息 → 事件 → 本地监听器分发"这一段，重点在解析失败、未知 op、监听器异常与开关语义。
- Q: `rocketmq.config-source` 这一已写入 `AGENTS.md` 但代码中不存在的配置键，后续应当如何收敛？ → A: 保留该键并使其真正生效：`local` / `nacos` 显式二选一；未配置时按类路径自动判定（兼容现状）并输出一条可检索的启动日志；键名、默认值与既有部署语义不变。
- Q: CDC 链路遇到"消息本身无法解析"与"业务监听器执行报错"这两类失败时分别如何处置？ → A: 分层处置：解析失败/未知 op 记录 warn 并计数（可选转发死信 topic）后正常返回；监听器异常逐监听器隔离捕获，全部执行完后汇总抛出，保持至少一次。
- Q: 本次真实 Broker 验证后续以什么形式留在仓库？ → A: 永久保留为环境变量门控的集成测试，打 `integration` 标签，`ROCKETMQ_ACCESS_KEY` / `ROCKETMQ_SECRET_KEY` / `ROCKETMQ_NAME_SERVER` 缺失时自动跳过，不引入 Testcontainers。
- Q: 异步发送最终失败（非 `SEND_OK` 或回调抛异常）时，业务方通过什么方式得知并补救？ → A: 新增可选发送失败回调扩展点，无实现时退化为 error 日志；`sendAsync` 签名、返回值与异常语义保持不变。
- Q: 性能最终以什么标准判定"改到位"？ → A: 不设绝对吞吐/时延门禁；只保留可复算口径（往返次数、日志条数、分配次数）与同环境重跑同量级的复现要求。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核、可用真实 Broker 复现 (Priority: P1)

框架维护者要判断"能否在生产启用该模块的发送与 CDC 能力"，需要分级结论；每条结论都必须能跳到代码位置，并且可用真实 Broker 复现观察到同样现象。

**Why this priority**: 审查的唯一价值是结论可信；结论不可复核时，后续计划与修复都建立在猜测上。

**Independent Test**: 随机抽取结论条目，按报告给出的位置与命令复现；高风险项必须能构造故障现场（例如把 `rocketmq.config-source=local` 打开后仍观察不到本地配置生效）。

**Acceptance Scenarios**:

1. **Given** 一条"配置来源选择失效"的结论，**When** 以 `rocketmq.config-source=local` 启动并检查容器中的配置 Bean 与环境属性，**Then** 本地配置类不存在、本地 yml 的 marker 属性读不到，与结论一致。
2. **Given** 一条"异步发送失败仅落日志"的结论，**When** 用错误 secret 触发异步发送失败，**Then** 业务方法仍返回 `true`，仅日志可见失败。
3. **Given** 一条"延迟消息生效"的正向结论，**When** 用真实 Broker 发送 5 秒延迟消息，**Then** 发送成功且返回 msgId，与结论一致。

---

### User Story 2 - 发送链路语义与配置装配可验证 (Priority: P1)

下游接入方需要明确知道每个发送方法在成功、失败、超时、ACL 拒绝、非法参数下的确切语义，以及配置从哪来、开关怎么生效。

**Why this priority**: 发送语义是模块对外契约的核心；配置来源失效会让所有接入方在错误的前提下配置生产环境。

**Independent Test**: 对 5 个发送方法各构造成功、Broker 拒绝、参数非法三类输入，记录返回值/异常/日志；对两种配置来源各启动一次上下文，断言实际加载结果。

**Acceptance Scenarios**:

1. **Given** 真实 Broker 开启 ACL，**When** 使用有效 `accessKey/secretKey` 发送 5 类消息，**Then** 全部成功，且失败路径在错误 secret 下表现为 `GXBusinessException` 且保留 Broker 侧 `AclException` 信息。
2. **Given** 请求 topic 为空、body 为空、延迟时间非正、延迟时间溢出，**When** 调用发送方法，**Then** 抛出带明确消息的 `GXBusinessException` 且不触达 Broker。
3. **Given** `rocketmq.config-source` 分别取 `local` 与 `nacos`，**When** 启动上下文，**Then** 报告给出"该键是否真的影响装配"的明确判定与证据。
4. **Given** 下游未启用 `@GXEnableLeafFramework`，**When** 应用启动，**Then** 报告说明模块组件是否被装配，以及缺少装配时是否有可观测信号。

---

### User Story 3 - 并发与上下文安全可验证 (Priority: P1)

运维与开发需要知道：并发发送时请求对象是否会被串改、异步回调是否携带调用上下文、CDC 多监听器之间是否互相污染。

**Why this priority**: 这些问题在单线程功能测试中不会暴露，一旦上线表现为"消息内容错乱""排查断链"，且难以复现。

**Independent Test**: 用多线程并发发送同一可变 DTO 并在发送后立即篡改字段；用两个监听器验证前者修改事件对后者的影响；在带 TraceId 的上下文中触发异步失败并检查日志关联。

**Acceptance Scenarios**:

1. **Given** 同一 `GXRocketMQMessageReqDto` 被多线程复用并在提交后修改，**When** 并发发送，**Then** 已提交消息的 topic/tag/key 不受影响（现状应通过，需回归锁定）。
2. **Given** 两个 CDC 监听器，前者修改事件字段，**When** 分发同一事件，**Then** 报告给出"后者看到被篡改数据"的判定与证据。
3. **Given** 监听器 A 抛异常，**When** 分发事件，**Then** 报告明确记录监听器 B 是否执行、消费方是否重试、重试后 A 是否重复执行。
4. **Given** 异步发送失败发生在带 TraceId 的请求中，**When** 检查失败日志，**Then** 报告给出"是否可关联到原请求"的判定。

---

### User Story 4 - 性能瓶颈定量化 (Priority: P2)

维护者需要判断该模块能否支撑目标吞吐。结论必须给出可复算口径：单条消息的往返次数、并发下的时延分布、日志与序列化的固定开销，而不是"较慢/较快"。

**Why this priority**: 无口径的性能结论无法转化为"改到什么程度算达标"的计划。

**Independent Test**: 按报告口径在真实 Broker 上重跑顺序与并发两组采样，断言量级与分布一致；按代码路径复算往返次数与日志条数。

**Acceptance Scenarios**:

1. **Given** 单线程顺序发送 200 条，**When** 记录每条耗时，**Then** 报告给出 avg/p50/p95/max 与样本量，且重跑结果同量级。
2. **Given** 8 线程各发送 50 条，**When** 汇总吞吐与分位，**Then** 报告给出吞吐、分位与相对单线程的衰减，并说明瓶颈构成（往返、日志、锁）。
3. **Given** 一批 CDC 事件与多个监听器，**When** 统计每事件分配与比较次数，**Then** 报告给出随列数与监听器数增长的量化口径。
4. **Given** 单条消息的完整发送路径，**When** 统计网络往返与日志条数，**Then** 报告给出固定开销清单，且可被代码位置逐条对应。

---

### User Story 5 - 对外契约与文档一致 (Priority: P3)

使用方依赖 `AGENTS.md`、开发手册与方法签名判断行为。文档与实现不一致处必须作为独立发现记录，而不是在审查中顺手改掉。

**Why this priority**: 契约漂移排查成本高，但不直接导致运行时错误，故低于前三项。

**Independent Test**: 逐条核对文档声明的配置来源、异步返回值语义、延迟实现方式、批量能力与实现，给出"一致 / 不一致 / 未覆盖"判定。

**Acceptance Scenarios**:

1. **Given** 文档称"优先加载 Nacos、失败回退本地"，**When** 核对实现与启动行为，**Then** 该声明被判为不一致并给出证据。
2. **Given** 手册称 `sendNormalMessage` 内部调用 `rocketMQTemplate.send`，**When** 核对实现，**Then** 记录实际调用为 `syncSend`。
3. **Given** 手册对本地配置路径写作 `${spring.profiles.active:}`，**When** 核对注解，**Then** 记录实际为 `/${spring.profiles.active}/` 及其在未设置 profile 时的风险。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现都应有编号、级别、影响面、证据、复现方式与"旧代码失败、新代码通过"的回归设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查是否具备八项要素（编号、级别、维度、位置、触发条件、影响、证据、回归设计），缺一即不合格。

**Acceptance Scenarios**:

1. **Given** 一条高危发现，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"测试先行"的任务。
2. **Given** 被判定为"既定契约"的行为（如 `sendAsync` 返回 `true` 仅表示提交），**When** 查阅报告，**Then** 明确标注为契约而非缺陷，避免被误改。

---

### Edge Cases

- 未设置 `spring.profiles.active` 时本地配置注解的占位符解析（本地源被激活的前提）。
- 类路径中存在 Nacos 注解类但缺少注解处理器（本模块当前的默认形态）。
- 延迟时间非正、恰好溢出 `long`、超过 Broker `timerMaxDelaySec`。
- topic 为空、含 `:`、含非法字符、超长；tag 为空/全空格/超长；messageKey 为空白。
- body 为 `null`（经 `Object` 构造器）、body 为空白、body 超过生产者最大消息大小。
- 真实 Broker 开启 ACL 且凭据错误/缺失；Broker 不可达；NameServer 不可达。
- 发送返回 `null`、`FLUSH_DISK_TIMEOUT`、`SLAVE_NOT_AVAILABLE`、`SEND_OK` 四种结果。
- 同一 DTO 在多个线程复用并在提交后修改；同一 DTO 连续多次发送。
- 异步发送回调线程池饱和；回调中抛异常。
- CDC 消息非 JSON、payload 为标量、`op` 缺失/未知、`before`/`after` 为空、`ts_ms` 缺失。
- CDC 开关两级组合（Bean 级开启 + 运行期关闭，Bean 级关闭）。
- CDC 监听器抛异常、监听器修改事件、监听器在运行期新增。
- 容器关闭时存在在途异步发送。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 18 个生产源码文件、3 个既有测试类与测试配置；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（逻辑正确性 / 线程安全 / 性能）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式：既有用例、可新增的回归用例设计或明确构造步骤；仅凭阅读的判断 MUST 标注为"未验证假设"。
- **FR-004**: 审查 MUST 在真实 Broker 上验证 5 类发送（普通 / 同步 / 延迟 / 异步 / 单向）的成功路径，并 MUST 用错误凭据验证 ACL 拒绝路径；凭据 MUST 来自环境变量，缺失时用例优雅跳过。
- **FR-005**: 配置来源 MUST 以真实启动上下文验证，逐条给出"`rocketmq.config-source` 是否生效""本地 yml 是否被加载""Nacos 是否被加载"的结论，不得只凭注解推断。
- **FR-006**: 线程安全结论 MUST 覆盖三类场景：共享可变状态（发送服务、CDC 事件对象、监听器集合）、生命周期（异步回调线程、监听器注册）、竞态（并发复用同一 DTO、监听器间共享事件）。
- **FR-007**: 性能结论 MUST 给出可复算口径：单条消息网络往返次数、每消息日志条数、顺序与并发两组采样的样本量/avg/p50/p95/max、CDC 每事件分配与比较次数量级。
- **FR-008**: 消息可靠性 MUST 按"至少一次 / 至多一次 / 尽力投递"对 5 类发送与 CDC 分发逐条定性，并指出与文档承诺不一致之处。
- **FR-009**: 既有测试 MUST 全部执行并记录结果；当前存在的失败用例 MUST 作为独立发现记录，不得删除或跳过用例来"通过"。
- **FR-010**: 对外契约（`AGENTS.md`、开发手册、公开方法签名、配置键、异常语义）MUST 与实现逐条对照，不一致处 MUST 单列为发现，MUST NOT 在审查阶段修改生产代码。
- **FR-011**: 审查报告 MUST 独立成文，`spec.md` 只保留摘要与引用，两者在交付时 MUST 相互一致。
- **FR-012**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异），且该测试 MUST 在无 Broker 环境下可跳过。
- **FR-013**: 本次交付 MUST NOT 改变生产代码行为；提出修复建议时 MUST 说明其对现有成功路径、配置键与异常语义的影响。
- **FR-014**: 审查 MUST 明确区分"缺陷"与"既定契约"（如 `sendAsync` 仅表示提交、`sendNormalMessage` 无返回值），对既定契约 MUST 要求文档同步而非计为缺陷。
- **FR-015**: 真实测试产生的消息 MUST 使用独立 topic/group 前缀并可在报告中清理或说明遗留，MUST NOT 复用生产 topic。
- **FR-016**: 审查结论 MUST 包含风险排序依据（影响面 × 触发概率 × 可观测性），使计划阶段可直接确定修复批次。
- **FR-017**: 配置来源契约 MUST 按"保留 `rocketmq.config-source` 并使其生效"收敛：`local` / `nacos` 显式二选一，未配置时 MUST 保持现有类路径自动判定行为并输出一条可检索启动日志；修复设计 MUST NOT 重命名该键或改变其默认语义（对应 F-01）。
- **FR-018**: CDC 失败处置 MUST 分层：消息解析失败、payload 为空、op 未知 MUST 记录 warn 级可检索日志并计数（可选转发死信 topic）后正常返回，MUST NOT 抛给消费方；监听器异常 MUST 逐监听器隔离捕获并保证其余监听器仍执行，全部执行完成后 MUST 汇总抛出以保持至少一次语义（对应 F-03、F-08）。
- **FR-019**: 真实 Broker 验证 MUST 以 `integration` 标签 + 环境变量门控的测试形式入库，默认构建（无 Broker 环境）MUST 自动跳过且不报错；MUST NOT 引入 Testcontainers 等新的容器依赖（对应 FR-004、FR-012）。
- **FR-020**: 异步发送最终失败 MUST 提供可选回调扩展点：无业务实现时 MUST 退化为 error 日志，有实现时 MUST 回传失败消息标识与失败原因；`sendAsync` 的方法签名、返回值与异常语义 MUST 保持不变（对应 F-02）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **发送语义矩阵**：方法（普通 / 同步 / 延迟 / 异步 / 单向）× 返回值 × 失败表达 × 投递语义 × 真实 Broker 实测结果。
- **配置来源矩阵**：来源（本地 / Nacos / 下游 application.yml）× 装配条件 × 是否真实生效 × 证据。
- **并发风险矩阵**：对象（请求 DTO / CDC 事件 / 监听器集合 / 异步回调）× 共享范围 × 现状保护手段 × 收敛路径。
- **性能口径**：往返次数、每消息日志条数、样本量与分位、CDC 分配与比较量级。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 18 个生产源码文件与 3 个既有测试类，且 100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"；0 条仅凭阅读的结论混入已确认项。
- **SC-003**: 真实 Broker 验证覆盖 5 类发送的成功路径 + ACL 拒绝路径 + 参数非法路径，三类路径的成功判定率 100%，且无凭据写入仓库。
- **SC-004**: 配置来源结论对"键是否生效 / 本地是否加载 / Nacos 是否加载"三项各给出唯一定性，覆盖率 100%，并附启动上下文证据。
- **SC-005**: 性能结论给出顺序与并发两组各不少于 200 条样本的分位数据，重跑两次同量级偏差不超过 30%，并按代码路径复算往返与日志条数；MUST NOT 以绝对吞吐或时延数值作为通过门槛（受硬件与网络影响）。
- **SC-006**: 既有测试执行率 100%，其中失败用例 100% 被记录为发现（当前基线：27 执行 / 1 失败）。
- **SC-007**: 5 类发送与 CDC 分发的投递语义 100% 有唯一定性，与文档承诺的偏差 100% 被列为发现。
- **SC-008**: 高危发现（F-01 / F-02 / F-03）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。

## Assumptions

- 真实验证环境为：本机 `127.0.0.1:9876`，RocketMQ 5.x Broker，`aclEnable=true`，`timerMaxDelaySec=604800`，`autoCreateTopicEnable=true`；凭据 `britton / britton123` 具备管理员权限，仅用于本地验证。
- 目标运行形态为 JDK 21 + Spring Boot 4.1.x，下游通过 `@GXEnableLeafFramework` 或等价包扫描装配本模块组件。
- 本次为审查交付，不修改生产代码；不引入消费者容器、事务消息、批量发送等新能力，只审查现状。
- 性能数据以单机单 Broker、单生产者实例为前提，不代表集群吞吐；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- 依赖版本以根 POM 为准（`rocketmq-* 5.3.2`、starter `2.3.6`、`nacos-client 3.1.1`）；升级或降级导致的行为差异不在本次范围。
- 既有测试中被锁定的行为（如 `sendAsync` 恒返回 `true`、`sendNormalMessage` 返回 `void`）按"既定契约"处理，审查只要求文档同步。
- CDC 的业务幂等、落库事务、跨库路由与重试补偿由业务模块负责，不在本模块审查范围。
