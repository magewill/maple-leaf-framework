# Contract: 配置、SPI、序列化白名单与可观测

**Feature**: 014-dubbo-nacos-module-audit | **Date**: 2026-10-25
**范围**: 配置键、SPI 扩展名、`serialize.allowlist`、日志与校验器

## 1. 配置键

| 键 | 默认 | 来源 | 说明 |
| --- | --- | --- | --- |
| `dubbo.provider.filter` | 未配置 | Dubbo 既有键 | **强制** `dubboExceptionFilter,-exception`（F-01，见异常契约） |
| `dubbo.consumer.filter` | 未配置 | Dubbo 既有键 | 可选；未配置时默认激活 consumer 侧 `@Activate` 扩展 |
| `spring.application.name` | — | Spring 既有键 | TraceId 生成前缀 + 日志 appName；本模块只缓存读取 |
| —（`maple.framework.dubbo.filter.check-strict` **不采纳**） | — | — | 本批不新增该键（YAGNI，宪法附加约束 §8）；校验器仅 ERROR 告警 |

**约束**：不新增其它配置键；`dubbo.provider.filter`/`dubbo.consumer.filter` 属 Dubbo 既有键，本模块只在文档登记，不改其语义。

## 2. SPI 扩展名（不得重命名）

| 文件 | 扩展名 | 实现类 | 分组 |
| --- | --- | --- | --- |
| `META-INF/dubbo/org.apache.dubbo.rpc.Filter` | `dubboExceptionFilter` | `GXDubboExceptionFilter` | provider |
| 同上 | `dubboClientTraceIdFilter` | `GXDubboClientTraceIdFilter` | consumer |
| 同上 | `dubboServerTraceIdFilter` | `GXDubboServerTraceIdFilter` | provider |
| `META-INF/dubbo/org.apache.dubbo.rpc.PenetrateAttachmentSelector` | `dubboPenetrateAttachmentSelector` | `GXPenetrateAttachmentSelector` | provider + consumer |

## 3. 序列化白名单（F-12）

- 当前内容：`com.google.common.collect.HashBasedTable`（仅一行）。
- **契约**：内容不变；在手册登记"来源/用途/新增传输对象时的评估流程"。
- 不得因修复而扩大白名单；如确需新增，必须在本文件与手册同步登记条目、原因与安全影响。

## 4. 启动期校验器（F-01）

- 类型：`GXDubboProviderFilterValidator`（`@Component`，`SmartInitializingSingleton`）。
- 行为：
  - 读取 `Environment.getProperty("dubbo.provider.filter")`；
  - 若为空 → ERROR：未排除内置 `exception`，业务码可能丢失；
  - 若非空但既不含 `-exception` 也不含 `dubboExceptionFilter` → ERROR/WARN（按严重度）；
  - `check-strict=true` 时抛 `GXBusinessException` 阻断启动。
- 输出必须 ASCII、包含修复指引与目标配置。

## 5. 日志与门面（F-14/F-10）

| 场景 | 级别 | 频率 |
| --- | --- | --- |
| TraceId 透传/接收 | DEBUG | 每请求 |
| `generateTraceId` 兜底 | DEBUG | 少见 |
| 未声明异常（provider） | ERROR | 异常路径 |
| filter 自身失败 | WARN + 计数 | 异常路径 |
| BeanPostProcessor 绑定成功/跳过 | INFO / WARN | 启动期 |
| 配置校验失败 | ERROR | 启动期 |

- 全部使用 SLF4J（`@Slf4j` 或 `LoggerFactory`），禁止 `@Log4j2`。
- 日志文案必须 ASCII（`AsciiSourceGuardTest` 守护）。

## 6. 兼容性

| 变更 | 兼容策略 |
| --- | --- |
| 不新增配置键 | 校验器仅 ERROR 告警，不改变既有启动行为 |
| `@Log4j2` → `@Slf4j` | 输出目标不变 |
| 启动 ERROR 告警 | 不阻断启动（`check-strict=false`） |
| 静态 `appName` 缓存 | 生产单上下文无感；测试/热部署用 `resetAppNameCache()` |
