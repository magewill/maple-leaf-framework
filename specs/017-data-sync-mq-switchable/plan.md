# Implementation Plan: leaf-base-data-sync 正确性 / 线程安全 / 性能审查与消息中间件可切换改造

**Branch**: `017-data-sync-mq-switchable` | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-data-sync-mq-switchable/spec.md`

## Summary

在不改变 `GXProcessCanalDataService` 扩展点、`database_table_Service` 命名约定与 `canal.*` 既有配置键的前提下，把当前"解析即成功、失败靠异常、只有 RabbitMQ"的实现改造为：

1. **消费结局显式化**：每条消息落入 成功 / 跳过 / 可重试失败 / 不可重试失败 四类之一，据此决定 ack、有界重投、死信落库，杜绝 6 类失败被静默 ack；
2. **热路径去容器查找**：启动期把 `GXProcessCanalDataService` 集合构建为不可变路由表，配合有界缓存（含负结果）与单次 JSON 解析，消除 `getBean` + 异常驱动的控制流；
3. **消息中间件可切换**：以 `canal.mq.type=rabbitmq|rocketmq`（默认 `rabbitmq`）在编译期/装配期互斥选择消费侧；RocketMQ 一律经 `leaf-base-rocketmq`（optional 依赖）接入，解析、路由、结局分类与计数逻辑两套 MQ 共享，业务零改动；
4. **配置与可观测性收敛**：`canal.*` 以 Spring Boot 标准 `@ConfigurationProperties` 为唯一绑定入口，配置源改为显式 `canal.config-source` 开关（默认等价旧行为）并打印实际来源；新增计数以 `LongAdder` + 可选 `MeterRegistry` 暴露；消费链路补齐 TraceId/MDC。

## Technical Context

**Language/Version**: Java 21（`<java.version>21`，根 POM）；构建期启用 ErrorProne + NullAway（`AnnotatedPackages=cn.maple`、JSpecifyMode）

**Primary Dependencies**:
- `leaf-base-framework`（compile，既有）；`leaf-base-rabbitmq`（compile，既有，传递带入 `spring-boot-starter-amqp`、`leaf-base-nacos`）
- `leaf-base-rocketmq`（**新增，`<optional>true</optional>`**；传递带入 `rocketmq-spring-boot-starter 2.3.6`、`nacos-client`、`fastjson` 1.x）
- `cn.hutool:hutool-bom 5.8.47`（既有）；`org.jspecify`（既有）
- 可观测：`io.micrometer:micrometer-core`（**不新增直接依赖**，仅以 `ObjectProvider<MeterRegistry>` 可选使用，来自根 POM `spring-boot-starter-actuator` 的既有管理）

**Storage**: N/A（本模块不持久化；死信依赖 broker 侧队列/topic，失败落库不引入新存储）

**Testing**: JUnit 5 + `spring-boot-starter-test`；装配类用 `ApplicationContextRunner`，运行期行为用 `@SpringBootTest`；真实中间件场景由环境变量门控（`RABBITMQ_*`、`ROCKETMQ_NAME_SERVER`），缺失时 `assumeTrue` 跳过

**Target Platform**: JVM 21 服务端库（Maven 多模块框架中的 `leaf-base-*` 之一，被下游应用以依赖方式引入）

**Project Type**: library（单模块，无 Web 层、无 CLI）

**Performance Goals**: 热路径 0 次 Spring 容器查找、0 次异常构造；处理器未命中场景框架侧单条消息耗时相对改造前下降 ≥ 80%（10 万条本地微基准）；32 线程 × 1000 条并发无串扰

**Constraints**:
- 只改 `leaf-base-data-sync`；不得修改 `leaf-base-rabbitmq` / `leaf-base-rocketmq` 的公开契约与内部实现
- 日志 ASCII；异常消息可中文；面向用户的异常沿用 `GXBusinessException` 家族
- 不新增第三方依赖；RocketMQ 版本不在本模块声明
- `mvn -pl leaf-base-data-sync -am test` 与构建期静态检查必须通过

**Scale/Scope**: 单实例默认 10 消费者 × prefetch 250（≈2500 在途）；表级处理器数量级 10²；库表组合数量级 10³（缓存键空间上限由此决定）；改造涉及生产类 14 个（新增约 9、修改 6），测试类预计 10–12 个

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 / 约束 | 门禁判定 | 依据与落地动作 |
| --- | --- | --- |
| §I 模块自治与按需装配 | PASS | 不新建模块（第二个消费方出现前，通用 MQ 抽象属投机抽象）；RocketMQ 依赖 `optional` + `@ConditionalOnClass`/`@ConditionalOnProperty`；RabbitMQ/RocketMQ 两套装配互斥 |
| §II 稳定契约优先（NON-NEGOTIABLE） | PASS（需回归锁定） | 保留 `canal.*` 键名与默认值、`GXCanalMessageParseService#parseMessage(String)`、`GXProcessCanalDataService` 三方法、Bean 命名约定、`defaultProcessCanalDataService` 兜底；新增键全部为增量；`GXCanalDataDto` 字段不改语义 |
| §III 统一响应与统一异常 | PASS | 非 Web 模块，不涉及 `GXResultUtils`；消费结局以枚举替代 `"success"/"error"/"warning"` 魔法值；失败以 `GXBusinessException` 家族 + 可检索日志表达 |
| §IV 显式配置与双配置源 | PASS（附四条护栏） | `canal.*` 统一 `@ConfigurationProperties`；配置源改 `canal.config-source=local|nacos`，`matchIfMissing` 默认等价旧行为；启动 INFO 打印实际来源与关键默认值；四种组合有测试；新增键登记 `additional-spring-configuration-metadata.json` |
| §V 空值安全与上下文纪律 | PASS | 新增/修改 API 标注 `@NullMarked`/`@Nullable`；消费线程 `setTraceId` + `removeTraceId` 于 `finally`；容器/线程池等资源有明确释放路径（RocketMQ 消费容器由 starter 托管，模块不另建线程池） |
| §VI 测试与静态检查门槛 | PASS | JUnit 5；每条高风险发现配"旧实现失败 / 新实现通过"回归用例；外部中间件用例可跳过；ErrorProne + NullAway 不绕过 |
| §VII 命名与包结构一致性 | PASS | 新增类型 `GX` 前缀 + 后缀表（`GXCanalOutcomeEnums`、`GXCanalConsumeStatistics`、`GXCanalRabbitMQConfig`、`GXCanalRocketMQListener`）；每包 `package-info.java`；日志 ASCII |
| 附加 §2/§3 依赖治理 | PASS（需交付说明） | RocketMQ 版本沿用 `leaf-base-rocketmq` 的 `<rocketmq-spring-boot-starter.version>`；本模块不新增版本号；传递依赖影响（nacos-client / fastjson / rocketmq-client）写入交付说明与 `research.md` D-09 |
| 附加 §4 反重复造轮子 / §8 反过度设计 | PASS（有复杂度登记） | 复用 `leaf-base-rocketmq` 连接与发送能力、死信与统计模式；不复用 `GXCdc*` 事件模型（会破坏业务零改动）；新增抽象仅为"失败处置策略"端口（两实现，满足"两处以上复用"） |
| 附加 §9 能力完备性对齐 | PASS | 不支持范围（顺序消费、事务消息、批量确认、跨 MQ 双跑、跨库 routing 过滤）写入 `AGENTS.md` + 手册 |

**结论**：无未豁免违例，Phase 0 可执行。设计完成后在本文末"Post-Design Constitution Re-check"复核。

## Project Structure

### Documentation (this feature)

```text
specs/017-data-sync-mq-switchable/
├── plan.md              # 本文件
├── spec.md              # 需求规格
├── research.md          # Phase 0：技术决策与替代方案
├── data-model.md        # Phase 1：实体、配置键、状态机
├── quickstart.md        # Phase 1：可执行验证指南
├── contracts/           # Phase 1：配置键 / 扩展点 / 失败处置 / 依赖契约
│   └── canal-consumer-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 输出（/speckit.tasks 生成）
```

### Source Code (repository root)

```text
leaf-base-data-sync/
├── pom.xml                                  # [改] 新增 leaf-base-rocketmq（optional）
└── src/
    ├── main/
    │   ├── java/cn/maple/canal/
    │   │   ├── config/
    │   │   │   ├── GXCanalConfig.java                    # [改] RabbitMQ 装配：条件化、@ConditionalOnMissingBean、DLX/DLQ
    │   │   │   ├── GXCanalRocketMQConfig.java            # [新] RocketMQ 消费侧装配（条件化）
    │   │   │   └── package-info.java
    │   │   ├── constant/
    │   │   │   ├── GXCanalConstant.java                  # [改] 新增 MQ 类型/结局/重投/死信相关常量
    │   │   │   └── package-info.java
    │   │   ├── dto/
    │   │   │   ├── GXCanalDataDto.java                   # [改] 仅补空值标注与不可变说明，字段语义不变
    │   │   │   ├── inner/GXCanalParseResultInnerDto.java # [新] 结构化解析结果（record）
    │   │   │   └── package-info.java
    │   │   ├── enums/
    │   │   │   ├── GXCanalOutcomeEnums.java              # [新] 成功/跳过/可重试失败/不可重试失败
    │   │   │   ├── GXCanalMqTypeEnums.java               # [新] rabbitmq/rocketmq
    │   │   │   └── package-info.java
    │   │   ├── listener/
    │   │   │   ├── GXCanalRabbitMQListener.java          # [改] 去 @Lazy、改用结构化结局、MDC
    │   │   │   ├── GXCanalRocketMQListener.java          # [新] RocketMQListener<String> + @RocketMQMessageListener
    │   │   │   └── package-info.java
    │   │   ├── metrics/
    │   │   │   ├── GXCanalConsumeStatistics.java         # [新] LongAdder + 可选 MeterRegistry
    │   │   │   └── package-info.java
    │   │   ├── properties/
    │   │   │   ├── GXCanalProperties.java                # [改] 新增 mqType/consumerEnabled/configSource/重投/死信键
    │   │   │   ├── local/GXLocalCanalProperties.java     # [改] 显式配置源开关 + 标准 @ConfigurationProperties 绑定
    │   │   │   ├── nacos/GXNacosCanalProperties.java     # [改] 同上（Nacos 注释路径降级为可选）
    │   │   │   └── package-info.java
    │   │   ├── service/
    │   │   │   ├── GXCanalMessageParseService.java       # [改] 新增结构化解析方法，保留 parseMessage
    │   │   │   ├── GXProcessCanalDataService.java        # [不变]
    │   │   │   ├── support/GXCanalHandlerResolver.java   # [新] 启动期路由快照 + 有界缓存
    │   │   │   ├── support/GXCanalFailureHandler.java    # [新] 失败处置端口（两 MQ 各一实现）
    │   │   │   └── impl/
    │   │   │       ├── GXCanalMessageParseServiceImpl.java        # [改] 单次解析、DDL 跳过、数组载荷、结局
    │   │   │       ├── GXDefaultProcessCanalDataServiceImpl.java  # [不变]
    │   │   │       ├── rabbit/GXCanalRabbitMQFailureHandler.java  # [新] 有界重投 + 死信队列入队
    │   │   │       └── rocketmq/GXCanalRocketMQFailureHandler.java# [新] RECONSUME_LATER / 死信 topic
    │   │   └── ...
    │   └── resources/META-INF/
    │       └── additional-spring-configuration-metadata.json       # [新] canal.* 全部公开键
    └── test/java/cn/maple/canal/
        ├── config/GXCanalConfigTest.java                        # [改] 条件化与覆盖能力
        ├── config/GXCanalRocketMQConfigTest.java                # [新] 互斥装配与依赖缺失提示
        ├── listener/GXCanalRabbitMQListenerTest.java            # [改] 结局驱动 + 容器注册断言
        ├── listener/GXCanalRocketMQListenerTest.java            # [新] 委派与重试语义
        ├── service/impl/GXCanalMessageParseServiceImplTest.java # [改] DDL/数组/单次解析/命名非法
        ├── service/support/GXCanalHandlerResolverTest.java      # [新] 快照、负缓存、并发
        ├── metrics/GXCanalConsumeStatisticsTest.java            # [新] 计数与 gauge
        ├── properties/GXCanalPropertiesBindingTest.java         # [新] 绑定与四组合配置源
        └── integration/GXCanalConsumerSpringBootTest.java       # [新] 装配与端到端（实施期改名：surefire 只识别 *Test）
```

**Structure Decision**: 单模块库（既有多模块框架中的 `leaf-base-data-sync`），沿用既有包结构（`config`/`constant`/`dto`/`listener`/`properties`/`service`），新增 `enums`、`metrics`、`service/support` 三个包；RocketMQ 与 RabbitMQ 的失败处置实现以子包隔离，共享逻辑留在 `service/impl` 与 `support`。不新建 Maven 模块。

## Complexity Tracking

| 新增复杂度 | 为何必要 | 更简单的替代方案被否的原因 |
| --- | --- | --- |
| `GXCanalFailureHandler` 端口 + 2 实现 | 两种 MQ 的传输级失败动作不可统一：RabbitMQ 无原生重投计数（拒绝后仅能 requeue 或落死信），RocketMQ 有 `RECONSUME_LATER` + `maxReconsumeTimes`；结局分类共享、动作必须分流 | 只保留共享重投逻辑：需要在 RabbitMQ 侧自建重投队列与 TTL，等于在通用层模拟另一种 broker 的语义，代码量与语义偏差都更大 |
| 启动期路由表 + 有界缓存（含负结果） | 热路径每消息 1–2 次 `getBean` 且未命中以异常驱动（E-05），是本次性能目标（SC-004）的前提 | 每次消息查 `ApplicationContext`：SC-004 无法达成；仅用 `ConcurrentHashMap` 无启动快照则无法在缺失 Bean 时给出启动期告警（FR-011） |
| `parse`（结构化）与 `parseMessage`（`Dict`）双入口 | `parseMessage` 是对外契约不得改签名（FR-001/§II），而消费侧需要可判定的结局与事件对象（FR-004） | 直接把 `parseMessage` 改为返回结果对象：破坏已发布的公共方法契约（宪法 §II NON-NEGOTIABLE） |
| 新增 `enums` / `metrics` 包 | 结局与 MQ 类型需要枚举（FR-037）；计数需要线程安全实现（FR-023） | 复用 `constant` 包承载枚举：违反 §VII 后缀表（枚举应为 `GX<概念>Enums` 且独立类型）；计数放 `GXSpringContextUtils` 等既有工具类：职责错配（附加约束 §5 单一职责） |

## Post-Design Constitution Re-check

Phase 1 设计完成后复核（结论记录于 `research.md` D-14）：全部原则仍为 PASS，无新增违例；Phase 0 提出的三项"待核实"（`@Lazy` 监听器注册、Canal 载荷形态、RocketMQ 消费容器在 Boot 4.1 的可用性）已在 `research.md` D-12 / D-04 / D-08 给出决策与验证方式，并作为实施第一步的验证任务。
