---

description: "Task list for leaf-base-eureka-client audit & hardening"
---

# Tasks: leaf-base-eureka-client 装配边界、默认配置与契约一致性审查

**Input**: Design documents from `/specs/009-eureka-client-module-audit/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本特性**必须**产出测试（spec FR-010 / FR-011）。既有 4 个用例中 1 个在 Windows 上失败，故测试任务按"先红后绿"执行：先写/改写会失败的断言，再改实现。

**Organization**: 按 spec 的 6 个用户故事（US1~US6）分阶段；US1~US4 为 P1，US5 为 P2，US6 为 P3。

**Status**: 40/40 已完成（2026-10-22）。结果与偏差见 [audit-report.md](./audit-report.md) §12。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1~US6）；Setup / Foundational / Polish 阶段不带故事标签
- 每个任务都给出精确文件路径；完成时须把证据回填 `audit-report.md` 对应章节

## Path Conventions

- 生产代码：`leaf-base-eureka-client/src/main/java/cn/maple/eureka/client/...`
- 资源：`leaf-base-eureka-client/src/main/resources/...`
- 测试：`leaf-base-eureka-client/src/test/java/cn/maple/eureka/client/...`
- 报告与设计：`specs/009-eureka-client-module-audit/`

---

## Phase 1: Setup（基线与快照）

**Purpose**: 冻结"改前"事实，使后续每条结论都可对比、可复算。

- [X] T001 创建 `specs/009-eureka-client-module-audit/audit-report.md` 骨架：§1 概览与基线、§2 证据表（E-01~E-24 占位）、§3 发现（F-01~F-13）、§4 装配矩阵、§5 配置来源矩阵、§6 依赖矩阵、§7 回归设计、§8 契约核对、§9 风险排序、§10 受影响清单与升级必查、§11 跨模块遗留
- [X] T002 运行 `mvn -o -pl leaf-base-eureka-client test`，把结果（`Tests run: 4, Failures: 1`、失败用例 `GXEurekaClientConfigTest.applicationYamlContainsExpectedClientDefaults:82`、CRLF 根因、时间戳）写入 `audit-report.md` §1
- [X] T003 快照依赖树：`mvn -o -pl leaf-base-eureka-client dependency:tree "-DoutputFile=target/deptree-before.txt"` 与 `dependency:tree "-Dverbose=true" "-DoutputFile=target/deptree-verbose-before.txt"`，把命令与摘要（`eureka-client 2.0.6`、`httpclient 4.5.14`、`httpclient5 5.6.4`、`antlr-runtime 3.5.3`、`error_prone_annotations 2.50.0`）写入 `audit-report.md` §6 预备
- [X] T004 [P] 复核换行与编码现状：列出 `leaf-base-eureka-client` 下所有资源文件的 CRLF/LF 状态与 `.gitattributes` 规则，把"平台一致性基线"写入 `audit-report.md` §1（对应 F-04、F-11）

---

## Phase 2: Foundational（阻塞项）

**Purpose**: 红基线转绿的前提 + 所有故事共用的测试基础设施。

**⚠️ CRITICAL**: 未完成本阶段，任何用户故事都无法判定"改对了没有"（当前 `mvn test` 为红）。

- [X] T005 改写红用例：在 `leaf-base-eureka-client/src/test/java/cn/maple/eureka/client/config/GXEurekaClientConfigTest.java` 中删除 `applicationYamlContainsExpectedClientDefaults` 的 YAML 文本断言，替换为 `applicationYamlIsAbsentFromClasspathRoot`（断言 `getClass().getClassLoader().getResourceAsStream("application.yml")` 为 `null`）；两处 `new String(...)` 显式指定 `StandardCharsets.UTF_8`；运行确认新断言**此刻失败**（因资源仍存在），记录为 RC-01 的"旧行为"
- [X] T006 [P] 新增测试夹具 `leaf-base-eureka-client/src/test/java/cn/maple/eureka/client/support/GXTestRegistrationServer.java`：基于 JDK `com.sun.net.httpserver.HttpServer` 绑定随机端口，记录收到的 `method` + `path`，对注册请求返回 204，提供 `baseUrl()` 与 `awaitRegistration(appName)`（超时 ≤ 5s，不引入第三方依赖）
- [X] T007 [P] 补 test 侧包级空值策略：新增 `.../testapp/package-info.java` 与 `.../support/package-info.java`，标注 `@org.jspecify.annotations.NullUnmarked`（`cn.maple.eureka.client.config` 在 main 侧已有声明，test 侧重复会产生重复 FQN，故不新增 —— 见报告 §12.1）
- [X] T008 回填 `audit-report.md` §2 证据表：E-01~E-20 逐条标注 `type`（`SOURCE`/`MEASURED`）、`locator`、`excerpt`、`collectedAt`；新增 E-21（开关关闭线程实测）、E-22（R-01 不可达启动实测）、E-23（R-02 覆盖顺序实测）、E-24（R-03 熔断键生效路径实测），写明探针命令与"探针文件已删除"

---

## Phase 3: User Story 1 - 引入即注册的影响面可判定，且可以关闭 (Priority: P1)

**Goal**: 提供 `maple.framework.enable.eureka-client` 开关（默认 `true`），关闭后无 Eureka Bean、无注册、无残留 `eureka.*` 值，并输出一条 ASCII 日志；同时产出"引入即注册"的 4 形态装配矩阵。

**Independent Test**: 用 `@SpringBootTest(webEnvironment=NONE)` 分别以开关 `false` / `true` / 显式 `eureka.client.enabled=false` 启动，断言 Bean 是否存在、`Environment` 值与日志；不依赖任何其它用户故事。

**契约**: [assembly-contract.md](./contracts/assembly-contract.md) §1、§2、§3、§4

- [X] T009 [US1] 新增 `leaf-base-eureka-client/src/test/java/cn/maple/eureka/client/config/GXEurekaClientSwitchTest.java`（先红）：`switchOffDisablesEurekaClient`、`explicitClientDisabledWinsOverMissingSwitch`、`switchMissingKeepsUpstreamBehaviour`
- [X] T010 [US1] 新增 `.../config/GXEurekaClientDefaultsPostProcessor.java`：实现 `EnvironmentPostProcessor`，开关为 `false` 时以最低优先级（仅当缺失）写入 `eureka.client.enabled=false` 并输出一条 ASCII INFO；开关为 `true` 时不写入任何键（日志经 `DeferredLogFactory` 输出）
- [X] T011 [US1] 新增 `.../resources/META-INF/spring.factories`，注册 `org.springframework.boot.EnvironmentPostProcessor=cn.maple.eureka.client.config.GXEurekaClientDefaultsPostProcessor`
- [X] T012 [US1] 在 `.../config/GXEurekaClientConfig.java` 增加 `@ConditionalOnProperty(prefix = "maple.framework.enable", name = "eureka-client", havingValue = "true", matchIfMissing = true)`，保持 `@AutoConfiguration(after = ...)`、`@ConditionalOnClass(...)`、空 Bean 体与"不开启 `@EnableFeignClients`"契约
- [X] T013 [US1] 新增 `.../resources/META-INF/additional-spring-configuration-metadata.json`，仅声明 `maple.framework.enable.eureka-client`（`java.lang.Boolean`、`defaultValue=true`），不得声明 `eureka.*`
- [X] T014 [US1] 产出装配矩阵写入 `audit-report.md` §4：4 形态 × `webType` × `eurekaBeans` / `registrationAttempted` / `startupResult` / `threads`
- [X] T015 [US1] 逐条核对 `assembly-contract.md` §1 真值表、§2 Bean 契约、§3 日志契约、§4 条件元数据（`ConditionalOnProperty=` 已出现在 `target/classes/META-INF/spring-autoconfigure-metadata.properties`）

---

## Phase 4: User Story 2 - 默认配置语义与覆盖风险可判定 (Priority: P1)

**Goal**: 消除 jar 根 `classpath:/application.yml` 与宿主的同名冲突；框架不再提供 `register-with-eureka`/`defaultZone` 固定值；示例配置移入 `examples/`；未配置 `defaultZone` 时输出一条 ASCII WARN。

**Independent Test**: `process-resources` 后断言 `target/classes/application.yml` 不存在、`examples/` 存在两份示例；以"无 `eureka.*` 配置"启动断言出现 WARN；以显式 `defaultZone` 启动断言绑定正确。

**契约**: [configuration-contract.md](./contracts/configuration-contract.md) §1、§2、§3

- [X] T016 [US2] 删除 `src/main/resources/application.yml`；新增 `examples/eureka-client-default.yml` 与 `examples/eureka-client-multi-registry.yml`（占位符 `EUREKA_DEFAULT_ZONE` / `EUREKA_REGISTRY_URLS`，无 `eureka700*.com`、无固定 IP/端口/凭据）
- [X] T017 [US2] 在 `GXEurekaClientConfig.java` 构造注入 `Environment`，开关为 `true` 且未配置 `defaultZone` 时输出**一条** ASCII WARN；检测逻辑抽为包级 `isDefaultZoneConfigured(Environment)` 以便确定性单测
- [X] T018 [US2] 校验产物无语义残留：`target/classes` 无 `application.yml`、`eureka700` 命中 0、`circuitbreaker` 命中 0、`register-with-eureka` 仅命中 `examples/`，结论写入 `audit-report.md` §5
- [X] T019 [US2] 新增/改写用例：`applicationYamlIsAbsentFromClasspathRoot`、`exampleConfigsAreShippedOutsideTheConfigRoot`、`frameworkProvidesNoRegistrationDefaults`、`warnsWhenDefaultZoneIsMissing`、`defaultZoneDetectionFollowsConfiguration`
- [X] T020 [US2] 产出配置来源矩阵写入 `audit-report.md` §5（含改前 E-23 实测结论与改后 3 次启动稳定性）

---

## Phase 5: User Story 3 - 依赖治理可判定 (Priority: P1)

**Goal**: 让三组 `<exclusion>` + 同坐标重声明的意图显式化；确认 `httpclient 4.5.14` 与 `httpclient5 5.6.4` 的分工；产出依赖矩阵与"可删除项"结论。

**Independent Test**: 修改前后 `dependency:tree` 逐行 diff（本次实测：两棵树均 198 行且完全一致）；`httpclient`/`httpclient5`/`eureka-client` 版本不变。

**契约**: [dependency-contract.md](./contracts/dependency-contract.md) §1、§3、§4

- [X] T021 [US3] 在 `leaf-base-eureka-client/pom.xml` 为 `antlr-runtime`、`error_prone_annotations`、`httpclient` 三组"排除 + 重声明"补中文注释（seata/eureka 冲突、版本由根 POM 统一、`httpclient` 4.x 与 `httpclient5` 分工）
- [X] T022 [US3] 验证 `error_prone_annotations` 重声明可否删除：实测删除后仍由 guava 传递提供 `2.50.0`，但 `dependency:tree` 的 provider 行变化（非零差异）→ 按契约**保留**重声明并加注释
- [X] T023 [US3] 产出依赖矩阵写入 `audit-report.md` §6（`eureka-client`、`spring-cloud-netflix-eureka-client`、`httpclient`、`httpclient5`、`antlr-runtime`、`error_prone_annotations`、`spring-boot-jackson2`、`xstream`、`netflix-eventbus`）
- [X] T024 [US3] 核对 `dependency-contract.md` §1 允许集合、§3 判定表、§5 禁止项，结论写入 `audit-report.md` §8 依赖条目

---

## Phase 6: User Story 4 - 既有测试基线可复算，并转为可信回归 (Priority: P1)

**Goal**: 补齐真实行为验证（注册请求真实发出）、双平台一致，并把旧-新差异固化为 RC 表。

**Independent Test**: Windows（CRLF）与 LF 归一化后各跑一次全量测试，用例数与通过数完全相同；本地 HttpServer 确实收到 `POST /eureka/apps/IT-APP`。

**契约**: [assembly-contract.md](./contracts/assembly-contract.md) §1（注册行为列）、plan D-05

- [X] T025 [US4] 新增 `.../config/GXEurekaClientRegistrationTest.java`：`@SpringBootTest(webEnvironment = NONE)` + `@DynamicPropertySource` 指向 `GXTestRegistrationServer`，断言收到 `POST /eureka/apps/IT-APP` 且 `Environment` 值与设置一致
- [X] T026 [US4] 双平台一致性验证：Windows（CRLF）12/0；全部模块源文件转 LF 后 12/0；结论写入 `audit-report.md` §7.1
- [X] T027 [US4] 在 `audit-report.md` §7 补全回归设计表 RC-01~RC-09（`findingRef`、`layer`、`className`、`assertion`、`oldBehavior`、`newBehavior`、平台结论）

---

## Phase 7: User Story 5 - 契约文档与实现一致 (Priority: P2)

**Goal**: 文档不再与实现相反；补齐模块手册与可发现性。

**Independent Test**: 逐条比对 `AGENTS.md`、`README.md`、`docs/`、配置元数据，输出一致/不一致清单（`AGENTS.md` 6 处历史不一致已修正）。

- [X] T028 [P] [US5] 重写 `leaf-base-eureka-client/AGENTS.md`：补 `## 2. 装配开关与装配契约`、修正 §1「启用 `@EnableFeignClients`」与「本模块装配 `@LoadBalanced` Bean」、删除 §3 的扫描范围与 `feign.hystrix.enabled` 基线、更新 §5 测试现状
- [X] T029 [P] [US5] 新增 `docs/leaf-base-eureka-client开发手册.md`（定位、开关、配置项、示例、覆盖顺序、熔断键迁移、升级必查、故障排查、验证命令）
- [X] T030 [P] [US5] 更新 `README.md` 模块表本模块行：注明"引入即注册（无默认注册地址）、可用 `maple.framework.enable.eureka-client=false` 关闭"
- [X] T031 [US5] 产出契约核对表写入 `audit-report.md` §8（`AGENTS.md` 6 条、`README.md` 1 条、`docs/` 手册、配置元数据）

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 报告可直接被计划/评审消费：风险排序有依据、受影响应用清单可执行、跨模块遗留有回写。

**Independent Test**: 抽取任一 F-xx，报告中具备七要素与 RC 编号；`spec.md` 与报告结论无矛盾。

- [X] T032 [US6] 在 `audit-report.md` §9 给出风险排序（影响面 × 触发概率 × 可观测性）与受影响范围
- [X] T033 [US6] 在 `audit-report.md` §10 给出受影响应用清单、判定命令、三类应用迁移动作表、升级必查配置键，并与模块手册 §7 互引
- [X] T034 [US6] 在 `audit-report.md` §11 回写 `specs/008-eureka-server-module-audit/audit-report.md:208` 的跨模块遗留并声明已收敛
- [X] T035 [US6] 终检 `spec.md` 与 `audit-report.md` 一致性（13 条发现编号/级别、E-01~E-24 引用、SC-001~SC-012 证据），不一致处改报告

---

## Phase 9: Polish（跨故事收尾）

**Purpose**: 门禁、跨模块回归、边界校验、验证指南实跑。

- [X] T036 构建门禁：`mvn -o -pl leaf-base-eureka-client clean install -DskipTests` → `BUILD SUCCESS`，无 ErrorProne/NullAway 诊断
- [X] T037 模块测试全绿：`mvn -o -pl leaf-base-eureka-client test` → `Tests run: 12, Failures: 0`；无 `@Disabled`、旧用例为改写而非删除
- [X] T038 跨模块回归：`mvn -o -pl leaf-base-eureka-server test` → `Tests run: 23, Failures: 0`
- [X] T039 变更边界校验：`git status --porcelain` 仅本模块 + `docs/leaf-base-eureka-client开发手册.md` + `README.md` + `specs/009-eureka-client-module-audit/`
- [X] T040 逐条实跑 [quickstart.md](./quickstart.md) §1~§10，偏差写入 `audit-report.md` §12.1

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖；T004 可与 T001~T003 并行
- **Foundational (Phase 2)**: 依赖 Phase 1；**阻塞所有用户故事**
- **US1 / US2 / US3 / US4**: 均只依赖 Foundational，可并行；T014/T020 的实测需要 T016 已完成
- **US5**: 依赖 US1 + US2（手册需写开关与"已移除的默认值"）
- **US6**: 依赖 US1~US5
- **Polish**: 依赖全部

### Story Completion Order（实际执行）

```text
Setup → Foundational → US1 → US2 → US3 → US4 → US5 → US6 → Polish
```

### Parallel Opportunities

- T004 与 T001~T003 并行
- T006 与 T007 并行
- T028 / T029 / T030 三者并行
- T021（pom 注释）与 T029（手册）可并行；T022 必须在 T021 之后

---

## Parallel Example: User Story 5

```text
Task: "重写 leaf-base-eureka-client/AGENTS.md（补 §2，修正 §1/§3/§5）"
Task: "新增 docs/leaf-base-eureka-client开发手册.md"
Task: "更新 README.md 模块表本模块行"
```

## Parallel Example: Foundational

```text
Task: "新增 GXTestRegistrationServer（JDK HttpServer 夹具）"
Task: "补 test 侧 package-info.java"
```

---

## Implementation Strategy

### MVP First（Foundational + US1 + US2）

1. Phase 1 Setup（冻结基线）
2. Phase 2 Foundational（红基线转绿 + 测试夹具 + 证据表）
3. Phase 3 US1（开关 + PostProcessor + 元数据 + 装配矩阵）
4. Phase 4 US2（示例化 + 无默认值 + 告警）→ 此时模块测试首次全绿
5. **STOP AND VALIDATE**：`/quickstart.md` §3、§5 单独跑通

### Incremental Delivery

1. Foundational → 测试可判定
2. US1 → 开关可用（可关闭注册）
3. US2 → 配置去风险（jar 内无 `application.yml`、无固定地址）
4. US4 → 注册行为被真实验证（HttpServer）
5. US3 → 依赖意图显式化（树零变化）
6. US5 → 文档与手册对齐
7. US6 → 报告可驱动后续排期
8. Polish → 门禁与边界校验

---

## Notes

- 本特性**未改** `leaf-framework`、`leaf-base-feign`、`leaf-base-webclient`、根 `pom.xml`；跨模块只有新增手册与改 README 一行（T039 校验）
- 既有失败用例为"改写"而非删除（T005、T037）
- 报告中的 `MEASURED` 证据均带命令与时间；纯阅读结论标注"未验证假设"
- 日志文本全部 ASCII；本特性不新增异常类型
- 提交信息遵循 `类型(范围): 简述`，例如 `fix(eureka-client): 移除 jar 内默认配置并提供装配开关`

---

## Phase 10: Convergence

**Purpose**: 关闭 `/speckit.converge` 在实施后二次比对中发现的缺口（2026-10-22）。本阶段只追加任务，不改动 Phase 1~9 的既有条目；完成后再次运行 `/speckit.implement`。

- [X] T041 [HIGH] 实测 `eureka7001.com`、`eureka7002.com`、`eureka7003.com` 在未配置 hosts 环境下的解析结果（`Resolve-DnsName`/`InetAddress`）与"改前默认配置下的注册请求表现"，在 `specs/009-eureka-client-module-audit/audit-report.md` §5 新增"默认域名可达性（改前行为）"小节，三个域名各一行、结论唯一化（可达/不可解析 + 注册请求结果），禁止"可能/通常"措辞；同时注明改后框架已无此默认值 per SC-005 / US2-AC4 (missing)
- [X] T042 [HIGH] 在 `audit-report.md` §7 增加"全量发现 × 复现方式 × 回归设计"映射表，覆盖 F-01 ~ F-13（重点补齐 F-04、F-05、F-06、F-08、F-10、F-11、F-12、F-13 的复现步骤与"旧失败/新通过"差异），并为文档类发现新增契约断言用例 `leaf-base-eureka-client/src/test/java/cn/maple/eureka/client/config/GXEurekaClientContractTest.java`：元数据含 `maple.framework.enable.eureka-client`（`defaultValue=true`）、`AGENTS.md` 与 `docs/leaf-base-eureka-client开发手册.md` 均出现开关键、`target/classes` 内不含 `eureka700` 与 `spring.cloud.openfeign.circuitbreaker` per FR-003 / FR-017 / SC-007 (partial)
- [X] T043 [MEDIUM] 新增宿主配置优先级用例到 `leaf-base-eureka-client/src/test/java/cn/maple/eureka/client/config/`：以 `src/test/resources/host/application.yml`（含 `eureka.client.register-with-eureka`、`service-url.defaultZone`）配合 `spring.config.location`/`additional-location` 启动上下文，断言生效值来自宿主文件、且模块不再提供同名默认；注意 spec FR-011 写作"收到 PUT"，Eureka 实际为 `POST /eureka/apps/{APP}`，需在报告 §7 记录该措辞差异 per FR-011 (partial)
- [X] T044 [MEDIUM] 修正 `leaf-base-eureka-client/src/main/java/cn/maple/eureka/client/config/GXEurekaClientConfig.java`：当 `eureka.client.enabled=false`（客户端已被应用显式关闭）时不得再输出 `Eureka client default zone is not configured ... registration will target the Spring Cloud default.`，并在 `GXEurekaClientSwitchTest$ExplicitClientDisabled` 增加"无该 WARN"断言；如需保留原行为则改 `contracts/assembly-contract.md` §1 第 2 行与 §3 的日志列使其与实现一致 per assembly-contract §1/§3 (contradicts)
- [X] T045 [MEDIUM] 实测并记录"仅使用 feign 的应用"在本次迁移前后行为不变：以 `leaf-base-feign` 既有用例（`GXFeignCircuitBreakerFallbackTest` 显式设置三个键）与 `config/{dev,test,pre,prod}/app-environment.yml` 为证据，并断言本模块产物内不含 `spring.cloud.openfeign.*`（`Get-ChildItem target/classes -Recurse | Select-String`），结论写入 `audit-report.md` §5 与 §12 per FR-013 (partial)
- [X] T046 [MEDIUM] 在 `audit-report.md` 新增"资源与启动成本"小节：同机同 JDK 下 `WebEnvironment.NONE` 空应用的上下文启动耗时（两次重跑并计算偏差百分比，口径 ≤ 30%）、常驻线程增量（默认开关 22 线程/4 Eureka vs 开关关闭 11 线程/0 Eureka，需复核默认开关形态），并说明测量命令与探针形态 per FR-018 (partial)
- [X] T047 [MEDIUM] 实测"引入但不注册"的三种配置组合（`eureka.client.enabled=false`、`spring.cloud.discovery.enabled=false`、`spring.cloud.service-registry.auto-registration.enabled=false`）各自的 Bean 装配/注册请求/`eureka.*` 残留值，追加为 `audit-report.md` §4 的"组合矩阵"，并为至少一种组合补回归用例 per US1-AC3 / FR-004 (partial)
- [X] T048 [MEDIUM] 替换 `docs/leaf-base-eureka-client开发手册.md` §5 的示例获取命令：`Expand-Archive` 对 `.jar` 实测报"不是支持的存档文件格式"，改为 `jar xf <jar> examples/` 或直接 `Copy-Item` 源码目录，并按新命令实测一次后把结果写入该节 per FR-014 / 手册可用性 (contradicts)
- [X] T049 [LOW] 在 `audit-report.md` §3 发现表补"改前位置（文件:行区间）"列或行内标注，使已删除的 `src/main/resources/application.yml` 相关条目（F-02、F-03、F-09）仍可定位：写成"改前 `src/main/resources/application.yml:1-15`（已删除）" per FR-002 (partial)
- [X] T050 [LOW] 在 `specs/009-eureka-client-module-audit/spec.md` 的"背景与审查结论摘要"开头补一句指向 `audit-report.md` 的引用链接（只加引用，不改任何 FR/SC 验收口径）per FR-016 (partial)
- [X] T051 [LOW] 在 `audit-report.md` 新增"边界场景处置"小节，逐条标注 spec Edge Cases 的处置（已实测 / 已由用例覆盖 / 明确不在本次范围），至少覆盖：部分注册中心不可达、403/404 注册被拒、TLS 与 basic auth、WebFlux 应用、LoadBalancer 无实例、`spring.config.location` 覆盖、Servlet 与 Reactive 两种 Web 类型；不得留空或写"待定" per spec Edge Cases (partial)
- [X] T052 [LOW] 在 `docs/开发手册.md` 的"模块开发手册"列表补 `leaf-base-eureka-client` 条目（与既有格式一致：链接 + 一句话说明），并在 PR 说明中登记 `leaf-base-eureka-server` 手册同样未入索引（不在本特性内改动）per FR-014 可发现性 (partial)

---

## Phase 11: Convergence

**Purpose**: 关闭第二轮 `/speckit.converge` 发现的 4 项残余缺口（2026-10-23）。本阶段只追加任务，不改动 Phase 1~10 的既有条目。

- [X] T053 [MEDIUM] 修正 `specs/009-eureka-client-module-audit/contracts/assembly-contract.md` §4：删除"`GXEurekaClientConfig.ConditionalOnProperty=...` 必须出现在 `spring-autoconfigure-metadata.properties`"这一条——Spring Boot 的 autoconfigure metadata 只记录 `AutoConfigureAfter/Before` 与 `ConditionalOnClass/ConditionalOnBean` 等，**不记录** `ConditionalOnProperty`；改为"开关条件以 `@ConditionalOnProperty` 注解 + `additional-spring-configuration-metadata.json` 为可验证载体"，并把实测输出（当前文件仅 `AutoConfigureAfter`、`ConditionalOnClass` 两行）作为佐证；同步修正 `audit-report.md` §7.3 的 F-05 行（现写"条件元数据含 `ConditionalOnProperty=`（T015 核对）"，与实测不符）；在 `leaf-base-eureka-client/src/test/java/cn/maple/eureka/client/config/GXEurekaClientContractTest.java` 增加断言：`spring-autoconfigure-metadata.properties` 含 `AutoConfigureAfter` 与 `ConditionalOnClass`、且不含 `eureka700` per assembly-contract §4 / FR-002 (contradicts)
- [X] T054 [LOW] 更新 `specs/009-eureka-client-module-audit/audit-report.md` 的用例计数：§1.4 "用例数 4 → 12（新增 8）" 与 §8 表格 "实施后 12 个用例" 均改为 21，并注明 Phase 10 新增 9 个用例（`GXEurekaClientContractTest` 3、`HostConfigTest` 2、`CombinationTest` 3、`DefaultZoneTest` +1），与 `AGENTS.md` §5 的 21 一致 per FR-002 / SC-001 (partial)
- [X] T055 [LOW] 把证据编号范围统一到 E-01 ~ E-27：`specs/009-eureka-client-module-audit/data-model.md` 第 28 行与第 34 行（补 E-25 启动成本、E-26 域名可达性、E-27 组合矩阵）、`research.md` D-09（"E-01 ~ E-21"）、`quickstart.md` §10（"E-01~E-24"）per FR-002 证据一致性 (partial)
- [X] T056 [LOW] 用当前 21 个用例重跑双平台验证（模块源文件转 LF → `mvn -o -pl leaf-base-eureka-client test` → 恢复 CRLF），把两侧结果写入 `audit-report.md` §7.1 与 §12，替换"LF 校验在 Phase 10 之前完成"的说明，使 SC-007 的双平台结论覆盖全部用例 per FR-010 / SC-007 (partial)

---

## Phase 12: Convergence

**Purpose**: 关闭第三轮 `/speckit.converge` 发现的 3 项文档口径缺口（2026-10-23）。本阶段只追加任务，不改动 Phase 1~11 的既有条目。

- [X] T057 [LOW] 更新 `specs/009-eureka-client-module-audit/audit-report.md` §12 的完成度行：`52/52（T001 ~ T040 + Phase 10 的 T041 ~ T052）` 改为 `56/56（T001 ~ T040 + Phase 10 的 T041 ~ T052 + Phase 11 的 T053 ~ T056）`；同步 `specs/009-eureka-client-module-audit/quickstart.md` §2 的期望值"Tests run: ≥7"为"Tests run: ≥23" per FR-003 / SC-001 (partial)
- [X] T058 [LOW] 在 `audit-report.md` §12.1 追加两行 Phase 11 记录：①assembly-contract §4 曾要求 `spring-autoconfigure-metadata.properties` 出现 `ConditionalOnProperty=`，经实测该文件只有 `AutoConfigureAfter`/`ConditionalOnClass`（Spring Boot 不记录该条件），已改为"注解 + additional metadata + 行为用例"三载体；②T054 的目标值原为 21，因 T053 在同一测试类新增 2 条断言，实际落为 23（并在 §1.4 已按 23 回填） per FR-002 (partial)
- [X] T059 [LOW] 在 `docs/leaf-base-eureka-client开发手册.md` §8"故障排查"表新增一行：现象"设置了 `eureka.client.enabled=false` 后不再出现 `Eureka client default zone is not configured` 告警"→ 原因"客户端已被显式关闭，模块不再提示 defaultZone 缺失（告警抑制是预期行为，不代表已配置）"→ 处理"若期望注册，请去掉该配置或改用 `maple.framework.enable.eureka-client=false` 并显式提供 `defaultZone`" per FR-014 / 手册可用性 (partial)

---

## Phase 13: Convergence

**Purpose**: 关闭第四轮 `/speckit.converge` 发现的唯一残余缺口（计数自引用）。本阶段只追加任务，不改动 Phase 1~12 的既有条目。

- [X] T060 [LOW] 把 `specs/009-eureka-client-module-audit/audit-report.md` §12 的完成度行由固定计数（现为"56/56（T001 ~ T040 + Phase 10 的 T041 ~ T052 + Phase 11 的 T053 ~ T056）"）改为**不含固定数字**的自校验表述，例如"任务完成度：100%——`tasks.md` 中全部任务均已勾选（数量与编号以 `tasks.md` 为准，本行不再维护计数）"，并保留对 `tasks.md` 的链接；同时在该行下方补一句"计数式表述会在后续 Convergence 追加任务后失效，故改用自校验口径" per FR-002 / SC-001 (partial)
