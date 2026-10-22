# Phase 0 研究：leaf-base-rocketmq 审查后的技术决策

**Feature**: `006-rocketmq-module-audit` | **Date**: 2026-10-22

本文件只回答"怎么改"的关键岔路，不重复 spec 的发现描述（F-xx / E-xx 见 [spec.md](./spec.md)）。

## D-01 配置来源三态装配

**Decision**: 用 `AnyNestedCondition`（`local` / `nacos` / 未配置三支）替代 `GXLocalRocketMQConfigProperties` 现有的 `@ConditionalOnMissingClass` 单条件：

- `local` 支：`@ConditionalOnProperty(name = "rocketmq.config-source", havingValue = "local")`；
- `nacos` 支：`@ConditionalOnProperty(name = "rocketmq.config-source", havingValue = "nacos")`；
- 未配置支：`rocketmq.config-source` 缺失 **且** `com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties` 不在类路径（即保持今天"无 Nacos 才用本地"的判定），并打 warn 说明"未显式指定，按类路径自动判定"。

**Rationale**: 现状根因是判定维度错了——用"类路径是否含 Nacos 注解类"决定本地源，而本模块硬依赖 `leaf-base-nacos`，`nacos-client 3.1.1` 确实携带该注解类，条件恒假。把显式键作为第一维度、类路径仅作为缺省回退，既让 `rocketmq.config-source` 生效（FR-017），又不改变未配置部署的既有行为（宪法 II）。

**Alternatives considered**:

- 仅把 `@ConditionalOnMissingClass` 换成 `@ConditionalOnProperty`：会让"未配置"的下游从"有配置类"变成"没有配置类"，属于隐性行为变更。
- 用 `@ConditionalOnExpression` 拼 SpEL：可读性差、NullAway 与 IDE 提示都不友好。
- 两个类都保留为 `@Component` 由 `@Primary` 选一个：会同时加载两套配置源，违背互斥装配语义。

**Impact**: `properties/local`、`properties/nacos` 两个类 + 新增条件类；测试需覆盖三态。
**Verification**: `GXRocketMQConfigPropertiesTest` 三态各一例；当前失败的 `loadsLocalConfigSource` 转为通过。

## D-02 本地 yml 的占位符与默认 profile

**Decision**: 保持 `@PropertySource` + `GXYamlPropertySourceFactory`，但把路径改为 `classpath:/${spring.profiles.active:default}/rocket-mq.yml`，并保留 `ignoreResourceNotFound = true`。

**Rationale**: 现写法 `/${spring.profiles.active}/` 在未设置 active profile 时占位符不可解析（`@PropertySource` 走 `resolveRequiredPlaceholders`），一旦本地源被激活就会启动失败。补默认值后与手册第 37 行描述一致，且 `ignoreResourceNotFound` 保证默认 profile 缺文件时不炸。

**Alternatives considered**:

- 改用 `spring.config.import=optional:classpath:...`：需要下游配置配合，模块无法自注入；若要自动化必须引入 `EnvironmentPostProcessor`，复杂度不成比例。
- 直接用 `application-<profile>.yml`：会让 RocketMQ 配置散落到主配置，与"独立 `rocket-mq.yml`"的既有契约冲突。

**Impact**: 一行注解改动 + 文档同步。
**Verification**: 不设 `spring.profiles.active` 时启动上下文不抛占位符异常；设置 `dev` 时能读到 `config/dev/rocket-mq.yml` 的 marker。

## D-03 Nacos 分支的落地方式

**Decision**: `nacos` 分支不再依赖 `@NacosConfigurationProperties` 注解生效，改为"模块只负责开关与校验，配置值由下游 `spring.config.import=nacos:<dataId>` 加载"；当 `config-source=nacos` 且 `rocketmq.name-server` 为空时，启动期输出 error 并 fail-fast。

**Rationale**: 依赖树中不存在 `nacos-spring-boot`（`nacos-spring-context`）处理器，注解是惰性的；而 `spring-cloud-starter-alibaba-nacos-config 2025.1.0.0` 已包含 `NacosConfigDataLocationResolver`，是当前版本官方支持的加载方式。引入 `nacos-spring-boot-starter` 与 Boot 4.1 的兼容性未经验证，且会重复引入一套配置体系。

**Alternatives considered**:

- 引入 `nacos-spring-boot-starter`：版本与 Boot 4.1 不匹配风险高、体积增加、与 spring-cloud-alibaba 的 ConfigData 机制重叠。
- 删除 nacos 分支：违背 `AGENTS.md` 与手册承诺的双配置源能力。
- 保留注解并"声明无效"：等于保留死代码。

**Impact**: `GXNacosRocketMQConfigProperties` 退化为"开关 + 校验 + 日志"角色；需在手册中写清 Nacos 侧的前置配置。
**Verification**: `config-source=nacos` 且 `name-server` 缺失 → 启动失败且错误信息可读；`name-server` 存在 → 正常启动并输出 nacos 来源日志。

## D-04 异步发送失败扩展点

**Decision**: 新增函数式接口 `GXRocketMQAsyncSendFailureHandler#onFailure(GXRocketMQAsyncSendFailureContext context)`，实现类以 `ObjectProvider` 注入；无实现时只记 error 日志。`GXRocketMQAsyncSendFailureContext` 为不可变 `record`，字段：`topic`、`tag`、`messageKey`、`msgId`（可空）、`sendStatus`（可空）、`cause`（可空）。回调内部 try-catch 包裹，业务实现抛异常只记 error，不影响生产者回调线程。

**Rationale**: FR-020 要求"可补偿但不动契约"。`ObjectProvider` 避免扩展点成为强依赖；record 保证跨线程传递的是快照（与发送路径的不可变快照风格一致）。回调线程属于生产者共享池，必须隔离业务异常，否则一个坏实现会污染其他消息的回调（宪法 V"异步执行必须可控"）。

**Alternatives considered**:

- 让业务传入 `SendCallback`：与现有"业务方无法提供回调"的文档契约冲突，且会暴露 RocketMQ 原生类型。
- `sendAsync` 返回 `CompletableFuture`：破坏性变更，需主版本。
- 发布 Spring 事件：与模块现有风格不一致（本模块无事件发布体系），且难以区分同步/异步失败。

**Impact**: 新增 1 个接口 + 1 个 record + 1 个注入点；`GXSendRocketMQServiceImpl` 构造器参数变化（Spring 注入，不影响调用方）。
**Verification**: 无实现时失败只打日志；有实现时在错误 secret 场景能收到 `sendStatus=null`、`cause` 非空的上下文。

## D-05 CDC 分层失败处置

**Decision**:

- 解析失败 / payload 为空 / op 未知：`log.warn`（含 `bizType`、失败分类、原始消息长度，不打印完整 body）+ `GXCdcDispatchStatistics` 的 `LongAdder` 计数 + 若配置了 `maple.framework.cdc.dead-letter-topic` 则转发后返回，否则直接返回。
- 监听器异常：逐个 `try-catch`，保证遍历不中断；全部执行完后若有异常，抛 `GXCdcDispatchException extends GXBusinessException`，把各监听器异常 `addSuppressed`，消息体中保留事件 `bizType`/`op`/`tsMs` 便于排障。

**Rationale**: 毒消息重试不可能成功，抛给消费方只会阻塞队列；监听器异常代表业务未成功，必须让消费方重试（至少一次），但不能因第一个监听器失败导致其余监听器永远收不到（F-08）。

**Alternatives considered**:

- 全部外抛：畸形消息无限重试，毒化队列。
- 全部吞掉：可靠性最低，等于"尽力投递"。
- 每个监听器独立线程：引入并发模型与顺序语义变化，超出审查范围。

**Impact**: `dispatcher/impl` 改动 + 新增 `exception`、统计类；`GXCdcMessageDispatcher#dispatch` 签名不变（异常从"可能抛"变为"汇总抛"，语义兼容）。
**Verification**: 毒消息 → 返回正常且计数 +1；两监听器前者抛错 → 后者仍执行，最终抛 `GXCdcDispatchException` 且 `getSuppressed()` 含原始异常。

## D-06 指标暴露方式

**Decision**: `GXCdcDispatchStatistics` 内部用 `LongAdder` 计数（成功、解析失败、未知 op、监听器失败、死信转发），并通过 `ObjectProvider<MeterRegistry>` 在存在时注册为 Counter/Gauge，不存在时仅保留本地计数与日志。

**Rationale**: `micrometer-metrics` 已由 `leaf-base-framework` 经 `actuator` 传递引入，用可选注入即可，无需新增依赖（宪法"能用现有封装替代就不引入新库"）。`LongAdder` 在高并发消费下优于 `AtomicLong`。

**Alternatives considered**:

- 直接依赖 `MeterRegistry`：不是所有下游都开 actuator，会造成启动失败。
- 引入 `spring-boot-starter-actuator`：本模块不应决定下游是否启用监控。

**Impact**: 新增 1 个统计类；`dispatcher` 构造参数增加一个可选提供者。
**Verification**: 无 `MeterRegistry` 环境下启动正常；有 `MeterRegistry` 时指标名可查（`rocketmq.cdc.dispatch.*`）。

## D-07 自动装配注册

**Decision**: 不新增 `AutoConfiguration.imports`；改为在 `GXSendRocketMQServiceImpl` / `GXCdcMessageDispatcherImpl` 缺失装配的场景不可自动感知的前提下，于模块文档与手册中明确"必须启用 `@GXEnableLeafFramework` 或扫描 `cn.maple.rocketmq`"，并在本地配置源装配日志中附带一行扫描提示。

**Rationale**: 自动装配会改变下游现有扫描语义（可能造成重复注册或提前初始化），属于宪法 II 的契约变更；本模块 4 个组件类目前依赖下游显式扫描，这是既定形态。审查阶段只要求"缺少装配可观测"（F-18），用文档 + 日志满足，成本最低。

**Alternatives considered**:

- 新增 `AutoConfiguration.imports` 注册 4 个类：需要处理与下游重复扫描的冲突，且会让 `rocketmq-spring-boot-starter` 的装配顺序变得不确定。
- 用 `@Import` 从框架配置强制引入：跨模块改动，超出"只改本模块"的约束。

**Impact**: 文档 + 日志；无代码结构性改动。
**Verification**: 未启用扫描的应用启动后无本模块 Bean，且手册已说明；启用后 4 个 Bean 齐备。

## D-08 发送路径日志与校验开销

**Decision**:

- 成功路径 5 处 `log.info` 统一降为 `log.debug`，失败路径保持 `error`；`sendAsync` 的成功回调降为 `debug`。
- 校验补齐为"不触达 Broker 的前置拒绝"：`topic` 匹配 `^[%a-zA-Z0-9_-]{1,127}$`、`tag` 长度 ≤128 且不含 `:`、`messageKey` ≤255、`body` UTF-8 字节数 ≤ `rocketmq.producer.max-message-size`（默认 4MB）。
- `GXRocketMQMessageReqDto` 的 `Object` 构造器对 `null` 直接抛 `GXBusinessException`，不再产出字面量 `"null"`；`(String, Object)` 与 `(Object)` 的歧义在 javadoc 中明确，不新增构造器（避免 API 面扩大）。

**Rationale**: FR-007 的验收口径是"成功路径 info 日志 0 条"，把日志降级比引入异步日志或采样更简单可靠；校验前置可避免无效 RTT，也让错误信息比 Broker 抛错更可读。构造器歧义属文档级修复，改重载集合会破坏既有调用方编译。

**Alternatives considered**:

- 保留 info 并改为采样输出：需要引入采样器，收益与复杂度不成比例。
- 通过 `@Valid` + Bean Validation：模块已排除 `starter-validation` 依赖，补齐依赖属于新增第三方依赖，且 DTO 是内部传输对象。
- 拆分 `GXRocketMQMessageReqDto` 构造器：破坏性变更。

**Impact**: `service/impl` 与 `dto/inner` 改动；需同步手册第 271/306 行等描述。
**Verification**: 结构指标复核（每消息往返 1 次、info 0 条）；非法 topic / 超长 tag / `null` body 均在前置校验抛出且无 Broker 交互。
