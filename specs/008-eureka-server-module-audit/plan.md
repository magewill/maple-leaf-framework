# Implementation Plan: leaf-base-eureka-server 装配边界、配置语义与依赖治理审查

**Branch**: `008-eureka-server-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-eureka-server-module-audit/spec.md`

## Summary

本次交付分两层：**审查结论**（独立报告 `audit-report.md`，覆盖 4 个生产交付物、1 个测试类的 2 个用例、单机/集群启动语义实测、依赖树核对）与**修复设计**（按 spec 的 12 项澄清结论，把 5 个高危、6 个中危、3 个低危发现落成"测试先行"的改造方案）。

技术路线：不改 `AGENTS.md` 登记的单机基线（`register-with-eureka=false`、`fetch-registry=false`），核心改造集中在六处：

1. **装配可控**：`GXEurekaServerConfig` 增加 `@ConditionalOnProperty(name = "maple.framework.enable.eureka-server", havingValue = "true")`（默认 `false`）与 `@ConditionalOnWebApplication(type = SERVLET)`；启用时输出一条 ASCII INFO 日志（宪法 IV 装配可观测）。
2. **门面缩面**：`leaf-framework/pom.xml` 移出本模块依赖，门面消费者不再被动成为注册中心；属受控破坏性变更，附迁移清单。
3. **配置分家**：删除 `src/main/resources/application.yml`，改为 `src/main/resources/examples/eureka-server-standalone.yml` 与 `eureka-server-peer.yml`（随 jar 分发、不在 `classpath:/` 根、不会被自动加载）；默认配置只保留单机 `false/false`，不出现固定地址、域名、端口。
4. **依赖收敛**：移除 `spring-boot-devtools`；**保留** `jersey-apache-connector 3.1.12`（实测替换为 apache5 会导致对等复制 `NoClassDefFoundError`，属上游硬约束）；删除 `httpclient` 4.x 的直接声明（依赖保留为传递引入）与对应 `<exclusion>`；删除与根 `dependencyManagement` 重复的 `hk2`/`woodstox-core` 排除-重引入对；删除与 BOM 同值的 `eureka.server.version` 覆盖。
5. **测试补位**：把"字符串包含 + 注解存在性"升级为条件装配矩阵（开关 × 上下文档位 × 类缺失）、真实注册中心启动（随机端口 + `/eureka/apps` 往返）与示例配置存在性断言。
6. **契约对齐**：新增 `docs/leaf-base-eureka-server开发手册.md` 与 `additional-spring-configuration-metadata.json`；修正 `AGENTS.md` 章节编号与"当前模块没有现成测试"的过时表述；更新 `README.md` 模块表。

验证全程零新增第三方依赖：条件装配用 `ApplicationContextRunner`（含 `FilteredClassLoader`），真实启动用 `@SpringBootTest(webEnvironment = RANDOM_PORT)`，依赖治理用 `mvn dependency:tree` 前后对比。

## Technical Context

**Language/Version**: Java 21（`maven.compiler.release=21`）；ErrorProne 2.50.0 + NullAway 0.13.4（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`）为编译期门槛

**Primary Dependencies**: `spring-boot 4.1.1`、`spring-cloud 2025.1.3`（`spring-cloud-netflix 5.0.2` → `spring-cloud-starter-netflix-eureka-server 5.0.2` → `eureka-client-jersey3/eureka-core-jersey3 2.0.6`、`jersey-server/jersey-container-servlet/jersey-client 4.0.2`、`jersey-hk2 4.0.3`、`spring-boot-starter-web` + `tomcat-embed-core 11.0.24`、`spring-boot-starter-actuator`）、`jspecify`；根 POM 已集中管理 `hk2 4.0.3`、`woodstox-core 7.3.0`、`httpclient 4.5.14`、`jersey-apache-connector 3.1.12`（**该坐标无 4.x 版本**，`leaf-base-manticore` 仍在用）、`jersey-hk2 4.0.3`；Jersey 4 的 HttpClient 连接器为 `jersey-apache5-connector`（4.0.2 可用，依赖 `httpclient5`）

**Storage**: N/A（注册表在内存中维护，节点间通过 HTTP 复制；模块自身无持久化职责）

**Testing**: JUnit 5（Jupiter）+ `spring-boot-starter-test` + AssertJ；条件装配用 `ApplicationContextRunner` + `FilteredClassLoader`；真实注册中心用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + JDK `java.net.http.HttpClient` 调 `/eureka/apps`；依赖治理用 `mvn -pl leaf-base-eureka-server dependency:tree` 前后 diff；不引入 MockWebServer/Testcontainers

**Target Platform**: JVM 库（被 Spring Boot 4.1.x 应用以 Maven 依赖引入）；修复后聚合门面 `leaf-framework` 不再传递本模块，注册中心部署方需直接依赖并显式开启开关

**Project Type**: library（多模块 Maven 工程中的 `leaf-base-eureka-server` 单一模块 + 已授权的 `leaf-framework/pom.xml` 一条依赖声明 + 根 POM 的版本治理条目）

**Performance Goals**: 结构口径为主——引入本模块前后上下文启动耗时、常驻线程数增量、注册中心相关内存基线可复算；同环境重跑两次偏差 ≤ 30%；不设绝对吞吐/时延门禁

**Constraints**: 单机默认 `register-with-eureka=false` / `fetch-registry=false` 不变（`AGENTS.md` 基线）；开关默认 `false` 与门面移除属受控破坏性变更，需 PR 说明 + 手册迁移章节；默认配置不得含固定 IP、固定域名、固定端口；不新增第三方依赖；ErrorProne/NullAway 零错误；改动范围限于本模块 + `leaf-framework/pom.xml` 该条依赖 + `docs/`/`README.md`/`AGENTS.md`（根 POM 最终**无改动**，因 apache5 方案被实测否决）

**Scale/Scope**: 4 个生产交付物（2 个 Java、2 个资源）、1 个测试类（2 个用例）、14 条发现（5 高 / 6 中 / 3 低）；预计改动与新增文件 ≤ 16

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
| --- | --- | --- |
| I 模块自治与按需装配 | PASS（改善） | 从聚合门面移出本模块正是"模块自治"的收敛；不新增模块、不改依赖方向；开关与 `@ConditionalOnWebApplication` 让能力可降级 |
| II 稳定契约优先 **NON-NEGOTIABLE** | PASS（含 1 项受控行为变更） | `GXEurekaServerConfig` 类名、包名、`AutoConfiguration.imports` 入口、`@EnableEurekaServer` 语义不变；单机 `false/false` 默认值不变。**受控变更**：开关默认 `false` + 门面不再传递本模块，属显式声明的破坏性变更，需在 PR、手册迁移章节与 `AGENTS.md` 中给出"旧行为 / 新行为 / 影响面 / 迁移步骤" |
| III 统一响应与统一异常 | PASS | 本模块无 HTTP 业务接口与异常类型变更；Eureka 原生端点（`/eureka/apps`）由其自身协议负责，不引入裸返回值 |
| IV 显式配置与双配置源 | PASS | 新增开关 `maple.framework.enable.eureka-server` 遵循 `maple.framework.enable.<feature>` 命名空间、默认关闭、补 `additional-spring-configuration-metadata.json`；本模块无 `properties/local|nacos` 结构，不强行引入双配置源骨架；装配过程输出明确 ASCII 日志 |
| V 空值安全与上下文纪律 | PASS | 不新增公共可空 API；新增/修改类型纳入 `cn.maple` 包（NullAway 已覆盖）；示例配置不引入运行期上下文 |
| VI 测试与静态检查门槛 | PASS | 每条缺陷附"旧代码失败、新代码通过"的回归用例；真实启动用例使用随机端口、可在无外部注册中心环境运行；`mvn -pl leaf-base-eureka-server test` 与 ErrorProne/NullAway 零错误为完成门槛 |
| VII 命名与包结构一致性 | PASS | 包 `cn.maple.eureka.server.config` 不变；示例资源目录 `examples/` 属配置资源，不涉及类命名；新增文档命名沿用 `docs/<模块名>开发手册.md` |
| 附加约束 2 依赖治理 | PASS（收敛） | 删除模块内与根 `dependencyManagement` 重复的版本与排除-重引入对；保留 `jersey-apache-connector 3.1.12`（实测为上游硬约束）；不删除任何"踩坑沉淀"的冲突覆盖（仅删除本模块内的重复声明），`httpclient` 4.5.14 的覆盖声明保留在根 POM 供其它模块使用；根 POM 最终零改动 |
| 附加约束 3 不新增依赖 | PASS | 测试与验证均使用既有依赖与 JDK 能力 |

**Gate 结论**：无未豁免的违反项，无需 Complexity Tracking 例外。唯一需跟踪项为"开关默认 `false` + 门面移除"的受控破坏性变更，已在 Constitution Check II 中说明理由、影响面与迁移要求。

**Phase 1 复查结果（2026-10-22）**：核对 `research.md` / `data-model.md` / `contracts/*` 后确认——未新增第三方依赖；未新增公开类（仅新增注解条件与开关）；新增配置键 1 个（`maple.framework.enable.eureka-server`，默认 `false`，带元数据）；删除 1 个 classpath 根配置资源、新增 2 个示例资源；改动文件落在本模块、`leaf-framework/pom.xml`、根 POM 治理条目与文档内。**9 条门禁保持 PASS。**

## Project Structure

### Documentation (this feature)

```text
specs/008-eureka-server-module-audit/
├── plan.md              # 本文件
├── spec.md              # 规格（已澄清 12 项）
├── research.md          # Phase 0：技术决策与备选方案
├── data-model.md        # Phase 1：审查产物模型与配置模型
├── quickstart.md        # Phase 1：端到端验证指南
├── contracts/           # Phase 1：装配 / 配置 / 依赖契约
├── checklists/
│   └── requirements.md  # 规格质量清单
└── audit-report.md      # 实施阶段产出：独立审查报告（本命令不创建）
```

### Source Code (repository root)

```text
leaf-base-eureka-server/
├── src/main/java/cn/maple/eureka/server/config/
│   ├── GXEurekaServerConfig.java          # 增加 @ConditionalOnProperty(默认 false) + @ConditionalOnWebApplication(SERVLET) + 启用日志
│   └── package-info.java                  # 不变（@NullUnmarked）
├── src/main/resources/
│   ├── application.yml                    # 删除（移出 classpath 根）
│   ├── examples/
│   │   ├── eureka-server-standalone.yml   # 新增：单机示例（false/false，无固定地址/主机名/端口）
│   │   └── eureka-server-peer.yml         # 新增：集群示例（true/true + 占位符 defaultZone + 对端端口）
│   └── META-INF/
│       ├── additional-spring-configuration-metadata.json               # 新增：开关元数据
│       └── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports  # 不变
├── src/test/java/cn/maple/eureka/server/config/
│   ├── GXEurekaServerConfigTest.java      # 改造：开关矩阵 + Web 类型契约 + 类缺失降级 + imports
│   ├── GXEurekaServerExamplesTest.java    # 新增：示例资源存在性/键值 + classpath 根已无 application.yml
│   └── GXEurekaServerSpringBootTest.java  # 新增：@SpringBootTest 随机端口启动注册中心 + /eureka/apps 往返
├── pom.xml                                # 移除 devtools、hk2/woodstox/httpclient 重复声明、eureka.server.version；保留 jersey-apache-connector 3.1.12
└── AGENTS.md                              # 修正章节编号、测试现状、开关与默认值基线

leaf-framework/pom.xml                     # 已授权：移出 leaf-base-eureka-server 依赖
pom.xml                                    # 最终无改动（apache5 替换方案被实测否决）
docs/leaf-base-eureka-server开发手册.md      # 新增（宪法 I 要求的交付集）
README.md                                  # 模块表：补充"需显式开启"与不再随门面传递
```

**Structure Decision**: 沿用现有单模块结构与 `cn.maple.eureka.server.config` 单一包；新增 `src/main/resources/examples/` 资源目录、`additional-spring-configuration-metadata.json` 与两个测试类，不新增生产包、不新增子模块。

## Phase 0 研究结论（详见 research.md）

| 编号 | 决策 | 关键理由 |
| --- | --- | --- |
| D-01 | 开关 `maple.framework.enable.eureka-server`，`@ConditionalOnProperty(havingValue="true", matchIfMissing=false)`，默认关闭 | 宪法 IV 要求开关默认关闭；Eureka Server 是部署角色而非库能力，必须显式启用 |
| D-02 | 同时加 `@ConditionalOnWebApplication(type = SERVLET)` | 注册中心依赖 Servlet 容器与 Jersey Servlet；无此守卫时 WebFlux 应用会以"缺 WebServerFactory"等间接错误失败 |
| D-03 | `leaf-framework/pom.xml` 移出本模块依赖 | 影响面从"所有门面消费者"收缩到"显式引入者"；比在门面里加排除或 optional 更直观 |
| D-04 | 删除 `src/main/resources/application.yml`，示例移入 `examples/`（standalone + peer） | 同名配置位置会与宿主 `application.yml` 竞争；`examples/` 不在配置位置根、不会被自动加载，仍可随 jar 取用 |
| D-05 | 默认配置只保留 `register-with-eureka=false` / `fetch-registry=false`；IP/hostname/defaultZone/port 一律不设 | `AGENTS.md` 第 51 条禁止固化本地域名/私有地址；环回地址与固定端口在任何真实部署下都需覆盖 |
| D-06 | 移除 `spring-boot-devtools` | 库模块传递开发期工具会让下游被动具备自动重启/远程调试入口；本模块源码零引用 |
| D-07 | **保留** `jersey-apache-connector 3.1.12`（不做代际替换），并在 `AGENTS.md`/手册中登记为禁止改动项 | 实测：替换为 `jersey-apache5-connector 4.0.2` 后双节点用例报 `NoClassDefFoundError: ApacheConnectorProvider`——`eureka-core-jersey3` 的复制客户端硬编码 Jersey 3 连接器；混合代际属上游约束而非本模块缺陷 |
| D-08 | 删除 `hk2-api`/`hk2-utils`/`woodstox-core`/`httpclient` 的排除-重引入对（保留 `jersey-apache-connector` 的排除+显式依赖） | 根 `dependencyManagement` 已固定这些版本；实测 `dependency:tree` 仅 `spring-boot-devtools` 消失，其余坐标版本不变 |
| D-14 | 新增 `GXEurekaServerClientDefaultsPostProcessor`：应用不是"Servlet 注册中心"（开关非 `true`，或开关为 `true` 但应用非 Servlet）且未显式配置 `eureka.client.enabled` 时，以最低优先级默认 `false` | 实测：移除 jar 内 `application.yml` 后，仅引入本模块的应用会因 starter 自带的客户端自动注册在注册中心不可达时**启动失败**；且开关为 `true` 的 WebFlux 应用同样会失败（E-20），故判定条件必须包含 Web 类型 |
| D-09 | 删除模块内 `<eureka.server.version>` 覆盖 | `spring-cloud-dependencies 2025.1.3` 已管理 `spring-cloud-netflix.version=5.0.2`；保留会造成 BOM 升级后钉死旧版 |
| D-10 | 开关启用时输出一条 ASCII INFO 日志（含开关名与监听端口占位） | 宪法 IV"装配过程输出明确日志、禁止静默降级"；日志用 ASCII 以满足 `AGENTS.md` 第 7 条 |
| D-11 | 测试升级为四类：条件装配矩阵（`ApplicationContextRunner`）、类缺失降级（`FilteredClassLoader`）、真实启动往返（`@SpringBootTest` 随机端口 + `/eureka/apps`）、资源契约（示例文件与 classpath 根断言） | 现有 2 个用例只断言字符串与注解存在性，无法暴露"引入即启用""开关无效"等问题 |
| D-12 | WebFlux 场景的验证降级为"注解契约断言 + 类路径缺失降级断言"，不新增 `leaf-base-webclient`/`spring-webflux` 依赖；真实 WebFlux 启动冒烟写入 quickstart 供消费方工程执行 | 宪法附加约束 3 禁止为新测试引入依赖；用注解契约与 `@ConditionalOnWebApplication` 语义断言可在零依赖下锁定该行为，残余风险以手册步骤补偿 |
| D-13 | 契约文档：`AGENTS.md` 补第 2 节、改写测试现状、登记开关与迁移；新增 `docs/leaf-base-eureka-server开发手册.md`；`README.md` 模块表补"需显式开启" | 宪法 I 要求模块交付集含手册；契约漂移必须双向对齐，不只改一侧 |

## Phase 1 设计产物

- [research.md](./research.md)：13 条决策（决策 / 理由 / 备选方案 / 影响面 / 验证方式）。
- [data-model.md](./data-model.md)：审查发现模型、启动语义矩阵、装配矩阵、依赖矩阵、配置键模型。
- [contracts/assembly-contract.md](./contracts/assembly-contract.md)：开关 × 上下文档位 × 依赖形态 → 是否启用注册中心。
- [contracts/configuration-contract.md](./contracts/configuration-contract.md)：默认配置、示例配置（standalone/peer）、开关默认值与迁移说明。
- [contracts/dependency-contract.md](./contracts/dependency-contract.md)：允许/禁止传递的依赖清单与验收断言。
- [quickstart.md](./quickstart.md)：基线复现、装配矩阵、真实启动、依赖治理、性能口径五类验证命令与期望结果。

## 实施顺序（供 /speckit.tasks 使用）

1. **基线固化**（无生产改动）：跑 `mvn -pl leaf-base-eureka-server test` 记录 2/0 基线；保存 `dependency:tree` 快照；产出 `audit-report.md` 骨架与证据索引（E-01 ~ E-17）。
2. **启动语义实测（F-02 / F-03 / F-09）**：单机与集群两种形态各启动一次，记录 5 个配置键 × 2 形态的应取值/实际取值，产出集群对等复制结论。
3. **依赖治理（F-04 / F-06 / F-07 / F-08）**：先保存 `dependency:tree` 快照作为比对基线，再按 D-06~D-09 精简 `pom.xml`；`diff` 仅允许 `spring-boot-devtools` 消失（其余坐标为直接声明→传递引入的结构变化）。
4. **装配收敛（F-01 / FR-016 / SC-011）**：加开关与 Web 类型条件、加启用日志、补 `additional-spring-configuration-metadata.json`；补条件装配矩阵用例（开关 false/true × SERVLET 契约 × 类缺失）。
5. **配置分家（F-05 / F-09）**：删除 `application.yml`，新增 `examples/eureka-server-standalone.yml` 与 `eureka-server-peer.yml`；补资源契约用例（classpath 根无 `application.yml`；示例键值正确）。
6. **真实启动验证（FR-004 / SC-003 / SC-004）**：`GXEurekaServerSpringBootTest` 以随机端口启动注册中心，注册一个实例并查询 `/eureka/apps`；记录启动耗时与常驻线程数。
7. **门面与文档（F-11 / FR-015）**：`leaf-framework/pom.xml` 移出本模块；新增模块手册（含迁移章节与 quickstart 步骤）；修正 `AGENTS.md` 编号与测试现状；更新 `README.md`。
8. **报告收口（FR-012 / FR-013）**：`audit-report.md` 与 `spec.md` 摘要一致性校验；逐条回填"旧失败/新通过"回归证据；标注 D-12 的验证残余风险。

## Complexity Tracking

无宪法违反项，无需豁免记录。受控破坏性变更 1 项（开关默认 `false` + 门面不再传递本模块、默认配置不再内置地址）已在 Constitution Check II 中说明理由与迁移要求；跨模块改动实际仅 1 处（`leaf-framework/pom.xml`），根 POM 因 apache5 方案被实测否决而保持原样。
