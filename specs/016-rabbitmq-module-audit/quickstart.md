# Quickstart：leaf-base-rabbitmq 审查与修复验证

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25

本指南只描述**如何验证**，实现细节见 [plan.md](./plan.md) 与 [contracts/](./contracts/)。

## 0. 前置条件

- JDK 21（`java -version`）
- Maven 3.9+，本地仓库已安装 `4.3.0-SNAPSHOT`（`mvn -o -pl leaf-base-rabbitmq test` 可解析父模块）
- 真实 Broker 验证可选：设置如下环境变量，缺失时集成用例自动跳过

```powershell
$env:RABBITMQ_HOST     = "127.0.0.1"
$env:RABBITMQ_PORT     = "5672"
$env:RABBITMQ_USERNAME = "<user>"
$env:RABBITMQ_PASSWORD = "<password>"
$env:RABBITMQ_VHOST    = "/"
```

> 凭据只允许来自环境变量，不得写入仓库任何文件。

## 1. 基线（修复前必须先记录）

```powershell
mvn -o -B -pl leaf-base-rabbitmq test
```

**期望（基线）**：`BUILD SUCCESS`，无 `Tests run` 行（0 用例）。把输出保存到 `specs/016-rabbitmq-module-audit/review-report.md` 的"基线"章节。

## 2. 缺陷复现（修复前应失败）

在实现修复前，先落下列回归用例并记录失败输出：

| 用例 | 复现的发现 | 期望（旧代码） |
| --- | --- | --- |
| `GXQueueDeclarationConcurrencyTest` | F-02 / F-09 | 缓存命中时不触达 Broker；32 线程声明次数 ≠ 1 或状态不一致 |
| `GXSendRabbitMQServiceImplTest#temporaryQueueIsExclusive` | F-03 | `Queue.isExclusive()==true`（不可被其他连接消费） |
| `GXSendRabbitMQServiceImplTest#traceIdIsRegenerated` | F-05 | MDC 有 traceId 时消息头仍为新 UUID |
| `GXSendRabbitMQServiceImplTest#callerPropertiesMutated` | F-06 | 发送后调用方 `MessageProperties.getContentType()` 非空 |
| `GXDefaultRabbitMQQueueListenerTest#emptyMessageIsSwallowed` | F-04 | 空消息不抛异常（被 ack） |

失败输出必须贴入 `review-report.md`，作为"旧代码失败"证据。

## 3. 修复后全量验证

```powershell
mvn -o -B -pl leaf-base-rabbitmq test
```

**期望**：

- `Tests run: 125, Failures: 0, Errors: 0, Skipped: 5`（无 Broker 时集成用例跳过；有环境时执行）
- ErrorProne / NullAway 无错误
- 连续执行 3 次结果一致（`for` 循环或手动 3 次，记录每次计数）

## 4. 并发验证

用例内已包含，亦可单独执行：

```powershell
mvn -o -B -pl leaf-base-rabbitmq test "-Dtest=GXSendRabbitMQServiceImplConcurrencyTest,GXQueueDeclarationConcurrencyTest"
```

**期望**：

- 16 线程 × 100 条：每条消息 `X-Trace-Id` 与 payload 一一对应，错误数 0
- 32 线程同一队列：`RabbitAdmin.declareQueue` 调用次数 = 1（Mockito `times(1)` 或 Broker 侧计数）
- 10 个不同队列并发：无全局锁等待，总耗时 < 串行基线

## 5. 配置来源验证

```powershell
mvn -o -B -pl leaf-base-rabbitmq test "-Dtest=GXRabbitMQConfigSourceTest,GXRabbitMQEnvironmentPostProcessorTest,GXRabbitMQConfigSpringBootTest"
```

**期望**：

- 未配置 → `GXNacosRabbitMQProperties` 存在、`GXLocalRabbitMQProperties` 不存在
- `config-source=local` → 反之，且 `classpath:`/`file:` 两种资源均可命中
- `config-source=nacos` + 无 server-addr → 启动成功、日志 `loaded=false`、无网络调用
- 伪 `ConfigService` 返回 YAML → 键可读，且 `application.yml` 同名键优先

## 6. 真实 Broker 验证（可选）

```powershell
mvn -o -B -pl leaf-base-rabbitmq test "-Dgroups=integration" "-Dtest=GXRabbitMQRealBrokerIT"
```

**期望**：

- 声明 → 绑定 → 发送 → `queueExists` → `purgeQueue` → `deleteQueue` 全链路通过
- `createTemporaryQueue` 声明的队列可由第二个连接消费
- 关闭 `mandatory` 时消息被丢弃；开启时 `GXReturnsCallback` 被调用
- 结果（含样本量）写入 `review-report.md`

## 7. 性能口径复现

无绝对门禁，只记录可复算指标：

| 指标 | 采集方式 | 期望 |
| --- | --- | --- |
| 每消息固定开销项 | 代码路径清单 + 日志计数 | 修复后 ≤4 项（序列化、traceId（仅缺失时）、属性快照、1 RTT） |
| 每消息 info 日志条数 | 用例中 appender 计数 | 0 |
| 同队列重复声明 RPC | Mockito 计数 | 稳态下每次 `createQueue` 1 次（无永久缓存） |
| 调度器并发度 | 4 个延迟任务完成时间差 | > 1 |
| 顺序/并发时延（有 Broker 时） | 500 条样本 | 记录 avg/p50/p95，两次同量级（偏差 ≤30%） |

## 8. 日志与静态检查

```powershell
# 非 ASCII 日志扫描（Java 源码中的 log. 调用行）
rg -n "log\.(info|warn|error|debug|trace)\(\"[^\"]*[^\x00-\x7F]" leaf-base-rabbitmq/src/main/java
```

**期望**：无输出（0 条）。

```powershell
mvn -o -B -pl leaf-base-rabbitmq test
```

**期望**：ErrorProne / NullAway 0 错误（编译阶段即失败则视为不通过）。

## 9. 交付检查清单

- [ ] 0 用例基线已记录
- [ ] 每个发现都有对应回归用例（修复前失败、修复后通过）
- [ ] 13 个公开方法均有成功/失败/空值或边界用例
- [ ] Spring 上下文用例覆盖 Bean 装配、配置源、监听器启停、转换器与回调
- [ ] 并发用例（16×100、32 线程同队列、10 队列并行）通过
- [ ] 集成用例在无环境时跳过、有环境时通过
- [ ] 连续 3 次全量执行结果一致
- [ ] 日志 ASCII 扫描无输出
- [ ] `AGENTS.md` / 手册与实现一致，能力边界已登记
- [ ] 未验证项与手工命令写入 `review-report.md`
