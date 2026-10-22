# 审查报告：leaf-base-eureka-server

**Feature**: `008-eureka-server-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本报告是 `spec.md` 的执行证据载体。`spec.md` 只保留摘要与本报告的引用；两者编号、级别、维度必须一致（FR-012）。

## 1. 基线与范围

### 1.1 测试基线

| 时点 | 用例数 | 结果 |
| --- | --- | --- |
| 变更前（E-16） | 2 | `Tests run: 2, Failures: 0, Errors: 0`，`BUILD SUCCESS`（6.3s） |
| 变更后（第二轮收敛） | 19 | `Tests run: 19, Failures: 0, Errors: 0`，`BUILD SUCCESS` |
| 变更后（第三轮收敛，T060 新增 FR-019 回归用例） | 20 | `Tests run: 20, Failures: 0, Errors: 0`，`BUILD SUCCESS` |
| 变更后（第四轮收敛，T068/T069 重命名 + 单机默认回归用例） | 23 | `Tests run: 23, Failures: 0, Errors: 0`，`BUILD SUCCESS` |

### 1.2 依赖基线

- 快照：变更前后各执行 `mvn -pl leaf-base-eureka-server dependency:tree` 并逐坐标比对，差异已摘入 §6，临时快照文件已清理
- 唯一实质差异：`spring-boot-devtools:4.1.1` 消失；其余坐标版本不变（详见 §6）

### 1.3 审查范围（逐文件判定）

| # | 交付物 | 判定 |
| --- | --- | --- |
| 1 | `src/main/java/.../GXEurekaServerConfig.java` | 缺陷（F-01、F-12）→ 已修复（开关 + Web 类型条件 + 启用日志） |
| 2 | `src/main/java/.../package-info.java` | 无缺陷（`@NullUnmarked`，无公共可空 API） |
| 3 | `src/main/resources/application.yml` | 缺陷（F-02、F-03、F-05、F-09）→ 已删除，改为 `examples/` 两个示例 |
| 4 | `src/main/resources/META-INF/spring/...AutoConfiguration.imports` | 无缺陷（P-01），内容不变 |
| 5 | `src/test/java/.../GXEurekaServerConfigTest.java` | 缺陷（F-10、F-13、F-14）→ 已重写为 6 个条件装配用例 |
| 6 | `pom.xml` | 缺陷（F-04、F-06、F-07、F-08）→ 已收敛（详见 §6） |
| 7 | `AGENTS.md` | 缺陷（F-11）→ 已修正（补第 2 节、测试现状、开关基线、连接器约束） |
| 8 | `src/main/java/.../GXEurekaServerClientDefaultsPostProcessor.java`（修复期新增） | 无缺陷，承担两项职责：(a) 应用不是 Servlet 注册中心且未显式配置 `eureka.client.enabled` 时，以最低优先级默认禁用客户端；(b) 应用是 Servlet 注册中心时，以最低优先级补 `eureka.client.register-with-eureka=false` / `fetch-registry=false`（显式配置优先）。`GXEurekaServerClientDefaultsPostProcessorTest` 现有 6 个用例覆盖上述组合，另由 `GXEurekaServerSwitchSpringBootTest#switchOnlySingleMachineDefaultsToNoRegistration` 在真实上下文中锁定 |
| 9 | `src/main/resources/META-INF/spring.factories`（修复期新增） | 无缺陷：仅注册上述 `EnvironmentPostProcessor`（Boot 4 新接口），无其它扩展点 |
| 10 | `src/main/resources/META-INF/additional-spring-configuration-metadata.json`（修复期新增） | 无缺陷：`defaultValue=false` 与 `matchIfMissing=false` 一致 |
| 11 | `src/main/resources/examples/eureka-server-{standalone,peer}.yml`（修复期新增） | 无缺陷：不在 `classpath:/` 根、无固定地址/域名/端口，`peer` 全部为占位符 |

### 1.4 不得破坏的不变量（P-01 ~ P-06）

- **P-01** `AutoConfiguration.imports` 仍只含 `GXEurekaServerConfig`，元数据文件正常生成。
- **P-02** 仍使用 `@AutoConfiguration`，类体未聚合业务 Bean（仅新增一条启用日志）。
- **P-03** 模块仍不含 Feign / WebClient / `@LoadBalanced` / 业务 Bean。
- **P-04** 单机默认 `register-with-eureka=false` / `fetch-registry=false` 保持不变：由框架以最低优先级默认提供（`GXEurekaServerClientDefaultsPostProcessor`），`examples/` 示例与 `AGENTS.md` 基线同步给出同值。
- **P-05** 根 POM 的冲突覆盖（`hk2`、`woodstox`、`httpclient`、`jersey-hk2`、`jersey-apache-connector`）全部保留。
- **P-06** 既有 2 个用例的断言意图被保留（imports 包含、`@AutoConfiguration` + `@EnableEurekaServer`），并扩展为 6 个。

## 2. 证据索引

| 编号 | 位置 | 内容 |
| --- | --- | --- |
| E-01 | 模块目录树 | 生产交付物 4 个文件；变更前测试 1 类 2 用例；无 docs 手册、无配置元数据 |
| E-02 | `GXEurekaServerConfig.java:12-16`（变更前） | 仅 `@AutoConfiguration`、`@ConditionalOnClass`、`@EnableEurekaServer`；无开关、无 Web 类型条件 |
| E-03 | `target/classes/META-INF/spring-autoconfigure-metadata.properties` | 仅类名与 `ConditionalOnClass` 两行 |
| E-04 | `leaf-framework/pom.xml:80-89`（变更前） | 无条件依赖本模块，同时依赖 `leaf-base-webclient` |
| E-05 | `dependency:tree` | starter 传递 `spring-boot-starter-web`、`spring-boot-starter-tomcat`（`tomcat-embed-core:11.0.24`）、`spring-boot-starter-actuator` |
| E-06 | `pom.xml:64-67`（变更前）+ 依赖树 | `spring-boot-devtools:4.1.1:compile`，无 scope、无 optional |
| E-07 | 依赖树 | `jersey-apache-connector:3.1.12` 与 `jersey-server/jersey-container-servlet/jersey-client:4.0.2`、`jersey-hk2:4.0.3` 同栈；`spring-bridge:3.1.1` 与 `hk2 4.0.3` 跨代际 |
| E-08 | 依赖树 | `httpclient:4.5.14` 与 `httpclient5:5.6.4` 并存 |
| E-09 | `pom.xml:25-63`（变更前） | 5 组 `<exclusion>` 后按相同坐标重新声明 4 个 |
| E-10 | 根 `pom.xml:85` + BOM `2025.1.3` 第 99 行 | 模块 `eureka.server.version=5.0.2` 与 BOM 的 `spring-cloud-netflix.version=5.0.2` 同值 |
| E-11 | `application.yml:1-11`（变更前） | `prefer-ip-address: true`、`ip-address: 127.0.0.1`、`hostname: eureka7001.com`、`register/fetch=false`、`defaultZone` 指向 7002/7003；无 `server.port` |
| E-12 | 构建日志 + `target/classes/application.yml`（变更前） | `application.yml` 位于资源根，随 jar 发布到 `classpath:/` |
| E-13 | `GXEurekaServerConfigTest.java:12-28`（变更前） | 仅 imports 字符串包含与注解存在性 |
| E-14 | `AGENTS.md:18/34/58`（变更前） | 章节从 `## 1.` 跳到 `## 3.`；称"当前模块没有现成测试" |
| E-15 | `docs/` + `src/main/resources/META-INF`（变更前） | 17 个手册无本模块手册；无配置元数据 |
| E-16 | 变更前执行 | `Tests run: 2, Failures: 0`，`BUILD SUCCESS` |
| E-17 | `package-info.java:1`；测试包目录 | 主包 `@NullUnmarked`；测试包无 `package-info.java` |
| E-18 | 实施期实测 | 替换为 `jersey-apache5-connector:4.0.2` 后双节点用例失败：`Cannot create Jersey3 client` → `NoClassDefFoundError: org/glassfish/jersey/apache/connector/ApacheConnectorProvider` |
| E-19 | 实施期实测 | 删除 jar 内 `application.yml` 后，仅引入本模块的应用启动失败：`Failed to start bean 'eurekaAutoServiceRegistration'` |
| E-20 | 实施期实测（仓库外 WebFlux 工程） | 开关 `=true` 且应用强制 reactive 时，首次实现仍启动失败（`PING=0 / EXITED=true`，`Failed to start bean 'eurekaAutoServiceRegistration'`）；修正默认策略后复测 `PING=200`、`/eureka/apps=404` |

## 3. 未验证假设台账（结项）

| 假设 | 结论 |
| --- | --- |
| U-01 `classpath:/application.yml` 与宿主同名配置的覆盖方向 | **已定性**：框架侧同名配置源已移除（`classpathRootHasNoApplicationYml` 断言 `classpath:/application.yml == null`）。双 JVM 实验（节点工作目录放置宿主 `application.yml`：`server.port=18795`、`maple.framework.enable.eureka-server=true`）启动后 `/eureka/apps` 返回 200 → 宿主配置直接生效，框架不再注入或覆盖任何 `eureka.*` 默认值。 |
| U-02 示例配置（`false/false` + `defaultZone`）下对等复制是否发生 | **已定性（双 JVM 实测）**：两个独立 JVM 节点（B=18771，A=18772，均为 `register-with-eureka=true` + `fetch-registry=true`，A 的 `defaultZone` 指向 B），向 A `POST /eureka/apps/GX-LAB-APP` 返回 **204**，B 在 **1 秒** 内可查询到该实例 → `replicated=true`。单 JVM 双上下文观察不到复制，原因是 Eureka 的 `EurekaServerContextHolder` 静态上下文被后启动的上下文覆盖，属**测试设计限制，不是产品缺陷**。`false/false` 形态下不注册也不拉取（`registryIsNotFetchedWhenFetchRegistryIsDisabled` 通过），因此该形态只能单机使用。 |
| U-03 `jersey-apache5-connector` 下对等通信是否正常 | **已定性：不可行**（E-18），改为保留 Jersey 3 连接器。 |
| U-04 删除重复声明后解析结果是否逐坐标一致 | **已定性：一致**，仅 devtools 消失（§6）。 |

## 4. 启动语义矩阵

| 配置键 | 形态 | 应取值 | 实际取值 | 结论 |
| --- | --- | --- | --- | --- |
| `register-with-eureka` | 单机 | `false` | `false`（`GXEurekaServerClientDefaultsPostProcessor` 最低优先级默认；示例文件同值） | 一致 |
| `register-with-eureka` | 集群 | `true` | 示例给 `true`；显式配置优先；旧默认 `false` 时节点不向对端注册 | 旧默认不满足集群，示例已修正 |
| `fetch-registry` | 单机 | `false` | `false`（同上，最低优先级默认） | 一致 |
| `fetch-registry` | 集群 | `true` | 示例给 `true`；实测 `false` 时节点不拉取对端注册表 | 旧默认不满足集群，实测锁定 |
| `service-url.defaultZone` | 单机 | 不设置 | 旧值指向 7002/7003（需 hosts） | 矛盾，已移出默认 |
| `service-url.defaultZone` | 集群 | 其余节点地址 | 示例用 `${EUREKA_PEER_URLS:...}` 占位符 | 一致 |
| `instance.hostname` | 单机 | 不设置 | 旧值 `eureka7001.com`（需 hosts） | 矛盾，已移出默认 |
| `instance.hostname` | 集群 | 各节点唯一 | 示例用 `${EUREKA_INSTANCE_HOSTNAME:localhost}` | 一致 |
| `instance.ip-address` | 单机 | 不设置 | 旧值 `127.0.0.1` + `prefer-ip-address=true` | 矛盾（环回地址入注册表），已移出默认 |
| `instance.ip-address` | 集群 | 不设置 | 示例不设置，仅 `prefer-ip-address` | 一致 |

**对等复制结论（双 JVM 实测）**：`register=false` + `fetch=false` 的旧默认配置**不能**作为集群成员使用（不注册、不拉取，实测确认无初始同步）；`true/true` + 互指 `defaultZone` 的集群成员配置**可以**工作——注册事件在 1 秒内复制到对端（`replicated=true`）。单 JVM 内双上下文不可用于该验证（Eureka 静态上下文互相覆盖），故仓库内保留的用例只断言确定性部分（无初始同步、本节点接受注册），复制结论以本次双 JVM 实验为准。

## 5. 装配矩阵

| 形态 | 注册中心 Bean | Servlet 容器 | `/eureka/apps` | 启动耗时（§9 口径） | 结论 |
| --- | --- | --- | --- | --- | --- |
| 仅本模块，开关缺省（修复前） | present | present | 200 | 未单独计时（同形态修复后为 5604 / 5605 ms） | 缺陷：引入即启用（**证据来源：推断**——由"无开关 + `@EnableEurekaServer` 无条件装配"与修复后同形态 404 对照得出，未对该基线单独起进程实测） |
| 仅本模块，开关缺省（修复后） | absent | present | 404 | **5604 / 5605 ms**（§9，偏差 0.02%） | 符合预期（`GXEurekaServerSwitchSpringBootTest#registryEndpointsAreAbsentWhenSwitchIsMissing` 实测；`#applicationStartsWhenSwitchIsMissingAndClientPropertiesAreUnset` 覆盖"未配置任何 `eureka.client.*`"路径，断言 `eureka.client.enabled=false` 且应用正常启动） |
| 开关 `=false` | absent | present | 404 | **5604 / 5605 ms**（与缺省同一路径，不重复计时） | 符合预期（同上） |
| 开关 `=true`（只设开关） | present | present | 200 | **7145 / 7098 ms**（§9，偏差 0.7%；相对未启用形态 +1535 ms / 约 +27%） | 符合预期（`GXEurekaServerSpringBootTest` 实测；`GXEurekaServerSwitchSpringBootTest#switchOnlySingleMachineDefaultsToNoRegistration` 锁定 `register-with-eureka`/`fetch-registry` 默认 `false`、`EurekaClientConfigBean` 不注册不拉取） |
| 聚合门面消费者（修复后） | 不含本模块：依赖树无该坐标，类路径不存在本模块类 → **未启用、无 Servlet 容器增量** | 无增量 | 不适用 | 不适用（类路径无本模块类，注册中心相关启动成本为 0） | 符合预期（`mvn -pl leaf-framework dependency:tree` 实测） |
| `EnableEurekaServer` 类缺失 | absent，上下文正常 | — | — | 不适用（`ApplicationContextRunner` 上下文级用例，未起进程） | 符合预期（`FilteredClassLoader` 用例） |
| WebFlux 应用，开关缺省 | absent | Netty（reactive） | 404 | **6837 / 6712 ms**（§9，偏差 1.8%） | **真实启动实测**：`GET /ping=200`、`/eureka/apps=404` |
| WebFlux 应用，开关 `=true` | absent | Netty（reactive） | 404 | **6712 / 6701 ms**（§9，偏差 0.16%；与开关缺省同量级，注册中心未装配故无增量） | **真实启动实测**：`GET /ping=200`、`/eureka/apps=404`（修正后，见 F-16；其客户端默认禁用路径另由 `GXEurekaServerClientDefaultsPostProcessorTest#disablesClientWhenSwitchIsEnabledButApplicationIsReactive` 保障） |

用例与证据：`GXEurekaServerSwitchSpringBootTest`（开关缺省 404 / 开启 200 / 缺省且零 `eureka.client.*` 正常启动且客户端默认禁用 / 只设开关时单机默认 `false/false`）、`GXEurekaServerSpringBootTest`（开关 `true` 真实注册往返）、`GXEurekaServerConfigTest`（三态条件与类缺失）、`GXEurekaServerClientDefaultsPostProcessorTest`（6 个默认值组合）、`mvn -pl leaf-framework dependency:tree`（不含本模块）、仓库外 WebFlux 工程冒烟与计时（`lab.flux.FluxApp` + `spring-boot-starter-webflux` + 本模块，两种开关场景各 2 次，计时口径见 §9）。

## 6. 依赖矩阵

| 坐标 | 变更前 | 变更后 | 判定 | 动作 |
| --- | --- | --- | --- | --- |
| `spring-boot-devtools` | 4.1.1 compile | 不存在 | 缺陷 | 移除 |
| `spring-cloud-starter-netflix-eureka-server` | 5.0.2（模块内显式版本） | 5.0.2（BOM 管理） | 冗余 | 删除模块内版本覆盖 |
| `jersey-apache-connector` | 3.1.12 | 3.1.12 | 上游约束 | 保留并锁定（禁止替换，E-18） |
| `jersey-server` / `jersey-client` / `jersey-container-servlet` | 4.0.2 | 4.0.2 | 无风险 | 不变 |
| `jersey-hk2` | 4.0.3 | 4.0.3 | 无风险 | 不变 |
| `httpclient` | 4.5.14 直接声明 | 4.5.14 传递引入 | 冗余（声明） | 删除直接声明，保留依赖 |
| `httpclient5` | 5.6.4 | 5.6.4 | 无风险 | 不变 |
| `hk2-api` / `hk2-utils` / `woodstox-core` | 4.0.3 / 4.0.3 / 7.3.0 直接声明 | 同版本，传递引入 | 冗余（声明） | 删除排除-重引入对 |
| `tomcat-embed-core` | 11.0.24 | 11.0.24 | 无风险 | 不变 |

**diff 结论**：唯一实质差异为 `spring-boot-devtools` 消失；其余坐标版本、scope 不变，仅由"直接声明"变为"传递引入"。

## 7. 配置来源矩阵

| 配置键 | 变更前来源 | 变更后来源 | 结论 |
| --- | --- | --- | --- |
| `eureka.client.register-with-eureka` | 框架 jar 内 `application.yml` | 框架最低优先级默认 `false`（仅 Servlet 注册中心场景，`GXEurekaServerClientDefaultsPostProcessor`）+ 应用配置/示例显式覆盖 | 竞争源已消除，单机语义保留在框架层 |
| `eureka.client.fetch-registry` | 框架 jar 内 `application.yml` | 同上（最低优先级默认 `false`，显式配置优先） | 竞争源已消除，单机语义保留在框架层 |
| `eureka.client.service-url.defaultZone` | 框架 jar 内 `application.yml` | 应用配置 | 竞争源已消除 |
| `eureka.instance.*` | 框架 jar 内 `application.yml` | 应用配置 | 竞争源已消除 |
| `maple.framework.enable.eureka-server` | 不存在 | 元数据声明 + 代码默认 `false` | 新增 |
| `eureka.client.enabled` | 未设置（starter 默认 `true`） | 非"Servlet 注册中心"场景由 `GXEurekaServerClientDefaultsPostProcessor` 以最低优先级置 `false` | 新增（F-15/F-16 修复） |

**宿主配置生效实测（U-01）**：在临时目录放置宿主 `application.yml`（`server.port=18795`、`maple.framework.enable.eureka-server=true`、`eureka.client.register-with-eureka=false`、`fetch-registry=false`），以该目录为工作目录启动应用：`http://localhost:18795/eureka/apps` 返回 **200** → 端口与开关均来自宿主配置，框架侧没有同名配置参与竞争，也不存在覆盖方向问题。

## 8. 契约核对清单

| 来源 | 条目 | 期望 | 实际 | 判定 |
| --- | --- | --- | --- | --- |
| `AGENTS.md` | 章节编号 | 1、2、3……连续 | 已补 `## 2. 启用方式与默认值`，编号连续 | 一致 |
| `AGENTS.md` | 测试现状 | 描述现有用例 | 已改为 23 个用例 / 6 个测试类的分类说明（`GXEurekaServerConfigTest` 6、`GXEurekaServerExamplesTest` 3、`GXEurekaServerSwitchSpringBootTest` 4、`GXEurekaServerSpringBootTest` 2、`GXEurekaServerPeerClusterSpringBootTest` 2、`GXEurekaServerClientDefaultsPostProcessorTest` 6） | 一致 |
| `AGENTS.md` | 功能基线 | 登记开关默认值与连接器约束 | 第 8/9/10 条已登记 | 一致 |
| `AGENTS.md` | 强制规则第 4 条 | 指向实际配置文件 | 改为 `examples/` 示例约束 | 一致 |
| `README.md` | 模块表 | 注明需显式开启、不再随门面传递 | 已更新 | 一致 |
| `docs/` | 手册 | 存在且含开关、示例、迁移 | 新增 `docs/leaf-base-eureka-server开发手册.md` | 一致 |
| 配置元数据 | 开关 | 名称/类型/默认值 | `META-INF/additional-spring-configuration-metadata.json` 已声明 | 一致 |

## 9. 资源与启动成本

| 指标 | 值 | 口径 |
| --- | --- | --- |
| 启用注册中心的进程启动耗时（第 1 次 / 第 2 次） | **7145 ms / 7098 ms**（偏差 0.7%） | 独立 JVM，从进程启动到 `/eureka/apps` 返回 200，同机同 JDK |
| 未启用注册中心（开关缺省）的进程启动耗时（第 1 次 / 第 2 次） | **5604 ms / 5605 ms**（偏差 0.02%） | 独立 JVM，从进程启动到 Servlet 端口可响应 |
| 启动耗时增量 | **+1535 ms（约 +27%）** | 同机、同命令行、G1 默认参数 |
| WebFlux 应用（开关缺省）的进程启动耗时（第 1 次 / 第 2 次） | **6837 ms / 6712 ms**（偏差 1.8%） | 仓库外探针（`lab.flux.FluxApp` + `spring-boot-starter-webflux` + 本模块，`--spring.main.web-application-type=reactive`），独立 JVM，从进程启动到 `GET /ping` 返回 200；`/eureka/apps` = 404 |
| WebFlux 应用（开关 `=true`）的进程启动耗时（第 1 次 / 第 2 次） | **6712 ms / 6701 ms**（偏差 0.16%） | 同上，附 `--maple.framework.enable.eureka-server=true`；注册中心未装配（`/eureka/apps` = 404），与缺省形态差异在噪声范围内 |
| 常驻线程数（注册中心节点，两个节点各测一次） | **89 / 89** | `jcmd <pid> Thread.print` 中 `^"` 开头的线程行数 |
| 常驻线程数（未启用注册中心的同栈应用） | **53** | 同上 |
| 线程数增量 | **+36** | Eureka Server/复制/驱逐/缓存刷新相关线程 |
| 堆内存（committed） | 均为 **139264 K（约 136 MB）**；used 59~73 MB 波动 | `jcmd <pid> GC.heap_info`；committed 由 JVM 区域化策略决定，未见模块引入的显著差异 |
| 测试类耗时（参考） | `GXEurekaServerSpringBootTest` 4.8s / `GXEurekaServerSwitchSpringBootTest` 15.2s（四个上下文）/ `GXEurekaServerPeerClusterSpringBootTest` 30~33s（含轮询） | surefire 报告 |

**采集限制**：启动耗时以"端口可响应"（Servlet 形态为端口可连接、WebFlux 形态为 `GET /ping` = 200）为观测点，未包含 Spring 上下文 `refresh` 完成后的全部后置任务；堆内存 used 随 GC 波动，只记录 committed 与区间。WebFlux 两项为仓库外探针测量（与 Servlet 形态同机同 JDK），未纳入 CI。以上口径均可在同机重新执行复算。

## 10. 发现清单

| 编号 | 级别 | 维度 | 位置 | 触发条件 | 影响面 | 证据 | 验证级别 | 回归设计 | 修复动作 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F-01 | 高 | 装配边界 | `GXEurekaServerConfig.java:12-16` | 引入模块即启用 | 门面全部消费者 | E-02/E-04/E-05 | 已实测 | `GXEurekaServerSwitchSpringBootTest#registryEndpointsAreAbsentWhenSwitchIsMissing`（旧代码下 `/eureka/apps` = 200，断言失败） | 开关 + Web 类型条件 + 日志 |
| F-02 | 高 | 配置语义 | `application.yml:1-11` | 按默认值部署 | 集群不可用 | E-11 | 已实测 | `GXEurekaServerPeerClusterSpringBootTest`（`fetch-registry=false` 时无初始同步；`true/true` + 互指 `defaultZone` 时本节点接受注册） | 删除默认配置，示例区分单机/集群 |
| F-03 | 高 | 配置语义 | `application.yml:3-5` | 容器/多主机部署 | 注册表含环回地址 | E-11 | 已实测 | 示例断言不含 `127.0.0.1` | 移出默认 |
| F-04 | 高 | 依赖治理 | `pom.xml:64-67` | 任意下游引入 | 生产获得 devtools | E-06 | 已实测 | 依赖树断言无 devtools | 移除 |
| F-05 | 高 | 配置语义 | `application.yml`（资源根） | 宿主有同名配置 | 配置静默竞争 | E-12 | 已实测 | `classpathRootHasNoApplicationYml` | 移入 `examples/` |
| F-06 | 中 | 依赖治理 | `pom.xml:73-77` | 试图统一 Jersey 代际 | 对等复制失败 | E-07/E-18 | 已实测 | 保留 3.1.12 + 双节点用例 | 文档化为上游约束 |
| F-07 | 中 | 依赖治理 | `pom.xml:25-63` | 阅读依赖声明 | 意图不可读 | E-09 | 已实测 | 依赖树逐坐标一致 | 删除 4 组排除-重引入 |
| F-08 | 中 | 依赖治理 | `pom.xml:18/24` | BOM 升级 | 钉死旧版本 | E-10 | 已实测 | 依赖树仍为 5.0.2 | 删除版本覆盖 |
| F-09 | 中 | 配置语义 | 注释与缺 `server.port` | 复制示例启动 | 端口与身份不符 | E-11 | 已实测 | 示例断言端口为占位符 | 示例使用占位符 |
| F-10 | 中 | 契约一致性 | `GXEurekaServerConfigTest.java` | 改动装配/配置 | 缺陷不可见 | E-13/E-16 | 已实测 | 用例数 2 → 23（6 个测试类，见 §1.1） | 重写测试 |
| F-11 | 中 | 契约一致性 | `AGENTS.md`、`docs/`、元数据 | 维护者查阅 | 误用风险 | E-14/E-15 | 已实测 | 文档核对清单全绿 | 补文档与元数据 |
| F-12 | 低 | 装配边界 | `GXEurekaServerConfig.java:13` | 阅读条件 | 误判存在降级路径 | E-02/E-03 | 已实测 | 类缺失用例 | 保留（作为兜底） |
| F-13 | 低 | 契约一致性 | `GXEurekaServerConfigTest.java:18` | 非 UTF-8 平台 | 断言噪声 | E-13 | 已实测 | 显式 `StandardCharsets.UTF_8` | 已修正 |
| F-14 | 低 | 契约一致性 | 测试包 | 空值检查 | 静态检查盲区 | E-17 | 已实测 | 期望差异：修复前 `src/test/java/.../package-info.java` 不存在（包级策略缺省，NullAway 无 `@NullUnmarked` 声明）；修复后该文件存在且首行为 `@org.jspecify.annotations.NullUnmarked`。断言点：`classpath 资源/文件存在性` + `mvn -pl leaf-base-eureka-server test` 编译阶段 ErrorProne/NullAway 零错误作为门禁 | 已修复：测试包新增 `package-info.java`（`@NullUnmarked`），与主包空值策略一致 |
| F-15 | 高 | 装配边界 | `spring.factories` 缺失 + 无 `application.yml` | 仅引入本模块且开关关闭 | 应用启动失败 | E-19 | 已实测 | `registryEndpointsAreAbsentWhenSwitchIsMissing` | `GXEurekaServerClientDefaultsPostProcessor` |
| F-16 | 中 | 装配边界 | `GXEurekaServerClientDefaultsPostProcessor`（初版条件） | 开关 `=true` 但应用为 WebFlux/非 Web | 客户端自动注册仍启用 → 启动失败 | E-20 | 已实测 | `disablesClientWhenSwitchIsEnabledButApplicationIsReactive` + 仓库外复测 | 默认策略改为"仅 Servlet 注册中心保留客户端" |

## 11. 风险排序与修复批次

排序依据：影响面 × 触发概率 × 可观测性。

| 批次 | 发现 | 理由 |
| --- | --- | --- |
| 第 1 批（阻断级，已交付） | F-15、F-01 | 都会导致"应用起不来"或"意外变成注册中心"，且无日志可循 |
| 第 2 批（已交付） | F-04、F-05、F-02、F-03 | 依赖外泄与配置竞争，影响所有下游，但需触发条件 |
| 第 3 批（已交付） | F-06 文档化、F-07、F-08、F-09、F-10、F-11 | 可维护性与可读性 |
| 第 4 批（已收口） | F-12（保留为兜底条件）、F-13（已修）、F-14（已修） | 低影响 |

## 12. 已知遗留

1. **单 JVM 复制验证限制**：Eureka 的 `EurekaServerContextHolder` 为静态上下文，同一 JVM 内两个注册中心上下文会互相覆盖，因此仓库内用例无法断言跨节点复制；该结论依赖双 JVM 实验（§3 U-02、§4）。若后续要进 CI，需以"启动两个子进程"的集成脚本实现，成本较高，暂不纳入。
2. **堆内存口径**：`used` 随 GC 与 JIT 波动，仅 committed 与线程数可稳定比较；如需精确定量需加 `-Xms/-Xmx` 固定值后复测。
3. **跨模块观察（未处理）**：`leaf-base-eureka-client` 的 `src/main/resources/application.yml` 存在与 F-05 同类的"库内同名配置"结构，超出本特性授权范围，建议后续专项收敛。
4. **环境观察（与模块无关）**：在仓库根目录直接启动应用会加载 `config/application.yml`，该模板含未过滤的 `@leaf.framework.client.token@` 占位符，触发 SnakeYAML 解析失败；这是 `config/` 模板的打包期过滤问题，已通过把实验进程工作目录切换到独立目录规避。
5. **WebFlux 形态启动耗时为仓库外采集**：已按 §9 口径测得开关缺省 6837 / 6712 ms、开关 `=true` 6712 / 6701 ms（观测点 `GET /ping` = 200）；因需仓库外工程与 `spring-boot-starter-webflux` 依赖，该数据未纳入 CI，复算步骤见 `quickstart.md` §5。
6. **已关闭项**：F-13（字符集显式化）与 F-14（测试包 `package-info.java`）均已修复，不再列为遗留；`jersey-apache-connector` 的代际混用（F-06）按上游硬约束长期保留，属登记项而非遗留缺陷。

## 13. 受影响应用清单（升级至 4.3.0 起）

本节与 `docs/leaf-base-eureka-server开发手册.md` §5 为同一口径，任一处更新必须同步另一处（SC-011）。

**1. 仓库内结论**：本仓库是多模块库工程，`<modules>` 中没有任何下游业务应用；`leaf-framework` 聚合门面已在修复中移出本模块，因此**仓库内不存在需要迁移的消费工程**。

**2. 外部消费方的判定命令**（在待升级工程根目录执行，输出非空即需要迁移）：

```powershell
mvn dependency:tree | Select-String -Pattern "leaf-base-eureka-server"
```

**3. 需要迁移的两类应用**：

| 应用类型 | 升级后的表现 | 迁移动作 |
| --- | --- | --- |
| 通过聚合门面 `leaf-framework` 间接获得本模块、并**确实**充当注册中心 | 门面不再传递本模块，注册中心能力直接消失（`/eureka/apps` 变 404，无异常提示） | 在自身 `pom.xml` 显式声明 `leaf-base-eureka-server`，并设置 `maple.framework.enable.eureka-server=true` |
| 直接依赖本模块、依赖"引入即启用"的注册中心 | 开关默认 `false`，注册中心不再自动装配 | 显式设置 `maple.framework.enable.eureka-server=true`，并从 `examples/` 复制单机或集群配置 |
| 只需要"注册自己"的业务服务（从不充当注册中心） | 无变化（若曾被动成为注册中心，升级后自动恢复为普通服务） | 无需动作；如需注册到注册中心，改用 `leaf-base-eureka-client` |

**4. 升级后必须检查的配置键**：`maple.framework.enable.eureka-server`（必须为 `true`）；`eureka.client.register-with-eureka` / `eureka.client.fetch-registry`（框架已提供 Servlet 注册中心场景的最低优先级默认 `false`，集群成员必须显式设 `true`）；`eureka.client.service-url.defaultZone`、`eureka.instance.hostname`、`eureka.instance.ip-address`、`server.port`（框架不再提供，必须由部署方给出）。
