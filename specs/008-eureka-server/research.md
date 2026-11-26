# Phase 0 Research: leaf-base-eureka-server 审查与修复设计

**Feature**: `008-eureka-server-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

本文件把 spec 的 12 项澄清与 14 条发现收敛为可执行决策。每条包含：决策、理由、备选方案、影响面、验证方式。事实性证据编号沿用 `spec.md` 的 E-01 ~ E-17。

---

## D-01 装配开关：`maple.framework.enable.eureka-server`，默认关闭

**决策**：在 `GXEurekaServerConfig` 上新增

```java
@ConditionalOnProperty(name = "maple.framework.enable.eureka-server", havingValue = "true", matchIfMissing = false)
```

开关默认 `false`，只有显式置为 `true` 才启用 `@EnableEurekaServer`。

**理由**：

1. 宪法 IV 要求"功能开关统一置于 `maple.framework.enable.<feature>`，默认关闭"。
2. Eureka Server 是**部署角色**而非库能力：一个应用是否充当注册中心由部署拓扑决定，不能由"依赖里有没有这个 jar"决定（对应 F-01）。
3. 没有开关时，门面消费者无法自救；有开关后即使误引入也能一键关闭。

**备选方案**：

- 默认 `true` 保持兼容：影响面仍覆盖所有门面消费者，只在消费者主动关闭时才缩小，未解决 F-01 的根因。
- 只用 `@ConditionalOnClass`：该条件恒定成立（模块自身就带 starter），等于无条件装配。
- 用 `@Profile` 控制：profile 是部署期环境概念，与"能力开关"语义混淆，且与宪法 IV 的开关约定不一致。

**影响面**：受控破坏性变更——升级后，原先依赖"引入即启用"的注册中心应用必须显式开启开关（迁移见 `contracts/configuration-contract.md`）。

**验证方式**：`ApplicationContextRunner` 分别以缺省属性、`=true`、`=false` 启动，断言注册中心相关 Bean 的有无（见 `contracts/assembly-contract.md` 矩阵）。

---

## D-02 追加 `@ConditionalOnWebApplication(type = SERVLET)`

**决策**：与 D-01 一起生效；非 Servlet 应用（WebFlux / REACTIVE / 非 Web）不装配注册中心。

**理由**：Eureka Server 依赖 Servlet 容器（`spring-boot-starter-web` + Tomcat）与 Jersey Servlet 容器。缺少该守卫时，WebFlux 应用会以"缺 `ReactiveWebServerFactory`"或 Jersey 初始化失败等**间接错误**启动失败，错误信息与真实原因脱节（对应 F-01 的次生影响）。

**备选方案**：

- 只在文档中说明"勿与 WebFlux 同用"：约束落在人身上，无法测试。
- 保留模块并让下游自行排除 starter：下游需知道内部依赖细节，成本高。

**影响面**：纯 Servlet 应用行为不变；WebFlux 应用由"启动失败"变为"静默不启用注册中心"，需在手册中显式说明。

**验证方式**：注解契约断言（`@ConditionalOnWebApplication` 的 `type` 必须为 `SERVLET`）；`FilteredClassLoader` 缺失 `EnableEurekaServer` 时上下文正常、无注册中心 Bean。

---

## D-03 聚合门面 `leaf-framework` 移出本模块

**决策**：删除 `leaf-framework/pom.xml` 中 `leaf-base-eureka-server` 依赖声明；`README.md` 模块表标注"需显式开启、不再随门面传递"。

**理由**：F-01 的影响面来自"门面无条件依赖"（E-04）。移除后影响面从"全部门面消费者"收缩为"显式引入者"，与 D-01 形成双保险：误引入者可通过开关关闭，未意图者根本拿不到。

**备选方案**：

- 在门面里加 `<exclusions>`：仍需保留依赖声明，语义别扭且容易被后续"整理依赖"时恢复。
- 保持门面不变、仅靠开关默认 `false`：影响面已消除，但所有门面消费者仍无谓承担 `spring-boot-starter-web`/Tomcat/Actuator 的体积与版本约束。

**影响面**：受控破坏性变更——门面消费者若曾"用着顺手"启动注册中心，必须显式加依赖；迁移清单在报告中逐条列出。

**验证方式**：`mvn -pl leaf-framework dependency:tree` 中不再出现 `leaf-base-eureka-server`；`README.md`/手册与门面实际依赖一致。

---

## D-04 删除 `src/main/resources/application.yml`，示例移入 `examples/`

**决策**：删除 classpath 根的 `application.yml`；新增 `src/main/resources/examples/eureka-server-standalone.yml` 与 `eureka-server-peer.yml`（随 jar 分发，但不在 `classpath:/` 根，不会被 `ConfigDataEnvironmentPostProcessor` 当作默认配置加载）。

**理由**：F-05 的根因是"库内配置与宿主 `application.yml` 同名同位置"（E-12）。`examples/` 子目录不在默认配置位置（`classpath:/`、`classpath:/config/`）内，既能随 jar 交付"复制即用"的样例，又不会参与配置加载。

**备选方案**：

- 改名 `application-eureka.yml`：仍会被当作 profile 配置文件（`spring.profiles` 命中时会加载），风险未消除。
- 只放文档 YAML 片段：下游需要手工抄写，易错；已由澄清否决。

**影响面**：下游若曾依赖框架 jar 内的默认值，升级后会自动落到 Spring Boot 的 Eureka 默认值（`register-with-eureka=true`、`fetch-registry=true`）——这正是必须在手册迁移章节里写清的一条。

**验证方式**：资源契约用例断言 `classpath:/application.yml` 不存在、两个示例存在且关键键值正确；另以真实类路径组装验证"宿主 `application.yml` 生效且不被框架覆盖"。

---

## D-05 默认配置只保留单机 `false/false`

**决策**：默认配置（若保留，仅存在于示例的单机版）只含：

```yaml
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

不出现 `instance.ip-address`、`instance.hostname`、`service-url.defaultZone`、`server.port`。

**理由**：`AGENTS.md` 第 51 条禁止固化本地域名/私有地址；`ip-address: 127.0.0.1` + `prefer-ip-address: true` 在任何非单机环境都会把环回地址写进注册表（F-03），`hostname: eureka7001.com` 需要 hosts 配合（F-09），固定端口会与被引入应用的 `server.port` 冲突。

**备选方案**：

- 保留 `defaultZone` 改为 `http://${eureka.instance.hostname}:${server.port}/eureka/`：在 `fetch-registry=false` 时该值无实际作用，属误导性配置。
- 用 `${EUREKA_HOST:localhost}` 占位符：仍是"框架给地址"，与其让用户覆盖不如让用户必填。

**影响面**：单机场景行为不变（`false/false` 保留）；集群场景从"看起来配了其实不可用"变为"必须按示例填写"。

**验证方式**：示例文件键值断言 + 启动语义矩阵（`contracts/configuration-contract.md`）。

---

## D-06 移除 `spring-boot-devtools`

**决策**：从 `leaf-base-eureka-server/pom.xml` 删除 `spring-boot-devtools` 依赖声明，不保留 `<optional>`/`<provided>`。

**理由**：库模块以 compile 作用域传递 devtools（E-06），下游生产包会获得重启类加载器与远程调试入口（F-04）。本模块全部源码（2 个 Java 文件）零引用 devtools，删除不影响编译。

**备选方案**：

- `<optional>true</optional>`：不传递，但模块自身编译期可见——本模块没有使用场景，属于"留着备用"，违反"不重复造轮子/不引无用依赖"的克制原则。
- `<scope>provided</scope>`：语义是"运行环境提供"，与 devtools 的实际用法不符，容易误导。

**影响面**：模块自身开发热重载需由开发者在本机临时添加；手册给出一行说明。

**验证方式**：`dependency:tree` 中 `spring-boot-devtools` 消失；`mvn -pl leaf-base-eureka-server test` 通过。

---

## D-07 保留 `jersey-apache-connector:3.1.12`（上游硬约束），不做代际替换

**事实核实（2026-10-22，两轮实验）**：

1. `org.glassfish.jersey.connectors:jersey-apache-connector` **最新版本为 3.1.12，不存在 4.0.x**（远程 `maven-metadata.xml` 无 4.x，`/4.0.2/...pom` 返回 404）；Jersey 4 的 HttpClient 连接器改名为 `jersey-apache5-connector`（4.0.0 ~ 4.0.3 可用，依赖 `httpclient5`）。
2. 按"统一代际"把连接器替换为 `jersey-apache5-connector:4.0.2` 后，**双节点用例直接失败**：
   `Cannot create Jersey3 client` → `NoClassDefFoundError: org/glassfish/jersey/apache/connector/ApacheConnectorProvider`。
   `eureka-core-jersey3:2.0.6` 的 `Jersey3ReplicationClient` **硬编码** Jersey 3 的 `ApacheConnectorProvider`，与 Jersey 4 的连接器包名不兼容。单节点用例不触发对等复制节点创建，因此不会暴露该问题——这正是"只测单机测不出来"的典型场景。

**决策**：**保留** `jersey-apache-connector:3.1.12` 作为显式依赖（版本由根 POM `jersey-apache-connector.version` 锁定），保留 starter 上对该坐标的 `<exclusion>`（用于锁定版本、避免上游传递版本漂移），并在 `AGENTS.md` 与开发手册中登记"禁止替换/移除"。

**理由**：这不是本模块的选择，而是 `eureka-core-jersey3` 的运行时约束。Jersey 容器侧（`jersey-server`/`jersey-container-servlet`）走 4.0.2、复制客户端侧走 3.1.12，属上游既定组合；强行统一会破坏对等复制。

**备选方案**：

- 替换为 `jersey-apache5-connector`：**已实测失败**（上述 `NoClassDefFoundError`）。
- 直接移除连接器依赖：同样会在创建复制节点时失败（同类缺失类）。
- 把根属性 `jersey-apache-connector.version` 改为 4.x：该坐标无 4.x 版本，且 `leaf-base-manticore` 共用该属性。

**影响面**：不改动任何依赖坐标；仅把"混合代际"从"缺陷"重新定性为"上游约束 + 必须文档化的锁定项"。

**验证方式**：`GXEurekaServerPeerClusterSpringBootTest` 两个用例（`fetch-registry=false` 时不拉取对端注册表；`register-with-fetch=true` 时本节点接受注册）必须通过；`dependency:tree` 中 `jersey-apache-connector:3.1.12`、`jersey-server:4.0.2`、`httpclient:4.5.14` 三个坐标保持不变。

---

## D-08 删除净效果为零的排除-重引入对与 `httpclient` 4.x 声明

**决策**：模块 `pom.xml` 中删除以下内容：

1. `hk2-api`、`hk2-utils`、`woodstox-core` 的 `<exclusion>` 与其后的同坐标重声明（版本由根 `dependencyManagement` 决定，删除前后解析结果应完全一致）；
2. `httpclient` 的 `<exclusion>` 与 `org.apache.httpcomponents:httpclient` 重声明（该依赖仍由 `jersey-apache-connector` 传递引入，版本仍由根 POM 锁定）；
3. **保留** `jersey-apache-connector` 的 `<exclusion>` 与显式依赖（见 D-07）。

**理由**：四处排除与重引入坐标完全相同（E-09、F-07），净效果为零且掩盖意图。注意：`httpclient 4.5.14` 不能删除——D-07 已确认 Jersey 3 连接器是其必需实现，删除本模块的**直接声明**即可，传递依赖保留。

**备选方案**：

- 保留现状只加注释：读者仍需逐条推演"排除又加回来"的意图。
- 同时删除根 POM 的所有冲突覆盖：会破坏其它模块（`AGENTS.md` ≥2 处声明依赖这些覆盖），违反"不得删除已有的冲突覆盖"。

**影响面**：根 POM 的 `httpclient.version`、`hk2.version`、`woodstox-core.version` 属性与条目**全部保留**；仅删除本模块内的重复声明。

**验证方式**：以 `dependency:tree` 快照逐坐标比对。实测结果：仅 `spring-boot-devtools:4.1.1` 消失；`hk2-api:4.0.3`、`hk2-utils:4.0.3`、`woodstox-core:7.3.0`、`httpclient:4.5.14`、`httpclient5:5.6.4`、`jersey-apache-connector:3.1.12`、`jersey-server:4.0.2`、`jersey-hk2:4.0.3`、`tomcat-embed-core:11.0.24` 版本全部不变（仅由直接声明变为传递引入）。

---

## D-09 删除模块内 `eureka.server.version` 覆盖

**决策**：删除模块 `pom.xml` 的 `eureka.server.version` 属性与 starter 上的显式 `<version>`，版本交给 `spring-cloud-dependencies 2025.1.3`（当前解析为 5.0.2）。

**理由**：两者同值（E-10、F-08），显式覆盖没有收益，却会在 BOM 升级后把 starter 钉死；违反宪法"第三方版本只在根 POM `<properties>` 声明"的单一来源要求。

**备选方案**：把属性提升到根 POM——仍是覆盖，只是换位置，问题未解决。

**影响面**：解析版本当前不变（5.0.2），后续跟随 BOM。

**验证方式**：`dependency:tree` 中 `spring-cloud-starter-netflix-eureka-server` 仍为 5.0.2。

---

## D-10 开关启用时输出一条 ASCII INFO 日志

**决策**：`GXEurekaServerConfig` 在启用路径上输出 `log.info("Eureka Server enabled via maple.framework.enable.eureka-server=true")`（如使用 `@Slf4j`，需确认模块已有 slf4j 可用；否则用 `LoggerFactory` 直接获取）。

**理由**：宪法 IV 要求"装配过程输出明确日志，禁止静默降级"；`AGENTS.md` 第 7 条要求日志文本为 ASCII。

**备选方案**：

- 不输日志：开关默认关闭后，"为什么注册中心没起来"会成为新的排障问题。
- 输出中文日志：违反 `AGENTS.md` 第 7 条。

**影响面**：每个注册中心进程启动时多一条 INFO。

**验证方式**：启动用例断言上下文正常；日志内容以 `OutputCaptureExtension` 或人工核对（不引入新依赖，优先人工核对 + 手册记录）。

---

## D-11 测试体系升级为四类

**决策**：

| 测试类 | 覆盖目标 | 关键断言 |
| --- | --- | --- |
| `GXEurekaServerConfigTest`（改造） | 条件装配矩阵、Web 类型契约、类缺失降级、imports 入口 | 缺省属性无注册中心 Bean；`=true` 有；`=false` 无；`@ConditionalOnWebApplication(type=SERVLET)` 存在；`FilteredClassLoader(EnableEurekaServer)` 下上下文正常 |
| `GXEurekaServerExamplesTest`（新增） | 资源契约 | `classpath:/application.yml` 不存在；`examples/eureka-server-standalone.yml` 含 `register-with-eureka: false` 与 `fetch-registry: false`；`examples/eureka-server-peer.yml` 含两处 `true` 与占位符 `defaultZone`；两者均不含 `127.0.0.1` 与固定 `server.port`（peer 示例除外，其端口须为占位符形式） |
| `GXEurekaServerSpringBootTest`（新增） | 真实启动与注册往返 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + 开关 `true`：`GET /eureka/apps` 返回 200；`POST /eureka/apps/{APP}` 注册后 `GET /eureka/apps/{APP}` 可见实例；`GET /actuator/health` 返回 200（可选） |

**理由**：现有 2 个用例（E-13）只断言字符串包含与注解存在性，无法暴露 F-01/F-02/F-05。`AGENTS.md` 第 5 节已要求上下文加载、imports 可加载、`application.yml` 关键值三类测试。

**备选方案**：只补 `@SpringBootTest`——启动类用例对"开关关闭"这一默认路径覆盖不足；只补 `ApplicationContextRunner`——无法验证真实 Jersey/注册表往返（D-07 的验证需要它）。

**影响面**：测试用例数从 2 增至约 10–12；运行时间增加（一次随机端口启动，预计数秒）。

**验证方式**：`mvn -pl leaf-base-eureka-server test` 全绿；用例在无外部注册中心、无外网环境可运行。

---

## D-12 WebFlux 场景的验证降级

**决策**：不为"WebFlux 应用不启用注册中心"引入 `leaf-base-webclient` 或 `spring-webflux` 依赖。改为两层证据：

1. **注解契约断言**：`@ConditionalOnWebApplication` 的 `type` 必须为 `SERVLET`（WebFlux 上下文不满足）；
2. **类缺失降级断言**：`FilteredClassLoader(EnableEurekaServer.class)` 下上下文正常、无注册中心 Bean。

真实 WebFlux 启动冒烟写入 `quickstart.md`，由消费方工程或 `leaf-base-webclient` 侧集成用例执行。

**理由**：宪法附加约束 3 禁止为新测试引入依赖；在 `leaf-base-eureka-server` 的测试范围内加 `leaf-base-webclient` 会形成"互斥技术栈"之间的构建耦合。

**备选方案**：

- 加 `spring-webflux` test 依赖：违反附加约束 3。
- 用 `ApplicationContextRunner.withPropertyValues("spring.main.web-application-type=reactive")`：类路径无 WebFlux 时上下文本身无法创建，测不出目标行为。

**影响面**：SC-011 的"WebFlux 正常启动"部分以"契约 + 消费方冒烟"组合覆盖，属显式登记的验证残余风险，需在报告中注明。

**验证方式**：`contracts/assembly-contract.md` 中标注该项为"契约级验证"，quickstart 提供消费方冒烟命令。

---

## D-14 开关关闭时默认禁用 Eureka 客户端（实测新增决策）

**实测事实（2026-10-22）**：开关关闭、且模块自带的 `application.yml` 被移除后，一个只引入本模块的 Servlet 应用**启动直接失败**：

```text
Failed to start bean 'eurekaAutoServiceRegistration'
  at EurekaAutoServiceRegistration.start -> EurekaServiceRegistry.register -> maybeInitializeClient
```

原因：`spring-cloud-starter-netflix-eureka-server` 传递引入 Eureka **client** 自动配置；在没有 `eureka.client.enabled=false` 也没有可达 `defaultZone` 时，应用会在启动末期尝试注册自身并抛错。旧 `application.yml` 里的 `register-with-eureka=false` 一直在掩盖这个行为。

**决策**：新增 `GXEurekaServerClientDefaultsPostProcessor`（`EnvironmentPostProcessor`，通过 `META-INF/spring.factories` 注册）。当（a）应用不是"Servlet 注册中心"（开关非 `true`，或开关为 `true` 但 `spring.main.web-application-type` 非 servlet）且（b）应用未显式配置 `eureka.client.enabled` 时，以**最低优先级**追加 `eureka.client.enabled=false`，并输出一条 ASCII INFO 说明来源。

**补充实测（E-20）**：初版只判断开关值，导致"开关 `true` + WebFlux 应用"仍启用客户端并启动失败（`Failed to start bean 'eurekaAutoServiceRegistration'`）。加入 Web 类型判定后，仓库外 WebFlux 工程两种场景均 `PING=200`、`/eureka/apps=404`；对应单测 `GXEurekaServerClientDefaultsPostProcessorTest` 现有 6 个用例（4 个客户端禁用/保留组合 + 2 个单机默认组合，见 D-05 与 `contracts/configuration-contract.md` §1.2）。

**理由**：

1. 满足 FR-016 / SC-011 的"开关为 `false` 时其它能力不受影响、应用可正常启动"。
2. 使用最低优先级属性源，应用显式配置 `eureka.client.enabled=true` 时仍然以其为准，不会破坏"既要注册中心又要当客户端"的场景。
3. 遵守宪法 IV"禁止静默降级"：降级必须伴随日志。

**备选方案**：

- 保留一份只含 `register-with-eureka=false` 的 `application.yml`：正是 F-05 要消除的同名配置竞争，不可取。
- 在模块里 `@AutoConfigureBefore` 覆盖 Spring Cloud 的客户端自动配置：跨库干预自动配置顺序，风险更高。
- 只在手册里要求消费方自行设置：把"引入即失败"留给使用者，属于把缺陷转嫁给下游。

**影响面**：新增 1 个生产类与 1 个 `spring.factories`；对显式配置过 `eureka.client.*` 的应用无影响。

**验证方式**：`GXEurekaServerSwitchSpringBootTest#applicationStartsWhenSwitchIsMissingAndClientPropertiesAreUnset` 在不设置任何 `eureka.client.*` 的情况下启动成功、`eureka.client.enabled=false` 且 `/eureka/apps` 返回 404；`#registryEndpointsArePresentWhenSwitchIsEnabled` 返回 200；`#switchOnlySingleMachineDefaultsToNoRegistration` 锁定只设开关时 `register-with-eureka`/`fetch-registry` 默认 `false`。

---

## D-13 契约文档与元数据对齐

**决策**：

1. 新增 `docs/leaf-base-eureka-server开发手册.md`：模块定位、开关与默认值、单机/集群示例用法、迁移章节（开关默认 false、门面不再传递、默认配置不再内置地址）、验证命令。
2. 新增 `META-INF/additional-spring-configuration-metadata.json`，声明 `maple.framework.enable.eureka-server`（`type=java.lang.Boolean`、`defaultValue=false`、描述为 ASCII 或中文均可，描述不进入日志）。
3. 修正 `AGENTS.md`：补 `## 2.`（原缺失）、第 5 节测试现状改为"已有条件装配与启动用例"，第 3 节补充"默认关闭，需显式开启"基线条目。
4. 更新 `README.md` 模块表：注明"需显式开启、不再随门面传递"。

**理由**：宪法 I 要求模块交付集含 `AGENTS.md` 与 `docs/<模块名>开发手册.md`；宪法 IV 要求新增开关补元数据；F-11 已登记三处契约漂移（E-14、E-15）。

**备选方案**：只改 `AGENTS.md`——手册缺口与元数据缺口仍在，IDE 无提示。

**影响面**：纯文档与元数据，无运行期影响。

**验证方式**：文档与实际配置键双向核对（`grep` 键名）；`mvn -pl leaf-base-eureka-server package` 产物中存在元数据文件；`AGENTS.md` 章节编号连续。

---

## 未决 / 需实测后定性的项

| 编号 | 项 | 处理方式 |
| --- | --- | --- |
| U-01 | 库内 `classpath:/application.yml` 与宿主同名配置的**覆盖方向**是否稳定 | 由 FR-007 的真实类路径组装实测定性；本计划按"会竞争"设计修复，不做方向断言 |
| U-02 | 三节点集群在 `false/false` 下是否发生对等复制 | 由 FR-004 实测；预期"不发生"，但结论以实测为准，报告不得先行断言 |
| U-03 | `jersey-apache5-connector 4.0.2` 下 Eureka 对等通信是否正常 | **已定性：不可行**。替换后双节点用例报 `NoClassDefFoundError: ApacheConnectorProvider`；Jersey 3 连接器为上游硬依赖（见 D-07） |
| U-04 | 删除 `httpclient`/`hk2`/`woodstox` 重复声明后解析结果是否逐坐标一致 | **已定性：一致**。`.tree-before.txt` 与 `.tree-after.txt` 比对显示仅 `spring-boot-devtools` 消失，其余坐标版本不变 |
