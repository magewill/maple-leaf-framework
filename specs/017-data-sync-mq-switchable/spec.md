# Feature Specification: leaf-base-data-sync 正确性 / 线程安全 / 性能审查与消息中间件可切换改造

**Feature Branch**: `017-data-sync-mq-switchable`（本次未经 `before_specify` hook 创建分支；规格目录名与分支名解耦）

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "审查 @leaf-base-data-sync 模块，需要审查它实现的正确性，线程安全性、性能。现在的实现只支持 rabbitmq 消息中间件，是否可以将 rocketmq 也集成进来，方便进行消息中间件的切换?"

## 背景与审查结论摘要

本规格把"`leaf-base-data-sync` 现状是否正确、是否线程安全、是否存在性能结构问题，以及能否在不改动业务处理器、不破坏既有契约的前提下接入 RocketMQ 以实现消息中间件切换"转化为可验收的交付要求。

审查对象与证据来源：

1. `leaf-base-data-sync` 全部 **23 个源码文件**（14 个生产类型 + 9 个 `package-info.java`）与 `src/main/resources/ymls/dev/common.yml`；
2. 契约来源：`leaf-base-data-sync/AGENTS.md`、`docs/leaf-base-data-sync开发手册.md`、`.specify/memory/constitution.md`（v1.3.0）；
3. 依赖基线（`leaf-base-data-sync/pom.xml` 实测）：仅 `leaf-base-framework`、`leaf-base-rabbitmq`（compile）+ `spring-boot-starter-test`（test），**无 RocketMQ 依赖、无 Nacos 依赖、无配置元数据资源、无自动装配注册**；
4. 可复用资产（既有封装，不得改造其公开契约）：`leaf-base-rabbitmq`（包根 `cn.maple.rabbitmq`，`GXSendRabbitMQService`、`GXRabbitMQQueueListener`，配置源开关 `maple.framework.rabbitmq.config-source`）、`leaf-base-rocketmq`（包根 `cn.maple.rocketmq`，`GXSendRocketMQService`、`GXRocketMQMessageReqDto`、配置源开关 `rocketmq.config-source`，其模块手册明确声明**不承载消费容器**，且禁止在模块内并行引入另一套发送抽象）；
5. 测试基线（实测）：3 个测试类共 16 个用例（`GXCanalMessageParseServiceImplTest` 12 + `GXCanalConfigTest` 2 + `GXCanalRabbitMQListenerTest` 2），全部为单元测试（`ApplicationContextRunner` + 反射注入 + 静态 mock `GXSpringContextUtils`），**无 `@SpringBootTest`、无并发用例、无失败处置用例、无 MQ 切换用例**。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 解析与分发的骨架（空值/非法 JSON/库表缺失的防御、`database + table` 到 Bean 名的约定、默认兜底、三类操作分发）是完整且可测的，但**消费结果对所有失败分支一律等同于成功**、**DDL 事件被当成未知错误**、**毒消息无限重投**、**批量/数组载荷不支持**四类问题会导致"看起来消费成功、实际数据丢失或空转" | 存在高风险缺陷 |
| 线程安全 | 解析服务本身无状态，可安全并发调用；风险集中在**可变配置 Bean 与 Nacos 刷新线程的可见性**、**监听器懒加载与端点注册的竞态风险**、**处理器路由依赖运行期容器查找而无快照**、**载荷集合与处理器共享可变引用** | 无致命竞态，但缺一致性约束 |
| 性能 | 每条消息至少 1～2 次 Spring 容器查找（未命中路径以**异常**驱动）、服务名格式化与驼峰转换、JSON 校验与反序列化两次入口、错误日志无节流；默认 `concurrency=10` 叠加 `prefetch=250` 形成约 2500 条在途上限且不可观测 | 存在结构性开销与容量盲区 |
| MQ 可切换 | 当前消息中间件**硬绑定** RabbitMQ（交换机/队列/绑定/监听全部以 AMQP 类型直接声明），无任何中间件抽象；接入 RocketMQ 已确定以 **`leaf-base-rocketmq` 为唯一依赖载体**（不直接引入 starter/client），技术上可行且不需要改动业务处理器，但必须同时解决**依赖传导（rocketmq-client / nacos-client / fastjson 强制下沉）**、**本地配置源必然失效**、**失败语义在两种 MQ 下的对齐**三个前提 | 可行，需先收敛依赖与解耦 |

关键证据（均可按文件与行号复核）：

- **E-01**：`pom.xml` 仅声明 `leaf-base-rabbitmq`（第 20–24 行），模块内所有消息收发类型（`org.springframework.amqp.core.*`、`@RabbitListener`）都是 AMQP 专用，没有任何"消息来源"抽象层。
- **E-02**：`GXCanalRabbitMQListener` 同时标注 `@Component` 与 `@Lazy`（第 14–16 行），且全仓库无任何注入点引用它。`@RabbitListener` 端点由 `RabbitListenerAnnotationBeanPostProcessor` 在 Bean **实例化之后**的后置处理阶段收集，而懒加载且无引用方的单例不会被预实例化 → **端点可能从未注册**（待核实；核实方式：`@SpringBootTest` 断言 `RabbitListenerEndpointRegistry#getListenerContainers` 非空且 `isRunning()==true`）。若确认不注册，则"生产环境在用"的前提与代码行为矛盾，必须优先定案。
- **E-03**：`listener` 丢弃 `parseMessage` 的返回值（`GXCanalRabbitMQListener` 第 30 行），而 `parseMessage` 用 `status=error/warning` 表达 6 类失败（空消息、非 JSON、JSON 反序列化失败、库表缺失、处理器不存在/类型不匹配、未知操作类型、处理器返回 `null`）→ 这些消息在 AMQP 侧表现为**消费成功并 ack**，既不可重试也无持久化失败记录。
- **E-04**：处理器异常原样抛出（`GXCanalMessageParseServiceImpl` 第 75/79/83 行 → `GXCanalRabbitMQListener` 第 34 行），但队列声明未绑定死信交换机、容器未配置 `defaultRequeueRejected` / `retry` / `errorHandler`（`GXCanalConfig` 全量与 `@RabbitListener` 注解参数）→ 默认 `defaultRequeueRejected=true` 时同一条失败消息被无限重投，`AGENTS.md` §9.1 声称的"由容器应用 ack/retry/reject 策略"实际并未配置。
- **E-05**：每条消息执行 `GXSpringContextUtils.getBean(serviceName)`（第 55 行），未命中时还要再查一次 `defaultProcessCanalDataService`（第 58 行）；`GXSpringContextUtils.getBean(String)` 内部以 `NoSuchBeanDefinitionException` 判定缺失（`GXSpringContextUtils` 第 33–36 行）→ **以异常构造与栈填充驱动默认兜底**，在"大量表未注册专用处理器"的常态下每条消息至少触发一次。
- **E-06**：服务名在热路径用 `CharSequenceUtil.format("{}_{}_Service", ...)` + `toCamelCase` 现算（第 51–52 行），无缓存；且该命名方案存在两类不可忽视的冲突：`db=a_b/table=c` 与 `db=a/table=b_c` 生成同名 Bean；表名含 `.`、`-`、大写字母时生成的 Bean 名不合法或无法命中，最终静默走默认兜底（错误只体现为一条 debug 日志）。
- **E-07**：`JSONUtil.isTypeJSON(message)`（第 33 行）与 `JSONUtil.toBean(message, ...)`（第 40 行）是两次独立入口；非法载荷路径还会额外构造 `JSONException` 并以 `log.error` 打印（第 34、42 行），无节流、无采样。
- **E-08**：仅支持"单个 JSON 对象"载荷。Canal flat message 在批量模式下投递的是 **JSON 数组**，`toBean` 到对象类型会失败或产出空对象（待核实实际报文形态；核实方式：抓取 Canal sink 实际载荷各一条，或补数组载荷用例）。
- **E-09**：DDL 事件未区分：`GXCanalDataDto` 已解析 `isDdl`（第 35 行）但从未被读取，`switch(type)` 只覆盖 `INSERT/UPDATE/DELETE`（第 72–89 行），`CREATE/ALTER/ERASE` 等 DDL 类型全部落入 `default` 分支并记 error；DDL 在高频 DDL 环境会持续产生"错误"噪声。
- **E-10**：`GXCanalProperties` 是 `@Data` 可变 Bean，`GXNacosCanalProperties` 声明 `autoRefreshed=true`（第 17 行）→ 刷新线程写、消费线程读，字段无 `volatile` / 快照语义；同时 `concurrency` 经 SpEL 只在容器启动时求值一次（`GXCanalRabbitMQListener` 第 26 行），刷新**不会**生效，与"可动态刷新"的对外承诺不一致。
- **E-11**：`GXLocalCanalProperties` 使用 `@PropertySource("classpath:/${spring.profiles.active}/canal.yml", ignoreResourceNotFound=false)`（第 18 行），而仓库中的 `canal.yml` 位于**外置目录** `config/{dev,pre,prod,test}/canal.yml`；模块 `src/main/resources` 下只有 `ymls/dev/common.yml`（内容 `canal: queue: canal.queue`，与 `GXCanalProperties` 任何字段都不对应），且全仓库无任何代码引用 `ymls` → 本地源要么因资源/占位符缺失启动失败，要么绑定的不是业务实际维护的那份文件。
- **E-12**：配置源互斥依赖 `@ConditionalOnMissingClass("com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties")`（`GXLocalCanalProperties` 第 17 行）。一旦按本需求引入 `leaf-base-rocketmq`（compile 依赖 `leaf-base-nacos`，其中含该注解类），条件**恒为 false**，本地源永不装配，而 Nacos 源又未被真实拉取 → 启动失败。这与 `specs/013-sso-module-audit` 的 F-06 属同型问题，宪法 §IV 已给出"显式开关 + 四条护栏"的解法。
- **E-13**：`GXCanalConfig` 声明三个裸类型全局 Bean：`Exchange`（`fanoutExchange`）、`Queue`（`canalQueue`）、`Binding`（`binding`），均无 `@ConditionalOnMissingBean`、无显式开关（第 21–34 行）→ 与下游同类 Bean 冲突时的后果是启动歧义；现有测试 `GXCanalConfigTest` 第 35 行直接用 `context.getBean(Queue.class)` 取值，本身就依赖"全局只有这一个 Queue"这一脆弱假设。
- **E-14**：模块无 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`、无 `spring.factories`、无 `additional-spring-configuration-metadata.json` → 装配完全依赖下游 `@ComponentScan("cn.maple")`；`canal.*` 全部配置键在 IDE 中无提示（宪法 §IV）。
- **E-15**：固定 `FanoutExchange` + 固定队列名常量（`GXCanalConstant` 第 7–10 行）：`routingKey` 对 fanout 无路由语义（仅写入 `Binding` 元数据），多 Canal 实例 / 多库分队列无法按 routingKey 过滤，所有订阅方收到全量变更，且队列名默认值 `canalQueue` 极易与下游自建队列重名。
- **E-16**：消费链路无 TraceId/MDC 传播：监听器直接接收 `String`（未读取 AMQP 消息头），`parseMessage` 的失败日志无法与上游链路关联（宪法 §V 要求异步/消费路径使用既有 MDC 包装能力）。
- **E-17**：`prefetch` 未配置（AMQP 默认 250）叠加 `concurrency=10`（常量默认值）→ 单实例在途消息约 2500 条；无批量消费、无背压、无指标，容量上限只能靠推算。
- **E-18**：`GXCanalDataDto` 的 `id`（第 23 行）不是 Canal flat message 标准字段（Canal 用 `pkNames` + `data` 表达主键），语义存疑；`data` / `old` 为可变 `List<Dict>` 且与处理器共享同一引用，处理器可回写框架侧对象。
- **E-19**：消费结果用字符串魔法值表达（`"success"` / `"error"` / `"warning"`），`GXCanalConstant` 只定义了 4 个 RabbitMQ 常量，未覆盖结果语义（宪法 §III 的同类约束）。
- **E-20**：文档漂移：手册 §2.3 称 `RABBITMQ_CANAL_QUEUE_NAME` 默认 `"example"`（实际 `canalQueue`）、称类名为 `CanalConstant`（实际 `GXCanalConstant`）、§2.2 称"确保消息在 RabbitMQ 重启后不会丢失"（当前无死信、无重试上限，该承诺不成立）。
- **E-21**：RocketMQ 接入的依赖载体已确定但影响面未收敛：`leaf-base-rocketmq` 的 `pom.xml` 中 `leaf-base-nacos`（第 24–28 行）、`fastjson` 1.x（第 29–33 行）、`rocketmq-spring-boot-starter 2.3.6`（第 34–72 行，**无 `<optional>`**）全部为 compile 传递依赖 → 若本模块以 compile 方式依赖它，`rocketmq-client`、`nacos-client`、`fastjson` 将强制传递给所有下游（含仅用 RabbitMQ 的应用），并直接触发 E-12 的配置源失效。
- **E-22**：`leaf-base-rocketmq` 已有一套**消费侧契约但没有消费容器**：`GXCdcMessageDispatcher#dispatch(String, String)` + `GXCdcEventListener`（`onCreate/onUpdate/onDelete/onRead`）+ `GXCdcDispatchStatistics` + 死信转发（`maple.framework.cdc.dead-letter-topic`，经 `RocketMQTemplate` 单向发送），整体由 `maple.framework.cdc.enable` 门控；全仓库 `@RocketMQMessageListener` 零命中，`rocketmq-spring-boot-starter` 的消费容器能力在本仓库从未被使用 → 其"事件模型"（`GXCdcEvent`/`GXCdcOperation`）与 Canal 载荷模型不同，且需要业务方改实现 `GXCdcEventListener`。

发现清单（分级）：

- **F-01（高）消费结果与 ack 语义脱钩，失败一律静默**：6 类失败分支全部以正常返回结束（E-03），不可重试错误与可重试错误无区分，无持久化失败记录。
- **F-02（高）毒消息无限重投**：异常向上抛出但无重试上限、无死信、无 `defaultRequeueRejected` 设置（E-04）。
- **F-03（高）监听器可能从未注册**：`@Lazy` 与 `@RabbitListener` 组合的注册风险未经验证，且模块无任何 Spring Boot 集成测试兜底（E-02、E-18 测试基线）。
- **F-04（高）DDL 事件被当作错误、批量载荷不支持**：DDL 类型未识别（E-09），数组载荷无法解析（E-08），两类都会在真实 Canal 场景直接发生。
- **F-05（中）处理器路由依赖热路径容器查找且以异常驱动**：每条消息 1–2 次 `getBean`，未命中即抛异常（E-05），无路由快照、无负缓存、无命中率观测。
- **F-06（中）Bean 命名方案存在冲突与非法名，失败静默**：库表组合冲突、特殊字符导致命名不合法，全部退化为"走默认处理器"而不告警（E-06）。
- **F-07（中）配置源在引入 RocketMQ 依赖后必然失效**：`@ConditionalOnMissingClass` 判定被 nacos 类路径击穿（E-12）；即使不引入 RocketMQ，本地源绑定的文件与业务维护的外置 `config/*/canal.yml` 也不是同一份（E-11）。
- **F-08（中）MQ 基础设施 Bean 为裸类型全局 Bean、不可覆盖**：`Exchange`/`Queue`/`Binding` 三个全局 Bean 与兄弟模块/下游冲突风险，且模块无法被"只当解析库用"（E-13）。
- **F-09（中）无配置元数据与自动装配注册**：装配依赖组件扫描、配置键无 IDE 提示、未装配时无任何信号（E-14）。
- **F-10（中）容量与背压不可观测**：`prefetch` 默认 250 × `concurrency` 10 的在途上限不可调、不可测（E-17）。
- **F-11（中）可刷新语义失真**：`autoRefreshed=true` 但影响装配的键刷新无效且无提示（E-10）。
- **F-12（中）TraceId/MDC 缺失**：消费链路无法关联上游（E-16）。
- **F-13（低）fanout 语义与多库路由能力缺失**：`routingKey` 形同虚设，默认队列名易冲突（E-15）。
- **F-14（低）载荷对象与处理器共享可变引用**：`data` / `old` 无防御性隔离（E-18）。
- **F-15（低）结果表达为字符串魔法值**（E-19）。
- **F-16（低）文档与实现漂移**（E-20）。
- **F-17（高）RocketMQ 依赖引入方式未定，会同时冲击依赖隔离与配置源**：接入必须经 `leaf-base-rocketmq`，而该模块的 rocketmq-client / nacos-client / fastjson 均为非 optional 传递依赖（E-21）；若本模块直接 compile 依赖，则"仅用 RabbitMQ 的下游"被迫引入整套 RocketMQ 与 Nacos 栈，本地配置源（E-12）也从"潜在失效"变成"必然失效"。
- **F-18（中）复用边界未定义，存在两种相反的错误**：完全不复用 `GXCdc*` 会重新实现死信转发与统计（违反宪法附加约束 §4 反重复造轮子）；直接以 `GXCdcMessageDispatcher`/`GXCdcEventListener` 作为 Canal 分发主路径会要求业务方改实现另一套监听接口、放弃 `database_table_Service` 约定与 `GXProcessCanalDataService`（违反 FR-029 零改动），且把另一能力域的开关 `maple.framework.cdc.enable` 混入数据同步（E-22）。

已确认为**正确实现**（不得在改造中被破坏）：

- **P-01**：`GXCanalMessageParseServiceImpl` 无实例字段，`Dict` 与 DTO 均按调用创建，多线程并发调用无共享状态（第 22–97 行）。
- **P-02**：交换机 `durable=true, autoDelete=false`、队列 `durable=true`（默认），符合 `AGENTS.md` §9.2 的持久化要求（`GXCanalConfig` 第 28、33 行）。
- **P-03**：监听器对消费异常**向上抛出而非吞掉**（第 32–35 行），保留了容器侧插手的可能，这是 F-02 的修复基础。
- **P-04**：解析入口对空值、非 JSON、库表缺失做了前置防御，且错误信息包含定位线索（第 28–49 行）。
- **P-05**：`database + table + "_Service"` → 小驼峰 Bean 名的扩展点约定、`defaultProcessCanalDataService` 兜底、三类操作分发语义清晰且有 12 个用例锁定（`GXCanalMessageParseServiceImplTest`）。
- **P-06**：模块 9 个包均提供 `package-info.java` 且统一 `@NullUnmarked`，为补齐 `@NullMarked`/`@Nullable` 提供基线。

## Clarifications

### Session 2026-10-27（规格编制期决策，供 `/speckit.clarify` 复核）

- Q: MQ 选择是"互斥单选"还是"迁移期双跑（同一应用同时订阅两种 MQ）"？ → A: **互斥单选**。切换以配置改动 + 重启达成，双跑会引入重复消费与幂等责任，超出当前模块职责（宪法附加约束 §8）。双跑能力登记为不支持。
- Q: 消息中间件抽象的落点是新建独立模块还是在 `leaf-base-data-sync` 内做适配？ → A: **本模块内做适配**。当前只有本模块有该需求，`leaf-base-rabbitmq` 与 `leaf-base-rocketmq` 的模块手册均禁止在其内部再引入并行抽象；待出现第二个消费方再按宪法 §I 抽取独立模块。
- Q: 切换开关的默认值？ → A: **默认保持 RabbitMQ**，保证既有部署零配置升级后行为不变（宪法 §II）。
- Q: 是否需要 RocketMQ 顺序消费？ → A: **默认并发消费**，与当前 RabbitMQ `concurrency=10` 的无序语义对齐；顺序/分区有序登记为不支持，并说明"需要严格顺序时按表维度拆分消费组"的替代方案。
- Q: 真实中间件验证如何做？ → A: 一律由环境变量门控（`RABBITMQ_*`、`ROCKETMQ_NAME_SERVER` 等），缺失时用例跳过并在交付报告中登记"未验证项"；不在仓库写入任何地址或凭据。
- Q（实施期回填）：RocketMQ 消费并发度是否必须可配置？ → A: **降级为固定值并显式登记**。`@RocketMQMessageListener` 的 `consumeThreadMax` / `maxReconsumeTimes` 为注解属性，不支持占位符；改用程序化 `DefaultRocketMQListenerContainer` 需以动态代理构造注解实例，复杂度与收益不匹配（宪法附加约束 §8）。因此 FR-022 增加"例外（已协商）"说明，实际值由启动日志打印，登记于 `AGENTS.md` §10 / 手册 §6.2 注。
- Q: RocketMQ 接入的依赖载体是什么？ → A: **必须依赖 `leaf-base-rocketmq`**（用户在本次澄清中明确要求）。本模块 MUST NOT 直接声明 `rocketmq-spring-boot-starter` / `rocketmq-client` 坐标，MUST NOT 在模块内另建一套 RocketMQ 连接与配置治理；名称服务、生产者组、配置源等一律沿用该模块既有键与校验逻辑。依赖以 `<optional>true</optional>` 声明（理由见 Assumptions），使用 RocketMQ 的下游需显式引入该模块；本模块的编译与测试类路径始终包含它，因此其传递依赖带来的配置源问题必须在本次一并解决，不得靠"下游没引就不会出问题"回避。
- Q: 是否复用 `leaf-base-rocketmq` 既有的 `GXCdc*` 消费分发链路（`GXCdcMessageDispatcher` / `GXCdcEventListener` / 统计 / 死信转发）？ → A: **不以其作为 Canal 分发主路径**，但**复用其能力面**。理由：`GXCdcEvent`/`GXCdcOperation` 是另一套规范化事件模型，直接套用会要求业务方改用 `GXCdcEventListener` 并放弃 `database_table_Service` 与 `GXProcessCanalDataService`（违反"业务零改动"），同时把 `maple.framework.cdc.enable` 这一其它能力域的开关混入数据同步。复用范围限定为：RocketMQ 连接/发送能力（`GXSendRocketMQService`、`RocketMQTemplate`）、死信转发与统计模式、配置源治理约定。该边界登记为不支持范围的补充说明（FR-038）。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核、缺陷可复现 (Priority: P1)

框架维护者要判断"该模块能否继续在生产使用、哪些缺陷必须先修"，需要每条结论都能跳到文件与行号，并按给定步骤复现故障。

**Why this priority**: 结论不可复核时，后续修复与排期都建立在猜测上；且本次审查发现的高风险项（ack 语义、毒消息重投）若不先在可复现条件下确认，修复方向无法收敛。

**Independent Test**: 随机抽取一条发现，按其"文件/行号 + 复现步骤"构造故障现场；高风险项必须能在本地可执行测试中稳定复现（例如构造处理器抛异常的消息，观察同一消息被反复投递且无失败记录）。

**Acceptance Scenarios**:

1. **Given** 一条"处理器不存在时静默走默认兜底"的结论，**When** 投递 `database`/`table` 无对应 Bean 的消息，**Then** 消费被判定为成功且无告警级日志，与结论一致。
2. **Given** 一条"DDL 事件被记为错误"的结论，**When** 投递 `isDdl=true` 的 `ALTER` 事件，**Then** 结果状态为 error 且消息被 ack，与结论一致。
3. **Given** 一条"监听器可能未注册"的待核实结论，**When** 在真实 Spring Boot 上下文中启动，**Then** 能明确得到"已注册/未注册"的结论并记录为定案。

---

### User Story 2 - 每条消息都有确定结局，毒消息不再空转 (Priority: P1)

运维与业务方要求：任何一条 Canal 变更消息最终必须落到"成功 / 跳过 / 待重试 / 已进入死信或失败记录"之一，不允许出现"既不算成功、也无任何记录"的路径，也不允许同一条消息无限重投。

**Why this priority**: 数据同步的价值在于"不丢、不卡"。当前实现把 6 类失败都当成成功，且失败消息可无限重投，这两点在真实故障下同时导致数据丢失与消费堆积。

**Independent Test**: 用可控的失败注入（空载荷、非 JSON、DDL、未知类型、处理器抛异常、处理器返回 null）逐条投递，断言每条消息的结局类别、重投次数上限与可检索日志/计数；断言不存在"无结局"路径。

**Acceptance Scenarios**:

1. **Given** 一条非 JSON 载荷，**When** 被消费，**Then** 归入"不可重试"，不进入重投循环，产生一条可检索的失败记录与对应计数。
2. **Given** 处理器抛异常，**When** 被消费，**Then** 归入"可重试"，重投次数达到上限后进入死信或落为失败记录，消息不丢失。
3. **Given** 一条 DDL 事件，**When** 被消费，**Then** 归入"跳过"并计数，不产生 error 级日志。
4. **Given** 处理器返回 `null`，**When** 被消费，**Then** 结局可判定（成功或失败二选一），不再以"warning 但 ack"的方式消失。

---

### User Story 3 - 通过配置切换消息中间件，业务处理器零改动 (Priority: P2)

使用方希望在 RabbitMQ 与 RocketMQ 之间按部署环境切换：只改配置（消息类型与对应连接/主题参数），已实现的 `GXProcessCanalDataService` 表级处理器与 `database_table_Service` 命名约定完全不变，不需要改代码、不需要改业务逻辑。

**Why this priority**: 这是本次需求的直接来源；但它建立在 P1 的正确性修复之上（否则切换后只是把缺陷复制到第二套 MQ），因此排在 P1 之后。

**Independent Test**: 同一组业务处理器与同一组 Canal 载荷，在只改配置的情况下分别以 RabbitMQ 与 RocketMQ 启动，断言处理结果、日志与计数逐条一致；断言仅改配置即可切换、代码零改动。

**Acceptance Scenarios**:

1. **Given** 配置为 RabbitMQ，**When** 应用启动，**Then** 交换机/队列/绑定/监听行为与改造前完全一致（名称、持久化、并发数）。
2. **Given** 配置为 RocketMQ 且已引入 `leaf-base-rocketmq`，**When** 应用启动，**Then** 只装配 RocketMQ 消费侧（复用该模块的连接与配置约定），不声明 RabbitMQ 交换机/队列/绑定，也不要求 RabbitMQ 连接可用。
3. **Given** 配置了未知的 MQ 类型，**When** 应用启动，**Then** 启动失败并给出可操作的提示（列出合法取值），不做静默回退。
4. **Given** 配置为 RocketMQ 但类路径缺少 `leaf-base-rocketmq`，**When** 启动，**Then** 启动失败并明确提示"引入 `leaf-base-rocketmq` 或改用 rabbitmq"，且该失败不影响仅使用 RabbitMQ 的下游应用。
5. **Given** 一个只使用 RabbitMQ 的下游应用，**When** 查看其依赖树，**Then** 不出现 `rocketmq-client`、`nacos-client`、`fastjson`（依赖隔离生效）。

---

### User Story 4 - 热路径开销与线程安全可量化 (Priority: P2)

框架维护者需要知道"每条消息框架侧引入多少开销、并发下会不会串扰"，并能用数据证明优化有效。

**Why this priority**: 数据同步是持续高频链路，框架侧开销会被放大到每条变更；线程安全问题一旦出现是随机故障，事后排查成本极高。

**Independent Test**: 以固定载荷集做本地基准（如 10 万条消息），对比改造前后的框架侧耗时与容器查找次数；以 32 线程 × 每线程 1000 条消息做并发用例，断言处理器命中率与串行一致、无串扰。

**Acceptance Scenarios**:

1. **Given** 一批"表未注册专用处理器"的消息，**When** 连续消费，**Then** 框架侧不再每条消息构造一次容器查找异常，处理器解析结果来自启动期快照或缓存。
2. **Given** 32 个线程并发消费 32000 条消息，**When** 全部完成，**Then** 每条消息都命中预期处理器，计数自洽，无跨消息状态污染。
3. **Given** 并发消费期间的配置刷新事件，**When** 刷新完成，**Then** 影响装配的键的生效范围有明确说明，且不会出现半新半旧的装配状态。

---

### User Story 5 - 装配可控、配置可发现、链路可追踪 (Priority: P3)

使用方需要：不需要消息消费能力时能明确关闭；`canal.*` 与 MQ 相关配置键在 IDE 中有提示；消费失败日志能关联到上游链路。

**Why this priority**: 影响接入体验与排障效率，但不影响核心数据正确性。

**Independent Test**: 在关闭开关的上下文中启动，断言无 MQ 基础设施 Bean、无连接尝试；检查配置元数据文件覆盖全部公开键；断言失败日志包含可关联的追踪标识。

**Acceptance Scenarios**:

1. **Given** 显式关闭数据同步消费，**When** 启动，**Then** 不声明任何 MQ 基础设施 Bean，也不尝试建立连接。
2. **Given** 任一 `canal.*` 公开配置键，**When** 在支持元数据的 IDE 中编辑配置，**Then** 有类型与默认值提示。
3. **Given** 一条消费失败的消息，**When** 查看日志，**Then** 可凭追踪标识与上游产生该变更的请求链路对齐。

### Edge Cases

- 载荷为空串 / 全空白：归入不可重试，且不得以 error 级刷屏。
- 载荷是 JSON 数组（批量）：逐个事件处理；数组内单条失败不得吞掉其余事件的处理记录。
- 载荷是合法 JSON 但缺 `database` 或 `table`：不可重试。
- 载荷是合法 JSON、`database`/`table` 合法但含 `.`、`-`、大写字母：路由结果可解释（要么命中，要么告警级"命名不合法"，不得静默兜底）。
- `db=a_b/table=c` 与 `db=a/table=b_c` 同时存在：必须能被区分；无法区分时启动期报错而非运行期静默混淆。
- `type` 为 DDL 类（`CREATE`/`ALTER`/`ERASE`/`DROP`/`TRUNCATE`）或 `isDdl=true`：跳过并计数。
- 处理器返回 `null`、抛出 `RuntimeException`、抛出 `Error`：结局分别为可判定失败、可重试、不可重试（`Error` 不得被包装成成功）。
- 同一消息重复投递（MQ 至少一次语义）：框架侧不得因此产生重复的失败记录风暴；幂等责任明确归属业务处理器。
- MQ 连接不可用 / 启动后 broker 断开：失败可见，不得表现为"启动成功但静默不消费"。
- 同时配置了两套 MQ 的参数：未选中那套的配置必须被忽略且不影响启动，选中那套缺失必需参数时启动失败。
- 配置在运行期被刷新（Nacos）而改变的键属于"装配期键"（队列名/交换机/并发/主题/消费组）：必须显式声明不生效，或以日志明确提示需要重启。
- 下游已存在同名/同类型 Bean（`Queue`/`Exchange`/`Binding`）：本模块不得导致启动歧义。

## Requirements *(mandatory)*

### Functional Requirements

**契约保持（改造前提）**

- **FR-001**: 改造 MUST 保持以下既有契约不变：`canal.*` 配置键名与默认值；`GXCanalMessageParseService#parseMessage(String)` 与 `GXProcessCanalDataService` 的方法签名；`database + table + "_Service"` → 小驼峰 Bean 名的扩展点约定；`defaultProcessCanalDataService` 兜底语义；RabbitMQ 交换机名/队列名/路由键/并发数默认值。
- **FR-002**: `GXCanalDataDto` 既有字段的语义与反序列化行为 MUST NOT 被改变；新增字段不得影响旧载荷的解析结果。
- **FR-003**: 本模块 MUST NOT 修改 `leaf-base-rabbitmq`、`leaf-base-rocketmq` 的公开契约与行为，也不得在这两个模块内新增并行抽象；一切 MQ 适配逻辑落在本模块内。RocketMQ 接入 MUST 以 `leaf-base-rocketmq` 为唯一依赖载体（见 FR-043）。

**消费结果与失败处置**

- **FR-004**: 系统 MUST 为每条被消费的消息判定唯一结局，取值为：成功、跳过、可重试失败、不可重试失败（进入死信/失败记录）。不允许存在无结局分支。
- **FR-005**: 解析类失败（空载荷、非 JSON、缺库表、未知操作类型、载荷结构不符）MUST 归类为不可重试，并产生可检索日志与计数；MUST NOT 进入无限重投。
- **FR-006**: 处理器抛出的异常 MUST 归类为可重试，且重投次数有明确上限；达到上限后 MUST 进入死信或落为可检索的失败记录。
- **FR-007**: 处理器返回 `null` MUST 有确定结局（当前为 warning 后 ack，需明确为成功或失败之一），不得出现"既不成功也无记录"。
- **FR-008**: DDL 事件 MUST 按"跳过"处理并计数，MUST NOT 记为错误、MUST NOT 进入重试。
- **FR-009**: 载荷 MUST 同时支持单个 JSON 对象与 JSON 数组（批量），数组按元素逐个判定结局；单条元素失败不得影响同批其他元素的处理与记录。
- **FR-010**: 处理器未命中（走默认兜底）MUST 可观测（计数 + 可配置的日志级别），不得只留 debug 日志。
- **FR-011**: 处理器命名不合法或库表组合冲突 MUST 在启动期或首次命中时以告警级信号暴露，不得静默退化为兜底处理。
- **FR-012**: 监听器 MUST 在消费异常时保持"向上抛出/显式 reject"的既有语义，MUST NOT 把失败转换为成功消费。

**线程安全与一致性**

- **FR-013**: 处理器路由表 MUST 在启动期构建为不可变快照（或等价的并发安全结构），运行期读取 MUST 无锁；若支持运行期刷新，MUST 原子替换而非逐项改写。
- **FR-014**: 所有跨线程共享的可变配置 MUST 具备明确的可见性保证（不可变快照、`volatile` 或并发容器），MUST NOT 依赖"字段恰好不被优化"。
- **FR-015**: 影响装配的配置键（队列名、交换机名、路由键、并发数、MQ 类型、主题、消费组）在运行期刷新后的行为 MUST 被显式声明：要么生效，要么记录"需重启生效"的日志；MUST NOT 表现为"可刷新"而实际不生效。
- **FR-016**: 单条消息处理 MUST NOT 让框架侧对象（配置、路由表、计数器）与业务处理器共享可变引用；`data` / `old` 等集合的共享语义 MUST 明确。
- **FR-017**: 消息处理链路 MUST NOT 引入新的静态可变状态；如需计数器，MUST 使用线程安全实现并支持重置/读取。

**性能**

- **FR-018**: 热路径 MUST NOT 每条消息执行 Spring 容器查找；不得以异常构造作为正常控制流。
- **FR-019**: `database + table` 到处理器的解析结果 MUST 可缓存（含"无专用处理器"的负结果），缓存键空间 MUST 有界（按库表组合，不随消息数增长）。
- **FR-020**: 单条消息的 JSON 解析 MUST 只做一次等价工作量（校验与反序列化不得各自完整解析一遍）。
- **FR-021**: 热路径日志 MUST NOT 产生昂贵字符串构造；error 级日志 MUST 具备节流/采样或计数聚合，避免故障期日志放大。
- **FR-022**: 消费并发度与在途上限 MUST 可配置且可观测（至少包含并发数与每条消费者预取/批量的上限），不得依赖默认值隐含的容量（当前约 2500 条在途）。**例外（已协商）**：RocketMQ 的 `consumeThreadMax`（20）与 `maxReconsumeTimes`（3）是 `@RocketMQMessageListener` 的注解属性，不接受占位符或配置覆盖，框架将二者固定为 `GXCanalConstant` 常量并在启动日志打印实际值；该例外已登记于 `AGENTS.md` §10 与开发手册 §6.2 注。
- **FR-023**: 系统 MUST 提供可读取的运行计数：成功、跳过、可重试失败、不可重试失败、处理器未命中、处理耗时分布；指标暴露方式 MUST 与框架既有可观测手段一致，不得为指标单独引入重量级依赖。

**消息中间件可切换**

- **FR-024**: 系统 MUST 提供显式的消息中间件类型选择键（默认 `rabbitmq`），取值非法时 MUST 启动失败并列出合法取值。
- **FR-025**: 选择 RabbitMQ 时，装配与运行行为 MUST 与改造前等价（同名交换机/队列/绑定、同默认并发、同扩展点语义）。
- **FR-026**: 选择 RocketMQ 时，MUST 只装配 RocketMQ 消费侧，MUST NOT 声明 RabbitMQ 交换机/队列/绑定，MUST NOT 因 RabbitMQ 不可用而启动失败。
- **FR-027**: 两种 MQ MUST 共享同一套解析、路由、失败分类与计数逻辑；MUST NOT 复制业务分发代码。
- **FR-028**: RocketMQ 侧 MUST 支持主题、消费组、标签/过滤表达式、名称服务地址的可配置化，并给出默认值与取值范围说明；配置键 MUST NOT 与 `rocketmq.*` 既有键冲突或重复定义。
- **FR-029**: 切换 MUST 只需改配置，MUST NOT 要求业务处理器、`database_table_Service` 命名、`GXProcessCanalDataService` 实现做任何改动。
- **FR-030**: 依赖隔离：RocketMQ 相关依赖 MUST NOT 被强加给仅使用 RabbitMQ 的下游；未引入 RocketMQ 依赖时选择 `rabbitmq` MUST 正常工作；选择 `rocketmq` 而依赖缺失时 MUST 启动失败并给出"请引入 `leaf-base-rocketmq` 或改用 rabbitmq"的可操作提示（依赖声明方式见 FR-044）。
- **FR-031**: 未被选中的 MQ 的消费基础设施 MUST NOT 被本模块隐式激活，MUST NOT 产生连接尝试或后台线程。

**配置与装配**

- **FR-032**: 引入 RocketMQ 相关依赖后，本地/Nacos 双配置源 MUST 仍然真实生效且互斥；若类路径条件不再可达，MUST 按宪法 §IV 改用显式开关并满足四条护栏（默认值保持既有行为、启动打印实际来源与关键默认值、互斥装配有测试、新键登记元数据）。
- **FR-033**: 本模块声明的 MQ 基础设施 Bean MUST 可被下游覆盖或关闭（`@ConditionalOnMissingBean` 与/或显式开关），Bean 名与类型 MUST NOT 与兄弟模块产生歧义。
- **FR-034**: 本模块全部公开配置键（含新增 MQ 键）MUST 登记到 `additional-spring-configuration-metadata.json`，并同步模块 `AGENTS.md` 与开发手册的配置键表。
- **FR-035**: 系统 MUST 提供"关闭数据同步消费"的开关；关闭时不装配任何 MQ 基础设施，也不建立连接。
- **FR-036**: 消费链路 MUST 生成/传播追踪标识并写入 MDC，使失败日志可与上游链路关联。

**表达与文档**

- **FR-037**: 消费结局 MUST 以常量或枚举表达，MUST NOT 继续扩散字符串魔法值；新增常量 MUST 归入模块既有常量类。
- **FR-038**: 不支持范围（顺序消费、事务消息、批量确认、跨 MQ 双跑、跨库 routing 过滤、业务幂等）MUST 在 `AGENTS.md` 与开发手册显式登记"不支持 + 原因 + 替代方案"（宪法附加约束 §9）。
- **FR-039**: 文档 MUST 与实现一致：修正手册中错误的默认值、类名与"RabbitMQ 重启不丢消息"等不成立的能力承诺。

**测试与门禁**

- **FR-040**: MUST 补齐单元测试与 Spring Boot 集成测试，覆盖：两类 MQ 的装配与互斥、MQ 切换、失败分类与重投上限、DDL 跳过、数组载荷、处理器未命中与命名非法、配置绑定与刷新语义、并发消费无串扰、关闭开关。
- **FR-041**: 依赖外部中间件的用例 MUST 在环境缺失时优雅跳过，不得让流水线因此失败；跳过项 MUST 在交付报告中列出。
- **FR-042**: 交付 MUST 通过 `mvn -pl leaf-base-data-sync -am test`，且 ErrorProne + NullAway 无错误；MUST NOT 通过删除编译参数或注解处理器绕过检查。

**消息中间件依赖载体与复用边界**

- **FR-043**: RocketMQ 接入 MUST 通过 `leaf-base-rocketmq` 依赖实现，且它是本模块唯一的 RocketMQ 坐标载体；本模块 MUST NOT 直接声明 `rocketmq-spring-boot-starter`、`rocketmq-client`、`rocketmq-acl`、`rocketmq-common` 等坐标，MUST NOT 自建 NameServer 客户端、生产者/消费者连接管理或另一套 RocketMQ 配置绑定；版本与传递依赖治理沿用该模块与根 POM（宪法 §II、附加约束 §2/§3）。
- **FR-044**: 该依赖 MUST 以 `<optional>true</optional>`（或等效的"下游显式引入"策略）声明，使仅使用 RabbitMQ 的下游应用依赖树中不新增 `rocketmq-client` / `nacos-client` / `fastjson`；选择 `rocketmq` 而依赖缺席时的失败提示 MUST 指明需要引入的 artifact（`leaf-base-rocketmq`）。
- **FR-045**: 由于 `leaf-base-rocketmq` 在编译期与测试期必然存在于本模块类路径，配置源选择结果 MUST 与类路径无关（显式开关），并 MUST 覆盖"类路径中有/无 nacos 注解类 × 默认值/显式取值"四种组合的装配测试，确保默认值不因引入依赖而悄然改变（宪法 §IV 四条护栏）。
- **FR-046**: 复用边界 MUST 明确并落地：RocketMQ 路径 MUST 复用该模块的连接/发送能力（`RocketMQTemplate`、`GXSendRocketMQService`）、死信转发与统计模式、名称服务与配置源治理约定；MUST NOT 以 `GXCdcMessageDispatcher` / `GXCdcEventListener` 作为 Canal 分发主路径，MUST NOT 引入 `maple.framework.cdc.*` 开关语义；该边界 MUST 同步写入模块 `AGENTS.md` 与开发手册。
- **FR-047**: 引入该依赖带来的传递依赖影响（`nacos-client`、`fastjson` 1.x、`rocketmq-client` 及根 POM 既有冲突覆盖项）MUST 在交付说明中给出用途、与现有依赖的重叠评估、体积/传递影响与可回滚方式（宪法附加约束 §3）。

### Key Entities

- **Canal 变更事件**：数据库变更的载体，含库名、表名、操作类型、是否 DDL、变更前后行数据、主键列名集合；同一批次可能包含多个事件。
- **表级处理器**：业务方为特定库表提供的扩展实现，按 `database_table_Service` 命名约定被定位；存在一个默认兜底处理器。
- **处理器路由表**：库表组合到处理器的映射，启动期一次性构建、运行期只读；包含"无专用处理器"的负结果。
- **消费结局**：成功、跳过、可重试失败、不可重试失败，决定消息的确认/重投/死信走向，并进入运行计数。
- **消息中间件类型**：`rabbitmq` / `rocketmq`，决定装配哪一套消费基础设施；同一时刻只允许一种生效。
- **MQ 连接参数集**：RabbitMQ 侧为交换机/队列/路由键/并发；RocketMQ 侧为名称服务地址/主题/消费组/标签过滤；未选中一侧的参数被忽略。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 切换消息中间件只需修改 1 个配置键（配套连接参数除外），业务处理器代码改动 0 行；同一组载荷在两种 MQ 下的处理结果与计数逐条一致。
- **SC-002**: 100% 的测试消息（含全部失败注入场景）都能被判定为四种结局之一；故障注入用例中"无结局"路径数量为 0。
- **SC-003**: 同一条可重试失败消息的重投次数有上限（默认不超过 3 次），随后进入死信或失败记录；用例证明不存在无限重投。
- **SC-004**: 单条正常消息在框架侧的容器查找次数与异常构造次数均为 0；处理器未命中场景下框架侧单条消息耗时相对改造前下降 ≥ 80%（基准：本地 10 万条消息微基准，记录命令与样本量）。
- **SC-005**: 32 线程并发消费 32000 条消息，处理器命中数与预期一致、计数自洽、无跨消息状态污染；测试可重复通过 10 次。
- **SC-006**: 每条高风险发现（F-01～F-04）都至少有一条"在旧实现上失败、在新实现上通过"的回归用例。
- **SC-007**: 两种 MQ 下启动后均可从日志一眼判定当前 MQ 类型、配置来源与关键默认值（不含敏感信息）；无法消费时不再出现"启动成功但静默不消费"。
- **SC-008**: 缺失真实中间件环境时，测试套件整体仍成功完成（跳过项显式列出），流水线不因环境缺失失败。
- **SC-009**: 全部公开配置键在 IDE 中可提示；执行 `mvn -pl leaf-base-data-sync -am test` 与构建期静态检查均无错误。
- **SC-010**: 关闭消费开关后，启动过程不产生任何 MQ 连接尝试与 MQ 基础设施 Bean（用例断言）。
- **SC-011**: 仅使用 RabbitMQ 的下游应用依赖树中，`rocketmq-client`、`nacos-client`、`fastjson` 出现次数为 0（`mvn -pl <下游> dependency:tree` 实测，记录命令与输出片段）。
- **SC-012**: 本模块 pom 中 RocketMQ 相关坐标条目为 1 条（`leaf-base-rocketmq`），且 `dependency:tree` 中不存在本模块直接声明的 `rocketmq-spring-boot-starter` / `rocketmq-client` 条目。
- **SC-013**: 配置源选择的四种组合（有/无 nacos 类路径 × 默认/显式取值）各有可执行用例，默认值下的实际生效来源与启动日志一致。

## Assumptions

- 消息中间件选择为**互斥单选**；迁移期通过部署切换，不做同一应用双订阅（双跑会引入重复消费与幂等责任，超出模块职责）。
- 两种 MQ 下 Canal 生产端投递的是**同一份 flat JSON 载荷**（差异只在 sink 适配）；因此解析与路由逻辑可完全共享。
- 业务方的 `canal.yml` 由下游应用的配置导入机制加载（外置 `config/<profile>/canal.yml`），本规格不改变其键名，只修正绑定方式使其真实生效。
- 抽象落在本模块内（适配器形态），不新建中间件抽象模块；出现第二个消费方时再按宪法 §I 抽取。
- **RocketMQ 接入依赖载体固定为 `leaf-base-rocketmq`**（用户明确要求），且以 `<optional>true</optional>` 声明：本模块编译/测试期始终可见，使用 RocketMQ 的下游需显式引入同一 artifact。这样既满足"经框架封装模块接入"，又不违反宪法 §I"模块自治与按需装配"（仅用 RabbitMQ 的下游不被拖入 `rocketmq-client` / `nacos-client` / `fastjson`）。
- RocketMQ 依赖版本沿用 `leaf-base-rocketmq` 已声明的版本，不在本模块新增版本号（宪法附加约束 §2）。
- RocketMQ 的**消费容器由本模块实现**（`leaf-base-rocketmq` 的模块手册明确不承载消费容器，且全仓库从未使用 `@RocketMQMessageListener`）；该容器的可用性（starter 2.3.6 在 Spring Boot 4.1 基线下的注册与消费行为）必须在实施期以集成测试实测，若不可用则改用该模块暴露的能力面自行装配并在文档登记。
- 幂等、跨系统编排、Canal Server 运维不在本模块职责内；框架只保证"消息有确定结局"，重复投递下的幂等由业务处理器负责。
- 顺序消费默认不做；需要严格顺序时由使用方按表维度拆分消费组，该替代方案写入文档。
- 真实中间件验证由环境变量门控，缺失即跳过；仓库内不写入任何真实地址或凭据。
- 现有 12 个单元用例作为回归基线保留，语义变化必须显式说明并同步更新。

## Out of Scope

- Canal Server / RocketMQ / RabbitMQ 的部署与运维，死信队列的运维治理平台。
- 业务幂等实现、跨系统编排、下游写入模型设计。
- 消费端批量处理与批量确认、事务消息、顺序消息、跨 MQ 双跑（登记为不支持，见 FR-038）。
- 新建独立的"消息中间件抽象"通用模块（当前无第二个消费方，属宪法附加约束 §8 禁止的投机性抽象）。
- 修改 `leaf-base-rabbitmq` / `leaf-base-rocketmq` 的公开契约与内部实现。
- 在 `leaf-base-rocketmq` 内新增消费容器、或在其中新增第二套发送/分发抽象（该模块 AGENTS 明确禁止）。
- 以 `GXCdcMessageDispatcher` / `GXCdcEventListener` 替换 Canal 的 `GXProcessCanalDataService` 扩展点（会破坏业务零改动，见 FR-046）。
