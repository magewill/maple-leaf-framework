# 覆盖率台账（52 行）

**Feature**: `004-redisson-module-audit`
**规则**: 行数 = 51 个生产 `.java` + 1 个 `application.yml`；`verdict = 缺陷` 必须指向 `F-NN`；`未覆盖` 仅允许 `package-info.java` 且 `gapReason` 非空。

## adapter（2）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `adapter/GXRedissonDebeziumReliableTopic.java` | 生产代码 | 风险 | — | 与 `GXRedissonMQUtils` 并存的第二套订阅入口，`addListener` 空值校验后转发；F-01/F-09 的同类风险在此同样成立，但无独立用例 |
| `adapter/package-info.java` | package-info | 未覆盖 | — | 纯包声明，无可执行语义 |

## annotation（2）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `annotation/GXRedissonDelayMQToTopic.java` | 生产代码 | 风险 | `RedissonUtilityRegressionTest`（注解存在性） | `timeout()` 默认 1800 秒，而 `GXRedissonDelayMQPostProcessor.DEFAULT_POLL_TIMEOUT_SECONDS = 30` 仅用于 `timeout <= 0`；默认值实际生效为 30 分钟，见 F-14 |
| `annotation/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## config（4）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `config/GXRedissonMQConfig.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`（auth 落位） | 与 `GXRedissonSpringDataConfig` 共用同一段地址解析逻辑，F-13 同样成立 |
| `config/GXRedissonSpringDataConfig.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`（auth 落位） | F-13：地址仅含分隔符导致 `addresses[0]` 越界；集群判定依赖键名含 `cluster` |
| `config/GXRedissonStreamMQConfig.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest`（生命周期契约） | 条件装配与 `destroyMethod = "stop"` 语义清晰 |
| `config/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## listener（4）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `listener/GXRedissonMQListener.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | `destroy()` 默认空实现，由后处理器统一注销，语义一致 |
| `listener/GXRedissonDelayMQListener.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | 默认实现校验入参并返回 `false` 触发重试，异常语义明确 |
| `listener/GXRedissonStreamMQListener.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | 仅声明注册方法 |
| `listener/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## processor（4）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `processor/GXRedissonDelayMQPostProcessor.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`、`GXRedissonDelayForwardingAuditTest`、`GXRedissonConsumerConcurrencyAuditTest` | F-02、F-05、F-06、F-08、F-14 |
| `processor/GXRedissonMQPostProcessor.java` | 生产代码 | 风险 | `RedissonUtilityRegressionTest`、`GXRedissonTopicSubscriptionAuditTest` | F-09：`destroy` 仅在本地缓存为空时才清理，残留条目会跨上下文存活 |
| `processor/GXRedissonStreamMQPostProcessor.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | 注册失败补偿路径已覆盖 |
| `processor/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## properties（6）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `properties/GXRedissonProperties.java` | 生产代码 | 无缺陷 | 间接 | 抽象属性基类，无逻辑 |
| `properties/GXRedissonConnectProperties.java` | 生产代码 | 无缺陷 | 间接 | 纯数据载体 |
| `properties/GXRedissonMQProperties.java` | 生产代码 | 无缺陷 | 间接 | 纯数据载体 |
| `properties/GXRedissonCacheManagerProperties.java` | 生产代码 | 无缺陷 | 间接 | 纯数据载体 |
| `properties/GXRedissonStreamMQProperties.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`、`GXRedissonConsumerConcurrencyAuditTest` | F-14：`blockingTimeoutMillis` 仅拒绝负数，0 被接受并导致永久阻塞读 |
| `properties/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## properties/local（5）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `properties/local/GXLocalRedissonProperties.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | `ignoreResourceNotFound = true`，缺 profile 不阻断启动 |
| `properties/local/GXLocalRedissonMQProperties.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | 同上 |
| `properties/local/GXLocalRedissonCacheManagerProperties.java` | 生产代码 | 无缺陷 | 间接 | 同上 |
| `properties/local/GXLocalRedissonStreamMQProperties.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | 同上 |
| `properties/local/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## properties/nacos（5）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `properties/nacos/GXNacosRedissonProperties.java` | 生产代码 | 风险 | 间接 | 热更新实例被多线程读取，字段非 `volatile`，可见性依赖实现细节；判定与理由见报告 §5.2 |
| `properties/nacos/GXNacosRedissonMQProperties.java` | 生产代码 | 风险 | 间接 | 同上 |
| `properties/nacos/GXNacosRedissonCacheManagerProperties.java` | 生产代码 | 风险 | 间接 | 同上 |
| `properties/nacos/GXNacosRedissonStreamMQProperties.java` | 生产代码 | 风险 | `RedissonUtilityRegressionTest`（默认值） | 同上；且刷新后不会重建消费者 |
| `properties/nacos/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## services（4）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `services/GXRedissonCacheService.java` | 生产代码 | 无缺陷 | 间接 | 接口契约明确 |
| `services/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |
| `services/impl/GXRedissonCacheServiceImpl.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest`（4 个用例） | F-15（既定契约，仅需文档同步）：TTL=-1 与全量物化行为已被用例锁定 |
| `services/impl/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## stream（2）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `stream/GXRedissonStreamMQManager.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`、`GXRedissonConsumerConcurrencyAuditTest` | F-14：`start()` 半途失败后 `stop()` 直接返回，已启动线程不可回收 |
| `stream/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## stream/dto、stream/handler（4）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `stream/dto/req/GXRedissonStreamMessageDto.java` | 生产代码 | 风险 | `RedissonUtilityRegressionTest` | `nextRetry()` 生成新 DTO，但重投的原始 JSON 不递增 `retryCount`，见 F-12 |
| `stream/dto/req/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |
| `stream/handler/GXRedissonStreamMessageHandler.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | 单方法契约 |
| `stream/handler/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## stream/queue（4）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `stream/queue/GXRedissonStreamImmediateMQ.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`、`GXRedissonStreamReliabilityAuditTest` | F-03、F-12 |
| `stream/queue/GXRedissonStreamDelayedMQ.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`、`GXRedissonStreamTransferAuditTest` | F-04 |
| `stream/queue/GXRedissonStreamPendingMessageRecovery.java` | 生产代码 | 风险 | `RedissonUtilityRegressionTest` | F-12：全局单锁 + 仅恢复持锁实例的 topic |
| `stream/queue/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## util（5）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `util/GXRedissonMQUtils.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`、`GXRedissonTopicSubscriptionAuditTest` | F-01、F-09、F-10 |
| `util/GXRedissonDelayMQUtils.java` | 生产代码 | 风险 | `RedissonUtilityRegressionTest` | F-08、F-16：静态缓存再持有一份 `RDelayedQueue`，抬高引用计数 |
| `util/GXRedissonStreamMQUtils.java` | 生产代码 | 无缺陷 | `RedissonUtilityRegressionTest` | 仅委托 manager 并做参数校验 |
| `util/GXRedissonUtils.java` | 生产代码 | 缺陷 | `RedissonUtilityRegressionTest`、`GXRedissonLockSemanticsAuditTest` | F-11 |
| `util/package-info.java` | package-info | 未覆盖 | — | 纯包声明 |

## 资源（1）

| 文件 | 类型 | verdict | referencedBy | gapReason / 发现 |
| --- | --- | --- | --- | --- |
| `src/main/resources/application.yml` | 配置文件 | 无缺陷 | 间接 | 排除 Redisson 官方自动配置，与本模块自装配一致，无重复注册 |

## 汇总

`verdict` 只使用 FR-001 规定的四值枚举（`无缺陷` / `风险` / `缺陷` / `未覆盖`）。"既定契约"不是独立取值：`GXRedissonCacheServiceImpl` 判为 `无缺陷`，并在发现列注明"既定契约、仅需文档同步（F-15）"，故不计入缺陷数。

| verdict | 行数 |
| --- | --- |
| 缺陷 | 9 |
| 风险 | 10 |
| 无缺陷 | 18 |
| 未覆盖（package-info） | 15 |
| 合计 | 52 |
