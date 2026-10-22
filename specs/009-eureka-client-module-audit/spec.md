# Feature Specification: leaf-base-eureka-client 装配边界、默认配置与契约一致性审查

**Feature Branch**: `009-eureka-client-module-audit`（`before_specify` hook 未注册，本次未创建 git 分支；当前分支为 `dev4.3.0-spec/008-eureka-server-module-audit`）

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "审查 @leaf-base-eureka-client"

## 背景与审查结论摘要

> **证据载体**：完整证据表（E-01 ~ E-27）、装配/配置/依赖矩阵、实测记录与回归设计见同目录 [audit-report.md](./audit-report.md)。本规格只保留结论摘要与指向该报告的引用。

本规格把"`leaf-base-eureka-client` 作为客户端接入模块，其引入后对宿主应用的实际影响是否可控、默认配置是否自洽、依赖是否干净、既有测试是否可信、契约文档是否与实现一致"转化为可验收的交付要求。审查对象与证据来源：

1. 模块全部生产交付物（4 个）：`cn/maple/eureka/client/config/GXEurekaClientConfig.java`（21 行）、同包 `package-info.java`（2 行）、`src/main/resources/application.yml`（15 行）、`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（1 行）；测试 1 个类 4 个用例 + 3 个测试夹具类。
2. 契约来源：`leaf-base-eureka-client/AGENTS.md`、`.specify/memory/constitution.md`、根 `pom.xml`、`leaf-framework/pom.xml`（聚合门面）、`README.md` 模块表、`docs/leaf-base-eureka-server开发手册.md`（默认值口径对照）、`specs/008-eureka-server-module-audit/audit-report.md`（跨模块遗留项）。
3. 依赖基线：`mvn -o -pl leaf-base-eureka-client dependency:tree` 与 `-Dverbose` 实测结果；本地仓库 `com/netflix/eureka/eureka-client/2.0.6/eureka-client-2.0.6.pom`。
4. **既有测试实测基线**：本次执行 `mvn -o -pl leaf-base-eureka-client test`，结果 **4 个用例、1 失败、0 错误、BUILD FAILURE**（2026-10-22 22:12，Windows / JDK 21）。

审查结论：

| 维度 | 结论 | 判定 |
| --- | --- | --- |
| 装配边界 | 自动装配类为空壳（无 `@Bean`、无开关、无 `@ConditionalOnProperty`）；`leaf-framework` 仍无条件依赖本模块 → **任何引入聚合门面的应用都会成为 Eureka 客户端并尝试注册**，且当前没有任何"引入但不注册"的开关 | 存在高风险缺陷 |
| 默认配置语义 | jar 根 `application.yml` 固化 `register-with-eureka: true` + 三个 `eureka700*.com` 域名；与 008 修复后的服务端手册（无默认 `defaultZone`、示例用 `localhost:7002/7003`）口径冲突；库内 `classpath:/application.yml` 与宿主同名 | 存在高风险缺陷 |
| 测试可信度 | **既有 4 个用例在 Windows 上 1 个失败**（CRLF 与 `\n` 断言不匹配），`mvn test` 当前为红；全部用例仅为 `ApplicationContextRunner` + 文本/注解断言，未覆盖真实启动与注册行为 | 存在高风险缺陷 |
| 契约一致性 | `AGENTS.md` 多处描述与实现相反（`@EnableFeignClients`、`@LoadBalanced` 归属、`feign.hystrix.enabled`、测试现状）；`docs/` 缺本模块手册；无配置元数据 | 契约漂移事实成立 |
| 依赖治理 | 3 组 `<exclusion>` 后按同坐标重新声明（净效果为零、无注释）；`httpclient 4.5.14` 与 `httpclient5 5.6.4` 并存；starter 版本交由 BOM 管理（本模块未钉版本，良好） | 存在中低风险缺陷 |
| 职责边界 | 本模块 jar 内 `application.yml` 全局打开 `spring.cloud.openfeign.circuitbreaker.enabled=true`，越出"注册发现"职责并改变宿主 Feign 行为 | 存在中风险缺陷 |

关键证据（均可按位置复核）：

- **E-01**：生产交付物仅 4 个文件（见上），无其它类、无 `docs` 手册、无配置元数据、无 `examples/` 目录。测试侧为 `GXEurekaClientConfigTest`（4 用例）、`testapp/TestApplication`、`testapp/OrderClient`、`testapp/OrderAdminClient`。
- **E-02**：`GXEurekaClientConfig` 第 18–21 行：`@AutoConfiguration(after = {GXFeignConfig.class, GXWebClientConfig.class, EurekaClientAutoConfiguration.class})`（第 18 行）、`@ConditionalOnClass({FeignClient.class, WebClient.class, EurekaClientAutoConfiguration.class})`（第 19 行），**类体为空**；**没有** `@EnableFeignClients`、**没有** `@ConditionalOnProperty`、**没有** 任何 `@Bean`、**没有** 日志。
- **E-03**：`target/classes/META-INF/spring-autoconfigure-metadata.properties` 仅两行条件信息：`GXEurekaClientConfig.AutoConfigureAfter=...`、`GXEurekaClientConfig.ConditionalOnClass=...`，说明条件评估只有"类是否存在"一条。
- **E-04**：`target/classes/application.yml` 存在（由 `src/main/resources/application.yml` 构建时复制到 classes 根），即随 jar 发布到 `classpath:/application.yml`。
- **E-05**：`application.yml` 第 11–15 行：`eureka.client.register-with-eureka: true`、`eureka.client.service-url.defaultZone: http://eureka7001.com:7001/eureka/,http://eureka7002.com:7002/eureka/,http://eureka7003.com:7003/eureka/`；文件为 **CRLF** 换行（15 处 CRLF）。
- **E-06**：`application.yml` 第 1–9 行：`spring.cloud.openfeign.circuitbreaker.enabled: true`、`group.enabled: false`、`alphanumeric-ids.enabled: true`（实测 openfeign 5.0.3 元数据中三个键均仍有效，非死键）。
- **E-07**：`mvn -o -pl leaf-base-eureka-client test` → `Tests run: 4, Failures: 1, Errors: 0, Skipped: 0`，失败用例 `GXEurekaClientConfigTest.applicationYamlContainsExpectedClientDefaults:82`，`BUILD FAILURE`（2026-10-22）。失败原因：第 82 行断言 `contains("spring:\n  cloud:\n    openfeign:\n      circuitbreaker:\n        enabled: true")`，而实际文件为 CRLF。
- **E-08**：测试结构：3 个 `ApplicationContextRunner` 用例（第 32、52 行）与 1 个 YAML 文本断言用例（第 77 行）；**没有** `@SpringBootTest`、**没有** 真实注册/发现验证；第 26、80 行 `new String(inputStream.readAllBytes())` 未指定字符集。
- **E-09**：`dependency:tree` 显示 `spring-cloud-starter-netflix-eureka-client:5.0.2` → `spring-cloud-starter:5.0.3`、`spring-cloud-netflix-eureka-client:5.0.2`（实际解析出 `spring-boot-jackson2:4.1.1`、`httpclient5:5.6.4` + `httpcore5:5.4.3`）+ `com.netflix.eureka:eureka-client:2.0.6`（含 `xstream:1.4.21`、`netflix-eventbus:0.3.0`、`spectator-api:1.7.3`、`jettison:1.5.4`、`jakarta.ws.rs-api:4.0.0`、Jackson 2 分支）。**注意**：`spring-boot-starter-webflux` 来自 `leaf-base-webclient`、`spring-boot-starter-actuator` 来自 `leaf-base-framework`，不由本模块 starter 引入（两者在 starter 的 POM 中声明但未传递解析）。
- **E-10**：`eureka-client:2.0.6` 的 POM 声明 `org.apache.httpcomponents:httpclient:4.5.3:compile`；模块 `pom.xml` 第 42–46 行把它排除后又以根 `pom.xml` 的 `httpclient.version=4.5.14` 重新声明（第 56–59 行）→ classpath 最终为 `httpclient:4.5.14` 与 `httpclient5:5.6.4` 并存。
- **E-11**：模块 `pom.xml` 第 30–59 行：`antlr-runtime`、`error_prone_annotations`、`httpclient` 三个坐标先 `<exclusion>` 再按**完全相同坐标**重新声明，且**无任何注释**说明意图；`-Dverbose` 依赖树中三者只出现在顶层（`error_prone_annotations` 另有一条 guava 路径标记 `omitted for duplicate`），根 `pom.xml` 第 79–82 行给出 `antlr-runtime.version=3.5.3`、`error-prone.version=2.50.0`、`httpclient.version=4.5.14`。
- **E-12**：`leaf-framework/pom.xml` 第 80–84 行以 `${project.parent.version}` **无条件**依赖本模块（无 `<optional>`、无 profile 隔离）；008 已把 `leaf-base-eureka-server` 移出聚合门面，本模块仍在门面内。
- **E-13**：`GXEurekaClientConfigTest` 第 81–84 行把当前默认值锁定为契约：`contains("register-with-eureka: true")`、`contains("alphanumeric-ids:\n          enabled: true")`、`doesNotContain("feign.hystrix.enabled")`。
- **E-14**：`AGENTS.md` §1（第 22–26 行）声称模块职责是"通过 `GXEurekaClientConfig` 启用 `@EnableFeignClients`""通过 `leaf-base-webclient` 自动装配带 `@LoadBalanced` 的 `WebClient.Builder` 与 `HttpServiceProxyFactory`""维护 `application.yml` 中 Eureka Client 连接注册中心的默认示例配置"；§3（第 39–45 行）把 `@EnableFeignClients` 扫描范围、`@LoadBalanced` Bean 契约、`feign.hystrix.enabled` 列为基线；§5（第 60 行）称"当前模块没有现成测试"。实际：无 `@EnableFeignClients`（E-02）；`@LoadBalanced WebClient.Builder` 与 `HttpServiceProxyFactory` 由 `leaf-base-webclient` 的 `GXWebClientConfig` 第 139–164 行提供；`feign.hystrix.enabled` 已不在配置中（E-05、E-13）；已有 4 个用例（E-07）。
- **E-15**：`docs/` 18 个手册中无 `leaf-base-eureka-client开发手册.md`；模块 `src/main/resources/META-INF` 下无 `additional-spring-configuration-metadata.json`。
- **E-16**：`README.md` 第 70 行本模块描述为"Eureka Client 装配"，未说明"引入即注册"、无开关、默认地址为 `eureka700*.com`；同表第 69 行 `leaf-base-eureka-server` 行已注明开关与"不再随门面传递"（本模块无对应说明）。
- **E-17**：`specs/008-eureka-server-module-audit/audit-report.md` 第 208 行已登记本模块"库内同名 `application.yml`"为跨模块遗留项，建议后续专项收敛（本规格即该项的收敛载体）。
- **E-18**：`docs/leaf-base-eureka-server开发手册.md` 第 70–83 行集群示例为 `defaultZone: ${EUREKA_PEER_URLS:http://localhost:7002/eureka/,http://localhost:7003/eureka/}`，并明确"`defaultZone` 无默认"；本模块默认写死三个 `eureka700*.com` 域名，客户端与服务端默认口径不一致。
- **E-19**：`package-info.java` 仅 `@org.jspecify.annotations.NullUnmarked`；test 侧 `cn.maple.eureka.client.config`、`cn.maple.eureka.client.testapp` 两个包无 `package-info.java`。
- **E-20**：模块 `pom.xml` 第 14–18 行重复声明 `maven.compiler.source/target=21` 与 `project.build.sourceEncoding`（根 `pom.xml` 第 41–45 行已声明）；`leaf-base-feign`、`leaf-base-webclient`、`leaf-base-eureka-server`、`leaf-base-manticore` 同样重复，属项目级惯例而非本模块独有。

发现清单（分级）：

- **F-01（高）引入即注册，无任何装配开关**：本模块不提供开关；聚合门面无条件依赖（E-12）→ 引入 `leaf-framework` 的全部应用在启动时都会尝试向三个 `eureka700*.com` 注册；与宪法第 I 条"对可选能力使用条件装配降级"、第 IV 条"功能开关统一置于 `maple.framework.enable.<feature>`"直接冲突（E-02、E-12）。
- **F-02（高）jar 根 `classpath:/application.yml` 与宿主同名配置位置冲突**：模块把 `eureka.client.*` 与 `spring.cloud.openfeign.circuitbreaker.*` 默认值放进 jar 根 `application.yml`，与宿主自己的 `application.yml` 处于同一配置位置，最终生效值取决于类路径解析顺序；下游可能出现"宿主 `eureka.*` 被框架默认值覆盖"或"框架默认值被静默丢弃"两种结果之一，且都不报错（E-04、E-17）。
- **F-03（高）默认值把示例域名与"必注册"固化为框架默认**：`register-with-eureka: true` + 固定 `http://eureka7001.com:7001`、`eureka7002.com:7002`、`eureka7003.com:7003`；与 `AGENTS.md` 第 53 条"不要把本地机器域名、临时端口或私有环境地址固化为框架默认"冲突，与 008 修复后的服务端手册口径冲突（E-05、E-18）。
- **F-04（高）既有测试基线为红且断言平台相关**：`applicationYamlContainsExpectedClientDefaults` 在 CRLF 环境下必失败（E-07）；断言方式为 YAML 文本包含（依赖缩进与换行符），任何格式化调整都会误报，且该断言把 F-03 的缺陷锁定为"期望行为"（E-13）。
- **F-05（中）自动装配类为空壳，条件与排序均无实际作用**：类体为空、无 Bean；`after` 对不产 Bean 的配置类无观察效果；`@ConditionalOnClass` 三个条件对"jar 内 `application.yml` 仍生效 + 传递依赖仍存在"没有任何保护，读者会误以为存在降级路径（E-02、E-03）。
- **F-06（高）契约文档与实现相反**：`AGENTS.md` 把 `@EnableFeignClients` 写成模块职责（实现明确要求业务方自行开启）、把 `@LoadBalanced`/`HttpServiceProxyFactory` 写成本模块装配（实际在 `leaf-base-webclient`）、把 `feign.hystrix.enabled` 列为基线（配置中已无）、称"当前模块没有现成测试"（已有 4 个用例）；维护者按 §1 会错误判断"模块会自动扫描 Feign 客户端"（E-14）。
- **F-07（中）测试未覆盖真实启动、注册与开关语义**：4 个用例全部在 `ApplicationContextRunner` 内运行，不加载 `application.yml`、不启动 Web 容器、不访问注册端点；违反 `AGENTS.md` §5 第 1 条"配置类变更至少覆盖上下文能加载"与第 6 条"优先 `@SpringBootTest`"（E-08）。
- **F-08（中）依赖排除与重新声明净效果为零且无注释**：三组 `<exclusion>` + 同坐标重声明使读者无法判断真实意图；`httpclient 4.5.14` 与 `httpclient5 5.6.4` 长期并存（E-10、E-11）。
- **F-09（中）本模块越出"注册发现"职责**：jar 内 `application.yml` 全局打开 `spring.cloud.openfeign.circuitbreaker.enabled=true`（并设 `group.enabled=false`、`alphanumeric-ids.enabled=true`），改变宿主 Feign 链路行为；这些键属于 `leaf-base-feign` 职责（E-06、E-14）。
- **F-10（中）交付物缺手册与配置元数据**：`docs/` 无本模块手册；无 `additional-spring-configuration-metadata.json`，新增开关后 IDE 无法提示（E-15）。
- **F-11（低）测试字符集未显式化**：两处 `new String(bytes)` 未指定 `UTF_8`（E-08）——当前失败正是编码/换行敏感性的直接体现。
- **F-12（低）test 侧包未声明空值策略**：两个测试包无 `package-info.java`（E-19）。
- **F-13（低，惯例项，不单独立项修复）模块 pom 重复声明编译器属性**：与仓库另外 4 个模块一致，属项目级惯例；若收敛须整批处理（跨模块），本规格只登记（E-20）。

已确认为**正确实现**（不得在修复中被破坏）：

- **P-01**：使用 `@AutoConfiguration` 而非 `@Configuration`，自动装配入口与 `AutoConfiguration.imports`（1 行）一致，条件元数据生成正确（E-01、E-03）。
- **P-02**：**不**自动启用 `@EnableFeignClients`，把扫描范围交还业务方（类 Javadoc 第 14–16 行明确说明），这是必须保持的契约（E-02、E-14）。
- **P-03**：starter 版本不钉版本号，统一由 `spring-cloud-dependencies` BOM 管理，未出现 008 中"钉死旧版本"的问题。
- **P-04**：`spring.cloud.openfeign.circuitbreaker.*` 三个键在当前 OpenFeign 5.0.3 中仍有效，非死键（E-06）。
- **P-05**：模块未混入注册中心服务端逻辑（无 `@EnableEurekaServer`），未引入业务服务名、业务 Feign 接口或业务降级（E-01、E-02）。
- **P-06**：`@ConditionalOnMissingBean` 之类的用户覆盖能力由上游 `leaf-base-webclient`/`leaf-base-feign` 提供，本模块未抢占 Bean 定义。

## Clarifications

### Session 2026-10-22

- Q: 本次交付是"只给审查结论与验证基线"，还是"结论 + 直接修复"？ → A: 结论 + 可执行验证基线 + 修复设计为本次必交付；生产代码修复由后续 `/speckit.plan` 依据发现分级排期。本规格不修改任何生产代码，但每条发现必须具备"旧代码失败、新代码通过"的回归设计。
- Q: 是否允许跨模块改动（根 `pom.xml`、`leaf-framework` 聚合门面、`leaf-base-feign`、`docs/`、`README.md`）？ → A: 审查阶段不修改任何文件；修复阶段的跨模块候选限定为四处最小集合——根 POM 的依赖治理条目、`leaf-framework/pom.xml` 中本模块依赖声明（本次**不**移除，见下条）、`leaf-base-feign` 承接熔断默认键、`docs/` 与 `README.md` 的模块说明；每一处必须在计划阶段单独列出影响面后再授权，禁止顺手改其它模块。
- Q: 是否把本模块从聚合门面 `leaf-framework` 中移除？ → A: **不移除**。移除会让已依赖"通过门面获得注册发现"的下游应用在升级后静默失去服务发现能力（比"被动注册"更难观测），属高风险破坏性变更；本规格要求改为"可显式关闭 + 文档明示 + 默认地址不固化"，并把移除门面作为**独立后续决策**登记，不在本次落地。
- Q: 新增开关的名称与默认值？ → A: 开关名 `maple.framework.enable.eureka-client`（遵循宪法第 IV 条 `maple.framework.enable.<feature>`），**默认 `true`**（保持既有行为，遵循宪法第 II 条"不得顺手改变默认行为"）；其语义是"允许显式关闭本模块带来的客户端装配与注册默认值"，关闭后不得遗留任何 `eureka.*` 默认值副作用，并输出一条 ASCII 日志说明降级来源。
- Q: 库内 `src/main/resources/application.yml` 判定为缺陷还是示例交付物？ → A: 判定为缺陷（与宿主同名配置位置）。修复方向为"移出 jar 根"到 `src/main/resources/examples/`（随 jar 分发、不会被自动加载），单注册中心与多注册中心各一份，并由模块手册给出复制步骤；**不得只改文件名**就宣称已修复，必须给出"宿主覆盖顺序"的实测证据。
- Q: 默认 `defaultZone` 与 `register-with-eureka` 如何处理？ → A: 框架默认**不得**出现任何固定域名、固定地址或固定端口（`eureka700*.com` 全部移除）；`register-with-eureka` 不再由框架默认配置声明（回归上游默认），未配置 `defaultZone` 时启动阶段 MUST 输出**一条** ASCII WARN 指明生效来源与配置键名；示例配置改为 `${EUREKA_DEFAULT_ZONE:...}` 形式并与服务端手册口径对齐。
- Q: 注册中心不可达时应用是否允许启动失败？ → A: 以实测为准，结论唯一化：审查 MUST 实测"三个 `eureka700*.com` 均不可解析/不可达"时的启动结果（正常启动并后台重试 / `Failed to start bean 'eurekaAutoServiceRegistration'` 失败），并给出唯一定性；若实测为启动失败，则修复 MUST 使客户端注册不阻塞应用启动，并有回归用例锁定。
- Q: `spring.cloud.openfeign.circuitbreaker.*` 三个键的归属？ → A: 判定为本模块越权（F-09）。修复方向为"由 `leaf-base-feign` 承接同名默认键"，且迁移前后"仅使用 feign 的应用"行为必须不变，需实测；本模块 jar 内不得再携带任何 Feign 键。该跨模块改动须在计划阶段单独列影响面，若 `leaf-base-feign` 侧评估为不可承接，则退化为"移入示例 + 模块手册明示"，不得保留在 jar 根。
- Q: 既有失败用例如何处置？ → A: **不得**删除、跳过或用 `@Disabled` 绕过；必须先把它改写为不依赖换行符的语义断言（绑定 `Environment`/`EurekaClientConfigBean`）再继续；修复前后的失败-通过差异必须记录（对应宪法第 VI 条"Bug 修复必须附带能在旧代码上失败、在新代码上通过的回归测试"）。
- Q: 是否允许引入新的测试依赖或依赖外部环境？ → A: 不引入。使用 `ApplicationContextRunner`、`@SpringBootTest`、JDK 内置 `com.sun.net.httpserver.HttpServer` 模拟注册端点、临时目录类路径组装完成验证；全部验证 MUST 在无外网、无外部注册中心的环境下可运行，且 MUST 在 Windows（CRLF）与 Linux（LF）下同时通过。
- Q: 独立的审查报告存放位置与文件名？ → A: `specs/009-eureka-client-module-audit/audit-report.md`；报告与规格同目录、同生命周期，`spec.md` 只保留摘要与该报告的引用。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 引入即注册的影响面可判定，且可以关闭 (Priority: P1)

下游接入方需要明确：把本模块（或聚合门面 `leaf-framework`）加入依赖后，应用启动时会发生什么；在完全不想用 Eureka 的应用里，如何只引入而不注册；关闭后是否还残留任何 `eureka.*` 默认值。

**Why this priority**: "加依赖就向三个外部域名注册"是跨项目级影响，且当前没有任何关闭手段；这是本模块最高影响面的结论。

**Independent Test**: 用 `ApplicationContextRunner`/最小 `@SpringBootTest` 分别验证"空应用 + 本模块""聚合门面消费者""开关关闭""显式 `eureka.client.enabled=false`"四种组合，记录是否装配 Eureka 客户端、是否发生注册请求、是否残留默认值。

**Acceptance Scenarios**:

1. **Given** 一个只引入本模块的普通应用，**When** 启动上下文，**Then** 报告给出是否装配 `CloudEurekaClient`/`EurekaAutoServiceRegistration`、是否向 `defaultZone` 发起注册、启动结果（成功/失败）。
2. **Given** 一个只引入聚合门面 `leaf-framework` 的应用，**When** 启动，**Then** 报告明确该应用是否被动成为 Eureka 客户端，并列出受影响应用范围（含 008 已移出服务端后的现状对比）。
3. **Given** 使用者希望"引入但不注册"，**When** 按现有配置项组合尝试（`eureka.client.enabled=false`、`spring.cloud.discovery.enabled=false`、`spring.cloud.service-registry.auto-registration.enabled=false`），**Then** 报告明确各组合的实际效果与残留项；若均不彻底，给出开关方案（名称、默认值、关闭后的可验证后果）。
4. **Given** 开关 `maple.framework.enable.eureka-client=false`，**When** 启动，**Then** 上下文中不存在 Eureka 客户端相关 Bean，`Environment` 中不存在由框架注入的 `eureka.*` 值，且存在一条说明降级来源的 ASCII 日志。

---

### User Story 2 - 默认配置语义与覆盖风险可判定 (Priority: P1)

接入方需要判断：模块自带的默认值是什么、能否直接使用；把它打进 jar 后，宿主的 `application.yml` 会不会被覆盖（或框架默认值被丢弃），以及默认注册地址在真实环境是否可用。

**Why this priority**: 同名配置位置的冲突不报错、只在特定组装方式下暴露；默认域名不可解析会让"注册"这一核心能力直接失效且难以定位。

**Independent Test**: 构造"框架 jar + 宿主 `application.yml`"的类路径，分别测试宿主配置/未配置 `eureka.client.*` 两种情形，记录最终生效值来源；用 `nslookup`/`InetAddress` 验证三个默认域名的可解析性。

**Acceptance Scenarios**:

1. **Given** 框架 jar 内含 `application.yml`、宿主 jar 也含 `application.yml`，**When** 启动应用，**Then** 报告给出最终生效值来自哪一方，并说明该结论是否稳定（多次启动是否一致），以及"框架默认值被丢弃"时是否可见。
2. **Given** 宿主显式设置 `eureka.client.register-with-eureka=false`，**When** 启动，**Then** 报告给出宿主值是否生效。
3. **Given** 宿主完全没有 `eureka.*` 配置，**When** 启动，**Then** 报告给出最终 `defaultZone` 与 `register-with-eureka` 的取值来源，以及是否产生注册流量。
4. **Given** 默认 `defaultZone` 中的三个 `eureka700*.com`，**When** 在未配置 hosts 的机器上解析，**Then** 报告给出每个域名的解析结果与注册请求的实际表现。

---

### User Story 3 - 依赖治理可判定 (Priority: P1)

维护者需要判断本模块给下游带来了哪些传递依赖、哪些声明是净效果为零的重复、哪些库并存会造成代际混用，以及最小修正集合是什么。

**Why this priority**: 本模块随聚合门面下发给所有应用；依赖形态直接决定下游的 classpath 与启动成本。

**Independent Test**: 执行 `dependency:tree`（含 `-Dverbose`），逐条比对模块 `pom.xml` 的排除/重新声明，比对根 POM 的 `dependencyManagement` 与 BOM 管理版本。

**Acceptance Scenarios**:

1. **Given** 模块 `pom.xml`，**When** 执行依赖树，**Then** 报告给出 3 组 `<exclusion>` 的"净效果为零/有效"逐条判定，以及 `error_prone_annotations` 经由 guava 路径仍在 classpath 的事实。
2. **Given** `httpclient:4.5.14` 与 `httpclient5:5.6.4` 并存，**When** 评估运行时影响，**Then** 报告明确两者分别被谁使用（`eureka-client` 4.x 声明 vs `spring-cloud-netflix-eureka-client` 5.x），并给出"保留/收敛"的建议与理由。
3. **Given** `spring-cloud-starter-netflix-eureka-client` 版本由 BOM 决定，**When** 核对根 POM，**Then** 报告确认无钉版本残留，并说明 BOM 升级时的行为。
4. **Given** 传递依赖清单，**When** 统计对下游的增量，**Then** 报告给出"引入本模块新增的依赖/常驻线程"清单（含 `eureka-client 2.0.6` 的 `xstream`、`netflix-eventbus`、`spectator`、Jackson 2 分支，以及 `httpclient5`；并明确 `webflux`/`actuator` 并非本模块 starter 引入）与可复算口径。

---

### User Story 4 - 既有测试基线可复算，并转为可信回归 (Priority: P1)

维护者需要知道"现在跑测试是什么结果"，以及哪些断言是不可信的；计划阶段需要把测试从"文本断言"升级为"行为断言"。

**Why this priority**: 基线为红意味着当前仓库状态无法通过门禁，且该失败本身暴露了断言方式不可移植。

**Independent Test**: 在 Windows 与 Linux（或 LF 归一化后）各执行一次 `mvn -o -pl leaf-base-eureka-client test`，比对结果；逐条评审 4 个用例的断言类型。

**Acceptance Scenarios**:

1. **Given** 当前工作区（CRLF 文件），**When** 执行模块测试，**Then** 报告记录"4 执行 / 1 失败"及失败用例名与断言行号，并给出根因（换行符）。
2. **Given** 同一用例在 LF 环境下的预期，**When** 对照，**Then** 报告明确该用例是"平台相关"而非"逻辑缺陷"，但同时判定其断言方式不合格。
3. **Given** 每个既有用例，**When** 判定其覆盖目标，**Then** 报告给出"保留/改写/新增行为断言"的处置建议，并禁止以删除或禁用来达标。

---

### User Story 5 - 契约文档与实现一致 (Priority: P2)

维护者与接入方需要文档与实现一致：`AGENTS.md` 的模块职责、`@EnableFeignClients` 的开启方式、`@LoadBalanced` 与 `HttpServiceProxyFactory` 的归属、历史 `feign.hystrix.enabled` 的现状、测试现状、模块手册与配置元数据。

**Why this priority**: 本模块是"客户端接入能力"的唯一说明来源；§1 的错误描述会直接导致业务方重复或漏开 Feign 扫描。

**Independent Test**: 逐条比对 `AGENTS.md`、`README.md` 模块表、`docs/` 目录、模块资源目录，输出"一致/不一致"清单并定位到行号。

**Acceptance Scenarios**:

1. **Given** `AGENTS.md` §1、§3、§5，**When** 与实现对照，**Then** 报告给出全部不一致点及行号（至少 4 处）。
2. **Given** `README.md` 与 `docs/`，**When** 检索本模块，**Then** 报告给出"缺手册/表内描述是否准确/是否与服务端手册口径冲突"的结论。
3. **Given** 模块资源目录，**When** 检查配置元数据，**Then** 报告给出新增开关后"必须声明哪些键"的清单。

---

### User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

计划阶段需要把发现直接转成任务：每条发现都应有编号、级别、维度、影响面、证据、复现方式与"旧代码失败、新代码通过"的回归设计；并明确哪些行为是契约、不得修改。

**Why this priority**: 审查不是终点；不能落到可执行任务的结论等于没有结论。

**Independent Test**: 抽取任一发现，检查是否具备七项要素（编号、级别、维度、位置、触发条件与影响、证据、复现与回归设计），缺一即不合格。

**Acceptance Scenarios**:

1. **Given** 一条高危发现，**When** 交由计划阶段，**Then** 无需补充上下文即可拆出"测试先行"的任务。
2. **Given** 被判定为既定契约的行为（不自动开启 `@EnableFeignClients`、保持门面依赖、开关默认 `true`），**When** 查阅报告，**Then** 明确标注为契约而非缺陷，避免被误改。

---

### Edge Cases

- 宿主应用有/无自己的 `classpath:/application.yml`；宿主用 `--spring.config.location`、`spring.config.import`、Profile 专属文件覆盖。
- 宿主 `application.yml` 只配置了部分 `eureka.*` 键；宿主完全不配置 Eureka。
- 三个 `eureka700*.com` 未在 hosts 解析、DNS 超时、部分不可达、全部不可达。
- 注册中心在线但客户端注册被拒（403/404）；注册中心 TLS/基本认证场景。
- 应用只引入 `leaf-base-feign` 而未用 WebFlux（`@ConditionalOnClass(WebClient)` 使本配置跳过，但 jar 内 `application.yml` 仍生效）。
- WebFlux 应用 + Eureka 客户端 + `spring-cloud-loadbalancer` 无实例时的调用结果。
- `eureka.client.enabled=false`、`spring.cloud.discovery.enabled=false`、`spring.cloud.service-registry.auto-registration.enabled=false` 三者的组合与覆盖关系。
- 关闭本模块开关但宿主显式设置 `eureka.client.enabled=true`（谁优先）。
- 聚合门面消费者从未打算使用 Eureka；门面同时引入 `leaf-base-webclient` 与 `leaf-base-feign`。
- 应用为 Servlet 与为 Reactive 两种 Web 类型下，客户端注册线程与容器的关系。
- 历史配置 `feign.hystrix.enabled` 仍出现在下游（已废弃键的实际效果）。
- 同一份源码在 Windows（CRLF）与 Linux（LF）下运行测试的结果一致性。
- 依赖升级：`spring-cloud 2025.1.x` BOM 升级后旧 `httpclient 4.5.14` 与新 `httpclient5` 的兼容性。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 审查 MUST 覆盖模块全部 4 个生产交付物与 1 个测试类的全部 4 个用例（含 3 个测试夹具类）；每个文件 MUST 有明确判定（无缺陷 / 风险 / 缺陷 / 未覆盖原因），不得跳过。
- **FR-002**: 每条发现 MUST 标注维度（装配边界 / 默认配置语义 / 测试可信度 / 依赖治理 / 职责边界 / 契约一致性）、严重级别（高 / 中 / 低）、代码位置（文件 + 行区间）、触发条件与影响面。
- **FR-003**: 每条发现 MUST 附带复现方式（既有用例、可新增的回归用例设计或明确构造步骤）；仅凭阅读的判断 MUST 明确标注为"未验证假设"。
- **FR-004**: 自动装配 MUST 在 4 种真实形态下各验证一次：仅本模块的空应用、聚合门面消费者、`maple.framework.enable.eureka-client=false`、显式 `eureka.client.enabled=false`；每种形态 MUST 给出"是否装配 Eureka 客户端 Bean、是否发起注册请求、上下文启动结果"三项定性（对应 F-01）。
- **FR-005**: 关闭能力 MUST 以开关 `maple.framework.enable.eureka-client` 提供，**默认 `true`**（保持既有行为）；开关为 `false` 时 MUST NOT 装配本模块带来的任何 Eureka 客户端装配与默认值，MUST 输出一条 ASCII 日志说明降级来源，MUST 有回归用例证明"关闭后无 Eureka Bean、无框架注入的 `eureka.*` 值、应用正常启动"（对应 F-01）。
- **FR-006**: 库内 `classpath:/application.yml` 的加载与覆盖语义 MUST 用真实类路径组装实测，给出确定结论（谁覆盖谁、是否稳定、框架值被丢弃时是否可见）；修复方案 MUST 把示例配置移出 classpath 根到本模块 `src/main/resources/examples/`（随 jar 分发但不会被自动加载），单/多注册中心各一份，并由模块手册给出复制步骤；MUST NOT 仅以"重命名文件"作为修复（对应 F-02）。
- **FR-007**: 默认值 MUST 逐项核对：`register-with-eureka`、`service-url.defaultZone`、`instance.*`（当前无）与缺失项；修复方向 MUST 为"框架默认不出现任何固定域名、固定地址或固定端口（`eureka700*.com` 全部移除），`register-with-eureka` 不再由框架默认声明；未配置 `defaultZone` 时输出一条 ASCII WARN 指明配置键名"，并与 `docs/leaf-base-eureka-server开发手册.md` 的集群示例口径对齐；MUST 校核是否违反 `AGENTS.md` 第 53 条（对应 F-03、E-18）。
- **FR-008**: 注册中心不可达时的启动行为 MUST 实测并给出唯一定性（正常启动并后台重试 / 启动失败），MUST 记录失败异常文本与触发 Bean；若结论为"启动失败"，修复 MUST 使注册不阻塞应用启动，并有"旧代码失败、新代码通过"的回归用例（对应 F-01、F-03），MUST NOT 以"通常不会失败"类措辞代替实测。
- **FR-009**: 既有测试 MUST 全部执行并记录结果（基线 4 执行 / 1 失败 / BUILD FAILURE，2026-10-22，Windows）；失败原因 MUST 定位到具体断言行与根因（CRLF）；MUST NOT 删除、跳过或 `@Disabled` 绕过任何用例（对应 F-04、F-11）。
- **FR-010**: 回归设计 MUST 以**语义断言**替代 YAML 文本断言：绑定 `Environment` / `EurekaClientConfigBean` 后断言字段值；MUST 在 Windows（CRLF）与 Linux（LF）下结果一致；MUST 不引入新的第三方依赖，MUST 用 JDK 内置 `com.sun.net.httpserver.HttpServer` 或等价手段模拟注册端点，MUST NOT 依赖外网或外部注册中心（对应 F-04、F-07）。
- **FR-011**: 测试覆盖 MUST 补齐至少 4 类真实行为：上下文能加载本模块自动配置（`@SpringBootTest`）、开关关闭/开启两向行为、注册请求确实发往配置的 `defaultZone`（本地模拟端点收到 PUT）、宿主 `application.yml` 覆盖优先（对应 F-07）。
- **FR-012**: 依赖治理 MUST 逐条核对 5 组事实：3 组 `<exclusion>` 与同坐标重声明的净效果、`error_prone_annotations` 的 guava 路径、`httpclient 4.5.14` 与 `httpclient5 5.6.4` 的并存与使用者、`eureka-client 2.0.6` 的 Jackson 2 分支、starter 版本由 BOM 管理；每组 MUST 给出"缺陷 / 冗余 / 无风险"判定与最小修正方案（对应 F-08）。
- **FR-013**: 本模块 jar 内 MUST NOT 再携带任何 `spring.cloud.openfeign.*` 键（当前 3 个）；这些默认值 MUST 由 `leaf-base-feign` 承接，且"仅使用 feign 的应用"行为 MUST 在迁移前后一致（需实测）；若 `leaf-base-feign` 侧评估为不可承接，MUST 退化为"移入 `examples/` 并由手册明示"，MUST NOT 保留在 jar 根（对应 F-09）。
- **FR-014**: 对外契约 MUST 与实现逐条对照并单列发现，至少覆盖：`AGENTS.md` §1 的 `@EnableFeignClients` 与 `@LoadBalanced`/`HttpServiceProxyFactory` 归属、§3 的 Feign 扫描与 `feign.hystrix.enabled` 基线、§5 的"没有现成测试"、`README.md` 模块表描述（是否说明"引入即注册"与服务端行口径一致）、`docs/` 手册缺失、配置元数据缺失（对应 F-06、F-10）。
- **FR-015**: 审查 MUST 明确区分"缺陷"与"既定契约"：至少确认"不自动开启 `@EnableFeignClients`"、"模块保持在聚合门面内"、"开关默认 `true`"、"starter 版本由 BOM 管理"为契约；对既定契约 MUST 要求文档与测试双向对齐，而非计为缺陷（对应 P-02、P-03、Clarifications）。
- **FR-016**: 审查活动 MUST NOT 修改任何生产代码、配置或依赖；审查报告 MUST 独立成文于 `specs/009-eureka-client-module-audit/audit-report.md`，`spec.md` 只保留摘要与该报告的引用，两者交付时 MUST 相互一致。
- **FR-017**: 每条发现 MUST 给出"能在旧代码上失败、在新代码上通过"的回归测试设计（输入、断言点、期望差异）；该设计 MUST 说明在 Windows 与 Linux 双平台的可执行性。
- **FR-018**: 资源与启动成本 MUST 给出可复算口径：引入本模块前后的上下文启动耗时、常驻线程增量（Eureka 心跳/注册线程）、注册相关内存基线；同环境重跑两次偏差 MUST ≤ 30%，MUST NOT 以绝对吞吐或时延数值作为通过门槛。
- **FR-019**: 跨模块影响面 MUST 量化：说明聚合门面 `leaf-framework` 的消费者是否 100% 被动成为 Eureka 客户端（依据：`leaf-framework/pom.xml` 第 80–84 行无条件依赖 + 本模块无开关），并 MUST NOT 在本规格中直接修改 `leaf-framework/pom.xml`。
- **FR-020**: 结论 MUST 包含风险排序依据（影响面 × 触发概率 × 可观测性），使计划阶段可直接确定修复批次；高影响面判定 MUST 说明受影响范围（如"引入聚合门面的全部应用"）。

### Key Entities *(include if data involved)*

- **审查发现（Finding）**：编号、级别、维度、代码位置、触发条件、影响面、证据、复现方式、回归测试设计、修复建议。
- **装配矩阵**：应用形态（仅本模块 / 聚合门面 / 开关关闭 / 显式 `eureka.client.enabled=false`）× 是否装配 Eureka Bean × 是否发起注册 × 启动结果。
- **配置来源矩阵**：配置键（`register-with-eureka`、`service-url.defaultZone`、`spring.cloud.openfeign.circuitbreaker.*`、`maple.framework.enable.eureka-client`）× 来源（框架 jar 内 `application.yml` / 宿主 `application.yml` / 命令行 / 模块代码默认）× 最终生效值 × 是否稳定。
- **依赖矩阵**：坐标（`eureka-client`、`spring-cloud-netflix-eureka-client`、`httpclient`、`httpclient5`、`antlr-runtime`、`error_prone_annotations`、`spring-boot-starter-webflux`、`spring-boot-starter-actuator`）× 解析版本 × 作用域 × 是否可传递 × 判定（缺陷 / 冗余 / 无风险）。
- **可达性记录**：默认域名（3 个）× 解析结果 × 注册请求结果 × 启动结果。
- **契约核对清单**：文档条目（`AGENTS.md` 章节、`README.md` 表格、`docs/` 手册、配置元数据）× 实现现状 × 一致 / 不一致。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 审查覆盖 100% 的 4 个生产交付物与 1 个测试类的 4 个用例，100% 的发现记录在独立报告中；新维护者可在 15 分钟内复现任一结论。
- **SC-002**: 100% 的发现具备"级别 + 位置 + 触发条件 + 影响 + 证据 + 复现方式 + 回归设计"七要素；0 条仅凭阅读的结论混入已确认项。
- **SC-003**: 装配矩阵覆盖 4 种应用形态，100% 给出"是否装配 Eureka Bean / 是否发起注册 / 启动结果"三项定性；受影响应用范围结论可被引用为跨模块任务输入。
- **SC-004**: 配置来源矩阵覆盖"框架 jar + 宿主 jar 同名配置"场景，100% 的键给出"最终生效来源"判定，并明确该判定在 3 次连续启动中是否一致。
- **SC-005**: 3 个默认域名的解析结果与注册表现 100% 有唯一定性；"注册中心不可达时的启动结果"必须为"成功并重试"或"失败"二者之一，不得出现"可能/通常"类措辞。
- **SC-006**: 依赖矩阵覆盖 5 组依赖事实，100% 给出"缺陷 / 冗余 / 无风险"判定；3 组 exclusion 的净效果结论必须有 `-Dverbose` 依赖树佐证。
- **SC-007**: 既有测试执行率 100%（基线 4 执行 / 1 失败），基线结果在报告中保留并可复算；新增回归设计 100% 具备"旧失败、新通过"的期望差异，且 100% 声明为不依赖换行符与外部环境。
- **SC-008**: 审查活动不修改生产代码、不新增第三方依赖、不依赖外部网络或外部注册中心；所有验证可在单机环境完成。
- **SC-009**: 契约核对覆盖 4 类文档/元数据来源（`AGENTS.md`、`README.md`、`docs/`、配置元数据），100% 给出"一致 / 不一致"判定，不一致项 100% 定位到具体章节或行号，且至少识别出 4 处 `AGENTS.md` 与实现相反的描述。
- **SC-010**: 3 条高危装配/配置发现（F-01、F-02、F-03）100% 具备可构造的故障现场与回归测试设计，计划阶段无需补充调研即可拆分为任务。
- **SC-011**: 开关默认 `true` 的兼容性影响 100% 量化：报告中列出"升级后需要显式关闭"的应用清单（当前仓库内结论 + 判定命令），并以回归用例证明"开关 `false` 时无 Eureka Bean 且正常启动""开关 `true` 时注册请求发往配置地址"两个方向各至少 1 个断言通过。
- **SC-012**: 契约与服务端模块口径 100% 对齐：报告 MUST 指出本模块默认 `defaultZone` 与 `docs/leaf-base-eureka-server开发手册.md` 集群示例的所有冲突点，并给出对齐后取值。

## Assumptions

- 目标运行形态为 JDK 21 + Spring Boot 4.1.x + Spring Cloud 2025.1.x（`spring-cloud-netflix 5.0.2`、`spring-cloud-openfeign 5.0.3`）；下游通过 `leaf-framework` 聚合门面或直接依赖引入本模块；ErrorProne + NullAway 保持启用。
- 本模块定位为"客户端注册发现接入模块"，注册中心服务端能力归 `leaf-base-eureka-server`（008 已完成开关与示例化改造）；本规格不评价服务端模块实现，只引用其默认值口径。
- `eureka7001.com` / `eureka7002.com` / `eureka7003.com` 属示例域名，需要 hosts 解析；本规格不把它们当作生产契约，但要求给出"未解析时的实际表现"结论。
- 开关默认 `true`、模块保留在聚合门面内，均为**保持现状**的决策；因此本规格不产生破坏性变更，也不要求下游在升级后必须改配置。
- 本次审查不引入 MockWebServer / Testcontainers / 外部注册中心；验证手段限于 `ApplicationContextRunner`、最小 `@SpringBootTest`、JDK 内置 `HttpServer`、依赖树分析与临时目录类路径组装。
- 审查阶段不修改任何生产代码与配置文件；Clarifications 中的开关、示例化、Feign 键迁移、失败用例处置结论用于约束后续计划阶段的修复设计，不在本次落地。
- 启动耗时与线程数的比较基线为"同机、同 JDK、空应用"；结论以"可复算口径 + 同量级复现"为验收标准，不承诺生产容量。
- 聚合门面 `leaf-framework` 的依赖清单属于跨模块事实，本规格只引用不修改；"是否把本模块移出聚合门面"已被明确判定为独立后续决策（见 Clarifications），不在本次范围。
- `specs/008-eureka-server-module-audit/audit-report.md` 第 208 行登记的跨模块遗留项（本模块库内 `application.yml`）由本规格承接收敛，收敛结论需回写该处引用关系。
