---
description: "Task list for 008-eureka-server-module-audit"
---

# Tasks: leaf-base-eureka-server 装配边界、配置语义与依赖治理审查

**Input**: Design documents from `/specs/008-eureka-server-module-audit/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性**必须**包含测试任务。依据 `spec.md` FR-010 / FR-013 / SC-007 与 `AGENTS.md` 第 5 节：每条发现需有"旧代码失败、新代码通过"的回归设计，且改动自动装配入口与默认配置必须补测试。测试任务以 TDD 顺序排列（先写失败用例，再实现）。

**Organization**: 任务按 `spec.md` 的 6 个用户故事分组，每个故事可独立实现与独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 对应 `spec.md` 的用户故事（US1 ~ US6）
- 所有任务均给出精确文件路径

## Path Conventions

单模块 Maven 库：`leaf-base-eureka-server/src/main/...`、`leaf-base-eureka-server/src/test/...`；跨模块仅 `leaf-framework/pom.xml` 与根 `pom.xml`（已授权最小集合）；文档 `docs/`、`README.md`；本特性文档 `specs/008-eureka-server-module-audit/`。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 固化审查基线与产物骨架，后续所有结论可复核。

- [X] T001 记录既有测试基线：执行 `mvn -pl leaf-base-eureka-server test`，将 `Tests run: 2 / Failures: 0 / Errors: 0 / BUILD SUCCESS` 与耗时写入 `specs/008-eureka-server-module-audit/audit-report.md` 的"基线"章节（对应 E-16）
- [X] T002 [P] 保存依赖树快照：`mvn -pl leaf-base-eureka-server dependency:tree -DoutputFile=specs/008-eureka-server-module-audit/.tree-before.txt`，作为 US3 的 diff 基线
- [X] T003 [P] 按 [data-model.md](./data-model.md) §1 创建 `specs/008-eureka-server-module-audit/audit-report.md` 的"发现清单"表格骨架（列：编号 / 级别 / 维度 / 位置 / 触发条件 / 影响面 / 证据 / 验证级别 / 复现 / 回归设计 / 修复动作）
- [X] T004 [P] 按 [data-model.md](./data-model.md) §2 ~ §7 在 `audit-report.md` 中建立 5 张空矩阵表：启动语义矩阵（10 行）、装配矩阵、依赖矩阵、配置来源矩阵、契约核对清单

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 证据索引与假设台账。任何用户故事的结论都必须引用这里的编号。

**⚠️ CRITICAL**: 本阶段完成前，US1 ~ US6 的结论不得写入报告。

- [X] T005 在 `audit-report.md` 建立证据索引 E-01 ~ E-17：逐条给出 `文件:行区间`，覆盖 `GXEurekaServerConfig.java`、`package-info.java`、`application.yml`、`AutoConfiguration.imports`、`pom.xml`、`AGENTS.md`、`leaf-framework/pom.xml` 与依赖树实测记录
- [X] T006 在 `audit-report.md` 建立"未验证假设台账"，登记 U-01（`classpath:/application.yml` 覆盖方向）、U-02（集群对等复制）、U-03（`jersey-apache5-connector` 可用性）、U-04（依赖 diff 结果），并注明各自的实测责任任务号
- [X] T007 [P] 在 `audit-report.md` 建立"审查范围与不变量"章节：列出 P-01 ~ P-06（不得在修复中破坏的正确实现）与 4 个生产交付物 + 1 个测试类的逐文件判定栏

---

## Phase 3: User Story 1 - 注册中心启动语义可复核（单机 vs 集群）(Priority: P1) 🎯 MVP

**Goal**: 用真实启动给出"示例默认值到底是单机还是集群"的唯一定性，并交付可复跑的启动用例。

**Independent Test**: `mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerStartupTest` 全绿，且按 `audit-report.md` 的启动语义矩阵逐行复现（10 个断言点）。

### Tests for User Story 1

- [X] T008 [US1] 在 `leaf-base-eureka-server/src/test/java/cn/maple/eureka/server/config/GXEurekaServerStartupTest.java` 新增 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 用例：设置 `maple.framework.enable.eureka-server=true`，用 JDK `java.net.http.HttpClient` 断言 `GET /eureka/apps` 返回 200；再 `POST /eureka/apps/{APP}` 注册后 `GET /eureka/apps/{APP}` 可见实例（先运行，允许因开关尚未实现而失败）
- [X] T009 [P] [US1] 在 `GXEurekaServerStartupTest.java` 补第二组用例：以 `server.port=0` + 固定 `eureka.instance.hostname` 启动，断言上下文启动成功并记录启动耗时与常驻线程数（供 T011 取数）——**部分完成**：第二组用例已落地（`registeredInstanceIsDiscoverable`），启动耗时取自 surefire 统计；线程数未采集（见报告 §9 未采集项）

### Implementation for User Story 1

- [X] T010 [US1] 以模块当前 `application.yml` 启动单机形态，逐项测量 5 个配置键（`register-with-eureka`、`fetch-registry`、`service-url.defaultZone`、`instance.hostname`、`instance.ip-address`）的实际生效值，填入 `audit-report.md` 启动语义矩阵"单机"5 行（对应 F-02、F-09）
- [X] T011 [US1] 以两个随机端口启动集群形态（互为 `defaultZone`），注册一个实例后查询对端，把"对等复制是否发生"的布尔结论 + 观测方式填入矩阵"集群"5 行与 U-02 台账（实测前不得断言结论）
- [X] T012 [US1] 测量并记录引入本模块前后的上下文启动耗时、常驻线程数增量、内存基线（同机同 JDK，两次运行偏差 ≤ 30%），写入 `audit-report.md` "资源与启动成本"章节（对应 FR-014）——**部分完成**：开关关闭/开启/双节点的启动耗时已记录；线程数与内存基线未采集，已在报告 §9 标注为残余
- [X] T013 [US1] 校验 `prefer-ip-address: true` + `ip-address: 127.0.0.1` + `hostname: eureka7001.com` 三者同时生效时实际注册到注册表的地址字段，给出"从另一台机器能否访问"的定性，写入 `audit-report.md`（对应 F-03）

**Checkpoint**: US1 完成即具备"审查结论可复核"的 MVP——结论可信、可复现，但不含任何装配/依赖修复。

---

## Phase 4: User Story 2 - 装配边界可验证：引入即生效的影响面 (Priority: P1)

**Goal**: 让"是否充当注册中心"由显式开关与 Web 类型决定，而不是由依赖存在决定。

**Independent Test**: `mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerConfigTest` 全绿；`mvn -pl leaf-framework dependency:tree` 不再包含 `leaf-base-eureka-server`。

### Tests for User Story 2

- [X] T014 [P] [US2] 改写 `leaf-base-eureka-server/src/test/java/cn/maple/eureka/server/config/GXEurekaServerConfigTest.java`：用 `ApplicationContextRunner` 断言开关缺省与 `=false` 时不出现注册中心 Bean、`=true` 时出现（先运行并确认失败）
- [X] T015 [P] [US2] 在同文件补两类契约用例：断言 `@ConditionalOnWebApplication.type == SERVLET`；用 `FilteredClassLoader(EnableEurekaServer.class)` 断言上下文正常且无注册中心 Bean

### Implementation for User Story 2

- [X] T016 [US2] 在 `leaf-base-eureka-server/src/main/java/cn/maple/eureka/server/config/GXEurekaServerConfig.java` 增加 `@ConditionalOnProperty(name="maple.framework.enable.eureka-server", havingValue="true", matchIfMissing=false)` 与 `@ConditionalOnWebApplication(type=SERVLET)`，并在启用路径输出一条 ASCII INFO 日志（对应 D-01、D-02、D-10）
- [X] T017 [P] [US2] 新增 `leaf-base-eureka-server/src/main/resources/META-INF/additional-spring-configuration-metadata.json`，声明 `maple.framework.enable.eureka-server`（`type=java.lang.Boolean`、`defaultValue=false`），与代码 `matchIfMissing=false` 一致
- [X] T018 [US2] 从 `leaf-framework/pom.xml` 移除 `leaf-base-eureka-server` 依赖声明（已授权跨模块最小改动，对应 D-03）
- [X] T019 [P] [US2] 更新 `README.md` 模块表的 `leaf-base-eureka-server` 行：注明"需显式开启 `maple.framework.enable.eureka-server=true`，且不再随聚合门面传递"
- [X] T020 [US2] 执行 `mvn -pl leaf-framework dependency:tree` 与 `mvn -pl leaf-base-eureka-server test`，把"门面不再含本模块""开关三态行为""启用日志出现"三项结果回填 `audit-report.md` 装配矩阵

**Checkpoint**: 装配边界可控，门面影响面归零；US1 的启动用例应在开关 `true` 下继续通过。

---

## Phase 5: User Story 3 - 依赖治理可验证 (Priority: P1)

**Goal**: 消除开发期工具外泄、Jersey 双代际、停产 HttpClient 4 与净效果为零的依赖声明，且解析结果变化可逐坐标解释。

**Independent Test**: `audit-report.md` 依赖矩阵 6 组事实全部有判定；`.tree-before.txt` 与 `.tree-after.txt` 的 diff 仅含契约登记的四类差异。

### Implementation for User Story 3

- [X] T021 [US3] 从 `leaf-base-eureka-server/pom.xml` 移除 `org.springframework.boot:spring-boot-devtools` 依赖声明（对应 D-06、F-04）
- [X] T022 [US3] 在 `leaf-base-eureka-server/pom.xml` 删除 `hk2-api`、`hk2-utils`、`woodstox-core`、`httpclient` 的 `<exclusion>` 与其后同坐标重声明（保留 `jersey-apache-connector` 的 `<exclusion>`，对应 D-08、F-07）
- [X] T023 [US3] 在 `leaf-base-eureka-server/pom.xml` 删除 `<eureka.server.version>` 属性与 starter 上的 `<version>`（对应 D-09、F-08）
- [X] T024 [US3] 在 `leaf-base-eureka-server/pom.xml` 把 `jersey-apache-connector` 依赖替换为 `org.glassfish.jersey.connectors:jersey-apache5-connector`（版本 `${jersey-apache5-connector.version}`，对应 D-07、F-06）——**结论修正**：替换后双节点用例报 `NoClassDefFoundError: ApacheConnectorProvider`，已回退为保留 `jersey-apache-connector:3.1.12`，并在 `AGENTS.md`/手册/报告中登记为上游硬约束（E-18）
- [X] T025 [P] [US3] 在根 `pom.xml` 新增属性 `<jersey-apache5-connector.version>4.0.2</jersey-apache5-connector.version>` 并在 `<dependencyManagement>` 登记 `jersey-apache5-connector`；**不得修改**既有 `jersey-apache-connector.version=3.1.12`（`leaf-base-manticore` 在用）
- [X] T026 [US3] 生成 `.tree-after.txt` 并与 `.tree-before.txt` diff，逐行解释；只允许"`jersey-apache-connector` 行消失 + `jersey-apache5-connector:4.0.2` 出现""`httpclient:4.5.14` 行消失""`spring-boot-devtools` 行消失"三类，其余差异一律回退并在 U-04 台账登记（对应 [dependency-contract.md](./contracts/dependency-contract.md) §3）
- [X] T027 [US3] 执行 `mvn -pl leaf-base-manticore dependency:tree` 确认 `jersey-apache-connector` 仍为 `3.1.12`；执行 `mvn -pl leaf-base-eureka-server dependency:tree` 确认 `jersey-hk2 4.0.3`、`jersey-server 4.0.2` 未变
- [X] T028 [US3] 把依赖矩阵 6 组事实（devtools 作用域、5 组 exclusion 净效果、connector 代际、hk2/spring-bridge 代际、httpclient 4 与 5 并存、starter 与 BOM 对应关系）逐行填入 `audit-report.md`，并标注 U-03/U-04 结论

**Checkpoint**: 依赖进入"可解释"状态；若 T026 出现非预期差异，按 [dependency-contract.md](./contracts/dependency-contract.md) §5 回退并记录证据。

---

## Phase 6: User Story 4 - 配置样例的可用性与覆盖风险 (Priority: P2)

**Goal**: 模块 jar 不再向宿主注入 `application.yml`，单机/集群示例可直接复制使用且不含环境绑定值。

**Independent Test**: `mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerExamplesTest` 全绿；`jar tf` 产物中无根路径 `application.yml`，含两个 `examples/*.yml`。

### Tests for User Story 4

- [X] T029 [US4] 新增 `leaf-base-eureka-server/src/test/java/cn/maple/eureka/server/config/GXEurekaServerExamplesTest.java`：断言 `classpath:/application.yml` 为 `null`；`examples/eureka-server-standalone.yml` 含 `register-with-eureka: false`、`fetch-registry: false`、`maple.framework.enable.eureka-server: true`；`examples/eureka-server-peer.yml` 含两处 `true`、占位符 `defaultZone`、占位符 `server.port`；两者均不含 `127.0.0.1`（先运行并确认失败）

### Implementation for User Story 4

- [X] T030 [US4] 删除 `leaf-base-eureka-server/src/main/resources/application.yml`（对应 D-04、F-05）
- [X] T031 [P] [US4] 新增 `leaf-base-eureka-server/src/main/resources/examples/eureka-server-standalone.yml`：仅含开关 `true` 与 `eureka.client.register-with-eureka=false` / `fetch-registry=false`，不出现 IP、hostname、`defaultZone`、`server.port`（对应 D-05）
- [X] T032 [P] [US4] 新增 `leaf-base-eureka-server/src/main/resources/examples/eureka-server-peer.yml`：`register-with-eureka: true`、`fetch-registry: true`、`defaultZone` 至少两个对端且为 `${...}` 占位符、`instance.hostname` 与 `server.port` 均为占位符（对应 D-05、[configuration-contract.md](./contracts/configuration-contract.md) §3）
- [X] T033 [US4] 组装"框架 jar + 宿主 `application.yml`"类路径，实测宿主 `eureka.client.*` 是否生效、多次启动结论是否一致，写入 `audit-report.md` 配置来源矩阵并关闭 U-01（对应 F-05、FR-007）——**方案调整**：改为"消除竞争源"（删除框架侧同名配置并由用例断言 `classpath:/application.yml == null`），原覆盖方向未再复测，已在报告 §3 U-01 说明
- [X] T034 [US4] 执行 `mvn -pl leaf-base-eureka-server package` 并用 `jar tf` 校验产物：无 `application.yml`、含 `examples/eureka-server-standalone.yml`、`examples/eureka-server-peer.yml`、`META-INF/additional-spring-configuration-metadata.json`

**Checkpoint**: 配置"示例"与"运行时契约"彻底分家，宿主配置不再被静默覆盖。

---

## Phase 7: User Story 5 - 契约文档与可发现性一致 (Priority: P2)

**Goal**: 文档与实现一致：章节完整、测试现状准确、有模块手册、配置键在 IDE 可发现。

**Independent Test**: `audit-report.md` 契约核对清单 4 类来源 100% 为"一致"；`docs/leaf-base-eureka-server开发手册.md` 存在且含开关与迁移章节。

### Implementation for User Story 5

- [X] T035 [P] [US5] 新增 `docs/leaf-base-eureka-server开发手册.md`：模块定位、开关与默认值、单机/集群示例用法（引用 `src/main/resources/examples/`）、迁移章节（开关默认 `false`、门面不再传递、默认配置不再内置地址）、验证命令（引用 [quickstart.md](./quickstart.md)）、受影响应用清单（对应 D-13、SC-011）
- [X] T036 [P] [US5] 修正 `leaf-base-eureka-server/AGENTS.md`：补 `## 2.` 缺失章节、把"当前模块没有现成测试"改为现有用例清单、在功能基线中登记"默认关闭、需显式开启"与"默认配置不含固定地址/端口"
- [X] T037 [US5] 逐条回填 `audit-report.md` 契约核对清单：`AGENTS.md` 章节与表述、`README.md` 模块表、手册、配置元数据 4 类来源的"一致 / 不一致"与行号，全部转为"一致"（对应 F-11、SC-009）

**Checkpoint**: 文档、元数据、代码三处对同一开关与同一默认值表述一致。

---

## Phase 8: User Story 6 - 结论可驱动后续修复排期 (Priority: P3)

**Goal**: 让每条发现都能被计划阶段直接拆成任务，无需补充上下文。

**Independent Test**: 抽查任一发现，七要素（编号/级别/维度/位置/触发条件与影响/证据/复现与回归设计）齐全；风险排序表可直接生成修复批次。

### Implementation for User Story 6

- [X] T038 [US6] 在 `audit-report.md` 逐条回填 F-01 ~ F-14 的七要素与"旧失败 / 新通过"回归证据（引用 T008/T014/T015/T029 的用例名）
- [X] T039 [US6] 校验 `audit-report.md` 与 `spec.md` 的一致性：F 编号、级别、维度、证据编号完全对应；任何新增发现必须先回填 `spec.md`（对应 FR-012）
- [X] T040 [US6] 在 `audit-report.md` 输出风险排序表（影响面 × 触发概率 × 可观测性）与建议修复批次，并把 D-12 的验证残余风险、U-01 ~ U-04 实测结论列入"已知遗留"
- [X] T041 [US6] 在 `docs/leaf-base-eureka-server开发手册.md` 附录生成迁移清单：升级后必须显式开启开关或直接依赖本模块的应用/配置键清单（对应 SC-011）

**Checkpoint**: 报告可作为下一轮修复计划的唯一输入。

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 收口与回归。

- [X] T042 [P] 全量回归：`mvn -pl leaf-base-eureka-server test`（用例数 ≥ 10、0 失败）与 ErrorProne/NullAway 零错误，结果回填 `audit-report.md`
- [X] T043 [P] 校验 `mvn -pl leaf-base-eureka-server package` 产物与 `quickstart.md` §9 完成判定表逐项一致
- [X] T044 清理临时快照：把 `.tree-before.txt` / `.tree-after.txt` 的关键差异摘入 `audit-report.md` 附录后删除这两个文件
- [X] T045 按 `quickstart.md` 走一遍全部 9 节验证，把与预期不符的步骤修正到 `quickstart.md`（只改命令与期望值，不改设计结论）——**部分完成**：已执行 §1 基线、§2/§3 用例、§4 依赖、§7 配置来源、§9 完成判定；§6 双节点手工步骤未执行（与报告 §3 U-02 残余一致）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 无依赖，立即开始
- **Phase 2 Foundational**: 依赖 Phase 1（需要基线数字与空表）；**阻塞全部用户故事**
- **Phase 3 US1 (P1)**: 依赖 Phase 2；T008 的用例在 T016 完成前会失败（预期，属 TDD 顺序），T010/T011 的"集群实测"须在 T032 之前完成以区分"现状"与"示例"
- **Phase 4 US2 (P1)**: 依赖 Phase 2；T014/T015 先失败，T016 后转绿
- **Phase 5 US3 (P1)**: 依赖 Phase 2；T026 依赖 T021 ~ T025 全部完成
- **Phase 6 US4 (P2)**: 依赖 Phase 2；T034 依赖 T030 ~ T032
- **Phase 7 US5 (P2)**: 依赖 US2（开关与默认值已定型）与 US4（示例路径已定型）
- **Phase 8 US6 (P3)**: 依赖 US1 ~ US5 的全部结论
- **Phase 9 Polish**: 依赖全部

### Story-level Dependencies

| 故事 | 依赖 | 说明 |
| --- | --- | --- |
| US1 | Foundational | 独立可交付（只读结论 + 启动用例） |
| US2 | Foundational | 独立可交付；US1 的 T008 在 T016 后转绿，属正向耦合 |
| US3 | Foundational | 独立可交付；T026 的 diff 断言不与 US2 文件重叠 |
| US4 | Foundational | 独立可交付；与 US2 共享 `resources/META-INF` 目录但文件不同 |
| US5 | US2、US4 | 文档必须描述最终开关名与最终示例路径 |
| US6 | US1 ~ US5 | 汇总 |

### Within Each User Story

- 测试先写并确认失败 → 再实现 → 再回填报告
- `pom.xml` 与 `GXEurekaServerConfig.java` 为单文件写入点，相关任务**不得并行**（T016、T021 ~ T024 串行）
- 报告回填任务（T010、T020、T028、T033、T037、T038 ~ T041）共享同一文件 `audit-report.md`，**按序执行**，不得并行

---

## Parallel Execution Examples

### Phase 1 + Phase 2

```text
T002 保存依赖树快照                    # 独立文件 .tree-before.txt
T003 报告发现清单骨架                  # audit-report.md（与 T002 无冲突）
T004 报告 5 张矩阵空表                 # audit-report.md（须在 T003 之后）
T005 证据索引 E-01 ~ E-17              # audit-report.md（须在 T004 之后）
T007 范围与不变量章节                  # audit-report.md（须在 T005 之后）
```

### User Story 2

```text
T014 开关三态用例（GXEurekaServerConfigTest.java）
T015 Web 类型与类缺失用例（同文件 → 与 T014 串行）
T017 配置元数据（additional-spring-configuration-metadata.json）   # 与 T014/T015 并行
T019 README 模块表                                                  # 与 T014/T015 并行
```

### User Story 3

```text
T025 根 POM 新增 apache5 属性（pom.xml）        # 与模块 pom 编辑并行
T021 ~ T024 模块 pom.xml（同一文件 → 严格串行）
T027 manticore/eureka-server 依赖树复核          # 在 T021 ~ T025 之后
```

### User Story 4

```text
T031 standalone 示例（examples/eureka-server-standalone.yml）
T032 peer 示例（examples/eureka-server-peer.yml）      # 不同文件，可并行
T030 删除 application.yml                              # 与 T031/T032 并行（不同文件）
```

---

## Implementation Strategy

### MVP First（User Story 1）

1. Phase 1 Setup + Phase 2 Foundational
2. Phase 3 US1：真实启动用例 + 启动语义矩阵 + 资源成本口径
3. **STOP and VALIDATE**：按 `quickstart.md` §1、§6 复现；结论可信即视为 MVP 达成

> 本特性是"审查驱动"型交付：US1 交付"结论可信"，US2/US3 交付"缺陷已修"，两者可分批上线。

### Incremental Delivery

1. Setup + Foundational → 报告骨架与基线就位
2. + US1 → 审查结论可复核（MVP）
3. + US2 → 装配边界可控（含门面缩面）
4. + US3 → 依赖干净且可解释
5. + US4 → 配置分家完成
6. + US5 → 文档与元数据一致
7. + US6 → 报告收口，可移交修复排期

### Parallel Team Strategy

1. 全员完成 Phase 1 + Phase 2（报告骨架单点写入，避免冲突）
2. Foundational 完成后：
   - 开发者 A：US1（启动实测 + `GXEurekaServerStartupTest`）
   - 开发者 B：US2（开关 + 装配用例 + 门面）
   - 开发者 C：US3（依赖治理 + 根 POM）
3. US2、US4 完成后由一人收口 US5、US6（报告与文档单点写入）

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 单文件写入点（`pom.xml`、`GXEurekaServerConfig.java`、`audit-report.md`）不得并行
- 每条发现必须"测试先失败、实现后通过"；不得删除既有 2 个用例
- 破坏性变更（开关默认 `false`、门面缩面、默认配置归零）必须同步写入 `docs/leaf-base-eureka-server开发手册.md` 与 PR 说明
- 未实测项一律标"未验证假设"，不得写成已确认缺陷（U-01 ~ U-04）

---

## Phase 10: Convergence

> 本阶段由 `/speckit.converge` 追加，用于收口 spec/plan/tasks 与实际代码之间的剩余差距。任务编号从 T046 起，不复用、不改写任何既有任务。

- [X] T046 在双 JVM / 双进程环境完成 Eureka 对等复制实测（节点 A 注册实例后查询节点 B），给出"replicated=true/false"的唯一结论与观测命令，回填 `audit-report.md` §4、§3 U-02 与 §12 遗留对应条目，替代当前"单 JVM 未观察到、待多机复核"的未定性表述 per FR-004 / US1/AC2 (partial)
- [X] T047 在临时外部 WebFlux 工程（仓库外，走 Maven 依赖引入本模块）完成冒烟：不设开关时应用正常启动且无 `/eureka/apps`，设 `maple.framework.enable.eureka-server=true` 时仍不装配注册中心；把命令、日志片段与结论写入 `audit-report.md` §5，并将证据路径补进 §12，替代当前仅注解契约的验证方式 per SC-011 / US2/AC4 (partial)
- [X] T048 采集引入本模块前后的常驻线程数与内存基线（如 `jcmd Thread.print` / `jcmd GC.heap_info`，同机同 JDK 各两次），并把开关关闭/开启的上下文启动耗时重复运行 2 次验证偏差 ≤ 30%，把数值与口径回填 `audit-report.md` §9，删除"未采集项"标注 per FR-014 (partial)
- [X] T049 以真实类路径组装实测"框架 jar + 宿主 `application.yml`"场景下 `eureka.client.*` 的最终生效来源与稳定性（多次启动一致），或给出等价实测（宿主配置与 `examples/` 并存时的生效值），回填 `audit-report.md` §7 与 §3 U-01，替代当前"仅断言 `classpath:/application.yml` 不存在"的证据链 per FR-007 (partial)
- [X] T050 对"聚合门面消费者"形态补三项定性（是否启用注册中心 / 是否激活 Servlet 容器 / 启动耗时），并明确标注 §5 中"修复前缺省=present/200"一行的证据来源（实测或推断），回填 `audit-report.md` §5 per FR-005 (partial)
- [X] T051 在 `docs/leaf-base-eureka-server开发手册.md` 迁移章节补"受影响应用清单"的可执行判定命令（按依赖树筛出仍通过 `leaf-framework` 传递本模块的应用），把通用检查清单替换为可复算步骤 per SC-011 (partial)
- [X] T052 修正 `quickstart.md` §2 的用例名与期望，改为实际存在的 `switchDisabledByDefaultInNonServletContext`、`eurekaServerConfigRequiresExplicitSwitch`、`eurekaServerConfigRequiresServletWebApplication`、`missingEurekaServerClassSkipsGracefully`，并补充"开关 `true` 由 `GXEurekaServerSwitchTest` / `GXEurekaServerStartupTest` 覆盖"的说明 per plan: 验证入口 (partial)
- [X] T053 为 `leaf-base-eureka-server/src/test/java/cn/maple/eureka/server/config/` 补 `package-info.java`（`@org.jspecify.annotations.NullUnmarked`），与主包空值策略声明保持一致 per Constitution V / F-14 (missing)

---

## Phase 11: Convergence

> 第二轮收敛：上一轮新增了 `GXEurekaServerClientDefaultsPostProcessor` 与 4 个单测（用例 15 → 19），契约类文档尚未同步。任务编号从 T054 起，不改写任何既有任务。

- [X] T054 将 `leaf-base-eureka-server/AGENTS.md` §5 的测试现状由"现有 15 个用例 / 五类"更新为"现有 19 个用例 / 六类"，补充 `GXEurekaServerClientDefaultsPostProcessorTest`（4 个：开关缺省、Servlet 注册中心、reactive+开关、显式配置优先），并保留"改动功能代码/装配入口/依赖/默认配置必须补齐测试"的要求 per FR-009 (partial)
- [X] T055 在 `audit-report.md` §1.3 的逐文件判定表中补登记修复阶段新增的生产交付物：`GXEurekaServerClientDefaultsPostProcessor.java`、`META-INF/spring.factories`、`META-INF/additional-spring-configuration-metadata.json`、`examples/eureka-server-{standalone,peer}.yml`，并给出各自判定（新增/无缺陷及理由） per FR-001 (partial)
- [X] T056 在 `contracts/configuration-contract.md` 增补 `eureka.client.enabled` 这一模块隐式默认：触发条件（非 Servlet 注册中心）、最低优先级语义、显式配置优先规则、日志字段与对应单测名；并在 `data-model.md` §5 配置键模型中加入该键，使下游可从契约文档得知该默认值 per FR-019 / Constitution IV (partial)
- [X] T057 在 `contracts/assembly-contract.md` 的 A-01 / A-02 行补充"Eureka 客户端同时被默认禁用（`eureka.client.enabled=false`）"的装配结果，并新增一行覆盖"开关 `=true` 但非 Servlet 应用"的场景（F-16） per FR-002 / plan: assembly matrix (partial)
- [X] T058 更新 `quickstart.md`：§3 增补 `GXEurekaServerClientDefaultsPostProcessorTest` 与 `GXEurekaServerSwitchTest` 的执行入口；§5 把"消费方冒烟待执行"改为已执行结论（开关缺省与 `=true` 两场景均 `PING=200`、`/eureka/apps=404`，证据见 `audit-report.md` §5） per SC-011 (partial)

---

## Phase 12: Convergence

> 第三轮收敛（2026-10-22）：本轮以 `mvn -pl leaf-base-eureka-server test` 实测 `Tests run: 19, Failures: 0` 为基线，核对 FR-001 ~ FR-019、SC-001 ~ SC-011、US1 ~ US6、D-01 ~ D-14 与宪法 I ~ VII。任务编号从 T059 起，不改写任何既有任务。

- [X] T059 **CRITICAL** 按宪法第 VI 条把 `leaf-base-eureka-server/src/test/java/cn/maple/eureka/server/config/GXEurekaServerStartupTest.java` 重命名为 `GXEurekaServerSpringBootTest.java`（`@SpringBootTest` 集成测试须命名为 `GX<能力>SpringBootTest`，与仓库既有 7 个 `*SpringBootTest` 一致），并同步 `leaf-base-eureka-server/AGENTS.md` §5、`quickstart.md` §3、`audit-report.md` §5/§10 中的类名引用；以 `mvn -pl leaf-base-eureka-server test -Dtest=GXEurekaServerSpringBootTest` 与全量 `test` 验证 per Constitution VI (contradicts)——**已完成**：类与文件均改为 `GXEurekaServerSpringBootTest`，`AGENTS.md` §5、`quickstart.md` §2/§3/§9、`audit-report.md` §5/§9 引用同步，并一并更新 `plan.md` §Structure/实施顺序与 `research.md` §验证矩阵中的类名；目标用例 2 个通过
- [X] T060 补 FR-019 缺失的仓库内回归用例：在 Servlet 应用启动路径上使 `maple.framework.enable.eureka-server` 缺省且**不设置任何 `eureka.client.*`**，断言应用正常启动、`/eureka/apps` 返回 404、`Environment` 中 `eureka.client.enabled=false`（证明 `META-INF/spring.factories` 注册的 `GXEurekaServerClientDefaultsPostProcessor` 在真实上下文生效；现有 `GXEurekaServerSwitchTest` 显式禁用客户端，未覆盖该路径），并把用例名与证据回填 `audit-report.md` §5 per FR-019 (missing)——**已完成**：`GXEurekaServerSwitchTest#applicationStartsWhenSwitchIsMissingAndClientPropertiesAreUnset`（启动成功 + `eureka.client.enabled=false` + `/eureka/apps=404`），模块用例数 19 → 20，§1.1/§5/§8/§10 已按 20 回填
- [X] T061 在 `audit-report.md` §5 装配矩阵新增"启动耗时"列，为 4 类应用形态（仅本模块 / WebFlux / 聚合门面消费者 / 显式关闭）逐行给出实测数值并引用 §9 对应行，确实不可测的形态写明"不适用"及理由 per FR-005 / SC-004 / US2/AC1 (partial)——**已完成**：新增"启动耗时（§9 口径）"列，仅本模块/显式关闭 5604/5605 ms、开关 `true` 7145/7098 ms、聚合门面与类缺失标"不适用"，WebFlux 两行标"未采集"并登记 §12 残余第 5 条
- [X] T062 修正 `leaf-base-eureka-server/AGENTS.md` 中仍以已删除的 `classpath:/application.yml` 为基线的两处表述（§1 第 3 条"维护 `application.yml` …默认示例配置"、§5 第 3 条"`application.yml` 变更至少覆盖关键配置值"），改为指向 `src/main/resources/examples/` 并由 `GXEurekaServerExamplesTest` 覆盖，消除与 §2 第 4 条 / §3 第 9 条的自相矛盾 per FR-009 (contradicts)——**已完成**：§1 第 3 条改为维护 `examples/` 下两份示例，§5 第 3 条改为 `examples/` 变更覆盖四个关键键（由 `GXEurekaServerExamplesTest` 断言）
- [X] T063 把 `audit-report.md` §8 契约核对表的"改为 15 个用例分类说明"与 §10 F-10 行的"用例数 2 → 15"更新为现状：19 个用例 / 6 个测试类（`GXEurekaServerConfigTest` 6、`GXEurekaServerExamplesTest` 3、`GXEurekaServerSwitchTest` 2、`GXEurekaServerStartupTest` 2、`GXEurekaServerPeerClusterTest` 2、`GXEurekaServerClientDefaultsPostProcessorTest` 4），与 `AGENTS.md` §5 对齐 per FR-012 / SC-009 (contradicts)——**已完成**：按 T060 新增用例后的实际值统一为 **20 个用例 / 6 个测试类**（`GXEurekaServerConfigTest` 6、`GXEurekaServerExamplesTest` 3、`GXEurekaServerSwitchTest` 3、`GXEurekaServerSpringBootTest` 2、`GXEurekaServerPeerClusterTest` 2、`GXEurekaServerClientDefaultsPostProcessorTest` 4），§1.1/§8/§10 F-10 三处一致
- [X] T064 修正 `audit-report.md` 中 F-14 的处置状态：§10 F-14 行由"未修复（低优先，登记遗留）"改为"已修复（测试包新增 `package-info.java`，`@NullUnmarked`）"，§11 第 4 批不再将 F-14 列为遗留，§12 如需保留说明须标注已关闭 per FR-012 / F-14 (contradicts)——**已完成**：F-14 行改为"已修复：测试包新增 `package-info.java`（`@NullUnmarked`）"，§11 第 4 批改为"已收口"，§12 新增第 6 条"已关闭项"（含 F-13、F-14，并说明 F-06 属登记约束）
- [X] T065 在 `GXEurekaServerConfig` 的启用日志中补充监听端口占位（从 `Environment` 读取 `server.port`，缺省输出 `server.port=<unset>`），保持纯 ASCII，并同步 `contracts/assembly-contract.md` §3 第 4 条与 `quickstart.md` §3 第 3 条的期望日志文本 per plan: D-10 (partial)——**已完成**：`GXEurekaServerConfig` 构造器注入 `Environment`，日志为 `Eureka Server enabled via maple.framework.enable.eureka-server=true, server.port=<值|unset>`（纯 ASCII），`assembly-contract.md` §3 第 4 条与 `quickstart.md` §3 第 3 条同步
- [X] T066 修正 `quickstart.md` 的复现口径：§1 期望改为"历史基线 2 个用例（E-16）；当前 `mvn -pl leaf-base-eureka-server test` 为 19 个用例"；§4 去掉对已删除的 `.tree-before.txt` / `.tree-after.txt` 的依赖，改为可直接执行的 `dependency:tree` + `Select-String` 断言命令 per plan: quickstart 复现口径 (partial)——**已完成**：§1 期望改为 20 个用例并注明历史基线 2 个（E-16），§4 改为对当前依赖树逐条断言契约 §3 的 5 条，§9 完成判定同步
- [X] T067 收口 `contracts/assembly-contract.md` A-07 的断言缺口：要么补"开关 `true` 且存在用户自定义同类型 Bean 时启动成功"的用例，要么把"断言方式"改为与实现一致的表述（`@EnableEurekaServer` 语义 + 未引入 `@ConditionalOnMissingBean` 的说明），二者择一并在用例清单中登记 per plan: assembly-contract A-07 (partial)——**已完成**（选择后一种）：按实现改写 A-07 断言方式为"本模块不声明任何 `@Bean`，不存在覆盖用户 Bean 的路径"，并以 `GXEurekaServerConfigTest#eurekaServerConfigIsAutoConfigurationAndEnablesEurekaServer` 与契约 §3 第 5 条作为校验依据

---

## Phase 13: Convergence

> 第四轮收敛（2026-10-22）：以 `mvn -pl leaf-base-eureka-server clean package` 实测 `Tests run: 20` 为基线，复核 FR-001 ~ FR-019、SC-001 ~ SC-011、US1 ~ US6、D-01 ~ D-14 与宪法 I ~ VII。新增 F2 的定性来自仓库外临时探针实测（只设开关时 `shouldRegisterWithEureka=true` / `shouldFetchRegistry=true` / `defaultZone=http://localhost:8761/eureka/`），探针已删除，未改动任何代码。任务编号从 T068 起，不改写任何既有任务。

- [X] T068 **CRITICAL** 按宪法第 VI 条把 `leaf-base-eureka-server/src/test/java/cn/maple/eureka/server/config/GXEurekaServerSwitchTest.java` 与 `GXEurekaServerPeerClusterTest.java` 重命名为 `GXEurekaServerSwitchSpringBootTest.java`、`GXEurekaServerPeerClusterSpringBootTest.java`（二者均通过 `SpringApplicationBuilder` 启动真实 Spring 上下文，属"需要 Spring 容器的集成测试"，仓库先例 `GXDubboRpcApiBeanPostProcessorSpringBootTest` 采用同一手法却按 `GX<能力>SpringBootTest` 命名），同步 `AGENTS.md` §5、`quickstart.md` §3、`audit-report.md` §5/§9 的类名引用，并以 `mvn -pl leaf-base-eureka-server test` 验证 20 个用例仍全绿 per Constitution VI (contradicts)——**已完成**：两个类与文件均重命名，`AGENTS.md` §5、`quickstart.md` §2/§3、`audit-report.md` §5/§8/§9/§10、`research.md` 两处引用同步；`spec.md` 与 `tasks.md` 的历史证据/任务记录保留旧名（不改写历史）
- [X] T069 恢复 FR-008 / plan D-05 要求的框架级单机默认：在 `GXEurekaServerClientDefaultsPostProcessor` 中为"Servlet 注册中心"场景以**最低优先级**补充 `eureka.client.register-with-eureka=false` 与 `eureka.client.fetch-registry=false`（应用显式配置优先，不写入任何地址/域名/端口），使"只设 `maple.framework.enable.eureka-server=true`"的最小单机部署得到 `false/false`（当前实测该场景为 `true/true` + `defaultZone=http://localhost:8761/eureka/`，仅 `examples/eureka-server-standalone.yml` 提供正确值），并新增回归用例 `switchOnlySingleMachineDefaultsToNoRegistration` 锁定 `EurekaClientConfigBean#shouldRegisterWithEureka/ShouldFetchRegistry` 均为 `false` 且显式配置可覆盖；同步 `contracts/configuration-contract.md` §2 表格与迁移提示、`data-model.md` §5.1、`audit-report.md` §4/§7、`docs/leaf-base-eureka-server开发手册.md` §1/§4、`quickstart.md` §3 per FR-008 / P-04 (partial)——**已完成**：`GXEurekaServerClientDefaultsPostProcessor#applySingleMachineDefaults` 以最低优先级逐键写入（已存在显式值则跳过）并输出一条 ASCII INFO；新增 3 个用例（`switchOnlySingleMachineDefaultsToNoRegistration`、`appliesSingleMachineDefaultsWhenServletRegistryIsEnabled`、`keepsExplicitRegistrationSettingsWhenServletRegistryIsEnabled`），用例数 20 → 23；`configuration-contract.md` 新增 §1.2 并改写 §2 表格/迁移提示、`data-model.md` 新增 §5.2、`audit-report.md` §4/§5/§7/§8 与手册 §1/§4/§5 同步
- [X] T070 在 `audit-report.md` 新增"受影响应用清单"章节：给出"仓库内无下游消费工程"的结论、按依赖树筛出仍需显式开启开关或直接依赖本模块的应用的执行命令，以及升级后必须设置 `maple.framework.enable.eureka-server=true` 的配置键清单；与 `docs/leaf-base-eureka-server开发手册.md` §5 互为引用，避免两处口径不一致 per SC-011 (partial)——**已完成**：新增 `audit-report.md` §13「受影响应用清单（升级至 4.3.0 起）」，含仓库内无下游消费工程的结论、`mvn dependency:tree | Select-String` 判定命令、三类应用（门面间接依赖/直接依赖/仅需注册自身）的迁移动作表，以及与手册 §5 互为引用的升级后必查配置键清单
- [X] T071 为 `audit-report.md` §10 F-14 行补齐「回归设计」：给出可验证口径（测试包 `package-info.java` 存在且标注 `@NullUnmarked` 的断言点，以及 ErrorProne/NullAway 在 `mvn -pl leaf-base-eureka-server test` 编译阶段生效作为门禁），删除当前的空值 `—` per FR-013 / SC-002 (partial)——**已完成**：F-14 回归设计改为"修复前无 `package-info.java`（包级策略缺省）/ 修复后文件存在且首行为 `@org.jspecify.annotations.NullUnmarked`；断言点为文件存在性 + `mvn -pl leaf-base-eureka-server test` 编译阶段的 ErrorProne/NullAway 零错误门禁"
- [X] T072 修正启用日志的端口语义：`GXEurekaServerConfig` 当前输出 `Environment` 中的 `server.port` 配置值（`@SpringBootTest(RANDOM_PORT)` 下为 `0`，并非实际监听端口），改为"配置值 + `0` 明确表示随机端口"的表述，或在上下文刷新后由监听端口补一条日志，并同步 `contracts/assembly-contract.md` §3 第 4 条与 `quickstart.md` §3 第 3 条的期望文本 per plan: D-10 (partial)——**已完成**（选择前一种）：日志改为 `Eureka Server enabled via maple.framework.enable.eureka-server=true, server.port={} (0 = random port)`，未设置时仍输出 `<unset>`；`assembly-contract.md` §3 第 4 条改为"输出 `server.port` 配置值（`0` 表示随机端口）"，`quickstart.md` §3 第 3 条同步

---

## Phase 14: Convergence

> 第五轮收敛（2026-10-22）：以 `mvn -pl leaf-base-eureka-server clean package` 实测 `Tests run: 23, Failures: 0` 为基线，复核 FR-001 ~ FR-019、SC-001 ~ SC-011、US1 ~ US6、D-01 ~ D-14 与宪法 I ~ VII。仅剩 T069 引入的一处描述漂移，无功能缺口。任务编号从 T073 起，不改写任何既有任务。

- [X] T073 让 `GXEurekaServerClientDefaultsPostProcessor` 相关描述与 T069 后的实现一致：`audit-report.md` §1.3 第 8 行改为两项职责（非 Servlet 注册中心时默认禁用客户端；Servlet 注册中心时以最低优先级补 `register-with-eureka=false` / `fetch-registry=false`）并写明 `GXEurekaServerClientDefaultsPostProcessorTest` 现有 6 个用例；`data-model.md` §5.1 的 `regression` 行改为"4 个客户端禁用用例（类内共 6 个）"；`research.md` D-14「补充实测（E-20）」中的"4 个用例"改为 6 个；同时把 `audit-report.md` §1.4 的 P-04 表述由"示例文件与文档一致"改为"框架最低优先级默认 + 示例文件与文档一致"，并复核这三处与 `AGENTS.md` §5、`quickstart.md` §3 的用例数（23 / 6 类）不再互相矛盾 per FR-012 / FR-001 / SC-009 (contradicts)——**已完成**：§1.3 第 8 行改为 (a)/(b) 两项职责 + 6 个用例 + 真实上下文用例名；P-04 改为"框架最低优先级默认 + `examples/` 与 `AGENTS.md` 同值"；`data-model.md` §5.1 改为"4 个客户端禁用用例（类内共 6 个，另 2 个覆盖 §5.2）"；`research.md` D-14 改为 6 个并区分两类组合；全仓复查后仅 `tasks.md` 历史轮次记录保留"4 个用例"字样

---

## Phase 15: Convergence

> 第六轮收敛（2026-10-22）：以 `Tests run: 23, Failures: 0`、`unchecked_tasks=0`、门面依赖树不含本模块为基线，复核 FR-001 ~ FR-019、SC-001 ~ SC-011、US1 ~ US6、D-01 ~ D-14 与宪法 I ~ VII。仅剩 SC-004 的一个量化数据点未采集。任务编号从 T074 起，不改写任何既有任务。

- [X] T074 补齐 WebFlux 形态的启动耗时，使 `audit-report.md` §5 装配矩阵 4 种形态全部给出"是否启用注册中心 / 是否激活 Servlet 容器 / 启动耗时"（SC-004 的 100% 口径）：按 T047 的仓库外工程方式（`spring-boot-starter-webflux` + 本模块、具名包 `lab.flux`），对"开关缺省"与"开关 `=true`"两种场景各计时 2 次（口径：进程启动到 `GET /ping` 返回 200，同机同 JDK，两次偏差 ≤ 30%），把数值回填 §5 两行的"启动耗时"列与 §9，随后删除或改写 §12 残余第 5 条为"已采集"，并在 `quickstart.md` §5 的复现步骤中补入计时方式，确保三处口径一致 per FR-005 / SC-004 (partial)——**已完成**：仓库外探针（`lab.flux.FluxApp` + `spring-boot-starter-webflux` + 本模块，`--spring.main.web-application-type=reactive`）实测开关缺省 **6837 / 6712 ms**（偏差 1.8%）、开关 `=true` **6712 / 6701 ms**（偏差 0.16%），两场景 `/ping`=200、`/eureka/apps`=404；已回填 §5 两行与 §9 两行、§12 第 5 条改写为"仓库外采集"，`quickstart.md` §5 补入计时口径（进程启动 → `/ping`=200，轮询 50 ms，每场景 2 次）；探针目录已清理，仓库依赖零改动
