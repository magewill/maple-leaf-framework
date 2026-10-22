# Quickstart: 如何复现与验收本次审查

**Feature**: `004-redisson-module-audit` | **Date**: 2026-10-22

本指南用于**验证审查结论**，不用于开发。所有命令在 `e:\project\java\maple-leaf-framework` 下执行；不需要真实 Redis。

## 0. 前置条件

| 项 | 要求 | 检查命令 |
| --- | --- | --- |
| JDK | 21（虚拟线程 API） | `java -version` |
| Maven | 使用本机 settings（本地仓库 `F:\repository`） | `mvn -v` |
| 框架快照 | `leaf-base-framework:4.3.0-SNAPSHOT` 已安装 | `Test-Path "F:/repository/cn/maple/framework/leaf-base-framework/4.3.0-SNAPSHOT"` |
| 依赖源码 | `redisson-4.7.0-sources.jar` 可用于事实核对 | `Test-Path "F:/repository/org/redisson/redisson/4.7.0/redisson-4.7.0-sources.jar"` |

## 1. 测试基线（SC-006）

```powershell
cd e:\project\java\maple-leaf-framework
mvn -pl leaf-base-redisson -o test
```

**预期**：`BUILD SUCCESS`，`RedissonUtilityRegressionTest` 38 个用例全绿，无 Redis 连接尝试；整模块执行 60 个用例全绿（38 既有 + 22 审计），汇总行为 `Tests run: 65, Failures: 0, Errors: 0, Skipped: 5`（5 个真实服务用例默认跳过）。

只跑单个类或单个用例：

```powershell
mvn -pl leaf-base-redisson -o test -Dtest=RedissonUtilityRegressionTest
mvn -pl leaf-base-redisson -o test -Dtest=GXRedissonDelayForwardingAuditTest
```

**失败排查**：若提示缺少兄弟模块产物，去掉 `-o` 或先在根目录执行 `mvn -pl leaf-base-framework -am install -DskipTests`。

## 2. 依赖事实核对（支撑 E-13 与三条修正结论）

```powershell
$tmp="$env:TEMP\rdn470"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[System.IO.Compression.ZipFile]::OpenRead("F:/repository/org/redisson/redisson/4.7.0/redisson-4.7.0-sources.jar")
$targets=@("org/redisson/RedissonReliableTopic.java","org/redisson/RedissonBlockingQueue.java","org/redisson/RedissonDelayedQueue.java","org/redisson/QueueTransferService.java","org/redisson/Redisson.java")
foreach($e in $zip.Entries){ if($targets -contains $e.FullName){ [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e,(Join-Path $tmp $e.Name),$true) } }
$zip.Dispose()
```

| 核对项 | 命令 | 预期结论 |
| --- | --- | --- |
| 订阅移除顺序 | `Select-String -Path "$tmp\RedissonReliableTopic.java" -Pattern "removeAll\|xgroup','destroy" -Context 0,3` | 本地 listeners 先移除，Redis 侧异步清理 → F-01 成立 |
| 批量抓取往返 | `Select-String -Path "$tmp\RedissonBlockingQueue.java" -Pattern "drainToAsync" -Context 0,12` | 单段 EVAL（`lrange`+`ltrim`）→ 抓取为 1 次往返 |
| 转移任务引用计数 | `Get-Content "$tmp\QueueTransferService.java"` | `incUsage/decUsage` + 按队列名去重 → F-08 后果为"计数不回落到 0" |
| `RDelayedQueue` 弃用 | `Select-String -Path "$tmp\Redisson.java" -Pattern "deprecated due to github issues" -Context 0,2` | 构造即打印弃用 error → F-16 成立 |

## 3. 六类高风险发现的复现步骤

| 发现 | 复现入口 | 预期现象 |
| --- | --- | --- |
| F-01 订阅不可自愈 | `GXRedissonTopicSubscriptionAuditTest`：mock `removeListener` 抛异常 → `unsubscribe` → 再次 `subscribe` | `subscribe` 返回非空 id，但 `addListener` 只被调用过 1 次；缓存条目仍在 |
| F-02 至多一次 | `GXRedissonDelayForwardingAuditTest`：构造"移出 Redis 后、重投前"进程中止（例：让 `requeueToBlockingQueue` 返回 `false`） | 消息既不在 Redis，也不在本地队列，仅在 `inFlightMessages`（随后 `destroy` 才可能补投） |
| F-03 无 handler 丢弃 | `GXRedissonStreamReliabilityAuditTest`：handler 未注册时投递一条消息 | `ack` 被调用、消息从 pending 消失、无 DLQ 记录 |
| F-04 全局锁 | `GXRedissonStreamTransferAuditTest`：两个 topic 各有 1 条到期消息 | 同一轮内两条消息的 6 次操作全部串行发生于同一把 `mq:delay:lock` 下 |
| F-05 无背压 | `GXRedissonDelayForwardingAuditTest`：mock handler 返回永不完成的 future，持续注入 100 条 | `inFlightMessages.size()` 单调增长，无上限拦截 |
| F-11 锁语义 | `GXRedissonLockSemanticsAuditTest`：`tryLock` 返回 `false` | 方法返回 `null`，与"操作返回 `null`"不可区分；释放路径多 2 次调用 |

执行方式：

```powershell
mvn -pl leaf-base-redisson -o test -Dtest="GXRedisson*AuditTest"
```

## 4. 性能口径复算（SC-004）

以延迟队列转发为例，打开 `processor/GXRedissonDelayMQPostProcessor.java`，从 `pollBatch` 到 `processMessageAsync` 数一遍：

1. `blockingQueue.drainTo(messages, 20)` → 1 次往返（批量接口按实现计 1）。
2. `localQueue.offer(...)` → 0 次（本地 `LinkedBlockingQueue`）。
3. 失败回退 `blockingQueue.offer(message)` → 1 次。
4. 重试耗尽 `delayedQueue.offer(message, ...)` → 1 次。

结论应为 `(1, 20, 无分布式锁)`。若报告中出现 `20` 次往返，即为口径错误（见 contracts/audit-coverage-contract.md §4）。

stream 延迟转移的 6 次按 `transferTopicDueMessages` 中连续出现的 `hash.get / transferSet.add / zset.remove / publish / hash.remove / transferSet.remove` 逐项数出，可用 `Select-String -Pattern "hash\.get|transferSet\.add|zset\.remove|immediateQueue\.publish|hash\.remove|transferSet\.remove"` 核对数量。

## 5. 覆盖率台账核对（SC-001）

```powershell
# 生产文件数（应为 51）
(Get-ChildItem e:\project\java\maple-leaf-framework\leaf-base-redisson\src\main\java -Recurse -Filter *.java).Count

# 测试被哪些生产类型直接引用
Select-String -Path e:\project\java\maple-leaf-framework\leaf-base-redisson\src\test\java\cn\maple\redisson\*.java -Pattern "^import cn\.maple\.redisson\.(\w+\.[\w.]+);" | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
```

**预期**：51 个文件与台账行数一致；未被测试引用的类型必须在 `checklists/coverage-ledger.md` 的 `gapReason` 中给出原因，且不得停留在空值。

## 6. 交付前检查（对照 contracts/review-report-schema.md §3）

```powershell
$r="e:\project\java\maple-leaf-framework\specs\004-redisson-module-audit\review-report.md"
(Select-String -Path $r -Pattern "^### F-\d{2}").Count          # 应等于 spec 中发现条目数（16）
(Select-String -Path $r -Pattern "- \*\*维度\*\*:").Count        # 应等于 16
(Select-String -Path $r -Pattern "约 [0-9]|大概|左右").Count      # 性能章节应为 0
git status --short -- leaf-base-redisson/src/main leaf-base-redisson/pom.xml   # 应为空（L-01/L-02）
```

**验收口径**：以上四条全部满足 + `mvn -pl leaf-base-redisson -o test` 全绿 + `review-report.md` 第 1 章与 `spec.md` 结论表一致，即认为本次审查交付可通过。

## 7. 真实服务验证（可选，默认跳过）

默认构建只用 Mock，不连接网络。需要对齐"真实 Redis 行为"时，用环境变量显式开启，凭据不入库：

```powershell
$env:AUDIT_REDIS_HOST="<redis-host>"   # 地址与密码均由本机环境提供，不写入仓库
$env:AUDIT_REDIS_PASSWORD="<由环境提供，勿写入任何文件>"
# 可选：$env:AUDIT_REDIS_PORT / $env:AUDIT_REDIS_DATABASE / $env:AUDIT_REDIS_USERNAME

mvn -pl leaf-base-redisson -o clean test -Dtest=GXRedissonRealServiceVerificationTest
```

预期：`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。五条用例分别验证：

| 用例 | 验证的真实行为 | 对应发现 |
| --- | --- | --- |
| `reliableTopicDeliversAndStopsAfterUnsubscribe` | 发布 → 订阅方收到；取消订阅后 `publish` 返回 0（消费组已销毁） | F-01 的**正常路径**成立，缺陷仅在移除失败时出现 |
| `delayedQueueDeliversExpiredMessage` | `sendDelayMessage` 500ms 后消息进入阻塞队列，且不早于延迟时间 | F-16（链路当前可用，底座已弃用） |
| `counterIncrementsAndExpires` | 自增 + 2s TTL 续期，到期后 key 消失 | F-11 的正常路径 |
| `lockSerialisesConcurrentOperations` | 4 线程并发时最大并发度为 1，且 4 次操作全部执行 | F-11 的锁语义基线 |
| `streamQueueRoundTrip` | `sendImmediate` → handler 收到消息 | F-03/F-12 的正常路径 |

**未设置 `AUDIT_REDIS_HOST` 时**，该类整体跳过，流水线汇总为 `Tests run: 65, Failures: 0, Errors: 0, Skipped: 5`——即 60 个用例实际执行并全绿、5 个真实服务用例跳过。**不要**把地址与密码写进 `pom.xml`、测试源码或 `config/` 模板。

### 实机环境已知现象

- 首次构造 `RDelayedQueue` 时，Redisson 4.7.0 会输出 `RDelayedQueue object is deprecated ... Use RReliableQueue object instead.`（error 级），这是 F-16 的实机确认，不是本模块新增日志。
- 若目标 Redis 未开启 keyspace 通知或使用了 ACL 限制，`stream` 相关能力可能被拒绝；此时应改为检查 ACL 而非判定模块缺陷。
