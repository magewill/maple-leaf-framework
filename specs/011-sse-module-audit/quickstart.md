# Quickstart: leaf-base-sse 审查与改造的验证指南

**Feature**: `011-sse-module-audit` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本指南只描述"如何验证"，不含实现代码。实现细节见 [plan.md](./plan.md) 与后续 `tasks.md`。

## 0. 前置条件

| 项 | 要求 | 核对命令 |
| --- | --- | --- |
| JDK | 21+（实测 25.0.4） | `java -version` |
| Maven | 3.9+，仓库已预热（可离线） | `mvn -v` |
| 工作目录 | 仓库根 `e:/project/java/maple-leaf-framework` | — |
| 外部依赖 | **无**（不需要 MySQL/Redis/MQ；Tomcat 11 由 `spring-boot-starter-web` 带入） | — |

## 1. 基线复现（审查结论可复核，SC-001/SC-006）

```powershell
# 1.1 既有测试基线：应为 10 执行 / 0 失败
mvn -o -pl leaf-base-sse test

# 1.2 上游语义核对（用于 R-01 ~ R-05）：直接读本地仓库里的源码包
#     F:/repository/org/springframework/spring-webmvc/7.0.9/spring-webmvc-7.0.9-sources.jar
#     期望看到：writeLock、Assert.state(!this.complete)、earlySendAttempts、
#     new DeferredResult<>(emitter.getTimeout())
```

**预期**

1. `Tests run: 10, Failures: 0, Errors: 0`（`GXSseEmitterServiceImplTest` 9 + `GXSseEmitterServiceSpringBootTest` 1）；
2. 上游源码中出现 `private final Lock writeLock = new ReentrantLock();` 与 `new DeferredResult<>(emitter.getTimeout())`。

## 2. 高危发现的故障现场（SC-007）

| 发现 | 现场构造 | 现状预期（改造前） | 改造后预期 |
| --- | --- | --- | --- |
| F-01 重试耗尽不关闭 | 子类覆盖 `send(SseEventBuilder)` 恒抛 `IOException`，替换缓存值后发送，等待 ≥ 800ms | 4 次尝试、缓存为空、`complete` 标志为 `false` | 4 次尝试、缓存为空、`complete` 标志为 `true` |
| F-02 顺序破坏 | 让第 1 条抛 `IOException`、第 2 条正常，记录客户端收到的顺序 | 先收到第 2 条 | 先收到第 1 条（重试后） |
| F-03 心跳不保活 | `createSseConnect(id, 60)` 后持续写入，观察连接终止时间 | 60s 后终止（与写入无关） | 60s 后终止（文档已如实声明） |
| F-04 首帧 flush 失败 | 真实 HTTP 端点 + 客户端在响应头后立即断开 | 缓存残留至超时 | 由容器错误回调或显式关闭清理，`drops`/`failures` 可见 |

> 现场构造使用 `GXSseEmitterServiceImpl#createEmitter(long)`（新增的 `protected` 工厂）与 `SseEmitter` 子类，**不得**用反射改私有字段（FR-023）。

## 3. 改造后的回归验证（SC-009）

```powershell
# 3.1 全量模块测试：必须全绿，用例数 ≥ 10 且单调增长
mvn -o -pl leaf-base-sse test

# 3.2 只跑新增的失败/并发/生命周期用例（快速定位）
mvn -o -pl leaf-base-sse test "-Dtest=GXSseEmitterServiceImplSendFailureTest"
mvn -o -pl leaf-base-sse test "-Dtest=GXSseEmitterServiceImplConcurrencyTest"
mvn -o -pl leaf-base-sse test "-Dtest=GXSseEmitterServiceImplLifecycleTest"

# 3.3 真实 SSE 端到端（RANDOM_PORT，读 text/event-stream）
mvn -o -pl leaf-base-sse test "-Dtest=GXSseEmitterHttpSpringBootTest"

# 3.4 静态检查（ErrorProne + NullAway 必须零错误）
mvn -o -pl leaf-base-sse clean test
```

**预期**

1. 所有用例通过；3.2 的三个类各自 ≤ 3s；3.3 能读到 `id:201`、`event: Push Msg`、`retry:2000`、`data:` 与关闭帧；
2. 3.4 无 NullAway 报错（`@Nullable` 标注完整）；
3. 测试日志中不出现非 ASCII 的 SSE 模块日志（`rg -n "c\.m\.s\.s\.i" target/surefire-reports`）。

## 4. 关键性质的手工核对（不可自动化的部分）

| 编号 | 核对方式 | 通过判据 |
| --- | --- | --- |
| C-01 | 断言"旧回调不误删新连接"（P-01）仍在用例集中 | `GXSseEmitterServiceImplTest` 中 3 条 `staleXxxCallback_*` 全部保留 |
| C-02 | 断言"空/缺失输入安全忽略"（P-04）仍在 | `sendMessageMethods_Ignore...` 保留 |
| C-03 | 断言"关闭/销毁幂等"（P-06）仍在 | `closeConnect_...`/`destroy_...` 保留 |
| C-04 | 分片共享同一 `msgId`（不再一片一个 id） | `mvn -o -pl leaf-base-sse test -Dtest=GXSseEmitterServiceImplSendFailureTest` 的 `splitChunks_ShareMsgIdAndCarryChunkIndex` |
| C-05 | 失败路径不在调用线程退避 | 让 1 个连接恒抛 `IOException`，测量 100 连接广播的调用耗时 | 调用耗时 ≈ 首投总耗时，不含 3×400ms 退避；`getMetrics().retries()` 在方法返回后继续增长 |
| C-06 | 单慢连接不阻塞其它连接 | 一个连接 `send` 阻塞 2s，另一个连接在同窗口内正常收到心跳/消息 |

## 5. 文档与契约一致性核对（SC-008）

| 核对项 | 方法 | 通过判据 |
| --- | --- | --- |
| 手册方法名/字段名与实现一致 | `rg -n "sendHeartbeatToAll\|SSE_CLIENT_CACHE\|removeUser\|1KB" docs/leaf-base-sse开发手册.md` | 无命中 |
| "心跳保活"表述已删除 | `rg -n "心跳.*(保持|维持).*连接" docs/leaf-base-sse开发手册.md` | 无命中（改为"探活与清理"） |
| 三条限制已声明 | 搜索"不承诺"章节 | 命中"投递成功/心跳保活/断点续传"三条 |
| `AGENTS.md` 红线已更新 | 阅读 §3 | 含"移除即关闭""保序""销毁语义"三条 |

## 6. 失败排查顺序

1. 用例失败且日志缺 `closed`/`complete` → 检查 D-01 入口是否在**所有**移除路径生效；
2. 顺序用例偶发失败 → 检查 `draining` 是否被重复提交（同一 client 出现两个 drain）；
3. NullAway 报错 → 检查新增 `@Nullable` 是否遗漏（`getMetrics` 返回不得为 `null`）；
4. 端到端用例超时 → 检查 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 是否被 `@SpringBootConfiguration` 干扰（测试内自带应用类）；
5. 线程/连接泄漏 → `destroy` 后断言 `getActiveConnectionCount()==0` 且 `getMetrics().connections()==0`。
