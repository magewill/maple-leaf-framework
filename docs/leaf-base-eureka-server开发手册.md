# leaf-base-eureka-server 开发手册

本模块提供 Eureka 注册中心的**服务端接入能力**：把本模块加入依赖并把开关打开，应用即成为 Eureka 注册中心。它不负责业务服务的注册、发现与远程调用，那些能力属于 `leaf-base-eureka-client`、`leaf-base-feign` 与 `leaf-base-webclient`。

## 1. 依赖与启用

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-eureka-server</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

```yaml
maple:
  framework:
    enable:
      eureka-server: true
```

| 开关 | 类型 | 默认值 | 生效条件 |
| --- | --- | --- | --- |
| `maple.framework.enable.eureka-server` | Boolean | `false` | 仅在 Servlet 应用中且显式置为 `true` 时启用 `@EnableEurekaServer` |

**要点**

1. 引入依赖**不会**自动成为注册中心，必须显式开启开关。
2. WebFlux / 非 Web 应用即使开关为 `true` 也不会启用（`@ConditionalOnWebApplication(type = SERVLET)`）。
3. 聚合门面 `leaf-framework` 不再传递本模块，注册中心应用需自行声明依赖。
4. 只要应用不是"Servlet 注册中心"（开关关闭，或开关开启但应用是 WebFlux / 非 Web），本模块会把 `eureka.client.enabled` 以最低优先级默认置为 `false`，避免 starter 自带的客户端自动注册在注册中心不可达时导致应用启动失败；应用显式配置 `eureka.client.enabled` 时以应用配置为准。
5. 应用是"Servlet 注册中心"时，本模块以最低优先级默认给出单机语义 `eureka.client.register-with-eureka=false` 与 `eureka.client.fetch-registry=false`：只设置开关即可作为单机注册中心使用，不会向未配置的 `defaultZone` 注册自身；集群成员必须显式把这两项设为 `true`（`examples/eureka-server-peer.yml` 已给出）。

## 2. 单机注册中心

复制 `src/main/resources/examples/eureka-server-standalone.yml`（随 jar 分发，位于 `examples/` 目录，不会被自动加载），并按应用实际情况设置 `server.port`：

```yaml
maple:
  framework:
    enable:
      eureka-server: true

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

单机形态下注册中心不向自身注册、也不拉取注册表，这是 `AGENTS.md` 登记的基线行为，不得修改默认值。

## 3. 集群注册中心

复制 `src/main/resources/examples/eureka-server-peer.yml`，每个节点覆盖占位符：

```yaml
maple:
  framework:
    enable:
      eureka-server: true

server:
  port: ${SERVER_PORT:7001}

eureka:
  instance:
    hostname: ${EUREKA_INSTANCE_HOSTNAME:localhost}
    prefer-ip-address: ${EUREKA_PREFER_IP_ADDRESS:true}
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: ${EUREKA_PEER_URLS:http://localhost:7002/eureka/,http://localhost:7003/eureka/}
```

| 配置项 | 单机 | 集群成员 |
| --- | --- | --- |
| `server.port` | 部署方指定 | 各节点不同（7001/7002/7003） |
| `eureka.instance.hostname` | 不设置 | 各节点唯一 |
| `eureka.instance.prefer-ip-address` | 不设置 | 容器环境建议 `true`，且不要再写死 `ip-address` |
| `eureka.client.register-with-eureka` | `false` | `true` |
| `eureka.client.fetch-registry` | `false` | `true` |
| `eureka.client.service-url.defaultZone` | 不设置 | 其余节点的 `http://<host>:<port>/eureka/` |

**注意**：`register-with-eureka=false` + `fetch-registry=false` 时，节点既不会注册到对端，也不会拉取对端注册表；这种配置只能作为单机注册中心使用，不能用作集群成员。

## 4. 框架不提供的默认配置

模块 jar 内**没有** `classpath:/application.yml`，以下配置一律由部署方提供：

- `server.port`
- `eureka.instance.hostname` / `eureka.instance.ip-address` / `eureka.instance.prefer-ip-address`
- `eureka.client.service-url.defaultZone`

原因：这些值与环境强相关，固化为框架默认值会在容器、多网卡或跨主机部署时把环回地址或错误域名写进注册表。

与位置无关的两项由框架提供默认值：`maple.framework.enable.eureka-server`（默认 `false`）、`eureka.client.register-with-eureka` / `eureka.client.fetch-registry`（Servlet 注册中心场景默认 `false`，显式配置优先）。

## 5. 迁移说明（破坏性变更）

| 旧行为 | 新行为 | 迁移动作 |
| --- | --- | --- |
| 引入模块（或聚合门面）即启用注册中心 | 需 `maple.framework.enable.eureka-server=true` | 注册中心应用在配置中显式开启开关 |
| 聚合门面默认包含本模块 | 门面不再包含 | 注册中心应用在自身 `pom.xml` 声明 `leaf-base-eureka-server` |
| jar 内 `application.yml` 提供 `register-with-eureka=false`、`fetch-registry=false` 与示例 `defaultZone` | jar 内无 `application.yml`；`false/false` 改为模块以最低优先级提供的默认值（显式配置优先），`defaultZone` 不再有默认 | 单机无需额外动作；集群从 `examples/` 复制集群配置，显式设 `true` 并填 `defaultZone` |
| 默认 `ip-address: 127.0.0.1`、`hostname: eureka7001.com` | 不再提供 | 按实际网络填写；容器场景使用 `prefer-ip-address` 自动探测 |
| 依赖 `spring-boot-devtools` 随库下发 | 已移除 | 需要热重载的应用自行声明 `spring-boot-devtools`（建议同时设置为不可传递） |

**受影响应用清单（可执行判定）**

升级前先在依赖方工程执行，筛出仍通过聚合门面间接依赖本模块的应用（应输出为空；非空即表示该应用需要迁移）：

```powershell
mvn dependency:tree | Select-String -Pattern "leaf-base-eureka-server"
```

若输出非空，按以下顺序处理：

1. 判定该应用是否真的需要充当注册中心（只需要"注册自己"的应用应使用 `leaf-base-eureka-client`）；
2. 需要时在自身 `pom.xml` 显式声明 `leaf-base-eureka-server`，并设置 `maple.framework.enable.eureka-server=true`；
3. 从 `examples/` 复制配置并替换全部占位符；
4. 移除对框架旧 `application.yml` 默认值的依赖（尤其是 `defaultZone` 与 `ip-address`）。

## 6. 依赖说明

| 依赖 | 说明 |
| --- | --- |
| `spring-cloud-starter-netflix-eureka-server` | 注册中心核心，版本由 `spring-cloud-dependencies` BOM 管理，模块内不写版本 |
| `jersey-apache-connector` | **必需**：`eureka-core-jersey3` 的复制客户端硬编码 `org.glassfish.jersey.apache.connector.ApacheConnectorProvider`，移除或替换会导致对等复制初始化失败（`NoClassDefFoundError`） |
| `httpclient` 4.5.x | 上述 Jersey 3 连接器的传递依赖，版本由根 POM 统一管理 |

> Jersey 容器侧使用 4.0.x、复制客户端侧使用 3.1.x 属上游约束，不是本模块的选择；升级 Spring Cloud 时必须同时验证对等复制。

## 7. 验证

```powershell
# 模块测试（含真实注册中心启动与双节点探针）
mvn -pl leaf-base-eureka-server test

# 打包并确认 jar 内无根路径 application.yml
mvn -pl leaf-base-eureka-server package
jar tf leaf-base-eureka-server/target/leaf-base-eureka-server-4.3.0-SNAPSHOT.jar | Select-String "application.yml"
```

期望：`application.yml` 只出现在 `examples/` 目录下；测试全部通过。

## 8. 相关文档

- 模块开发约定：[`leaf-base-eureka-server/AGENTS.md`](../leaf-base-eureka-server/AGENTS.md)
- 本次审查与修复计划：[`specs/008-eureka-server-module-audit/`](../specs/008-eureka-server-module-audit/)
