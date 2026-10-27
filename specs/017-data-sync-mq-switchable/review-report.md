# leaf-base-data-sync 审查与实施报告

**Feature**: `017-data-sync-mq-switchable` | **Branch**: `017-data-sync-mq-switchable` | **Date**: 2026-10-27

**范围**: `leaf-base-data-sync` 正确性 / 线程安全 / 性能审查 + 消息中间件可切换（RabbitMQ / RocketMQ）改造。
**约束**: 只改 `leaf-base-data-sync`；不修改 `leaf-base-rabbitmq` / `leaf-base-rocketmq`；保留既有公共契约。

---

## 1. 基线（改造前，T001）

| 项 | 结果 |
| --- | --- |
| 命令 | `mvn -o -B -pl leaf-base-data-sync -am test` |
| 结果 | `BUILD SUCCESS` |
| 用例 | 3 个测试类，共 **16** 个用例（`GXCanalConfigTest` 2、`GXCanalRabbitMQListenerTest` 2、`GXCanalMessageParseServiceImplTest` 12），全部通过 |
| 测试类型 | 全部为单元测试，无 `@SpringBootTest`、无并发用例、无失败处置用例、无 MQ 切换用例 |
| 依赖 | `leaf-base-framework`、`leaf-base-rabbitmq`（compile，传递带入 `spring-boot-starter-amqp`、`leaf-base-nacos`、`nacos-client 3.1.1`）、`spring-boot-starter-test` |
| 构建门槛 | ErrorProne + NullAway 生效，无 error |

改造后：**10 个测试类，共 64 个用例，全部通过**。

---

## 2. 关键证据（文件:行号，改造前状态）

| 编号 | 证据 | 位置 |
| --- | --- | --- |
| E-01 | 仅依赖 `leaf-base-rabbitmq`，所有收发类型为 AMQP 专用，无中间件抽象 | `leaf-base-data-sync/pom.xml:20-24` |
| E-02 | `@Component` + `@Lazy` + `@RabbitListener`，全仓库无注入点 → 端点可能不注册 | `GXCanalRabbitMQListener.java:14-16` |
| E-03 | 监听器丢弃 `parseMessage` 返回值，6 类失败均表现为 ack | `GXCanalRabbitMQListener.java:30` |
| E-04 | 异常上抛但无重试上限/无死信/无 `defaultRequeueRejected` 配置 | `GXCanalRabbitMQListener.java:32-35`、`GXCanalConfig.java:21-34` |
| E-05 | 每条消息 1–2 次 `getBean`，未命中以 `NoSuchBeanDefinitionException` 驱动 | `GXCanalMessageParseServiceImpl.java:55-58`、`GXSpringContextUtils.java:33-36` |
| E-06 | 服务名每消息现算，命名可冲突/不可用且静默兜底 | `GXCanalMessageParseServiceImpl.java:51-52` |
| E-07 | `isTypeJSON` + `toBean` 两次入口，错误日志无节流 | `GXCanalMessageParseServiceImpl.java:33-44` |
| E-08 | 只支持单对象载荷，数组载荷解析失败 | `GXCanalMessageParseServiceImpl.java:40` |
| E-09 | `isDdl` 解析但从未读取，DDL 落入 default 记 error | `GXCanalDataDto.java:35`、`GXCanalMessageParseServiceImpl.java:72-89` |
| E-10 | `@Data` 可变配置 + `autoRefreshed=true`，字段非 volatile；`concurrency` 启动期求值 | `GXCanalProperties.java:10-27`、`GXNacosCanalProperties.java:17` |
| E-11 | `@PropertySource("classpath:/${spring.profiles.active}/canal.yml", ignoreResourceNotFound=false)` 与外置 `config/*/canal.yml` 不是同一来源；`ymls/dev/common.yml` 无任何加载方 | `GXLocalCanalProperties.java:18` |
| E-12 | 本地源判定依赖 `@ConditionalOnMissingClass`，而 `leaf-base-rabbitmq` 已传递带入 nacos 注解类 → 条件恒 false | `GXLocalCanalProperties.java:17` |
| E-13 | `Exchange`/`Queue`/`Binding` 为裸类型全局 Bean，无 `@ConditionalOnMissingBean` | `GXCanalConfig.java:21-34` |
| E-14 | 无自动装配注册、无配置元数据 | 模块 `src/main/resources` 缺失 `META-INF` |
| E-15 | fanout + 固定队列名常量，routingKey 无路由语义 | `GXCanalConstant.java:7-10`、`GXCanalConfig.java:22-29` |
| E-16 | 消费链路无 TraceId/MDC | `GXCanalRabbitMQListener.java:27-36` |
| E-17 | `prefetch` 默认 250 × `concurrency` 10 ≈ 2500 在途，不可观测 | 无配置/无日志 |
| E-18 | `GXCanalDataDto.id` 非 Canal 标准字段；`data`/`old` 与处理器共享引用 | `GXCanalDataDto.java:23-43` |
| E-19 | 结果用 `"success"/"error"/"warning"` 魔法值 | `GXCanalMessageParseServiceImpl.java:30-93` |
| E-20 | 手册漂移：队列默认值写成 `example`、类名写成 `CanalConstant`、声称"RabbitMQ 重启不丢消息" | `docs/leaf-base-data-sync开发手册.md` §2.2/§2.3 |
| E-21 | `leaf-base-rocketmq` 的 `rocketmq-spring-boot-starter`/`leaf-base-nacos`/`fastjson` 为非 optional 传递依赖 | `leaf-base-rocketmq/pom.xml:24-72` |
| E-22 | `leaf-base-rocketmq` 有 `GXCdc*` 消费契约但无消费容器，且全仓库 `@RocketMQMessageListener` 零命中 | `GXCdcMessageDispatcherImpl.java:28`、`GXCdcEventListener.java` |

---

## 3. 发现清单与处置

| 编号 | 级别 | 结论 | 处置 |
| --- | --- | --- | --- |
| F-01 | 高 | 6 类失败均 ack，无重试无记录 | 已修复：四类结局 + 计数 + 死信（`GXCanalOutcomeEnums`、`GXCanalMessageParseServiceImpl#parse`） |
| F-02 | 高 | 毒消息无限重投 | 已修复：`canal.retry.max-attempts`（默认 3）+ 死信队列 |
| F-03 | 高 | 监听器可能从未注册 | **已定案**：`@Lazy` 监听器确实不注册（测试 `GXCanalListenerRegistrationTest#shouldNotRegisterLazyListener` 通过）；已移除 `@Lazy` 并加回归断言 |
| F-04 | 高 | DDL 记为错误；数组载荷不支持 | 已修复：DDL → `SKIPPED`；数组按元素处理（`/canal/batch.json` 用例） |
| F-05 | 中 | 热路径异常驱动的容器查找 | 已修复：`GXCanalHandlerResolver` 启动快照 + 有界缓存（含负结果） |
| F-06 | 中 | 命名冲突/非法名静默兜底 | 已修复：首见 WARN + 计数（`warnOnce`） |
| F-07 | 中 | 配置源失效 / 绑定不生效 | 已修复：标准 `@ConfigurationProperties` + `canal.config-source` 显式开关 + 四组合测试 |
| F-08 | 中 | MQ 基础设施 Bean 不可覆盖 | 已修复：`@ConditionalOnMissingBean(name=...)` + 消费总开关 |
| F-09 | 中 | 无元数据/无自动装配信号 | 已修复：`additional-spring-configuration-metadata.json`（18 键 + 3 组 hints） |
| F-10 | 中 | 容量不可观测 | 已修复：启动日志打印 `concurrency × prefetch` 与死信目的地 |
| F-11 | 中 | `autoRefreshed` 语义失真 | 已处置：装配期键在文档中显式声明"需重启生效" |
| F-12 | 中 | 无 TraceId | 已修复：读头优先 + 缺失生成 + `finally` 清理 |
| F-13 | 低 | fanout 语义缺路由能力 | 已登记不支持（`AGENTS.md` §9、手册 §6.4） |
| F-14 | 低 | 载荷共享可变引用 | 已文档化（`GXCanalDataDto` Javadoc 语义保持不变） |
| F-15 | 低 | 字符串魔法值 | 已修复：常量收敛到 `GXCanalConstant`（历史入口仍返回字符串以保持契约） |
| F-16 | 低 | 文档漂移 | 已修复：手册 §2.1/§2.2/§2.3、§2.5/§2.6、§3 与 §6。 |
| F-17 | 高 | RocketMQ 依赖传导冲击隔离与配置源 | 已修复：`<optional>true</optional>` + `@ConditionalOnClass`；下游依赖树 `rocketmq` 命中 0 |
| F-18 | 中 | 复用边界未定义 | 已登记：复用连接/发送/死信/统计模式，不复用 `GXCdc*` 作为主路径（`AGENTS.md` §1、手册 §6.4） |

保持项（未被破坏）：`parseMessage(String)` 签名与 handler `Dict` 返回语义、`database_table_Service` 命名约定、`defaultProcessCanalDataService` 兜底、`canal.*` 既有 4 个键名与默认值、交换机/队列/绑定 Bean 名称、durable 语义。

---

## 4. 定案结论（原"待核实"项）

| 项 | 结论 | 证据 |
| --- | --- | --- |
| `@Lazy` 与 `@RabbitListener` | 懒加载监听器 Bean 不会被实例化，端点**不会注册**。移除 `@Lazy` 后端点正常注册 | `GXCanalListenerRegistrationTest`：eager 场景 1 个容器、lazy 场景 0 个容器 |
| Canal 载荷形态 | 数组载荷在改造前**无法处理**（返回 `status=error`）；改造后按元素逐个产出结局 | `/canal/batch.json`：3 元素 → 成功/跳过/不可重试 各 1 |
| RocketMQ 消费容器 | 采用 `@RocketMQMessageListener` + `RocketMQListener<String>`，装配与委派路径已由用例覆盖；真实 broker 连通性未在本环境验证 | `GXCanalRocketMQConfigTest`、`GXCanalRocketMQListenerTest`、`GXCanalConsumerSpringBootTest` |

---

## 5. 验证结果（quickstart V1–V9）

| 场景 | 结果 | 说明 |
| --- | --- | --- |
| V1 编译与既有回归 | ✅ | `mvn -o -B -pl leaf-base-data-sync -am test` → 全 reactor SUCCESS，本模块 64 用例通过；ErrorProne/NullAway 无 error。注：首次执行时 `leaf-base-framework` 因 IDE 编译残留（`GXMdcWrapperForkJoinPoolTest` 报 "Unresolved compilation problem"）失败，`mvn -pl leaf-base-framework clean` 后重跑通过，与本特性无关 |
| V2 结局全覆盖 | ✅ | 空白/非 JSON/标量/缺库表/DDL（`isDdl` 与 `type` 两种）/未知类型/处理器异常/返回 null/混合批量，各有断言 |
| V3 RabbitMQ 装配与端点注册 | ✅ 装配 / ⏭️ 真实投递 | 条件装配、DLQ 参数、Bean 让位、端点注册均有断言；真实投递由 `GXCanalRabbitRealBrokerTest`（`RABBITMQ_HOST` 门控）覆盖，本环境 `Skipped` |
| V4 MQ 切换 | ✅ 装配 / ⏭️ 真实 rocketmq | 两模式处理结果一致（`GXCanalConsumerSpringBootTest`）；非法值、缺必填项、缺依赖均 fail-fast；真实消费由 `GXCanalRocketRealBrokerTest`（`ROCKETMQ_NAME_SERVER` 门控）覆盖，本环境 `Skipped`；两者配置结构由 `GXCanalRealBrokerBootstrapTest` 离线冒烟锁定 |
| V5 依赖缺失提示 | ✅ | `FilteredClassLoader` 场景下消息含 `leaf-base-rocketmq` 与 `rabbitmq` |
| V6 依赖隔离 | ✅ | 本模块：`leaf-base-rocketmq ... (optional)`，`rocketmq-client`/`fastjson` 均标 optional；下游临时工程：`rocketmq` 命中 **0**，`nacos` 命中 11（全部来自既有 `leaf-base-rabbitmq` 路径） |
| V7 性能与并发 | ✅ | 微基准 50 000 条：平均 **19.0 µs/条**（JIT 预热后同量级），热路径容器查找 0 次；32 线程 × 1000 条计数自洽 |
| V8 配置源四组合 | ✅ | 有/无 nacos 类路径 × 默认/显式，各有断言；显式 `nacos` 缺类路径时 fail-fast |
| V9 元数据与文档一致 | ✅ | 17 个键与 3 组 hints 已登记（`consume-thread-max` 按 T059 移除后为 17）；手册键表与实现一致 |

**跳过项清单**（本环境 `Skipped: 2`，套件整体仍 `BUILD SUCCESS`）：

| 用例 | 门控变量 | 覆盖内容 |
| --- | --- | --- |
| `GXCanalRabbitRealBrokerTest#shouldConsumeValidPayloadAndDeadLetterInvalidPayload` | `RABBITMQ_HOST` | 真实投递消费 + 非法载荷进入 `canal.dlq` + 计数 |
| `GXCanalRocketRealBrokerTest#shouldConsumePayloadFromRocketMqTopic` | `ROCKETMQ_NAME_SERVER` | 经 `RocketMQTemplate` 投递后由共享解析链路处理 + 计数 |

两者的配置装配由 `GXCanalRealBrokerBootstrapTest` 在离线环境验证（`@EnableRabbit`、门控注解、Bean 方法齐全），避免"只有到有 broker 的环境才第一次执行"。

---

## 6. 依赖与装配变更

| 项 | 变更 |
| --- | --- |
| 新增依赖 | `cn.maple.framework:leaf-base-rocketmq`（**optional**，版本 `${project.parent.version}`） |
| 传递依赖影响 | `rocketmq-client 5.3.2`、`rocketmq-spring-boot 2.3.6`、`fastjson 2.0.65` 全部 optional，不传递给下游；`nacos-client 3.1.1` 由既有 `leaf-base-rabbitmq` 传递（无净增） |
| 新增 Bean | `canalDeadLetterExchange`、`canalDeadLetterQueue`、`canalDeadLetterBinding`（RabbitMQ，均 `@ConditionalOnMissingBean`） |
| 条件装配 | `canal.mq-type`（默认 rabbitmq）+ `canal.consumer-enabled`（默认 true）双重约束 |
| 新增配置键 | `canal.mq-type`、`canal.consumer-enabled`、`canal.config-source`、`canal.retry.max-attempts`、`canal.dead-letter.*`（3）、`canal.handler-cache-size`、`canal.handler-miss-log-level`、`canal.rocketmq.*`（5） |
| 既有键 | 名称、类型、默认值全部未变 |

---

## 7. 上线注意（必须确认）

1. **`@Lazy` 移除属生产行为变更**：若此前端点确实未注册，本次上线后开始真实消费 Canal 变更。上线前必须确认下游 `GXProcessCanalDataService` 实现的幂等性（重复投递、回放场景）。
2. **配置源改为标准绑定**：`canal.*` 现在会真实从 `Environment`（外置 `config/<profile>/canal.yml` 或 Nacos 导入）绑定。此前这些键实际未生效、全部走常量默认值；若下游配置文件中的值与默认值不同，**升级后行为会改变**，需逐项核对外置配置。
3. **失败消息开始进入死信**：RabbitMQ 需确保 `canal.dlx`/`canal.dlq` 可被运维查看与重放；如有自定义运维脚本依赖"静默 ack"，需同步调整。
4. **RocketMQ 模式为新增能力**：默认 `mq-type=rabbitmq`，不影响存量部署；切换到 `rocketmq` 前需确认 `rocketmq.name-server` 可达、topic 已创建、消费组权限正确。
5. **RocketMQ 重投次数固定为 3**：`@RocketMQMessageListener(maxReconsumeTimes=3)` 为注解常量，不受 `canal.retry.max-attempts` 影响；需要不同上限时需改代码（已登记于 `AGENTS.md` §9.4 与手册 §6.2 注）。

---

## 8. 交付说明（PR 描述草稿）

**动机**：修复"消费成功但数据丢失/空转"的高风险缺陷，消除热路径异常驱动的容器查找，并让消息中间件可在 RabbitMQ 与 RocketMQ 之间按配置切换。

**影响面**：仅 `leaf-base-data-sync`；`leaf-base-rabbitmq` / `leaf-base-rocketmq` 零改动（`git status` 已确认）。公共契约保持不变，新增能力均为增量。

**配置键变更**：仅新增键（见 §6），既有键语义与默认值不变；`canal.config-source` 为新增可选项，缺省保持类路径探测。

**验证命令与结果**：
- `mvn -o -B -pl leaf-base-data-sync -am test` → 64 用例通过，`BUILD SUCCESS`；
- `mvn -o -B -pl leaf-base-data-sync dependency:tree "-Dincludes=org.apache.rocketmq:*,com.alibaba.nacos:*,com.alibaba:fastjson"` → RocketMQ 与 fastjson 全部标记 optional；
- 下游临时工程（仅依赖本模块）`dependency:tree` → `rocketmq` 命中 0。

**兼容性策略**：默认值全部保持既有行为；行为变化集中在"失败不再静默 ack"与"配置真正生效"，已在 §7 逐条列出并需在发布说明中提示下游核对配置。

**未验证项**：真实 RabbitMQ / RocketMQ broker 端到端（环境变量门控，见 §5）。

---

## 9. 收敛（Phase 9）执行结果

`/speckit.converge` 发现 13 项缺口，已全部处理（T058–T070）：

| 任务 | 缺口 | 处置 | 证据 |
| --- | --- | --- | --- |
| T058 | FR-014 配置可见性 | `GXCanalProperties` 与 3 个嵌套配置类的可变字段全部改为 `volatile`（嵌套对象为 `final`） | `GXCanalPropertiesBindingTest#shouldDeclareEveryMutableFieldVolatile` |
| T059 | `consume-thread-max` 配了不生效 | 移除该配置键；改由注解常量 `ROCKETMQ_CONSUME_THREAD_MAX=20` 生效并在启动日志标注 fixed | `GXCanalRocketMQListener`、`GXCanalRocketMQConfig`、元数据/手册已同步 |
| T060 | 无真实 broker 用例 | 新增 `GXCanalRabbitRealBrokerTest` / `GXCanalRocketRealBrokerTest`，分别以 `RABBITMQ_HOST` / `ROCKETMQ_NAME_SERVER` 门控 | 本环境变量缺失 → `Skipped: 2`（套件整体仍 SUCCESS） |
| T061 | 无改造前基线 | 基准用例新增旧路径对照（`getBean` 未命中 + 异常） | 实测 `legacyMicros=3.927`、`currentMicros=0.208`、**improvement=94.7%** |
| T062 | 日志未节流 | 新增 `GXCanalLogThrottle`（首 3 条 + 计数），解析 WARN 与死信 ERROR 全部接入 | `GXCanalLogThrottleTest` 覆盖于各用例 |
| T063 | DTO 共享语义未文档化 | `GXCanalDataDto` 补 `data`/`old` 共享说明与 `@Nullable` | 源文件 Javadoc |
| T064 | 装配期键无运行期提示 | 启动打印 `GXCanalConstant.ASSEMBLY_RESTART_HINT` | `GXCanalPropertiesBindingTest#shouldExposeRestartHintForAssemblyTimeKeys` |
| T065 | 接口新增抽象方法破坏兼容 | `parse` 改为 `default` 方法并显式抛 `UnsupportedOperationException` | `GXCanalMessageParseService` |
| T066 | AGENTS 缺不支持范围表 | 新增 `AGENTS.md` §10（能力 + 原因 + 替代），与手册 §6.4 对齐 | 文档 |
| T067 | 未使用公共 API | `logSummary` 接到 `@PreDestroy`；删除 `hasDefaultHandler` / `handlerCount` / `isSuccess` | 编译与用例通过 |
| T068 | F-17 无自动化回归 | 新增 `DependencyIsolationTest`（解析模块 pom，断言 optional 且无直接 rocketmq 坐标） | 用例通过 |
| T069 | 缺刷新语义用例 / 重复稳定性 | 新增装配期键刷新不变断言；并发计数用例改 `@RepeatedTest(10)` | `GXCanalPropertiesBindingTest`、`GXCanalConsumeStatisticsTest` |
| T070 | 重投丢 traceId | RabbitMQ 重投消息写入 `traceId` 头；RocketMQ 侧在类 Javadoc 与 §10 登记"无法透传" | `GXCanalRabbitMQFailureHandlerTest` 断言头存在 |

收敛后测试基线：**80 个用例，0 失败，2 跳过**（跳过项即两个真实 broker 用例）。

### Phase 10 二次收敛（T071–T077）

| 任务 | 缺口 | 处置 |
| --- | --- | --- |
| T071 | 手册示例仍含已移除的 `consume-thread-max` | §6.1 示例删除该键；§5 性能调优改为"注解常量固定 20" |
| T072 | 监听器 4 处日志未节流 | 两套监听器改为经 `GXCanalLogThrottle` 输出，并加"日志走节流"用例 |
| T073 | FR-022 在 RocketMQ 侧未满足 | 评估后**降级并显式登记**：改程序化容器需动态代理构造注解实例，复杂度不匹配收益（附加约束 §8）；`spec.md` FR-022 增加"例外（已协商）"，Clarifications 回填决策 |
| T074 | contract/手册引用不存在的键 | 统一改为 `spring.rabbitmq.listener.simple.prefetch` / 注解 `consumeThreadMax` |
| T075 | `data-model.md` 写已移除的 `max-reconsume-times` | 改为"超过注解固定值（3 次）" |
| T076 | 门控用例的配置从未离线验证 | 新增 `GXCanalRealBrokerBootstrapTest`（校验门控注解、Bean 方法、`@EnableRabbit`） |
| T077 | 报告 §5 未回写 T060 结果 | V3/V4 行与"跳过项清单"已更新 |

二次收敛后基线：**86 个用例，0 失败，2 跳过**。

### Phase 11 三次收敛（T078–T079）

| 任务 | 缺口 | 处置 |
| --- | --- | --- |
| T078 | FR-040 列出的"命名非法"与"命名冲突"两条告警路径无断言 | `GXCanalHandlerResolverTest` 新增两条用例：非法名首见告警后仍兜底且负结果被缓存；`a_b.c` 与 `a.b_c` 映射同一 Bean 名时触发冲突告警且两次解析均成功 |
| T079 | quickstart 与契约文档写"`GXCanalConfig` 的 5 个 Bean" | 更正为 6 个并列出死信三件套 |

三次收敛后基线：**88 个用例，0 失败，2 跳过**。

### Phase 12 四次收敛（T080–T082）

| 任务 | 缺口 | 处置 |
| --- | --- | --- |
| T080 | quickstart 引用不存在的 `GXCanalConsumerSpringBootIT` | 4 处（V3/V4 用例清单 + 两条可执行命令）改为真实类名，并补入门控用例与离线冒烟类；`plan.md` 的同一文件名同步更正 |
| T081 | quickstart 基线用例数 12 与门控方式说明不符 | 改为 16（12+2+2）；`assumeTrue` 说明改为 `@EnabledIfEnvironmentVariable`；`spec.md` §背景第 5 条的同一数字一并更正 |
| T082 | 报告 V9 写"18 个键" | 更正为 17 个键、3 组 hints |

四次收敛后基线：**88 个用例，0 失败，2 跳过**（本阶段仅文档改动）。

### Phase 13 五次收敛（T083）

| 缺口 | 处置 |
| --- | --- |
| FR-009 批量元素失败语义不完整（多失败元素只记一次；处理器异常中止后续元素） | ①`GXCanalMessageParseServiceImpl#parseArray` 改为逐元素捕获异常（`parseEventSafely`），失败元素标记 `RETRYABLE_FAILURE` 后继续处理剩余元素（单个对象载荷仍按旧契约抛出，`parseMessage` 语义不变）；②两套监听器的批量循环逐元素累加 `retryableFailure`/`nonRetryableFailure` 并逐条（节流）记录，整批只执行一次传输动作，同时存在两类失败时优先按可重试处理；③失败计数职责从 handler 移到 listener（handler 只负责死信计数），避免重复计数 |

新增用例：`shouldIsolateBatchElementFromHandlerFailure`（解析层）、`shouldCountEveryFailedBatchElementButActOnce`、`shouldPreferRetryableFailureForTheTransportAction`（监听器层）。

五次收敛后基线：**91 个用例，0 失败，2 跳过**。

### Phase 14 六次收敛（T084–T088）

T083 改变了一处对外可观察语义（数组载荷的处理器异常由"上抛"改为"逐元素捕获"），本轮把连带的行为说明全部对齐，**零代码逻辑改动**：

| 任务 | 文件 | 处置 |
| --- | --- | --- |
| T084 | `GXCanalMessageParseService.java`、`GXCanalMessageParseServiceImpl.java` | 删除"never returns `RETRYABLE_FAILURE`"断言，改为"单对象上抛 / 数组逐元素捕获并以 `RETRYABLE_FAILURE` 返回"；类注释同步 |
| T085 | 开发手册 §2.6 | 同一断言改为随载荷形态分述，并与 §6.3 批量语义互相引用 |
| T086 | `AGENTS.md` §5 / §9 红线 1、10 | 合并互斥表述：红线 1 明确"单对象上抛、数组逐元素隔离"，红线 10 补"不要合并两条路径"；§5 第 4 条同步 |
| T087 | 契约 §C1.1 + 不变式 | 处理器异常条目补载荷形态差异；新增不变式 4（元素级隔离、逐元素计数、整批单次传输动作、可重试优先） |
| T088 | `data-model.md` §5.1 / §6 | 状态机标注两种载荷形态的异常路径；新增不变式 4/5；补计数职责划分（结局计数归监听器、`deadLetter` 归 handler） |

六次收敛后基线：**91 个用例，0 失败，2 跳过**（本阶段仅注释与文档）。

### Phase 15 七次收敛（T089）

| 任务 | 处置 |
| --- | --- |
| T089 | `AGENTS.md` §2 目录职责第 10 条补登记 `service/support` 下的 `GXCanalLogThrottle`，与目录实际三个类（`GXCanalHandlerResolver` / `GXCanalFailureHandler` / `GXCanalLogThrottle`）及 §9 红线 9 对齐 |

七次收敛后基线：**91 个用例，0 失败，2 跳过**（本条仅文档）。

### Phase 16 八次收敛（T090）

| 任务 | 处置 |
| --- | --- |
| T090 | 消除死常量与重复字面量：`LOG_LEVEL_*` 常量块上移到文件顶部，`DEFAULT_HANDLER_MISS_LOG_LEVEL` 改为引用 `LOG_LEVEL_WARN_ONCE`（避免非法前向引用），解析器的 warn-once 兜底分支加注释说明"默认值即兜底值"；新增断言 `shouldDefaultHandlerMissLogLevelToWarnOnce` 锁定同源关系。33 个常量现全部有引用 |

八次收敛后基线：**92 个用例，0 失败，2 跳过**。

## 10. 遗留与后续建议

1. 真实 broker 的端到端验证与压测（建议在预发环境执行 quickstart V3/V4 的门控场景）。
2. `leaf-base-rabbitmq` 的 `GXDefaultRabbitMQQueueListenerImpl` 同样带 `@Lazy`，按本次结论该监听器也不会注册；属跨模块问题，建议另立 spec 处理（本特性未跨模块改动）。
3. fanout 语义下无法按库/表路由；若后续出现"多 Canal 实例 + 队列隔离"需求，再评估切换 topic exchange（需下游绑定迁移说明）。
