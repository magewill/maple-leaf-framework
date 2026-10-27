# Quickstart & Validation Guide: leaf-base-data-sync 审查修复与 MQ 可切换

**Feature**: `specs/017-data-sync-mq-switchable` | **Date**: 2026-10-27

本文件是**验证指南**，不含实现代码。实现细节见 `tasks.md`（`/speckit.tasks` 生成）；契约见 [contracts/canal-consumer-contract.md](./contracts/canal-consumer-contract.md)；键与状态机见 [data-model.md](./data-model.md)。

---

## 0. 前置条件

| 项 | 要求 |
| --- | --- |
| JDK | 21（`java -version`） |
| Maven | 使用仓库自带 wrapper 或 `mvn`；离线可加 `-o` |
| 构建基线 | 仓库根目录可编译：`mvn -o -q -DskipTests install` 失败时，先记录失败模块，不要归因于本特性 |
| 真实中间件（可选） | `RABBITMQ_HOST`/`RABBITMQ_PORT`/`RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD`/`RABBITMQ_VHOST`；`ROCKETMQ_NAME_SERVER` |

所有真实中间件用例**必须**在变量缺失时跳过，不得让流水线失败。实现使用 `@EnabledIfEnvironmentVariable`（等价于 `assumeTrue` 语义，且跳过项在 surefire 报告中以 `Skipped` 显式列出）。

---

## 1. V1 编译与既有回归（最低门槛）

```powershell
mvn -o -B -pl leaf-base-data-sync -am test
```

期望：
- `BUILD SUCCESS`；`target/surefire-reports` 中改造前的 16 个用例仍全部通过（`GXCanalMessageParseServiceImplTest` 12 + `GXCanalConfigTest` 2 + `GXCanalRabbitMQListenerTest` 2，`parseMessage` 兼容入口未被破坏）；
- ErrorProne / NullAway 无 error（不得通过修改 `maven-compiler-plugin` 参数规避）。

---

## 2. V2 消费结局全覆盖（SC-002 / SC-003 / SC-006）

用例类：`cn.maple.canal.service.impl.GXCanalMessageParseServiceImplTest`、`cn.maple.canal.listener.*ListenerTest`

逐条投递并断言"结局 + 计数 + 是否进入重投/死信"：

| 输入 | 期望结局 | 附加断言 |
| --- | --- | --- |
| `"  "`（空白） | NON_RETRYABLE | 计数 +1；不进重投 |
| `"abc"`（非 JSON） | NON_RETRYABLE | 日志含长度截断；计数 +1 |
| `{"database":"db"}`（缺表） | NON_RETRYABLE | reason 可区分"缺库表" |
| `{"database":"db","table":"user","isDdl":true,"type":"ALTER"}` | SKIPPED | 无 ERROR 日志；计数 +1 |
| `{"database":"db","table":"user","type":"CREATE"}` | SKIPPED | 同上 |
| `{"...","type":"UPSERT"}` | NON_RETRYABLE | reason 含未知类型 |
| 处理器抛 `IllegalStateException` | RETRYABLE | 第 1–3 次重投、第 4 次死信；`deadLetter` +1 |
| 处理器返回 `null` | NON_RETRYABLE | 不再出现"warning + ack" |
| 数组载荷含 3 个事件（1 成功 / 1 DDL / 1 非 JSON 元素） | 3 条独立结局 | `success=1, skipped=1, nonRetryable=1` |
| `db=a_b/table=c` 与 `db=a/table=b_c` 先后到达 | 命中后 WARN 命名冲突 | 计数 +1，兜底行为不变 |

命令：

```powershell
mvn -o -B -pl leaf-base-data-sync test -Dtest="GXCanalMessageParseServiceImplTest,*ListenerTest,*StatisticsTest"
```

---

## 3. V3 RabbitMQ 装配与端点注册（F-03 定案）

用例：`GXCanalConfigTest`（`ApplicationContextRunner`）、`GXCanalListenerRegistrationTest`（端点注册）、`GXCanalConsumerSpringBootTest`（装配）、`GXCanalRabbitRealBrokerTest`（真实 broker，`RABBITMQ_HOST` 门控）、`GXCanalRealBrokerBootstrapTest`（门控用例的离线冒烟）

断言：
1. `canal.consumer-enabled=false` → 无 `Exchange`/`Queue`/`Binding` Bean，无连接尝试；
2. 默认配置 → 6 个 Bean 存在（`fanoutExchange`、`canalQueue`、`binding`，死信三件套 `canalDeadLetterExchange`/`canalDeadLetterQueue`/`canalDeadLetterBinding`），且 `RabbitListenerEndpointRegistry#getListenerContainers()` 非空、目标容器 `isRunning()==true`（**不要求真实 broker**，容器可处于连接重试状态但必须已注册）；
3. 预置下游同名 `Queue` Bean → 启动成功（`@ConditionalOnMissingBean` 让位）。

真实 broker 版本（可选）：

```powershell
$env:RABBITMQ_HOST="..."; mvn -o -B -pl leaf-base-data-sync test -Dtest="GXCanalRabbitRealBrokerTest"
```

期望：投递一条 `INSERT` 载荷后，`canal.consumer.success` 递增，`canalQueue` 中无残留消息；投递一条非 JSON 载荷后，`canal.dlq` 中出现该消息（可在管理台查看）。

---

## 4. V4 MQ 切换（SC-001 / SC-007）

用例：`GXCanalRocketMQConfigTest`、`GXCanalConsumerSpringBootTest`（`canal.mq-type=rocketmq` 上下文）、`GXCanalRocketRealBrokerTest`（真实 broker，`ROCKETMQ_NAME_SERVER` 门控）、`GXCanalRealBrokerBootstrapTest`（门控用例的离线冒烟）

1. 相同业务处理器与相同载荷，`canal.mq-type=rabbitmq` 与 `=rocketmq` 两次上下文中执行，断言事件处理记录逐条一致；
2. `mq-type=rocketmq` 上下文中断言**不存在** `Exchange`/`Queue`/`Binding` Bean；
3. `mq-type=UNKNOWN` → 启动失败，异常信息含 `rabbitmq`、`rocketmq` 字样；
4. 启动日志可读到 `mqType`、配置来源、在途上限、死信目的地。

真实 RocketMQ（可选）：

```powershell
$env:ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
$env:ROCKETMQ_NAME_SERVER="127.0.0.1:9876"; mvn -o -B -pl leaf-base-data-sync test -Dtest="GXCanalRocketRealBrokerTest"
```

期望：向 `canal.rocketmq.topic` 发送一条 `UPDATE` 载荷，处理器被调用一次；发送一条非 JSON 载荷，`canal.rocketmq.dead-letter-topic` 收到该消息（若已配置）。

---

## 5. V5 依赖缺失提示（FR-030 / SC-007）

用例：`GXCanalRocketMQConfigTest#shouldFailFastWhenRocketmqDependencyMissing`

使用 Spring 的 `FilteredClassLoader(RocketMQListener.class)` 构造上下文，配置 `canal.mq-type=rocketmq`：

期望：启动失败，消息同时包含 `leaf-base-rocketmq` 与 `rabbitmq`（给出两条出路），且异常不吞掉根因。

---

## 6. V6 依赖隔离（SC-011 / SC-012）

模块自身（应只有 1 个 RocketMQ 坐标，且标记 optional）：

```powershell
mvn -o -B -pl leaf-base-data-sync dependency:tree "-Dincludes=org.apache.rocketmq:*,com.alibaba.nacos:*,com.alibaba:fastjson"
```

期望：`leaf-base-rocketmq ... (optional)` 出现；不存在本模块直接声明的 `rocketmq-spring-boot-starter` / `rocketmq-client` 直接依赖行。

下游视角（手工校验，验证 optional 不传递）：新建一个临时 consumer POM（仅依赖 `leaf-base-data-sync`），执行同一命令。

期望：`org.apache.rocketmq` 与 `com.alibaba:fastjson` 条目为 0；`nacos-client` 若存在须来自 `leaf-base-rabbitmq` 的既有传递路径（记录来源，不作为本特性引入）。

---

## 7. V7 性能与并发（SC-004 / SC-005）

- 微基准（本地，记录命令与样本量）：10 万条"表未注册处理器"的消息，对比改造前后框架侧单条耗时；期望下降 ≥ 80%，且容器查找次数（可由测试用 `ApplicationContext` 包装计数）为 0。
- 并发用例：`GXCanalHandlerResolverTest` + `GXCanalConsumeStatisticsTest`，32 线程 × 1000 条，断言每条命中预期处理器、计数总和 = 32000、无串扰；连续 10 次通过。

```powershell
mvn -o -B -pl leaf-base-data-sync test -Dtest="GXCanalHandlerResolverTest,GXCanalConsumeStatisticsTest"
```

---

## 8. V8 配置源四组合（SC-013 / FR-045）

用例：`GXCanalPropertiesBindingTest`

| 类路径 | `canal.config-source` | 期望装配 | 期望启动日志 |
| --- | --- | --- | --- |
| 有 nacos 注解类 | 未配置 | Nacos 变体 | `config source: NACOS` |
| 有 nacos 注解类 | `local` | 本地变体（显式覆盖类路径探测） | `config source: LOCAL` |
| 无 nacos 注解类（FilteredClassLoader） | 未配置 | 本地变体 | `config source: LOCAL` |
| 无 nacos 注解类 | `nacos` | 启动失败并提示类路径缺少 Nacos 支持 | — |

同时断言：四种组合下 `canal.canal-queue-name` 等键的绑定值来自 `Environment`（用 `ApplicationContextRunner#withPropertyValues` 覆盖后能在 Bean 中读到），确保"绑定入口真实生效"（修复 E-11）。

---

## 9. V9 元数据与文档一致性（FR-034 / FR-039）

```powershell
Select-String -Path "leaf-base-data-sync/src/main/resources/META-INF/additional-spring-configuration-metadata.json" -Pattern "canal\."
```

期望：`data-model.md` §4.1/§4.2 列出的每个键都能在元数据中找到；`AGENTS.md` 与 `docs/leaf-base-data-sync开发手册.md` 的键表与默认值一致（尤其修正手册中 `RABBITMQ_CANAL_QUEUE_NAME` 默认值 `example` → `canalQueue`、类名 `CanalConstant` → `GXCanalConstant`、删除"RabbitMQ 重启不丢消息"的不成立承诺）。

---

## 10. 交付前检查（对应宪法 §VI / 质量门禁）

1. `mvn -o -B -pl leaf-base-data-sync -am test` 通过；
2. 每条高风险发现（F-01～F-04、F-17）至少一条"旧实现失败 / 新实现通过"的回归用例；
3. 真实中间件用例的跳过项写入交付报告（"未验证项"清单）；
4. 依赖树变化与传递依赖影响写入提交说明（宪法附加约束 §3）；
5. 移除 `@Lazy`（D-12）作为生产行为变更在提交说明中单列，并提示下游确认处理器幂等性；
6. 不支持范围已同步 `AGENTS.md` + 手册（FR-038）。
