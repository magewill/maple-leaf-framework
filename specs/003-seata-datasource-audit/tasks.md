---

description: "Task list for Seata × 动态数据源审查加固（设计定论后重建）"
---

# Tasks: Seata 与动态数据源协作正确性审查加固

**Input**: Design documents from `/specs/003-seata-datasource-audit/`

**Prerequisites**: plan.md, spec.md（含 10 条澄清）, research.md, data-model.md, contracts/, quickstart.md

**Tests**: 必交付项。`FR-012` 与 `SC-002` 要求"不依赖外部中间件的自动化测试基线"，宪法 VI 要求修复类测试"在旧代码上失败、在新代码上通过"。

**关键测试约束（已核实，见 research R-07）**: 真实 `org.apache.seata.rm.datasource.DataSourceProxy` 的构造器会 `getConnection()` 并校验 `undo_log`，无库环境必然失败。因此**所有测试不得构造真实代理**：代理类只做"类名/接口/构造器"反射契约测试，包装后的行为一律用实现 `SeataDataSourceProxy` 的桩对象验证，禁止 `getClass()` 断言。

**Organization**: 按用户故事分组，每个故事可独立实现与独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1～US5）
- 每条任务均给出精确文件路径

## Path Conventions

Maven 多模块库：生产代码位于 `leaf-base-seata/src/main/java/...`、`leaf-base-datasource/src/main/java/...`；测试位于对应模块 `src/test/java/...`；特性产物位于 `specs/003-seata-datasource-audit/`。

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立审查报告骨架与可复核的事实基线

- [X] T001 按 `contracts/review-report-schema.md` 的 7 章结构创建报告骨架（标题 + 章节 + 复现索引表头），文件：`specs/003-seata-datasource-audit/review-report.md`
- [X] T002 [P] 采集环境与版本事实（`java -version`、`mvn -v`、根 POM 的 `seata.spring.boot.starter.version`、`seata-all-2.6.0.jar` 类清单命令与输出），写入报告第 7 章，文件：`specs/003-seata-datasource-audit/review-report.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 统一 Seata 代理类名的单一事实来源（F-02），作为所有后续故事的判定基线

**⚠️ CRITICAL**: 本阶段完成前不得开始任何用户故事

- [X] T003 [P] 新增契约测试：断言 `GXDynamicDataSourceConfig` **实际使用的类名常量**等于 `org.apache.seata.rm.datasource.DataSourceProxy`，且该类实现 `org.apache.seata.rm.datasource.SeataDataSourceProxy`、存在 `(javax.sql.DataSource)` 构造器；**不实例化该类**；**旧代码（`io.seata...`）上必须失败**，文件：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXSeataProxyContractTest.java`
- [X] T004 将 `wrapSeataDataSource` 的反射类名从 `io.seata.rm.datasource.DataSourceProxy` 改为新命名空间，并抽为 `private static final String` 常量（包可见以便 T003 断言；方法签名不变；日志级别保持 debug），文件：`leaf-base-datasource/src/main/java/cn/maple/core/datasource/config/GXDynamicDataSourceConfig.java`
- [X] T005 [P] 把装配条件中的 `org.apache.seata.rm.datasource.SeataDataSourceProxy` 抽为常量并加注释"锚定 Seata 2.6.0 新命名空间，禁止回退旧命名空间"，与 T004 的常量语义一致，文件：`leaf-base-seata/src/main/java/cn/maple/seata/config/GXSeataDynamicDataSourceConfig.java`

**Checkpoint**: `mvn -pl leaf-base-datasource -am test -Dtest=GXSeataProxyContractTest` 通过

---

## Phase 3: User Story 1 - 审查结论可复核、可定位 (Priority: P1) 🎯 MVP

**Goal**: 产出一份逐条可复核的独立审查报告，覆盖全部 Seata 引用点与已确认结论。

**Independent Test**: 随机抽取报告中的任一 Finding，按其"复现"字段执行命令或测试，现象与报告描述一致，全过程不超过 15 分钟。

- [X] T006 [P] [US1] 将 E-01～E-08 写入报告第 3 章，每条含代码位置（文件:行号区间）或依赖坐标 + 类全名 + 核对命令，文件：`specs/003-seata-datasource-audit/review-report.md`
- [X] T007 [P] [US1] 按模板写入 F-01～F-07，字段齐全（级别/位置/触发条件/影响/证据/复现/建议/状态）；`BLOCKER` 仅限 F-01、F-02；F-06 须注明"仅文档说明，不承诺运行时检测"，文件：`specs/003-seata-datasource-audit/review-report.md`
- [X] T008 [US1] 写入第 5 章"已确认无缺陷项"：路由键栈语义、上下文恢复、跨数据源 XID 与全局锁保留、路由数据源不被二次代理，各给出守护测试类与断言要点，文件：`specs/003-seata-datasource-audit/review-report.md`
- [X] T009 [US1] 生成第 6 章复现索引表并逐条实际执行，同时记录"修复前现象"与耗时；不可执行条目必须改写或降级为 `RISK`，文件：`specs/003-seata-datasource-audit/review-report.md`

**Checkpoint**: 报告可独立交付，SC-001 / SC-006 达成

---

## Phase 4: User Story 2 - 代理不生效时必须显式暴露 (Priority: P1)

**Goal**: 启动期确认"哪些数据源被代理、配置来自哪里"；**已装配但未代理**时由 seata 侧 warn，严格开关下启动失败；未启用 Seata 时不产生告警。

**Independent Test**: `mvn -pl leaf-base-seata test -Dtest=GXSeataProxyAssemblyTest` 覆盖 VERIFIED / DEGRADED / FAILED；`-Dtest=GXSeataProxyContractTest` 断言未启用场景为 debug 且 0 告警。

- [X] T010 [P] [US2] 新增配置元数据并登记 `maple.framework.enable.seata-strict`（`Boolean`、`defaultValue: false`、描述说明"true 时未代理即启动失败"），文件：`leaf-base-seata/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- [X] T011 [US2] 新增 `SmartInitializingSingleton` 校验 bean：**以 `ObjectProvider<GXDynamicDataSource>` 获取数据源，取不到时输出 WARN 并跳过校验（不得启动失败）**；遍历 `getResolvedDataSources()`；配置来源由 `ObjectProvider<GXLocalSeataProperties>` / `ObjectProvider<GXNacosSeataProperties>` 判定（**输出点唯一**）；全部实现 `SeataDataSourceProxy` 输出单条 INFO `[Seata] enabled=..., config-source=..., datasources=[...], proxied=[...]`；存在未代理项输出 WARN（含数据源名称集合）；严格模式下抛 `GXBusinessException`（消息含数据源名称、`org.apache.seata.rm.datasource.SeataDataSourceProxy`、开关键名）；日志全 ASCII，文件：`leaf-base-seata/src/main/java/cn/maple/seata/config/GXSeataDynamicDataSourceConfig.java`
- [X] T012 [P] [US2] 新增装配测试：用实现 `SeataDataSourceProxy` 的桩数据源与普通桩数据源对照，覆盖 VERIFIED / DEGRADED / FAILED（严格模式用 `withPropertyValues`）；**DEGRADED/FAILED 在旧代码上必须失败**（旧实现无校验 bean），文件：`leaf-base-seata/src/test/java/cn/maple/seata/config/GXSeataProxyAssemblyTest.java`
- [X] T013 [US2] 在契约测试中补断言：代理类被 `FilteredClassLoader` 隐藏时返回**同一实例**且不抛异常；该场景日志为 **DEBUG 且 0 条 WARN/ERROR**；**旧代码若改为 warn 则失败**，文件：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXSeataProxyContractTest.java`
- [X] T014 [US2] 回填报告：F-01、F-02 置为 `FIXED`，附修复后测试全名与修复前后日志对照，文件：`specs/003-seata-datasource-audit/review-report.md`

**Checkpoint**: US2 两组用例全绿；F-01/F-02 可交付

---

## Phase 5: User Story 3 - 装配边界不牵连数据源模块 (Priority: P2)

**Goal**: 没有动态数据源 bean 时安静跳过并告警，而不是启动崩溃。

**Independent Test**: 无 `GXDynamicDataSource` bean 的 `ApplicationContextRunner` 启动成功、出现跳过告警；补齐 bean 后定制器注入生效。

- [X] T015 [US3] 将 `seataDynamicDataSourceCustomizer` 参数改为 `ObjectProvider<GXDynamicDataSource>`，在 `customize` 回调内惰性解析；取不到时输出 WARN（`[Seata] GXDynamicDataSource is absent, skip MyBatis-Plus datasource customization`）并跳过注入，文件：`leaf-base-seata/src/main/java/cn/maple/seata/config/GXSeataDynamicDataSourceConfig.java`
- [X] T016 [P] [US3] 更新既有断言"配置类只能有一个公开 bean 方法"为"不存在返回 `DataSource`/代理类型的 bean 方法，且恰有一个 `SqlSessionFactoryBeanCustomizer`"；保留原注入断言，文件：`leaf-base-seata/src/test/java/cn/maple/seata/config/GXSeataDynamicDataSourceConfigTest.java`
- [X] T017 [US3] 新增缺失场景测试：无 `GXDynamicDataSource` bean 时上下文启动成功、定制器不注入、校验 bean 跳过且告警可断言；**旧代码上必须失败**（旧实现抛 `NoSuchBeanDefinitionException`），文件：`leaf-base-seata/src/test/java/cn/maple/seata/config/GXSeataDataSourceAbsenceTest.java`

**Checkpoint**: US3 用例独立通过，且不破坏 US2 的三态断言

---

## Phase 6: User Story 4 - 启用 Seata 不改变数据源模块既有行为 (Priority: P2)

**Goal**: 固定"包装后类型契约""包装前后路由与上下文一致""拦截器链仍在"，并覆盖关闭路径。全部使用桩代理，不构造真实代理。

**Independent Test**: `mvn -pl leaf-base-datasource -am test -Dtest='GXSeataWrapperContractTest,GXSeataDataSourceParityTest,GXSeataDataSourceLifecycleTest,GXDataSourceAspectTest'` 全部通过。

- [X] T018 [P] [US4] 新增包装契约测试（正向断言，桩代理）：`SeataDataSourceProxy` 桩 `isInstance` 为真、`getTargetDataSource()` 返回构建时传入的原始实例、路由源 `isInstance(SeataDataSourceProxy)` 为假（FR-007 两条事实）；**禁止 `getClass()` 断言，禁止构造真实代理**，文件：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXSeataWrapperContractTest.java`
- [X] T019 [US4] 新增包装前后对照 + 拦截器链冒烟：同配置在"已包装（桩）"与"未包装"两形态下路由访问序列与 `GXDynamicContextHolder` 恢复一致；另断言 MyBatis-Plus 拦截器链仍含分页、租户/数据权限、防全表更新，且自动填充仍生效（对应 FR-009 收窄范围），文件：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXSeataDataSourceParityTest.java`
- [X] T020 [US4] 新增关闭路径测试：`GXDynamicDataSource#destroy()` 关闭其管理的连接池且不重复关闭，包装层（桩）不额外持有需关闭资源，文件：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXSeataDataSourceLifecycleTest.java`

**Checkpoint**: 既有 `GXDataSourceAspectTest` 12 个用例保持通过（SC-004）

---

## Phase 7: User Story 5 - 配置入口与文档一致 (Priority: P3)

**Goal**: 配置来源与双入口冲突可观测、缺失失败信息可定位、4 种组合各有独立用例、文档与实现一致。

**Independent Test**: `mvn -pl leaf-base-seata test -Dtest=GXSeataPropertiesSpringBootTest` 的 4 个组合用例全绿；手册与契约文档逐条对照无冲突。

- [X] T021 [P] [US5] 在 T011 已实现的校验 bean 中补"两套配置入口同时命中"的 WARN（按 `nacos` 优先记录 `config-source=nacos`）；**复用来源探测，不新增第二个 `config-source` 日志点**，文件：`leaf-base-seata/src/main/java/cn/maple/seata/config/GXSeataDynamicDataSourceConfig.java`
- [X] T022 [US5] 补齐 4 个独立配置组合用例：①隐藏 `NacosConfigManager` 时仅本地入口装配；②存在该类型时仅 Nacos 入口装配、本地不装配；③profile 缺失 → 启动失败且消息含"profile 未设置/缺失"；④资源缺失 → 启动失败且消息含当前 profile 与 `classpath:/<profile>/seata.yml`；**③④ 的消息断言在旧代码上必须失败**，文件：`leaf-base-seata/src/test/java/cn/maple/seata/properties/GXSeataPropertiesSpringBootTest.java`
- [X] T023 [P] [US5] 修订模块手册：条件装配类名、`classpath:/<profile>/seata.yml` 路径约定、外部 `config/<profile>/` 需入类路径、严格开关、失败语义、包装后类型契约（经 `getTargetDataSource()` 取连接池）、**不检测"自动+手工并存"（使用方配置边界）**，文件：`docs/leaf-base-seata开发手册.md`
- [X] T024 [P] [US5] 更新模块指南的变更前检查项与推荐命令（新增开关、两个模块回归命令、日志分级要求、禁止构造真实代理的测试约束），文件：`leaf-base-seata/AGENTS.md`

**Checkpoint**: SC-005 达成（4 种组合行为与文档一致）

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 全量回归、横切检查与交付物一致性收尾

- [X] T025 执行全量回归：`mvn -pl leaf-base-datasource -am test` 与 `mvn -pl leaf-base-seata -am test`，记录用例总数与结果，文件：`specs/003-seata-datasource-audit/review-report.md`
- [X] T026 [P] 扫描本次新增/修改源码的日志文案：非 ASCII 字符 0 条、连接串/账号/口令 0 条；确认"未启用 Seata"路径无 WARN、`config-source` 只有一个输出点，文件：`specs/003-seata-datasource-audit/review-report.md`
- [X] T027 回填报告状态并核对一致性：F-01/F-02/F-03 为 `FIXED` 且附测试全名；报告的编号/级别与 `spec.md` 摘要一致；不出现"混合代理检测"承诺，文件：`specs/003-seata-datasource-audit/review-report.md`
- [X] T028 按 `quickstart.md` 第 6 节逐项勾选交付前检查，未通过项回到对应任务修复，并把勾选结果写入报告第 1 章，文件：`specs/003-seata-datasource-audit/review-report.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 T002 的版本事实；**阻塞所有用户故事**
- **US1 (Phase 3)**: 依赖 Phase 2
- **US2 (Phase 4)**: 依赖 Phase 2；T011 与 T015/T021 共享同一文件，**必须串行**
- **US3 (Phase 5)**: 依赖 Phase 2；T015 必须在 T011 之后（同文件）
- **US4 (Phase 6)**: 仅依赖 Phase 2，可与 US2/US3 并行（不同文件）
- **US5 (Phase 7)**: T021 依赖 T011（同文件、复用来源探测）；T022 依赖 T011；T023/T024 可全程并行
- **Polish (Phase 8)**: 依赖全部用户故事完成

### Within Each User Story

- 测试先写并确认失败（T003、T012、T013、T017、T022），失败点见各任务描述
- 契约常量（T004、T005）先于校验与日志改造（T011、T021）
- 实现先于文档定稿（T023/T024 在 T011/T021 之后）

### Parallel Opportunities

- Phase 1：T001 与 T002
- Phase 2：T003 与 T005（不同文件）；T004 依赖 T003
- US1：T006 与 T007
- US2：T010 与 T012
- US4：T018 / T019 / T020 三个测试文件互相独立
- US5：T023 / T024 与 T021 / T022 可并行

---

## Parallel Example: User Story 2

```bash
# 可并行：元数据与装配测试是不同文件
Task: "T010 登记 maple.framework.enable.seata-strict"
Task: "T012 编写 GXSeataProxyAssemblyTest 三态用例"

# 串行：同文件 GXSeataDynamicDataSourceConfig.java
Task: "T011 增加 SmartInitializingSingleton 校验 bean（含来源探测）"
```

## Parallel Example: User Story 4

```bash
# 三个测试文件互不重叠，可同时开始
Task: "T018 GXSeataWrapperContractTest（桩代理，正向断言）"
Task: "T019 GXSeataDataSourceParityTest（对照 + 拦截器链冒烟）"
Task: "T020 GXSeataDataSourceLifecycleTest（destroy 关闭）"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. 完成 Phase 1 Setup
2. 完成 Phase 2 Foundational（阻塞项）
3. 完成 Phase 3 US1 → 产出可复核的审查报告
4. **STOP and VALIDATE**：随机抽 2 条 Finding 按复现命令验证
5. 报告即可交付，后续修复按优先级推进

### Incremental Delivery

1. Setup + Foundational → 类名基线统一，契约测试锁定
2. US1 → 报告交付（MVP）
3. US2 → 代理失效分级可观测 + 严格开关（最高风险项闭环）
4. US3 → 装配边界解耦
5. US4 → 类型契约 + 行为对照 + 拦截器链冒烟
6. US5 → 配置组合与文档对齐
7. Polish → 全量回归与交付物一致性

### Task Conflict Notes

- `GXSeataDynamicDataSourceConfig.java` 被 T005 → T011 → T015 → T021 依次修改，必须串行
- `GXDynamicDataSourceConfig.java` 被 T004 单独修改（日志级别保持不变），可与 seata 模块并行
- `GXSeataProxyContractTest.java` 被 T003 与 T013 先后修改，串行执行
- 报告类任务（T001/T002/T006/T007/T008/T009/T014/T025/T026/T027/T028）写同一文件，按阶段串行

---

## Notes

- `[P]` 表示不同文件且无未完成依赖
- 每个用户故事在 Checkpoint 处可独立验证
- 所有测试禁止构造真实 Seata 代理（构造即连库并校验 `undo_log`），包装行为用 `SeataDataSourceProxy` 桩对象验证
- 交付前确认：`BLOCKER` 全部 `FIXED`，日志全 ASCII，"未启用 Seata"路径 0 告警

---

## Phase 9: Convergence

- [X] T029 以可捕获的日志追加器（log4j2 `AbstractAppender` 或等价方案）补运行时日志断言：存在未代理数据源时确实产生含数据源名称的 WARN、全部已代理时 INFO 含 `proxied=[...]`、未启用 Seata 时 0 条 WARN，文件：`leaf-base-seata/src/test/java/cn/maple/seata/config/GXSeataProxyAssemblyTest.java` 与 `leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXSeataProxyContractTest.java` per SC-003 / FR-011 (partial)
- [X] T030 在对照测试中补自动填充链路断言（对含 `createdAt`/`updatedAt` 的实体执行 `GXAutoFillMetaObjectHandler#insertFill`，断言字段被填充），文件：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/config/GXSeataDataSourceParityTest.java` per FR-009 / US4-AC5 (partial)
- [X] T031 在无 `GXDynamicDataSource` bean 的场景中断言"可检索的跳过原因"告警文本可被检索到（当前只断言 bean 存在与不注入），文件：`leaf-base-seata/src/test/java/cn/maple/seata/config/GXSeataDataSourceAbsenceTest.java` per US3-AC1 (partial)
- [X] T032 新增"使用方自定义 `SqlSessionFactory` bean 时框架不覆盖"的用例（自定义工厂存在时定制器不改变其数据源），文件：`leaf-base-seata/src/test/java/cn/maple/seata/config/GXSeataDataSourceAbsenceTest.java` per US3-AC3 (missing)
- [X] T033 将审查报告第 6 章中不可直接执行的条目改为可执行形式（F-06 的 `<seata-all.jar>` 占位符给出具体定位命令；F-07 改为可执行的对照命令），文件：`specs/003-seata-datasource-audit/review-report.md` per SC-006 / FR-002 (partial)
- [X] T034 修正审查报告统计与级别：§1 的 seata 测试类数量与实际（4 个）一致；§2 将 F-07 由 `CLEAN` 改为 `RISK`，并核对 §2 与 §4 的级别/状态一致，文件：`specs/003-seata-datasource-audit/review-report.md` per FR-016 (contradicts)
- [X] T035 修正 `leaf-base-seata/AGENTS.md` 第 4 节重复的"3."编号（新增的测试约束独立编号，后续条目顺延），文件：`leaf-base-seata/AGENTS.md` per Constitution VII (contradicts)

---

## Phase 10: Convergence

- [X] T036 将真实环境用例 `bothTargetDataSourcesAreWrappedBySeataProxy` 的断言从"类名包含 DataSourceProxy"改为精确匹配 `org.apache.seata.rm.datasource.DataSourceProxy`（并保留 `instanceof SeataDataSourceProxy`），确保集成层固定新命名空间，文件：`leaf-base-datasource/src/test/java/cn/maple/core/datasource/it/MultiDataSourceIT.java` per FR-003 / SC-004 (partial)
- [X] T037 在审查报告第 6 章补一条可直接粘贴执行的真实环境命令（含 `-Ddynamic.datasource.framework.*` / `slave1.*` 的 url、username、password、connection-properties 完整参数与 `-Dtest=SeataGlobalTransactionIT`），使 E-09 结论可在 15 分钟内复现，文件：`specs/003-seata-datasource-audit/review-report.md` per SC-006 / FR-002 (partial)
- [X] T038 补 `config-source` 取值用例：本地入口生效时断言日志含 `config-source=local`，Nacos 入口生效时断言 `config-source=nacos`（用 `ApplicationContextRunner` 注入对应 properties bean），文件：`leaf-base-seata/src/test/java/cn/maple/seata/config/GXSeataProxyAssemblyTest.java` per US2/AC3, US2/AC4, FR-011 (partial)
