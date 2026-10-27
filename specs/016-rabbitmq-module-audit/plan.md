# Implementation Plan: leaf-base-rabbitmq 正确性、线程安全与性能审查及修复

**Branch**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-rabbitmq-module-audit/spec.md`

## Summary

交付分两层：**审查结论**（已在 `spec.md` 固化 22 条证据、22 条发现、7 条已确认正确行为）与**修复 + 功能完备性测试**（用户硬性要求）。修复严格限定在 `leaf-base-rabbitmq` 单模块，公开方法签名与配置键不改，行为修正全部写入迁移说明。

技术路线围绕四处结构性改造：

1. **配置装配真实化**：用显式开关 `maple.framework.rabbitmq.config-source`（默认 `nacos`，保持现状）替换恒假的 `@ConditionalOnMissingClass`；Nacos 侧不再依赖不存在的注解处理器，改由 `EnvironmentPostProcessor` 在启动早期用 `NacosFactory.createConfigService` 拉取 `rabbit.yml` 并注入低优先级 `PropertySource`（未配置 server-addr 时零网络开销）；本地侧同时支持 `classpath:` 与外置 `file:./config/<profile>/rabbit.yml`；启动输出一条可检索的实际来源日志。
2. **资源声明 truthful 化**：删除"永久为真"的 `queueCache` 与全局 `ReentrantLock`，改为按队列名单飞（`ConcurrentHashMap<String, CompletableFuture<Queue>>`）+ 每次真实声明；`queueExists` 一律查询 Broker；`deleteQueue` 先删 Broker 再清状态；`createTemporaryQueue` 改为非排他 + 自动删除并登记"排他队列不支持"。
3. **发送/消费语义统一**：发送前用 `MessagePropertiesBuilder.fromClonedProperties` 做属性快照；traceId 复用 MDC、仅在缺失时生成；参数校验抛 `GXBusinessException`；资源/通道操作失败抛 `GXRabbitMQOperationException`（`null`/`false` 只保留给"不存在"这类正常结果）；消费端不可解析消息 `AmqpRejectAndDontRequeueException`，其余异常原样抛出交给容器重试。
4. **生命周期与开销治理**：调度器改为 `ThreadPoolTaskScheduler` + `setVirtualThreads(true)`（JDK 21）+ 合理 poolSize + 显式关闭，去掉"每次触发新建执行器并阻塞 `.get()`"；异步执行器修正拒绝策略、补 `@PreDestroy`、声明 JDK 队列语义；日志全量 ASCII 化、成功路径 info 降为 debug、删除死代码与未使用依赖。

测试是本计划的验收核心：模块从 0 用例提升到 ≥60 用例，覆盖 13 个公开方法、4 个装配/配置/监听类型、16×100 并发、32 线程同队列声明、Spring 上下文装配，并以环境变量门控的真实 Broker 集成用例补端到端证据。

## Technical Context

**Language/Version**: Java 21（`maven.compiler.release=21`）；构建启用 ErrorProne 2.50.0 + NullAway 0.13.4（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`）

**Primary Dependencies**: `spring-rabbit`/`spring-amqp 4.1.1`、`amqp-client 5.30.0`、`spring-boot 4.1.1`（`spring-boot-autoconfigure`、`spring-boot-amqp`）、`hutool 5.8.47`、`jspecify 1.0.1`、`micrometer-core 1.17.1`（框架传递，仅用于可选计数器）、`nacos-client 3.1.1`（经 `leaf-base-nacos`，compile）、`leaf-base-framework`；**移除** `leaf-base-retry`（零引用）

**Storage**: N/A（消息与资源状态全部在外部 Broker；模块不引入本地持久化）

**Testing**: JUnit 5（Jupiter）+ `spring-boot-starter-test 4.1.1` + `spring-rabbit-test 4.1.1` + Mockito 5.x + AssertJ；条件装配用 `ApplicationContextRunner`；装配/回调/监听用 `@SpringBootTest`（`classes = GXRabbitMQConfig.class` + 最小 `@SpringBootApplication` 测试配置）；真实 Broker 用 `@Tag("integration")` + `@EnabledIfEnvironmentVariable`

**Target Platform**: JVM 库（Spring Boot 4.1.x 应用以 Maven 依赖引入）；下游需 `@GXEnableLeafFramework` 或等价 `cn.maple` 包扫描

**Project Type**: library（多模块 Maven 工程中的 `leaf-base-rabbitmq` 单模块）

**Performance Goals**: 结构口径而非绝对吞吐——单条消息固定开销项从 6 项降至 ≤4 项（Hutool 序列化保留、UUID 仅在无 traceId 时生成、Environment 查询消除、`getBean` 从每消息降至装配期、成功路径 info 日志 0 条、RTT 仍为 1 次）；资源声明路径无全局锁、并发不同队列可并行；异步调度并发度 > 1（虚拟线程调度器）；同队列并发声明由单飞收敛为 1 次 Broker 声明

**Constraints**: 只改 `leaf-base-rabbitmq`；不改 `GXSendRabbitMQService` 方法签名与配置键命名；不新增运行时依赖（测试依赖除外）；不引入 Testcontainers；ErrorProne/NullAway 零告警；真实用例默认跳过；凭据只从环境变量读取

**Scale/Scope**: 12 个生产类型（改动约 10 个）+ 新增 3 个类型（`GXRabbitMQOperationException`、`GXRabbitMQEnvironmentPostProcessor`、`GXRabbitMQListenerStatistics`）+ 10 个测试类；目标用例数 ≥60

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
| --- | --- | --- |
| I 模块自治与按需装配 | PASS | 改动全部落在 `leaf-base-rabbitmq`；不新增模块、不反向依赖；Nacos 能力用 `ObjectProvider`/类存在性判断降级，缺失时仅告警不阻断启动 |
| II 稳定契约优先 **NON-NEGOTIABLE** | PASS（含 2 项显式登记的语义修正） | 方法签名、配置键、异常类型、`X-Trace-Id`/`X-Timestamp`/`X-Source-Service`/`messageId` 头名不变；语义修正仅两处：① 创建/通道类方法失败由 `null`/`false` 改为抛 `GXRabbitMQOperationException`（"不存在"类结果仍用 `false`）；② `createTemporaryQueue` 由排他改为非排他自动删除。两者均在 `contracts/`、`AGENTS.md`、手册写清迁移路径，属宪法允许的"同步补充兼容说明 + 迁移路径 + 回归测试" |
| III 统一响应与统一异常 | PASS | `GXRabbitMQOperationException extends GXBusinessException`，携带操作类型、资源名与 `Dict` 明细；参数校验复用 `GXBusinessException`；不再以 `null` 表达失败 |
| IV 显式配置与双配置源 | PASS（按宪法"显式开关"四护栏落地） | `maple.framework.rabbitmq.config-source` 默认值保持既有生效行为（nacos）；启动 INFO 打印实际来源与关键默认值（不打印密钥）；补互斥装配与默认值测试；新键登记 `additional-spring-configuration-metadata.json` |
| V 空值安全与上下文纪律 | PASS | 修改的 API 补 `@Nullable`/非空契约；`MessageProperties` 快照避免跨线程共享可变对象；异步任务用 `TaskDecorator` 传递 MDC；调度器/执行器均有 `@PreDestroy` |
| VI 测试与静态检查门槛 | PASS | 每个缺陷带"旧代码失败、新代码通过"回归用例；真实 Broker 用例缺环境自动跳过；`mvn -o -B -pl leaf-base-rabbitmq test` + ErrorProne/NullAway 零错误为完成门槛 |
| VII 命名与包结构一致性 | PASS | 新类型沿用 `GX` 前缀；异常入 `exception` 包，环境后处理器入 `properties` 包，计数器入 `listener/impl`；日志全 ASCII；移除 `@SuppressWarnings("all")` |

**Gate 结论**：无违反项，无需 Complexity Tracking 豁免。

**Phase 1 复查结果（2026-10-25）**：核对 `research.md` / `data-model.md` / `contracts/*` / `quickstart.md` 后确认——新增类型 3 个均为 `GX` 前缀且落在既有分层；新增配置键 2 个（`maple.framework.rabbitmq.config-source`、`maple.framework.rabbitmq.scheduler.expose`），均有默认值且不改变既有部署行为；未新增运行时依赖（仅 test 作用域）；未改公开方法签名；未触及其他模块。**7 条门禁保持 PASS。**

## Project Structure

### Documentation (this feature)

```text
specs/016-rabbitmq-module-audit/
├── plan.md              # 本文件
├── spec.md              # 规格（22 证据 / 22 发现 / 33 需求）
├── research.md          # Phase 0：14 条技术决策与备选方案
├── data-model.md        # Phase 1：声明/快照/配置源/监听结果/生命周期模型
├── quickstart.md        # Phase 1：分层验证与性能复现命令
├── contracts/
│   ├── send-contract.md              # 13 个公开方法的返回/异常/空值契约
│   ├── queue-declaration-contract.md # 单飞声明、缓存移除、锁与失败补偿
│   ├── config-source-contract.md     # config-source 开关、Nacos 拉取、日志与优先级
│   ├── listener-contract.md          # 消费失败分层处置、计数、启停
│   └── lifecycle-contract.md         # 调度器/执行器所有权、关闭、Bean 暴露开关
├── checklists/
│   └── requirements.md  # 规格质量清单（已通过）
└── review-report.md     # 实施阶段产出：独立审查报告（本命令不创建）
```

### Source Code (repository root)

```text
leaf-base-rabbitmq/
├── pom.xml                                       # + test 依赖；- leaf-base-retry
├── src/main/java/cn/maple/rabbitmq/
│   ├── config/GXRabbitMQConfig.java              # 转换器/回调装配、调度器重写、生命周期
│   ├── dto/inner/GXRabbitMQMessageReqDto.java    # 空值标注、tag 语义收敛、快照入口
│   ├── exception/GXRabbitMQOperationException.java   # 新增
│   ├── listener/GXRabbitMQQueueListener.java     # 契约 Javadoc（不再吞异常）
│   ├── listener/impl/GXDefaultRabbitMQQueueListenerImpl.java  # 拒绝策略、计数、删死代码
│   ├── listener/impl/GXRabbitMQListenerStatistics.java        # 新增：可选 Micrometer 计数
│   ├── properties/GXRabbitMQProperties.java      # 空值/默认值、Javadoc、metadata 对齐
│   ├── properties/local/GXLocalRabbitMQProperties.java    # 显式开关 + 双路径 PropertySource
│   ├── properties/nacos/GXNacosRabbitMQProperties.java    # 去掉无效注解，改为绑定 + 来源校验
│   ├── properties/GXRabbitMQEnvironmentPostProcessor.java # 新增：早期拉取 rabbit.yml
│   └── service/{,impl/}GXSendRabbitMQService*.java        # 单飞声明、快照、统一失败、锁移除
├── src/main/resources/META-INF/
│   ├── additional-spring-configuration-metadata.json      # 新增
│   └── spring.factories                                   # 新增：注册 EnvironmentPostProcessor
└── src/test/java/cn/maple/rabbitmq/
    ├── config/GXRabbitMQConfigSpringBootTest.java
    ├── properties/GXRabbitMQConfigSourceTest.java
    ├── properties/GXRabbitMQEnvironmentPostProcessorTest.java
    ├── listener/GXDefaultRabbitMQQueueListenerTest.java
    ├── dto/GXRabbitMQMessageReqDtoTest.java
    ├── service/impl/GXSendRabbitMQServiceImplTest.java
    ├── service/impl/GXSendRabbitMQServiceImplConcurrencyTest.java
    ├── service/impl/GXQueueDeclarationConcurrencyTest.java
    ├── service/impl/GXSendRabbitMQServiceImplLifecycleTest.java
    └── integration/GXRabbitMQRealBrokerIT.java
```

**Structure Decision**: 沿用现有 `config` / `service` / `properties` / `listener` / `dto` 分层，仅新增 `exception` 包、1 个 `properties` 级后处理器、1 个监听器计数器与 `src/main/resources/META-INF`；不新增子模块、不引入新抽象层，符合宪法附加约束 §8（不为已有能力再包一层）。

## Phase 0 研究结论（详见 research.md）

| 编号 | 决策 | 关键理由 |
| --- | --- | --- |
| D-01 | 用 `maple.framework.rabbitmq.config-source`（`local`/`nacos`，默认 `nacos`）显式互斥，替换恒假的 `@ConditionalOnMissingClass` | `nacos-client 3.1.1` 确实含注解类（`jar tf` 已验证），类路径条件在 compile 依赖下永远无法表达"无 Nacos" |
| D-02 | 删除 `queueCache` + 全局锁，改按队列名单飞 `ConcurrentHashMap<String, CompletableFuture<Queue>>` | 永久缓存无法感知 Broker 删除；单飞既保证同队列只声明 1 次，又让不同队列并行，且不需要永久状态 |
| D-03 | `queueExists` 一律 `rabbitAdmin.getQueueProperties` 查询；`deleteQueue` 先删 Broker 再清理状态 | 缓存短路是"谎报存在"的直接根因；顺序错误会造成状态反转 |
| D-04 | `createTemporaryQueue` 改为非排他 + `autoDelete`，排他队列登记为不支持 | 排他队列绑定 Admin 连接，连接回收即删除且其他连接不可消费，原语义不可实现 |
| D-05 | 发送前用 `MessagePropertiesBuilder.fromClonedProperties` 快照，再在快照上写追踪头 | 4.1.1 无 `MessageProperties` 拷贝构造器（`javap` 验证），`fromClonedProperties` 是官方深拷贝入口 |
| D-06 | traceId 取 `getNullableTraceId()`，为空才 `generateTraceId()`；`spring.application.name` 装配期注入缓存 | 现实现每消息新建 UUID，链路必然断开；每消息 Environment 查询属无效开销 |
| D-07 | 创建/通道类操作失败抛 `GXRabbitMQOperationException`；"不存在"仍返回 `false` | 统一失败风格，同时保留可判定的正常结果；迁移路径写入契约与手册 |
| D-08 | 出站继续使用 Hutool（`Date`/`LocalDateTime` 毫秒时间戳，wire format 不变）；模板用共享 `JsonMapper` 承担入站类型化转换；写 `__TypeId__=cn.hutool.core.lang.Dict` 并将 `cn.hutool.core.lang` 加入信任包 | 实测 Hutool 与 Jackson 3 的时间类型输出不同，改栈会破坏既有消息格式（宪法 II）；"单栈"收敛为"单一出站序列化器 + 入站兼容性用例"（实施期修订，见 spec Clarifications） |
| D-09 | 调度的虚拟线程分支改用 `ThreadPoolTaskScheduler#setVirtualThreads(true)`，poolSize=`max(2, CPU)`，显式 `destroy()` | 现实现每次触发新建执行器并在调度线程阻塞 `.get()`，实际并发度为 1；`setVirtualThreads` 是 Spring 7 官方能力 |
| D-10 | `rabbitTaskScheduler` 保持 Bean 暴露（兼容），新增 `maple.framework.rabbitmq.scheduler.expose`（默认 `true`）允许关闭，关闭时由 `GXRabbitMQConfig` 内部持有并 `@PreDestroy` 关闭 | 该 Bean 会顶掉 Boot 默认 `taskScheduler`；既要保持兼容，又要给下游退路 |
| D-11 | 异步执行器保留 `ThreadPoolExecutor`，拒绝策略改 `AbortPolicy` + 失败 Future，`@PreDestroy` 优雅关闭，文档声明 `max-size` 仅在队列满时生效（`queue-capacity=0` 时用 `SynchronousQueue` 让 max 立刻生效） | 保持有界并发与背压；`CallerRunsPolicy` 会把 Broker IO 反压到调用线程 |
| D-12 | 消费端：空消息/不可解析 → `AmqpRejectAndDontRequeueException`；业务异常原样抛出；计数走可选 `MeterRegistry` | 毒消息重试无意义；业务失败需要容器重试；计数不新增依赖 |
| D-13 | Nacos 拉取用 `NacosFactory.createConfigService` + `getConfig(dataId, group, timeout)`，仅在显式配置了 server-addr 时执行，超时 2s，失败只 warn；`PropertySource` 以最低优先级加入 | 注解处理器不存在；低优先级保证"本地显式配置优先"，不改变现有部署行为 |
| D-14 | 删除死代码与 `@SuppressWarnings("all")`，移除 `leaf-base-retry`；`GXRecoveryCallback` 保留接口并在 Javadoc 说明需调用方提供 `RetryTemplate` | 未使用依赖与死路径违反 YAGNI；接口删除属破坏性变更，保留并说明 |

## Phase 1 设计产物

- [research.md](./research.md)：14 条决策（决策 / 理由 / 备选方案 / 影响面 / 验证方式 / 风险）。
- [data-model.md](./data-model.md)：声明请求与指纹、单飞条目、消息快照、配置源、监听结果、生命周期与统计模型。
- [contracts/send-contract.md](./contracts/send-contract.md)：13 个公开方法的返回值、异常、空值与追踪/序列化契约。
- [contracts/queue-declaration-contract.md](./contracts/queue-declaration-contract.md)：单飞声明、缓存移除、锁与失败补偿、临时队列语义。
- [contracts/config-source-contract.md](./contracts/config-source-contract.md)：开关矩阵、Nacos 拉取与优先级、日志、metadata、校验。
- [contracts/listener-contract.md](./contracts/listener-contract.md)：失败分层、ack 语义、计数、启停条件。
- [contracts/lifecycle-contract.md](./contracts/lifecycle-contract.md)：调度器/执行器所有权、关闭顺序、Bean 暴露开关。
- [quickstart.md](./quickstart.md)：从基线复现到全量测试、并发、集成、性能与日志扫描的执行手册。

## 契约变更与迁移（必须写入 AGENTS.md 与手册）

| 变更 | 旧行为 | 新行为 | 迁移方式 | 兼容性 |
| --- | --- | --- | --- | --- |
| `createQueue` / `createDurableQueue` / `createTemporaryQueue` / `setupMessageChannel` / `setupDurableMessageChannel` 失败 | 返回 `null`/`false`，原因仅在日志 | 抛 `GXRabbitMQOperationException`（含操作、资源名、`Dict` 明细） | 删除 `null`/`false` 判断，改为捕获业务异常；需要"尽力而为"的调用方显式 `try-catch` 并记录 | 签名不变，语义变更已登记 |
| `setupMessageChannelAsync` 失败 | `CompletableFuture` 以 `false` 正常完成 | 以 `failedFuture(GXRabbitMQOperationException)` 完成 | `thenCompose`/`exceptionally` 处理失败 | 签名不变 |
| `createTemporaryQueue` | 声明排他队列（其他连接不可用） | 声明非排他 + `autoDelete=true` 队列 | 需要严格排他的场景请自行持有连接并用 `QueueBuilder` 声明；模块登记"排他队列不支持" | 行为修正 |
| `GXRabbitMQMessageReqDto.tag` | 写入 `consumerTag`（消费侧属性） | 不再写 `consumerTag`；字段保留但标记"发送侧忽略" | 需要设置关联标识请用 `correlationData` 或 header | 字段保留 |
| `leaf-base-retry` 依赖 | 声明但零引用 | 移除 | 下游若依赖该传递依赖，请自行显式引入 | 传递依赖收窄 |
| 新增 `maple.framework.rabbitmq.config-source` | 无 | `local`/`nacos`，默认 `nacos`（保持当前实际生效变体） | 无需改动；需要本地 yml 时显式设为 `local` | 向后兼容 |
| 新增 `maple.framework.rabbitmq.scheduler.expose` | 无（恒暴露 `TaskScheduler`） | 默认 `true` 保持暴露；`false` 时内部持有 | 想保住 Boot 默认调度器的下游设为 `false` | 向后兼容 |

## 实施顺序（供 /speckit.tasks 使用）

1. **测试骨架**：模块 `pom.xml` 增加 `spring-boot-starter-test`、`spring-rabbit-test`、`junit-jupiter`（test 作用域）；建立 10 个测试类骨架并记录基线（当前 0 用例）。
2. **失败用例先行（F-01～F-05）**：队列声明/缓存、临时队列、traceId、属性改写、消费吞异常五组回归，先跑出失败输出并归档。
3. **配置装配（F-01、FR-019）**：`config-source` 开关 + 环境后处理器 + 本地双路径 + 启动日志 + metadata；用 `ApplicationContextRunner` 覆盖三态。
4. **资源声明（F-02/F-03/F-08/F-09）**：移除永久缓存与全局锁，引入单飞；`queueExists`/`deleteQueue` 顺序修正；通道创建失败补偿与错误定位。
5. **发送链路（F-05/F-06/F-07/F-12/F-17/F-18）**：属性快照、traceId 复用、失败统一、序列化单栈、日志 ASCII 与级别、成功日志后移。
6. **消费链路（F-04、FR-008）**：拒绝策略 + 计数 + 死代码清理 + `@ConditionalOnProperty` 启停。
7. **生命周期（F-10/F-11/F-13）**：调度器重写与关闭、执行器拒绝策略与关闭、Bean 暴露开关。
8. **文档与能力边界（F-16、F-22、FR-032/FR-033）**：更新 `AGENTS.md`、开发手册、能力不支持清单。
9. **验证收口**：全量 3 次、并发、集成（有环境）、性能口径、ASCII 扫描，产出 `review-report.md` 与用例统计。

## Risks & Mitigations

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 失败语义由 `false` 改抛异常，下游未更新 | 运行时行为变化 | 兼容表 + 手册 + 提交说明；`setupMessageChannel` 内部仍返回 `boolean`（对外契约不变），仅底层资源方法抛异常 |
| Nacos 启动期拉取增加启动耗时 | 启动变慢 | 仅在配置了 server-addr 时执行；超时 2s；失败只 warn；可用 `config-source=local` 完全关闭 |
| 序列化换栈导致日期格式差异 | 消费端解析失败 | 按 Hutool 现有格式配置 `JsonMapper`，并用"同一载荷双栈输出对比"回归锁定 |
| 调度器行为变化影响 `@Scheduled` | 全局调度变化 | 默认保持 Bean 暴露；poolSize 提升且虚拟线程化；提供 `scheduler.expose=false` |
| 无真实 Broker 环境 | 端到端证据缺失 | 集成用例环境变量门控 + 报告中登记未验证项与手工命令 |

## Complexity Tracking

无宪法违反项，无需豁免记录。新增类型均为替换现有错误实现（恒假条件、永久缓存、阻塞式虚拟线程包装、无效注解），不构成投机性抽象。
