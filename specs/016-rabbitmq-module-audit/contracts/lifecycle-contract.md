# 契约：线程、调度与关闭

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25

## 所有权矩阵

| 组件 | Bean | 线程名前缀 | 虚拟线程 | 关闭责任 | 开关 |
| --- | --- | --- | --- | --- | --- |
| `rabbitTaskScheduler` | `expose=true` 时是 Bean；`false` 时由 `GXRabbitMQConfig` 内部持有 | `maple-rabbit-scheduler-` | JDK ≥21 时 `setVirtualThreads(true)` | `ThreadPoolTaskScheduler#shutdown`（Bean 由容器销毁；内部实例由 `@PreDestroy`） | `maple.framework.rabbitmq.scheduler.expose`（默认 `true`） |
| `rabbitmqAsyncExecutor` | 否 | `rabbitmq-async-worker-` | 否（有界并发 + 背压） | `@PreDestroy`：`shutdown` → 等待 30s → `shutdownNow` | 既有 `thread-pool.*` |

## 调度器

| 参数 | 值 | 理由 |
| --- | --- | --- |
| `poolSize` | `max(2, availableProcessors)` | 原实现默认 1，导致串行；异步回复/延迟任务需要并行 |
| `virtualThreads` | JDK ≥21 启用 | 任务本身不阻塞调度线程，虚拟线程降低资源占用 |
| `removeOnCancelPolicy` | `true` | 取消的超时任务立即释放 |
| `waitForTasksToCompleteOnShutdown` | `true` + 30s | 关闭时给在途任务窗口 |
| `errorHandler` | 记录 ASCII error，不抛出 | 单个任务异常不影响调度器 |
| `daemon` | `false` | 与现状一致（应用退出前完成收尾） |

**禁止**：在 `schedule(Runnable, Trigger)` 中新建执行器、在调度线程上调用 `Future.get()`。

## 异步执行器

| 参数 | 规则 |
| --- | --- |
| `corePoolSize` | `min(coreSize, maxSize)`，且 ≥1 |
| `maxPoolSize` | `max(coreSize, maxSize)` |
| 队列 | `queueCapacity > 0` → `LinkedBlockingQueue(capacity)`；`<= 0` → `SynchronousQueue` |
| `maxSize` 生效条件 | 队列满（`LinkedBlockingQueue`）或容量为 0（`SynchronousQueue`）；文档必须写明 |
| 拒绝策略 | `AbortPolicy`；`setupMessageChannelAsync` 捕获后返回 `failedFuture`，不得在调用线程执行 Broker IO |
| 空闲回收 | `allowCoreThreadTimeOut(true)` |
| MDC | 提交前捕获 `MDC.getCopyOfContextMap()`，任务内恢复、`finally` 清理 |
| 关闭 | 见上表；关闭期间提交的任务返回 `failedFuture` 并记录 ASCII warn |

## 上下文重复启动

- 每次上下文启动创建的线程数固定：调度器 `poolSize` + 执行器按需创建（空闲回收）。
- 上下文关闭后，模块命名的线程数必须归零（允许 JVM 线程池内部线程短暂存活，但 30s 内必须终止）。
- 用例：连续 `ApplicationContextRunner` 启动/关闭 3 次，按线程名前缀计数，断言 ≤ `poolSize` 且关闭后为 0（留 1s 采样窗口）。

## 兼容性

- `rabbitTaskScheduler` Bean 名称与类型保持不变（默认 `expose=true`）。
- `AsyncRabbitTemplate` 的 `TaskScheduler` 注入方式不变。
- 新增 `scheduler.expose=false` 仅影响 Bean 是否存在，不影响 `AsyncRabbitTemplate` 功能。

## 验证要点

| 场景 | 断言 |
| --- | --- |
| 4 个延迟任务并发提交 | 完成时间差 < 串行累计耗时（并发度 > 1） |
| `expose=false` | 容器中无 `TaskScheduler` 类型 Bean；`AsyncRabbitTemplate` 仍可工作 |
| `expose=true` | 存在名为 `rabbitTaskScheduler` 的 Bean |
| `queue-capacity=0, core=1, max=2, 3 并发` | 第 3 个提交被拒绝，`setupMessageChannelAsync` 返回失败 Future |
| 关闭时存在在途任务 | 任务完成后线程终止；无静默丢弃（日志可见） |
| 启动/关闭 3 次 | 线程数不累积 |
