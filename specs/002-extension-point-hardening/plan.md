# Implementation Plan: 扩展点模块审查加固（leaf-base-extension）

**Branch**: `002-extension-point-hardening` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-extension-point-hardening/spec.md`

**Note**: 本计划只覆盖 `leaf-base-extension` 模块内部；不新增运行期注册/注销、扩展优先级或多实现组合能力，不改变注解属性、公共方法签名与自动装配 Bean 名称。

## Summary

审查确认主链路可用（四层兜底、自动装配、仓库并发正确、单次解析 15–44 ns），但存在两处确定的正确性缺陷与若干语义瑕疵：

1. **场景声明解析只覆盖一种写法**：`AnnotatedElementUtils.findMergedAnnotation(GXExtension.class)` 对"重复注解""显式容器""笛卡尔积容器""单注解+容器混用"四种写法返回 `null` 或只返回部分坐标，导致注册静默为 0 条或漏注册。实测证据见 `research.md#R1`。
2. **两个执行入口行为不对等**：字符串坐标入口（`locateComponent(String, String)`）只做一次精确查找，缺失四层兜底。
3. 附带瑕疵：扩展异常的"解析失败"语义被两个构造器写成 200；`@PostConstruct` 注册时机导致扩展 Bean 被提前实例化且冲突报错顺序不确定；坐标字符串不做格式校验；注册期每坐标重复反射解析扩展点接口；debug 日志在关闭时仍求值 `getSimpleName()`；端到端示例测试因旧测试框架零执行。

技术方案：把"一个实现类声明了哪些坐标"收敛为**唯一内部解析流程**（`doRegistration` / `doRegistrationExtensions` 变成同一流程的两个入口），把执行器的两个入口收敛为**唯一兜底流程**（字符串坐标先解析成场景对象再走同一条兜底），注册时机从 `@PostConstruct` 改为"所有单例就绪后"的有序注册，并补齐并发、基准、Spring 上下文与端到端回归四类测试。

## Technical Context

**Language/Version**: Java 21（根 POM `maven.compiler.release=21`）；编译期强制 ErrorProne 2.50.0 + NullAway 0.13.4（`AnnotatedPackages=cn.maple`、`JSpecifyMode=true`），构建失败即不合格

**Primary Dependencies**: `leaf-base-framework`（Spring Boot 4.1.1、Spring Framework 7.0.9、Hutool 5.8.47）；注解解析使用 Spring `AnnotatedElementUtils` / `MergedAnnotations`；测试使用 `spring-boot-starter-test`（JUnit 5 Jupiter、AssertJ、Mockito、`ApplicationContextRunner`）。**不新增任何第三方依赖**

**Storage**: N/A（纯进程内 `ConcurrentHashMap`，注册期可写、运行期只读）

**Testing**: JUnit 5（Jupiter）+ AssertJ + `ApplicationContextRunner` + `@SpringBootTest`；模块级命令 `mvn -pl leaf-base-extension -am test`；并发用虚拟线程（`Thread.ofVirtual()` / `Executors.newVirtualThreadPerTaskExecutor()`）；性能用手写预热 + 多轮采样的双门禁（6 × 对照中位数 + 1 μs 上限），并用可计数仓库替身断言查找次数 ≤ 4、用 1 vs 100 规模对比断言规模无关，不引入 JMH

**Target Platform**: JVM 21 服务端类库（被下游 Spring Boot 应用以 Maven 依赖引入，通过 `AutoConfiguration.imports` 装配）

**Project Type**: library（Maven Reactor 中的 `leaf-base-extension` 单模块）

**Performance Goals**: 双门禁——单次解析耗时中位数 ≤ 6 × 同进程裸映射查找中位数，且 ≤ 1 μs（当前基线：精确命中 15–26 ns，四层兜底 23–44 ns）；兜底触达仓库的查找次数恒为 ≤ 4（与注册规模无关，以可计数替身断言）；1 个实现与 100 个实现两种规模下耗时中位数比值 ≤ 1.2；解析路径零反射、零字符串拼接、零全量拷贝

**Constraints**: 只改 `leaf-base-extension`；注解属性、公共方法签名、Bean 名称、场景标识格式（`bizId.useCase.scenario` + 两个默认占位符）保持不变；五处行为收紧（见 Complexity Tracking）必须配回归测试与迁移说明；NullAway 必须通过

**Scale/Scope**: 16 个主源码文件（7 个核心类型 + 3 个注册/执行类型 + 1 个自动装配 + 1 个异常 + 4 个 `package-info`）、12 个测试类（11 个共 39 个用例通过 + 1 个 3 用例零执行）；并发验证强度 8 虚拟线程 × 10 000 次竞争注册 + 8 虚拟线程 × 200 000 次并发解析

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 本计划的约束动作 | 结论 |
| --- | --- | --- |
| I. 模块自治与按需装配 | 改动全部落在 `leaf-base-extension`；不新增模块、不改依赖方向、不新增第三方依赖；自动装配仍由 `AutoConfiguration.imports` 驱动，`@ConditionalOnMissingBean` 覆盖语义不变 | PASS |
| II. 稳定契约优先 | 注解属性与默认值、公共方法签名（`execute*`、`locateExtension`、`doRegistration*`、`registerExtension*`）、Bean 名称（`extensionBootstrap`/`extensionRepository`/`extensionExecutor`/`extensionRegister`）、坐标字符串格式全部不变；只收紧行为，共 5 处需公示（见 Complexity Tracking） | PASS（附迁移说明） |
| III. 统一响应与统一异常 | 注册与解析失败统一抛 `GXExtensionException`（仍继承 `GXBusinessException`，异常处理链路不变）；错误码复用宪法允许的 `HttpStatus` 常量：解析未命中=404，注册冲突/装配错误=500；不新增状态码枚举 | PASS |
| IV. 显式配置与双配置源 | 本次不新增任何配置项与开关（spec FR-019 明确不新增运行期能力），因此无需 `additional-spring-configuration-metadata.json` 变更 | PASS |
| V. 空值安全与上下文纪律 | 包级 `@NullUnmarked` 保持不变（与模块现状一致）；本次修改的公共 API 补齐 `@Nullable`（`getExtensionPointClass()`、`getBizScenario()`）与 `@NullMarked`（新增静态工厂）；无 `ThreadLocal`、无线程池、无资源持有，无需释放路径 | PASS |
| VI. 测试与静态检查门槛 | 每个缺陷配"旧代码失败、新代码通过"的回归测试；注册与装配用 Spring 上下文测试；并发与性能补自动化门禁；端到端示例迁移到 JUnit 5 后必须真实执行；不使用 JUnit 4 | PASS |
| VII. 命名与包结构一致性 | 不新增类型（避免为一次性逻辑抽类）；仅在既有类上新增方法：`GXBizScenario.fromUniqueIdentity(String)`、`GXExtensionRegister.resolveBizScenarios(Class)`；命名沿用 `GX` 前缀与动词约定 | PASS |

**初判**：无原则违反。5 处行为收紧（字符串坐标格式校验、"解析不出坐标"改为抛异常、异常码 200→404、混用注解补齐注册、父子同坐标改为冲突失败）均由 spec 澄清确认，属于缺陷修复方向，记入 Complexity Tracking 并需在提交说明中公示；另有 2 处实现与验收口径调整（注册时机、性能门禁口径）。

## Project Structure

### Documentation (this feature)

```text
specs/002-extension-point-hardening/
├── plan.md              # 本文件
├── research.md          # Phase 0：12 项技术决策、实测证据与备选方案
├── data-model.md        # Phase 1：坐标、场景、仓库状态、解析轨迹
├── quickstart.md        # Phase 1：可执行验证场景与预期结果
├── contracts/           # Phase 1：公共契约
│   ├── extension-annotation.md   # 注解与坐标声明组合矩阵
│   ├── executor-api.md           # 执行入口、兜底顺序、异常契约
│   ├── coordinate.md             # 坐标格式与构造期校验
│   └── autoconfiguration.md      # Bean 名称、覆盖方式、注册时机
└── tasks.md             # Phase 2（/speckit.tasks 生成，本命令不产出）
```

### Source Code (repository root)

```text
leaf-base-extension/
├── pom.xml                                        # 迁移完成后移除 junit4 依赖
├── src/main/java/cn/maple/extension/
│   ├── GXBizScenario.java                         # 新增 fromUniqueIdentity(String)：三段校验与还原
│   ├── GXExtensionCoordinate.java                 # 字符串构造补三段校验；两个可空 getter 补 @Nullable
│   ├── GXExtensionExecutor.java                   # 两个入口收敛到同一兜底流程；debug 日志加开关判断
│   ├── GXExtensionRepository.java                 # 保持语义；仅补注释与 null 契约，不改并发模型
│   ├── GXExtension.java / GXExtensions.java       # 不改（契约冻结）
│   ├── GXExtensionPoint.java                      # 不改
│   ├── exception/GXExtensionException.java        # 两个 200 构造器改为 404；其余构造器不变
│   ├── register/GXAbstractComponentExecutor.java  # 仅补 Javadoc（默认实现行为与签名不变）
│   ├── register/GXExtensionBootstrap.java         # @PostConstruct → SmartInitializingSingleton；按 Bean 名有序注册；合并两个注解来源
│   ├── register/GXExtensionRegister.java          # 新增 resolveBizScenarios：五种声明写法统一解析；扩展点解析提到循环外
│   └── autoconfigure/GXExtensionAutoConfiguration.java  # 不改（Bean 名称与条件冻结）
├── src/main/resources/META-INF/spring/…imports    # 不改
└── src/test/java/cn/maple/extension/
    ├── GXBizScenarioTest.java                     # 补 fromUniqueIdentity 用例
    ├── GXExtensionCoordinateTest.java             # 补段数/空白段校验用例
    ├── GXExtensionExecutorTest.java               # 补字符串入口四层兜底 + 未命中
    ├── GXExtensionRepositoryTest.java             # 保持不变
    ├── GXExtensionConcurrencyTest.java            # 新增：竞争注册唯一成功 + 并发解析一致
    ├── GXExtensionExecutorBenchmarkTest.java      # 新增：双门禁（6× 对照 + 1μs）+ 查找次数 + 1 vs 100 规模对比
    ├── GXExtensionCustomerSpringBootTest.java     # 由 ExtensionTest 迁移而来：JUnit 4 → 5，用例与断言不变，类名符合宪法命名
    ├── register/GXExtensionRegisterTest.java      # 补重复注解/显式容器/笛卡尔积/混用/空声明/父子继承冲突用例
    ├── register/GXExtensionBootstrapTest.java     # 补混用注解、仅笛卡尔积、懒加载 Bean、Bean 唯一性
    └── exception/GXExtensionExceptionTest.java    # 补错误码断言（404 而非 200）
```

**Structure Decision**: 采用"单模块内收敛"策略。不新增任何新类型，只在既有类型上把散落的解析逻辑收敛为单一流程——注册侧收敛进 `GXExtensionRegister`，执行侧收敛进 `GXExtensionExecutor`，坐标解析收敛进 `GXBizScenario`。理由：这三处逻辑各自被两处入口复用（`doRegistration`/`doRegistrationExtensions`、类坐标/字符串坐标、场景对象/坐标字符串），满足"抽取后至少两处复用"的门槛；不新建类可避免违反 AGENTS.md 第 11 条。

## Phase 0 研究结论（详见 research.md）

12 项决策已全部收敛，无 NEEDS CLARIFICATION 遗留。关键取舍：

1. **声明解析**用 `findMergedRepeatableAnnotations`（返回 `Set`，Spring 7 签名）+ `findMergedAnnotation(GXExtensions)` 组合：前者已覆盖"单注解/重复注解/显式容器/混用"四种写法，后者仅在"仅笛卡尔积属性"时补充；两者皆空才判定为"未声明坐标"并抛异常。
2. **混用注解**（`@GXExtension` 与 `@GXExtensions` 同时出现）在 Java 语言层合法，且 `findMergedRepeatableAnnotations` 会同时返回两处坐标，因此不需要额外分支。
3. **执行器两个入口**：字符串坐标 → `GXBizScenario.fromUniqueIdentity` → 与类坐标共用同一条四层兜底流程；`GXAbstractComponentExecutor.locateComponent(String,String)` 默认实现保留（避免破坏子类），但 `GXExtensionExecutor` 覆盖它且不再只做精确匹配。
4. **注册时机**改为 `SmartInitializingSingleton`：避免在 Bean 创建阶段触发全量扩展 Bean 实例化，并使注册顺序可按 Bean 名排序，从而让冲突报错可复现。
5. **错误码**：仅把两个使用 200 的构造器改为 404；注册冲突改抛 `GXExtensionException`（500），保持 `GXBusinessException` 父子关系不变。
6. **性能门禁**为双门禁：多轮采样取中位数，同时要求 ≤ 6 × 同进程 `ConcurrentHashMap.get` 对照中位数、且 ≤ 1 μs；既抵消机器差异，又保留绝对天花板。不引入 JMH。
7. **规模无关**为双证据：用可计数的仓库替身断言单次解析触达仓库的查找次数恒为 ≤ 4；再用"1 个实现 vs 100 个实现"两种规模的耗时中位数比值 ≤ 1.2 覆盖对象分配等间接开销。
8. **继承与冲突**：`@Inherited` 语义保留；父类与子类各自声明并落到同一坐标时按重复声明冲突失败，信息给出父子双方类名，禁止子类静默覆盖（`javac` 允许父子各自标注，故必须显式判定）。
9. **失败信息分层**：未命中给扩展点 + 请求场景；注册冲突给坐标 + 冲突双方实现类；扩展点接口非法给实现类 + 候选接口列表。每类只承诺它能真实提供的信息，避免出现不可满足的验收断言。

## Phase 1 设计产出

- **data-model.md**：5 类模型（业务场景、扩展坐标、注册声明、扩展仓库、解析结果）及其字段、校验规则与状态流转；含继承冲突判定与"查找次数 ≤ 4"的性能不变量。
- **contracts/**：4 份契约（注解与坐标声明、执行入口与兜底、坐标格式、自动装配），明确"只收紧行为、不改签名"的兼容边界与分层失败信息。
- **quickstart.md**：9 个可执行验证场景，覆盖多写法注册、混用注解、字符串入口兜底、非法坐标、错误码、并发、性能双门禁与规模无关、懒加载注册、端到端示例。

## Phase 1 后宪法复核

| 原则 | 复核结果 |
| --- | --- |
| I | 依然单模块、零新增依赖；新增仅 2 个方法 + 3 个测试类 → PASS |
| II | 契约项全部保持；5 处行为收紧已登记并配回归测试 → PASS + 迁移说明 |
| III | 统一抛 `GXExtensionException`，错误码改用 404/500 常量，无新枚举 → PASS |
| IV | 无新增配置键，无需元数据文件 → PASS |
| V | 新增/修改 API 的空值语义已用 `@Nullable`/`@NullMarked` 标注；无上下文与资源 → PASS |
| VI | 缺陷→回归测试一一映射；并发/基准/上下文/端到端四类门禁齐备 → PASS |
| VII | 未新增类型，方法命名延续既有动词约定 → PASS |

## Complexity Tracking

| Violation / 行为变化 | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| 字符串坐标构造期由"接受任意字符串"改为"必须三段点分" | 只有可解析的坐标才能执行四层兜底（FR-005/FR-020）；否则同一场景在字符串入口与类入口选出不同实现 | 保留宽松并把不一致写进契约：兜底承诺出现例外，验收无法用统一断言表达；运行时再判：失败点从构造期后移到请求期 |
| `doRegistration` 对"解析不出坐标"由静默跳过改为抛异常 | 静默跳过会把装配缺陷推后到运行期以"找不到扩展"暴露（FR-002） | 仅记 WARN 后跳过：缺陷仍后置，只是多一条线索；保持静默：与宪法"不得用静默表达失败"冲突 |
| 扩展异常码由 200 改为 404（两个构造器） | 200 会让上游按成功处理（FR-008），与统一异常体系冲突 | 新增模块级状态码枚举：改变对外错误码且新增契约，反而扩大影响面 |
| 混用 `@GXExtension` + `@GXExtensions` 由"漏注册一半"改为"两处都注册" | 语言层允许混用且 Spring 能同时解析出两处坐标（实测见 research R1），漏注册属缺陷 | 禁止混用：需新增编译期检查或运行期拒绝，属于额外能力且会拒绝当前合法写法 |
| 注册时机由 `@PostConstruct` 改为 `SmartInitializingSingleton` | `@PostConstruct` 期间 `getBeansWithAnnotation` 会强制实例化全部扩展 Bean，且顺序不可控，冲突报错不可复现 | 保持 `@PostConstruct`：装配期副作用与不确定性保留；改 `ContextRefreshedEvent`：同一事件可能因父子上下文多次触发，需要额外幂等保护 |
| 父子同坐标由"语义未定义"改为"冲突失败" | 现状依赖 `@Inherited` + Spring 合并语义，父子各自声明时结果取决于 Bean 注册顺序；本规格已确认按重复声明处理（FR-003） | 允许子类覆盖：需要定义"谁算子类"并引入 WARN 日志与顺序依赖，排查成本高于直接禁止；继承坐标不注册：子类无法接管父类场景 |
| 性能验收由"100 ns 绝对阈值"改为"6 × 对照 + 1 μs"双门禁 | 绝对纳秒阈值在共享 CI 上受 CPU 降频与 GC 影响会偶发失败，导致门禁被人为放宽或删除 | 保留 100 ns：灵敏度高但不可维护；仅用相对基线：机器整体变慢时门禁形同虚设 |
