# 契约：RabbitMQ 配置来源

**Feature**: `016-rabbitmq-module-audit` | **Date**: 2026-10-25

## 配置键

| 键 | 默认值 | 说明 | 状态 |
| --- | --- | --- | --- |
| `spring.rabbitmq.*` | Spring Boot 默认 | 连接、监听、模板配置；由 `GXRabbitMQProperties` 绑定 | 不变 |
| `maple.framework.mq.rabbitmq.enable` | `false` | 默认监听器启停 | 不变 |
| `maple.framework.mq.rabbitmq.default-queue-name` | 见下 | 默认监听队列名（最高优先级覆盖） | 不变 |
| `maple.framework.rabbitmq.config-source` | `nacos` | `local` / `nacos`，互斥装配 | **新增** |
| `maple.framework.rabbitmq.scheduler.expose` | `true` | 是否把 `rabbitTaskScheduler` 暴露为 `TaskScheduler` Bean | **新增** |
| `maple.framework.rabbitmq.thread-pool.core-size` | `CPU` | 异步声明执行器核心线程 | 不变 |
| `maple.framework.rabbitmq.thread-pool.max-size` | `CPU×2` | 最大线程（受队列容量约束） | 不变 |
| `maple.framework.rabbitmq.thread-pool.queue-capacity` | `1000` | `<=0` 时使用同步队列 | 不变（语义文档化） |

## 来源矩阵

| `config-source` | 可用条件 | 生效 Bean | 实际加载 | 启动日志（ASCII） |
| --- | --- | --- | --- | --- |
| 未配置（默认） | — | `GXNacosRabbitMQProperties` | Nacos（有地址时）+ `application.yml` | `RabbitMQ config source: nacos (default), dataId=rabbit.yml, loaded=<bool>, keys=<n>` |
| `nacos` | 始终 | `GXNacosRabbitMQProperties` | 同上 | 同上 |
| `local` | 始终 | `GXLocalRabbitMQProperties` | `classpath:/<profile>/rabbit.yml` + `file:./config/<profile>/rabbit.yml`（任一存在即加载） | `RabbitMQ config source: local, profile=<p>, resources=[...]` |

- 两个变体共享 `@ConfigurationProperties(prefix="spring.rabbitmq")`，业务代码只面向 `GXRabbitMQProperties` 基类。
- 默认值 `nacos` 与修复前"实际生效变体"一致，避免 Bean 身份变化。
- 两个变体均标注 `@Primary`：Spring Boot 的 `RabbitAutoConfiguration` 同时看到 Boot 自带 `RabbitProperties` 与模块变体，不指定主 Bean 会抛 `NoUniqueBeanDefinitionException`（F-25）。

## 优先级（从高到低）

```text
命令行参数 > application.yml / application-*.yml > nacos:rabbit.yml（addLast）> config/<profile>/rabbit.yml
```

- Nacos 属性源以 `environment.getPropertySources().addLast(...)` 加入，保证既有本地显式配置仍可覆盖，不改变现有部署行为。
- 本地 `@PropertySource` 默认遵循 Spring 规则（后加载覆盖先加载），文档中声明"两处同名键时 `file:` 覆盖 `classpath:`"。

## Nacos 拉取规则

| 项 | 值 |
| --- | --- |
| 触发条件 | `config-source=nacos` **且** `spring.cloud.nacos.config.server-addr` 或 `nacos.config.server-addr` 非空 |
| dataId / group | `rabbit.yml` / `${spring.cloud.nacos.config.group:${nacos.config.group:}}`（空则 `DEFAULT_GROUP`） |
| 账号 | 复用既有 `spring.cloud.nacos.username/password`、`nacos.config.*` |
| 超时 | 2000ms（不可配置，避免新增键） |
| 失败 | 记录 `warn`（ASCII，含 dataId 与异常类名，不含密钥），继续启动 |
| 地址为空 | 不发起任何网络调用，`loaded=false`，日志级别 `info` |
| 解析 | `YamlPropertySourceLoader`（Spring Boot 自带），多文档按顺序合并 |

## 不得出现

- 日志或异常中出现 `password`、`secret`、完整地址列表。
- 以 `rabbit.yml` 缺失为由阻断启动。
- 两套 Properties 变体同时装配（`@ConditionalOnProperty` 互斥，测试断言另一变体 Bean 不存在）。

## Metadata

`src/main/resources/META-INF/additional-spring-configuration-metadata.json` 必须登记：`maple.framework.rabbitmq.config-source`、`maple.framework.rabbitmq.scheduler.expose`、三个 `thread-pool.*`、`maple.framework.mq.rabbitmq.enable`、`maple.framework.mq.rabbitmq.default-queue-name`，并为前两者提供 `defaultValue` 与枚举提示。

## 验证要点

| 场景 | 断言 |
| --- | --- |
| 未配置 `config-source` | `GXNacosRabbitMQProperties` 存在，`GXLocalRabbitMQProperties` 不存在 |
| `config-source=local` | 反之；`classpath:` 与 `file:` 两种资源各命中一次 |
| `config-source=nacos` + 无 server-addr | 不抛异常；日志 `loaded=false`；`application.yml` 值可读 |
| `config-source=nacos` + 伪 ConfigService 返回 YAML | 键进入 Environment，且 `application.yml` 同名键仍优先 |
| 伪 ConfigService 抛异常 | 启动成功，`warn` 日志含 dataId，不含口令 |
| 两个变体 | 任意取值下仅一个 Bean 存在 |
