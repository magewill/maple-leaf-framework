# 契约：配置项

**类型**：公共配置（下游 `application.yml` / Nacos 可直接设置）
**稳定性**：键名只增不改；语义变更按破坏性变更处理，必须保留旧键。

## 既有键（保持不变）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `maple.framework.mybatis.async.listener.core-pool-size-factor` | `1.0` | 核心线程数 = CPU 核数 × 系数 |
| `maple.framework.mybatis.async.listener.max-pool-size-factor` | `1.0` | 最大线程数 = 核心线程数 × 系数 |
| `maple.framework.mybatis.async.listener.queue-capacity` | `1000` | 队列容量，`0` 表示直接交给线程池 |
| `maple.framework.mybatis.async.listener.keep-alive-seconds` | `120` | 空闲线程存活时间 |
| `maple.framework.mybatis.async.listener.long-task-threshold-ms` | `30000` | 长任务告警阈值 |
| `maple.framework.mybatis.async.listener.await-termination-seconds` | `60` | 停机等待窗口 |
| `maple.framework.mybatis.async.listener.virtual.enabled` | 继承 `spring.threads.virtual.enabled` | 是否启用虚拟线程 |
| `maple.framework.mybatis.async.listener.virtual.concurrency-limit` | `0` | 虚拟线程并发上限；`0` 表示取最大池化线程数，`-1` 表示不限制 |

## 新增键

| 键 | 默认值 | 说明 | 关联需求 |
| --- | --- | --- | --- |
| `maple.framework.enable.mybatis-listener` | `true` | 整体开关；关闭后不解析声明、不构造事件、不投递（回到无监听基线） | FR-019 |
| `maple.framework.enable.mybatis-listener-async` | `true` | 异步模式开关；关闭后异步声明按同步方式执行，避免任务排队 | FR-019 |
| `maple.framework.mybatis.listener.deduplicate` | `true` | 同一监听器在同一操作中是否去重（关闭可恢复旧的多接口重复投递行为） | FR-002 |
| `maple.framework.mybatis.listener.propagate-exception` | `false` | 回调异常是否向业务调用方传播（`true` 为旧行为） | FR-006、FR-015 |
| `maple.framework.mybatis.listener.sync-after-commit` | `false` | 同步回调是否延后到事务提交后执行 | FR-004 |
| `maple.framework.mybatis.listener.strict-snapshot` | `false` | 严格快照模式：出现不可克隆值时记降级日志与计数 | FR-008 |
| `maple.framework.mybatis.listener.metadata-cache-size` | `2048` | 声明解析缓存容量 | FR-018 |
| `maple.framework.mybatis.listener.metadata-cache-expire-seconds` | `3600` | 声明解析缓存空闲过期时间 | FR-018 |
| `maple.framework.mybatis.listener.slow-callback-threshold-ms` | `30000` | 同步回调长耗时告警阈值 | FR-020 |

## 约束

1. 上述键必须绑定到类型安全的属性类，禁止散落 `@Value`（整体开关允许在条件装配时使用）。
2. 所有新增键必须登记到 `META-INF/additional-spring-configuration-metadata.json`，包含 `name`、`type`、`defaultValue`、`description`。
3. 默认值组合必须等价于"加固后的推荐行为"，同时可通过开关逐项回退到旧行为。
4. 配置项不得包含敏感信息；不得引入 Nacos 专属属性类（本能力非外部化配置）。
5. 配置读取失败（如类型不匹配）必须启动失败并给出明确原因，不得静默取默认值。
