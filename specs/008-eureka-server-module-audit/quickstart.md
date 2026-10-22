# Quickstart: leaf-base-eureka-server 审查与修复验证

**Feature**: `008-eureka-server-module-audit` | **Date**: 2026-10-22

本指南用于复现审查结论、验证修复结果。全部命令在仓库根目录执行，Windows PowerShell 环境；**不依赖外网、不依赖外部注册中心**。

## 0. 前置条件

- JDK 21、Maven 3.9+；本地仓库已具备 `spring-boot 4.1.1` / `spring-cloud 2025.1.3` 依赖（首次执行会联网拉取）。
- 无 hosts 要求：所有验证使用 `localhost` 与随机端口。

## 1. 基线复现（审查证据 E-16）

```powershell
mvn -pl leaf-base-eureka-server test
```

期望：`Tests run: 23, Failures: 0, Errors: 0`，`BUILD SUCCESS`。

> 历史基线为 `Tests run: 2`（审查前，证据 E-16）；修复与后续收敛后为 23 个用例（6 个测试类），两者的差值即本轮新增的回归覆盖。

依赖树检查（不落盘，供 §4 逐坐标核对）：

```powershell
mvn -pl leaf-base-eureka-server dependency:tree | Select-String -Pattern "devtools|jersey|httpclient|hk2|woodstox|eureka-server"
```

## 2. 装配矩阵验证（`contracts/assembly-contract.md` A-01 ~ A-05、A-07）

```powershell
mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerConfigTest
```

期望（修复后）：

| 用例 | 期望 |
| --- | --- |
| `switchDisabledByDefaultInNonServletContext` | 缺省属性下不创建 `GXEurekaServerConfig` Bean |
| `eurekaServerConfigRequiresExplicitSwitch` | `@ConditionalOnProperty` 的 name=`maple.framework.enable.eureka-server`、havingValue=`true`、matchIfMissing=`false` |
| `eurekaServerConfigRequiresServletWebApplication` | `@ConditionalOnWebApplication(type=SERVLET)` 存在 |
| `missingEurekaServerClassSkipsGracefully` | `FilteredClassLoader(EnableEurekaServer)` 下上下文正常、无注册中心 Bean |
| `autoConfigurationImportContainsEurekaServerConfig` | `AutoConfiguration.imports` 仍含入口类 |

> 开关 `=true` 的运行期行为由 `GXEurekaServerSwitchSpringBootTest`（`registryEndpointsArePresentWhenSwitchIsEnabled` → `/eureka/apps` 200）与 `GXEurekaServerSpringBootTest`（真实注册往返）覆盖，不在 `GXEurekaServerConfigTest` 内以非 Web 上下文断言。

## 3. 真实启动与注册往返（`contracts/assembly-contract.md` A-01、A-03）

```powershell
mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerSpringBootTest
mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerSwitchSpringBootTest
mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerClientDefaultsPostProcessorTest
```

期望：

1. 随机端口启动成功，`GET /eureka/apps` 返回 `200`；
2. `POST /eureka/apps/{APP}` 注册实例后，`GET /eureka/apps/{APP}` 能查询到该实例；
3. 控制台出现一条 ASCII INFO：`Eureka Server enabled via maple.framework.enable.eureka-server=true, server.port=<配置值> (0 = random port)`（未显式设置 `server.port` 时输出 `server.port=<unset>`）；
4. `GXEurekaServerSwitchSpringBootTest`：开关缺省 → `/eureka/apps` = 404；开关 `true` → 200；开关缺省且**未配置任何 `eureka.client.*`** 时应用正常启动、`eureka.client.enabled=false`、`/eureka/apps` = 404（`applicationStartsWhenSwitchIsMissingAndClientPropertiesAreUnset`）；只设开关时 `register-with-eureka` / `fetch-registry` 默认为 `false`（`switchOnlySingleMachineDefaultsToNoRegistration`）；
5. `GXEurekaServerClientDefaultsPostProcessorTest`：6 个用例全绿（开关缺省禁用 / Servlet 注册中心保留 / reactive + 开关仍禁用 / 显式客户端配置优先 / Servlet 注册中心补单机默认 / 显式注册配置优先）。

同时记录（写入报告"资源与启动成本"表）：上下文启动耗时、常驻线程数增量、两次运行偏差是否 ≤ 30%。

## 4. 依赖治理验证（`contracts/dependency-contract.md` §3）

```powershell
mvn -pl leaf-base-eureka-server dependency:tree | Select-String -Pattern "devtools|jersey|httpclient|hk2|woodstox|eureka-server"
mvn -pl leaf-framework dependency:tree | Select-String -Pattern "leaf-base-eureka-server"
```

期望与契约 §3 的断言逐条对应（变更前快照已按 T044 清理，改为直接对当前依赖树断言）：

- 第一条中**无 `devtools`**；
- `jersey-apache-connector:3.1.12` **必须保留**（`eureka-core-jersey3` 复制客户端的硬依赖，替换为 `jersey-apache5-connector` 会 `NoClassDefFoundError`，见报告 E-18），且**不存在** `jersey-apache5-connector`；
- `jersey-server`/`jersey-client`/`jersey-container-servlet` 为 `4.0.2`，`jersey-hk2` 为 `4.0.3`；
- `httpclient:4.5.14` 仍存在（传递引入，模块内无直接声明），`httpclient5:5.6.4` 不变；
- `hk2-api`/`hk2-utils` 为 `4.0.3`、`woodstox-core` 为 `7.3.0`，均由传递引入；
- `spring-cloud-starter-netflix-eureka-server` 为 `5.0.2` 且模块内无 `<version>`；
- 第二条**无输出**。

期望：

- 第一条中**无 `devtools`**；
- `jersey-apache-connector:3.1.12` **必须保留**（`eureka-core-jersey3` 复制客户端的硬依赖，替换为 `jersey-apache5-connector` 会 `NoClassDefFoundError`，见报告 E-18）；
- `jersey-server`/`jersey-client`/`jersey-container-servlet` 为 `4.0.2`，`jersey-hk2` 为 `4.0.3`；
- `httpclient:4.5.14` 仍存在（传递引入），`httpclient5:5.6.4` 不变；
- 第二条**无输出**。

## 5. 消费方冒烟（D-12 的残余风险补偿）

**已于 2026-10-22 执行完毕**（仓库外工程：`spring-boot-starter-webflux` + 本模块，包名 `lab.flux`，避免默认包全类路径扫描）：

| 场景 | 观测 | 进程启动到 `/ping`=200（2 次） |
| --- | --- | --- |
| 不设开关，`--spring.main.web-application-type=reactive` | `GET /ping` = **200**；`GET /eureka/apps` = **404**；进程持续运行 | **6837 / 6712 ms**（偏差 1.8%） |
| 设置 `--maple.framework.enable.eureka-server=true` | `GET /ping` = **200**；`GET /eureka/apps` = **404**；进程持续运行 | **6712 / 6701 ms**（偏差 0.16%，与缺省同量级） |

结论：WebFlux 应用在两种开关状态下均正常启动，且都不装配注册中心（`@ConditionalOnWebApplication(SERVLET)` 生效，客户端由 `GXEurekaServerClientDefaultsPostProcessor` 默认禁用）；两行启动耗时与开关无关（注册中心未装配，无增量）。证据见 `audit-report.md` §5、§9 与 E-20。

**复现步骤**（无需修改本仓库依赖）：

1. 新建独立 Maven 工程，依赖 `spring-boot-starter-webflux` 与本模块（`mvn -pl leaf-base-eureka-server install -DskipTests` 后再引用）；
2. 写一个 `@SpringBootApplication` 类，放在具名包下（不要用默认包），提供一个 `GET /ping` 路由；
3. 用 `java -cp <依赖类路径> lab.flux.FluxApp --server.port=18801 --spring.main.web-application-type=reactive` 启动，分别验证上表两行；
4. 计时口径：以秒表从"启动 java 进程"到"`GET /ping` 首次返回 200"计（轮询间隔 50 ms），每场景跑 2 次、同机同 JDK，两次偏差应 ≤ 30%。

## 6. 启动语义矩阵（单机 / 集群，`contracts/configuration-contract.md` §4）

**单机**：

```powershell
# 复制 examples/eureka-server-standalone.yml 到消费方应用，设置 server.port=7001
# 启动后访问
curl.exe -s http://localhost:7001/eureka/apps
```

期望：返回空注册表 XML/JSON；日志中**不出现**向 `defaultZone` 注册自身的记录。

**集群（两节点）**：

```powershell
# 节点 A：examples/eureka-server-peer.yml，server.port=7001，defaultZone 指向 7002
# 节点 B：同样配置，server.port=7002，defaultZone 指向 7001
# 客户端注册到 A 后查询 B
curl.exe -s http://localhost:7002/eureka/apps
```

期望：B 的注册表中出现注册到 A 的实例（`replicated=true`）。若为 `false`，记录为"集群示例不可用"并回填报告。

## 7. 配置来源验证（D-04 / F-05）

```powershell
# 1) 确认模块 jar 内不再有根路径 application.yml
jar tf leaf-base-eureka-server/target/leaf-base-eureka-server-4.3.0-SNAPSHOT.jar | Select-String -Pattern "application.yml"
```

期望：只出现 `examples/eureka-server-standalone.yml` 与 `examples/eureka-server-peer.yml`，**没有** `application.yml`。

```powershell
# 2) 资源契约用例
mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerExamplesTest
```

期望：断言 `classpath:/application.yml` 为 `null`；两个示例存在且键值符合 `contracts/configuration-contract.md` §3。

## 8. 契约文档核对（SC-009）

```powershell
Select-String -Path leaf-base-eureka-server/AGENTS.md -Pattern "^## "
Select-String -Path leaf-base-eureka-server/src/main/resources/META-INF/additional-spring-configuration-metadata.json -Pattern "eureka-server"
Select-String -Path docs/leaf-base-eureka-server开发手册.md -Pattern "maple.framework.enable.eureka-server"
```

期望：`AGENTS.md` 章节编号连续（1、2、3……无跳号）；元数据含开关键；手册含开关与迁移章节。

## 9. 完成判定

| 检查 | 通过标准 |
| --- | --- |
| `mvn -pl leaf-base-eureka-server test` | 全绿（23 个用例，0 失败） |
| `mvn -pl leaf-base-eureka-server package` | 成功，jar 内无 `application.yml`、含 2 个示例与元数据 |
| 依赖断言 | 契约 §3 的 5 条断言全部满足（无 devtools、保留 `jersey-apache-connector:3.1.12`、无 apache5 坐标、`httpclient:4.5.14` 传递引入、starter 由 BOM 管理） |
| `mvn -pl leaf-framework dependency:tree` | 不含本模块 |
| 报告一致性 | `audit-report.md` 的 F 编号、级别与 `spec.md` 完全对应 |
