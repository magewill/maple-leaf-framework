# Quickstart：验证 leaf-base-rocketmq 审查结论与修复

本指南只给"怎么跑、看什么"，实现细节见 [plan.md](./plan.md) 与 [contracts/](./contracts/)。

## 0. 前置条件

- JDK 21+、Maven 3.10+，仓库根目录执行。
- 真实 Broker 验证（第 3 节）需要：RocketMQ 5.x，NameServer 地址、ACL 凭据。凭据**只允许**通过环境变量注入，禁止写入任何文件。

## 1. 基线：既有测试与审查报告

```powershell
mvn -pl leaf-base-rocketmq -am test
```

**期望（审查阶段基线）**：27 个用例执行，1 个失败——`GXRocketMQConfigPropertiesTest$LocalConfigSourceTest.loadsLocalConfigSource`（`Expected size: 1 but was: 0`）。该失败即 F-01 的证据，必须在报告中记录而**不是**删用例。

**期望（修复后）**：同一命令 0 失败，且用例数不少于 35（三态装配、发送校验、CDC 分层处置、扩展点各至少 1 例）。

## 2. 配置来源三态（F-01 / FR-017）

```powershell
# 显式本地
mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQConfigPropertiesTest
```

**期望**：

- `config-source=local` → 本地 Properties Bean 存在，`rocketmq.test-marker=local-source` 可读。
- `config-source=nacos` → Nacos 校验 Bean 存在、本地 Bean 不存在。
- 未配置 → 按类路径判定，日志含 `auto-detected`。
- `config-source=bogus` → 启动失败，消息含 `unsupported`。

**日志抽查**：启动日志中必须出现 `RocketMQ config source: ...`，且本地源分支写明 `profile` 与 `resource`（或 `NOT_FOUND`）。

## 3. 真实 Broker 验证（FR-004 / FR-019）

```powershell
$env:ROCKETMQ_NAME_SERVER = "127.0.0.1:9876"
$env:ROCKETMQ_ACCESS_KEY  = "<从本地环境提供>"
$env:ROCKETMQ_SECRET_KEY  = "<从本地环境提供>"
mvn -pl leaf-base-rocketmq test -Dtest=GXRocketMQRealBrokerIT
```

**期望**：

1. 5 类发送（普通 / 同步 / 延迟 5s / 异步 / 单向）全部成功，延迟消息返回非空 `msgId`。
2. 错误 secret → `GXBusinessException`，日志保留 Broker 侧 `AclException: Check signature failed`；客户端重试 3 次（`Send [3] times, still failed`）。
3. 并发采样（8 线程 × 25 条）全部成功，无异常；控制台打印 `count / avg / p50 / p95 / max`。
4. 未设置上述环境变量时，该类**整体跳过**（不失败、不阻塞流水线）。

**参照量级（本机单 Broker、单生产者实测，仅作同量级复现参照）**：顺序 200 条 avg≈1.3ms、p50≈1.1ms、p95≈2.3ms；8×50 并发吞吐≈2700 msg/s、p95≈8.5ms。

## 4. 发送语义（F-04–F-07 / FR-020）

```powershell
mvn -pl leaf-base-rocketmq test -Dtest=GXSendRocketMQServiceImplTest
```

**期望**：

- `null` body、非法 topic、超长 tag、`messageKey` 超长 → 前置抛 `GXBusinessException`，且 `verifyNoInteractions(rocketMQTemplate)`。
- `SEND_OK / null / FLUSH_DISK_TIMEOUT / SLAVE_NOT_AVAILABLE` 四种结果的行为与 [send-contract.md](./contracts/send-contract.md) 一致。
- 成功路径 `info` 日志条数为 0（appender 断言）。
- 注册失败扩展点后，`onException` 场景能收到 context；未注册时仅 `error` 日志。

## 5. CDC 分层处置（F-03 / F-08–F-10 / FR-018）

```powershell
mvn -pl leaf-base-rocketmq test -Dtest=GXCdcMessageDispatcherImplTest
```

**期望**：

- 非 JSON、`op=x`、空白消息 → 不抛异常，计数与 `warn` 日志符合 [cdc-dispatch-contract.md](./contracts/cdc-dispatch-contract.md)。
- 两个监听器，前者抛错 → 后者仍执行一次；最终抛 `GXCdcDispatchException` 且 `getSuppressed().length == 1`。
- 开关关闭 → 无监听器被调用，日志级别为 `warn`。
- 事件对象不可修改（无 setter / 集合不可变）。

## 6. 结构开销复核（F-12 / F-15 / FR-007）

不跑压测，按代码路径复算即可：

| 指标 | 复核方式 | 目标 |
| --- | --- | --- |
| 每消息网络往返 | 统计发送路径上的 `syncSend/asyncSend/oneWay` 调用次数 | 1（异步/单向为提交 1 次） |
| 成功路径 info 日志 | 统计 `log.info` 调用点 | 0 |
| CDC 每事件容器分配 | 统计 `Dict` 构造点 × 事件数 | ≤4 |
| CDC 每事件 diff 次数 | 统计 `Maps.difference` 调用点 | 1 |

## 7. 文档一致性抽查（FR-010）

```powershell
Select-String -Path "leaf-base-rocketmq\AGENTS.md","docs\leaf-base-rocketmq开发手册.md" -Pattern "config-source|rocketMQTemplate.send|批量|延时等级"
```

**期望**：`config-source` 的描述与三态矩阵一致；`sendNormalMessage` 不再写成调用 `rocketMQTemplate.send`；"延时等级固定"的表述改为"Broker 定时消息（`timerMaxDelaySec`）"。
