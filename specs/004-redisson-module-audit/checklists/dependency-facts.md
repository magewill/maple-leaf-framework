# 依赖侧事实核对（Redisson 4.7.0）

**Feature**: `004-redisson-module-audit`
**来源**: `F:\repository\org\redisson\redisson\4.7.0\redisson-4.7.0-sources.jar`（本地源码包，非网络资料）

## 核对命令

```powershell
$tmp="$env:TEMP\rdn470"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[System.IO.Compression.ZipFile]::OpenRead("F:/repository/org/redisson/redisson/4.7.0/redisson-4.7.0-sources.jar")
$targets=@("org/redisson/RedissonReliableTopic.java","org/redisson/RedissonBlockingQueue.java","org/redisson/RedissonDelayedQueue.java","org/redisson/QueueTransferService.java","org/redisson/Redisson.java")
foreach($e in $zip.Entries){ if($targets -contains $e.FullName){ [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e,(Join-Path $tmp $e.Name),$true) } }
$zip.Dispose()
```

## 事实 D-1：可靠主题移除监听器的执行顺序

`RedissonReliableTopic#removeListenerAsync(String...)`：

1. 先同步执行 `listeners.keySet().removeAll(Arrays.asList(listenerIds))`（本地监听器立即消失）；
2. 随后 `if (listeners.isEmpty())` 才取消 `readFuture` 与 `timeoutTask`；
3. 最后异步 `evalWriteAsync` 执行 `xgroup destroy` + `zrem`（清理 Redis 侧消费组与超时集合）。

**推论**：该异步步骤失败时，本地已不再投递消息，但调用方（`GXRedissonMQUtils`）的缓存未被清理 → 后续 `subscribe` 命中缓存直接返回旧 `listenerId`，形成"订阅成功但无投递"。**支撑 F-01（证据等级 D）。**

## 事实 D-2：阻塞队列批量抓取的往返次数

`RedissonBlockingQueue#drainToAsync(Collection, int)` 实现为单段 Lua：

```text
local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;
local vals = redis.call('lrange', KEYS[1], 0, elemNum);
redis.call('ltrim', KEYS[1], elemNum + 1, -1);
return vals
```

**推论**：`drainTo(c, n)` = **1 次网络往返**，与 `n` 无关。抓取侧不存在 N+1。**修正 F-05 措辞：只主张"in-flight 无背压"，不得主张"抓取低效"。** 另：`remainingCapacity()` 恒为 `Integer.MAX_VALUE`、`offer(e, timeout, unit)` 直接退化为 `offer(e)`，即"带超时的入队"并不阻塞。

## 事实 D-3：延迟队列转移任务的引用计数

`QueueTransferService#schedule(String name, QueueTransferTask task)`：

```text
tasks.compute(name, (k, t) -> { if (t == null) { task.start(); return task; } t.incUsage(); return t; });
```

`remove(String name)` 仅在 `task.decUsage() == 0` 时 `stop()`。

`Redisson#getDelayedQueue(RQueue)` 每次调用都 `new RedissonDelayedQueue<>(...)`（不做实例复用），构造即 `schedule`。

**推论**：重复创建抬高的引用计数只能被逐次 `destroy()` 递减；泄漏一处即导致容器关闭后转移任务仍在运行。**支撑 F-08（证据等级 D）。** 本模块存在两套 `RDelayedQueue` 持有者（`GXRedissonDelayMQUtils` 的静态缓存与 `GXRedissonDelayMQPostProcessor` 的每队列实例），使计数至少为 2。

## 事实 D-4：`RDelayedQueue` 已被上游弃用

`Redisson#getDelayedQueue` 首次调用即：

```text
log.error("RDelayedQueue object is deprecated due to github issues #3020, #2998, #1057. Use RReliableQueue object instead.");
```

`RedissonDelayedQueue` 类与 `RDelayedQueue` 接口均标注 `@Deprecated`。

**推论**：延迟转发链路的底座是弃用 API，上游移除即整体失效。**支撑 F-16（证据等级 D）。**

## 事实 D-5：`neverDelivered()` + `timeout` 的语义

`RedissonReliableTopic#poll` 内部使用 `readGroupAsync(id, "consumer", StreamReadGroupArgs.neverDelivered().timeout(Duration.ofSeconds(0)))`；`timeout(0)` 在 Redis `XREADGROUP` 中表示**永久阻塞**。

**推论**：模块把 `blockingTimeoutMillis = 0` 视为合法值（`validate()` 只拒绝负数），等价于"启动后无法通过 shutdownNow 之外的路径退出"。**支撑 F-14。**
