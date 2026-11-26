# 契约：配置项与示例配置

**模块**: `leaf-base-eureka-server` | **版本**: 4.3.0

## 1. 新增开关

| 属性 | 值 |
| --- | --- |
| 键名 | `maple.framework.enable.eureka-server` |
| 类型 | `java.lang.Boolean` |
| 默认值 | `false` |
| 绑定方式 | `@ConditionalOnProperty(name = "maple.framework.enable.eureka-server", havingValue = "true", matchIfMissing = false)` |
| 元数据 | `META-INF/additional-spring-configuration-metadata.json`（`name` / `type` / `defaultValue` / `description`） |
| 命名依据 | 宪法 IV：`maple.framework.enable.<feature>`，默认关闭 |

**语义**：`true` = 本应用充当 Eureka 注册中心；`false`/缺省 = 不装配任何注册中心能力，且不输出 WARN/ERROR。

## 1.1 模块施加的隐式默认：`eureka.client.enabled`

| 属性 | 值 |
| --- | --- |
| 键名 | `eureka.client.enabled`（Eureka 原生键，本模块不新增命名空间） |
| 施加方式 | `GXEurekaServerClientDefaultsPostProcessor`（`EnvironmentPostProcessor`，经 `META-INF/spring.factories` 注册） |
| 触发条件 | 应用**不是 Servlet 注册中心**：开关非 `true`，或开关为 `true` 但 `spring.main.web-application-type` 非 `servlet` |
| 取值 | `false`，以**最低优先级属性源**（`addLast`）写入 |
| 覆盖规则 | 应用在任意更高优先级位置显式配置 `eureka.client.enabled` 时，以应用值为准，本模块不覆盖 |
| 可观测性 | 施加时输出一条 ASCII INFO，说明"因不是 Servlet 注册中心而默认禁用客户端，显式配置可保留客户端行为" |
| 回归用例 | `GXEurekaServerClientDefaultsPostProcessorTest`（开关缺省 / Servlet 注册中心保留 / reactive + 开关仍禁用 / 显式配置优先） |

**为什么需要**：`spring-cloud-starter-netflix-eureka-server` 会传递引入 Eureka **client** 自动配置；若不禁用，应用会在启动末期尝试向 `eureka.client.service-url.defaultZone`（默认 `http://localhost:8761/eureka/`）注册自身，注册中心不可达时抛 `Failed to start bean 'eurekaAutoServiceRegistration'` 并终止启动（F-15、F-16）。

## 1.2 模块施加的单机默认：`register-with-eureka` / `fetch-registry`

| 属性 | 值 |
| --- | --- |
| 键名 | `eureka.client.register-with-eureka`、`eureka.client.fetch-registry`（Eureka 原生键） |
| 施加方式 | 同一 `GXEurekaServerClientDefaultsPostProcessor`（`EnvironmentPostProcessor`） |
| 触发条件 | 应用**是** Servlet 注册中心（开关 `true` 且 Web 类型为 servlet） |
| 取值 | 两者均 `false`，以**最低优先级属性源**（`addLast`）分别写入；已存在显式值时该键不写入 |
| 覆盖规则 | 应用或集群示例显式设 `true` 时以显式值为准（对等复制必须显式设为 `true`） |
| 可观测性 | 施加时输出一条 ASCII INFO，列出实际写入的键 |
| 回归用例 | `GXEurekaServerSwitchSpringBootTest#switchOnlySingleMachineDefaultsToNoRegistration`、`GXEurekaServerClientDefaultsPostProcessorTest#appliesSingleMachineDefaultsWhenServletRegistryIsEnabled`、`#keepsExplicitRegistrationSettingsWhenServletRegistryIsEnabled` |

**为什么需要**：这两项是与部署位置无关的单机语义（`spec.md` FR-008 与 `AGENTS.md` 第 3 节基线）。模块 jar 内不再有 `application.yml`，若不补默认，只设置开关的单机部署会回落到 Eureka 原生默认 `true/true` + `defaultZone=http://localhost:8761/eureka/`，导致注册中心把自身注册到未必存在的 8761 端口并反复拉取注册表。

## 2. 默认配置（模块 jar 内不承载）

修复后模块**不再包含** `classpath:/application.yml`。因此：

| 配置项 | 框架是否提供默认值 | 说明 |
| --- | --- | --- |
| `maple.framework.enable.eureka-server` | 是（`false`） | 模块自有开关（见 §1） |
| `eureka.client.enabled` | 是（非 Servlet 注册中心场景默认 `false`） | 模块施加的隐式默认（见 §1.1），应用显式配置优先 |
| `eureka.client.register-with-eureka` | 是（Servlet 注册中心场景默认 `false`） | 模块施加的最低优先级默认（见 §1.2）；集群成员需显式设 `true` |
| `eureka.client.fetch-registry` | 是（Servlet 注册中心场景默认 `false`） | 同上 |
| `eureka.client.service-url.defaultZone` | 否 | 必须由部署方提供 |
| `eureka.instance.hostname` / `ip-address` / `prefer-ip-address` | 否 | 必须由部署方提供 |
| `server.port` | 否 | 必须由部署方提供（示例给参考值） |

> 关键迁移提示：升级后，原先"依赖框架 jar 内 `application.yml` 获得 `false/false`"的应用仍由模块以最低优先级默认给出 `false/false`（仅 Servlet 注册中心场景），单机语义不变；但 `defaultZone`、`instance.hostname`、`instance.ip-address`、`server.port` 不再有任何框架默认值，必须由部署方提供。集群成员必须显式把 `register-with-eureka` / `fetch-registry` 设为 `true`，否则不会参与对等复制。

## 3. 示例配置（随 jar 分发，不自动加载）

| 文件 | 用途 | 必含 | 必不含 |
| --- | --- | --- | --- |
| `src/main/resources/examples/eureka-server-standalone.yml` | 单机注册中心 | `eureka.client.register-with-eureka: false`、`eureka.client.fetch-registry: false`、`maple.framework.enable.eureka-server: true` | 任何固定 IP、域名、端口、`defaultZone` |
| `src/main/resources/examples/eureka-server-peer.yml` | 集群成员 | `eureka.client.register-with-eureka: true`、`eureka.client.fetch-registry: true`、`service-url.defaultZone`（≥2 个对端，占位符形式）、`instance.hostname`（占位符）、`server.port`（占位符或示例值） | `127.0.0.1`、任何需要 hosts 解析的固定域名 |

**取值约束**：示例中的可变项一律使用 `${ENV_VAR:default}` 或 `${property:default}` 占位符，便于 `AGENTS.md` 第 51 条"不固化本地域名/私有地址"的核验。

## 4. 单机 / 集群配置矩阵

| 配置项 | 单机 | 集群成员 |
| --- | --- | --- |
| `maple.framework.enable.eureka-server` | `true` | `true` |
| `server.port` | 部署方指定（示例注释 7001） | 各节点不同（7001/7002/7003） |
| `eureka.instance.hostname` | 不设置（默认取本机名） | 各节点唯一（占位符） |
| `eureka.instance.prefer-ip-address` | 不设置 | 视网络而定，容器环境建议 `true` 且不写 `ip-address` |
| `register-with-eureka` | `false` | `true`（对等注册是复制的前提） |
| `fetch-registry` | `false` | `true` |
| `service-url.defaultZone` | 不设置 | 其余节点的 `http://<host>:<port>/eureka/` |

## 5. 迁移说明（破坏性变更）

| 旧行为（≤ 4.2.x / 现状） | 新行为（4.3.0 起） | 迁移动作 |
| --- | --- | --- |
| 引入模块（或聚合门面）即启用注册中心 | 需 `maple.framework.enable.eureka-server=true` | 注册中心应用显式加开关；门面消费者无需任何动作 |
| 聚合门面默认包含本模块 | 门面不再包含 | 注册中心应用在自身 `pom.xml` 声明 `leaf-base-eureka-server` |
| jar 内 `application.yml` 提供 `false/false` 与示例 `defaultZone` | jar 内无 `application.yml` | 从 `examples/` 复制对应文件并按环境填值 |
| `ip-address: 127.0.0.1`、`hostname: eureka7001.com` 作为默认 | 不再提供 | 部署方按实际网络填写；容器场景使用 `prefer-ip-address` 自动探测 |

## 6. 反例（必须被判为不通过）

- 元数据中的 `defaultValue` 与代码 `matchIfMissing` 不一致。
- 示例配置出现 `127.0.0.1` 或需要 hosts 的固定域名。
- 开关键落在 `eureka.*` 命名空间下（与上游冲突）。
- 保留了其它旧键别名（宪法 II 不要求为从未生效的键保留兼容）。
