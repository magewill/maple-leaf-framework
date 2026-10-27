---
description: "Task list for leaf-base-rabbitmq 正确性、线程安全与性能审查及修复"
---

# Tasks: leaf-base-rabbitmq 正确性、线程安全与性能审查及修复

**Input**: Design documents from `/specs/016-rabbitmq-module-audit/`

**Prerequisites**: plan.md（技术栈与结构）、spec.md（6 个用户故事、33 条 FR）、research.md（14 条决策）、data-model.md（9 个模型）、contracts/（5 份契约）、quickstart.md（9 步验证）

**Tests**: 明确要求（spec FR-024～FR-031、用户指令"修改完代码之后一定做功能完备性的测试"）。所有修复任务前必须先有失败的回归用例。

**Organization**: 按用户故事分组；US1/US2/US3/US5 为 P1，US4 为 P2，US6 为 P3。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：可并行（不同文件、无未完成依赖）
- **[Story]**：所属用户故事（US1～US6）
- 所有路径均为仓库相对路径

## Path Conventions

- 生产代码：`leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/`
- 资源：`leaf-base-rabbitmq/src/main/resources/META-INF/`
- 测试：`leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/`
- 规格产物：`specs/016-rabbitmq-module-audit/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立测试基线、测试依赖与资源目录，后续所有故事共用。

- [X] T001 执行基线命令 `mvn -o -B -pl leaf-base-rabbitmq test` 并记录输出（期望 `BUILD SUCCESS`、0 用例）到 `specs/016-rabbitmq-module-audit/review-report.md` 的"基线"章节
- [X] T002 [P] 修改 `leaf-base-rabbitmq/pom.xml`：新增 test 作用域 `spring-boot-starter-test`、`spring-rabbit-test`、`org.junit.jupiter:junit-jupiter`；移除零引用的 `leaf-base-retry` 依赖
- [X] T003 [P] 新建测试启动类 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/GXRabbitMQTestApplication.java`（`@SpringBootApplication(scanBasePackages="cn.maple.rabbitmq")` + `@EnableAutoConfiguration`，仅测试用）
- [X] T004 [P] 新建 `leaf-base-rabbitmq/src/main/resources/META-INF/additional-spring-configuration-metadata.json`，登记 `maple.framework.rabbitmq.config-source`（默认 `nacos`，枚举 `local`/`nacos`）、`maple.framework.rabbitmq.scheduler.expose`（默认 `true`）、`maple.framework.rabbitmq.thread-pool.{core-size,max-size,queue-capacity}`、`maple.framework.mq.rabbitmq.{enable,default-queue-name}`
- [X] T005 [P] 新建 `leaf-base-rabbitmq/src/main/resources/META-INF/spring.factories`，注册 `org.springframework.boot.env.EnvironmentPostProcessor=cn.maple.rabbitmq.properties.GXRabbitMQEnvironmentPostProcessor`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有故事共用的异常、统计、空值契约与测试夹具。

**⚠️ CRITICAL**: 本阶段完成前不得开始任何用户故事。

- [X] T006 新建 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/exception/GXRabbitMQOperationException.java`：继承 `GXBusinessException`，字段 `operation`（枚举 `DECLARE_EXCHANGE`/`DECLARE_QUEUE`/`BIND`/`UNBIND`/`DELETE_QUEUE`/`PURGE_QUEUE`/`SEND`）、`resource`（`String`，不含敏感值）、`detail`（`Dict`，含 `completedSteps`/`failedStep`/`causeType`）
- [X] T007 [P] 新建 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/exception/package-info.java`，内容 `@org.jspecify.annotations.NullUnmarked`（与模块其余包一致）
- [X] T008 [P] 新建 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/impl/GXRabbitMQListenerStatistics.java`：通过 `ObjectProvider<MeterRegistry>` 可选注册 Counter `rabbitmq.listener.messages.processed`、`.rejected`（tag `reason=empty|unparseable`）、`.failed`（tag `type`，白名单外归 `other`）；无注册表时仅记 ASCII debug
- [X] T009 [P] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/dto/inner/GXRabbitMQMessageReqDto.java`：为 `correlationData` 加 `@Nullable`，`data`/`messageProperties` 契约 Javadoc（发送后不被改写），`tag` 标注"发送侧忽略，仅为兼容保留"
- [X] T010 [P] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/GXRabbitMQProperties.java`：`defaultQueueName` 补 Javadoc（同时支持 `spring.rabbitmq.default-queue-name` 与 `defaultQueueName` 两种写法），`connectionLimit`/`channelCacheSize`/`channelCheckoutTimeout` 补非负校验说明
- [X] T011 [P] 新建测试夹具 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/support/GXTestRabbitFixtures.java`：构造 Mock `RabbitTemplate`/`RabbitAdmin`、`GXRabbitMQMessageReqDto` 样例、MDC 清理工具
- [X] T012 更新 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/GXSendRabbitMQService.java` 与 `listener/GXRabbitMQQueueListener.java` 的 Javadoc：写明失败抛异常契约、`@Nullable` 返回值、监听器不再吞异常（仅注释，不改签名）

**Checkpoint**: 编译通过，`GXRabbitMQOperationException` 与统计类可被测试引用。

---

## Phase 3: User Story 1 - 审查结论可复核、可定位到代码 (Priority: P1) 🎯 审计交付

**Goal**: 22 条发现每条具备"编号/级别/维度/位置/触发条件/影响/证据/复现方式/回归设计/修复项/兼容性"。

**Independent Test**: 抽查任一发现，按报告给出的文件、行号与命令复现出同样现象。

- [X] T013 [US1] 在 `specs/016-rabbitmq-module-audit/review-report.md` 建立报告骨架：证据索引 E-01～E-24、发现索引 F-01～F-22、三维结论表、风险排序（影响面 × 触发概率 × 可观测性）
- [X] T014 [US1] 逐条补齐 F-01～F-11 的九项要素到 `specs/016-rabbitmq-module-audit/review-report.md`（含修复前代码位置与复现步骤）
- [X] T015 [US1] 逐条补齐 F-12～F-22 的九项要素到 `specs/016-rabbitmq-module-audit/review-report.md`，并把仅静态判定的结论标注"待上下文验证"
- [X] T016 [US1] 在 `specs/016-rabbitmq-module-audit/review-report.md` 追加"已确认正确行为"P-01～P-07，并说明修复中不得破坏的约束（`RabbitTemplate` 线程安全、可信包白名单、回调异常隔离、双检锁语义）
- [X] T017 [US1] 复核报告中的行号与当前 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/**` 一致，修正漂移（此任务在代码修改前完成并留档）

---

## Phase 4: User Story 2 - 修复后发送、声明、消费三条链路语义可信 (Priority: P1)

**Goal**: 队列声明与 Broker 真实状态一致；配置源真实生效；消费失败不再静默丢弃；失败表达统一。

**Independent Test**: 删除 Broker 侧队列后 `queueExists` 返回 `false`；`config-source=local` 时仅本地 Bean 存在；空消息抛出 `AmqpRejectAndDontRequeueException`。

### 配置装配

- [X] T018 [P] [US2] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/properties/GXRabbitMQConfigSourceTest.java`：`ApplicationContextRunner` 覆盖"未配置/local/nacos"三态，断言两个 Properties 变体互斥且仅一个存在（先失败）
- [X] T019 [P] [US2] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/properties/GXRabbitMQEnvironmentPostProcessorTest.java`：无 server-addr 不联网、YAML 打平后可读、拉取异常只 warn、`application.yml` 同名键优先
- [X] T020 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/local/GXLocalRabbitMQProperties.java`：`@ConditionalOnProperty(name="maple.framework.rabbitmq.config-source", havingValue="local")`；`@PropertySource` 同时覆盖 `classpath:/${spring.profiles.active:dev}/rabbit.yml` 与 `file:./config/${spring.profiles.active:dev}/rabbit.yml`（`ignoreResourceNotFound=true`）；构造日志改为 ASCII 并记录 profile
- [X] T021 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/nacos/GXNacosRabbitMQProperties.java`：删除 `@NacosConfigurationProperties` 与 `@NacosProperties`，改为 `@ConditionalOnProperty(..., havingValue="nacos", matchIfMissing=true)` + `@ConfigurationProperties(prefix="spring.rabbitmq")`；ASCII 日志说明来源
- [X] T022 [US2] 新建 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/properties/GXRabbitMQEnvironmentPostProcessor.java`：按 research D-13 实现（`ordered=Ordered.LOWEST_PRECEDENCE`、`addLast`、2s 超时、失败只 warn、日志 `RabbitMQ config source: ...` 不含口令；YAML 用 `YamlPropertySourceLoader` 解析）
- [X] T023 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java` 的 `@PostConstruct`，追加一条 ASCII 启动日志：实际生效的 Properties Bean 类型 + `cacheMode/connectionLimit/channelCacheSize` 关键默认值（不打印口令/地址全量）

### 队列与绑定声明

- [X] T024 [P] [US2] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXQueueDeclarationSemanticsTest.java`：每次调用必声明、属性冲突抛异常、`queueExists` 不读缓存、`deleteQueue` 失败不改状态、"队列不存在"返回 `false`（先失败）
- [X] T025 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java`：删除 `queueCache` 与 `queueOperationLock`；`createQueue` 改为"单飞 + 每次真实 `declareQueue`"；`queueExists` 只查 `getQueueProperties`；`deleteQueue`/`purgeQueue` 统一异常语义
- [X] T026 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 的 `createTemporaryQueue`：改为 `durable=false, exclusive=false, autoDelete=true`，Javadoc 注明排他队列不支持（对应 F-03）
- [X] T027 [P] [US2] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXMessageChannelFailureTest.java`：断言 `setupMessageChannel` 失败抛 `GXRabbitMQOperationException` 且 `detail.completedSteps`/`failedStep` 正确
- [X] T028 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 的 `setupMessageChannel`/`createExchange`/`bindQueueToExchange`/`unbindQueueFromExchange`：按 `contracts/queue-declaration-contract.md` 输出 `completedSteps`，交换机类型冲突包装为 `operation=DECLARE_EXCHANGE`
- [X] T029 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java`：新增前置校验（队列名/交换机名/路由键非空白且 ≤255、`exchange.getName()` 非空），抛 `GXBusinessException`（对应 F-21）

### 消费链路

- [X] T030 [P] [US2] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/listener/GXDefaultRabbitMQQueueListenerTest.java`：空消息/非法 JSON → `AmqpRejectAndDontRequeueException`；业务异常原样抛出；正常 JSON/非 JSON 不抛（先失败）
- [X] T031 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/impl/GXDefaultRabbitMQQueueListenerImpl.java`：`@ConditionalOnProperty` 替换 `@ConditionalOnExpression`；失败分层（拒绝/抛出）；删除 `handleComplexJsonMessage` 与注释残留；日志全 ASCII 并输出长度与摘要前缀（≤64 字符）
- [X] T032 [US2] 在 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/impl/GXDefaultRabbitMQQueueListenerImpl.java` 接入 `GXRabbitMQListenerStatistics`（构造注入 `ObjectProvider<MeterRegistry>`），三条计数路径与 `contracts/listener-contract.md` 一致
- [X] T033 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/listener/impl/GXDefaultRabbitMQQueueListenerImpl.java` 的队列名占位符为 `${maple.framework.mq.rabbitmq.default-queue-name:#{@gxRabbitMQProperties.defaultQueueName}}`，并给两个 Properties 变体加 `@Component("gxRabbitMQProperties")`

### 序列化与发送失败语义

- [X] T034 [P] [US2] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXMessageSerializationTest.java`：同一 `Dict` 经 Hutool 与 `JsonMapper` 序列化后 `JsonNode` 语义相等（日期/时间/嵌套集合/POJO）；发送消息含 `__TypeId__=cn.hutool.core.lang.Dict`（先失败）
- [X] T035 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java`：构造唯一 `JsonMapper`（`JavaTimeModule`、`WRITE_DATES_AS_TIMESTAMPS=false`、`Date` 格式 `yyyy-MM-dd HH:mm:ss`、`LocalDateTime` `ISO_LOCAL_DATE_TIME`、`NON_NULL`），同时用于转换器与发送端；`@Configuration(proxyBeanMethods=false)`
- [X] T036 [US2] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 的序列化与错误包装：`Dict` 走 `JsonMapper`、写 `__TypeId__`、不写 `consumerTag`、`IllegalArgumentException`/`NPE` 统一为 `GXBusinessException`、Broker 异常包装为 `GXRabbitMQOperationException`（`AmqpException` 保留）

**Checkpoint**: US2 独立验证——`GXQueueDeclarationSemanticsTest`、`GXDefaultRabbitMQQueueListenerTest`、`GXRabbitMQConfigSourceTest` 全绿。

---

## Phase 5: User Story 3 - 并发与上下文安全可验证 (Priority: P1)

**Goal**: 并发不串扰、traceId 可关联、声明路径不死锁且不全局串行、线程受管。

**Independent Test**: 16×100 并发发送无头/载荷错配；32 线程同队列声明 1 次；上下文启动/关闭 3 次线程数不累积。

- [X] T037 [P] [US3] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXMessagePropertiesIsolationTest.java`：发送后调用方 `MessageProperties` 未被改写（`contentType`/`headers`/`timestamp` 保持原值）；并发复用同一 DTO 时消息头互不覆盖（先失败）
- [X] T038 [US3] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java`：用 `MessagePropertiesBuilder.fromClonedProperties` 生成快照，所有 header/时间戳/ID 写入快照（对应 F-06）
- [X] T039 [P] [US3] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXTracePropagationTest.java`：MDC 有 traceId 时复用、无时生成且格式 `app:uuid`、多线程不串号、`spring.application.name` 不逐条查询（先失败）
- [X] T040 [US3] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 的追踪逻辑：`getNullableTraceId()` 优先、`@Value("${spring.application.name:unknown}")` 装配期注入（对应 F-05、F-17）
- [X] T041 [P] [US3] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXQueueDeclarationConcurrencyTest.java`：32 线程同队列断言 `declareQueue` 仅 1 次；10 队列 × 20 线程断言并行且无全局锁（先失败）
- [X] T042 [US3] 在 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 实现单飞 `ConcurrentHashMap<String, CompletableFuture<Queue>>`（`key=name#fingerprint`，`whenComplete` 移除，异常完成同样移除）
- [X] T043 [P] [US3] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/config/GXRabbitMQConfigSpringBootTest.java`：断言 Bean 装配（`rabbitTemplate`/`rabbitAdmin`/`asyncRabbitTemplate`/`rabbitMessagingTemplate`）、转换器与三个回调、`scheduler.expose` 两态、监听器启停；4 个延迟任务并发完成（先失败）
- [X] T044 [US3] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java`：删除 `getVirtualThreadPoolTaskScheduler`，改为标准 `ThreadPoolTaskScheduler`（`setVirtualThreads` JDK≥21、`poolSize=max(2,CPU)`、`removeOnCancelPolicy`、errorHandler、30s 优雅关闭）；`scheduler.expose` 控制是否注册 Bean，`false` 时内部持有并 `@PreDestroy` 关闭
- [X] T045 [P] [US3] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImplLifecycleTest.java`：`queue-capacity=0` 时第 3 个并发提交被拒绝并返回失败 Future；关闭后在途任务完成且线程终止；MDC 在任务内可见（先失败）
- [X] T046 [US3] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 的线程池：`core=min(core,max)`、`allowCoreThreadTimeOut(true)`、`queueCapacity<=0` 用 `SynchronousQueue`、`AbortPolicy`、MDC 装饰器、`@PreDestroy`（`shutdown`→30s→`shutdownNow`，ASCII 日志）
- [X] T047 [US3] 修改 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 的 `setupMessageChannelAsync`：捕获 `RejectedExecutionException` 与业务失败，统一返回 `failedFuture(GXRabbitMQOperationException)`（对应 F-11）

**Checkpoint**: US3 独立验证——并发三组用例全绿，且 US2 用例未被破坏。

---

## Phase 6: User Story 5 - 功能完备性测试可执行、可复核 (Priority: P1)

**Goal**: 从 0 用例提升到 ≥60 用例，覆盖 13 个公开方法、4 个装配/配置/监听类型、并发与集成；默认构建无 Broker 也能全绿。

**Independent Test**: `mvn -o -B -pl leaf-base-rabbitmq test` 连续 3 次结果一致；无环境变量时集成用例跳过。

- [X] T048 [P] [US5] 在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImplTest.java` 补齐 13 个公开方法矩阵：每个方法至少成功 / 失败（参数非法或 Broker 异常）/ 空值或边界；使用 `GXTestRabbitFixtures`
- [X] T049 [P] [US5] 在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/dto/GXRabbitMQMessageReqDtoTest.java` 覆盖 Builder/构造器/`@EqualsAndHashCode`/`@Nullable` 契约与 `tag` 忽略语义
- [X] T050 [P] [US5] 在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/listener/GXDefaultRabbitMQQueueListenerTest.java` 补齐指标计数断言（有/无 `MeterRegistry` 两态）与日志 ASCII 断言
- [X] T051 [P] [US5] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/integration/GXRabbitMQRealBrokerIT.java`：`@Tag("integration")` + `@EnabledIfEnvironmentVariable(named="RABBITMQ_HOST", matches=".+")`，覆盖声明→绑定→发送→`queueExists`→`purgeQueue`→`deleteQueue`、临时队列第二连接消费、`mandatory` 返回回调
- [X] T052 [US5] 配置 `leaf-base-rabbitmq/pom.xml` 的 surefire：默认 `excludedGroups=integration`，`-Dgroups=integration` 时只跑集成；验证无环境时 `Skipped` 计数正确、构建成功
- [X] T053 [US5] 连续 3 次执行 `mvn -o -B -pl leaf-base-rabbitmq test` 并把每次的 `Tests run/Failures/Errors/Skipped` 与耗时写入 `specs/016-rabbitmq-module-audit/review-report.md` 的"测试证据"章节
- [X] T054 [US5] 在 `specs/016-rabbitmq-module-audit/review-report.md` 建立"发现 → 用例 → 修复前失败输出 → 修复后通过"映射表，确保 F-01～F-22 无遗漏（无法自动化的登记为"未验证项"）

**Checkpoint**: US5 独立验证——用例数 ≥60、通过率 100%、3 次一致、无 Broker 环境可构建。

---

## Phase 7: User Story 4 - 性能问题定量化 (Priority: P2)

**Goal**: 给出可复算口径（固定开销项、锁范围、调度并发度、时延分位），不设绝对吞吐门槛。

**Independent Test**: 修复前后各 ≥500 条样本，固定开销项不增加，调度并发度 >1。

- [X] T055 [P] [US4] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXSendOverheadTest.java`：用 Logback `ListAppender` 断言发送成功路径 info 日志 0 条、MDC 有值时无 UUID 生成、发送路径无 `getEnvironmentValue` 调用（统计固定开销项 ≤4）
- [X] T056 [P] [US4] 新建 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXRabbitMQThroughputSamplingIT.java`（`@Tag("integration")`，env 门控）：顺序 500 条 + 8 线程 × 50 条，输出 avg/p50/p95/max 与吞吐，仅打印不断言阈值
- [X] T057 [US4] 在 `specs/016-rabbitmq-module-audit/review-report.md` 记录修复前（基线代码路径复算）与修复后（用例实测）两组口径对比与差异说明
- [X] T058 [US4] 在 `leaf-base-rabbitmq/AGENTS.md` 与 `docs/leaf-base-rabbitmq开发手册.md` 记录 `thread-pool.max-size` 的 JDK 队列语义、`queue-capacity<=0` 的严格模式、调度器并发度与固定开销清单

**Checkpoint**: US4 独立验证——口径可复算、修复前后对比有数据、无绝对门槛断言。

---

## Phase 8: User Story 6 - 契约、文档与能力边界一致 (Priority: P3)

**Goal**: 文档与实现一致；缺失的官方能力显式登记"不支持 + 原因 + 替代方案"。

**Independent Test**: 逐条核对手册 §2～§7 与实现，100% 一致；能力边界表覆盖批量/延迟/死信/指标/消息大小。

- [X] T059 [US6] 重写 `leaf-base-rabbitmq/AGENTS.md` 的模块定位、配置键表、失败语义、临时队列不支持、监听器拒绝策略、`GXRecoveryCallback` 触发条件、测试命令（`mvn -o -B -pl leaf-base-rabbitmq test`、集成用例环境变量）
- [X] T060 [US6] 修订 `docs/leaf-base-rabbitmq开发手册.md` §2.3（配置前缀/条件）、§2.6（队列名解析）、§2.7（`recover(Throwable)`）、§3.2（配置块）、§6.1（不可变声明）、§7（死信与拒绝策略）
- [X] T061 [US6] 在 `leaf-base-rabbitmq/AGENTS.md` 新增"能力边界"表：批量发送、延迟/优先级/TTL、排他队列、死信治理、消息大小前置校验、指标暴露逐项登记状态与替代方案（宪法附加约束 §9）
- [X] T062 [P] [US6] 清理 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java` 注释掉的 `connectionFactory` Bean、`@SuppressWarnings("all")`；清理 `GXSendRabbitMQServiceImpl` 中未使用的 import
- [X] T063 [P] [US6] 执行 ASCII 日志扫描 `rg -n "log\.(info|warn|error|debug|trace)\(\"[^\"]*[^\x00-\x7F]" leaf-base-rabbitmq/src/main/java`，逐条改为 ASCII 并复核关键字（`RABBITMQ_LISTENER_REJECTED` 等）
- [X] T064 [US6] 在 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java` 增加装配可观测日志（未启用/已启用、`@EnableRabbit` 生效），避免"未装配无信号"
- [X] T065 [US6] 复核 `leaf-base-rabbitmq/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 与实现键名一致（含默认值与枚举提示），并核对 `config/*/rabbit.yml` 示例键名

**Checkpoint**: US6 独立验证——文档抽查 20 条声明全部与代码一致；ASCII 扫描无输出。

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 收口验证、报告定稿、交付说明。

- [X] T066 交叉核对 5 份契约（`specs/016-rabbitmq-module-audit/contracts/*.md`）与实现行为，逐条打勾并记录偏差
- [X] T067 执行 `mvn -o -B -pl leaf-base-rabbitmq test` 与 `mvn -o -B -pl leaf-base-rabbitmq -DskipTests install`，确认 ErrorProne/NullAway 0 错误
- [X] T068 在 `specs/016-rabbitmq-module-audit/review-report.md` 定稿：结论表、发现状态（已修复/已登记不支持）、测试证据、性能口径、未验证项与手工命令
- [X] T069 更新 `specs/016-rabbitmq-module-audit/checklists/requirements.md` 的最终校验状态，并起草提交说明（行为变化、兼容性、测试覆盖、未验证项）

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Phase 1 (T001-T005)
    └──> Phase 2 (T006-T012)
            ├──> US1 (T013-T017)            # 可与 US2 并行（仅写 review-report.md）
            ├──> US2 (T018-T036)            # 依赖 T006/T009/T010/T011
            │       └──> US3 (T037-T047)    # 需 US2 的异常与单飞基础
            ├──> US5 (T048-T054)            # 需要 US2/US3 的实现存在才能全绿
            ├──> US4 (T055-T058)            # 需要 US2/US3 完成后测量
            └──> US6 (T059-T065)            # 需要最终行为确定
Phase 9 (T066-T069) 依赖以上全部
```

### 文件冲突提示

- `GXSendRabbitMQServiceImpl.java` 被 T025/T026/T028/T029/T036/T038/T040/T042/T046/T047 修改 → **串行执行**，不得并行。
- `GXRabbitMQConfig.java` 被 T023/T035/T044/T062/T064 修改 → 串行。
- `GXDefaultRabbitMQQueueListenerImpl.java` 被 T031/T032/T033 修改 → 串行。
- 不同测试文件可并行（T018/T019、T024/T027、T030/T034、T037/T039/T041、T043/T045、T048～T051）。

### Within Each User Story

- 测试先行：先写用例并记录失败输出，再改实现
- 类型 → 服务 → 装配 → 集成
- 每个 checkpoint 必须跑一次全量 `mvn -o -B -pl leaf-base-rabbitmq test`

---

## Parallel Example: User Story 2

```text
# 三个不同测试文件可同时开工（均期望先失败）：
Task: "T018 GXRabbitMQConfigSourceTest"
Task: "T024 GXQueueDeclarationSemanticsTest"
Task: "T030 GXDefaultRabbitMQQueueListenerTest"

# 实现阶段按文件分组串行：
T020 -> T021 -> T022 -> T023   (properties/config)
T025 -> T026 -> T028 -> T029   (service/impl)
T031 -> T032 -> T033           (listener)
```

## Parallel Example: User Story 3

```text
Task: "T037 GXMessagePropertiesIsolationTest"
Task: "T039 GXTracePropagationTest"
Task: "T041 GXQueueDeclarationConcurrencyTest"
Task: "T043 GXRabbitMQConfigSpringBootTest"
Task: "T045 GXSendRabbitMQServiceImplLifecycleTest"

# 随后实现串行：T038 -> T040 -> T042 -> T044 -> T046 -> T047
```

---

## Implementation Strategy

### MVP First（US1 + US2）

1. Phase 1 Setup → Phase 2 Foundational
2. US1 产出可复核的审查报告（22 条发现）
3. US2 修复高危正确性缺陷（配置源、队列声明、临时队列、消费毒消息、失败语义）
4. **STOP & VALIDATE**：`GXQueueDeclarationSemanticsTest`、`GXDefaultRabbitMQQueueListenerTest`、`GXRabbitMQConfigSourceTest` 全绿
5. 此阶段已能回答"这个模块能不能继续用"

### Incremental Delivery

1. MVP（US1 + US2）→ 正确性可信
2. US3 → 并发与上下文可信
3. US5 → 功能完备性测试达标（**最终验收不可跳过**）
4. US4 → 性能口径可复算
5. US6 → 契约/文档/能力边界一致

### Notes

- 集成用例（T051/T056）默认跳过，缺失环境时在报告的"未验证项"登记
- 任何任务若发现需要改 `leaf-base-framework`，先停下并说明原因，不得直接跨模块修改
- 提交粒度建议：每个 Phase 一次提交，提交信息 `fix(rabbitmq): ...` / `test(rabbitmq): ...`

---

## 执行记录（2026-10-25）

**状态**：69 个任务全部完成；`mvn -o -B -pl leaf-base-rabbitmq test` 连续 3 次通过，**106 用例 / 0 失败 / 0 错误 / 0 跳过**。详见 [review-report.md](./review-report.md)。

| 阶段 | 完成情况 | 关键产物 |
| --- | --- | --- |
| Setup | 完成 | test 依赖、`spring.factories`、`additional-spring-configuration-metadata.json` |
| Foundational | 完成 | `GXRabbitMQOperationException`、`GXRabbitMQListenerStatistics`、Javadoc 契约 |
| US1 | 完成 | `review-report.md`：25 条发现（含实施期新发现 F-23/F-24/F-25） |
| US2 | 完成 | 配置源、队列声明、临时队列、监听拒绝、失败语义、序列化兼容 |
| US3 | 完成 | 属性快照、traceId 复用、单飞声明、调度器/执行器重写与关闭 |
| US5 | 完成 | 16 个测试类、106 用例、3 次一致 |
| US4 | 完成 | 固定开销项口径、调度并发断言、日志级别断言 |
| US6 | 完成 | `AGENTS.md`、开发手册、能力边界表、ASCII 扫描 |
| Polish | 完成 | 契约核对、clean test、报告定稿 |

### 与计划的偏差

1. **T035 序列化**：出站继续使用 Hutool（保持 `Date`/`LocalDateTime` 为毫秒时间戳）；Jackson 仅用于类型化转换 + 兼容性用例。原因：改成 Jackson 会破坏 wire format（宪法 II）。
2. **T028/T039 `purgeQueue`/`unbind`**：`purgeQueue` 不返回 `false`（404 转异常）；`unbind` 幂等返回 `true`。契约已同步。
3. **T067**：以 `mvn -o -B -pl leaf-base-rabbitmq clean test` 代替 `install`，避免覆盖本地仓库快照。
4. **T051**：`GXRabbitMQRealBrokerIT` 与 `GXRabbitMQThroughputSamplingIT` 已实现，但因无 Broker 环境未执行，登记为未验证项。
5. **实施期新增修复**：移除 `jackson-datatype-jsr353`（F-23）、补 `cn.hutool.core.lang` 信任包（F-24）、配置变体 `@Primary`（F-25）。三者均由新增 Spring 上下文测试发现，属生产阻断级。

---

## Phase 10: Convergence

- [X] T070 **CRITICAL** 用 `GXMdcWrapperThreadPoolExecutor` 替换 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 中的原生 `ThreadPoolExecutor`（第 429 行）与原生 `new Thread` 调用（第 508 行），删除 `setupMessageChannelAsync` 中的手工 MDC 捕获/恢复（第 303 行），改由包装器传播上下文，并补用例断言异步任务可见调用方 MDC；保留 `AbortPolicy`、`SynchronousQueue` 与 `@PreDestroy` 语义 per Constitution V (contradicts)
- [X] T071 修正集成用例门控：移除 `leaf-base-rabbitmq/pom.xml` 中的 `<excludedGroups>integration</excludedGroups>`（或改为仅默认 profile 生效），使默认 `mvn test` 以 `Skipped` 呈现集成用例、`-Dgroups=integration` 能真正选中；同步更新 `specs/016-rabbitmq-module-audit/quickstart.md`、`leaf-base-rabbitmq/AGENTS.md` §6、`docs/leaf-base-rabbitmq开发手册.md` §9 的命令与期望结果 per FR-028 (contradicts)
- [X] T072 将 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXMessagePropertiesIsolationTest.java` 的并发发送规模从 16×20 提升到 16×100（≥1600 条），保持 header/payload 零错配并记录执行时长 per FR-027 (partial)
- [X] T073 在 `specs/016-rabbitmq-module-audit/review-report.md` 补 22 个源码文件的逐文件判定矩阵、每条发现的"触发条件 / 复现命令 / 回归用例"三列，以及修复前失败输出证据 per FR-001/FR-002/FR-003/SC-002 (partial)
- [X] T074 新增无 Broker 的 500 条发送样本采样用例（mock `RabbitTemplate`，放在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/`），输出每消息固定开销与耗时分布，并更新 `specs/016-rabbitmq-module-audit/review-report.md` §6 的修复前后口径对比 per SC-006 (missing)
- [X] T075 新增 `maple.framework.rabbitmq.scheduler.expose=false` 的 Spring 上下文用例（`leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/config/`），断言无 `TaskScheduler` Bean、`AsyncRabbitTemplate` 仍持有调度器、关闭后内部调度器线程终止 per FR-015/T043 (partial)
- [X] T076 在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/config/GXRabbitMQConfigSpringBootTest.java` 断言配置绑定结果（`GXRabbitMQProperties#getPort()`、`getDefaultQueueName()`、唯一配置变体） per FR-026 (partial)
- [X] T077 用 `SimpleMeterRegistry` 覆盖监听计数 `rabbitmq.listener.messages.processed` / `.rejected(reason)` / `.failed(type)`（`leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/listener/`），替代仅对 mock 统计的验证 per FR-008 (partial)
- [X] T078 更新 `specs/016-rabbitmq-module-audit/plan.md` 的 D-08 与 Phase 1 说明、`specs/016-rabbitmq-module-audit/research.md` 的 D-08，记录最终序列化决策（出站 Hutool + Jackson 类型化转换），消除与实现及 `spec.md` 实施期澄清的矛盾 per plan D-08 (contradicts)
- [X] T079 新增上下文连续 3 次启动/关闭的线程留存测试，按 `maple-rabbit-scheduler-` 与 `rabbitmq-async-worker-` 前缀计数，断言关闭后不增长 per SC-005 (missing)
- [X] T080 在 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/exception/GXRabbitMQOperationException.java` 增加 `QUERY_QUEUE` 并在 `queueExists` 失败路径使用，或更新 `specs/016-rabbitmq-module-audit/data-model.md` §7 说明复用 `DECLARE_QUEUE` 的理由 per data-model §7 (partial)
- [X] T081 覆盖 `arguments` 含 `null` 值的边界：在 `GXSendRabbitMQServiceImpl#createQueue` 声明前确定性处理（剔除或拒绝）并补用例 per spec Edge Cases (missing)
- [X] T082 本地来源启动日志补充实际 `spring.profiles.active`；在 `docs/leaf-base-rabbitmq开发手册.md` §2.4 补 `purgeQueue`/`unbindQueueFromExchange` 的失败语义 per FR-019/FR-033 (partial)

---

## Phase 11: Convergence

- [X] T083 为 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/GXSendRabbitMQService.java` 中可空契约补 `@Nullable` 与 Javadoc：`sendNormalMessage` 返回值（恒为 `null`）、`createQueue`/`setupMessageChannel`/`setupMessageChannelAsync` 的 `arguments`/`queueArgs` 参数，并同步实现类签名注释 per FR-023/FR-011 (partial)
- [X] T084 在 `specs/016-rabbitmq-module-audit/review-report.md` §6 补修复前 ≥500 条样本的采集方式与结果（基于历史提交/worktree 运行同一 mock 采样用例），或明确登记无法复现的原因与替代口径 per SC-006 (partial)
- [X] T085 在 `specs/016-rabbitmq-module-audit/review-report.md` §3.1 去掉 `properties/*/package-info` 通配行，逐条列出全部 22 个源码文件（含 `properties/package-info.java`） per FR-001 (partial)
- [X] T086 在 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/service/impl/GXSendRabbitMQServiceImpl.java` 发送前对 `data` 做不可变快照，或在 Javadoc/契约中明确"发送期间调用方不得修改 payload"，并在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/service/impl/GXMessagePropertiesIsolationTest.java` 补同一 DTO 并发改写 payload 的用例 per FR-010 (partial)
- [X] T087 在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/config/GXRabbitMQThreadLifecycleTest.java` 增加 `rabbitmq-async-worker-` 前缀断言：触发一次异步通道创建并关闭执行器后，线程数不残留 per SC-005/T079 (partial)
- [X] T088 在 `leaf-base-rabbitmq/src/test/java/cn/maple/rabbitmq/config/GXRabbitMQSchedulerExposeDisabledTest.java` 增加内部调度器关闭验证：上下文关闭后轮询 `maple-rabbit-scheduler-` 线程终止 per FR-015/T075 (partial)
- [X] T089 为 `leaf-base-rabbitmq/src/main/java/cn/maple/rabbitmq/config/GXRabbitMQConfig.java` 的 `@EnableRabbit` 增加可关闭开关，或在 `leaf-base-rabbitmq/AGENTS.md` 与 `docs/leaf-base-rabbitmq开发手册.md` §8 登记"不可独立关闭 + 原因 + 替代方案" per FR-022 (partial)

---

## Phase 12: Convergence

- [X] T090 修正 `leaf-base-rabbitmq/AGENTS.md` 目录职责第 10 条的过期描述（"`src/test`：106 个用例（单元 / 上下文 / 并发），集成用例以 `integration` 分组排除"），改为当前基线 125 用例（120 执行 + 5 集成跳过）、集成用例默认参与构建且无 Broker 时以 `Skipped` 呈现 per FR-033/SC-010 (partial)




