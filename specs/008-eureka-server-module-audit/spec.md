# Feature Specification: leaf-base-eureka-server 装配边界、配置语义与依赖治理审查

**Feature Branch**: `008-eureka-server-module-audit`（`before_specify` hook 未注册，本次未创建 git 分支；当前分支为 `dev4.3.0-spec/007-feign-module-audit`）

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "审查 @leaf-base-eureka-server 模块"

## 背景与审查结论摘要

本规格把"`leaf-base-eureka-server` 作为注册中心启动模块，其自动装配边界是否可控、默认配置是否自洽、依赖是否干净、契约文档是否与实现一致"转化为可验收的交付要求。审查对象与证据来源：

1. 模块全部生产交付物：`GXEurekaServerConfig.java`（16 行）、`package-info.java`（2 行）、`src/main/resources/application.yml`（11 行）、`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（1 行）；测试 1 个类、2 个用例。
2. 契约来源：`leaf-base-eureka-server/AGENTS.md`、`.specify/memory/constitution.md`、根 `pom.xml`、`leaf-framework/pom.xml`（聚合门面）、`README.md` 模块表。
3. 依赖基线：`mvn -pl leaf-base-eureka-server dependency:tree` 实测结果；`spring-cloud-dependencies:2025.1.3` BOM（本地仓库 `spring-cloud-netflix.version=5.0.2`）。
4. **既有测试实测基线**：本次执行 `mvn -pl leaf-base-eureka-server test`，结果 **2 个用例、0 失败、BUILD SUCCESS**（2026-10-22，耗时 6.3s）。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 装配边界 | 自动装配类只有 `@AutoConfiguration` + `@ConditionalOnClass` + `@EnableEurekaServer`，无开关、无 Web 类型约束；聚合门面 `leaf-framework` 无条件依赖本模块 → **任何引入门面的应用都会被强制变成 Eureka Server，并被动获得内嵌 Tomcat 与 Actuator** | 存在高风险缺陷 |
| 配置语义 | `register-with-eureka=false` + `fetch-registry=false` + `defaultZone` 指向 7002/7003 + `hostname=eureka7001.com` + 无 `server.port` → **示例配置既不是可用单机形态，也不构成可用集群**；`ip-address: 127.0.0.1` 与 `prefer-ip-address: true` 把环回地址固化为默认 | 存在高风险缺陷 |
| 依赖治理 | `spring-boot-devtools` 以 compile 作用域随库下发；Jersey 3.1.12 与 Jersey 4.0.2/4.0.3 同栈；HttpClient 4.5.14 与 HttpClient5 5.6.4 并存；5 个 `<exclusion>` 后按同坐标重新声明（净效果为零）；`eureka.server.version=5.0.2` 与 BOM 同值冗余 | 存在中高风险缺陷 |
| 资源与启动成本 | 引入即拉起 servlet 容器（Tomcat 11.0.24）+ Actuator + Jersey 容器，注册中心进程的启动成本被转嫁给所有门面消费者 | 需量化口径 |
| 契约一致性 | `AGENTS.md` 缺第 2 节、"当前模块没有现成测试"表述过时；`docs/` 17 个手册中无本模块手册；无 `additional-spring-configuration-metadata.json` | 契约漂移事实成立 |

关键证据（均可按位置复核）：

- **E-01**：模块生产交付物只有 4 个文件（2 个 Java：`cn/maple/eureka/server/config/GXEurekaServerConfig.java`、同包 `package-info.java`；2 个资源：`application.yml`、`AutoConfiguration.imports`），测试 1 个类 2 个用例，无其它类、无 `docs` 手册、无配置元数据。
- **E-02**：`GXEurekaServerConfig` 类体为空（第 15–16 行），注解仅 `@AutoConfiguration`（第 12 行）、`@ConditionalOnClass(EnableEurekaServer.class)`（第 13 行）、`@EnableEurekaServer`（第 14 行）；**没有** `@ConditionalOnProperty`、`@ConditionalOnWebApplication`、`@ConditionalOnMissingBean`、`@ConditionalOnClass` 之外的任何降级条件。
- **E-03**：生成的 `target/classes/META-INF/spring-autoconfigure-metadata.properties` 只有两行（配置类名、`ConditionalOnClass=...EnableEurekaServer`），说明条件评估只有"类是否存在"一条。
- **E-04**：`leaf-framework/pom.xml` 第 80–84 行以 `${project.parent.version}` 无条件依赖 `leaf-base-eureka-server`（无 `<optional>true</optional>`、无 profile 隔离），同时第 90–94 行依赖 WebFlux 取向的 `leaf-base-webclient`。
- **E-05**：`dependency:tree` 显示 `spring-cloud-starter-netflix-eureka-server:5.0.2:compile` 传递引入 `spring-boot-starter-web:4.1.1`、`spring-boot-starter-tomcat:4.1.1`（`tomcat-embed-core:11.0.24`）、`spring-boot-starter-actuator`。
- **E-06**：`dependency:tree` 显示 `org.springframework.boot:spring-boot-devtools:jar:4.1.1:compile`；模块 `pom.xml` 第 64–67 行声明该依赖时未写 `<scope>`、未写 `<optional>`。
- **E-07**：`jersey-apache-connector:3.1.12`（Jersey 3.x 分支）与 `jersey-server:4.0.2`、`jersey-container-servlet:4.0.2`、`jersey-client:4.0.2`、`jersey-hk2:4.0.3`（Jersey 4.x 分支）同栈；`org.glassfish.hk2:spring-bridge:3.1.1` 与 `hk2-api/hk2-utils/hk2-locator:4.0.3` 同栈。
- **E-08**：`org.apache.httpcomponents:httpclient:4.5.14`（HttpComponents 4.x，已停产分支）与 `org.apache.httpcomponents.client5:httpclient5:5.6.4` 同栈。
- **E-09**：模块 `pom.xml` 第 25–46 行排除 `hk2-api`、`hk2-utils`、`httpclient`、`woodstox-core`、`jersey-apache-connector`；第 48–63 行又按**完全相同的坐标**重新声明前四个，版本由根 `dependencyManagement` 固定（根 `pom.xml` 第 80–82 行 `hk2.version=4.0.3`、`woodstox-core.version=7.3.0`、`httpclient.version=4.5.14`；第 486–506 行声明）→ 排除与重新引入净效果为零；只有 `jersey-apache-connector` 带显式 `3.1.12`。
- **E-10**：本地仓库 `spring-cloud-dependencies:2025.1.3` BOM 第 99 行 `spring-cloud-netflix.version=5.0.2`；模块 `pom.xml` 第 18 行 `eureka.server.version=5.0.2` 与 BOM 同值 → 属冗余声明，且 BOM 升级后模块会把该 starter 钉在旧版本。
- **E-11**：`application.yml` 同时设置 `eureka.instance.prefer-ip-address: true`、`eureka.instance.ip-address: 127.0.0.1`、`eureka.instance.hostname: eureka7001.com`、`eureka.client.register-with-eureka: false`、`eureka.client.fetch-registry: false`、`eureka.client.service-url.defaultZone: http://eureka7002.com:7002/eureka/,http://eureka7003.com:7003/eureka/`；**没有** `server.port`（第 1–11 行）。
- **E-12**：该 `application.yml` 位于 `src/main/resources/` 根（构建日志 "Copying 2 resources from src\main\resources to target\classes"，产物 `target/classes/application.yml`），即与 Spring Boot 默认配置位置 `classpath:/application.yml` 同名，并随 jar 发布给下游。
- **E-13**：`GXEurekaServerConfigTest` 仅 2 个用例：`autoConfigurationImportContainsEurekaServerConfig`（读 imports 文件做字符串包含断言）与 `eurekaServerConfigIsAutoConfigurationAndEnablesEurekaServer`（注解存在性断言）；**没有** Spring 上下文加载、**没有** `application.yml` 关键值断言、**没有**启动行为验证。
- **E-14**：`AGENTS.md` 章节编号从 `## 1. 模块定位` 直接跳到 `## 3. 功能基线`（缺第 2 节）；第 58 行"当前模块没有现成测试"与现状（已有 2 个用例）矛盾。
- **E-15**：`docs/` 共 17 个手册，无 `leaf-base-eureka-server开发手册.md`；`src/main/resources` 下无 `META-INF/additional-spring-configuration-metadata.json`。
- **E-16**：实测基线 `mvn -pl leaf-base-eureka-server test` → `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`（2026-10-22）。
- **E-17**：`package-info.java` 只标注 `@org.jspecify.annotations.NullUnmarked`；测试包 `cn.maple.eureka.server.config`（test 侧）无 `package-info.java`。
- **E-18**：实施期实测——把 `jersey-apache-connector` 替换为 `jersey-apache5-connector:4.0.2` 后，双节点用例失败：`Cannot create Jersey3 client` → `NoClassDefFoundError: org/glassfish/jersey/apache/connector/ApacheConnectorProvider`（`GXEurekaServerPeerClusterTest` 报告）。
- **E-19**：实施期实测——删除 jar 内 `application.yml` 后，仅引入本模块的应用启动失败：`Failed to start bean 'eurekaAutoServiceRegistration'`（`GXEurekaServerSwitchTest#registryEndpointsAreAbsentWhenSwitchIsMissing` 首次运行）。

发现清单（分级）：

- **F-01（高）引入即启用注册中心，无开关、无 Web 类型约束**：本模块不提供任何关闭手段，聚合门面无条件依赖 → 引入 `leaf-framework` 的全部应用都会被拉起注册中心与内嵌 Tomcat；应用若以 WebFlux 为主栈，与同门面 `leaf-base-webclient` 形成 servlet/reactive 冲突；与宪法第 I 条"对可选能力使用条件装配降级"、第 IV 条"功能开关统一置于 `maple.framework.enable.<feature>`、默认关闭"直接冲突（E-02、E-04、E-05）。
- **F-02（高）示例默认配置语义自相矛盾，"集群"实际不可用**：`register-with-eureka=false` 与 `fetch-registry=false` 表示"不向任何人注册、也不拉取任何人"，此时 `defaultZone` 指向的 7002/7003 既不会收到本节点注册、也不会与之做注册表复制；再叠加 `hostname=eureka7001.com`（本节点身份是 7001）与 `defaultZone`（对端是 7002/7003）的不一致，示例既不是单机形态也不是等效集群（E-11）。
- **F-03（高）环回地址与硬编码域名被固化为框架默认**：`prefer-ip-address: true` + `ip-address: 127.0.0.1` 使注册信息中的地址固定为环回地址（容器、多主机、同机多网卡场景下对端不可达）；`hostname` 同时被硬编码为 `eureka7001.com`，与 `AGENTS.md` 第 51 条"不要把本地机器域名、临时端口或私有环境地址固化为框架默认"冲突（E-11、E-14）。
- **F-04（高）`spring-boot-devtools` 以 compile 作用域随库下发**：库模块传递 devtools 会让下游应用在非开发环境被动具备"自动重启 / 远程调试入口"能力，且改变类加载与静态初始化行为；库模块不应把开发期工具作为可传递编译依赖（E-06）。
- **F-05（高）库内 `classpath:/application.yml` 与宿主应用同名配置位置**：本模块把注册中心默认值放进 jar 根 `application.yml`，与宿主应用自己的 `application.yml` 处于同一配置位置，两者最终生效值取决于类路径解析顺序；下游可能出现"自己的 `eureka.*` 被框架默认值覆盖"或"框架默认值被静默丢弃"两种结果之一，且都不报错（E-12）。
- **F-06（中，实测定性为"上游约束"）Jersey 3 连接器与 Jersey 4 容器并存**：`jersey-apache-connector:3.1.12` + `httpclient:4.5.14` 属 Jersey 3 分支，与 `jersey-server/container-servlet/client:4.0.2`、`jersey-hk2:4.0.3`（Jersey 4 分支）同栈；`spring-bridge:3.1.1` 与 `hk2 4.0.3` 亦跨代际（E-07、E-08）。**实测结论（E-18）**：替换为 `jersey-apache5-connector:4.0.2` 后双节点用例报 `NoClassDefFoundError: org/glassfish/jersey/apache/connector/ApacheConnectorProvider`——该连接器是 `eureka-core-jersey3` 复制客户端的硬依赖。故本项不是可修复缺陷，而是"必须保留并锁定版本 + 文档化"的约束。
- **F-07（中）排除后按同坐标重新声明，净效果为零且掩盖意图**：`hk2-api`、`hk2-utils`、`httpclient`、`woodstox-core` 被排除后立即以相同 groupId/artifactId 回来（版本由根 `dependencyManagement` 决定），读者无法判断真实意图；同时使已停产分支的 `httpclient 4.5.14` 长期留在 classpath（E-09、E-08）。
- **F-08（中）starter 版本与 BOM 同值冗余，BOM 升级即钉死旧版本**：`eureka.server.version=5.0.2` 与 `spring-cloud-dependencies:2025.1.3` 管理的 `spring-cloud-netflix.version=5.0.2` 相同，属无收益的重复声明；BOM 升级到 5.0.x 之后版本时，本模块会停留在 5.0.2 且无任何提示（E-10）。
- **F-09（中）示例配置缺 `server.port`，与自身注释和节点身份不一致**：`application.yml` 第 10 行注释里的单机示例依赖 `${server.port}`，但文件中未设置 `server.port`；下游按示例启动会监听 8080，而节点身份写的是 7001（E-11）。
- **F-10（中）测试只覆盖字符串与注解存在性，未覆盖启动与配置语义**：违反 `AGENTS.md` 第 5 节三项要求中的"配置类变更至少覆盖上下文能加载 `GXEurekaServerConfig`"与"`application.yml` 变更至少覆盖关键配置值"（E-13、E-16）。
- **F-11（中）契约文档漂移**：`AGENTS.md` 章节编号断裂（缺第 2 节）、"当前模块没有现成测试"表述过时；`docs/` 缺本模块手册；无配置元数据导致 `eureka.*` 之外的自定义键（当前无）与模块能力在 IDE 中不可发现（E-14、E-15）。
- **F-12（低）`@ConditionalOnClass` 与直接使用注解并存**：条件只表达"starter 在类路径"，对使用者没有额外保护价值，却让人误以为存在降级路径（E-02、E-03）。
- **F-13（低）测试用 `new String(bytes)` 未指定字符集**：当前断言内容为 ASCII，风险低，但与"编码显式化"约定不符（E-13）。
- **F-14（低）测试包未声明空值策略**：test 侧包无 `package-info.java`，与"每个包声明包级空值策略"的惯例不一致（E-17）。
- **F-15（高，实施期实测发现）移除 jar 内 `application.yml` 后，仅引入本模块的应用启动失败**：`spring-cloud-starter-netflix-eureka-server` 传递引入 Eureka **client** 自动配置，默认 `register-with-eureka=true` 且 `defaultZone=http://localhost:8761/eureka/`；注册中心不可达时 `eurekaAutoServiceRegistration` 启动失败（E-19）。旧 `application.yml` 的 `register-with-eureka: false` 一直在掩盖该行为，因此 F-05 的修复会放大此缺陷，必须与"开关关闭时默认禁用客户端"同批交付。
- **F-16（中，实施期实测发现并已修复）开关为 `true` 但应用非 Servlet 时，客户端默认未禁用导致启动失败**：`GXEurekaServerClientDefaultsPostProcessor` 初版只判断开关值，开关为 `true` 的 WebFlux 应用仍会启用 Eureka 客户端自动注册并在注册中心不可达时抛 `Failed to start bean 'eurekaAutoServiceRegistration'`（E-20）。修复后判定条件改为"是否为 Servlet 注册中心"，并有单测与仓库外复测双重覆盖。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：自动装配入口与 `AutoConfiguration.imports` 一致（第 1 行 `cn.maple.eureka.server.config.GXEurekaServerConfig`），`spring-autoconfigure-metadata.properties` 生成正确（E-03）。
- **P-02**：使用 `@AutoConfiguration` 而非 `@Configuration`，符合 Spring Boot 自动装配规范；类体为空，未把业务 Bean 聚合进配置类（E-02）。
- **P-03**：模块边界清晰：不含 Feign、WebClient、`@LoadBalanced`、业务 Bean，与 `AGENTS.md` 第 1 节定位一致（E-01、E-02）。
- **P-04**：`register-with-eureka=false` 与 `fetch-registry=false` 在"单机注册中心"这一场景下是正确默认值，`AGENTS.md` 第 3 节已把它登记为基线，修复不得改变单机语义（E-11、E-14）。
- **P-05**：依赖冲突治理已在根 POM 集中登记（`hk2`、`woodstox-core`、`httpclient`、`jersey-hk2` 的版本与中文冲突注释），版本来源单一（E-09）。
- **P-06**：既有 2 个用例通过，可作为回归基线（E-16）。

## Clarifications

### Session 2026-10-22

- Q: 本次交付是"只给审查结论与验证基线"，还是"结论 + 直接修复"？ → A: 结论 + 可执行验证基线 + 修复设计为本次必交付；生产代码修复由后续 `/speckit.plan` 依据发现分级排期。本规格不修改任何生产代码，只要求每条发现具备"旧代码失败、新代码通过"的回归设计。
- Q: 是否允许跨模块改动（根 `pom.xml`、`leaf-framework` 聚合门面、`config/` 模板）？ → A: 审查阶段不修改任何文件；修复阶段的跨模块候选限定为三处最小集合——根 POM 的版本/冲突治理条目、`leaf-framework/pom.xml` 中本模块的依赖声明、下游示例配置；每一处都必须在计划阶段单独列出影响面后再授权，禁止顺手改其它模块。
- Q: `spring-boot-devtools` 是判定为缺陷还是既定设计？ → A: 判定为缺陷（库模块传递开发期工具）。处置方式由后续澄清收敛为"从本模块依赖中彻底移除"（本模块源码零引用 devtools），热重载能力由宿主应用自行声明，模块手册补本机开启说明。
- Q: 库内 `src/main/resources/application.yml` 是判定为缺陷还是示例交付物？ → A: 判定为缺陷（与宿主应用同名配置位置）。修复方向为"移出 jar 根"（放置位置由后续澄清收敛为 `src/main/resources/examples/`），并保留"复制即可运行"的获取路径；不得只改文件名就宣称已修复，必须给出下游覆盖顺序的实测证据。
- Q: 默认"集群"配置是否按缺陷处理，是否会改默认值？ → A: 按"示例配置自相矛盾"处理。`register-with-eureka=false` / `fetch-registry=false` 的单机默认值保持不变（P-04）；新增集群场景的显式配置样例与配置矩阵，并要求给出"对等复制是否发生"的实测结论，不得仅凭文档断言。
- Q: 是否允许引入新的测试依赖或依赖外部环境？ → A: 不引入。使用 `ApplicationContextRunner` / `@SpringBootTest`、JDK 内置能力与临时目录构造验证，审查与回归设计都必须能在无外网、无外部注册中心的环境下运行。
- Q: 性能以什么标准判定"改到位"？ → A: 不设吞吐/时延门禁。只要求给出可复算口径：引入本模块带来的启动固定成本（上下文启动耗时）、常驻线程数、注册中心相关内存基线，以及同环境重跑两次偏差不超过 30%。
- Q: 独立的审查报告存放位置与文件名？ → A: `specs/008-eureka-server-module-audit/audit-report.md`；报告与规格同目录、同生命周期，`spec.md` 只保留摘要与该报告的引用。
- Q: 新增的"启用注册中心"开关默认值取 `true` 还是 `false`？ → A: 默认 `false`（开关名 `maple.framework.enable.eureka-server`），同时把聚合门面 `leaf-framework` 中的本模块依赖移出；该变更声明为破坏性变更，注册中心部署方必须显式开启开关或直接依赖本模块，迁移路径与受影响应用清单在报告中列明。
- Q: Jersey 3.1.12 连接器与 Jersey 4.0.x 同栈的依赖冲突按哪个方向收敛？ → A: 统一代际：以 `jersey-apache5-connector 4.0.2` 替换 `jersey-apache-connector 3.1.12`（实施前核实该旧坐标无 4.x 版本，Jersey 4 的 HttpClient 连接器已改名为 apache5 坐标），移除 `httpclient` 4.x 的重复声明与对应 `<exclusion>`，并以真实启动的注册中心上下文验证 Jersey 初始化与节点间调用；验证失败时记录证据并回退到 Jersey 默认连接器。**实施期实测：该方案不可行（E-18，`NoClassDefFoundError: ApacheConnectorProvider`），已按 FR-017 保留 `jersey-apache-connector 3.1.12` 并文档化为上游约束。**
- Q: 框架默认配置里与部署环境绑定的项（`ip-address`、`hostname`、`defaultZone`）应该如何处置？ → A: 框架默认只保留与位置无关的单机语义（`register-with-eureka=false`、`fetch-registry=false`）；`ip-address`、`hostname`、`defaultZone`、`server.port` 全部移出默认配置，改为示例文档提供，默认配置中不得出现固定地址、固定域名或固定端口。
- Q: 移出 jar 根后的单机与集群示例配置以什么形式交付？ → A: 放在本模块 `src/main/resources/examples/` 下随 jar 分发（不在 `classpath:/` 根，不会被自动加载），单机与集群各一份，并由 `docs/leaf-base-eureka-server开发手册.md` 引用并给出复制步骤。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 注册中心启动语义可复核（单机 vs 集群）(Priority: P1)

框架维护者要判断"按模块自带默认值启动后，它到底是单机注册中心还是可用集群"，以及 5 个关键配置项（`register-with-eureka`、`fetch-registry`、`service-url.defaultZone`、`instance.hostname`、`instance.ip-address`）在两种形态下应取什么值。

**Why this priority**: 注册中心是全部微服务的发现入口；形态判断错误会导致"服务注册成功但彼此不可见"这类无法从日志看出的故障。

**Independent Test**: 按示例配置启动一个节点，再按报告给出的集群配置启动两个节点，观察注册表是否互相同步、客户端能否从任一节点看到全部实例。

**Acceptance Scenarios**:

1. **Given** 模块自带的 `application.yml` 默认值，**When** 启动一个应用，**Then** 报告给出该节点是否向 7002/7003 发起注册、是否拉取注册表、注册表内容的唯一定性。
2. **Given** 两个节点按示例 `defaultZone` 互相指向，**When** 客户端注册到节点 A，**Then** 报告明确节点 B 的注册表中是否出现该实例（对等复制是否发生）。
3. **Given** 单机形态（`register-with-eureka=false` / `fetch-registry=false`），**When** 单独启动，**Then** 报告确认该形态可用且与 `AGENTS.md` 基线一致。
4. **Given** `instance.hostname=eureka7001.com`、`ip-address=127.0.0.1`、`prefer-ip-address=true` 三者同时存在，**When** 查看注册信息，**Then** 报告给出最终注册到注册表的地址字段值，以及从另一台机器能否访问该地址的判定。

---

### User Story 2 - 装配边界可验证：引入即生效的影响面 (Priority: P1)

下游接入方需要明确：仅把本模块（或聚合门面 `leaf-framework`）加入依赖后，应用会发生什么；能不能只引入而不启用；在 WebFlux 应用中会发生什么。

**Why this priority**: "加依赖就变成注册中心"是跨项目级影响，且没有开关可用；这是本模块最高影响面的结论。

**Independent Test**: 用 `ApplicationContextRunner` 或最小 `@SpringBootTest` 分别验证"空应用 + 本模块""WebFlux 应用 + 本模块""聚合门面消费者""显式尝试关闭"四种组合，记录是否出现 Eureka Server 相关 Bean、是否拉起 Tomcat。

**Acceptance Scenarios**:

1. **Given** 一个只引入本模块的普通应用，**When** 启动上下文，**Then** 报告给出是否存在 Eureka Server 自动配置、是否激活 servlet 容器、启动耗时。
2. **Given** 一个 WebFlux 应用（依赖 `leaf-base-webclient`）同时引入本模块，**When** 启动，**Then** 报告给出实际生效的 Web 栈与是否启动失败/降级。
3. **Given** 一个只引入聚合门面 `leaf-framework` 的应用，**When** 启动，**Then** 报告明确该应用是否被动成为注册中心，并列出受影响的应用范围。
4. **Given** 使用者希望"引入但不启用"，**When** 按现有配置项组合尝试，**Then** 报告明确当前是否可行；若不可行，给出最小开关方案（开关名、默认值、向后兼容策略）。

---

### User Story 3 - 依赖治理可验证 (Priority: P1)

维护者需要判断本模块给下游带来了哪些传递依赖、哪些是冲突或已停产代际、哪些是净效果为零的声明，以及最小修正集合是什么。

**Why this priority**: 注册中心依赖会传递给所有门面消费者；devtools 与 Jersey/HttpClient 代际混用属于"不报错但改变运行形态"的问题。

**Independent Test**: 对模块执行依赖树与依赖收敛检查，逐条比对模块 `pom.xml` 的排除/重新声明，比对 BOM 管理版本。

**Acceptance Scenarios**:

1. **Given** 模块 `pom.xml`，**When** 执行依赖树，**Then** 报告给出 `spring-boot-devtools` 的作用域与可传递性判定。
2. **Given** 5 组 `<exclusion>`，**When** 逐条比对重新声明的坐标，**Then** 报告明确每组"净效果为零/有效"以及 `jersey-apache-connector` 的例外原因。
3. **Given** Jersey 3.1.12 与 Jersey 4.0.2/4.0.3 同栈，**When** 评估运行时影响，**Then** 报告给出"是否存在类加载/方法签名不兼容风险"的定性，以及"是否需要统一到 4.x"的建议。
4. **Given** `eureka.server.version=5.0.2` 与 BOM 同值，**When** 依据宪法第 2 条与附加约束 2 判定，**Then** 报告给出"冗余/保留理由"结论与最小治理动作。

---

### User Story 4 - 配置样例的可用性与覆盖风险 (Priority: P2)

接入方需要判断：能否直接复制模块内的 `application.yml` 使用；把它打进 jar 后，宿主的 `application.yml` 会不会被覆盖或覆盖框架默认值。

**Why this priority**: 同名配置位置的问题只在特定组装方式下暴露，一旦发生很难定位，属于"必须先有确定性结论再动手"的风险。

**Independent Test**: 构造"框架 jar + 宿主 `application.yml`"的类路径，分别测试宿主配置了 `eureka.client.*` 与未配置两种情形，记录最终生效值来源。

**Acceptance Scenarios**:

1. **Given** 框架 jar 内含 `application.yml`，宿主 jar 也含 `application.yml`，**When** 启动应用，**Then** 报告给出最终生效值来自哪一方，并说明该结论是否稳定（多次启动是否一致）。
2. **Given** 宿主显式设置 `eureka.client.fetch-registry=true`，**When** 启动，**Then** 报告给出宿主值是否生效。
3. **Given** 只复制模块内 `application.yml` 作为运行配置，**When** 启动，**Then** 报告给出可运行性结论（缺 `server.port` 的实际监听端口、`eureka7001.com` 是否可解析）。

---

### User Story 5 - 契约文档与可发现性一致 (Priority: P2)

维护者与接入方需要文档与实现一致：`AGENTS.md` 章节完整、测试现状描述准确、有模块手册、配置项在 IDE 中可发现。

**Why this priority**: 本模块是"注册中心能力"的唯一说明来源；文档漂移会直接导致误用（例如误以为默认是集群）。

**Independent Test**: 逐条比对 `AGENTS.md`、`README.md` 模块表、`docs/` 目录、模块资源目录，输出"一致/不一致"清单。

**Acceptance Scenarios**:

1. **Given** `AGENTS.md`，**When** 核对章节编号与测试现状表述，**Then** 报告给出全部不一致点及位置。
2. **Given** `docs/` 与 `README.md`，**When** 检索本模块，**Then** 报告给出"缺手册/表内描述是否准确"的结论。
3. **Given** 模块资源目录，**When** 检查配置元数据，**Then** 报告给出是否需要（以及需要哪些键的）`additional-spring-configuration-metadata.json`。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现都应有编号、级别、维度、影响面、证据、复现方式与"旧代码失败、新代码通过"的回归设计。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查是否具备七项要素（编号、级别、维度、位置、触发条件与影响、证据、复现与回归设计），缺一即不合格。

**Acceptance Scenarios**:

1. **Given** 一条高危发现，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"测试先行"的任务。
2. **Given** 被判定为既定契约的行为（单机默认 `false/false`、模块不含业务 Bean、`@AutoConfiguration` 入口），**When** 查阅报告，**Then** 明确标注为契约而非缺陷，避免被误改。

---

### Edge Cases

- 宿主应用同时引入本模块与 `leaf-base-webclient`（servlet 与 reactive 双栈）；宿主自定义 `WebApplicationType.REACTIVE`。
- 聚合门面 `leaf-framework` 消费者只想用 Feign/数据源，从未打算启动注册中心。
- 容器/云主机部署（`prefer-ip-address=true` + `ip-address=127.0.0.1`）；同机多网卡；IPv6 优先。
- `eureka7001.com` / `eureka7002.com` / `eureka7003.com` 未在 hosts 解析；DNS 不可用。
- 未设置 `server.port` 与已设置 `server.port` 但值不等于节点身份端口（7001）。
- 三节点集群中只有一个节点把 `register-with-eureka` / `fetch-registry` 改为 `true`。
- 宿主 `application.yml` 与本模块 `application.yml` 同时存在；宿主通过 `--spring.config.location` 覆盖。
- 宿主使用 Undertow/Jetty 而非 Tomcat；宿主排除 `spring-boot-starter-tomcat`。
- 生产环境 classpath 中存在 `spring-boot-devtools`（远程调试/自动重启被激活）。
- Jersey 3 连接器与 Jersey 4 容器在同一 JVM 初始化；`jersey-apache-connector` 被下游排除。
- `spring-cloud-dependencies` 升级后本模块 starter 仍为 5.0.2。
- 上下文启动失败（端口占用、Jersey 初始化异常）时的错误可读性。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 4 个生产交付物（`GXEurekaServerConfig.java`、`package-info.java`、`application.yml`、`AutoConfiguration.imports`）与 1 个测试类的全部 2 个用例；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（装配边界 / 配置语义 / 依赖治理 / 资源与启动成本 / 契约一致性）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式（既有用例、可新增的回归用例设计或明确构造步骤）；仅凭阅读的判断 MUST 明确标注为"未验证假设"。
- **FR-004**: 启动语义 MUST 以"单机"与"集群"两种形态实测，逐项给出 5 个配置键（`register-with-eureka`、`fetch-registry`、`service-url.defaultZone`、`instance.hostname`、`instance.ip-address`）× 2 形态 = 10 个断言点的"应取值 / 实际取值 / 结论"三元定性；集群形态 MUST 给出"对等注册表复制是否发生"的实测结论（对应 F-02）。
- **FR-005**: 自动装配 MUST 在 4 种真实形态下各验证一次：仅本模块的空应用、WebFlux 应用、聚合门面消费者、显式尝试关闭；每种形态 MUST 给出"是否启用 Eureka Server、是否激活 servlet 容器、启动耗时"三项定性（对应 F-01）。
- **FR-006**: 依赖治理 MUST 逐条核对 6 组事实：`spring-boot-devtools` 作用域与可传递性、5 组 `<exclusion>` 的净效果、`jersey-apache-connector` 版本代际、`hk2`/`spring-bridge` 代际、`httpclient` 4.x 与 `httpclient5` 并存、`eureka.server.version` 与 BOM 的对应关系；每组 MUST 给出"缺陷 / 冗余 / 无风险"判定与最小修正方案（对应 F-04、F-06、F-07、F-08）。
- **FR-007**: 库内 `classpath:/application.yml` 的加载与覆盖语义 MUST 用真实类路径组装实测，给出确定结论（谁覆盖谁、是否稳定）；修复方案 MUST 把示例配置移出 classpath 根，改置于本模块 `src/main/resources/examples/` 下（随 jar 分发但不在配置位置根，不会被自动加载），单机与集群各一份，并由模块手册给出复制步骤；MUST NOT 仅以"重命名文件"作为修复（对应 F-05）。
- **FR-008**: 默认配置值 MUST 逐项核对一致性：`prefer-ip-address`、`ip-address`、`hostname`、`defaultZone`、缺失的 `server.port`，每项给出"一致 / 矛盾"判定，并 MUST 校核是否违反 `AGENTS.md` 第 51 条"不得把本地机器域名、临时端口或私有环境地址固化为框架默认"（对应 F-03、F-09）。修复方向 MUST 为"框架默认只保留与位置无关的单机语义（`register-with-eureka=false`、`fetch-registry=false`），`ip-address`、`hostname`、`defaultZone`、`server.port` 一律移出默认配置并放入示例文档"，MUST NOT 在默认配置中保留任何固定地址、固定域名或固定端口。
- **FR-009**: 对外契约 MUST 与实现逐条对照，至少覆盖：`AGENTS.md` 章节完整性（缺第 2 节）、第 5 节测试要求与现有 2 个用例的差距、"当前模块没有现成测试"的过时表述、`README.md` 模块表描述、`docs/` 手册缺失、配置元数据缺失；不一致处 MUST 单列为发现（对应 F-11）。
- **FR-010**: 既有测试 MUST 全部执行并记录结果（基线 2 执行 / 0 失败 / BUILD SUCCESS，2026-10-22）；失败用例 MUST 作为独立发现记录，MUST NOT 删除或跳过用例来"通过"。
- **FR-011**: 审查 MUST 明确区分"缺陷"与"既定契约"：至少确认 `@AutoConfiguration` 入口形态、模块不含业务 Bean、单机默认 `register-with-eureka=false` / `fetch-registry=false` 为契约；对既定契约 MUST 要求文档与测试双向对齐，而非计为缺陷。
- **FR-012**: 本规格的审查活动 MUST NOT 修改任何生产代码、配置或依赖；审查报告 MUST 独立成文于 `specs/008-eureka-server-module-audit/audit-report.md`，`spec.md` 只保留摘要与该报告的引用，两者交付时 MUST 相互一致。
- **FR-013**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异）；该测试 MUST 不引入新的第三方依赖、MUST 不依赖外部网络或外部注册中心、MUST 在无中间件环境下可运行。
- **FR-014**: 资源与启动成本 MUST 给出可复算口径：引入本模块前后的上下文启动耗时、常驻线程数增量、注册中心相关内存基线；同环境重跑两次偏差 MUST ≤ 30%，MUST NOT 以绝对吞吐或时延数值作为通过门槛。
- **FR-015**: 跨模块影响面 MUST 量化：说明聚合门面 `leaf-framework` 的消费者是否 100% 被动成为注册中心，列出判定依据（依赖声明位置与条件装配缺失），并 MUST NOT 在本规格中直接修改 `leaf-framework/pom.xml`。
- **FR-016**: 修复设计 MUST 给出装配开关方案：开关名为 `maple.framework.enable.eureka-server`（遵循宪法第 IV 条 `maple.framework.enable.<feature>` 形态），**默认 `false`**，仅当显式置为 `true` 时启用 `@EnableEurekaServer`；同时 MUST 把聚合门面 `leaf-framework` 中本模块的依赖移出。方案 MUST 声明该变更为破坏性变更，并 MUST 给出迁移路径（注册中心部署方显式设置开关为 `true`，或直接依赖本模块）与受影响应用清单；MUST 说明开关为 `false` 时框架其它能力不受影响、WebFlux 应用可正常启动（对应 F-01）。
- **FR-017**: 修复设计 MUST 按实测结论收敛依赖：`jersey-apache-connector`（Jersey 3）MUST **保留**并锁定 `3.1.12`（`eureka-core-jersey3` 复制客户端的硬依赖，替换为 `jersey-apache5-connector` 实测报 `NoClassDefFoundError: ApacheConnectorProvider`），`httpclient` 4.x MUST 保留（该连接器的必需实现）但模块内 MUST NOT 再直接声明；MUST 删除 `hk2-api`/`hk2-utils`/`woodstox-core` 的排除-重引入对；`spring-boot-devtools` MUST 从本模块依赖中彻底移除（源码零引用），并在模块手册写明"热重载由宿主应用自行声明 `spring-boot-devtools`"的获取方式；混合代际 MUST 在 `AGENTS.md` 与手册中登记为上游约束与禁止改动项；MUST NOT 在无验证的情况下直接升级主版本（对应 F-04、F-06、F-07）。
- **FR-018**: 结论 MUST 包含风险排序依据（影响面 × 触发概率 × 可观测性），使计划阶段可直接确定修复批次；高影响面判定 MUST 说明受影响范围（如"引入聚合门面的全部应用"）。
- **FR-019**: 修复设计 MUST 消除 F-15/F-16 的启动失败路径：当应用**不是 Servlet 注册中心**（开关非 `true`，或开关为 `true` 但应用为 WebFlux/非 Web）且未显式配置 `eureka.client.enabled` 时，模块 MUST 以**最低优先级**默认 `eureka.client.enabled=false`（应用显式配置优先），MUST 输出一条 ASCII 日志说明降级来源，并 MUST 有回归用例证明"开关关闭且未配置任何 `eureka.client.*` 时应用正常启动、`/eureka/apps` 返回 404"，以及"开关为 `true` 的 WebFlux 应用同样正常启动且不装配注册中心"。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **启动语义矩阵**：配置键（5 个）× 形态（单机 / 集群）× 应取值 / 实际取值 / 结论。
- **装配矩阵**：应用形态（空应用 / WebFlux / 聚合门面 / 显式关闭）× 是否启用注册中心 × 是否激活 servlet 容器 × 启动耗时。
- **依赖矩阵**：坐标（devtools / hk2-api / hk2-utils / woodstox-core / httpclient / jersey-apache-connector / eureka starter）× 解析版本 × 作用域 × 是否可传递 × 判定（缺陷 / 冗余 / 无风险）。
- **配置来源矩阵**：配置键 × 来源（框架 jar 内 `application.yml` / 宿主 `application.yml` / 命令行）× 最终生效值 × 是否稳定。
- **契约核对清单**：文档条目（`AGENTS.md` 章节、`README.md` 表格、`docs/` 手册、配置元数据）× 实现现状 × 一致 / 不一致。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 4 个生产交付物与 1 个测试类的 2 个用例，100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"七要素；0 条仅凭阅读的结论混入已确认项。
- **SC-003**: 启动语义矩阵覆盖 5 个配置键 × 2 种形态（10 个断言点）并 100% 有唯一定性；集群形态必须给出"对等复制是否发生"的明确结论，不得使用"可能/通常"类措辞。
- **SC-004**: 装配矩阵覆盖 4 种应用形态，100% 给出"是否启用注册中心 / 是否激活 servlet 容器 / 启动耗时"，且聚合门面形态的受影响范围结论可被引用为跨模块任务的输入。
- **SC-005**: 依赖矩阵覆盖 6 组依赖事实，100% 给出"缺陷 / 冗余 / 无风险"判定；其中 devtools 可传递性与 5 组 exclusion 的净效果结论必须有依赖树或收敛结果佐证。
- **SC-006**: 配置来源矩阵至少覆盖"框架 jar + 宿主 jar 同名配置"场景，100% 的键给出"最终生效来源"判定，并明确该判定是否随启动次数变化。
- **SC-007**: 既有测试执行率 100%（基线 2 执行 / 0 失败），基线结果在报告中保留并可复算；新增回归设计 100% 具备"旧失败、新通过"的期望差异。
- **SC-008**: 本规格的审查活动不修改生产代码、不新增第三方依赖、不依赖外部网络或外部注册中心；所有验证可在单机环境完成。
- **SC-009**: 契约核对覆盖 4 类文档/元数据来源（`AGENTS.md`、`README.md`、`docs/`、配置元数据），100% 给出"一致 / 不一致"判定，不一致项 100% 定位到具体章节或行号。
- **SC-010**: 5 条高危发现（F-01 ~ F-05）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。
- **SC-011**: 开关默认 `false` 的兼容性影响 100% 量化：报告中列出"升级后必须显式开启开关或直接依赖本模块"的应用清单，并以回归用例证明"开关 `false` 时 WebFlux 应用正常启动且不出现注册中心 Bean""开关 `true` 时注册中心正常启用"两个方向各至少 1 个断言通过。

## Assumptions

- 目标运行形态为 JDK 21 + Spring Boot 4.1.x + Spring Cloud 2025.1.x（`spring-cloud-netflix 5.0.2`）；当前下游通过 `leaf-framework` 聚合门面或直接依赖引入本模块，修复后门面不再传递本模块，注册中心部署方改为直接依赖并显式开启开关；ErrorProne + NullAway 保持启用。
- 本模块的定位是"注册中心启动模块"，不是业务服务侧配置模块；业务侧的注册与发现归 `leaf-base-eureka-client`，本次审查不评价 client 模块的配置质量，只在需要对照默认值时引用。
- `eureka7001.com` / `eureka7002.com` / `eureka7003.com` 属于示例域名，需要 hosts 解析；本规格不把它当作生产契约，但要求给出"未解析时的实际表现"结论。
- 单机形态的 `register-with-eureka=false` / `fetch-registry=false` 是既定契约，本次不改变其默认值；集群形态以新增显式样例与配置矩阵的方式补齐，不通过修改默认值实现。
- 本次审查不引入 MockWebServer / Testcontainers / 外部注册中心，验证手段限于 `ApplicationContextRunner`、最小 `@SpringBootTest`、依赖树分析与临时目录类路径组装。
- 审查阶段不修改任何生产代码与配置文件；Clarifications 中的 devtools、库内 `application.yml`、装配开关、依赖收敛结论用于约束后续计划阶段的修复设计，不在本次落地。
- 启动耗时与线程数的比较基线为"同机、同 JDK、空应用"；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- 聚合门面 `leaf-framework` 的依赖清单属于跨模块事实，本规格只引用不修改；已按 Clarifications 确定"把本模块移出聚合门面"，该跨模块改动仍需在计划阶段列出影响面后执行，范围仅限 `leaf-framework/pom.xml` 中该条依赖声明。
