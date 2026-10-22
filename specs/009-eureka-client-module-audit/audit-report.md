# Audit Report — leaf-base-eureka-client（4.3.0-SNAPSHOT）

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Tasks**: [tasks.md](./tasks.md) | **Contracts**: [contracts/](./contracts/)
**审查时间**: 2026-10-22 | **执行环境**: Windows / JDK 25 运行时（编译目标 21）/ Maven 3.9 离线（`-o`）/ 本地仓库 `F:\repository`

本报告是 `spec.md` 摘要的证据载体：`spec.md` 只保留结论摘要与指向本报告的引用，两者交付时必须一致。

## 1. 概览与基线

### 1.1 被审查对象（改前）

| 类别 | 内容 | 行数 |
| --- | --- | --- |
| 生产 Java | `src/main/java/cn/maple/eureka/client/config/GXEurekaClientConfig.java` | 21 |
| 生产 Java | `src/main/java/cn/maple/eureka/client/config/package-info.java` | 2 |
| 生产资源 | `src/main/resources/application.yml` | 15 |
| 生产资源 | `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 1 |
| 测试 | `src/test/java/cn/maple/eureka/client/config/GXEurekaClientConfigTest.java` | 91（4 个用例） |
| 测试夹具 | `testapp/TestApplication.java`、`testapp/OrderClient.java`、`testapp/OrderAdminClient.java` | 7 / 10 / 10 |
| 契约文档 | `leaf-base-eureka-client/AGENTS.md`（86 行）、`README.md` 模块表第 70 行 | —— |
| 缺失交付物 | `docs/leaf-base-eureka-client开发手册.md`、`META-INF/additional-spring-configuration-metadata.json`、`examples/` | —— |

### 1.2 改前测试基线（红）

```powershell
mvn -o -pl leaf-base-eureka-client test
```

| 项 | 值 |
| --- | --- |
| 时间 | 2026-10-22 22:28 ~ 22:29（两次一致） |
| 结果 | `Tests run: 4, Failures: 1, Errors: 0, Skipped: 0` → `BUILD FAILURE` |
| 失败用例 | `GXEurekaClientConfigTest.applicationYamlContainsExpectedClientDefaults:82` |
| 期望 | `contains("spring:\n  cloud:\n    openfeign:\n      circuitbreaker:\n        enabled: true")` |
| 实际 | 同一段文本，但换行为 CRLF |
| 根因 | 断言以 `\n` 字面量匹配 YAML 文本；文件为 CRLF（F-04、F-11） |

### 1.3 平台与编码基线

| 项 | 值 |
| --- | --- |
| `git config core.autocrlf` | `true` |
| `.gitattributes` | 不存在 |
| 模块内文件换行 | 8 个文本文件全部 CRLF（java 5、yml 1、imports 1、AGENTS.md 1） |
| 结论 | 任何基于换行符的文本断言必然平台相关；测试必须改为语义断言（F-04 / plan D-08） |

### 1.4 改后结果（实施完成）

| 项 | 值 |
| --- | --- |
| 用例数 | 4 → **23**（Phase 1~9 新增 8、Phase 10 新增 9、Phase 11 新增 2；改写 1、删除 0）。当前分布：`GXEurekaClientConfigTest` 5、`GXEurekaClientContractTest` 5、`GXEurekaClientDefaultZoneTest` 4、`GXEurekaClientSwitchTest` 3、`GXEurekaClientCombinationTest` 3、`GXEurekaClientHostConfigTest` 2、`GXEurekaClientRegistrationTest` 1 |
| CRLF 工作区 | `Tests run: 23, Failures: 0`（2026-10-23 00:40 复测；Phase 9 时为 12/0 连续 3 次一致） |
| LF 归一化 | `Tests run: 23, Failures: 0`（全部源文件转 LF 后复测，见 §7.1） |
| jar 产物 | `target/classes/application.yml` **不存在**；`examples/` 含 2 个示例 |
| 语义残留 | `eureka700` 命中 0 次、`circuitbreaker` 命中 0 次；`register-with-eureka` 仅 2 次（均在 `examples/`） |

## 2. 证据表

| ID | 类型 | 位置 / 命令 | 摘要 | 采集时间 |
| --- | --- | --- | --- | --- |
| E-01 | SOURCE | `leaf-base-eureka-client/src/main/**` | 生产交付物仅 4 个文件；无 `docs` 手册、无配置元数据、无 `examples/` | 2026-10-22 |
| E-02 | SOURCE | `.../config/GXEurekaClientConfig.java:18-21` | `@AutoConfiguration(after = {GXFeignConfig, GXWebClientConfig, EurekaClientAutoConfiguration})` + `@ConditionalOnClass({FeignClient, WebClient, EurekaClientAutoConfiguration})`，类体为空；无 `@EnableFeignClients`、无 `@ConditionalOnProperty`、无 `@Bean` | 2026-10-22 |
| E-03 | SOURCE | `target/classes/META-INF/spring-autoconfigure-metadata.properties`（改前） | 仅 `AutoConfigureAfter`、`ConditionalOnClass` 两行 | 2026-10-22 |
| E-04 | SOURCE | `target/classes/application.yml`（改前） | 由 `src/main/resources/application.yml` 复制到 classes 根，随 jar 发布为 `classpath:/application.yml` | 2026-10-22 |
| E-05 | SOURCE | `src/main/resources/application.yml:11-15`（改前） | `register-with-eureka: true`；`defaultZone` 为 `http://eureka7001.com:7001/eureka/,...7002...,...7003...`；文件 15 处 CRLF | 2026-10-22 |
| E-06 | SOURCE | `src/main/resources/application.yml:1-9`（改前） | `spring.cloud.openfeign.circuitbreaker.enabled/group.enabled/alphanumeric-ids.enabled` = `true/false/true`；openfeign 5.0.3 元数据确认三键仍有效 | 2026-10-22 |
| E-07 | MEASURED | `mvn -o -pl leaf-base-eureka-client test` | 改前 `Tests run: 4, Failures: 1`，失败断言见 §1.2 | 2026-10-22 22:28 |
| E-08 | SOURCE | `.../config/GXEurekaClientConfigTest.java:20-91`（改前） | 3 个 `ApplicationContextRunner` 用例 + 1 个 YAML 文本断言；无 `@SpringBootTest`；两处 `new String(bytes)` 未指定字符集 | 2026-10-22 |
| E-09 | MEASURED | `mvn -o -pl leaf-base-eureka-client dependency:tree` | `spring-cloud-starter-netflix-eureka-client:5.0.2` → `spring-cloud-netflix-eureka-client:5.0.2`（`spring-boot-jackson2:4.1.1`、`httpclient5:5.6.4`）+ `eureka-client:2.0.6`（`xstream:1.4.21`、`netflix-eventbus:0.3.0`、`spectator-api:1.7.3`、`jettison:1.5.4`）；`webflux` 来自 `leaf-base-webclient`、`actuator` 来自 `leaf-base-framework`，**不**由本 starter 引入 | 2026-10-22 22:28 |
| E-10 | SOURCE | `F:\repository\com\netflix\eureka\eureka-client\2.0.6\eureka-client-2.0.6.pom` | `eureka-client` 声明 `org.apache.httpcomponents:httpclient:4.5.3:compile`；模块排除后按根 POM `httpclient.version=4.5.14` 重新声明 | 2026-10-22 |
| E-11 | SOURCE | `pom.xml:30-59`（改前） | `antlr-runtime`、`error_prone_annotations`、`httpclient` 三坐标先 `<exclusion>` 再同坐标重声明，无注释；`-Dverbose` 树中三者仅顶层出现（`error_prone_annotations` 另有 guava 路径 `omitted for duplicate`） | 2026-10-22 |
| E-12 | SOURCE | `leaf-framework/pom.xml:80-84` | 以 `${project.parent.version}` 无条件依赖本模块（无 `optional`、无 profile）；008 已把 `leaf-base-eureka-server` 移出门面 | 2026-10-22 |
| E-13 | SOURCE | `.../GXEurekaClientConfigTest.java:81-84`（改前） | 把 `register-with-eureka: true`、`alphanumeric-ids` 与 `doesNotContain("feign.hystrix.enabled")` 锁定为期望值 | 2026-10-22 |
| E-14 | SOURCE | `leaf-base-eureka-client/AGENTS.md:22-26,39-45,60`（改前） | §1 声称启用 `@EnableFeignClients`、装配 `@LoadBalanced` Bean；§3 列 `@EnableFeignClients` 扫描与 `feign.hystrix.enabled` 基线；§5 称"当前模块没有现成测试"；§1 后直接跳 §3（缺 §2） | 2026-10-22 |
| E-15 | SOURCE | `docs/`、`src/main/resources/META-INF/` | 18 个手册中无本模块手册；无 `additional-spring-configuration-metadata.json` | 2026-10-22 |
| E-16 | SOURCE | `README.md:70` | 描述为"Eureka Client 装配"，未说明"引入即注册"、无开关、默认地址为 `eureka700*.com` | 2026-10-22 |
| E-17 | SOURCE | `specs/008-eureka-server-module-audit/audit-report.md:208` | 已登记本模块"库内同名 `application.yml`"为跨模块遗留项，建议专项收敛 | 2026-10-22 |
| E-18 | SOURCE | `docs/leaf-base-eureka-server开发手册.md:70-83` | 集群示例 `defaultZone: ${EUREKA_PEER_URLS:http://localhost:7002/eureka/,http://localhost:7003/eureka/}`，并明确"`defaultZone` 无默认" | 2026-10-22 |
| E-19 | SOURCE | `.../config/package-info.java`（改前） | 仅 `@NullUnmarked`；test 侧 `config`、`testapp` 两包无 `package-info.java` | 2026-10-22 |
| E-20 | SOURCE | `pom.xml:14-18` | 重复声明 `maven.compiler.source/target=21` 与 `project.build.sourceEncoding`（根 POM 41-45 行已声明）；`leaf-base-feign`/`webclient`/`eureka-server`/`manticore` 同样重复 | 2026-10-22 |
| E-21 | MEASURED | 探针 `ThreadProbeTest`（已删除）→ `mvn ... -Dtest=ThreadProbeTest` | 开关 `false` 时上下文 **11 线程、Eureka 线程 0**；与开关默认时的 22 线程 / 4 Eureka 线程形成 A/B 基线 | 2026-10-22 22:45 |
| E-22 | MEASURED | 探针 `ProbeUnreachableRegistryTest`（已删除）→ `mvn ... -Dtest=Probe*` | `defaultZone=http://127.0.0.1:1/eureka/` 时上下文**正常启动**；`foReplicator` 线程 POST 失败、30s 重试；无 `Failed to start bean 'eurekaAutoServiceRegistration'` | 2026-10-22 22:16 |
| E-23 | MEASURED | 探针 `ProbeConfigPrecedenceTest`（已删除），连跑 2 次 | 宿主侧 `application.yml` 生效（`defaultZone=http://host-config-wins:9999/eureka/`、`register-with-eureka=false`），框架 jar 内同名文件被静默丢弃；`spring.cloud.openfeign.circuitbreaker.enabled` 读到 `null` | 2026-10-22 22:16 |
| E-24 | MEASURED | `Select-String config/dev/app-environment.yml` + `leaf-base-feign` 源码 | 三个熔断键已存在于 `config/{dev,test,pre,prod}/app-environment.yml`；`leaf-base-feign` 只提供 `GXCircuitBreakerConfig`（前缀 `maple.framework.feign.circuit-breaker`），模块内无 `application.yml` | 2026-10-22 22:20 |
| E-25 | MEASURED | 探针 `StartupCostProbeTest`（已删除）→ `mvn ... -Dtest=StartupCostProbeTest`，同 JVM 连续 3 次 | 启动耗时 7832 / 4813 / 4619 ms，热态偏差 `|4813-4619|/4619 = 4.2%`（≤30%）；常驻线程 17，其中 Eureka 3；`EurekaClient` Bean 2 个 | 2026-10-23 00:05 |
| E-26 | MEASURED | `[System.Net.Dns]::GetHostAddresses` + `TcpClient.ConnectAsync` + `Invoke-WebRequest http://<domain>/eureka/apps/` | 三域名均解析到 `198.18.0.81/82/83`（benchmark 网段，非真实注册中心）、TCP 7001/7002/7003 可建立；HTTP GET 全部失败（7001/7002「连接被意外关闭」、7003「操作超时」） | 2026-10-23 00:10 |
| E-27 | MEASURED | 探针 `CombinationProbeTest`（已删除）→ `mvn ... -Dtest=CombinationProbeTest*` | `spring.cloud.discovery.enabled=false`：`EurekaClient` Bean 0、无 `eurekaAutoServiceRegistration`、无请求；`spring.cloud.service-registry.auto-registration.enabled=false`：`EurekaClient` Bean 2、无 `eurekaAutoServiceRegistration`、仅有 `GET /eureka/apps/`（**无 POST 注册**） | 2026-10-23 00:00 |

## 3. 发现清单

| ID | 维度 | 级别 | 位置 | 触发条件 | 影响 | 判定 | 处置 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 装配边界 | 高 | `GXEurekaClientConfig.java:18-23`、`leaf-framework/pom.xml:80-84` | 引入本模块或聚合门面 | 引入门面的全部应用成为 Eureka 客户端并尝试注册，无法关闭 | 缺陷 | 已修复：新增开关 `maple.framework.enable.eureka-client` + `EnvironmentPostProcessor`（T009~T013） |
| F-02 | 默认配置语义 | 高 | 改前 `src/main/resources/application.yml`（jar 根，**已删除**；等价现位置 `target/classes/application.yml`） | 宿主自带 `application.yml` | 同名配置位置冲突：框架默认值被静默丢弃或覆盖宿主，均不报错（E-23 实测） | 缺陷 | 已修复：删除 jar 根文件，移入 `examples/`（T016） |
| F-03 | 默认配置语义 | 高 | 改前 `src/main/resources/application.yml:11-15`（**已删除**） | 默认启动 | 固化 `register-with-eureka: true` 与三个 `eureka700*.com`，与服务端手册口径冲突 | 缺陷 | 已修复：框架不再提供这两类默认值，仅保留缺失告警（T016/T017） |
| F-04 | 测试可信度 | 高 | `GXEurekaClientConfigTest.java:81-84` | Windows(CRLF) | 基线为红；断言依赖换行符与缩进；且把缺陷锁定为期望 | 缺陷 | 已修复：改写为语义断言（T005/T019） |
| F-05 | 装配边界 | 中 | `GXEurekaClientConfig.java` | 类路径缺任一依赖 | 空壳配置类的 `after`/`ConditionalOnClass` 对"jar 内默认值仍生效"无保护，误导读者以为存在降级 | 风险 | 已缓解：开关提供真实降级路径，条件元数据新增 `ConditionalOnProperty`（T012） |
| F-06 | 契约一致性 | 高 | `AGENTS.md:22-26,39-45,60` | 阅读文档 | 误以为模块会自动开启 Feign 扫描；`feign.hystrix.enabled` 与"无测试"表述失真 | 缺陷 | 已修复：重写 §1/§3/§5 并补 §2（T028） |
| F-07 | 测试可信度 | 中 | `GXEurekaClientConfigTest.java:20-91` | 任何改动 | 4 个用例均为在 `ApplicationContextRunner` 内运行，不加载配置、不启动上下文、不产生真实请求 | 缺陷 | 已修复：新增 3 个 `@SpringBootTest` 类 + HttpServer 夹具（T009/T019/T025） |
| F-08 | 依赖治理 | 中 | `pom.xml:30-59` | 阅读/升级依赖 | 三组 exclusion 与重声明净效果为零且无注释；`httpclient` 4.x 与 5.x 并存无说明 | 风险 | 已缓解：补中文注释，记录"删除后仍由 guava 提供"的实测结论（T021/T022） |
| F-09 | 职责边界 | 中 | 改前 `src/main/resources/application.yml:1-9`（**已删除**） | 引入本模块 | 全局打开 Feign 熔断，越出注册发现职责 | 缺陷 | 已修复：三个键移出本模块；真实提供路径为 `config/{env}/app-environment.yml`（T016、E-24） |
| F-10 | 契约一致性 | 中 | `docs/`、`META-INF/` | 接入方查阅 | 无模块手册、无配置元数据，开关不可发现 | 缺陷 | 已修复：新增手册与元数据（T013/T029） |
| F-11 | 测试可信度 | 低 | `GXEurekaClientConfigTest.java:26,80` | 跨平台 | 读取文件未指定字符集，是本次红基线的直接诱因之一 | 缺陷 | 已修复：统一 `StandardCharsets.UTF_8`（T005） |
| F-12 | 契约一致性 | 低 | test 侧包 | 触碰测试代码 | 测试包未声明包级空值策略 | 风险 | 已修复：补 3 个 `package-info.java`（T007） |
| F-13 | 依赖治理 | 低 | `pom.xml:14-18` | —— | 重复声明编译器属性；与仓库另外 4 个模块一致 | 惯例 | 仅登记，不单独立项（跨模块整批处理） |

已确认契约（不得在后续修改中破坏）：

- **P-01**：`@AutoConfiguration` + `AutoConfiguration.imports`（1 行）入口正确（E-02、E-03）。
- **P-02**：**不**自动开启 `@EnableFeignClients`（E-02、E-14），新增用例 `autoConfigurationDoesNotRegisterFeignClientsByItself` 守护。
- **P-03**：starter 无钉版本，由 BOM 管理（E-09）。
- **P-04**：三个熔断键在 OpenFeign 5.0.3 仍有效（E-06）。
- **P-05**：无 `@EnableEurekaServer`、无业务服务名/业务 Feign 接口（E-01、E-02）。
- **P-06**：本模块不定义 Bean，不抢占 `leaf-base-webclient`/`leaf-base-feign` 的能力（E-02）。

## 4. 装配矩阵（实测）

| # | 应用形态 | 开关 | webType | Eureka Bean | 是否发起注册 | 启动结果 | 线程 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 仅本模块（默认开关，未配置任何 `eureka.*`） | 未设置（`true`） | NONE | `CloudEurekaClient`、`eurekaAutoServiceRegistration` 存在 | 是 → 未配置时回落 `http://localhost:8761/eureka/`（启动日志 1 条 WARN 指明） | 成功 | 17 线程，其中 Eureka 3（`DiscoveryClient-%d`×2、`InstanceInfoReplicator`，E-25）；`@SpringBootTest` 形态因测试框架附加线程为 22/4（E-22） |
| 2 | 聚合门面 `leaf-framework` 消费者 | 未设置（`true`） | NONE | 同 #1（门面无条件依赖本模块，见 E-12） | 同 #1（**等价推导**：依赖来源不同、运行时无差异，未单独起应用） | 成功 | 同 #1（推导） |
| 3 | 开关关闭 | `false` | NONE | 无（`EurekaClient` 集合为空，`eurekaAutoServiceRegistration` 不存在） | 否 | 成功 | 11 线程，Eureka 0（实测，E-21） |
| 4 | 显式 `eureka.client.enabled=false` | 未设置（`true`） | NONE | 无 | 否 | 成功 | 0 Eureka 线程（与 #3 同机制，未单独采样） |

对应用例：`GXEurekaClientSwitchTest`（#1、#3、#4，3 个用例）、`GXEurekaClientDefaultZoneTest`（#1 的配置语义）。

### 4.1 "引入但不注册"组合矩阵（E-27 实测）

| 组合 | `EurekaClient` Bean | `eurekaAutoServiceRegistration` | 是否发生注册（POST） | `eureka.*` 残留 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `eureka.client.enabled=false` | 0 | 无 | 否 | 无（框架不写） | 最彻底：客户端整体不装配 |
| `spring.cloud.discovery.enabled=false` | 0 | 无 | 否 | 无 | 同样彻底；同时关闭 Spring Cloud 发现抽象（`DiscoveryClient` Bean 计数变为 2） |
| `spring.cloud.service-registry.auto-registration.enabled=false` | 2 | 无 | **否**（仅 `GET /eureka/apps/`，无 `POST`） | 无 | 客户端仍在（会继续拉取注册表），只是不自动注册；适合"只消费不注册"的服务 |

对应用例：`GXEurekaClientCombinationTest`（3 个嵌套用例，断言 Bean 与"无 POST"）。

## 5. 配置来源矩阵

改前（E-23 实测）：宿主 `application.yml` **胜出**，框架 jar 内同名文件被静默丢弃；仅在宿主没有该文件时框架值才生效。

| 配置键 | 改前框架默认 | 改后框架默认 | 宿主未配置时生效值 | 生效来源 | 稳定性 |
| --- | --- | --- | --- | --- | --- |
| `maple.framework.enable.eureka-client` | 不存在 | 不存在（`matchIfMissing=true`） | `true` | 注解默认 | 3 次一致 |
| `eureka.client.register-with-eureka` | `true`（jar yml） | **不再提供** | `Environment` 读到 `null`；`EurekaClientConfigBean` 默认 `true` | 上游默认 | 3 次一致 |
| `eureka.client.service-url.defaultZone` | `eureka7001/7002/7003` | **不再提供** | `Environment` 读到 `null`；客户端回落 `http://localhost:8761/eureka/`，并有 1 条 ASCII WARN | 上游默认 | 3 次一致 |
| `spring.cloud.openfeign.circuitbreaker.enabled` | `true`（jar yml，宿主有 yml 时不生效） | **移出本模块** | `null`（未开启） | 上游未设置；由 `config/{env}/app-environment.yml` 提供 `true` | 3 次一致 |
| `spring.cloud.openfeign.circuitbreaker.group.enabled` | `false` | 移出本模块 | `null` | 同上（模板提供 `false`） | 3 次一致 |
| `spring.cloud.openfeign.circuitbreaker.alphanumeric-ids.enabled` | `true` | 移出本模块 | `null` | 同上（模板提供 `true`） | 3 次一致 |
| `eureka.client.enabled` | 不涉及 | 仅开关为 `false` 时写 `false`（最低优先级） | 未写入 | 应用显式值优先 | 3 次一致 |

覆盖顺序（改后）：命令行 > 环境变量 > 宿主 `application-{profile}.yml` > 宿主 `application.yml` > Spring Cloud 默认；框架不再占用其中任何一层。

宿主配置文件优先已由用例固化：`GXEurekaClientHostConfigTest` 以 `spring.config.location=classpath:/host/` 加载 `src/test/resources/host/application.yml`，断言 `spring.application.name`、`register-with-eureka`、`defaultZone` 三值全部来自宿主文件，且 `spring.cloud.openfeign.circuitbreaker.enabled` 与开关本身在 `Environment` 中均不存在（框架未注入）。

### 5.1 默认域名可达性（改前行为，E-26 实测）

| 域名 | DNS 解析 | TCP 7001/7002/7003 | `GET /eureka/apps/` | 结论 |
| --- | --- | --- | --- | --- |
| `eureka7001.com` | 成功 → `198.18.0.81`（含 IPv6 `2001:2::53`） | 可建立连接 | 失败：连接被意外关闭 | 不是可用注册中心 |
| `eureka7002.com` | 成功 → `198.18.0.82` | 可建立连接 | 失败：连接被意外关闭 | 不是可用注册中心 |
| `eureka7003.com` | 成功 → `198.18.0.83` | 可建立连接 | 失败：操作超时 | 不是可用注册中心 |

定性：`198.18.0.0/15` 属基准测试保留网段，本机（含代理）会接受 TCP 连接但不提供 Eureka 协议响应。因此"改前默认值"在**任何**环境下都无法完成注册：有代理时表现为 HTTP 失败（本次实测），无代理/hosts 时表现为 `UnknownHostException`（E-23 已用 `host-config-wins` 复现该路径）。客户端行为固定为"启动成功 + 30s 周期重试 + `TransportException` 日志"（E-22），不会以启动失败暴露。

### 5.2 "仅使用 feign 的应用"行为不变（E-24 + 用例证据）

三个 `spring.cloud.openfeign.circuitbreaker.*` 键自始至终不由 `leaf-base-feign` 提供（该模块只提供 `GXCircuitBreakerConfig`，前缀 `maple.framework.feign.circuit-breaker`），也不在 `leaf-base-feign` 的 jar 内；其真实来源是 `config/{dev,test,pre,prod}/app-environment.yml`，且 `leaf-base-feign` 的用例（`GXFeignCircuitBreakerFallbackTest:29-32`）显式设置这三个键，证明测试与运行都不依赖外部模块提供默认值。本次移出仅影响"引入了 eureka-client 但既没用环境模板也没显式配置"的应用，属已声明的兼容性变更（§4 迁移表）。

产物层面已由 `GXEurekaClientContractTest` 锁定：`target/classes` 下不存在含 `spring.cloud.openfeign.circuitbreaker` 或 `eureka700` 的条目。

## 6. 依赖矩阵

| 坐标 | 解析版本 | 作用域 | 可传递 | 引入者 | 实际使用者 | 判定 | 动作 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `com.netflix.eureka:eureka-client` | 2.0.6 | compile | 是 | starter | 客户端核心 | 无风险 | 不动 |
| `org.springframework.cloud:spring-cloud-netflix-eureka-client` | 5.0.2 | compile | 是 | starter | 自动配置 | 无风险 | 不动 |
| `org.apache.httpcomponents:httpclient` | 4.5.14 | compile | 是（显式） | 模块声明（锚定） | `eureka-client` 2.0.6（声明 4.5.3） | 必要（版本锚定） | 保留 + 注释 |
| `org.apache.httpcomponents.client5:httpclient5` | 5.6.4 | compile | 是 | `spring-cloud-netflix-eureka-client` | 新版客户端传输 | 无风险（并存） | 不动 + 注释 |
| `org.antlr:antlr-runtime` | 3.5.3 | compile | 是（显式） | 模块声明（锚定） | seata/eureka 冲突锚点 | 冗余但必要 | 保留 + 注释 |
| `com.google.errorprone:error_prone_annotations` | 2.50.0 | compile | 是 | 模块声明 + guava 传递 | 注解依赖 | 冗余（净效果为零） | 保留 + 注释（见下） |
| `org.springframework.boot:spring-boot-jackson2` | 4.1.1 | compile | 是 | `spring-cloud-netflix-eureka-client` | Eureka JSON 序列化（Jackson 2） | 无风险（与框架 Jackson 3 并存） | 不动 |
| `com.thoughtworks.xstream:xstream` | 1.4.21 | compile | 是 | `eureka-client` | XML 配置解析 | 无风险（上游强制） | 不动，登记 |
| `com.netflix.netflix-commons:netflix-eventbus` | 0.3.0 | runtime | 是 | `eureka-client` | 事件总线 | 无风险 | 不动 |

`error_prone_annotations` 实测（T022）：删除模块内重声明后，`dependency:tree` 仍出现 `com.google.errorprone:error_prone_annotations:jar:2.50.0`（来源由 `eureka-client` 迁移到 `guava` 传递），diff 仅 1 行变化（provider 位置）。按 [dependency-contract.md](./contracts/dependency-contract.md) §3 "仅当 diff 完全一致时删除"，**保留重声明并加注释**：显式锚定比"偶然传递"稳定。

## 7. 回归设计（RC）

| ID | 关联发现 | 层级 | 用例 | 断言（语义） | 改前 | 改后 |
| --- | --- | --- | --- | --- | --- | --- |
| RC-01 | F-02 | 上下文 | `GXEurekaClientConfigTest#applicationYamlIsAbsentFromClasspathRoot` | `classpath:/application.yml` 为 `null` | 红（资源存在；原 CRLF 文本断言失败） | 绿 |
| RC-02 | F-01 | 集成（`@SpringBootTest`） | `GXEurekaClientSwitchTest$SwitchOff#switchOffDisablesEurekaClient` | 无 `EurekaClient`/`eurekaAutoServiceRegistration`；`eureka.client.enabled=false`；日志含禁用语句 | 红（开关不存在，客户端仍装配） | 绿 |
| RC-03 | F-01 | 集成 | `GXEurekaClientSwitchTest$ExplicitClientDisabled#explicitClientDisabledWinsOverMissingSwitch` | 应用显式 `false` 时无客户端 Bean，值为 `false` | 绿（上游行为，守护契约） | 绿 |
| RC-04 | F-01 | 集成 | `GXEurekaClientSwitchTest#switchMissingKeepsUpstreamBehaviour` | 默认开关下 `eureka.client.enabled` 为 `null`（框架不写入）、客户端存在 | 绿（守护契约） | 绿 |
| RC-05 | F-03 / F-09 | 集成 | `GXEurekaClientDefaultZoneTest#frameworkProvidesNoRegistrationDefaults` | `register-with-eureka`、`defaultZone`、`circuitbreaker.enabled` 在 `Environment` 中均为 `null` | 红（jar yml 提供三值） | 绿 |
| RC-06 | F-03 | 集成 | `GXEurekaClientDefaultZoneTest#warnsWhenDefaultZoneIsMissing` | 输出含 `Eureka client default zone is not configured` | 红（无告警） | 绿 |
| RC-07 | F-03 | 单元 | `GXEurekaClientDefaultZoneTest#defaultZoneDetectionFollowsConfiguration` | 检测逻辑对空/已配置返回 `false`/`true` | 红（方法不存在） | 绿 |
| RC-08 | F-07 | 集成 | `GXEurekaClientRegistrationTest#registrationIsSentToConfiguredDefaultZone` | 本地 HttpServer 收到 `POST /eureka/apps/IT-APP`，且环境值与配置一致 | 红（无此类用例/无法验证） | 绿 |
| RC-09 | F-02 | 单元 | `GXEurekaClientConfigTest#exampleConfigsAreShippedOutsideTheConfigRoot` | `examples/*.yml` 可读、`application.yml` 为 `null` | 红（无 examples） | 绿 |

### 7.1 平台一致性

| 平台 | 用例数 | 失败 | 时间 |
| --- | --- | --- | --- |
| Windows（CRLF 工作区） | 12 | 0 | 2026-10-22 22:47 ~ 22:52（连续 3 次，Phase 9 基线） |
| LF 归一化（全部模块源文件转 LF 后） | 12 | 0 | 2026-10-22 22:53（Phase 9 基线） |
| Windows（CRLF，Phase 11 后全量） | **23** | 0 | 2026-10-23 00:40 |
| LF 归一化（Phase 11 后全量，含新增 11 个用例） | **23** | 0 | 2026-10-23 00:42 |

结论（Phase 11 复验）：两侧用例数与通过数完全一致（23/0/0），新增用例同样不含换行符断言；全部文件已恢复 CRLF（孤立 LF 计数 0）。

结论：用例数与通过数完全一致；测试中不存在换行符/缩进断言（所有断言基于 `Environment`、Bean 集合与真实 HTTP 请求）。

### 7.2 一次性说明（实施期偏差）

1. `containsOnlyOnce` 改为 `contains`：同一 JVM 内多个 Spring 上下文共享日志 appender，跨上下文的输出会串到当前 `CapturedOutput`；"每次上下文 1 条"由"构造函数唯一调用点"保证，并由 `warnsWhenDefaultZoneIsMissing` 覆盖存在性。
2. 环境后处理器的日志必须使用 `DeferredLogFactory`：`EnvironmentPostProcessor` 早于日志系统初始化，静态 `LoggerFactory` 的输出会丢失（初版实测被丢弃，已修正）。

### 7.3 全量发现 × 复现方式 × 回归设计（覆盖 F-01 ~ F-13）

| 发现 | 复现方式（可执行） | 回归 / 验证载体 | 旧 → 新 |
| --- | --- | --- | --- |
| F-01 | 开关 `false` 启动：`mvn -o -pl leaf-base-eureka-client test -Dtest=GXEurekaClientSwitchTest*` | RC-02、RC-03、RC-04；`GXEurekaClientCombinationTest` | 无开关、始终注册 → 可关闭且无残留 |
| F-02 | 类路径同名配置：探针 E-23（宿主 yml 胜出、框架值被丢弃） | RC-01、RC-09；`GXEurekaClientHostConfigTest` | 红（`application.yml` 存在）→ 绿（不存在） |
| F-03 | 默认域名探活：E-26（DNS/TCP/HTTP 三项） | RC-05、RC-06、RC-07 | 框架默认 `eureka700*.com` → 无默认值 + 缺失 WARN |
| F-04 | `mvn -o -pl leaf-base-eureka-client test`（改前 §1.2 红基线） | RC-01（断言替换）；§7.1 双平台 | `\n` 文本断言失败 → 语义断言双平台通过 |
| F-05 | `Get-Content target/classes/META-INF/spring-autoconfigure-metadata.properties`（仅 `AutoConfigureAfter`、`ConditionalOnClass` 两键）+ 反射读取 `@ConditionalOnProperty` | `GXEurekaClientContractTest#autoconfigureMetadataKeepsDocumentedKeys`、`#switchConditionIsDeclaredOnTheConfigurationClass` | 仅 `ConditionalOnClass`（无开关）→ 新增 `@ConditionalOnProperty`；**该条件不进入 autoconfigure metadata**（Spring Boot 平台限制，见 assembly-contract §4 修正） |
| F-06 | 比对 `AGENTS.md` 与实现（§8 表 6 条） | `GXEurekaClientContractTest#documentationDescribesTheSwitch` | 文档与实现相反 → 一致 |
| F-07 | `mvn ... -Dtest=GXEurekaClientSwitchTest*,GXEurekaClientRegistrationTest` | RC-02、RC-05、RC-08 | 仅 `ApplicationContextRunner` → 真实上下文 + 真实 HTTP |
| F-08 | 依赖树 diff（§6 末段，两棵树 198 行一致） | `dependency-contract.md` §4 验证流程 | 无注释的排除/重声明 → 有注释且树零变化 |
| F-09 | `GXEurekaClientContractTest#producedClassesContainNoRegistrationDefaultsOrFeignKeys` | 同左 + E-24 | jar 内含熔断键 → 产物内 0 命中 |
| F-10 | `GXEurekaClientContractTest#metadataDeclaresOnlyTheModuleSwitchWithDefaultTrue`；`Test-Path docs/leaf-base-eureka-client开发手册.md` | 同左 | 无元数据/无手册 → 均存在 |
| F-11 | 读文件统一 `StandardCharsets.UTF_8`：RC-01 用例代码 | RC-01 | 未指定字符集 → 显式 UTF-8 |
| F-12 | `Get-ChildItem leaf-base-eureka-client/src/test/java/cn/maple/eureka/client -Recurse -Filter package-info.java` | 结构检查（`testapp`、`support` 各 1；`config` 由 main 侧覆盖，见 §12.1） | test 侧无声明 → 已补 |
| F-13 | `Select-String leaf-base-eureka-client/pom.xml -Pattern "maven.compiler.source"` | 仅登记（跨模块整批处理） | 保持惯例，不改 |

## 8. 契约核对

| 文档 | 位置 | 原文（摘要） | 实现事实 | 判定 | 动作 |
| --- | --- | --- | --- | --- | --- |
| `AGENTS.md` | §1 第 22 行 | "通过 `GXEurekaClientConfig` 启用 `@EnableFeignClients`" | 类中无该注解，且**不得**新增（P-02） | 不一致 | 已改写 |
| `AGENTS.md` | §1 第 23 行 | "通过 `leaf-base-webclient` 自动装配 `@LoadBalanced` WebClient.Builder 与 `HttpServiceProxyFactory`" | 实际由 `leaf-base-webclient` 的 `GXWebClientConfig` 提供，本模块只保证排序 | 不一致 | 已改写 |
| `AGENTS.md` | §3 第 40 行 | "`@EnableFeignClients` 的扫描范围变化会影响…" | 本模块不涉及扫描 | 不一致 | 已删除该基线 |
| `AGENTS.md` | §3 第 44 行 | "`feign.hystrix.enabled` 属于历史兼容配置" | 配置中已无该键，测试亦断言不存在 | 不一致 | 已删除并新增"不得复活"红线 |
| `AGENTS.md` | §5 第 60 行 | "当前模块没有现成测试" | 实施后 23 个用例 | 不一致 | 已更新 |
| `AGENTS.md` | 章节编号 | `## 1.` 后直接 `## 3.` | 缺第 2 节 | 不一致 | 已补 `## 2. 装配开关与装配契约` |
| `README.md` | 第 70 行 | "Eureka Client 装配" | 未说明"引入即注册""可关闭""无默认地址" | 不一致 | 已更新 |
| `docs/` | 目录 | 无本模块手册 | 存在 18 个其它手册 | 不一致 | 已新增 `docs/leaf-base-eureka-client开发手册.md` |
| `additional-spring-configuration-metadata.json` | —— | 不存在 | 新增开关需可发现 | 不一致 | 已新增（仅声明本模块开关） |

## 9. 风险排序

排序依据：影响面 × 触发概率 × 可观测性（越"静默"越靠前）。

| 排序 | 发现 | 影响面 | 触发概率 | 可观测性 | 结论 |
| --- | --- | --- | --- | --- | --- |
| 1 | F-01 引入即注册、无开关 | 引入聚合门面的**全部应用** | 100%（引用即触发） | 低（注册成功时无日志） | 已修复，最高优先 |
| 2 | F-02 同名配置位置冲突 | 任何自带 `application.yml` 的应用 | 高 | 极低（两种结果都不报错） | 已修复（移出 jar 根） |
| 3 | F-03 固定示例域名 | 依赖框架默认值注册的应用 | 中（宿主有 yml 时不生效） | 低（域名不可解析只在 WARN 级出现） | 已修复（移除默认值 + 告警） |
| 4 | F-04 基线为红 + 平台相关断言 | 全部提交 | 100%（Windows） | 高（构建失败） | 已修复（语义断言 + 双平台） |
| 5 | F-06 契约文档与实现相反 | 全部接入方 | 高（按文档误解 Feign 扫描） | 低 | 已修复 |
| 6 | F-09 Feign 键越界 | 只用本模块且未用环境模板的应用 | 低（多数场景被宿主 yml 覆盖） | 低 | 已修复（移出） |
| 7 | F-07 测试不覆盖真实行为 | 后续所有改动 | 100% | 中 | 已修复（3 个集成用例） |
| 8 | F-08 / F-10 / F-11 / F-12 / F-13 | 维护者 | —— | 高 | 已缓解/已登记 |

## 10. 受影响应用清单与升级必查

仓库内结论：本仓库为框架聚合仓库，**无下游消费工程**；受影响面按依赖传递计算。

判定命令（在目标应用根目录执行）：

```powershell
mvn -o dependency:tree | Select-String "leaf-base-eureka-client|leaf-framework"
```

| 应用类型 | 判定 | 升级后必须做的事 |
| --- | --- | --- |
| 依赖聚合门面 `leaf-framework` | 依赖树出现 `leaf-framework` 即间接依赖本模块 | ①检查是否真的需要注册发现：不需要则设 `maple.framework.enable.eureka-client=false`；②需要则从 `examples/eureka-client-default.yml` 复制配置并填真实地址 |
| 直接依赖 `leaf-base-eureka-client` | 依赖树直接出现本模块 | 同上；并核对是否依赖过旧的 `eureka700*.com` 默认值 |
| 只需要"注册自己"、原本借用服务端模块 | 依赖树含 `leaf-base-eureka-server` 且开关为 `false` | 保持服务端开关关闭，改用本模块 + `examples/` 配置 |

升级必查配置键：

1. `maple.framework.enable.eureka-client`（默认 `true`，需要关闭时显式设 `false`）
2. `eureka.client.service-url.defaultZone`（框架不再提供，必须由应用提供）
3. `eureka.client.register-with-eureka`（框架不再提供，Spring Cloud 默认 `true`）
4. `spring.cloud.openfeign.circuitbreaker.enabled/group.enabled/alphanumeric-ids.enabled`（已移出本模块，见 `config/{env}/app-environment.yml`）

与 [`docs/leaf-base-eureka-client开发手册.md`](../../docs/leaf-base-eureka-client开发手册.md) §7 互为引用，两处口径一致。

## 11. 跨模块遗留

1. `specs/008-eureka-server-module-audit/audit-report.md:208` 登记的"本模块库内同名 `application.yml`"**已收敛**：文件已移出 jar 根，默认值改由 `examples/` 与文档交付（本报告 §5、§7 RC-01/RC-09）。
2. 默认值口径对齐：服务端手册集群示例使用 `${EUREKA_PEER_URLS:http://localhost:7002/eureka/,http://localhost:7003/eureka/}`（自身为 7001），客户端多注册中心示例使用 `${EUREKA_REGISTRY_URLS:...7001...,...7002...,...7003...}`（节点集合含自身），语义一致；客户端单注册中心示例回落 `localhost:8761`，与服务端"必须显式指定端口"的约束一致（框架不再替应用决定拓扑）。
3. 未在本特性范围的遗留：`agent` 侧"是否把本模块移出聚合门面"仍是独立决策（`spec.md` Clarifications），本报告只提供影响面数据（E-12、§10）。
4. 跨模块改动范围：本特性只新增 `docs/leaf-base-eureka-client开发手册.md` 与修改 `README.md` 一行；`leaf-framework/pom.xml`、`leaf-base-feign`、`leaf-base-webclient`、根 `pom.xml` 均未改动（T039 校验）。

## 12. 实施结果与验证

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 模块测试（CRLF） | `mvn -o -pl leaf-base-eureka-client test` | `Tests run: 23, Failures: 0, Errors: 0`（Phase 11 复测） |
| 模块测试（LF） | 源文件转 LF 后同上 | `Tests run: 23, Failures: 0`（Phase 11 复验，结论见 §7.1） |
| 构建门禁 | `mvn -o -pl leaf-base-eureka-client clean install -DskipTests` | `BUILD SUCCESS`（ErrorProne + NullAway 无诊断） |
| 跨模块回归 | `mvn -o -pl leaf-base-eureka-server test` | `Tests run: 23, Failures: 0`，`BUILD SUCCESS` |
| 依赖形态 | 用 `git show HEAD:leaf-base-eureka-client/pom.xml` 重建改前树，与改后树逐行 diff | 两棵树均 198 行、**完全一致**（本特性不改变任何解析坐标） |
| 产物校验 | `target/classes` 检索 | `application.yml` 不存在；`eureka700` 0 命中；`circuitbreaker` 0 命中；`examples/` 2 个文件 |
| 变更边界 | `git status --porcelain` | 仅本模块改动 + `docs/leaf-base-eureka-client开发手册.md` + `README.md` + `specs/009-eureka-client-module-audit/`；无 `leaf-framework`/`leaf-base-feign`/`leaf-base-webclient`/根 `pom.xml` |

任务完成度：**100%** —— [tasks.md](./tasks.md) 中全部任务均已勾选（数量与编号以 `tasks.md` 为准，本行不再维护计数）。

> 计数式表述（如"56/56"）会在后续 Convergence 追加任务后失效，故改用自校验口径：判定标准是 `tasks.md` 内不存在未勾选的 `- [ ] T###` 条目。

### 12.1 实施期偏差与调整（与 tasks.md 的差异）

| 项 | 计划 | 实际 | 原因 |
| --- | --- | --- | --- |
| T007 包级空值策略 | 补 3 个 `package-info.java`（`config`/`testapp`/`support`） | 补 **2** 个（`testapp`/`support`） | `cn.maple.eureka.client.config` 在 main 侧已有 `package-info`，test 侧再加会产生重复 FQN（IDE 报 `type package-info is already defined`），主侧声明已覆盖该包 |
| 降级日志实现 | 静态 `LoggerFactory` + `LOGGER.info` | 构造注入 `DeferredLogFactory` | `EnvironmentPostProcessor` 早于日志系统初始化，静态日志的 INFO 被丢弃（实测捕获不到），DeferredLog 在日志就绪后回放 |
| `defaultZone` 告警断言 | `containsOnlyOnce` | `contains`（存在性）+ 唯一调用点保证"每次上下文 1 条" | 同一 JVM 内多个上下文共享日志 appender，跨上下文输出会串入当前 `CapturedOutput` |
| T022 删除重复重声明 | 零差异则删除 | **保留**并加注释 | 删除后 `dependency:tree` 的 provider 行发生变化（来源由显式声明变为 guava 传递），按 [dependency-contract.md](./contracts/dependency-contract.md) §3 判定为"非零差异" |
| T017 检测逻辑 | 内联判断 | 抽出包级方法 `isDefaultZoneConfigured(Environment)` | 使"未配置/已配置"两态可被确定性单测覆盖，不依赖全局日志捕获 |
| 装配矩阵 #2 / #4 线程 | 逐形态采样 | #2 由依赖事实等价推导；#4 由"未装配客户端"推导 | 门面消费者需起的应用与本模块形态一致（E-12）；#4 与 #3 同机制（`eureka.client.enabled=false`），未单独采样 |
| 告警触发条件（Phase 10 / T044） | 开关 `true` 即告警 | 追加"客户端未被显式关闭"前置条件 | 实测发现 `eureka.client.enabled=false` 时仍输出"registration will target the Spring Cloud default"，与 assembly-contract §1 第 2 行冲突（假信号），改为 `shouldWarnAboutDefaultZone` 统一判定 |
| FR-011 措辞（Phase 10 / T043） | 规格写"本地模拟端点收到 PUT" | 实际为 `POST /eureka/apps/{APP}` | Eureka 注册协议使用 POST；断言以真实方法为准，差异已在 §7.3 记录 |
| 条件元数据断言（Phase 11 / T053） | 曾要求 `spring-autoconfigure-metadata.properties` 出现 `ConditionalOnProperty=` | 该文件实际只有 `AutoConfigureAfter`、`ConditionalOnClass` 两键；契约已改为"注解 + `additional-spring-configuration-metadata.json` + 行为用例"三载体，并由 `GXEurekaClientContractTest` 断言 | Spring Boot 的 autoconfigure metadata 不记录 `@ConditionalOnProperty`，原要求属对框架能力的误判（代码本身无需改动） |
| 用例计数（Phase 11 / T054） | 目标值 21 | 实际 23 | T053 在同一测试类（`GXEurekaClientContractTest`）新增 2 条断言，故 §1.4/§8/§12 按 23 回填 |

### 12.2 资源与启动成本（E-25 实测）

| 口径 | 数值 |
| --- | --- |
| 启动耗时（同 JVM 连续 3 次，`WebEnvironment.NONE`，注册+拉取开启） | 7832 ms / 4813 ms / 4619 ms |
| 热态重跑偏差 | `|4813 − 4619| / 4619 = 4.2%`（门槛 ≤ 30%） |
| 常驻线程（默认开关） | 17（其中 Eureka 3：`DiscoveryClient-%d`×2 + `InstanceInfoReplicator`） |
| 常驻线程（开关关闭，E-21） | 11（其中 Eureka 0） |
| 首次运行说明 | 第一次含类加载与 JIT 预热，作为"冷启动上界"单列，不参与偏差判定 |

## 13. 边界场景处置

| 场景（spec Edge Cases） | 处置 | 依据 |
| --- | --- | --- |
| 宿主有/无 `application.yml`；`spring.config.location` 覆盖 | 已实测 | E-23（改前）、`GXEurekaClientHostConfigTest`（改后） |
| 三个默认域名未解析 / 部分不可达 / 全部不可达 | 已实测（本机被代理解析到 `198.18.0.x`，HTTP 全部失败） | E-26、§5.1 |
| 默认值指向的注册中心在真实环境不可解析 | 已实测路径 | E-23（`host-config-wins` → `UnknownHostException`） |
| 注册中心不可达时的启动结果 | 已实测 | E-22（启动成功、30s 重试、`TransportException` 日志） |
| `eureka.client.enabled=false`、`spring.cloud.discovery.enabled=false`、`spring.cloud.service-registry.auto-registration.enabled=false` 及其组合 | 已实测（三种组合各自定性） | E-27、§4.1 |
| 关闭本模块开关但宿主显式 `eureka.client.enabled=true` | 已由契约定义并由实现保证"应用值优先" | assembly-contract §1 第 5 行；`GXEurekaClientSwitchTest$SwitchOff` |
| WebFlux / Servlet 两种 Web 类型 | **不在本次范围**（本模块不提供容器；`webEnvironment=NONE` 已覆盖装配语义，REACTIVE 形态的注册线程行为需要在宿主工程验证） | plan D-05；spec Assumptions |
| 注册中心在线但返回 403/404、TLS 与 basic auth | **不在本次范围**（属注册中心鉴权/传输配置，非本模块装配职责） | spec Assumptions（不引入外部注册中心） |
| `spring-cloud-loadbalancer` 无实例时的调用结果 | **不在本次范围**（由 `leaf-base-webclient` 决定，本模块不定义调用契约） | AGENTS.md §1「不负责」第 3 条 |
| 历史 `feign.hystrix.enabled` | 已处理（不再提供，且测试断言不存在） | E-05、`AGENTS.md` §3 第 7 条 |
| 注册中心在线但只部分节点不可达 | **不在本次范围**（需真实集群；`examples/eureka-client-multi-registry.yml` 给出多地址样例与说明） | 手册 §8 |
