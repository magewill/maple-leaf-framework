# Feature Specification: leaf-base-rabbitmq 正确性、线程安全与性能审查及修复

**Feature Branch**: `016-rabbitmq-module-audit`（未通过 hook 创建分支；当前分支为 `dev4.3.0-spec/015-dubbo-zk-module-audit`）

**Created**: 2026-10-25

**Status**: Draft

**Input**: User description: "审查 @leaf-base-rabbitmq 模块的正确性、线程安全性、性能。你在修改完代码之后，一定做功能完备性的测试。"

## 背景与审查结论摘要

本规格把"`leaf-base-rabbitmq` 现状是否正确、是否线程安全、是否存在性能结构问题，以及修复后如何证明功能完备"转化为可验收的交付要求。

审查对象与证据来源：

1. `leaf-base-rabbitmq` 全部 **22 个源码文件**（12 个生产类型 + 10 个 `package-info.java`），模块无 `src/test`；
2. 契约来源：`leaf-base-rabbitmq/AGENTS.md`、`docs/leaf-base-rabbitmq开发手册.md`、`.specify/memory/constitution.md`（v1.3.0）；
3. 依赖基线（`mvn -o -B -pl leaf-base-rabbitmq dependency:list` 实测）：`spring-rabbit/spring-amqp 4.1.1`、`amqp-client 5.30.0`、`nacos-client 3.1.1`（compile，含 `com/alibaba/nacos/api/config/annotation/NacosConfigurationProperties.class`，已用 `jar tf` 验证）、**无** `nacos-spring-context` / `nacos-config-spring-boot` 注解处理器、**无** `spring-retry`；模块硬依赖 `leaf-base-framework`、`leaf-base-nacos`、`leaf-base-retry`；
4. 构建基线（实测）：`mvn -o -B -pl leaf-base-rabbitmq test` → `BUILD SUCCESS`，**0 个测试用例**，`pom.xml` 中无任何 test 作用域依赖。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 逻辑正确性 | 发送主链路（参数校验 → 序列化 → 追踪头 → `convertAndSend`）可跑通，但**队列声明缓存与 Broker 状态永久脱钩**、**"临时队列"用共享连接声明排他队列导致不可消费**、**消费端吞掉全部异常导致毒消息静默丢失**、**双配置源只有一条名义生效**四类问题会造成"无报错但行为与承诺不符" | 存在高风险缺陷 |
| 线程安全 | `RabbitTemplate`/`RabbitAdmin` 本身线程安全，服务实例的 `volatile` 双检锁写法正确；风险集中在**调用方 `MessageProperties` 被就地改写并在并发下串扰**、**全局锁跨越网络 IO**、**队列缓存缺少一致性约束**、**调度器线程池未受管** | 无致命竞态，需补强约束 |
| 性能 | 每消息固定 6 项附加开销（Hutool 序列化、UUID 追踪号、Environment 查询、属性改写、debug 日志、1 次 RTT），资源声明被单把全局锁串行化，虚拟线程调度器实际**并发度为 1**，异步线程池 `maxSize` 在队列未满时永不生效 | 存在结构性上限 |

关键证据（均可按文件与行号复核）：

- **E-01**：`GXLocalRabbitMQProperties` 以 `@ConditionalOnMissingClass({"com.alibaba.nacos.api.config.annotation.NacosConfigurationProperties"})` 判定本地源（第 16 行），而该注解类**确实存在于 `nacos-client 3.1.1`**（compile 依赖，已 `jar tf` 验证），条件恒为 false，本地配置类永不装配。
- **E-02**：`GXNacosRabbitMQProperties` 使用 `@NacosConfigurationProperties(dataId="rabbit.yml", autoRefreshed=true)`（第 18–27 行），但依赖树中无任何注解处理器；该类实际只靠 `@ConfigurationProperties(prefix="spring.rabbitmq")`（第 28 行）绑定 `Environment`，**Nacos 的 `rabbit.yml` 从未被拉取，`autoRefreshed` 无执行者**，而启动日志仍打印"使用NACOS配置"（第 31 行）。
- **E-03**：本地源依赖 `@PropertySource("classpath:/${spring.profiles.active:dev}/rabbit.yml")`（第 17 行），而仓库内 `config/{dev,pre,prod,test}/rabbit.yml` 是**外置目录**配置，不在任何模块的 classpath 根；即使本地源被激活，该 `classpath:` 前缀也无法解析外置文件（模块 `src/main/resources` 不存在）。
- **E-04**：`GXRabbitMQProperties.defaultQueueName`（第 11 行）无任何读取方；监听器队列名取自 `${maple.framework.mq.rabbitmq.default-queue-name:${spring.rabbitmq.default-queue-name:${spring.rabbitmq.defaultQueueName:maple.default.queue}}}`（listener 第 29 行），因此 `config/dev/rabbit.yml` 的 `defaultQueueName: magleton` 对监听器无效。
- **E-05**：`sendNormalMessage` 直接就地改写调用方传入的 `MessageProperties`（`service/impl` 第 96–104 行：contentType、contentEncoding、consumerTag、headers、timestamp、messageId），并用**同一实例**构造 `Message`（第 104 行），无防御性拷贝。
- **E-06**：`queueCache` 仅以队列名为键（`service/impl` 第 37 行），只在 `deleteQueue` 时移除（第 167 行），不感知 Broker 侧删除、Broker 重启、其他实例声明；`createQueue`（第 128–131 行）与 `queueExists`（第 237–239 行）命中缓存时**不发起任何 AMQP 调用**即返回成功/存在。
- **E-07**：`createQueue` 命中缓存时无条件用本次入参重建 `Queue` 对象（第 130、136 行），因此 `durable/exclusive/autoDelete/arguments` 与首次声明不同也不会重新声明，调用方误以为属性已生效。
- **E-08**：`createTemporaryQueue`（第 159–161 行）以 `exclusive=true, autoDelete=true` 通过共享 `RabbitAdmin` 连接声明；排他队列绑定声明连接，连接关闭即删除且不能被其他连接消费，返回的 `Queue` 对文档用途不可用。
- **E-09**：`createQueue` 失败返回 `null`（第 147 行）；`deleteQueue`/`purgeQueue`/`createExchange`/`bindQueueToExchange`/`unbindQueueFromExchange` 失败返回 `false`（第 176、263、274、287、300 行）；`setupMessageChannelAsync` 异常时以 `false` 正常完成而非 `exceptionally`（第 228–232 行）；`sendNormalMessage` 抛 `AmqpException`；参数校验用 `Objects.requireNonNull`（第 85–89 行）在 `try` 外抛 NPE。同一 API 家族存在 4 种失败表达。
- **E-10**：`enhanceMessageWithTracing` 调用 `GXTraceIdContextUtils.generateTraceId()`（第 188 行），该方法**每次生成新 UUID**（`GXTraceIdContextUtils:66-74`），从不读取 MDC 中已有 traceId，并使用 `getTraceId()` 之外的重载；跨服务链路必然断开。
- **E-11**：`GXDefaultRabbitMQQueueListenerImpl.process` 捕获 `AmqpException` 与 `Exception` 后仅记录日志（第 39–43 行），`processJsonMessage` 解析失败仅 `warn`（第 59–61 行），方法正常返回 → 消息被 ack，毒消息永久丢失，无 reject/requeue/DLX。
- **E-12**：`queueOperationLock` 在持有期间执行 `rabbitAdmin.declareExchange/declareQueue/declareBinding`（第 313–338 行）与 `deleteQueue`（第 166–168 行）等网络 IO，全部资源声明被串行化；`deleteQueue` 先移除缓存再删除 Broker（第 167–168 行），删除失败时缓存与真实状态相反。
- **E-13**：`createQueue` 的锁内二次检查（第 134–137 行）与 `setupMessageChannel` 外层锁（第 313 行）嵌套复用同一把 `ReentrantLock`，功能正确但把"按队列加锁"退化为"全局加锁"。
- **E-14**：`getVirtualThreadPoolTaskScheduler`（第 52–70 行）在每次触发时新建 `newVirtualThreadPerTaskExecutor()` 并在调度线程上 `.get()` 阻塞等待（第 58–60 行）；`ThreadPoolTaskScheduler` 未设置 `poolSize`（默认 1），所有异步回复/延迟任务实际串行；`InterruptedException` 被包装为 `RuntimeException` 未恢复中断位；调度器无 `destroy()` 调用路径。
- **E-15**：虚拟线程分支的判定为 `JavaVersion.getJavaVersion().isEqualOrNewerThan(JavaVersion.SEVENTEEN)`（第 195 行），而 `Executors.newVirtualThreadPerTaskExecutor()` 需要 JDK 21；JDK 17–20 会在运行期抛 `NoSuchMethodError`（当前基线 21，但判定与实际能力不符）。
- **E-16**：异步执行器 `coreSize`/`maxSize`/`queueCapacity` 来自 `@Value`（第 41–48 行），使用 `LinkedBlockingQueue(1000)` + `CallerRunsPolicy`（第 62–73 行）：队列未满时 `maxSize` 永不生效，队列满后由**调用线程**执行 Broker 声明 IO；执行器字段 `volatile` 但无 `@PreDestroy`，线程为 daemon，关闭时在途任务被丢弃且无日志。
- **E-17**：`rabbitTaskScheduler`（第 193–229 行）以 `TaskScheduler` 类型暴露且无 `@ConditionalOnMissingBean`，会顶掉 Spring Boot 默认 `taskScheduler`，使全局 `@Scheduled` 落到 `2×CPU`、非 daemon、`awaitTerminationSeconds=180` 的线程池上。
- **E-18**：回调装配在每次回调时通过 `GXSpringContextUtils.getBean(Class)` 查找实现（第 110、126、139 行），属于每回调一次的上下文查找；返回/确认/恢复三个回调只用日志兜底，`GXRecoveryCallback` 依赖 `RetryTemplate` 才会触发，而模板从未设置 `retryTemplate`（第 92–152 行），`spring-retry` 也不在 classpath，**恢复回调是死路径**，`leaf-base-retry` 依赖（`pom.xml` 第 28–32 行）零引用。
- **E-19**：消息体由 Hutool `JSONUtil.toJsonStr` 序列化（第 104 行），模板却装配 `JacksonJsonMessageConverter` + `DefaultClassMapper.trustedPackages("cn.hutool.core","cn.maple")`（config 第 98–106 行），发送时不写 `__TypeId__`；同一消息在生产端用 Hutool 格式化、消费端用 Jackson 3 反序列化，日期/数字/转义规则不一致即失败。
- **E-20**：模块所有日志文案均为中文（config 第 114、120、130、143、146、211、226、253 行；service/impl 第 75、110、114、117、120、129、142、146、170、172、176、210、229、251、259、262、270、273、282、286、295、299、308、317、323、329、334、341 行；listener 第 37、40、42、57、60、65、72、74 行），违反 `AGENTS.md` §3.1/§10 与宪法 §VII"日志优先 ASCII"。
- **E-21**：模块无 `src/main/resources`，无 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`；`GXRabbitMQConfig` 是 `@Configuration` 且强制 `@EnableRabbit`，组件仅靠下游 `@ComponentScan("cn.maple")` 生效，未装配时无任何信号；`@Resource` 字段注入为必需依赖（config 第 46–50 行），配置变体缺失即启动失败且报错不指向根因。
- **E-22**：死代码与残留：`handleComplexJsonMessage`（listener 第 68–77 行，含 `Thread.sleep(100)`）无调用方；listener 第 58 行注释掉的异步调用；config 第 258–273 行注释掉的 `connectionFactory` Bean；两个配置类均标注 `@SuppressWarnings("all")`。
- **E-23**：`config/META-INF/additional-spring-configuration-metadata.json` 仅有 8 条历史键，无 `maple.framework.mq.rabbitmq.*`、`maple.framework.rabbitmq.thread-pool.*`、`spring.rabbitmq.connection-limit` 等本模块配置项，IDE 无提示。
- **E-24**：`pom.xml` 无 test 依赖；仓库既有测试基线不可直接复用（`spring-boot-starter-test` 仅在根 POM `dependencyManagement` 声明，需模块显式引入）。

发现清单（分级）：

- **F-01（高）双配置源名义存在、实际只有一条且标错来源**：本地源永不装配（E-01），Nacos 源从不拉取 `rabbit.yml`、`autoRefreshed` 无效（E-02），外置 `config/*/rabbit.yml` 无法被 `classpath:` 解析（E-03），启动日志仍宣称使用 Nacos（E-02）。下游按文档配置 `spring.rabbitmq.*` 到 `rabbit.yml` 时，实际只有 `application.yml` 生效。
- **F-02（高）队列缓存与 Broker 永久脱钩**：缓存只增不减、只按名比较、命中即跳过声明（E-06、E-07）；Broker 重启、队列被运维删除、多实例声明差异都会让"创建成功"变成谎报，随后发送消息被静默丢弃（`mandatory` 未配置时无返回）。
- **F-03（高）"临时队列"语义错误**：排他队列通过共享 Admin 连接声明，连接回收即删除，其他连接无法消费（E-08）。
- **F-04（高）消费端毒消息静默丢失**：`process` 全捕获 + 正常返回 → ack；解析失败、处理失败都不重试、不进死信（E-11），与手册 §7 的 DLX/`AmqpRejectAndDontRequeueException` 建议相反。
- **F-05（高）追踪链路断裂且每消息新建 UUID**：不复用 MDC 中的 traceId（E-10），一条消息一个新 traceId，无法与请求日志关联。
- **F-06（中）调用方 `MessageProperties` 被就地改写，并发共享即串扰**：同一 DTO 在多线程复用时 header、timestamp、messageId 互相覆盖；`consumerTag` 是消费侧属性被写到发送消息上（E-05）。
- **F-07（中）失败语义四套并存且不可区分**：`null` / `false` / `AmqpException` / NPE，失败原因只剩日志，"队列不存在"与"Broker 不可达"无法区分（E-09）。
- **F-08（中）通道创建部分失败无补偿、无定位信息**：交换机成功、队列失败时残留交换机；绑定失败时残留队列；`createExchange` 吞异常导致"已存在但类型不同"被误报为创建失败（E-09 相关路径）。
- **F-09（中）全局锁跨越网络 IO，删除顺序错误**：所有声明串行、RTT 叠加；删除失败后缓存与真实状态相反（E-12、E-13）。
- **F-10（中）虚拟线程调度器实际串行、不可关闭、版本判定越界**：并发度为 1、每次触发新建执行器、无 destroy、JDK 17–20 会 `NoSuchMethodError`（E-14、E-15）。
- **F-11（中）异步执行器参数失真、无优雅停机**：`maxSize` 无意义，`CallerRunsPolicy` 把 Broker IO 反压到调用线程，关闭丢任务（E-16）。
- **F-12（中）序列化双栈、类型信息缺失**：Hutool 生产 / Jackson 消费，`Dict` 强绑定调用方，无 `__TypeId__`（E-19）。
- **F-13（中）`rabbitTaskScheduler` 劫持全局 `TaskScheduler`**：影响所有 `@Scheduled` 与 Boot 默认调度（E-17）。
- **F-14（中）恢复回调是死代码、`leaf-base-retry` 未使用**：文档承诺的"发送重试与恢复"未接线（E-18）。
- **F-15（中）零测试基线**：`mvn test` 0 用例、无 test 依赖（E-24），任何修复都没有回归保护；这也直接阻碍用户要求的"功能完备性测试"。
- **F-16（中）能力完备性缺口未登记**：无批量发送、无延迟/优先级/TTL、无发布确认结果面、无消费者重试与死信治理、无消息大小前置校验、无指标，既未实现也未在 `AGENTS.md`/手册登记"不支持范围 + 替代方案"（宪法附加约束 §9）。
- **F-17（低）每消息固定开销偏高**：UUID + Environment 查询 + Hutool 序列化 + debug 日志 + 属性改写 + 1 RTT，确认/回退路径每次 `getBean`（E-10、E-18、E-20）。
- **F-18（低）日志非 ASCII 且级别不当**：全部中文文案；资源声明每成功一次一条 `info`；发送"成功"日志在 Broker 确认前输出（E-20）。
- **F-19（低）死代码与残留注释**（E-22）。
- **F-20（低）装配与可观测性缺失**：无自动装配注册、字段注入为硬依赖、`@EnableRabbit` 强制开启、未装配无信号（E-21）。
- **F-21（低）配置元数据缺失、参数无前置校验**：无 metadata（E-23）；队列名长度/字符、消息体大小、routingKey 长度均未校验，错误全部下沉为 Broker `PRECONDITION_FAILED`/channel 关闭。
- **F-22（低）文档与实现大面积漂移**：手册 §2.3 称本地源前缀 `maple.framework.mq.rabbitmq`、条件基于 `ConfigService`（实际 `spring.rabbitmq` + `NacosConfigurationProperties`）；§2.7 称 `GXRecoveryCallback.recover(RetryContext)`（实际 `Throwable`）；§2.6 称队列名由 `GXSpELToolUtils` 计算（实际为属性占位符链）；§3.2 的配置块整体不绑定（仅 `enable` 与 `default-queue-name` 被读取）；§6.1 称"通过 Builder 创建不可变对象"（实际 `@Data` 可变 DTO）。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：`RabbitTemplate`、`RabbitAdmin` 由 Spring AMQP 提供且线程安全；服务实现除 `queueCache`、`rabbitMqAsyncExecutor` 外无共享可变状态，`volatile` + `synchronized` 双检锁写法正确（第 50–81 行）。
- **P-02**：发送路径显式设置 UTF-8 编码与 `application/json` 内容类型（第 98–99 行），与后续序列化方式一致。
- **P-03**：`DefaultClassMapper.setTrustedPackages("cn.hutool.core","cn.maple")`（config 第 99 行）收窄了反序列化白名单，避免任意类实例化。
- **P-04**：确认/回退/恢复回调分别用 `try-catch` 隔离（config 第 109–121、125–134、138–147 行），业务回调异常不会破坏发送主链路。
- **P-05**：`createQueue` 的缓存双检 + `ReentrantLock` 保证同一队列在单进程内不会重复声明（第 128–140 行），并发下无重复声明风暴（但存在 F-02/F-09 的代价）。
- **P-06**：模块可在当前基线下编译通过（ErrorProne + NullAway 无错误），`mvn -o -B -pl leaf-base-rabbitmq test` 为 `BUILD SUCCESS`。
- **P-07**：10 个包全部提供 `package-info.java` 且统一 `@NullUnmarked`，为后续补 `@NullMarked`/`@Nullable` 提供了基线。

## Clarifications

### Session 2026-10-25

- Q: 本次交付是"只出审查结论"还是"结论 + 直接修复 + 测试"？ → A: 结论、修复、测试三者都是交付物。用户明确要求"修改完代码之后做功能完备性的测试"，因此修复必须落地并有可执行测试证明。
- Q: 修复是否允许改变 `GXSendRabbitMQService` 的公开签名与既有失败语义？ → A: 默认不允许。保留全部既有方法签名、配置键、异常类型语义；修正"返回 `null`/静默 `false`"这类缺陷时，以补齐 `@Nullable` 契约、显式异常或新增重载的方式实现，并在提交说明中写清兼容性。
- Q: 真实验证是否使用仓库 `config/*/rabbit.yml` 中的 `192.168.56.x` 与 `britton` 凭据？ → A: 不使用、不写入任何新文件。真实验证一律由环境变量（`RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`/`RABBITMQ_VHOST`）门控，缺失时用例 `assumeTrue` 跳过并在报告中登记"未验证项"。
- Q: 双配置源如何收敛？ → A: 保留 `spring.rabbitmq` 前缀与 `maple.framework.mq.rabbitmq.enable` 语义不变；两种来源必须**真实生效且互斥**，启动时打印一条可检索的实际来源日志；不新增"必须配置否则启动失败"的键。
- Q: 消费端失败如何处置？ → A: 分层：不可解析/空消息按"不可重试"处理（记录可检索日志 + 计数，不 ack 后静默丢弃）；业务处理异常按"可重试"处理（reject/requeue 或转死信），默认不改变既有 ack 行为之外的效果，具体策略在 `AGENTS.md` 登记。
- Q: 日志语言？ → A: 本模块所有日志文案改为 ASCII（`AGENTS.md` §3.1 强制），面向用户的异常消息仍可用中文。

### Session 2026-10-25（实施期）

- Q: FR-018 要求"序列化 MUST 单一一致"，是否意味着出站也要改成 Jackson？ → A: 否。实测 Hutool 将 `java.util.Date` 与 `LocalDateTime` 均写为毫秒时间戳，改用 Jackson 会改变既有 wire format（违反宪法 II）。最终方案：出站保持 Hutool（唯一出站序列化器）、入站类型化转换统一用共享 `JsonMapper`，并新增"Jackson 能读取 Hutool 输出"的兼容性用例。
- Q: 实施期新发现是否纳入本次交付？ → A: 是。新增 3 条生产阻断级发现：`jackson-datatype-jsr353` 触发 Boot `JsonMapper` ServiceLoader 失败（F-23）、`DefaultClassMapper` 信任包未覆盖 `cn.hutool.core.lang` 子包（F-24）、两个 `RabbitProperties` Bean 导致 `RabbitAutoConfiguration` 注入歧义（F-25）；均已在本次修复并登记于 `review-report.md`。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 审查结论可复核、可定位到代码 (Priority: P1)

框架维护者要决定"该模块能否继续在生产使用、哪些问题必须先修"，需要每条结论都能跳到文件与行号，并能在本地复现。

**Why this priority**: 结论不可复核时，后续修复与排期都建立在猜测上。

**Independent Test**: 随机抽取一条发现，按报告给出的文件/行号/命令复现；高风险项必须能构造故障现场（例如先 `createQueue` 再用不同属性调用一次，观察是否重新声明）。

**Acceptance Scenarios**:

1. **Given** 一条"队列缓存永不失效"的结论，**When** 用同一队列名先 `durable=true` 再以 `durable=false` 调用 `createQueue`，**Then** 第二次不发起声明且返回的对象属性与 Broker 实际不一致，与结论一致。
2. **Given** 一条"本地配置源永不装配"的结论，**When** 启动 Spring 上下文并查询 Bean，**Then** `GXLocalRabbitMQProperties` 不存在、`GXNacosRabbitMQProperties` 存在，且 `rabbit.yml` 中的 marker 键读不到。
3. **Given** 一条"恢复回调不触发"的结论，**When** 制造可重试的发送失败，**Then** 只有 error 日志，`GXRecoveryCallback` 实现的方法不被调用。

---

### User Story 2 - 修复后发送、声明、消费三条链路语义可信 (Priority: P1)

接入方需要明确知道：什么情况下消息确实可路由、资源确实已声明、消费失败会怎样；不能再用"返回 `true` 但实际没声明"这种结果。

**Why this priority**: 这是模块存在的意义；语义不可信时，下游所有可靠性设计都失效。

**Independent Test**: 对每个公开方法构造成功、失败、异常、空值、边界输入，断言返回值/异常/副作用三者一致；对队列声明做"Broker 侧删除后再次调用"的回归。

**Acceptance Scenarios**:

1. **Given** 队列曾被声明且在 Broker 侧被删除，**When** 调用 `createQueue`/`queueExists`，**Then** 系统重新声明或明确报告不存在，不得仅凭缓存返回成功。
2. **Given** 以不同 `durable/exclusive/autoDelete/arguments` 重复调用 `createQueue`，**Then** 要么重新声明并生效，要么明确失败并说明冲突，不得静默返回。
3. **Given** 一条无法解析的消息被消费，**Then** 消息不会被 ack 后静默丢弃，且有可检索日志与计数。
4. **Given** 参数为 `null`/空白/超长队列名，**Then** 抛出带明确语义的业务异常且不触达 Broker。

---

### User Story 3 - 并发与上下文安全可验证 (Priority: P1)

维护者需要知道：并发发送时消息属性是否串扰、追踪号是否可关联、声明路径是否死锁或串行到不可用。

**Why this priority**: 这类缺陷只在并发下暴露，上线后表现为"消息内容错乱""排查断链"，复现成本极高。

**Independent Test**: 16 线程复用同一 DTO 发送并在发送后立刻改写字段，逐条校验 `X-Trace-Id` 与 payload 的对应关系；32 线程同时声明同一队列，统计声明次数。

**Acceptance Scenarios**:

1. **Given** 同一 DTO 被 16 线程并发复用，**When** 全部发送完成，**Then** 每条消息的 header 与 payload 只属于自己，无交叉覆盖。
2. **Given** 调用方线程已存在 traceId，**When** 发送消息，**Then** 消息头携带该 traceId，不生成新 ID。
3. **Given** 32 线程并发声明同一队列，**Then** Broker 侧只发生 1 次声明，且无死锁、无重复失败。
4. **Given** 上下文重复启动/关闭 3 次，**Then** 模块创建的线程数不累积。

---

### User Story 4 - 性能问题定量化且不设虚假门槛 (Priority: P2)

维护者需要判断该模块能否支撑目标吞吐。结论必须给出可复算口径：每消息固定开销项、并发下的时延分布、锁持有时长、线程池实际并发度。

**Why this priority**: 无口径的性能结论无法转化为可验收的修复。

**Independent Test**: 按报告口径在相同环境重跑修复前后两组采样，断言固定开销项减少、调度器并发度从 1 提升、声明路径不再被单锁串行。

**Acceptance Scenarios**:

1. **Given** 修复前后各发送 500 条消息，**When** 统计每消息固定开销项，**Then** 修复后不高于修复前，并给出逐项清单。
2. **Given** 多个异步任务同时排队，**When** 观察调度器实际并发，**Then** 不再固定为 1。
3. **Given** 10 个不同队列并发声明，**When** 观察锁等待，**Then** 不因单一全局锁相互阻塞（同队列仍互斥）。

---

### User Story 5 - 功能完备性测试可执行、可复核 (Priority: P1)

用户要求"修改完代码之后一定做功能完备性的测试"。维护者需要一套覆盖成功/失败/异常/空值/边界/并发/装配的测试，并能在无 Broker 环境默认通过、在有 Broker 环境做真实验证。

**Why this priority**: 这是本次交付的硬性验收条件；没有测试即视为未完成。

**Independent Test**: 在干净环境执行模块级命令，统计用例数与失败数；在提供 Broker 环境变量后执行集成用例，统计真实声明/发送/消费结果。

**Acceptance Scenarios**:

1. **Given** 未提供任何 Broker 环境变量，**When** 执行 `mvn -o -B -pl leaf-base-rabbitmq test`，**Then** 全部用例通过，集成用例自动跳过且不报错。
2. **Given** 提供了 Broker 环境变量，**When** 执行集成用例，**Then** 队列/交换机/绑定/发送/消费路径真实通过，并在报告中记录样本量。
3. **Given** 连续执行 3 次全量测试，**Then** 结果一致，无偶发失败。

---

### User Story 6 - 契约、文档与能力边界一致 (Priority: P3)

使用方依赖 `AGENTS.md`、开发手册与方法签名判断行为；文档与实现不一致处必须收敛，不支持的能力必须显式登记。

**Why this priority**: 不直接导致运行时错误，但会持续制造错误配置与错误预期。

**Independent Test**: 逐条核对手册 §2–§7 的声明与实现，给出"一致 / 不一致 / 未覆盖"判定；核对缺失的官方能力是否已登记。

**Acceptance Scenarios**:

1. **Given** 手册声明的配置前缀与前缀下的键，**When** 核对实现，**Then** 100% 声明与实现一致，或已在手册修正。
2. **Given** 批量发送、延迟消息、死信治理、指标等未实现能力，**When** 查阅 `AGENTS.md`/手册，**Then** 已登记"不支持范围 + 原因 + 替代方案"。
3. **Given** 日志扫描，**Then** 模块内 0 条非 ASCII 日志文案。

---

### Edge Cases

- `queueName` 为 `null`、空白、含空格/中文、超过 255 字节；`routingKey` 为 `null`/空白/超长；`exchange.getName()` 为空白。
- `arguments` 为 `null`、含 `null` 值、含不可序列化值；同一队列以冲突属性二次声明。
- 队列在 Broker 侧被删除 / Broker 重启 / vhost 变更后再次调用声明方法。
- 连接不可达、认证失败、vhost 不存在、channel 被 Broker 关闭（`PRECONDITION_FAILED`）。
- 消息体为空、超大、含非 UTF-8 字节；`data` 为 `null` 或包含 `LocalDateTime`/`BigDecimal`/嵌套集合。
- `messageProperties` 为 `null`；同一实例被重复使用；调用方在发送后立即修改。
- `mandatory=true` 但无可路由队列；`publisher-returns=true` 与 `false` 两种组合。
- 消费端：空消息体、非 JSON、JSON 数组/标量、超大 JSON、处理抛 `AmqpException`/运行时异常。
- 启用 `maple.framework.mq.rabbitmq.enable=true` 但默认队列不存在。
- 上下文关闭时仍在途的异步声明任务；上下文重复启动。
- 并发声明同一队列、并发声明不同队列、并发 `deleteQueue` 与 `createQueue`。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 22 个源码文件（12 个生产类型 + 10 个 `package-info.java`）；每个文件 MUST 有明确判定（正确 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（正确性 / 线程安全 / 性能）、级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件、影响面与证据。
- **FR-003**: 每条发现 MUST 附带复现方式（既有用例、新增回归用例设计或明确构造步骤）；仅凭阅读的判断 MUST 标注为"静态判定，待上下文验证"。
- **FR-004**: 修复 MUST 只触碰 `leaf-base-rabbitmq` 模块；确需跨模块时 MUST 先说明原因、受影响模块与最小改动集。
- **FR-005**: 修复 MUST NOT 改变 `GXSendRabbitMQService` 既有方法签名、配置键语义、异常类型语义与序列化契约；若无法避免语义修正，MUST 以新增重载/开关/`@Nullable` 契约声明的方式实现，并在模块文档与提交说明中给出兼容说明。
- **FR-006**: 队列声明结果 MUST 与 Broker 真实状态一致：缓存命中 MUST NOT 作为"队列存在"的唯一依据；Broker 侧删除后 MUST 能重新声明或明确报告不存在；同一队列以不同属性二次声明 MUST 重新声明或明确失败。
- **FR-007**: 排他/临时队列 MUST 以可被目标消费者连接使用的方式声明，或 MUST 显式声明"不支持临时队列"并从公开能力中移除，MUST NOT 返回不可用的 `Queue`。
- **FR-008**: 消费失败 MUST 分层处置且可观测：不可解析消息 MUST NOT 在 ack 后静默丢弃；业务异常 MUST 触发可配置的 reject/requeue 或死信路径；两种路径 MUST 有可检索日志与计数。
- **FR-009**: 消息追踪 MUST 优先复用调用方上下文中的 traceId，仅在缺失时生成；MUST NOT 每条消息无条件新建 UUID；MUST NOT 每条消息重新查询 `spring.application.name`。
- **FR-010**: 发送 MUST NOT 就地改写调用方传入的 `MessageProperties`；MUST 在发送前对可变 DTO 做快照或防御性拷贝；`consumerTag` 等消费侧属性 MUST NOT 写入发送消息。
- **FR-011**: 失败表达 MUST 统一：参数非法 MUST 抛 `GXBusinessException`（或既有同类业务异常）而非 NPE；可恢复的 Broker 异常 MUST 抛 `AmqpException` 子类；返回 `null`/`false` 的路径 MUST 补齐 `@Nullable` 契约并在 Javadoc 说明，或改为异常；同一 API 家族 MUST 只有一种失败风格。
- **FR-012**: 资源声明 MUST NOT 在持有全局锁的情况下执行阻塞网络 IO；同队列 MUST 互斥、不同队列 MUST 可并行；锁内 MUST NOT 调用可能回调业务代码的方法。
- **FR-013**: `deleteQueue` MUST 保证缓存与 Broker 状态最终一致（先成功删除再失效缓存，或失败不改缓存）。
- **FR-014**: 通道创建 MUST 具备失败补偿或明确的"部分创建"语义：交换机/队列/绑定任一失败时 MUST 说明已完成步骤，并提供清理路径或幂等重试。
- **FR-015**: 调度器 MUST NOT 把并发度压到 1：异步回复/延迟任务 MUST 可在多个线程并行执行；虚拟线程分支 MUST 按实际 JDK 能力判定（≥21）；MUST NOT 在调度线程上阻塞等待子任务完成。
- **FR-016**: 模块创建的所有线程池/调度器 MUST 有明确释放路径（`@PreDestroy`/`DisposableBean`/关闭钩子），关闭时 MUST NOT 静默丢弃在途任务。
- **FR-017**: 异步执行器 MUST 使配置的 `max-size`、`queue-capacity` 真实生效（或修正配置语义并在文档声明）；拒绝策略 MUST NOT 把 Broker IO 反压到 Web/事件线程，或 MUST 在文档中显式声明该行为。
- **FR-018**: 序列化 MUST 单一一致：生产与消费使用同一栈与同一格式；发送 MUST 写入必要的类型信息，或明确以 `Dict`/`Map` 为统一载荷契约；MUST 有跨栈兼容性测试。
- **FR-019**: 双配置源 MUST 真实生效且互斥：本地源与 Nacos 源的装配条件 MUST 与运行期实际加载结果一致；启动 MUST 输出一条可检索的实际来源日志（不含敏感值）。
- **FR-020**: 模块所有日志文案 MUST 为 ASCII；MUST 删除发送成功路径上的误导性日志（在 Broker 确认前宣称成功）；资源声明成功日志 MUST 降级为 debug。
- **FR-021**: 死代码（未调用的私有方法、注释掉的 Bean/调用）MUST 删除；未使用的依赖 MUST 删除或接线使用；`@SuppressWarnings("all")` MUST 移除或收窄。
- **FR-022**: 模块装配 MUST 有可观测性：未装配/装配失败 MUST 有明确日志；`@EnableRabbit` 与 `TaskScheduler` Bean MUST NOT 无条件覆盖下游默认行为，或 MUST 提供关闭开关。
- **FR-023**: 新增/修改的配置项 MUST 登记 `additional-spring-configuration-metadata.json`；新增或修改的 API MUST 补齐 JSpecify 空值标注（`@NullMarked`/`@Nullable`）。
- **FR-024**: 修复 MUST 附带"旧代码失败、新代码通过"的回归测试；测试 MUST 先于或伴随实现提交。
- **FR-025**: 测试 MUST 覆盖模块全部公开方法（`GXSendRabbitMQService` 13 个方法）与 4 个装配/监听/配置类型，每个方法至少覆盖成功、失败/异常、空值或边界三类输入。
- **FR-026**: 测试 MUST 包含 Spring 上下文测试（`@SpringBootTest` 或等价），断言 Bean 装配、条件开关、配置绑定、消息转换器、回调装配与监听器启停。
- **FR-027**: 测试 MUST 包含并发测试：≥16 线程 × ≥100 条消息的并发发送（校验 header/payload 不串扰）、≥32 线程并发声明同一队列（校验只声明 1 次）、并发声明不同队列（校验不相互阻塞）。
- **FR-028**: 测试 MUST 包含真实 Broker 集成测试，以 `integration` 标签 + 环境变量门控；默认构建 MUST 自动跳过且不失败；MUST NOT 引入 Testcontainers 等新容器依赖；MUST NOT 在源码/测试/配置模板中写入凭据。
- **FR-029**: 测试 MUST 可重复：全量用例连续执行 3 次结果一致；不得依赖执行顺序、时间窗口或外部状态残留。
- **FR-030**: 交付 MUST 记录可复算的验证证据：用例总数、通过/跳过/失败数、执行命令、并发样本量、性能口径与修复前后对比。
- **FR-031**: 未覆盖或无法自动化的验证 MUST 在交付说明中登记为"未验证项"，附带手工命令、样本规模与观察指标；MUST NOT 用"已测试"概括。
- **FR-032**: 能力完备性 MUST 按宪法附加约束 §9 处理：批量发送、延迟/优先级/TTL、发布确认结果面、消费者重试/死信、指标、消息大小前置校验等官方能力，MUST 补齐或 MUST 在 `AGENTS.md`/手册登记"不支持范围 + 原因 + 替代方案"。
- **FR-033**: `AGENTS.md` 与 `docs/leaf-base-rabbitmq开发手册.md` MUST 与修复后的实现一致，至少覆盖：配置前缀与键、配置源、监听器队列名来源、回调签名、失败语义、测试命令。

### Key Entities

- **审查发现（Finding）**：编号、级别、维度、位置、触发条件、影响、证据、复现方式、回归测试设计、修复建议、兼容性影响。
- **方法语义矩阵**：13 个公开方法 × 成功 / 失败 / 异常 / 空值 / 边界 × 返回值 / 异常 / 副作用。
- **线程安全矩阵**：共享对象（`queueCache`、`ExecutorService`、`TaskScheduler`、DTO/`MessageProperties`、`RabbitTemplate` 回调）× 共享范围 × 保护手段 × 收敛方式。
- **性能口径**：每消息固定开销项清单、锁持有时长、调度器实际并发度、顺序/并发时延分位（样本量与分位值）。
- **测试矩阵**：单元 × 上下文 × 并发 × 集成（环境变量门控）× 重复执行。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 22 个源码文件 100% 有判定，0 个"未审查"；高危发现（F-01～F-05）100% 具备可构造的故障现象与回归用例设计。
- **SC-002**: 所有已确认缺陷 100% 有对应回归用例，且在修复前可失败、修复后通过；回归用例在无 Broker 环境可执行。
- **SC-003**: 模块级测试基线从 0 提升到完整套件：用例数 ≥ 60，覆盖 13 个公开方法与 4 个装配/监听/配置类型，通过率 100%，跳过项仅为集成用例；连续 3 次执行结果一致。
- **SC-004**: 并发送测 16 线程 × 100 条消息时，消息头与载荷的对应关系错误数为 0；32 线程并发声明同一队列时 Broker 声明次数为 1，死锁为 0。
- **SC-005**: 线程安全结论对"共享可变状态、生命周期、竞态"三类各给出唯一定性；修复后上下文重复启动 3 次，模块自建线程数不增长（可观测线程名计数）。
- **SC-006**: 性能口径 100% 可复算：修复前后各 ≥ 500 条样本，固定开销项逐项对应代码位置；修复后每消息固定开销项数量不高于修复前，且异步调度实际并发度 > 1。
- **SC-007**: 双配置源结论对"是否生效 / 是否互斥 / 是否可观测"三项各给出唯一定性，并有 Spring 上下文证据；启动日志可检索到实际来源，0 条敏感值。
- **SC-008**: 消费失败路径 100% 有明确处置（重试/死信/丢弃 + 日志 + 计数），0 条"仅记录日志后 ack"的静默路径。
- **SC-009**: 模块内非 ASCII 日志文案数量为 0（可脚本统计）；发送成功日志 0 条早于 Broker 确认。
- **SC-010**: `mvn -o -B -pl leaf-base-rabbitmq test` 在无 Broker 环境一次通过；ErrorProne/NullAway 0 错误；`AGENTS.md` 与手册中 100% 的配置键/回调签名/队列名来源与实现一致。

## Assumptions

- 目标运行形态为 JDK 21 + Spring Boot 4.1.1 + Spring AMQP 4.1.1，下游通过 `@GXEnableLeafFramework` 或等价包扫描装配本模块。
- 本次交付包含生产代码修复，修复范围限定在 `leaf-base-rabbitmq`；`leaf-base-framework` 的 `GXTraceIdContextUtils`、`GXCommonUtils` 等公共工具不修改，追踪修复通过在本模块内选择正确 API 实现。
- 真实验证依赖外部 Broker，是否可用由环境变量决定；无环境时以 Spring 上下文 + Mock AMQP 组件覆盖，并在报告中登记未验证项。
- `config/*/rabbit.yml` 与 `config/application.rabbitmq.yml` 属仓库示例外置配置，不作为模块资源打包；其配置键需与修复后的绑定前缀一致。
- 既有测试基线为 0（模块无 `src/test`），因此"回归用例先失败"通过在修复前先提交测试并记录失败输出证明，而非依赖历史用例。
- 性能验收不设绝对吞吐/时延门槛（受硬件与网络影响），只以"固定开销项、锁持有范围、调度并发度、同环境重跑同量级"为口径。
- 消费者容器、重试拦截器、死信队列的具体 Broker 侧拓扑由部署方提供；模块只负责"可配置、可观测、可接入"，不接管全局治理。
- 不引入新的运行时依赖；仅在 `pom.xml` 增加 test 作用域依赖（`spring-boot-starter-test`、`spring-rabbit-test`、`junit-jupiter`）。
