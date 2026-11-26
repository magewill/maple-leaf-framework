# 审查报告：leaf-base-rocketmq

**分支**：`006-rocketmq-module-audit` ｜ **日期**：2026-10-22 ｜ **规格**：[spec.md](./spec.md) ｜ **计划**：[plan.md](./plan.md)

**审查对象**：`leaf-base-rocketmq`（18 个生产源码文件、3 个既有测试类、1 个测试配置）
**验证环境**：JDK 21 目标 / 构建 JDK 25；`rocketmq-spring-boot-starter 2.3.6` + `rocketmq-client/acl 5.3.2`；本机 Broker `127.0.0.1:9876`（`aclEnable=true`、`timerMaxDelaySec=604800`、`autoCreateTopicEnable=true`），凭据取自环境变量（`ROCKETMQ_ACCESS_KEY` / `ROCKETMQ_SECRET_KEY`），未写入仓库。

## 1. 基线（修复前）

```powershell
mvn -pl leaf-base-rocketmq test
```

- 结果：**27 执行 / 1 失败 / 0 跳过**
- 失败用例：`GXRocketMQConfigPropertiesTest$LocalConfigSourceTest.loadsLocalConfigSource`
- 失败断言原文：`Expected size: 1 but was: 0 in: {}`（期望本地配置 Bean 存在，实际为 0）
- 归因：`GXLocalRocketMQConfigProperties` 用 `@ConditionalOnMissingClass("com.alibaba.nacos...NacosConfigurationProperties")` 判定，而本模块硬依赖 `leaf-base-nacos` → `nacos-client 3.1.1` **确实包含该类**，条件恒为 false（E-01）→ F-01。

## 2. 生产文件覆盖表（18/18）

| # | 文件 | 判定 |
| --- | --- | --- |
| 1 | `dispatcher/GXCdcMessageDispatcher.java` | 无缺陷（契约过窄：返回 void，无法回传分发结果，记为观察项） |
| 2 | `dispatcher/package-info.java` | 无缺陷（`@NullUnmarked` 与前缀一致） |
| 3 | `dispatcher/impl/GXCdcMessageDispatcherImpl.java` | **缺陷**：F-03、F-08、F-09、F-10、F-11（见 §4） |
| 4 | `dispatcher/impl/package-info.java` | 无缺陷 |
| 5 | `dto/inner/GXCdcEvent.java` | **缺陷**：可变对象跨监听器共享 + 每监听器重复 diff（F-09、F-15） |
| 6 | `dto/inner/GXCdcOperation.java` | 无缺陷（`fromCode` 对 null/空白返回 null，边界正确） |
| 7 | `dto/inner/GXRocketMQMessageReqDto.java` | **缺陷**：`null` body 序列化为 `"null"`、构造器重载歧义（F-05、F-06） |
| 8 | `dto/inner/package-info.java` | 无缺陷 |
| 9 | `listener/GXCdcEventListener.java` | 无缺陷（默认方法提供了合理空实现） |
| 10 | `listener/package-info.java` | 无缺陷 |
| 11 | `properties/local/GXLocalRocketMQConfigProperties.java` | **缺陷**：F-01（条件恒假 + 占位符无默认值） |
| 12 | `properties/local/package-info.java` | 无缺陷 |
| 13 | `properties/nacos/GXNacosRocketMQConfigProperties.java` | **缺陷**：F-01（注解无处理器，从未生效） |
| 14 | `properties/nacos/package-info.java` | 无缺陷 |
| 15 | `service/GXSendRocketMQService.java` | 风险：返回值三元化、`boolean` 无信息量（F-07，既定契约） |
| 16 | `service/package-info.java` | 无缺陷 |
| 17 | `service/impl/GXSendRocketMQServiceImpl.java` | **缺陷**：F-02、F-04、F-05、F-07、F-12、F-13 |
| 18 | `service/impl/package-info.java` | 无缺陷 |

## 3. 真实 Broker 实测记录

### 3.1 修复前探针（一次性验证，代码未入库）

| 场景 | 结果 |
| --- | --- |
| 5 类发送（普通/同步/延迟 5s/异步/单向） | 全部成功 |
| 错误 secret | `AclException: Check signature failed for accessKey=...`，客户端重试 3 次、耗时 192ms 后抛 `MQClientException` |
| 顺序 200 条 | avg 1304µs / p50 1130 / p95 2304 / max 14303 |
| 8×50 并发 | avg 2902µs / p50 2183 / p95 8513 / max 10509，吞吐 2690/s |

### 3.2 修复后集成用例（`GXRocketMQRealBrokerIT`，env 门控）

```powershell
$env:ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
$env:ROCKETMQ_ACCESS_KEY="<env>"; $env:ROCKETMQ_SECRET_KEY="<env>"
mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQRealBrokerIT
```

- 结果：**3 执行 / 0 失败**（三次复跑均通过）
- 顺序 200 条：avg 1014–1438µs / p50 916–1243 / p95 1735–2811 / max 2672–6951
- 8×25 并发：avg 2227–2667µs / p50 2038–2297 / p95 4089–5913，吞吐 **2897–3532 msg/s**
- 三次复跑的 avg 与 p95 偏差均 < 30%（满足 SC-005），量级与修复前探针一致（说明本次改动未引入发送路径性能退化）
- 无环境变量时：`Tests run: 3, Skipped: 3`，构建成功（满足 FR-019）
- 服务级错误凭据（第 4 个用例，经 `GXSendRocketMQService#syncSend`）：抛 `GXBusinessException("Failed to send sync message: Send [3] times, still failed...")`，cause 为 Spring `MessagingException`（其内部再包裹客户端异常）；**Broker 侧 `AclException: Check signature failed` 只在客户端日志中可见，不在异常消息链里**——该差异记录于 §8 遗留项 6。

### 3.3 CDC 端到端实测（MySQL → Debezium → RocketMQ → Dispatcher）

**环境**：MySQL `8.4.11`（`192.168.56.105:3306`，库 `test`，表 `t_listener_user`，1 行）；Debezium Server `3.7.0.Final`（Quarkus，sink=`rocketmq`）；RocketMQ 5.x（`aclEnable=true`，`127.0.0.1:9876`）；本模块以真实消费到的消息驱动 `GXCdcMessageDispatcher`。

**上游先决问题（均位于 Debezium 侧，不在本模块）**：

1. 直接运行 `run.bat` 未设置 `ENABLE_DEBEZIUM_SCRIPTING=true`，`lib_opt` 未进入类路径 → `io.debezium.transforms.ContentBasedRouter` `ClassNotFoundException` → 引擎线程中断；表现为"Quarkus 启动成功、控制台 Listening on 8080，但没有任何 CDC 数据"。
2. `debezium.source.table.include.list=test.user` 指向不存在的表（库内实际为 `t_listener_user` 等 5 张表）→ 日志 `After applying the include/exclude list filters, no changes will be captured`，快照 0 行。
3. `D:\tools\debezium1`（2.5.0）无法对接 MySQL 8.4：`SHOW MASTER STATUS` 在 8.4 已被移除，快照阶段报 SQL 语法错误；必须使用 3.7。
4. 不能用"去掉 route 转换器"的方式绕过第 1 点：Debezium 默认 topic 为 `<prefix>.<db>.<table>`，含点号，Broker 直接拒绝（`The specified topic[...] contains illegal characters, allowing only ^[%|a-zA-Z0-9_-]+$`）。

**修复后实测结果**：

- 快照产生 1 条真实事件并投递到 topic `debezium-test-t-listener-user-dev`（topic 已自动创建）。
- 真实消息体（截断，省略 `ts_us`/`ts_ns`）：

  ```json
  {"before":null,"after":{"id":105,"username":"carol","score":30,"status":"archived","created_at":null,"updated_at":null,"created_by":null,"updated_by":null,"is_deleted":0,"deleted_at":null,"ext":null},"source":{"version":"3.7.0.Final","connector":"mysql","name":"debezium","ts_ms":1792633713000,"snapshot":"last","db":"test","table":"t_listener_user","file":"mysql-bin.000002","pos":434,"row":0},"transaction":null,"op":"r","ts_ms":1792662514782}
  ```

- 经 `GXRocketMQRealCdcIT` 消费并分发：`consumed=1`、`dispatched=1`、`opHistogram={READ=1}`、`parseFailure=0`、`unknownOp=0`；`event.getAfter().getStr("username")` 取到真实列值 `carol`，`source.db/table` 与真实库表一致。
- 同一消费链路追加负向输入：`dispatch("not-json")` → `parseFailure=1`；`dispatch("{\"op\":\"x\"}")` → `unknownOp=1`；两次均**不抛异常**、监听器未被误调（符合 FR-018 分层处置契约）。

**未覆盖（初轮）**：`CREATE` / `UPDATE` / `DELETE` 三条分支未能在真实链路上触发——源库账号 `debezium` 只有只读权限（实测 `INSERT command denied to user 'debezium'`），无法制造数据变更。三条分支目前由 32 个单测覆盖；如需实测，需提供具备 DML 权限的账号。

**复测（2026-10-22 18:10–18:25，上游修正后）**：Debezium 实例以 `test.user` 表为源重新启动，连接器线程存活（`debezium-mysqlconnector-debezium-change-event-source-coordinator`），写入 topic `debezium-test-user-dev`。依次执行"插入 → 更新 → 删除"后，经 `GXRocketMQRealCdcIT` 消费：`consumed=3`、`dispatched=3`、`opHistogram={CREATE=1, UPDATE=1, DELETE=1}`、`parseFailure=0`、`unknownOp=0`。三类真实增量事件样本（截断）：

```json
CREATE: {"before":null,"after":{"id":1,"username":"ikero"},"source":{"db":"test","table":"user","snapshot":"false"},"op":"c"}
UPDATE: {"before":{"id":1,"username":"ikero"},"after":{"id":1,"username":"ikero1"},"op":"u"}
DELETE: {"before":{"id":1,"username":"ikero1"},"after":null,"source":{"db":"test","table":"user"},"op":"d"}
```

即 `READ`（快照）、`CREATE`、`UPDATE`、`DELETE` 四类事件均已在真实链路上验证：`before`/`after` 形态、`source.db`/`table`、`op → GXCdcOperation` 映射、`ts_ms` 提取全部正确。`tombstones.on.delete=false` 下确认**没有**产生 `null` 消息体（不会触发空白消息分支）。

**失败路径实测（`GXRocketMQRealCdcFailureIT`，消息真实经过 Broker）**：每次运行使用独立 tag 发布并只消费该 tag，避免历史消息干扰。

| 场景 | 真实消息 | 观测结果 | 结论 |
| --- | --- | --- | --- |
| 毒消息（JSON 截断） | `{"op":"c","after":{"id":1}` | `dispatch` 不抛异常，`parseFailureCount=1`，`warn` 日志 | 符合 FR-018：记录 + 计数后正常返回 |
| 未知 op | `{"op":"x","after":{"id":2}}` | 不抛异常，`unknownOperationCount=1` | 同上 |
| 毒消息之后的合法消息 | `{"op":"c"...}` + `{"op":"u"...}` | `successCount=2`，监听器收到 `CREATE:bot`、`UPDATE:bot2` | **毒消息不会阻塞后续消息** |
| 监听器抛异常 | 真实 `CREATE` 事件 | 抛 `GXCdcDispatchException` 且 `getSuppressed().length=1`；排在后面的监听器仍收到事件；`listenerFailureCount=1` | 隔离生效，异常保持对外可见 |
| 异常后的 Broker 行为 | 同上，消费方返回 `RECONSUME_LATER` | 同一消息被 **重投 ≥2 次**（日志可见两次 `ConsumeMessageThread` 失败），`maxReconsumeTimes=2` 后进重试/DLQ | 印证"至少一次 + 重复执行"，业务必须幂等（F-08 影响定性成立） |

本次运行写入 `leaf-base-rocketmq/target/cdc-failure-evidence.txt` 的实测计数：

```text
[broker-redelivery]      attempts=2 listenerFailure=2
[listener-exception]     suppressed=1 listenerFailure=1 success=0 otherListenerEvents=[CREATE:bot]
[poison-and-unknown-op]  consumed=4 parseFailure=1 unknownOp=1 success=2 listenerEvents=[CREATE:bot, UPDATE:bot2]
```

**复现命令**：

```powershell
# 1) 启动 Debezium（必须启用 scripting，否则 ContentBasedRouter 缺失）
$env:ENABLE_DEBEZIUM_SCRIPTING="true"
D:\tools\debezium-server\run.bat   # 或按 lib_opt 显式加入类路径

# 2) 消费真实事件并驱动 Dispatcher
$env:ROCKETMQ_NAME_SERVER="127.0.0.1:9876"
$env:ROCKETMQ_ACCESS_KEY="<env>"; $env:ROCKETMQ_SECRET_KEY="<env>"
mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQRealCdcIT

# 3) 失败路径（毒消息 / 未知 op / 监听器异常 + Broker 重投）
mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQRealCdcFailureIT
```

### 3.4 结构开销复核表（按代码路径复算，非压测）

| 指标 | 口径 | 结果 | 代码位置 |
| --- | --- | --- | --- |
| 每消息网络往返 | 一次业务发送触达 Broker 的请求-响应次数 | 同步/延迟 = 1；异步/单向 = 1 次提交（不等待响应） | `GXSendRocketMQServiceImpl#syncSend` / `#sendDelayMessage` / `#sendAsync` / `#sendOneway` |
| 成功路径 `info` 日志 | 成功分支上的 `info` 调用点 | 0（5 处成功日志全部为 `debug`） | 同文件 `sendNormalMessage`/`sendDelayMessage`/`syncSend`/`handleAsyncSuccess`/`sendOneway` |
| 失败路径日志 | 每次失败输出的可检索日志 | 1 条 `error`，含 `destination`、`messageKey`、`status`、`msgId` | `checkSendResult`、`handleAsyncSuccess`、`handleAsyncException` |
| CDC 每事件 `Dict` 分配 | `Dict` 实例数 | 4（before/after/source/transaction，构造期防御拷贝；JSON 场景走 `instanceof Dict` 快速路径，无中间对象） | `GXCdcEvent#toDict`（4 个调用点） |
| CDC 每事件 diff | `Maps.difference` 调用次数 | 1（构造期计算一次，结果缓存在 `differingFields`） | `GXCdcEvent` 构造器 |
| CDC 每事件额外容器 | 除 4 个 `Dict` 外的容器 | 2（1 个差异 `Set` + 1 个匹配监听器 `List`） | `GXCdcEvent#differingFields`、`GXCdcMessageDispatcherImpl#resolveMatchedListeners` |
| 监听器匹配调用 | 每监听器的过滤开销 | 2 次（`supports` + `shouldIgnore` 各 1 次），匹配后只遍历一次 | `GXCdcMessageDispatcherImpl#resolveMatchedListeners` |
| 异步失败回调 | 每消息回调线程开销 | 1 次 `ObjectProvider#orderedStream` 取实现 + 每实现 1 次 `try-catch` | `GXSendRocketMQServiceImpl#notifyFailure` |

复算方式：按上表"结果"列逐项数代码路径上的调用点/构造点，与 `quickstart.md` §6 的四个目标（往返 ≤1、成功路径 info = 0、`Dict` ≤4、diff = 1）逐项对齐，结论为全部达标。

## 4. 发现 → 任务映射与风险排序

排序依据：影响面 × 触发概率 × 可观测性。

| 排序 | 发现 | 级别 | 维度 | 影响 | 修复任务 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | F-01 配置来源整体失效 | 高 | 正确性 | 所有下游的 `config/<profile>/rocket-mq.yml` 与 Nacos 配置均不生效，且无日志 | T018–T020、T014 | 已修复 |
| 2 | F-03 CDC 毒消息静默丢弃 | 高 | 正确性 | 解析失败/未知 op 被 ack，消息永久丢失 | T028、T024 | 已修复 |
| 3 | F-02 异步失败不可补偿 | 高 | 正确性 | 业务无法区分"已投递"与"已丢失" | T023、T016 | 已修复 |
| 4 | F-08 监听器无隔离 | 中 | 正确性 | 前者失败导致其余监听器永远漏收 + 重试重复 | T029、T025 | 已修复 |
| 5 | F-09 事件跨监听器可变 | 中 | 线程安全 | 后执行监听器看到被篡改数据 | T030、T027 | 已修复 |
| 6 | F-04 非 `SEND_OK` 一律抛异常 | 中 | 正确性 | 重复消息 + 单次失败 3×超时 | 文档化 + `error` 日志（T021/T034） | 已说明 |
| 7 | F-05 校验缺口 | 中 | 正确性 | 无效 topic/超长体触达 Broker；`"null"` 被真实发出 | T021、T022、T015 | 已修复 |
| 8 | F-11 上下文丢失 | 中 | 线程安全 | 异步失败日志无法关联请求 | T031 | 已修复 |
| 9 | F-10 监听器集合固化 | 中 | 线程安全 | 动态注册静默失效 | T032 | 已修复 |
| 10 | F-12 关键路径 info 日志 | 中 | 性能 | 高吞吐下日志成为瓶颈 | T034、T033 | 已修复 |
| 11 | F-13 无批量封装 | 中 | 性能 | 每消息 1 次 RTT，并发 p95 放大 | 报告记录，不实现（超范围） | 已说明 |
| 12 | F-06 构造器重载歧义 | 中 | 正确性 | 字符串 body 双重编码 | T022 | 已修复（javadoc + null 拒绝） |
| 13 | F-07 返回语义三元化 | 中 | 契约 | 调用方判断成本高 | 文档化（T037） | 既定契约 |
| 14 | F-14 目的地拼装不设防 | 低 | 正确性 | `topic:tag` 歧义 | T021（topic 禁 `:`） | 已修复 |
| 15 | F-15 CDC 分配与 diff 开销 | 低 | 性能 | 宽表 × N 监听器放大 | T030、T035 | 已修复 |
| 16 | F-16 异步无背压/超时覆盖 | 低 | 性能 | 提交后可无限涌入 | 报告记录 | 未处理（需评估） |
| 17 | F-17 配置类空壳 | 低 | 维护性 | IDE 无提示 | T009、T019/T020 | 已修复 |
| 18 | F-18 装配依赖扫描 | 低 | 可观测 | 未装配时无信号 | T019（启动日志提示） | 已缓解 |

### 4.1 发送语义矩阵

| 方法 | 返回值 | 失败表达 | 阻塞 | 投递语义 | 真实 Broker |
| --- | --- | --- | --- | --- | --- |
| `sendNormalMessage` | `void` | 抛 `GXBusinessException` | 是（1 次 RTT） | 至少一次（上游重试可致重复） | 通过 |
| `syncSend` | 恒 `true` | 抛 `GXBusinessException` | 是（1 次 RTT） | 至少一次 | 通过 |
| `sendDelayMessage` | `msgId` | 抛 `GXBusinessException`（非正/溢出前置拒绝） | 是（1 次 RTT） | 至少一次（Broker 定时消息，受 `timerMaxDelaySec` 限） | 通过（5s 延迟实测） |
| `sendAsync` | 提交即 `true` | 提交阶段抛异常；最终失败走扩展点 | 否 | 尽力投递 + 可选失败补偿 | 通过 |
| `sendOneway` | 提交即 `true` | 提交阶段抛异常 | 否 | 至多一次 | 通过 |

CDC 分发语义：

| 场景 | 投递语义 | 失败归属 |
| --- | --- | --- |
| 解析成功 + 监听器全部成功 | 至少一次（模块内不重试，由消费方决定） | — |
| 解析成功 + 任一监听器失败 | 至少一次（汇总抛 `GXCdcDispatchException`，消费方重试） | 业务方负责幂等 |
| 解析失败 / payload 为空 / op 未知 | 至多一次（记录 + 计数 + 可选死信） | 死信 topic 或日志 |
| 开关关闭 | 不投递 | 计数 `skipped` |

### 4.2 配置来源矩阵

| 来源 | 装配条件 | 是否真实加载 | 证据 |
| --- | --- | --- | --- |
| 本地 `config/<profile>/rocket-mq.yml` | `rocketmq.config-source=local`；或未配置且类路径无 Nacos 注解类 | 是（文件缺失时 `ignoreResourceNotFound` 不报错，日志标 `NOT_FOUND`） | `GXRocketMQConfigPropertiesTest#loadsLocalConfigSourceWhenExplicitlySelected`（`rocketmq.test-marker=local-source`） |
| Nacos `rocket-mq.yml` | `rocketmq.config-source=nacos`；或未配置且类路径有 Nacos 注解类 | 值由下游 `spring.config.import=nacos:rocket-mq.yml` 加载，模块只做开关与 `name-server` 校验 | `#loadsNacosConfigSourceWhenExplicitlySelected`、`#failsFastWhenExplicitNacosSourceHasBlankNameServer` |
| 下游 `application.yml` 的 `rocketmq.*` | 始终生效（Spring `Environment`） | 是 | 单测与集成测试均以属性注入验证 |
| 其他 `config-source` 取值 | — | 启动失败 | `#rejectsUnsupportedConfigSource` |

### 4.3 并发风险矩阵

| 对象 | 共享范围 | 现状保护 | 收敛路径 |
| --- | --- | --- | --- |
| 请求 DTO（`GXRocketMQMessageReqDto`） | 调用方线程 | 发送前快照为不可变 `MessageRequest` record | 已锁定：`GXSendRocketMQServiceImplConcurrencyTest`（8 线程并发 + 提交后篡改） |
| CDC 事件（`GXCdcEvent`） | 所有匹配监听器 | `final` 字段、无 setter、构造期防御拷贝、diff 一次缓存 | 已锁定：`GXCdcEventTest` |
| 监听器集合 | 所有消费线程 | `ObjectProvider` 每消息解析，保持 `@Order` | 运行期新增 Bean 立即可见 |
| 异步回调 | 生产者回调线程池 | 快照 + MDC 包装 + 逐实现 `try-catch` | 已锁定：`GXRocketMQAsyncSendFailureHandlerTest`、`GXSendRocketMQServiceImplAsyncMdcTest` |
| 分发计数 | 多消费线程 | `LongAdder` 无锁累加 | 无共享热点 |
| 旧键迁移提示 | 多线程 | `AtomicBoolean#compareAndSet` | 每上下文只提示一次 |

### 4.4 发现明细（八要素）

| 发现 | 代码位置（文件:行区间） | 触发条件 | 复现方式 | 回归测试设计 |
| --- | --- | --- | --- | --- |
| F-01 | `properties/local/GXLocalRocketMQConfigProperties.java:26-39`、`properties/nacos/GXNacosRocketMQConfigProperties.java:23-43`、`properties/GXRocketMQConfigSources.java:1-30` | `rocketmq.config-source` 缺失或显式指定，且类路径含 `nacos-client` | `mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQConfigPropertiesTest` | 修复前 `loadsLocalConfigSource` 失败（`Expected size: 1 but was: 0`）；修复后四态（local/nacos/未配置/bogus）全绿 |
| F-02 | `service/impl/GXSendRocketMQServiceImpl.java:125-155,189-226` | 异步发送最终失败：非 `SEND_OK`、回调异常、空结果 | `-Dtest=GXRocketMQAsyncSendFailureHandlerTest` | 旧代码无扩展点（仅日志）；新代码 handler 收到 context（`cause` 非空 / `sendStatus` 非空二选一） |
| F-03 | `dispatcher/impl/GXCdcMessageDispatcherImpl.java:61-113,156-174` | 非 JSON、payload 为空、`op` 未知、开关关闭 | `-Dtest=GXCdcMessageDispatcherImplTest#parseFailureIsCounted+#unknownOperationIsCounted` | 旧代码静默返回；新代码计数 +1，配置死信时转发并计数 |
| F-04 | `service/impl/GXSendRocketMQServiceImpl.java:252-260` | Broker 返回 `FLUSH_DISK_TIMEOUT` / `SLAVE_NOT_AVAILABLE` / `null` | `-Dtest=GXSendRocketMQServiceImplTest#syncSendThrowsWhenBrokerStatusIsNotOk` | 断言异常含状态名 + 输出 1 条 `error`（`#failurePathLogsAtErrorLevel`） |
| F-05 | `service/impl/GXSendRocketMQServiceImpl.java:270-300` | `null` body、topic 含 `:`/超长、tag 超长或含 `:`、key 超长、body 超 4MB | `-Dtest=GXSendRocketMQServiceImplTest` 中 7 个校验用例 | 断言抛异常且 `verifyNoInteractions(rocketMQTemplate)`（未触达 Broker） |
| F-06 | `dto/inner/GXRocketMQMessageReqDto.java:68-107` | 以 `String` 或 `null` 走 `Object` 构造器 | `#sendNormalMessageRejectsLiteralNullBody` | 构造器拒绝 `null`；javadoc 说明 `(Object)` 与 `(String, Object)` 歧义 |
| F-07 | `service/GXSendRocketMQService.java:7-42` | 调用方需判断发送是否成功 | 对照 §4.1 语义矩阵 | 不改签名，仅文档化（`AGENTS.md` + 手册） |
| F-08 | `dispatcher/impl/GXCdcMessageDispatcherImpl.java:96-112` | 两个监听器，前者抛异常 | `#listenerFailureIsAggregatedAndOtherListenersStillRun` | 后者仍执行一次；抛 `GXCdcDispatchException` 且 `getSuppressed().length == 1` |
| F-09 | `dto/inner/GXCdcEvent.java:23-99` | 监听器读取并尝试修改事件 | `GXCdcEventTest#exposesNoSetters+#copiesPayloadMapsDefensively` | 无 setter；源 Map 修改不影响事件 |
| F-10 | `dispatcher/impl/GXCdcMessageDispatcherImpl.java:50-59` | 运行期新增监听器 Bean | `-Dtest=GXCdcMessageDispatcherImplTest`（多监听器 + `@Order`） | 新增 Bean 后立即可被解析到 |
| F-11 | `service/impl/GXSendRocketMQServiceImpl.java:132-145,204-210` | 带 TraceId 的请求发生异步失败 | `-Dtest=GXSendRocketMQServiceImplAsyncMdcTest` | 回调线程可见原 TraceId；结束后恢复回调线程原上下文 |
| F-12 | `service/impl/GXSendRocketMQServiceImpl.java:63,87,110,167,200` | 高吞吐发送（每消息 1 条 info） | `#successPathDoesNotLogAtInfoLevel` | 成功路径 `info` 计数为 0，失败保持 `error` |
| F-13 | `service/GXSendRocketMQService.java:7-42` | 需要批量发送场景 | 见 §3.4 结构开销表 + `quickstart.md` §6 | 未实现（需改契约），登记为遗留项 |
| F-14 | `service/impl/GXSendRocketMQServiceImpl.java:228-234` | topic 含 `:` 导致 `topic:tag` 歧义 | `#sendNormalMessageRejectsTopicWithColon` | 前置拒绝，destination 拼装无歧义 |
| F-15 | `dto/inner/GXCdcEvent.java:42-54,80-91` | 宽表事件 × 多监听器 | `GXCdcEventTest#detectsSingleChangedField` + §3.4 | 每事件 diff 1 次、`Dict` 4 个、额外容器 2 个 |
| F-16 | `service/impl/GXSendRocketMQServiceImpl.java:125-155` | 异步提交速率超过处理能力 | 无自动化（依赖生产者内部信号量） | 遗留项：需业务评估限流策略 |
| F-17 | `properties/local/GXLocalRocketMQConfigProperties.java:26-39`、`properties/nacos/GXNacosRocketMQConfigProperties.java:23-43`、`META-INF/additional-spring-configuration-metadata.json` | 下游需要 IDE 配置提示 | 启动日志 + metadata 文件 | 已补 metadata 与来源日志 |
| F-18 | `properties/local/GXLocalRocketMQConfigProperties.java:27-38` | 下游未启用包扫描（未用 `@GXEnableLeafFramework`） | 观察启动日志 | 日志输出 `Component scanning hint: ...` |

### 4.5 高危故障现场构造步骤

**F-01 配置来源失效**

1. 前置：任意引入 `leaf-base-rocketmq`（含 `nacos-client`）的应用；在 `config/default/rocket-mq.yml` 写 `rocketmq.test-marker: local-source`。
2. 操作：设置 `rocketmq.config-source=local` 后启动（修复前该键无任何效果）。
3. 期望（修复前）：容器内无 `GXLocalRocketMQConfigProperties`，`rocketmq.test-marker` 读不到；`mvn -pl leaf-base-rocketmq test` 报 `Expected size: 1 but was: 0`。
4. 期望（修复后）：Bean 存在、marker 可读，日志含 `RocketMQ config source: local (explicit)`。

**F-02 异步失败不可补偿**

1. 前置：Broker 开启 ACL（`aclEnable=true`）。
2. 操作：用错误 `secretKey` 构建 `RocketMQTemplate`，调用 `sendAsync`（或直接触发回调的 `onException`）。
3. 期望（修复前）：方法返回 `true`，仅一条 `error` 日志，无任何回调，业务无法得知失败。
4. 期望（修复后）：注册 `GXRocketMQAsyncSendFailureHandler` 后收到 context，`cause` 非空且 `messageKey` 与发送时一致。

**F-03 CDC 毒消息静默丢弃**

1. 前置：`maple.framework.cdc.enable=true`、`maple.framework.cdc.active-status=enabled`。
2. 操作：调用 `dispatcher.dispatch("not-json", "order")`，或 `dispatch("{\"op\":\"x\"}", "order")`。
3. 期望（修复前）：方法正常返回，仅日志；无计数、无死信，消费方 ack 后消息永久丢失。
4. 期望（修复后）：`parseFailureCount()` / `unknownOperationCount()` 各 +1；配置 `maple.framework.cdc.dead-letter-topic` 时转发成功并 `deadLetterCount()` +1。

### 4.6 与 plan D-01 的实现偏差

`research.md` D-01 计划使用 `AnyNestedCondition`（内嵌 `AllNestedConditions`）表达三态装配。实现阶段实测该写法中的 `@ConditionalOnExpression("'${rocketmq.config-source:}'.length() == 0")` 分支在 `ApplicationContextRunner` 下不生效（结果是本地与 Nacos 两个配置源**都不装配**，正好复现 F-01 的静默形态）。

因此改为两个显式 `Condition` 实现（`GXLocalRocketMQConfigSourceCondition` / `GXNacosRocketMQConfigSourceCondition`）：显式键优先，缺失时按 `ClassUtils.isPresent` 判定 Nacos 注解类。语义与 D-01 定义的三态矩阵完全一致，且判定逻辑可被单测直接覆盖（`GXRocketMQConfigPropertiesTest` 五例）。此偏差不影响契约，但记录在此以便后续维护者理解为何未使用嵌套条件 DSL。

## 5. 文档 ↔ 实现对照表

| 文档声明 | 实现 | 判定 |
| --- | --- | --- |
| "优先 Nacos，失败回退本地"（手册 §2） | 由 `rocketmq.config-source` 三态决定 | 不一致 → 已改文档 |
| 本地路径 `classpath:${spring.profiles.active:}/rocket-mq.yml` | 原为 `/${spring.profiles.active}/`，现补 `:default` | 不一致 → 已改文档 + 实现 |
| Nacos 通过 `@NacosConfigurationProperties` 加载 | 注解无处理器，从未生效 | 不一致 → 已改文档 + 实现 |
| `sendNormalMessage` 调用 `rocketMQTemplate.send` | 实为 `syncSend` | 不一致 → 已改文档 |
| 延迟消息"延时等级固定 / 设置 `setDeliverTimeMs`" | `syncSendDeliverTimeMills` + Broker `timerMaxDelaySec` | 不一致 → 已改文档 |
| "发送成功记录日志" | 成功路径已降为 `debug` | 已改文档 |
| 异步发送"业务方无法提供回调" | 新增可选失败扩展点（`SendCallback` 仍不可传入） | 已改文档 + `AGENTS.md` |
| 无 CDC 章节 | 模块含完整 CDC 适配能力 | **未覆盖** → 遗留项：建议后续补章节（本次仅在 `AGENTS.md` 记录契约） |
| 未封装批量发送 | 接口确无 batch 方法 | 一致 |
| `AGENTS.md`：配置来源由 `rocketmq.config-source` 显式选择 | 修复前代码无此键 | 不一致 → 已修复实现并细化文档 |

## 6. 既定契约 vs 缺陷

以下行为**不视为缺陷**，只要求文档同步：

1. `sendAsync` / `sendOneway` 提交成功即返回 `true`——失败通过扩展点补偿，不改返回值语义。
2. `sendNormalMessage` 返回 `void`，失败抛异常。
3. `syncSend` 失败抛异常而非返回 `false`。
4. `GXCdcEvent.before/after` 缺失时返回空 `Dict`（不区分"字段缺失"与"空对象"）。
5. 模块不提供批量发送、事务消息、消费者容器（超出模块职责）。

## 7. 修复后验证

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 单测全量 | `mvn -pl leaf-base-rocketmq clean test` | **58 执行 / 0 失败** |
| 真实 Broker | `mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQRealBrokerIT`（含 env） | **4 执行 / 0 失败** |
| 真实 CDC 链路 | `mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQRealCdcIT`（含 env + 运行中的 Debezium） | **1 执行 / 0 失败**（`debezium-test-user-dev`：consumed=3，CREATE+UPDATE+DELETE 分发成功） |
| CDC 失败路径 | `mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQRealCdcFailureIT`（含 env） | **3 执行 / 0 失败**（毒消息 / 未知 op / 监听器异常 + Broker 重投） |
| 缺环境跳过 | 上述两条（无 env） | **4 / 1 跳过，构建成功** |
| 静态检查 | 上述命令内含 ErrorProne + NullAway | 零告警 |

## 8. 遗留项

1. **F-16 异步背压**：`semaphoreAsyncSendNum` 等生产者内部状态不对外暴露；是否需要在服务层加限流需业务评估，本次未实施。
2. **批量发送**：接口层仍为单条语义；实现批量需改动公共契约，建议单独排期。
3. **手册缺 CDC 章节**：`docs/leaf-base-rocketmq开发手册.md` 目前无 CDC 内容，本次仅在 `AGENTS.md` 固化契约。
4. **`GXCdcEvent` 集合仍为 `Dict` 返回**：已去除 setter 并做构造期防御拷贝，但 `getBefore().put(...)` 仍可改自身视图，未改为不可变返回类型以保持 API 兼容。
5. **`cdc.active.status` 旧键**：保留兼容并加一次性迁移提示，未来大版本可移除。
6. **ACL 失败原因未进入异常消息**：`GXBusinessException` 的消息来自 `MessagingException`（"Send [3] times, still failed"），Broker 的 `AclException` 细节只在客户端日志。若要求异常消息直接可定位鉴权问题，需要在服务层追溯 root cause 并拼接——会改变现有错误文本，建议作为独立变更评估。
7. **上游 Debezium 启动参数依赖人工设置**：`ENABLE_DEBEZIUM_SCRIPTING=true` 缺失会导致"服务启动成功但无数据"，属于极易误判的静默失效；建议在其启动脚本或部署文档中固化该环境变量（不在本仓库范围）。
8. **CDC 实测用例对 topic 状态敏感**：topic 被重建/清空时首个 20s 窗口可能读到 0 条（已加一次重试）；若在 CI 中长期运行，建议改为"先确认 topic 有消息再断言"或由测试自行投递样本。
