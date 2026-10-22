# Implementation Plan: leaf-base-framework 稳定性、正确性、线程安全与性能审查

**Branch**: `005-framework-module-audit` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-framework-module-audit/spec.md`

## Summary

本次交付是**审查产物**，不改生产代码。四件事，顺序不可颠倒：

1. **结论独立成文**：把 22 条证据（E-01～E-22）与 21 条发现（F-01～F-21）写入 `review-report.md`，每条附维度、级别、位置、触发条件、影响、证据、复现方式、回归测试设计与兼容性影响，满足 SC-002/SC-008/SC-009。
2. **覆盖台账显式化**：309 个生产文件按包建立逐文件判定台账（`coverage-ledger.md`），四档判定（无缺陷 / 风险 / 缺陷 / 仅契约核对），满足 FR-001/SC-001。
3. **把"读代码结论"升级为"可执行现状"**：为 8 条高危发现各写一条特征化用例（characterization test），断言**当前有缺陷的行为**；修复落地时这些断言必须翻转，因此它们同时充当修复验收开关（FR-011/SC-008）。
4. **口径化性能结论**：6 条横切路径（XSS 改写、请求体缓存、限流、并发工具、事件分发、反射/拷贝）各给可复算公式，并用"调用次数/复杂度/上限"而非绝对耗时断言，避免 flaky（SC-004）。

技术路径刻意保持零运行时改动：`pom.xml` 不新增依赖，`src/main` 零改动，仅新增 `src/test/java/cn/maple/core/framework/audit/**` 与 `specs/005-framework-module-audit/**`。

## Technical Context

**Language/Version**: Java 21 编译目标（根 POM `maven.compiler.release=21`）；本机验证运行在 JDK 25.0.4 上，两者行为差异（尤其虚拟线程调度）已登记为本计划的风险项

**Primary Dependencies**: Spring Boot 4.1.1 / Spring Framework 7.x（`spring-boot-starter-web`、`-aop`、`-validation`、`-actuator`、`-log4j2`）、Caffeine、Guava（EventBus）、Hutool（`hutool-core/json/http/extra/crypto`）、Jackson（`com.fasterxml` + `tools.jackson` 双栈）、Lombok、BouncyCastle；测试侧 JUnit 5（Jupiter 6.0.3）、Mockito、AssertJ、`spring-boot-starter-test`（含 `ApplicationContextRunner`、`MockMvc`）

**Storage**: 无持久化。审查对象涉及的内存结构：Caffeine 缓存、Guava EventBus 订阅注册表、静态 `ConcurrentHashMap`（限流/反射/拷贝缓存）、`ThreadLocal`/MDC

**Testing**: `mvn -o -pl leaf-base-framework test`；已实测基线 **`Tests run: 518, Failures: 0, Errors: 0, Skipped: 0`（65 个测试类）**，可作为 SC-007 的对照基线。单类执行：`mvn -o -pl leaf-base-framework test -Dtest=<Class>#<method>`

**Target Platform**: JVM 21 库，被全部 `leaf-base-*` 模块与下游业务工程以 Maven 依赖引入；运行形态包括 Servlet 容器、虚拟线程、`@Async`、ForkJoin/并行流、多容器测试环境

**Project Type**: Maven 多模块库（library）；本次交付为**审查报告 + 特征化测试**，无对外接口变更

**Performance Goals**: 不设吞吐指标，设**口径指标**——6 条横切路径各给出：单请求内存峰值与分配次数、单次调用复杂度与规模的关系、结构上限（缓存/队列/并发度/请求体），数值必须可由代码路径逐项复算（SC-004）。可执行冒烟只断言"调用次数/规模关系"，不断言绝对耗时

**Constraints**:

- 不改生产代码：`src/main/**` 与 `pom.xml` 零改动；新增代码仅限 `src/test/**`
- 不依赖真实容器、数据库或中间件；全部审计用例必须能在离线单模块环境运行，不得因环境缺失失败
- 不新增测试依赖（不使用 Awaitility、JMH、架构约束库）；并发等待统一用 `CountDownLatch` + 有界 `get(timeout)`
- 不修改编译参数绕过 ErrorProne/NullAway；新增测试代码同样要能编译通过
- 断言不得使用绝对耗时阈值（既有 `GXMdcThreadUtilsTest` 的 `durationMs < 2000` 属反例，不复制该手法）
- 日志与断言消息用 ASCII；报告用中文，代码位置统一为 `文件:行区间`
- 发现编号（F-01…F-21）与证据编号（E-01…E-22）在本轮内保持稳定；新增发现追加编号，不得重排既有编号
- 判定分级固定：缺陷（与承诺/宪法冲突且可复现）/ 风险（当前可接受但条件变化即失效）/ 契约偏差（文档与实现不一致）/ 既定契约（已被既有用例锁定）/ 未验证假设（无法在本环境证实）

**Scale/Scope**: 26 个包、309 个生产源码文件（dto 83、util 33、service 27、exception 23、event 15、config 15、web 14、validator 13、ddd 12、annotation 11、api 11、其余 8 包合计 64）、65 个测试类、519 个 `@Test` 注解（实测执行 518）。交付 1 份审查报告 + 1 份覆盖台账 + 5 份设计产物 + 8 个审计用例类；生产代码改动 0

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 不改依赖、不改装配条件；审查把"`GXMdcWrapper*` 未被注册为 Bean""`GXAsyncConfig` 每次 `new` 执行器"记为事实，修复建议中标注需独立审批，不在本规格内改 |
| II. 稳定契约优先 | PASS（需登记） | 本次零生产改动，公开注解/DTO/异常语义/配置键不变。F-20/F-21 指向的"文档承诺 vs 实现"属**已存在**的契约偏差，报告单列并要求文档同步，不改实现 |
| III. 统一响应与统一异常 | PASS（需登记） | F-07 指出兜底与容器异常把 `getCause().getMessage()` 写入响应、参数类型错误以 `ok(500)` 返回，与"不得回显堆栈/内部路径""业务失败必须抛异常"冲突；只出结论不改 `GXExceptionHandler` |
| IV. 显式配置与双配置源 | PASS（需登记） | F-03/F-04/F-05 涉及硬编码默认密钥与 `maple.framework.web.filter.xss-filter.max-request-size` 未登记进 `META-INF/additional-spring-configuration-metadata.json`，违反"敏感值只允许外部注入""新增开关同步写入元数据"；报告列为发现，不新增配置键 |
| V. 空值安全与上下文纪律 | PASS（需登记） | 本模块是宪法第 V 条的主要落点；F-01/F-02/F-14 直接指向"`ThreadLocal` 成对 push/poll""异步必须用 MDC 包装能力"的偏差。审查只做判定与复现，不修改上下文实现 |
| VI. 测试与静态检查门槛 | PASS（需登记） | 新增审计用例为 JUnit 5、无外部中间件、无新依赖；不触碰编译参数。**已知偏差登记**：`GXExceptionHandler`（26 个处理器）、三个敏感数据服务、`GXCORSConfig`、`GXHTMLFilter`、`GXBeanCopierCache` 当前零用例覆盖，报告需给出"未覆盖清单"作为后续补齐输入 |
| VII. 命名与包结构 | PASS | 新增测试放 `cn.maple.core.framework.audit`，命名 `GX<被测能力>AuditTest`；沿用 `GX` 前缀与 SLF4J 门面，不新增生产包 |

**Gate 结论**：通过，0 违规项，无需豁免。跨模块改动 0 处。

## Project Structure

### Documentation (this feature)

```text
specs/005-framework-module-audit/
├── spec.md              # 需求（已完成：22 证据 / 21 发现 / 9 条 SC）
├── plan.md              # 本文件
├── research.md          # Phase 0：判定口径、验证手法、实测基线、时序事实核对
├── data-model.md        # Phase 1：Finding / 证据 / 四张矩阵 / 口径表 / 台账
├── quickstart.md        # Phase 1：可执行验证指南（命令 + 预期现象）
├── contracts/
│   ├── review-report-schema.md       # 报告条目结构、级别定义、证据等级、校验规则
│   └── audit-verdict-contract.md     # 四档判定、上下文四段判定、性能口径、缺陷 vs 契约
├── review-report.md     # 实现阶段交付：逐条 Finding + 证据 + 复现命令
├── coverage-ledger.md   # 实现阶段交付：309 文件逐文件判定台账
└── tasks.md             # Phase 2（/speckit.tasks 生成，本命令不创建）
```

### Source Code (repository root)

```text
leaf-base-framework/
├── pom.xml                                      # 不改
├── src/main/java/cn/maple/core/framework/       # 不改（审查对象，309 个文件 / 26 个包）
├── src/main/resources/META-INF/                 # 不改（审查对象：两份配置元数据）
└── src/test/java/cn/maple/core/framework/
    ├── （既有 65 个测试类，518 个用例）          # 不改（行为基线）
    └── audit/                                   # 新增：审查特征化用例
        ├── GXContextPropagationAuditTest.java      # F-01/F-02/F-03
        ├── GXConcurrencySafetyAuditTest.java       # F-13/F-14
        ├── GXEventContractAuditTest.java           # F-08/F-09/F-11
        ├── GXEventExecutionAuditTest.java          # F-10
        ├── GXCacheIsolationAuditTest.java          # F-12
        ├── GXWebBoundaryAuditTest.java             # F-05/F-07/F-18
        ├── GXSecurityBoundaryAuditTest.java        # F-03/F-04/F-06/F-17
        └── GXPerformanceBudgetAuditTest.java       # F-16 + SC-004 六条口径
```

**Structure Decision**: 沿用仓库既有多模块布局，不新增模块、不新增生产包。审查对象保持只读；审计用例独立放在 `cn.maple.core.framework.audit`，与既有 `GX<被测类型>Test` 的单元测试职责分离（后者验证"设计意图"，前者固化"当前现状"）。`dto`/`annotation`/`constant`/`code`/`ddd` 等纯契约包不写用例，只做契约核对并登记台账（见 research D1）。

## Phase 0: Research Summary

详见 [research.md](./research.md)。全部未知项已解决，无 `NEEDS CLARIFICATION` 残留。核心决策：

1. **判定四档 + 假设隔离**：每个文件必须落到"无缺陷 / 风险 / 缺陷 / 仅契约核对"之一；无法复现的判断标 `未验证假设` 且不得计入缺陷统计（对应 SC-002 的"0 条混入"）。
2. **特征化测试而非期望测试**：审计用例断言**当前行为**，用例名与方法名显式表达现状（如 `asyncExecutorDoesNotPropagateTraceId`），修复后必须翻转断言；这样审查交付不需要改生产代码就能被 CI 验证。
3. **时序事实核对（本轮主要收益）**：`GXCommonUtils.getEnvironmentValue` 在 `Environment` 不可用时返回默认值（`GXCommonUtils.java:107-112`、`155-159`），因此静态初始化期读取配置**不会抛错，但会把默认值固化**。据此把"静态初始化读配置"从 F 级缺陷降级为 `风险（配置时序）`，避免计划阶段误判为启动崩溃。
4. **零新增依赖的并发验证**：用 `AnnotationConfigApplicationContext` 直接装载 `GXAsyncConfig` + 一个带 `@Async` 的测试 Bean 验证上下文传播；用 `ApplicationContextRunner` 验证事件 BPP 在代理 Bean 上的行为；用 `MockHttpServletRequest/Response + MockFilterChain` 验证过滤器链，不启动真实容器。
5. **性能口径替代压测**：以"代码路径计数（格式化次数、拷贝次数、遍历次数）+ 复杂度 × 规模 + 结构上限"复核，可执行冒烟只断言次数与规模关系。禁止绝对耗时阈值。
6. **实测基线**：`mvn -o -pl leaf-base-framework test` → 518 用例全绿、0 跳过，作为 SC-007 的对照；审计用例必须在此基线上只增不改。

## Phase 1: Design Summary

- **data-model.md**：七个实体——Finding、Evidence、上下文纪律矩阵项、并发与生命周期矩阵项、失败收敛项、性能口径项、覆盖台账项；含 Finding 状态机（初稿 → 已复核 → 已定级 → 已复现 → 已交付）与"缺陷/风险/契约偏差/既定契约/未验证假设"判定规则。
- **contracts/review-report-schema.md**：每条 Finding 的必填字段、级别与维度取值域、证据等级（源码事实 > 可执行验证 > 阅读推断）、"可复现"判定标准、报告与 `spec.md` 编号对齐规则、SC-009 结论段模板。
- **contracts/audit-verdict-contract.md**：四档判定边界、"上下文四段（写入—传播—清理—异常路径）"的判定表、6 条性能口径的计数公式、"缺陷 vs 既定契约"的双条件判定、覆盖台账判定值的取值域。
- **quickstart.md**：基线命令与预期输出、8 个审计用例类的执行方式、8 条高危发现的单点复现命令与预期现象、性能口径复算步骤、台账与报告一致性核对方式。

三条收窄结论直接约束设计：①"静态初始化读配置"只登记为风险，不计缺陷（研究 D3）；②`@EnableCaching` 缺失只主张"模块不提供缓存 key 隔离能力"，不主张"缓存不生效"（研究 D10）；③`GXSQLFilter` 的误杀/漏判归为"既定契约 + 安全边界不足"，不要求改变现有过滤语义（研究 D9）。

## Post-Design Constitution Re-check

| 原则 | 判定 | 设计落地后的变化 |
| --- | --- | --- |
| I. 模块自治 | PASS | 设计只新增测试包与规格文档；`pom.xml` 零改动，不引入压测/断言库 |
| II. 稳定契约 | PASS | 契约对照把"文档承诺 vs 实现"逐条列出，不产生生产改动；修复建议统一标注"需独立审批 + 兼容性等级" |
| III. 统一异常 | PASS | F-07 只出结论；审计用例用 `assertThat(response.getBody().getMsg())` 固化现状，不修改异常类型与状态码 |
| IV. 显式配置 | PASS | F-03/F-04/F-05 的配置缺失结论附带"应补元数据键名清单"，明确本次不新增键 |
| V. 空值安全与上下文 | PASS | 审计用例对 `null`/空串/空集合/无请求上下文均有显式断言；新增代码标注 JSpecify，不使用裸 `ThreadLocal` |
| VI. 测试门槛 | PASS | 8 个审计用例类全部无外部中间件、无新依赖、无常量耗时断言；台账登记 309 文件可及性 |
| VII. 命名与包结构 | PASS | 新增包 `cn.maple.core.framework.audit`，命名 `GX<能力>AuditTest`，与既有约定不冲突 |

**结论**：设计后仍通过，无违规项。

## Complexity Tracking

> 无 Constitution Check 违规项，无需豁免说明。

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| — | — | — |
