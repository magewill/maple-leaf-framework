# Tasks: leaf-base-data-sync 正确性 / 线程安全 / 性能审查与消息中间件可切换改造

**Input**: Design documents from `/specs/017-data-sync-mq-switchable/`

**Prerequisites**: `plan.md`、`spec.md`、`research.md`、`data-model.md`、`contracts/canal-consumer-contract.md`、`quickstart.md`

**Tests**: 本特性**要求测试**（`spec.md` FR-040～FR-042、宪法 §VI：每条高风险发现需"旧实现失败 / 新实现通过"的回归用例，装配类改动需 Spring Boot 集成测试）。因此各 User Story 阶段均含测试任务，且测试任务先于对应实现任务。

**Organization**: 按 `spec.md` 的 5 个 User Story 分组，每个 Story 可独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属 User Story（US1～US5）
- 所有任务均给出精确文件路径

## Path Conventions

单模块库：仓库根下 `leaf-base-data-sync/src/main/java/cn/maple/canal/...`、`leaf-base-data-sync/src/test/java/cn/maple/canal/...`、`leaf-base-data-sync/src/main/resources/...`。规格产物位于 `specs/017-data-sync-mq-switchable/`。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 依赖、资源、包结构等一次性准备

- [X] T001 运行基线 `mvn -o -B -pl leaf-base-data-sync -am test`，记录用例总数、通过数与耗时，作为 `specs/017-data-sync-mq-switchable/review-report.md` 的"基线"数据来源
- [X] T002 [P] 在 `leaf-base-data-sync/pom.xml` 新增 `cn.maple.framework:leaf-base-rocketmq` 依赖，`<optional>true</optional>`，版本 `${project.parent.version}`；不声明任何 `org.apache.rocketmq:*` 直接坐标（FR-043/FR-044）
- [X] T003 [P] 新建 `leaf-base-data-sync/src/main/resources/META-INF/additional-spring-configuration-metadata.json`，先登记既有 4 个键：`canal.concurrency-count`、`canal.canal-queue-name`、`canal.exchange-name`、`canal.routing-key`（含 `defaultValue`，与 `data-model.md` §4.1 完全一致）
- [X] T004 [P] 新建包与 `package-info.java`：`cn/maple/canal/enums`、`cn/maple/canal/metrics`、`cn/maple/canal/service/support`（统一 `@org.jspecify.annotations.NullUnmarked`，与既有 9 个包保持一致）
- [X] T005 [P] 新建测试资源样例载荷：`leaf-base-data-sync/src/test/resources/canal/single.json`（单个 Canal flat message）与 `leaf-base-data-sync/src/test/resources/canal/batch.json`（同结构对象的 JSON 数组，2～3 个事件，含 1 条 DDL）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有 User Story 共用的类型与配置骨架，必须先完成

**⚠️ CRITICAL**: 本阶段完成前，任何 User Story 都无法编译通过

- [X] T006 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/enums/GXCanalOutcomeEnums.java`：`SUCCESS`/`SKIPPED`/`RETRYABLE_FAILURE`/`NON_RETRYABLE_FAILURE`，Javadoc 按 `data-model.md` §1.1 写明两套 MQ 的动作差异
- [X] T007 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/enums/GXCanalMqTypeEnums.java`：`RABBITMQ`/`ROCKETMQ`，Javadoc 说明绑定自 `canal.mq-type` 且非法值启动失败
- [X] T008 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/dto/inner/GXCanalParseResultInnerDto.java`（`record`）：`outcome`(非空)、`reason`(`@Nullable`)、`event`(`@Nullable GXCanalDataDto`)、`operation`(`@Nullable String`)、`batchIndex`(int)、`retryable`(boolean)；构造期校验不变式 `outcome==SUCCESS ⟺ event!=null && operation!=null`、`retryable == (outcome==RETRYABLE_FAILURE)`
- [X] T009 [P] 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/constant/GXCanalConstant.java`：新增结局相关常量（`x-canal-retry-count` 头名、`insert`/`update`/`delete` 操作名）、死信默认值（`canal.dlx`/`canal.dlq`/`canal.dead`）、重投默认上限 `3`、处理器缓存默认上限 `4096`；保持既有 4 个 RabbitMQ 常量不变
- [X] T010 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/metrics/GXCanalConsumeStatistics.java`：`LongAdder` 计数 `success`/`skipped`/`retryableFailure`/`nonRetryableFailure`/`handlerMiss`/`deadLetter`/`batchEvents`；构造器接收 `ObjectProvider<MeterRegistry>` 并在可用时注册 `canal.consumer.*` Gauge（注册失败仅 WARN）；提供只读计数方法与 `reset()`（`data-model.md` §5.1）
- [X] T011 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/GXCanalRetryProperties.java`（`canal.retry`，字段 `maxAttempts` 默认 3，校验范围 `0..10`）与 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/GXCanalDeadLetterProperties.java`（`canal.dead-letter`：`exchange`=`canal.dlx`、`queue`=`canal.dlq`、`routingKey`=`canal.dead`，均非空）
- [X] T012 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/GXCanalRocketMQConsumerProperties.java`（`canal.rocketmq`：`topic` 空、`consumerGroup` 空、`tag`=`*`、`maxReconsumeTimes` 默认取 `retry.max-attempts`、`deadLetterTopic` 空；`topic` 需匹配 `^[%a-zA-Z0-9_-]{1,127}$`）
- [X] T013 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/GXCanalProperties.java`：新增 `mqType`(`GXCanalMqTypeEnums`，默认 `RABBITMQ`)、`consumerEnabled`(boolean，默认 `true`)、`configSource`(String，默认空)、`handlerCacheSize`(int，默认 4096)、`logHandlerMissLevel`(String，默认 `warn-once`)，并以 `@NestedConfigurationProperty` 组合 T011/T012 的嵌套配置；既有 4 个字段的名字与默认值保持不变
- [X] T014 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/local/GXLocalCanalProperties.java` 与 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/nacos/GXNacosCanalProperties.java`：统一补 `@ConfigurationProperties(prefix = "canal")` 作为唯一绑定入口；移除 `@PropertySource` 中 `ignoreResourceNotFound = false` 的硬失败语义（改为不依赖 classpath 文件）；类路径条件保持现状作为默认行为的等价物（显式开关在 T043）
- [X] T015 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/support/GXCanalHandlerResolver.java`：启动期（`SmartInitializingSingleton`）用 `getBeansOfType(GXProcessCanalDataService.class)` 构建不可变 `Map<String, GXProcessCanalDataService>` 快照并单独持有 `defaultProcessCanalDataService`；`resolve(database, table)` 走 `ConcurrentHashMap` 缓存（键 `database.table`，含 `NO_HANDLER` 负结果），缓存超过 `canal.handler-cache-size` 时整体清空；对"命名不合法"与"库表组合冲突"记录首见 WARN 并计数（`data-model.md` §3.1）
- [X] T016 [P] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/support/GXCanalFailureHandler.java`：方法 `void handle(GXCanalParseResultInnerDto result, @Nullable String rawPayload)`，Javadoc 写明"结局分类已确定，实现只负责传输级动作"
- [X] T017 运行 `mvn -o -B -pl leaf-base-data-sync -am test`（用例来源 `leaf-base-data-sync/src/test/java/cn/maple/canal/`），确认 Phase 2 后编译与既有 12 个用例仍全部通过（ErrorProne/NullAway 无 error）

**Checkpoint**: 类型与配置骨架就绪，可开始按 Story 增量交付

---

## Phase 3: User Story 1 - 审查结论可复核、缺陷可复现 (Priority: P1) 🎯 MVP

**Goal**: 交付可复核的审查报告，并把两个"待核实"项定案（`@Lazy` 端点注册、Canal 载荷形态）。

**Independent Test**: 随机抽取报告中的一条发现，按其"文件/行号 + 复现步骤"在本地复现；两个待核实项必须给出明确结论（而非"待确认"）。

### Tests for User Story 1

- [X] T018 [P] [US1] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/listener/GXCanalListenerRegistrationIT.java`：用 `ApplicationContextRunner` 加载 `GXCanalConfig` + `GXCanalRabbitMQListener`，断言 `RabbitListenerEndpointRegistry#getListenerContainers()` 非空且目标容器 `isRunning()==true`；同时提供一个带 `@Lazy` 的对照上下文，用两次结果定案 F-03（不写真实 broker 连接）
- [X] T019 [P] [US1] 扩展 `leaf-base-data-sync/src/test/java/cn/maple/canal/service/impl/GXCanalMessageParseServiceImplTest.java`：用 T005 的 `canal/batch.json` 断言当前实现对数组载荷的行为（预期失败/空结果），把 E-08 变成可执行证据
- [X] T020 [US1] 新建 `specs/017-data-sync-mq-switchable/review-report.md`：写入基线数据（T001）、E-01～E-22 证据（文件:行号）、F-01～F-18 分级、P-01～P-06 保持项、每条高风险发现的复现步骤与验证命令，以及 T018/T019 的定案结论

### Implementation for User Story 1

- [X] T021 [US1] 依据 T018 结论处理 `@Lazy`：若确认端点未注册，则在 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRabbitMQListener.java` 移除 `@Lazy`，并在 `review-report.md` 标注为"生产行为变更 + 首次上线需确认处理器幂等性"；若确认已注册，则保留注解并把证据写入报告
- [X] T022 [US1] 依据 T019 结论确定数组载荷优先级，更新 `review-report.md` 的"未验证项/已定案项"清单，并把结论链接回 `research.md` D-04

**Checkpoint**: 审查结论与两个待核实项全部可复核，可独立交付给维护者决策

---

## Phase 4: User Story 2 - 每条消息都有确定结局，毒消息不再空转 (Priority: P1)

**Goal**: 四类结局落地，解析失败不再 ack 消失，重投有上限，死信可运维。

**Independent Test**: 用 quickstart V2 的输入表逐条投递，断言每条消息的结局、重投次数与计数；不存在"无结局"路径。

### Tests for User Story 2

- [X] T023 [P] [US2] 扩展 `leaf-base-data-sync/src/test/java/cn/maple/canal/service/impl/GXCanalMessageParseServiceImplTest.java`：覆盖空白、非 JSON、缺库表、`isDdl=true`、`type=CREATE`、未知类型、处理器返回 `null`、处理器抛异常、`batch.json` 混合元素（成功/跳过/失败各一），逐条断言 `GXCanalOutcomeEnums` 与 `reason`
- [X] T024 [P] [US2] 重写 `leaf-base-data-sync/src/test/java/cn/maple/canal/listener/GXCanalRabbitMQListenerTest.java`：断言监听器按结局调用 `GXCanalFailureHandler`（SUCCESS/SKIPPED 不调用；RETRYABLE/NON_RETRYABLE 调用且不再吞异常）
- [X] T025 [P] [US2] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/service/impl/rabbit/GXCanalRabbitMQFailureHandlerTest.java`：断言 `x-canal-retry-count` 0→1→2→3 递增重投、第 4 次抛 `AmqpRejectAndDontRequeueException`、`NON_RETRYABLE_FAILURE` 直接抛该异常，且 `canal.consumer.retryable.failure`/`dead.letter` 计数正确

### Implementation for User Story 2

- [X] T026 [US2] 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/GXCanalMessageParseService.java`：新增 `GXCanalParseResultInnerDto parse(String raw)`（支持对象与数组，返回首个/逐元素结果需在实现类内以列表形式对外提供）；**保留** `Dict parseMessage(String)` 签名不变
- [X] T027 [US2] 重写 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/impl/GXCanalMessageParseServiceImpl.java`：改为单次 `JSONUtil.parse` + `instanceof JSONObject/JSONArray` 分支；DDL（`isDdl=true` 或 `CREATE/ALTER/ERASE/DROP/TRUNCATE`）→ `SKIPPED`；库表缺失/未知类型/处理器缺失且无兜底/返回 `null` → `NON_RETRYABLE_FAILURE`；处理器异常透传（由调用侧归类为 `RETRYABLE_FAILURE`）；`parseMessage` 作为兼容包装把结局映射回 `status`/`message` 键
- [X] T028 [US2] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/impl/rabbit/GXCanalRabbitMQFailureHandler.java`：实现 T016 端口；可重试且未达 `canal.retry.max-attempts` 时用 `RabbitTemplate` 重投到同一交换机/路由键并写入自增头，达上限或不可重试则抛 `AmqpRejectAndDontRequeueException`；全程更新 `GXCanalConsumeStatistics`
- [X] T029 [US2] 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/config/GXCanalConfig.java`：主队列改为 `QueueBuilder.durable(...).deadLetterExchange(...).deadLetterRoutingKey(...)`，新增 `canal.dlx`（DirectExchange，durable，非 autoDelete）与 `canal.dlq`（durable）两个 Bean；全部 Bean 加 `@ConditionalOnMissingBean`
- [X] T030 [US2] 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRabbitMQListener.java`：改调 `parse`，逐元素按结局分派，失败交给 `errorHandler`（`@RabbitListener(errorHandler = ...)`）；日志改为 ASCII 且带 `traceId`/`database`/`table`/`batchIndex`
- [X] T031 [US2] 在 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/support/GXCanalHandlerResolver.java` 与 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/impl/GXCanalMessageParseServiceImpl.java` 中落地 `canal.log.handler-miss-level`（`off`/`debug`/`warn-once`/`warn`）与 `handlerMiss` 计数（FR-010），首见 WARN 场景覆盖"处理器未命中"与"命名冲突"

**Checkpoint**: US1 + US2 均为 P1，可先交付"结论 + 不丢消息"的 MVP

---

## Phase 5: User Story 3 - 通过配置切换消息中间件，业务处理器零改动 (Priority: P2)

**Goal**: `canal.mq-type` 一键切换 RabbitMQ / RocketMQ，业务处理器与命名约定零改动，依赖隔离。

**Independent Test**: 同一组处理器与载荷在两种 `canal.mq-type` 下处理结果一致；`rocketmq` 模式不声明任何 AMQP 基础设施；缺依赖时启动失败并给出可操作提示。

### Tests for User Story 3

- [X] T032 [P] [US3] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/config/GXCanalRocketMQConfigTest.java`：断言 `mq-type=rocketmq` 时无 `Exchange`/`Queue`/`Binding` Bean；`mq-type=UNKNOWN` 启动失败且消息含 `rabbitmq`/`rocketmq`；用 `FilteredClassLoader(RocketMQListener.class)` 断言缺依赖时失败信息含 `leaf-base-rocketmq`
- [X] T033 [P] [US3] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/listener/GXCanalRocketMQListenerTest.java`：断言 `onMessage` 委派 `parse`；可重试失败抛出异常（交出 `RECONSUME_LATER`）；不可重试失败调用死信转发后正常返回
- [X] T034 [US3] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/integration/GXCanalConsumerSpringBootIT.java`：`@SpringBootTest` 分别在 `mq-type=rabbitmq` / `rocketmq` profile 下启动，投递同一组载荷（单对象 + 数组），断言处理器调用序列与 `GXCanalConsumeStatistics` 计数逐条一致；真实 broker 用例由 `RABBITMQ_*`/`ROCKETMQ_NAME_SERVER` 门控（`assumeTrue`）

### Implementation for User Story 3

- [X] T035 [US3] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRocketMQListener.java`：`implements RocketMQListener<String>` + `@RocketMQMessageListener(consumerGroup="${canal.rocketmq.consumer-group}", topic="${canal.rocketmq.topic}", selectorExpression="${canal.rocketmq.tag:*}", maxReconsumeTimes=...)`，整体 `@ConditionalOnProperty(canal.mq-type=rocketmq)` + `@ConditionalOnClass(RocketMQListener.class)`；内部委派 `parse` 并按结局抛异常/正常返回
- [X] T036 [US3] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/impl/rocketmq/GXCanalRocketMQFailureHandler.java`：不可重试失败经 `GXSendRocketMQService#sendOneway(GXRocketMQMessageReqDto)` 转发到 `canal.rocketmq.dead-letter-topic`（未配置则只记录 + 计数），不改变 `rocketmq.*` 既有键语义
- [X] T037 [US3] 为 `leaf-base-data-sync/src/main/java/cn/maple/canal/config/GXCanalConfig.java` 与 `GXCanalRabbitMQListener` 增加 `@ConditionalOnProperty(name="canal.mq-type", havingValue="rabbitmq", matchIfMissing=true)`，确保默认行为与改造前一致且与 RocketMQ 分支互斥
- [X] T038 [US3] 新建 `leaf-base-data-sync/src/main/java/cn/maple/canal/config/GXCanalRocketMQConfig.java`：校验 `mq-type=rocketmq` 时 `canal.rocketmq.topic`/`consumer-group` 非空（缺失即抛 `GXBusinessException` 并指出键名），并在 `RocketMQListener` 类缺失时给出"引入 `leaf-base-rocketmq` 或改用 rabbitmq"的启动失败提示

**Checkpoint**: MQ 切换可用，业务侧零改动；RabbitMQ 用户依赖树不被污染

---

## Phase 6: User Story 4 - 热路径开销与线程安全可量化 (Priority: P2)

**Goal**: 消除热路径容器查找与异常控制流，缓存有界，并发无串扰，优化效果可测量。

**Independent Test**: quickstart V7 —— 10 万条未命中消息的框架侧耗时下降 ≥ 80%，容器查找次数为 0；32 线程 × 1000 条计数自洽且连续 10 次通过。

### Tests for User Story 4

- [X] T039 [P] [US4] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/service/support/GXCanalHandlerResolverTest.java`：覆盖启动快照命中、负缓存、缓存上限清空、命名冲突/非法名告警计数、以及 32 线程并发 `resolve` 结果一致性
- [X] T040 [P] [US4] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/metrics/GXCanalConsumeStatisticsTest.java`：32 线程 × 1000 条并发计数（总和 = 32000）、`MeterRegistry` 缺席时不影响功能、`reset()` 语义
- [X] T041 [P] [US4] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/benchmark/GXCanalParseBenchmarkTest.java`：10 万条"表未注册处理器"消息的本地微基准，输出框架侧单条耗时与容器查找次数（用可计数的 `ApplicationContext` 包装或探针 Bean），作为 SC-004 的证据来源

### Implementation for User Story 4

- [X] T042 [US4] 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/impl/GXCanalMessageParseServiceImpl.java`：处理器定位改为调用 `GXCanalHandlerResolver#resolve`，删除 `GXSpringContextUtils.getBean` 调用，确保热路径不再产生 `NoSuchBeanDefinitionException`
- [X] T043 [US4] 优化热路径日志：在 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/impl/GXCanalMessageParseServiceImpl.java` 与 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRabbitMQListener.java` 中使用参数化占位符且避免字符串拼接，`canal.log.handler-miss-level` 生效，error 级失败日志按"首见 + 计数聚合"输出，避免故障期日志放大（FR-021）
- [X] T044 [US4] 在 `leaf-base-data-sync/src/main/java/cn/maple/canal/config/GXCanalConfig.java` 的启动日志输出在途上限（`canal.concurrency-count × spring.rabbitmq.listener.simple.prefetch` 或 RocketMQ `consume-thread-max`）与缓存上限，使容量可观测（FR-022）

**Checkpoint**: 性能目标可测量，并发无串扰

---

## Phase 7: User Story 5 - 装配可控、配置可发现、链路可追踪 (Priority: P3)

**Goal**: 可关闭消费、配置键可提示、配置源真实生效且与类路径无关、日志可关联链路。

**Independent Test**: quickstart V8 —— 四种"类路径 × 配置源"组合各有断言且启动日志与实际一致；关闭开关后无 MQ Bean、无连接尝试；元数据覆盖全部公开键。

### Tests for User Story 5

- [X] T045 [P] [US5] 新建 `leaf-base-data-sync/src/test/java/cn/maple/canal/properties/GXCanalPropertiesBindingTest.java`：用 `ApplicationContextRunner` + `FilteredClassLoader` 覆盖四种组合（有/无 nacos 类路径 × 默认/显式 `canal.config-source`），断言装配结果、启动日志中的来源标识、以及 `withPropertyValues` 覆盖后键值能真实绑定（修复 E-11）
- [X] T046 [P] [US5] 扩展 `leaf-base-data-sync/src/test/java/cn/maple/canal/config/GXCanalConfigTest.java`：`canal.consumer-enabled=false` 时无 `Exchange`/`Queue`/`Binding`/DLQ Bean；预置下游同名 `Queue` Bean 时本模块让位且启动成功

### Implementation for User Story 5

- [X] T047 [US5] 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/config/GXCanalConfig.java` 与 `GXCanalRocketMQConfig.java`、两个监听器：统一加 `canal.consumer-enabled` 条件（`matchIfMissing=true`），关闭时不装配任何 MQ 基础设施
- [X] T048 [US5] 修改 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/local/GXLocalCanalProperties.java` 与 `.../nacos/GXNacosCanalProperties.java`：实现 `canal.config-source=local|nacos` 显式互斥装配（缺省等价旧探测行为），并在装配处打印一条 INFO：实际来源 + 关键默认值（不含密钥）
- [X] T049 [US5] 补齐 `leaf-base-data-sync/src/main/resources/META-INF/additional-spring-configuration-metadata.json`：`data-model.md` §4.2 的全部新键（类型、默认值、`hints` 的枚举取值）
- [X] T050 [US5] 在 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRabbitMQListener.java` 与 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRocketMQListener.java` 实现 TraceId/MDC：优先读取 AMQP `headers["traceId"]` / RocketMQ `userProperties["traceId"]`，缺失则 `GXTraceIdContextUtils.generateTraceId()`，`setTraceId` 后于 `finally` 调 `removeTraceId()`（D-11）
- [X] T051 [US5] 更新 `leaf-base-data-sync/AGENTS.md` 与 `docs/leaf-base-data-sync开发手册.md`：新增/变更配置键表、MQ 切换说明、失败处置语义、C7 不支持范围表，并修正既有漂移（`RABBITMQ_CANAL_QUEUE_NAME` 默认值 `example`→`canalQueue`、`CanalConstant`→`GXCanalConstant`、删除"RabbitMQ 重启不丢消息"的不成立承诺）

**Checkpoint**: 接入与排障体验达标，全部 User Story 完成

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 收口、全量验证与交付材料

- [X] T052 [P] 清理死代码与冗余：删除 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/impl/GXCanalMessageParseServiceImpl.java` 中已无用的 `isTypeJSON` 前置校验残留，统一 `operator` 常量来源到 `leaf-base-data-sync/src/main/java/cn/maple/canal/constant/GXCanalConstant.java`，检查新增类型无未使用公共 API
- [X] T053 [P] 执行 quickstart V6 依赖树校验：`mvn -o -B -pl leaf-base-data-sync dependency:tree "-Dincludes=org.apache.rocketmq:*,com.alibaba.nacos:*,com.alibaba:fastjson"`，记录 `leaf-base-data-sync/pom.xml` 中 `leaf-base-rocketmq` 为 optional 且本模块无直接 rocketmq 坐标，结果写入 `specs/017-data-sync-mq-switchable/review-report.md`
- [X] T054 [P] 在临时下游工程 `<tmp>/canal-consumer-check/pom.xml`（仅依赖 `leaf-base-data-sync`）上重复 V6 命令，验证 `org.apache.rocketmq`/`fastjson` 条目为 0（SC-011），完成后删除临时工程
- [X] T055 执行 quickstart V1/V2/V3/V4/V5/V7/V8/V9 全流程，把每条场景的"通过/跳过（原因）"写入 `specs/017-data-sync-mq-switchable/review-report.md` 的"验证结果"章节
- [X] T056 运行 `mvn -o -B -pl leaf-base-data-sync -am test`（用例目录 `leaf-base-data-sync/src/test/java/cn/maple/canal/`）与构建期静态检查，确认 ErrorProne/NullAway 无 error；不得修改 `leaf-base-data-sync/pom.xml` 与根 `pom.xml` 中的 `maven-compiler-plugin` 参数规避
- [X] T057 编写交付说明（PR 描述草稿）到 `review-report.md` 末尾：改动动机、影响面、配置键变更、依赖树变化与传递依赖影响（宪法附加约束 §3）、`@Lazy` 移除的上线注意项、真实中间件"未验证项"清单

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖，可立即开始
- **Phase 2 (Foundational)**: 依赖 Phase 1；**阻塞所有 User Story**
- **Phase 3 (US1, P1)**: 依赖 Phase 2；交付审查报告与两个待核实项定案
- **Phase 4 (US2, P1)**: 依赖 Phase 2；与 US1 可并行（US1 只改报告与 `@Lazy`/数组断言，US2 改解析与失败处置）；**T021（移除 `@Lazy`）与 T030 同文件，须串行**
- **Phase 5 (US3, P2)**: 依赖 US2（监听器/失败处置骨架已就位）
- **Phase 6 (US4, P2)**: 依赖 US2（解析链路已结构化）；T042 与 T027 同文件，须串行
- **Phase 7 (US5, P3)**: 依赖 US2/US3（两套基础设施都已存在）
- **Phase 8 (Polish)**: 依赖全部 Story

### Within Each User Story

1. 测试任务先写并先失败（T023～T025 等）；
2. 类型/端口 → 实现 → 装配 → 集成；
3. Story 完成后跑该 Story 的 Independent Test，再进入下一优先级。

### Story 完成顺序（推荐）

```text
Setup → Foundational → US1 ┐
                          ├→ US2 → US3 → US5
                          └→ US4 ┘
                          ↑ US4 的 T042 需在 US2 的 T027 之后
```

---

## Parallel Execution Examples

### Phase 2（类型骨架，全部不同文件）

```text
Task: "T006 GXCanalOutcomeEnums.java"
Task: "T007 GXCanalMqTypeEnums.java"
Task: "T008 GXCanalParseResultInnerDto.java"
Task: "T009 GXCanalConstant.java"
Task: "T010 GXCanalConsumeStatistics.java"
```

### US1（报告与两个定案验证互不干扰）

```text
Task: "T018 GXCanalListenerRegistrationIT.java"
Task: "T019 batch.json 数组载荷断言"
→ 两者结果汇入 T020 review-report.md
```

### US2（三个测试类可并行编写）

```text
Task: "T023 解析结局表用例"
Task: "T024 监听器结局驱动用例"
Task: "T025 GXCanalRabbitMQFailureHandlerTest.java"
```

### US3（测试与实现按文件隔离）

```text
Task: "T032 GXCanalRocketMQConfigTest.java"
Task: "T033 GXCanalRocketMQListenerTest.java"
Task: "T035 GXCanalRocketMQListener.java"
Task: "T036 GXCanalRocketMQFailureHandler.java"
```

---

## Implementation Strategy

### MVP First（US1 + US2）

1. Phase 1 Setup → Phase 2 Foundational；
2. Phase 3 US1：审查报告 + 两个待核实项定案（不改变运行行为，可独立评审）；
3. Phase 4 US2：结局分类 + 失败处置（最高风险项修复）；
4. **STOP & VALIDATE**：跑 quickstart V1/V2/V3，确认"无结局路径 = 0"再继续。

### Incremental Delivery

1. US1 → 交付报告，维护者可据此排期；
2. US2 → 上线"不丢消息 + 有界重投"（生产行为变更需与下游确认幂等）；
3. US4 → 上线性能改进（可独立回滚，风险低）；
4. US3 → 上线 RocketMQ 切换能力（默认关闭，不影响现存部署）；
5. US5 → 收口装配/配置/追踪与文档。

### 风险与回滚

| 风险点 | 缓解 | 回滚方式 |
| --- | --- | --- |
| 移除 `@Lazy` 后开始真实消费 | T018 先定案；上线前确认处理器幂等 | 恢复 `@Lazy`（回到原状） |
| RocketMQ 消费容器在 Boot 4.1 不可用 | T034/T035 先做装配验证；不可用则按 `research.md` D-08 备选方案并登记 | 只上 `mq-type=rabbitmq`，RocketMQ 分支保持未启用 |
| 配置源改为显式开关导致下游行为变化 | 默认值等价旧行为 + T045 四组合测试 | 保留旧探测路径，新增键不强制 |
| 死信队列名与下游冲突 | `@ConditionalOnMissingBean` + 可配置键 | 改键值或预置 Bean |

---

## Notes

- [P] 任务 = 不同文件且无未完成依赖；同文件任务（T021/T030、T027/T042、T029/T047）必须串行
- 测试任务必须在实现前先失败（宪法 §VI/§VI 回归要求）
- 真实中间件用例一律 `assumeTrue` 门控，跳过项写入 `review-report.md`
- 每完成一个逻辑组提交一次，提交信息遵循 `类型(范围): 简述`（如 `fix(data-sync): 消费结局显式化与有界重投`）
- 交付前确认未修改 `leaf-base-rabbitmq` / `leaf-base-rocketmq` 任何文件（`git status` 检查）

---

## Phase 9: Convergence

- [X] T058 为 `leaf-base-data-sync/src/main/java/cn/maple/canal/properties/GXCanalProperties.java` 及 `GXCanalRetryProperties.java`、`GXCanalDeadLetterProperties.java`、`GXCanalRocketMQConsumerProperties.java` 中跨线程共享的字段建立可见性保证（`volatile` 字段或启动后不可变快照），并在 `GXCanalPropertiesBindingTest` 中补一条并发可见性用例 per FR-014 (partial)
- [X] T059 收敛 `canal.rocketmq.consume-thread-max` 的口径：要么在 `leaf-base-data-sync/src/main/java/cn/maple/canal/config/GXCanalRocketMQConfig.java` 启动校验"仅支持注解固定值，其它值 fail-fast"，要么从 `GXCanalRocketMQConsumerProperties.java` 与 `additional-spring-configuration-metadata.json`、`docs/leaf-base-data-sync开发手册.md` §6.2 中移除该键 per FR-022/FR-028 (contradicts)
- [X] T060 新增 `leaf-base-data-sync/src/test/java/cn/maple/canal/integration/GXCanalRealBrokerIT.java`：以 `assumeTrue(RABBITMQ_HOST / ROCKETMQ_NAME_SERVER)` 门控的真实投递用例，覆盖成功消费、死信可见性与重投上限，并把跳过项写入 `specs/017-data-sync-mq-switchable/review-report.md` per FR-041/SC-008 (missing)
- [X] T061 在 `leaf-base-data-sync/src/test/java/cn/maple/canal/benchmark/GXCanalParseBenchmarkTest.java` 增加改造前基线测量（模拟 `GXSpringContextUtils.getBean` + 异常驱动的查找路径），输出对照耗时与下降比例并回写 `review-report.md` §5 per SC-004 (partial)
- [X] T062 为 `GXCanalRabbitMQFailureHandler.java`、`GXCanalRocketMQFailureHandler.java` 的 ERROR 日志与 `GXCanalMessageParseServiceImpl.java` 的解析 WARN 日志增加节流/聚合（首 N 条 + 累计计数），并补相应用例 per FR-021 (partial)
- [X] T063 在 `leaf-base-data-sync/src/main/java/cn/maple/canal/dto/GXCanalDataDto.java` 为 `data` / `old` 补"与处理器共享可变引用"语义说明，并为可空字段补 `@org.jspecify.annotations.Nullable` per FR-016 (partial)
- [X] T064 在 `leaf-base-data-sync/src/main/java/cn/maple/canal/config/GXCanalPropertiesConfig.java` 或 `GXCanalConfig.java` 启动时输出一条"装配期配置键（mq-type/queue/exchange/concurrency/topic/consumer-group）变更需重启生效"的 INFO，并在 `GXCanalPropertiesBindingTest` 中断言 per FR-015 (partial)
- [X] T065 为 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/GXCanalMessageParseService.java` 的新增方法 `parse` 提供 `default` 实现（委托 `parseMessage` 或抛出明确的 `UnsupportedOperationException`），或在 `AGENTS.md`/手册登记接口新增方法的兼容性声明 per Constitution §II (partial)
- [X] T066 在 `leaf-base-data-sync/AGENTS.md` 增加"不支持范围（能力 + 原因 + 替代方案）"表格，与 `docs/leaf-base-data-sync开发手册.md` §6.4 保持一致 per FR-038 (partial)
- [X] T067 处理未使用的公共 API：`GXCanalConsumeStatistics#logSummary`（接线到 `@PreDestroy` 或删除）、`GXCanalHandlerResolver#hasDefaultHandler`、`GXCanalParseResultInnerDto#isSuccess`；`handlerCount()` 仅测试使用需注明或内联 per Constitution 附加约束 §8 (unrequested)
- [X] T068 新增 `leaf-base-data-sync/src/test/java/cn/maple/canal/dependency/DependencyIsolationTest.java`：解析 `leaf-base-data-sync/pom.xml` 断言 `leaf-base-rocketmq` 声明为 `<optional>true</optional>` 且不存在直接 `org.apache.rocketmq:*` 坐标 per SC-006/F-17 (missing)
- [X] T069 在 `GXCanalPropertiesBindingTest` 补"配置刷新语义"用例（装配期键刷新后不生效且日志提示），并把 32 线程 × 1000 条并发用例改为 `@RepeatedTest(10)` per FR-040/SC-005 (partial)
- [X] T070 在 `GXCanalRabbitMQFailureHandler#republish` 中透传 `traceId` 头；RocketMQ 侧按 `research.md` D-11 读取 `userProperties["traceId"]`（不可行时在文档显式登记"仅生成新 traceId"） per FR-036 (partial)

---

## Phase 10: Convergence

- [X] T071 删除 `docs/leaf-base-data-sync开发手册.md` §6.1 示例 yaml 中的 `consume-thread-max: 20`，并把 §5"性能调优"中"RocketMQ 侧由 `canal.rocketmq.consume-thread-max` 决定"改为"由 `@RocketMQMessageListener` 注解常量固定为 20，启动日志打印实际值" per FR-039 (contradicts)
- [X] T072 将 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRabbitMQListener.java` 与 `GXCanalRocketMQListener.java` 的 4 处 `log.error`/`log.warn` 改为经 `GXCanalLogThrottle` 输出（键按失败类型划分），并补监听器层节流用例 per FR-021 (partial)
- [X] T073 让 RocketMQ 消费并发度可配置：改用程序化 `DefaultRocketMQListenerContainer` 装配（starter 提供，不违反 FR-043），从新增的 `canal.rocketmq.consume-thread-max` 读取线程数并同步元数据/文档；若确认不可行则在 `spec.md` 提交修订说明并把该条从 §10"不支持"升级为显式协商项 per FR-022 (partial)
- [X] T074 修正 `specs/017-data-sync-mq-switchable/contracts/canal-consumer-contract.md` §C7 与 `docs/leaf-base-data-sync开发手册.md` §6.4"批量确认"行中引用的 `consume-thread-max`，改为 `spring.rabbitmq.listener.simple.prefetch` 与 RocketMQ 注解 `consumeThreadMax` per FR-039 (contradicts)
- [X] T075 修正 `specs/017-data-sync-mq-switchable/data-model.md` §1.1 中"超 `max-reconsume-times`"表述，改为"超过注解固定值 3 次后进入 `%DLQ%<group>`" per FR-039 (partial)
- [X] T076 为 `GXCanalRabbitRealBrokerTest.BrokerConfig` 与 `GXCanalRocketRealBrokerTest.BrokerConfig` 新增离线上下文冒烟用例（`ApplicationContextRunner` + `autoStartup=false`，不建立连接），确保门控用例的装配在无 broker 环境下也能被验证 per FR-041/SC-008 (missing)
- [X] T077 回写 `specs/017-data-sync-mq-switchable/review-report.md` §5 的 V3/V4 行：标注已新增门控用例且本环境结果为 `Skipped: 2`，并把两个用例名列入"跳过项"清单 per FR-039/SC-008 (partial)

---

## Phase 11: Convergence

- [X] T078 在 `leaf-base-data-sync/src/test/java/cn/maple/canal/service/support/GXCanalHandlerResolverTest.java` 补两条告警路径用例：①库表名含 `.`/`-`/大写时首见 WARN 且仍按兜底解析；②`db=a_b/table=c` 与 `db=a/table=b_c` 解析到同一 Bean 名时触发命名冲突告警且两次解析都成功 per FR-040/FR-011 (partial)
- [X] T079 把 `specs/017-data-sync-mq-switchable/quickstart.md` 第 67 行与 `specs/017-data-sync-mq-switchable/contracts/canal-consumer-contract.md` 第 45 行的"`GXCanalConfig` 的 5 个 Bean"更正为 6 个，并列出死信三件套（`canalDeadLetterExchange`/`canalDeadLetterQueue`/`canalDeadLetterBinding`） per FR-039 (partial)

---

## Phase 12: Convergence

- [X] T080 修正 `specs/017-data-sync-mq-switchable/quickstart.md` 中不存在的用例类名：V3（第 63 行）与 V4（第 82 行）的 `GXCanalConsumerSpringBootIT` 改为 `GXCanalConsumerSpringBootTest`，并在两处补入门控用例 `GXCanalRabbitRealBrokerTest` / `GXCanalRocketRealBrokerTest` 与离线冒烟 `GXCanalRealBrokerBootstrapTest` per FR-039 (partial)
- [X] T081 修正 `specs/017-data-sync-mq-switchable/quickstart.md` 第 29 行的"既有 12 个用例"为"既有 16 个用例（parse 12 + config 2 + listener 2）"，并把第 18 行的"必须 `assumeTrue`"改为"必须由环境变量门控（实现使用 `@EnabledIfEnvironmentVariable`，等价于 `assumeTrue` 语义）" per FR-039 (partial)
- [X] T082 把 `specs/017-data-sync-mq-switchable/review-report.md` 第 103 行（V9）的"18 个键"更正为"17 个键、3 组 hints"，与 `leaf-base-data-sync/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 实际条目数一致 per FR-039 (partial)

---

## Phase 13: Convergence

- [X] T083 对齐 FR-009 的元素级失败语义：在 `leaf-base-data-sync/src/main/java/cn/maple/canal/listener/GXCanalRabbitMQListener.java` 与 `GXCanalRocketMQListener.java` 的批量处理循环中，对**每个**失败元素分别累加 `retryableFailure`/`nonRetryableFailure` 并逐条（经 `GXCanalLogThrottle`）记录，仅对首个失败执行一次传输动作；同时处理"处理器异常中断同批后续元素"的行为——或在 `GXCanalMessageParseServiceImpl` 内改为按元素捕获异常并标记该元素为 `RETRYABLE_FAILURE` 后继续处理剩余元素（`parseMessage` 保持对首个元素异常透传以兼容旧契约），或在 `AGENTS.md` §10 与开发手册登记该限制并补充"因中断而未处理的元素数"计数；补相应用例（同批 2+ 个失败元素、同批第 1 个元素抛异常后其余元素仍被处理） per FR-009 (partial)

---

## Phase 14: Convergence

- [X] T084 修正 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/GXCanalMessageParseService.java` 中 `parse` 的 Javadoc：删除"never returns `RETRYABLE_FAILURE`"的断言，改为"单个 JSON 对象载荷上抛处理器异常（保持 `parseMessage` 旧契约）；JSON 数组载荷逐元素捕获，失败元素以 `RETRYABLE_FAILURE` 返回且不影响其余元素" per FR-002/FR-039 (contradicts)
- [X] T085 修正 `docs/leaf-base-data-sync开发手册.md` §2.6 第 83 行的相同断言，使其与同文档 §6.3"批量语义"段（单元素失败不中止同批、逐元素计数、整批单次传输动作）一致 per FR-039 (contradicts)
- [X] T086 合并 `leaf-base-data-sync/AGENTS.md` 中相互冲突的两条红线：第 140 行（要求处理器异常必须从 `GXCanalMessageParseServiceImpl` 抛出）与第 149 行（要求批内元素隔离并转为 `RETRYABLE_FAILURE`）统一表述为"单个对象载荷上抛、数组载荷逐元素隔离" per FR-039 (contradicts)
- [X] T087 在 `specs/017-data-sync-mq-switchable/contracts/canal-consumer-contract.md` 的 §C2 行为变更说明（第 25 行）与失败处置表（第 69–70 行）补入批量语义：批内元素级隔离、逐元素计数与日志、整批只执行一次传输动作、两类失败并存时优先可重试 per FR-039 (partial)
- [X] T088 在 `specs/017-data-sync-mq-switchable/data-model.md` 的状态机（第 160–178 行）与计数说明（§3）中补齐"逐元素计数、整批单次传输动作、可重试优先"三条规则，与手册 §6.3 保持一致 per FR-039 (partial)

---

## Phase 15: Convergence

- [X] T089 在 `leaf-base-data-sync/AGENTS.md` 的目录职责第 10 条补登记 `service/support` 下缺失的 `GXCanalLogThrottle`（热路径日志节流，与 §9 红线 9 呼应），使目录地图与 `leaf-base-data-sync/src/main/java/cn/maple/canal/service/support` 的实际三个类一致 per FR-034/FR-039 (partial)

---

## Phase 16: Convergence

- [X] T090 消除 `leaf-base-data-sync/src/main/java/cn/maple/canal/constant/GXCanalConstant.java` 中的死常量与重复字面量：`LOG_LEVEL_WARN_ONCE` 当前零引用，而 `DEFAULT_HANDLER_MISS_LOG_LEVEL` 又重复写了 `"warn-once"` → 二者取其一（推荐让默认值引用 `LOG_LEVEL_WARN_ONCE`，或在 `GXCanalHandlerResolver` 的 warn-once 分支显式引用该常量后保留），使默认值与判定分支同源、33 个常量全部有引用 per FR-039/Constitution 附加约束 §8 (unrequested)
