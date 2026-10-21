# Implementation Plan: Seata 与动态数据源协作正确性审查加固

**Branch**: `003-seata-datasource-audit` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-seata-datasource-audit/spec.md`

## Summary

本次交付三件事，顺序不可颠倒：

1. **审查结论独立成文**：把已完成的静态审查与依赖核对结果（E-01～E-08、F-01～F-07）写入 `review-report.md`，每条附代码位置、触发条件、证据与复现命令，满足 SC-001/SC-006。
2. **修掉三个已确认缺陷的最小集合**：把数据源侧反射使用的 Seata 代理类名从旧命名空间 `io.seata...` 统一到基线版本新命名空间 `org.apache.seata...`（F-02）；把"已启用但未代理"从静默改为 seata 侧含数据源名称的 warn，并提供默认关闭的严格开关（F-01），同时**保持数据源侧在未启用 Seata 时只记 debug**，避免给纯数据源用户制造启动噪声；把 Seata 集成对动态数据源 bean 的硬依赖改为惰性解析，缺失时跳过装配（F-03）。
3. **建立不依赖外部中间件的测试基线**：契约测试（类名/接口/构造器）+ 装配测试（ApplicationContextRunner、FilteredClassLoader）+ 启动期校验测试，覆盖代理生效、代理缺失、装配缺失、包装前后行为对照、配置互斥五类场景。

技术路径刻意保持"零新增运行时依赖"：`leaf-base-datasource` 对 Seata 只有 test-scope 依赖，因此继续用反射实例化代理，但**只使用新命名空间类名**，并把"是否具备代理能力"的判定统一到 `org.apache.seata.rm.datasource.SeataDataSourceProxy` 这一个标识上。代理是否真正生效由 `leaf-base-seata` 在启动期读取路由数据源的已解析目标源进行权威校验——那里有编译期依赖与配置语义，不需要 datasource 认识 Seata 的开关。

## Technical Context

**Language/Version**: Java 21（根 POM `maven.compiler.release=21`），构建 JDK 25 可用但语言级别不变

**Primary Dependencies**: Spring Boot 4.1.1、Spring Framework 7.0.9、MyBatis-Plus 3.5.17（`SqlSessionFactoryBeanCustomizer`）、Druid 1.2.28、Seata 2.6.0（`org.apache.seata:seata-spring-boot-starter`）、JUnit 5 + AssertJ

**Storage**: N/A（框架模块，不拥有数据库 schema；`undo_log` 属业务库，本次不建表、不验证第二阶段）

**Testing**: JUnit 5（Jupiter）+ AssertJ + `ApplicationContextRunner` + `FilteredClassLoader`；命令 `mvn -pl leaf-base-seata -am test`、`mvn -pl leaf-base-datasource -am test`

**Target Platform**: JVM 21 库，被 Spring Boot 4.x 应用以 Maven 依赖方式引入

**Project Type**: Maven 多模块库（library），无独立可执行入口

**Performance Goals**: 启动期校验为对已解析数据源集合的一次只读遍历，复杂度 O(数据源数量)，单数据源 < 1ms；运行期零新增开销（不进入 SQL 执行路径，不新增代理层）

**Constraints**:
- 不新增运行时依赖；`leaf-base-datasource` 不得引入 Seata 编译期依赖
- 不改变公开注解、既有配置键语义与 `seata.yml` 迁移路径；新增开关默认值必须保持现有启动表现
- 日志一律 ASCII，`warn`/`error` 必须包含数据源名称；日志分级固定：未启用 Seata（类路径无代理类型）→ datasource 侧 debug；已装配但目标源未代理 → seata 侧 warn
- 只承诺两条可断言事实：路由数据源不被二次代理、每个物理数据源最多一层代理；不实现"自动代理与手工代理并存"的运行时检测
- ErrorProne + NullAway（`AnnotatedPackages=cn.maple`）必须零错误；新代码补 `@NullMarked`/`@Nullable`
- 测试不得依赖真实 Seata Server、Nacos、MySQL
- 测试不得构造真实 Seata 代理：`DataSourceProxy` 构造器会 `getConnection()` 并校验 `undo_log` 表，无库环境必然失败；包装行为一律用实现 `SeataDataSourceProxy` 的桩对象验证，代理类本身只做"类名/接口/构造器"反射契约测试

**Scale/Scope**: 2 个 Maven 模块；生产代码改动 2 个文件 + 新增 1 个资源配置；测试 7 个文件（1 改 6 新增）；文档 2 处；审查报告 1 份

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 |
| --- | --- | --- |
| I. 模块自治与按需装配 | PASS | 不新增运行时依赖；`leaf-base-datasource` 继续"无 Seata 也能用"（反射 + 类缺失即跳过）；依赖方向仍为 seata → datasource，无反向依赖 |
| II. 稳定契约优先 | PASS（需登记） | 成功路径不变；datasource 侧日志级别不变（仍为 debug），未启用 Seata 的用户不会新增告警；新开关默认 `false` 保持下游启动表现；唯一"契约收紧"是把类型判定统一到新命名空间，需在 PR 中声明并附 Seata 版本锚定说明 |
| III. 统一响应与统一异常 | PASS | 严格模式失败沿用 `GXBusinessException`；不涉及 Controller/响应结构 |
| IV. 显式配置与双配置源 | PASS | 新开关走 `maple.framework.enable.*` 并写入 `additional-spring-configuration-metadata.json`；本地/Nacos 互斥语义不变；无敏感值硬编码 |
| V. 空值安全与上下文纪律 | PASS | 新增代码标注 JSpecify；校验器只读取已解析数据源，不新增 `ThreadLocal`、不改变上下文恢复路径 |
| VI. 测试与静态检查门槛 | PASS | 新增契约/装配/校验测试；不依赖外部中间件；不修改编译参数绕过静态检查 |
| VII. 命名与包结构 | PASS | 新增类型放在既有 `cn.maple.seata.config` / `cn.maple.core.datasource.config` 包；沿用 `GX` 前缀与 `Config`/`Test` 后缀；日志走 SLF4J |

**Gate 结论**：通过，无需豁免项。跨模块改动仅 1 处（`leaf-base-datasource` 的代理类名常量），按 FR-013 在 PR 中给出最小影响面与回归证明。

## Project Structure

### Documentation (this feature)

```text
specs/003-seata-datasource-audit/
├── spec.md              # 需求（已完成，含 8 条澄清）
├── plan.md              # 本文件
├── research.md          # Phase 0：技术决策与备选方案
├── data-model.md        # Phase 1：审查结论/场景矩阵/包装契约/配置契约
├── quickstart.md        # Phase 1：可执行验证指南
├── contracts/
│   ├── seata-assembly-contracts.md   # 装配、配置键、日志、异常、类型契约
│   └── review-report-schema.md       # 审查报告条目结构与校验规则
├── review-report.md     # 实现阶段交付：逐条 Finding + 证据 + 复现命令
└── tasks.md             # Phase 2（/speckit.tasks 生成）
```

### Source Code (repository root)

```text
leaf-base-datasource/
└── src/
    ├── main/java/cn/maple/core/datasource/config/
    │   └── GXDynamicDataSourceConfig.java        # 改：代理类名常量 → 新命名空间（日志级别保持 debug）
    └── test/java/cn/maple/core/datasource/config/
        ├── GXSeataProxyContractTest.java          # 新：类名/接口/构造器契约 + 类缺失降级行为
        ├── GXSeataWrapperContractTest.java        # 新：包装后类型契约（正向断言，不写负向陈述）
        ├── GXSeataDataSourceParityTest.java       # 新：包装 / 未包装两种形态的路由与上下文对照
        └── GXSeataDataSourceLifecycleTest.java    # 新：destroy 关闭连接池、不重复关闭

leaf-base-seata/
└── src/
    ├── main/
    │   ├── java/cn/maple/seata/
    │   │   ├── config/GXSeataDynamicDataSourceConfig.java   # 改：惰性解析数据源 + 启动期代理校验 + 来源日志
    │   │   └── properties/GXLocalSeataProperties.java       # 不改（仅被 ObjectProvider 探测）
    │   └── resources/META-INF/
    │       └── additional-spring-configuration-metadata.json # 新：严格开关元数据
    └── test/java/cn/maple/seata/
        ├── config/GXSeataDynamicDataSourceConfigTest.java    # 改：放宽"只允许一个 bean"的过强断言，补新增行为
        ├── config/GXSeataProxyAssemblyTest.java              # 新：装配/校验/严格模式
        └── config/GXSeataDataSourceAbsenceTest.java          # 新：无动态数据源 bean 时跳过装配

docs/leaf-base-seata开发手册.md                   # 改：条件装配、类名、路径、开关、失败语义、类型契约
leaf-base-seata/AGENTS.md                         # 改：与实现对齐的检查项与命令
```

**Structure Decision**: 沿用仓库既有多模块布局，不新增模块、不新增包。审查报告与设计产物放在特性目录，生产改动限定在 `leaf-base-seata` 与 `leaf-base-datasource` 两个模块的既有包路径下。

## Phase 0: Research Summary

详见 [research.md](./research.md)。核心决策：

1. **保留反射、只换新命名空间类名**（不引入 Seata 编译依赖，不把代理创建下沉到 seata 模块）。
2. **代理生效校验放 seata 模块**，读取 `GXDynamicDataSource#getResolvedDataSources()`（Spring 7 公开 API），用 `SeataDataSourceProxy` 判定，`SmartInitializingSingleton` 时机执行。
3. **不用 `@ConditionalOnBean`**（扫描装配下顺序不可靠），改用 `ObjectProvider<GXDynamicDataSource>` 惰性解析。
4. **严格开关** `maple.framework.enable.seata-strict` 默认 `false`，读取点只在 seata 模块。
5. **不为 `leaf-base-seata` 新增 `AutoConfiguration.imports`**：与 datasource 模块现状一致，新增自动装配会造成扫描 + 自动装配双注册，属超范围装配重写。
6. **测试不实例化真实代理**：已核对 Seata 2.6.0 源码，`DataSourceProxy#init` 会取连接、读 `jdbcUrl` 并校验 `undo_log`；因此代理类只做反射契约测试，包装后的行为断言（接口实现、`getTargetDataSource()` 返回原实例、销毁路径）用桩实现完成。

## Phase 1: Design Summary

- **data-model.md**：四个实体——审查结论（Finding）、协作场景矩阵、数据源包装契约、配置入口契约；含代理状态机（未启用 → 已包装 → 已校验 / 已告警 / 严格失败），状态机的"未启用"分支只允许 debug。
- **contracts/seata-assembly-contracts.md**：装配条件、配置键（含新开关）、日志分级与行格式、异常语义、包装后类型契约（只写可正向断言的事实，不写"不再保证为某类型"式负向承诺）。
- **contracts/review-report-schema.md**：审查报告每条的必填字段与"可复现"判定规则。
- **quickstart.md**：两条模块级测试命令、类清单核对命令、五类协作场景（含 4 种配置组合各自独立用例、拦截器链与自动填充存在性冒烟、桩代理说明）的验证步骤与预期输出。

两条收窄结论直接约束设计：①FR-007 只断言"路由数据源不被二次代理"与"每个物理数据源最多一层代理"，不设计任何"自动 + 手工并存"的检测点；②FR-009 的对照范围限定为路由与上下文逐项 + 拦截器链与自动填充的存在性冒烟，其余既有行为由数据源模块既有测试保证。

## Post-Design Constitution Re-check

| 原则 | 判定 | 设计落地后的变化 |
| --- | --- | --- |
| I. 模块自治 | PASS | 设计未引入依赖；`GXDynamicDataSourceConfig` 仍可在无 Seata 类路径下工作（反射失败 → debug 后返回原数据源，不产生告警） |
| II. 稳定契约 | PASS | 设计明确"成功路径不新增日志级别噪声之上的行为变化"；类型契约变化写入 contracts 并由测试固定 |
| III. 统一异常 | PASS | 严格模式仅新增一种失败入口，复用 `GXBusinessException` |
| IV. 显式配置 | PASS | 新开关 + 元数据；配置来源日志不新增配置键 |
| V. 空值安全 | PASS | 校验器用 `ObjectProvider#getIfAvailable` 与 `@Nullable` 标注，避免 NPE 路径 |
| VI. 测试门槛 | PASS | 测试矩阵见 quickstart，全部无外部依赖 |
| VII. 命名与包结构 | PASS | 无新增类型名争议；校验逻辑以 `SmartInitializingSingleton` bean 形式存在于既有 `GXSeataDynamicDataSourceConfig` |

**结论**：设计后仍通过，无违规项。

## Complexity Tracking

> 无 Constitution Check 违规项，无需豁免说明。

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| — | — | — |
