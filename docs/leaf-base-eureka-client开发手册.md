# leaf-base-eureka-client 开发手册

## 1. 模块定位

`leaf-base-eureka-client` 提供 Eureka **客户端**接入能力：把本模块加入依赖，应用即具备向注册中心注册自身、并通过服务名做客户端负载均衡调用的基础条件。

它不负责：启动注册中心（见 `leaf-base-eureka-server开发手册.md`）、自动开启 Feign 扫描、提供注册地址默认值、定义业务 Feign 接口。

继承关系上，本模块只依赖 `leaf-base-feign`（提供 `spring-cloud-starter-openfeign` 与熔断基础）与 `leaf-base-webclient`（提供 `@LoadBalanced WebClient.Builder`、`HttpServiceProxyFactory` 与 `spring-cloud-starter-loadbalancer`），不引入 Web 容器。

## 2. 快速接入

1. 引入依赖（版本由父 POM 管理，不要写死）：

```xml
<dependency>
    <groupId>cn.maple.framework</groupId>
    <artifactId>leaf-base-eureka-client</artifactId>
    <version>${project.parent.version}</version>
</dependency>
```

2. 在宿主 `application.yml` 中设置应用名与注册中心地址（复制第 4 节示例或参考 `examples/`）。
3. 业务方自行在主类上开启 Feign 扫描：`@EnableFeignClients(basePackages = "...")`——本模块**不会**替你开启。
4. 启动应用，日志中出现 `DiscoveryClient_<APP>/...: registering service...` 表示已发起注册。

## 3. 装配开关

| 配置键 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `maple.framework.enable.eureka-client` | `Boolean` | `true` | `false` 时不装配 Eureka 客户端，也不会注册；适合"依赖在 classpath 但暂不使用注册中心"的场景 |

开关关闭（`false`）时：

- 上下文不出现 Eureka 客户端 Bean（`EurekaClient`、`eurekaAutoServiceRegistration`）；
- 框架以**最低优先级**写入 `eureka.client.enabled=false`，宿主显式配置仍然优先；
- 输出一条 ASCII 日志：`Eureka client disabled via maple.framework.enable.eureka-client=false`。

开关开启（默认）时，框架**不写入**任何 `eureka.*` 键：注册地址与注册开关完全由宿主决定。

## 4. 配置项与示例

框架不提供以下默认值（历史版本曾在 jar 内 `application.yml` 提供，已移除）：

| 配置键 | 框架默认 | 由谁设置 |
| --- | --- | --- |
| `eureka.client.register-with-eureka` | 无（回落到 Spring Cloud 默认 `true`） | 宿主 / 示例文件 |
| `eureka.client.service-url.defaultZone` | 无（回落到 Spring Cloud 默认 `http://localhost:8761/eureka/`） | 宿主 / 示例文件 |
| `eureka.client.fetch-registry` | 无 | 宿主 / 示例文件 |

未配置 `defaultZone` 时，启动日志会出现一条告警，提示注册将发往 Spring Cloud 默认地址：

```
Eureka client default zone is not configured (eureka.client.service-url.defaultZone); registration will target the Spring Cloud default.
```

单注册中心（示例文件 `examples/eureka-client-default.yml`）：

```yaml
eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: ${EUREKA_DEFAULT_ZONE:http://localhost:8761/eureka/}
```

多注册中心（示例文件 `examples/eureka-client-multi-registry.yml`）：

```yaml
eureka:
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      defaultZone: ${EUREKA_REGISTRY_URLS:http://localhost:7001/eureka/,http://localhost:7002/eureka/,http://localhost:7003/eureka/}
```

## 5. 示例文件的获取与覆盖顺序

示例文件随 jar 打包在 `examples/` 下，**不在** `classpath:/` 根，因此不会被自动加载。

取值方式（注意：`Expand-Archive` 只支持 `.zip`，对 `.jar` 会报"不是支持的存档文件格式"，请使用 `jar xf`）：

```powershell
# 从本地仓库的 jar 中取出示例（路径与版本按实际替换）
New-Item -ItemType Directory -Force .\eureka-client-example | Out-Null
Push-Location .\eureka-client-example
jar xf "$env:USERPROFILE\.m2\repository\cn\maple\framework\leaf-base-eureka-client\4.3.0-SNAPSHOT\leaf-base-eureka-client-4.3.0-SNAPSHOT.jar" examples/
Pop-Location
Get-ChildItem .\eureka-client-example\examples
```

或直接从源码目录复制：`leaf-base-eureka-client/src/main/resources/examples/`。

**覆盖顺序**（由高到低，实测结论见 `specs/009-eureka-client-module-audit/audit-report.md` §5）：

1. 命令行参数（`--eureka.client.service-url.defaultZone=...`）
2. 环境变量（本手册示例使用 `EUREKA_DEFAULT_ZONE` / `EUREKA_REGISTRY_URLS`）
3. 宿主 `application-{profile}.yml`
4. 宿主 `application.yml`
5. Spring Cloud 自身默认值（`http://localhost:8761/eureka/`）

框架不再占用第 4/5 层之间的位置，因此不会出现"框架默认值覆盖宿主配置"或"框架默认值被静默丢弃"的情况。

## 6. Feign 熔断相关配置（不在本模块）

历史版本曾在 jar 内 `application.yml` 提供：

```yaml
spring.cloud.openfeign.circuitbreaker.enabled: true
spring.cloud.openfeign.circuitbreaker.group.enabled: false
spring.cloud.openfeign.circuitbreaker.alphanumeric-ids.enabled: true
```

这三个键属于 Feign 能力，已移出本模块。当前框架的真实提供路径是环境模板 `config/{dev,test,pre,prod}/app-environment.yml`（已含相同取值）。若你的应用未使用这些模板，请自行在上述位置或宿主 `application.yml` 中显式配置。

## 7. 升级必查（4.3.0 起）

| 变更 | 影响 | 动作 |
| --- | --- | --- |
| jar 内不再有 `application.yml` | 依赖框架默认 `defaultZone` 的应用会回落到 `localhost:8761` | 复制 `examples/` 示例到宿主配置并设置真实地址 |
| 框架不再声明 `register-with-eureka` | 依赖框架默认 `true` 的应用行为不变（Spring Cloud 默认同为 `true`） | 无需动作；不想注册时置开关为 `false` |
| `spring.cloud.openfeign.circuitbreaker.*` 移出本模块 | 只用本模块、未用环境模板的应用将失去熔断默认值 | 使用 `config/{env}/app-environment.yml` 或显式配置 |
| 新增开关 `maple.framework.enable.eureka-client` | 无破坏性影响 | 需要"引入但不注册"时置为 `false` |

## 8. 故障排查

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 启动成功但日志持续出现 `TransportException: Cannot execute request on any known server` | 注册中心不可达；注册与拉取按 30s 重试，**不影响应用启动** | 检查 `defaultZone`、网络与注册中心状态；或置开关为 `false` 停止尝试 |
| `Eureka client default zone is not configured` | 未配置 `defaultZone`，正在使用 Spring Cloud 默认地址 | 显式配置 `eureka.client.service-url.defaultZone` |
| 服务注册成功但调用方找不到实例 | 应用名不一致、实例被注册到另一注册中心、或调用方未启用负载均衡 | 核对 `spring.application.name`、`defaultZone` 与调用方依赖 |
| 关闭开关后仍然看到 Eureka Bean | 宿主显式设置了 `eureka.client.enabled=true`（应用值优先） | 去掉该显式配置或改为 `false` |
| 设置 `eureka.client.enabled=false` 后不再出现 `Eureka client default zone is not configured` 告警 | 客户端已被显式关闭，模块不再提示 `defaultZone` 缺失（告警抑制是预期行为，**不代表已正确配置**） | 若期望注册，请去掉该配置；如需"引入但不注册"则改用 `maple.framework.enable.eureka-client=false` |
| Feign 客户端未走熔断 | 三个 `spring.cloud.openfeign.circuitbreaker.*` 键缺失 | 使用 `config/{env}/app-environment.yml` |

## 9. 验证命令

```powershell
# 模块测试（23 个用例）
mvn -o -pl leaf-base-eureka-client test

# 构建期静态检查（ErrorProne + NullAway）
mvn -o -pl leaf-base-eureka-client clean install -DskipTests

# 依赖形态核对
mvn -o -pl leaf-base-eureka-client dependency:tree "-Dverbose=true"
```

相关契约与审查证据：`specs/009-eureka-client-module-audit/`（`spec.md`、`contracts/`、`audit-report.md`）。
