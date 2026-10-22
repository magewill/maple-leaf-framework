# 配置契约：leaf-base-eureka-client

**Feature**: `009-eureka-client-module-audit` | **Date**: 2026-10-22

本契约约束"哪些键由本模块负责、取值从哪来、升级时改什么"。

## 1. 键位归属（修复后）

| 配置键 | 修复前（本模块 jar 内 `application.yml`） | 修复后 | 归属 | 说明 |
| --- | --- | --- | --- | --- |
| `maple.framework.enable.eureka-client` | 不存在 | **新增**，默认 `true` | 本模块 | 关闭后不装配 Eureka 客户端（见装配契约 §1） |
| `eureka.client.register-with-eureka` | `true` | **不再由框架提供** | 应用 / 示例 | 回归 Spring Cloud 默认（`true`），框架不再背书 |
| `eureka.client.service-url.defaultZone` | `http://eureka7001.com:7001/eureka/,...7002..., ...7003...` | **不再由框架提供** | 应用 / 示例 | 框架默认不得出现固定域名、地址或端口 |
| `spring.cloud.openfeign.circuitbreaker.enabled` | `true` | **移出本模块** | `config/{dev,test,pre,prod}/app-environment.yml` | 该模板已含三个键；本模块 jar 内不得再出现任何 `spring.cloud.openfeign.*` |
| `spring.cloud.openfeign.circuitbreaker.group.enabled` | `false` | 同上 | 同上 | —— |
| `spring.cloud.openfeign.circuitbreaker.alphanumeric-ids.enabled` | `true` | 同上 | 同上 | 上游 openfeign 5.0.3 仍支持该键 |
| `eureka.client.enabled` | 不涉及 | 仅当开关为 `false` 时以最低优先级写 `false` | 本模块（PostProcessor） | 应用显式值优先 |

## 2. jar 资源布局契约

```text
src/main/resources/
├── META-INF/
│   ├── spring.factories                                  # EnvironmentPostProcessor 注册
│   ├── additional-spring-configuration-metadata.json     # 开关元数据
│   └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── examples/
    ├── eureka-client-default.yml                         # 单注册中心
    └── eureka-client-multi-registry.yml                  # 多注册中心
```

**硬性约束**：

- `src/main/resources/application.yml` MUST 不存在（`target/classes/application.yml` 同样不得存在）；否则与宿主同名配置位置的歧义回归（spec F-02 / R-02 实测）。
- `examples/` 下的文件 MUST NOT 位于 `classpath:/` 根，MUST NOT 被 `spring.config.import` 自动引用。
- 任何默认值不得包含固定域名、固定 IP、固定端口或凭据。

## 3. 示例文件内容契约

| 文件 | 必含键 | 禁止 |
| --- | --- | --- |
| `eureka-client-default.yml` | `eureka.client.service-url.defaultZone: ${EUREKA_DEFAULT_ZONE:http://localhost:8761/eureka/}`、`eureka.client.register-with-eureka: true`、注释说明"复制到宿主 `application.yml` 后按环境改名" | `eureka700*.com`、真实内网地址、明文凭据 |
| `eureka-client-multi-registry.yml` | 三个及以上由 `${EUREKA_REGISTRY_URLS:...}` 占位符提供的 URL、`fetch-registry: true` | 固定集群拓扑写死为唯一默认 |

**校验**：示例必须能被 `.specify` 外的宿主直接复制运行；文档手册给出复制命令与覆盖顺序说明。

## 4. 升级迁移表（行为变更，需在 PR / 手册"升级必查"中列明）

| 变更 | 谁受影响 | 迁移动作 | 验证方式 |
| --- | --- | --- | --- |
| 框架不再提供 `defaultZone` | 依赖框架默认地址完成注册的应用 | 从 `examples/eureka-client-default.yml` 复制并改为真实地址，或显式设置 `eureka.client.service-url.defaultZone` | 启动日志出现 1 条 WARN 即表示未配置；配置后 WARN 消失且注册请求发往新地址 |
| 框架不再声明 `register-with-eureka` | 依赖"框架默认 `true`"的应用 | 无需动作（Spring Cloud 默认同为 `true`）；希望不注册的应用设置开关或 `eureka.client.enabled=false` | 注册请求是否发出 |
| `spring.cloud.openfeign.circuitbreaker.*` 移出本模块 | 仅引入 eureka-client 且未使用 `config/{env}/app-environment.yml` 的应用 | 使用环境模板或显式配置三个键 | Feign 客户端是否被熔断包装（`CircuitBreakerFactory` 生效） |
| 新增开关（默认 `true`） | 无 | 需要"引入但不注册"时设置 `maple.framework.enable.eureka-client=false` | 上下文中无 Eureka Bean |

## 5. 配置元数据契约

`META-INF/additional-spring-configuration-metadata.json` 必须且只声明：

| name | type | defaultValue | 说明 |
| --- | --- | --- | --- |
| `maple.framework.enable.eureka-client` | `java.lang.Boolean` | `true` | "Enable Eureka client auto-configuration contributions of this module."（描述可为中文，不进入日志） |

**约束**：不得声明 `eureka.*`（上游所有）；`defaultValue` 必须与 `matchIfMissing=true` 语义一致（`true`）。
