# Phase 0 Research: leaf-base-data-sync 审查修复与消息中间件可切换

**Feature**: `specs/017-data-sync-mq-switchable` | **Date**: 2026-10-27

所有决策均以仓库实测证据为前提（`leaf-base-data-sync` 23 个源文件、`leaf-base-rabbitmq` / `leaf-base-rocketmq` 的 pom 与关键类、根 POM、宪法 v1.3.0）。未决澄清项为 0；三项需运行期确认的技术事实以"验证任务 + 备选方案"形式收敛（D-04、D-08、D-12）。

---

## D-01 MQ 类型选择键与互斥装配

- **Decision**: 新增 `canal.mq.type`，取值 `rabbitmq` | `rocketmq`，默认 `rabbitmq`（`matchIfMissing = true`）。RabbitMQ 侧 Bean 与监听器以 `@ConditionalOnProperty(name="canal.mq.type", havingValue="rabbitmq", matchIfMissing=true)` 装配；RocketMQ 侧以 `havingValue="rocketmq"` + `@ConditionalOnClass` 装配。非法取值由 `@ConfigurationProperties` 的枚举绑定失败在启动期报出（`BindException` 信息中包含合法取值）。
- **Rationale**: 默认值保证既有部署零配置升级行为不变（宪法 §II）；用同一键控制"哪一套消费侧生效"，天然互斥，避免两套基础设施同时声明；枚举绑定比字符串比较更早失败、更易诊断。
- **Alternatives considered**:
  - `maple.framework.mq.type`：与宪法 §IV"单模块内部开关沿用模块既有前缀"冲突（本模块既有前缀为 `canal`），且 `maple.framework.enable.*` 语义是"跨模块能力开关"，不适用于"二选一"。
  - 布尔双开关（`canal.rabbitmq.enabled` + `canal.rocketmq.enabled`）：允许同时为 true 或同时为 false，需要额外校验，且违反"互斥单选"决策。

## D-02 配置绑定入口与双配置源

- **Decision**: 以 Spring Boot 标准 `@ConfigurationProperties(prefix="canal")` 作为 `canal.*` 的**唯一**绑定入口；配置**来源**由显式开关 `canal.config-source=local|nacos` 控制，`matchIfMissing` 保持"类路径探测"这一既有行为等价物；启动时 INFO 打印"实际生效来源 + 关键默认值（不含密钥）"。`@NacosConfigurationProperties` 仅在处理器类真实存在（`@ConditionalOnClass` 指向 `com.alibaba.nacos.spring.context.annotation.config.NacosConfigurationProperties` 之类处理器所属类型）时作为附加路径保留，否则不参与绑定。
- **Rationale**: 实测 `leaf-base-rabbitmq`（compile 依赖）已传递带入 `leaf-base-nacos`，因此 `@ConditionalOnMissingClass("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties")` **当前恒为 false**——本地变体从未装配；而 Nacos 变体只有注解、依赖树中无对应注解处理器，`canal.*` 实际从未绑定，全部走常量默认值（E-11、E-12 的直接后果）。以标准绑定入口替换"注解处理器依赖"是唯一能让 `config/<profile>/canal.yml`（外置、由下游通过 Spring 配置导入加载）真正生效的方式，同时兼容 Spring Cloud Alibaba 的 `spring.config.import=nacos:` 路径。
- **Alternatives considered**:
  - 保留 `@PropertySource("classpath:/${spring.profiles.active}/canal.yml")`：该路径在仓库中不存在（`canal.yml` 位于外置 `config/`），`ignoreResourceNotFound=false` 还会让缺失直接启动失败。
  - 统一改成 Nacos 单源：破坏无 Nacos 环境的可用性（宪法 §I"缺少其它基础设施时可用"）。
  - 直接把默认值改成 `local`：会改变已发布行为（部分下游确实在使用 Nacos），违反 §IV 护栏①。

## D-03 处理器路由：启动期不可变快照 + 有界缓存（含负结果）

- **Decision**: `GXCanalHandlerResolver` 在 `SmartInitializingSingleton`/`@PostConstruct` 阶段取 `ApplicationContext.getBeansOfType(GXProcessCanalDataService.class)`，构建 `Map<String, GXProcessCanalDataService>`（Bean 名 → 实现）不可变快照；热路径按 `db_table_Service` → 小驼峰名查快照，未命中即走默认兜底，并把结果写入 `ConcurrentHashMap` 缓存（含 `NO_HANDLER` 负结果），缓存条目超过 `canal.handler-cache-size`（默认 4096）时整体清空（简单有界策略，避免引入 LRU 依赖）。
- **Rationale**: 消除 E-05 的 `getBean` 与异常控制流（SC-004 前提）；Bean 集合在运行期不变（Nacos 刷新配置不会新增 Bean），启动期快照与"运行期原子替换"（FR-013）等价且更简单；负缓存避免"大多数表没有专用处理器"时的重复转换与查找。
- **Alternatives considered**:
  - 直接把 `getBeansOfType` 结果按 `database/table` 建表：Bean 名不含可靠的库表信息，无法反向解析。
  - 用 Caffeine 做 LRU：新增第三方依赖（宪法附加约束 §3），收益不抵成本。
  - 运行期监听 `ContextRefreshedEvent` 重建：无实际需求（Bean 集合不变），属投机设计（附加约束 §8）。

## D-04 载荷形态：单对象与数组，单次解析

- **Decision**: 用 `JSONUtil.parse(message)` 解析一次得到 `JSON`，再按 `instanceof JSONObject` / `JSONArray` 分支：对象走单事件，数组逐元素处理并**逐元素判定结局**；元素级失败不影响同批其他元素。删除 `JSONUtil.isTypeJSON` 前置校验（其失败分支由同一处 `JSONException` 捕获替代）。布尔值 `isDdl=true` 或 `type` ∈ {`CREATE`,`ALTER`,`ERASE`,`DROP`,`TRUNCATE`} 一律判定为"跳过"。
- **Rationale**: 消除 E-07 的双入口解析；`JSONUtil.toBean` 对 `application/json` 的对象/数组都能落到目标类型，但当前实现只声明了对象目标，数组载荷会静默失败（E-08）。数组化处理同时覆盖 Canal `flatMessage` 批量投递与未来批量 sink。
- **Verification task（实施第一步）**: 抓取真实 Canal sink 报文样本（对象 / 数组各一），补一条"数组载荷处理 N 个事件且逐条计数"的回归用例；若生产只投递对象，数组分支仍保留（成本仅为一次 `instanceof`）。
- **Alternatives considered**:
  - 只支持对象、遇到数组直接判失败：会在批量模式下 100% 丢消息。
  - 先用 `isTypeJSON` 再 `toBean`：保留双解析（E-07 的性能问题）。

## D-05 消费结局枚举与 `parseMessage` 契约共存

- **Decision**: 新增 `GXCanalOutcomeEnums`（`SUCCESS` / `SKIPPED` / `RETRYABLE_FAILURE` / `NON_RETRYABLE_FAILURE`）与 `GXCanalParseResultInnerDto`（`record`：结局 + 原因 + `@Nullable GXCanalDataDto` 事件 + 批内序号）。`GXCanalMessageParseService` 新增 `parse(String raw)` 返回结果记录；**保留** `Dict parseMessage(String)` 作为兼容包装（把结映射回既有 `status`/`message` 键，旧调用方与旧用例继续可用），监听器改走 `parse`。
- **Rationale**: `parseMessage` 是已发布公共方法（§II 不可改签名），而消费侧需要可判定结局；包装法以零破坏代价同时满足两者。字符串魔法值收敛为枚举（FR-037）。
- **Alternatives considered**:
  - 直接把 `parseMessage` 改为返回记录：破坏既有契约。
  - 用 `Dict` 携带枚举：仍需字符串键，且类型不安全。

## D-06 失败处置：共享分类 + MQ 原生动作（有界重投上限 3）

- **Decision**:
  - **RabbitMQ**：为 `@RabbitListener` 指定模块自有 `RabbitListenerErrorHandler`。可重试失败时读取消息头 `x-canal-retry-count`（缺省 0）：小于 `canal.retry.max-attempts`（默认 3）则用 `RabbitTemplate` 重新投递到同一交换机/路由键并自增该头，随后**正常返回**（原消息 ack）；达到上限或不可重试失败则抛 `AmqpRejectAndDontRequeueException`，由队列声明的 DLX 路由到死信队列。
  - **RocketMQ**：监听器抛出异常（可重试失败）→ 容器返回 `RECONSUME_LATER`，`maxReconsumeTimes` 设为同一配置值，超限后进入 broker `%DLQ%`；不可重试失败则经 `GXSendRocketMQService#sendOneway` 转发到 `canal.rocketmq.dead-letter-topic` 后正常返回（`CONSUME_SUCCESS`）。
  - 两类 MQ 的**分类规则**共用同一处实现（解析结果 + `isRetryable` 判定），传输动作分离。
- **Rationale**: RabbitMQ 的 reject/requeue 没有投递计数（`x-death` 只在死信时出现），要实现"上限 3 次"只能自行携带计数；RocketMQ 有原生 `maxReconsumeTimes`，硬造统一重投会浪费其能力并增加复杂度。两者最终都能保证"有界 + 不丢 + 可检索"（SC-003）。
- **Alternatives considered**:
  - 引入 `spring-retry` + `RetryInterceptor`：新增第三方依赖，且 `leaf-base-rabbitmq` 已明确不再引入 `spring-retry`（`016` FR 约束），会形成两套重试认知。
  - 全局 `spring.rabbitmq.listener.simple.default-requeue-rejected=false`：影响**全部**监听器（包括下游自建队列），属于跨边界副作用。
  - 仅依赖 broker 的死信（0 次重试）：与 SC-003/FR-006 的"默认 3 次"不符。

## D-07 死信与重投目的地声明

- **Decision**: RabbitMQ 侧由 `GXCanalConfig` 声明 `canal.dlx`（DirectExchange，durable，非 autoDelete）与 `canal.dlq`（durable），并把主队列通过 `QueueBuilder.deadLetterExchange(...)`/`deadLetterRoutingKey(...)` 绑定；名称由 `canal.dead-letter.exchange` / `canal.dead-letter.queue` 配置，默认值集中在 `GXCanalConstant`。全部 Bean 加 `@ConditionalOnMissingBean` 与 `canal.mq.type=rabbitmq` 条件。RocketMQ 侧不声明 DLQ（由 broker `%DLQ%<group>` 兜底），额外提供可选 `canal.rocketmq.dead-letter-topic` 用于"不可重试失败"的显式转发。
- **Rationale**: 死信必须可运维（可查看、可重放），且不得依赖下游手工声明 DLX；`@ConditionalOnMissingBean` 满足 FR-033 的可覆盖性。
- **Alternatives considered**:
  - 只在文档里要求下游自建 DLX：现状就是这样，等于没有（E-04）。
  - 用 TTL 队列做延迟重试：当前无延迟需求（Canal 变更无延迟语义要求），属过度实现。

## D-08 RocketMQ 消费侧容器形态（经 `leaf-base-rocketmq`）

- **Decision**: 通过 `leaf-base-rocketmq` 传递带入的 `rocketmq-spring-boot-starter` 声明**一个**消费 Bean：`GXCanalRocketMQListener implements RocketMQListener<String>`，标注 `@RocketMQMessageListener(consumerGroup = "${canal.rocketmq.consumer-group}", topic = "${canal.rocketmq.topic}", selectorExpression = "${canal.rocketmq.tag:*}", maxReconsumeTimes = ...)`，`onMessage` 内委派 `GXCanalMessageParseService#parse`，异常按 D-06 处理。该类整体 `@ConditionalOnProperty(canal.mq.type=rocketmq)` + `@ConditionalOnClass(RocketMQListener.class)`。
- **Rationale**: 满足 FR-043（不新建连接管理，复用 starter 托管容器）与 FR-030（依赖缺失时不加载该类）；`@RocketMQMessageListener` 的容器由 starter 负责注册，避免手写 `DefaultMQPushConsumer` 生命周期。
- **Verification task（实施第一步，与 F-18 对应）**: 该路径在本仓库**从未被使用**（全仓库 `@RocketMQMessageListener` 零命中），`rocketmq-spring-boot-starter 2.3.6` 面向 Boot 2/3，需在 Boot 4.1.1 基线下实测容器是否注册并消费。备选方案：显式声明 `DefaultRocketMQListenerContainer` Bean（仍为 starter 提供的容器类，不违反 FR-043），或退回 `@ConditionalOnClass` 守卫并显式抛"当前基线不支持 RocketMQ 消费"的启动错误（此时按 FR-038 登记）。
- **Alternatives considered**:
  - 直接用 `DefaultMQPushConsumer`：属于 FR-043 禁止的"自建消费者连接管理"。
  - 复用 `GXCdcMessageDispatcher`/`GXCdcEventListener`：要求业务改用另一套监听接口，破坏"业务零改动"（FR-029/FR-046）。

## D-09 依赖声明方式与传递依赖影响

- **Decision**: `leaf-base-rocketmq` 以 `<optional>true</optional>` 声明（版本 `${project.parent.version}`，不新增第三方位版本号）；代码侧以 `@ConditionalOnClass` 守卫；下游要启用 RocketMQ 必须显式引入同一 artifact。
- **Rationale**: 该模块的 `rocketmq-spring-boot-starter`、`leaf-base-nacos`、`fastjson` 1.x 均为**非 optional** 传递依赖（`leaf-base-rocketmq/pom.xml` 第 24–72 行）；若本模块 compile 依赖，仅用 RabbitMQ 的下游会被强制引入整套 RocketMQ + Nacos 栈（SC-011 直接失败），并进一步污染其配置源判定。
- **影响评估（宪法附加约束 §3，写入交付说明）**: `rocketmq-client`（版本由该模块控制）、`nacos-client`（已由 `leaf-base-rabbitmq` 带入，无净增）、`fastjson` 1.x（**净增**，与既有 Jackson/Hutool 功能重叠，仅该模块内部使用；需确认不进入本模块的序列化路径）、`reflections`/`protobuf`/`snappy`/`zstd` 等已由根 POM 冲突覆盖项兜住。
- **Alternatives considered**:
  - compile 依赖：破坏依赖隔离（FR-044/SC-011）。
  - `provided`：在下游运行期不传递，但语义上暗示"由容器提供"，与事实不符。
  - 新起 `leaf-base-data-sync-rocketmq` 模块：为一个可选分支新建模块，属过度设计（附加约束 §8）。

## D-10 指标实现方式

- **Decision**: 新增 `GXCanalConsumeStatistics`，使用 `LongAdder` 计数（成功 / 跳过 / 可重试失败 / 不可重试失败 / 处理器未命中 / 死信 / 批内元素数），构造器接收 `ObjectProvider<MeterRegistry>`，可用时注册 `Gauge`（前缀 `canal.consumer.`），不可用则静默跳过；同时输出周期性/关闭时的汇总日志（INFO，ASCII）。
- **Rationale**: 与 `leaf-base-rocketmq` 的 `GXCdcDispatchStatistics` 完全同构（既有模式，非新发明）；Micrometer 仅以可选 API 使用，不新增依赖，也不要求下游启用 actuator（FR-023 的"不得为指标引入重量级依赖"）。
- **Alternatives considered**:
  - 直接依赖 `spring-boot-starter-actuator`：给所有下游强加 actuator（非必要）。
  - 自研指标注册与导出：重复造轮子。

## D-11 TraceId 与 MDC 传播

- **Decision**: 消费者入口（两套 MQ 各一处）优先读取消息头/用户属性中的追踪标识（AMQP `headers["traceId"]`、RocketMQ `userProperties["traceId"]`），缺失则 `GXTraceIdContextUtils.generateTraceId()`；`setTraceId` 后在 `finally` 中 `removeTraceId()`。解析与兜底日志统一带 `traceId` 占位符。
- **Rationale**: 宪法 §V 要求异步/消费链路使用既有 MDC 能力；`removeTraceId` 精确清理，避免线程复用导致串扰（`clearTraceId` 会清空整个 MDC，可能抹掉下游其它上下文，故不采用）。
- **Alternatives considered**: 用 `GXMdcWrapper*` 包装线程池：本模块不持有线程池（容器线程由 AMQP/starter 管理），无包装对象；`clearTraceId`：副作用过大。

## D-12 `@Lazy` 与监听器端点注册（F-03 定案）

- **Decision**: 移除 `GXCanalRabbitMQListener` 上的 `@Lazy`，并新增一条 `@SpringBootTest`/`ApplicationContextRunner` 用例断言 `RabbitListenerEndpointRegistry#getListenerContainers()` 非空且目标容器 `isRunning()`。该改动须在交付说明中标注为**生产行为变更**（若此前确实未注册，则本次上线后开始真实消费，首次上线需确认下游处理器的幂等性）。
- **Rationale**: `@RabbitListener` 的端点由 `RabbitListenerAnnotationBeanPostProcessor` 在 Bean 实例化后的后置处理阶段收集；`@Lazy` 且无注入点的单例不会被预实例化 → 端点极可能从未注册（E-02）。`leaf-base-rabbitmq` 的 `GXDefaultRabbitMQQueueListenerImpl` 同样带 `@Lazy`，说明该问题可能跨模块存在（本规格只修本模块，另一处记录为后续跟进项，不跨模块改动）。
- **Alternatives considered**:
  - 保留 `@Lazy` 并在别处注入该 Bean 以强制实例化：隐式依赖，脆弱。
  - 改为 `@Bean` 方法声明监听器：与现有 `@Component` 风格不一致，且同样需要 `@Lazy` 之外的处理。

## D-13 测试策略（无 Testcontainers 前提下的可执行验证）

- **Decision**: 三层验证：①纯单元（解析、结局、缓存、计数、命名冲突、DDL/数组）；②`ApplicationContextRunner` 装配测试（MQ 互斥、条件装配、Bean 覆盖、配置源四组合、依赖缺失提示）；③`@SpringBootTest` 端到端（容器注册断言、失败处置链路、并发无串扰），真实 broker 场景由环境变量门控并 `assumeTrue` 跳过。RocketMQ 消费容器注册用"Dummy name-server + 断言 Bean/容器创建"，不要求真实连接。
- **Rationale**: 仓库无 Testcontainers（根 POM 未管理），引入属新增依赖；用 Spring 上下文级断言即可覆盖本次改动的风险面（装配与语义），真实连通性交给可跳过的集成用例。
- **Alternatives considered**: 引入 Testcontainers：新增较重依赖且 CI 需要 Docker，收益不抵成本（附加约束 §3）。

## D-15 实施期结论回填（2026-10-27）

- **D-04（载荷形态）**：数组载荷在改造前确实无法处理（`parseMessage` 返回 `status=error`）；改造后按元素逐个产出结局，`/canal/batch.json` 用例（3 元素：成功/跳过/不可重试）通过。
- **D-08（RocketMQ 容器）**：最终采用 `@RocketMQMessageListener` + `RocketMQListener<String>`；`maxReconsumeTimes` 为注解常量（`GXCanalConstant.DEFAULT_RETRY_MAX_ATTEMPTS`=3），无法由占位符驱动，故移除 `canal.rocketmq.max-reconsume-times` 键，改为固定上限 + 文档登记（`AGENTS.md` §9.4、手册 §6.2 注）。真实 broker 连通性仍未验证（环境缺失，见 `review-report.md` §5）。
- **D-12（`@Lazy`）**：已用 `GXCanalListenerRegistrationTest` 定案——懒加载监听器 Bean 不会实例化，端点因此从不注册（对照场景注册 0 个容器，eager 场景注册 1 个）。已移除生产监听器上的 `@Lazy` 并加断言防回归。
- **配置键微调**：`canal.log.handler-miss-level` 在实现中落为扁平键 `canal.handler-miss-log-level`（`GXCanalProperties#handlerMissLogLevel`），避免为一个键引入第三层嵌套配置类；`data-model.md` 已同步。

## D-14 Post-Design Constitution Re-check

- **Decision**: Phase 1 设计（`data-model.md` / `contracts/canal-consumer-contract.md` / `quickstart.md`）完成后复核宪法 7 条原则 + 附加约束 §2/§3/§4/§8/§9：全部 PASS，无新增违例。
- **Rationale**: 设计未引入新模块、新第三方依赖或投机抽象；新增的抽象均为"两实现以上"或"契约兼容必需"（已在 `plan.md` 的 Complexity Tracking 登记）。唯一需要显式声明的行为变更是 D-12（移除 `@Lazy` 可能使消费从"实际未运行"变为"真实运行"），已在计划中标注为上线注意项。
- **Alternatives considered**: 无（复核动作本身不含替代方案）。
