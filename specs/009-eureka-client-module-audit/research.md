# Phase 0 Research — leaf-base-eureka-client 审查与修复设计

**Feature**: `009-eureka-client-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

本文件把 `spec.md` 中标记为"必须实测"的 3 项事实（FR-008、FR-006、FR-013）与全部实现路径决策一次性收敛。**R-01 ~ R-03 为本次真实执行结果**（探针代码运行后已删除，命令与输出片段见 §1），其余为设计决策。

## 1. 实测结论（Phase 0 真实执行）

### R-01 注册中心不可达时的启动行为 → 结论：**正常启动，后台重试**

**执行方式**：在模块 `src/test/java` 下临时新增探针 `ProbeUnreachableRegistryTest`（`@SpringBootTest(webEnvironment=NONE)` + `eureka.client.service-url.defaultZone=http://127.0.0.1:1/eureka/` + `register-with-eureka=true` + `fetch-registry=true`），运行 `mvn -o -pl leaf-base-eureka-client test -Dtest=Probe*`，随后删除探针文件。

**观测结果**：

- 上下文加载成功（`Tests run: 1, Failures: 0, Errors: 0`），**没有** `Failed to start bean 'eurekaAutoServiceRegistration'`。
- 注册与拉取均在后台进行：`foReplicator` 线程执行 `POST /eureka/apps/PROBE-APP`，失败后按 30 秒周期重试；`DiscoveryClient` 输出 `was unable to refresh its cache! This periodic background refresh will be retried in 30 seconds`。
- 日志噪音为 `ERROR`/`WARN` 级 `TransportException: Cannot execute request on any known server`，每次重试各一条。

**Decision**：不新增"注册失败则启动失败"的保护，也不吞掉异常；把"不可达不影响启动 + 持续错误日志 + 4 个常驻线程"作为**已知运行形态**写入报告与手册。

**Rationale**：实测否定了"启动失败"假设（spec F-01/FR-008 的两种可能结论之一），因此 FR-008 的通过标准变为"结论唯一化 + 固定成本可复算"，不需要引入额外的启动保护复杂度（宪法"复杂度必须被证明"）。

**Alternatives considered**：
- 在开关开启且注册失败时 `System.exit` / 抛异常：会破坏"注册中心短暂不可用时服务仍可启动"的容灾语义，拒绝。
- 关闭 Eureka 的错误日志：吞掉可观测性，违反宪法第 IV 条"禁止静默降级"，拒绝。

### R-02 宿主 `application.yml` 与框架 jar 内同名文件的覆盖顺序 → 结论：**宿主胜，框架值被静默丢弃**

**执行方式**：临时新增 `src/test/resources/application.yml`（`register-with-eureka: false`、`defaultZone: http://host-config-wins:9999/eureka/`）与探针 `ProbeConfigPrecedenceTest`，从 `Environment` 读取最终生效值；连跑 **2 次**结果一致。探针与临时资源已删除。

**观测结果**：

- `PROBE_DEFAULT_ZONE=http://host-config-wins:9999/eureka/`、`PROBE_REGISTER=false` → 宿主侧文件胜出。
- 日志中注册/拉取目标为 `http://host-config-wins:9999/eureka/`，**没有任何**请求发往框架默认的 `eureka700*.com`。
- 对照组（无宿主侧文件时，模块自身 `target/classes/application.yml`）才提供框架默认值。

**Decision**：修复必须是"框架 jar 不再提供 `classpath:/application.yml`"，把默认值改为 `src/main/resources/examples/` 下的示例 + 文档；**不**尝试用 `spring.config.import`、`@PropertySource` 或改名来"抢优先级"。

**Rationale**：同名位置的结果取决于类路径顺序，两种结果都不报错；不修复则"框架默认值"在多数应用里根本不存在（等于文档撒谎），在少数应用里又可能覆盖宿主配置。移出配置位置使歧义消失。

**Alternatives considered**：
- 保留但改名为 `application-leaf-eureka-client.yml` 并 `spring.config.import`：仍会被宿主显式 import 顺序影响，且新增隐式加载路径，违反"显式配置"原则。
- 用 `EnvironmentPostProcessor` 注入默认值：可在开关开启时提供"位置无关"的默认值，但 `register-with-eureka`/`defaultZone` 恰恰**不该**由框架默认提供（spec FR-007），因此不采用；该机制只用于开关关闭时的 `eureka.client.enabled=false`（见 D-01）。

### R-03 Feign 熔断默认键的真实生效路径 → 结论：**在 `config/{env}/app-environment.yml`，不在 jar 内**

**执行方式**：`ProbeConfigPrecedenceTest` 读取 `spring.cloud.openfeign.circuitbreaker.enabled`；检索 `config/` 与 `leaf-base-feign`。

**观测结果**：

- 宿主有 `application.yml` 时该键为 `null`（框架 jar 内的 `true` 未生效）。
- `config/dev/app-environment.yml` 第 1–8 行已声明 `spring.cloud.openfeign.circuitbreaker.enabled=true`、`group.enabled=false`、`alphanumeric-ids.enabled=true`（`test`/`pre`/`prod` 同构，见 `specs/007-feign-module-audit/spec.md` E-08）。
- `leaf-base-feign` 侧提供的是 `GXCircuitBreakerConfig`（`FrameworkRetryCircuitBreakerFactory` 定制器，前缀 `maple.framework.feign.circuit-breaker`），**不**负责 OpenFeign 的三个开关键，模块内无 `application.yml`。

**Decision**：本模块直接删除三个键，不改 `leaf-base-feign`、不新增 `EnvironmentPostProcessor`；升级说明写"需要熔断默认值的应用使用环境模板或显式配置"。

**Rationale**：真实生效路径已存在且属"宿主/环境配置"层；把键搬进 `leaf-base-feign` 的 jar 会复刻同一个"库内同名配置"缺陷，并把行为变更扩大到只用 feign 的应用。删除是唯一不扩大影响面的方案。

**Alternatives considered**：
- 由 `leaf-base-feign` 用 `EnvironmentPostProcessor` 提供最低优先级默认值：会让"只用 feign"的应用从"未开启熔断"变为"默认开启"，属静默行为变更，拒绝。
- 留在本模块并文档化：违反职责边界（F-09），且 R-02 已证明其多数场景下不生效，保留只会误导。

## 2. 设计决策

### D-01 开关实现：`@ConditionalOnProperty` + `EnvironmentPostProcessor` 双保险

**Decision**：开关名 `maple.framework.enable.eureka-client`（`Boolean`，默认 `true`）。

1. `GXEurekaClientConfig` 增加 `@ConditionalOnProperty(prefix = "maple.framework.enable", name = "eureka-client", havingValue = "true", matchIfMissing = true)`，使自动装配元数据如实反映条件（当前只有 `ConditionalOnClass`，见 spec E-03）。
2. 新增 `GXEurekaClientDefaultsPostProcessor implements EnvironmentPostProcessor`，在 `META-INF/spring.factories` 注册；读取开关，为 `false` 时以 `addLast` 语义（最低优先级、仅在应用未显式配置时）设置 `eureka.client.enabled=false`，并输出一条 ASCII INFO：`Eureka client disabled via maple.framework.enable.eureka-client=false`；为 `true` 时**不写入任何键**。

**Rationale**：仅靠 `@ConditionalOnProperty` 无法阻止 `EurekaClientAutoConfiguration` 自身的默认行为（默认 `register-with-eureka=true`、`defaultZone=http://localhost:8761/eureka/`）；FR-005 要求"关闭后无 Bean、无残留值"，必须在属性层关闭 `eureka.client.enabled`。该模式与 `specs/008-eureka-server-module-audit` 的 `GXEurekaServerClientDefaultsPostProcessor` 同构，属已被本项目验证过的做法。

**Alternatives considered**：
- 只用 `@ConditionalOnMissingBean` / 排除自动配置：需要在宿主侧配置 `spring.autoconfigure.exclude`，违背"库应自洽"。
- 只用 `EnvironmentPostProcessor`（不加 `@ConditionalOnProperty`）：条件元数据仍不可读，IDE/Actuator 条件报告会失真。

### D-02 删除 jar 根 `application.yml`，改为 `examples/`

**Decision**：删除 `leaf-base-eureka-client/src/main/resources/application.yml`；新增 `examples/eureka-client-default.yml`（单注册中心）与 `examples/eureka-client-multi-registry.yml`（多注册中心），均使用 `${EUREKA_DEFAULT_ZONE:...}` 占位符且**不含真实域名**；回归用例断言 `classpath:/application.yml` 为 `null`（与 008 T029 同构）。

**Rationale**：R-02 已证明同名位置的不可控性；`examples/` 随 jar 分发、不在配置位置根，不会被自动加载，"复制即可用"的获取路径保留。

**Alternatives considered**：
- 只改文件名（如 `eureka-client.yml`）：仍可能被 `spring.config.import` 意外引入，且未解决"默认值从哪来"的语义问题，拒绝。

### D-03 默认值收缩：只保留"与位置无关"的语义

**Decision**：框架默认**不**声明 `eureka.client.register-with-eureka` 与 `eureka.client.service-url.defaultZone`；`GXEurekaClientConfig` 构造注入 `Environment`，当开关为 `true` 且 `eureka.client.service-url[defaultZone]`（含 `.defaultZone` 两种写法）均未配置时，输出**一条** ASCII WARN：`Eureka client default zone is not configured (eureka.client.service-url.defaultZone); registration will target the Spring Cloud default.`。示例配置改为与服务端手册口径一致（`localhost` 占位/CDN 无关），不再出现 `eureka700*.com`。

**Rationale**：spec FR-007 明确"框架默认不得出现固定域名、地址或端口"；WARN 保证"未配置"可被观测（宪法 IV 禁止静默降级）。实测使该 WARN 只在真正的"无配置"场景出现一次。

**Alternatives considered**：
- 保留 `register-with-eureka: true` 作为默认：仍会把"注册"变成隐式行为，且与"无默认地址"组合后指向 `localhost:8761`，更易误解，拒绝。

### D-04 依赖治理：只补注释 + 仅在零影响时删除重复声明

**Decision**：

1. 三组 `<exclusion>`（`antlr-runtime`、`error_prone_annotations`、`httpclient`）保留，补中文注释说明"解决 seata/eureka 冲突 + 版本由根 POM 统一"（对应根 `pom.xml` 第 472–506 行的既有注释）。
2. `error_prone_annotations` 的重声明**仅当** `dependency:tree -Dverbose` 在删除前后完全一致（guava 已引入同版本）时删除；否则保留。
3. `httpclient 4.5.14` 必须保留（`eureka-client 2.0.6` 的 POM 声明 `4.5.3`，排除后需重新锚定版本），并注释说明与 `httpclient5 5.6.4` 的分工。
4. 不新增任何依赖；不动根 POM 的 `dependencyManagement`。

**Rationale**：宪法"不得删除已有的冲突覆盖"；未经验证的删除会改变运行期 classpath。仅做"意图显式化"，风险最低。

**Alternatives considered**：
- 全量删除 exclusion + 重声明：会让 `httpclient` 回落到 4.5.3（与 `httpclient5` 代际更混乱）并使 `antlr-runtime` 版本失去锚点，拒绝。

### D-05 测试改造：语义断言 + 真实上下文 + JDK HttpServer

**Decision**：

| 用例 | 层级 | 断言 |
| --- | --- | --- |
| `autoConfigurationImportContainsEurekaClientConfig` | 单元 | 保留（读 imports，字符集显式 `UTF_8`） |
| `autoConfigurationDoesNotRegisterFeignClientsByItself` | 上下文 | 保留（P-02 契约） |
| `userEnabledFeignClientsKeepSeparateContextConfigurations` | 上下文 | 保留（P-02 契约） |
| `applicationYamlContainsExpectedClientDefaults` | —— | **删除并替换**：改为 `applicationYamlIsAbsentFromClasspathRoot` + `environmentBindsExpectedValues`（绑定 `Environment`，不读文本） |
| `switchOffDisablesEurekaClient` | `@SpringBootTest` | 开关 `false` → 无 `EurekaClient`/`EurekaAutoServiceRegistration` Bean；`eureka.client.enabled=false`；日志含禁用语句 |
| `registrationIsSentToConfiguredDefaultZone` | `@SpringBootTest` + HttpServer | 本地端点收到 `POST /eureka/apps/{APP}`；请求 URL 与配置一致；`register-with-eureka=true` 被显式配置时生效 |
| `hostConfigurationWinsOverExamples` | `@SpringBootTest` | 通过 `properties` 显式设置 `defaultZone` → 生效值等于设置值；`examples/` 文件不被自动加载 |

**Rationale**：spec FR-010/FR-011 要求语义断言、真实行为、无新依赖、双平台一致；JDK `HttpServer` 在本模块可用（`jdk.httpserver` 模块，无需第三方）。

**Alternatives considered**：
- 用 MockWebServer/WireMock：新增依赖，违反 spec Clarifications。
- 用真实 Eureka Server 模块启动：跨模块依赖 + 端口/超时不稳定，拒绝（008 已证明容器级验证成本高）。

### D-06 契约文档收敛

**Decision**：重写 `AGENTS.md` —— 补缺失的 §2（章节编号从 §1 直接跳到 §3，同 008 的 F-14 类问题）、§1 删除"通过 `GXEurekaClientConfig` 启用 `@EnableFeignClients`"与"本模块装配 `@LoadBalanced` WebClient.Builder"的错误表述（改为"由 `leaf-base-webclient` 提供，本模块只保证其自动装配顺序"）、§3 删除 `@EnableFeignClients` 扫描范围基线与 `feign.hystrix.enabled` 基线（后者已不存在）、§5 更新"当前模块没有现成测试"并写入新测试要求。新增 `docs/leaf-base-eureka-client开发手册.md`（定位/开关/配置键/示例/升级必查/故障排查）。更新 `README.md` 模块表行：注明"引入即注册、可用 `maple.framework.enable.eureka-client=false` 关闭、无默认注册地址"。

**Rationale**：spec FR-014 / SC-009；文档漂移是本模块最易导致误用的缺陷（业务方按 §1 会误以为 Feign 扫描自动开启）。

**Alternatives considered**：
- 只改 §1：§3、§5 的错误基线仍会误导后续维护者，拒绝。

### D-07 配置元数据

**Decision**：新增 `META-INF/additional-spring-configuration-metadata.json`，声明 `maple.framework.enable.eureka-client`（`java.lang.Boolean`、`defaultValue=true`、描述指向手册）。不声明 `eureka.*`（属上游）。

**Rationale**：宪法 IV "新增开关同步写入 metadata"；008 已有同构做法。

### D-08 双平台与字符集

**Decision**：所有测试读文件用 `StandardCharsets.UTF_8`；删除任何依赖 `\n` 字面量的断言；`examples/*.yml` 与 `additional-spring-configuration-metadata.json` 统一以 LF 保存并在 `.gitattributes` 现有规则下校验。

**Rationale**：既有唯一失败用例即由 CRLF 触发（spec E-07）；"断言不依赖换行符"是 SC-007 的硬要求。

**Alternatives considered**：
- 把断言改为 `\r\n`：只是把问题搬到另一个平台，拒绝。

### D-09 审查报告结构与证据

**Decision**：`audit-report.md` 与 008 对齐：§1 概览与基线、§2 证据表（E-01 ~ E-27，标注 `SOURCE`/`MEASURED`）、§3 发现（F-01 ~ F-13，七要素）、§4 装配矩阵（含 §4.1 组合矩阵）、§5 配置来源矩阵（含 §5.1 域名可达性、§5.2 feign-only 行为不变）、§6 依赖矩阵、§7 回归设计（含 §7.3 全量发现映射）、§8 契约核对、§9 风险排序、§10 受影响应用清单与升级必查、§11 跨模块遗留、§13 边界场景处置（回写 008 第 208 行引用）。

**Rationale**：spec FR-002/FR-003/FR-016/FR-020；`SOURCE` 与 `MEASURED` 必须可区分（R-02/R-03 已由探针转为 `MEASURED`）。

### D-10 变更边界与回滚

**Decision**：全部改动可通过单次 revert 回滚（新增文件 + 1 个删除文件 + `AGENTS.md`/`README.md` 文本）；不涉及数据库、不涉及跨模块代码。若 `EnvironmentPostProcessor` 在 Servlet/Reactive/NONE 三种上下文出现装配异常，回滚该 PostProcessor 并保留开关的 `@ConditionalOnProperty` 部分（降级为"关闭自动配置类"而不是"关闭客户端"），同时在报告中登记为未解决项。

**Rationale**：宪法 Governance "复杂度必须被证明 + 需给出回滚方式"。
