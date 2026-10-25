# Contract: 装配配置与可观测性（leaf-base-dubbo-zk）

**Scope**: Dubbo filter 装配要求、启动期校验、日志与计数、SPI 资源、不支持范围

## 1. 强制装配契约

| 配置键 | 推荐值 | 级别 |
| --- | --- | --- |
| `dubbo.provider.filter` | `dubboServerTraceIdFilter,dubboExceptionFilter,-exception` | **强制**（缺失会导致业务异常 `code/msg/data` 丢失） |
| `dubbo.consumer.filter` | `dubboClientTraceIdFilter,dubboExceptionFilter,-exception` | 推荐（缺失则无 TraceId 透传、无 consumer 侧兜底分类） |

`-exception` 的作用：排除 Dubbo 内置 `ExceptionFilter`（`dubbo-3.3.6-sources.jar` 第 49 行 `@Activate(group = PROVIDER)`），否则它会把跨 jar 的 `GXBusinessException` 包装成普通 `RuntimeException`。该键不出现 `-exception` 时允许应用启动（兼容存量），但必须输出 ERROR。

## 2. 启动期校验契约（`GXDubboProviderFilterValidator`）

| 项 | 要求 |
| --- | --- |
| 触发时机 | `SmartInitializingSingleton#afterSingletonsInstantiated`（所有单例就绪后） |
| 读取 | `Environment#getProperty("dubbo.provider.filter", "")` |
| 判定 | 包含 `-exception` → 通过；否则 ERROR |
| 副作用 | **只告警**，不抛异常、不阻断启动 |
| 日志文案（ASCII） | `DUBBO FILTER: built-in ExceptionFilter is active; GXBusinessException code/msg/data may be lost. Configure dubbo.provider.filter=dubboServerTraceIdFilter,dubboExceptionFilter,-exception (current=%s)` |
| 装配方式 | `@Component`（依赖外部组件扫描，与模块既有 `GXDubboRpcApiBeanPostProcessor` 一致） |

## 3. 日志契约

| 场景 | 级别 | 是否含敏感信息 |
| --- | --- | --- |
| TraceId 透传/接收 | DEBUG | 否 |
| 未声明运行时异常 | ERROR | 异常消息可能含业务信息，但不含参数值 |
| filter 自身失败 | WARN + 计数 | 否 |
| 绑定失败 | WARN | 否 |
| 装配缺失 | ERROR | 否 |

全部日志文案必须为 ASCII；不得新增 INFO 级每请求日志。

## 4. 可观测性计数

`GXDubboExceptionFilter.filterFailureCount()`（包级可见）返回 filter 自身抛错的累计次数：

- 递增点：`onResponse` 外层 `catch (Throwable)`；
- 用途：测试断言与后续接入指标的基础；
- 约束：不得暴露为公共 API，不得新增未被使用的 reset 方法。

## 5. SPI 与资源契约（不得变更）

| 资源 | 内容 |
| --- | --- |
| `META-INF/dubbo/org.apache.dubbo.rpc.Filter` | `dubboExceptionFilter`、`dubboClientTraceIdFilter`、`dubboServerTraceIdFilter` |
| `META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector` | `dubboPenetrateAttachmentSelector` |
| `security/serialize.allowlist` | `com.google.common.collect.HashBasedTable`（`GXBaseServeApi#count(Table)` 入参类型需要） |

## 6. 已知问题登记（本次不修）

| 问题 | 位置 | 处理 |
| --- | --- | --- |
| `serialize-check-status: WRAN` 非法（合法值为 `DISABLE`/`WARN`/`STRICT`，`SerializeCheckStatus.valueOf` 会抛 `IllegalArgumentException`） | `config/application-dubbo-zk.yml.conf` 第 13 行 | 手册登记；超出模块边界，由样例配置专项目统一修正 |
| `GXBusinessException.data == null` 时降级为基类异常 | `GXDubboExceptionFilter#attachTraceId` | 手册登记；需上游 `leaf-base-framework` 提供可写 `data` 的构造协议后才能保真 |

## 7. 不支持范围（显式声明）

- Sentinel 原生指标/控制台规则管理：本模块只做异常语义转换，不集成 Sentinel 指标。
- Zookeeper 注册中心端到端服务发现：属于 `dubbo-zookeeper-curator5-spring-boot-starter` 的能力，本模块不封装也不在测试中连真实 ZK。
- Provider 侧权限/鉴权 filter、消费者路由：不在本模块范围。
