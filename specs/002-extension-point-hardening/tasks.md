# Tasks: 扩展点模块审查加固（leaf-base-extension）

**Input**: Design documents from `/specs/002-extension-point-hardening/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 本特性强制测试先行（spec FR-015、FR-016 + 模块 AGENTS.md 第 12 条）。每个缺陷修复任务前必须先有"在旧代码上失败"的测试任务。

**Organization**: 按用户故事分组，每个故事可独立实现与独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同阶段其它任务并行（不同文件、无未完成依赖）
- **[Story]**: 对应 spec.md 的用户故事（US1–US5）
- 所有路径相对于仓库根 `e:/project/java/maple-leaf-framework/`

## Path Conventions

单模块 Maven 库项目：

- 主源码：`leaf-base-extension/src/main/java/cn/maple/extension/`
- 测试源码：`leaf-base-extension/src/test/java/cn/maple/extension/`
- 构建脚本：`leaf-base-extension/pom.xml`
- 模块验证命令：`mvn -pl leaf-base-extension -am test`
- 下游编译验证：`mvn -q -DskipTests install`（仓库根）

---

## Phase 1: Setup

**Purpose**: 锁定改造前基线，使后续每个"测试先行"任务都有明确红/绿判据。

- [x] T001 运行 `mvn -pl leaf-base-extension -am test` 并记录基线到 `leaf-base-extension/target/surefire-reports/`：逐类记录 `Tests run` 与总数、确认 `cn.maple.extension.ExtensionTest.txt` 不存在（3 个用例零执行）、记录 `execute` 精确命中与四层兜底的单次开销量级（参照 15–26 ns / 23–44 ns）；后续验收一律与本基线比较，不硬编码用例数

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: US2、US3、US5 共用的两处底座——"字符串标识可还原为场景"与"坐标构造期校验"。US1 的注册路径也依赖坐标校验。

**⚠️ CRITICAL**: 本阶段完成前，任何用户故事都无法通过各自验收。

- [x] T002 [P] 测试先行（应在旧代码上失败）：在 `leaf-base-extension/src/test/java/cn/maple/extension/GXBizScenarioTest.java` 增加 `fromUniqueIdentity` 用例——合法三段 `a.b.c` 还原成功；`a.b`（2 段）、`a.b.c.d`（4 段）、`a..c`（空白段）、`""`（空串）均抛 `IllegalArgumentException` 且信息含实际段数与非法段
- [x] T003 [P] 实现 `fromUniqueIdentity(String identity)` 于 `leaf-base-extension/src/main/java/cn/maple/extension/GXBizScenario.java`：按 `.` 拆分，要求恰为 3 段且每段非空（`bizId`/`useCase`/`scenario` 各自非空、非空白、不含 `.`），失败信息含实际段数与非法段；方法标注 `@NullMarked`
- [x] T004 [P] 测试先行（应在旧代码上失败）：在 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionCoordinateTest.java` 增加字符串构造校验用例——`a.b`、`a.b.c.d`、`a..c` 构造即失败；合法 `a.b.c` 构造成功且 `getExtensionPointClass()`/`getBizScenario()` 为 `null`（`@Nullable`）；与等价类型坐标 `equals` 为真、`hashCode` 相同
- [x] T005 [P] 实现于 `leaf-base-extension/src/main/java/cn/maple/extension/GXExtensionCoordinate.java`：字符串构造器改用 `GXBizScenario.fromUniqueIdentity` 校验（失败信息含段数）；`getExtensionPointClass()` 与 `getBizScenario()` 补 `@Nullable`；`equals`/`hashCode` 仍只用 `extensionPointName + bizScenarioUniqueIdentity`

**Checkpoint**: 坐标与场景的输入契约已锁定，字符串坐标可安全进入兜底流程。

---

## Phase 3: User Story 1 - 多场景扩展必须被完整注册 (Priority: P1) 🎯 MVP

**Goal**: 一个实现类声明的全部场景坐标都被注册；解析不出坐标或父子撞同一坐标时立即失败而非静默。

**Independent Test**: `mvn -pl leaf-base-extension test -Dtest=GXExtensionRegisterTest,GXExtensionBootstrapTest` —— 五种声明写法的注册条数等于声明坐标数；空声明抛异常；混用注解的 Bean 两处坐标都注册；父子同坐标冲突失败。

**契约依据**: `contracts/extension-annotation.md` 第 2、3、5 节；`research.md` R1、R11。

- [x] T006 [P] [US1] 测试先行（应在旧代码上失败）：在 `leaf-base-extension/src/test/java/cn/maple/extension/register/GXExtensionRegisterTest.java` 增加用例——(a) 重复 `@GXExtension` ×2 经 `doRegistration` 注册 2 条；(b) `@GXExtensions(value={..,..})` 注册 2 条；(c) `@GXExtensions(bizId={"a","b"}, useCase={"c"}, scenario={"d","e"})` 注册 4 条；(d) 混用 `@GXExtension` + `@GXExtensions` 注册 2 条；(e) `@GXExtensions(bizId={})` 抛 `GXExtensionException` 且信息含实现类全名与声明形态；(f) 父类 `@GXExtension(bizId="p")`、子类 `@GXExtension(bizId="p")` 各自注册后冲突失败，信息同时含父子双方类名
- [x] T007 [US1] 在 `leaf-base-extension/src/main/java/cn/maple/extension/register/GXExtensionRegister.java` 新增 `resolveBizScenarios(Class<?>)` 并让 `doRegistration` 与 `doRegistrationExtensions` 共用：`repeatable = AnnotatedElementUtils.findMergedRepeatableAnnotations(clazz, GXExtension.class)`（Spring 7 返回 `Set`）逐项转坐标；当 `container != null && container.value().length == 0` 时追加笛卡尔积 `bizId × useCase × scenario`；坐标总数为 0 时抛 `GXExtensionException`
- [x] T008 [US1] 修改 `leaf-base-extension/src/main/java/cn/maple/extension/register/GXExtensionRegister.java`：扩展点接口解析（`calculateExtensionPoint`）提到坐标循环外，每个实现类只反射一次；冲突注册（含父子同坐标）与"无扩展点接口/多扩展点接口/命名不合规"统一抛 `GXExtensionException`，信息含坐标、已有实现类名、冲突实现类名或候选接口列表
- [x] T009 [P] [US1] 测试先行（应在旧代码上失败）：在 `leaf-base-extension/src/test/java/cn/maple/extension/register/GXExtensionBootstrapTest.java` 增加用例——(a) 混用注解的 Bean 启动后两处坐标都可执行；(b) 仅笛卡尔积属性的 Bean 启动后被注册
- [x] T010 [US1] 修改 `leaf-base-extension/src/main/java/cn/maple/extension/register/GXExtensionBootstrap.java`：把"`@GXExtension` 与 `@GXExtensions` 两段循环 + 前者见到容器就跳过"改为"合并两个注解来源的 Bean 名、按名去重、统一走 `doRegistration`"，从而覆盖混用与仅笛卡尔积两种形态

**Checkpoint**: US1 可独立验收——`GXExtensionRegisterTest` + `GXExtensionBootstrapTest` 全绿，且 (a)–(f) 用例在旧代码上为红。

---

## Phase 4: User Story 2 - 所有调用入口的场景兜底顺序一致 (Priority: P1)

**Goal**: 类型坐标入口与字符串坐标入口返回同一实现，四条兜底路径完全一致。

**Independent Test**: `mvn -pl leaf-base-extension test -Dtest=GXExtensionExecutorTest` —— 5 类用例（精确/默认场景/默认用例/全局默认/全未命中）在两个入口上结果相同。

**契约依据**: `contracts/executor-api.md` 第 2、3 节；`research.md` R3。

- [x] T011 [P] [US2] 测试先行（应在旧代码上失败）：在 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionExecutorTest.java` 增加用例——同一组已注册数据下，用 `GXExtensionCoordinate.valueOf(extPointName, identity)` 走 `execute`/`executeVoid`，在"仅全局默认""仅默认场景""三档分层""精确+兜底共存"四种注册组合下命中与类型入口相同的实现；全未命中时两入口抛同类异常
- [x] T012 [US2] 修改 `leaf-base-extension/src/main/java/cn/maple/extension/GXExtensionExecutor.java`：抽出私有 `locateExtensionByName(String extensionPointName, GXBizScenario bizScenario)` 承载四层兜底（精确 → `identityWithDefaultScenario` → `identityWithDefaultUseCase` → 全局默认，触达仓库次数 ≤ 4）；`locateExtension(Class, GXBizScenario)` 与 `locateComponent(String, String)` 都委托它，字符串入口先用 `fromUniqueIdentity` 还原场景
- [x] T013 [P] [US2] 在 `leaf-base-extension/src/main/java/cn/maple/extension/register/GXAbstractComponentExecutor.java` 为 `locateComponent(String, String)` 默认实现补充 Javadoc：默认不支持字符串坐标，子类可覆盖以支持完整兜底（**不改行为、不改签名**）

**Checkpoint**: US2 可独立验收——`GXExtensionExecutorTest` 中字符串入口与类型入口断言一致。

---

## Phase 5: User Story 3 - 失败要显式、错误语义要正确 (Priority: P2)

**Goal**: 解析失败一律非 2xx 且可定位；信息要素按失败类型分层；空入参在触达仓库前失败。

**Independent Test**: `mvn -pl leaf-base-extension test -Dtest=GXExtensionExceptionTest,GXExtensionExecutorTest,GXExtensionRegisterTest` —— 错误码为 404；每类失败的信息要素符合 `contracts/executor-api.md` 4.1 的分层要求。

**契约依据**: `contracts/executor-api.md` 第 4、4.1 节；`spec.md` FR-008、FR-009、FR-010；`research.md` R12。

- [x] T014 [P] [US3] 测试先行（应在旧代码上失败）：在 `leaf-base-extension/src/test/java/cn/maple/extension/exception/GXExtensionExceptionTest.java` 断言 `new GXExtensionException("x")` 与 `new GXExtensionException("x", new RuntimeException())` 的错误码为 `404` 而非 `200`，且仍是 `GXBusinessException` 实例
- [x] T015 [US3] 修改 `leaf-base-extension/src/main/java/cn/maple/extension/exception/GXExtensionException.java`：`GXExtensionException(String msg)` 与 `GXExtensionException(String msg, Throwable e)` 中的 `HttpStatus.HTTP_OK` 改为 `HttpStatus.HTTP_NOT_FOUND`；其余构造器与继承关系不变
- [x] T016 [US3] 测试先行：在 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionExecutorTest.java` 增加用例——未命中异常错误码为 404，信息含扩展点标识与请求场景标识且**不要求**实现类名；`execute(null, ...)`、`execute(extPoint, null, ...)`、`executeVoid(extPoint, null, ...)` 抛 `NullPointerException` 且不触达仓库
- [x] T017 [US3] 修改 `leaf-base-extension/src/main/java/cn/maple/extension/GXExtensionExecutor.java`：未命中统一抛 `GXExtensionException`（404，信息含扩展点全名与 `uniqueIdentity`，不拼接实现类名）；`null` 校验改用 `Objects.requireNonNull` 并保留字段名信息；日志文本保持 ASCII
- [x] T018 [US3] 测试先行与实现：在 `leaf-base-extension/src/test/java/cn/maple/extension/register/GXExtensionRegisterTest.java` 增加分层信息断言——冲突用例断言含坐标 + 已有实现类名 + 冲突实现类名（含父子继承冲突）；多扩展点接口用例断言列出全部候选接口；命名不合规用例断言含实现类名与约定片段；断言文本全为 ASCII

**Checkpoint**: US3 可独立验收——三类失败的错误码与分层信息要素全部有断言。

---

## Phase 6: User Story 4 - 端到端示例场景必须被自动化测试真正执行 (Priority: P2)

**Goal**: 客户域端到端示例在构建时真实执行，成为兜底链路与装配的回归门禁；类名符合宪法命名。

**Independent Test**: `mvn -pl leaf-base-extension test -Dtest=GXExtensionCustomerSpringBootTest` 后 `target/surefire-reports/cn.maple.extension.GXExtensionCustomerSpringBootTest.txt` 存在且 `Tests run: 3, Failures: 0, Errors: 0`。

**契约依据**: `research.md` R10；宪法第 VI 条命名规则（`GX<能力>SpringBootTest`）。

- [x] T019 [US4] 迁移并把 `leaf-base-extension/src/test/java/cn/maple/extension/ExtensionTest.java` 改名为 `GXExtensionCustomerSpringBootTest.java`（同目录、`public class GXExtensionCustomerSpringBootTest`）：删除 `@RunWith(SpringJUnit4ClassRunner.class)`（保留 `@SpringBootTest(classes = ExtTestApplication.class)`），`org.junit.Test` → `org.junit.jupiter.api.Test`，`org.junit.Assert.assertNotNull` → `org.junit.jupiter.api.Assertions.assertNotNull`；三个用例的入参、业务调用与断言内容保持原样
- [x] T020 [US4] 修改 `leaf-base-extension/pom.xml`：移除 `junit:junit` 依赖与冗余的 `org.junit.jupiter:junit-jupiter-api` 声明（保留 `spring-boot-starter-test`）；确认无其它测试类引用 JUnit 4
- [x] T021 [US4] 运行 `mvn -pl leaf-base-extension test` 并核对 `leaf-base-extension/target/surefire-reports/`：`GXExtensionCustomerSpringBootTest` 出现且 3 个用例通过，其余测试类用例数不少于 T001 记录的基线

**Checkpoint**: US4 可独立验收——端到端示例从"零执行"变为"3 通过"，且类名符合宪法命名。

---

## Phase 7: User Story 5 - 并发正确性与解析开销被测试守护 (Priority: P3)

**Goal**: 并发注册/解析的既有正确性被自动化锁定；解析开销与"规模无关"有可重复的三层证据。

**Independent Test**: `mvn -pl leaf-base-extension test -Dtest=GXExtensionConcurrencyTest,GXExtensionExecutorBenchmarkTest` —— 竞争注册恰 1 次成功、并发解析 0 次异常结果、双门禁通过、查找次数 ≤ 4、规模比 ≤ 1.2。

**契约依据**: `contracts/executor-api.md` 第 5 节；`research.md` R7、R8。

- [x] T022 [P] [US5] 新增 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionConcurrencyTest.java`：8 个虚拟线程（`Thread.ofVirtual()`）在同一 `CountDownLatch` 释放后各尝试注册同一坐标 10 000 次 → 成功次数恰为 1、`size()` 为 1；随后 8 个虚拟线程各解析 200 000 次 → 无空实现、无与坐标不符的结果
- [x] T023 [P] [US5] 新增 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionExecutorBenchmarkTest.java`，提供三层证据：(a) 双门禁——预热后 5 轮采样取中位数，执行器精确命中与四层兜底均须 ≤ 6 × "裸 `ConcurrentHashMap.get` 对照"中位数且 ≤ 1 μs，对照汇总量写入 `volatile` 字段防 JIT 消除；(b) 查找次数——用可计数的仓库替身（重写 `findExtension` 计数的子类）断言精确命中触达 1 次、四层兜底 ≤ 4 次；(c) 规模无关——注册 1 个实现与注册 100 个实现两种场景下执行器耗时中位数比值 ≤ 1.2
- [x] T024 [US5] 修改 `leaf-base-extension/src/main/java/cn/maple/extension/GXExtensionExecutor.java`：`locateComponent` 的 `log.debug("[Located Extension]: {}", extension.getClass().getSimpleName())` 改为 `log.isDebugEnabled()` 包裹（或直接传 `Class` 对象），消除 debug 关闭时的 `getSimpleName()` 求值开销

**Checkpoint**: US5 可独立验收——并发与三层性能证据全绿，且无回归时稳定通过。

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: 注册时机、装配唯一性与确定性、全量回归、消融验证与迁移公示。

- [x] T025 修改 `leaf-base-extension/src/main/java/cn/maple/extension/register/GXExtensionBootstrap.java`：`@PostConstruct init()` 改为实现 `SmartInitializingSingleton#afterSingletonsInstantiated`；注册前按 Bean 名排序，使"同一坐标两条声明"的已有/冲突角色在多次启动间保持一致
- [x] T026 [P] 扩展 `leaf-base-extension/src/test/java/cn/maple/extension/autoconfigure/GXExtensionAutoConfigurationTest.java`：新增上下文用例——组件扫描覆盖 `cn.maple.extension` 且自动装配同时生效时，`extensionBootstrap`/`extensionRepository`/`extensionExecutor`/`extensionRegister` 四个 Bean 各只有一个（依据 `contracts/autoconfiguration.md` §2）
- [x] T027 [P] 在 `leaf-base-extension/src/test/java/cn/maple/extension/register/GXExtensionBootstrapTest.java` 增加两个用例——(a) `@Lazy` 扩展 Bean 启动后仍可被执行器命中；(b) 同一组冲突声明启动两次，断言报错信息中"已有实现/冲突实现"的类名角色完全一致（验证 T025 的排序确定性）
- [x] T028 全量回归：运行 `mvn -pl leaf-base-extension -am test` 确认全绿、ErrorProne/NullAway 无报错、`target/surefire-reports/` 中每个测试类 `Tests run > 0`、既有 `GXExtensionRepositoryTest` 的只读快照用例仍通过（守护 FR-013）；再运行 `mvn -q -DskipTests install` 确认下游模块无需改动即可编译通过（守护 SC-007）
- [x] T029 消融验证（手工，验证后还原，不保留改动）：临时把 `leaf-base-extension/src/main/java/cn/maple/extension/GXExtensionExecutor.java` 的字符串入口改回"仅精确匹配"、把 `leaf-base-extension/src/main/java/cn/maple/extension/register/GXExtensionRegister.java` 的解析改回 `findMergedAnnotation(GXExtension)`，确认 T006/T011/T019 对应用例变红；把观察结论写入 PR 描述
- [x] T030 在 PR 描述中公示 5 处行为收紧与迁移路径（参照 `specs/002-extension-point-hardening/plan.md` 的 Complexity Tracking）：字符串坐标严格化、空声明改抛异常、扩展异常码 200→404、混用注解补齐注册、父子同坐标改为冲突失败；另附 2 处实现/验收口径调整（注册时机、性能双门禁）

---

## Dependencies

```
Phase 1 (T001)
    │
    ▼
Phase 2 (T002–T005, Foundational)
    │
    ├──► Phase 3 US1 (T006–T010) ──┐
    ├──► Phase 4 US2 (T011–T013) ──┤
    ├──► Phase 5 US3 (T014–T018) ──┼──► Phase 8 Polish (T025–T030)
    ├──► Phase 6 US4 (T019–T021) ──┤
    └──► Phase 7 US5 (T022–T024) ──┘
```

**故事间依赖说明**：

- **US1 (P1)**：仅依赖 Phase 2。可独立交付（MVP）。
- **US2 (P1)**：依赖 Phase 2 的 `fromUniqueIdentity`（T003）。不依赖 US1。
- **US3 (P2)**：T016/T017 与 US2 共用 `GXExtensionExecutor.java`，必须在 T012 之后执行（同文件串行）；T018 与 T006 共用测试文件，须在 T006 之后。
- **US4 (P2)**：独立；但端到端用例会同时覆盖 US1/US2 的兜底与装配，建议在 US1、US2 之后跑以获得最大回归价值。
- **US5 (P3)**：独立；T024 与 US2/US3 共用 `GXExtensionExecutor.java`，同文件串行。
- **Polish**：T025 依赖 T010（同一 `GXExtensionBootstrap.java`）；T028/T029 依赖全部故事完成。

### 同文件串行约束

| 文件 | 顺序 |
| --- | --- |
| `GXExtensionExecutor.java` | T012 → T017 → T024 |
| `GXExtensionRegister.java` | T007 → T008 |
| `GXExtensionBootstrap.java` | T010 → T025 |
| `GXExtensionRegisterTest.java` | T006 → T018 |
| `GXExtensionExecutorTest.java` | T011 → T016 |
| `GXExtensionBootstrapTest.java` | T009 → T027 |

---

## Parallel Opportunities

```text
# Phase 2：两个底座互不相关，可并行
T002 (GXBizScenarioTest.java)  ║  T003 (GXBizScenario.java)
T004 (GXExtensionCoordinateTest.java)  ║  T005 (GXExtensionCoordinate.java)

# Phase 3–7：不同文件的故事可并行
T006 (GXExtensionRegisterTest.java)  ║  T009 (GXExtensionBootstrapTest.java)
T011 (GXExtensionExecutorTest.java)  ║  T013 (GXAbstractComponentExecutor.java)  ║  T014 (GXExtensionExceptionTest.java)
T019 (GXExtensionCustomerSpringBootTest.java)  ║  T022 (GXExtensionConcurrencyTest.java)  ║  T023 (GXExtensionExecutorBenchmarkTest.java)

# Phase 8：两个 Spring 上下文用例互不干扰
T026 (GXExtensionAutoConfigurationTest.java)  ║  T027 (GXExtensionBootstrapTest.java)
```

---

## Implementation Strategy

### MVP First（仅 US1）

1. T001 基线 → T002–T005 底座
2. T006–T010（US1）
3. **STOP and VALIDATE**：`mvn -pl leaf-base-extension test -Dtest=GXExtensionRegisterTest,GXExtensionBootstrapTest`
4. 此时已修复最严重的"静默漏注册"，可独立交付

### Incremental Delivery

1. 底座 + US1 → 验证 → 交付（修复静默漏注册，含父子冲突判定）
2. + US2 → 验证 → 交付（修复入口不对等）
3. + US3 → 验证 → 交付（错误语义与分层信息）
4. + US4 → 验证 → 交付（端到端门禁恢复 + 命名合规）
5. + US5 → 验证 → 交付（并发/性能三层证据）
6. + Polish → 全量回归 + 下游编译 + 消融验证

### Parallel Team Strategy

底座完成后按文件所有权分工，避免同文件冲突：

- 开发者 A：US1（`GXExtensionRegister.java` + `GXExtensionBootstrap.java` + 对应测试）
- 开发者 B：US2 + US3（`GXExtensionExecutor.java` + `GXExtensionException.java`，按串行顺序 + 各自测试）
- 开发者 C：US4 + US5（端到端测试迁移 + `pom.xml` + 两个新测试类）
- 收尾由一人完成 Polish（`GXExtensionBootstrap.java` 时机改造 + 全量回归 + 下游编译）

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖；同文件任务见"同文件串行约束"
- 每个测试先行任务必须先确认在**旧代码上失败**，再进入对应实现任务（AGENTS.md 第 12 条）
- 每个任务完成后运行一次 `mvn -pl leaf-base-extension test -Dtest=<相关测试类>`，避免批量失败难以定位
- 不要为一次性逻辑新增类型；本次仅新增 2 个方法（`GXBizScenario.fromUniqueIdentity`、`GXExtensionRegister.resolveBizScenarios`）与 2 个测试类
- FR-018（引入缓存前证明收益）已由 `research.md` R7 决策为**不引入缓存**，因此无对应任务；若实现过程中临时想加缓存，须先补基准数据
- 日志文本必须为 ASCII；公共 API 的空值语义用 `@Nullable` / `@NullMarked` 明确
- 术语统一：本任务清单只用"执行器（Executor）"指代解析组件，不再使用"解析器"
- 提交信息格式 `fix(extension): ...`，一个逻辑组一次提交

---

## Phase 9: Convergence

- [x] T031 在 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionConcurrencyTest.java` 增加"注册期间并发解析"用例：注册线程写入目标坐标的同时，8 个虚拟线程各解析 200 000 次，断言每次结果要么是正确的实现、要么是 `GXExtensionException` 未命中，绝不出现 `null`、其他实现或非预期异常（总计 60 秒超时保护），以覆盖"读-写并发"可见性而非"先注册后读取" per US5/AC2 + FR-012 + quickstart 场景 6 (partial)
- [x] T032 在 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionExecutorBenchmarkTest.java` 把对照物改回字面意义的裸 `ConcurrentHashMap.get`（键在循环外预构造，汇总量写入 `volatile` 字段），相对门禁按"每次仓库查找"计预算（精确命中 1 次、四层兜底 4 次，查找次数由计数替身另行断言），保留 ≤ 1 μs 绝对门禁；同时去掉 `GXExtensionExecutor.locateExtensionByName` 中与 `GXExtensionCoordinate` 构造重复的空白校验（公开入口的入参校验保留），使精确命中实测中位数 ≤ 6 × 裸对照，并在 `research.md` 记录对照口径与实测值；不得以放宽倍数收场 per SC-006 + FR-014 (contradicts)
- [x] T033 在 `leaf-base-extension/src/main/java/cn/maple/extension/register/GXExtensionRegister.java` 的 `resolveBizScenarios` 中把"注解属性为空白（`@GXExtension(bizId=" ")`、`@GXExtensions(bizId={" "})`）导致 `GXBizScenario.valueOf` 抛 `IllegalArgumentException`"包装为 `GXExtensionException`，信息含实现类全名、声明形态与非法属性值，并在 `GXExtensionRegisterTest` 补这两例断言（旧代码上应为"信息缺失"的失败） per FR-002 + contracts/extension-annotation.md §3 (partial)
- [x] T034 在 `leaf-base-extension/src/test/java/cn/maple/extension/GXExtensionExecutorTest.java` 补"请求场景恰为全局默认占位符"用例：仅注册全局默认实现时解析 `GXBizScenario.newDefault()` 命中该实现，并用计数仓库断言查找次数仍 ≤ 4（覆盖四次查找退化为同一键的边界，结果正确且无重复副作用） per Edge Cases: 默认占位符场景 (partial)
- [x] T035 更新 `specs/002-extension-point-hardening/quickstart.md` 场景 9 的命令与报告路径为 `GXExtensionCustomerSpringBootTest` 与 `cn.maple.extension.GXExtensionCustomerSpringBootTest.txt`，与 T019 的类名迁移保持一致 per quickstart 场景 9 + SC-005 (partial)
- [x] T036 在 `GXBizScenario.validateUniqueIdentity` 的 Javadoc 与 PR 描述中说明该包级方法的引入理由（坐标构造期复用校验、避免为校验创建场景对象），并补一条用例断言坐标构造失败的"实际段数/非法段"信息与 `fromUniqueIdentity` 完全一致，消除与 plan"仅新增 2 个方法"及 T005"改用 `fromUniqueIdentity` 校验"表述的偏差 per plan: Structure Decision + T005 (unrequested)

---

## Phase 10: Convergence

- [x] T037 把 `GXExtensionExecutor.locate` 改回调用公共 `extensionRepository.findExtension(String, String).orElse(null)`，并让 `GXExtensionRepository.findExtension(String, String)` 重新委派到可覆盖的 `findExtension(GXExtensionCoordinate)`，删除包级 `findExtensionOrNull`；两个计数仓库替身改回覆盖公共方法；补一条用例断言"自定义仓库覆盖 `findExtension(String, String)` / `findExtension(GXExtensionCoordinate)` 后执行器解析走覆盖实现"；复跑性能门禁，若实测超出 `research.md` R13 记录区间则同步更新 per FR-017 + contracts/autoconfiguration.md §2 + Constitution II (contradicts)
- [x] T038 在 `GXExtensionRegister` 中对 `describeDeclaration` 回显的注解属性值（含 `GXExtensions` 三个数组与 `GXExtension` 三个字段）做 ASCII 安全化（非 ASCII 与不可打印字符转义为 `\uXXXX`），并补一条"非 ASCII `bizId` 声明失败"的用例断言异常消息全为 ASCII per FR-010 (partial)
