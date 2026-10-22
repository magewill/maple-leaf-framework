# 契约：配置来源（`rocketmq.config-source`）

**范围**：`cn.maple.rocketmq.properties.local` / `properties.nacos` 的装配语义。**兼容性**：键名 `rocketmq.config-source`、取值 `local` / `nacos`、默认行为不得变更（宪法 II）。

## 1. 三态矩阵

| `rocketmq.config-source` | 类路径含 Nacos 注解类 | 实际装配 | 启动日志（INFO） |
| --- | --- | --- | --- |
| `local` | 任意 | 仅本地源 | `RocketMQ config source: local, profile=<p>, resource=<path or NOT_FOUND>` |
| `nacos` | 任意 | 仅 Nacos 校验器 | `RocketMQ config source: nacos, name-server=<addr or EMPTY>` |
| 未配置 | 否 | 仅本地源 | `RocketMQ config source: local (auto-detected, no nacos client), profile=<p>` |
| 未配置 | 是 | 仅 Nacos 校验器 | `RocketMQ config source: nacos (auto-detected); set rocketmq.config-source to make it explicit` |
| 其他值 | 任意 | 启动失败 | `IllegalStateException: unsupported rocketmq.config-source=<v>` |

**不变量**：任意时刻最多一个来源生效；`IGNORED` 状态不存在——即不允许"两个配置类都不装配且无日志"的静默形态（F-01 的直接原因）。

## 2. 本地源契约

- 资源路径：`classpath:/${spring.profiles.active:default}/rocket-mq.yml`。
- `ignoreResourceNotFound = true`：文件缺失**不失败**，但日志必须写明 `NOT_FOUND` 与解析后的路径。
- 绑定前缀：`rocketmq.*`，由 `rocketmq-spring-boot-starter` 的 `RocketMQProperties` 完成字段绑定；本模块的 `GX*Properties` 只承担"来源选择 + 日志"，**不新增字段**（避免双绑定歧义）。

## 3. Nacos 源契约

- 模块不加载 Nacos 数据本身，值由下游 `spring.config.import=nacos:<dataId>`（`NacosConfigDataLocationResolver`）提供，`dataId` 约定为 `rocket-mq.yml`。
- 启动校验（`config-source=nacos` 时）：
  - `rocketmq.name-server` 为空 → 抛 `GXBusinessException`（fail-fast），消息包含"nacos 来源下必须提供 rocketmq.name-server"。
  - `rocketmq.producer.group` 为空 → `warn`，不阻断（消费者场景可能不需要生产者）。
- 禁止：继续保留"无处理器的 `@NacosConfigurationProperties` 注解即视为加载成功"的假象。

## 4. 装配缺失提示

- 应用上下文中既无发送服务 Bean 也无 CDC Bean 时，模块无法感知（无自动装配）；因此**在本地源装配日志中追加一行**：`Component scanning hint: enable @GXEnableLeafFramework or scan cn.maple.rocketmq to activate this module's beans.`（对应 F-18）

## 5. 回归断言点

1. `config-source=local` → 本地 Bean 存在、`rocketmq.test-marker=local-source` 可读（修复当前失败用例）。
2. `config-source=nacos` → Nacos Bean 存在、本地 Bean 不存在；`name-server` 缺失时启动失败且错误可读。
3. 未配置 + 类路径含 nacos-client → Nacos Bean 存在且日志含 `auto-detected`。
4. `config-source=bogus` → 启动失败，消息含 `unsupported`。
