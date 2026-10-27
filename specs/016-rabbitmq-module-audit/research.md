# Phase 0 研究：leaf-base-rabbitmq 修复决策

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25

所有结论基于实测或官方 API 反编译验证，未验证项单独标注。

## 实测事实基线

| 项 | 实测结果 | 采集方式 |
| --- | --- | --- |
| Spring AMQP / amqp-client | `4.1.1` / `5.30.0` | `mvn -o -B -pl leaf-base-rabbitmq dependency:list` |
| `nacos-client` | `3.1.1`（compile，经 `leaf-base-nacos`） | 同上 |
| Nacos 注解类是否存在 | `com/alibaba/nacos/api/config/annotation/NacosConfigurationProperties.class` **存在** | `jar tf nacos-client-3.1.1.jar` |
| Nacos 注解处理器 | 依赖树中**无** `nacos-spring-context` / `nacos-config-spring-boot` | `dependency:list` 全量输出检索 |
| `spring-retry` | **不在** classpath | 同上 |
| `MessageProperties` 拷贝构造器 | **不存在**（仅无参构造） | `javap spring-amqp-4.1.1.jar` |
| 官方深拷贝入口 | `MessagePropertiesBuilder.fromClonedProperties(MessageProperties)` | 同上 |
| Nacos 拉取 API | `NacosFactory.createConfigService(Properties)` + `ConfigService#getConfig(dataId, group, timeout)` | `javap nacos-client-3.1.1.jar` |
| micrometer | `micrometer-core 1.17.1`（compile，框架传递） | `dependency:list` |
| 测试依赖现状 | 模块 `pom.xml` 无 test 依赖 | 阅读 `pom.xml` |
| 测试基线 | `mvn -o -B -pl leaf-base-rabbitmq test` → `BUILD SUCCESS`，0 用例 | 实际执行 |

## D-01 配置源：显式开关替代类路径条件

**决策**：新增 `maple.framework.rabbitmq.config-source`（`local` | `nacos`，`matchIfMissing` 落在 `nacos`），两个 Properties 变体用 `@ConditionalOnProperty` 互斥装配；`GXLocalRabbitMQProperties` 不再使用 `@ConditionalOnMissingClass`。

**理由**：`nacos-client` 是 compile 依赖，且其 jar 内确实包含判定用的注解类，`@ConditionalOnMissingClass` 恒为 false；这与 `leaf-base-rocketmq` 的 F-01 同源问题（见 `specs/006-rocketmq-module-audit/spec.md` E-01/E-02）。

**备选方案**：
1. 保留类路径条件、删除本地变体 —— 破坏公开类，且无法解释"本地配置"承诺；
2. 以 `com.alibaba.nacos.api.config.ConfigService` 作为判定类 —— 同样恒在，无意义；
3. 引入 `nacos-spring-boot-starter` 让注解生效 —— 与 Boot 4.1 / Spring Cloud Alibaba 2025.1 存在冲突风险，且新增运行时依赖。

**影响面**：默认 `nacos` 与当前"实际生效变体"一致，`@Resource GXRabbitMQProperties` 注入不受影响；显式 `local` 是本版本新增能力。

**验证**：`ApplicationContextRunner` 三态（未配置 / local / nacos）断言 Bean 存在性与启动日志。

**风险**：下游若通过具体类型 `GXNacosRabbitMQProperties` 注入，切换到 `local` 后失配 —— 手册声明应面向基类编程。

---

## D-02 队列声明：删除永久缓存与全局锁，改按名单飞

**决策**：删除 `queueCache` 与 `queueOperationLock`；`createQueue` 每次真实调用 `rabbitAdmin.declareQueue`，并用 `ConcurrentHashMap<String, CompletableFuture<Queue>>` 合并同一队列的并发声明（完成后移除条目）。

**理由**：
- 永久缓存只在 `deleteQueue` 时失效，无法感知 Broker 重启、运维删除、其他实例声明（`spec.md` E-06/E-07）；
- `RabbitAdmin.declareQueue` 幂等，重复声明的代价是 1 次 RTT，仅在资源准备路径发生，不在发送路径；
- 单飞让 32 线程并发声明收敛为 1 次 Broker 声明（满足 `spec.md` SC-004），且不同队列完全并行，消除全局锁（E-12）。

**备选方案**：
1. 保留缓存 + TTL —— 仍存在 TTL 窗口内的"谎报存在"，与 FR-006 冲突；
2. 保留缓存 + 指纹 —— 解决属性冲突，但不解决 Broker 侧删除；
3. 保留全局锁 —— 与 FR-012 冲突。

**影响面**：内部字段删除，无公开 API 变化；`createQueue` 的 RPC 次数在"重复调用同一队列"场景从 0 变为 1（可接受，单飞已消除并发放大）。

**验证**：并发用例断言 Broker 声明次数 = 1；"删除后再次创建"用例断言重新声明；"属性冲突"用例断言抛异常。

**风险**：`CompletableFuture` 条目必须在 `whenComplete` 中移除，否则退化为永久缓存；用例需覆盖异常完成路径。

---

## D-03 存在性判断与删除顺序

**决策**：`queueExists` 只以 `rabbitAdmin.getQueueProperties` 为准；`deleteQueue` 先删除 Broker，成功后才更新本地状态（本版本已无缓存，只保留日志）。

**理由**：E-06 的直接修复；"缓存命中即存在"在 Broker 侧删除后会让调用方永远拿到错误答案。

**备选方案**：`queueExists` 用 passive declare —— 队列不存在会关闭 channel，异常噪声大，不如 `getQueueProperties` 的 `null` 语义清晰。

**影响面**：`queueExists` 每次 1 次 RPC（原本命中缓存时 0 次）；调用频率低，可接受。

**验证**：Mock `RabbitAdmin` 返回 `null` 时断言 `false`，返回非空 `Properties` 时断言 `true`。

---

## D-04 临时队列语义

**决策**：`createTemporaryQueue(name)` 改为 `durable=false, exclusive=false, autoDelete=true`，并在 Javadoc、`AGENTS.md`、手册登记"不支持排他队列"。

**理由**：排他队列绑定声明连接（`RabbitAdmin` 的缓存连接），连接因缓存淘汰/重建关闭时队列被删除，且其他连接无法消费，方法返回值不可用（E-08）。在库模块内长期持有专用连接来维持排他队列会引入资源所有权问题，违反模块边界。

**备选方案**：
1. 用 `RabbitAdmin` 声明排他队列 —— 现有错误实现；
2. 模块内维护专用 `Connection` —— 生命周期与泄漏风险高，且 `RabbitAdmin` 仍可能复用；
3. 服务端命名队列（`new Queue("", ...)`） —— 排他问题依旧。

**影响面**：行为修正，需在兼容表中登记；现有调用方若依赖排他语义需自行声明。

**验证**：断言 `Queue.isExclusive()==false`、`isAutoDelete()==true`；集成用例验证另一连接可消费。

---

## D-05 发送属性快照

**决策**：`sendNormalMessage` 先 `MessagePropertiesBuilder.fromClonedProperties(callerProps).build()` 得到快照，所有 header/时间戳/ID 写入快照，再 `new Message(body, snapshot)`。

**理由**：`MessageProperties` 在 4.1.1 无拷贝构造器（`javap` 验证），当前实现直接改写调用方对象（E-05），并发复用同一 DTO 会互相覆盖；`fromClonedProperties` 是官方提供的深拷贝入口。

**备选方案**：
1. 手工逐字段拷贝 —— 易漏字段（`replyToAddress`、`received*`、`headers` 副本）；
2. 要求调用方每次 new DTO —— 无法约束，且不解决共享 `MessageProperties` 的问题；
3. 把 `MessageProperties` 从 DTO 移除 —— 破坏公开契约。

**影响面**：调用方传入的 `messageProperties` 不再被改写；若调用方原本依赖"发送后读取 contentType"的副作用，将失效（属缺陷修复，写入兼容表）。

**验证**：发送后断言调用方对象的 `contentType` 仍为 `null`、`headers` 不含 `X-Trace-Id`；并发用例断言消息头与载荷一一对应。

---

## D-06 追踪上下文

**决策**：`String traceId = GXTraceIdContextUtils.getNullableTraceId(); if (isBlank) traceId = generateTraceId();`；`spring.application.name` 通过 `@Value("${spring.application.name:unknown}")` 在装配期注入到字段（`final` 不可行，用 `volatile` + 构造注入的 Properties 替代）。

**理由**：`generateTraceId()` 每次新建 UUID（框架 `GXTraceIdContextUtils:66-74`），发送链路必然断链（E-10）；每消息 `GXCommonUtils.getEnvironmentValue` 是 Environment 查询（E-16）。

**备选方案**：在模块内自行从 `MDC` 读取 —— 与框架工具重复，且 `TRACE_ID_KEY` 是框架契约，必须复用。

**影响面**：消息头 `X-Trace-Id` 的值由"新 UUID"变为"调用方 traceId 或新 UUID"，与文档承诺一致；`messageId` 缺省仍取 traceId。

**验证**：MDC 有值时断言头值等于 MDC；无值时断言非空且符合 `app:uuid` 形态；并发用例断言不同线程不串号。

---

## D-07 失败语义

**决策**：新增 `GXRabbitMQOperationException extends GXBusinessException`（字段：`operation`、`resource`、`Dict detail`）。创建/声明/绑定/清空失败抛该异常；`deleteQueue` 在"队列不存在"时返回 `false`（正常结果），传输/权限失败抛异常；参数非法抛 `GXBusinessException`。

**理由**：现状四套失败风格（`null`/`false`/`AmqpException`/NPE），调用方无法区分"不存在"与"Broker 不可达"（E-09）。

**备选方案**：全部保留 `boolean` + `@Nullable` —— 信息量不足，且无法携带失败原因；全部改 `void` + 异常 —— `deleteQueue` 的"不存在"是合法结果，需要 `boolean`。

**影响面**：语义变更，写入兼容表。`setupMessageChannel` 保持 `boolean` 返回类型，但语义收敛为"成功返回 `true`、失败抛异常"，不再返回 `false`——因为 `false` 无法表达失败原因，与 FR-011 冲突。

**验证**：参数空值 → `GXBusinessException`；Mock `RabbitAdmin` 抛 `AmqpException` → `GXRabbitMQOperationException` 且 `cause` 保留；队列不存在 → `false`。

---

## D-08 序列化单栈与格式兼容

**决策（实施期修订）**：出站载荷**继续使用 Hutool** `JSONUtil.toJsonStr`（`java.util.Date` 与 `LocalDateTime` 均为毫秒时间戳，与修复前 wire format 完全一致）；`GXRabbitMQConfig` 内用共享 `JsonMapper`（`JavaTimeModule` + `NON_NULL`）装配 `JacksonJsonMessageConverter`，仅承担入站类型化转换；发送前写入 `__TypeId__=cn.hutool.core.lang.Dict`，并把 `cn.hutool.core.lang` 显式加入信任包。

**理由**：实测 Hutool 将 `Date`/`LocalDateTime` 都写为毫秒时间戳，而 Jackson 3 默认写 ISO-8601 字符串；若按原计划改用 Jackson，会改变既有消息的 wire format，违反宪法 II（稳定契约优先）。因此"单一栈"收敛为"单一出站序列化器 + 入站兼容性用例"，而不是强行统一到 Jackson。

**备选方案**：
1. 出站改用 Jackson 并配置自定义时间序列化器 —— 与 Hutool 输出仍存在 `LocalDateTime` 精度/格式差异，风险高于收益；
2. 移除 Jackson 转换器 —— 会改变 typed 入站转换行为；
3. 引入自定义双栈 `MessageConverter` —— 增加抽象层，违反 YAGNI。

**影响面**：出站字节与修复前一致；`__TypeId__` 与信任包修复解决入站 `Dict` 反序列化失败（F-24）。

**验证**：`GXMessageSerializationTest` 断言出站 body 等于 Hutool 输出、`__TypeId__` 正确、Jackson 可读取 Hutool 载荷。

**风险**：`Dict` 内嵌任意 POJO 时两栈字段可见性规则不同（Hutool 走 getter/field，Jackson 走 getter）—— 用例覆盖 POJO 与嵌套集合。

---

## D-09 调度器

**决策**：`rabbitTaskScheduler` 改为标准 `ThreadPoolTaskScheduler`：`setVirtualThreads(JavaVersion >= 21)`、`setPoolSize(max(2, CPU))`、`setThreadNamePrefix("maple-rabbit-scheduler-")`、`setRemoveOnCancelPolicy(true)`、`setErrorHandler(...)`、`setWaitForTasksToCompleteOnShutdown(true)`、`setAwaitTerminationSeconds(30)`、`setDaemon(false)`；Bean 的 `destroyMethod` 由 Spring 调用 `shutdown()`（`ThreadPoolTaskScheduler` 实现 `DisposableBean`）。

**理由**：现实现重写 `schedule(Runnable, Trigger)`，每次触发新建 `newVirtualThreadPerTaskExecutor()` 并在调度线程 `.get()` 阻塞；`ThreadPoolTaskScheduler` 默认 poolSize=1，导致并发度恒为 1（E-14）。Spring 7.0.9 的 `ThreadPoolTaskScheduler#setVirtualThreads` 直接支持虚拟线程，无需手写包装。

**备选方案**：
1. 保留重写但不再阻塞 —— 仍要管理每任务执行器生命周期；
2. `SimpleAsyncTaskScheduler` —— 不支持固定周期/触发器语义，`AsyncRabbitTemplate` 的延迟/超时任务需要 `TaskScheduler` 契约；
3. 直接用 `ScheduledExecutorService` —— 失去 Spring 生命周期集成。

**影响面**：`TaskScheduler` Bean 类型不变；JDK < 21 时退化为平台线程池（基线为 21）。原 `JavaVersion.SEVENTEEN` 判定导致 JDK 17–20 `NoSuchMethodError`（E-15）随之消除。

**验证**：并发提交 4 个延迟任务，断言完成时间差 < 串行耗时（并发度 > 1）；上下文关闭后断言线程终止（线程名计数）。

---

## D-10 调度器 Bean 暴露

**决策**：新增 `maple.framework.rabbitmq.scheduler.expose`（默认 `true`）。`true` 时保持 `TaskScheduler` Bean（现状兼容）；`false` 时不注册 Bean，由 `GXRabbitMQConfig` 内部持有调度器并注入 `AsyncRabbitTemplate`，`@PreDestroy` 关闭。

**理由**：该 Bean 会让 Boot `TaskSchedulingAutoConfiguration` 的默认 `taskScheduler` 退让，进而影响全应用 `@Scheduled`（E-17）。既要兼容，又要给下游退路，符合 FR-022。

**备选方案**：直接不暴露 Bean —— 破坏"按类型/名称注入 `rabbitTaskScheduler`"的既有用法，且 `AsyncRabbitTemplate` 需要它。

**验证**：`ApplicationContextRunner` 两种取值断言 Bean 存在性；`asyncRabbitTemplate` 在两种模式下都能拿到调度器。

---

## D-11 异步执行器

**决策**：保留 `ThreadPoolExecutor`；`corePoolSize=min(coreSize,maxSize)`、`allowCoreThreadTimeOut(true)`；`queueCapacity<=0` 时使用 `SynchronousQueue`（使 `max-size` 立即生效），否则 `LinkedBlockingQueue(capacity)`；拒绝策略 `AbortPolicy`，在 `setupMessageChannelAsync` 内捕获 `RejectedExecutionException` 并返回 `failedFuture`；`@PreDestroy` 中 `shutdown()` → `awaitTermination(30s)` → `shutdownNow()`，日志 ASCII；任务通过 `TaskDecorator` 等价逻辑（提交前捕获 MDC，任务内恢复并清理）保留追踪上下文。

**理由**：`CallerRunsPolicy` 会把 Broker 声明 IO 反压到调用线程（E-16）；`maxSize` 在 `LinkedBlockingQueue` 未满时不生效是 JDK 既有语义，需显式文档化并提供 `queue-capacity=0` 的严格模式。

**备选方案**：
1. 虚拟线程 + `Semaphore` 限流 —— 需重定义 `thread-pool.*` 配置语义，改动面大于收益；
2. `ThreadPoolTaskExecutor` —— 拒绝策略与队列语义相同，但缺少 `allowCoreThreadTimeOut` 的直接开关；
3. 保留 `CallerRunsPolicy` —— 与"不反压调用线程"的修复目标冲突。

**验证**：`core=max=2, queue=0, 4 并发` 用例断言第 3 个任务被拒绝且返回失败 Future；关闭用例断言在途任务完成后线程终止。

---

## D-12 消费失败分层

**决策**：`process` 中 `AmqpException`/`RuntimeException` 原样抛出（交给容器重试与 `default-requeue-rejected` 配置）；空消息体与 JSON 解析失败抛 `AmqpRejectAndDontRequeueException`；计数通过 `GXRabbitMQListenerStatistics`（`ObjectProvider<MeterRegistry>`，无注册表时仅日志）记录 `rabbitmq.listener.messages.rejected`。

**理由**：现状全捕获 + 正常返回 = ack，毒消息永久丢失（E-11）；重试不可解析消息没有意义。

**备选方案**：自定义 `ErrorHandler` —— 会接管全局容器行为，超出模块边界；转发死信 —— 需要 Broker 拓扑与交换机名，属部署方职责。

**影响面**：默认监听器行为变化（此前永不抛异常），需在 `AGENTS.md` 登记；计数器指标名纳入契约。

**验证**：空消息用例断言抛 `AmqpRejectAndDontRequeueException`；JSON 非法用例同断言；业务异常用例断言原样抛出。

---

## D-13 Nacos 启动期拉取

**决策**：新增 `GXRabbitMQEnvironmentPostProcessor`（`spring.factories` 注册）。仅当 `maple.framework.rabbitmq.config-source=nacos`（默认）且 `spring.cloud.nacos.config.server-addr`/`nacos.config.server-addr` 非空时执行：用 `NacosFactory.createConfigService(props)` 拉取 `dataId=rabbit.yml`、`groupId=${spring.cloud.nacos.config.group:${nacos.config.group:DEFAULT_GROUP}}`、超时 2s；成功则 `environment.getPropertySources().addLast(new MapPropertySource("nacos-rabbit.yml", flatten(yaml)))`；失败/为空只 `warn`，不阻断启动。日志：`RabbitMQ config source: nacos, dataId=rabbit.yml, loaded=true|false, keys=N`。

**理由**：`@NacosConfigurationProperties` 无处理器（E-02）；`EnvironmentPostProcessor` 是 Spring Boot 官方早期扩展点，能在 `@ConfigurationProperties` 绑定前注入；低优先级保证 `application.yml`/命令行仍可覆盖，不改变现有部署语义。

**备选方案**：
1. 要求下游写 `spring.config.import=optional:nacos:rabbit.yml` —— 需要下游改动，且模块声明"支持 Nacos"落空；
2. 在 `@Bean` 初始化时拉取 —— 绑定已发生，无效；
3. 引入 `nacos-spring-boot-starter` —— 版本冲突风险 + 新依赖。

**影响面**：新增一个 `META-INF/spring.factories` 文件；无 Nacos 地址时零网络、零副作用。

**验证**：单测用可替换的 `ConfigService` 工厂（包私有 setter 或 `protected` 方法）断言"有地址才拉取""失败不抛出""YAML 打平后键可读"；上下文用例断言低优先级（`application.yml` 覆盖生效）。

**风险**：YAML 解析需 `snakeyaml`（`spring-boot-starter-amqp` 已排除 `snakeyaml`，但 `spring-boot` 自带 `snakeyaml` 由 Boot 引入；需在实现时确认，若缺失则改用 `YamlPropertySourceLoader`，它是 Spring Boot 自带 API，不新增依赖）。

---

## D-14 清理与依赖

**决策**：删除 `handleComplexJsonMessage`、注释掉的 `connectionFactory` Bean、注释掉的 `runAsync` 调用、两个 `@SuppressWarnings("all")`；`pom.xml` 移除 `leaf-base-retry`；`GXRecoveryCallback` 保留并在 Javadoc 说明"仅在调用方为 `RabbitTemplate` 设置 `RetryTemplate` 时触发"。

**理由**：宪法附加约束 §8（YAGNI）与 FR-021；`spring-retry` 不在 classpath，恢复回调无法由模块自动接线（E-18）。

**备选方案**：为触发恢复回调引入 `spring-retry` —— 新增运行时依赖，且改变发送重试语义（可能造成重复投递），超出本次修复范围。

**验证**：`mvn -o -B -pl leaf-base-rabbitmq dependency:tree` 确认无 `leaf-base-retry`；全量用例通过；`AGENTS.md` 记录恢复回调触发条件。

---

## 未验证项（实施阶段必须闭合）

1. Nacos 侧真实拉取未做端到端验证（无 Nacos 服务地址）；以单测覆盖协议调用与降级路径，集成用例环境变量门控。
2. 真实 Broker 侧"临时队列可被另一连接消费"需在有 Broker 环境执行，默认跳过。
3. 性能对比样本需在修复前后同一环境采集，当前仅有代码路径口径。
