# 审查与修复报告：leaf-base-rabbitmq

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## 1. 基线

| 项 | 结果 | 采集命令 |
| --- | --- | --- |
| 构建 | `BUILD SUCCESS` | `mvn -o -B -pl leaf-base-rabbitmq test` |
| 测试 | **0 个用例**（无 `src/test`、`pom.xml` 无 test 依赖） | 同上 |
| 依赖 | `spring-amqp/spring-rabbit 4.1.1`、`amqp-client 5.30.0`、`nacos-client 3.1.1`、无 `spring-retry` | `mvn -o -B -pl leaf-base-rabbitmq dependency:list` |
| 类路径判定 | `nacos-client-3.1.1.jar` **包含** `com/alibaba/nacos/api/config/annotation/NacosConfigurationProperties.class` | `jar tf` |

## 2. 结论

| 维度 | 修复前 | 修复后 |
| --- | --- | --- |
| 正确性 | 双配置源失效、队列缓存谎报、临时队列不可用、毒消息静默丢失、追踪断链 | 配置源真实生效且互斥；每次声明触达 Broker；临时队列可消费；失败分层处置；traceId 复用 MDC |
| 线程安全 | 调用方 `MessageProperties` 被改写、全局锁跨网络 IO、调度器/执行器未受管 | 属性快照、无全局锁（单飞替代）、调度器与执行器有明确关闭路径 |
| 性能 | 每消息 6 项固定开销；声明被单锁串行；虚拟线程调度器并发度 = 1；`max-size` 失效 | 固定开销 ≤4 项；不同队列并行；调度并发度 >1；队列语义文档化并提供严格模式 |
| 可验证性 | 0 用例 | **125 用例**（120 执行 + 5 集成跳过），连续 3 次全绿 |

## 3. 发现与处置

级别：高 / 中 / 低。状态：已修复 / 已登记 / 部分修复。

| 编号 | 级别 | 维度 | 原始位置 | 现象 | 证据 | 处置 |
| --- | --- | --- | --- | --- | --- | --- |
| F-01 | 高 | 正确性 | `GXLocalRabbitMQProperties:16`、`GXNacosRabbitMQProperties:17-27` | 本地源条件恒假；Nacos 注解无处理器，`rabbit.yml` 从不拉取 | E-01/E-02（`jar tf` + `dependency:list`） | 已修复：`config-source` 互斥 + `EnvironmentPostProcessor` 拉取 + 启动日志 |
| F-02 | 高 | 正确性 | `GXSendRabbitMQServiceImpl:37,128-131,237-239` | 永久缓存命中即返回成功，Broker 侧删除后仍报存在 | E-06/E-07 | 已修复：删除缓存，单飞 + 每次声明；`queueExists` 只查 Broker |
| F-03 | 高 | 正确性 | 同文件 `159-161` | 排他队列经共享 Admin 连接声明，其他连接不可消费 | E-08 | 已修复：改为非排他 + 自动删除，并在文档登记"排他不支持" |
| F-04 | 高 | 正确性 | `GXDefaultRabbitMQQueueListenerImpl:39-43,59-61` | 全捕获后正常返回 → ack，毒消息丢失 | E-11 | 已修复：空/不可解析抛 `AmqpRejectAndDontRequeueException`，其余原样抛出 + 计数 |
| F-05 | 高 | 正确性 | `GXSendRabbitMQServiceImpl:188` | 每消息 `generateTraceId()` 新建 UUID，链路断开 | E-10 | 已修复：优先 `getNullableTraceId()` |
| F-06 | 中 | 线程安全 | 同文件 `96-104` | 就地改写调用方 `MessageProperties`，并发复用即串扰 | E-05 | 已修复：`MessagePropertiesBuilder.fromClonedProperties` 快照 |
| F-07 | 中 | 正确性 | 同文件 `85-89,147,176,263,274,287,300,228-232` | 4 种失败风格（null/false/AmqpException/NPE） | E-09 | 已修复：参数 → `GXBusinessException`；Broker → `GXRabbitMQOperationException`；异步 → `failedFuture` |
| F-08 | 中 | 正确性 | 同文件 `312-339` | 通道部分失败无补偿、无失败步骤 | E-09 | 已修复：`detail.completedSteps`/`failedStep` |
| F-09 | 中 | 性能/线程安全 | 同文件 `39,139,166-168,313-338` | 全局锁跨越 AMQP 网络 IO；删除先清缓存 | E-12/E-13 | 已修复：删除全局锁；`deleteQueue` 先删 Broker |
| F-10 | 中 | 性能 | `GXRabbitMQConfig:52-70` | 每次触发新建虚拟线程执行器并在调度线程阻塞 `.get()`；默认 poolSize=1 → 并发度 1；无销毁 | E-14 | 已修复：`ThreadPoolTaskScheduler` + 虚拟线程 + `max(2,CPU)` + 30s 优雅关闭 |
| F-11 | 中 | 性能/正确性 | `GXSendRabbitMQServiceImpl:62-74` | `maxSize` 不生效；`CallerRunsPolicy` 把 IO 反压到调用线程；无关闭 | E-16 | 已修复：`AbortPolicy` + `SynchronousQueue` 严格模式 + `@PreDestroy` |
| F-12 | 中 | 正确性 | 同文件 `104` + `GXRabbitMQConfig:98-106` | 生产 Hutool / 消费 Jackson，无类型头 | E-19 | 部分修复：出站保持 Hutool（wire 兼容），补 `__TypeId__`，Jackson 统一用于类型化转换，新增兼容性用例 |
| F-13 | 中 | 正确性 | `GXRabbitMQConfig:193-229` | `TaskScheduler` Bean 顶掉 Boot 默认调度器 | E-17 | 已修复：标准池参数 + `scheduler.expose` 开关 |
| F-14 | 中 | 正确性 | `GXRabbitMQConfig:137-149` + `pom.xml` | 恢复回调无 `RetryTemplate` 永不触发；`leaf-base-retry` 零引用 | E-18 | 已修复：移除未使用依赖 + Javadoc/文档说明触发条件 |
| F-15 | 中 | 可验证性 | 模块无 `src/test` | 0 用例，改动无回归保护 | E-24 | 已修复：125 用例（单元/上下文/并发/集成门控） |
| F-16 | 中 | 完备性 | 全模块 | 批量/延迟/死信/指标/大小校验未实现也未登记 | E-16 相关 | 已登记：`AGENTS.md` §5、手册 §8 |
| F-17 | 低 | 性能 | `GXSendRabbitMQServiceImpl:188-210` | 每消息 UUID + Environment 查询 + info/debug 日志 | E-10/E-16 | 已修复：环境名装配期注入；成功路径仅 debug |
| F-18 | 低 | 规范 | 全模块 | 日志全中文 | E-20 | 已修复：`rg` 扫描 0 条非 ASCII 日志 |
| F-19 | 低 | 规范 | `GXDefaultRabbitMQQueueListenerImpl:68-77`、`GXRabbitMQConfig:258-273` | 死代码、注释掉的 Bean、`@SuppressWarnings("all")` | E-22 | 已修复：全部删除 |
| F-20 | 低 | 装配 | 模块无 `resources` | 未装配无信号、字段注入硬依赖 | E-21 | 部分修复：启动日志 + Bean 暴露开关；未引入自动装配 imports（避免改变下游扫描语义） |
| F-21 | 低 | 正确性 | `GXSendRabbitMQServiceImpl` | 队列名/路由键无长度与控制字符校验；无配置元数据 | E-23 | 已修复：255 上限 + 控制字符拒绝 + `additional-spring-configuration-metadata.json` |
| F-22 | 低 | 文档 | 手册 §2.3/2.6/2.7/3.2/6.1 | 前缀、条件、回调签名、SpEL、不可变声明均与实现不符 | 手册原文 | 已修复：手册重写并与实现对齐 |
| F-23 | **高** | 正确性 | `pom.xml`（`jackson-datatype-jsr353`） | 该模块通过 ServiceLoader 注册，缺少 `org.glassfish.json.JsonProviderImpl`，Boot `JacksonAutoConfiguration` 创建 `JsonMapper.Builder` 时抛 `ServiceConfigurationError`，**应用启动失败** | 上下文测试首次运行 7 个 error | 已修复：移除未使用的 `jackson-datatype-jsr353` |
| F-24 | **高** | 正确性 | `GXRabbitMQConfig:99` | `setTrustedPackages("cn.hutool.core")` **不覆盖子包** `cn.hutool.core.lang`，`Dict` 类型化反序列化被拒 | 兼容性用例报 "not in the trusted packages" | 已修复：显式加入 `cn.hutool.core.lang` |
| F-25 | **高** | 装配 | `GXRabbitMQProperties` 子类 | 容器同时存在 Boot 的 `RabbitProperties` 与模块变体 → `RabbitConnectionFactoryCreator` 注入 `NoUniqueBeanDefinitionException`，**应用启动失败** | 上下文测试首次运行 7 个 error | 已修复：两个变体标 `@Primary` |

> F-23/F-24/F-25 由本次新增的 Spring 上下文测试发现，均为生产阻断级问题，属审查的重要产出。

## 4. 已确认正确行为（修复中未破坏）

| 编号 | 行为 |
| --- | --- |
| P-01 | `RabbitTemplate`/`RabbitAdmin` 线程安全复用 |
| P-02 | 发送设置 UTF-8 与 `application/json` |
| P-03 | `DefaultClassMapper` 白名单机制（本次补齐子包） |
| P-04 | 三个回调各自 `try-catch` 隔离，业务异常不影响发送链路 |
| P-05 | 队列声明幂等；单飞保证同进程并发只声明一次 |
| P-06 | 模块编译与构建基线通过 |
| P-07 | 全部包提供 `package-info.java` |

## 5. 测试证据

命令：`mvn -o -B -pl leaf-base-rabbitmq test`（包含 `*IT`，无 Broker 时集成用例 Skip）。

| 次数 | 用例 | 失败 | 错误 | 跳过 | 结果 |
| --- | --- | --- | --- | --- | --- |
| 第 1 次 | 125 | 0 | 0 | 5 | BUILD SUCCESS |
| 第 2 次 | 125 | 0 | 0 | 5 | BUILD SUCCESS |
| 第 3 次 | 125 | 0 | 0 | 5 | BUILD SUCCESS |

集成用例选择验证：`-Dgroups=integration` → `Tests run: 5, Skipped: 5`（无 Broker 环境），证明分组命令可选中用例。

用例分布：

| 测试类 | 用例数 | 覆盖 |
| --- | --- | --- |
| `GXSendRabbitMQServiceImplTest` | 38 | 13 个公开方法的成功/失败/空值/边界、`arguments` 空值、`QUERY_QUEUE`；13 个公开方法均覆盖 |
| `GXRabbitMQConfigSpringBootTest` | 8 | Bean 装配、转换器、三回调、调度器并发、监听器、单一配置变体、配置绑定 |
| `GXDefaultRabbitMQQueueListenerTest` | 7 | 空/空白/不可解析/正常/异常/空对象 |
| `GXMessageChannelFailureTest` | 7 | 通道部分失败、异步失败、线程池拒绝 |
| `GXSendOverheadTest` | 5 | 关键路径 0 条 info 日志、trace 复用 |
| `GXRabbitMQEnvironmentPostProcessorTest` | 5 | 无地址跳过、YAML 加载、优先级、Nacos 异常降级、local 跳过 |
| `GXQueueDeclarationSemanticsTest` | 5 | Broker 删除后重新声明、删除失败、空名 |
| `GXMessageSerializationTest` | 5 | wire format、`__TypeId__`、null 省略、Unicode、入站兼容 |
| `GXRabbitMQConfigSourceTest` | 4 | local/nacos/默认/未知取值互斥 |
| `GXQueueDeclarationConcurrencyTest` | 4 | 32 线程同队列 1 次声明、10 队列并行、失败广播 |
| `GXTracePropagationTest` | 4 | MDC 复用、生成、上下文不变、多线程不串号 |
| `GXRabbitMQMessageReqDtoTest` | 4 | Builder/无参/`@Nullable`/默认值 |
| `GXSendRabbitMQServiceImplLifecycleTest` | 4 | MDC 传递（由 MDC 包装线程池提供）、幂等关闭 |
| `GXRabbitMQSchedulerExposeDisabledTest` | 4 | `scheduler.expose=false`：无 TaskScheduler Bean、异步模板仍有调度器、内部调度器关闭后线程终止 |
| `GXRabbitMQListenerStatisticsTest` | 3 | `SimpleMeterRegistry` 下 processed/rejected/failed 计数与降级 |
| `GXMessagePropertiesIsolationTest` | 4 | 调用方对象不被改写、复用快照独立、发送后改写载荷不影响已发送消息、16×100 无串扰 |
| `GXDefaultRabbitMQQueueListenerConditionTest` | 3 | enable 三态 |
| `GXRabbitMQOperationExceptionTest` | 3 | 字段、cause、completedSteps |
| `GXSendOverheadSamplingTest` | 1 | 500 条发送采样（avg/p50/p95/max） |
| `GXRabbitMQThreadLifecycleTest` | 2 | 上下文 3 次启停后调度器线程不残留；异步执行器线程随关闭终止 |
| `GXRabbitMQRealBrokerIT` | 3（跳过） | 声明/发送/接收/删除、临时队列、清空 |
| `GXRabbitMQThroughputSamplingIT` | 2（跳过） | 顺序/并发吞吐采样 |

### 3.1 逐文件判定（22 个源码文件）

| 文件 | 判定 |
| --- | --- |
| `config/GXRabbitMQConfig` | 缺陷已修复：转换器/回调/调度器重写，构造注入 + 启动日志 |
| `config/package-info` | 无缺陷 |
| `service/GXSendRabbitMQService` | 契约补充 Javadoc，签名不变 |
| `service/impl/GXSendRabbitMQServiceImpl` | 缺陷已修复：快照、单飞、MDC 包装线程池、统一失败 |
| `service/impl/package-info` | 无缺陷 |
| `service/package-info` | 无缺陷 |
| `properties/GXRabbitMQProperties` | Javadoc 与默认值说明补齐 |
| `properties/GXRabbitMQEnvironmentPostProcessor` | 新增：Nacos 启动期拉取 |
| `properties/local/GXLocalRabbitMQProperties` | 缺陷已修复：显式开关 + 双路径 PropertySource |
| `properties/nacos/GXNacosRabbitMQProperties` | 缺陷已修复：移除无效注解 + `@Primary` |
| `properties/package-info` | 无缺陷 |
| `properties/local/package-info` | 无缺陷 |
| `properties/nacos/package-info` | 无缺陷 |
| `listener/GXRabbitMQQueueListener` | 契约补充"禁止吞异常" |
| `listener/impl/GXDefaultRabbitMQQueueListenerImpl` | 缺陷已修复：拒绝策略、计数、死代码删除 |
| `listener/impl/GXRabbitMQListenerStatistics` | 新增：可选指标计数 |
| `listener/impl/package-info` | 无缺陷 |
| `listener/package-info` | 无缺陷 |
| `dto/inner/GXRabbitMQMessageReqDto` | `@Nullable` 与 tag 语义补齐 |
| `dto/inner/package-info` | 无缺陷 |
| `callback/GXConfirmCallback` | 无缺陷（仅 Javadoc 由手册覆盖） |
| `callback/GXReturnsCallback` | 无缺陷 |
| `callback/GXRecoveryCallback` | 无缺陷；触发条件已在文档说明 |
| `callback/package-info` | 无缺陷 |
| `exception/GXRabbitMQOperationException` | 新增：步骤可定位的异常 |
| `support/GXRabbitMQJsonSupport` | 新增：共享 `JsonMapper` 与类型头常量 |

### 3.2 发现 → 复现 → 回归用例

| 编号 | 触发条件 | 复现 / 回归用例 | 修复前观察 |
| --- | --- | --- | --- |
| F-01 | 启动上下文并检查 Properties Bean | `GXRabbitMQConfigSourceTest`、`GXRabbitMQEnvironmentPostProcessorTest` | 本地变体永不装配；Nacos `rabbit.yml` 从不加载 |
| F-02 | 同一队列连续两次 `createQueue`，或 Broker 侧删除后查询 | `GXQueueDeclarationSemanticsTest`、`GXQueueDeclarationConcurrencyTest` | 缓存命中不触达 Broker |
| F-03 | 调用 `createTemporaryQueue` 检查 `isExclusive` | `GXSendRabbitMQServiceImplTest#shouldDeclareTemporaryQueueAsNonExclusiveAndAutoDelete` | 返回排他队列 |
| F-04 | 发送空消息 / 非法 JSON 给默认监听器 | `GXDefaultRabbitMQQueueListenerTest` | 仅 warn 后正常返回（ack） |
| F-05 | MDC 有 traceId 时发送并检查头 | `GXTracePropagationTest` | 头值为新 UUID |
| F-06 | 发送后检查调用方 `MessageProperties` | `GXMessagePropertiesIsolationTest` | contentType/headers 被改写 |
| F-07 | 参数为空 / Broker 抛错 | `GXSendRabbitMQServiceImplTest`、`GXMessageChannelFailureTest` | NPE / 返回 `null` / 返回 `false` |
| F-08 | 交换机成功、队列失败 | `GXMessageChannelFailureTest#shouldReportCompletedExchangeWhenQueueFails` | 无失败步骤信息 |
| F-09 | 10 个不同队列并发声明 | `GXQueueDeclarationConcurrencyTest#shouldNotSerializeDifferentQueues` | 全局锁串行 |
| F-10 | 4 个延迟任务并发 | `GXRabbitMQConfigSpringBootTest#shouldRunScheduledTasksConcurrently` | 串行（并发度 1） |
| F-11 | `queue-capacity=0` 且并发超限 | `GXMessageChannelFailureTest#shouldReturnFailedFutureWhenExecutorRejects` | CallerRuns 反压 / 无失败 Future |
| F-12 | 发送 `Dict` 并检查 body 与类型头 | `GXMessageSerializationTest` | 无 `__TypeId__`，双栈格式不可验证 |
| F-13 | `scheduler.expose=false` 启动 | `GXRabbitMQSchedulerExposeDisabledTest` | 无开关，必然暴露 |
| F-14 | 检查依赖与恢复回调触发条件 | 依赖清单 + 文档 | 依赖零引用；回调永不触发 |
| F-15 | 执行 `mvn test` | 全量套件 | 0 用例 |
| F-16 | 查阅能力边界 | `AGENTS.md` §5、手册 §8 | 未登记 |
| F-17 | 500 条发送采样 | `GXSendOverheadSamplingTest`、`GXSendOverheadTest` | 每消息 UUID + Environment 查询 |
| F-18 | `rg` 扫描日志 | ASCII 扫描 0 条 | 全中文日志 |
| F-19 | 代码检索死代码 | 人工复核 | `handleComplexJsonMessage` 等残留 |
| F-20 | 启动日志 | `GXRabbitMQConfig` 启动日志 | 无来源/profile 信息 |
| F-21 | 超长/控制字符队列名、null 参数值 | `GXSendRabbitMQServiceImplTest` | 直接下沉到 Broker |
| F-22 | 对照手册与实现 | 手册重写 | 多处不符 |
| F-23 | 启动 Spring 上下文 | `GXRabbitMQConfigSpringBootTest` | `JSR353Module` 初始化失败，上下文起不来 |
| F-24 | 用 Jackson 读取 `Dict` 载荷 | `GXMessageSerializationTest#shouldDeserializeHutoolPayloadWithJacksonConverter` | 信任包拒绝 `cn.hutool.core.lang` |
| F-25 | 启动 Spring 上下文 | `GXRabbitMQConfigSpringBootTest` | `NoUniqueBeanDefinitionException`（两个 `RabbitProperties`） |

### 5.1 修复前失败证据（实施过程实测）

| 发现 | 失败输出摘要 |
| --- | --- |
| F-23 | `ServiceConfigurationError: ... JSR353Module could not be instantiated` → `ClassNotFoundException: org.glassfish.json.JsonProviderImpl`，上下文启动 7 个 error |
| F-24 | `IllegalArgumentException: The class 'cn.hutool.core.lang.Dict' is not in the trusted packages: [java.util, java.lang, cn.hutool.core, cn.maple]` |
| F-25 | `NoUniqueBeanDefinitionException: ... expected single matching bean but found 2: GXNacosRabbitMQProperties,spring.rabbitmq-...RabbitProperties` |
| F-06 | 断言 `expected: null but was: "application/octet-stream"`（调用方属性被写入） |
| F-07/F-11 | `CompletableFuture[Incomplete]`（旧实现以 `false` 正常完成，未以失败 Future 表达） |
| F-12 | `expected: {"date"=...L} but was: {"date"="2026-10-25 10:00:00"}`（双栈格式差异） |
| F-05 | `Expected "test-app:" but got bare UUID`（生成新 traceId） |

## 6. 性能口径

无真实 Broker 环境，仅给出可复算的代码路径口径：

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| 发送固定开销项 | 6（Hutool 序列化、UUID、Environment 查询、属性原位改写、debug 日志、1 RTT） | 4（Hutool 序列化、属性快照、debug 日志、1 RTT）；traceId 仅在 MDC 缺失时生成 |
| 成功路径 info 日志 | 1 条（资源声明）/ 0 条（发送） | 0 条（用例 `GXSendOverheadTest` 断言） |
| 资源声明并发 | 全局锁串行 | 同队列单飞、不同队列并行 |
| 调度器并发度 | 1 | `max(2, CPU)`，用例断言 4×200ms 任务 < 700ms |
| 同队列重复声明 | 缓存命中 0 次（可能谎报） | 稳态每次 1 次 RPC（可预测） |

**无 Broker 实测采样（`GXSendOverheadSamplingTest`，500 条，mock `RabbitTemplate`）**：

```text
rabbitmq overhead sampling: samples=500, avgUs=51, p50Us=16, p95Us=142, maxUs=3111
```

说明：该样本只覆盖模块自身开销（校验、快照、追踪头、Hutool 序列化、Mock 调用），不含网络 RTT；真实端到端时延需 `GXRabbitMQThroughputSamplingIT` 在有 Broker 环境采集（未验证项）。

**修复前样本复现方式（未执行，替代口径已给出）**：

修复前代码仍可从 `HEAD` 取出（本次改动尚未提交），复现步骤：

```powershell
git worktree add %TEMP%\mq-baseline HEAD
# 把当前 leaf-base-rabbitmq/pom.xml 的 test 依赖与
# src/test/java/.../GXTestRabbitFixtures.java、GXSendOverheadSamplingTest.java
# 复制到 worktree 对应位置（采样用例只依赖公开构造器与 @Value 字段名，可直接编译）
mvn -o -B -pl leaf-base-rabbitmq test -Dtest=GXSendOverheadSamplingTest
git worktree remove %TEMP%\mq-baseline --force
```

未执行原因：需要临时修改基线模块的 `pom.xml` 以引入 test 依赖，且会与当前工作区并行占用本地仓库；为避免污染工作区，本次以代码路径复算作为修复前口径（6 项 → 4 项，逐项对应 `spec.md` E-16/E-17）。若需要实测数字，按上述命令单独采集并回填本节。

## 7. 未验证项

| 项 | 原因 | 手工命令 / 观察指标 |
| --- | --- | --- |
| 真实 Broker 声明/发送/消费全链路 | 本机未提供 Broker（分组命令已验证可选中：5 用例全部 Skip） | `RABBITMQ_HOST=... mvn -o -B -pl leaf-base-rabbitmq test -Dgroups=integration`；观察 `GXRabbitMQRealBrokerIT` 结果 |
| 临时队列跨连接消费 | 同上 | 同上，`shouldDeclareTemporaryQueueConsumable` |
| 顺序/并发时延分位 | 同上 | `GXRabbitMQThroughputSamplingIT` 输出 avg/p50/p95/max |
| Nacos 真实拉取 | 无 Nacos 服务 | 单测覆盖协议调用与降级；端到端需真实 server-addr |
| 批处理/延迟/死信治理 | 未实现，已登记能力边界 | 见 `AGENTS.md` §5、手册 §8 |

## 8. 与计划的偏差

| 项 | 计划 | 实际 | 原因 |
| --- | --- | --- | --- |
| 序列化 | 生产端改用 Jackson，格式对齐 Hutool | 生产端继续 Hutool；Jackson 仅类型化转换 + 兼容性用例 | Hutool 将 `Date`/`LocalDateTime` 写为毫秒时间戳；改 Jackson 会破坏 wire format（宪法 II） |
| `GXSendRabbitMQService` 失败语义 | 全部抛异常 | 参数/Broker 失败抛异常；`deleteQueue` 保留 `false` 表示不存在 | "不存在"是合法结果，不属于失败 |
| 自动装配 | 未引入 imports | 同计划 | 避免改变下游 `@ComponentScan` 语义 |
| `spring-rabbit-test` | 计划引入 | 已引入但未使用 | 为后续真实 Broker 断言预留；无运行时影响 |

## 9. 交付清单

- [x] 125 用例（120 执行 + 5 集成跳过）、连续 3 次全绿
- [x] 13 个公开方法矩阵覆盖
- [x] Spring 上下文装配、配置源、监听器启停、转换器、回调
- [x] 并发：16×20 发送、32 线程声明、10 队列并行
- [x] 日志 ASCII 扫描 0 条
- [x] `AGENTS.md`、开发手册、5 份契约与实现对齐
- [x] 能力边界登记
- [x] 未验证项与手工命令登记
